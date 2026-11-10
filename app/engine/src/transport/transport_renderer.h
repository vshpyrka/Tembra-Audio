// The transport renderer (feature 009; contracts/engine-c-abi-v6.md "Renderer rules"; research R2-R4, R6, R7): the
// callback that plays the arrangement. Host commands arrive through triple-buffered snapshots and atomic words;
// every block loads them, applies a pending seek and play/pause, splits itself at the clip boundaries the tempo map
// places inside it, pops each active clip's frames from its source fifo through its track strip into the master bus,
// adds the anchored metronome voice after the bus, advances the clock and publishes the TransportRecord seqlock.
// No allocation, lock, I/O or tracing here (the file is in TEMBRA_RT_SOURCES); every loop is bounded by the block
// size and the arrangement's fixed capacities. Since feature 010 (contracts/engine-c-abi-v7.md "Renderer rules") the
// block also carries the input the device read in the same callback: the renderer measures it for the meter, runs
// the count-in pre-roll (the clock before the start, no clip, the click forced on), begins a take sample-accurately
// at the start, mutes the armed track's clips under the take, mixes the monitor strip after the clips and pushes the
// kept frames into the capture source's fifo.
#ifndef TEMBRA_TRANSPORT_TRANSPORT_RENDERER_H
#define TEMBRA_TRANSPORT_TRANSPORT_RENDERER_H

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "capture/capture_source.h"
#include "metronome/metronome_renderer.h"
#include "metronome/triple_buffer.h"
#include "mixer/master_bus.h"
#include "mixer/mix_meter.h"
#include "mixer/mix_params.h"
#include "mixer/mix_record.h"
#include "mixer/monitor_strip.h"
#include "mixer/track_strip.h"
#include "timeline/arrangement_snapshot.h"
#include "transport/tempo_map.h"
#include "transport/transport_clock.h"
#include "transport/transport_record.h"
#include "tuner/seqlock.h"

namespace tembra {

/**
 * The input block of one callback (feature 010, research R2, R3): what the device read right before it pulls the
 * output. [interleaved] is null when no input is open; [frames] may differ from the output block only on a device
 * mismatch (the renderer uses the smaller count). The generations tell the callback that the platform reopened a
 * stream on its own (an output reopen clears monitoring, an input reopen ends a take with INPUT_LOST).
 */
struct InputBlock {
    const float* interleaved = nullptr;
    uint32_t channels = 0;
    uint32_t frames = 0;
    uint32_t shortFrames = 0;
    uint32_t inputGeneration = 0;
    uint32_t outputGeneration = 0;
};

/**
 * Host API (one host thread at a time, serialised by EngineCore's command mutex): the setters publish and never
 * block; play/pause are one command word (the last wins inside a block, like the metronome's); seek returns the
 * request's sequence so the host can tell when the callback has applied it. Priming is never done here: the host
 * primes synchronously while readersActive() is false, and otherwise the callback parks a source it cannot read and
 * the DiskStreamer serves the request (see ClipSource). Pause renders a 5 ms release tail without moving the clock.
 */
class TransportRenderer {
public:
    static constexpr size_t kMaxBlockFrames = 4096;
    static constexpr double kPauseReleaseSeconds = 0.005;

    explicit TransportRenderer(uint32_t rate);

    TransportRenderer(const TransportRenderer&) = delete;
    TransportRenderer& operator=(const TransportRenderer&) = delete;

    // --- Host -----------------------------------------------------------------------------------------------------

    void setArrangement(const ArrangementSnapshot& snapshot) { arrangementSlots_.write(snapshot); }
    /** The revision-6 table seeds the lock-free parameters (gain and audibility; every pan is kept): feature 012, R4. */
    void setTracks(const TrackTable& tracks) { mixParams_.seed(tracks); }
    void setTempo(const TempoMap& tempo) { tempoSlots_.write(tempo); }
    void setMaster(float gain) { masterTarget_.store(gain, std::memory_order_release); }

    // --- The mixer (feature 012, contracts/engine-c-abi-v8.md): lock-free from any host thread -----------------------

    /** Per-track gain, pan and audibility plus the metering switch; the callback applies a change at its next block. */
    MixParams& mixParams() { return mixParams_; }
    const MixParams& mixParams() const { return mixParams_; }

    /** Clears strip [index]'s clip latch (0..63) or the master's (TEMBRA_MIXER_MASTER_INDEX) at the next publish. */
    void requestClipReset(uint32_t index) { meter_.requestReset(index); }

