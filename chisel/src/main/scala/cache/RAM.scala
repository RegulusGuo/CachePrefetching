package cache

import chisel3._
import chisel3.util._


class DualPortBRAMIO(DATA_WIDTH: Int, DEPTH: Int) extends Bundle {
    val clk   = Input(Clock())
    val rst   = Input(Reset())
    val wea   = Input(Bool())
    val web   = Input(Bool())
    val addra = Input(UInt(log2Ceil(DEPTH).W))
    val addrb = Input(UInt(log2Ceil(DEPTH).W))
    val dina  = Input(UInt(DATA_WIDTH.W))
    val dinb  = Input(UInt(DATA_WIDTH.W))
    val douta = Output(UInt(DATA_WIDTH.W))
    val doutb = Output(UInt(DATA_WIDTH.W))

  override def cloneType = (new DualPortBRAMIO(DATA_WIDTH, DEPTH)).asInstanceOf[this.type]
}


class DualPortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
    val io = IO(new DualPortBRAMIO(DATA_WIDTH, DEPTH))

    val sim_dual_port_bram = Module(new SimDualPortBRAM(DATA_WIDTH, DEPTH))
    sim_dual_port_bram.io <> io
}

class SimDualPortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
    val io = IO(new DualPortBRAMIO(DATA_WIDTH, DEPTH))

    val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

    io.douta := RegNext(mem(io.addra))
    io.doutb := RegNext(mem(io.addrb))

    when(io.wea) {
        mem(io.addra) := io.dina
    }
    when(io.web) {
        mem(io.addrb) := io.dinb
    }
}


// wrapper class for dual_port_ram
class XPMDualPortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends BlackBox(
  Map("DATA_WIDTH" -> DATA_WIDTH, "DEPTH" -> DEPTH)
) with HasBlackBoxInline {

  override val desiredName = "dual_port_bram"

  val io = IO(new DualPortBRAMIO(DATA_WIDTH, DEPTH))

  setInline(
    "dual_port_bram.v",
    s"""
       |module dual_port_bram #(
       |	parameter DATA_WIDTH = 32,
       |	parameter DEPTH      = 1024,
       |	parameter LATENCY    = 1,
       |	parameter LATENCY_A  = LATENCY,
       |	parameter LATENCY_B  = LATENCY
       |) (
       |	input  clk,
       |	input  rst,
       |	input  wea,
       |	input  web,
       |	input  [$$clog2(DEPTH)-1:0] addra,
       |	input  [$$clog2(DEPTH)-1:0] addrb,
       |	input  [DATA_WIDTH-1:0] dina,
       |	input  [DATA_WIDTH-1:0] dinb,
       |	output [DATA_WIDTH-1:0] douta,
       |	output [DATA_WIDTH-1:0] doutb
       |);
       |
       |// xpm_memory_tdpram: True Dual Port RAM
       |// Xilinx Parameterized Macro, Version 2016.2
       |xpm_memory_tdpram #(
       |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
       |	.MEMORY_PRIMITIVE("auto"),
       |	.CLOCKING_MODE("common_clock"),
       |	.USE_MEM_INIT(0),
       |	.WAKEUP_TIME("disable_sleep"),
       |	.MESSAGE_CONTROL(0),
       |
       |	// Port A module parameters
       |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
       |	.READ_DATA_WIDTH_A(DATA_WIDTH),
       |	.READ_RESET_VALUE_A("0"),
       |	.READ_LATENCY_A(LATENCY_A),
       |	.WRITE_MODE_A("read_first"),
       |
       |	// Port B module parameters
       |	.WRITE_DATA_WIDTH_B(DATA_WIDTH),
       |	.READ_DATA_WIDTH_B(DATA_WIDTH),
       |	.READ_RESET_VALUE_B("0"),
       |	.READ_LATENCY_B(LATENCY_B),
       |	.WRITE_MODE_B("read_first")
       |) xpm_mem (
       |	.sleep          (1'b0 ),
       |	// Port A module ports
       |	.clka           (clk  ),
       |	.rsta           (rst  ),
       |	.ena            (1'b1 ),
       |	.regcea         (1'b0 ),
       |	.wea            (wea  ),
       |	.addra          (addra),
       |	.dina           (dina ),
       |	.injectsbiterra (1'b0 ),
       |	.injectdbiterra (1'b0 ),
       |	.douta          (douta),
       |	.sbiterra       (     ),
       |	.dbiterra       (     ),
       |
       |	// Port B module ports
       |	.clkb           (clk  ),
       |	.rstb           (rst  ),
       |	.enb            (1'b1 ),
       |	.regceb         (1'b0 ),
       |	.web            (web  ),
       |	.addrb          (addrb),
       |	.dinb           (dinb ),
       |	.injectsbiterrb (1'b0 ),
       |	.injectdbiterrb (1'b0 ),
       |	.doutb          (doutb),
       |	.sbiterrb       (     ),
       |	.dbiterrb       (     )
       |);
       |
       |endmodule
       |""".stripMargin
  )
}

