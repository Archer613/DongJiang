package DONGJIANG.RNMASTER

import DONGJIANG._
import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import xs.utils._
import Utils.FastArb._
import Utils.IDConnector._

class RnMaster(rnMasId: Int)(implicit p: Parameters) extends NodeBase(hasReq2Slice = true, hasDBRCReq = true) {
  val nodeParam = djparam.rnNodeMes(rnMasId)

// --------------------- IO declaration ------------------------//
  val chiIO = IO(new Bundle {
    // CHI
    val chnls      = CHIBundleDownstream(chiParams)
    val linkCtrl   = new CHILinkCtrlIO()
  })

  // --------------------- Modules declaration ------------------------//
  val chiCtrl = Module(new OutboundLinkCtrl())

  val txReq = Module(new OutboundFlitCtrl(gen = new CHIBundleREQ(chiParams), lcrdMax = nodeParam.nrRnTxLcrdMax, nodeParam.aggregateIO))
  val txRsp = Module(new OutboundFlitCtrl(gen = new CHIBundleRSP(chiParams), lcrdMax = nodeParam.nrRnTxLcrdMax, nodeParam.aggregateIO))
  val rxSnp = Module(new InboundFlitCtrl(gen = new CHIBundleSNP(chiParams), lcrdMax = nodeParam.nrRnRxLcrdMax, nodeParam.aggregateIO))
  val rxRsp = Module(new InboundFlitCtrl(gen = new CHIBundleRSP(chiParams), lcrdMax = nodeParam.nrRnRxLcrdMax, nodeParam.aggregateIO))

  val txDat = Module(new ChiDatOut(lcrdMax = nodeParam.nrRnRxLcrdMax, aggregateIO = nodeParam.aggregateIO))
  val rxDat = Module(new ChiDatIn(nrReqBuf = nodeParam.nrReqBuf, aggregateIO = nodeParam.aggregateIO))

  val reqBuf = Module(new RnMasReqBufWrapper(rnMasId))

  // ------------------------ Connection ---------------------------//
  chiCtrl.io.chiLinkCtrl <> chiIO.linkCtrl
  chiCtrl.io.txRun := true.B // TODO
  chiCtrl.io.txAllLcrdRetrun := rxRsp.io.allLcrdRetrun | rxRsp.io.allLcrdRetrun | rxDat.io.allLcrdRetrun

  txReq.io.linkState := chiCtrl.io.txState
  txReq.io.chi <> chiIO.chnls.txreq
  txReq.io.flit <> reqBuf.io.chi.txreq

  txRsp.io.linkState := chiCtrl.io.txState
  txRsp.io.chi <> chiIO.chnls.txrsp
  txRsp.io.flit <> reqBuf.io.chi.txrsp

  txDat.io.linkState := chiCtrl.io.txState
  txDat.io.chi <> chiIO.chnls.txdat
  txDat.io.flit <> reqBuf.io.chi.txdat
  txDat.io.dataFDB <> io.dbSigs.dataFDB
  txDat.io.dataFDBVal <> reqBuf.io.dataFDBVal

  rxSnp.io.linkState := chiCtrl.io.rxState
  rxSnp.io.chi <> chiIO.chnls.rxsnp
  rxSnp.io.flit <> reqBuf.io.chi.rxsnp

  rxRsp.io.linkState := chiCtrl.io.rxState
  rxRsp.io.chi <> chiIO.chnls.rxrsp
  rxRsp.io.flit <> reqBuf.io.chi.rxrsp

  rxDat.io.linkState := chiCtrl.io.rxState
  rxDat.io.chi <> chiIO.chnls.rxdat
  rxDat.io.flit <> reqBuf.io.chi.rxdat
  rxDat.io.dataTDB <> io.dbSigs.dataTDB
  rxDat.io.reqBufDBIDVec <> reqBuf.io.reqBufDBIDVec

  reqBuf.io.req2Slice <> io.req2SliceOpt.get
  reqBuf.io.resp2Node <> io.resp2NodeOpt.get
  reqBuf.io.req2Node <> io.req2Node
  reqBuf.io.resp2Slice <> io.resp2Slice
  reqBuf.io.wReq <> io.dbSigs.wReq
  reqBuf.io.wResp <> io.dbSigs.wResp
  reqBuf.io.dbRCReq <> io.dbSigs.dbRCReqOpt.get
}