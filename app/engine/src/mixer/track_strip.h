// One track's channel strip (feature 009, research R6; feature 012, research R5): the fader gain times the pan law's
// side gain times audibility (mute and solo are resolved to one audible flag in Kotlin), one 10 ms ramp per side so
// gain, pan, mute and solo changes are all smoothed and land exactly on their targets. Mono sources are duplicated to
// both outputs and shaped by the constant-power law (1/1 at the centre: a centred track renders as revision 7 did);
// stereo sources pass through with balance (the opposite side attenuates, nothing is boosted). Callback-only; no
// allocation, every loop bounded by the block.
#ifndef TEMBRA_MIXER_TRACK_STRIP_H
#define TEMBRA_MIXER_TRACK_STRIP_H

#include <cstdint>

#include "mixer/gain_ramp.h"

namespace tembra {

class TrackStrip {
public:
    explicit TrackStrip(uint32_t rate = 48000) : left_(rate, 1.0f), right_(rate, 1.0f) {}

    void setRate(uint32_t rate) {
        left_.setRate(rate);
        right_.setRate(rate);
    }

    /** The revision-7 form: the centre of the mono law, [gain] (0..2) when [audible], else 0. */
    void setTarget(float gain, bool audible) { setTarget(gain, 0.0f, audible, false); }
    void snap(float gain, bool audible) { snap(gain, 0.0f, audible, false); }

    /** The targets the next samples ramp toward: fader [gain] × side gain of [pan] (−1..1, [stereo] law) × [audible]. */
    void setTarget(float gain, float pan, bool audible, bool stereo);

    /** As setTarget, keeping the source kind the strip last processed. */
    void setTarget(float gain, float pan, bool audible) { setTarget(gain, pan, audible, stereo_); }

    /** The first values a strip receives are taken at once (nothing has played through it yet). */
    void snap(float gain, float pan, bool audible, bool stereo);
    void snap(float gain, float pan, bool audible) { snap(gain, pan, audible, stereo_); }

    /**
     * Writes [frames] stereo frames of [in] ([channels] interleaved) times the ramping side gains into [outStereo]. A
     * change of source kind (mono to stereo or back) re-targets the ramps, so a clip change never jumps.
     */
    void process(const float* in, uint32_t channels, float* outStereo, uint32_t frames);

    /** The ramps keep moving while nothing plays on the track, so the next clip starts at the settled gains. */
    void advance(uint32_t frames) {
        left_.advance(frames);
        right_.advance(frames);
    }

    /** The left side's gain for the next sample, both ramps advanced (the monitor strip runs at the centre, where the sides agree). */
    float nextGain() {
        right_.next();
        return left_.next();
    }

    float currentGain() const { return left_.current(); }
    float targetGain() const { return left_.target(); }
    float currentRightGain() const { return right_.current(); }
    float targetRightGain() const { return right_.target(); }
    bool stereo() const { return stereo_; }

private:
    void targets(float& left, float& right) const;

    GainRamp left_;
    GainRamp right_;
    float gain_ = 1.0f;
    float pan_ = 0.0f;
    bool audible_ = true;
    bool stereo_ = false;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_TRACK_STRIP_H
