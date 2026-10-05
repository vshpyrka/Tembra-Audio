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

// --- The input level (feature 006; contracts/engine-c-abi-v4.md, research R2) ---

TEST_CASE("the input level is the window's RMS after the filter", "[analyzer][level]") {
    TunerAnalyzer analyzer(config(), kRate);
    const auto readings = feed(analyzer, scaledToRmsDb(sine(220.0, kRate, 0.5), -40.0));
    REQUIRE(readings.size() > 8);
    // Before the window is full the level covers the hops received so far, so the first hop already reads the tone.
    REQUIRE(std::abs(readings.front().input_level_db - (-40.0f)) < 2.0f);
    REQUIRE(std::abs(readings.back().input_level_db - (-40.0f)) < 0.5f);
    REQUIRE(readings.back().gate_open == 1);
}

TEST_CASE("sound two octaves outside the hearing range counts at least 20 dB less and has no pitch", "[analyzer][level]") {
    for (const double hz : {12.5, 5600.0}) {
        TunerAnalyzer analyzer(config(), kRate);
        const auto readings = feed(analyzer, scaledToRmsDb(sine(hz, kRate, 2.0), -20.0));
        REQUIRE(readings.size() > 40);
        for (size_t i = readings.size() / 2; i < readings.size(); ++i) {
            INFO("tone at " << hz << " Hz, reading " << i << " level " << readings[i].input_level_db);
            REQUIRE(readings[i].input_level_db <= -40.0f);
            REQUIRE(readings[i].has_pitch == 0);
        }
    }
}

TEST_CASE("silence and a resting analyzer read the floor level with the gate open", "[analyzer][level]") {
    TunerAnalyzer analyzer(config(), kRate);
    tembra_pitch_reading reading{};
    analyzer.read(reading);
    REQUIRE(reading.sequence == 0);
    REQUIRE(reading.input_level_db == -120.0f);
    REQUIRE(reading.gate_open == 1);

    const auto quiet = feed(analyzer, silence(kRate, 0.3));
    REQUIRE_FALSE(quiet.empty());
    REQUIRE(quiet.back().input_level_db == -120.0f);
    REQUIRE(quiet.back().gate_open == 1);

    feed(analyzer, scaledToRmsDb(sine(220.0, kRate, 0.3), -30.0));
    analyzer.publishResting();
    analyzer.read(reading);
    REQUIRE(reading.input_level_db == -120.0f);
    REQUIRE(reading.gate_open == 1);
}

// --- The noise gate (feature 006, User Story 1; contracts/engine-c-abi-v4.md, research R1 and R3) ---

namespace {

/** Every open-string pitch of feature 003's presets, A1 to E4, as MIDI note numbers (spec SC-001). */
constexpr int kOpenStrings[] = {33, 35, 36, 38, 39, 40, 43, 44, 45, 48, 49, 50, 53, 54, 55, 57, 58, 59, 62, 63, 64};

/** The gate levels the spec checks at. */
constexpr float kCheckLevels[] = {-60.0f, -50.0f, -40.0f};

double midiHz(int midi) { return 440.0 * std::pow(2.0, (midi - 69) / 12.0); }

/** A plucked-string-like tone at [hz] whose level, in the gate's own measure, is [db]. */
std::vector<float> toneAt(double hz, double db, double seconds) {
    return scaledToRmsDb(harmonicTone(hz, kRate, seconds), db);
}

std::vector<float> part(const std::vector<float>& signal, size_t from, size_t to) {
    return std::vector<float>(signal.begin() + static_cast<std::ptrdiff_t>(from), signal.begin() + static_cast<std::ptrdiff_t>(to));
}

/** Frames from the start of [readings] (one hop each) to the first one [accept] holds for, or 0 when none does. */
template <class Accept>
size_t framesUntil(const std::vector<tembra_pitch_reading>& readings, Accept accept) {
    for (size_t i = 0; i < readings.size(); ++i) {
        if (accept(readings[i])) return (i + 1) * 1024;
    }
    return 0;
}

}  // namespace

