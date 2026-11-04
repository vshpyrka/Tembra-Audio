// The real-time capture source (feature 010, research R5, R8; contracts/engine-c-abi-v7.md "Renderer rules" 3):
// measures every input block for the meter, latches clips, discards the alignment frames at a take's start, pushes
// the kept frames into the ordered fifo and counts overruns, and publishes the CaptureRecord the host reads.
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <cstdint>
#include <vector>

#include "capture/capture_record.h"
#include "capture/capture_source.h"
#include "tembra_engine.h"

namespace {

constexpr uint32_t kRate = 48000;

std::vector<float> constantBlock(uint32_t frames, uint32_t channels, float left, float right) {
    std::vector<float> block(static_cast<size_t>(frames) * channels);
    for (uint32_t i = 0; i < frames; ++i) {
        block[i * channels] = left;
        if (channels > 1) block[i * channels + 1] = right;
    }
    return block;
}

/** The input frame index encoded in the sample: value = base + (frame mod 500000) / 1e6. */
std::vector<float> numbered(uint32_t frames, uint32_t channels, int64_t firstFrame, float base = 0.2f) {
    std::vector<float> block(static_cast<size_t>(frames) * channels);
    for (uint32_t i = 0; i < frames; ++i) {
        const float value = base + static_cast<float>((firstFrame + i) % 500000) * 1e-6f;
        for (uint32_t c = 0; c < channels; ++c) block[i * channels + c] = value;
    }
    return block;
}

int64_t frameOf(float sample, float base = 0.2f) { return std::llround((sample - base) * 1e6f); }

}  // namespace

TEST_CASE("the block peak is measured per channel and held for 64 ms", "[capture][source]") {
    tembra::CaptureSource source(kRate);
    source.prepare(2, 0);
    const auto loud = constantBlock(240, 2, 0.5f, -0.25f);
    source.measure(loud.data(), 2, 240, 0);
    source.publish(true, false, false, false, 0, 1);
    tembra::CaptureRecord record = source.record();
    REQUIRE(record.peak[0] == 0.5f);
    REQUIRE(record.peak[1] == 0.25f);
    REQUIRE(record.channels == 2);
    REQUIRE(record.rate == kRate);
    REQUIRE(record.armed == 1);
    // 60 ms of silence (12 blocks of 5 ms) still shows the transient; 70 ms no longer does.
    const auto silent = constantBlock(240, 2, 0.0f, 0.0f);
    for (int i = 0; i < 12; ++i) source.measure(silent.data(), 2, 240, 0);
    source.publish(true, false, false, false, 0, 13);
    REQUIRE(source.record().peak[0] == 0.5f);
    for (int i = 0; i < 2; ++i) source.measure(silent.data(), 2, 240, 0);
    source.publish(true, false, false, false, 0, 15);
    REQUIRE(source.record().peak[0] == 0.0f);
    REQUIRE(source.record().peak[1] == 0.0f);
}

TEST_CASE("the clip latch sets at full scale and clears only on a reset", "[capture][source]") {
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    const auto nearly = constantBlock(64, 1, 0.9998f, 0.0f);
    source.measure(nearly.data(), 1, 64, 0);
    source.publish(true, false, false, false, 0, 1);
    REQUIRE(source.record().clipped == 0);
    const auto full = constantBlock(64, 1, -0.9999f, 0.0f);
    source.measure(full.data(), 1, 64, 0);
    source.publish(true, false, false, false, 0, 2);
    REQUIRE(source.record().clipped == 1);
    const auto quiet = constantBlock(64, 1, 0.1f, 0.0f);
    for (int i = 0; i < 100; ++i) source.measure(quiet.data(), 1, 64, 0);
    source.publish(true, false, false, false, 0, 102);
    REQUIRE(source.record().clipped == 1);  // sticky
    source.requestClipReset();
    source.measure(quiet.data(), 1, 64, 0);
    source.publish(true, false, false, false, 0, 103);
    REQUIRE(source.record().clipped == 0);
}

TEST_CASE("pushes begin after the alignment frames are discarded and keep the order", "[capture][source]") {
    tembra::CaptureSource source(kRate);
    source.prepare(1, 100);
    REQUIRE(source.alignmentFrames() == 100);
    source.beginTake();
    // The take starts 30 frames into the first block: frames 0..29 are measured only, 30..129 discarded, 130.. kept.
    const auto first = numbered(256, 1, 0);
    source.measure(first.data(), 1, 256, 0);
    source.capture(first.data(), 1, 256, 30);
    REQUIRE(source.framesCaptured() == 256 - 30 - 100);
    const auto second = numbered(256, 1, 256);
    source.measure(second.data(), 1, 256, 0);
    source.capture(second.data(), 1, 256, 0);
    REQUIRE(source.framesCaptured() == 2 * 256 - 130);
    std::vector<float> drained(1024);
    const size_t n = source.fifo().read(drained.data(), drained.size());
    REQUIRE(n == 2 * 256 - 130);
    for (size_t i = 0; i < n; ++i) REQUIRE(frameOf(drained[i]) == 130 + static_cast<int64_t>(i));
    REQUIRE(source.inputOverruns() == 0);
}

