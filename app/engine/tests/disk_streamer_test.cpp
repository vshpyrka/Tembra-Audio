// The disk streamer (feature 009, research R5; contracts/engine-c-abi-v6.md "Threading"): the one reader thread that
// primes and refills every clip source's fifo while the callback only pops; driven here with FakeDecoder sources.
#include <catch2/catch_test_macros.hpp>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <thread>
#include <vector>

#include "fake_decoder.h"
#include "timeline/clip_source.h"
#include "timeline/disk_streamer.h"

namespace {

constexpr uint32_t kRate = 48000;

struct Sources {
    std::vector<std::unique_ptr<tembra::ClipSource>> owned;
    std::vector<tembra::ClipSource*> views;
    std::vector<int64_t> seeks;

    tembra::ClipSource* add(int64_t frames, uint32_t channels = 2, int64_t stallMicros = 0) {
        auto decoder = std::make_unique<tembra::FakeDecoder>(frames, channels, kRate);
        decoder->seekLog = &seeks;
        decoder->readStallMicros = stallMicros;
        owned.push_back(std::make_unique<tembra::ClipSource>(std::move(decoder), tembra::fakeInfo(frames, channels, kRate), "fake"));
        views.push_back(owned.back().get());
        return owned.back().get();
    }
};

template <class Accept>
bool waitFor(int millis, Accept accept) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(millis);
    while (std::chrono::steady_clock::now() < deadline) {
        if (accept()) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    return accept();
}

}  // namespace

TEST_CASE("prime fills each source to capacity or EOF", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* longer = sources.add(1'000'000);
    tembra::ClipSource* shorter = sources.add(5000, 1);
    tembra::DiskStreamer streamer(kRate);
    streamer.setSources(sources.views);
    streamer.prime(0, {{longer, 100, 100}, {shorter, 2000, 2000}});
    CHECK(longer->available() == tembra::ClipSource::kFifoFrames);
    CHECK(longer->primedFrom() == 100);
    CHECK_FALSE(longer->eof());
    CHECK(shorter->available() == 3000);
    CHECK(shorter->primedFrom() == 2000);
    CHECK(shorter->eof());
    CHECK(streamer.primedPosition() == 0);
}

TEST_CASE("the thread refills while the consumer drains and stop joins", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* source = sources.add(2'000'000);
    tembra::DiskStreamer streamer(kRate);
    streamer.setSources(sources.views);
    streamer.prime(0, {{source, 0, 0}});
    streamer.start();
    REQUIRE(streamer.running());
    std::vector<float> block(1024 * 2);
    int64_t frame = 0;
    bool everStarved = false;
    // 200 blocks of 1024 frames: far more than one fifo, so the thread must keep up.
    for (int i = 0; i < 200; ++i) {
        if (!waitFor(500, [&] { return source->available() >= 1024; })) break;
        if (!source->pop(frame, 1024, block.data())) everStarved = true;
        REQUIRE(block[0] == tembra::FakeDecoder::ramp()(frame, 0));
        REQUIRE(block[2 * 1023 + 1] == tembra::FakeDecoder::ramp()(frame + 1023, 1));
        frame += 1024;
    }
    CHECK(frame == 200 * 1024);
    CHECK_FALSE(everStarved);
    CHECK(source->starved() == 0);
    streamer.stop();
    REQUIRE_FALSE(streamer.running());
    streamer.stop();  // idempotent
}

