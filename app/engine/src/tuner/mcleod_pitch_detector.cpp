#include "tuner/mcleod_pitch_detector.h"

#include <algorithm>
#include <cmath>

namespace tembra {

namespace {

size_t powerOfTwoAtLeast(size_t value) {
    size_t power = 1;
    while (power < value) power <<= 1;
    return power;
}

}  // namespace

McLeodPitchDetector::McLeodPitchDetector(uint32_t windowFrames, float minHz, float maxHz, float peakRatio)
    : windowFrames_(windowFrames),
      minHz_(minHz),
      maxHz_(maxHz),
      peakRatio_(peakRatio),
      fftSize_(powerOfTwoAtLeast(static_cast<size_t>(windowFrames) * 2)),
      fft_(fftSize_),
      padded_(fftSize_, 0.0),
      spectrum_(fftSize_ / 2),
      correlation_(fftSize_, 0.0),
      prefix_(static_cast<size_t>(windowFrames) + 1, 0.0),
      nsdf_(windowFrames, 0.0) {
    maxima_.reserve(64);
}

void McLeodPitchDetector::computeNsdf(const float* window, size_t n, size_t maxLag) {
    // Autocorrelation through the FFT of the zero-padded window: r(τ) = IFFT(|X|²).
    for (size_t i = 0; i < n; ++i) padded_[i] = window[i];
    for (size_t i = n; i < fftSize_; ++i) padded_[i] = 0.0;
    fft_.fft(padded_.data(), spectrum_.data());
    for (auto& bin : spectrum_) bin = std::complex<double>(std::norm(bin), 0.0);
    fft_.ifft(spectrum_.data(), correlation_.data());

    // Prefix sums of x² give m(τ) = Σ x_j² + x_{j+τ}² over the overlap in O(1) per lag.
    prefix_[0] = 0.0;
    for (size_t i = 0; i < n; ++i) prefix_[i + 1] = prefix_[i] + static_cast<double>(window[i]) * window[i];
    const double total = prefix_[n];

    // The inverse transform's scale is fixed by r(0), which equals the total energy.
    const double scale = correlation_[0] > 0.0 ? total / correlation_[0] : 0.0;
    for (size_t lag = 0; lag <= maxLag; ++lag) {
        const double m = prefix_[n - lag] + (total - prefix_[lag]);
        nsdf_[lag] = m > 0.0 ? (2.0 * correlation_[lag] * scale) / m : 0.0;
    }
}

bool McLeodPitchDetector::findKeyMaxima(size_t maxLag) {
    maxima_.clear();
    size_t lag = 1;
    // Skip the zero-lag lobe: wait for the first negative value.
    while (lag <= maxLag && nsdf_[lag] > 0.0) ++lag;
    while (lag <= maxLag) {
        // Positive-going zero crossing.
        while (lag <= maxLag && nsdf_[lag] <= 0.0) ++lag;
        if (lag > maxLag) break;
        size_t bestLag = lag;
        double best = nsdf_[lag];
        while (lag <= maxLag && nsdf_[lag] > 0.0) {
            if (nsdf_[lag] > best) {
                best = nsdf_[lag];
                bestLag = lag;
            }
            ++lag;
        }
        maxima_.push_back({bestLag, best});
    }
    return !maxima_.empty();
}

PitchEstimate McLeodPitchDetector::estimate(const float* window, size_t n, float sampleRate) {
    if (window == nullptr || n < 2 || n > windowFrames_ || sampleRate <= 0.0f) return {0.0f, 0.0f};
    const size_t minLag = static_cast<size_t>(std::floor(sampleRate / maxHz_));
    size_t maxLag = static_cast<size_t>(std::ceil(sampleRate / minHz_));
    maxLag = std::min(maxLag, n * 3 / 4);
    if (minLag < 2 || maxLag <= minLag) return {0.0f, 0.0f};

    double energy = 0.0;
    for (size_t i = 0; i < n; ++i) energy += static_cast<double>(window[i]) * window[i];
    if (energy < kEnergyFloorPerFrame * static_cast<double>(n)) return {0.0f, 0.0f};

    computeNsdf(window, n, maxLag);
    if (!findKeyMaxima(maxLag)) return {0.0f, 0.0f};

    double highest = 0.0;
    for (const KeyMaximum& m : maxima_) highest = std::max(highest, m.value);
    if (highest <= 0.0) return {0.0f, 0.0f};
    const double threshold = peakRatio_ * highest;
    const KeyMaximum* chosen = nullptr;
    for (const KeyMaximum& m : maxima_) {
        if (m.value >= threshold) {
            chosen = &m;
            break;
        }
    }
    if (chosen == nullptr) return {0.0f, 0.0f};
    // A first peak below the shortest lag means the pitch is above the range: rest rather than read a sub-harmonic.
    if (chosen->lag < minLag || chosen->lag > maxLag) return {0.0f, 0.0f};

    double lag = static_cast<double>(chosen->lag);
    double value = chosen->value;
    if (chosen->lag >= 1 && chosen->lag + 1 <= maxLag) {
        const double a = nsdf_[chosen->lag - 1];
        const double b = nsdf_[chosen->lag];
        const double c = nsdf_[chosen->lag + 1];
        const double denominator = a - 2.0 * b + c;
        if (denominator < 0.0) {
            const double delta = 0.5 * (a - c) / denominator;
            lag += delta;
            value = b - 0.25 * (a - c) * delta;
        }
    }
    const float hz = static_cast<float>(sampleRate / lag);
    const float clarity = static_cast<float>(std::clamp(value, 0.0, 1.0));
    return {hz, clarity};
}

}  // namespace tembra
