package DONGJIANG.SLICE

import DONGJIANG._
import chisel3.{UInt, _}
import chisel3.util._
import org.chipsalliance.cde.config._
import xs.utils.ParallelPriorityMux
import Utils.FastArb.fastPriorityArbDec2Val

class MSHRCtl()(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    val sliceId       = Input(UInt(bankBits.W))
    // Req and Resp To Slice
    val req2Slice     = Flipped(Decoupled(new Req2SliceBundle()))
    val resp2Slice    = Flipped(Decoupled(new Resp2SliceBundle()))
    // Task To MainPipe
    val mpTask        = Decoupled(new MpTaskBundle())
    // Update Task To MainPipe
    val udpMSHR       = Flipped(Decoupled(new UpdateMSHRBundle()))
    // Directory Read Req
    val dirRead       = Decoupled(new DirReadBundle())
    // TxReqQueue or SnpCtl Valid Number
    val mpReqValNum   = Input(UInt((mpTaskQBits + 1).W))
    val snpCtlValNum  = Input(UInt((snpCtlIdBits + 1).W))
  })

  // TODO: Delete the following code when the coding is complete
  io <> DontCare

}