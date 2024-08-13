package DONGJIANG

import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import scala.collection.immutable.ListMap
import scala.math.{max, min}

// -------------------------------------------------------------- Decode Bundle ------------------------------------------------------------------------ //

class OperationsBundle extends Bundle {
    val Snoop       = Bool()
    val ReadDown    = Bool()
    val ReadDB      = Bool()
    val ReadDS      = Bool()
    val WriteDS     = Bool()
    val WSDir       = Bool()
    val WCDir       = Bool()
    val Atomic      = Bool()
    val WriteBack   = Bool()
}


object TaskType {
    val width       = 9
    val Snoop       = "b0_0000_0001".U
    val ReadDown    = "b0_0000_0010".U
    val ReadDB      = "b0_0000_0100".U
    val ReadDS      = "b0_0000_1000".U
    val WriteDS     = "b0_0001_0000".U
    val WSDir       = "b0_0010_0000".U
    val WCDir       = "b0_0100_0000".U
    val WriteBack   = "b0_1000_0000".U
    val Commit      = "b1_0000_0000".U
}

object RespType {
    val width = 1
    val TpyeSnoop       = "b0".U
    val TpyeReadDown    = "b1".U
}

// ---------------------------------------------------------------- Xbar Id Bundle ----------------------------------------------------------------------------- //

object IdL0 {
    val width      = 3
    val SLICE      = "b000".U
    val RNSLV      = "b001".U
    val RNMAS      = "b010".U
    val SNMAS      = "b011".U
    val CMO        = "b100".U
    val AXI        = "b101".U
}

class IDBundle(implicit p: Parameters) extends DJBundle {
    val idL0 = UInt(IdL0.width.W) // Module: IDL0 [3.W]
    val idL1 = UInt(max(rnNodeIdBits, bankBits).W) // SubModule: RnSlave, RnMaster, Slices
    val idL2 = UInt(max(rnReqBufIdBits, max(snReqBufIdBits, mshrWayBits)).W) // SubSubModule: RnReqBufs, SnReqBufs, mshrWays

    def mshrWay  = idL2
    def reqBufId = idL2

    def isSLICE  = idL0 === IdL0.SLICE
    def isRNSLV  = idL0 === IdL0.RNSLV
    def isRNMAS  = idL0 === IdL0.RNMAS
    def isSNMAS  = idL0 === IdL0.SNMAS
    def isCMO    = idL0 === IdL0.CMO
    def isAXI    = idL0 === IdL0.AXI
}

trait HasFromIDBits extends DJBundle { this: Bundle => val from = new IDBundle() }

trait HasToIDBits extends DJBundle { this: Bundle => val to = new IDBundle() }
class ToIDBundle(implicit p: Parameters) extends DJBundle with HasToIDBits

trait HasIDBits extends DJBundle with HasFromIDBits with HasToIDBits

trait HasDBID extends DJBundle { this: Bundle => val dbid = UInt(dbIdBits.W) }

trait HasAddr extends DJBundle { this: Bundle => val addr = UInt(addressBits.W) }

trait HasMSHRSet extends DJBundle { this: Bundle => val mshrSet = UInt(mshrSetBits.W) }

trait HasMSHRWay extends DJBundle { this: Bundle => val mshrWay = UInt(mshrWayBits.W) }

// ---------------------------------------------------------------- Req To Slice Bundle ----------------------------------------------------------------------------- //
trait HasReq2SliceBundle extends DJBundle with HasAddr { this: Bundle =>
    // CHI Id(Use in RnSlave)
    val srcIDOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    val txnIDOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    // Snp Mes (Use in RnMaster)
    val isSnp       = Bool()
    val doNotGoToSD = Bool()
    val retToSrc    = Bool()
    // CHI Mes(Common)
    val opcode      = UInt(6.W)
    // Other(Common)
    val willSnp     = Bool()
}

class Req2SliceBundleWitoutXbarId(implicit p: Parameters) extends DJBundle with HasReq2SliceBundle

class Req2SliceBundle(implicit p: Parameters) extends DJBundle with HasReq2SliceBundle with HasIDBits


// ---------------------------------------------------------------- Resp To Node Bundle ----------------------------------------------------------------------------- //

