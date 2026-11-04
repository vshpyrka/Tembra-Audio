#include "platform/android/oboe_audio_device.h"

#include <chrono>
#include <thread>

#include "tembra_engine.h"

namespace tembra {

OboeAudioDevice::OboeAudioDevice() : bridge_(std::make_shared<Bridge>(this)), inputScratch_(kMaxInputFrames * kMaxInputChannels, 0.0f) {}

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
    if (closing_) return;
    if (inputStream_ && inputStream_.get() == stream) {
        // The duplex input (feature 010, research R2): reopen it with the stored settings; the engine sees the new
        // generation and ends a running take with INPUT_LOST, the arm stays.
        inputForCallback_.store(nullptr, std::memory_order_release);
        waitForCallbackLocked();
        retiredInputStream_ = std::move(inputStream_);
        if (openDuplexInputLocked() != oboe::Result::OK) {
            inputStream_.reset();
            inputChannels_.store(0, std::memory_order_release);
        }
        inputGeneration_.fetch_add(1, std::memory_order_acq_rel);
        return;
    }
    if (!stream_ || stream_.get() != stream) return;  // the host is closing it, or it is an old stream
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
    measureLatencyLocked();  // the new route (e.g. Bluetooth) has its own latency (research R7)
    outputGeneration_.fetch_add(1, std::memory_order_acq_rel);  // the engine clears monitoring (research R12); the input stays
}

// --- The duplex input (feature 010, research R2) ----------------------------------------------------------------

oboe::Result OboeAudioDevice::openDuplexInputLocked() {
    const auto build = [&](oboe::InputPreset preset, oboe::SharingMode sharing, int32_t channels) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(channels)
            ->setSampleRate(static_cast<int32_t>(inputRate_))
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(sharing)
            ->setInputPreset(preset)
            ->setErrorCallback(bridge_);  // no data callback: the output callback reads it non-blocking
        if (stream_) builder.setBufferCapacityInFrames(stream_->getBufferCapacityInFrames() * 2);
        return builder.openStream(inputStream_);
    };
    // Unprocessed keeps gain control, echo cancellation and noise suppression off the signal (FR-002); the device's own
    // channel count up to the maximum; Exclusive where granted.
    const int32_t wanted = oboe::ChannelCount::Unspecified;
    oboe::Result result = build(oboe::InputPreset::Unprocessed, oboe::SharingMode::Exclusive, wanted);
    if (result != oboe::Result::OK) result = build(oboe::InputPreset::Unprocessed, oboe::SharingMode::Shared, wanted);
    if (result != oboe::Result::OK) result = build(oboe::InputPreset::VoiceRecognition, oboe::SharingMode::Shared, wanted);
    if (result != oboe::Result::OK || !inputStream_) return result == oboe::Result::OK ? oboe::Result::ErrorInternal : result;
    if (static_cast<uint32_t>(inputStream_->getChannelCount()) > inputMaxChannels_) {
        inputStream_->close();
        inputStream_.reset();
        result = build(oboe::InputPreset::Unprocessed, oboe::SharingMode::Shared, static_cast<int32_t>(inputMaxChannels_));
        if (result != oboe::Result::OK) result = build(oboe::InputPreset::VoiceRecognition, oboe::SharingMode::Shared, static_cast<int32_t>(inputMaxChannels_));
        if (result != oboe::Result::OK || !inputStream_) return result == oboe::Result::OK ? oboe::Result::ErrorInternal : result;
    }
    if (inputStream_->requestStart() != oboe::Result::OK) {
        inputStream_->close();
        inputStream_.reset();
        return oboe::Result::ErrorInternal;
    }
    inputChannels_.store(static_cast<uint32_t>(inputStream_->getChannelCount()), std::memory_order_release);
    // Two input bursts read ahead absorb a burst that lands just after the output callback (the reservoir's header);
    // one burst left an occasional short read on the Pixel 6 Pro (one in a 29-minute take, 33 s into a 2-minute one).
    const int32_t burst = inputStream_->getFramesPerBurst();
    const uint32_t cushion = (burst > 0 ? static_cast<uint32_t>(burst) : kDefaultBurstFrames) * kCushionBursts;
    inputReservoir_.configure(static_cast<uint32_t>(inputStream_->getChannelCount()), cushion);
    measureInputLatencyLocked();
    warmUpLeft_ = kWarmUpBursts;
    inputForCallback_.store(inputStream_.get(), std::memory_order_release);
    return oboe::Result::OK;
}

int32_t OboeAudioDevice::openInputBeside(uint32_t rate, uint32_t maxChannels, CaptureCallback callback, void* context) {
    std::lock_guard<std::mutex> lock(streamLock_);
    if (!stream_ || stream_->getDirection() != oboe::Direction::Output) return TEMBRA_ERR_STATE;
    if (inputStream_) return TEMBRA_OK;  // idempotent
    capture_ = callback;
    captureContext_ = context;
    inputRate_ = rate == 0 ? sampleRate_.load() : rate;
    inputMaxChannels_ = maxChannels < 1 ? 1 : (maxChannels > kMaxInputChannels ? kMaxInputChannels : maxChannels);
    if (openDuplexInputLocked() != oboe::Result::OK) {
        inputStream_.reset();
        capture_ = nullptr;
        return TEMBRA_ERR_DEVICE;
    }
    return TEMBRA_OK;
}

void OboeAudioDevice::waitForCallbackLocked() {
    // The output callback reads the input pointer at its start; one that holds the old pointer finishes within a burst.
    for (int spins = 0; spins < 1000 && inCallback_.load(std::memory_order_acquire); ++spins) {
        std::this_thread::sleep_for(std::chrono::microseconds(100));
    }
}

