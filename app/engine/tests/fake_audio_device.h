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
    int64_t latencyNanos = 0;  // what a measurement would find; outputLatencyNanos() returns the last refresh
    int reopenCount = 0;
    bool reopenFails = false;
    // Feature 010: the input opened beside the output (openInputBeside), fed one block per pullOutput.
    bool failInputOpen = false;
    int64_t inputLatencyNs = 0;         // what inputLatencyNanos() reports
    uint32_t inputChannelsAvailable = 1;  // what the "hardware" offers; the open clamps to maxChannels
    uint32_t inputRequestedRate = 0;
    uint32_t inputRequestedMaxChannels = 0;
    int inputOpenCount = 0;
    int inputCloseCount = 0;
    bool duplexInputOpen = false;
    uint32_t inputXrunsReported = 0;

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
        storedRender_ = callback;
        storedContext_ = context;
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
    int64_t outputLatencyNanos() const override { return cachedLatency_; }
    void refreshOutputLatency() override { cachedLatency_ = outputOpen ? latencyNanos : 0; }

    /**
     * Simulates OboeAudioDevice after a route change (research R1): the platform closes the output, then the device
     * reopens it with the settings and render callback it stored at openOutput() and measures the new latency; frames
     * pulled in between are silence the engine never sees. With [reopenFails] the stream stays closed and deviceLost()
     * turns true. Returns false when no output was open.
     */
    bool reopenOutput(uint32_t framesLostDuringReopen = 0) {
        if (!outputOpen) return false;
        renderCallback_ = nullptr;
        std::vector<float> gap(2u * framesLostDuringReopen);
        if (framesLostDuringReopen > 0) pullOutput(gap.data(), framesLostDuringReopen);
        ++reopenCount;
        if (reopenFails) {
            outputOpen = false;
            lost = true;
            return true;
        }
        renderCallback_ = storedRender_;
        renderContext_ = storedContext_;
        refreshOutputLatency();
        ++outputGeneration_;  // the duplex input, if open, is untouched by an output reopen (research R2)
        return true;
    }

    /** Delivers [frames] mono frames to the input callback, as a platform callback would. */
    void feedInput(const float* mono, uint32_t frames) {
        if (inputCallback_ != nullptr) inputCallback_(inputContext_, mono, frames);
    }

    /**
     * Pulls [frames] interleaved stereo frames from the render callback into [stereo] (2 × frames floats). With the
     * duplex input open, the block last given to feedInputWhileRendering() reaches the capture callback first, in the
     * same "callback", as the Oboe output callback's non-blocking read and the RemoteIO render callback's
     * AudioUnitRender do (research R2, R3); without one a silent block of [frames] is delivered.
     */
    void pullOutput(float* stereo, uint32_t frames) {
        for (uint32_t i = 0; i < frames * 2; ++i) stereo[i] = 0.0f;
        if (duplexInputOpen && captureCallback_ != nullptr) {
            if (pendingInput_.empty() || pendingChannels_ == 0) {
                silentInput_.assign(static_cast<size_t>(frames) * inputChannels_, 0.0f);
                captureCallback_(captureContext_, silentInput_.data(), inputChannels_, frames, 0);
            } else {
                captureCallback_(captureContext_, pendingInput_.data(), pendingChannels_, pendingFrames_, pendingShort_);
                pendingInput_.clear();
            }
        }
        if (renderCallback_ != nullptr) renderCallback_(renderContext_, stereo, frames);
    }

    // --- Feature 010: the duplex input -----------------------------------------------------------------------------

    int32_t openInputBeside(uint32_t preferredRate, uint32_t maxChannels, CaptureCallback callback, void* context) override {
        inputRequestedRate = preferredRate;
        inputRequestedMaxChannels = maxChannels;
        if (failInputOpen) return TEMBRA_ERR_DEVICE;
        captureCallback_ = callback;
        captureContext_ = context;
        inputChannels_ = inputChannelsAvailable < maxChannels ? inputChannelsAvailable : maxChannels;
        if (inputChannels_ == 0) inputChannels_ = 1;
        duplexInputOpen = true;
        inputOpen = true;
        ++inputOpenCount;
        return TEMBRA_OK;
    }

    void closeInput() override {
        if (duplexInputOpen) ++inputCloseCount;
        duplexInputOpen = false;
        inputOpen = outputOpen ? false : inputOpen;
        captureCallback_ = nullptr;
        pendingInput_.clear();
    }

    uint32_t inputChannels() const override { return duplexInputOpen ? inputChannels_ : 0; }
    int64_t inputLatencyNanos() const override { return duplexInputOpen ? inputLatencyNs : 0; }
    uint32_t inputXruns() const override { return inputXrunsReported; }
    uint32_t inputGeneration() const override { return inputGeneration_; }
    uint32_t outputGeneration() const override { return outputGeneration_; }

    /** The block the next pullOutput delivers to the capture callback ([shortFrames] of it were not read by the "device"). */
    void feedInputWhileRendering(const float* interleaved, uint32_t channels, uint32_t frames, uint32_t shortFrames = 0) {
        pendingInput_.assign(interleaved, interleaved + static_cast<size_t>(frames) * channels);
        pendingChannels_ = channels;
        pendingFrames_ = frames;
        pendingShort_ = shortFrames;
    }

    /** The platform closed and reopened the input on its own (a route change); the stream stays open with a new generation. */
    void reopenInput() { ++inputGeneration_; }

private:
    InputCallback inputCallback_ = nullptr;
    void* inputContext_ = nullptr;
    RenderCallback renderCallback_ = nullptr;
    void* renderContext_ = nullptr;
    RenderCallback storedRender_ = nullptr;
    void* storedContext_ = nullptr;
    int64_t cachedLatency_ = 0;
    CaptureCallback captureCallback_ = nullptr;
    void* captureContext_ = nullptr;
    uint32_t inputChannels_ = 1;
    uint32_t inputGeneration_ = 0;
    uint32_t outputGeneration_ = 0;
    std::vector<float> pendingInput_;
    std::vector<float> silentInput_;
    uint32_t pendingChannels_ = 0;
    uint32_t pendingFrames_ = 0;
    uint32_t pendingShort_ = 0;
};

}  // namespace tembra

#endif  // TEMBRA_TESTS_FAKE_AUDIO_DEVICE_H
