// Pre-filter in front of the pitch detector: a DC blocker, a second-order high-pass just below the hearing range
// and a second-order low-pass just above it. Stateful, in place, no allocation after construction.
#ifndef TEMBRA_TUNER_BAND_PASS_H
#define TEMBRA_TUNER_BAND_PASS_H

#include <cstddef>
#include <cstdint>

namespace tembra {

class BandPass {
public:
    /** Cut-offs sit at 0.9 × [lowHz] and 1.1 × [highHz] so the range itself passes within a decibel. */
    explicit BandPass(uint32_t sampleRate, float lowHz = 50.0f, float highHz = 1400.0f);

    void process(float* samples, size_t count);
    void reset();

private:
    struct Biquad {
        double b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0;
        double z1 = 0.0, z2 = 0.0;
        double process(double x);
        void reset();
    };

    static Biquad highPass(double sampleRate, double cutoffHz, double q);
    static Biquad lowPass(double sampleRate, double cutoffHz, double q);

    /** Corner near 8 Hz: a 0.995 pole would already cost 0.8 dB at the low E (research R1 asks for the range within 1 dB). */
    static constexpr double kDcPole = 0.999;
    static constexpr double kButterworthQ = 0.7071067811865476;
    static constexpr double kLowFactor = 0.9;
    static constexpr double kHighFactor = 1.1;

    double dcX1_ = 0.0;
    double dcY1_ = 0.0;
    Biquad highPass_;
    Biquad lowPass_;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_BAND_PASS_H
