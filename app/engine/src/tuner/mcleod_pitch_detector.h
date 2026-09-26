// McLeod Pitch Method (research R1): the normalised square difference function computed from an FFT
// autocorrelation and prefix sums, the first key maximum at or above peak_ratio × the highest, parabolic
// interpolation of its lag. All buffers are allocated in the constructor.
#ifndef TEMBRA_TUNER_MCLEOD_PITCH_DETECTOR_H
#define TEMBRA_TUNER_MCLEOD_PITCH_DETECTOR_H

#include <complex>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "dsp/fft.h"
#include "tuner/pitch_detector.h"

namespace tembra {

class McLeodPitchDetector : public PitchDetector {
public:
    McLeodPitchDetector(uint32_t windowFrames, float minHz, float maxHz, float peakRatio);

    PitchEstimate estimate(const float* window, size_t n, float sampleRate) override;

private:
    struct KeyMaximum {
        size_t lag;
        double value;
    };

    void computeNsdf(const float* window, size_t n, size_t maxLag);
    bool findKeyMaxima(size_t maxLag);

    static constexpr double kEnergyFloorPerFrame = 1e-8;  // RMS below 1e-4 (−80 dBFS) is silence

    const uint32_t windowFrames_;
    const float minHz_;
    const float maxHz_;
    const float peakRatio_;
    const size_t fftSize_;
    signalsmith::fft::RealFFT<double> fft_;
    std::vector<double> padded_;
    std::vector<std::complex<double>> spectrum_;
    std::vector<double> correlation_;
    std::vector<double> prefix_;
    std::vector<double> nsdf_;
    std::vector<KeyMaximum> maxima_;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_MCLEOD_PITCH_DETECTOR_H
