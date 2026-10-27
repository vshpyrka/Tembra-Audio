#include "mixer/master_bus.h"

#include <algorithm>

namespace tembra {

void MasterBus::begin(uint32_t frames) {
    const size_t samples = std::min<size_t>(static_cast<size_t>(frames), kMaxFrames) * 2;
    for (size_t i = 0; i < samples; ++i) accumulator_[i] = 0.0f;
}

void MasterBus::add(const float* stereo, uint32_t frames) {
    const size_t samples = std::min<size_t>(static_cast<size_t>(frames), kMaxFrames) * 2;
    for (size_t i = 0; i < samples; ++i) accumulator_[i] += stereo[i];
}

void MasterBus::finish(float* out, uint32_t frames) {
    const size_t count = std::min<size_t>(static_cast<size_t>(frames), kMaxFrames);
    for (size_t i = 0; i < count; ++i) {
        const float gain = master_.next();
        out[2 * i] = std::clamp(accumulator_[2 * i] * gain, -1.0f, 1.0f);
        out[2 * i + 1] = std::clamp(accumulator_[2 * i + 1] * gain, -1.0f, 1.0f);
    }
}

}  // namespace tembra
