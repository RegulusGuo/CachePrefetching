package cache

import const._
import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.util.experimental._
import chisel3.experimental.BundleLiterals._
import chisel3.util.experimental.BoringUtils
import javax.management.modelmbean.ModelMBean
import scala.annotation.switch

class MetaIODSimple extends Bundle with CacheConfig {
    // TODO: fix TagBits
    val indexFetch  = Input(UInt(indexBits.W))
    val indexUpdate = Input(UInt(indexBits.W))
    val tagUpdate   = Input(UInt(tagBits.W))
    val update      = Input(Bool())
    val write       = Input(Bool())
    val hit         = Output(Bool())
    val tagFetch    = Output(UInt(tagBits.W))
    val dirty       = Output(Bool())
}

class MetaDataBRAM extends Module with CacheConfig {
  // TODO: fix TagBits
  val io = IO(new MetaIODSimple)
  val blk = Module(new DualPortBRAM(tagBits + 2, nline))  // dirty, valid, tag
  // a read port, b write port
  // forwarding for simultaneously read and write
  val dout = Mux(RegNext(blk.io.web && blk.io.addra === blk.io.addrb), RegNext(blk.io.dinb), blk.io.douta)
  val v = dout(tagBits)
  val tag = dout(tagBits - 1, 0)

  blk.io.clk   := clock
  blk.io.rst   := reset

  blk.io.wea   := false.B
  blk.io.addra := io.indexFetch
  blk.io.dina  := 0.U

  blk.io.web   := io.update
  blk.io.addrb := io.indexUpdate
  blk.io.dinb  := Cat(io.write, true.B, io.tagUpdate) // dirty: 1bit, valid: 1bit, tag

  io.dirty     := dout(tagBits + 1)
  io.hit       := v && io.tagUpdate === tag // reg_update || (reg_tag === tag && v)
  io.tagFetch := tag
}

class MetaBufBRAM extends Module with CacheConfig {
    // TODO: fix TagBits
    val io = IO(new MetaIODSimple)
    val blk = Module(new DualPortBRAM(indexBits + tagBits + 2, 1))  // dirty, valid, tag, index
    // a read port, b write port
    // forwarding for simultaneously read and write
    val dout  = Mux(RegNext(blk.io.web && blk.io.addra === blk.io.addrb), RegNext(blk.io.dinb), blk.io.douta)
    val v     = dout(tagBits + indexBits)
    val tag   = dout(tagBits - 1 + indexBits, indexBits)
    val index = dout(indexBits - 1, 0)

    blk.io.clk   := clock
    blk.io.rst   := reset

    blk.io.wea   := false.B
    blk.io.addra := io.indexFetch
    blk.io.dina  := 0.U

    blk.io.web   := io.update
    blk.io.addrb := 0.U
    blk.io.dinb  := Cat(io.write, true.B, io.tagUpdate, io.indexUpdate) // dirty: 1bit, valid: 1bit, tag, index

    io.dirty     := dout(tagBits + 1 + indexBits)
    io.hit       := v && io.tagUpdate === tag && io.indexUpdate === index // reg_update || (reg_tag === tag && v)
    io.tagFetch  := tag
}

class DCache extends Module with CacheConfig with MemAccessType {
    val io = IO(new CacheIO)

    val data = Seq.tabulate(nway) { i => Module(new DualPortBRAM(DATA_WIDTH = cachelineBits, nline))}
    val meta = Seq.tabulate(nway) { i => Module(new MetaDataBRAM) }
    val dataBuf = if (prefetch) Module(new DualPortBRAM(DATA_WIDTH = cachelineBits, 1)) else null
    val metaBuf = if (prefetch) Module(new MetaBufBRAM) else null
    
    for (i <- 0 until nway) {
        data(i).io.clk := clock
        data(i).io.rst := reset
    }
    if (prefetch) {
        dataBuf.io.clk := clock
        dataBuf.io.rst := reset
    }
    
