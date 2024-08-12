package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._

class DSUChiRxDat(snId: Int) (implicit p : Parameters) extends DJModule {
  val nodeParam                = djparam.snNodeMes(snId)
 // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val chi                  = CHIChannelIO(new CHIBundleDAT(chiParams))
      val rxState              = Input(UInt(LinkStates.width.W))
      val flit                 = Flipped(Decoupled(new CHIBundleDAT(chiParams)))
    })
 // -------------------------- Wire/Reg define -----------------------------//
  val lcrdFreeNumReg           = RegInit(0.U((log2Ceil(nodeParam.nrSnRxLcrdMax)+1).W))
  val flitReg                  = RegInit(0.U.asTypeOf(new CHIBundleDAT(chiParams)))
  val flitvReg                 = RegInit(false.B)
  val flit                     = WireInit(0.U.asTypeOf(new CHIBundleDAT(chiParams)))
  val flitv                    = WireInit(false.B)
  val taskReady                = WireInit(false.B)

 // -------------------------- Logic -----------------------------//
  flitv                       := io.flit.fire
  flit                        := io.flit.bits

  flitvReg                    := flitv
  flitReg                     := Mux(flitv, flit, flitReg)
  

  switch(io.rxState) {
    is(LinkStates.STOP) {
      // Nothing to do
    }
    is(LinkStates.ACTIVATE) {
      lcrdFreeNumReg         := lcrdFreeNumReg + io.chi.lcrdv.asUInt
    }
    is(LinkStates.RUN) {
      lcrdFreeNumReg         := lcrdFreeNumReg + io.chi.lcrdv.asUInt - flitv
      taskReady              := lcrdFreeNumReg > 0.U
    }
    is(LinkStates.DEACTIVATE) {
      // when(lcrdFreeNumReg > 0.U){
      //   flitv                := true.B
      //   flitReg              := 0.U.asTypeOf(io.flit.bits)
      //   lcrdFreeNumReg       := 0.U
      // }
    }
  }

  io.flit.ready              := taskReady 

  /*
   * Output chi flit
   */
  io.chi.flitpend            := flitv
  io.chi.flitv               := flitvReg
  io.chi.flit                := flitReg
}