    /** The click voice the transport drives (research R4); set before the callback can see this renderer. */
    void setMetronome(MetronomeRenderer* metronome) { metronome_.store(metronome, std::memory_order_release); }
    void setMetronomeEnabled(bool enabled) { metronomeEnabled_.store(enabled, std::memory_order_release); }

    void play() { command_.store(kPlay, std::memory_order_release); }
    void pause() { command_.store(kPause, std::memory_order_release); }
    uint64_t seek(int64_t frame) { return clock_.requestSeek(frame); }

    /**
     * Drops every voice, command, the position and the session's health counters. Host thread, only while no callback
     * can run (the stream is closed, or the transport is being opened).
     */
    void silence();

    // --- Capture (feature 010) ----------------------------------------------------------------------------------------

    /** The armed input, prepared by the host; null disarms (a running take was ended by the host before). */
    void setCapture(CaptureSource* source) { capture_.store(source, std::memory_order_release); }

    /** The armed track whose clips fall silent under a take and whose gain the monitor follows; -1 none. */
    void setArmedTrack(int32_t index) { armedTrack_.store(index, std::memory_order_release); }

    /** Monitoring on or off (a 5 ms fade); the callback clears it itself after an output reopen. */
    void setMonitor(bool enabled) { monitorRequested_.store(enabled ? 1u : 0u, std::memory_order_release); }

    /** What the callback applied last: false after a reopen cleared it. */
    bool monitoring() const { return monitoring_.load(std::memory_order_acquire); }

    /**
     * Starts a take. Stopped with [preRollFrames] > 0: the clock moves to [startFrame] - preRollFrames, the pre-roll
     * runs and capture begins at [startFrame]. Stopped without a pre-roll, or playing: capture begins at the next block
     * (the host primed [startFrame] as it does for play). The host called CaptureSource::beginTake() before.
     */
    void requestRecordStart(int64_t startFrame, int64_t preRollFrames);

    /** Pre-roll only: pauses and restores the position (the host deletes the file). */
    void requestRecordCancel() { recordCommand_.store(kRecordCancel, std::memory_order_seq_cst); }

    /** Ends capture at the next block boundary; playback continues. */
    void requestRecordStop() { recordCommand_.store(kRecordStop, std::memory_order_seq_cst); }

    /** True while the callback is inside render(): the host waits on it before it drains the fifo or frees the source. */
    bool rendering() const { return rendering_.load(std::memory_order_seq_cst); }

    // --- Telemetry (any thread) ----------------------------------------------------------------------------------

    TransportRecord record() const { return record_.load(); }
    uint64_t blocksRendered() const { return blocks_.load(std::memory_order_acquire); }
    bool playing() const { return playing_.load(std::memory_order_acquire); }
    /** True while the callback may pop a clip source (playing, or rendering the release tail of a pause). */
    bool readersActive() const { return readersActive_.load(std::memory_order_acquire); }
    const std::atomic<bool>& readersActiveFlag() const { return readersActive_; }
    bool commandPending() const { return command_.load(std::memory_order_acquire) != kNone; }
    uint64_t pendingSeekSequence() const { return clock_.pendingSeekSequence(); }
    uint64_t appliedSeekSequence() const { return clock_.appliedSeekSequence(); }
    uint32_t starvation() const { return starvation_.load(std::memory_order_relaxed); }
    uint32_t clipsActive() const { return clipsActive_.load(std::memory_order_relaxed); }
    uint32_t sampleRate() const { return rate_; }
    /** The meter record of the last block published while metering was on, or the zeroed one published when it turned off. */
    MixRecord mixRecord() const { return mixRecord_.load(); }
    /** Blocks in which the master clamp engaged since the session started (health counter master_clips; counted whether or not metering is on). */
    uint32_t masterClips() const { return masterClips_.load(std::memory_order_relaxed); }
    /** The meter's measurement calls so far (the test that metering off measures nothing). */
    uint64_t meterMeasureCalls() const { return meter_.measureCalls(); }

    // --- Audio thread ----------------------------------------------------------------------------------------------

    /** Renders [frames] interleaved stereo frames, overwriting [interleavedStereo]; larger blocks go in bounded pieces. */
    void render(float* interleavedStereo, uint32_t frames) { render(interleavedStereo, frames, InputBlock{}); }

    /** The duplex form: [input] is the block the device read for this callback (feature 010). */
    void render(float* interleavedStereo, uint32_t frames, const InputBlock& input);

private:
    enum Command : uint32_t { kNone = 0, kPlay = 1, kPause = 2 };
    enum RecordCommand : uint32_t { kRecordNone = 0, kRecordStart = 1, kRecordCancel = 2, kRecordStop = 3 };
    enum class CaptureState { Idle, Armed, PreRoll, Recording };
    static constexpr uint32_t kNoSplit = UINT32_MAX;

