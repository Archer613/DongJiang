package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._
import DONGJIANG.DJParam._


class CHISN (implicit p : Parameters) extends DJModule {
 // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val hnChi           = Vec(djparam.nrBank, CHIBundleUpstream(chiParams))
      val hnLinkCtrl      = Vec(djparam.nrBank, Flipped(new CHILinkCtrlIO()))
    })
 // -------------------------- Module declaration -----------------------------//

  def createSnSlices(id: Int) = { val snSlices = Module(new SNSlice(id)); snSlices }
  
  val snSlices            = (0 until djparam.nrBank).map(i => createSnSlices(i))
  val cmem                = Seq.fill(djparam.nrBank){Module(new MemHelper())}
 

 // -------------------------- Connect declaration -----------------------------//

  io.hnChi.zip(snSlices.map(_.io.chi)).foreach{ case(h, c) => h <> c }
  io.hnLinkCtrl.zip(snSlices.map(_.io.chiLinkCtrl)).foreach { case (h, c) => h <> c }

/* 
 * SNSlice is connected to Mem
 */
  

  cmem.zip(snSlices).foreach{case(mem, sn) => mem.rIdx := sn.io.readCmemAddr;  mem.ren := sn.io.readCmemEn;  sn.io.readCmemRsp := mem.rdata}
  cmem.zip(snSlices).foreach{case(mem, sn) => mem.wIdx := sn.io.writeCmemAddr; mem.wen := sn.io.writeCmemEn; mem.wdata := sn.io.writeCmemData; mem.clk := clock}



  }
