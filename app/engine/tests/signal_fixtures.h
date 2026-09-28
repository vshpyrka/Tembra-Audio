// Test-only signal generators shared by the DSP tests: harmonic tones with a weak fundamental (a plucked or bowed
// string), sines, silence, white noise, hard clipping and cent arithmetic.
#ifndef TEMBRA_TESTS_SIGNAL_FIXTURES_H
#define TEMBRA_TESTS_SIGNAL_FIXTURES_H

#include <cmath>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace tembra::fixtures {

constexpr double kPi = 3.14159265358979323846;

/** Partial amplitudes of the standard test tone: the fundamental 12 dB below the second harmonic (research R16). */
inline const std::vector<double>& defaultAmplitudes() {
    static const std::vector<double> amplitudes{0.25, 1.0, 0.5, 0.3, 0.2};
    return amplitudes;
}

/** [seconds] of a harmonic tone at [hz] whose partials follow [amplitudes]; the mix peaks near 0.5. */
inline std::vector<float> harmonicTone(double hz, double sampleRate, double seconds,
                                       const std::vector<double>& amplitudes = defaultAmplitudes()) {
    const size_t frames = static_cast<size_t>(seconds * sampleRate);
    double sum = 0.0;
    for (double a : amplitudes) sum += a;
    const double scale = 0.5 / sum;
    std::vector<float> out(frames);
    for (size_t i = 0; i < frames; ++i) {
        const double t = static_cast<double>(i) / sampleRate;
        double sample = 0.0;
        for (size_t k = 0; k < amplitudes.size(); ++k) {
            sample += amplitudes[k] * std::sin(2.0 * kPi * hz * static_cast<double>(k + 1) * t);
        }
        out[i] = static_cast<float>(sample * scale);
    }
    return out;
}

inline std::vector<float> sine(double hz, double sampleRate, double seconds, double amplitude = 0.5) {
    const size_t frames = static_cast<size_t>(seconds * sampleRate);
    std::vector<float> out(frames);
    for (size_t i = 0; i < frames; ++i) {
        out[i] = static_cast<float>(amplitude * std::sin(2.0 * kPi * hz * static_cast<double>(i) / sampleRate));
    }
    return out;
}

inline std::vector<float> silence(double sampleRate, double seconds) {
    return std::vector<float>(static_cast<size_t>(seconds * sampleRate), 0.0f);
}

/** Uniform white noise at [dbfs] peak (a linear congruential generator keeps the test deterministic). */
inline std::vector<float> whiteNoise(double dbfs, uint32_t seed, double sampleRate, double seconds) {
    const size_t frames = static_cast<size_t>(seconds * sampleRate);
    const double peak = std::pow(10.0, dbfs / 20.0);
    std::vector<float> out(frames);
    uint32_t state = seed;
    for (size_t i = 0; i < frames; ++i) {
        state = state * 1664525u + 1013904223u;
        const double unit = static_cast<double>(state >> 8) / static_cast<double>(1u << 24);  // [0, 1)
        out[i] = static_cast<float>((unit * 2.0 - 1.0) * peak);
    }
    return out;
}

/** Hard-clips [signal] at ±[threshold]. */
inline std::vector<float> clipped(std::vector<float> signal, float threshold) {
    for (float& s : signal) {
        if (s > threshold) s = threshold;
        if (s < -threshold) s = -threshold;
    }
    return signal;
}

inline double centsOffset(double hz, double cents) { return hz * std::pow(2.0, cents / 1200.0); }

inline double centsBetween(double a, double b) { return 1200.0 * std::log2(b / a); }

}  // namespace tembra::fixtures

#endif  // TEMBRA_TESTS_SIGNAL_FIXTURES_H
