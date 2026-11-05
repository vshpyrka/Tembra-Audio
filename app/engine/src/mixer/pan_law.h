// The pan law (feature 012, research R5; contracts/engine-c-abi-v8.md "Renderer rules"): the side gains a pan
// position gives a track. Mono sources follow the centre-compensated constant-power law, θ = (pan + 1)·π/4,
// L = √2·cos θ, R = √2·sin θ: 1/1 at the centre (so every project from before the feature plays unchanged), √2 on
// one side and silence on the other at the extremes, L² + R² = 2 everywhere (the loudness stays put as a track
// moves). Stereo sources are balanced: the same curve capped at 1, so the opposite side attenuates and nothing is
// boosted. Header-only and pure; the strip calls it when a target changes, never per sample.
#ifndef TEMBRA_MIXER_PAN_LAW_H
#define TEMBRA_MIXER_PAN_LAW_H

#include <algorithm>
#include <cmath>

namespace tembra {

struct PanLaw {
    static constexpr float kRoot2 = 1.41421356237f;

    /** Fills [left] and [right] for [pan] in −1 (left) … 1 (right); a value outside the range is held at the extremes. */
    static void gains(float pan, bool stereo, float& left, float& right) {
        const float position = std::isfinite(pan) ? std::clamp(pan, -1.0f, 1.0f) : 0.0f;
        if (position == 0.0f) {
            left = 1.0f;
            right = 1.0f;
            return;
        }
        if (position <= -1.0f) {
            left = stereo ? 1.0f : kRoot2;
            right = 0.0f;
            return;
        }
        if (position >= 1.0f) {
            left = 0.0f;
            right = stereo ? 1.0f : kRoot2;
            return;
        }
        const double theta = (static_cast<double>(position) + 1.0) * kQuarterPi;
        float l = static_cast<float>(kRoot2 * std::cos(theta));
        float r = static_cast<float>(kRoot2 * std::sin(theta));
        if (stereo) {
            l = std::min(1.0f, l);
            r = std::min(1.0f, r);
        }
        left = l;
        right = r;
    }

private:
    static constexpr double kQuarterPi = 0.78539816339744830962;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_PAN_LAW_H
