// The track strip (feature 009, research R6): a 10 ms linear gain ramp toward gain x audibility, mono duplicated to
// both outputs at unity, stereo passed through; driven at 96, 256 and 1024 frames like the other renderers.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <vector>

#include "mixer/gain_ramp.h"
#include "mixer/track_strip.h"

namespace {

constexpr uint32_t kRate = 48000;
constexpr std::array<uint32_t, 3> kBlocks{96, 256, 1024};
constexpr uint32_t kRampFrames = 480;  // 10 ms at 48 kHz

/** Runs a constant mono 1.0 through [strip] in [block]-frame blocks for [frames] frames; returns the left channel. */
std::vector<float> runConstant(tembra::TrackStrip& strip, uint32_t block, uint32_t frames) {
    std::vector<float> in(block, 1.0f);
    std::vector<float> out(block * 2);
    std::vector<float> left;
    for (uint32_t done = 0; done < frames; done += block) {
        strip.process(in.data(), 1, out.data(), block);
        for (uint32_t i = 0; i < block; ++i) left.push_back(out[2 * i]);
    }
    return left;
}

float maxStep(const std::vector<float>& samples) {
    float worst = 0.0f;
    for (size_t i = 1; i < samples.size(); ++i) worst = std::max(worst, std::abs(samples[i] - samples[i - 1]));
    return worst;
}

}  // namespace

TEST_CASE("the gain ramp is linear over 10 ms and lands exactly on its target", "[mixer][ramp]") {
    tembra::GainRamp ramp(kRate, 1.0f);
    REQUIRE(ramp.current() == 1.0f);
    REQUIRE(ramp.settled());
    ramp.setTarget(0.5f);
    REQUIRE_FALSE(ramp.settled());
    std::vector<float> values;
    for (uint32_t i = 0; i < kRampFrames + 10; ++i) values.push_back(ramp.next());
    CHECK(std::abs(values[kRampFrames / 2 - 1] - 0.75f) < 0.01f);
    CHECK(values[kRampFrames - 1] == 0.5f);
    CHECK(values[kRampFrames] == 0.5f);
    CHECK(values.back() == 0.5f);
    CHECK(ramp.settled());
    CHECK(maxStep(values) < 0.01f);
    // A new target mid-ramp starts from the current value, never from the old target.
    ramp.setTarget(1.0f);
    for (uint32_t i = 0; i < kRampFrames / 2; ++i) ramp.next();
    const float midway = ramp.current();
    ramp.setTarget(0.0f);
    CHECK(std::abs(ramp.next() - midway) < 0.01f);
    // advance() moves the ramp without producing samples.
    ramp.advance(kRampFrames);
    CHECK(ramp.current() == 0.0f);
    CHECK(ramp.settled());
}

TEST_CASE("a gain step from 1.0 to 0.5 ramps over 10 ms within one block without a discontinuity", "[mixer][strip]") {
    for (uint32_t block : kBlocks) {
        INFO("block " << block);
        tembra::TrackStrip strip(kRate);
        strip.setTarget(1.0f, true);
        runConstant(strip, block, 4096);  // settles at unity
        strip.setTarget(0.5f, true);
        const auto left = runConstant(strip, block, 4096);
        CHECK(left[0] > 0.98f);
        CHECK(std::abs(left[kRampFrames / 2] - 0.75f) < 0.01f);
        // Settled by 10 ms plus one block at the latest; still ramping one block before 10 ms.
        const uint32_t settledBy = kRampFrames + block;
        for (uint32_t i = settledBy; i < left.size(); ++i) REQUIRE(left[i] == 0.5f);
        if (block < kRampFrames) CHECK(left[kRampFrames - block] > 0.5f);
        CHECK(maxStep(left) < 0.01f);
    }
}

TEST_CASE("audible = 0 ramps to silence and back", "[mixer][strip]") {
    tembra::TrackStrip strip(kRate);
    strip.setTarget(0.8f, true);
    runConstant(strip, 256, 2048);
    strip.setTarget(0.8f, false);
    const auto left = runConstant(strip, 256, 2048);
    CHECK(left[0] > 0.7f);
    CHECK(left[kRampFrames + 256] == 0.0f);
    CHECK(left.back() == 0.0f);
    CHECK(maxStep(left) < 0.01f);
    strip.setTarget(0.8f, true);
    const auto back = runConstant(strip, 256, 2048);
    CHECK(std::abs(back.back() - 0.8f) < 1e-6f);
}

TEST_CASE("mono input is duplicated to both channels at unity and stereo passes through", "[mixer][strip]") {
    tembra::TrackStrip strip(kRate);
    strip.setTarget(1.0f, true);
    std::vector<float> mono(64);
    for (uint32_t i = 0; i < 64; ++i) mono[i] = static_cast<float>(i) * 0.01f - 0.3f;
    std::vector<float> out(128);
    strip.process(mono.data(), 1, out.data(), 64);
    for (uint32_t i = 0; i < 64; ++i) {
        REQUIRE(out[2 * i] == mono[i]);
        REQUIRE(out[2 * i + 1] == mono[i]);
    }
    std::vector<float> stereo(128);
    for (uint32_t i = 0; i < 128; ++i) stereo[i] = static_cast<float>(i) * 0.005f - 0.3f;
    strip.process(stereo.data(), 2, out.data(), 64);
    for (uint32_t i = 0; i < 128; ++i) REQUIRE(out[i] == stereo[i]);
    // More channels than two: the first pair is taken.
    std::vector<float> quad(256);
    for (uint32_t i = 0; i < 256; ++i) quad[i] = static_cast<float>(i);
    strip.process(quad.data(), 4, out.data(), 64);
    REQUIRE(out[0] == 0.0f);
    REQUIRE(out[1] == 1.0f);
    REQUIRE(out[2] == 4.0f);
    REQUIRE(out[3] == 5.0f);
}

TEST_CASE("the rendered gain matches the target within 0.1 dB after the ramp (SC-006)", "[mixer][strip]") {
    for (float target : {0.25f, 0.5f, 1.0f, 1.5f, 2.0f}) {
        tembra::TrackStrip strip(kRate);
        strip.setTarget(target, true);
        const auto left = runConstant(strip, 256, 1024);
        const double db = 20.0 * std::log10(static_cast<double>(left.back()) / static_cast<double>(target));
        INFO("target " << target);
        CHECK(std::abs(db) < 0.1);
    }
}

TEST_CASE("advance keeps the ramp moving while no clip plays on the track", "[mixer][strip]") {
    tembra::TrackStrip strip(kRate);
    strip.setTarget(1.0f, true);
    runConstant(strip, 256, 1024);
    strip.setTarget(0.0f, true);
    strip.advance(kRampFrames);
    CHECK(strip.currentGain() == 0.0f);
    const auto left = runConstant(strip, 256, 256);
    CHECK(left[0] == 0.0f);
}
