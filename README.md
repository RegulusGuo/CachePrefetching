# Hardware Prefetching


## Data Prefetching

### Stream Buffer

<img src="https://upload.wikimedia.org/wikipedia/commons/0/08/CachePrefetching_StreamBuffers.png" alt="A typical stream buffer setup as originally proposed" style="zoom:80%;" />



+ Whenever the prefetch mechanism detects a miss on a memory block, say A, it allocates a stream to begin prefetching successive blocks from the missed block onward. If the stream buffer can hold 4 blocks, then we would prefetch A+1, A+2, A+3, A+4 and hold those in the allocated stream buffer. If the processor consumes A+1 next, then it shall be moved "up" from the stream buffer to the processor's cache. The first entry of the stream buffer would now be A+2 and so on. This pattern of prefetching successive blocks is called **Sequential Prefetching**. It is mainly used when contiguous locations are to be prefetched. For example, it is used when prefetching instructions.

## Instruction Prefetching

## How to set up

### Requirement

+ JDK
+ sbt

### Project

Branch `main` is the cache without stream buffer.

The version with stream buffer:
```
git checkout hw_prefetch
```

You can also turn off `prefetch` by set:
```scala
val prefetch = false
```
in CacheConfig.scala

Generate Verilog:
```
cd chisel
make
```
P.S. `make` generates both Test.v and DCache.v, Test.v is for simulation; if you only need DCache.v, please run:
```
make cache
```
