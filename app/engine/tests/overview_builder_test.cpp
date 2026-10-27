// The waveform overview builder (feature 009, research R10; contracts/engine-c-abi-v6.md ".peaks file format"): int8
// min/max per channel at 256/1024/4096/16384 frames per bucket, the pyramid, the periodic header flush that lets a
// reader draw a partial file, silence, and the host-side time budget (SC-007's early warning).
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <functional>
#include <string>
#include <vector>

#include "tembra_engine.h"
#include "timeline/overview_builder.h"
#include "wav_fixtures.h"

#ifndef TEMBRA_FIXTURES_DIR
#error "tests/CMakeLists.txt must define TEMBRA_FIXTURES_DIR (specs/009-timeline-arranger/fixtures)"
#endif

namespace {

std::string fixture(const char* name) { return std::string(TEMBRA_FIXTURES_DIR) + "/" + name; }

/** Streams a 16-bit PCM WAV of [frames] x [channels] from [sample] without holding it in memory. */
bool writeStreamedWav(const std::string& path, uint32_t rate, uint32_t channels, int64_t frames, const std::function<float(int64_t, uint32_t)>& sample) {
    FILE* file = std::fopen(path.c_str(), "wb");
    if (file == nullptr) return false;
    const uint32_t dataBytes = static_cast<uint32_t>(frames * channels * 2);
    const uint16_t blockAlign = static_cast<uint16_t>(channels * 2);
    std::vector<uint8_t> header;
    const auto u32 = [&](uint32_t v) { for (int b = 0; b < 4; ++b) header.push_back(static_cast<uint8_t>((v >> (8 * b)) & 0xFF)); };
    const auto u16 = [&](uint16_t v) { header.push_back(static_cast<uint8_t>(v & 0xFF)); header.push_back(static_cast<uint8_t>(v >> 8)); };
    const auto tag = [&](const char* t) { header.insert(header.end(), t, t + 4); };
    tag("RIFF");
    u32(36 + dataBytes);
    tag("WAVE");
    tag("fmt ");
    u32(16);
    u16(1);
    u16(static_cast<uint16_t>(channels));
    u32(rate);
    u32(rate * blockAlign);
    u16(blockAlign);
    u16(16);
    tag("data");
    u32(dataBytes);
    std::fwrite(header.data(), 1, header.size(), file);
    std::vector<int16_t> chunk(static_cast<size_t>(65536) * channels);
    for (int64_t done = 0; done < frames;) {
        const int64_t n = std::min<int64_t>(65536, frames - done);
        for (int64_t i = 0; i < n; ++i) {
            for (uint32_t c = 0; c < channels; ++c) {
                const float v = std::clamp(sample(done + i, c), -1.0f, 1.0f);
                chunk[static_cast<size_t>(i) * channels + c] = static_cast<int16_t>(std::lround(v * 32767.0));
            }
        }
        std::fwrite(chunk.data(), 2, static_cast<size_t>(n) * channels, file);
        done += n;
    }
    return std::fclose(file) == 0;
}

/** The reader's side of the contract, written independently of the builder. */
struct Peaks {
    uint16_t version = 0;
    uint16_t channels = 0;
    uint32_t levels = 0;
    uint32_t baseBucket = 0;
    int64_t frames = 0;
    uint64_t completed = 0;
    std::vector<std::vector<int8_t>> level;  // per level: (min, max) per channel per bucket

    static uint64_t bucketsOf(int64_t frames, uint32_t bucket) { return static_cast<uint64_t>((frames + bucket - 1) / bucket); }

    bool read(const std::string& path, bool withLevels = true) {
        const auto bytes = tembra::fixtures::readFile(path);
        if (bytes.size() < 32 || std::memcmp(bytes.data(), "TPK1", 4) != 0) return false;
        std::memcpy(&version, bytes.data() + 4, 2);
        std::memcpy(&channels, bytes.data() + 6, 2);
        std::memcpy(&levels, bytes.data() + 8, 4);
        std::memcpy(&baseBucket, bytes.data() + 12, 4);
        std::memcpy(&frames, bytes.data() + 16, 8);
        std::memcpy(&completed, bytes.data() + 24, 8);
        if (!withLevels) return true;
        level.assign(levels, {});
        size_t offset = 32;
        for (uint32_t l = 0; l < levels; ++l) {
            const uint64_t buckets = bucketsOf(frames, baseBucket << (2 * l));
            const size_t count = static_cast<size_t>(buckets) * channels * 2;
            if (offset + count > bytes.size()) return false;
            level[l].assign(reinterpret_cast<const int8_t*>(bytes.data() + offset), reinterpret_cast<const int8_t*>(bytes.data() + offset + count));
            offset += count;
        }
        return offset == bytes.size();
    }
};

int8_t quantise(float x) { return static_cast<int8_t>(std::clamp<long>(std::lround(x * 127.0f), -127, 127)); }

}  // namespace