TEST_CASE("a source whose fifo runs dry increments starved", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* source = sources.add(2'000'000, 2, 20'000);  // each read stalls 20 ms: the disk is slow
    tembra::DiskStreamer streamer(kRate);
    streamer.setSources(sources.views);
    streamer.prime(0, {{source, 0, 0}});
    std::vector<float> block(4096 * 2);
    int64_t frame = 0;
    // Drain the whole prime at once, then keep asking: the stalled thread cannot keep up.
    streamer.start();
    for (int i = 0; i < 8; ++i) {
        source->pop(frame, 4096, block.data());
        frame += 4096;
    }
    CHECK(source->starved() >= 1);
    streamer.stop();
}

TEST_CASE("a seek re-primes from the new frame and the callback's requests are served", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* source = sources.add(2'000'000);
    tembra::DiskStreamer streamer(kRate);
    streamer.setSources(sources.views);
    streamer.prime(0, {{source, 0, 0}});
    std::vector<float> block(256 * 2);
    REQUIRE(source->pop(0, 256, block.data()));

    // The host seeks while stopped: a synchronous prime from the new frame.
    streamer.prime(500'000, {{source, 500'000, 500'000}});
    CHECK(source->primedFrom() == 500'000);
    REQUIRE(source->pop(500'000, 256, block.data()));
    CHECK(block[0] == tembra::FakeDecoder::ramp()(500'000, 0));

    // The callback seeks while playing: the pop parks and requests, the thread primes, the pop resumes.
    streamer.start();
    REQUIRE_FALSE(source->pop(900'000, 256, block.data()));
    REQUIRE(waitFor(500, [&] { return source->pendingRequest() == -1 && source->primedFrom() == 900'000; }));
    REQUIRE(source->pop(900'000, 256, block.data()));
    CHECK(block[0] == tembra::FakeDecoder::ramp()(900'000, 0));
    streamer.stop();
}

TEST_CASE("a requested re-prime runs on the thread once the readers are inactive", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* source = sources.add(2'000'000);
    std::atomic<bool> readersActive{true};
    tembra::DiskStreamer streamer(kRate, &readersActive);
    streamer.setSources(sources.views);
    streamer.prime(0, {{source, 0, 0}});
    streamer.start();
    streamer.requestReprime(700'000, {{source, 700'000, 700'000}});
    std::this_thread::sleep_for(std::chrono::milliseconds(40));
    CHECK(source->primedFrom() == 0);  // the callback may still be popping: not yet
    readersActive.store(false);
    REQUIRE(waitFor(500, [&] { return source->primedFrom() == 700'000; }));
    CHECK(streamer.primedPosition() == 700'000);
    streamer.invalidate();
    CHECK(streamer.primedPosition() == -1);
    streamer.stop();
}

TEST_CASE("sources within two seconds of the position are primed first", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* far = sources.add(1'000'000);
    tembra::ClipSource* near = sources.add(1'000'000);
    tembra::ClipSource* now = sources.add(1'000'000);
    tembra::ClipSource* later = sources.add(1'000'000);
    tembra::DiskStreamer streamer(kRate);
    streamer.setSources(sources.views);
    const int64_t position = 48000;
    // Needed at: far in 5 s, near in 1 s, now at the position, later in 3 s (window = 2 s).
    streamer.prime(position, {{far, 11, position + 5 * 48000}, {near, 22, position + 48000}, {now, 33, position}, {later, 44, position + 3 * 48000}});
    REQUIRE(sources.seeks.size() == 4);
    CHECK(sources.seeks[0] == 33);  // now
    CHECK(sources.seeks[1] == 22);  // near
    CHECK(sources.seeks[2] == 44);  // later (outside the window, but sooner than far)
    CHECK(sources.seeks[3] == 11);  // far
    // A source nothing needs (frame -1) is left alone; one already primed at its frame is not decoded again.
    sources.seeks.clear();
    streamer.prime(position, {{far, -1, 0}, {now, 33, position}, {near, 35, position}});
    REQUIRE(sources.seeks.size() == 1);
    CHECK(sources.seeks[0] == 35);
}

TEST_CASE("setSources drops a removed source from the pending work", "[timeline][streamer]") {
    Sources sources;
    tembra::ClipSource* keep = sources.add(1'000'000);
    tembra::ClipSource* drop = sources.add(1'000'000);
    std::atomic<bool> readersActive{true};
    tembra::DiskStreamer streamer(kRate, &readersActive);
    streamer.setSources(sources.views);
    streamer.requestReprime(10'000, {{keep, 10'000, 10'000}, {drop, 10'000, 10'000}});
    streamer.setSources({keep});
    readersActive.store(false);
    REQUIRE(streamer.serviceOnce());
    CHECK(keep->primedFrom() == 10'000);
    CHECK(drop->primedFrom() == -1);
}