void OboeAudioDevice::closeInput() {
    std::shared_ptr<oboe::AudioStream> input;
    std::shared_ptr<oboe::AudioStream> retired;
    {
        std::lock_guard<std::mutex> lock(streamLock_);
        inputForCallback_.store(nullptr, std::memory_order_release);
        waitForCallbackLocked();
        input = std::move(inputStream_);
        retired = std::move(retiredInputStream_);
        capture_ = nullptr;
        inputChannels_.store(0, std::memory_order_release);
        inputLatencyNanos_.store(0, std::memory_order_release);
    }
    if (input) {
        input->stop();
        input->close();
    }
}

uint32_t OboeAudioDevice::inputChannels() const { return inputChannels_.load(std::memory_order_acquire); }

int64_t OboeAudioDevice::inputLatencyNanos() const { return inputLatencyNanos_.load(std::memory_order_acquire); }

uint32_t OboeAudioDevice::inputXruns() const {
    std::lock_guard<std::mutex> lock(streamLock_);
    if (!inputStream_) return 0;
    const auto count = inputStream_->getXRunCount();
    return count ? static_cast<uint32_t>(count.value()) : 0u;
}

void OboeAudioDevice::measureInputLatencyLocked() {
    // Oboe derives the input value from the stream's hardware timestamp ("hardware is earlier", research R9).
    if (!inputStream_) {
        inputLatencyNanos_.store(0, std::memory_order_release);
        return;
    }
    const auto latency = inputStream_->calculateLatencyMillis();
    int64_t nanos = latency ? static_cast<int64_t>(latency.value() * 1'000'000.0) : 0;
    if (nanos <= 0) {
        // No timestamp yet (the stream just started): the buffer size at the rate is the next best estimate.
        const int32_t rate = inputStream_->getSampleRate();
        nanos = rate > 0 ? static_cast<int64_t>(inputStream_->getBufferSizeInFrames()) * 1'000'000'000LL / rate : 0;
    }
    // The reservoir's cushion delays every captured frame by that many frames more: part of the input latency.
    const int32_t rate = inputStream_->getSampleRate();
    if (rate > 0) nanos += static_cast<int64_t>(inputReservoir_.cushionFrames()) * 1'000'000'000LL / rate;
    inputLatencyNanos_.store(nanos, std::memory_order_release);
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
    closeInput();
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

int64_t OboeAudioDevice::outputLatencyNanos() const { return latencyNanos_.load(std::memory_order_acquire); }

void OboeAudioDevice::refreshOutputLatency() {
    std::lock_guard<std::mutex> lock(streamLock_);
    measureLatencyLocked();
    if (inputStream_) measureInputLatencyLocked();
}

void OboeAudioDevice::measureLatencyLocked() {
    // calculateLatencyMillis() is not safe on the data callback before Android R (research R7); never called there.
    if (!stream_ || stream_->getDirection() != oboe::Direction::Output) {
        latencyNanos_.store(0, std::memory_order_release);
        return;
    }
    const auto latency = stream_->calculateLatencyMillis();
    latencyNanos_.store(latency ? static_cast<int64_t>(latency.value() * 1'000'000.0) : 0, std::memory_order_release);
}

uint32_t OboeAudioDevice::sampleRate() const { return sampleRate_.load(); }

bool OboeAudioDevice::deviceLost() const { return lost_.load(); }

bool OboeAudioDevice::realtime() const {
    std::lock_guard<std::mutex> lock(streamLock_);
    return stream_ != nullptr && stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency;
}

uint32_t OboeAudioDevice::xrunCount() const {
    std::lock_guard<std::mutex> lock(streamLock_);
    if (stream_ == nullptr) return 0;
    const auto count = stream_->getXRunCount();
    return count ? static_cast<uint32_t>(count.value()) : 0u;
}

oboe::DataCallbackResult OboeAudioDevice::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    // Audio thread: copy only (Constitution I).
    if (stream->getDirection() == oboe::Direction::Input) {
        if (input_ != nullptr) input_(context_, static_cast<const float*>(audioData), static_cast<uint32_t>(numFrames));
    } else if (render_ != nullptr) {
        // Feature 010 (research R2): the duplex input is read non-blocking here through the reservoir, which keeps two
        // bursts read ahead and zero-pads only when even those run out, and handed to the capture callback before the
        // render of the same block; the first bursts after an open are a warm-up.
        inCallback_.store(true, std::memory_order_seq_cst);
        oboe::AudioStream* input = inputForCallback_.load(std::memory_order_seq_cst);
        if (input != nullptr && capture_ != nullptr) {
            const uint32_t channels = static_cast<uint32_t>(input->getChannelCount());
            const uint32_t frames = static_cast<uint32_t>(numFrames) > kMaxInputFrames ? kMaxInputFrames : static_cast<uint32_t>(numFrames);
            uint32_t shortFrames = inputReservoir_.pull(inputScratch_.data(), frames, [input](float* dst, uint32_t maxFrames) {
                const auto result = input->read(dst, static_cast<int32_t>(maxFrames), 0);
                return result ? static_cast<uint32_t>(result.value() < 0 ? 0 : result.value()) : 0u;
            });
            if (warmUpLeft_ > 0) {
                --warmUpLeft_;
                shortFrames = 0;
            }
            capture_(captureContext_, inputScratch_.data(), channels, frames, shortFrames);
        }
        render_(context_, static_cast<float*>(audioData), static_cast<uint32_t>(numFrames));
        inCallback_.store(false, std::memory_order_seq_cst);
    } else {
        float* out = static_cast<float*>(audioData);
        for (int32_t i = 0; i < numFrames * 2; ++i) out[i] = 0.0f;
    }
    return oboe::DataCallbackResult::Continue;
}

}  // namespace tembra
