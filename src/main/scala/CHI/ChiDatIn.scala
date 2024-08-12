package DONGJIANG.CHI

import DONGJIANG._
import DONGJIANG.IdL0._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class ChiDatIn(nrReqBuf: Int, aggregateIO: Boolean)(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    val chi           = Flipped(CHIChannelIO(new CHIBundleDAT(chiParams), aggregateIO))
    val linkState     = Input(UInt(LinkStates.width.W))
    val allLcrdRetrun = Output(Bool()) // Deactive Done
    val flit          = Decoupled(new CHIBundleDAT(chiParams))
    val dataTDB       = Decoupled(new NodeTDBData())
    val reqBufDBIDVec = Vec(nrReqBuf, Flipped(Valid(new Bundle {
      val bankId      = UInt(bankBits.W)
      val dbid        = UInt(dbIdBits.W)
    })))
  })

// --------------------- Modules declaration --------------------- //
  val chiDat  = Module(new InboundFlitCtrl(gen = new CHIBundleDAT(chiParams), lcrdMax = 2, aggregateIO))


// ------------------- Reg/Wire declaration ---------------------- //
  val flit    = Wire(Decoupled(new CHIBundleDAT(chiParams)))
  val selBuf  = Wire(new Bundle { val bankId = UInt(bankBits.W); val dbid = UInt(dbIdBits.W) })
  val alreadySendFlitReg = RegInit(false.B)


// --------------------- Logic ----------------------------------- //
  /*
   * Connect chiDat
   */
  chiDat.io.linkState := io.linkState
  io.allLcrdRetrun    := chiDat.io.allLcrdRetrun
  chiDat.io.chi       <> io.chi
  chiDat.io.flit      <> flit

  /*
   * Connect io.flit
   */
  io.flit.valid       := flit.valid & !alreadySendFlitReg
  io.flit.bits        := flit.bits
  io.flit.bits.data   := DontCare

  alreadySendFlitReg  := Mux(io.dataTDB.fire, false.B, Mux(alreadySendFlitReg, alreadySendFlitReg, io.flit.fire))

  /*
   * Select reqBuf bankId and dbid
   */
  selBuf := io.reqBufDBIDVec(flit.bits.txnID(rnReqBufIdBits - 1, 0)).bits

  /*
   * Connec dataTDB
   */
  flit.ready                := io.dataTDB.ready
  io.dataTDB.valid          := io.flit.valid
  io.dataTDB.bits.data      := io.flit.bits.data
  io.dataTDB.bits.dataID    := io.flit.bits.dataID
  io.dataTDB.bits.dbid      := selBuf.dbid
  io.dataTDB.bits.to.idL0   := IdL0.SLICE
  io.dataTDB.bits.to.idL1   := selBuf.bankId
  io.dataTDB.bits.to.idL2   := DontCare


// --------------------- Assertion ------------------------------- //
  assert(!io.flit.valid | io.flit.ready)
  assert(!io.flit.valid | io.reqBufDBIDVec(flit.bits.txnID(rnReqBufIdBits - 1, 0)).valid)

}