///////////////////////////////////////////////////////////////////////

class DualPortLUTRAMIO(DATA_WIDTH: Int, DEPTH: Int) extends Bundle {
    val clk   = Input(Clock())
    val rst   = Input(Reset())
    val wea   = Input(Bool())
    val addra = Input(UInt(log2Ceil(DEPTH).W))
    val addrb = Input(UInt(log2Ceil(DEPTH).W))
    val dina  = Input(UInt(DATA_WIDTH.W))
    val douta = Output(UInt(DATA_WIDTH.W))
    val doutb = Output(UInt(DATA_WIDTH.W))

    override def cloneType = (new DualPortLUTRAMIO(DATA_WIDTH, DEPTH)).asInstanceOf[this.type]
}

class DualPortLUTRAM(DATA_WIDTH: Int, DEPTH: Int, verilator: Boolean = false) extends Module {
    val io = IO(new DualPortLUTRAMIO(DATA_WIDTH, DEPTH))

    if (verilator) {
        val sim_dual_port_lutram = Module(new SimDualPortLUTRAM(DATA_WIDTH, DEPTH))
        sim_dual_port_lutram.io <> io
    } else {
        val xpm_dual_port_lutram = Module(new XPMDualPortLUTRAM(DATA_WIDTH, DEPTH))
        xpm_dual_port_lutram.io <> io
    }
}

class SimDualPortLUTRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
    val io  = IO(new DualPortLUTRAMIO(DATA_WIDTH, DEPTH))
    val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

    io.douta := mem(io.addra)
    io.doutb := mem(io.addrb)

    when(io.wea) {
        mem(io.addra) := io.dina
    }
}


// wrapper class for dual_port_lutram
class XPMDualPortLUTRAM(DATA_WIDTH: Int, DEPTH: Int) extends BlackBox(
  Map("DATA_WIDTH" -> DATA_WIDTH, "DEPTH" -> DEPTH)
) with HasBlackBoxInline {

  override val desiredName = "dual_port_lutram"

  val io = IO(new DualPortLUTRAMIO(DATA_WIDTH, DEPTH))

  setInline(
    "dual_port_lutram.v",
    s"""
       |module dual_port_lutram #(
       |	parameter DATA_WIDTH = 32,
       |	parameter DEPTH      = 1024,
       |	parameter LATENCY    = 0,
       |	parameter LATENCY_A  = LATENCY,
       |	parameter LATENCY_B  = LATENCY
       |) (
       |	input  clk,
       |	input  rst,
       |	input  wea,
       |	input  [$$clog2(DEPTH)-1:0] addra,
       |	input  [$$clog2(DEPTH)-1:0] addrb,
       |	input  [DATA_WIDTH-1:0] dina,
       |	output [DATA_WIDTH-1:0] douta,
       |	output [DATA_WIDTH-1:0] doutb
       |);
       |
       |xpm_memory_dpdistram #(
       |	// Common module parameters
       |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
       |	.CLOCKING_MODE("common_clock"),
       |	.USE_MEM_INIT(0),
       |	.MESSAGE_CONTROL(0),
       |
       |	// Port A module parameters
       |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
       |	.READ_DATA_WIDTH_A(DATA_WIDTH),
       |	.READ_RESET_VALUE_A("0"),
       |	.READ_LATENCY_A(LATENCY_A),
       |
       |	// Port B module parameters
       |	.READ_DATA_WIDTH_B(DATA_WIDTH),
       |	.READ_RESET_VALUE_B("0"),
       |	.READ_LATENCY_B(LATENCY_B)
       |) xpm_mem (
       |	// Port A module ports
       |	.clka           (clk  ),
       |	.rsta           (rst  ),
       |	.ena            (1'b1 ),
       |	.regcea         (1'b0 ),
       |	.wea            (wea  ),
       |	.addra          (addra),
       |	.dina           (dina ),
       |	.douta          (douta),
       |
       |	// Port B module ports
       |	.clkb           (clk  ),
       |	.rstb           (rst  ),
       |	.enb            (1'b1 ),
       |	.regceb         (1'b0 ),
       |	.addrb          (addrb),
       |	.doutb          (doutb)
       |);
       |
       |endmodule
       |""".stripMargin
  )
}

