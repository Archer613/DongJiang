package DONGJIANG.RNMASTER

import DONGJIANG._
import DONGJIANG.IdL0._
import DONGJIANG.CHI._
import chisel3._
import org.chipsalliance.cde.config._
import chisel3.util.{Cat, Decoupled, PopCount, RegEnable, Valid, ValidIO, log2Ceil}

class RBFSMState(implicit p: Parameters) extends Bundle {
  // schedule
  val s_req       = Bool() // to CHI REQ { Read / Dataless / Atomic / CMO / Write }
  val s_rcDB      = Bool() // when need to write back data or snpFwd need retToSrc
  val s_data      = Bool() // to CHI DAT when write back data or snpFwd resp compData

  val s_getDBID   = Bool() // when need to send Read
  val s_compAck   = Bool() // when Req Done
  val s_reqResp   = Bool() // to Slice

  val s_snp2mshr  = Bool() // to Slice
  val s_snpResp   = Bool() // to CHI RSP / DAT

  val s_reqUdpMSHR  = Bool() // to Slice when send CompAck
  val s_snpUdpMSHR  = Bool() // to Slice when send SnpResp*

  // wait
  val w_dbidResp  = Bool() // from CHI RSP
  val w_dbData    = Bool()

  val w_dbid      = Bool()
  val w_hnResp    = Bool() // from CHI RSP or DAT

  val w_mpResp    = Bool() // from Slice to Deal Snp
}


class RnMasReqBuf(rnMasId: Int, reqBufId: Int)(implicit p: Parameters) extends DJModule {
  val nodeParam = djparam.rnNodeMes(rnMasId)

// --------------------- IO declaration ------------------------//
  val io = IO(new Bundle {
    val free        = Output(Bool())
    // CHI
    val chi         = CHIBundleDecoupled(chiParams)
    // slice ctrl signals
    val req2Slice   = Decoupled(new Req2SliceBundle())
    val resp2Node   = Flipped(Decoupled(new Resp2NodeBundle()))
    val req2Node    = Flipped(Decoupled(new Req2NodeBundle()))
    val resp2Slice  = Decoupled(new Resp2SliceBundle())
    // For txDat and rxDat sinasl
    val reqBufDBID  = Valid(new Bundle {
      val bankId    = UInt(bankBits.W)
      val dbid      = UInt(dbIdBits.W)
    })
    // slice DataBuffer signals
    val dbRCReq     = Decoupled(new DBRCReq())
    val wReq        = Decoupled(new DBWReq())
    val wResp       = Flipped(Decoupled(new DBWResp()))
    val dataFDBVal  = Input(Bool())
  })

// --------------------- Reg and Wire declaration ------------------------//
  // reqBuf Ctrl
  val freeReg       = RegInit(true.B)
  val fsmReg        = RegInit(0.U.asTypeOf(new RBFSMState))
  // req reg
  val reqReg        = RegInit(0.U.asTypeOf(new DJBundle with HasFromIDBits {
    val addr        = UInt(addressBits.W)
    val opcode      = UInt(6.W)
    val txnId       = UInt(chiParams.txnidBits.W)
    val srcId       = UInt(chiParams.nodeIdBits.W)
    val tgtId       = UInt(chiParams.nodeIdBits.W)
    // Snp Mes
    val fwdNIdOpt   = if(djparam.useDCT) Some(UInt(chiParams.nodeIdBits.W)) else None
    val fwdTxnIdOpt = if(djparam.useDCT) Some(UInt(chiParams.txnidBits.W)) else None
    val retToSrc    = Bool()
    val doNotGoToSD = Bool()
  }))
  // req from slice or txreq
  val reqFRxSnp     = WireInit(0.U.asTypeOf(reqReg))
  val req2Node      = WireInit(0.U.asTypeOf(reqReg))
  val reqIsWrite    = WireInit(false.B)
  val reqNeedData   = WireInit(false.B)
  val snpIsFwd      = WireInit(false.B)
  // data crtl
  val getDBNumReg       = RegInit(0.U(log2Ceil(nrBeat + 1).W))
  val getRxDatNumReg    = RegInit(0.U(log2Ceil(nrBeat + 1).W))
  val getAllData        = WireInit(false.B) // get all Data from DataBuffer or TxDat
  val dbidReg           = RegInit(0.U(dbIdBits.W))
  val dbidBankIdReg     = RegInit(0.U(dbIdBits.W)) // dbid from which bank
  val sendTxDatNumReg   = RegInit(0.U(log2Ceil(nrBeat * 2 + 1).W))
  val reqSendAllData    = WireInit(false.B) // Send all Data to HN when need to send CompData / WriteData
  val snpSendAllData    = WireInit(false.B) // Send all Data to HN when need to send SnpRespData
  // req resp to slice req
  val reqRespReg        = RegInit(0.U(3.W))
  val reqRespHasDataReg = RegInit(false.B)
  val reqRespDBIDReg    = RegInit(0.U(chiParams.dbidBits.W))
  val reqRespHomeNIdOrSrcIdReg = RegInit(0.U(chiParams.nodeIdBits.W)) // CompData HomeNID or Comp SrcID
  // slice resp reg
  val mpRespReg         = RegInit(0.U.asTypeOf(new Resp2NodeBundle()))


// ---------------------------  ReqBuf State release/alloc/set logic --------------------------------//
  /*
   * ReqBuf release logic
   */
  val alloc = io.req2Node.fire | io.chi.rxsnp.fire
  val release = fsmReg.asUInt === 0.U // all s_task / w_task done
  freeReg := Mux(release & !alloc, true.B, Mux(alloc, false.B, freeReg))
  io.free := freeReg

