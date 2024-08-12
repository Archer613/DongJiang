package NHSN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import NHDSU.CHI._
import NHDSU._
import NHDSU.CHI.CHIOp._

class NHSN (implicit p : Parameters) extends DSUModule {
 // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val hnChi           = Vec(dsuparam.nrBank, CHIBundleUpstream(chiBundleParams))
      val hnLinkCtrl      = Vec(dsuparam.nrBank, Flipped(new CHILinkCtrlIO()))
    })
 // -------------------------- Module declaration -----------------------------//

  val snSlices            = Seq.fill(dsuparam.nrBank) { Module(new SNSlice())}
  val cmem                = Seq.fill(dsuparam.nrBank){Module(new MemHelper())}

 // -------------------------- Connect declaration -----------------------------//

  io.hnChi.zip(snSlices.map(_.io.chi)).foreach{ case(h, c) => h <> c }
  io.hnLinkCtrl.zip(snSlices.map(_.io.chiLinkCtrl)).foreach { case (h, c) => h <> c }

/* 
 * SNSlice is connected to RegMem
 */
  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.readCmemEn).foreach{case(mem,ren) => mem.ren := ren}}
  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.readCmemRsp).foreach{case(mem,rsp) => rsp := mem.rdata}}
  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.readCmemAddr).foreach{case(mem,addr) => mem.rIdx := addr}}

  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.writeCmemAddr).foreach{case(mem, addr) => mem.wIdx := addr}}
  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.writeCmemData).foreach{case(mem, wdata) => mem.wdata := wdata}}
  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.writeCmemEn).foreach{case(mem, wren) => mem.wen := wren}}
  // cmem.zip(snSlices).foreach{case(sliceMem, sn) => sliceMem.zip(sn.io.writeCmemEn).foreach{case(mem, wren) => mem.clk := clock}}

  cmem.zip(snSlices).foreach{case(mem, sn) => mem.rIdx := sn.io.readCmemAddr;  mem.ren := sn.io.readCmemEn;  sn.io.readCmemRsp := mem.rdata}
  cmem.zip(snSlices).foreach{case(mem, sn) => mem.wIdx := sn.io.writeCmemAddr; mem.wen := sn.io.writeCmemEn; mem.wdata := sn.io.writeCmemData; mem.clk := clock}



  }
