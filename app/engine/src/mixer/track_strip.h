// One track's channel strip (feature 009, research R6): a smoothed gain toward gain x audibility (mute and solo are
// resolved to one audible flag in Kotlin), mono clips to both outputs at unity, stereo through. Callback-only;
// no allocation, every loop bounded by the block.
#ifndef TEMBRA_MIXER_TRACK_STRIP_H
#define TEMBRA_MIXER_TRACK_STRIP_H

#include <cstdint>

#include "mixer/gain_ramp.h"

namespace tembra {

class TrackStrip {
public:
    explicit TrackStrip(uint32_t rate = 48000) : ramp_(rate, 1.0f) {}

    void setRate(uint32_t rate) { ramp_.setRate(rate); }

    /** The target the next samples ramp toward: [gain] (0..2) when [audible], else 0. */
    void setTarget(float gain, bool audible) { ramp_.setTarget(audible ? gain : 0.0f); }

    /** The first value a strip receives is taken at once (nothing has played through it yet). */
    void snap(float gain, bool audible) { ramp_.snap(audible ? gain : 0.0f); }

    /** Writes [frames] stereo frames of [in] ([channels] interleaved) times the ramping gain into [outStereo]. */
    void process(const float* in, uint32_t channels, float* outStereo, uint32_t frames);

    /** The ramp keeps moving while nothing plays on the track, so the next clip starts at the settled gain. */
    void advance(uint32_t frames) { ramp_.advance(frames); }

    /** The gain for the next sample (the monitor strip multiplies it with its own fade). */
    float nextGain() { return ramp_.next(); }

    float currentGain() const { return ramp_.current(); }
    float targetGain() const { return ramp_.target(); }

private:
    GainRamp ramp_;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_TRACK_STRIP_H
