// Oboe input and output streams (research R3): the callbacks only hand frames to the engine's callbacks. An input
// stream the platform closes on its own (device disconnected) is reported through deviceLost(); an output stream is
// reopened with the same settings and render callback so the metronome keeps its position (feature 004, research R1).
// Since feature 010 (research R2) a second input stream can open beside the output: the output callback reads it
// non-blocking and hands the block to the capture callback right before the render callback, so capture and playback
// share one thread and one clock; a reopen of either stream bumps its generation for the engine.
#ifndef TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include <oboe/Oboe.h>

#include "platform/audio_device.h"
#include "capture/input_reservoir.h"

namespace tembra {

class OboeAudioDevice : public AudioDevice {
public:
    OboeAudioDevice();
    ~OboeAudioDevice() override;

    int32_t openInput(uint32_t preferredRate, InputCallback callback, void* context) override;
    int32_t openOutput(uint32_t preferredRate, RenderCallback callback, void* context) override;
    void close() override;
    uint32_t sampleRate() const override;
    bool deviceLost() const override;
    int64_t outputLatencyNanos() const override;
    void refreshOutputLatency() override;
    /** Low-latency performance mode granted by Oboe on the open stream (feature 007). Host threads only. */
    bool realtime() const override;
    /** Oboe's getXRunCount() on the open stream, sampled off the callback (feature 007). Host threads only. */
    uint32_t xrunCount() const override;

    // Feature 010: the duplex input.
    int32_t openInputBeside(uint32_t rate, uint32_t maxChannels, CaptureCallback callback, void* context) override;
    void closeInput() override;
    uint32_t inputChannels() const override;
    int64_t inputLatencyNanos() const override;
    uint32_t inputXruns() const override;
    uint32_t inputGeneration() const override { return inputGeneration_.load(std::memory_order_acquire); }
    uint32_t outputGeneration() const override { return outputGeneration_.load(std::memory_order_acquire); }

private:
    /** Oboe keeps its callbacks as shared pointers; this bridge forwards to the device that owns it. */
    class Bridge : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
    public:
        explicit Bridge(OboeAudioDevice* device) : device_(device) {}
        oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
        void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

    private:
        OboeAudioDevice* device_;
    };

    static constexpr uint32_t kMaxInputFrames = 4096;
    static constexpr uint32_t kMaxInputChannels = 2;
    static constexpr uint32_t kWarmUpBursts = 2;
    static constexpr uint32_t kDefaultCushionFrames = 96;  // the reservoir's cushion when the stream reports no burst

    oboe::Result openInputWithPreset(uint32_t preferredRate, oboe::InputPreset preset);
    oboe::Result openOutputStream(uint32_t preferredRate);
    oboe::Result openDuplexInputLocked();
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames);
    void onStreamClosedByPlatform(oboe::AudioStream* stream);
    void measureLatencyLocked();       // streamLock_ held; control or error thread
    void measureInputLatencyLocked();  // streamLock_ held; control or error thread
    void waitForCallbackLocked();      // streamLock_ held: lets an output callback that is reading the input finish

    std::shared_ptr<Bridge> bridge_;
    // Host threads and Oboe's error thread only, never the data callback. Oboe calls are made outside it where a
    // stream is being stopped, so a disconnect in flight cannot deadlock with close().
    mutable std::mutex streamLock_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::shared_ptr<oboe::AudioStream> retiredStream_;  // a stream Oboe closed; released on the next close()
    uint32_t outputRate_ = 48000;                        // the preferred rate of the open output, for a reopen
    bool closing_ = false;
    InputCallback input_ = nullptr;
    RenderCallback render_ = nullptr;
    void* context_ = nullptr;
    std::atomic<uint32_t> sampleRate_{48000};
    std::atomic<bool> lost_{false};
    std::atomic<int64_t> latencyNanos_{0};  // measured on the control or error thread, read lock-free

    // The duplex input (feature 010). The stream pointer the callback reads is an atomic the host clears before it
    // stops the stream, then waits for the callback to pass; the scratch is allocated once.
    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::shared_ptr<oboe::AudioStream> retiredInputStream_;
    std::atomic<oboe::AudioStream*> inputForCallback_{nullptr};
    std::atomic<bool> inCallback_{false};
    CaptureCallback capture_ = nullptr;
    void* captureContext_ = nullptr;
    uint32_t inputRate_ = 48000;
    uint32_t inputMaxChannels_ = 1;
    std::atomic<uint32_t> inputChannels_{0};
    std::atomic<int64_t> inputLatencyNanos_{0};
    std::atomic<uint32_t> inputGeneration_{0};
    std::atomic<uint32_t> outputGeneration_{0};
    uint32_t warmUpLeft_ = 0;  // callback-owned
    std::vector<float> inputScratch_;
    InputReservoir inputReservoir_{kMaxInputFrames * 2, kMaxInputChannels};  // configured with the stream closed to the callback
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H
