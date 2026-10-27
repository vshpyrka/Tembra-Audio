// The take recorder thread (feature 010, research R6; contracts/engine-c-abi-v7.md "Threading"): drains the capture
// fifo into a crash-safe 24-bit WAV with a header refreshed every second, publishes the 256-frame peak buckets, and
// ends the take itself at the frame cap, below the free-space floor or on a write failure.
#include <catch2/catch_test_macros.hpp>

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <string>
#include <thread>
#include <vector>

#include "capture/capture_source.h"
#include "capture/peak_ring.h"
#include "capture/take_recorder.h"
#include "tembra_engine.h"
#include "timeline/audio_decoder.h"
#include "wav_fixtures.h"

namespace {

constexpr uint32_t kRate = 48000;

std::vector<float> numbered(uint32_t frames, uint32_t channels, int64_t firstFrame) {
    std::vector<float> block(static_cast<size_t>(frames) * channels);
    for (uint32_t i = 0; i < frames; ++i) {
        const float value = 0.2f + static_cast<float>((firstFrame + i) % 500000) * 1e-6f;
        for (uint32_t c = 0; c < channels; ++c) block[i * channels + c] = value + static_cast<float>(c) * 0.3f;
    }
    return block;
}

int64_t frameOf(float sample) { return std::llround((sample - 0.2f) * 1e6f); }

std::vector<float> decodeAll(const std::string& path, uint32_t channels) {
    int32_t error = TEMBRA_OK;
    tembra_clip_info info{};
    auto decoder = tembra::AudioDecoder::open(path.c_str(), 0, &info, &error);
    REQUIRE(decoder);
    std::vector<float> out(static_cast<size_t>(info.frames) * channels);
    REQUIRE(decoder->read(out.data(), static_cast<size_t>(info.frames)) == static_cast<size_t>(info.frames));
    return out;
}

tembra::TakeRecorder::Config configFor(const std::string& path, uint32_t channels) {
    tembra::TakeRecorder::Config config;
    config.path = path;
    config.channels = channels;
    config.rate = kRate;
    config.maxFrames = 30LL * 60 * kRate;
    config.minFreeBytes = 50ULL * 1024 * 1024;
    return config;
}

/** Waits up to [millis] for [done] to become true. */
template <class Done>
bool waitFor(int millis, Done done) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(millis);
    while (std::chrono::steady_clock::now() < deadline) {
        if (done()) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
    return done();
}

}  // namespace

