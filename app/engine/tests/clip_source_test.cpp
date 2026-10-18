// The clip source (feature 009, research R5; contracts/engine-c-abi-v6.md "Clip sources"): a decoder on a fixture,
// its fifo, prime and the callback-side pop with its seek accuracy, skip, park and starvation rules.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "fake_decoder.h"
#include "tembra_engine.h"
#include "timeline/audio_decoder.h"
#include "timeline/clip_source.h"

#ifndef TEMBRA_FIXTURES_DIR
#error "tests/CMakeLists.txt must define TEMBRA_FIXTURES_DIR (specs/009-timeline-arranger/fixtures)"
#endif

namespace {

constexpr uint32_t kRate = 48000;

std::string fixture(const char* name) { return std::string(TEMBRA_FIXTURES_DIR) + "/" + name; }

std::unique_ptr<tembra::ClipSource> openSource(const char* name, uint32_t rate = kRate) {
    int32_t error = TEMBRA_OK;
    tembra_clip_info info{};
    auto decoder = tembra::AudioDecoder::open(fixture(name).c_str(), rate, &info, &error);
    REQUIRE(decoder);
    REQUIRE(error == TEMBRA_OK);
    return std::make_unique<tembra::ClipSource>(std::move(decoder), info, fixture(name));
}

/** Fills the fifo from [frame] and pops [frames]; the pop must not starve. */
std::vector<float> primeAndPop(tembra::ClipSource& source, int64_t frame, uint32_t frames) {
    REQUIRE(source.prime(frame));
    std::vector<float> out(static_cast<size_t>(frames) * source.channels());
    REQUIRE(source.pop(frame, frames, out.data()));
    return out;
}

/** Decodes the whole source at the output rate through prime/refill/pop. */
std::vector<float> decodeAll(tembra::ClipSource& source) {
    std::vector<float> all;
    REQUIRE(source.prime(0));
    std::vector<float> block(1024 * source.channels());
    int64_t frame = 0;
    for (;;) {
        source.refill();
        const int64_t remaining = source.outputFrames() - frame;
        if (remaining <= 0) break;
        const uint32_t want = static_cast<uint32_t>(std::min<int64_t>(1024, remaining));
        source.pop(frame, want, block.data());
        all.insert(all.end(), block.begin(), block.begin() + static_cast<std::ptrdiff_t>(want * source.channels()));
        frame += want;
    }
    return all;
}

/** Largest absolute difference between [a] at [offsetA] and [b] at [offsetB] over [count] floats. */
float maxDiff(const std::vector<float>& a, size_t offsetA, const std::vector<float>& b, size_t offsetB, size_t count) {
    float worst = 0.0f;
    for (size_t i = 0; i < count; ++i) worst = std::max(worst, std::abs(a[offsetA + i] - b[offsetB + i]));
    return worst;
}

}  // namespace

TEST_CASE("every fixture opens with its tembra_clip_info", "[timeline][clip]") {
    struct Expected {
        const char* file;
        uint32_t format;
        uint32_t channels;
        uint32_t rate;
        int64_t frames;
        int64_t outputFrames;
    };
    const Expected matrix[] = {
        {"tone-48k-stereo.wav", TEMBRA_FORMAT_WAV, 2, 48000, 48000, 48000},
        {"tone-44k1-mono-16.wav", TEMBRA_FORMAT_WAV, 1, 44100, 44100, 48000},
        {"tone-8bit-8k.wav", TEMBRA_FORMAT_WAV, 1, 8000, 8000, 48000},
        {"tone-float32-192k.wav", TEMBRA_FORMAT_WAV, 2, 192000, 48000, 12000},
        {"tone-4ch.wav", TEMBRA_FORMAT_WAV, 4, 48000, 24000, 24000},
        {"tone.flac", TEMBRA_FORMAT_FLAC, 2, 48000, 48000, 48000},
    };
    for (const Expected& expected : matrix) {
        INFO(expected.file);
        auto source = openSource(expected.file);
        CHECK(source->info().format == expected.format);
        CHECK(source->info().channels == expected.channels);
        CHECK(source->info().sample_rate == expected.rate);
        CHECK(source->info().frames == expected.frames);
        CHECK(source->channels() == expected.channels);
        CHECK(source->outputRate() == kRate);
        CHECK(source->outputFrames() >= expected.outputFrames - 2);
        CHECK(source->outputFrames() <= expected.outputFrames + 2);
        CHECK(source->fifoFrames() == tembra::ClipSource::kFifoFrames);
        // The whole file streams through the fifo with the expected number of output frames.
        const auto all = decodeAll(*source);
        const int64_t streamed = static_cast<int64_t>(all.size() / expected.channels);
        CHECK(streamed >= expected.outputFrames - 2);
        CHECK(streamed <= expected.outputFrames + 2);
        CHECK(source->starved() == 0);
    }
    auto mp3 = openSource("tone.mp3");
    CHECK(mp3->info().format == TEMBRA_FORMAT_MP3);
    CHECK(mp3->channels() == 2);
    CHECK(mp3->info().sample_rate == 48000);
    CHECK(mp3->info().frames >= 47000);
}

