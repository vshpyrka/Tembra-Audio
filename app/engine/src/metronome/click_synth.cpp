#include "metronome/click_synth.h"

#include <algorithm>
#include <cmath>
#include <vector>

namespace tembra {

namespace {

constexpr double kPi = 3.14159265358979323846;

/** RBJ biquad (direct form I); host-side offline filtering only. */
class Biquad {
public:
    static Biquad bandPass(double centreHz, double q, double rate) {
        const double w = 2.0 * kPi * centreHz / rate;
        const double alpha = std::sin(w) / (2.0 * q);
        const double a0 = 1.0 + alpha;
        return Biquad(alpha / a0, 0.0, -alpha / a0, -2.0 * std::cos(w) / a0, (1.0 - alpha) / a0);
    }

    static Biquad highPass(double cutoffHz, double rate) {
        const double w = 2.0 * kPi * cutoffHz / rate;
        const double alpha = std::sin(w) / (2.0 * std::sqrt(0.5));
        const double c = std::cos(w);
        const double a0 = 1.0 + alpha;
        return Biquad((1.0 + c) / 2.0 / a0, -(1.0 + c) / a0, (1.0 + c) / 2.0 / a0, -2.0 * c / a0, (1.0 - alpha) / a0);
    }

    static Biquad lowPass(double cutoffHz, double rate) {
        const double w = 2.0 * kPi * cutoffHz / rate;
        const double alpha = std::sin(w) / (2.0 * std::sqrt(0.5));
        const double c = std::cos(w);
        const double a0 = 1.0 + alpha;
        return Biquad((1.0 - c) / 2.0 / a0, (1.0 - c) / a0, (1.0 - c) / 2.0 / a0, -2.0 * c / a0, (1.0 - alpha) / a0);
    }

    void process(std::vector<double>& signal) {
        for (double& x : signal) {
            const double y = b0_ * x + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
            x2_ = x1_;
            x1_ = x;
            y2_ = y1_;
            y1_ = y;
            x = y;
        }
    }

private:
    Biquad(double b0, double b1, double b2, double a1, double a2) : b0_(b0), b1_(b1), b2_(b2), a1_(a1), a2_(a2) {}

