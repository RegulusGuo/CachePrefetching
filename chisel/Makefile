DIR  :=	$(CURDIR)


default: cache

test:
	sbt "runMain test.GenTest"
	-@cp ./build/verilog/test/Test.v ../verilog/Test.v
	-@rm $(DIR)/build/verilog/test/Test.fir
	-@rm $(DIR)/build/verilog/test/Test.anno.json
	-@cat $(DIR)/build/verilog/test/RAM_B.v >> $(DIR)/build/verilog/test/Test.v

cache:
	sbt "runMain cache.GenCache"
	-@cp ./build/verilog/cache/DCache.v ../verilog/DCache.v
	-@rm $(DIR)/build/verilog/cache/DCache.fir
	-@rm $(DIR)/build/verilog/cache/DCache.anno.json


clean:
	-@rm -rf build/

.PHONY: clean