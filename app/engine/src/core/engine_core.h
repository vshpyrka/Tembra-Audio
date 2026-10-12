// The process-wide engine behind the C ABI: the audio state machine, the owner of the platform device, the capture
// pipeline (analyzer + analysis thread), the tone renderer, the sample player and the metronome (feature 004,
// research R1: the owner moved here from tuner/tuner_engine so the tuner does not own the metronome).
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

#include "core/audio_health.h"
#include "metronome/click_set.h"
#include "metronome/metronome_grid.h"
#include "metronome/metronome_renderer.h"
#include "platform/audio_device.h"
#include "tembra_engine.h"
#include "tuner/sample_player.h"
#include "tuner/tone_generator.h"
#include "tuner/tuner_analyzer.h"
#include "tuner/wav_decoder.h"

namespace tembra {

/**
 * State rules (contracts/engine-c-abi-v2.md, engine-c-abi-v3.md, data-model.md §6): at most one stream is open;
 * LISTENING, TONE and METRONOME exclude each other; a device that cannot open leaves the engine idle. Host commands
 * are serialised with a mutex that no audio callback ever takes. The analysis thread (research R4) drains the
 * analyzer, stops capture by itself when nobody reads for `watchdog_seconds` (research R7) and when the device is lost.
 * The metronome ignores deviceLost(): the platform device reopens the output itself (research R1).
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

    uint32_t state() const { return state_.load(std::memory_order_acquire); }
    void read(tembra_pitch_reading& out) const;

    /** Audio-thread health (feature 007): false while no stream is open. Host threads only. */
    bool readAudioHealth(tembra_audio_health& out);

    static void defaultConfig(tembra_tuner_config& out);

#ifdef TEMBRA_BUILD_TESTS
    /** Test hook: replaces the platform device (the engine owns it). Stops any open stream and forgets the last reading and the noise gate. */
    void setDevice(std::unique_ptr<AudioDevice> device);
#endif

private:
    EngineCore();
    static void onInput(void* context, const float* mono, uint32_t frames);
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
};

}  // namespace tembra

#endif  // TEMBRA_CORE_ENGINE_CORE_H