    val byteOffset = log2Ceil(dataWidth >> 3)
    //----------accept req----------
    val arOffset = io.cpu.req.bits.addr(offsetBits - 1, offsetBits - log2Ceil(cachelineBits / dataWidth))
    val arIndex  = io.cpu.req.bits.addr(indexBits + offsetBits - 1, offsetBits)
    val arTag    = io.cpu.req.bits.addr(addrWidth - 1, addrWidth - tagBits)

    //----------hit process---------
    val hasHit      = Wire(Bool())
    val hitBuf      = if (prefetch) Wire(Bool()) else null
    val hpStall     = Wire(Bool())
    // val hpFetchLine = Vec(nway, chiselTypeOf(Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))))
    val hpFetchLine = Seq.tabulate(nway + 1) { i => Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W))) }
    // val hpWriteLine = Vec(nway, chiselTypeOf(Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))))
    val hpWriteLine = Seq.tabulate(nway + 1) { i => Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W))) }
    val hpReq       = RegEnable(io.cpu.req.bits, !hpStall)
    val hpIndex     = RegEnable(arIndex,  !hpStall)
    val hpTag       = RegEnable(arTag,    !hpStall)
    val hpOffset    = RegEnable(arOffset, !hpStall)
    val hpValid     = RegInit(false.B)
    val hpRespValid = if (prefetch) hpValid && (hasHit || hitBuf) else hpValid && hasHit

    //----------miss process--------
    val mpReq   = RegInit(0.U.asTypeOf(io.cpu.req.bits))
    // val mpReq    = RegInit(0.U.asTypeOf(new CacheCoreReq))
    val mpValid = RegInit(false.B)
    val mpRespValid  = Wire(Bool())
    val mpStall = mpValid && !mpRespValid
    val mpWriteCache = Wire(Bool())
    val mpWriteLine  = Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))
    val mpRefillLine = Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))
    val mpDirtyAddr  = Reg(UInt(addrWidth.W))
    val mpDirtyData  = Reg(UInt(cachelineBits.W))
    val mpDirtyBufAddr = if (prefetch) Reg(UInt(addrWidth.W)) else null
    val mpDirtyBufData = if (prefetch) Reg(UInt(cachelineBits.W)) else null
    val mpOffset = RegInit(0.U(offsetBits.W))
    val mpIndex  = RegInit(0.U(indexBits.W))
    val mpTag    = RegInit(0.U(tagBits.W))
    val mpRefillAddr = Cat(mpTag, mpIndex, 0.U(offsetBits.W))
    val mpRefillBufAddr = if (prefetch) Wire(UInt(addrWidth.W)) else null
    val mpMask   = Reg(UInt(dataWidth.W))
    val mpShift  = Reg(UInt((byteOffset + 3).W))
    // miss process FSM
    val mpWriteBack :: mpRefill :: Nil = Enum(2)
    val mpState = RegInit(mpWriteBack)

    // accept req
    for (i <- 0 until nway) {
        meta(i).io.indexFetch := arIndex
        data(i).io.wea := false.B
        data(i).io.addra := arIndex
        data(i).io.dina := 0.U
    }
    if (prefetch) {
        metaBuf.io.indexFetch := 0.U
        dataBuf.io.wea   := false.B
        dataBuf.io.addra := 0.U
        dataBuf.io.dina  := 0.U
    }
    io.bar.resp.ready := true.B

    // hit process
    hasHit  := meta(0).io.hit || meta(1).io.hit // TODO: not general, just temporary
    hpStall := mpStall
    hpValid := Mux((hpValid && !hasHit) || mpStall, false.B, io.cpu.req.valid)
    if (prefetch) {
        hpValid := Mux((hpValid && !hasHit && !hitBuf) || mpStall, false.B, io.cpu.req.valid)
    }
    io.dbg.hit    := hasHit
    io.dbg.hitWay := Mux(hasHit && meta(1).io.hit, 1.U, 0.U)
    io.dbg.replaceWay := hpTag(0)
    
    for (i <- 0 until nway) {
        hpFetchLine(i) := Mux(RegNext(data(i).io.web && data(i).io.addra === data(i).io.addrb),
                               RegNext(data(i).io.dinb.asTypeOf(chiselTypeOf(hpFetchLine(i)))), // forward
                               data(i).io.douta.asTypeOf(chiselTypeOf(hpFetchLine(i))))         // normal
    }
    
    if (prefetch) {
        hitBuf := metaBuf.io.hit
        io.dbg.hitBuf := hitBuf
        hpFetchLine(nway) := Mux(RegNext(dataBuf.io.web && dataBuf.io.addra === dataBuf.io.addrb),
                                  RegNext(dataBuf.io.dinb(cachelineBits-1, 0).asTypeOf(chiselTypeOf(hpFetchLine(nway)))), // forward
                                  dataBuf.io.douta(cachelineBits-1, 0).asTypeOf(chiselTypeOf(hpFetchLine(nway))))         // normal
    }

    val hpMask = MuxLookup(hpReq.mtype, "hffffffff".U,
        Seq(
            MEMBYTE.U -> "h000000ff".U,
            MEMHALF.U -> "h0000ffff".U
        )
    )
    val hpShift = (hpReq.addr(byteOffset - 1, 0) << 3).asUInt()  // 要写入的数据在一个word中的位置
    val hpWriteWord = Wire(Vec(nway + 1, UInt(dataWidth.W)))     // +1 for prefetch buffer
    for (i <- 0 until nway) {
        hpWriteWord(i) := ((hpMask & hpReq.wdata) << hpShift).asUInt() | ((~(hpMask << hpShift)).asUInt() & hpFetchLine(i)(hpOffset).asUInt()).asUInt()
        hpWriteLine(i) := hpFetchLine(i)
        hpWriteLine(i)(hpOffset) := hpWriteWord(i)
    }
    if (prefetch) {
        hpWriteWord(nway) := ((hpMask & hpReq.wdata) << hpShift).asUInt() | ((~(hpMask << hpShift)).asUInt() & hpFetchLine(nway)(hpOffset).asUInt()).asUInt()
        hpWriteLine(nway) := hpFetchLine(nway)
        hpWriteLine(nway)(hpOffset) := hpWriteWord(nway)
    }
    
    val rdata = Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))
    rdata := Mux(hasHit, Mux(meta(0).io.hit, hpFetchLine(0), hpFetchLine(1)), 0.U.asTypeOf(rdata))
    if (prefetch) {
        rdata := Mux(hasHit, Mux(meta(0).io.hit, hpFetchLine(0), hpFetchLine(1)), Mux(hitBuf, hpFetchLine(2), 0.U.asTypeOf(rdata)))
    }
    io.cpu.resp.bits.rdata := Mux(mpValid, mpRefillLine(mpOffset) & mpMask, rdata(hpOffset) & hpMask).asTypeOf(chiselTypeOf(io.cpu.resp.bits.rdata))
    io.cpu.resp.valid      := hpRespValid || mpRespValid
 
    for (i <- 0 until nway) {
        data(i).io.web   := Mux(mpValid, i.U === mpTag(0).asUInt && mpWriteCache, hpValid && meta(i).io.hit && hpReq.wen)
        data(i).io.addrb := Mux(mpValid, mpIndex, hpIndex)
        data(i).io.dinb  := Mux(mpValid, Mux(mpReq.wen, mpWriteLine.asUInt(), io.bar.resp.bits.rdata.asUInt()), hpWriteLine(i).asUInt())
    }

    for (i <- 0 until nway) {
        meta(i).io.indexUpdate := Mux(mpValid, mpIndex, hpIndex)
        meta(i).io.tagUpdate   := Mux(mpValid, mpTag, hpTag)
        meta(i).io.write  := Mux(mpValid, mpReq.wen, hpReq.wen)
        meta(i).io.update := Mux(mpValid, false.B, hpValid && meta(i).io.hit && hpReq.wen)
    }
    
    if (prefetch) {
        dataBuf.io.web   := Mux(mpValid, mpWriteCache, hpValid && hitBuf && hpReq.wen)
        dataBuf.io.addrb := 0.U
        dataBuf.io.dinb  := Mux(mpValid, io.bar.resp.bits.rdataBuf.asUInt(), hpWriteLine(nway).asUInt())
        metaBuf.io.indexUpdate := Mux(mpValid, mpRefillBufAddr(indexBits + offsetBits - 1, offsetBits), hpIndex)
        metaBuf.io.tagUpdate   := Mux(mpValid, mpRefillBufAddr(addrWidth - 1, addrWidth - tagBits), hpTag)
        metaBuf.io.write  := Mux(mpValid, mpReq.wen, hpReq.wen)
        metaBuf.io.update := Mux(mpValid, mpState === mpRefill, hpValid && hitBuf && hpReq.wen)
    }

    // miss process
    mpWriteCache := mpValid && mpState === mpRefill && io.bar.resp.valid

    io.bar.req.bits.addr  := Mux(mpValid, Mux(mpState === mpWriteBack, mpDirtyAddr, mpRefillAddr), io.cpu.req.bits.addr)
    io.bar.req.bits.wdata := Mux(mpValid, mpDirtyData, io.cpu.req.bits.wdata)
    io.bar.req.bits.mtype := io.cpu.req.bits.mtype
    io.bar.req.bits.wen   := Mux(mpValid, Mux(mpTag(0), meta(1).io.dirty, meta(0).io.dirty) && mpState === mpWriteBack, false.B)
    io.bar.req.valid := Mux(mpValid, mpValid && !io.bar.resp.valid, false.B)

    if (prefetch) {
        mpDirtyBufAddr  := mpDirtyAddr  + "h10".U
        mpRefillBufAddr := mpRefillAddr + "h10".U
        io.bar.req.bits.addrBuf  := Mux(mpValid, Mux(mpState === mpWriteBack, mpDirtyBufAddr, mpRefillBufAddr), io.cpu.req.bits.addr)
        io.bar.req.bits.wdataBuf := Mux(mpValid, mpDirtyBufData, 0.U)
        io.bar.req.bits.wenBuf   := Mux(mpValid, metaBuf.io.dirty && mpState === mpWriteBack, false.B)
    }

    mpRefillLine     := io.bar.resp.bits.rdata.asTypeOf(chiselTypeOf(mpRefillLine))
    io.cpu.req.ready := io.cpu.resp.valid // FIXME
    
    when (!mpStall) {
        mpValid  := hpValid && !hasHit
        mpReq    := hpReq
        mpIndex  := hpIndex
        mpTag    := hpTag
        mpOffset := hpOffset
        mpMask   := hpMask
        mpShift  := hpShift

        mpState     := Mux(Mux(hpTag(0), meta(1).io.dirty, meta(0).io.dirty), mpWriteBack, mpRefill)
        mpDirtyAddr := Cat(Mux(hpTag(0), meta(1).io.tagFetch, meta(0).io.tagFetch), hpIndex, 0.U(offsetBits.W))
        mpDirtyData := Mux(hpTag(0), hpFetchLine(1).asTypeOf(chiselTypeOf(mpDirtyData)), 
                                     hpFetchLine(0).asTypeOf(chiselTypeOf(mpDirtyData)))
        if (prefetch) {
            mpValid := hpValid && !hasHit && !hitBuf
            mpDirtyBufData := hpFetchLine(nway).asTypeOf(mpDirtyBufData)
        }
    }
    val mpWriteWord = ((mpMask & mpReq.wdata) << mpShift).asUInt() | ((~(mpMask << mpShift)).asUInt() & mpRefillLine(mpOffset))
    mpWriteLine := mpRefillLine
    mpWriteLine(mpOffset) := mpWriteWord
    mpRespValid := false.B
    when (mpValid) {
        switch(mpState) {
            is (mpWriteBack) { // dirty
                mpState := Mux(io.bar.resp.valid, mpRefill, mpState)
            }
            is (mpRefill) { // clean
                when (hpTag(0)) {
                    meta(1).io.update := true.B
                }.otherwise {
                    meta(0).io.update := true.B
                }
                mpRespValid := mpWriteCache
            }
        }
    }
}

object GenCache {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName
    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/"+packageName, "-X", "verilog"),
      Seq(ChiselGeneratorAnnotation(() => new DCache)))
  }
}