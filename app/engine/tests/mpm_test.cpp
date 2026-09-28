// Accuracy, octave correctness and rejection behaviour of the McLeod pitch detector on synthetic signals
// (spec FR-011, FR-012, SC-001 to SC-003, research R1), offline and without an audio device.
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <vector>

#include "signal_fixtures.h"
#include "tuner/mcleod_pitch_detector.h"

using namespace tembra;
using namespace tembra::fixtures;

namespace {

constexpr uint32_t kWindow = 4096;
constexpr float kMinHz = 50.0f;
constexpr float kMaxHz = 1400.0f;
constexpr float kPeakRatio = 0.9f;

double midiHz(int midi) { return 440.0 * std::pow(2.0, (midi - 69) / 12.0); }

McLeodPitchDetector detector() { return McLeodPitchDetector(kWindow, kMinHz, kMaxHz, kPeakRatio); }

PitchEstimate estimateAt(McLeodPitchDetector& mpm, const std::vector<float>& signal, size_t offset, float sampleRate) {
    return mpm.estimate(signal.data() + offset, kWindow, sampleRate);
}

}  // namespace

TEST_CASE("every semitone from A1 to E5 is found within a cent at 48 and 44.1 kHz", "[mpm][accuracy]") {
    auto mpm = detector();
    for (double sampleRate : {48000.0, 44100.0}) {
        for (int midi = 33; midi <= 76; ++midi) {
            const double f0 = midiHz(midi);
            const auto tone = harmonicTone(f0, sampleRate, 0.2);
            const PitchEstimate e = estimateAt(mpm, tone, 512, static_cast<float>(sampleRate));
            INFO("midi " << midi << " at " << sampleRate << " Hz: " << e.hz << " Hz, clarity " << e.clarity);
            REQUIRE(e.clarity >= 0.5f);
            REQUIRE(std::abs(centsBetween(f0, e.hz)) < 1.0);
        }
    }
}

TEST_CASE("offsets of 10, 20 and 40 cents are measured within a cent", "[mpm][accuracy]") {
    auto mpm = detector();
    for (int midi : {40, 45, 50, 55, 59, 64, 33, 36}) {
        for (double cents : {10.0, -10.0, 20.0, -20.0, 40.0, -40.0}) {
            const double target = centsOffset(midiHz(midi), cents);
            const auto tone = harmonicTone(target, 48000.0, 0.2);
            const PitchEstimate e = estimateAt(mpm, tone, 256, 48000.0f);
            INFO("midi " << midi << " offset " << cents << ": " << e.hz << " Hz");
            REQUIRE(e.clarity >= 0.5f);
            REQUIRE(std::abs(centsBetween(target, e.hz)) < 1.0);
        }
    }
}

TEST_CASE("weak fundamentals never read an octave off", "[mpm][octave]") {
    auto mpm = detector();
    const std::vector<double> pitches{55.0, 82.41, 110.0, 146.83, 196.0, 246.94, 329.63, 65.41, 98.0, 220.0};
    for (double f0 : pitches) {
        const auto tone = harmonicTone(f0, 48000.0, 3.0);
        int within = 0;
        constexpr int kWindows = 100;
        for (int w = 0; w < kWindows; ++w) {
            const PitchEstimate e = estimateAt(mpm, tone, static_cast<size_t>(w) * 1024, 48000.0f);
            if (e.clarity >= 0.5f && std::abs(centsBetween(f0, e.hz)) < 30.0) ++within;
        }
        INFO(f0 << " Hz: " << within << " of " << kWindows << " windows within 30 cents");
        REQUIRE(within >= 99);
    }
}

TEST_CASE("silence and noise have no clear pitch", "[mpm][gate]") {
    auto mpm = detector();
    const auto quiet = silence(48000.0, 0.2);
    REQUIRE(estimateAt(mpm, quiet, 0, 48000.0f).clarity < 0.5f);
    const auto noise = whiteNoise(-30.0, 12345u, 48000.0, 0.2);
    for (size_t offset : {0u, 1024u, 2048u, 4096u}) {
        REQUIRE(estimateAt(mpm, noise, offset, 48000.0f).clarity < 0.5f);
    }
}

TEST_CASE("a tone above the range is rejected rather than read as a sub-harmonic", "[mpm][gate]") {
    auto mpm = detector();
    const auto high = sine(2000.0, 48000.0, 0.2);
    const PitchEstimate e = estimateAt(mpm, high, 100, 48000.0f);
    const bool rejected = e.clarity < 0.5f || e.hz < kMinHz || e.hz > kMaxHz;
    INFO("2 kHz sine read as " << e.hz << " Hz with clarity " << e.clarity);
    REQUIRE(rejected);
}

TEST_CASE("a hard-clipped low E still reads within three cents", "[mpm][robustness]") {
    auto mpm = detector();
    const auto tone = clipped(harmonicTone(82.41, 48000.0, 0.2), 0.3f);
    const PitchEstimate e = estimateAt(mpm, tone, 512, 48000.0f);
    REQUIRE(e.clarity >= 0.5f);
    REQUIRE(std::abs(centsBetween(82.41, e.hz)) < 3.0);
}
