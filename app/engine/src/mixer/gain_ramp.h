// A linear gain ramp over 10 ms (feature 009, research R6: "5-20 ms", click-free within the spec's 20 ms, FR-024).
// Per-sample so a 1024-frame block never carries a jump; callback-only state, no allocation.
#ifndef TEMBRA_MIXER_GAIN_RAMP_H
#define TEMBRA_MIXER_GAIN_RAMP_H

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace tembra {

class GainRamp {
public:
    static constexpr double kRampSeconds = 0.010;

    explicit GainRamp(uint32_t rate = 48000, float initial = 1.0f) : current_(initial), target_(initial) { setRate(rate); }

    /** Host, before the callback uses the ramp (a renderer is built per stream rate). */
    void setRate(uint32_t rate) { rampFrames_ = std::max<uint32_t>(1, static_cast<uint32_t>(std::llround(kRampSeconds * rate))); }

    /** Starts a ramp from the current value to [target] over 10 ms; a target already reached settles at once. */
    void setTarget(float target) {
        target_ = target;
        if (target_ == current_) {
            remaining_ = 0;
            step_ = 0.0f;
            return;
        }
        remaining_ = rampFrames_;
        step_ = (target_ - current_) / static_cast<float>(rampFrames_);
    }

    /** Sets the gain at once, without a ramp (the first value a strip ever receives). */
    void snap(float value) {
        current_ = value;
        target_ = value;
        remaining_ = 0;
        step_ = 0.0f;
    }

    /** The gain for the next sample. */
    float next() {
        if (remaining_ > 0) {
            current_ += step_;
            if (--remaining_ == 0) current_ = target_;  // land exactly, whatever float rounding did on the way
        }
        return current_;
    }

    /** Moves the ramp by [frames] samples without producing them (a track with no clip playing). */
    void advance(uint32_t frames) {
        if (remaining_ == 0) return;
        if (frames >= remaining_) {
            remaining_ = 0;
            current_ = target_;
            return;
        }
        current_ += step_ * static_cast<float>(frames);
        remaining_ -= frames;
    }

    float current() const { return current_; }
    float target() const { return target_; }
    bool settled() const { return remaining_ == 0; }

private:
    uint32_t rampFrames_ = 480;
    float current_;
    float target_;
    float step_ = 0.0f;
    uint32_t remaining_ = 0;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_GAIN_RAMP_H
