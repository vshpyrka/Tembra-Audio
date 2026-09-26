#include "tuner/tone_generator.h"

#include <cmath>

namespace tembra {

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
constexpr uint32_t kCommandStart = 1;
constexpr uint32_t kCommandStop = 2;
constexpr double kSameFrequencyHz = 0.01;

/** Raised-cosine ramp value in [0, 1] for position/length. */
float ramp(uint32_t position, uint32_t length) {
    if (length == 0) return 1.0f;
    const double x = static_cast<double>(position) / static_cast<double>(length);
    return static_cast<float>(0.5 - 0.5 * std::cos(x * 3.14159265358979323846));
}
}  // namespace

ToneGenerator::ToneGenerator(uint32_t sampleRate)
    : sampleRate_(sampleRate), rampFrames_(static_cast<uint32_t>(kRampSeconds * sampleRate)) {}

void ToneGenerator::start(double frequencyHz, uint32_t durationMs) {
    requestedHz_.store(frequencyHz, std::memory_order_relaxed);
    const int64_t frames = durationMs == 0 ? -1 : static_cast<int64_t>(static_cast<double>(durationMs) * sampleRate_ / 1000.0);
    requestedFrames_.store(frames, std::memory_order_relaxed);
    active_.store(true, std::memory_order_release);
    command_.store(kCommandStart, std::memory_order_release);
}

void ToneGenerator::stop() {
    if (!active_.load(std::memory_order_acquire)) return;
    command_.store(kCommandStop, std::memory_order_release);
}

bool ToneGenerator::isActive() const { return active_.load(std::memory_order_acquire); }

void ToneGenerator::render(float* out, uint32_t frames) {
    const uint32_t command = command_.exchange(0, std::memory_order_acq_rel);
    if (command == kCommandStart) {
        const double hz = requestedHz_.load(std::memory_order_relaxed);
        const int64_t total = requestedFrames_.load(std::memory_order_relaxed);
        const bool sameTone = stage_ != Stage::Idle && stage_ != Stage::Release && stage_ != Stage::Switching &&
                              std::abs(hz - currentHz_) < kSameFrequencyHz;
        if (sameTone) {
            // Re-arm: keep phase and level, only the remaining duration changes (spec FR-019).
            remainingFrames_ = total < 0 ? -1 : total - static_cast<int64_t>(rampFrames_);
        } else if (stage_ == Stage::Idle) {
            currentHz_ = hz;
            increment_ = kTwoPi * hz / sampleRate_;
            phase_ = 0.0;
            remainingFrames_ = total < 0 ? -1 : total - static_cast<int64_t>(rampFrames_) * 2;
            rampPosition_ = 0;
            stage_ = Stage::Attack;
        } else {
            // Another frequency: release the current tone, then start the new one (at most 10 ms of near silence).
            pendingHz_ = hz;
            pendingFrames_ = total;
            rampPosition_ = 0;
            releaseFromLevel_ = level_;
            stage_ = Stage::Switching;
        }
    } else if (command == kCommandStop && stage_ != Stage::Idle) {
        if (stage_ != Stage::Release) {
            rampPosition_ = 0;
            releaseFromLevel_ = level_;
            stage_ = Stage::Release;
        }
    }
    for (uint32_t i = 0; i < frames; ++i) renderFrame(out[2 * i], out[2 * i + 1]);
}

void ToneGenerator::renderFrame(float& left, float& right) {
    if (stage_ == Stage::Idle) {
        left = 0.0f;
        right = 0.0f;
        return;
    }
    // Envelope.
    switch (stage_) {
        case Stage::Attack:
            level_ = ramp(rampPosition_, rampFrames_);
            if (++rampPosition_ >= rampFrames_) {
                level_ = 1.0f;
                stage_ = Stage::Sustain;
            }
            break;
        case Stage::Sustain:
            level_ = 1.0f;
            if (remainingFrames_ >= 0) {
                if (remainingFrames_ == 0) {
                    rampPosition_ = 0;
                    releaseFromLevel_ = level_;
                    stage_ = Stage::Release;
                } else {
                    --remainingFrames_;
                }
            }
            break;
        case Stage::Release:
        case Stage::Switching: {
            // The release always starts from the level reached, so a stop mid-attack is click-free too.
            level_ = releaseFromLevel_ * (1.0f - ramp(rampPosition_, rampFrames_));
            if (++rampPosition_ >= rampFrames_) {
                level_ = 0.0f;
                if (stage_ == Stage::Switching) {
                    currentHz_ = pendingHz_;
                    increment_ = kTwoPi * pendingHz_ / sampleRate_;
                    phase_ = 0.0;
                    remainingFrames_ = pendingFrames_ < 0 ? -1 : pendingFrames_ - static_cast<int64_t>(rampFrames_) * 2;
                    rampPosition_ = 0;
                    stage_ = Stage::Attack;
                } else {
                    stage_ = Stage::Idle;
                    active_.store(false, std::memory_order_release);
                }
            }
            break;
        }
        case Stage::Idle:
            break;
    }
    const float sample = static_cast<float>(std::sin(phase_)) * kAmplitude * level_;
    phase_ += increment_;
    if (phase_ >= kTwoPi) phase_ -= kTwoPi;
    left = sample;
    right = sample;
}

}  // namespace tembra
