#include "core/audio_health.h"

#include <time.h>

namespace tembra {

uint64_t AudioHealth::nowMicros() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return static_cast<uint64_t>(now.tv_sec) * 1'000'000u + static_cast<uint64_t>(now.tv_nsec) / 1'000u;
}

void AudioHealth::streamOpened(bool realtime) {
    callbacks_.store(0, std::memory_order_relaxed);
    sumMicros_.store(0, std::memory_order_relaxed);
    lastMicros_.store(0, std::memory_order_relaxed);
    maxMicros_.store(0, std::memory_order_relaxed);
    overBudget_.store(0, std::memory_order_relaxed);
    xruns_.store(0, std::memory_order_relaxed);
    starvation_.store(0, std::memory_order_relaxed);
    clipsActive_.store(0, std::memory_order_relaxed);
    realtime_.store(realtime, std::memory_order_relaxed);
    open_.store(true, std::memory_order_release);
}

void AudioHealth::streamClosed() { open_.store(false, std::memory_order_release); }

void AudioHealth::record(uint64_t startMicros, uint64_t endMicros, uint32_t frames, uint32_t rate) {
    const uint64_t duration = endMicros >= startMicros ? endMicros - startMicros : 0;
    const auto micros = static_cast<uint32_t>(duration > UINT32_MAX ? UINT32_MAX : duration);
    callbacks_.fetch_add(1, std::memory_order_relaxed);
    sumMicros_.fetch_add(micros, std::memory_order_relaxed);
    lastMicros_.store(micros, std::memory_order_relaxed);
    if (micros > maxMicros_.load(std::memory_order_relaxed)) maxMicros_.store(micros, std::memory_order_relaxed);
    if (rate > 0) {
        const uint64_t periodMicros = static_cast<uint64_t>(frames) * 1'000'000u / rate;
        if (micros > periodMicros) overBudget_.fetch_add(1, std::memory_order_relaxed);
    }
}

void AudioHealth::addXruns(uint32_t count) { xruns_.fetch_add(count, std::memory_order_relaxed); }

void AudioHealth::setXruns(uint32_t count) { xruns_.store(count, std::memory_order_relaxed); }

void AudioHealth::setTransportCounters(uint32_t starvation, uint32_t clipsActive) {
    starvation_.store(starvation, std::memory_order_relaxed);
    clipsActive_.store(clipsActive, std::memory_order_relaxed);
}

bool AudioHealth::read(tembra_audio_health& out) const {
    if (!open_.load(std::memory_order_acquire)) return false;
    const uint64_t callbacks = callbacks_.load(std::memory_order_relaxed);
    const uint64_t sum = sumMicros_.load(std::memory_order_relaxed);
    out.callbacks = callbacks;
    out.last_us = lastMicros_.load(std::memory_order_relaxed);
    out.max_us = maxMicros_.load(std::memory_order_relaxed);
    out.over_budget = overBudget_.load(std::memory_order_relaxed);
    out.xruns = xruns_.load(std::memory_order_relaxed);
    out.realtime = realtime_.load(std::memory_order_relaxed) ? 1 : 0;
    out._reserved[0] = out._reserved[1] = out._reserved[2] = 0;
    out.avg_us = callbacks > 0 ? static_cast<uint32_t>(sum / callbacks) : 0;
    out.starvation = starvation_.load(std::memory_order_relaxed);
    out.clips_active = clipsActive_.load(std::memory_order_relaxed);
    return true;
}

}  // namespace tembra