  /*
   * Alloc or Set state
   */
  when(io.req2Node.fire & reqIsWrite) {
    // send
    fsmReg.s_req      := true.B
    fsmReg.s_rcDB     := true.B
    fsmReg.s_data     := true.B
    fsmReg.s_reqUdpMSHR := true.B
    // wait
    fsmReg.w_dbidResp := true.B
    fsmReg.w_dbData   := true.B
  }.elsewhen(io.req2Node.fire) {
    // send
    fsmReg.s_req      := true.B
    fsmReg.s_getDBID  := reqNeedData
    fsmReg.s_compAck  := io.req2Node.bits.expCompAck
    fsmReg.s_reqResp  := true.B
    // wait
    fsmReg.w_dbid     := true.B
    fsmReg.w_hnResp   := true.B
  }.elsewhen(io.chi.rxsnp.fire) {
    // send
    fsmReg.s_snp2mshr := true.B
    fsmReg.s_data     := snpIsFwd
    fsmReg.s_rcDB     := snpIsFwd
    fsmReg.s_snpResp  := true.B
    fsmReg.s_snpUdpMSHR := true.B
    // wait
    fsmReg.w_mpResp   := true.B
    fsmReg.w_dbData   := io.chi.rxsnp.bits.retToSrc
  }.otherwise {
    /*
     * Commmon
     */
    fsmReg.s_req      := Mux(io.req2Slice.fire,                 false.B, fsmReg.s_req)
    fsmReg.s_data     := Mux(io.chi.txdat.fire & reqSendAllData,false.B, fsmReg.s_data)
    fsmReg.s_rcDB     := Mux(io.dbRCReq.fire,                   false.B, fsmReg.s_rcDB)
    fsmReg.w_dbData   := Mux(io.dataFDBVal & getAllData,        false.B, fsmReg.w_dbData)

    /*
     * Write Req
     */
    fsmReg.w_dbidResp := Mux(io.chi.rxrsp.fire,                 false.B, fsmReg.w_dbidResp)
    fsmReg.s_reqUdpMSHR := Mux(io.resp2Slice.fire,              false.B, fsmReg.s_reqUdpMSHR)

    /*
     * { Read / Dataless / Atomic / CMO } Req
     */
    fsmReg.s_getDBID  := Mux(io.wReq.fire,                      false.B, fsmReg.s_getDBID)
    fsmReg.s_compAck  := Mux(io.chi.txrsp.fire,                 false.B, fsmReg.s_compAck)
    fsmReg.s_reqResp  := Mux(io.resp2Slice.fire,                false.B, fsmReg.s_reqResp)
    fsmReg.w_dbid     := Mux(io.wResp.fire,                     false.B, fsmReg.w_dbid)
    fsmReg.w_hnResp   := Mux(io.chi.rxrsp.fire | (io.chi.rxdat.fire & getAllData), false.B, fsmReg.w_hnResp)

    /*
     * Snoop Req
     * Must send CompData before SnpRespData in DCT
     */
    fsmReg.s_snp2mshr := Mux(io.req2Node.fire,                 false.B, fsmReg.s_snp2mshr)
    fsmReg.s_snpResp  := Mux(io.chi.txrsp.fire | (io.chi.txdat.fire & snpSendAllData), false.B, fsmReg.s_snpResp)
    fsmReg.w_mpResp   := Mux(io.resp2Node.fire,                 false.B, fsmReg.w_mpResp)
    fsmReg.s_snpUdpMSHR := Mux(io.resp2Slice.fire,              false.B, fsmReg.s_snpUdpMSHR)
  }


// ---------------------------  Receive Req(TxReq and req2Node) Logic --------------------------------//
  /*
   * Receive req2Node(Read / Dataless / Atomic / CMO / Write)
   */
  req2Node.addr       := io.req2Node.bits.addr
  req2Node.opcode     := io.req2Node.bits.opcode
  req2Node.from       := io.req2Node.bits.from
  req2Node.tgtId      := io.req2Node.bits.tgtID
  reqIsWrite          := CHIOp.REQ.isWriteX(io.req2Node.bits.opcode)
  reqNeedData         := CHIOp.REQ.isReadX(io.req2Node.bits.opcode)

