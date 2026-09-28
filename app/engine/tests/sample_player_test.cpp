// Recordings: decode, resample, loop crossfade, one shot and stop (spec FR-019, FR-022).
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <vector>

#include "signal_fixtures.h"
#include "tuner/mcleod_pitch_detector.h"
#include "tuner/sample_player.h"
#include "tuner/wav_decoder.h"
#include "wav_fixtures.h"

using namespace tembra;
using namespace tembra::fixtures;

namespace {

std::vector<float> renderFrames(SamplePlayer& player, size_t frames, uint32_t block = 480) {
    std::vector<float> mono;
    mono.reserve(frames);
    std::vector<float> stereo(block * 2);
    size_t rendered = 0;
    while (rendered < frames) {
        const uint32_t n = static_cast<uint32_t>(std::min<size_t>(block, frames - rendered));
        player.render(stereo.data(), n);
        for (uint32_t i = 0; i < n; ++i) mono.push_back(stereo[2 * i]);
        rendered += n;
    }
    return mono;
}

float maxJump(const std::vector<float>& mono, size_t from, size_t to) {
    float j = 0.0f;
    for (size_t i = std::max<size_t>(from, 1); i < std::min(to, mono.size()); ++i) j = std::max(j, std::abs(mono[i] - mono[i - 1]));
    return j;
}

DecodedSample sine220() {
    const std::string path = tempPath("tembra-sample-220.wav");
    REQUIRE(writeTempWav(path, 44100, 1, 16, sine(220.0, 44100.0, 1.5, 0.5)));
    const auto bytes = readFile(path);
    auto decoded = WavDecoder::decode(bytes.data(), bytes.size());
    REQUIRE(decoded.has_value());
    return *decoded;
}

}  // namespace

TEST_CASE("a 16-bit mono WAV decodes to mono float at its own rate", "[sample][decode]") {
    const DecodedSample sample = sine220();
    REQUIRE(sample.sampleRate == 44100);
    REQUIRE(sample.mono.size() == 66150);
}

TEST_CASE("a looped recording plays at pitch and crosses the loop point without a jump", "[sample][loop]") {
    const DecodedSample sample = sine220();
    SamplePlayer player(48000);
    player.play(sample, true);
    const auto mono = renderFrames(player, 48000 * 3);
    McLeodPitchDetector mpm(4096, 50.0f, 1400.0f, 0.9f);
    REQUIRE(std::abs(centsBetween(220.0, mpm.estimate(mono.data() + 4096, 4096, 48000.0f).hz)) < 1.0);
    // The loop point falls at 1.5 s of 44.1 kHz material = 72 000 output frames.
    REQUIRE(maxJump(mono, 72000 - 512, 72000 + 512) < 0.05f);
    REQUIRE(player.isActive());
}

TEST_CASE("a one shot ends after the resampled length", "[sample][once]") {
    const DecodedSample sample = sine220();
    SamplePlayer player(48000);
    player.play(sample, false);
    const size_t expected = static_cast<size_t>(66150) * 48000 / 44100;
    const auto mono = renderFrames(player, expected + 2048);
    size_t lastNonZero = 0;
    for (size_t i = 0; i < mono.size(); ++i) if (mono[i] != 0.0f) lastNonZero = i;
    REQUIRE(lastNonZero >= expected - 512);
    REQUIRE(lastNonZero <= expected + 512);
    REQUIRE_FALSE(player.isActive());
}

TEST_CASE("a 24-bit stereo WAV decodes to a mono average at its rate", "[sample][decode]") {
    const std::string path = tempPath("tembra-sample-stereo.wav");
    const auto mono = sine(330.0, 48000.0, 0.5, 0.4);
    std::vector<float> stereo(mono.size() * 2);
    for (size_t i = 0; i < mono.size(); ++i) {
        stereo[2 * i] = mono[i];
        stereo[2 * i + 1] = mono[i] * 0.5f;
    }
    REQUIRE(writeTempWav(path, 48000, 2, 24, stereo));
    const auto bytes = readFile(path);
    auto decoded = WavDecoder::decode(bytes.data(), bytes.size());
    REQUIRE(decoded.has_value());
    REQUIRE(decoded->sampleRate == 48000);
    REQUIRE(decoded->mono.size() == mono.size());
    REQUIRE(std::abs(decoded->mono[1000] - mono[1000] * 0.75f) < 1e-3f);
}

TEST_CASE("garbage is not a WAV", "[sample][decode]") {
    const uint8_t garbage[3] = {1, 2, 3};
    REQUIRE_FALSE(WavDecoder::decode(garbage, sizeof(garbage)).has_value());
    REQUIRE_FALSE(WavDecoder::decode(nullptr, 0).has_value());
}

TEST_CASE("stop releases a recording within 5 ms", "[sample][stop]") {
    const DecodedSample sample = sine220();
    SamplePlayer player(48000);
    player.play(sample, true);
    renderFrames(player, 4800);
    player.stop();
    const auto tail = renderFrames(player, 480, 240);
    REQUIRE(maxJump(tail, 1, tail.size()) < 0.05f);
    for (size_t i = 240; i < tail.size(); ++i) REQUIRE(tail[i] == 0.0f);
    REQUIRE_FALSE(player.isActive());
}

TEST_CASE("sixty seconds of looping a recording have no seam", "[sample][loop]") {
    const DecodedSample sample = sine220();
    SamplePlayer player(48000);
    player.play(sample, true);
    const auto mono = renderFrames(player, 48000 * 60, 480);
    REQUIRE(maxJump(mono, 1, mono.size()) < 0.05f);
}
