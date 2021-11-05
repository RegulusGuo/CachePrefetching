package cache

import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import const._

// from the view of cache
class CacheCoreReq extends Bundle with CacheConfig with MemAccessType {
    val addr  = UInt(addrWidth.W)
    val wen   = Bool()
    val wdata = UInt(dataWidth.W)
    val mtype = UInt(MEMTYPE.W)
}

class CacheCoreResp extends Bundle with CacheConfig with MemAccessType {
    val rdata = UInt(dataWidth.W)
}

class CacheCoreIO extends Bundle with CacheConfig with MemAccessType {
    val req  = Flipped(Decoupled(new CacheCoreReq)) // input valid, output ready
    val resp = Decoupled(new CacheCoreResp)         // input ready, output valid
}

// from the view of cache
class CacheMemReq extends Bundle with CacheConfig with MemAccessType {
    val addr  = UInt(addrWidth.W)
    val wen   = Bool()
    val wdata = UInt(cachelineBits.W)
    val mtype = UInt(MEMTYPE.W)
    val wenBuf   = if (prefetch) Bool() else null
    val addrBuf  = if (prefetch) UInt(addrWidth.W) else null
    val wdataBuf = if (prefetch) UInt(cachelineBits.W) else null
}

class CacheMemResp extends Bundle with CacheConfig with MemAccessType {
    val rdata    = UInt(cachelineBits.W)
    val rdataBuf = if (prefetch) UInt(cachelineBits.W) else null
}

class CacheMemIO extends Bundle with CacheConfig with MemAccessType {
    val req  = Decoupled(new CacheMemReq)           // input ready, output valid
    val resp = Flipped(Decoupled(new CacheMemResp)) // input valid, output ready
}

class DebugIO extends Bundle with CacheConfig with MemAccessType {
    val hit        = Output(Bool())
    val hitBuf     = if (prefetch) Output(Bool()) else null
    val hitWay     = Output(UInt(1.W))
    val replaceWay = Output(UInt(1.W))
}

class CacheIO extends Bundle with CacheConfig with MemAccessType {
    val cpu = new CacheCoreIO
    val bar = new CacheMemIO
    val dbg = new DebugIO
}