  /*
   * Receive chiTxReq(Snoop)
   */
  reqFRxSnp.addr      := io.chi.rxsnp.bits.addr
  reqFRxSnp.opcode    := io.chi.rxsnp.bits.opcode
  reqFRxSnp.txnId     := io.chi.rxsnp.bits.txnID
  reqFRxSnp.srcId     := io.chi.rxsnp.bits.srcID
  if(djparam.useDCT) {
    reqFRxSnp.fwdNIdOpt.get   := io.chi.rxsnp.bits.fwdNID
    reqFRxSnp.fwdTxnIdOpt.get := io.chi.rxsnp.bits.fwdTxnID
  }
  reqFRxSnp.retToSrc        := io.chi.rxsnp.bits.retToSrc
  reqFRxSnp.doNotGoToSD     := io.chi.rxsnp.bits.doNotGoToSD
  snpIsFwd                  := CHIOp.SNP.isSnpXFwd(io.chi.rxsnp.bits.opcode)

  /*
   * Save req2Node or reqFTxReq
   */
  reqReg := Mux(io.chi.rxsnp.fire, reqFRxSnp, Mux(io.req2Node.fire, req2Node, reqReg))


// ---------------------------  Receive CHI Resp(RxRsp and RxDat) Logic --------------------------------//
  /*
   * Receive RxRsp or RxDat
   */
  // Receive RxRsp
  when(io.chi.rxrsp.fire & fsmReg.w_hnResp) {
    reqRespReg        := io.chi.rxrsp.bits.resp
    reqRespDBIDReg    := io.chi.rxrsp.bits.dbID
    reqRespHasDataReg := false.B
    reqRespHomeNIdOrSrcIdReg := io.chi.rxrsp.bits.srcID
  // Receive RxDat
  }.elsewhen(io.chi.rxdat.fire & fsmReg.w_hnResp) {
    reqRespReg        := io.chi.rxdat.bits.resp
    reqRespHasDataReg := true.B
    reqRespHomeNIdOrSrcIdReg := io.chi.rxdat.bits.homeNID
  }
  getRxDatNumReg      := Mux(release, 0.U, getRxDatNumReg + io.chi.rxdat.fire.asUInt)
  // Set Ready value
  io.chi.rxrsp.ready  := true.B
  io.chi.rxdat.ready  := true.B


// ---------------------------  Send CHI Req or Resp(TxReq, TxRsp and TxDat) Logic --------------------------------//
  /*
   * Send TxReq
   */
  io.chi.txreq.valid        := fsmReg.s_req & !fsmReg.w_dbid
  io.chi.txreq.bits         := DontCare
  io.chi.txreq.bits.tgtID   := reqReg.tgtId
  io.chi.txreq.bits.srcID   := rnMasId.U
  io.chi.txreq.bits.txnID   := reqBufId.U
  io.chi.txreq.bits.opcode  := reqReg.opcode
  io.chi.txreq.bits.size    := log2Ceil(djparam.blockBytes).U
  io.chi.txreq.bits.addr    := reqReg.addr
  io.chi.txreq.bits.memAttr := MemAttr(false.B, true.B, false.B, false.B)
  io.chi.txreq.bits.expCompAck := fsmReg.s_compAck

