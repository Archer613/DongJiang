package DONGJIANG

import DONGJIANG.CHI._
import DONGJIANG.RNSLAVE._
import DONGJIANG.RNMASTER._
import DONGJIANG.SLICE._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import xs.utils.perf.{DebugOptions, DebugOptionsKey}
import Utils.GenerateVerilog
import Utils.IDConnector._
import Utils.FastArb._

abstract class DJModule(implicit val p: Parameters) extends Module with HasDJParam
abstract class DJBundle(implicit val p: Parameters) extends Bundle with HasDJParam


class DongJiang()(implicit p: Parameters) extends DJModule {
/*
 * System Architecture: (3 RNSLAVE, 1 RNMASTER and 2 bank)
 *
 *    ------------               ----------------------------------------------------------
 *    | RNSLAVE  | <---> | <---> |      |   Dir   |      |  SnpCtl  |                     |
 *    ------------       |       |      -----------      ------------      ---------      |        ------------
 *                       |       | ---> | Arbiter | ---> | MainPipe | ---> | Queue | ---> |  <---> | SNMASTER |
 *    ------------       |       |      -----------      ------------      ---------      |        ------------
 *    | RNSLAVE  | <---> | <---> |      |   DB    | <--> |    DS    |                     |
 *    ------------       |       ----------------------------------------------------------
 *                       |                              Slice
 *                      XBar
 *                       |
 *    ------------       |       ----------------------------------------------------------
 *    | RNSLAVE  | <---> | <---> |      |   Dir   |      |  SnpCtl  |                     |
 *    ------------       |       |      -----------      ------------      ---------      |        ------------
 *                       |       | ---> | Arbiter | ---> | MainPipe | ---> | Queue | ---> |  <---> | SNMASTER |
 *    ------------       |       |      -----------      ------------      ---------      |        ------------
 *    | RNMASTER | <---> | <---> |      |   DB    | <--> |    DS    |                     |
 *    ------------               ----------------------------------------------------------
 *                                                      Slice
 */




/*
 * System ID Map Table:
 * [Module]     |  [private ID]             |  [XBar ID]
 *
 * BASE Slice Ctrl Signals:
 * [req2Slice]  |  [hasAddr]                |  from: [idL0]   [idL1]    [idL2]       | to: [idL0]     [idL1]    [idL2]
 * [resp2Node]  |  [hasCHIChnl]             |  from: [idL0]   [idL1]    [idL2]       | to: [idL0]     [idL1]    [idL2]
 * [req2Node]   |  [hasAddr]                |  from: [idL0]   [idL1]    [idL2]       | to: [idL0]     [idL1]    [idL2]
 * [resp2Slice] |  [hasMSHRSet] [HasDBID]   |  from: [idL0]   [idL1]    [idL2]       | to: [idL0]     [idL1]    [idL2]
 *
 *
 * BASE Slice DB Signals:
 * [dbRCReq]    |  [hasMSHRSet] [hasDBID]   |  from: [idL0]   [idL1]    [idL2]       | to: [idL0]     [idL1]    [idL2]
 * [wReq]       |                           |  from: [idL0]   [idL1]    [idL2]       | to: [idL0]     [idL1]    [idL2]
 * [wResp]      |  [hasDBID]                |  from: [SLICE]  [sliceId] [DontCare]   | to: [idL0]     [idL1]    [idL2]
 * [dataFDB]    |                           |  from: None                            | to: [idL0]     [idL1]    [idL2]
 * [dataTDB]    |  [hasDBID]                |  from: None                            | to: [idL0]     [idL1]    [idL2]
 *
 * ****************************************************************************************************************************************************
 *
 * RnSlave <-> Slice Ctrl Signals:
 * [req2Slice]  |  [hasAddr]                |  from: [RNSLV]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [DontCare]
 * [resp2Node]  |  [hasCHIChnl]             |  from: [SLICE]  [sliceId] [mshrWay]    | to: [RNSLV]    [nodeId]  [reqBufId]
 * [req2Node]   |  [hasAddr]                |  from: [SLICE]  [sliceId] [mshrWay]    | to: [RNSLV]    [nodeId]  [DontCare]
 * [resp2Slice] |  [hasMSHRSet] [HasDBID]   |  from: [RNSLV]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [mshrWay]
 *
 *
 * RnSlave <-> Slice DB Signals:
 * [wReq]       |                           |  from: [RNSLV]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [DontCare]
 * [wResp]      |  [hasDBID]                |  from: [SLICE]  [sliceId] [DontCare]   | to: [RNSLV]    [nodeId]  [reqBufId]
 * [dataFDB]    |                           |  from: None                            | to: [RNSLV]    [nodeId]  [reqBufId]
 * [dataTDB]    |  [hasDBID]                |  from: None                            | to: [SLICE]    [sliceId] [DontCare]
 *
 * ****************************************************************************************************************************************************
 *
 * RnMaster <-> Slice Ctrl Signals:
 * [req2Slice]  |  [hasAddr]                |  from: [RNMAS]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [DontCare]
 * [resp2Node]  |  [hasCHIChnl]             |  from: [SLICE]  [sliceId] [mshrWay]    | to: [RNMAS]    [nodeId]  [reqBufId]
 * [req2Node]   |  [hasAddr]                |  from: [SLICE]  [sliceId] [mshrWay]    | to: [RNMAS]    [nodeId]  [DontCare]
 * [resp2Slice] |  [hasMSHRSet] [HasDBID]   |  from: [RNMAS]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [mshrWay]
 *
 *
 * RnMaster <-> Slice DB Signals:
 * [dbRCReq]    |  [hasMSHRSet] [hasDBID]   |  from: [RNMAS]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [mshrWay]    // When Data from DS use mshrId(Cat(Set, way))
 * [wReq]       |                           |  from: [RNMAS]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [DontCare]
 * [wResp]      |  [hasDBID]                |  from: [SLICE]  [sliceId] [DontCare]   | to: [RNMAS]    [nodeId]  [reqBufId]
 * [dataFDB]    |                           |  from: None                            | to: [RNMAS]    [nodeId]  [reqBufId]
 * [dataTDB]    |  [hasDBID]                |  from: None                            | to: [SLICE]    [sliceId] [DontCare]
 *
 * ****************************************************************************************************************************************************
 *
 * Slice <-> SnMaster Ctrl Signals:
 * [req2Node]   |  [hasAddr]                |  from: [SLICE]  [sliceId] [mshrWay]    | to: [SNMAS]    [nodeId]  [DontCare]
 * [resp2Slice] |  [hasMSHRSet] [HasDBID]   |  from: [RNSLV]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [mshrWay]
 *
 *
 * Slice <-> SnMaster DB Signals:
 * [dbRCReq]    |  [hasMSHRSet] [hasDBID]   |  from: [SNMAS]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [mshrWay]   // When Data from DS use mshrId(Cat(Set, way))
 * [wReq]       |                           |  from: [SNMAS]  [nodeId]  [reqBufId]   | to: [SLICE]    [sliceId] [DontCare]
 * [wResp]      |  [hasDBID]                |  from: [SLICE]  [sliceId] [DontCare]   | to: [RNMAS]    [nodeId]  [reqBufId]  // Unuse from
 * [dataFDB]    |                           |  from: None                            | to: [RNMAS]    [nodeId]  [reqBufId]
 * [dataTDB]    |  [hasDBID]                |  from: None                            | to: [SLICE]    [sliceId] [DontCare]
 *
 * ****************************************************************************************************************************************************
 *
 * MainPipe S4 Commit <-> DB Signals:
 * [dbRCReq]    |  [hasMSHRSet] [hasDBID]   |  from: [SLICE]  [sliceId] [DontCare]   | to: [NODE]     [nodeId]  [reqBufId] // Unuse mshrSet
 *
 *
 * MainPipe S4 Commit <-> DS Signals:
 * [dsRWReq]    |  [hasMSHRSet] [hasDBID]   |  from: [SLICE]  [sliceId] [mshrWay]    | to: [NODE]     [nodeId]  [reqBufId]
 *
 *
 * DS <-> DB Signals:
 * [dbRCReq]    |  [hasMSHRSet] [hasDBID]   |  from: [SLICE]  [sliceId] [dsId]       | to: [NODE]     [nodeId]  [reqBufId]  // Unuse mshrSet
 * [wReq]       |                           |  from: [SLICE]  [sliceId] [dsId]       | to: [SLICE]    [sliceId] [DontCare]
 * [wResp]      |  [hasDBID]                |  from: [SLICE]  [sliceId] [DontCare]   | to: [SLICE]    [sliceId] [dsId]      // Unuse from
 * [dataFDB]    |                           |  from: None                            | to: [SLICE]    [sliceId] [dsId]
 * [dataTDB]    |  [hasDBID]                |  from: None                            | to: [SLICE]    [sliceId] [mshrWay]
 *
 */


/*
 * CHI ID Map Table:
 *
 * *********************************************************** RNSLAVE ***************************************************************************
 *
 * tgtNodeID    <-> Get from Slice req
 * nodeID       <-> RnSlave
 * reqBufId     <-> ReqBuf
 * fwdNId       <-> Get from Slice req
 * fwdTxnID     <-> Get from Slice req
 *
 *
 *
 * { Read / Dataless / Atomic / CMO }   TxReq: Store {                    |  SrcID_g = SrcID     |   TxnID_g = TxnID     |                      }
 * { CompAck                        }   TxRsp: Match {                    |                      |   TxnID  == reqBufId  |                      }
 * { CompData                       }   RxDat: Send  { TgtID   = SrcID_g  |  SrcID   = nodeID    |   TxnID   = TxnID_g   |  DBID    = reqBufId  }
 * { Comp                           }   RxRsp: Send  { TgtID   = SrcID_g  |  SrcID   = nodeID    |   TxnID   = TxnID_g   |  DBID    = reqBufId  }
 *
 *
 * { Write                          }   TxReq: Store {                    |  SrcID_g = SrcID     |   TxnID_g = TxnID     |                      }
 * { WriteData                      }   TxDat: Match {                    |                      |   TxnID  == reqBufId  |                      }
 * { CompDBIDResp                   }   RxRsp: Send  { TgtID   = SrcID_g  |  SrcID   = nodeID    |   TxnID   = TxnID_g   |  DBID    = reqBufId  }
 *
 *
 * { SnoopResp                      }   TxRsp: Match {                    |                      |   TxnID  == reqBufId  |                      }
 * { SnoopRespData                  }   TxDat: Match {                    |                      |   TxnID  == reqBufId  |                      }
 * { Snoop                          }   RxSnp: Send  { TgtID  = tgtNodeID |  SrcID   = nodeID    |   TxnID   = reqBufId  |                      }
 *
 *
 * { SnpRespFwded                   }   TxRsp: Match {                    |                      |   TxnID  == reqBufId  |                      }
 * { SnpRespDataFwded               }   TxDat: Match {                    |                      |   TxnID  == reqBufId  |                      }
 * { SnoopFwd                       }   RxSnp: Send  {                    |  SrcID   = nodeID    |   TxnID   = reqBufId  |                      |   FwdNId  = fwdNId    |   FwdTxnID    = fwdTxnID }
 *
 *
 *
 * *********************************************************** RNMASTRE *************************************************************************
 *
 * tgtNodeID    <-> Get from Slice req
 * nodeID       <-> RnMaster
 * reqBufId     <-> ReqBuf
 *
 *
 * { Read / Dataless / Atomic / CMO }   TxReq: Send  { TgtID = tgtNodeID  |  SrcID   = nodeID    |   TxnID   = reqBufId  |                      }
 * { CompAck(When get CompData)     }   TxRsp: Send  { TgtID = HomeNID_g  |  SrcID   = nodeID    |   TxnID   = DBID_g    |                      }
 * { CompAck(When get Comp)         }   TxRsp: Send  { TgtID = SrcID_g    |  SrcID   = nodeID    |   TxnID   = DBID_g    |                      }
 * { CompData                       }   RxDat: M & S {                    |                      |   TxnID  == reqBufId  |  DBID_g  = DBID      |   HomeNID_g   = HomeNID   }
 * { Comp                           }   RxRsp: M & S {                    |  SrcID_g = SrcID     |   TxnID  == reqBufId  |  DBID_g  = DBID      }
 *
 *
 * { Write                          }   TxReq: Send  { TgtID = tgtNodeID  |  SrcID   = nodeID    |   TxnID   = reqBufId  |                      }
 * { WriteData                      }   TxDat: Send  { TgtID = tgtNodeID  |  SrcID   = nodeID    |   TxnID   = DBID_g    |                      }
 * { CompDBIDResp                   }   RxRsp: M & G {                    |                      |   TxnID  == reqBufId  |  DBID_g = DBID       }
 *
 *
 * { SnoopResp                      }   TxRsp: Match { TgtID = SrcID_g    |  SrcID   = nodeID    |   TxnID   = TxnID_g   |                      }
 * { SnoopRespData                  }   TxDat: Send  { TgtID = SrcID_g    |  SrcID   = nodeID    |   TxnID   = TxnID_g   |                      }
 * { Snoop                          }   RxSnp: Store {                    |  SrcID_g = SrcID     |   TxnID_g = TxnID     |                      }
 *
 *
 * { SnpRespFwded                   }   TxRsp: Match { TgtID = SrcID_g    |  SrcID   = nodeID    |   TxnID   = TxnID_g   |                      }
 * { SnpRespDataFwded               }   TxDat: Match { TgtID = SrcID_g    |  SrcID   = nodeID    |   TxnID   = TxnID_g   |                      }
 * { CompData                       }   TxDat: Match { TgtID = FwdNId_g   |  SrcID   = nodeID    |   TxnID   = FwdTxnID  |  DBID = TxnID_g      |   HomeNID     = SrcID_g   }
 * { SnoopFwd                       }   RxSnp: Store {                    |  SrcID_g = SrcID     |   TxnID_g = TxnID     |                      |   FwdNId_g    = FwdNId    |   FwdTxnID_g  = FwdTxnID }
 *
 *
 *
 * *********************************************************** SNMASTRE *************************************************************************
 *
 * reqBufId     <-> ReqBuf
 *
 * { Read                           }   TxReq: Send  { TgtID = 0          |  SrcID   = 0         |   TxnID   = reqBufId  |                      }
 * { CompData                       }   RxDat: Match {                    |                      |   TxnID  == reqBufId  |                      }
 *
 * { Write                          }   TxReq: Send  { TgtID = 0          |  SrcID   = 0         |   TxnID   = reqBufId  |                      }
 * { WriteData                      }   TxDat: Send  { TgtID = 0          |  SrcID   = 0         |   TxnID   = DBID_g    |                      }
 * { CompDBIDResp                   }   RxRsp: M & G {                    |                      |   TxnID  == reqBufId  |  DBID_g = DBID       }
 *
 */


// ------------------------------------------ IO declaration ----------------------------------------------//
    val io = IO(new Bundle {
        val rnSlvChi            = Vec(nrRnSlv, CHIBundleUpstream(chiParams))
        val rnSlvChiLinkCtrl    = Vec(nrRnSlv, Flipped(new CHILinkCtrlIO()))
        val rnMasChi            = Vec(nrRnMas, CHIBundleDownstream(chiParams))
        val rnMasChiLinkCtrl    = Vec(nrRnMas, new CHILinkCtrlIO())
        val snMasChi            = Vec(djparam.nrBank, CHIBundleDownstream(chiParams))
        val snMasChiLinkCtrl    = Vec(djparam.nrBank, new CHILinkCtrlIO())
    })

