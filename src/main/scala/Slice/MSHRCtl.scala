package DONGJIANG.SLICE

import DONGJIANG._
import DONGJIANG.CHI._
import Utils.Encoder._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._


class MSHREntry()(implicit p: Parameters) extends DJBundle {
  val beSend  = Bool()
  val tag     = UInt(mshrTagBits.W)
  val bank    = UInt(bankBits.W)
  val ops     = new OperationsBundle()
  val reqMes = new ReqBaseMesBundle()
  val respMes = new Resp2SliceBundleWitoutXbarId()
  val respVal = Bool()

  def valid   = beSend | !ops.asUInt.orR
}


class MSHRCtl()(implicit p: Parameters) extends DJModule {
// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    val sliceId       = Input(UInt(bankBits.W))
    // Req and Resp To Slice
    val req2Slice     = Flipped(Decoupled(new Req2SliceBundle()))
    val resp2Slice    = Flipped(Decoupled(new Resp2SliceBundle()))
    // Task To MainPipe
    val mpTask        = Decoupled(new MpTaskBundle())
    // Update Task To MainPipe
    val udpMSHR       = Flipped(Decoupled(new UpdateMSHRBundle()))
    // Directory Read Req
    val dirRead       = Decoupled(new DirReadBundle())
    // Req Retry: Resp To Rn Node
    val retry2RnNode  = Decoupled(new Resp2NodeBundle())
  })

  // TODO: Delete the following code when the coding is complete
  io <> DontCare



// --------------------- Reg / Wire declaration ------------------------//
  // mshrTable
  val mshrTableReg    = RegInit(VecInit(Seq.fill(djparam.nrMSHRSet) { VecInit(Seq.fill(djparam.mrMSHRWay) { 0.U.asTypeOf(new MSHREntry()) }) }))
  val mshrLockVecReg  = RegInit(VecInit(Seq.fill(djparam.nrMSHRSet) { false.B }))
  // Transfer Req From Node To MSHREntry
  val mshrAlloc_s0    = Wire(new MSHREntry())
  // retry mes
  val retryTo_s0_g    = RegInit(0.U.asTypeOf(Valid(new IDBundle())))
  // task s0
  val canGo_s0        = Wire(Bool())
  val task_s0         = Wire(Valid(new MpTaskBundle()))



// ------------------------ S0: Receive Req From Node or Let It Retry-------------------------- //
  /*
   * Get MSHR Mes of Req From Node Set
   */
  val (mshrTag, mshrSet, mshrBank) = parseMSHRAddress(io.req2Slice.bits.addr); dontTouch(mshrTag); dontTouch(mshrSet); dontTouch(mshrBank)
  val mshrSetMatchVec = mshrTableReg(mshrSet).map { case m => m.valid & m.tag === mshrTag & m.bank === mshrBank }
  val mshrSetInvVec   = mshrTableReg(mshrSet).map(!_.valid)
  val mshrInvWay      = PriorityEncoder(mshrSetInvVec)
  val canReceiveReq   = !mshrSetMatchVec.reduce(_ | _) & mshrSetInvVec.reduce(_ | _)

  /*
   * Transfer Req From Node To MSHREntry
   */
  mshrAlloc_s0.beSend   := io.req2Slice.valid
  mshrAlloc_s0.tag      := mshrTag
  mshrAlloc_s0.bank     := mshrBank
  mshrAlloc_s0.reqMes   := io.req2Slice.bits
  mshrAlloc_s0.ops      := 0.U.asTypeOf(mshrAlloc_s0.ops)


  /*
   * Receive Req From Node
   */
  io.req2Slice.ready    := !retryTo_s0_g.valid
  retryTo_s0_g.valid    := Mux(retryTo_s0_g.valid, !io.retry2RnNode.fire, io.req2Slice.fire & !canReceiveReq)
  retryTo_s0_g.bits     := Mux(io.req2Slice.fire, io.req2Slice.bits.from, retryTo_s0_g.bits)


  /*
   * Retry Resp To Rn Node
   * TODO: Completer Retry Logic In Rn ReqBuf
   */
  io.retry2RnNode.valid         := retryTo_s0_g.valid
  io.retry2RnNode.bits          := DontCare
  io.retry2RnNode.bits.reqRetry := true.B
  io.retry2RnNode.bits.to       := retryTo_s0_g.bits



// ----------------------------------- S0: Update MHSR Table ----------------------------------- //
  /*
   * Update mshrTable value
   */
  val receiveReqMshr    = mshrTableReg(mshrSet)(mshrInvWay)
  receiveReqMshr        := Mux(mshrAlloc_s0.valid & canReceiveReq, mshrAlloc_s0, receiveReqMshr)



// --------------------------------- S0: Get task_s0 from MSHR -------------------------------- //
  /*
   * Get task_s0 mshrSet and mshrWay from MSHR
   */
  val mshrCanSendSetVec   = mshrTableReg.zip(mshrLockVecReg).map { case(t, l) => t.map(_.beSend).reduce(_ | _) & !l }
  val mshrCanSendSet      = RREncoder(mshrCanSendSetVec)
  val mshrCanSendWayVec   = mshrTableReg(mshrCanSendSet).map(_.beSend)
  val mshrCanSendWay      = RREncoder(mshrCanSendWayVec)
  val canSend_s0          = beSendMshr.beSend & !mshrLockVecReg(mshrCanSendSet)


  /*
   * send task_s0
   */
  val beSendMshr          = mshrTableReg(mshrCanSendSet)(mshrCanSendWay)
  task_s0.valid           := canSend_s0
  task_s0.bits.addr       := Cat(beSendMshr.tag, mshrCanSendSet, beSendMshr.bank, 0.U(offsetBits.W))
  task_s0.bits.reqMes     := beSendMshr.reqMes
  task_s0.bits.respMes    := beSendMshr.respMes
  task_s0.bits.respVal    := beSendMshr.respVal


  /*
   * Set Lock and BeSend Value
   */
  beSendMshr.beSend               := Mux(canSend_s0, !canGo_s0, true.B) // TODO
  mshrLockVecReg(mshrCanSendSet)  := Mux(canSend_s0, !canGo_s0, true.B) // TODO




// ----------------------------------- S1: Update MHSR Table ----------------------------------- //











}