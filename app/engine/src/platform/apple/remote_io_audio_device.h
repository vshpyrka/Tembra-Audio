// RemoteIO Audio Unit driven from C++ with the Core Audio C API (research R3): input on bus 1 rendered into a
// pre-allocated buffer, output on bus 0 rendered by the engine's callback. The session itself is configured from
// Kotlin (IosAudioSession). Since feature 010 (research R3 of that feature) both buses can be enabled on the one unit:
// openInputBeside() uninitialises the running output unit, enables bus 1, initialises and restarts it, and the render
// callback pulls bus 1 with AudioUnitRender for the same frame count before rendering the output, so capture and
// playback share one callback and one clock (a short output gap at the restart, measured in the verification notes).
#ifndef TEMBRA_PLATFORM_APPLE_REMOTE_IO_AUDIO_DEVICE_H
#define TEMBRA_PLATFORM_APPLE_REMOTE_IO_AUDIO_DEVICE_H

#include <AudioToolbox/AudioToolbox.h>

#include <atomic>
#include <cstdint>
#include <vector>

#include "platform/audio_device.h"

namespace tembra {

class RemoteIoAudioDevice : public AudioDevice {
public:
    RemoteIoAudioDevice() = default;
    ~RemoteIoAudioDevice() override;

    int32_t openInput(uint32_t preferredRate, InputCallback callback, void* context) override;
    int32_t openOutput(uint32_t preferredRate, RenderCallback callback, void* context) override;
    void close() override;
    uint32_t sampleRate() const override;
    bool deviceLost() const override;
    /** The RemoteIO I/O thread is real-time by construction (feature 007). */
    bool realtime() const override { return true; }
    /** The latency the host reported (AVAudioSession.outputLatency + ioBufferDuration, research R7); 0 until then. */
    int64_t outputLatencyNanos() const override;
    void setReportedLatencyNanos(int64_t nanos) override;

    // Feature 010: the duplex input on bus 1 of the running output unit.
    int32_t openInputBeside(uint32_t rate, uint32_t maxChannels, CaptureCallback callback, void* context) override;
    void closeInput() override;
    uint32_t inputChannels() const override { return duplexChannels_.load(); }
    int64_t inputLatencyNanos() const override { return reportedInputLatencyNanos_.load(); }
    void setReportedInputLatencyNanos(int64_t nanos) override { reportedInputLatencyNanos_.store(nanos); }

private:
    static OSStatus inputProc(void* refCon, AudioUnitRenderActionFlags* flags, const AudioTimeStamp* timestamp, UInt32 bus,
                              UInt32 frames, AudioBufferList* ioData);
    static OSStatus renderProc(void* refCon, AudioUnitRenderActionFlags* flags, const AudioTimeStamp* timestamp, UInt32 bus,
                               UInt32 frames, AudioBufferList* ioData);
    bool createUnit();
    double hardwareRate(UInt32 bus, double fallback) const;
    bool configureDuplexLocked(bool enableInput);

    static constexpr UInt32 kInputBus = 1;
    static constexpr UInt32 kOutputBus = 0;
    static constexpr UInt32 kMaxFramesPerSlice = 4096;

    AudioUnit unit_ = nullptr;
    InputCallback input_ = nullptr;
    RenderCallback render_ = nullptr;
    void* context_ = nullptr;
    std::vector<float> inputBuffer_;
    AudioBufferList inputList_{};
    std::vector<float> outputScratch_;  // interleaved stereo rendered by the engine, split into the unit's planar buffers
    std::atomic<uint32_t> sampleRate_{48000};
    std::atomic<int64_t> reportedLatencyNanos_{0};

    // The duplex input (feature 010): planar buffers bus 1 renders into, interleaved into the scratch for the engine.
    CaptureCallback capture_ = nullptr;
    void* captureContext_ = nullptr;
    uint32_t duplexMaxChannels_ = 1;
    std::atomic<uint32_t> duplexChannels_{0};
    std::vector<std::vector<float>> duplexPlanar_;
    std::vector<uint8_t> duplexListStorage_;  // an AudioBufferList with room for two buffers
    std::vector<float> duplexScratch_;
    std::atomic<int64_t> reportedInputLatencyNanos_{0};
};

}  // namespace tembra

#endif  // TEMBRA_PLATFORM_APPLE_REMOTE_IO_AUDIO_DEVICE_H
