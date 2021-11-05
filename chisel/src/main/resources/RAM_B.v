//-------------------------------------
module RAM_B(
    input clka,      // normal clock
    input [31: 0] addra,
    input [127:0] dina,
    input         wea, 
    output[127:0] douta,

    input [31: 0] addrb,
    input [127:0] dinb,
    input         web, 
    output[127:0] doutb,
    
    input[1:0] mtype
);

    reg[7:0] dmem[0:1<<14 - 1];

    initial	begin
        $readmemh("ram.hex", dmem);
    end

    always @(posedge clka) begin
        if (wea & ~|addra[30:14]) begin
            dmem[addra[13:0] + 15] <= dina[127 : 120];
            dmem[addra[13:0] + 14] <= dina[119 : 112];
            dmem[addra[13:0] + 13] <= dina[111 : 104];
            dmem[addra[13:0] + 12] <= dina[103 : 96];
            dmem[addra[13:0] + 11] <= dina[95  : 88];
            dmem[addra[13:0] + 10] <= dina[87  : 80];
            dmem[addra[13:0] + 9]  <= dina[79  : 72];
            dmem[addra[13:0] + 8]  <= dina[71  : 64];
            dmem[addra[13:0] + 7]  <= dina[63  : 56];
            dmem[addra[13:0] + 6]  <= dina[55  : 48];
            dmem[addra[13:0] + 5]  <= dina[47  : 40];
            dmem[addra[13:0] + 4]  <= dina[39  : 32];
            dmem[addra[13:0] + 3]  <= dina[31  : 24];
            dmem[addra[13:0] + 2]  <= dina[23  : 16];
            dmem[addra[13:0] + 1]  <= dina[15  : 8];
            dmem[addra[13:0]]      <= dina[7   : 0];
        end 
        if (web & ~|addrb[30:14]) begin
            dmem[addrb[13:0] + 15] <= dinb[127 : 120];
            dmem[addrb[13:0] + 14] <= dinb[119 : 112];
            dmem[addrb[13:0] + 13] <= dinb[111 : 104];
            dmem[addrb[13:0] + 12] <= dinb[103 : 96];
            dmem[addrb[13:0] + 11] <= dinb[95  : 88];
            dmem[addrb[13:0] + 10] <= dinb[87  : 80];
            dmem[addrb[13:0] + 9]  <= dinb[79  : 72];
            dmem[addrb[13:0] + 8]  <= dinb[71  : 64];
            dmem[addrb[13:0] + 7]  <= dinb[63  : 56];
            dmem[addrb[13:0] + 6]  <= dinb[55  : 48];
            dmem[addrb[13:0] + 5]  <= dinb[47  : 40];
            dmem[addrb[13:0] + 4]  <= dinb[39  : 32];
            dmem[addrb[13:0] + 3]  <= dinb[31  : 24];
            dmem[addrb[13:0] + 2]  <= dinb[23  : 16];
            dmem[addrb[13:0] + 1]  <= dinb[15  : 8];
            dmem[addrb[13:0]]      <= dinb[7   : 0];
        end
    end
    
    assign douta = addra[30:14] ? 128'b0 :
                   {dmem[addra[13:0] + 15],
                    dmem[addra[13:0] + 14],
                    dmem[addra[13:0] + 13],
                    dmem[addra[13:0] + 12],
                    dmem[addra[13:0] + 11], 
                    dmem[addra[13:0] + 10], 
                    dmem[addra[13:0] + 9], 
                    dmem[addra[13:0] + 8],
                    dmem[addra[13:0] + 7],
                    dmem[addra[13:0] + 6],
                    dmem[addra[13:0] + 5],
                    dmem[addra[13:0] + 4],
                    dmem[addra[13:0] + 3], 
                    dmem[addra[13:0] + 2], 
                    dmem[addra[13:0] + 1], 
                    dmem[addra[13:0]]};

    assign doutb = addrb[30:14] ? 128'b0 :
                   {dmem[addrb[13:0] + 15],
                    dmem[addrb[13:0] + 14],
                    dmem[addrb[13:0] + 13],
                    dmem[addrb[13:0] + 12],
                    dmem[addrb[13:0] + 11], 
                    dmem[addrb[13:0] + 10], 
                    dmem[addrb[13:0] + 9], 
                    dmem[addrb[13:0] + 8],
                    dmem[addrb[13:0] + 7],
                    dmem[addrb[13:0] + 6],
                    dmem[addrb[13:0] + 5],
                    dmem[addrb[13:0] + 4],
                    dmem[addrb[13:0] + 3], 
                    dmem[addrb[13:0] + 2], 
                    dmem[addrb[13:0] + 1], 
                    dmem[addrb[13:0]]};

endmodule