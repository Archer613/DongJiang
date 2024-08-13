package DONGJIANG.SNMASTER

import DONGJIANG. _
import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import Utils.FastArb._
import Utils.IDConnector.idSelDec2DecVec

class SnMasReqBufWrapper(snMasId: Int)(implicit p: Parameters) extends DJModule {
  val nodeParam = djparam.snNodeMes(snMasId)

// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    // CHI
    val chi           = CHIBundleDecoupled(chiParams)
    // slice ctrl signals
    val req2Node      = Flipped(Decoupled(new Req2NodeBundle()))
    val resp2Slice    = Decoupled(new Resp2SliceBundle())
    // For txDat and rxDat sinasl
    val reqBufDBIDVec = Vec(nodeParam.nrReqBuf, Valid(new Bundle {
      val bankId      = UInt(bankBits.W)
      val dbid        = UInt(dbIdBits.W)
    }))
    // slice DataBuffer signals
    val dbRCReq       = Decoupled(new DBRCReq())
    val wReq          = Decoupled(new DBWReq())
    val wResp         = Flipped(Decoupled(new DBWResp()))
    val dataFDBVal    = Flipped(Valid(new ToIDBundle))
  })


// --------------------- Modules declaration ------------------------//
  def createReqBuf(id: Int) = { val reqBuf = Module(new SnMasReqBuf(snMasId, id)); reqBuf }
  val reqBufs               = (0 until nodeParam.nrReqBuf).map(i => createReqBuf(i))

// --------------------- Wire declaration ------------------------//
  val reqSelId              = Wire(UInt(rnReqBufIdBits.W))
  val canReceive            = Wire(Bool())

// ------------------------ Connection ---------------------------//
  /*
   * Connect Unuse CHI Channels
   */
  io.chi.txrsp <> DontCare
  io.chi.rxsnp <> DontCare


  /*
   * ReqBuf Select
   */
  reqSelId    := PriorityEncoder(reqBufs.map(_.io.free))
  canReceive  := reqBufs.map(_.io.free).reduce(_ | _)

  /*
   * connect io.chi.rx <-> reqBufs.chi.rx
   */
  reqBufs.map(_.io.chi).zipWithIndex.foreach {
    case(reqBuf, i) =>
      reqBuf.rxsnp        <> DontCare
      // rxrsp
      reqBuf.rxrsp.valid  := io.chi.rxrsp.valid & io.chi.rxrsp.bits.txnID === i.U
      reqBuf.rxrsp.bits   := io.chi.rxrsp.bits
      // rxdat
      reqBuf.rxdat.valid  := io.chi.rxdat.valid & io.chi.rxdat.bits.txnID === i.U
      reqBuf.rxdat.bits   := io.chi.rxdat.bits
  }

  // Set io.chi.rx_xxx.ready value
  io.chi.rxrsp.ready  := true.B
  io.chi.rxdat.ready  := true.B


  /*
   * connect io.chi.tx <-> reqBufs.chi.tx
   */
  reqBufs.map(_.io.chi.txrsp <> DontCare)
  fastArbDec2Dec(reqBufs.map(_.io.chi.txreq), io.chi.txreq)
  fastArbDec2Dec(reqBufs.map(_.io.chi.txdat), io.chi.txdat)


  /*
   * Set reqBufDBIDVec value
   */
  io.reqBufDBIDVec := reqBufs.map(_.io.reqBufDBID)


  /*
   * Connect slice DataBuffer signals
   */
  fastArbDec2Dec(reqBufs.map(_.io.dbRCReq), io.dbRCReq)
  fastArbDec2Dec(reqBufs.map(_.io.wReq), io.wReq)
  idSelDec2DecVec(io.wResp, reqBufs.map(_.io.wResp), level = 2)
  reqBufs.zipWithIndex.foreach{ case(reqBuf, i) => reqBuf.io.dataFDBVal := io.dataFDBVal.valid & io.dataFDBVal.bits.to.idL2 === i.U }

  /*
   * Connect Slice Ctrl Signals
   */
  fastArbDec2Dec(reqBufs.map(_.io.resp2Slice), io.resp2Slice)
  reqBufs.zipWithIndex.foreach {
    case (reqBuf, i) =>
      reqBuf.io.req2Node.valid := io.req2Node.valid & reqSelId === i.U & canReceive
      reqBuf.io.req2Node.bits  := io.req2Node.bits
  }
  io.req2Node.ready := canReceive



// --------------------- Assertion ------------------------------- //
  if(nodeParam.addressIdBits > 0) {
    assert(Mux(io.req2Node.valid, io.req2Node.bits.addr(addressBits - 1, addressBits - nodeParam.addressIdBits) === nodeParam.addressId.U, true.B))
  }

  assert(Mux(io.chi.rxrsp.valid, PopCount(reqBufs.map(_.io.chi.rxrsp.fire)) === 1.U, true.B))
  assert(Mux(io.chi.rxdat.valid, PopCount(reqBufs.map(_.io.chi.rxdat.fire)) === 1.U, true.B))

}