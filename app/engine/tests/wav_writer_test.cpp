// The engine's own WAV writer (feature 010, research R6; contracts/engine-c-abi-v7.md): a 44-byte RIFF/PCM header
// the recorder thread rewrites every second so an abrupt end leaves a readable file, 24-bit little-endian samples
// clamped at full scale, and a write failure that is reported, never thrown.
#include <catch2/catch_test_macros.hpp>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "capture/wav_writer.h"
#include "tembra_engine.h"
#include "timeline/audio_decoder.h"
#include "wav_fixtures.h"

namespace {

uint32_t u32(const uint8_t* at) { return static_cast<uint32_t>(at[0]) | (static_cast<uint32_t>(at[1]) << 8) | (static_cast<uint32_t>(at[2]) << 16) | (static_cast<uint32_t>(at[3]) << 24); }
uint16_t u16(const uint8_t* at) { return static_cast<uint16_t>(at[0] | (at[1] << 8)); }

std::vector<float> rampSignal(size_t frames, uint32_t channels) {
    std::vector<float> out(frames * channels);
    for (size_t i = 0; i < frames; ++i) {
        for (uint32_t c = 0; c < channels; ++c) out[i * channels + c] = static_cast<float>((static_cast<double>(i % 1000) / 1000.0) * 0.8 - 0.4) + static_cast<float>(c) * 0.1f;
    }
    return out;
}

}  // namespace

TEST_CASE("the header carries the 24-bit PCM facts for one and two channels at 48 kHz", "[capture][wav]") {
    for (uint32_t channels : {1u, 2u}) {
        uint8_t header[tembra::WavWriter::kHeaderBytes];
        tembra::WavWriter::encodeHeader(header, channels, 48000, 96000);
        REQUIRE(std::memcmp(header, "RIFF", 4) == 0);
        REQUIRE(u32(header + 4) == 36 + 96000);
        REQUIRE(std::memcmp(header + 8, "WAVE", 4) == 0);
        REQUIRE(std::memcmp(header + 12, "fmt ", 4) == 0);
        REQUIRE(u32(header + 16) == 16);
        REQUIRE(u16(header + 20) == 1);  // PCM
        REQUIRE(u16(header + 22) == channels);
        REQUIRE(u32(header + 24) == 48000);
        REQUIRE(u32(header + 28) == 48000 * channels * 3);
        REQUIRE(u16(header + 32) == channels * 3);
        REQUIRE(u16(header + 34) == 24);
        REQUIRE(std::memcmp(header + 36, "data", 4) == 0);
        REQUIRE(u32(header + 40) == 96000);
    }
}

TEST_CASE("float samples become little-endian int24 with clamping at full scale", "[capture][wav]") {
    const float in[6] = {0.0f, 0.5f, -0.5f, 1.0f, 1.5f, -2.0f};
    uint8_t out[18];
    tembra::WavWriter::encodeInt24(in, 6, out);
    auto value = [&](size_t i) {
        int32_t v = static_cast<int32_t>(out[i * 3]) | (static_cast<int32_t>(out[i * 3 + 1]) << 8) | (static_cast<int32_t>(out[i * 3 + 2]) << 16);
        if (v & 0x800000) v -= 0x1000000;
        return v;
    };
    REQUIRE(value(0) == 0);
    REQUIRE(value(1) == 4194304);   // 0.5 × 2^23 rounded
    REQUIRE(value(2) == -4194304);
    REQUIRE(value(3) == 8388607);   // +1.0 clamps to the top code
    REQUIRE(value(4) == 8388607);   // above full scale clamps
    REQUIRE(value(5) == -8388608);  // below full scale clamps
}

TEST_CASE("after refreshSizes the file decodes with the frames written so far, and finish leaves the final sizes", "[capture][wav]") {
    const std::string path = tembra::fixtures::tempPath("tembra-wav-writer-refresh.wav");
    tembra::WavWriter writer;
    REQUIRE(writer.open(path.c_str(), 2, 48000));
    REQUIRE(writer.framesWritten() == 0);
    const std::vector<float> first = rampSignal(4800, 2);
    REQUIRE(writer.write(first.data(), 4800));
    REQUIRE(writer.refreshSizes());
    tembra_audio_info info{};
    REQUIRE(tembra::AudioDecoder::inspect(path.c_str(), info) == TEMBRA_OK);
    REQUIRE(info.channels == 2);
    REQUIRE(info.sample_rate == 48000);
    REQUIRE(info.frames == 4800);
    REQUIRE(info.format == TEMBRA_FORMAT_WAV);
    // More audio after the refresh lands after what was written, not over it.
    const std::vector<float> second = rampSignal(960, 2);
    REQUIRE(writer.write(second.data(), 960));
    REQUIRE(writer.finish());
    REQUIRE_FALSE(writer.isOpen());
    REQUIRE(tembra::AudioDecoder::inspect(path.c_str(), info) == TEMBRA_OK);
    REQUIRE(info.frames == 5760);
    const auto bytes = tembra::fixtures::readFile(path);
    REQUIRE(bytes.size() == 44 + 5760 * 2 * 3);
    REQUIRE(u32(bytes.data() + 40) == 5760 * 2 * 3);
    REQUIRE(u32(bytes.data() + 4) == 36 + 5760 * 2 * 3);
    // The samples round-trip through the decoder within int24 precision.
    int32_t error = TEMBRA_OK;
    auto decoder = tembra::AudioDecoder::open(path.c_str(), 48000, nullptr, &error);
    REQUIRE(decoder);
    std::vector<float> back(5760 * 2);
    REQUIRE(decoder->read(back.data(), 5760) == 5760);
    for (size_t i = 0; i < first.size(); ++i) REQUIRE(std::abs(back[i] - first[i]) < 2.0f / 8388608.0f);
    for (size_t i = 0; i < second.size(); ++i) REQUIRE(std::abs(back[first.size() + i] - second[i]) < 2.0f / 8388608.0f);
    std::remove(path.c_str());
}

TEST_CASE("a path that cannot be created and a file lost under the writer are reported, not thrown", "[capture][wav]") {
    tembra::WavWriter refused;
    REQUIRE_FALSE(refused.open("/nonexistent-tembra-dir/take.wav", 1, 48000));
    REQUIRE_FALSE(refused.isOpen());
    const std::string path = tembra::fixtures::tempPath("tembra-wav-writer-lost.wav");
    tembra::WavWriter writer;
    REQUIRE(writer.open(path.c_str(), 1, 48000));
    const std::vector<float> block = rampSignal(256, 1);
    REQUIRE(writer.write(block.data(), 256));
    writer.loseFileForTest();  // the FILE* is closed beneath the writer
    REQUIRE_FALSE(writer.write(block.data(), 256));
    REQUIRE(writer.failed());
    REQUIRE_FALSE(writer.refreshSizes());
    REQUIRE_FALSE(writer.finish());
    REQUIRE(writer.framesWritten() == 256);
    std::remove(path.c_str());
}
