package NHSN

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import NHDSU.CHI._
import NHDSU._

class WrDat (implicit p : Parameters) extends DSUModule {
 // -------------------------- IO declaration -----------------------------//
    val io = IO(new Bundle {
      val writeDataIn              = Flipped(Decoupled(new WriteData(chiBundleParams)))
      val writeCmemAddr            = Output(Vec(nrBeat, UInt(64.W)))
      val writeCmemData            = Output(Vec(nrBeat,UInt(64.W)))
      val writeEnable              = Output(Vec(nrBeat, Bool()))
    })

 // ------------------------ Wire/Reg declaration ---------------------------//
  
  val firstBeat                    = Wire(Bool())
  val secondBeat                   = Wire(Bool())
  val writeData                    = WireInit(0.U(64.W))
  val writeAddr                    = WireInit(0.U(64.W))

// ------------------------------- Logic ------------------------------------//
  
  firstBeat                        := io.writeDataIn.bits.dataID === 0.U & io.writeDataIn.fire
  secondBeat                       := io.writeDataIn.bits.dataID === 2.U & io.writeDataIn.fire
  writeData                        := io.writeDataIn.bits.data(63,0)
  dontTouch(writeData)
  writeAddr                        := io.writeDataIn.bits.addr

/* 
 * Output logic
 */
  io.writeCmemAddr                 := VecInit(Seq.fill(nrBeat)(writeAddr))
  io.writeCmemData                 := VecInit(Seq.fill(nrBeat)(writeData))
  io.writeEnable                   := VecInit(Seq(firstBeat, secondBeat))
  io.writeDataIn.ready             := true.B

}