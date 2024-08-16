package DONGJIANG

import DONGJIANG.CHI._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import scala.math.{max, min}

case object DJParamKey extends Field[DJParam](DJParam())


// RN Node Params, used for generation
case class RnNodeParam
(
    name: String = "RnSalve",
    nrReqBuf: Int = 16,
    isSlave: Boolean = true,
    addressId: Int = 0,
    addressIdBits: Int = 0,
    peferTgtIdMap: Option[Seq[Int]] = None,
    aggregateIO: Boolean = false,
    // can receive or send chi lcrd num
    nrRnTxLcrdMax: Int = 4,
    nrRnRxLcrdMax: Int = 4,
) {
    val isMaster = !isSlave
    val hasReq2Slice = true
    val hasDBRCReq = isMaster
    require(nrRnTxLcrdMax <= 15)
    require(nrRnRxLcrdMax <= 15)
}


// SN Node Params, used for generation
case class SnNodeParam
(
    name: String = "SnMaster",
    nrReqBuf: Int = 16,
    addressBits: Int = 48,
    addressId: Int = 0,
    addressIdBits: Int = 0,
    aggregateIO: Boolean = false,
    // can receive or send chi lcrd num
    nrSnTxLcrdMax: Int = 4,
    nrSnRxLcrdMax: Int = 4,

) {
    val isSlave = false
    val isMaster = !isSlave // SN be Master forever
    val hasReq2Slice = false
    val hasDBRCReq = true // must be true when its master
    require(nrSnTxLcrdMax <= 15)
    require(nrSnRxLcrdMax <= 15)
}


case class DJParam(
                    // -------------------------- Base Mes ---------------------- //
                    blockBytes: Int = 64,
                    beatBytes: Int = 32,
                    addressBits: Int = 48,
                    hasLLC: Boolean = true,
                    useInNoc: Boolean = false,
                    useDCT: Boolean = false, // Dont open it when useInNoc is false
                    // ------------------------- Rn / Sn Base Mes -------------------- //
                    rnNodeMes: Seq[RnNodeParam] = Seq(RnNodeParam( name = "RnSalve_0" ),
                                                      RnNodeParam( name = "RnMaster_0", isSlave = false)),
                    snNodeMes: Seq[SnNodeParam] = Seq(SnNodeParam( name = "SnMaster_0" ),
                                                      SnNodeParam( name = "SnMaster_1" )),
                    // ------------------------ Slice Base Mes ------------------ //
                    nrMpTaskQueue: Int = 4,
                    nrMpReqQueue: Int = 4,
                    nrMpRespQueue: Int = 4,
                    mpBlockBySet: Boolean = true,
                    // MSHR
                    nrMSHRSets: Int = 4,
                    nrMSHRWays: Int = 4,
                    // number of bank or buffer
                    nrBank: Int = 2,
                    nrSnpCtl: Int = 16,
                    nrDataBuf: Int = 16,
                    // ------------------------ Directory * DataStorage Mes ------------------ //
                    // self dir & ds mes, dont care when hasLLC is false
                    nrSelfDirBank: Int = 2,
                    selfWays: Int = 4,
                    selfSets: Int = 32,
                    selfDirMulticycle: Int = 2,
                    selfDirHoldMcp: Boolean = true,
                    dsMulticycle: Int = 2,
                    dsHoldMcp: Boolean = true,
                    selfReplacementPolicy: String = "plru",
                    // snoop(client) dir mes
                    nrClientDirBank: Int = 2,
                    clientDirWays: Int = 4,
                    clientDirSets: Int = 32,
                    clientDirMulticycle: Int = 2,
                    clientDirHoldMcp: Boolean = true,
                    clientReplacementPolicy: String = "plru",
                  ) {
    require(rnNodeMes.length > 0)
    require(nrMpTaskQueue > 0)
    require(nrMpReqQueue > 0)
    require(nrMpRespQueue > 0)
    require(nrMSHRSets <= selfSets)
    require(nrBank == 1 | nrBank == 2 | nrBank == 4)
    require(snNodeMes.length == nrBank)
    require(nrMSHRWays <= min(selfWays, clientDirWays))
    require(selfReplacementPolicy == "random" || selfReplacementPolicy == "plru")
    require(clientReplacementPolicy == "random" || clientReplacementPolicy == "plru")
}

trait HasDJParam {
    val p: Parameters
    val djparam = p(DJParamKey)

    // Base Mes Parameters
    val nrBeat          = djparam.blockBytes / djparam.beatBytes
    val addressBits     = djparam.addressBits
    val dataBits        = djparam.blockBytes * 8
    val beatBits        = djparam.beatBytes * 8

    // RN Parameters
    val nrRnNode        = djparam.rnNodeMes.length
    val nrRnSlv         = djparam.rnNodeMes.map(_.isSlave).count(_ == true)
    val nrRnMas         = djparam.rnNodeMes.map(_.isMaster).count(_ == true)
    val rnSlvNodeIdBits = log2Ceil(nrRnSlv)
    val rnNodeIdBits    = log2Ceil(nrRnNode)
    val nrRnReqBufMax   = djparam.rnNodeMes.map(_.nrReqBuf).max
    val rnReqBufIdBits  = log2Ceil(nrRnReqBufMax)

    // SN Parameters
    val snReqBufIdBits  = log2Ceil(djparam.snNodeMes.map(_.nrReqBuf).max)