///////////////////////////////////////////////////////////////////////////////

class SinglePortBRAMIO(DATA_WIDTH: Int, DEPTH: Int) extends Bundle {
    val clk  = Input(Clock())
    val rst  = Input(Reset())
    val we   = Input(Bool())
    val addr = Input(UInt(log2Ceil(DEPTH).W))
    val din  = Input(UInt(DATA_WIDTH.W))
    val dout = Output(UInt(DATA_WIDTH.W))

    override def cloneType = (new SinglePortBRAMIO(DATA_WIDTH, DEPTH)).asInstanceOf[this.type]
}

class SinglePortBRAM(DATA_WIDTH: Int, DEPTH: Int, verilator: Boolean = false, mmio: Boolean = false, lut: Boolean = false) extends Module {
    val io = IO(new SinglePortBRAMIO(DATA_WIDTH, DEPTH))

    if (mmio) {
        io.dout := 0.U
    } else if (verilator || lut) {
        val sim_single_port_bram = Module(new SimSinglePortBRAM(DATA_WIDTH, DEPTH))
        sim_single_port_bram.io <> io
    } else {
        val xpm_single_port_bram = Module(new XPMSinglePortBRAM(DATA_WIDTH, DEPTH))
        xpm_single_port_bram.io <> io
    }
}

class SimSinglePortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
    val io = IO(new SinglePortBRAMIO(DATA_WIDTH, DEPTH))

    val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

    io.dout := RegNext(mem(io.addr))

    when(io.we) {
        mem(io.addr) := io.din
    }
}

// wrapper class for single_port_ram
class XPMSinglePortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends BlackBox(
  Map("DATA_WIDTH" -> DATA_WIDTH, "DEPTH" -> DEPTH)
) with HasBlackBoxInline {

    override val desiredName = "single_port_bram"

    val io = IO(new SinglePortBRAMIO(DATA_WIDTH, DEPTH))

    setInline(
      "single_port_bram.v",
      s"""
         |module single_port_bram # (
         |  parameter DATA_WIDTH = 32,
         |	parameter DEPTH      = 1024,
         |	parameter LATENCY    = 1
         |)(
         |	input  clk,
         |	input  rst,
         |	input  we,
         |	input  [$$clog2(DEPTH)-1:0] addr,
         |	input  [DATA_WIDTH-1:0]  din,
         |	output [DATA_WIDTH-1:0]  dout
         |);
         |
         |// xpm_memory_spram: Single Port RAM
         |// Xilinx Parameterized Macro, Version 2016.2
         |xpm_memory_spram #(
         |	// Common module parameters
         |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
         |	.MEMORY_PRIMITIVE("auto"),
         |	.USE_MEM_INIT(0),
         |	.WAKEUP_TIME("disable_sleep"),
         |	.MESSAGE_CONTROL(0),
         |	// Port A module parameters
         |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
         |	.READ_DATA_WIDTH_A(DATA_WIDTH),
         |	.READ_RESET_VALUE_A("0"),
         |	.READ_LATENCY_A(LATENCY),
         |	.WRITE_MODE_A("write_first")
         |) xpm_mem (
         |	// Common module ports
         |	.sleep          ( 1'b0  ),
         |	// Port A module ports
         |	.clka           ( clk   ),
         |	.rsta           ( rst   ),
         |	.ena            ( 1'b1  ),
         |	.regcea         ( 1'b0  ),
         |	.wea            ( we    ),
         |	.addra          ( addr  ),
         |	.dina           ( din   ),
         |	.injectsbiterra ( 1'b0  ), // do not change
         |	.injectdbiterra ( 1'b0  ), // do not change
         |	.douta          ( dout  ),
         |	.sbiterra       (       ), // do not change
         |	.dbiterra       (       )  // do not change
         |);
         |
         |endmodule
         |""".stripMargin
    )
}