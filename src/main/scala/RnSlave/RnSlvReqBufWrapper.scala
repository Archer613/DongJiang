package DONGJIANG.RNSLAVE

import DONGJIANG. _
import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import Utils.FastArb._
import Utils.IDConnector.idSelDec2DecVec

class ReqBufSelector(nrRnReqBuf: Int = 8)(implicit p: Parameters) extends DJModule {
  val io = IO(new Bundle() {
    val idle = Input(Vec(nrRnReqBuf, Bool()))
    val idleNum = Output(UInt((rnReqBufIdBits+1).W))
    val out0 = UInt(rnReqBufIdBits.W)
    val out1 = UInt(rnReqBufIdBits.W)
  })
  io.idleNum := PopCount(io.idle)
  io.out0 := PriorityEncoder(io.idle)
  val idle1 = WireInit(io.idle)
  idle1(io.out0) := false.B
  io.out1 := PriorityEncoder(idle1)
}


class RnSlvReqBufWrapper(rnSlvId: Int)(implicit p: Parameters) extends DJModule {
  val nodeParam = djparam.rnNodeMes(rnSlvId)

// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    // CHI
    val chi           = Flipped(CHIBundleDecoupled(chiParams))
    // slice ctrl signals
    val req2Slice     = Decoupled(new Req2SliceBundle())
    val resp2Node     = Flipped(Decoupled(new Resp2NodeBundle()))
    val req2Node      = Flipped(Decoupled(new Req2NodeBundle()))
    val resp2Slice    = Decoupled(new Resp2SliceBundle())
    // For txDat and rxDat sinasl
    val reqBufDBIDVec = Vec(nodeParam.nrReqBuf, Valid(new Bundle {
      val bankId      = UInt(bankBits.W)
      val dbid        = UInt(dbIdBits.W)
    }))
    // slice DataBuffer signals
    val wReq          = Decoupled(new DBWReq())
    val wResp         = Flipped(Decoupled(new DBWResp()))
    val dataFDBVal    = Flipped(Valid(new ToIDBundle))
  })

// --------------------- Modules declaration ------------------------//
  def createReqBuf(id: Int) = { val reqBuf = Module(new RnSlvReqBuf(rnSlvId, id)); reqBuf }
  val reqBufs               = (0 until nodeParam.nrReqBuf).map(i => createReqBuf(i))
  val reqSel                = Module(new ReqBufSelector(nodeParam.nrReqBuf))
//  val nestCtl         = Module(new NestCtl()) // TODO: Nest Ctrl

// --------------------- Wire declaration ------------------------//
  val reqSelId0   = Wire(UInt(rnReqBufIdBits.W)) // Priority
  val reqSelId1   = Wire(UInt(rnReqBufIdBits.W))

  val canReceive0 = Wire(Bool())
  val canReceive1 = Wire(Bool())

// ------------------------ Connection ---------------------------//
  /*
   * ReqBufSelector idle input
   */
  reqSel.io.idle  := reqBufs.map(_.io.free)
  reqSelId0       := reqSel.io.out0
  reqSelId1       := reqSel.io.out0
  canReceive0     := reqSel.io.idleNum > 0.U
  canReceive1     := reqSel.io.idleNum > 1.U



  /*
   * connect io.chi.tx <-> reqBufs.chi.tx
   */
  reqBufs.map(_.io.chi).zipWithIndex.foreach {
    case(reqBuf, i) =>
      // txreq
      reqBuf.txreq.valid  := io.chi.txreq.valid & reqSelId1 === i.U & canReceive1
      reqBuf.txreq.bits   := io.chi.txreq.bits
      // txrsp
      reqBuf.txrsp.valid  := io.chi.txrsp.valid & io.chi.txrsp.bits.txnID === i.U
      reqBuf.txrsp.bits   := io.chi.txrsp.bits
      // txdat
      reqBuf.txdat.valid  := io.chi.txdat.valid & io.chi.txrsp.bits.txnID === i.U
      reqBuf.txdat.bits   := io.chi.txdat.bits
  }

  // Set io.chi.tx_xxx.ready value
  io.chi.txreq.ready  := canReceive1
  io.chi.txrsp.ready  := true.B
  io.chi.txdat.ready  := true.B


  /*
   * connect io.chi.rx <-> reqBufs.chi.rx
   */
  fastArbDec2Dec(reqBufs.map(_.io.chi.rxsnp), io.chi.rxsnp)
  fastArbDec2Dec(reqBufs.map(_.io.chi.rxrsp), io.chi.rxrsp)
  fastArbDec2Dec(reqBufs.map(_.io.chi.rxdat), io.chi.rxdat)


  /*
   * Set reqBufDBIDVec value
   */
  io.reqBufDBIDVec := reqBufs.map(_.io.reqBufDBID)


  /*
   * Connect slice DataBuffer signals
   */
  fastArbDec2Dec(reqBufs.map(_.io.wReq), io.wReq)
  idSelDec2DecVec(io.wResp, reqBufs.map(_.io.wResp), level = 2)
  reqBufs.zipWithIndex.foreach{ case(reqBuf, i) => reqBuf.io.dataFDBVal := io.dataFDBVal.valid & io.dataFDBVal.bits.to.idL2 === i.U }

  /*
   * Connect Slice Ctrl Signals
   */
  fastArbDec2Dec(reqBufs.map(_.io.req2Slice), io.req2Slice)
  idSelDec2DecVec(io.resp2Node, reqBufs.map(_.io.resp2Node), level = 2)
  fastArbDec2Dec(reqBufs.map(_.io.resp2Slice), io.resp2Slice)
  reqBufs.zipWithIndex.foreach {
    case (reqBuf, i) =>
      reqBuf.io.req2Node.valid := io.req2Node.valid & reqSelId0 === i.U & canReceive0
      reqBuf.io.req2Node.bits  := io.req2Node.bits
  }
  io.req2Node.ready := canReceive0



// --------------------- Assertion ------------------------------- //
  assert(Mux(io.chi.txrsp.valid, PopCount(reqBufs.map(_.io.chi.txrsp.fire)) === 1.U, true.B))
  assert(Mux(io.chi.txdat.valid, PopCount(reqBufs.map(_.io.chi.txdat.fire)) === 1.U, true.B))

}