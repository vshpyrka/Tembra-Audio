#include "platform/null_audio_device.h"

#include "tembra_engine.h"

namespace tembra {

int32_t NullAudioDevice::openInput(uint32_t preferredRate, InputCallback, void*) {
    sampleRate_ = preferredRate;
    return TEMBRA_OK;
}

int32_t NullAudioDevice::openOutput(uint32_t preferredRate, RenderCallback, void*) {
    sampleRate_ = preferredRate;
    return TEMBRA_OK;
}

void NullAudioDevice::close() {}

uint32_t NullAudioDevice::sampleRate() const { return sampleRate_; }

bool NullAudioDevice::deviceLost() const { return false; }

}  // namespace tembra
