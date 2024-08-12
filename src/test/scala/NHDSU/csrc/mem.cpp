#include <stdio.h>
#ifdef __cplusplus
#include <map>
#include <unordered_map>
#endif

#include <cassert>
using namespace std;

typedef __uint64_t addr_t;
map<addr_t, __uint64_t> mem;

__uint64_t _mem_read(addr_t idx){
    __uint64_t rdata = 0;
    rdata = mem[idx];
    return rdata;
}
extern "C" __uint64_t mem_read_helper(__uint8_t ren, addr_t idx){
    if(!ren){
        return 0;
    }
    return _mem_read(idx);
}

void _mem_write(addr_t idx, __uint64_t wdata){
    mem[idx] = wdata;
}
extern "C" void mem_write_helper(__uint8_t wen, addr_t idx, __uint64_t wdata){
    if(wen){
        _mem_write(idx, wdata);
    }
}