// Test-only AudioDevice that drives the engine's callbacks synchronously from the test thread.
#ifndef TEMBRA_TESTS_FAKE_AUDIO_DEVICE_H
#define TEMBRA_TESTS_FAKE_AUDIO_DEVICE_H

#include <cstdint>
#include <vector>

#include "platform/audio_device.h"
#include "tembra_engine.h"

namespace tembra {

class FakeAudioDevice : public AudioDevice {
public:
    bool failOpen = false;
    uint32_t requestedRate = 0;
    uint32_t reportedRate = 48000;
    bool closed = false;
    bool lost = false;
    bool inputOpen = false;
    bool outputOpen = false;

    int32_t openInput(uint32_t preferredRate, InputCallback callback, void* context) override {
        requestedRate = preferredRate;
        if (failOpen) return TEMBRA_ERR_DEVICE;
        inputCallback_ = callback;
        inputContext_ = context;
        inputOpen = true;
        closed = false;
        return TEMBRA_OK;
    }

    int32_t openOutput(uint32_t preferredRate, RenderCallback callback, void* context) override {
        requestedRate = preferredRate;
        if (failOpen) return TEMBRA_ERR_DEVICE;
        renderCallback_ = callback;
        renderContext_ = context;
        outputOpen = true;
        closed = false;
        return TEMBRA_OK;
    }

    void close() override {
        inputOpen = false;
        outputOpen = false;
        inputCallback_ = nullptr;
        renderCallback_ = nullptr;
        closed = true;
    }

    uint32_t sampleRate() const override { return reportedRate; }
    bool deviceLost() const override { return lost; }

    /** Delivers [frames] mono frames to the input callback, as a platform callback would. */
    void feedInput(const float* mono, uint32_t frames) {
        if (inputCallback_ != nullptr) inputCallback_(inputContext_, mono, frames);
    }

    /** Pulls [frames] interleaved stereo frames from the render callback into [stereo] (2 × frames floats). */
    void pullOutput(float* stereo, uint32_t frames) {
        for (uint32_t i = 0; i < frames * 2; ++i) stereo[i] = 0.0f;
        if (renderCallback_ != nullptr) renderCallback_(renderContext_, stereo, frames);
    }

private:
    InputCallback inputCallback_ = nullptr;
    void* inputContext_ = nullptr;
    RenderCallback renderCallback_ = nullptr;
    void* renderContext_ = nullptr;
};

}  // namespace tembra

#endif  // TEMBRA_TESTS_FAKE_AUDIO_DEVICE_H
