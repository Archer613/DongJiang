package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._


class RspGen(snId: Int) (implicit p : Parameters) extends DJModule {
  val nodeParam                = djparam.snNodeMes(snId)
 // -------------------------- IO declaration -----------------------------//
  val io = IO(new Bundle {
    val reqFlitIn            = Flipped(Decoupled(new CHIBundleREQ(chiParams)))
    val rspFlitOut           = Decoupled(new CHIBundleRSP(chiParams))
    val bufNoEmpty           = Output(Bool())
    val fsmFull              = Input(Bool())
    val datQueueFull         = Input(Bool())
    val rspQueueFull         = Output(Bool())
    val dataRegEnq           = Input(Bool())
  })

// -------------------------- Module define -----------------------------//
  val queue                  = Module(new Queue(new CHIBundleRSP(chiParams), entries = nodeParam.nrSnRxLcrdMax, pipe = true, flow = false, hasFlush = false))


// -------------------------- Wire/Reg define -----------------------------//
  val rspFlit                = WireInit(0.U.asTypeOf(io.rspFlitOut.bits))
  val writeReq               = Wire(Bool())
  val readReqOrder           = Wire(Bool())
  val queueFull              = Wire(Bool())
  val queueEmpty             = Wire(Bool())


// ------------------------------- Logic --------------------------------//
  writeReq                  := io.reqFlitIn.bits.opcode === REQ.WriteNoSnpFull & io.reqFlitIn.fire
  readReqOrder              := io.reqFlitIn.bits.opcode === REQ.ReadNoSnp      & io.reqFlitIn.fire & io.reqFlitIn.bits.order =/= 0.U
  
  rspFlit.opcode            := Mux(writeReq, RSP.CompDBIDResp, Mux(readReqOrder, RSP.ReadReceipt, 0.U))
  rspFlit.txnID             := io.reqFlitIn.bits.txnID
  rspFlit.dbID              := io.reqFlitIn.bits.txnID
  rspFlit.srcID             := io.reqFlitIn.bits.tgtID
  rspFlit.tgtID             := io.reqFlitIn.bits.srcID

  queue.io.enq.bits         := Mux(io.reqFlitIn.fire, rspFlit, 0.U.asTypeOf(rspFlit))
  queue.io.enq.valid        := writeReq || readReqOrder
  queue.io.deq.ready        := io.rspFlitOut.ready

  queueFull                 := queue.io.count === nodeParam.nrSnRxLcrdMax.U
  queueEmpty                := queue.io.count === 0.U

  
 
  

/* 
 * Output logic
 */
  io.reqFlitIn.ready        := !queueFull & !io.fsmFull & !io.datQueueFull & !io.dataRegEnq
  io.rspFlitOut.valid       := !queueEmpty
  io.rspFlitOut.bits        := queue.io.deq.bits
  io.bufNoEmpty             := !queueEmpty
  io.rspQueueFull           := queueFull

}