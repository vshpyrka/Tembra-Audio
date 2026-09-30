#include "platform/android/oboe_audio_device.h"

#include "tembra_engine.h"

namespace tembra {

OboeAudioDevice::OboeAudioDevice() : bridge_(std::make_shared<Bridge>(this)) {}

OboeAudioDevice::~OboeAudioDevice() { close(); }

oboe::DataCallbackResult OboeAudioDevice::Bridge::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    return device_->onAudioReady(stream, audioData, numFrames);
}

void OboeAudioDevice::Bridge::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result) {
    device_->onStreamClosedByPlatform(stream);
}

void OboeAudioDevice::onStreamClosedByPlatform(oboe::AudioStream* stream) {
    // Oboe's error thread, after it closed [stream] (a device was disconnected or the route changed).
    std::lock_guard<std::mutex> lock(streamLock_);
    if (closing_ || !stream_ || stream_.get() != stream) return;  // the host is closing it, or it is an old stream
    if (stream->getDirection() == oboe::Direction::Input) {
        lost_.store(true);  // the tuner's capture path stops and the host decides (feature 003)
        return;
    }
    // Output: reopen with the stored settings and the same render callback; the renderer keeps its position and,
    // because Oboe converts to the requested rate, the stream rate is unchanged (research R1). Opening a new stream
    // is a valid action in onErrorAfterClose.
    retiredStream_ = std::move(stream_);
    if (openOutputStream(outputRate_) != oboe::Result::OK || !stream_ || stream_->requestStart() != oboe::Result::OK) {
        if (stream_) stream_->close();
        stream_.reset();
        lost_.store(true);
        return;
    }
    sampleRate_.store(static_cast<uint32_t>(stream_->getSampleRate()));
}

oboe::Result OboeAudioDevice::openInputWithPreset(uint32_t preferredRate, oboe::InputPreset preset) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(static_cast<int32_t>(preferredRate))
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setInputPreset(preset)
        ->setDataCallback(bridge_)
        ->setErrorCallback(bridge_);
    return builder.openStream(stream_);
}

int32_t OboeAudioDevice::openInput(uint32_t preferredRate, InputCallback callback, void* context) {
    close();
    std::lock_guard<std::mutex> lock(streamLock_);
    input_ = callback;
    render_ = nullptr;
    context_ = context;
    lost_.store(false);
    // Unprocessed keeps gain control and noise suppression off the signal; some devices only offer VoiceRecognition.
    oboe::Result result = openInputWithPreset(preferredRate, oboe::InputPreset::Unprocessed);
    if (result != oboe::Result::OK) result = openInputWithPreset(preferredRate, oboe::InputPreset::VoiceRecognition);
    if (result != oboe::Result::OK || !stream_) {
        stream_.reset();
        return TEMBRA_ERR_DEVICE;
    }
    sampleRate_.store(static_cast<uint32_t>(stream_->getSampleRate()));
    if (stream_->requestStart() != oboe::Result::OK) {
        stream_->close();
        stream_.reset();
        return TEMBRA_ERR_DEVICE;
    }
    return TEMBRA_OK;
}

oboe::Result OboeAudioDevice::openOutputStream(uint32_t preferredRate) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(static_cast<int32_t>(preferredRate))
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(bridge_)
        ->setErrorCallback(bridge_);
    return builder.openStream(stream_);
}

int32_t OboeAudioDevice::openOutput(uint32_t preferredRate, RenderCallback callback, void* context) {
    close();
    std::lock_guard<std::mutex> lock(streamLock_);
    input_ = nullptr;
    render_ = callback;
    context_ = context;
    lost_.store(false);
    outputRate_ = preferredRate;
    if (openOutputStream(preferredRate) != oboe::Result::OK || !stream_) {
        stream_.reset();
        return TEMBRA_ERR_DEVICE;
    }
    sampleRate_.store(static_cast<uint32_t>(stream_->getSampleRate()));
    if (stream_->requestStart() != oboe::Result::OK) {
        stream_->close();
        stream_.reset();
        return TEMBRA_ERR_DEVICE;
    }
    return TEMBRA_OK;
}

void OboeAudioDevice::close() {
    std::shared_ptr<oboe::AudioStream> stream;
    std::shared_ptr<oboe::AudioStream> retired;
    {
        std::lock_guard<std::mutex> lock(streamLock_);
        closing_ = true;  // a disconnect arriving now does not reopen
        stream = std::move(stream_);
        retired = std::move(retiredStream_);
    }
    if (stream) {
        stream->stop();
        stream->close();
    }
    std::lock_guard<std::mutex> lock(streamLock_);
    closing_ = false;
    input_ = nullptr;
    render_ = nullptr;
}

int64_t OboeAudioDevice::outputLatencyNanos() const {
    // Control thread only: calculateLatencyMillis() is not safe on the data callback before Android R (research R7).
    std::lock_guard<std::mutex> lock(streamLock_);
    if (!stream_ || stream_->getDirection() != oboe::Direction::Output) return 0;
    const auto latency = stream_->calculateLatencyMillis();
    if (!latency) return 0;
    return static_cast<int64_t>(latency.value() * 1'000'000.0);
}

uint32_t OboeAudioDevice::sampleRate() const { return sampleRate_.load(); }

bool OboeAudioDevice::deviceLost() const { return lost_.load(); }

oboe::DataCallbackResult OboeAudioDevice::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    // Audio thread: copy only (Constitution I).
    if (stream->getDirection() == oboe::Direction::Input) {
        if (input_ != nullptr) input_(context_, static_cast<const float*>(audioData), static_cast<uint32_t>(numFrames));
    } else if (render_ != nullptr) {
        render_(context_, static_cast<float*>(audioData), static_cast<uint32_t>(numFrames));
    } else {
        float* out = static_cast<float*>(audioData);
        for (int32_t i = 0; i < numFrames * 2; ++i) out[i] = 0.0f;
    }
    return oboe::DataCallbackResult::Continue;
}

}  // namespace tembra
