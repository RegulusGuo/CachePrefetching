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
    val addra = Input(UInt(32.W))
    val dina  = Input(UInt(32.W))
    val wea   = Input(Bool())
    val douta = Output(UInt(32.W))
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
    dmem.io.wea   := dcache.io.bar.req.bits.wen
    dmem.io.addra := dcache.io.bar.req.bits.addr
    dmem.io.dina  := dcache.io.bar.req.bits.wdata
    dcache.io.bar.resp.bits.rdata := dmem.io.douta

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