TEST_CASE("a full fifo counts one overrun per short write and drops the tail, never reordering", "[capture][source]") {
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    source.beginTake();
    const size_t capacity = source.fifo().capacity();
    REQUIRE(capacity >= static_cast<size_t>(4 * kRate));  // four seconds of mono
    const uint32_t block = 1024;
    int64_t fed = 0;
    while (static_cast<size_t>(fed) + block <= capacity) {
        const auto data = numbered(block, 1, fed);
        source.capture(data.data(), 1, block, 0);
        fed += block;
    }
    REQUIRE(source.inputOverruns() == 0);
    const auto overflow = numbered(block, 1, fed);
    source.capture(overflow.data(), 1, block, 0);  // only part of it fits
    REQUIRE(source.inputOverruns() == 1);
    source.capture(overflow.data(), 1, block, 0);  // nothing fits
    REQUIRE(source.inputOverruns() == 2);
    std::vector<float> drained(capacity);
    const size_t n = source.fifo().read(drained.data(), drained.size());
    REQUIRE(n == capacity);
    for (size_t i = 0; i < n; ++i) REQUIRE(frameOf(drained[i]) == static_cast<int64_t>(i % 500000));
    REQUIRE(source.framesCaptured() == static_cast<int64_t>(capacity));  // the dropped tails were never kept
}

TEST_CASE("a short device read counts as an overrun after the warm-up bursts and not before", "[capture][source]") {
    tembra::CaptureSource source(kRate);
    source.prepare(1, 0);
    const auto block = constantBlock(240, 1, 0.1f, 0.0f);
    source.measure(block.data(), 1, 240, 240);  // the first two bursts after arming may be short
    source.measure(block.data(), 1, 240, 100);
    REQUIRE(source.inputOverruns() == 0);
    source.measure(block.data(), 1, 240, 0);
    REQUIRE(source.inputOverruns() == 0);
    source.measure(block.data(), 1, 240, 17);
    REQUIRE(source.inputOverruns() == 1);
    source.measure(block.data(), 1, 240, 240);
    REQUIRE(source.inputOverruns() == 2);
}

TEST_CASE("the published record carries every field of the capture snapshot", "[capture][source]") {
    tembra::CaptureSource source(kRate);
    source.prepare(2, 480);
    source.beginTake();
    const auto block = numbered(480, 2, 0);
    source.measure(block.data(), 2, 480, 0);
    source.capture(block.data(), 2, 480, 0);  // the whole block is discarded (480 alignment frames)
    const auto more = numbered(480, 2, 480);
    source.measure(more.data(), 2, 480, 0);
    source.capture(more.data(), 2, 480, 0);
    source.publish(true, true, false, true, 12345, 7);
    const tembra::CaptureRecord record = source.record();
    REQUIRE(record.armed == 1);
    REQUIRE(record.recording == 1);
    REQUIRE(record.preRoll == 0);
    REQUIRE(record.monitoring == 1);
    REQUIRE(record.channels == 2);
    REQUIRE(record.rate == kRate);
    REQUIRE(record.peak[0] > 0.2f);
    REQUIRE(record.clipped == 0);
    REQUIRE(record.framesCaptured == 480);
    REQUIRE(record.takeStartFrames == 12345);
    REQUIRE(record.alignmentFrames == 480);
    REQUIRE(record.stopReason == TEMBRA_TAKE_STOP_USER);
    REQUIRE(record.inputOverruns == 0);
    REQUIRE(record.backlogFrames == 480);  // waiting in the fifo
    REQUIRE(record.blocks == 7);
    // The recorder thread may end a take itself; the reason reaches the record.
    source.endTake(TEMBRA_TAKE_STOP_DISK_FULL);
    REQUIRE(source.engineStopRequested());
    source.publish(true, false, false, true, 12345, 8);
    REQUIRE(source.record().stopReason == TEMBRA_TAKE_STOP_DISK_FULL);
    REQUIRE(source.record().recording == 0);
    // A new take starts clean.
    source.beginTake();
    REQUIRE_FALSE(source.engineStopRequested());
    REQUIRE(source.framesCaptured() == 0);
    source.publish(true, false, false, true, 0, 9);
    REQUIRE(source.record().stopReason == TEMBRA_TAKE_STOP_USER);
}
