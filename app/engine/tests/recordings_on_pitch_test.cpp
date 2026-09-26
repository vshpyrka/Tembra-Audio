// Every bundled drop-in recording must read within a cent of its note through the tuner's own analyzer (spec FR-022).
// With no recordings the test passes vacuously and says so; a synthetic WAV still proves the decode, resample and
// loop path.
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <filesystem>
#include <string>
#include <vector>

#include "signal_fixtures.h"
#include "tembra_engine.h"
#include "tuner/sample_player.h"
#include "tuner/tuner_analyzer.h"
#include "tuner/wav_decoder.h"
#include "wav_fixtures.h"

using namespace tembra;
using namespace tembra::fixtures;

namespace {

/** "guitar-Fsharp3.wav" → 185.0 Hz; returns 0 when the name does not follow the rule. */
double noteHzFromFileName(const std::string& stem) {
    const size_t dash = stem.find('-');
    if (dash == std::string::npos) return 0.0;
    std::string note = stem.substr(dash + 1);
    const size_t sharp = note.find("sharp");
    if (sharp != std::string::npos) note.replace(sharp, 5, "#");
    static const char* names[] = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    size_t split = 1;
    if (note.size() > 1 && note[1] == '#') split = 2;
    const std::string pitchClass = note.substr(0, split);
    int index = -1;
    for (int i = 0; i < 12; ++i) if (pitchClass == names[i]) index = i;
    if (index < 0 || split >= note.size()) return 0.0;
    const int octave = std::stoi(note.substr(split));
    const int midi = (octave + 1) * 12 + index;
    return 440.0 * std::pow(2.0, (midi - 69) / 12.0);
}

double medianEstimate(const DecodedSample& sample) {
    tembra_tuner_config config{};
    config.window_frames = 4096;
    config.hop_frames = 1024;
    config.min_hz = 50.0f;
    config.max_hz = 1400.0f;
    config.clarity_threshold = 0.5f;
    config.peak_ratio = 0.9f;
    config.median_size = 5;
    config.watchdog_seconds = 0.0f;
    TunerAnalyzer analyzer(config, sample.sampleRate);
    std::vector<double> estimates;
    for (size_t offset = 0; offset < sample.mono.size(); offset += 256) {
        const uint32_t frames = static_cast<uint32_t>(std::min<size_t>(256, sample.mono.size() - offset));
        analyzer.push(sample.mono.data() + offset, frames);
        while (analyzer.analyseAvailable()) {
            tembra_pitch_reading r{};
            analyzer.read(r);
            if (r.has_pitch) estimates.push_back(r.frequency_hz);
        }
    }
    if (estimates.empty()) return 0.0;
    std::sort(estimates.begin(), estimates.end());
    return estimates[estimates.size() / 2];
}

}  // namespace

TEST_CASE("bundled recordings are on pitch", "[recordings]") {
#ifdef TEMBRA_TUNER_RECORDINGS_DIR
    const std::filesystem::path dir(TEMBRA_TUNER_RECORDINGS_DIR);
    int checked = 0;
    if (std::filesystem::is_directory(dir)) {
        for (const auto& entry : std::filesystem::directory_iterator(dir)) {
            if (entry.path().extension() != ".wav") continue;
            const std::string stem = entry.path().stem().string();
            INFO(entry.path().string());
            const double target = noteHzFromFileName(stem);
            REQUIRE(target > 0.0);
            const auto bytes = readFile(entry.path().string());
            auto decoded = WavDecoder::decode(bytes.data(), bytes.size());
            REQUIRE(decoded.has_value());
            REQUIRE(decoded->mono.size() >= decoded->sampleRate);  // at least one second
            const double measured = medianEstimate(*decoded);
            REQUIRE(measured > 0.0);
            REQUIRE(std::abs(centsBetween(target, measured)) <= 1.0);
            ++checked;
        }
    }
    if (checked == 0) SUCCEED("no bundled recordings");
#else
    SUCCEED("no bundled recordings");
#endif
}

TEST_CASE("a synthetic recording decodes, resamples and loops through the player", "[recordings]") {
    const std::string path = tempPath("tembra-recording-e2.wav");
    REQUIRE(writeTempWav(path, 44100, 1, 16, harmonicTone(82.41, 44100.0, 1.2)));
    const auto bytes = readFile(path);
    auto decoded = WavDecoder::decode(bytes.data(), bytes.size());
    REQUIRE(decoded.has_value());
    REQUIRE(std::abs(centsBetween(82.41, medianEstimate(*decoded))) <= 1.0);
    SamplePlayer player(48000);
    player.play(*decoded, true);
    std::vector<float> stereo(480 * 2);
    float peak = 0.0f;
    for (int block = 0; block < 400; ++block) {
        player.render(stereo.data(), 480);
        for (float s : stereo) peak = std::max(peak, std::abs(s));
    }
    REQUIRE(peak > 0.3f);
    REQUIRE(player.isActive());
}
