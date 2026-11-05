// The process-wide engine behind the C ABI: the audio state machine, the owner of the platform device, the capture
// pipeline (analyzer + analysis thread), the tone renderer, the sample player, the metronome (feature 004,
// research R1: the owner moved here from tuner/tuner_engine so the tuner does not own the metronome) and, since
// feature 009 (research R2), the project transport: the transport renderer, the disk streamer and the clip sources.
#ifndef TEMBRA_CORE_ENGINE_CORE_H
#define TEMBRA_CORE_ENGINE_CORE_H

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "capture/capture_source.h"
#include "capture/peak_ring.h"
#include "capture/take_recorder.h"
#include "core/audio_health.h"
#include "metronome/click_set.h"
#include "metronome/metronome_grid.h"
#include "metronome/metronome_renderer.h"
#include "platform/audio_device.h"
#include "tembra_engine.h"
#include "timeline/arrangement_snapshot.h"
#include "timeline/clip_source.h"
#include "timeline/disk_streamer.h"
#include "transport/tempo_map.h"
#include "transport/transport_renderer.h"
#include "tuner/sample_player.h"
#include "tuner/tone_generator.h"
#include "tuner/tuner_analyzer.h"
#include "tuner/wav_decoder.h"

namespace tembra {

/**
 * State rules (contracts/engine-c-abi-v2.md, engine-c-abi-v3.md, engine-c-abi-v6.md, data-model.md §6): at most one
 * stream is open; LISTENING, TONE, METRONOME and TRANSPORT exclude each other; a device that cannot open leaves the
 * engine idle. Host commands are serialised with a mutex that no audio callback ever takes. The analysis thread
 * (research R4) drains the analyzer, stops capture by itself when nobody reads for `watchdog_seconds` (research R7)
 * and when the device is lost. The metronome ignores deviceLost(): the platform device reopens the output itself
 * (research R1). The transport (feature 009) owns a TransportRenderer built at the stream rate, a DiskStreamer
 * thread that primes and refills the clip sources, and the clip-source table whose handles the arrangement names;
 * a source an arrangement references is never closed, and a closed one is freed two blocks after its retirement.
 */
class EngineCore {
public:
    static EngineCore& instance();

    int32_t startListening(const tembra_tuner_config& config);
    int32_t startTestSignal(const tembra_tuner_config& config, const tembra_test_signal& signal);
    void stopListening();

    /** The noise gate (feature 006, research R4): kept for the engine's life and handed to a live analyzer at once. */
    int32_t setGate(bool enabled, float openDb);

    int32_t startTone(double frequencyHz, uint32_t durationMs);
    int32_t loadSample(const uint8_t* bytes, uint32_t length);
    int32_t playSample(int32_t handle, bool loop);
    void unloadSample(int32_t handle);
    void stopTone();

    int32_t setGrid(const tembra_metronome_grid& grid);
    int32_t selectSound(uint32_t sound);
    int32_t loadCustomSound(const char* path, tembra_custom_sound_info* info);
    void unloadCustomSound();
    int32_t startMetronome();
    void stopMetronome();
    int32_t previewClick(uint32_t level);
    void setOutputLatencyNs(int64_t nanos);
    void readTick(tembra_metronome_tick& out);

    // --- The project transport (feature 009, contracts/engine-c-abi-v6.md) ----------------------------------------

    int32_t openTransport(const tembra_transport_config& config);
    void closeTransport();
    int32_t setTransportTempo(uint32_t bpm, uint32_t beatsPerBar, uint32_t beatUnit);
    int32_t setTransportArrangement(const tembra_arrangement& arrangement);
    int32_t setTransportTrack(uint32_t index, float gain, bool audible);
    int32_t setTransportMaster(float gain);
    int32_t setTransportMetronome(bool enabled);
    int32_t transportPlay();
    int32_t transportPause();
    int32_t transportSeekBeats(double beats);
    /** Lock-free; zeros unless TRANSPORT. audible_seconds = position + elapsed since the block - the output latency. */
    void readTransport(tembra_transport_snapshot& out);
    /** Decodes on the calling thread (outside the command mutex) at the stream rate, or 48 kHz while no stream is open. */
    int32_t openClipSource(const char* path, tembra_clip_info& info);
    /** Silently refused while the current arrangement references the handle. */
    void closeClipSource(int32_t handle);

    // --- Capture, takes and monitoring (feature 010, contracts/engine-c-abi-v7.md) -------------------------------------

    int32_t arm(const tembra_capture_config& config);
    void disarm();
    int32_t setMonitor(bool enabled, uint32_t trackIndex);
    void setInputLatencyNs(int64_t nanos);
    int32_t recordStart(const tembra_record_config& config);
    int32_t recordCancel();
    int32_t recordStop(tembra_take_info& out);
    /** Lock-free; zeros unless armed. */
    void readCapture(tembra_capture_snapshot& out);
    void resetClip();
    uint32_t readPeaks(uint64_t fromIndex, tembra_peak_bucket* out, uint32_t maxCount, uint64_t* nextIndex);

