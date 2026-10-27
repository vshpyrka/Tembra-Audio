#include "capture/capture_source.h"

#include <algorithm>
#include <cmath>

#include "tembra_engine.h"

namespace tembra {

CaptureSource::CaptureSource(uint32_t rate)
    : rate_(rate),
      fifo_(static_cast<size_t>(kFifoSeconds * rate) * kMaxChannels),
      windowFrames_(static_cast<uint32_t>(kRecentPeakSeconds * rate)) {}

void CaptureSource::prepare(uint32_t channels, int64_t alignmentFrames) {
    channels_ = channels == 0 ? 1 : (channels > kMaxChannels ? kMaxChannels : channels);
    alignmentFrames_.store(alignmentFrames, std::memory_order_release);
    framesCaptured_.store(0, std::memory_order_release);
    inputOverruns_.store(0, std::memory_order_release);
    backlogMax_.store(0, std::memory_order_release);
    stopReason_.store(TEMBRA_TAKE_STOP_USER, std::memory_order_release);
    engineStop_.store(false, std::memory_order_release);
    clipReset_.store(false, std::memory_order_release);
    discardRemaining_ = 0;
    blocksMeasured_ = 0;
    clipped_ = false;
    for (PeakEntry& entry : history_) entry = PeakEntry{};
    historyNext_ = 0;
    fifo_.reset();
    record_.store(CaptureRecord{});
}

void CaptureSource::beginTake() {
    framesCaptured_.store(0, std::memory_order_release);
    backlogMax_.store(0, std::memory_order_release);
    stopReason_.store(TEMBRA_TAKE_STOP_USER, std::memory_order_release);
    engineStop_.store(false, std::memory_order_release);
    discardRemaining_ = alignmentFrames_.load(std::memory_order_acquire);
    fifo_.reset();  // the consumer is not reading between takes
}

void CaptureSource::endTake(uint32_t stopReason) {
    stopReason_.store(stopReason, std::memory_order_release);
    engineStop_.store(true, std::memory_order_release);
}

void CaptureSource::noteBacklog(uint32_t frames) {
    uint32_t current = backlogMax_.load(std::memory_order_relaxed);
    while (frames > current && !backlogMax_.compare_exchange_weak(current, frames, std::memory_order_acq_rel)) {
    }
}

void CaptureSource::measure(const float* interleaved, uint32_t channels, uint32_t frames, uint32_t shortFrames) {
    const uint32_t used = std::min<uint32_t>(channels, kMaxChannels);
    PeakEntry entry;
    entry.frames = frames;
    for (uint32_t i = 0; i < frames; ++i) {
        for (uint32_t c = 0; c < used; ++c) {
            const float magnitude = std::fabs(interleaved[static_cast<size_t>(i) * channels + c]);
            if (magnitude > entry.peak[c]) entry.peak[c] = magnitude;
        }
    }
    history_[historyNext_] = entry;
    historyNext_ = (historyNext_ + 1) % kPeakHistory;
    if (clipReset_.exchange(false, std::memory_order_acq_rel)) clipped_ = false;
    if (entry.peak[0] >= kClipLevel || entry.peak[1] >= kClipLevel) clipped_ = true;
    if (blocksMeasured_ < kWarmUpBlocks) {
        ++blocksMeasured_;
    } else if (shortFrames > 0) {
        inputOverruns_.fetch_add(1, std::memory_order_acq_rel);
    }
}

void CaptureSource::capture(const float* interleaved, uint32_t channels, uint32_t frames, uint32_t keepFrom) {
    if (keepFrom >= frames) return;
    uint32_t from = keepFrom;
    uint32_t available = frames - keepFrom;
    if (discardRemaining_ > 0) {
        const uint32_t discard = static_cast<uint32_t>(std::min<int64_t>(discardRemaining_, available));
        discardRemaining_ -= discard;
        from += discard;
        available -= discard;
        if (available == 0) return;
    }
    const size_t samples = static_cast<size_t>(available) * channels;
    const size_t written = fifo_.write(interleaved + static_cast<size_t>(from) * channels, samples);
    if (written < samples) inputOverruns_.fetch_add(1, std::memory_order_acq_rel);
    framesCaptured_.fetch_add(static_cast<int64_t>(written / channels), std::memory_order_acq_rel);
}

float CaptureSource::recentPeak(uint32_t channel) const {
    // The newest entry always counts; older ones while their cumulative frames stay within the window.
    float peak = 0.0f;
    uint32_t covered = 0;
    for (uint32_t k = 0; k < kPeakHistory; ++k) {
        const PeakEntry& entry = history_[(historyNext_ + kPeakHistory - 1 - k) % kPeakHistory];
        if (entry.frames == 0) break;
        if (k > 0 && covered >= windowFrames_) break;
        if (entry.peak[channel] > peak) peak = entry.peak[channel];
        covered += entry.frames;
    }
    return peak;
}

void CaptureSource::publish(bool armed, bool recording, bool preRoll, bool monitoring, int64_t takeStartFrames, uint64_t blocks) {
    CaptureRecord record;
    record.armed = armed ? 1u : 0u;
    record.recording = recording ? 1u : 0u;
    record.preRoll = preRoll ? 1u : 0u;
    record.monitoring = monitoring ? 1u : 0u;
    record.channels = channels_;
    record.rate = rate_;
    record.peak[0] = recentPeak(0);
    record.peak[1] = channels_ > 1 ? recentPeak(1) : 0.0f;
    record.clipped = clipped_ ? 1u : 0u;
    record.framesCaptured = framesCaptured_.load(std::memory_order_relaxed);
    record.takeStartFrames = takeStartFrames;
    record.alignmentFrames = alignmentFrames_.load(std::memory_order_relaxed);
    record.stopReason = stopReason_.load(std::memory_order_relaxed);
    record.inputOverruns = inputOverruns_.load(std::memory_order_relaxed);
    const uint32_t backlog = static_cast<uint32_t>(fifo_.available() / channels_);
    record.backlogFrames = backlog;
    if (backlog > backlogMax_.load(std::memory_order_relaxed)) backlogMax_.store(backlog, std::memory_order_relaxed);
    record.blocks = blocks;
    record_.store(record);
}

}  // namespace tembra
