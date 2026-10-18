// Test-only Decoder over an in-memory signal (feature 009): a deterministic sample for every frame and channel, a log
// of every seek, and an optional per-read stall so the disk streamer's starvation path can be provoked.
#ifndef TEMBRA_TESTS_FAKE_DECODER_H
#define TEMBRA_TESTS_FAKE_DECODER_H

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <thread>
#include <vector>

#include "timeline/audio_decoder.h"

namespace tembra {

class FakeDecoder : public Decoder {
public:
    using Sample = std::function<float(int64_t frame, uint32_t channel)>;

    /** A ramp whose value identifies the frame: frame / 1e6 + channel; exact in float up to a few million frames. */
    static Sample ramp() {
        return [](int64_t frame, uint32_t channel) { return static_cast<float>(frame) * 1e-6f + static_cast<float>(channel); };
    }

    FakeDecoder(int64_t frames, uint32_t channels, uint32_t rate, Sample sample = ramp())
        : frames_(frames), channels_(channels), rate_(rate), sample_(std::move(sample)) {}

    uint32_t channels() const override { return channels_; }
    uint32_t outputRate() const override { return rate_; }
    int64_t outputFrames() const override { return frames_; }

    bool seek(int64_t frame) override {
        if (seekLog != nullptr) seekLog->push_back(frame);
        if (frame < 0 || frame > frames_) return false;
        position_ = frame;
        return true;
    }

    size_t read(float* interleaved, size_t frames) override {
        if (readStallMicros > 0) std::this_thread::sleep_for(std::chrono::microseconds(readStallMicros));
        const size_t n = static_cast<size_t>(std::max<int64_t>(0, std::min<int64_t>(static_cast<int64_t>(frames), frames_ - position_)));
        for (size_t i = 0; i < n; ++i) {
            for (uint32_t c = 0; c < channels_; ++c) interleaved[i * channels_ + c] = sample_(position_ + static_cast<int64_t>(i), c);
        }
        position_ += static_cast<int64_t>(n);
        ++reads;
        return n;
    }

    int64_t position() const { return position_; }

    std::vector<int64_t>* seekLog = nullptr;  // every seek(frame) is appended here when set
    int64_t readStallMicros = 0;              // sleeps this long per read (simulates a slow disk)
    uint64_t reads = 0;

private:
    int64_t frames_;
    uint32_t channels_;
    uint32_t rate_;
    Sample sample_;
    int64_t position_ = 0;
};

inline tembra_clip_info fakeInfo(int64_t frames, uint32_t channels, uint32_t rate) {
    tembra_clip_info info{};
    info.channels = channels;
    info.sample_rate = rate;
    info.frames = frames;
    info.format = TEMBRA_FORMAT_WAV;
    return info;
}

}  // namespace tembra

#endif  // TEMBRA_TESTS_FAKE_DECODER_H
