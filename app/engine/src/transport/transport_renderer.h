// The transport renderer (feature 009; contracts/engine-c-abi-v6.md "Renderer rules"; research R2-R4, R6, R7): the
// callback that plays the arrangement. Host commands arrive through triple-buffered snapshots and atomic words;
// every block loads them, applies a pending seek and play/pause, splits itself at the clip boundaries the tempo map
// places inside it, pops each active clip's frames from its source fifo through its track strip into the master bus,
// adds the anchored metronome voice after the bus, advances the clock and publishes the TransportRecord seqlock.
// No allocation, lock, I/O or tracing here (the file is in TEMBRA_RT_SOURCES); every loop is bounded by the block
// size and the arrangement's fixed capacities.
#ifndef TEMBRA_TRANSPORT_TRANSPORT_RENDERER_H
#define TEMBRA_TRANSPORT_TRANSPORT_RENDERER_H

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "metronome/metronome_renderer.h"
#include "metronome/triple_buffer.h"
#include "mixer/master_bus.h"
#include "mixer/track_strip.h"
#include "timeline/arrangement_snapshot.h"
#include "transport/tempo_map.h"
#include "transport/transport_clock.h"
#include "transport/transport_record.h"
#include "tuner/seqlock.h"

namespace tembra {

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
    void setTracks(const TrackTable& tracks) { trackSlots_.write(tracks); }
    void setTempo(const TempoMap& tempo) { tempoSlots_.write(tempo); }
    void setMaster(float gain) { masterTarget_.store(gain, std::memory_order_release); }

    /** The click voice the transport drives (research R4); set before the callback can see this renderer. */
    void setMetronome(MetronomeRenderer* metronome) { metronome_.store(metronome, std::memory_order_release); }
    void setMetronomeEnabled(bool enabled) { metronomeEnabled_.store(enabled, std::memory_order_release); }

    void play() { command_.store(kPlay, std::memory_order_release); }
    void pause() { command_.store(kPause, std::memory_order_release); }
    uint64_t seek(int64_t frame) { return clock_.requestSeek(frame); }

    /** Drops every voice, command and the position. Host thread, only while no callback can run (the stream is closed). */
    void silence();

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

    // --- Audio thread ----------------------------------------------------------------------------------------------

    /** Renders [frames] interleaved stereo frames, overwriting [interleavedStereo]; larger blocks go in bounded pieces. */
    void render(float* interleavedStereo, uint32_t frames);

private:
    enum Command : uint32_t { kNone = 0, kPlay = 1, kPause = 2 };

    void renderBlock(float* out, uint32_t frames);
    void applyTracks(const TrackTable& table);
    void recomputeClipFrames();
    void requestSources(int64_t position);
    uint32_t renderPlaying(int64_t position, uint32_t frames, float* out);
    uint32_t renderSegment(int64_t position, uint32_t frames, float* out);

    const uint32_t rate_;
    const uint32_t releaseFrames_;

    // Host → callback.
    TripleBuffer<ArrangementSnapshot> arrangementSlots_;
    TripleBuffer<TrackTable> trackSlots_;
    TripleBuffer<TempoMap> tempoSlots_;
    std::atomic<float> masterTarget_{1.0f};
    std::atomic<MetronomeRenderer*> metronome_{nullptr};
    std::atomic<bool> metronomeEnabled_{false};
    std::atomic<uint32_t> command_{kNone};

    // Callback → host.
    std::atomic<bool> playing_{false};
    std::atomic<bool> readersActive_{false};
    std::atomic<uint64_t> blocks_{0};
    std::atomic<uint32_t> starvation_{0};
    std::atomic<uint32_t> clipsActive_{0};
    Seqlock<TransportRecord> record_;

    // Callback-owned.
    TransportClock clock_;
    TempoMap tempo_;
    const ArrangementSnapshot* arrangement_ = nullptr;
    uint64_t framesVersion_ = UINT64_MAX;  // the arrangement version the clip frames were computed for
    uint64_t appliedTracks_ = UINT64_MAX;
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
};

}  // namespace tembra

#endif  // TEMBRA_TRANSPORT_TRANSPORT_RENDERER_H
