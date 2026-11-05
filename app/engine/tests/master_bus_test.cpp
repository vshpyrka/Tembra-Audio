// The master bus (feature 009, research R6): float summing of the track strips, a 10 ms master gain ramp and a
// ±1.0 clamp before the device. Since feature 012 the bus reports whether the clamp engaged in the block it finished,
// so the renderer counts clamped blocks (master_clips) and the meter lights the master's latch.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

#include "mixer/master_bus.h"

namespace {

constexpr uint32_t kRate = 48000;
constexpr uint32_t kRampFrames = 480;

std::vector<float> constant(uint32_t frames, float left, float right) {
    std::vector<float> stereo(frames * 2);
    for (uint32_t i = 0; i < frames; ++i) {
        stereo[2 * i] = left;
        stereo[2 * i + 1] = right;
    }
    return stereo;
}

}  // namespace

TEST_CASE("the bus sums two strips", "[mixer][bus]") {
    tembra::MasterBus bus(kRate);
    const auto a = constant(256, 0.25f, -0.1f);
    const auto b = constant(256, 0.5f, 0.3f);
    std::vector<float> out(512);
    bus.begin(256);
    bus.add(a.data(), 256);
    bus.add(b.data(), 256);
    bus.finish(out.data(), 256);
    for (uint32_t i = 0; i < 256; ++i) {
        REQUIRE(std::abs(out[2 * i] - 0.75f) < 1e-6f);
        REQUIRE(std::abs(out[2 * i + 1] - 0.2f) < 1e-6f);
    }
}

TEST_CASE("the master gain ramps over 10 ms", "[mixer][bus]") {
    tembra::MasterBus bus(kRate);
    const auto in = constant(256, 1.0f, 1.0f);
    std::vector<float> out(512);
    std::vector<float> left;
    bus.setMaster(0.5f);
    for (int block = 0; block < 8; ++block) {
        bus.begin(256);
        bus.add(in.data(), 256);
        bus.finish(out.data(), 256);
        for (uint32_t i = 0; i < 256; ++i) left.push_back(out[2 * i]);
    }
    CHECK(left[0] > 0.99f);
    CHECK(std::abs(left[kRampFrames / 2] - 0.75f) < 0.01f);
    CHECK(left[kRampFrames + 256] == 0.5f);
    CHECK(left.back() == 0.5f);
    float worst = 0.0f;
    for (size_t i = 1; i < left.size(); ++i) worst = std::max(worst, std::abs(left[i] - left[i - 1]));
    CHECK(worst < 0.01f);
}

TEST_CASE("a +6 dB sum is clamped to ±1.0", "[mixer][bus]") {
    tembra::MasterBus bus(kRate);
    const auto hot = constant(128, 1.0f, -1.0f);
    std::vector<float> out(256);
    bus.begin(128);
    bus.add(hot.data(), 128);
    bus.add(hot.data(), 128);
    bus.finish(out.data(), 128);
    for (uint32_t i = 0; i < 128; ++i) {
        REQUIRE(out[2 * i] == 1.0f);
        REQUIRE(out[2 * i + 1] == -1.0f);
    }
}

TEST_CASE("the bus reports an engaged clamp for the block it finished and forgets it at the next begin", "[mixer][bus][clip]") {
    tembra::MasterBus bus(kRate);
    REQUIRE_FALSE(bus.clampEngaged());
    const auto hot = constant(128, 1.0f, -1.0f);
    const auto quiet = constant(128, 0.25f, 0.25f);
    std::vector<float> out(256);
    bus.begin(128);
    bus.add(hot.data(), 128);
    bus.add(hot.data(), 128);
    bus.finish(out.data(), 128);
    REQUIRE(bus.clampEngaged());
    bus.begin(128);
    REQUIRE_FALSE(bus.clampEngaged());
    bus.add(quiet.data(), 128);
    bus.finish(out.data(), 128);
    REQUIRE_FALSE(bus.clampEngaged());
    // Exactly ±1.0 is not a clamp; a hair above it is.
    bus.begin(128);
    bus.add(hot.data(), 128);
    bus.finish(out.data(), 128);
    REQUIRE_FALSE(bus.clampEngaged());
    const auto hair = constant(128, 1.0001f, 0.0f);
    bus.begin(128);
    bus.add(hair.data(), 128);
    bus.finish(out.data(), 128);
    REQUIRE(bus.clampEngaged());
}

TEST_CASE("nothing active renders silence", "[mixer][bus]") {
    tembra::MasterBus bus(kRate);
    std::vector<float> out(512, 0.7f);
    bus.begin(256);
    bus.finish(out.data(), 256);
    for (float sample : out) REQUIRE(sample == 0.0f);
    // The accumulator is cleared by begin(): a previous block's sum never leaks.
    const auto in = constant(256, 0.4f, 0.4f);
    bus.begin(256);
    bus.add(in.data(), 256);
    bus.finish(out.data(), 256);
    bus.begin(256);
    bus.finish(out.data(), 256);
    for (float sample : out) REQUIRE(sample == 0.0f);
}
