// The lock-free single-producer single-consumer ring buffer between the input callback and the analysis thread.
#include <catch2/catch_test_macros.hpp>

#include <cstddef>
#include <vector>

#include "tuner/ring_buffer.h"

namespace {

std::vector<float> ramp(size_t count, float start = 0.0f) {
    std::vector<float> values(count);
    for (size_t i = 0; i < count; ++i) values[i] = start + static_cast<float>(i);
    return values;
}

}  // namespace

TEST_CASE("push makes frames available", "[ring]") {
    tembra::SpscRingBuffer<float> ring(16384);
    REQUIRE(ring.capacity() == 16384);
    REQUIRE(ring.available() == 0);
    const auto block = ramp(256);
    ring.push(block.data(), block.size());
    REQUIRE(ring.available() == 256);
    REQUIRE(ring.overruns() == 0);
}

TEST_CASE("read waits for a full hop and then consumes it", "[ring]") {
    tembra::SpscRingBuffer<float> ring(16384);
    std::vector<float> out(1024);
    for (int block = 0; block < 3; ++block) {
        const auto data = ramp(256, static_cast<float>(block * 256));
        ring.push(data.data(), data.size());
        REQUIRE_FALSE(ring.read(out.data(), 1024));
    }
    const auto data = ramp(256, 768.0f);
    ring.push(data.data(), data.size());
    REQUIRE(ring.available() == 1024);
    REQUIRE(ring.read(out.data(), 1024));
    REQUIRE(ring.available() == 0);
    for (size_t i = 0; i < 1024; ++i) REQUIRE(out[i] == static_cast<float>(i));
}

TEST_CASE("wrap-around preserves order", "[ring]") {
    tembra::SpscRingBuffer<float> ring(1024);
    std::vector<float> out(768);
    auto first = ramp(768);
    ring.push(first.data(), first.size());
    REQUIRE(ring.read(out.data(), 768));
    // The next 768 frames straddle the end of the storage.
    auto second = ramp(768, 1000.0f);
    ring.push(second.data(), second.size());
    REQUIRE(ring.available() == 768);
    REQUIRE(ring.read(out.data(), 768));
    for (size_t i = 0; i < 768; ++i) REQUIRE(out[i] == 1000.0f + static_cast<float>(i));
}

TEST_CASE("overflow drops the oldest frames and counts an overrun", "[ring]") {
    tembra::SpscRingBuffer<float> ring(16384);
    auto data = ramp(16384 + 512);
    ring.push(data.data(), data.size());
    REQUIRE(ring.overruns() > 0);
    REQUIRE(ring.available() == 16384);
    std::vector<float> out(1024);
    REQUIRE(ring.read(out.data(), 1024));
    // The oldest 512 frames were overwritten; the read starts at the oldest retained frame.
    REQUIRE(out[0] == 512.0f);
    REQUIRE(out[1023] == 512.0f + 1023.0f);
    REQUIRE(ring.available() == 16384 - 1024);
}

TEST_CASE("capacity rounds up to a power of two", "[ring]") {
    tembra::SpscRingBuffer<float> ring(1000);
    REQUIRE(ring.capacity() == 1024);
}
