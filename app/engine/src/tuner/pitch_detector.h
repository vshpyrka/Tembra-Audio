// The algorithm seam of the tuner (research R2): one window in, one estimate out. McLeodPitchDetector is the
// only implementation; YIN or another method would be a drop-in.
#ifndef TEMBRA_TUNER_PITCH_DETECTOR_H
#define TEMBRA_TUNER_PITCH_DETECTOR_H

#include <cstddef>

namespace tembra {

struct PitchEstimate {
    float hz;       // meaningful only when clarity is above the caller's threshold
    float clarity;  // 0..1, the height of the chosen NSDF peak
};

class PitchDetector {
public:
    virtual ~PitchDetector() = default;

    /** Estimates the pitch of [n] mono frames at [sampleRate]; clarity 0 when nothing periodic was found. */
    virtual PitchEstimate estimate(const float* window, size_t n, float sampleRate) = 0;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_PITCH_DETECTOR_H