TEST_CASE("a seek lands within one output frame on WAV and FLAC", "[timeline][clip]") {
    for (const char* file : {"tone-48k-stereo.wav", "tone.flac", "tone-44k1-mono-16.wav"}) {
        INFO(file);
        auto whole = openSource(file);
        const auto all = decodeAll(*whole);
        const uint32_t channels = whole->channels();
        auto seeking = openSource(file);
        for (int64_t frame : {1LL, 777LL, 12345LL, 30000LL}) {
            const auto block = primeAndPop(*seeking, frame, 256);
            // Exact on the same-rate files. The resampled file lands within half a source frame (a 0.014 phase
            // difference on this sine) after a 16-frame filter warm-up, so it is compared from frame 64 on.
            const bool resampled = std::string(file).find("44k1") != std::string::npos;
            const float tolerance = resampled ? 0.02f : 1e-6f;
            const size_t from = resampled ? 64 : 0;
            float best = 1.0f;
            int64_t bestShift = 99;
            for (int64_t shift = -1; shift <= 1; ++shift) {
                const float diff = maxDiff(all, static_cast<size_t>((frame + shift) * channels + from * channels), block, from * channels, (256 - from) * channels);
                if (diff < best) {
                    best = diff;
                    bestShift = shift;
                }
            }
            INFO("frame " << frame << " shift " << bestShift << " diff " << best);
            CHECK(best <= tolerance);
        }
    }
}

TEST_CASE("a seek lands within one MP3 frame on MP3", "[timeline][clip]") {
    auto whole = openSource("tone.mp3");
    const auto all = decodeAll(*whole);
    auto seeking = openSource("tone.mp3");
    const int64_t frame = 20000;
    const auto block = primeAndPop(*seeking, frame, 512);
    float best = 1.0f;
    int64_t bestShift = 0;
    for (int64_t shift = -1152; shift <= 1152; ++shift) {
        if (frame + shift < 0) continue;
        const float diff = maxDiff(all, static_cast<size_t>((frame + shift) * 2), block, 0, 512 * 2);
        if (diff < best) {
            best = diff;
            bestShift = shift;
        }
    }
    // dr_mp3's seek table lands within one MP3 frame and its decoder state differs slightly from a linear decode
    // (research R5 accepted this: the contract bounds the seek, not the bit-exactness).
    INFO("best shift " << bestShift << " diff " << best);
    CHECK(best < 0.01f);
    CHECK(std::abs(bestShift) <= 1152);
}

