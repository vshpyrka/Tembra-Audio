// Oboe input and output streams (research R3): the callbacks only hand frames to the engine's callbacks; a stream
// the platform closes on its own (device disconnected) is reported through deviceLost().
#ifndef TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H

#include <atomic>
#include <cstdint>
#include <memory>

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
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames);

    std::shared_ptr<Bridge> bridge_;
    std::shared_ptr<oboe::AudioStream> stream_;
    InputCallback input_ = nullptr;
    RenderCallback render_ = nullptr;
    void* context_ = nullptr;
    std::atomic<uint32_t> sampleRate_{48000};
    std::atomic<bool> lost_{false};
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_ANDROID_OBOE_AUDIO_DEVICE_H
