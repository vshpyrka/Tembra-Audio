// Preparation of a custom click (contracts/click-sounds.md, research R4): onset alignment, the 2 s cap, fades,
// the three levels and resampling to the stream rate. Fixtures are generated here: a 20 ms decaying sine "click"
// after N ms of silence with an optional tail.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

#include "metronome/click_sample_builder.h"
#include "metronome/click_set.h"
#include "metronome/sound_resampler.h"
#include "tembra_engine.h"

namespace {

constexpr double kPi = 3.14159265358979323846;

size_t frames(double ms, uint32_t rate) { return static_cast<size_t>(std::llround(ms * rate / 1000.0)); }

/** [leadMs] of silence, a 20 ms 1 kHz sine at 0.5 decaying with a 5 ms time constant, then [tailMs] of a 0.2 ring. */
std::vector<float> decayingClick(uint32_t rate, double leadMs, double tailMs = 0.0) {
    std::vector<float> out(frames(leadMs, rate), 0.0f);
    const size_t clickFrames = frames(20.0, rate);
    for (size_t i = 0; i < clickFrames; ++i) {
        const double t = static_cast<double>(i) / rate;
        out.push_back(static_cast<float>(0.5 * std::exp(-t / 0.005) * std::sin(2.0 * kPi * 1000.0 * t)));
    }
    const size_t tailFrames = frames(tailMs, rate);
    for (size_t i = 0; i < tailFrames; ++i) {
        const double t = static_cast<double>(i + clickFrames) / rate;
        out.push_back(static_cast<float>(0.2 * std::sin(2.0 * kPi * 1000.0 * t)));
    }
    return out;
}

float peakOf(const std::vector<float>& pcm) {
    float peak = 0.0f;
    for (float s : pcm) peak = std::max(peak, std::abs(s));
    return peak;
}

double peakDb(const std::vector<float>& pcm) { return 20.0 * std::log10(static_cast<double>(peakOf(pcm))); }

/** First frame whose magnitude is above −40 dB relative to the peak. */
size_t onsetOf(const std::vector<float>& pcm) {
    const float threshold = peakOf(pcm) * 0.01f;
    for (size_t i = 0; i < pcm.size(); ++i) {
        if (std::abs(pcm[i]) > threshold) return i;
    }
    return pcm.size();
}

}  // namespace

TEST_CASE("leading silence is trimmed to the onset with a 1 ms fade-in", "[metronome][builder]") {
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::ClickSampleBuilder::prepare(decayingClick(48000, 100.0), 48000, 48000, &info, &set) == TEMBRA_OK);
    const std::vector<float>& click = set.click.pcm;
    REQUIRE(set.click.rate == 48000u);
    REQUIRE(onsetOf(click) <= frames(1.0, 48000));
    REQUIRE(click.front() == 0.0f);
    const float peak = peakOf(click);
    const size_t fade = frames(1.0, 48000);
    for (size_t n = 0; n < fade; ++n) {
        const double envelope = 0.5 - 0.5 * std::cos(kPi * static_cast<double>(n) / static_cast<double>(fade));
        REQUIRE(std::abs(click[n]) <= envelope * peak + 1e-6);
    }
    REQUIRE(info.trimmed_ms >= 98.0f);
    REQUIRE(info.trimmed_ms <= 100.5f);
    REQUIRE(info.source_rate == 48000u);
    REQUIRE(info.kept_frames == click.size());
    REQUIRE(std::abs(info.kept_ms - static_cast<float>(click.size()) / 48.0f) < 0.01f);
}

TEST_CASE("the three levels are normalised from the prepared click", "[metronome][builder]") {
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::ClickSampleBuilder::prepare(decayingClick(48000, 10.0), 48000, 48000, &info, &set) == TEMBRA_OK);
    REQUIRE(std::abs(peakDb(set.click.pcm) - -9.0) <= 0.5);
    REQUIRE(std::abs(peakDb(set.accent.pcm) - -3.0) <= 0.5);
    REQUIRE(peakDb(set.accent.pcm) <= -1.0);  // the +6 dB accent is limited to −1 dBFS
    REQUIRE(std::abs(peakDb(set.sub.pcm) - -17.0) <= 0.5);
    REQUIRE(set.accent.pcm.size() == set.click.pcm.size());
    REQUIRE(set.sub.pcm.size() == set.click.pcm.size());
}

