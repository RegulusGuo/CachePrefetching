DIR  :=	$(CURDIR)


default: all

all:
	$(MAKE) test
	$(MAKE) cache

test:
	sbt "runMain test.GenTest"
	-@rm $(DIR)/build/verilog/test/Test.fir
	-@rm $(DIR)/build/verilog/test/Test.anno.json
	-@cat $(DIR)/build/verilog/test/RAM_B.v >> $(DIR)/build/verilog/test/Test.v
	-@cp ./build/verilog/test/Test.v ../verilog/Test.v

cache:
	sbt "runMain cache.GenCache"
	-@rm $(DIR)/build/verilog/cache/DCache.fir
	-@rm $(DIR)/build/verilog/cache/DCache.anno.json
	-@cp ./build/verilog/cache/DCache.v ../verilog/DCache.v


clean:
	-@rm -rf build/

.PHONY: clean