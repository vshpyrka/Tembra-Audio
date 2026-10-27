// The monitor strip (feature 010, research R12): the armed track's input through its own TrackStrip (gain and
// audibility, 10 ms ramps) times a 5 ms fade that follows the monitoring flag, summed into the master bus after the
// clips. A second ramp instance, so the track's own strip never runs twice per block. Callback-only; no allocation,
// every loop bounded by the block (the file is in TEMBRA_RT_SOURCES).
#ifndef TEMBRA_MIXER_MONITOR_STRIP_H
#define TEMBRA_MIXER_MONITOR_STRIP_H

#include <cstdint>

#include "mixer/track_strip.h"

namespace tembra {

class MonitorStrip {
public:
    static constexpr double kFadeSeconds = 0.005;

    explicit MonitorStrip(uint32_t rate = 48000) : strip_(rate) { setRate(rate); }

    void setRate(uint32_t rate);

    /** The armed track's targets (gain 0..2, audible), ramped over 10 ms by the strip. */
    void setTarget(float gain, bool audible) { strip_.setTarget(gain, audible); }
    void snap(float gain, bool audible) { strip_.snap(gain, audible); }

    /** Starts the 5 ms fade in or out. */
    void setEnabled(bool enabled);

    /** True while monitoring sounds (enabled, or fading out). */
    bool active() const { return enabled_ || fade_ > 0.0f; }
    bool enabled() const { return enabled_; }

    /** Writes [frames] stereo frames of [in] ([channels] interleaved) times gain and fade into [outStereo]. */
    void process(const float* in, uint32_t channels, float* outStereo, uint32_t frames);

    /** Moves the ramps on while nothing is processed. */
    void advance(uint32_t frames);

private:
    TrackStrip strip_;
    bool enabled_ = false;
    float fade_ = 0.0f;
    float fadeStep_ = 1.0f / 240.0f;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_MONITOR_STRIP_H