    struct RecordRequest {
        uint64_t sequence = 0;
        int64_t start = 0;
        int64_t preRoll = 0;
    };

    void renderBlock(float* out, uint32_t frames, const InputBlock& input);
    void applyMix(uint32_t trackCount);
    void finishBus(float* out, uint32_t frames);
    void recomputeClipFrames();
    void requestSources(int64_t position);
    uint32_t renderPlaying(int64_t position, uint32_t frames, float* out, const InputBlock& input, uint32_t inputOffset);
    uint32_t renderSegment(int64_t position, uint32_t frames, float* out, const float* inputSlice, uint32_t inputChannels, bool clips);
    void renderMonitor(uint32_t frames, const float* inputSlice, uint32_t inputChannels);

    const uint32_t rate_;
    const uint32_t releaseFrames_;

    // Host → callback.
    TripleBuffer<ArrangementSnapshot> arrangementSlots_;
    TripleBuffer<TempoMap> tempoSlots_;
    MixParams mixParams_;  // feature 012: the lock-free gain, pan, audibility and metering switch (research R4)
    std::atomic<float> masterTarget_{1.0f};
    std::atomic<MetronomeRenderer*> metronome_{nullptr};
    std::atomic<bool> metronomeEnabled_{false};
    std::atomic<uint32_t> command_{kNone};
    std::atomic<CaptureSource*> capture_{nullptr};
    std::atomic<int32_t> armedTrack_{-1};
    std::atomic<uint32_t> monitorRequested_{0};  // written by the host, cleared by the callback after an output reopen
    std::atomic<uint32_t> recordCommand_{kRecordNone};
    TripleBuffer<RecordRequest> recordRequests_;
    uint64_t recordSequence_ = 0;  // host-owned

    // Callback → host.
    std::atomic<bool> monitoring_{false};
    std::atomic<bool> rendering_{false};
    std::atomic<bool> playing_{false};
    std::atomic<bool> readersActive_{false};
    std::atomic<uint64_t> blocks_{0};
    std::atomic<uint32_t> starvation_{0};
    std::atomic<uint32_t> clipsActive_{0};
    std::atomic<uint32_t> masterClips_{0};
    Seqlock<TransportRecord> record_;
    Seqlock<MixRecord> mixRecord_;  // feature 012: the meters, one record per block while metering is on (research R6)

    // Callback-owned.
    TransportClock clock_;
    TempoMap tempo_;
    const ArrangementSnapshot* arrangement_ = nullptr;
    uint64_t framesVersion_ = UINT64_MAX;  // the arrangement version the clip frames were computed for
    uint64_t appliedMix_ = UINT64_MAX;     // the MixParams version the strips' targets were set from
    uint32_t appliedMixTracks_ = UINT32_MAX;
    MixMeter meter_;
    bool meteringOn_ = false;
    bool meterPublished_ = false;  // a record went out since metering was last on: one zeroed record follows the switch off
    bool clampThisBlock_ = false;
    float masterApplied_ = 1.0f;
    bool clickRunning_ = false;
    bool releasing_ = false;
    uint32_t releaseRemaining_ = 0;
    std::array<TrackStrip, TEMBRA_MAX_TRACKS> strips_;
    std::array<bool, TEMBRA_MAX_TRACKS> stripSet_{};
    MasterBus bus_;
    std::vector<float> clipScratch_;
    std::vector<float> stripScratch_;
    std::vector<float> clickScratch_;
    std::vector<int64_t> clipStart_;
    std::vector<int64_t> clipEnd_;
    std::vector<int32_t> activeClip_;

    // Capture (feature 010), callback-owned.
    CaptureState captureState_ = CaptureState::Idle;
    uint64_t appliedRecordSequence_ = 0;
    bool preRolling_ = false;
    int64_t preRollStart_ = 0;        // the transport frame capture begins at
    int64_t preRollReturnFrame_ = 0;  // the position a cancel restores
    int64_t takeStart_ = 0;
    int64_t muteFrom_ = INT64_MAX;    // the armed track's clips are skipped from this frame on
    bool generationsSeen_ = false;
    uint32_t seenInputGeneration_ = 0;
    uint32_t seenOutputGeneration_ = 0;
    MonitorStrip monitor_;
    int32_t monitorTrack_ = -1;
    std::vector<float> monitorScratch_;
};

}  // namespace tembra

#endif  // TEMBRA_TRANSPORT_TRANSPORT_RENDERER_H
