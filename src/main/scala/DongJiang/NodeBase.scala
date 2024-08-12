package DONGJIANG

import DONGJIANG._
import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import xs.utils._
import Utils.FastArb._
import Utils.IDConnector._

abstract class NodeBase(hasReq2Slice: Boolean = false, hasRCReq: Boolean = false)(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    // slice ctrl signals
    val req2Slice     = Decoupled(new Req2SliceBundle())
    val resp2Node     = Flipped(Decoupled(new Resp2NodeBundle()))
    val req2NodeOpt   = if(hasReq2Slice) Some(Flipped(Decoupled(new Req2NodeBundle()))) else None
    val resp2Sliceopt = if(hasReq2Slice) Some(Decoupled(new Resp2SliceBundle())) else None
    // slice DataBuffer signals
    val dbSigs        = new DBBundle(hasRCReq)
  })
}