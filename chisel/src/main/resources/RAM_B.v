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

    always @(posedge clka) begin
        if (wea & ~|addra[30:9]) begin
            data[addra[8:0] + 15] <= dina[127 : 120];
            data[addra[8:0] + 14] <= dina[119 : 112];
            data[addra[8:0] + 13] <= dina[111 : 104];
            data[addra[8:0] + 12] <= dina[103 : 96];
            data[addra[8:0] + 11] <= dina[95  : 88];
            data[addra[8:0] + 10] <= dina[87  : 80];
            data[addra[8:0] + 9]  <= dina[79  : 72];
            data[addra[8:0] + 8]  <= dina[71  : 64];
            data[addra[8:0] + 7]  <= dina[63  : 56];
            data[addra[8:0] + 6]  <= dina[55  : 48];
            data[addra[8:0] + 5]  <= dina[47  : 40];
            data[addra[8:0] + 4]  <= dina[39  : 32];
            data[addra[8:0] + 3]  <= dina[31  : 24];
            data[addra[8:0] + 2]  <= dina[23  : 16];
            data[addra[8:0] + 1]  <= dina[15  : 8];
            data[addra[8:0]]      <= dina[7   : 0];
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
                    data[addra[8:0] + 8],
                    data[addra[8:0] + 7],
                    data[addra[8:0] + 6],
                    data[addra[8:0] + 5],
                    data[addra[8:0] + 4],
                    data[addra[8:0] + 3], 
                    data[addra[8:0] + 2], 
                    data[addra[8:0] + 1], 
                    data[addra[8:0]]};

endmodule