TEST_CASE("a 3 s ringing tail is cut 2 s after the onset with a 5 ms fade-out to zero", "[metronome][builder]") {
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::ClickSampleBuilder::prepare(decayingClick(48000, 10.0, 3000.0), 48000, 48000, &info, &set) == TEMBRA_OK);
    const std::vector<float>& click = set.click.pcm;
    const size_t onset = onsetOf(click);
    const double keptAfterOnset = static_cast<double>(click.size() - onset);
    REQUIRE(std::abs(keptAfterOnset - 96000.0) <= 48.0);
    REQUIRE(click.back() == 0.0f);
    const float peak = peakOf(click);
    const size_t fade = frames(5.0, 48000);
    const size_t fadeStart = click.size() - fade;
    for (size_t j = 0; j < fade; ++j) {
        const double envelope = 0.5 + 0.5 * std::cos(kPi * static_cast<double>(j) / static_cast<double>(fade));
        REQUIRE(std::abs(click[fadeStart + j]) <= envelope * peak + 1e-6);
    }
}

TEST_CASE("a 4 ms transient is kept whole with the fade-out over its last 2 ms", "[metronome][builder]") {
    std::vector<float> source(frames(10.0, 48000), 0.0f);
    source.insert(source.end(), frames(4.0, 48000), 0.5f);  // a DC pulse: gain is visible sample by sample
    source.insert(source.end(), frames(50.0, 48000), 0.0f);
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::ClickSampleBuilder::prepare(source, 48000, 48000, &info, &set) == TEMBRA_OK);
    const std::vector<float>& click = set.click.pcm;
    const double expected = static_cast<double>(frames(4.0, 48000) + frames(1.0, 48000));
    REQUIRE(std::abs(static_cast<double>(click.size()) - expected) <= 2.0);
    REQUIRE(click.back() == 0.0f);
    const float level = static_cast<float>(std::pow(10.0, -9.0 / 20.0));
    // 3 ms before the end the pulse is untouched; 1 ms before the end it is about half way down the 2 ms fade.
    REQUIRE(std::abs(click[click.size() - 1 - frames(3.0, 48000)] - level) < 1e-3f);
    const float middle = click[click.size() - 1 - frames(1.0, 48000)];
    REQUIRE(middle > 0.3f * level);
    REQUIRE(middle < 0.7f * level);
}

TEST_CASE("a 96 kHz source is resampled to 48 kHz", "[metronome][builder]") {
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::ClickSampleBuilder::prepare(decayingClick(96000, 100.0, 200.0), 96000, 48000, &info, &set) == TEMBRA_OK);
    REQUIRE(info.source_rate == 96000u);
    REQUIRE(set.click.rate == 48000u);
    REQUIRE(set.accent.rate == 48000u);
    REQUIRE(set.sub.rate == 48000u);
    const double expected = static_cast<double>(info.kept_frames) * 0.5;
    REQUIRE(std::abs(static_cast<double>(set.click.pcm.size()) - expected) <= 1.0);
    REQUIRE(std::abs(peakDb(set.click.pcm) - -9.0) <= 0.5);
}

TEST_CASE("an all-silent input is refused", "[metronome][builder]") {
    std::vector<float> quiet(48000, 0.0f);
    for (size_t i = 0; i < quiet.size(); i += 7) quiet[i] = 0.0005f;  // −66 dBFS
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::ClickSampleBuilder::prepare(quiet, 48000, 48000, &info, &set) == TEMBRA_ERR_SILENT);
    REQUIRE(tembra::ClickSampleBuilder::prepare(std::vector<float>(4800, 0.0f), 48000, 48000, &info, &set) == TEMBRA_ERR_SILENT);
    REQUIRE(tembra::ClickSampleBuilder::prepare(std::vector<float>(), 48000, 48000, &info, &set) == TEMBRA_ERR_SILENT);
}

TEST_CASE("the resampler keeps frequency and length", "[metronome][resampler]") {
    const uint32_t from = 96000;
    const uint32_t to = 48000;
    std::vector<float> sine(48000);
    for (size_t i = 0; i < sine.size(); ++i) sine[i] = static_cast<float>(0.5 * std::sin(2.0 * kPi * 1000.0 * static_cast<double>(i) / from));

    SECTION("96 kHz to 48 kHz") {
        const std::vector<float> out = tembra::SoundResampler::resample(sine, from, to);
        REQUIRE(std::abs(static_cast<double>(out.size()) - 24000.0) <= 1.0);
        // Positive-going zero crossings, interpolated, away from the edges.
        std::vector<double> crossings;
        for (size_t i = 100; i + 100 < out.size(); ++i) {
            if (out[i - 1] < 0.0f && out[i] >= 0.0f) {
                const double fraction = static_cast<double>(-out[i - 1]) / static_cast<double>(out[i] - out[i - 1]);
                crossings.push_back(static_cast<double>(i - 1) + fraction);
            }
        }
        REQUIRE(crossings.size() > 10);
        const double period = (crossings.back() - crossings.front()) / static_cast<double>(crossings.size() - 1);
        const double hz = static_cast<double>(to) / period;
        REQUIRE(std::abs(hz - 1000.0) / 1000.0 < 0.001);
    }

    SECTION("equal rates are the identity") {
        const std::vector<float> out = tembra::SoundResampler::resample(sine, from, from);
        REQUIRE(out == sine);
    }
}
