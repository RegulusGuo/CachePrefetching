package test

import const._
import cache._
import chisel3._
import chisel3.util._
import chisel3.stage._
import chisel3.util.experimental._
import chisel3.experimental.BundleLiterals._
import chisel3.util.experimental.BoringUtils
import javax.management.modelmbean.ModelMBean
import scala.annotation.switch

class DmemIO extends Bundle with CacheConfig with MemAccessType {
    val clka  = Input(Clock())
    val addra = Input(UInt(addrWidth.W))
    val dina  = Input(UInt(cachelineBits.W))
    val wea   = Input(Bool())
    val douta = Output(UInt(cachelineBits.W))

    val addrb = Input(UInt(addrWidth.W))
    val dinb  = Input(UInt(cachelineBits.W))
    val web   = Input(Bool())
    val doutb = Output(UInt(cachelineBits.W))
    val mtype = Input(UInt(MEMTYPE.W))
}

class RAM_B extends BlackBox with HasBlackBoxResource {
    val io = IO(new DmemIO)
    addResource("/RAM_B.v")
}

class TestIO extends Bundle with CacheConfig with MemAccessType {
    val dbg = new DebugIO
    val cpu = new CacheCoreIO
}

class Test extends Module with CacheConfig with MemAccessType {
    val io = IO(new TestIO)

    val dcache = Module(new DCache)
    val dmem   = Module(new RAM_B)
    dmem.io.clka  := clock
    dmem.io.mtype := dcache.io.bar.req.bits.mtype
    // dmem.io.wea   := dcache.io.bar.req.bits.wen
    // dmem.io.addra := dcache.io.bar.req.bits.addr
    // dmem.io.dina  := dcache.io.bar.req.bits.wdata
    // dcache.io.bar.resp.bits.rdata := dmem.io.douta

    // val dmem = Module(new DualPortBRAM(DATA_WIDTH = cachelineBits, 1 << 10))
    // dmem.io.clk := clock
    // dmem.io.rst := reset
    dmem.io.wea   := false.B 
    dmem.io.addra := dcache.io.bar.req.bits.addr
    dmem.io.dina  := 0.U
    dmem.io.web   := dcache.io.bar.req.bits.wen
    dmem.io.addrb := dcache.io.bar.req.bits.addr
    dmem.io.dinb  := dcache.io.bar.req.bits.wdata
    dcache.io.bar.resp.bits.rdata := dmem.io.doutb

    if (prefetch) {
        dmem.io.wea   := dcache.io.bar.req.bits.wenBuf
        dmem.io.addra := dcache.io.bar.req.bits.addrBuf
        dmem.io.dina  := dcache.io.bar.req.bits.wdataBuf
        dcache.io.bar.resp.bits.rdataBuf := dmem.io.douta
    }

    dcache.io.bar.req.ready  := true.B
    dcache.io.bar.resp.valid := true.B

    dcache.io.cpu <> io.cpu
    dcache.io.dbg <> io.dbg
}

object GenTest {
    def main(args: Array[String]): Unit = {
        val packageName = this.getClass.getPackage.getName
        (new chisel3.stage.ChiselStage).execute(
        Array("-td", "build/verilog/"+packageName, "-X", "verilog"),
        Seq(ChiselGeneratorAnnotation(() => new Test)))
    }
}