    io <> DontCare

// ------------------------------------------ Modules declaration ----------------------------------------------//
    def createRnSlv(id: Int) = { val rnSlv = Module(new RnSlave(id)); rnSlv }
    def createRnMas(id: Int) = { val rnMas = Module(new RnMaster(id)); rnMas }
    val rnSlaves    = (0 until nrRnSlv).map(i => createRnSlv(i))
    val rnMasters   = (nrRnSlv until nrRnNode).map(i => createRnMas(i))
    val rnNodes     = rnSlaves ++ rnMasters
    val xbar        = Module(new RN2SliceXbar())
    val slices      = Seq.fill(djparam.nrBank) { Module(new Slice()) }

    slices.foreach(_.io <> DontCare)
    // TODO:
    xbar.io.bankVal.foreach(_ := true.B)


// ---------------------------------------------- Connection ---------------------------------------------------//
    /*
     * Connect IO CHI
     */
    rnSlaves.map(_.chiIO.chnls).zip(io.rnSlvChi).foreach { case(a, b) => a <> b}
    rnSlaves.map(_.chiIO.linkCtrl).zip(io.rnSlvChiLinkCtrl).foreach{ case(a, b) => a <> b}

    rnMasters.map(_.chiIO.chnls).zip(io.rnMasChi).foreach { case (a, b) => a <> b }
    rnMasters.map(_.chiIO.linkCtrl).zip(io.rnMasChiLinkCtrl).foreach { case (a, b) => a <> b }