TEST_CASE("a tone 6 dB under the gate is never read, at every open string and check level", "[analyzer][gate]") {
    for (const float gate : kCheckLevels) {
        for (const int midi : kOpenStrings) {
            TunerAnalyzer analyzer(config(), kRate, false, gate);
            const auto readings = feed(analyzer, toneAt(midiHz(midi), gate - 6.0, 10.0));
            REQUIRE(readings.size() > 400);
            int read = 0;
            int open = 0;
            for (const auto& r : readings) {
                if (r.has_pitch != 0) ++read;
                if (r.gate_open != 0) ++open;
            }
            INFO("gate " << gate << " dB, MIDI note " << midi << ": " << read << " readings, " << open << " open hops");
            REQUIRE(read == 0);
            REQUIRE(open == 0);
        }
    }
}

TEST_CASE("a tone just above the gate, and well above it, is read within 250 ms and within a cent", "[analyzer][gate]") {
    for (const float gate : kCheckLevels) {
        // The level of a sound is known to about half a decibel (research R2), so "at the gate level" is 1 dB over it.
        for (const double above : {1.0, 6.0}) {
            for (const double hz : {55.0, 82.41, 329.63}) {
                TunerAnalyzer analyzer(config(), kRate, false, gate);
                feed(analyzer, silence(kRate, 0.5));
                const auto readings = feed(analyzer, toneAt(hz, gate + above, 1.0));
                INFO("gate " << gate << " dB, tone " << above << " dB over it at " << hz << " Hz");
                const size_t frames = framesUntil(readings, [](const tembra_pitch_reading& r) { return r.has_pitch == 1; });
                REQUIRE(frames > 0);
                REQUIRE(frames <= 12000);
                REQUIRE(readings.back().has_pitch == 1);
                REQUIRE(readings.back().gate_open == 1);
                REQUIRE(std::abs(centsBetween(hz, readings.back().frequency_hz)) < 1.0);
            }
        }
    }
}

TEST_CASE("a note over a quiet background is read, and the background is never named", "[analyzer][gate]") {
    const float gate = -50.0f;
    const double background = 196.0;  // G3, 6 dB under the gate
    const double note = 82.41;        // E2, 12 dB over the background
    TunerAnalyzer analyzer(config(), kRate, false, gate);
    // One second of background alone, one with the note over it, one of background alone again.
    std::vector<float> signal = toneAt(background, gate - 6.0, 3.0);
    const auto loud = toneAt(note, gate + 6.0, 1.0);
    for (size_t i = 0; i < loud.size(); ++i) signal[kRate + i] += loud[i];
    const auto readings = feed(analyzer, signal);

    double firstRead = 0.0;
    double lastRead = 0.0;
    for (const auto& r : readings) {
        if (r.has_pitch != 1) continue;
        const double seconds = static_cast<double>(r.sequence) * 1024.0 / kRate;
        if (firstRead == 0.0) firstRead = seconds;
        lastRead = seconds;
        INFO("read " << r.frequency_hz << " Hz at " << seconds << " s");
        // Whatever is read, whenever, is named as the note (inside its semitone) and is never the background.
        REQUIRE(std::abs(centsBetween(note, r.frequency_hz)) < 50.0);
        REQUIRE(std::abs(centsBetween(background, r.frequency_hz)) > 100.0);
        // The gate decides whether a sound is read; it does not clean it (spec FR-026). With a pitched background
        // 12 dB under the note the detector itself settles about 7 cents flat, gate or no gate (measured 2026-10-05).
        if (seconds > 1.3 && seconds < 2.0) REQUIRE(std::abs(centsBetween(note, r.frequency_hz)) < 15.0);
    }
    INFO("first read at " << firstRead << " s, last at " << lastRead << " s");
    REQUIRE(firstRead > 1.0);    // nothing while only the background sounds
    REQUIRE(firstRead <= 1.25);  // the note within a quarter of a second
    REQUIRE(lastRead >= 1.9);    // read for as long as it sounds
    REQUIRE(lastRead <= 2.5);    // and at rest within half a second of its end
}

