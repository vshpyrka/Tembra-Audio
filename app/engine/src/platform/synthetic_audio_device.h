// An input device that generates the test signal (research R16): a plucked-string-like harmonic tone rendered on
// its own real-time thread in 256-frame blocks at 48 kHz, delivered to the input callback exactly as Oboe or
// RemoteIO would. Never opens output.
#ifndef TEMBRA_PLATFORM_SYNTHETIC_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_SYNTHETIC_AUDIO_DEVICE_H

#include <atomic>
#include <cstdint>
#include <thread>
#include <vector>

#include "platform/audio_device.h"
#include "tembra_engine.h"

namespace tembra {

class SyntheticAudioDevice : public AudioDevice {
public:
    explicit SyntheticAudioDevice(const tembra_test_signal& signal);
    ~SyntheticAudioDevice() override;

    int32_t openInput(uint32_t preferredRate, InputCallback callback, void* context) override;
    int32_t openOutput(uint32_t preferredRate, RenderCallback callback, void* context) override;
    void close() override;
    uint32_t sampleRate() const override;
    bool deviceLost() const override;

private:
    static constexpr uint32_t kRate = 48000;
    static constexpr uint32_t kBlockFrames = 256;

    void run();
    void render(float* out, uint32_t frames);

    const tembra_test_signal signal_;
    std::vector<double> amplitudes_;  // per partial, already scaled to the requested peak
    std::vector<double> phases_;
    std::vector<float> block_;
    std::atomic<bool> running_{false};
    std::thread thread_;
    InputCallback callback_ = nullptr;
    void* context_ = nullptr;
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_SYNTHETIC_AUDIO_DEVICE_H
