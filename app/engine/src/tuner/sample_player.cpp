#include "tuner/sample_player.h"

#include <cmath>

namespace tembra {

namespace {
constexpr uint32_t kCommandPlay = 1;
constexpr uint32_t kCommandStop = 2;
constexpr double kHalfPi = 1.57079632679489661923;
}  // namespace

SamplePlayer::SamplePlayer(uint32_t sampleRate)
    : sampleRate_(sampleRate), releaseFrames_(static_cast<uint32_t>(kReleaseSeconds * sampleRate)) {}

void SamplePlayer::play(const DecodedSample& sample, bool loop) {
    requested_.store(&sample, std::memory_order_relaxed);
    requestedLoop_.store(loop, std::memory_order_relaxed);
    active_.store(true, std::memory_order_release);
    command_.store(kCommandPlay, std::memory_order_release);
}

void SamplePlayer::stop() {
    if (!active_.load(std::memory_order_acquire)) return;
    command_.store(kCommandStop, std::memory_order_release);
}

bool SamplePlayer::isActive() const { return active_.load(std::memory_order_acquire); }

float SamplePlayer::sampleAt(double position) const {
    const auto& mono = current_->mono;
    const size_t index = static_cast<size_t>(position);
    if (index + 1 >= mono.size()) return mono[mono.size() - 1];
    const float fraction = static_cast<float>(position - static_cast<double>(index));
    return mono[index] + (mono[index + 1] - mono[index]) * fraction;
}

void SamplePlayer::render(float* out, uint32_t frames) {
    const uint32_t command = command_.exchange(0, std::memory_order_acq_rel);
    if (command == kCommandPlay) {
        current_ = requested_.load(std::memory_order_relaxed);
        loop_ = requestedLoop_.load(std::memory_order_relaxed);
        position_ = 0.0;
        step_ = current_ != nullptr && current_->sampleRate > 0 ? static_cast<double>(current_->sampleRate) / sampleRate_ : 1.0;
        crossfadeFrames_ = current_ != nullptr ? kCrossfadeSeconds * current_->sampleRate : 0.0;
        releasing_ = false;
        releasePosition_ = 0;
    } else if (command == kCommandStop && current_ != nullptr && !releasing_) {
        releasing_ = true;
        releasePosition_ = 0;
    }
    for (uint32_t i = 0; i < frames; ++i) {
        float value = 0.0f;
        if (current_ != nullptr) {
            const double length = static_cast<double>(current_->mono.size());
            if (position_ >= length - 1.0) {
                if (loop_) {
                    position_ -= (length - 1.0 - crossfadeFrames_);
                } else {
                    current_ = nullptr;
                    active_.store(false, std::memory_order_release);
                }
            }
            if (current_ != nullptr) {
                value = sampleAt(position_);
                // Equal-power crossfade of the tail into the head over the last 10 ms of a loop.
                const double tailStart = length - 1.0 - crossfadeFrames_;
                if (loop_ && crossfadeFrames_ > 0.0 && position_ >= tailStart) {
                    const double t = (position_ - tailStart) / crossfadeFrames_;
                    const float tailGain = static_cast<float>(std::cos(t * kHalfPi));
                    const float headGain = static_cast<float>(std::sin(t * kHalfPi));
                    value = value * tailGain + sampleAt(position_ - tailStart) * headGain;
                }
                position_ += step_;
                if (releasing_) {
                    const float gain = 1.0f - static_cast<float>(releasePosition_) / static_cast<float>(releaseFrames_);
                    value *= gain;
                    if (++releasePosition_ >= releaseFrames_) {
                        current_ = nullptr;
                        releasing_ = false;
                        active_.store(false, std::memory_order_release);
                    }
                }
            }
        }
        out[2 * i] = value;
        out[2 * i + 1] = value;
    }
}

}  // namespace tembra
