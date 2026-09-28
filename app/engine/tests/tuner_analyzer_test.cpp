// The analyzer that turns pushed frames into published readings: hop assembly, response, stability, smoothing,
// resting and validation (spec FR-010, FR-012, SC-006, SC-007).
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <vector>

#include "signal_fixtures.h"
#include "tembra_engine.h"
#include "tuner/tuner_analyzer.h"

using namespace tembra;
using namespace tembra::fixtures;

namespace {

constexpr uint32_t kRate = 48000;

tembra_tuner_config config() {
    tembra_tuner_config c{};
    c.window_frames = 4096;
    c.hop_frames = 1024;
    c.min_hz = 50.0f;
    c.max_hz = 1400.0f;
    c.clarity_threshold = 0.5f;
    c.peak_ratio = 0.9f;
    c.median_size = 5;
    c.watchdog_seconds = 5.0f;
    return c;
}

/** Pushes [signal] in 256-frame blocks, analysing after each block; returns every reading published on the way. */
std::vector<tembra_pitch_reading> feed(TunerAnalyzer& analyzer, const std::vector<float>& signal) {
    std::vector<tembra_pitch_reading> readings;
    uint64_t lastSequence = analyzer.sequence();
    for (size_t offset = 0; offset < signal.size(); offset += 256) {
        const uint32_t frames = static_cast<uint32_t>(std::min<size_t>(256, signal.size() - offset));
        analyzer.push(signal.data() + offset, frames);
        while (analyzer.analyseAvailable()) {
            tembra_pitch_reading r{};
            analyzer.read(r);
            if (r.sequence != lastSequence) {
                readings.push_back(r);
                lastSequence = r.sequence;
            }
        }
    }
    return readings;
}

}  // namespace

TEST_CASE("a full window plus a hop publishes the pitch and the sequence counts hops", "[analyzer]") {
    TunerAnalyzer analyzer(config(), kRate);
    const auto tone = harmonicTone(82.41, kRate, (4096.0 + 1024.0) / kRate);
    const auto readings = feed(analyzer, tone);
    REQUIRE(readings.size() == 5);
    for (size_t i = 0; i < readings.size(); ++i) REQUIRE(readings[i].sequence == i + 1);
    const tembra_pitch_reading& last = readings.back();
    REQUIRE(last.has_pitch == 1);
    REQUIRE(std::abs(centsBetween(82.41, last.frequency_hz)) < 1.0);
    REQUIRE(last.sample_rate == kRate);
    REQUIRE(last.synthetic == 0);
}

TEST_CASE("the first reading arrives within 250 ms of a note starting", "[analyzer][response]") {
    TunerAnalyzer analyzer(config(), kRate);
    feed(analyzer, silence(kRate, 0.5));
    const auto tone = harmonicTone(110.0, kRate, 0.5);
    size_t framesUntilPitch = 0;
    bool found = false;
    uint64_t lastSequence = analyzer.sequence();
    for (size_t offset = 0; offset < tone.size() && !found; offset += 256) {
        analyzer.push(tone.data() + offset, 256);
        framesUntilPitch = offset + 256;
        while (analyzer.analyseAvailable()) {
            tembra_pitch_reading r{};
            analyzer.read(r);
            if (r.sequence != lastSequence && r.has_pitch == 1) found = true;
            lastSequence = r.sequence;
        }
    }
    REQUIRE(found);
    REQUIRE(framesUntilPitch <= 12000);
}

TEST_CASE("a steady tone reads steadily", "[analyzer][stability]") {
    TunerAnalyzer analyzer(config(), kRate);
    const auto tone = harmonicTone(196.0, kRate, (4096.0 + 1024.0 * 110) / kRate);
    const auto readings = feed(analyzer, tone);
    REQUIRE(readings.size() >= 100);
    int compared = 0;
    for (size_t i = readings.size() - 100; i < readings.size(); ++i) {
        REQUIRE(readings[i].has_pitch == 1);
        if (i > readings.size() - 100) {
            const double jump = std::abs(centsBetween(readings[i - 1].frequency_hz, readings[i].frequency_hz));
            REQUIRE(jump <= 1.0);
            ++compared;
        }
        REQUIRE(std::abs(centsBetween(196.0, readings[i].frequency_hz)) < 1.0);
    }
    REQUIRE(compared == 99);
}

TEST_CASE("one window an octave up does not move the reading by more than five cents", "[analyzer][smoothing]") {
    TunerAnalyzer analyzer(config(), kRate);
    const auto steady = harmonicTone(146.83, kRate, 1.0);
    const auto readingsBefore = feed(analyzer, steady);
    const float before = readingsBefore.back().frequency_hz;
    const auto octave = harmonicTone(293.66, kRate, 4096.0 / kRate);
    const auto during = feed(analyzer, octave);
    const auto after = feed(analyzer, steady);
    for (const auto& r : during) {
        if (r.has_pitch == 1) REQUIRE(std::abs(centsBetween(before, r.frequency_hz)) <= 5.0);
    }
    REQUIRE(after.back().has_pitch == 1);
    REQUIRE(std::abs(centsBetween(146.83, after.back().frequency_hz)) < 1.0);
}

TEST_CASE("the meter rests within half a second of the note ending", "[analyzer][resting]") {
    TunerAnalyzer analyzer(config(), kRate);
    feed(analyzer, harmonicTone(220.0, kRate, 0.5));
    const auto quiet = silence(kRate, 1.0);
    size_t framesUntilRest = 0;
    bool resting = false;
    uint64_t lastSequence = analyzer.sequence();
    for (size_t offset = 0; offset < quiet.size() && !resting; offset += 256) {
        analyzer.push(quiet.data() + offset, 256);
        framesUntilRest = offset + 256;
        while (analyzer.analyseAvailable()) {
            tembra_pitch_reading r{};
            analyzer.read(r);
            if (r.sequence != lastSequence && r.has_pitch == 0) resting = true;
            lastSequence = r.sequence;
        }
    }
    REQUIRE(resting);
    REQUIRE(framesUntilRest <= 24000);
}

TEST_CASE("a tone above the range never has a pitch", "[analyzer][gate]") {
    TunerAnalyzer analyzer(config(), kRate);
    const auto readings = feed(analyzer, sine(2000.0, kRate, 1.0));
    REQUIRE_FALSE(readings.empty());
    for (const auto& r : readings) REQUIRE(r.has_pitch == 0);
}

TEST_CASE("the input peak follows the block", "[analyzer]") {
    TunerAnalyzer analyzer(config(), kRate);
    const auto readings = feed(analyzer, sine(220.0, kRate, 0.2, 0.3));
    REQUIRE_FALSE(readings.empty());
    REQUIRE(std::abs(readings.back().input_peak - 0.3f) < 0.02f);
}

TEST_CASE("a hop that does not divide the window is rejected", "[analyzer][config]") {
    tembra_tuner_config c = config();
    REQUIRE(TunerAnalyzer::validate(c));
    c.hop_frames = 1000;
    REQUIRE_FALSE(TunerAnalyzer::validate(c));
    c = config();
    c.hop_frames = 8192;
    REQUIRE_FALSE(TunerAnalyzer::validate(c));
    c = config();
    c.min_hz = 0.0f;
    REQUIRE_FALSE(TunerAnalyzer::validate(c));
}
