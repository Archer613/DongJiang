package DONGJIANG.CHI

import DONGJIANG. _
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._

class ChiDatOut(lcrdMax: Int, aggregateIO: Boolean)(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    val chi           = CHIChannelIO(new CHIBundleDAT(chiParams), aggregateIO)
    val linkState     = Input(UInt(LinkStates.width.W))
    val flit          = Flipped(Decoupled(new CHIBundleDAT(chiParams)))
    val dataFDB       = Flipped(Decoupled(new NodeFDBData))
    val dataFDBVal    = Valid(new ToIDBundle)
  })

// --------------------- Modules declaration --------------------- //
  val chiDat  = Module(new OutboundFlitCtrl(gen = new CHIBundleDAT(chiParams), lcrdMax = lcrdMax, aggregateIO))


// ------------------- Reg/Wire declaration ---------------------- //
  val flit    = Wire(Decoupled(new CHIBundleDAT(chiParams)))
  val alreadySendFDBValReg = RegInit(false.B)

// --------------------- Logic ----------------------------------- //
  /*
   * Connect txDat
   */
  chiDat.io.linkState         := io.linkState
  chiDat.io.chi               <> io.chi
  chiDat.io.flit              <> flit
  chiDat.io.flit.bits.data    := io.dataFDB.bits.data
  chiDat.io.flit.bits.dataID  := io.dataFDB.bits.dataID
  chiDat.io.flit.bits.be      := Fill(chiDat.flit.be.getWidth, 1.U(1.W))

  /*
   * Set flit value
   */
  io.flit <> flit

  /*
   * Set dataFDB ready
   */
  io.dataFDB.ready := io.flit.fire

  /*
   * Set dataFDBVal Value
   */
  io.dataFDBVal.valid   := io.dataFDB.valid & !alreadySendFDBValReg
  io.dataFDBVal.bits.to := io.dataFDB.bits.to

  alreadySendFDBValReg  := Mux(flit.fire, false.B, Mux(alreadySendFDBValReg, alreadySendFDBValReg, io.dataFDBVal.valid))

// --------------------- Assertion ------------------------------- //
  assert(!io.flit.valid | io.flit.ready)
}