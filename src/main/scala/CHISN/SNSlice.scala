package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._

class SNSlice(snId: Int) (implicit p : Parameters) extends DJModule {
  val nodeParam                = djparam.snNodeMes(snId)
 // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val chi              = CHIBundleUpstream(chiParams)
      val chiLinkCtrl      = Flipped(new CHILinkCtrlIO())
      
      val readCmemAddr     = Output(UInt(addressBits.W))
      val readCmemEn       = Output(Bool())
      val readCmemRsp      = Input(UInt(64.W))

      val writeCmemAddr    = Output(UInt(64.W))
      val writeCmemData    = Output(UInt(64.W))
      val writeCmemEn      = Output(Bool())
    })

 // ------------------------ Module declaration --------------------------//

  val snChiCtrl             = Module(new InboundLinkCtrl())
  val snChiTxReq            = Module(new InboundFlitCtrl(gen = new CHIBundleREQ(chiParams), lcrdMax = nodeParam.nrSnTxLcrdMax, nodeParam.aggregateIO))
  val snChiTxDat            = Module(new InboundFlitCtrl(gen = new CHIBundleDAT(chiParams), lcrdMax = nodeParam.nrSnTxLcrdMax, nodeParam.aggregateIO))

  val snChiRxDat            = Module(new OutboundFlitCtrl(gen = new CHIBundleDAT(chiParams), lcrdMax = nodeParam.nrSnRxLcrdMax, nodeParam.aggregateIO))
  val snChiRxRsp            = Module(new OutboundFlitCtrl(gen = new CHIBundleRSP(chiParams), lcrdMax = nodeParam.nrSnRxLcrdMax, nodeParam.aggregateIO))


  val writeBuffer           = Module(new WriteBuf())
  val writeDat              = Module(new WrDat())
  val rspGen                = Module(new RspGen(snId))
  val datGen                = Module(new DatGen(snId))

  val rxActive              = Wire(Bool())




// ------------------------------ Logic --------------------------------//

  snChiCtrl.io.txAllLcrdRetrun   := snChiTxReq.io.allLcrdRetrun || snChiTxDat.io.allLcrdRetrun
  // snChiCtrl.io.rspFlitBufNoEmpty := rspGen.io.bufNoEmpty
  // snChiCtrl.io.datFlitBufNoEmpty := datGen.io.bufNoEmpty
  
  rxActive                       := rspGen.io.bufNoEmpty || datGen.io.bufNoEmpty
  snChiCtrl.io.rxRun             := rxActive

  snChiTxReq.io.linkState        := snChiCtrl.io.txState
  snChiTxDat.io.linkState        := snChiCtrl.io.txState

  writeBuffer.io.reqFlit         <> snChiTxReq.io.flit
  writeBuffer.io.datFlit         <> snChiTxDat.io.flit
  writeBuffer.io.dataRegEnq      := datGen.io.regEnq
  writeBuffer.io.datQueueFull    := datGen.io.datQueueFull
  writeBuffer.io.rspQueueFull    := rspGen.io.rspQueueFull

  writeDat.io.writeDataIn        <> writeBuffer.io.wrDat

  rspGen.io.reqFlitIn            <> snChiTxReq.io.flit
  rspGen.io.fsmFull              := writeBuffer.io.fsmFull
  rspGen.io.datQueueFull         := datGen.io.datQueueFull
  rspGen.io.dataRegEnq           := datGen.io.regEnq

  datGen.io.readReqFlit          <> snChiTxReq.io.flit
  datGen.io.cmemRdRsp            := io.readCmemRsp
  datGen.io.fsmFull              := writeBuffer.io.fsmFull
  datGen.io.rspQueueFull         := rspGen.io.rspQueueFull

  snChiRxDat.io.flit             <> datGen.io.dataFlit
  snChiRxDat.io.linkState        := snChiCtrl.io.rxState
  snChiRxRsp.io.linkState        := snChiCtrl.io.rxState
  snChiRxRsp.io.flit             <> rspGen.io.rspFlitOut

/* 
 * Output logic
 */

  io.chiLinkCtrl          <> snChiCtrl.io.chiLinkCtrl
  io.chi.txreq            <> snChiTxReq.io.chi
  io.chi.txdat            <> snChiTxDat.io.chi
  io.chi.txrsp            <> DontCare
  io.chi.rxdat            <> snChiRxDat.io.chi
  io.chi.rxrsp            <> snChiRxRsp.io.chi
  io.chi.rxsnp            <> DontCare

  io.writeCmemAddr        := writeDat.io.writeCmemAddr
  io.writeCmemData        := writeDat.io.writeCmemData
  io.writeCmemEn          := writeDat.io.writeEnable
  io.readCmemAddr         := datGen.io.readCmemAddr
  io.readCmemEn           := datGen.io.readCmemEn
  


}
