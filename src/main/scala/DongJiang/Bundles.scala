package DONGJIANG

import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import scala.collection.immutable.ListMap
import scala.math.{max, min}


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
trait HasReqBaseMesBundle extends DJBundle { this: Bundle =>
    // CHI Id(Use in RnSlave)
    val srcIDOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    val txnIDOpt    = if (djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    // Snp Mes(Use in RnMaster)
    val isSnp       = Bool()
    val doNotGoToSD = Bool()
    val retToSrc    = Bool()
    // CHI Mes(Common)
    val opcode      = UInt(6.W)
    // Other(Common)
    val willSnp     = Bool()
}

class ReqBaseMesBundle(implicit p: Parameters) extends DJBundle with HasReqBaseMesBundle with HasFromIDBits

trait HasReq2SliceBundle extends DJBundle with HasReqBaseMesBundle with HasAddr

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
    // Let ReqBuf Req Send To Slice Retry
    val reqRetry       = Bool()
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


// ---------------------------------------------------------------- DataBuffer Base Bundle ----------------------------------------------------------------------------- //
class MpTaskBundle(implicit p: Parameters) extends DJBundle with HasAddr {
    val reqMes  = new ReqBaseMesBundle()
    val respMes = new Resp2SliceBundleWitoutXbarId()
    val respVal = Bool()
}

class UpdateMSHRBundle(implicit p: Parameters) extends DJBundle

class DSTaskBundle(implicit p: Parameters) extends DJBundle

class DirReadBundle(implicit p: Parameters) extends DJBundle

class DirRespBundle(implicit p: Parameters) extends DJBundle

class DirWriteBundle(implicit p: Parameters) extends DJBundle

class SnpTaskBundle(implicit p: Parameters) extends DJBundle



