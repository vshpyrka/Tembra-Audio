// The five built-in click sounds (contracts/click-sounds.md, research R3): peak levels, envelopes, lengths and
// that the sounds are recognisably different.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <string>
#include <vector>

#include "metronome/click_synth.h"
#include "tembra_engine.h"

namespace {

constexpr double kPi = 3.14159265358979323846;

const std::array<tembra_click_sound, 5> kSounds{TEMBRA_SOUND_CLASSIC_WOOD, TEMBRA_SOUND_808_RIM, TEMBRA_SOUND_DIGITAL_BEEP,
                                                TEMBRA_SOUND_COWBELL, TEMBRA_SOUND_MECHANICAL};

double peakDb(const std::vector<float>& pcm) {
    float peak = 0.0f;
    for (float s : pcm) peak = std::max(peak, std::abs(s));
    return 20.0 * std::log10(static_cast<double>(peak));
}

double targetDb(tembra_click_level level) {
    switch (level) {
        case TEMBRA_CLICK_SUB: return -17.0;
        case TEMBRA_CLICK_CLICK: return -9.0;
        case TEMBRA_CLICK_ACCENT: return -3.0;
    }
    return 0.0;
}

/** Magnitude-weighted mean frequency of the DFT (Goertzel per bin, bins 1..N/2). */
double spectralCentroid(const std::vector<float>& pcm, uint32_t rate) {
    const size_t n = pcm.size();
    double weighted = 0.0;
    double total = 0.0;
    for (size_t k = 1; k <= n / 2; ++k) {
        const double w = 2.0 * kPi * static_cast<double>(k) / static_cast<double>(n);
        const double coeff = 2.0 * std::cos(w);
        double s1 = 0.0;
        double s2 = 0.0;
        for (float x : pcm) {
            const double s0 = static_cast<double>(x) + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        const double magnitude = std::sqrt(std::max(0.0, s1 * s1 + s2 * s2 - coeff * s1 * s2));
        weighted += magnitude * static_cast<double>(k) * rate / static_cast<double>(n);
        total += magnitude;
    }
    return weighted / total;
}

}  // namespace

TEST_CASE("every sound and level peaks at its target with a clean envelope", "[metronome][synth]") {
    for (tembra_click_sound sound : kSounds) {
        for (tembra_click_level level : {TEMBRA_CLICK_SUB, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_ACCENT}) {
            INFO("sound " << static_cast<int>(sound) << " level " << static_cast<int>(level));
            const tembra::ClickSample sample = tembra::ClickSynth::render(sound, level, 48000);
            REQUIRE(sample.rate == 48000u);
            REQUIRE(std::abs(peakDb(sample.pcm) - targetDb(level)) <= 0.5);
            REQUIRE(sample.pcm.front() == 0.0f);
            REQUIRE(sample.pcm.back() == 0.0f);
            REQUIRE(sample.pcm.size() <= static_cast<size_t>(0.120 * 48000));
            REQUIRE(sample.pcm.size() >= static_cast<size_t>(0.004 * 48000));
        }
    }
}

TEST_CASE("accent is at least 6 dB above click and click 6 dB above sub", "[metronome][synth]") {
    for (tembra_click_sound sound : kSounds) {
        INFO("sound " << static_cast<int>(sound));
        const double sub = peakDb(tembra::ClickSynth::render(sound, TEMBRA_CLICK_SUB, 48000).pcm);
        const double click = peakDb(tembra::ClickSynth::render(sound, TEMBRA_CLICK_CLICK, 48000).pcm);
        const double accent = peakDb(tembra::ClickSynth::render(sound, TEMBRA_CLICK_ACCENT, 48000).pcm);
        REQUIRE(accent - click >= 6.0 - 1e-6);
        REQUIRE(click - sub >= 6.0);
    }
}

TEST_CASE("the five click sounds are pairwise distinct by spectral centroid", "[metronome][synth]") {
    std::array<double, 5> centroids{};
    for (size_t i = 0; i < kSounds.size(); ++i) {
        centroids[i] = spectralCentroid(tembra::ClickSynth::render(kSounds[i], TEMBRA_CLICK_CLICK, 48000).pcm, 48000);
    }
    for (size_t i = 0; i < centroids.size(); ++i) {
        for (size_t j = i + 1; j < centroids.size(); ++j) {
            INFO("sounds " << i << " and " << j << ": " << centroids[i] << " Hz vs " << centroids[j] << " Hz");
            const double ratio = std::max(centroids[i], centroids[j]) / std::min(centroids[i], centroids[j]);
            REQUIRE(ratio >= 1.15);
        }
    }
}

TEST_CASE("rendering at 44.1 kHz keeps the durations in milliseconds", "[metronome][synth]") {
    for (tembra_click_sound sound : kSounds) {
        for (tembra_click_level level : {TEMBRA_CLICK_SUB, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_ACCENT}) {
            INFO("sound " << static_cast<int>(sound) << " level " << static_cast<int>(level));
            const auto at48 = tembra::ClickSynth::render(sound, level, 48000);
            const auto at44 = tembra::ClickSynth::render(sound, level, 44100);
            REQUIRE(at44.rate == 44100u);
            const double expected = static_cast<double>(at48.pcm.size()) * 44100.0 / 48000.0;
            REQUIRE(std::abs(static_cast<double>(at44.pcm.size()) - expected) <= 1.0);
            REQUIRE(at44.pcm.front() == 0.0f);
            REQUIRE(at44.pcm.back() == 0.0f);
        }
    }
}