TEST_CASE("the base level holds int8 min and max per channel per 256 frames", "[timeline][overview]") {
    const std::string out = tembra::fixtures::tempPath("tembra-overview-stereo.peaks");
    REQUIRE(tembra::OverviewBuilder::build(fixture("tone-48k-stereo.wav").c_str(), out.c_str()) == TEMBRA_OK);
    Peaks peaks;
    REQUIRE(peaks.read(out));
    CHECK(peaks.version == 1);
    CHECK(peaks.channels == 2);
    CHECK(peaks.levels == 4);
    CHECK(peaks.baseBucket == 256);
    CHECK(peaks.frames == 48000);
    CHECK(peaks.completed == Peaks::bucketsOf(48000, 256));
    REQUIRE(peaks.level[0].size() == Peaks::bucketsOf(48000, 256) * 2 * 2);
    // Brute force over the same signal the fixture holds (440 Hz left, 660 Hz right, 0.5 peak, 16-bit).
    for (uint64_t b = 0; b < Peaks::bucketsOf(48000, 256); ++b) {
        for (uint32_t c = 0; c < 2; ++c) {
            float lo = 1.0f;
            float hi = -1.0f;
            const double hz = c == 0 ? 440.0 : 660.0;
            for (int64_t i = static_cast<int64_t>(b * 256); i < std::min<int64_t>(48000, static_cast<int64_t>((b + 1) * 256)); ++i) {
                const float v = static_cast<float>(std::lround(0.5 * std::sin(2.0 * M_PI * hz * i / 48000.0) * 32767.0)) / 32767.0f;
                lo = std::min(lo, v);
                hi = std::max(hi, v);
            }
            INFO("bucket " << b << " channel " << c);
            REQUIRE(peaks.level[0][(b * 2 + c) * 2] == quantise(lo));
            REQUIRE(peaks.level[0][(b * 2 + c) * 2 + 1] == quantise(hi));
        }
    }
}

TEST_CASE("the coarser levels are the min and max of their children", "[timeline][overview]") {
    const std::string out = tembra::fixtures::tempPath("tembra-overview-mono.peaks");
    REQUIRE(tembra::OverviewBuilder::build(fixture("tone-44k1-mono-16.wav").c_str(), out.c_str()) == TEMBRA_OK);
    Peaks peaks;
    REQUIRE(peaks.read(out));
    CHECK(peaks.frames == 44100);
    CHECK(peaks.channels == 1);
    for (uint32_t l = 1; l < 4; ++l) {
        const uint64_t buckets = Peaks::bucketsOf(44100, 256u << (2 * l));
        REQUIRE(peaks.level[l].size() == buckets * 2);
        const uint64_t childBuckets = Peaks::bucketsOf(44100, 256u << (2 * (l - 1)));
        for (uint64_t b = 0; b < buckets; ++b) {
            int8_t lo = 127;
            int8_t hi = -127;
            for (uint64_t child = b * 4; child < std::min(childBuckets, (b + 1) * 4); ++child) {
                lo = std::min(lo, peaks.level[l - 1][child * 2]);
                hi = std::max(hi, peaks.level[l - 1][child * 2 + 1]);
            }
            INFO("level " << l << " bucket " << b);
            REQUIRE(peaks.level[l][b * 2] == lo);
            REQUIRE(peaks.level[l][b * 2 + 1] == hi);
        }
    }
}