TEST_CASE("the drained file equals the pushed frames with no gap and no repeat", "[capture][recorder]") {
    const std::string path = tembra::fixtures::tempPath("tembra-recorder-gapless.wav");
    tembra::CaptureSource source(kRate);
    source.prepare(2, 0);
    source.beginTake();
    tembra::PeakRing ring;
    tembra::TakeRecorder recorder(source, ring, configFor(path, 2));
    REQUIRE(recorder.start());
    int64_t fed = 0;
    for (int block = 0; block < 300; ++block) {  // 300 × 480 = 3 s, pushed faster than real time
        const auto data = numbered(480, 2, fed);
        source.capture(data.data(), 2, 480, 0);
        fed += 480;
        if (block % 20 == 0) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    recorder.stop();
    REQUIRE(recorder.framesWritten() == fed);
    REQUIRE(source.inputOverruns() == 0);
    const auto back = decodeAll(path, 2);
    REQUIRE(static_cast<int64_t>(back.size()) == fed * 2);
    for (int64_t i = 0; i < fed; ++i) {
        REQUIRE(frameOf(back[static_cast<size_t>(i) * 2]) == i);
        REQUIRE(std::abs(back[static_cast<size_t>(i) * 2 + 1] - (back[static_cast<size_t>(i) * 2] + 0.3f)) < 3.0f / 8388608.0f);
    }
    // One peak bucket per 256 frames reached the ring.
    REQUIRE(ring.count() == static_cast<uint64_t>(fed / 256));
    tembra::PeakBucket bucket{};
    uint64_t next = 0;
    REQUIRE(ring.read(0, &bucket, 1, &next) == 1);
    REQUIRE(bucket.max[0] == 25);  // 0.2 × 127 ≈ 25
    REQUIRE(bucket.max[1] == 64);  // 0.5 × 127 ≈ 64
    std::remove(path.c_str());
}

TEST_CASE("the header is refreshed every second so the file reads mid-take", "[capture][recorder]") {
    const std::string path = tembra::fixtures::tempPath("tembra-recorder-refresh.wav");
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    source.beginTake();
    tembra::PeakRing ring;
    tembra::TakeRecorder recorder(source, ring, configFor(path, 1));
    recorder.headerRefreshSeconds = 0.05;  // the test does not wait a second
    std::atomic<int> refreshes{0};
    std::atomic<int64_t> framesReadable{-1};
    recorder.onHeaderRefresh = [&] {
        tembra_audio_info info{};
        if (tembra::AudioDecoder::inspect(path.c_str(), info) == TEMBRA_OK) framesReadable.store(info.frames);
        refreshes.fetch_add(1);
    };
    REQUIRE(recorder.start());
    const auto data = numbered(4800, 1, 0);
    source.capture(data.data(), 1, 4800, 0);
    REQUIRE(waitFor(2000, [&] { return refreshes.load() >= 1 && framesReadable.load() == 4800; }));
    REQUIRE(framesReadable.load() == 4800);
    recorder.stop();
    REQUIRE(recorder.framesWritten() == 4800);
    std::remove(path.c_str());
}

TEST_CASE("the frame cap ends the take with MAX_LENGTH and keeps exactly the cap", "[capture][recorder]") {
    const std::string path = tembra::fixtures::tempPath("tembra-recorder-cap.wav");
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    source.beginTake();
    tembra::PeakRing ring;
    auto config = configFor(path, 1);
    config.maxFrames = 1000;
    tembra::TakeRecorder recorder(source, ring, config);
    REQUIRE(recorder.start());
    const auto data = numbered(2400, 1, 0);
    source.capture(data.data(), 1, 2400, 0);
    REQUIRE(waitFor(2000, [&] { return source.engineStopRequested(); }));
    REQUIRE(source.stopReason() == TEMBRA_TAKE_STOP_MAX_LENGTH);
    recorder.stop();
    REQUIRE(recorder.framesWritten() == 1000);
    tembra_audio_info info{};
    REQUIRE(tembra::AudioDecoder::inspect(path.c_str(), info) == TEMBRA_OK);
    REQUIRE(info.frames == 1000);
    std::remove(path.c_str());
}

TEST_CASE("free space below the floor ends the take with DISK_FULL", "[capture][recorder]") {
    const std::string path = tembra::fixtures::tempPath("tembra-recorder-disk.wav");
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    source.beginTake();
    tembra::PeakRing ring;
    auto config = configFor(path, 1);
    config.minFreeBytes = 1000;
    std::atomic<int64_t> free{10'000};
    tembra::TakeRecorder recorder(source, ring, config, [&](const char*) { return free.load(); });
    recorder.headerRefreshSeconds = 0.02;  // the free-space probe shares the refresh cadence
    REQUIRE(recorder.start());
    const auto data = numbered(480, 1, 0);
    source.capture(data.data(), 1, 480, 0);
    REQUIRE_FALSE(waitFor(100, [&] { return source.engineStopRequested(); }));
    free.store(500);
    REQUIRE(waitFor(2000, [&] { return source.engineStopRequested(); }));
    REQUIRE(source.stopReason() == TEMBRA_TAKE_STOP_DISK_FULL);
    recorder.stop();
    REQUIRE(recorder.framesWritten() == 480);  // what was written stays
    std::remove(path.c_str());
}

TEST_CASE("a write failure ends the take with WRITE_FAILED and the writer reports it", "[capture][recorder]") {
    const std::string path = tembra::fixtures::tempPath("tembra-recorder-write-failure.wav");
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    source.beginTake();
    tembra::PeakRing ring;
    tembra::TakeRecorder recorder(source, ring, configFor(path, 1));
    REQUIRE(recorder.start());
    const auto data = numbered(480, 1, 0);
    source.capture(data.data(), 1, 480, 0);
    REQUIRE(waitFor(2000, [&] { return recorder.framesWritten() == 480; }));
    recorder.writerForTest().loseFileForTest();
    source.capture(data.data(), 1, 480, 0);
    REQUIRE(waitFor(2000, [&] { return source.engineStopRequested(); }));
    REQUIRE(source.stopReason() == TEMBRA_TAKE_STOP_WRITE_FAILED);
    recorder.stop();
    REQUIRE(recorder.writeFailed());
    std::remove(path.c_str());
}

TEST_CASE("the backlog counter records the largest queue and the drain of four seconds completes within a second", "[capture][recorder]") {
    const std::string path = tembra::fixtures::tempPath("tembra-recorder-drain.wav");
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    source.beginTake();
    // Four seconds queued before the thread exists: the largest backlog the fifo can hold.
    int64_t fed = 0;
    while (source.fifo().freeSpace() >= 480) {
        const auto data = numbered(480, 1, fed);
        source.capture(data.data(), 1, 480, 0);
        fed += 480;
    }
    REQUIRE(fed >= 4 * kRate - 480);
    tembra::PeakRing ring;
    tembra::TakeRecorder recorder(source, ring, configFor(path, 1));
    REQUIRE(recorder.start());
    const auto started = std::chrono::steady_clock::now();
    recorder.stop();
    const auto elapsed = std::chrono::steady_clock::now() - started;
    REQUIRE(elapsed < std::chrono::seconds(1));
    REQUIRE(recorder.framesWritten() == fed);
    REQUIRE(recorder.backlogMax() >= static_cast<uint32_t>(fed - 480));
    REQUIRE(source.recordBacklogMax() >= static_cast<uint32_t>(fed - 480));
    std::remove(path.c_str());
}
