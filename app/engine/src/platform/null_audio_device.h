// AudioDevice for the macOS host: opens succeed, no callback ever fires (the DSP is tested offline).
#ifndef TEMBRA_PLATFORM_NULL_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_NULL_AUDIO_DEVICE_H

#include "platform/audio_device.h"

namespace tembra {

class NullAudioDevice : public AudioDevice {
public:
    int32_t openInput(uint32_t preferredRate, InputCallback callback, void* context) override;
    int32_t openOutput(uint32_t preferredRate, RenderCallback callback, void* context) override;
    void close() override;
    uint32_t sampleRate() const override;
    bool deviceLost() const override;

private:
    uint32_t sampleRate_ = 48000;
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_NULL_AUDIO_DEVICE_H
