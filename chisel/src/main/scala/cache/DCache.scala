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

class DCache extends Module with CacheConfig with MemAccessType {
    val io = IO(new CacheIO)

    val data = Seq.tabulate(nway) { i => Module(new DualPortBRAM(DATA_WIDTH = cachelineBits, nline))}
    val meta = Seq.tabulate(nway) { i => Module(new MetaDataBRAM) }
    
    for (i <- 0 until nway) {
        data(i).io.clk := clock
        data(i).io.rst := reset
    }
    
    val byteOffset = log2Ceil(dataWidth >> 3)
    //----------accept req----------
    val arOffset = io.cpu.req.bits.addr(offsetBits - 1, offsetBits - log2Ceil(cachelineBits / dataWidth))
    val arIndex  = io.cpu.req.bits.addr(indexBits + offsetBits - 1, offsetBits)
    val arTag    = io.cpu.req.bits.addr(addrWidth - 1, addrWidth - tagBits)

    //----------hit process---------
    val hasHit      = Wire(Bool())
    val hpStall     = Wire(Bool())
    // val hpFetchLine = Vec(nway, chiselTypeOf(Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))))
    val hpFetchLine = Seq.tabulate(nway) { i => Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W))) }
    // val hpWriteLine = Vec(nway, chiselTypeOf(Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))))
    val hpWriteLine = Seq.tabulate(nway) { i => Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W))) }
    val hpReq       = RegEnable(io.cpu.req.bits, !hpStall)
    val hpIndex     = RegEnable(arIndex,  !hpStall)
    val hpTag       = RegEnable(arTag,    !hpStall)
    val hpOffset    = RegEnable(arOffset, !hpStall)
    val hpValid     = RegInit(false.B)
    val hpRespValid = hpValid && (meta(0).io.hit || meta(1).io.hit)

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
    val mpOffset = RegInit(0.U(offsetBits.W))
    val mpIndex  = RegInit(0.U(indexBits.W))
    val mpTag    = RegInit(0.U(tagBits.W))
    val mpRefillAddr = Cat(mpTag, mpIndex, 0.U(offsetBits.W))
    val mpMask   = Reg(UInt(dataWidth.W))
    val mpShift  = Reg(UInt((byteOffset + 3).W))

    // accept req
    for (i <- 0 until nway) {
        meta(i).io.indexFetch := arIndex
        data(i).io.wea := false.B
        data(i).io.addra := arIndex
        data(i).io.dina := 0.U
    }
    io.bar.resp.ready := true.B
    // hit process
    hasHit  := meta(0).io.hit || meta(1).io.hit // TODO: not general, just temporary
    hpStall := mpStall
    hpValid := Mux((hpValid && !hasHit) || mpStall, false.B, io.cpu.req.valid)
    // val hitWay = Mux(hasHit && meta(1).io.hit, 1.U, 0.U)
    io.dbg.hit    := hasHit
    io.dbg.hitWay := Mux(hasHit && meta(1).io.hit, 1.U, 0.U)
    io.dbg.replaceWay := hpTag(0)

    for (i <- 0 until nway) {
        hpFetchLine(i) := Mux(RegNext(data(i).io.web && data(i).io.addra === data(i).io.addrb),
                                RegNext(data(i).io.dinb.asTypeOf(chiselTypeOf(hpFetchLine(i)))), // forward
                                data(i).io.douta.asTypeOf(chiselTypeOf(hpFetchLine(i))))         // normal
    }

    val hpMask = MuxLookup(hpReq.mtype, "hffffffff".U,
        Seq(
            MEMBYTE.U -> "h000000ff".U,
            MEMHALF.U -> "h0000ffff".U,
        )
    )
    val hpShift = (hpReq.addr(byteOffset - 1, 0) << 3).asUInt()  // 要写入的数据在一个word中的位置
    val hpWriteWord = Wire(Vec(nway, UInt(dataWidth.W)))
    for (i <- 0 until nway) {
        // FIXME
        // val hpWriteWordWire = Wire(UInt(dataWidth.W))
        // hpWriteWordWire := ((hpMask & hpReq.wdata) << hpShift).asUInt() | ((~(hpMask << hpShift)).asUInt() & hpFetchLine(i)(hpOffset).asUInt()).asUInt()
        hpWriteWord(i) := ((hpMask & hpReq.wdata) << hpShift).asUInt() | ((~(hpMask << hpShift)).asUInt() & hpFetchLine(i)(hpOffset).asUInt()).asUInt()
        // hpWriteWord(i) := hpWriteWordWire
        hpWriteLine(i) := hpFetchLine(i)
        hpWriteLine(i)(hpOffset) := hpWriteWord(i)
    }
    
    io.cpu.resp.valid      := hpRespValid || mpRespValid
    val rdata = Wire(Vec(cachelineBits / dataWidth, UInt(dataWidth.W)))
    // for (i <- 0 until nway) {
    //     rdata := Mux(meta(i).io.hit, 0.U.asTypeOf(rdata), hpFetchLine(i))
    // }
    rdata := Mux(hasHit, Mux(meta(0).io.hit, hpFetchLine(0), hpFetchLine(1)), 0.U.asTypeOf(rdata))
    io.cpu.resp.bits.rdata := Mux(mpValid, mpRefillLine(mpOffset) & mpMask, rdata(hpOffset) & hpMask).asTypeOf(chiselTypeOf(io.cpu.resp.bits.rdata))
 
    for (i <- 0 until nway) {
        data(i).io.web := Mux(mpValid, i.U === mpTag(0).asUInt && mpWriteCache, hpValid && meta(i).io.hit && hpReq.wen)
        data(i).io.addrb := Mux(mpValid, mpIndex, hpIndex)
        data(i).io.dinb := Mux(mpValid, Mux(mpReq.wen, mpWriteLine.asUInt(), io.bar.resp.bits.rdata.asUInt()), hpWriteLine(i).asUInt())
    }

    for (i <- 0 until nway) {
        meta(i).io.indexUpdate := Mux(mpValid, mpIndex, hpIndex)
        meta(i).io.tagUpdate   := Mux(mpValid, mpTag, hpTag)
        meta(i).io.write  := Mux(mpValid, mpReq.wen, hpReq.wen)
        meta(i).io.update := Mux(mpValid, false.B, hpValid && meta(i).io.hit && hpReq.wen)
    }
    
    // miss process
    val mpWriteBack :: mpRefill :: Nil = Enum(2)
    val mpState = RegInit(mpWriteBack)

    mpWriteCache := mpValid && mpState === mpRefill && io.bar.resp.valid

    io.bar.req.bits.addr  := Mux(mpValid, Mux(mpState === mpWriteBack, mpDirtyAddr, mpRefillAddr), io.cpu.req.bits.addr)
    io.bar.req.bits.wdata := Mux(mpValid, mpDirtyData, io.cpu.req.bits.wdata)
    io.bar.req.bits.mtype := io.cpu.req.bits.mtype
    io.bar.req.bits.wen   := Mux(mpValid, Mux(mpTag(0), meta(1).io.dirty, meta(0).io.dirty) && mpState === mpWriteBack, false.B)
                                          // io.cpu.req.bits.wen)
    io.bar.req.valid := Mux(mpValid, mpValid && !io.bar.resp.valid, false.B)
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

        when (hpTag(0)) {
            mpState     := Mux(meta(1).io.dirty, mpWriteBack, mpRefill)
            mpDirtyAddr := Cat(meta(1).io.tagFetch, hpIndex, 0.U(offsetBits.W))
            mpDirtyData := hpFetchLine(1).asTypeOf(chiselTypeOf(mpDirtyData))
        }.otherwise {
            mpState     := Mux(meta(0).io.dirty, mpWriteBack, mpRefill)
            mpDirtyAddr := Cat(meta(0).io.tagFetch, hpIndex, 0.U(offsetBits.W))
            mpDirtyData := hpFetchLine(0).asTypeOf(chiselTypeOf(mpDirtyData))
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