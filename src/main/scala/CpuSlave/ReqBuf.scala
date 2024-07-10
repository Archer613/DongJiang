package NHDSU.CPUSALVE

import NHDSU._
import _root_.NHDSU.CHI._
import chisel3._
import org.chipsalliance.cde.config._
import chisel3.util.{Decoupled, PopCount, RegEnable, ValidIO, log2Ceil}

class ReqBuf()(implicit p: Parameters) extends DSUModule {
  val io = IO(new Bundle {
    val free        = Output(Bool())
    val cpuSlvId    = Input(UInt(coreIdBits.W))
    val reqBufId    = Input(UInt(reqBufIdBits.W))
    // CHI
    val chi         = Flipped(CHIBundleDecoupled(chiBundleParams))
    // mainpipe
    val mpTask      = Decoupled(new TaskBundle())
    val mpResp      = Flipped(ValidIO(new RespBundle()))
    // snpCtrl
    val snpTask     = Flipped(Decoupled(new TaskBundle()))
    val snpResp     = Decoupled(new RespBundle())
    // dataBuffer
    val wReq        = Decoupled(new CpuDBWReq())
    val wResp       = Flipped(Decoupled(new CpuDBWResp()))
    val dbDataValid = Input(Bool())
  })

  // TODO: Delete the following code when the coding is complete
  io.free := true.B
  io.chi := DontCare
  io.snpTask := DontCare
  io.snpResp := DontCare
  io.mpTask := DontCare
  io.mpResp := DontCare
  io.wReq := DontCare
  io.wResp := DontCare
  dontTouch(io)

// --------------------- Modules declaration ---------------------//


// --------------------- Reg and Wire declaration ------------------------//
  val freeReg         = RegInit(true.B)
  val reqTxnIDReg     = WireInit(0.U.asTypeOf(io.chi.txreq.bits.txnID))
  val taskReg         = RegInit(0.U.asTypeOf(new TaskBundle()))
  val respReg         = RegInit(0.U.asTypeOf(new RespBundle()))
  val fsmReg          = RegInit(0.U.asTypeOf(new RBFSMState()))
  val reqValid        = WireInit(false.B)
  val alloc           = WireInit(false.B)
  val release         = WireInit(false.B)
  val task            = WireInit(0.U.asTypeOf(io.mpTask.bits))
  val getAllData      = WireInit(false.B)
  val getDataNumReg   = RegInit(0.U(log2Ceil(nrBeat+1).W))
  val cleanTask       = WireInit(0.U.asTypeOf(new TaskBundle()))
  val cleanTaskVal    = WireInit(false.B)

  dontTouch(fsmReg)
  dontTouch(getAllData)
  dontTouch(getDataNumReg)
  dontTouch(release)

// ---------------------------  Receive Req/Resp Logic --------------------------------//
  /*
   * Receive Cpu/Snp Req
   */
  reqValid := io.chi.txreq.valid | io.snpTask.valid
  when(io.chi.txreq.valid) {
    taskReg         := task
    val txreq       = io.chi.txreq.bits
    // task base message
    task.channel    := CHIChannel.TXREQ
    task.opcode     := txreq.opcode
    task.isR        := (CHIOp.REQ.ReadShared <= txreq.opcode & txreq.opcode <= CHIOp.REQ.ReadUnique) |
                       (CHIOp.REQ.ReadOnceCleanInvalid <= txreq.opcode & txreq.opcode <= CHIOp.REQ.ReadNotSharedDirty)
    task.isWB       := CHIOp.REQ.WriteEvictFull <= txreq.opcode & txreq.opcode <= CHIOp.REQ.WriteUniquePtlStash
    // task addr
    task.addr       := txreq.addr
    // task id
    task.from.idL0  := IdL0.CPU
    task.from.idL1  := io.cpuSlvId
    task.from.idL2  := io.reqBufId
    task.to.idL0    := IdL0.SLICE
    task.to.idL1    := DontCare
    task.to.idL2    := DontCare
    // task other
    task.isClean    := false.B
    task.readDir    := true.B
    task.btWay      := DontCare
    // other
    reqTxnIDReg     := txreq.txnID
  }


  /*
   * Receive Cpu Resp
   */
  respReg := Mux(io.mpResp.fire, io.mpResp.bits, respReg)

