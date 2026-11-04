// The duplex input's reservoir (feature 010 follow-up; research R2). The Android output callback reads the input stream
// non-blocking right before it renders the same block. An input burst that lands a little after that callback left the
// read short; the shortfall was zero-padded into the take and counted as an input overrun (the Pixel 6 Pro's 30-minute
// soaks counted 5 or 6, the other devices none). The reservoir reads a cushion of input ahead of the block it hands on
// (two bursts on Android), so a burst that is late by up to the cushion is absorbed: the block comes from frames
// already held and the late burst refills the cushion on the next callback. Frames stay in order and none is dropped or
// repeated; it never reads more than one block plus the cushion ahead, so a backlog stays in the stream's own buffer as
// before. The added latency is the cushion, which the device reports as part of the input latency the take alignment
// uses. Audio thread only after configure(): no allocation, no lock, no I/O, no tracing.
#ifndef TEMBRA_CAPTURE_INPUT_RESERVOIR_H
#define TEMBRA_CAPTURE_INPUT_RESERVOIR_H

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

namespace tembra {

class InputReservoir {
public:
    /** Room for [capacityFrames] frames of up to [maxChannels]; the one allocation, on a control thread. */
    InputReservoir(uint32_t capacityFrames, uint32_t maxChannels)
        : capacity_(capacityFrames), maxChannels_(maxChannels == 0 ? 1 : maxChannels),
          buffer_(static_cast<size_t>(capacityFrames) * (maxChannels == 0 ? 1 : maxChannels), 0.0f) {}

    InputReservoir(const InputReservoir&) = delete;
    InputReservoir& operator=(const InputReservoir&) = delete;

    /** Control thread, while the callback cannot see the reservoir: empties it and primes again on the next pull. */
    void configure(uint32_t channels, uint32_t cushionFrames) {
        channels_ = std::clamp<uint32_t>(channels, 1, maxChannels_);
        cushion_ = std::min(cushionFrames, capacity_ / 2);
        held_ = 0;
        primed_ = false;
    }

    /**
     * Fills [out] with [frames] interleaved frames, topping the reservoir up through [read] (a non-blocking read that
     * writes at most the given number of frames to the given pointer and returns how many it wrote). Until one block
     * plus the cushion has arrived the block is silence and nothing counts (the stream is starting); after that the
     * return value is the number of frames that were missing and zero-padded, which the caller counts as an overrun.
     */
    template <typename Read>
    uint32_t pull(float* out, uint32_t frames, Read&& read) {
        const uint32_t block = std::min(frames, capacity_ - cushion_);
        const uint32_t want = block + cushion_;
        if (held_ < want) {
            const uint32_t got = read(buffer_.data() + static_cast<size_t>(held_) * channels_, want - held_);
            held_ += std::min(got, want - held_);
        }
        const size_t sampleCount = static_cast<size_t>(frames) * channels_;
        if (!primed_) {
            if (held_ < want) {
                std::fill(out, out + sampleCount, 0.0f);
                return 0;
            }
            primed_ = true;
        }
        const uint32_t delivered = std::min(block, held_);
        const size_t deliveredSamples = static_cast<size_t>(delivered) * channels_;
        std::memcpy(out, buffer_.data(), deliveredSamples * sizeof(float));
        std::fill(out + deliveredSamples, out + sampleCount, 0.0f);
        held_ -= delivered;
        if (held_ > 0) std::memmove(buffer_.data(), buffer_.data() + deliveredSamples, static_cast<size_t>(held_) * channels_ * sizeof(float));
        return frames - delivered;
    }

    bool primed() const { return primed_; }
    uint32_t heldFrames() const { return held_; }
    uint32_t cushionFrames() const { return cushion_; }

private:
    const uint32_t capacity_;
    const uint32_t maxChannels_;
    std::vector<float> buffer_;
    uint32_t channels_ = 1;
    uint32_t cushion_ = 0;
    uint32_t held_ = 0;
    bool primed_ = false;
};

}  // namespace tembra

#endif  // TEMBRA_CAPTURE_INPUT_RESERVOIR_H
