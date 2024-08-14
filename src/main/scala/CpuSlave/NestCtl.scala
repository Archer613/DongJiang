package NHDSU.CPUSALVE

import NHDSU._
import NHDSU.CHI._
import NHDSU.CHI.CHIOp._
import chisel3._
import org.chipsalliance.cde.config._
import chisel3.util.{Decoupled, PopCount, RegEnable, ValidIO, log2Ceil, Cat}
import freechips.rocketchip.util.SeqToAugmentedSeq

// TODO: Stall writeBack when has some reqBuf Snoop RN(already get dbid) is same addr with it
// TODO: Stall snoop when has some reqBuf writeBack HN(already get dbid) is same addr with it

class NestCtl()(implicit p: Parameters) extends DSUModule {
  val io = IO(new Bundle {
    // Nest Signals
    val reqBufOutVec  = Vec(dsuparam.nrReqBuf, Flipped(ValidIO(new NestOutMes())))
    val reqBufInVec   = Vec(dsuparam.nrReqBuf, ValidIO(new NestInMes()))
  })

  // TODO: Delete the following code when the coding is complete
  io.reqBufOutVec := DontCare
  io.reqBufInVec := DontCare
  dontTouch(io)

// --------------------- Reg and Wire declaration ----------------------- //
  val nestVecReg    = Seq.fill(dsuparam.nrReqBuf) { Seq.fill(dsuparam.nrReqBuf) { RegInit(false.B) } }
  val blockMecVec   = Wire(Vec(dsuparam.nrReqBuf, Bool()))
  

// ----------------------------- Logic ---------------------------------- //

  // address match vec
  nestVecReg.zipWithIndex.foreach {
    case(vec, i) =>
      vec.zipWithIndex.foreach {
        case(n, j) =>
          when(io.reqBufOutVec(i).valid & io.reqBufOutVec(i).bits.opcode === REQ.WriteBackFull) {
            if (i != j) n := io.reqBufOutVec(i).bits.nestAddr === io.reqBufOutVec(j).bits.nestAddr & io.reqBufOutVec(j).valid & (io.reqBufOutVec(j).bits.opcode === REQ.ReadNoSnp || 
            io.reqBufOutVec(j).bits.opcode === REQ.ReadUnique || io.reqBufOutVec(j).bits.opcode === REQ.ReadNotSharedDirty)
            else        n := false.B // it cant nest itself
          }.otherwise {
            n := false.B
          }
      }
  }
  blockMecVec := nestVecReg.reduce((rowA, rowB) => {
    VecInit((rowA zip rowB).map{ case(a, b) => a||b })
  })

  blockMecVec.zipWithIndex.foreach{
    case(block, i) =>
      io.reqBufInVec(i).valid := block
      io.reqBufInVec(i).bits.block := block
  }
  




// --------------------------- Assertion -------------------------------- //
//   nestVecReg.zipWithIndex.foreach {
//     case(vec, i) =>
//       assert(PopCount(vec) <= 1.U, "ReqBuf[0x%x] only can nest one ReqBuf", i.U)
//       // TODO: Del it
//       assert(!vec.reduce(_ | _), "ReqBuf[0x%x] be nested", i.U)
//   }
}