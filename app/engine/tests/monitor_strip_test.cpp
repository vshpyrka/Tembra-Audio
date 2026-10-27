// The monitor strip (feature 010, research R12): the armed track's input through its gain and audibility into the
// master bus, mono duplicated, stereo through, gain ramped over 10 ms and a 5 ms fade when monitoring stops.
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <vector>

#include "mixer/master_bus.h"
#include "mixer/monitor_strip.h"

namespace {

constexpr uint32_t kRate = 48000;

std::vector<float> mono(uint32_t frames, float value) { return std::vector<float>(frames, value); }

std::vector<float> stereo(uint32_t frames, float left, float right) {
    std::vector<float> out(frames * 2);
    for (uint32_t i = 0; i < frames; ++i) {
        out[2 * i] = left;
        out[2 * i + 1] = right;
    }
    return out;
}

}  // namespace

TEST_CASE("mono input is duplicated through the gain into the bus and stereo passes", "[mixer][monitor]") {
    tembra::MonitorStrip strip(kRate);
    strip.snap(0.5f, true);
    strip.setEnabled(true);
    std::vector<float> out(2 * 480);
    strip.process(mono(480, 0.8f).data(), 1, out.data(), 480);  // the enable fade settles inside 240 frames
    strip.process(mono(480, 0.8f).data(), 1, out.data(), 480);
    REQUIRE(std::abs(out[0] - 0.4f) < 1e-5f);
    REQUIRE(std::abs(out[1] - 0.4f) < 1e-5f);
    REQUIRE(std::abs(out[959] - 0.4f) < 1e-5f);
    strip.process(stereo(480, 0.2f, -0.6f).data(), 2, out.data(), 480);
    REQUIRE(std::abs(out[0] - 0.1f) < 1e-5f);
    REQUIRE(std::abs(out[1] - (-0.3f)) < 1e-5f);
    tembra::MasterBus bus(kRate);
    bus.begin(480);
    bus.add(out.data(), 480);
    std::vector<float> mixed(2 * 480);
    bus.finish(mixed.data(), 480);
    REQUIRE(std::abs(mixed[0] - 0.1f) < 1e-5f);
    REQUIRE(std::abs(mixed[1] - (-0.3f)) < 1e-5f);
}

TEST_CASE("an inaudible track silences the monitor and gain changes ramp over 10 ms", "[mixer][monitor]") {
    tembra::MonitorStrip strip(kRate);
    strip.snap(1.0f, true);
    strip.setEnabled(true);
    std::vector<float> out(2 * 480);
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    REQUIRE(std::abs(out[958] - 0.5f) < 1e-5f);
    strip.setTarget(1.0f, false);  // muted or not soloed: audible = 0
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    REQUIRE(out[0] > 0.45f);         // the ramp starts from the current gain
    REQUIRE(out[2 * 479] == 0.0f);   // and has reached zero within 480 frames
    for (uint32_t i = 1; i < 480; ++i) REQUIRE(out[2 * i] <= out[2 * (i - 1)] + 1e-6f);
    strip.setTarget(0.25f, true);
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    REQUIRE(std::abs(out[2 * 240] - 0.0625f) < 1e-3f);  // 10 ms = 480 frames: the gain is half way (0.125) at frame 240
    REQUIRE(std::abs(out[2 * 479] - 0.125f) < 1e-3f);   // and settled at 0.25 by the end
}

TEST_CASE("the 5 ms fade when monitoring stops has no discontinuity", "[mixer][monitor]") {
    tembra::MonitorStrip strip(kRate);
    strip.snap(1.0f, true);
    strip.setEnabled(true);
    std::vector<float> out(2 * 480);
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    REQUIRE(strip.active());
    strip.setEnabled(false);
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    REQUIRE(std::abs(out[0] - 0.5f) < 0.01f);
    REQUIRE(out[2 * 239] > 0.0f);
    REQUIRE(out[2 * 240] == 0.0f);  // 5 ms = 240 frames
    REQUIRE(out[2 * 479] == 0.0f);
    for (uint32_t i = 1; i < 480; ++i) {
        REQUIRE(out[2 * i] <= out[2 * (i - 1)] + 1e-6f);
        REQUIRE(std::abs(out[2 * i] - out[2 * (i - 1)]) <= 0.01f);
    }
    REQUIRE_FALSE(strip.active());
    // Re-enabling fades back in over the same 5 ms.
    strip.setEnabled(true);
    strip.process(mono(480, 0.5f).data(), 1, out.data(), 480);
    REQUIRE(out[0] < 0.01f);
    REQUIRE(std::abs(out[2 * 240] - 0.5f) < 1e-5f);
    REQUIRE(strip.active());
}