  /*
   * Send TxRsp(CompAck or SnpResp)
   */
  io.chi.txrsp.valid        := (fsmReg.s_compAck & !fsmReg.s_reqResp) | (fsmReg.s_snpResp & !fsmReg.w_mpResp & !reqReg.retToSrc)
  io.chi.txrsp.bits         := DontCare
  io.chi.txrsp.bits.opcode  := Mux(fsmReg.s_compAck, CHIOp.RSP.CompAck,         mpRespReg.opcode)
  io.chi.txrsp.bits.tgtID   := Mux(fsmReg.s_compAck, reqRespHomeNIdOrSrcIdReg,  reqReg.srcId)
  io.chi.txrsp.bits.srcID   := nrRnSlv.U
  io.chi.txrsp.bits.txnID   := Mux(fsmReg.s_compAck, reqRespDBIDReg,            reqReg.txnId)
  io.chi.txrsp.bits.respErr := Mux(fsmReg.s_compAck, DontCare,                  RespErr.NormalOkay) // TODO: Complete data error indicate
  io.chi.txrsp.bits.resp    := Mux(fsmReg.s_compAck, DontCare,                  mpRespReg.resp)
  io.chi.txrsp.bits.fwdState := Mux(fsmReg.s_compAck, DontCare,                 mpRespReg.fwdStateOpt.getOrElse(0.U))

  /*
   * Send TxDat(WriteData or CompData or SnpRespData)
   */
  io.chi.txdat.valid        := (fsmReg.s_data  | (fsmReg.s_snpResp & reqReg.retToSrc)) & !fsmReg.w_mpResp & fsmReg.w_dbData & io.dataFDBVal
  io.chi.txdat.bits         := DontCare
  //                                                                      [WriteData]         [CompData]                [SnpRespData]
  io.chi.txdat.bits.opcode  := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  mpRespReg.opcode,   CHIOp.DAT.CompData),      mpRespReg.opcode)
  io.chi.txdat.bits.tgtID   := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  reqReg.tgtId,       reqReg.fwdNIdOpt.get),    reqReg.srcId)
  io.chi.txdat.bits.srcID   := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  rnMasId.U,          rnMasId.U),               rnMasId.U)
  io.chi.txdat.bits.txnID   := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  reqRespDBIDReg,     reqReg.fwdTxnIdOpt.get),  reqReg.txnId)
  io.chi.txdat.bits.homeNID := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  DontCare,           reqReg.srcId),            DontCare)
  io.chi.txdat.bits.respErr := RespErr.NormalOkay // TODO: Complete data error indicate
  io.chi.txdat.bits.resp    := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  mpRespReg.resp,     mpRespReg.fwdStateOpt.getOrElse(0.U)), mpRespReg.resp)
  io.chi.txdat.bits.fwdState := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp, DontCare,           DontCare),                mpRespReg.fwdStateOpt.getOrElse(0.U))
  io.chi.txdat.bits.dbID    := Mux(fsmReg.s_data, Mux(!fsmReg.s_snpResp,  DontCare,           reqReg.txnId),            DontCare)
  // Count
  sendTxDatNumReg           := Mux(release, 0.U, sendTxDatNumReg + io.chi.txdat.fire)
  reqSendAllData            := sendTxDatNumReg === nrBeat.U       | (sendTxDatNumReg === (nrBeat - 1).U & io.chi.txdat.fire)
  when(CHIOp.SNP.isSnpXFwd(reqReg.opcode)){
    snpSendAllData          := sendTxDatNumReg === (nrBeat * 2).U | (sendTxDatNumReg === (nrBeat * 2 - 1).U & io.chi.txdat.fire)
  }.otherwise{
    snpSendAllData          := sendTxDatNumReg === nrBeat.U       | (sendTxDatNumReg === (nrBeat - 1).U & io.chi.txdat.fire)
  }



