#include "mixer/monitor_strip.h"

#include <algorithm>
#include <cmath>

namespace tembra {

void MonitorStrip::setRate(uint32_t rate) {
    strip_.setRate(rate);
    const uint32_t fadeFrames = std::max<uint32_t>(1, static_cast<uint32_t>(std::llround(kFadeSeconds * rate)));
    fadeStep_ = 1.0f / static_cast<float>(fadeFrames);
}

void MonitorStrip::setEnabled(bool enabled) { enabled_ = enabled; }

void MonitorStrip::process(const float* in, uint32_t channels, float* out, uint32_t frames) {
    const float target = enabled_ ? 1.0f : 0.0f;
    if (channels <= 1) {
        for (uint32_t i = 0; i < frames; ++i) {
            if (fade_ < target) fade_ = std::min(target, fade_ + fadeStep_);
            else if (fade_ > target) fade_ = std::max(target, fade_ - fadeStep_);
            const float sample = in[i] * strip_.nextGain() * fade_;
            out[2 * i] = sample;
            out[2 * i + 1] = sample;
        }
        return;
    }
    for (uint32_t i = 0; i < frames; ++i) {
        if (fade_ < target) fade_ = std::min(target, fade_ + fadeStep_);
        else if (fade_ > target) fade_ = std::max(target, fade_ - fadeStep_);
        const float gain = strip_.nextGain() * fade_;
        out[2 * i] = in[i * channels] * gain;
        out[2 * i + 1] = in[i * channels + 1] * gain;
    }
}

void MonitorStrip::advance(uint32_t frames) {
    strip_.advance(frames);
    const float target = enabled_ ? 1.0f : 0.0f;
    const float delta = fadeStep_ * static_cast<float>(frames);
    if (fade_ < target) fade_ = std::min(target, fade_ + delta);
    else if (fade_ > target) fade_ = std::max(target, fade_ - delta);
}

}  // namespace tembra
