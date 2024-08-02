package DONGJIANG

import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import scala.collection.immutable.ListMap
import scala.math.{max, min}

// -------------------------------------------------------------- Use In Decode ------------------------------------------------------------------------ //

class OperationsBundle extends Bundle {
    val Snoop       = Bool()
    val ReadDown    = Bool()
    val ReadDB      = Bool()
    val ReadDS      = Bool()
    val WriteDS     = Bool()
    val WSDir       = Bool()
    val WCDir       = Bool()
    val Atomic      = Bool()
    val WriteBack   = Bool()
}


object TaskType {
    val width       = 9
    val Snoop       = "b0_0000_0001".U
    val ReadDown    = "b0_0000_0010".U
    val ReadDB      = "b0_0000_0100".U
    val ReadDS      = "b0_0000_1000".U
    val WriteDS     = "b0_0001_0000".U
    val WSDir       = "b0_0010_0000".U
    val WCDir       = "b0_0100_0000".U
    val WriteBack   = "b0_1000_0000".U
    val Commit      = "b1_0000_0000".U
}

object RespType {
    val width = 1
    val TpyeSnoop       = "b0".U
    val TpyeReadDown    = "b1".U
}

// ----------------------------------------------------------------------------------------------------------------------------------------------- //

object IdL0 {
    val width      = 3
    val SLICE      = "b000".U
    val RNSLV      = "b001".U
    val RNMAS      = "b010".U
    val SNMAS      = "b011".U
    val CMO        = "b100".U
    val AXI        = "b101".U
}

class IDBundle(implicit p: Parameters) extends DJBundle {
    val idL0 = UInt(IdL0.width.W) // Module: IDL0 [3.W]
    val idL1 = UInt(max(coreIdBits, bankBits).W) // SubModule: RnSlaves, Slices [max:2.W]
    val idL2 = UInt(max(reqBufIdBits, max(snoopCtlIdBits, replTxnidBits)).W) // SubSubModule: ReqBufs, SnpCtls, blockSets + blockWays [max:7.W]

    def isSLICE  = idL0 === IdL0.SLICE
    def isRNSLV  = idL0 === IdL0.RNSLV
    def isRNMAS  = idL0 === IdL0.RNMAS
    def isSNMAS  = idL0 === IdL0.SNMAS
    def isCMO    = idL0 === IdL0.CMO
    def isAXI    = idL0 === IdL0.AXI
}

trait HasFromIDBits extends DJBundle { this: Bundle => val from = new IDBundle() }

trait HasToIDBits extends DJBundle { this: Bundle => val to = new IDBundle() }

trait HasIDBits extends DJBundle with HasFromIDBits with HasToIDBits

trait HasDBID extends DJBundle { this: Bundle => val dbid = UInt(dbIdBits.W) }

class TaskBundle(implicit p: Parameters) extends DJBundle with HasIDBits with HasDBID {
    // constants related to CHI
    def tgtID       = 0.U(chiBundleParams.nodeIdBits.W)
    def srcID       = 0.U(chiBundleParams.nodeIdBits.W)
    // value in use
    val opcode      = UInt(6.W)
    val addr        = UInt(addressBits.W)
    val resp        = UInt(3.W) // snpResp or wbReq; resp.width = 3
    val isWB        = Bool() // write back
    val isSnpHlp    = Bool() // req is from snoop helper
    val writeBt     = Bool() // write block table in ReqArb
    val readDir     = Bool()
    val btWay       = UInt(blockWayBits.W) // block table
    val willSnp     = Bool()
    val snpHasData  = Bool()
}

class RespBundle(implicit p: Parameters) extends DJBundle with HasToIDBits with HasCHIChannel{
    val opcode      = UInt(6.W)
    val resp        = UInt(3.W)
    val addr        = UInt(addressBits.W) // TODO: Del it
    val btWay       = UInt(blockWayBits.W) // block table
    val cleanBt     = Bool()
}

trait HasSnpTask extends DJBundle { this: Bundle =>
    val opcode          = UInt(6.W)
    val addr            = UInt(addressBits.W)
    val snpDoNotGoToSD  = Bool()
    val snpRetToSrc     = Bool()
}

class SnpTaskBundle(implicit p: Parameters) extends DJBundle with HasSnpTask with HasIDBits
class MpSnpTaskBundle(implicit p: Parameters) extends DJBundle with HasSnpTask with HasFromIDBits {
    val srcOp       = UInt(6.W)
    val hitVec      = Vec(djparam.nrCore, Bool())
    val isSnpHlp    = Bool()
    val btWay       = UInt(blockWayBits.W) // block table

}

class SnpRespBundle(implicit p: Parameters) extends DJBundle with HasIDBits with HasDBID {
    val resp    = UInt(3.W) // snpResp; resp.width = 3
    val hasData = Bool()
}

