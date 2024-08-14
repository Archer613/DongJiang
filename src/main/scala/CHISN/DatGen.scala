package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._
import DONGJIANG.SNMASTER._

class DatGen(snId: Int) (implicit p : Parameters) extends DJModule {
  val nodeParam            = djparam.snNodeMes(snId)
  // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val readReqFlit      = Flipped(Decoupled(new CHIBundleREQ(chiParams)))
      val dataFlit         = Decoupled(new CHIBundleDAT(chiParams))
      
      val readCmemAddr     = Output(UInt(64.W))
      val readCmemEn       = Output(Bool())
      val cmemRdRsp        = Input(UInt(64.W))

      val bufNoEmpty       = Output(Bool())
      val fsmFull          = Input(Bool())
      val datQueueFull     = Output(Bool())
      val rspQueueFull     = Input(Bool())
      val regEnq           = Output(Bool())
    })
 // --------------------------- Module define ------------------------------//
  val queue                = Module(new Queue(new CHIBundleDAT(chiParams), entries = nodeParam.nrSnRxLcrdMax, pipe = true, flow = false, hasFlush = false))

 // -------------------------- Wire/Reg define ----------------------------//
  val readAddr             = WireInit(0.U(64.W))
  val respData             = WireInit(0.U.asTypeOf(io.cmemRdRsp))


  val enqValidReg          = RegNext(io.readReqFlit.fire  & io.readReqFlit.bits.opcode === REQ.ReadNoSnp)

  val dataFlitEnq          = WireInit(0.U.asTypeOf(io.dataFlit.bits))
  val dataFlitEnqReg       = RegInit(0.U.asTypeOf(io.dataFlit.bits))

  val queueFull            = Wire(Bool())
  val queueEmpty           = Wire(Bool())



 // ------------------------------ Logic ---------------------------------//

  readAddr                := io.readReqFlit.bits.addr
  respData                := io.cmemRdRsp

  queueFull               := queue.io.count === nodeParam.nrSnRxLcrdMax.U
  queueEmpty              := queue.io.count === 0.U
/* 
 * Queue enq and deq logic
 */
  queue.io.deq.ready      := io.dataFlit.ready
  queue.io.enq.valid      := io.readReqFlit.valid & io.readReqFlit.bits.opcode === REQ.ReadNoSnp || enqValidReg

  queue.io.enq.bits       := Mux(io.readReqFlit.fire, dataFlitEnq, Mux(enqValidReg, dataFlitEnqReg, 0.U.asTypeOf(dataFlitEnqReg)))


  // dataFlitEnq.tgtID       := dsuparam.idmap.HNID.U
  // dataFlitEnq.srcID       := dsuparam.idmap.SNID.U
  dataFlitEnq.data        := respData(63, 32)
  dataFlitEnq.dataID      := 0.U
  dataFlitEnq.opcode      := DAT.CompData
  dataFlitEnq.txnID       := io.readReqFlit.bits.txnID

  // dataFlitEnqReg.tgtID    := dsuparam.idmap.HNID.U
  // dataFlitEnqReg.srcID    := dsuparam.idmap.SNID.U
  dataFlitEnqReg.data     := respData(31, 0)
  dataFlitEnqReg.dataID   := 2.U
  dataFlitEnqReg.opcode   := DAT.CompData
  dataFlitEnqReg.txnID    := io.readReqFlit.bits.txnID

  




/* 
 * Output logic
 */

  io.readCmemAddr         := readAddr
  io.readCmemEn           := io.readReqFlit.fire
  io.dataFlit.valid       := !queueEmpty
  io.dataFlit.bits        := Mux(queue.io.deq.fire, queue.io.deq.bits, 0.U.asTypeOf(io.dataFlit.bits))
  io.readReqFlit.ready    := !enqValidReg & !queueFull & !io.fsmFull & !io.rspQueueFull
  io.datQueueFull         := queueFull
  io.bufNoEmpty           := !queueEmpty
  io.regEnq               := enqValidReg
}