    // --- The mixer (feature 012, contracts/engine-c-abi-v8.md): lock-free, never under commands_ ---------------------

    /** The setters store atomics on the renderer and return (any host thread); ERR_STATE unless TRANSPORT. */
    int32_t setMixTrack(uint32_t index, float gain, float pan, bool audible);
    int32_t setMixPan(uint32_t index, float pan);
    int32_t setMixMaster(float gain);
    int32_t setMetering(bool enabled);
    /** Lock-free seqlock copy; zeros unless TRANSPORT. */
    void readMix(tembra_mix_snapshot& out);
    /** 0..63 a strip, TEMBRA_MIXER_MASTER_INDEX the master; applied at the callback's next block. */
    int32_t resetMixClip(uint32_t index);

    uint32_t state() const { return state_.load(std::memory_order_acquire); }
    void read(tembra_pitch_reading& out) const;

    /** Audio-thread health (feature 007): false while no stream is open. Host threads only. */
    bool readAudioHealth(tembra_audio_health& out);

    static void defaultConfig(tembra_tuner_config& out);

#ifdef TEMBRA_BUILD_TESTS
    /** Test hook: replaces the platform device (the engine owns it). Stops any open stream and forgets the last reading, the noise gate and the clip sources. */
    void setDevice(std::unique_ptr<AudioDevice> device);
    /** Test seam (feature 012, the main-thread audit): holds the command mutex for as long as the lock lives. */
    std::unique_lock<std::mutex> holdCommandsForTest();
    /** Test seam (feature 012): the renderer the telemetry reads, to inspect the seeded parameters; null before the first open. */
    TransportRenderer* transportRendererForTest();
#endif

private:
    EngineCore();
    static void onInput(void* context, const float* mono, uint32_t frames);
    static void onCapture(void* context, const float* interleaved, uint32_t channels, uint32_t frames, uint32_t shortFrames);
    static void onRender(void* context, float* interleavedStereo, uint32_t frames);
    void render(float* interleavedStereo, uint32_t frames);
    int32_t ensureOutputOpen();
    void onOutputOpened(uint32_t rate);
    void closeOutputLocked();
    void waitForRelease();
    void stopMetronomeLocked();
    const ClickSet* selectedSetLocked() const;
    void retireLocked(std::unique_ptr<ClickSet> set);
    void sweepRetiredLocked(bool outputClosed);
    int64_t outputLatencyNanos() const;
    int32_t beginCapture(const tembra_tuner_config& config, AudioDevice& device, bool synthetic);
    void analysisLoop();
    void finishCaptureFromThread();
    void stopListeningLocked();
    void stopToneLocked();
    void ensureTransportLocked(uint32_t rate);
    void closeTransportLocked();
    void retireSourceLocked(std::unique_ptr<ClipSource> source);
    void sweepRetiredSourcesLocked(bool outputClosed);
    std::vector<ClipSource*> sourceTableLocked() const;
    std::vector<ClipSource*> openSourcesLocked() const;
    int64_t transportPositionLocked() const;
    void disarmLocked();
    void determineAlignmentLocked();
    int64_t inputLatencyNanosLocked() const;
    void waitForCallback();
    void endTakeLocked(uint32_t stopReason);
    /** The renderer the mixer's lock-free calls target while TRANSPORT; null otherwise (feature 012). */
    TransportRenderer* mixerTarget() const;

    static constexpr uint32_t kPreferredRate = 48000;
    static constexpr int kIdleSleepMillis = 4;
    static constexpr int kReleaseWaitMillis = 30;
    static constexpr size_t kScratchFrames = 4096;
    static constexpr size_t kBuiltInSounds = 5;

    std::mutex commands_;
    std::atomic<uint32_t> state_{TEMBRA_AUDIO_IDLE};
    // The callback times itself into these atomics (feature 007); callbackRate_ is set before the stream opens.
    AudioHealth health_;
    std::atomic<uint32_t> callbackRate_{kPreferredRate};
    std::unique_ptr<AudioDevice> platformDevice_;
    std::unique_ptr<AudioDevice> syntheticDevice_;
    AudioDevice* activeInput_ = nullptr;
    std::shared_ptr<TunerAnalyzer> analyzer_;
    std::atomic<TunerAnalyzer*> callbackAnalyzer_{nullptr};
    std::thread analysisThread_;
    std::atomic<bool> running_{false};
    float gateDb_ = TunerAnalyzer::kGateOff;  // guarded by commands_

