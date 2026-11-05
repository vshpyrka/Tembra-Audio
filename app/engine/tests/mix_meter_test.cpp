// The mixer's meter (feature 012, research R6; contracts/engine-c-abi-v8.md "Renderer rules"): measured on the callback
// per block only while metering is on, published through one seqlock record. Peak over a 64 ms history per side, a
// 300 ms exponential mean square per side (the VU's source), a sticky clip latch per strip and for the master, the
// master's clamp count; a block split into segments accumulates and publishes once.
#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>

#include <cmath>
#include <cstdint>
#include <vector>

#include "mixer/mix_meter.h"
#include "mixer/mix_record.h"
#include "signal_fixtures.h"
#include "tembra_engine.h"
#include "tuner/seqlock.h"

using Catch::Matchers::WithinAbs;
using Catch::Matchers::WithinRel;

namespace {

constexpr uint32_t kRate = 48000;

std::vector<float> stereoOf(const std::vector<float>& mono, size_t from, uint32_t frames, float leftScale = 1.0f, float rightScale = 1.0f) {
    std::vector<float> out(static_cast<size_t>(frames) * 2);
    for (uint32_t i = 0; i < frames; ++i) {
        const float sample = from + i < mono.size() ? mono[from + i] : 0.0f;
        out[2 * i] = sample * leftScale;
        out[2 * i + 1] = sample * rightScale;
    }
    return out;
}

/** Runs [mono] through strip 0 in [block]-frame blocks, publishing each; returns the last record. */
tembra::MixRecord runStrip(tembra::MixMeter& meter, tembra::Seqlock<tembra::MixRecord>& out, const std::vector<float>& mono, uint32_t block, uint64_t& blocks) {
    tembra::MixRecord record{};
    for (size_t done = 0; done < mono.size(); done += block) {
        const auto stereo = stereoOf(mono, done, block);
        meter.beginBlock(block);
        meter.measureStrip(0, stereo.data(), block);
        meter.publish(out, ++blocks, 1);
        record = out.load();
    }
    return record;
}

}  // namespace

TEST_CASE("a −6 dBFS sine reads a peak of 0.5 and an rms of 0.354 within 1 %", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    uint64_t blocks = 0;
    const auto sine = tembra::fixtures::sine(440.0, kRate, 2.0, 0.5);
    const tembra::MixRecord record = runStrip(meter, out, sine, 256, blocks);
    CHECK(record.metering == 1);
    CHECK(record.count == 1);
    CHECK(record.block == blocks);
    REQUIRE_THAT(record.peak[0][0], WithinRel(0.5, 0.01));
    REQUIRE_THAT(record.peak[0][1], WithinRel(0.5, 0.01));
    REQUIRE_THAT(record.rms[0][0], WithinRel(0.5 / std::sqrt(2.0), 0.01));
    REQUIRE_THAT(record.rms[0][1], WithinRel(0.5 / std::sqrt(2.0), 0.01));
    CHECK(record.clipMask == 0);
    CHECK(record.masterClipped == 0);
}

TEST_CASE("the 64 ms peak history keeps a one-block transient for 12 blocks of 5 ms", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    const uint32_t block = 240;  // 5 ms
    std::vector<float> transient(block, 0.0f);
    transient[7] = 0.9f;
    const std::vector<float> quiet(block, 0.0f);
    uint64_t blocks = 0;
    auto publish = [&](const std::vector<float>& mono) {
        const auto stereo = stereoOf(mono, 0, block);
        meter.beginBlock(block);
        meter.measureStrip(0, stereo.data(), block);
        meter.publish(out, ++blocks, 1);
        return out.load();
    };
    REQUIRE(publish(transient).peak[0][0] == 0.9f);
    for (int k = 1; k <= 12; ++k) {
        INFO("silent block " << k);
        REQUIRE(publish(quiet).peak[0][0] == 0.9f);
    }
    publish(quiet);
    REQUIRE(publish(quiet).peak[0][0] == 0.0f);  // 14 silent blocks: 70 ms, the transient has left the window
}

