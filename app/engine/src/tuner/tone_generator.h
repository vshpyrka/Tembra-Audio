// The reference tone (research R4, R5): a sine with a double phase accumulator, 5 ms raised-cosine attack and release,
// −12 dBFS, one shot or loop; re-arming the same frequency changes only the remaining duration. The host writes
// commands into atomics, the callback renders and applies the ramps itself, so a stop never clicks (spec FR-021).
#ifndef TEMBRA_TUNER_TONE_GENERATOR_H
#define TEMBRA_TUNER_TONE_GENERATOR_H

#include <atomic>
#include <cstdint>

namespace tembra {

class ToneGenerator {
public:
    explicit ToneGenerator(uint32_t sampleRate);

    /** Starts or re-arms a tone; [durationMs] 0 loops until stop(). Host thread. */
    void start(double frequencyHz, uint32_t durationMs);

    /** Schedules the release from the current level. Host thread; idempotent. */
    void stop();

    /** True from start() until the release has finished rendering. */
    bool isActive() const;

    /** Renders [frames] interleaved stereo frames; silence once inactive. Audio thread, no allocation. */
    void render(float* interleavedStereo, uint32_t frames);

    static constexpr float kAmplitude = 0.25f;
    static constexpr double kRampSeconds = 0.005;

private:
    enum class Stage : uint32_t { Idle, Attack, Sustain, Release, Switching };

    void renderFrame(float& left, float& right);

    const uint32_t sampleRate_;
    const uint32_t rampFrames_;
    // Commands (host → callback).
    std::atomic<double> requestedHz_{0.0};
    std::atomic<int64_t> requestedFrames_{0};  // total frames from now for a one shot, -1 = loop
    std::atomic<uint32_t> command_{0};         // 1 = start/re-arm, 2 = stop
    std::atomic<bool> active_{false};
    // Callback state.
    double phase_ = 0.0;
    double increment_ = 0.0;
    double currentHz_ = 0.0;
    double pendingHz_ = 0.0;
    int64_t pendingFrames_ = 0;
    int64_t remainingFrames_ = 0;  // sustain frames left before the release begins; -1 = loop
    uint32_t rampPosition_ = 0;
    float level_ = 0.0f;
    float releaseFromLevel_ = 1.0f;
    Stage stage_ = Stage::Idle;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_TONE_GENERATOR_H
