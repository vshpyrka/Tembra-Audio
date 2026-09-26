#include "tuner/band_pass.h"

#include <cmath>

namespace tembra {

namespace {
constexpr double kPi = 3.14159265358979323846;
}  // namespace

double BandPass::Biquad::process(double x) {
    // Transposed direct form II.
    const double y = b0 * x + z1;
    z1 = b1 * x - a1 * y + z2;
    z2 = b2 * x - a2 * y;
    return y;
}

void BandPass::Biquad::reset() {
    z1 = 0.0;
    z2 = 0.0;
}

BandPass::Biquad BandPass::highPass(double sampleRate, double cutoffHz, double q) {
    const double w0 = 2.0 * kPi * cutoffHz / sampleRate;
    const double cosW0 = std::cos(w0);
    const double alpha = std::sin(w0) / (2.0 * q);
    const double a0 = 1.0 + alpha;
    Biquad b;
    b.b0 = ((1.0 + cosW0) / 2.0) / a0;
    b.b1 = (-(1.0 + cosW0)) / a0;
    b.b2 = ((1.0 + cosW0) / 2.0) / a0;
    b.a1 = (-2.0 * cosW0) / a0;
    b.a2 = (1.0 - alpha) / a0;
    return b;
}

BandPass::Biquad BandPass::lowPass(double sampleRate, double cutoffHz, double q) {
    const double w0 = 2.0 * kPi * cutoffHz / sampleRate;
    const double cosW0 = std::cos(w0);
    const double alpha = std::sin(w0) / (2.0 * q);
    const double a0 = 1.0 + alpha;
    Biquad b;
    b.b0 = ((1.0 - cosW0) / 2.0) / a0;
    b.b1 = (1.0 - cosW0) / a0;
    b.b2 = ((1.0 - cosW0) / 2.0) / a0;
    b.a1 = (-2.0 * cosW0) / a0;
    b.a2 = (1.0 - alpha) / a0;
    return b;
}

BandPass::BandPass(uint32_t sampleRate, float lowHz, float highHz)
    : highPass_(highPass(sampleRate, lowHz * kLowFactor, kButterworthQ)),
      lowPass_(lowPass(sampleRate, highHz * kHighFactor, kButterworthQ)) {}

void BandPass::process(float* samples, size_t count) {
    for (size_t i = 0; i < count; ++i) {
        const double x = samples[i];
        const double dc = x - dcX1_ + kDcPole * dcY1_;
        dcX1_ = x;
        dcY1_ = dc;
        samples[i] = static_cast<float>(lowPass_.process(highPass_.process(dc)));
    }
}

void BandPass::reset() {
    dcX1_ = 0.0;
    dcY1_ = 0.0;
    highPass_.reset();
    lowPass_.reset();
}

}  // namespace tembra