// ---------------------- DataBuffer Bundle ------------------- //
object DBState {
    val width       = 3
    // FREE -> ALLOC -> WRITING -> WRITE_DONE -> FREE
    // FREE -> ALLOC -> WRITING -> WRITE_DONE -> READING(needClean) -> READ(needClean) -> FREE
    // FREE -> ALLOC -> WRITING -> WRITE_DONE -> READING(!needClean) -> READ(!needClean) -> READ_DONE -> READING(needClean) -> READ(needClean) -> FREE
    val FREE        = "b000".U
    val ALLOC       = "b001".U
    val WRITTING    = "b010".U // Has been written some beats
    val WRITE_DONE  = "b011".U // Has been written all beats
    val READ        = "b100".U // Ready to read
    val READING     = "b101".U // Already partially read
    val READ_DONE   = "b110".U // Has been read all beat
}
class DBEntry(implicit p: Parameters) extends DJBundle with HasToIDBits {
    val state       = UInt(DBState.width.W)
    val beatVals    = Vec(nrBeat, Bool())
    val beatRNum    = UInt(log2Ceil(nrBeat).W)
    val needClean   = Bool()
    val beats       = Vec(nrBeat, UInt(beatBits.W))

    def getBeat     = beats(beatRNum)
    def toDataID: UInt = {
        if (nrBeat == 1) { 0.U }
        else if (nrBeat == 2) { Mux(beatRNum === 0.U, "b00".U, "b10".U) }
        else { beatRNum }
    }
}
trait HasDBRCOp extends DJBundle { this: Bundle =>
    val isRead = Bool()
    val isClean = Bool()
}
// Base Data Bundle
trait HasDBData extends DJBundle { this: Bundle =>
    val data = UInt(beatBits.W)
    val dataID = UInt(2.W)
    def beatNum: UInt = {
        if (nrBeat == 1) { 0.U }
        else if (nrBeat == 2) { Mux(dataID === 0.U, 0.U, 1.U) }
        else { dataID }
    }
    def isLast: Bool = beatNum === (nrBeat - 1).U
}
// DataBuffer Read/Clean Req
class DBRCReq(implicit p: Parameters) extends DJBundle with HasDBRCOp with HasDBID with HasToIDBits

// RN Bundle
class RnDBWReq(implicit p: Parameters) extends DJBundle with HasIDBits                                  // RN   ---[wReq] ---> DB
class RnDBWResp(implicit p: Parameters) extends DJBundle with HasIDBits with HasDBID                    // DB   ---[wResp] --> RN
class RnDBOutData(implicit p: Parameters) extends DJBundle with HasDBData with HasToIDBits              // DB   ---[Data] ---> RN
class RnDBInData(implicit p: Parameters) extends DJBundle with HasDBData with HasToIDBits with HasDBID  // RN   ---[Data] ---> DB

class RnDBBundle(implicit p: Parameters) extends DJBundle {
    val wReq        = Decoupled(new RnDBWReq)                      // from[RN][coreID][reqBufID];      to[SLICE][sliceId][dontCare];
    val wResp       = Flipped(Decoupled(new RnDBWResp))            // from[SLICE][sliceId][dontCare];  to[RN][coreID][reqBufID];      hasDBID
    val dataFDB     = Flipped(Decoupled(new RnDBOutData))          // from[None];                      to[RN][coreID][reqBufID];
    val dataTDB     = Decoupled(new RnDBInData)                    // from[None];                      to[SLICE][sliceId][dontCare];   hasDBID
}

// MASTER Bundle
class MsDBWReq(implicit p: Parameters) extends DJBundle                                        // MS  ---[wReq] ---> DB
class MsDBWResp(implicit p: Parameters) extends DJBundle with HasDBID                          // DB  ---[wResp] --> MS
class MsDBOutData(implicit p: Parameters) extends DJBundle with HasDBData with HasToIDBits     // DB  ---[Data] ---> MS
class MsDBInData(implicit p: Parameters) extends DJBundle with HasDBData with HasDBID          // MS  ---[Data] ---> DB

class MsDBBundle(implicit p: Parameters) extends DJBundle {
    val wReq        = Decoupled(new MsDBWReq)                        // from[None];  to[None];
    val wResp       = Flipped(Decoupled(new MsDBWResp))              // from[None];  to[None];                          hasDBID
    val dataFDB     = Flipped(Decoupled(new MsDBOutData))            // from[None];  to[MASTER][dontCare][replTxnid];
    val dataTDB     = Decoupled(new MsDBInData)                      // from[None];  to[None];                          hasDBID
}

