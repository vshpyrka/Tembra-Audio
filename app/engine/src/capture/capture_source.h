// The real-time capture source (feature 010, research R5, R8; contracts/engine-c-abi-v7.md "Renderer rules" 3):
// owns the ordered fifo the callback pushes and the recorder thread pops, the per-block peak and the 64 ms
// recent-peak ring behind the meter, the sticky clip latch, the alignment discard at a take's start, the overrun
// counters and the CaptureRecord seqlock. The transport renderer calls measure(), capture() and publish() on the
// callback; the engine prepares it on the control thread while the callback cannot see it. No allocation after
// construction, no lock, no I/O, no tracing (the file is in TEMBRA_RT_SOURCES).
#ifndef TEMBRA_CAPTURE_CAPTURE_SOURCE_H
#define TEMBRA_CAPTURE_CAPTURE_SOURCE_H

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>

#include "capture/capture_record.h"
#include "timeline/spsc_fifo.h"
#include "tuner/seqlock.h"

namespace tembra {

class CaptureSource {
public:
    static constexpr double kFifoSeconds = 4.0;
    static constexpr double kRecentPeakSeconds = 0.064;
    static constexpr float kClipLevel = 0.9999f;
    static constexpr uint32_t kMaxChannels = 2;
    static constexpr uint32_t kMaxBlockFrames = 4096;
    static constexpr uint32_t kWarmUpBlocks = 2;
    static constexpr uint32_t kPeakHistory = 128;  // blocks of peaks kept for the 64 ms window (≥ 0.5 ms blocks)

    explicit CaptureSource(uint32_t rate);

    CaptureSource(const CaptureSource&) = delete;
    CaptureSource& operator=(const CaptureSource&) = delete;

    // --- Control thread, while the callback cannot see the source (before the arm publishes it) --------------------

    /** Resets everything for an input of [channels] at the construction rate with [alignmentFrames] of discard. */
    void prepare(uint32_t channels, int64_t alignmentFrames);

    // --- Control thread, any time ------------------------------------------------------------------------------------

    /** The discard the next take applies (a route change re-measured it). */
    void setAlignmentFrames(int64_t frames) { alignmentFrames_.store(frames, std::memory_order_release); }

    /** Clears the clip latch at the callback's next block. */
    void requestClipReset() { clipReset_.store(true, std::memory_order_release); }

    /**
     * Starts a take's bookkeeping before the renderer begins pushing: the kept-frame count, the discard, the backlog
     * maximum and the stop reason start over. Only while no take runs.
     */
    void beginTake();

    /** The recorder thread (or the engine) ends the take itself with [stopReason]; the callback observes it at its next block. */
    void endTake(uint32_t stopReason);

    bool engineStopRequested() const { return engineStop_.load(std::memory_order_acquire); }
    uint32_t stopReason() const { return stopReason_.load(std::memory_order_acquire); }
    void clearEngineStop() { engineStop_.store(false, std::memory_order_release); }

    // --- Audio thread --------------------------------------------------------------------------------------------------

    /**
     * Measures one input block of [channels] × [frames] (zero-padded by the device when short): the per-channel peak,
     * the clip latch, the recent-peak ring; [shortFrames] > 0 counts an overrun once the warm-up bursts are over.
     */
    void measure(const float* interleaved, uint32_t channels, uint32_t frames, uint32_t shortFrames);

    /**
     * Pushes the block's frames from [keepFrom] on into the fifo after the alignment discard (the take's first
     * frames); a full fifo loses the tail and counts one overrun. Only while a take records.
     */
    void capture(const float* interleaved, uint32_t channels, uint32_t frames, uint32_t keepFrom);

    /** Publishes the record for this block. */
    void publish(bool armed, bool recording, bool preRoll, bool monitoring, int64_t takeStartFrames, uint64_t blocks);

    // --- Any thread ----------------------------------------------------------------------------------------------------

    CaptureRecord record() const { return record_.load(); }
    SpscFifo<float>& fifo() { return fifo_; }
    uint32_t channels() const { return channels_; }
    uint32_t rate() const { return rate_; }
    int64_t alignmentFrames() const { return alignmentFrames_.load(std::memory_order_acquire); }
    int64_t framesCaptured() const { return framesCaptured_.load(std::memory_order_acquire); }
    uint32_t inputOverruns() const { return inputOverruns_.load(std::memory_order_acquire); }
    uint32_t recordBacklogMax() const { return backlogMax_.load(std::memory_order_acquire); }
    /** The recorder thread reports the largest backlog it drained (the callback reports what it left). */
    void noteBacklog(uint32_t frames);

private:
    float recentPeak(uint32_t channel) const;

    const uint32_t rate_;
    SpscFifo<float> fifo_;
    uint32_t channels_ = 1;
    std::atomic<int64_t> alignmentFrames_{0};
    std::atomic<int64_t> framesCaptured_{0};
    std::atomic<uint32_t> inputOverruns_{0};
    std::atomic<uint32_t> backlogMax_{0};
    std::atomic<uint32_t> stopReason_{0};
    std::atomic<bool> engineStop_{false};
    std::atomic<bool> clipReset_{false};
    Seqlock<CaptureRecord> record_;

    // Callback-owned.
    int64_t discardRemaining_ = 0;
    uint32_t blocksMeasured_ = 0;
    bool clipped_ = false;
    struct PeakEntry {
        float peak[2] = {0.0f, 0.0f};
        uint32_t frames = 0;
    };
    std::array<PeakEntry, kPeakHistory> history_{};
    uint32_t historyNext_ = 0;
    uint32_t windowFrames_;
};

}  // namespace tembra

#endif  // TEMBRA_CAPTURE_CAPTURE_SOURCE_H
