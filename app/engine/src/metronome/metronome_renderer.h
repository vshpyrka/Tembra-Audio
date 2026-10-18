// The real-time click scheduler (research R1, R2, R5, R7): a frame counter in the output callback, the unrounded
// position of the last tick recomputed with the live tempo every block, one sounding voice and one releasing voice.
#ifndef TEMBRA_METRONOME_METRONOME_RENDERER_H
#define TEMBRA_METRONOME_METRONOME_RENDERER_H

#include <atomic>
#include <cstdint>

#include "metronome/click_set.h"
#include "metronome/metronome_grid.h"
#include "metronome/metronome_tick.h"
#include "metronome/triple_buffer.h"
#include "tembra_engine.h"
#include "tuner/seqlock.h"

namespace tembra {

/**
 * Host API (one host thread at a time; EngineCore serialises it under its command mutex): setGrid, setSet, start,
 * stop, preview and anchor publish through triple buffers and atomics and never block. render() runs on the output
 * callback only and does no allocation, locking, logging or I/O; its loops are bounded by the block size.
 *
 * Scheduling (R1): nextTickFrame = lastTickFrame + framesPerTick with the grid of the current block, rounded only at
 * emit. Grid changes map the position: new tempo → the next tick moves, a tick already rendered never does; new
 * beats per bar → the bar restarts at the next beat boundary; new ticks per beat → the beat index is kept and the next
 * tick lands on the next beat boundary; new levels only → nothing moves.
 *
 * Voices (R5): a voice still sounding when the next tick arrives follows a 3 ms raised-cosine ramp to zero ending
 * exactly on that tick; if the ramp could not complete (the tick moved closer), the remainder is released by the
 * second voice. stop() releases every voice over 5 ms.
 */
class MetronomeRenderer {
public:
    explicit MetronomeRenderer(uint32_t rate);

    MetronomeRenderer(const MetronomeRenderer&) = delete;
    MetronomeRenderer& operator=(const MetronomeRenderer&) = delete;

    /** Publishes a validated grid; the callback applies it from its next block. */
    void setGrid(const MetronomeGrid& grid);

    /** Publishes the click set the next voice plays; [set] must stay alive while references() reports it. */
    void setSet(const ClickSet* set);

    /** Starts ticking: tick 0 (the level at index 0) sounds at frame 0 of the next rendered block. */
    void start();

    /** Stops ticking and releases any sounding voice over 5 ms. Idempotent. */
    void stop();

    /** Plays one voice of the current set at [level] without ticking (the sound picker's preview). */
    void preview(tembra_click_level level);

    /** Transport hook (feature 009, research R4): at output frame [frame] the bar is at [beatPosition] beats. */
    void anchor(int64_t frame, double beatPosition);

    /**
     * The frame counter the next render() starts at, in the frame space anchor() expects. Audio thread only: the
     * transport renderer, which renders this metronome every block, anchors relative to it.
     */
    int64_t framePosition() const { return framePos_; }

    /** True from start()/preview() until nothing sounds any more and the metronome is not running. */
    bool isActive() const;

    /** True while ticking (the last start() was not followed by stop()), as seen by the callback. */
    bool isRunning() const { return running_.load(std::memory_order_acquire); }

    /** Renders [frames] interleaved stereo frames, overwriting [interleavedStereo]. Audio thread only. */
    void render(float* interleavedStereo, uint32_t frames);

    /**
     * The last tick for the beat light: audible_in_ns = blockTimeNs + (tickFrame − blockStartFrame) × 1e9 / rate +
     * [latencyNs] − [nowNs] (R7). Lock-free; any thread.
     */
    void readTick(int64_t latencyNs, int64_t nowNs, tembra_metronome_tick* out) const;

    /** The raw record published after the last block. */
    MetronomeTick lastTick() const { return tick_.load(); }

    uint32_t sampleRate() const { return rate_; }

    /** Blocks rendered so far; with references() it tells the host when a retired set may be freed. */
    uint64_t blocksRendered() const { return blocks_.load(std::memory_order_acquire); }

    /** True when a voice was playing from [set] at the end of the last block. */
    bool references(const ClickSet* set) const;

    /** Drops every voice and pending command. Host thread, only while no callback can run (the stream is closed). */
    void silence();

    /** The engine's monotonic clock (CLOCK_MONOTONIC on Android, mach_absolute_time's clock on Apple). */
    static int64_t nowNanos();

    static constexpr double kPreTickRampSeconds = 0.003;
    static constexpr double kStopReleaseSeconds = 0.005;

private:
    struct Voice {
        const float* pcm = nullptr;
        uint32_t length = 0;
        uint32_t position = 0;
        const ClickSet* set = nullptr;
        float gain = 1.0f;
        bool active = false;
        bool releasing = false;
        float releaseFrom = 1.0f;
        uint32_t releasePosition = 0;
        uint32_t releaseLength = 1;
    };

    /** The click sets the two voices were playing at the end of a block (read as one record, never torn). */
    struct VoiceSets {
        const ClickSet* voice = nullptr;
        const ClickSet* releasing = nullptr;
    };

    struct AnchorRequest {
        uint64_t sequence = 0;
        int64_t frame = 0;
        double beatPosition = 0.0;
    };

    enum Command : uint32_t { kNone = 0, kStart = 1, kStop = 2 };
    static constexpr int64_t kNoTick = INT64_MAX;

    void applyGrid(const MetronomeGrid& next);
    void applyAnchor(const AnchorRequest& request);
    void beginRun();
    void endRun();
    void startVoice(const ClickSample& sample, const ClickSet* set);
    void emitTick(int64_t tickFrame, double exactFrame, const ClickSet* set);
    void renderSegment(float* out, uint32_t from, uint32_t to, int64_t nextTick);
    static void release(Voice& voice, uint32_t frames);
    float voiceSample(Voice& voice, int64_t frame, int64_t nextTick);
    static const ClickSample& sampleFor(const ClickSet& set, uint32_t level);

    const uint32_t rate_;
    const uint32_t rampFrames_;
    const uint32_t stopFrames_;
    const float maxGainStep_;

    // Host → callback.
    TripleBuffer<MetronomeGrid> gridSlots_;
    TripleBuffer<const ClickSet*> setSlots_;
    TripleBuffer<AnchorRequest> anchorSlots_;
    uint64_t anchorSequence_ = 0;  // host-owned
    std::atomic<uint32_t> command_{kNone};
    std::atomic<int32_t> previewRequest_{-1};

    // Callback → host.
    std::atomic<bool> active_{false};
    std::atomic<bool> running_{false};
    std::atomic<uint64_t> blocks_{0};
    Seqlock<VoiceSets> voiceSets_;
    Seqlock<MetronomeTick> tick_;

    // Callback-owned scheduler state (data-model.md §6).
    MetronomeGrid grid_;
    int64_t framePos_ = 0;
    double lastTickFrame_ = 0.0;
    uint32_t nextTick_ = 0;   // position in the bar of the next tick
    uint64_t emitted_ = 0;    // ticks since start
    uint64_t appliedAnchor_ = 0;
    bool ticking_ = false;
    bool restartAtBeat_ = false;
    Voice voice_;
    Voice releasing_;
    MetronomeTick record_;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_METRONOME_RENDERER_H
