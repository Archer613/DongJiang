package NHDSU.CHI

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tilelink._
import scala.collection.immutable.ListMap

class CHIBundleREQ(params: CHIBundleParameters) extends Bundle {
    val channelName = "'REQ' channel"

    val qos          = UInt(4.W)
    val tgtID        = UInt(params.nodeIdBits.W)
    val srcID        = UInt(params.nodeIdBits.W)
    val txnID        = UInt(12.W)
    val returnNID    = UInt(params.nodeIdBits.W)
    val opcode       = UInt(7.W)
    val size         = UInt(3.W)
    val addr         = UInt(params.addressBits.W)
    val ns           = Bool()
    val nse          = Bool()
    val likelyShared = Bool()
    val allowRetry   = Bool()
    val order        = UInt(2.W)
    val pCrdType     = UInt(4.W)
    val memAttr      = UInt(4.W)
    val snpAttr      = UInt(1.W)
    val cah          = Bool()
    // val excl         = cah
    // val snoopMe      = cah
    val expCompAck = Bool()
}

class CHIBundleRSP(params: CHIBundleParameters) extends Bundle {
    val channelName = "'RSP' channel"

    val qos      = UInt(4.W)
    val tgtID    = UInt(params.nodeIdBits.W)
    val srcID    = UInt(params.nodeIdBits.W)
    val txnID    = UInt(12.W)
    val opcode   = UInt(5.W)
    val respErr  = UInt(2.W)
    val resp     = UInt(3.W)
    val cBusy    = UInt(3.W)
    val dbID     = UInt(12.W)
    val pCrdType = UInt(4.W)
}

class CHIBundleSNP(params: CHIBundleParameters) extends Bundle {
    val channelName = "'SNP' channel"

    val qos         = UInt(4.W)
    val srcID       = UInt(params.nodeIdBits.W)
    val txnID       = UInt(12.W)
    val fwdNID      = UInt(params.nodeIdBits.W)
    val fwdTxnID    = UInt(12.W)
    val opcode      = UInt(5.W)
    val addr        = UInt((params.addressBits - 3).W)
    val ns          = Bool()
    val nse         = Bool()
    val doNotGoToSD = Bool()
    val retToSrc    = Bool()
}

class CHIBundleDAT(params: CHIBundleParameters) extends Bundle {
    val channelName = "'DAT' channel"

    val qos       = UInt(4.W)
    val tgtID     = UInt(params.nodeIdBits.W)
    val srcID     = UInt(params.nodeIdBits.W)
    val txnID     = UInt(12.W)
    val homeNID   = UInt(params.nodeIdBits.W)
    val opcode    = UInt(4.W)
    val respErr   = UInt(2.W)
    val resp      = UInt(3.W)
    val cBusy     = UInt(3.W)
    val dbID      = UInt(12.W)
    val ccID      = UInt(2.W)
    val dataID    = UInt(2.W)
    val cah       = Bool()
    val be        = UInt((params.dataBits / 8).W)
    val data      = UInt(params.dataBits.W)
    val dataCheck = if (params.dataCheck) Some(UInt((params.dataBits / 8).W)) else None
    val poison    = if (params.dataCheck) Some(UInt((params.dataBits / 64).W)) else None
}

class CHIChannelIO[T <: Data](gen: T, aggregateIO: Boolean = false) extends Bundle {
    val flitpend = Output(Bool())
    val flitv    = Output(Bool())
    val flit     = if (aggregateIO) Output(UInt(gen.getWidth.W)) else Output(gen)
    val lcrdv    = Input(Bool())
}

object CHIChannelIO {
    def apply[T <: Data](gen: T, aggregateIO: Boolean = false): CHIChannelIO[T] = new CHIChannelIO(gen, aggregateIO)
}

class CHIBundleDownstream(params: CHIBundleParameters, aggregateIO: Boolean = false) extends Record {
    val txreq: CHIChannelIO[CHIBundleREQ] = CHIChannelIO(new CHIBundleREQ(params), aggregateIO)
    val txdat: CHIChannelIO[CHIBundleDAT] = CHIChannelIO(new CHIBundleDAT(params), aggregateIO)
    val txrsp: CHIChannelIO[CHIBundleRSP] = CHIChannelIO(new CHIBundleRSP(params), aggregateIO)

