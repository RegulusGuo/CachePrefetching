//-------------------------------------
module RAM_B(
    input [31:0] addra,
    input clka,      // normal clock
    input[127:0] dina,
    input wea, 
    output[127:0] douta,
    input[1:0] mtype
);

    reg[7:0] data[0:511];

    initial	begin
        $readmemh("ram.hex", data);
    end

    integer i;
    always @(posedge clk) begin
        if (wea & ~|addra[30:9]) begin
            for (i = 0; i < 16; i = i+1) begin
                data[addra[8:0] + i] <= dina[i<<3 + 7 : i<<3];
            end
        end
    end
    
    assign douta = addra[30:9] ? 128'b0 :
                   {data[addra[8:0] + 15],
                    data[addra[8:0] + 14],
                    data[addra[8:0] + 13],
                    data[addra[8:0] + 12],
                    data[addra[8:0] + 11], 
                    data[addra[8:0] + 10], 
                    data[addra[8:0] + 9], 
                    data[addra[8:0] + 8]
                    data[addra[8:0] + 7],
                    data[addra[8:0] + 6],
                    data[addra[8:0] + 5],
                    data[addra[8:0] + 4],
                    data[addra[8:0] + 3], 
                    data[addra[8:0] + 2], 
                    data[addra[8:0] + 1], 
                    data[addra[8:0]]}

endmodule