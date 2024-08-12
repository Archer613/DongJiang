package DONGJIANG.SLICE

import DONGJIANG._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import xs.utils._

class Slice()(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    val valid           = Output(Bool())
    val sliceId         = Input(UInt(bankBits.W))
    // slice ctrl signals: RnNode <> Slice
    val rnReq2Slice     = Flipped(Decoupled(new Req2SliceBundle()))
    val resp2RnNode     = Decoupled(new Resp2NodeBundle())
    val req2RnNode      = Decoupled(new Req2NodeBundle())
    val rnResp2Slice    = Flipped(Decoupled(new Resp2SliceBundle()))
    // slice ctrl signals: RnNode <> Slice
    val req2SnNode      = Decoupled(new Req2NodeBundle())
    val snResp2Slice    = Flipped(Decoupled(new Resp2SliceBundle()))
    // slice DataBuffer signals: RnNode <> Slice
    val rnDBSigs        = Flipped(new DBBundle(hasDBRCReq = true))
    val snDBSigs        = Flipped(new DBBundle(hasDBRCReq = true))
  })

  io <> DontCare

}