TEST_CASE("noise above the gate opens it and still has no pitch", "[analyzer][gate]") {
    const auto noise = whiteNoise(-20.0, 20261005u, kRate, 2.0);
    TunerAnalyzer plain(config(), kRate);
    TunerAnalyzer gated(config(), kRate, false, -50.0f);
    const auto a = feed(plain, noise);
    const auto b = feed(gated, noise);
    REQUIRE(a.size() == b.size());
    REQUIRE(b.size() > 80);
    for (size_t i = 8; i < b.size(); ++i) {
        INFO("hop " << i << " level " << b[i].input_level_db);
        REQUIRE(b[i].gate_open == 1);
        REQUIRE(b[i].has_pitch == 0);
        REQUIRE(a[i].has_pitch == 0);
    }
}

TEST_CASE("a change of the gate takes effect on the next hop and is judged afresh", "[analyzer][gate]") {
    TunerAnalyzer analyzer(config(), kRate, false, -60.0f);
    const auto tone = toneAt(82.41, -55.0, 3.0);

    const auto underTheSound = feed(analyzer, part(tone, 0, kRate));
    REQUIRE(underTheSound.back().gate_open == 1);
    REQUIRE(underTheSound.back().has_pitch == 1);

    analyzer.setGate(-50.0f);
    const auto overTheSound = feed(analyzer, part(tone, kRate, 2 * kRate));
    REQUIRE(overTheSound.front().gate_open == 0);
    const size_t untilRest = framesUntil(overTheSound, [](const tembra_pitch_reading& r) { return r.has_pitch == 0; });
    REQUIRE(untilRest > 0);
    REQUIRE(untilRest <= 24000);
    REQUIRE(overTheSound.back().has_pitch == 0);

    analyzer.setGate(TunerAnalyzer::kGateOff);
    const auto noGate = feed(analyzer, part(tone, 2 * kRate, 3 * kRate));
    REQUIRE(noGate.front().gate_open == 1);
    const size_t untilRead = framesUntil(noGate, [](const tembra_pitch_reading& r) { return r.has_pitch == 1; });
    REQUIRE(untilRead > 0);
    REQUIRE(untilRead <= 12000);
    REQUIRE(std::abs(centsBetween(82.41, noGate.back().frequency_hz)) < 1.0);
}

TEST_CASE("with no gate, and with a gate far under the sound, every reading is what it was before", "[analyzer][gate]") {
    const auto tone = harmonicTone(82.41, kRate, 1.0);
    TunerAnalyzer plain(config(), kRate);
    TunerAnalyzer wideOpen(config(), kRate, false, -110.0f);
    const auto a = feed(plain, tone);
    const auto b = feed(wideOpen, tone);
    REQUIRE(a.size() == b.size());
    REQUIRE(a.size() >= 40);
    for (size_t i = 0; i < a.size(); ++i) {
        INFO("hop " << i);
        REQUIRE(a[i].gate_open == 1);
        REQUIRE(b[i].gate_open == 1);
        REQUIRE(a[i].frequency_hz == b[i].frequency_hz);
        REQUIRE(a[i].clarity == b[i].clarity);
        REQUIRE(a[i].has_pitch == b[i].has_pitch);
        REQUIRE(a[i].input_peak == b[i].input_peak);
    }
    // What the engine published for this tone before the gate acted (recorded on 2026-10-05 from revision 4 with the
    // level measure and no gate rule in the analyzer): 82.4094 Hz at hop 10, 82.4100 Hz at hops 20 and 40, clarity 1.
    REQUIRE(std::abs(a[9].frequency_hz - 82.4094f) < 0.001f);
    REQUIRE(std::abs(a[19].frequency_hz - 82.41f) < 0.001f);
    REQUIRE(std::abs(a[39].frequency_hz - 82.41f) < 0.001f);
    REQUIRE(a[9].clarity > 0.99f);
    REQUIRE(a[39].has_pitch == 1);
}
