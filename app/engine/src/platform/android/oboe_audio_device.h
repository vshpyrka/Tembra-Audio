// Oboe input and output streams (research R3): the callbacks only hand frames to the engine's callbacks. An input
// stream the platform closes on its own (device disconnected) is reported through deviceLost(); an output stream is
// reopened with the same settings and render callback so the metronome keeps its position (feature 004, research R1).
#ifndef TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>

#include <oboe/Oboe.h>

#include "platform/audio_device.h"

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

    oboe::Result openInputWithPreset(uint32_t preferredRate, oboe::InputPreset preset);
    oboe::Result openOutputStream(uint32_t preferredRate);
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames);
    void onStreamClosedByPlatform(oboe::AudioStream* stream);

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
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H
