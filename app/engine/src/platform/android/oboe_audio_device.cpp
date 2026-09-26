#include "platform/android/oboe_audio_device.h"

#include "tembra_engine.h"

namespace tembra {

OboeAudioDevice::OboeAudioDevice() : bridge_(std::make_shared<Bridge>(this)) {}

OboeAudioDevice::~OboeAudioDevice() { close(); }

oboe::DataCallbackResult OboeAudioDevice::Bridge::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    return device_->onAudioReady(stream, audioData, numFrames);
}

void OboeAudioDevice::Bridge::onErrorAfterClose(oboe::AudioStream*, oboe::Result) {
    device_->lost_.store(true);
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

int32_t OboeAudioDevice::openOutput(uint32_t preferredRate, RenderCallback callback, void* context) {
    close();
    input_ = nullptr;
    render_ = callback;
    context_ = context;
    lost_.store(false);
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
    if (builder.openStream(stream_) != oboe::Result::OK || !stream_) {
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
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    input_ = nullptr;
    render_ = nullptr;
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
