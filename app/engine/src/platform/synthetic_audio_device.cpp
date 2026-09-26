#include "platform/synthetic_audio_device.h"

#include <algorithm>
#include <chrono>
#include <cmath>

namespace tembra {

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
// Relative level of harmonics 2, 3, 4, 5, as the Catch2 fixtures use; higher partials fall off further.
constexpr double kHarmonicLevels[] = {1.0, 0.5, 0.3, 0.2};
}  // namespace

SyntheticAudioDevice::SyntheticAudioDevice(const tembra_test_signal& signal) : signal_(signal), block_(kBlockFrames * 1, 0.0f) {
    const uint32_t partials = std::max<uint32_t>(1, signal.harmonics);
    amplitudes_.resize(partials);
    phases_.assign(partials, 0.0);
    for (uint32_t k = 0; k < partials; ++k) {
        if (k == 0) {
            amplitudes_[k] = signal.fundamental_gain;
        } else if (k - 1 < 4) {
            amplitudes_[k] = kHarmonicLevels[k - 1];
        } else {
            amplitudes_[k] = kHarmonicLevels[3] / static_cast<double>(k - 3);
        }
    }
    // Scale so the actual peak of one cycle equals the requested amplitude.
    constexpr int kProbe = 4096;
    double peak = 0.0;
    for (int i = 0; i < kProbe; ++i) {
        const double phase = kTwoPi * static_cast<double>(i) / kProbe;
        double sample = 0.0;
        for (uint32_t k = 0; k < partials; ++k) sample += amplitudes_[k] * std::sin(phase * static_cast<double>(k + 1));
        peak = std::max(peak, std::abs(sample));
    }
    const double scale = peak > 0.0 ? static_cast<double>(signal.amplitude) / peak : 0.0;
    for (double& a : amplitudes_) a *= scale;
}

SyntheticAudioDevice::~SyntheticAudioDevice() { close(); }

int32_t SyntheticAudioDevice::openInput(uint32_t, InputCallback callback, void* context) {
    if (running_.load()) return TEMBRA_ERR_STATE;
    callback_ = callback;
    context_ = context;
    running_.store(true);
    thread_ = std::thread([this] { run(); });
    return TEMBRA_OK;
}

int32_t SyntheticAudioDevice::openOutput(uint32_t, RenderCallback, void*) { return TEMBRA_ERR_DEVICE; }

void SyntheticAudioDevice::close() {
    running_.store(false);
    if (thread_.joinable()) thread_.join();
    callback_ = nullptr;
    context_ = nullptr;
}

uint32_t SyntheticAudioDevice::sampleRate() const { return kRate; }

bool SyntheticAudioDevice::deviceLost() const { return false; }

void SyntheticAudioDevice::render(float* out, uint32_t frames) {
    const double increment = kTwoPi * signal_.frequency_hz / kRate;
    for (uint32_t i = 0; i < frames; ++i) {
        double sample = 0.0;
        for (size_t k = 0; k < amplitudes_.size(); ++k) {
            sample += amplitudes_[k] * std::sin(phases_[k]);
            phases_[k] += increment * static_cast<double>(k + 1);
            if (phases_[k] >= kTwoPi) phases_[k] -= kTwoPi;
        }
        out[i] = static_cast<float>(sample);
    }
}

void SyntheticAudioDevice::run() {
    using clock = std::chrono::steady_clock;
    const auto period = std::chrono::nanoseconds(static_cast<int64_t>(1e9 * kBlockFrames / kRate));
    auto next = clock::now();
    while (running_.load(std::memory_order_relaxed)) {
        render(block_.data(), kBlockFrames);
        if (callback_ != nullptr) callback_(context_, block_.data(), kBlockFrames);
        next += period;
        std::this_thread::sleep_until(next);
    }
}

}  // namespace tembra
