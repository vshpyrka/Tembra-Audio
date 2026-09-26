// Plays a decoded recording (research R4, R5): linear-interpolating resampler to the stream rate, a 10 ms equal-power
// crossfade at the loop point, once or looped, and a 5 ms release on stop. Commands are atomics; render allocates nothing.
#ifndef TEMBRA_TUNER_SAMPLE_PLAYER_H
#define TEMBRA_TUNER_SAMPLE_PLAYER_H

#include <atomic>
#include <cstdint>

#include "tuner/wav_decoder.h"

namespace tembra {

class SamplePlayer {
public:
    explicit SamplePlayer(uint32_t sampleRate);

    /** Starts [sample] (which must outlive playback); [loop] repeats it seamlessly. Host thread. */
    void play(const DecodedSample& sample, bool loop);

    /** Schedules a 5 ms release. Host thread; idempotent. */
    void stop();

    bool isActive() const;

    /** Renders [frames] interleaved stereo frames. Audio thread, no allocation. */
    void render(float* interleavedStereo, uint32_t frames);

    static constexpr double kCrossfadeSeconds = 0.010;
    static constexpr double kReleaseSeconds = 0.005;

private:
    float sampleAt(double position) const;

    const uint32_t sampleRate_;
    std::atomic<const DecodedSample*> requested_{nullptr};
    std::atomic<bool> requestedLoop_{false};
    std::atomic<uint32_t> command_{0};
    std::atomic<bool> active_{false};
    const DecodedSample* current_ = nullptr;
    bool loop_ = false;
    double position_ = 0.0;
    double step_ = 1.0;
    double crossfadeFrames_ = 0.0;
    uint32_t releaseFrames_ = 0;
    uint32_t releasePosition_ = 0;
    bool releasing_ = false;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_SAMPLE_PLAYER_H
