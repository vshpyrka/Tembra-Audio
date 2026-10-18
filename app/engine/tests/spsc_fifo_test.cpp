// The clip stream fifo (feature 009, research R5): one producer (the disk streamer), one consumer (the callback),
// power-of-two capacity, partial writes and reads that return what they moved, never blocking, never overwriting.
#include <catch2/catch_test_macros.hpp>

#include <cstddef>
#include <cstdint>
#include <thread>
#include <vector>

#include "timeline/spsc_fifo.h"

TEST_CASE("the capacity rounds up to a power of two", "[timeline][fifo]") {
    REQUIRE(tembra::SpscFifo<float>(1000).capacity() == 1024);
    REQUIRE(tembra::SpscFifo<float>(1024).capacity() == 1024);
    REQUIRE(tembra::SpscFifo<float>(16384 * 2).capacity() == 32768);
    REQUIRE(tembra::SpscFifo<float>(1).capacity() == 1);
    REQUIRE(tembra::SpscFifo<float>(0).capacity() == 1);
}

TEST_CASE("write and read move partial counts and report them", "[timeline][fifo]") {
    tembra::SpscFifo<float> fifo(8);
    REQUIRE(fifo.available() == 0);
    REQUIRE(fifo.freeSpace() == 8);
    const float in[6] = {1, 2, 3, 4, 5, 6};
    REQUIRE(fifo.write(in, 6) == 6);
    REQUIRE(fifo.available() == 6);
    REQUIRE(fifo.freeSpace() == 2);
    REQUIRE(fifo.write(in, 6) == 2);  // only two fit: a partial write
    REQUIRE(fifo.available() == 8);
    REQUIRE(fifo.freeSpace() == 0);
    REQUIRE(fifo.write(in, 6) == 0);  // full: nothing written, nothing overwritten
    float out[16] = {};
    REQUIRE(fifo.read(out, 5) == 5);
    REQUIRE(out[0] == 1.0f);
    REQUIRE(out[4] == 5.0f);
    REQUIRE(fifo.available() == 3);
    REQUIRE(fifo.read(out, 16) == 3);  // partial read
    REQUIRE(out[0] == 6.0f);
    REQUIRE(out[1] == 1.0f);
    REQUIRE(out[2] == 2.0f);
    REQUIRE(fifo.read(out, 1) == 0);
    REQUIRE(fifo.available() == 0);
}

TEST_CASE("wrap-around keeps order and skip discards", "[timeline][fifo]") {
    tembra::SpscFifo<int32_t> fifo(4);
    int32_t value = 0;
    int32_t out[4];
    for (int round = 0; round < 10; ++round) {
        int32_t batch[3] = {value, value + 1, value + 2};
        value += 3;
        REQUIRE(fifo.write(batch, 3) == 3);
        REQUIRE(fifo.read(out, 3) == 3);
        REQUIRE(out[0] == batch[0]);
        REQUIRE(out[1] == batch[1]);
        REQUIRE(out[2] == batch[2]);
    }
    int32_t batch[4] = {100, 101, 102, 103};
    REQUIRE(fifo.write(batch, 4) == 4);
    REQUIRE(fifo.skip(2) == 2);
    REQUIRE(fifo.available() == 2);
    REQUIRE(fifo.read(out, 2) == 2);
    REQUIRE(out[0] == 102);
    REQUIRE(out[1] == 103);
    REQUIRE(fifo.skip(5) == 0);
}

TEST_CASE("reset empties the fifo for a new stream position", "[timeline][fifo]") {
    tembra::SpscFifo<float> fifo(16);
    const float in[4] = {1, 2, 3, 4};
    fifo.write(in, 4);
    fifo.reset();
    REQUIRE(fifo.available() == 0);
    REQUIRE(fifo.freeSpace() == 16);
    REQUIRE(fifo.write(in, 4) == 4);
    float out[4];
    REQUIRE(fifo.read(out, 4) == 4);
    REQUIRE(out[3] == 4.0f);
}

TEST_CASE("a producer and a consumer on two threads lose nothing over a million frames", "[timeline][fifo]") {
    constexpr size_t kTotal = 1'000'000;
    tembra::SpscFifo<float> fifo(4096);
    std::vector<float> received;
    received.reserve(kTotal);
    std::thread producer([&fifo] {
        std::vector<float> chunk(300);
        size_t next = 0;
        while (next < kTotal) {
            const size_t want = std::min(chunk.size(), kTotal - next);
            for (size_t i = 0; i < want; ++i) chunk[i] = static_cast<float>(next + i);
            size_t written = 0;
            while (written < want) {
                written += fifo.write(chunk.data() + written, want - written);
                if (written < want) std::this_thread::yield();
            }
            next += want;
        }
    });
    std::vector<float> chunk(777);
    while (received.size() < kTotal) {
        const size_t got = fifo.read(chunk.data(), chunk.size());
        if (got == 0) {
            std::this_thread::yield();
            continue;
        }
        received.insert(received.end(), chunk.begin(), chunk.begin() + static_cast<std::ptrdiff_t>(got));
    }
    producer.join();
    REQUIRE(received.size() == kTotal);
    bool ordered = true;
    for (size_t i = 0; i < kTotal; ++i) {
        if (received[i] != static_cast<float>(i)) {
            ordered = false;
            break;
        }
    }
    REQUIRE(ordered);
    REQUIRE(fifo.available() == 0);
}