TEST_CASE("a silent file yields all-zero buckets", "[timeline][overview]") {
    const std::string out = tembra::fixtures::tempPath("tembra-overview-silence.peaks");
    REQUIRE(tembra::OverviewBuilder::build(fixture("silence-5s.wav").c_str(), out.c_str()) == TEMBRA_OK);
    Peaks peaks;
    REQUIRE(peaks.read(out));
    CHECK(peaks.frames == 240000);
    for (uint32_t l = 0; l < 4; ++l) {
        for (int8_t value : peaks.level[l]) REQUIRE(value == 0);
    }
}

TEST_CASE("the header's completed count is updated at least every 10 s of audio while a long file builds", "[timeline][overview][slow]") {
    const std::string wav = tembra::fixtures::tempPath("tembra-overview-long-31min.wav");
    const int64_t frames = 48000LL * 60 * 31;
    REQUIRE(writeStreamedWav(wav, 48000, 1, frames, [](int64_t i, uint32_t) { return 0.5f * std::sin(2.0 * M_PI * 220.0 * static_cast<double>(i) / 48000.0); }));
    const std::string out = tembra::fixtures::tempPath("tembra-overview-long-31min.peaks");
    std::vector<uint64_t> completedSeen;
    std::vector<int64_t> framesSeen;
    const int32_t result = tembra::OverviewBuilder::build(wav.c_str(), out.c_str(), [&] {
        Peaks header;
        REQUIRE(header.read(out, false));
        completedSeen.push_back(header.completed);
        framesSeen.push_back(header.frames);
    });
    std::remove(wav.c_str());
    REQUIRE(result == TEMBRA_OK);
    REQUIRE(completedSeen.size() >= 31 * 6 - 1);  // at least one flush per 10 s of audio
    const uint64_t tenSeconds = 48000ULL * 10 / 256;
    REQUIRE(completedSeen.front() <= tenSeconds);
    for (size_t i = 1; i < completedSeen.size(); ++i) {
        REQUIRE(completedSeen[i] >= completedSeen[i - 1]);
        REQUIRE(completedSeen[i] - completedSeen[i - 1] <= tenSeconds);
    }
    for (int64_t f : framesSeen) REQUIRE(f == frames);  // the total is known from the first header
    Peaks peaks;
    REQUIRE(peaks.read(out));
    CHECK(peaks.completed == Peaks::bucketsOf(frames, 256));
    CHECK(completedSeen.back() == peaks.completed);
    std::remove(out.c_str());
}

TEST_CASE("a five-minute stereo 48 kHz WAV builds in under 1.5 s on the host", "[timeline][overview][slow]") {
    const std::string wav = tembra::fixtures::tempPath("tembra-overview-5min.wav");
    const int64_t frames = 48000LL * 60 * 5;
    REQUIRE(writeStreamedWav(wav, 48000, 2, frames, [](int64_t i, uint32_t c) {
        return 0.4f * std::sin(2.0 * M_PI * (c == 0 ? 330.0 : 495.0) * static_cast<double>(i) / 48000.0);
    }));
    const std::string out = tembra::fixtures::tempPath("tembra-overview-5min.peaks");
    const auto started = std::chrono::steady_clock::now();
    REQUIRE(tembra::OverviewBuilder::build(wav.c_str(), out.c_str()) == TEMBRA_OK);
    const double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
    std::remove(wav.c_str());
    INFO("built in " << seconds << " s");
    CHECK(seconds < 1.5);
    Peaks peaks;
    REQUIRE(peaks.read(out));
    CHECK(peaks.frames == frames);
    CHECK(peaks.channels == 2);
    std::remove(out.c_str());
}

TEST_CASE("the builder refuses what it cannot decode or write, and the ABI forwards", "[timeline][overview][abi]") {
    const std::string out = tembra::fixtures::tempPath("tembra-overview-refused.peaks");
    REQUIRE(tembra::OverviewBuilder::build(fixture("not-audio.txt").c_str(), out.c_str()) == TEMBRA_ERR_DECODE);
    REQUIRE(tembra::OverviewBuilder::build(fixture("tone.flac").c_str(), "/nonexistent-dir/x.peaks") == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_overview_build(nullptr, out.c_str()) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_overview_build(fixture("tone.flac").c_str(), nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_overview_build(fixture("tone.mp3").c_str(), out.c_str()) == TEMBRA_OK);
    Peaks peaks;
    REQUIRE(peaks.read(out));
    CHECK(peaks.channels == 2);
    CHECK(peaks.frames >= 47000);
    std::remove(out.c_str());
}
