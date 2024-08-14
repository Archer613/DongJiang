package CHISN

import chisel3._
import chisel3.util._
import DONGJIANG._
import DONGJIANG.CHI._
// import NHDSU.DSUParam._
import org.chipsalliance.cde.config._


class WriteData(params: CHIBundleParameters) extends Bundle {

    val addr           = UInt(params.addressBits.W)
    val txnID          = UInt(8.W)
    val dataID         = UInt(2.W)
    val data           = UInt(params.dataBits.W)

}

class WriteReg(params: CHIBundleParameters) extends  Bundle {
    val addr           = UInt(params.addressBits.W)
    val data           = UInt(params.dataBits.W)
    val beat           = UInt(2.W)
}


object WrState { // Read Ctl State
    val width      = 2
    val nrState    = 3
    val IDLE       = "b00".U
    val WAITDATA   = "b01".U
    val SENDDAT1   = "b10".U
}

class WriteBufTableEntry(implicit p: Parameters) extends DJBundle {
    val state   = UInt(WrState.width.W)
    val txnID   = UInt(8.W)
    val addr    = UInt((djparam.addressBits).W)
}

object WrDatState {
    val width   = 2
    val nrState = 3
    val IDLE    = "b00".U
    val WAITDATA = "b01".U
    val WRDATA   = "b10".U
}


class WriteDataTable extends Bundle {
    val wrAddr  = UInt(64.W)
    val data    = UInt(64.W)
    val txnId   = UInt(8.W)
    val state   = UInt(WrDatState.width.W)
}
