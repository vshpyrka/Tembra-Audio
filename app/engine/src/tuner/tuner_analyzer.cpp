#include "tuner/tuner_analyzer.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

namespace tembra {

int64_t TunerAnalyzer::nowNanos() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

bool TunerAnalyzer::validate(const tembra_tuner_config& config) {
    if (config.window_frames < 256 || config.window_frames > 65536) return false;
    if (config.hop_frames == 0 || config.hop_frames > config.window_frames) return false;
    if (config.window_frames % config.hop_frames != 0) return false;
    if (!(config.min_hz > 0.0f) || !(config.max_hz > config.min_hz)) return false;
    if (!(config.clarity_threshold >= 0.0f && config.clarity_threshold <= 1.0f)) return false;
    if (!(config.peak_ratio > 0.0f && config.peak_ratio <= 1.0f)) return false;
    if (config.median_size == 0 || config.median_size > 64) return false;
    if (!(config.watchdog_seconds >= 0.0f)) return false;
    return true;
}

TunerAnalyzer::TunerAnalyzer(const tembra_tuner_config& config, uint32_t sampleRate, bool synthetic)
    : config_(config),
      sampleRate_(sampleRate),
      synthetic_(synthetic),
      ring_(static_cast<size_t>(config.window_frames) * 4),
      filter_(sampleRate, config.min_hz, config.max_hz),
      detector_(config.window_frames, config.min_hz, config.max_hz, config.peak_ratio),
      smoother_(config.median_size, config.clarity_threshold, config.min_hz, config.max_hz),
      hop_(config.hop_frames, 0.0f),
      window_(config.window_frames, 0.0f),
      lastReadNanos_(nowNanos()),
      watchdogNanos_(static_cast<int64_t>(static_cast<double>(config.watchdog_seconds) * 1e9)) {
    // Sequence 0 until the first analysed hop (contracts/engine-c-abi-v2.md).
    tembra_pitch_reading initial{};
    initial.state = TEMBRA_AUDIO_LISTENING;
    initial.sample_rate = sampleRate_;
    initial.synthetic = synthetic_ ? 1 : 0;
    reading_.store(initial);
}

void TunerAnalyzer::push(const float* mono, uint32_t frames) {
    ring_.push(mono, frames);
}

bool TunerAnalyzer::analyseAvailable() {
    const size_t hop = config_.hop_frames;
    if (ring_.available() < hop) return false;
    if (!ring_.read(hop_.data(), hop)) return false;

    float peak = 0.0f;
    for (size_t i = 0; i < hop; ++i) peak = std::max(peak, std::abs(hop_[i]));
    filter_.process(hop_.data(), hop);

    const size_t window = config_.window_frames;
    std::memmove(window_.data(), window_.data() + hop, (window - hop) * sizeof(float));
    std::memcpy(window_.data() + (window - hop), hop_.data(), hop * sizeof(float));
    filled_ = std::min(filled_ + hop, window);

    Smoothed smoothed{0.0f, false};
    float clarity = 0.0f;
    if (filled_ >= window) {
        const PitchEstimate estimate = detector_.estimate(window_.data(), window, static_cast<float>(sampleRate_));
        clarity = estimate.clarity;
        smoothed = smoother_.push(estimate);
    }
    publish(smoothed.hz, clarity, peak, smoothed.hasPitch);
    return true;
}

void TunerAnalyzer::publish(float hz, float clarity, float inputPeak, bool hasPitch) {
    tembra_pitch_reading reading{};
    reading.sequence = sequence_.load(std::memory_order_relaxed) + 1;
    reading.state = TEMBRA_AUDIO_LISTENING;
    reading.sample_rate = sampleRate_;
    reading.frequency_hz = hz;
    reading.clarity = clarity;
    reading.input_peak = inputPeak;
    reading.has_pitch = hasPitch ? 1 : 0;
    reading.synthetic = synthetic_ ? 1 : 0;
    reading_.store(reading);
    sequence_.store(reading.sequence, std::memory_order_release);
}

void TunerAnalyzer::read(tembra_pitch_reading& out) const {
    out = reading_.load();
    lastReadNanos_.store(nowNanos(), std::memory_order_relaxed);
}

bool TunerAnalyzer::watchdogExpired(int64_t nowNanos) const {
    if (watchdogNanos_ <= 0) return false;
    return nowNanos - lastReadNanos_.load(std::memory_order_relaxed) > watchdogNanos_;
}

void TunerAnalyzer::publishResting() {
    tembra_pitch_reading reading = reading_.load();
    reading.state = TEMBRA_AUDIO_IDLE;
    reading.has_pitch = 0;
    reading.frequency_hz = 0.0f;
    reading.clarity = 0.0f;
    reading.input_peak = 0.0f;
    reading_.store(reading);
}

}  // namespace tembra
