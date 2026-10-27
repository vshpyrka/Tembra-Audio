// The live peak ring (feature 010, research R7): the recorder thread publishes one int8 min/max bucket per 256
// frames, the host polls at 10 Hz; nothing is lost while the reader keeps up, a stalled reader learns how many it
// missed through nextIndex, and the bucket values equal OverviewBuilder's base level.
#include <catch2/catch_test_macros.hpp>

#include <atomic>
#include <cstdint>
#include <thread>
#include <vector>

#include "capture/peak_ring.h"

namespace {
/** Where the reader got to, so the writer of the two-thread test never laps it (the ring is not a flow control). */
std::atomic<uint64_t> readerIndexHint{0};
}  // namespace

TEST_CASE("the ring holds 1024 buckets and a bucket is four int8 values", "[capture][peaks]") {
    REQUIRE(tembra::PeakRing::kCapacity == 1024);
    REQUIRE(sizeof(tembra::PeakBucket) == 4);
    tembra::PeakRing ring;
    REQUIRE(ring.count() == 0);
    tembra::PeakBucket out[4];
    uint64_t next = 99;
    REQUIRE(ring.read(0, out, 4, &next) == 0);
    REQUIRE(next == 0);
}

TEST_CASE("a writer and a reader on two threads lose nothing over a million buckets", "[capture][peaks]") {
    tembra::PeakRing ring;
    constexpr uint64_t kTotal = 1'000'000;
    std::atomic<bool> done{false};
    std::thread writer([&] {
        for (uint64_t i = 0; i < kTotal; ++i) {
            tembra::PeakBucket bucket{};
            bucket.min[0] = static_cast<int8_t>(-(static_cast<int>(i % 127)));
            bucket.max[0] = static_cast<int8_t>(i % 127);
            bucket.min[1] = static_cast<int8_t>(-(static_cast<int>((i / 127) % 127)));
            bucket.max[1] = static_cast<int8_t>((i / 127) % 127);
            // The reader lags at most a few hundred buckets; a writer that would lap it waits.
            while (ring.count() - readerIndexHint.load(std::memory_order_acquire) > 512) std::this_thread::yield();
            ring.push(bucket);
        }
        done.store(true, std::memory_order_release);
    });
    std::vector<tembra::PeakBucket> got(64);
    uint64_t index = 0;
    uint64_t mismatches = 0;
    while (index < kTotal) {
        uint64_t next = 0;
        const uint32_t n = ring.read(index, got.data(), 64, &next);
        if (n == 0) {
            if (done.load(std::memory_order_acquire) && ring.count() == index) break;
            std::this_thread::yield();
            continue;
        }
        REQUIRE(next - n == index);  // nothing skipped
        for (uint32_t k = 0; k < n; ++k) {
            const uint64_t i = index + k;
            if (got[k].max[0] != static_cast<int8_t>(i % 127) || got[k].max[1] != static_cast<int8_t>((i / 127) % 127)) ++mismatches;
        }
        index = next;
        readerIndexHint.store(index, std::memory_order_release);
    }
    writer.join();
    REQUIRE(index == kTotal);
    REQUIRE(mismatches == 0);
}

TEST_CASE("a stalled reader sees the overrun through nextIndex and the oldest kept bucket", "[capture][peaks]") {
    tembra::PeakRing ring;
    for (uint64_t i = 0; i < 3000; ++i) {
        tembra::PeakBucket bucket{};
        bucket.max[0] = static_cast<int8_t>(i % 100);
        ring.push(bucket);
    }
    tembra::PeakBucket out[16];
    uint64_t next = 0;
    const uint32_t n = ring.read(100, out, 16, &next);  // bucket 100 was overwritten long ago
    REQUIRE(n == 16);
    const uint64_t first = next - n;
    REQUIRE(first > 100);                 // the copy started at the oldest kept bucket, not at 100
    REQUIRE(first >= 3000 - 1024);        // the ring keeps the last 1024
    REQUIRE(first < 3000 - 1000);
    REQUIRE(out[0].max[0] == static_cast<int8_t>(first % 100));
    // The current count onwards reads nothing new.
    REQUIRE(ring.read(3000, out, 16, &next) == 0);
    REQUIRE(next == 3000);
    // Asking beyond the count is clamped to the count.
    REQUIRE(ring.read(5000, out, 16, &next) == 0);
    REQUIRE(next == 3000);
}

TEST_CASE("the accumulator makes one bucket per 256 frames with OverviewBuilder's quantisation", "[capture][peaks]") {
    REQUIRE(tembra::PeakAccumulator::kBucketFrames == 256);
    REQUIRE(tembra::PeakAccumulator::quantise(1.0f) == 127);
    REQUIRE(tembra::PeakAccumulator::quantise(-1.0f) == -127);
    REQUIRE(tembra::PeakAccumulator::quantise(1.7f) == 127);
    REQUIRE(tembra::PeakAccumulator::quantise(0.5f) == 64);  // lround(63.5)
    REQUIRE(tembra::PeakAccumulator::quantise(-0.004f) == -1);
    tembra::PeakAccumulator accumulator;
    accumulator.reset(2);
    std::vector<tembra::PeakBucket> buckets;
    // 600 stereo frames: the first 256 hold ±0.5 on the left and 0.25 on the right, the next 256 a single spike, the rest stays open.
    std::vector<float> block(600 * 2, 0.0f);
    for (size_t i = 0; i < 256; ++i) {
        block[i * 2] = (i % 2 == 0) ? 0.5f : -0.5f;
        block[i * 2 + 1] = 0.25f;
    }
    block[300 * 2] = -0.9f;
    block[300 * 2 + 1] = 0.1f;
    accumulator.add(block.data(), 600, [&](const tembra::PeakBucket& bucket) { buckets.push_back(bucket); });
    REQUIRE(buckets.size() == 2);
    REQUIRE(buckets[0].min[0] == -64);
    REQUIRE(buckets[0].max[0] == 64);
    REQUIRE(buckets[0].min[1] == 32);
    REQUIRE(buckets[0].max[1] == 32);
    REQUIRE(buckets[1].min[0] == -114);
    REQUIRE(buckets[1].max[0] == 0);
    REQUIRE(buckets[1].min[1] == 0);
    REQUIRE(buckets[1].max[1] == 13);
    // The open bucket completes when the next frames arrive.
    accumulator.add(block.data(), 168, [&](const tembra::PeakBucket& bucket) { buckets.push_back(bucket); });
    REQUIRE(buckets.size() == 3);
    REQUIRE(buckets[2].max[0] == 64);
}