// DS Bundle
class DsDBWReq(implicit p: Parameters) extends DJBundle                                                // DS  ---[wReq] ---> DB
class DsDBWResp(implicit p: Parameters) extends DJBundle with HasDBID                                  // DB  ---[wResp] --> DS
class DsDBOutData(implicit p: Parameters) extends DJBundle with HasDBData with HasDBID                 // DB  ---[Data] ---> DS
class DsDBInData(implicit p: Parameters) extends DJBundle with HasDBData with HasDBID                  // DS  ---[Data] ---> DB

class DsDBBundle(beat: Int = 1)(implicit p: Parameters) extends DJBundle {
    val wReq        = Decoupled(new DsDBWReq)                        // from[None];  to[None];
    val wResp       = Flipped(Decoupled(new DsDBWResp))              // from[None];  to[None];   hasDBID
    val dataFDB     = Flipped(Decoupled(new DsDBOutData))            // from[None];  to[None];   hasDBID
    val dataTDB     = Decoupled(new DsDBInData)                      // from[None];  to[None];   hasDBID // TODO: Add to[RN/MS], omit read requests
}


// ---------------------- ReqBuf Bundle ------------------- //
class RBFSMState(implicit p: Parameters) extends Bundle {
    // schedule
    val s_snp       = Bool()
    val s_snpResp   = Bool()
    val s_req2mp    = Bool() // expect write back req
    val s_wbReq2mp  = Bool()
    val s_resp      = Bool()
    val s_clean     = Bool()
    val s_getDBID   = Bool()
    val s_dbidResp  = Bool()

    // wait
    val w_snpResp   = Bool()
    val w_mpResp    = Bool()
    val w_dbid      = Bool()
    val w_dbData    = Bool()
    val w_rnData    = Bool()
    val w_compAck   = Bool()
}


// --------------------- ReqArb Bundle ------------------- //
class BlockTableEntry(implicit p: Parameters) extends DJBundle {
    val valid   = Bool()
    val tag     = UInt(blockTagBits.W)
    val bank    = UInt(bankBits.W)
    // TODO: block by way full
}

class WCBTBundle(implicit p: Parameters) extends DJBundle with HasToIDBits {
    val addr    = UInt(addressBits.W)
    val btWay   = UInt(blockWayBits.W) // block table
    val isClean = Bool()
}


// -------------------- ReadCtl Bundle ------------------ //
object RCState { // Read Ctl State
    val width      = 3
    val nrState    = 6
    val FREE       = "b000".U
    val GET_ID     = "b001".U
    val WAIT_ID    = "b010".U
    val SEND_REQ   = "b011".U
    val WAIT_RESP  = "b100".U
    val SEND_RESP  = "b101".U
}

class ReadCtlTableEntry(implicit p: Parameters) extends DJBundle with HasFromIDBits {
    val opcode  = UInt(6.W)
    val state   = UInt(RCState.width.W)
    val txnid   = UInt(8.W)
    val addr    = UInt(addressBits.W)
    val btWay   = UInt(blockWayBits.W) // block table
}


// ------------------- DSReqEntry -------------------- //
// TODO: Can it get id in advance
// State Read DB: FREE -> RC_DB2DS -> WRITE_DS -> FREE
// State Write DB: FREE -> GET_ID -> READ_DS -> WRITE_DB -> RC_DB2OTH -> FREE
// State Read & Write DB: FREE -> GET_ID -> READ_DS -> WRITE_DB -> RC_DB2OTH -> RC_DB2DS -> WRITE_DS -> FREE
object DSState {
    val width       = 3
    val nrState     = 7
    val FREE        = "b000".U
    val GET_ID      = "b001".U
    val READ_DS     = "b010".U
    val WRITE_DB    = "b011".U
    val RC_DB2OTH   = "b100".U
    val RC_DB2DS    = "b101".U
    val WRITE_DS    = "b110".U
}

class DSReqEntry(implicit p: Parameters) extends DJBundle with HasToIDBits {
    val set     = UInt(dsSetBits.W)
    val bank    = UInt(dsBankBits.W)
    val wayOH   = UInt(djparam.ways.W)
    val state   = UInt(DSState.width.W)
    val ren     = Bool() // read DS
    val wen     = Bool() // write DS
    val rDBID   = UInt(dbIdBits.W) // read DataBuffer DBID
    val wDBID   = UInt(dbIdBits.W) // write DataBuffer DBID
    val rBeatNum = UInt(log2Ceil(nrBeat).W) // read DS beat num
    val sBeatNum = UInt(log2Ceil(nrBeat).W) // send to DB beat num
}

// ------------------- Nest Mes -------------------- //
class NestOutMes(implicit p: Parameters) extends DJBundle {
    val nestAddr = UInt((addressBits - offsetBits).W)
}

class NestInMes(implicit p: Parameters) extends DJBundle {
    val block = Bool()
}