    /*
     * Connect RNs <-> Xbar
     */
    rnNodes.zipWithIndex.foreach {
        case(rn, i) =>
            // slice ctrl signals
            if(djparam.rnNodeMes(i).hasReq2Slice) {
                xbar.io.req2Slice.in(i)             <> rn.io.req2SliceOpt.get
                xbar.io.resp2Node.out(i)            <> rn.io.resp2NodeOpt.get
            } else {
                xbar.io.req2Slice.in(i)             <> DontCare
                xbar.io.resp2Node.out(i)            <> DontCare
            }
            xbar.io.req2Node.out(i)                 <> rn.io.req2Node
            xbar.io.resp2Slice.in(i)                <> rn.io.resp2Slice
            // slice DataBuffer signals
            if (djparam.rnNodeMes(i).hasDBRCReq) {
                xbar.io.dbSigs.in(i).dbRCReqOpt.get <> rn.io.dbSigs.dbRCReqOpt.get
            } else {
                xbar.io.dbSigs.in(i).dbRCReqOpt.get <> DontCare
            }
            xbar.io.dbSigs.in(i).wReq               <> rn.io.dbSigs.wReq
            xbar.io.dbSigs.in(i).wResp              <> rn.io.dbSigs.wResp
            xbar.io.dbSigs.in(i).dataFDB            <> rn.io.dbSigs.dataFDB
            xbar.io.dbSigs.in(i).dataTDB            <> rn.io.dbSigs.dataTDB
    }


    /*
     * Connect Slice <-> Xbar
     */
    slices.zipWithIndex.foreach {
        case (slice, i) =>
            // slice ctrl signals
            xbar.io.req2Slice.out(i)    <> slice.io.rnReq2Slice
            xbar.io.resp2Node.in(i)     <> slice.io.resp2RnNode
            xbar.io.req2Node.in(i)      <> slice.io.req2RnNode
            xbar.io.resp2Slice.out(i)   <> slice.io.rnResp2Slice
            // slice DataBuffer signals
            xbar.io.dbSigs.out(i)       <> slice.io.rnDBSigs
    }

}