    // Slice Queue
    val mpTaskQBits     = log2Ceil(djparam.nrMpTaskQueue)
    val mpReqQBits      = log2Ceil(djparam.nrMpReqQueue)
    val mpRespQBits     = log2Ceil(djparam.nrMpRespQueue)

    // Slice Id Bits Parameters
    val snpCtlIdBits    = log2Ceil(djparam.nrSnpCtl)
    val dbIdBits        = log2Ceil(djparam.nrDataBuf)

    // DIR BASE Parameters
    val bankBits        = log2Ceil(djparam.nrBank)
    val offsetBits      = log2Ceil(djparam.blockBytes)

    // SELF DIR Parameters: [sTag] + [sSet] + [sDirBank] + [bank] + [offset]
    // [sSet] + [sDirBank] = [setBis]
    val sWayBits        = log2Ceil(djparam.selfWays)
    val sDirBankBits    = log2Ceil(djparam.nrSelfDirBank)
    val sSetBits        = log2Ceil(djparam.selfSets/djparam.nrSelfDirBank)
    val sTagBits        = djparam.addressBits - sSetBits - sDirBankBits - bankBits - offsetBits

    // CLIENT DIR Parameters: [cTag] + [cSet] + [cDirBank] + [bank] + [offset]
    // [cSet] + [cDirBank] = [clientSetsBits]
    val cWayBits        = log2Ceil(djparam.clientDirWays)
    val cDirBankBits    = log2Ceil(djparam.nrClientDirBank)
    val cSetBits        = log2Ceil(djparam.clientDirSets / djparam.nrClientDirBank)
    val cTagBits        = djparam.addressBits - cSetBits - cDirBankBits - bankBits - offsetBits

    // DS Parameters
    val dsWayBits       = sWayBits
    val dsSetBits       = sSetBits

    // MSHR TABLE Parameters: [mshrTag] + [mshrSet] + [bank] + [offset]
    val mshrWayBits     = log2Ceil(djparam.nrMSHRWays)
    val mshrSetBits     = log2Ceil(djparam.nrMSHRSets)
    val mshrTagBits     = djparam.addressBits - mshrSetBits - bankBits - offsetBits

    // replacement Parameters
    val sUseRepl        = djparam.selfReplacementPolicy != "random"
    val sReplWayBits    = djparam.selfWays - 1
    val cUseRepl        = djparam.clientReplacementPolicy != "random"
    val cReplWayBits    = djparam.clientDirWays - 1
    require(djparam.selfReplacementPolicy == "random" | djparam.selfReplacementPolicy == "plru", "It should modify sReplWayBits when use replacement except of random or plru")
    require(djparam.clientReplacementPolicy == "random" | djparam.clientReplacementPolicy == "plru", "It should modify cReplWayBits when use replacement except of random or plru")

    // Node address id map check
    // TODO

    // TIMEOUT CHECK CNT VALUE
    val TIMEOUT_RB      = 10000 // ReqBuf
    val TIMEOUT_DB      = 8000  // DataBuffer
    val TIMEOUT_BT      = 8000  // BlockTable
    val TIMEOUT_MP      = 8000  // MainPipe
    val TIMEOUT_SNP     = 8000  // SnoopCtl
    val TIMEOUT_DS      = 6000  // DataStorage
    val TIMEOUT_RC      = 6000  // ReadCtl
    val TIMEOUT_TXD     = 1000  // SnChiTxDat

    // chiParams
    val chiParams = CHIBundleParameters(
        nodeIdBits = 7,
        addressBits = addressBits,
        dataBits = beatBits,
        dataCheck = false,
        snpHasTgtId = djparam.useInNoc
    )

    // some requirements
    require(rnReqBufIdBits <= chiParams.txnidBits & snReqBufIdBits <= chiParams.txnidBits)
    require(dbIdBits <= chiParams.dbidBits)

    def parseAddress(x: UInt, modBankBits: Int = 1, setBits: Int = 1, tagBits: Int = 1): (UInt, UInt, UInt, UInt, UInt) = {
        val offset  = x
        val bank    = offset    >> offsetBits
        val modBank = bank      >> bankBits
        val set     = modBank   >> modBankBits
        val tag     = set       >> setBits
        // return: [5:tag] [4:set] [3:modBank] [2:bank] [1:offset]
        (tag(tagBits - 1, 0), set(setBits - 1, 0), modBank(modBankBits - 1, 0), bank(bankBits - 1, 0), offset(offsetBits - 1, 0))
    }

    def parseMSHRAddress(x: UInt, mpBlockBySet: Boolean = false): (UInt, UInt, UInt) = {
        val tag = WireInit(0.U(mshrTagBits.W))
        val bank = WireInit(0.U(bankBits.W))
        val (tag_, set, modBank, bank_, offset) = parseAddress(x, modBankBits = 0, setBits = mshrSetBits, tagBits = mshrTagBits)
        if (mpBlockBySet) {
            tag := tag_ // TODO: When !mpBlockBySet it must support useWayOH Check and RetryQueue
            bank := bank_
        } else {
            require(sSetBits + sDirBankBits > mshrSetBits)
            tag := tag_(sSetBits + sDirBankBits - 1 - mshrSetBits, 0)
            bank := 0.U
        }
        // return: [3:mshrTag] [2:mshrSet] [1:bank]
        (tag, set, bank)
    }

    def toDataID(x: UInt): UInt = {
        if (nrBeat == 1) { x }
        else if (nrBeat == 2) { Mux(x === 0.U, 0.U, 2.U) }
        else { 0.U }
    }
}
