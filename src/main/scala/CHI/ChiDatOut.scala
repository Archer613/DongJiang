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
  val rxDat   = Module(new OutboundFlitCtrl(gen = new CHIBundleDAT(chiParams), lcrdMax = lcrdMax, aggregateIO))


// ------------------- Reg/Wire declaration ---------------------- //
  val flit    = Wire(Decoupled(new CHIBundleDAT(chiParams)))


// --------------------- Logic ----------------------------------- //
  /*
   * Connect txDat
   */
  rxDat.io.linkState        := io.linkState
  rxDat.io.chi              <> io.chi
  rxDat.io.flit             <> flit
  rxDat.io.flit.bits.data   := io.dataFDB.bits.data
  rxDat.io.flit.bits.dataID := io.dataFDB.bits.dataID
  rxDat.io.flit.bits.be     := Fill(rxDat.flit.be.getWidth, 1.U(1.W))

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
  io.dataFDBVal.valid := io.dataFDB.valid
  io.dataFDBVal.bits.to := io.dataFDB.bits.to



// --------------------- Assertion ------------------------------- //
  assert(!io.flit.valid | io.flit.ready)
}