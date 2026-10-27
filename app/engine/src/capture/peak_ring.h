// The live peak ring (feature 010, research R7): the recorder thread accumulates one int8 min/max bucket per 256
// frames per channel (the .peaks base level of OverviewBuilder) and publishes it into a ring of 1024 buckets with a
// monotonic count; the host polls at 10 Hz and detects an overrun through the next index it gets back. Header-only,
// allocation-free after construction, lock-free: the writer stores then release-increments, the reader copies then
// re-checks the count (the seqlock discipline). Included by the capture source (real-time), so no tracing here.
#ifndef TEMBRA_CAPTURE_PEAK_RING_H
#define TEMBRA_CAPTURE_PEAK_RING_H

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace tembra {

/** Mirrors tembra_peak_bucket: min and max per channel; a mono bucket leaves channel 1 at zero. */
struct PeakBucket {
    int8_t min[2];
    int8_t max[2];
};

/** Accumulates interleaved frames into 256-frame buckets with OverviewBuilder's quantisation (round(x × 127), ±127). */
class PeakAccumulator {
public:
    static constexpr uint32_t kBucketFrames = 256;

    static int8_t quantise(float x) { return static_cast<int8_t>(std::clamp<long>(std::lround(x * 127.0f), -127, 127)); }

    void reset(uint32_t channels) {
        channels_ = channels > 2 ? 2 : (channels == 0 ? 1 : channels);
        filled_ = 0;
        clear();
    }

    /** Adds [frames] interleaved frames of [channels_]; calls [push](bucket) for each bucket completed. */
    template <class Push>
    void add(const float* interleaved, size_t frames, Push push) {
        for (size_t i = 0; i < frames; ++i) {
            for (uint32_t c = 0; c < channels_; ++c) {
                const int8_t q = quantise(interleaved[i * channels_ + c]);
                if (q < lo_[c]) lo_[c] = q;
                if (q > hi_[c]) hi_[c] = q;
            }
            if (++filled_ == kBucketFrames) {
                PeakBucket bucket{};
                for (uint32_t c = 0; c < channels_; ++c) {
                    bucket.min[c] = lo_[c];
                    bucket.max[c] = hi_[c];
                }
                push(bucket);
                filled_ = 0;
                clear();
            }
        }
    }

    uint32_t channels() const { return channels_; }

private:
    void clear() {
        lo_[0] = lo_[1] = 127;
        hi_[0] = hi_[1] = -127;
    }

    uint32_t channels_ = 1;
    uint32_t filled_ = 0;
    int8_t lo_[2] = {127, 127};
    int8_t hi_[2] = {-127, -127};
};

class PeakRing {
public:
    static constexpr uint32_t kCapacity = 1024;

    /** Writer, while no reader runs (a new take): forgets every bucket. */
    void reset() { count_.store(0, std::memory_order_release); }

    /** Writer: stores bucket number count() and publishes it. */
    void push(const PeakBucket& bucket) {
        const uint64_t count = count_.load(std::memory_order_relaxed);
        buckets_[static_cast<size_t>(count % kCapacity)] = bucket;
        std::atomic_thread_fence(std::memory_order_release);
        count_.store(count + 1, std::memory_order_release);
    }

    uint64_t count() const { return count_.load(std::memory_order_acquire); }

    /**
     * Reader: copies up to [maxCount] buckets from [fromIndex] into [out]; returns how many and sets [*nextIndex] to the
     * index after the last copied. When [fromIndex] was overwritten the copy starts at the oldest safely kept bucket,
     * so `*nextIndex - returned > fromIndex` tells the caller what it missed.
     */
    uint32_t read(uint64_t fromIndex, PeakBucket* out, uint32_t maxCount, uint64_t* nextIndex) const {
        for (;;) {
            const uint64_t count = count_.load(std::memory_order_acquire);
            // The slot of bucket b is rewritten when bucket b + kCapacity is stored, which begins once count == b + kCapacity;
            // the last kCapacity - 1 buckets are therefore safe to copy.
            const uint64_t oldest = count >= kCapacity ? count - (kCapacity - 1) : 0;
            uint64_t start = fromIndex < oldest ? oldest : fromIndex;
            if (start > count) start = count;
            const uint32_t n = static_cast<uint32_t>(std::min<uint64_t>(maxCount, count - start));
            if (out != nullptr) {
                for (uint32_t i = 0; i < n; ++i) out[i] = buckets_[static_cast<size_t>((start + i) % kCapacity)];
            }
            std::atomic_thread_fence(std::memory_order_acquire);
            const uint64_t after = count_.load(std::memory_order_acquire);
            if (after < start + kCapacity) {  // nothing copied was rewritten meanwhile
                if (nextIndex != nullptr) *nextIndex = start + n;
                return out == nullptr ? 0u : n;
            }
            fromIndex = after - (kCapacity - 1);  // the writer lapped the copy: start again from what is kept now
        }
    }

private:
    std::array<PeakBucket, kCapacity> buckets_{};
    std::atomic<uint64_t> count_{0};
};

}  // namespace tembra

#endif  // TEMBRA_CAPTURE_PEAK_RING_H
