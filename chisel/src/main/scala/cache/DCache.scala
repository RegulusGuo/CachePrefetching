package cache

import const._
import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import chisel3.experimental.BundleLiterals._
import chisel3.util.experimental.BoringUtils

class DCache(cachelineBits: Int = 128, addrWidth: Int = 32) extends Module with CacheConfig {
    val io = IO(new Bundle{
        val cpu = new CacheCoreIO(cachelineBits, addrWidth)
        val bar = new CacheMemIO(cachelineBits, addrWidth)
    })
}