// ---------------------------  Receive resp2Node / Send req2Slice and resp2Slice --------------------------------//
  /*
   * Receive Resp From Slice
   */
  mpRespReg := Mux(io.resp2Node.fire, io.resp2Node.bits, mpRespReg)

  /*
     * Send Req To Slice
     */
  io.req2Slice.valid            := fsmReg.s_snp2mshr
  io.req2Slice.bits.opcode      := reqReg.opcode
  io.req2Slice.bits.addr        := reqReg.addr
  io.req2Slice.bits.isSnp       := true.B
  io.req2Slice.bits.willSnp     := true.B
  if (djparam.useDCT) io.req2Slice.bits.srcIDOpt.get := reqReg.srcId
  if (djparam.useDCT) io.req2Slice.bits.txnIDOpt.get := reqReg.txnId
  // IdMap
  io.req2Slice.bits.to.idL0     := SLICE
  io.req2Slice.bits.to.idL1     := parseAddress(reqReg.addr)._2 // Remap in Xbar
  io.req2Slice.bits.to.idL2     := DontCare
  io.req2Slice.bits.from.idL0   := RNSLV
  io.req2Slice.bits.from.idL1   := rnMasId.U
  io.req2Slice.bits.from.idL2   := reqBufId.U
  // Use in RnMaster
  io.req2Slice.bits.retToSrc    := reqReg.retToSrc
  io.req2Slice.bits.doNotGoToSD := reqReg.doNotGoToSD

  /*
   * Send Resp To Slice
   * send update MSHR and send snoop resp also use resp2Slice
   */
  io.resp2Slice.valid           := (fsmReg.s_snpUdpMSHR | fsmReg.s_reqUdpMSHR) & PopCount(fsmReg.asUInt) === 1.U // only udpMSHR need to do
  io.resp2Slice.bits.resp       := reqRespReg
  io.resp2Slice.bits.isSnpResp  := false.B
  io.resp2Slice.bits.dbid       := dbidReg
  io.resp2Slice.bits.mshrSet    := parseMSHRAddress(reqReg.addr)._1
  if(djparam.useDCT) io.resp2Slice.bits.fwdStateOpt.get := DontCare
  // IdMap
  io.resp2Slice.bits.to         := Mux(fsmReg.s_snpUdpMSHR, mpRespReg.from, reqReg.from)


// -----------------------------------------------  DataBuffer Ctrl Signals  ------------------------------------------//
  /*
   * Send Read and Clean Req to DataBuffer
   */
  //                            WriteBack Data
  io.dbRCReq.valid          := (fsmReg.s_rcDB & fsmReg.s_data & !fsmReg.w_dbidResp & fsmReg.s_reqUdpMSHR) |
  //                            SnpRespDataFwd
                               (fsmReg.s_rcDB & fsmReg.s_snpResp & !fsmReg.s_data & fsmReg.s_snpUdpMSHR)
  io.dbRCReq.bits.isRead    := true.B
  io.dbRCReq.bits.isClean   := true.B
  io.dbRCReq.bits.to        := Mux(fsmReg.s_reqUdpMSHR, reqReg.from, mpRespReg.from)
  io.dbRCReq.bits.from.idL0 := RNSLV
  io.dbRCReq.bits.from.idL1 := rnMasId.U
  io.dbRCReq.bits.from.idL2 := reqBufId.U
  io.dbRCReq.bits.mshrSet   := parseMSHRAddress(reqReg.addr)._2
  io.dbRCReq.bits.dbid      := DontCare
  io.dbRCReq.bits.useDBID   := false.B



  /*
   * Send wReq to get dbid
   */
  io.wReq.valid           := fsmReg.s_getDBID
  // IdMap
  io.wReq.bits.to.idL0    := SLICE
  io.wReq.bits.to.idL1    := parseAddress(reqReg.addr)._2 // Remap in Xbar
  io.wReq.bits.to.idL2    := DontCare
  io.wReq.bits.from.idL0  := RNSLV
  io.wReq.bits.from.idL1  := rnMasId.U
  io.wReq.bits.from.idL2  := reqBufId.U

  /*
   * Receive dbid from wResp
   */
  dbidReg       := Mux(io.wResp.fire, io.wResp.bits.dbid, dbidReg)
  dbidBankIdReg := Mux(io.wResp.fire, io.wResp.bits.from.idL1, dbidBankIdReg)

  /*
  * Count data get from DataBuffer number
  */
  getDBNumReg   := Mux(release, 0.U, getDBNumReg + io.dataFDBVal.asUInt)


