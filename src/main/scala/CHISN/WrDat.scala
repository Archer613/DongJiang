package CHISN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import DONGJIANG._
import DONGJIANG.CHI._
import DONGJIANG.CHI.CHIOp._


class WrDat (implicit p : Parameters) extends DJModule {
 // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val writeDataIn              = Flipped(Decoupled(new WriteData(chiParams)))
      val writeCmemAddr            = Output(UInt(64.W))
      val writeCmemData            = Output(UInt(64.W))
      val writeEnable              = Output(Bool())
    })

 // ------------------------ Wire/Reg declaration ---------------------------//
  
  val firstBeat                    = Wire(Bool())
  val secondBeat                   = Wire(Bool())
  val writeData1                   = RegInit(0.U(32.W))
  val writeData2                   = RegInit(0.U(32.W))
  val writeAddr                    = RegInit(0.U(64.W))

// ------------------------------- Logic ------------------------------------//
  
  firstBeat                        := io.writeDataIn.bits.dataID === 0.U & io.writeDataIn.fire
  secondBeat                       := io.writeDataIn.bits.dataID === 2.U & io.writeDataIn.fire
  

  when(firstBeat){
    writeData1                     := io.writeDataIn.bits.data(31,0)
  }
  when(secondBeat){
    writeData2                     := io.writeDataIn.bits.data(31,0)
    writeAddr                      := io.writeDataIn.bits.addr
  }

/* 
 * Output logic
 */
  io.writeCmemAddr                 := writeAddr
  io.writeCmemData                 := Cat(writeData1, writeData2)
  io.writeEnable                   := RegNext(secondBeat)
  io.writeDataIn.ready             := true.B

}