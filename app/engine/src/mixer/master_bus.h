// The master bus (feature 009, research R6): sums the track strips in float, applies the smoothed master gain and
// clamps to ±1.0 before the device. The accumulator is allocated once for the largest block the engine renders.
// Peak metering, pan and a limiter belong to feature 010.
#ifndef TEMBRA_MIXER_MASTER_BUS_H
#define TEMBRA_MIXER_MASTER_BUS_H

#include <cstddef>
#include <cstdint>
#include <vector>

#include "mixer/gain_ramp.h"

namespace tembra {

class MasterBus {
public:
    static constexpr size_t kMaxFrames = 4096;

    explicit MasterBus(uint32_t rate = 48000) : master_(rate, 1.0f), accumulator_(kMaxFrames * 2, 0.0f) {}

    void setRate(uint32_t rate) { master_.setRate(rate); }

    /** The master target (0..1), reached over 10 ms. */
    void setMaster(float gain) { master_.setTarget(gain); }

    /** Clears the accumulator for a block of [frames] (≤ kMaxFrames). */
    void begin(uint32_t frames);

    /** Adds [frames] stereo frames of one strip. */
    void add(const float* stereo, uint32_t frames);

    /** Writes the summed block times the master gain, clamped to ±1.0, into [outStereo]. */
    void finish(float* outStereo, uint32_t frames);

    float currentMaster() const { return master_.current(); }

private:
    GainRamp master_;
    std::vector<float> accumulator_;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_MASTER_BUS_H
