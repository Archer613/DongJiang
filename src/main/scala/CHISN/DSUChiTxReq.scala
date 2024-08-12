package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._

// Module definition with parameterized widths and depth
class DSUChiTxReq(snId: Int) (implicit p : Parameters) extends DJModule {
  val nodeParam              = djparam.snNodeMes(snId)
 // -------------------------- IO declaration -----------------------------//

  val io                     = IO(new Bundle {
    val chi                  = Flipped(CHIChannelIO(new CHIBundleREQ(chiParams)))
    val txState              = Input(UInt(LinkStates.width.W))
    
    //Dequeue flit
    val flit                 = Decoupled(new CHIBundleREQ(chiParams))
    val lcrdReturn           = Output(Bool())
  })

// --------------------- Modules declaration --------------------- //
  val queue                  = Module(new Queue(new CHIBundleREQ(chiParams), entries = nodeParam.nrSnTxLcrdMax, pipe = true, flow = false, hasFlush = false))

// ------------------- Reg/Wire declaration ---------------------- //
  val lcrdSendNumReg         = RegInit(0.U((log2Ceil(nodeParam.nrSnTxLcrdMax)+1).W))
  val lcrdFreeNum            = Wire(UInt((log2Ceil(nodeParam.nrSnTxLcrdMax)+1).W))
  val lcrdv                  = WireInit(false.B)
  val enq                    = WireInit(0.U.asTypeOf(io.flit))
  val inFlit                 = WireInit(0.U.asTypeOf(new CHIBundleREQ(chiParams)))
  val lcrdReturn             = WireInit(false.B)
  dontTouch(lcrdFreeNum)

// --------------------- Logic ----------------------------------- //
  // Count lcrd
  lcrdSendNumReg            := lcrdSendNumReg + io.chi.lcrdv.asUInt - io.chi.flitv.asUInt
  lcrdFreeNum               := nodeParam.nrSnTxLcrdMax.U - queue.io.count - lcrdSendNumReg

  inFlit                    := io.chi.flit


  /*
   * FSM
   */

  switch(io.txState) {
    is(LinkStates.STOP) {
      // Nothing to do
    }
    is(LinkStates.ACTIVATE) {
      // Nothing to do
    }
    is(LinkStates.RUN) {
      // Send lcrd
      lcrdv                 := lcrdFreeNum > 0.U
      // Receive txReq
      enq.valid             := RegNext(io.chi.flitpend) & io.chi.flitv
      enq.bits              := io.chi.flit
      // Return credit flit
      when(inFlit.opcode    === REQ.ReqLCrdReturn & io.chi.flitv){
        lcrdReturn          := true.B
      }
    }
    is(LinkStates.DEACTIVATE) {
      when(inFlit.opcode    === REQ.ReqLCrdReturn & io.chi.flitv){
        lcrdReturn          := true.B
      }
    }
  }

  // allLcrdRetrun
  when(lcrdReturn){
    lcrdSendNumReg          := 0.U
  }

  /*
   * Connection
   */
  // lcrdv
  io.chi.lcrdv             := lcrdv
  // enq
  queue.io.enq             <> enq
  // deq
  io.flit                  <> queue.io.deq
  
  io.lcrdReturn            := lcrdReturn


// --------------------- Assertion ------------------------------- //
  switch(io.txState) {
    is(LinkStates.STOP) {
      assert(!io.chi.flitv, "When STOP, HN cant send flit")
    }
    is(LinkStates.ACTIVATE) {
      assert(!io.chi.flitv, "When ACTIVATE, HN cant send flit")
    }
    is(LinkStates.RUN) {
      assert(Mux(queue.io.enq.valid, queue.io.enq.ready, true.B), "When flitv is true, queue must be able to receive flit")
    }
    is(LinkStates.DEACTIVATE) {
      assert(!io.chi.lcrdv,  "When DEACTIVATE, SN cant send lcrdv")
    }
  }

  assert(lcrdSendNumReg <= nodeParam.nrSnTxLcrdMax.U, "Lcrd be send cant over than nrSnTxLcrdMax")
  assert(queue.io.count <= nodeParam.nrSnTxLcrdMax.U, "queue.io.count cant over than nrSnTxLcrdMax")
  assert(lcrdFreeNum <= nodeParam.nrSnTxLcrdMax.U, "lcrd free num cant over than nrSnTxLcrdMax")
 
}