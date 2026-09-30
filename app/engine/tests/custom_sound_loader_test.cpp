// The custom click loader (contracts/click-sounds.md, research R4): dr_wav decode of every accepted encoding, the
// 30 s read cap, the downmix and the refusals.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

#include "metronome/click_set.h"
#include "metronome/custom_sound_loader.h"
#include "tembra_engine.h"
#include "wav_fixtures.h"

namespace {

constexpr double kPi = 3.14159265358979323846;

/** [seconds] of a 1 kHz sine peaking at exactly 0.5 (a sample falls on every crest at 8 and 192 kHz). */
std::vector<float> sineAtHalf(uint32_t rate, double seconds) {
    std::vector<float> out(static_cast<size_t>(seconds * rate));
    for (size_t i = 0; i < out.size(); ++i) out[i] = static_cast<float>(0.5 * std::sin(2.0 * kPi * 1000.0 * static_cast<double>(i) / rate));
    return out;
}

std::vector<float> interleave(const std::vector<float>& mono, uint32_t channels) {
    std::vector<float> out;
    out.reserve(mono.size() * channels);
    for (float s : mono) {
        for (uint32_t c = 0; c < channels; ++c) out.push_back(s);
    }
    return out;
}

double peakDb(const std::vector<float>& pcm) {
    float peak = 0.0f;
    for (float s : pcm) peak = std::max(peak, std::abs(s));
    return 20.0 * std::log10(static_cast<double>(peak));
}

const char* nameOf(tembra::fixtures::WavEncoding encoding) {
    switch (encoding) {
        case tembra::fixtures::WavEncoding::Pcm8: return "pcm8";
        case tembra::fixtures::WavEncoding::Pcm16: return "pcm16";
        case tembra::fixtures::WavEncoding::Pcm24: return "pcm24";
        case tembra::fixtures::WavEncoding::Pcm32: return "pcm32";
        case tembra::fixtures::WavEncoding::Float32: return "float32";
    }
    return "?";
}

}  // namespace

TEST_CASE("every accepted encoding decodes mono and stereo at 8 and 192 kHz", "[metronome][loader]") {
    using tembra::fixtures::WavEncoding;
    for (WavEncoding encoding : {WavEncoding::Pcm8, WavEncoding::Pcm16, WavEncoding::Pcm24, WavEncoding::Pcm32, WavEncoding::Float32}) {
        for (uint32_t channels : {1u, 2u}) {
            for (uint32_t rate : {8000u, 192000u}) {
                INFO(nameOf(encoding) << " channels " << channels << " rate " << rate);
                const std::vector<float> mono = sineAtHalf(rate, 0.25);
                const std::string path = tembra::fixtures::tempPath(std::string("tembra-loader-") + nameOf(encoding) + "-" +
                                                                    std::to_string(channels) + "-" + std::to_string(rate) + ".wav");
                REQUIRE(tembra::fixtures::writeWav(path, rate, channels, encoding, interleave(mono, channels)));

                std::vector<float> decoded;
                tembra_custom_sound_info info{};
                REQUIRE(tembra::CustomSoundLoader::decode(path.c_str(), &decoded, &info) == TEMBRA_OK);
                REQUIRE(decoded.size() == mono.size());
                REQUIRE(std::abs(peakDb(decoded) - peakDb(mono)) <= 0.1);
                REQUIRE(info.source_rate == rate);
                REQUIRE(info.source_channels == channels);
                REQUIRE(info.source_bits == tembra::fixtures::bitsOf(encoding));

                tembra::ClickSet set;
                tembra_custom_sound_info loaded{};
                REQUIRE(tembra::CustomSoundLoader::load(path.c_str(), 48000, &loaded, &set) == TEMBRA_OK);
                REQUIRE(loaded.source_rate == rate);
                REQUIRE(loaded.source_channels == channels);
                REQUIRE(loaded.source_bits == tembra::fixtures::bitsOf(encoding));
                REQUIRE(set.click.rate == 48000u);
                REQUIRE(std::abs(peakDb(set.click.pcm) - -9.0) <= 0.5);
                std::remove(path.c_str());
            }
        }
    }
}

TEST_CASE("only the first 30 s of a long file are read", "[metronome][loader]") {
    const uint32_t rate = 8000;
    // 40 s: silence until 29.5 s, then a sine to the end. Read in full the kept sound would be capped at 2 s; read
    // for 30 s it is the half second before the cap.
    std::vector<float> samples(static_cast<size_t>(40.0 * rate), 0.0f);
    const size_t onset = static_cast<size_t>(29.5 * rate);
    for (size_t i = onset; i < samples.size(); ++i) samples[i] = static_cast<float>(0.5 * std::sin(2.0 * kPi * 1000.0 * static_cast<double>(i - onset) / rate));
    const std::string path = tembra::fixtures::tempPath("tembra-loader-40s.wav");
    REQUIRE(tembra::fixtures::writeWav(path, rate, 1, tembra::fixtures::WavEncoding::Pcm16, samples));

    std::vector<float> decoded;
    tembra_custom_sound_info info{};
    REQUIRE(tembra::CustomSoundLoader::decode(path.c_str(), &decoded, &info) == TEMBRA_OK);
    REQUIRE(decoded.size() == static_cast<size_t>(30 * rate));

    tembra::ClickSet set;
    REQUIRE(tembra::CustomSoundLoader::load(path.c_str(), 48000, &info, &set) == TEMBRA_OK);
    REQUIRE(info.kept_ms > 490.0f);
    REQUIRE(info.kept_ms < 515.0f);
    std::remove(path.c_str());
}

TEST_CASE("bytes that are not a WAV are refused as undecodable", "[metronome][loader]") {
    const std::string path = tembra::fixtures::tempPath("tembra-loader-random.wav");
    {
        std::ofstream file(path, std::ios::binary);
        uint32_t state = 12345u;
        for (int i = 0; i < 4096; ++i) {
            state = state * 1664525u + 1013904223u;
            file.put(static_cast<char>(state >> 24));
        }
    }
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::CustomSoundLoader::load(path.c_str(), 48000, &info, &set) == TEMBRA_ERR_DECODE);
    std::remove(path.c_str());
}

TEST_CASE("a missing path is an argument error", "[metronome][loader]") {
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    const std::string path = tembra::fixtures::tempPath("tembra-loader-does-not-exist.wav");
    REQUIRE(tembra::CustomSoundLoader::load(path.c_str(), 48000, &info, &set) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra::CustomSoundLoader::load(nullptr, 48000, &info, &set) == TEMBRA_ERR_ARGUMENT);
}

TEST_CASE("a compressed (ADPCM) WAV is refused as undecodable", "[metronome][loader]") {
    const std::string path = tembra::fixtures::tempPath("tembra-loader-adpcm.wav");
    REQUIRE(tembra::fixtures::writeTaggedWav(path, 2, 48000, 1, 4, 2048));
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::CustomSoundLoader::load(path.c_str(), 48000, &info, &set) == TEMBRA_ERR_DECODE);
    std::remove(path.c_str());
}

TEST_CASE("a silent WAV is refused as silent", "[metronome][loader]") {
    const std::string path = tembra::fixtures::tempPath("tembra-loader-silent.wav");
    REQUIRE(tembra::fixtures::writeWav(path, 48000, 1, tembra::fixtures::WavEncoding::Pcm16, std::vector<float>(4800, 0.0f)));
    tembra_custom_sound_info info{};
    tembra::ClickSet set;
    REQUIRE(tembra::CustomSoundLoader::load(path.c_str(), 48000, &info, &set) == TEMBRA_ERR_SILENT);
    std::remove(path.c_str());
}
