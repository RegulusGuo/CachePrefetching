package cache

import chisel3._
import const.CacheConfig

// from the view of cache
class CacheCoreReq(cachelineBits: Int = 128, addrWidth: Int = 32) extends Bundle {
    val ready = Input(Bool())
    val addr  = Input(UInt(addrWidth.W))
    val wen   = Input(Bool())
    val wdata = Input(UInt(cachelineBits.W))
}

class CacheCoreResp(cachelineBits: Int = 128, addrWidth: Int = 32) extends Bundle {
    val valid = Output(Bool())
    val rdata = Output(UInt(32.W))
}

class CacheCoreIO(cachelineBits: Int = 128, addrWidth: Int = 32) extends Bundle {
    val req  = new CoreCacheReq(cachelineBits, addrWidth)
    val resp = new CoreCacheResp(cachelineBits, addrWidth)
}

// from the view of cache
class CacheMemReq(cachelineBits: Int = 128, addrWidth: Int = 32) extends Bundle {
    val ready = Output(Bool())
    val addr  = Output(UInt(addrWidth.W))
    val wen   = Output(Bool())
    val wdata = Output(UInt(cachelineBits.W))
}

class CacheMemResp(cachelineBits: Int = 128, addrWidth: Int = 32) extends Bundle {
    val valid = Input(Bool())
    val rdata = Input(UInt(cachelineBits.W))
}

class CacheMemIO(cachelineBits: Int = 128, addrWidth: Int = 32) extends Bundle {
    val req  = new CacheMemReq(cachelineBits, addrWidth)
    val resp = new CacheMemResp(cachelineBits, addrWidth)
}