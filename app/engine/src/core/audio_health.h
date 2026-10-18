// Audio-thread health counters (feature 007, contracts/engine-audio-health.md): written on the callback with relaxed
// atomic stores from a monotonic clock (no allocation, lock, logging or blocking: Constitution I), read by the
// non-real-time getter tembra_engine_audio_health. Defined now, filled with real load by the audio spine.
#ifndef TEMBRA_CORE_AUDIO_HEALTH_H
#define TEMBRA_CORE_AUDIO_HEALTH_H

#include <atomic>
#include <cstdint>

#include "tembra_engine.h"

namespace tembra {

class AudioHealth {
public:
    /** Microseconds from the monotonic clock; allocation-free and non-blocking, safe on the callback. */
    static uint64_t nowMicros();

    /** Stream start (host thread): resets the counters and records the real-time status of the audio thread. */
    void streamOpened(bool realtime);
    /** Stream closed (host thread): read() reports no stream until the next open. */
    void streamClosed();

    /**
     * One callback of [frames] at [rate] that ran from [startMicros] to [endMicros] (audio thread). Updates callbacks,
     * last_us, max_us and, when the duration exceeded the buffer period, over_budget. Single writer: the callback.
     */
    void record(uint64_t startMicros, uint64_t endMicros, uint32_t frames, uint32_t rate);

    /** Underruns and overruns found by the host-side drain (never the callback): adds to the running count. */
    void addXruns(uint32_t count);
    /** Sets the running xrun count from the platform stream's own counter (Oboe's getXRunCount, sampled off-callback). */
    void setXruns(uint32_t count);
    /** The transport renderer's counters (feature 009, revision 6), sampled from its atomics on the host thread. */
    void setTransportCounters(uint32_t starvation, uint32_t clipsActive);

    /** Copies the counters into [out]; false (and [out] untouched) while no stream is open. avg_us is the running mean. */
    bool read(tembra_audio_health& out) const;

private:
    std::atomic<bool> open_{false};
    std::atomic<uint64_t> callbacks_{0};
    std::atomic<uint64_t> sumMicros_{0};
    std::atomic<uint32_t> lastMicros_{0};
    std::atomic<uint32_t> maxMicros_{0};
    std::atomic<uint32_t> overBudget_{0};
    std::atomic<uint32_t> xruns_{0};
    std::atomic<uint32_t> starvation_{0};
    std::atomic<uint32_t> clipsActive_{0};
    std::atomic<bool> realtime_{false};
};

}  // namespace tembra

#endif  // TEMBRA_CORE_AUDIO_HEALTH_H
