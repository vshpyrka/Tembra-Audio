#include "tuner/pitch_smoother.h"

#include <algorithm>
#include <cmath>

namespace tembra {

PitchSmoother::PitchSmoother(uint32_t medianSize, float clarityThreshold, float minHz, float maxHz)
    : medianSize_(medianSize == 0 ? 1 : medianSize),
      clarityThreshold_(clarityThreshold),
      minHz_(minHz),
      maxHz_(maxHz),
      history_(medianSize_, 0.0),
      sorted_(medianSize_, 0.0) {}

bool PitchSmoother::isOctaveOf(double hz, double reference) const {
    if (hz <= 0.0 || reference <= 0.0) return false;
    const double cents = 1200.0 * std::log2(hz / reference);
    return std::abs(cents - 1200.0) <= kOctaveToleranceCents || std::abs(cents + 1200.0) <= kOctaveToleranceCents;
}

double PitchSmoother::median() {
    for (size_t i = 0; i < historyCount_; ++i) sorted_[i] = history_[i];
    std::sort(sorted_.begin(), sorted_.begin() + static_cast<long>(historyCount_));
    return sorted_[historyCount_ / 2];
}

Smoothed PitchSmoother::push(const PitchEstimate& estimate) {
    bool accepted = estimate.clarity >= clarityThreshold_ && estimate.hz >= minHz_ && estimate.hz <= maxHz_;
    if (accepted && hasAverage_) {
        const double stable = std::exp2(logHz_);
        if (isOctaveOf(estimate.hz, stable)) {
            // An octave jump counts only when the next estimate repeats it.
            if (pendingOctaveHz_ > 0.0 && std::abs(1200.0 * std::log2(estimate.hz / pendingOctaveHz_)) <= kOctaveToleranceCents) {
                pendingOctaveHz_ = 0.0;
                hasAverage_ = false;  // restart the average at the new octave
                historyCount_ = 0;
                historyNext_ = 0;
            } else {
                pendingOctaveHz_ = estimate.hz;
                return {lastHz_, hasPitch_};
            }
        } else {
            pendingOctaveHz_ = 0.0;
        }
    }
    if (accepted) {
        rejectedRun_ = 0;
        ++acceptedRun_;
        history_[historyNext_] = estimate.hz;
        historyNext_ = (historyNext_ + 1) % medianSize_;
        historyCount_ = std::min<size_t>(historyCount_ + 1, medianSize_);
        const double med = median();
        const double logMed = std::log2(med);
        if (!hasAverage_) {
            logHz_ = logMed;
            hasAverage_ = true;
        } else {
            logHz_ += kAlpha * (logMed - logHz_);
        }
        lastHz_ = static_cast<float>(std::exp2(logHz_));
        if (acceptedRun_ >= kAcceptedBeforePitch) hasPitch_ = true;
    } else {
        acceptedRun_ = 0;
        ++rejectedRun_;
        pendingOctaveHz_ = 0.0;
        if (rejectedRun_ > kRejectedBeforeRest) {
            hasPitch_ = false;
            hasAverage_ = false;
            historyCount_ = 0;
            historyNext_ = 0;
        }
    }
    return {lastHz_, hasPitch_};
}

void PitchSmoother::reset() {
    historyNext_ = 0;
    historyCount_ = 0;
    logHz_ = 0.0;
    hasAverage_ = false;
    pendingOctaveHz_ = 0.0;
    acceptedRun_ = 0;
    rejectedRun_ = 0;
    hasPitch_ = false;
    lastHz_ = 0.0f;
}

}  // namespace tembra