TEST_CASE("a pop ahead within the fifo skips, a pop outside parks and requests, a dry fifo starves", "[timeline][clip]") {
    auto decoder = std::make_unique<tembra::FakeDecoder>(1'000'000, 2, kRate);
    tembra::ClipSource source(std::move(decoder), tembra::fakeInfo(1'000'000, 2, kRate), "fake");
    REQUIRE(source.prime(1000));
    REQUIRE(source.primedFrom() == 1000);
    REQUIRE(source.pendingRequest() == -1);
    std::vector<float> out(512 * 2);

    // Contiguous pops read the ramp exactly.
    REQUIRE(source.pop(1000, 512, out.data()));
    CHECK(out[0] == tembra::FakeDecoder::ramp()(1000, 0));
    CHECK(out[1] == tembra::FakeDecoder::ramp()(1000, 1));
    CHECK(out[2 * 511] == tembra::FakeDecoder::ramp()(1511, 0));
    REQUIRE(source.pop(1512, 512, out.data()));
    CHECK(out[0] == tembra::FakeDecoder::ramp()(1512, 0));

    // A jump forward that the fifo still holds is skipped to.
    REQUIRE(source.pop(5000, 512, out.data()));
    CHECK(out[0] == tembra::FakeDecoder::ramp()(5000, 0));
    CHECK(source.starved() == 0);

    // A jump beyond the fifo (backwards here) parks the source: silence, a starvation mark and a request.
    REQUIRE_FALSE(source.pop(200, 512, out.data()));
    CHECK(out[0] == 0.0f);
    CHECK(out[1023] == 0.0f);
    CHECK(source.starved() == 1);
    CHECK(source.pendingRequest() == 200);
    CHECK(source.pendingNeededAt() >= 0);
    REQUIRE_FALSE(source.pop(712, 512, out.data()));  // still parked until the streamer primes
    CHECK(source.starved() == 2);

    // The streamer services the request; the next pop at the primed frame resumes.
    REQUIRE(source.prime(source.pendingRequest()));
    CHECK(source.pendingRequest() == -1);
    REQUIRE(source.pop(200, 512, out.data()));
    CHECK(out[0] == tembra::FakeDecoder::ramp()(200, 0));

    // Draining the fifo without a refill leaves the rest silent and counts a starvation once per pop.
    const uint32_t held = static_cast<uint32_t>(source.fifoFrames());
    std::vector<float> big(static_cast<size_t>(held) * 2 + 2 * 64);
    const uint32_t beforeStarve = source.starved();
    REQUIRE_FALSE(source.pop(712, held + 64, big.data()));
    CHECK(source.starved() == beforeStarve + 1);
    CHECK(big[big.size() - 1] == 0.0f);
    CHECK_FALSE(source.eof());
}

TEST_CASE("the end of the source is silence without a starvation mark", "[timeline][clip]") {
    auto decoder = std::make_unique<tembra::FakeDecoder>(1000, 1, kRate);
    tembra::ClipSource source(std::move(decoder), tembra::fakeInfo(1000, 1, kRate), "fake");
    REQUIRE(source.prime(900));
    CHECK(source.eof());
    std::vector<float> out(256);
    REQUIRE(source.pop(900, 256, out.data()));
    CHECK(out[99] == tembra::FakeDecoder::ramp()(999, 0));
    CHECK(out[100] == 0.0f);
    CHECK(out[255] == 0.0f);
    CHECK(source.starved() == 0);
    REQUIRE(source.pop(1156, 256, out.data()));  // entirely past the end: silence
    CHECK(out[0] == 0.0f);
    CHECK(source.starved() == 0);
}

TEST_CASE("prime is a no-op when the fifo already starts at the frame and nothing was consumed", "[timeline][clip]") {
    std::vector<int64_t> seeks;
    auto decoder = std::make_unique<tembra::FakeDecoder>(100'000, 2, kRate);
    decoder->seekLog = &seeks;
    tembra::ClipSource source(std::move(decoder), tembra::fakeInfo(100'000, 2, kRate), "fake");
    REQUIRE(source.prime(4000));
    REQUIRE(seeks.size() == 1);
    REQUIRE(source.prime(4000));
    REQUIRE(seeks.size() == 1);  // still primed there
    std::vector<float> out(64 * 2);
    REQUIRE(source.pop(4000, 64, out.data()));
    REQUIRE(source.prime(4000));
    REQUIRE(seeks.size() == 2);  // consumed: primed again
    REQUIRE(source.prime(9000));
    REQUIRE(seeks.size() == 3);
}

TEST_CASE("a replaced decoder takes over at its own rate", "[timeline][clip]") {
    auto source = openSource("tone-44k1-mono-16.wav", 44100);
    CHECK(source->outputRate() == 44100);
    CHECK(source->outputFrames() == 44100);
    int32_t error = TEMBRA_OK;
    tembra_clip_info info{};
    auto at48k = tembra::AudioDecoder::open(source->path().c_str(), 48000, &info, &error);
    REQUIRE(at48k);
    source->setDecoder(std::move(at48k));
    CHECK(source->outputRate() == 48000);
    CHECK(source->outputFrames() >= 47998);
    CHECK(source->outputFrames() <= 48002);
    const auto block = primeAndPop(*source, 0, 64);
    CHECK(std::abs(block[0]) < 0.01f);  // the sine starts at zero
}
