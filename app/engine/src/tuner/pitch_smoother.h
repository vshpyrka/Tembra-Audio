// Turns raw per-hop estimates into a steady reading: clarity and range gate, median of the last N accepted values,
// an octave-jump guard, a light exponential average in the log-frequency domain and a short hold before resting.
#ifndef TEMBRA_TUNER_PITCH_SMOOTHER_H
#define TEMBRA_TUNER_PITCH_SMOOTHER_H

#include <cstddef>
#include <cstdint>
#include <vector>

#include "tuner/pitch_detector.h"

namespace tembra {

struct Smoothed {
    float hz;
    bool hasPitch;
};

class PitchSmoother {
public:
    PitchSmoother(uint32_t medianSize, float clarityThreshold, float minHz, float maxHz);

    Smoothed push(const PitchEstimate& estimate);
    void reset();

private:
    static constexpr double kAlpha = 0.3;
    static constexpr double kOctaveToleranceCents = 3.0;
    static constexpr int kAcceptedBeforePitch = 2;
    static constexpr int kRejectedBeforeRest = 4;

    bool isOctaveOf(double hz, double reference) const;
    double median();

    const uint32_t medianSize_;
    const float clarityThreshold_;
    const float minHz_;
    const float maxHz_;
    std::vector<double> history_;
    std::vector<double> sorted_;
    size_t historyNext_ = 0;
    size_t historyCount_ = 0;
    double logHz_ = 0.0;
    bool hasAverage_ = false;
    double pendingOctaveHz_ = 0.0;
    int acceptedRun_ = 0;
    int rejectedRun_ = 0;
    bool hasPitch_ = false;
    float lastHz_ = 0.0f;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_PITCH_SMOOTHER_H