// ---------------------------  Other Signals  --------------------------------//
  /*
   * getAllData logic
   */
  getAllData :=                                         getRxDatNumReg === nrBeat.U     | (getRxDatNumReg === (nrBeat - 1).U & io.chi.rxdat.fire) | //get data from rxdat
                Mux(CHIOp.SNP.isSnpXFwd(reqReg.opcode), getDBNumReg === nrBeat.U        | (getDBNumReg === (nrBeat - 1).U & io.dataFDBVal),         //get data from db without fwd
                                                        getDBNumReg === (nrBeat * 2).U  | (getDBNumReg === (nrBeat * 2 - 1).U & io.dataFDBVal))     //get data from db with fwd

  /*
   * Output reqBufDBID
   */
  io.reqBufDBID.valid := fsmReg.w_hnResp & !fsmReg.w_dbid
  io.reqBufDBID.bits.dbid := dbidReg
  io.reqBufDBID.bits.bankId := dbidBankIdReg

  /*
   * Set io ready value
   */
  io.chi.rxsnp.ready := true.B
  io.chi.rxrsp.ready := true.B
  io.chi.rxdat.ready := true.B
  io.req2Node.ready  := true.B
  io.resp2Node.ready := true.B
  io.wResp.ready     := true.B


  // ---------------------------  Assertion  --------------------------------//
  // when it is free, it can receive or send mes
  assert(Mux(io.free, !io.chi.txreq.valid, true.B))
  assert(Mux(io.free, !io.chi.txrsp.valid, true.B))
  assert(Mux(io.free, !io.chi.txdat.valid, true.B))
  assert(Mux(io.free, !io.chi.rxdat.valid, true.B))
  assert(Mux(io.free, !io.chi.rxrsp.valid, true.B))
  assert(Mux(io.free, !io.req2Slice.valid, true.B))
  assert(Mux(io.free, !io.resp2Node.valid, true.B))
  assert(Mux(io.free, !io.resp2Slice.valid, true.B))
  assert(Mux(io.free, !io.reqBufDBID.valid, true.B))
  assert(Mux(io.free, !io.dbRCReq.valid, true.B))
  assert(Mux(io.free, !io.wReq.valid, true.B))
  assert(Mux(io.free, !io.wResp.valid, true.B))
  assert(Mux(io.free, !io.dataFDBVal, true.B))

  assert(Mux(!freeReg, !(io.chi.rxsnp.valid | io.req2Node.valid), true.B), "When ReqBuf valid, it cant input new req")
  assert(Mux(io.chi.rxsnp.valid | io.req2Node.valid, io.free, true.B), "Reqbuf cant block req input")
  assert(!(io.chi.rxsnp.valid & io.req2Node.valid), "Reqbuf cant receive rxsnp and reqTask at the same time")
  when(release) {
    assert(fsmReg.asUInt === 0.U, "when ReqBuf release, all task should be done")
  }
  assert(Mux(getDBNumReg === (nrBeat * 2).U, !io.dataFDBVal, true.B), "ReqBuf get data from DataBuf overflow")
  assert(Mux(io.dataFDBVal, fsmReg.s_data & fsmReg.w_dbData, true.B), "When dbDataValid, ReqBuf should set s_data and w_dbData")
  assert(Mux(io.dataFDBVal, !fsmReg.w_mpResp, true.B), "When dataFDBVal, ReqBuf should has been receive mpResp")

  assert(Mux(fsmReg.s_snpResp & io.chi.rxrsp.fire, !io.chi.rxrsp.bits.resp(2), true.B))

  val cntReg = RegInit(0.U(64.W))
  cntReg := Mux(io.free, 0.U, cntReg + 1.U)
  assert(cntReg < TIMEOUT_RB.U, "REQBUF[0x%x] ADDR[0x%x] OP[0x%x] SNP[0x%x] TIMEOUT", reqBufId.U, reqReg.addr, reqReg.opcode, reqReg.from.isSLICE.asUInt)



















}