    val rxrsp: CHIChannelIO[CHIBundleRSP] = Flipped(CHIChannelIO(new CHIBundleRSP(params), aggregateIO))
    val rxdat: CHIChannelIO[CHIBundleDAT] = Flipped(CHIChannelIO(new CHIBundleDAT(params), aggregateIO))
    val rxsnp: CHIChannelIO[CHIBundleSNP] = Flipped(CHIChannelIO(new CHIBundleSNP(params), aggregateIO))

    // @formatter:off
    val elements = ListMap(
        "txreq" -> txreq,
        "txdat" -> txdat,
        "txrsp" -> txrsp,
        "rxrsp" -> rxrsp,
        "rxdat" -> rxdat,
        "rxsnp" -> rxsnp
    )
    // @formatter:on
}

class CHIBundleUpstream(params: CHIBundleParameters, aggregateIO: Boolean = false) extends Record {
    val txreq: CHIChannelIO[CHIBundleREQ] = Flipped(CHIChannelIO(new CHIBundleREQ(params), aggregateIO))
    val txdat: CHIChannelIO[CHIBundleDAT] = Flipped(CHIChannelIO(new CHIBundleDAT(params), aggregateIO))
    val txrsp: CHIChannelIO[CHIBundleRSP] = Flipped(CHIChannelIO(new CHIBundleRSP(params), aggregateIO))

    val rxrsp: CHIChannelIO[CHIBundleRSP] = CHIChannelIO(new CHIBundleRSP(params), aggregateIO)
    val rxdat: CHIChannelIO[CHIBundleDAT] = CHIChannelIO(new CHIBundleDAT(params), aggregateIO)
    val rxsnp: CHIChannelIO[CHIBundleSNP] = CHIChannelIO(new CHIBundleSNP(params), aggregateIO)

    // @formatter:off
    val elements = ListMap(
        "txreq" -> txreq,
        "txdat" -> txdat,
        "txrsp" -> txrsp,
        "rxrsp" -> rxrsp,
        "rxdat" -> rxdat,
        "rxsnp" -> rxsnp
    )
    // @formatter:on
}

class CHIBundleDecoupled(params: CHIBundleParameters) extends Bundle {
    val txreq = Decoupled(new CHIBundleREQ(params))
    val txdat = Decoupled(new CHIBundleDAT(params))
    val txrsp = Decoupled(new CHIBundleRSP(params))

    val rxrsp = Flipped(Decoupled(new CHIBundleRSP(params)))
    val rxdat = Flipped(Decoupled(new CHIBundleDAT(params)))
    val rxsnp = Flipped(Decoupled(new CHIBundleRSP(params)))
}

object CHIBundleDownstream {
    def apply(params: CHIBundleParameters, aggregateIO: Boolean = false): CHIBundleDownstream = new CHIBundleDownstream(params, aggregateIO)
}

object CHIBundleUpstream {
    def apply(params: CHIBundleParameters, aggregateIO: Boolean = false): CHIBundleUpstream = new CHIBundleUpstream(params, aggregateIO)
}

object CHIBundleDecoupled {
    def apply(params: CHIBundleParameters): CHIBundleDecoupled = new CHIBundleDecoupled(params)
}

class CHILinkCtrlIO extends Bundle {
    val txsactive = Output(Bool())
    val rxsactive = Input(Bool())

    val txactivereq = Output(Bool())
    val txactiveack = Input(Bool())

    val rxactivereq = Input(Bool())
    val rxactiveack = Output(Bool())
}

object LinkStates {
    val width = 2

    def STOP        = 0.U(width.W)
    def ACTIVATE    = 1.U(width.W)
    def RUN         = 2.U(width.W)
    def DEACTIVATE  = 3.U(width.W)
}

class LinkState extends Bundle {
    val state = UInt(LinkStates.width.W)
}