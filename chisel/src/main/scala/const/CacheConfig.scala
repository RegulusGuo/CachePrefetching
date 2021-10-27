package const

import chisel3._
import chisel3.util._

trait CacheConfig {
    val cachelineBits = 128  // 16 Bytes
    val addrWidth     = 32
    val dataWidth     = 32   // == register size
    val nway          = 2
    val nline         = 256
    val offsetBits    = 4
    val indexBits     = 8
    val tagBits       = addrWidth - offsetBits - indexBits
}

trait MemAccessType {
    val MEMBYTE  = 0
    val MEMHALF  = 1
    val MEMWORD  = 2
    val MEMDWORD = 3
    val MEMTYPE  = 2
}