trait HasResp2NodeBundle extends DJBundle with HasCHIChannel { this: Bundle =>
    // CHI Id
    val srcIDOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    val txnIDOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    // CHI Mes
    val opcode      = UInt(6.W)
    // Indicate Snoopee final state
    val resp        = UInt(ChiResp.width.W)
    // Indicate Requster final state in DCT
    val fwdStateOpt = if (djparam.useDCT) Some(UInt(ChiResp.width.W)) else None
}

class Resp2NodeBundleWitoutXbarId(implicit p: Parameters) extends DJBundle with HasResp2NodeBundle

class Resp2NodeBundle(implicit p: Parameters) extends DJBundle with HasResp2NodeBundle with HasIDBits


// ---------------------------------------------------------------- Req To Node Bundle ----------------------------------------------------------------------------- //
trait HasReq2NodeBundle extends DJBundle with HasAddr { this: Bundle =>
    // CHI Id
    val tgtIdOpt    = if (djparam.useInNoc) Some(UInt(chiParams.nodeIdBits.W)) else None
    val srcIdOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    val txnIdOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    // Snp Mes (Use in RnSlave)
    val retToSrc    = Bool()
    val doNotGoToSD = Bool()
    // CHI Mes (Common)
    val opcode      = UInt(6.W)
    // CHI Mes (Use in RnMaster)
    val resp        = UInt(ChiResp.width.W) // Use in write back
    val expCompAck  = Bool()
    val tgtID       = UInt(chiParams.nodeIdBits.W)
}

class Req2NodeBundleWitoutXbarId(implicit p: Parameters) extends DJBundle with HasReq2NodeBundle

class Req2NodeBundle(implicit p: Parameters) extends DJBundle with HasReq2NodeBundle with HasIDBits


// ---------------------------------------------------------------- Resp To Slice Bundle ----------------------------------------------------------------------------- //
trait HasResp2SliceBundle extends DJBundle with HasDBID with HasMSHRSet { this: Bundle =>
    val isSnpResp   = Bool()
    // Indicate Snoopee final state
    val resp        = UInt(ChiResp.width.W)
    // Indicate Requster final state in DCT
    val fwdStateOpt = if (djparam.useDCT) Some(UInt(ChiResp.width.W)) else None
}

class Resp2SliceBundleWitoutXbarId(implicit p: Parameters) extends DJBundle with HasResp2SliceBundle

class Resp2SliceBundle(implicit p: Parameters) extends DJBundle with HasResp2SliceBundle with HasIDBits


// ---------------------------------------------------------------- DataBuffer Base Bundle ----------------------------------------------------------------------------- //
trait HasDBRCOp extends DJBundle { this: Bundle =>
    val isRead = Bool()
    val isClean = Bool()
}
// Base Data Bundle
trait HasDBData extends DJBundle { this: Bundle =>
    val data = UInt(beatBits.W)
    val dataID = UInt(2.W)
    def beatNum: UInt = {
        if (nrBeat == 1) { 0.U }
        else if (nrBeat == 2) { Mux(dataID === 0.U, 0.U, 1.U) }
        else { dataID }
    }
    def isLast: Bool = beatNum === (nrBeat - 1).U
}

// DataBuffer Read/Clean Req
class DBRCReq(implicit p: Parameters)       extends DJBundle with HasDBRCOp with HasMSHRSet with HasDBID with HasIDBits { val useDBID = Bool()}
class DBWReq(implicit p: Parameters)        extends DJBundle                                             with HasIDBits
class DBWResp(implicit p: Parameters)       extends DJBundle                                with HasDBID with HasIDBits
class NodeFDBData(implicit p: Parameters)   extends DJBundle with HasDBData                              with HasToIDBits
class NodeTDBData(implicit p: Parameters)   extends DJBundle with HasDBData                 with HasDBID with HasToIDBits

class DBBundle(hasDBRCReq: Boolean = false)(implicit p: Parameters) extends DJBundle {
    val dbRCReqOpt  = if(hasDBRCReq) Some(Decoupled(new DBRCReq)) else None
    val wReq        = Decoupled(new DBWReq)
    val wResp       = Flipped(Decoupled(new DBWResp))
    val dataFDB     = Flipped(Decoupled(new NodeFDBData))
    val dataTDB     = Decoupled(new NodeTDBData)
}