  /*
   * Receive and Count Data Valid
   */
  getDataNumReg := Mux(release, 0.U, getDataNumReg + (io.dbDataValid & io.chi.rxdat.fire).asUInt)
  getAllData := getDataNumReg === nrBeat.U | (getDataNumReg === (nrBeat - 1).U & (io.dbDataValid & io.chi.rxdat.fire))

// ---------------------------  Output Req/Resp Logic --------------------------------//
  /*
   * task or resp output
   */
  cleanTask.isClean   := true.B
  cleanTask.addr      := taskReg.addr
  cleanTask.btWay     := respReg.btWay
  cleanTaskVal        := fsmReg.s_clean & PopCount(fsmReg.asUInt) === 1.U // only clean need to do
  io.mpTask.valid     := fsmReg.s_wReq2mp | fsmReg.s_rReq2mp | cleanTaskVal
  io.mpTask.bits      := Mux(cleanTaskVal, cleanTask, taskReg)

  /*
   * chi rxdat output
   */
  io.chi.rxdat.valid        := fsmReg.s_rResp & fsmReg.w_data & io.dbDataValid
  io.chi.rxdat.bits.opcode  := respReg.opcode
  // IDs
  io.chi.rxdat.bits.tgtID   := dsuparam.idmap.RNID(0).U
  io.chi.rxdat.bits.srcID   := dsuparam.idmap.HNID.U
  io.chi.rxdat.bits.txnID   := reqTxnIDReg
  io.chi.rxdat.bits.homeNID := dsuparam.idmap.HNID.U
  io.chi.rxdat.bits.dbID    := io.reqBufId
  io.chi.rxdat.bits.resp    := respReg.resp


  /*
   * IO ready ctrl logic
   * Whether the io.chi.txreq and io.snpTask can be input is determined by io.free in ReqBufSel
   */
  io.snpTask.ready   := true.B // Always be true
  io.chi.txreq.ready := true.B // Always be true
  io.chi.txdat.ready := true.B // Always be true
  io.chi.txrsp.ready := true.B // Always be true

// ---------------------------  ReqBuf State release/alloc/set logic --------------------------------//
  /*
   * ReqBuf release logic
   */
  alloc := !RegNext(reqValid) & reqValid
  release := fsmReg.asUInt === 0.U // all s_task/w_task done
  freeReg := Mux(release & !alloc, true.B, Mux(alloc, false.B, freeReg))
  io.free := freeReg


  /*
   * Alloc or Set state
   */
  when(io.chi.txreq.valid) {
    // send
    fsmReg.s_rReq2mp  := task.isR
    fsmReg.s_wReq2mp  := task.isWB
    fsmReg.s_rResp    := true.B
    fsmReg.s_clean    := true.B // TODO: consider send clean before all task done
    // wait
    fsmReg.w_rResp    := true.B
    fsmReg.w_data     := false.B
    fsmReg.w_compAck  := io.chi.txreq.bits.expCompAck
  }.otherwise {
    // send
    fsmReg.s_rReq2mp  := Mux(io.mpTask.fire, false.B, fsmReg.s_rReq2mp)
    fsmReg.s_wReq2mp  := Mux(io.mpTask.fire, false.B, fsmReg.s_wReq2mp)
    fsmReg.s_rResp    := Mux(io.chi.rxrsp.fire | (io.chi.rxdat.fire & getAllData), false.B, fsmReg.s_rResp)
    fsmReg.s_clean    := Mux(io.mpTask.fire & io.mpTask.bits.isClean, false.B, fsmReg.s_clean)
    // wait
    fsmReg.w_rResp    := Mux(io.mpResp.fire, false.B, fsmReg.w_rResp)
    fsmReg.w_data     := Mux(io.mpResp.fire & io.mpResp.bits.isRxDat, true.B, Mux(getAllData, false.B, fsmReg.w_data))
    fsmReg.w_compAck  := Mux(io.chi.txrsp.fire & io.chi.txrsp.bits.opcode === CHIOp.RSP.CompAck, false.B, fsmReg.w_compAck)
  }


// --------------------- Assertion ------------------------------- //
  assert(Mux(!freeReg, !(io.chi.txreq.valid | io.snpTask.valid), true.B), "When ReqBuf valid, it cant input new req")
  assert(Mux(io.chi.txreq.valid | io.snpTask.valid, io.free, true.B), "Reqbuf cant block req input")
  assert(!(io.chi.txreq.valid & io.snpTask.valid), "Reqbuf cant receive txreq and snpTask at the same time")
  when(release) {
    assert(fsmReg.asUInt === 0.U, "when ReqBuf release, all task should be done")
  }
  assert(!(task.isR & task.isWB), "Cant alloc r and wb task at the same time")
  assert(Mux(getDataNumReg === nrBeat.U, !io.dbDataValid, true.B), "ReqBuf get data from DataBuf overflow")
  assert(Mux(io.dbDataValid, fsmReg.s_rResp & fsmReg.w_data, true.B), "When dbDataValid, ReqBuf should set s_rResp and w_data")
}