    // Output side: created when the output opens, at the stream's rate; the callback only reads the atomics.
    bool outputOpen_ = false;
    std::unique_ptr<ToneGenerator> generator_;
    std::unique_ptr<SamplePlayer> player_;
    std::atomic<ToneGenerator*> callbackGenerator_{nullptr};
    std::atomic<SamplePlayer*> callbackPlayer_{nullptr};
    std::vector<float> scratch_;
    std::mutex samples_;  // host threads only; the callback reads the sample through the player's atomic pointer
    std::vector<std::unique_ptr<DecodedSample>> sampleTable_;
    int32_t playingHandle_ = -1;

    // Metronome (research R1-R5, R7). The renderer is created at the first open and replaced only when a later open
    // reports another rate; a replaced renderer is kept (not freed) because tembra_metronome_read may be reading it.
    std::unique_ptr<MetronomeRenderer> metronome_;
    std::vector<std::unique_ptr<MetronomeRenderer>> retiredRenderers_;
    std::atomic<MetronomeRenderer*> callbackMetronome_{nullptr};   // non-null while the output stream is open
    std::atomic<MetronomeRenderer*> telemetryMetronome_{nullptr};  // the renderer tembra_metronome_read reads
    std::array<ClickSet, kBuiltInSounds> builtIns_;                  // rendered at builtInRate_; rewritten only while closed
    uint32_t builtInRate_ = 0;
    std::unique_ptr<ClickSet> custom_;
    tembra_custom_sound_info customInfo_{};
    std::string customPath_;
    uint32_t customRate_ = 0;
    struct RetiredSet {
        std::unique_ptr<ClickSet> set;
        uint64_t retiredAtBlock;
    };
    std::vector<RetiredSet> retiredSets_;  // custom sets the callback may still be playing
    MetronomeGrid grid_;
    uint32_t selectedSound_ = TEMBRA_SOUND_CLASSIC_WOOD;
    std::atomic<int64_t> reportedLatencyNs_{0};
    // The platform device as tembra_metronome_read sees it: only its lock-free outputLatencyNanos() and deviceLost()
    // are called through it. It changes only in the test hook setDevice().
    std::atomic<AudioDevice*> telemetryDevice_{nullptr};

    // Transport (feature 009, research R2-R7). The renderer is built at the stream rate on open and replaced only
    // when a later open reports another rate (the replaced one is kept, like the metronome's, for a concurrent
    // tembra_transport_read). The streamer thread lives from open to close. Clip sources are indexed by handle.
    std::unique_ptr<TransportRenderer> transport_;
    std::vector<std::unique_ptr<TransportRenderer>> retiredTransports_;
    std::atomic<TransportRenderer*> callbackTransport_{nullptr};   // non-null only while TRANSPORT
    std::atomic<TransportRenderer*> telemetryTransport_{nullptr};  // the renderer tembra_transport_read reads
    std::unique_ptr<DiskStreamer> streamer_;
    std::vector<std::unique_ptr<ClipSource>> clipSources_;  // guarded by commands_; handle = index
    struct RetiredSource {
        std::unique_ptr<ClipSource> source;
        uint64_t retiredAtBlock;
    };
    std::vector<RetiredSource> retiredSources_;  // sources a stale snapshot on the callback may still pop
    std::unique_ptr<ArrangementSnapshot> hostArrangement_;  // the published arrangement: reference checks, prime targets
    TrackTable hostTracks_;
    /** The arrangement's track count for the revision-7 fast path's index check, readable without commands_ (feature 012). */
    std::atomic<uint32_t> mixTrackCount_{0};
    TempoMap hostTempo_;
    uint64_t arrangementVersion_ = 0;
    uint32_t transportRate_ = kPreferredRate;
    bool hostPlaying_ = false;
    int64_t lastSeekFrame_ = 0;
    uint64_t lastSeekSequence_ = 0;

    // Capture (feature 010, research R1-R12). The source and the peak ring live for the transport session; the recorder
    // thread for one take. The callback-owned input block is set by onCapture and consumed by the render of the same
    // device callback. armed_ and monitorTrack_ are host state under commands_; the latencies are atomics the
    // lock-free reader combines.
    std::unique_ptr<CaptureSource> capture_;
    PeakRing peakRing_;
    std::unique_ptr<TakeRecorder> recorder_;
    std::atomic<bool> armed_{false};  // host-written under commands_; the lock-free capture reader checks it
    bool takeRunning_ = false;
    bool takeInfoPending_ = false;
    tembra_take_info pendingTakeInfo_{};
    std::string takePath_;
    uint32_t takeTrack_ = 0;
    std::atomic<int64_t> reportedInputLatencyNs_{0};
    std::atomic<int64_t> inputLatencyNs_{0};
    std::atomic<int64_t> alignmentOutputLatencyNs_{0};
    const float* inputBlock_ = nullptr;  // callback-owned
    uint32_t inputBlockChannels_ = 0;
    uint32_t inputBlockFrames_ = 0;
    uint32_t inputBlockShort_ = 0;
};

}  // namespace tembra

#endif  // TEMBRA_CORE_ENGINE_CORE_H
