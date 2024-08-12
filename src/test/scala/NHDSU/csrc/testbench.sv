`timescale 1ns/1ps
module testbench;
    reg clk;
    reg en;
    reg wen;
    reg [63:0] addr;
    reg [63:0] wdata;
    wire [63:0] rdata;

    
    
    initial begin
         clk = 0;
         forever #5 clk = ~clk;
    end

    import "DPI-C" function void mem_write_helper(input byte wen, input longint  addr , input longint wdata );
    import "DPI-C" function longint mem_read_helper(input byte en, input longint addr);

    always @(posedge clk) begin
        if(wen) begin
            mem_write_helper(wen, addr, wdata);
        end
    end
    assign rdata = mem_read_helper(en, addr);

    initial begin
        en = 0;
        wen = 0;
        addr = 0;
        wdata =64'h0;
        #10;
        addr = 64'h8;
        wdata = 64'h DEA;
        wen = 1;
        #10 wen = 0;

        #10 addr = 64'h8;
        en = 1;
        #100

        $display("Read data : %h", rdata);
        $finish;
    end

    initial begin
    $dumpfile("testbench.fsdb");
    $dumpvars(0, testbench);
    end

    
     

endmodule