    double b0_, b1_, b2_, a1_, a2_;
    double x1_ = 0.0, x2_ = 0.0, y1_ = 0.0, y2_ = 0.0;
};

/** Deterministic white noise in [−1, 1] (a fixed seed keeps every render identical). */
std::vector<double> noise(size_t frames, uint32_t seed) {
    std::vector<double> out(frames);
    uint32_t state = seed;
    for (double& x : out) {
        state = state * 1664525u + 1013904223u;
        x = static_cast<double>(state) / 2147483648.0 - 1.0;
    }
    return out;
}

size_t framesFor(double ms, uint32_t rate) { return static_cast<size_t>(std::llround(ms * static_cast<double>(rate) / 1000.0)); }

/** Adds [weight] × [partial] × exp(−t / tau) over the first [ms] of [mix]. */
template <class Partial>
void addDecaying(std::vector<double>& mix, uint32_t rate, double ms, double tauMs, double weight, Partial partial) {
    const size_t frames = std::min(mix.size(), framesFor(ms, rate));
    for (size_t i = 0; i < frames; ++i) {
        const double t = static_cast<double>(i) / rate;
        mix[i] += weight * partial(i, t) * std::exp(-t * 1000.0 / tauMs);
    }
}

double sine(double hz, double t) { return std::sin(2.0 * kPi * hz * t); }

double square(double hz, double t) { return sine(hz, t) >= 0.0 ? 1.0 : -1.0; }

/** The raw recipe of [sound] (contracts/click-sounds.md) with the tonal partials multiplied by [ratio]. */
std::vector<double> recipe(tembra_click_sound sound, double ratio, uint32_t rate, double& attackMs) {
    attackMs = 1.0;
    switch (sound) {
        case TEMBRA_SOUND_808_RIM: {
            // Partial pair 1.7 kHz / 2.55 kHz over 12 ms and a 1 ms noise transient: the rimshot.
            std::vector<double> mix(framesFor(12.0, rate), 0.0);
            addDecaying(mix, rate, 12.0, 3.0, 1.0, [&](size_t, double t) { return sine(1700.0 * ratio, t); });
            addDecaying(mix, rate, 12.0, 3.0, 0.45, [&](size_t, double t) { return sine(2550.0 * ratio, t); });
            const auto transient = noise(framesFor(1.0, rate), 808u);
            addDecaying(mix, rate, 1.0, 0.5, 0.5, [&](size_t i, double) { return transient[i]; });
            return mix;
        }
        case TEMBRA_SOUND_DIGITAL_BEEP: {
            // A pure 1 kHz sine over 40 ms with a 2 ms attack: the reference click.
            attackMs = 2.0;
            std::vector<double> mix(framesFor(40.0, rate), 0.0);
            addDecaying(mix, rate, 40.0, 12.0, 1.0, [&](size_t, double t) { return sine(1000.0 * ratio, t); });
            return mix;
        }
        case TEMBRA_SOUND_COWBELL: {
            // Square partials 587 Hz and 845 Hz (the 808 cowbell's 1.44 ratio), band-passed 400–2 000 Hz, 90 ms.
            std::vector<double> mix(framesFor(90.0, rate), 0.0);
            addDecaying(mix, rate, 90.0, 22.0, 1.0, [&](size_t, double t) { return square(587.0 * ratio, t); });
            addDecaying(mix, rate, 90.0, 22.0, 0.7, [&](size_t, double t) { return square(845.0 * ratio, t); });
            Biquad high = Biquad::highPass(400.0, rate);
            Biquad low = Biquad::lowPass(2000.0, rate);
            high.process(mix);
            low.process(mix);
            return mix;
        }
        case TEMBRA_SOUND_MECHANICAL: {
            // A 4 ms burst of high filtered noise and a 3 kHz resonance over 15 ms: a pendulum escapement tick.
            std::vector<double> mix(framesFor(15.0, rate), 0.0);
            auto burst = noise(framesFor(4.0, rate), 4u);
            Biquad band = Biquad::bandPass(7000.0, 0.9, rate);
            band.process(burst);
            addDecaying(mix, rate, 4.0, 1.2, 1.6, [&](size_t i, double) { return burst[i]; });
            addDecaying(mix, rate, 15.0, 3.5, 0.5, [&](size_t, double t) { return sine(3000.0 * ratio, t); });
            return mix;
        }
        case TEMBRA_SOUND_CLASSIC_WOOD:
        case TEMBRA_SOUND_CUSTOM:
        default: {
            // Band-passed noise (2–5 kHz) over 8 ms and a 2.2 kHz damped sine over 25 ms: the woodblock "tok".
            std::vector<double> mix(framesFor(25.0, rate), 0.0);
            auto burst = noise(framesFor(8.0, rate), 2200u);
            Biquad band = Biquad::bandPass(std::sqrt(2000.0 * 5000.0), std::sqrt(2000.0 * 5000.0) / 3000.0, rate);
            band.process(burst);
            addDecaying(mix, rate, 8.0, 2.0, 0.3, [&](size_t i, double) { return burst[i]; });
            addDecaying(mix, rate, 25.0, 6.0, 1.0, [&](size_t, double t) { return sine(2200.0 * ratio, t); });
            return mix;
        }
    }
}

}  // namespace

ClickSample ClickSynth::render(tembra_click_sound sound, tembra_click_level level, uint32_t rate) {
    const double ratio = level == TEMBRA_CLICK_ACCENT ? kAccentRatio : 1.0;
    double attackMs = 1.0;
    std::vector<double> mix = recipe(sound, ratio, rate, attackMs);

    // Envelope rules (brief §3.4): raised-cosine attack from exactly 0, then the last 2 ms linearly to exactly 0.
    const size_t attack = std::max<size_t>(1, framesFor(attackMs, rate));
    for (size_t i = 0; i < attack && i < mix.size(); ++i) {
        mix[i] *= 0.5 - 0.5 * std::cos(kPi * static_cast<double>(i) / static_cast<double>(attack));
    }
    const size_t tail = std::max<size_t>(1, framesFor(2.0, rate));
    const size_t tailStart = mix.size() > tail ? mix.size() - tail : 0;
    for (size_t i = tailStart; i < mix.size(); ++i) {
        mix[i] *= static_cast<double>(mix.size() - 1 - i) / static_cast<double>(tail);
    }

    double peak = 0.0;
    for (double x : mix) peak = std::max(peak, std::abs(x));
    const double targetDb = level == TEMBRA_CLICK_ACCENT ? kAccentDbfs : (level == TEMBRA_CLICK_SUB ? kSubDbfs : kClickDbfs);
    const double gain = peak > 0.0 ? std::pow(10.0, targetDb / 20.0) / peak : 0.0;

    ClickSample sample;
    sample.rate = rate;
    sample.pcm.resize(mix.size());
    for (size_t i = 0; i < mix.size(); ++i) sample.pcm[i] = static_cast<float>(mix[i] * gain);
    return sample;
}

}  // namespace tembra
