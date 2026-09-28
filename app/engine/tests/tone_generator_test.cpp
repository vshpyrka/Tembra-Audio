// The reference tone: pitch, ramps, click-free stop, one shot, re-arm and frequency switch (spec FR-018, FR-019, FR-021, SC-005).
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <vector>

#include "signal_fixtures.h"
#include "tuner/mcleod_pitch_detector.h"
#include "tuner/tone_generator.h"

using namespace tembra;
using namespace tembra::fixtures;

namespace {

/** Renders [frames] frames in blocks of the given sizes cycling, returning the interleaved stereo output. */
std::vector<float> renderFrames(ToneGenerator& gen, size_t frames, const std::vector<uint32_t>& blocks = {256, 480, 1024}) {
    std::vector<float> out;
    out.reserve(frames * 2);
    size_t rendered = 0;
    size_t b = 0;
    std::vector<float> block;
    while (rendered < frames) {
        const uint32_t n = static_cast<uint32_t>(std::min<size_t>(blocks[b % blocks.size()], frames - rendered));
        block.assign(n * 2, 0.0f);
        gen.render(block.data(), n);
        out.insert(out.end(), block.begin(), block.end());
        rendered += n;
        ++b;
    }
    return out;
}

std::vector<float> left(const std::vector<float>& stereo) {
    std::vector<float> mono(stereo.size() / 2);
    for (size_t i = 0; i < mono.size(); ++i) mono[i] = stereo[2 * i];
    return mono;
}

float peak(const std::vector<float>& mono, size_t from = 0, size_t to = 0) {
    if (to == 0) to = mono.size();
    float p = 0.0f;
    for (size_t i = from; i < to; ++i) p = std::max(p, std::abs(mono[i]));
    return p;
}

float maxJump(const std::vector<float>& mono, size_t from = 1, size_t to = 0) {
    if (to == 0) to = mono.size();
    float j = 0.0f;
    for (size_t i = std::max<size_t>(from, 1); i < to; ++i) j = std::max(j, std::abs(mono[i] - mono[i - 1]));
    return j;
}

double measuredHz(const std::vector<float>& mono, size_t offset, float sampleRate) {
    McLeodPitchDetector mpm(4096, 50.0f, 1400.0f, 0.9f);
    return mpm.estimate(mono.data() + offset, 4096, sampleRate).hz;
}

}  // namespace

TEST_CASE("a looping tone is a clean sine at the requested pitch", "[tone]") {
    ToneGenerator gen(48000);
    gen.start(82.41, 0);
    const auto stereo = renderFrames(gen, 48000);
    for (size_t i = 0; i < stereo.size(); i += 2) REQUIRE(stereo[i] == stereo[i + 1]);
    const auto mono = left(stereo);
    REQUIRE(std::abs(centsBetween(82.41, measuredHz(mono, 8000, 48000.0f))) < 0.5);
    REQUIRE(peak(mono, 1000) <= 0.26f);
    REQUIRE(peak(mono, 1000) > 0.24f);
    REQUIRE(gen.isActive());
}

TEST_CASE("the attack rises monotonically over 5 ms", "[tone]") {
    // A 1 kHz tone puts a half period in every 24-frame block, so block peaks follow the envelope.
    ToneGenerator gen(48000);
    gen.start(1000.0, 0);
    const auto mono = left(renderFrames(gen, 480, {480}));
    float lastPeak = 0.0f;
    for (size_t i = 0; i < 240; i += 24) {
        const float p = peak(mono, i, i + 24);
        REQUIRE(p >= lastPeak - 1e-4f);
        lastPeak = p;
    }
    REQUIRE(peak(mono, 240, 480) > 0.24f);
}

TEST_CASE("stop releases over 5 ms without a click and then goes silent", "[tone]") {
    ToneGenerator gen(48000);
    gen.start(82.41, 0);
    renderFrames(gen, 4800);
    gen.stop();
    const auto tail = left(renderFrames(gen, 480, {240}));
    REQUIRE(maxJump(tail) < 0.02f);
    for (size_t i = 240; i < tail.size(); ++i) REQUIRE(tail[i] == 0.0f);
    REQUIRE_FALSE(gen.isActive());
    gen.stop();  // idempotent
    REQUIRE_FALSE(gen.isActive());
}

TEST_CASE("a one shot ends by itself", "[tone]") {
    ToneGenerator gen(48000);
    gen.start(440.0, 200);
    const auto mono = left(renderFrames(gen, 48000 / 4, {480}));
    REQUIRE(peak(mono, 0, 9600) > 0.2f);
    REQUIRE(peak(mono, 10000) == 0.0f);
    REQUIRE_FALSE(gen.isActive());
}

TEST_CASE("the pitch holds at 44.1 kHz", "[tone]") {
    ToneGenerator gen(44100);
    gen.start(82.41, 0);
    const auto mono = left(renderFrames(gen, 44100));
    REQUIRE(std::abs(centsBetween(82.41, measuredHz(mono, 8000, 44100.0f))) < 0.5);
}

TEST_CASE("re-arming the same frequency neither restarts nor dips", "[tone][rearm]") {
    ToneGenerator gen(48000);
    gen.start(82.41, 0);
    const auto before = left(renderFrames(gen, 4800, {480}));
    gen.start(82.41, 2000);
    const auto after = left(renderFrames(gen, 4800, {480}));
    std::vector<float> joined(before.end() - 1, before.end());
    joined.insert(joined.end(), after.begin(), after.end());
    REQUIRE(maxJump(joined) < 0.02f);
    REQUIRE(peak(after) > 0.24f);
    // About two seconds later the one shot has ended.
    const auto rest = left(renderFrames(gen, 48000 * 2, {1024}));
    REQUIRE(peak(rest, 0, 48000) > 0.2f);
    REQUIRE(peak(rest, 96000 - 240) == 0.0f);
    REQUIRE_FALSE(gen.isActive());
}

TEST_CASE("switching frequency leaves at most 10 ms of near silence", "[tone][switch]") {
    ToneGenerator gen(48000);
    gen.start(82.41, 0);
    renderFrames(gen, 4800);
    gen.start(110.0, 0);
    const auto mono = left(renderFrames(gen, 4800, {480}));
    // Count frames whose local peak stays below −40 dBFS (0.01) before the new tone reaches full level.
    size_t quiet = 0;
    for (size_t i = 0; i + 48 <= mono.size(); i += 48) {
        if (peak(mono, i, i + 48) < 0.01f) quiet += 48;
    }
    REQUIRE(quiet <= 480);
    REQUIRE(maxJump(mono) < 0.02f);
    REQUIRE(std::abs(centsBetween(110.0, measuredHz(left(renderFrames(gen, 8192)), 2048, 48000.0f))) < 0.5);
}

TEST_CASE("sixty seconds of loop have no seam", "[tone][loop]") {
    ToneGenerator gen(48000);
    gen.start(82.41, 0);
    const auto mono = left(renderFrames(gen, 48000 * 60, {480}));
    const float bound = static_cast<float>(0.25 * 2.0 * 3.14159265358979323846 * 82.41 / 48000.0 * 1.5);
    REQUIRE(maxJump(mono, 480) < bound);
}