TEST_CASE("the clip latch lights at 0.9999 on either side and clears only on a reset", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    const uint32_t block = 256;
    std::vector<float> loud(block * 2, 0.0f);
    std::vector<float> nearly(block * 2, 0.0f);
    std::vector<float> quiet(block * 2, 0.0f);
    nearly[2 * 10] = 0.999f;       // left, below the latch level
    loud[2 * 20 + 1] = -0.9999f;   // right, at the latch level
    uint64_t blocks = 0;
    auto publish = [&](const std::vector<float>& stereo, uint32_t strip) {
        meter.beginBlock(block);
        meter.measureStrip(strip, stereo.data(), block);
        meter.publish(out, ++blocks, 3);
        return out.load();
    };
    REQUIRE(publish(nearly, 2).clipMask == 0);
    REQUIRE(publish(loud, 2).clipMask == (1ull << 2));
    for (int k = 0; k < 20; ++k) REQUIRE(publish(quiet, 2).clipMask == (1ull << 2));
    meter.requestReset(2);
    REQUIRE(publish(quiet, 2).clipMask == 0);
    // A reset request for the master or a strip that is not lit is harmless, and a request for an index past the master is ignored.
    meter.requestReset(tembra::MixMeter::kMaster);
    meter.requestReset(65);
    REQUIRE(publish(quiet, 2).clipMask == 0);
}

TEST_CASE("the 300 ms mean-square window reaches 95 % of a step within 0.9 s and not within 0.6 s", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    const uint32_t block = 240;
    const std::vector<float> step(block, 0.5f);  // a constant: mean square 0.25
    uint64_t blocks = 0;
    auto publish = [&]() {
        const auto stereo = stereoOf(step, 0, block);
        meter.beginBlock(block);
        meter.measureStrip(0, stereo.data(), block);
        meter.publish(out, ++blocks, 1);
        return out.load();
    };
    tembra::MixRecord at600{};
    tembra::MixRecord at900{};
    for (int k = 1; k <= 180; ++k) {
        const tembra::MixRecord record = publish();
        if (k == 120) at600 = record;  // 0.6 s
        if (k == 180) at900 = record;  // 0.9 s
    }
    const double ms600 = static_cast<double>(at600.rms[0][0]) * at600.rms[0][0];
    const double ms900 = static_cast<double>(at900.rms[0][0]) * at900.rms[0][0];
    CHECK(ms600 < 0.95 * 0.25);
    CHECK(ms600 > 0.80 * 0.25);
    CHECK(ms900 >= 0.95 * 0.25);
    CHECK(ms900 < 0.25);
}

TEST_CASE("metering off publishes a zeroed record", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    const uint32_t block = 256;
    const auto sine = tembra::fixtures::sine(440.0, kRate, 0.1, 0.5);
    uint64_t blocks = 0;
    runStrip(meter, out, sine, block, blocks);
    REQUIRE(out.load().peak[0][0] > 0.4f);
    meter.publishSilent(out, ++blocks);
    const tembra::MixRecord record = out.load();
    CHECK(record.metering == 0);
    CHECK(record.block == blocks);
    CHECK(record.count == 0);
    CHECK(record.peak[0][0] == 0.0f);
    CHECK(record.rms[0][0] == 0.0f);
    CHECK(record.masterPeak[0] == 0.0f);
    CHECK(record.clipMask == 0);
    CHECK(record.masterClipped == 0);
}

TEST_CASE("the record is published once per block across a segment split with the same values", "[mixer][meter]") {
    const uint32_t block = 256;
    const auto sine = tembra::fixtures::sine(1000.0, kRate, 2.0, 0.8);  // two seconds: the 300 ms window has settled
    tembra::MixMeter whole(kRate);
    tembra::MixMeter split(kRate);
    tembra::Seqlock<tembra::MixRecord> wholeOut;
    tembra::Seqlock<tembra::MixRecord> splitOut;
    uint64_t blocks = 0;
    for (size_t done = 0; done + block <= sine.size(); done += block) {
        ++blocks;
        const auto stereo = stereoOf(sine, done, block);
        whole.beginBlock(block);
        whole.measureStrip(0, stereo.data(), block);
        whole.publish(wholeOut, blocks, 1);
        split.beginBlock(block);
        split.measureStrip(0, stereo.data(), 100);
        split.measureStrip(0, stereo.data() + 200, block - 100);
        split.publish(splitOut, blocks, 1);
    }
    const tembra::MixRecord a = wholeOut.load();
    const tembra::MixRecord b = splitOut.load();
    CHECK(a.block == b.block);
    CHECK(a.block == blocks);
    REQUIRE_THAT(b.peak[0][0], WithinAbs(a.peak[0][0], 1e-6));
    REQUIRE_THAT(b.rms[0][0], WithinAbs(a.rms[0][0], 1e-6));
    REQUIRE_THAT(a.rms[0][0], WithinRel(0.8 / std::sqrt(2.0), 0.01));
}

