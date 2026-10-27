// The offline inspector (feature 009, research R5, R11; contracts/engine-c-abi-v6.md "Offline audio tools"): the
// decoder matrix over the committed fixtures, the refusals, and the ABI forwarder.
#include <catch2/catch_test_macros.hpp>

#include <cstdint>
#include <string>

#include "tembra_engine.h"
#include "timeline/audio_decoder.h"

#ifndef TEMBRA_FIXTURES_DIR
#error "tests/CMakeLists.txt must define TEMBRA_FIXTURES_DIR (specs/009-timeline-arranger/fixtures)"
#endif

namespace {

std::string fixture(const char* name) { return std::string(TEMBRA_FIXTURES_DIR) + "/" + name; }

struct Expected {
    const char* file;
    uint32_t format;
    uint32_t channels;
    uint32_t rate;
    int64_t frames;
};

}  // namespace

TEST_CASE("inspect reports format, channels, rate and frames for every accepted fixture", "[timeline][decoder]") {
    const Expected matrix[] = {
        {"tone-48k-stereo.wav", TEMBRA_FORMAT_WAV, 2, 48000, 48000},
        {"tone-44k1-mono-16.wav", TEMBRA_FORMAT_WAV, 1, 44100, 44100},
        {"tone-8bit-8k.wav", TEMBRA_FORMAT_WAV, 1, 8000, 8000},
        {"tone-float32-192k.wav", TEMBRA_FORMAT_WAV, 2, 192000, 48000},
        {"silence-5s.wav", TEMBRA_FORMAT_WAV, 1, 48000, 240000},
        {"tone-4ch.wav", TEMBRA_FORMAT_WAV, 4, 48000, 24000},
        {"tone.flac", TEMBRA_FORMAT_FLAC, 2, 48000, 48000},
    };
    for (const Expected& expected : matrix) {
        INFO(expected.file);
        tembra_audio_info info{};
        REQUIRE(tembra::AudioDecoder::inspect(fixture(expected.file).c_str(), info) == TEMBRA_OK);
        CHECK(info.format == expected.format);
        CHECK(info.channels == expected.channels);
        CHECK(info.sample_rate == expected.rate);
        CHECK(info.frames == expected.frames);
    }
}

TEST_CASE("inspect accepts the MP3 within the encoder's padding", "[timeline][decoder]") {
    tembra_audio_info info{};
    REQUIRE(tembra::AudioDecoder::inspect(fixture("tone.mp3").c_str(), info) == TEMBRA_OK);
    CHECK(info.format == TEMBRA_FORMAT_MP3);
    CHECK(info.channels == 2);
    CHECK(info.sample_rate == 48000);
    // One second of audio; the encoder adds a delay and pads the last frame (up to a few 1152-sample frames).
    CHECK(info.frames >= 47000);
    CHECK(info.frames <= 48000 + 4 * 1152);
}

TEST_CASE("inspect refuses what is not audio, a truncated WAV and a missing file", "[timeline][decoder]") {
    tembra_audio_info info{};
    REQUIRE(tembra::AudioDecoder::inspect(fixture("not-audio.txt").c_str(), info) == TEMBRA_ERR_DECODE);
    REQUIRE(tembra::AudioDecoder::inspect(fixture("truncated.wav").c_str(), info) == TEMBRA_ERR_DECODE);
    REQUIRE(tembra::AudioDecoder::inspect(fixture("does-not-exist.wav").c_str(), info) == TEMBRA_ERR_DECODE);
    REQUIRE(tembra::AudioDecoder::inspect(nullptr, info) == TEMBRA_ERR_ARGUMENT);
}

TEST_CASE("the ABI inspector forwards and checks its arguments", "[timeline][decoder][abi]") {
    tembra_audio_info info{};
    REQUIRE(tembra_audio_inspect(nullptr, &info) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_inspect(fixture("tone.flac").c_str(), nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_inspect(fixture("tone.flac").c_str(), &info) == TEMBRA_OK);
    CHECK(info.format == TEMBRA_FORMAT_FLAC);
    CHECK(info.frames == 48000);
    REQUIRE(tembra_audio_inspect(fixture("not-audio.txt").c_str(), &info) == TEMBRA_ERR_DECODE);
}

TEST_CASE("a decoder opened at another rate reports its output length and reads to the end", "[timeline][decoder]") {
    int32_t error = TEMBRA_OK;
    tembra_clip_info info{};
    auto decoder = tembra::AudioDecoder::open(fixture("tone-44k1-mono-16.wav").c_str(), 48000, &info, &error);
    REQUIRE(decoder);
    REQUIRE(error == TEMBRA_OK);
    CHECK(info.channels == 1);
    CHECK(info.sample_rate == 44100);
    CHECK(info.frames == 44100);
    CHECK(decoder->channels() == 1);
    CHECK(decoder->outputRate() == 48000);
    const int64_t expected = 44100LL * 48000 / 44100;
    CHECK(decoder->outputFrames() >= expected - 2);
    CHECK(decoder->outputFrames() <= expected + 2);
    std::vector<float> buffer(1024);
    int64_t total = 0;
    for (;;) {
        const size_t got = decoder->read(buffer.data(), 1024);
        if (got == 0) break;
        total += static_cast<int64_t>(got);
    }
    CHECK(total >= expected - 2);
    CHECK(total <= expected + 2);

    auto refused = tembra::AudioDecoder::open(fixture("not-audio.txt").c_str(), 48000, &info, &error);
    REQUIRE_FALSE(refused);
    REQUIRE(error == TEMBRA_ERR_DECODE);
}
