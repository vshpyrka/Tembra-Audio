// The process-wide engine behind the C ABI: the audio state machine, the owner of the platform device, the capture
// pipeline (analyzer + analysis thread) and, from the By Ear story on, the tone renderer and sample player.
#ifndef TEMBRA_TUNER_TUNER_ENGINE_H
#define TEMBRA_TUNER_TUNER_ENGINE_H

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>

#include "platform/audio_device.h"
#include "tembra_engine.h"
#include "tuner/sample_player.h"
#include "tuner/tone_generator.h"
#include "tuner/tuner_analyzer.h"
#include "tuner/wav_decoder.h"

#include <vector>

namespace tembra {

/**
 * State rules (contracts/engine-c-abi-v2.md): at most one stream is open; listening refuses while a tone plays and
 * a tone refuses while listening; a device that cannot open leaves the engine idle. Host commands are serialised
 * with a mutex that no audio callback ever takes. The analysis thread (research R4) drains the analyzer, stops
 * capture by itself when nobody reads for `watchdog_seconds` (research R7) and when the device is lost.
 */
class TunerEngine {
public:
    static TunerEngine& instance();

    int32_t startListening(const tembra_tuner_config& config);
    int32_t startTestSignal(const tembra_tuner_config& config, const tembra_test_signal& signal);
    void stopListening();

    int32_t startTone(double frequencyHz, uint32_t durationMs);
    int32_t loadSample(const uint8_t* bytes, uint32_t length);
    int32_t playSample(int32_t handle, bool loop);
    void unloadSample(int32_t handle);
    void stopTone();

    uint32_t state() const { return state_.load(std::memory_order_acquire); }
    void read(tembra_pitch_reading& out) const;

    static void defaultConfig(tembra_tuner_config& out);

#ifdef TEMBRA_BUILD_TESTS
    /** Test hook: replaces the platform device (the engine owns it). Stops any open stream and forgets the last reading. */
    void setDevice(std::unique_ptr<AudioDevice> device);
#endif

private:
    TunerEngine();
    static void onInput(void* context, const float* mono, uint32_t frames);
    static void onRender(void* context, float* interleavedStereo, uint32_t frames);
    void render(float* interleavedStereo, uint32_t frames);
    int32_t ensureOutputOpen();
    void waitForRelease();
    int32_t beginCapture(const tembra_tuner_config& config, AudioDevice& device, bool synthetic);
    void analysisLoop();
    void finishCaptureFromThread();
    void stopListeningLocked();
    void stopToneLocked();

    static constexpr uint32_t kPreferredRate = 48000;
    static constexpr int kIdleSleepMillis = 4;
    static constexpr int kReleaseWaitMillis = 30;
    static constexpr size_t kScratchFrames = 4096;

    std::mutex commands_;
    std::atomic<uint32_t> state_{TEMBRA_AUDIO_IDLE};
    std::unique_ptr<AudioDevice> platformDevice_;
    std::unique_ptr<AudioDevice> syntheticDevice_;
    AudioDevice* activeInput_ = nullptr;
    std::shared_ptr<TunerAnalyzer> analyzer_;
    std::atomic<TunerAnalyzer*> callbackAnalyzer_{nullptr};
    std::thread analysisThread_;
    std::atomic<bool> running_{false};

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
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_TUNER_ENGINE_H