TEST_CASE("the master is measured after the clamp: its peak, its mean square and a sticky latch the clamp lights", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    const uint32_t block = 256;
    std::vector<float> full(block * 2, 1.0f);   // what the clamp leaves of a hot sum
    std::vector<float> half(block * 2, 0.5f);
    uint64_t blocks = 0;
    auto publish = [&](const std::vector<float>& stereo, bool clamped) {
        meter.beginBlock(block);
        meter.measureMaster(stereo.data(), block, clamped);
        meter.publish(out, ++blocks, 0);
        return out.load();
    };
    tembra::MixRecord record = publish(half, false);
    CHECK(record.masterPeak[0] == 0.5f);
    CHECK(record.masterPeak[1] == 0.5f);
    CHECK(record.masterClipped == 0);
    // A split block clamps in both halves: one record, the latch lit.
    meter.beginBlock(block);
    meter.measureMaster(full.data(), 128, true);
    meter.measureMaster(full.data() + 256, 128, true);
    meter.publish(out, ++blocks, 0);
    record = out.load();
    CHECK(record.block == blocks);
    CHECK(record.masterPeak[0] == 1.0f);
    CHECK(record.masterPeak[1] == 1.0f);
    CHECK(record.masterClipped == 1);
    for (int k = 0; k < 14; ++k) record = publish(half, false);  // 14 blocks of 5.3 ms: 75 ms, past the 64 ms window
    CHECK(record.masterClipped == 1);  // sticky
    CHECK(record.masterPeak[0] == 0.5f);  // the 64 ms history has let the full-scale blocks go
    meter.requestReset(tembra::MixMeter::kMaster);
    record = publish(half, false);
    CHECK(record.masterClipped == 0);
    CHECK(record.clipMask == 0);
    // A sample at the latch level without an engaged clamp lights the master latch too (a hot but unclamped sum).
    std::vector<float> hot(block * 2, 0.0f);
    hot[5] = 0.9999f;
    record = publish(hot, false);
    CHECK(record.masterClipped == 1);
    // The master's mean square follows the same 300 ms window as the strips'.
    for (int k = 0; k < 400; ++k) record = publish(half, false);
    REQUIRE_THAT(record.masterRms[0], WithinRel(0.5, 0.01));
}

TEST_CASE("reset clears the histories, the windows and the latches", "[mixer][meter]") {
    tembra::MixMeter meter(kRate);
    tembra::Seqlock<tembra::MixRecord> out;
    const uint32_t block = 256;
    std::vector<float> full(block * 2, 1.0f);
    meter.beginBlock(block);
    meter.measureStrip(0, full.data(), block);
    meter.measureMaster(full.data(), block, true);
    meter.publish(out, 1, 1);
    REQUIRE(out.load().clipMask == 1);
    REQUIRE(out.load().masterClipped == 1);
    REQUIRE(meter.measureCalls() == 2);
    meter.reset();
    std::vector<float> quiet(block * 2, 0.0f);
    meter.beginBlock(block);
    meter.measureStrip(0, quiet.data(), block);
    meter.measureMaster(quiet.data(), block, false);
    meter.publish(out, 2, 1);
    const tembra::MixRecord record = out.load();
    CHECK(record.peak[0][0] == 0.0f);
    CHECK(record.rms[0][0] == 0.0f);
    CHECK(record.clipMask == 0);
    CHECK(record.masterClipped == 0);
    CHECK(record.masterPeak[0] == 0.0f);
    CHECK(record.masterRms[0] == 0.0f);
}

TEST_CASE("the record mirrors the ABI snapshot's capacity", "[mixer][meter][abi]") {
    static_assert(sizeof(tembra::MixRecord::peak) == sizeof(tembra_mix_snapshot::peak), "64 tracks × 2 sides of peaks");
    static_assert(sizeof(tembra::MixRecord::rms) == sizeof(tembra_mix_snapshot::rms), "64 tracks × 2 sides of rms");
    static_assert(tembra::MixMeter::kMaster == TEMBRA_MIXER_MASTER_INDEX, "the master's reset index");
    REQUIRE(sizeof(tembra::MixRecord) < 1300);
}
