#include "mixer/master_bus.h"

#include <algorithm>

namespace tembra {

void MasterBus::begin(uint32_t frames) {
    const size_t samples = std::min<size_t>(static_cast<size_t>(frames), kMaxFrames) * 2;
    for (size_t i = 0; i < samples; ++i) accumulator_[i] = 0.0f;
    clampEngaged_ = false;
}

void MasterBus::add(const float* stereo, uint32_t frames) {
    const size_t samples = std::min<size_t>(static_cast<size_t>(frames), kMaxFrames) * 2;
    for (size_t i = 0; i < samples; ++i) accumulator_[i] += stereo[i];
}

void MasterBus::finish(float* out, uint32_t frames) {
    const size_t count = std::min<size_t>(static_cast<size_t>(frames), kMaxFrames);
    bool clamped = false;
    for (size_t i = 0; i < count; ++i) {
        const float gain = master_.next();
        const float left = accumulator_[2 * i] * gain;
        const float right = accumulator_[2 * i + 1] * gain;
        if (left > 1.0f || left < -1.0f || right > 1.0f || right < -1.0f) clamped = true;
        out[2 * i] = std::clamp(left, -1.0f, 1.0f);
        out[2 * i + 1] = std::clamp(right, -1.0f, 1.0f);
    }
    clampEngaged_ = clamped;
}

}  // namespace tembra
