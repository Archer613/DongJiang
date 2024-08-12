package DONGJIANG

import DONGJIANG._
import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import xs.utils._
import Utils.FastArb._
import Utils.IDConnector._

abstract class NodeBase(hasReq2Slice: Boolean = false, hasDBRCReq: Boolean = false)(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    // slice ctrl signals
    val req2SliceOpt  = if(hasReq2Slice) Some(Decoupled(new Req2SliceBundle())) else None
    val resp2NodeOpt  = if(hasReq2Slice) Some(Flipped(Decoupled(new Resp2NodeBundle()))) else None
    val req2Node      = Flipped(Decoupled(new Req2NodeBundle()))
    val resp2Slice    = Decoupled(new Resp2SliceBundle())
    // slice DataBuffer signals
    val dbSigs        = new DBBundle(hasDBRCReq)
  })
}