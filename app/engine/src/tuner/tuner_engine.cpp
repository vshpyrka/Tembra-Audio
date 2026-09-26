#include "tuner/tuner_engine.h"

#include <chrono>
#include <cstring>

#include "platform/null_audio_device.h"
#include "platform/synthetic_audio_device.h"

#if defined(__ANDROID__)
#include "platform/android/oboe_audio_device.h"
#elif defined(__APPLE__)
#include <TargetConditionals.h>
#if TARGET_OS_IPHONE
#include "platform/apple/remote_io_audio_device.h"
#endif
#endif

namespace tembra {

namespace {

/** Oboe on Android, RemoteIO on iOS, a silent device on the macOS host where the tests run (research R3). */
std::unique_ptr<AudioDevice> makePlatformDevice() {
#if defined(__ANDROID__)
    return std::make_unique<OboeAudioDevice>();
#elif defined(__APPLE__) && TARGET_OS_IPHONE
    return std::make_unique<RemoteIoAudioDevice>();
#else
    return std::make_unique<NullAudioDevice>();
#endif
}

}  // namespace

TunerEngine& TunerEngine::instance() {
    static TunerEngine engine;
    return engine;
}

TunerEngine::TunerEngine() : platformDevice_(makePlatformDevice()) {}

void TunerEngine::defaultConfig(tembra_tuner_config& out) {
    out.window_frames = 4096;
    out.hop_frames = 1024;
    out.min_hz = 50.0f;
    out.max_hz = 1400.0f;
    out.clarity_threshold = 0.5f;
    out.peak_ratio = 0.9f;
    out.median_size = 5;
    out.watchdog_seconds = 5.0f;
}

// --- Capture ---------------------------------------------------------------------------------------------------

int32_t TunerEngine::startListening(const tembra_tuner_config& config) {
    if (!TunerAnalyzer::validate(config)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_IDLE) return TEMBRA_ERR_STATE;
    return beginCapture(config, *platformDevice_, false);
}

int32_t TunerEngine::startTestSignal(const tembra_tuner_config& config, const tembra_test_signal& signal) {
    if (!TunerAnalyzer::validate(config)) return TEMBRA_ERR_ARGUMENT;
    if (!(signal.frequency_hz >= 20.0 && signal.frequency_hz <= 20000.0)) return TEMBRA_ERR_ARGUMENT;
    if (!(signal.amplitude > 0.0f && signal.amplitude <= 1.0f)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_IDLE) return TEMBRA_ERR_STATE;
    syntheticDevice_ = std::make_unique<SyntheticAudioDevice>(signal);
    const int32_t result = beginCapture(config, *syntheticDevice_, true);
    if (result != TEMBRA_OK) syntheticDevice_.reset();
    return result;
}

int32_t TunerEngine::beginCapture(const tembra_tuner_config& config, AudioDevice& device, bool synthetic) {
    // A thread left over from a watchdog or device-loss stop is joined before anything new starts.
    running_.store(false);
    if (analysisThread_.joinable()) analysisThread_.join();
    callbackAnalyzer_.store(nullptr, std::memory_order_release);

    const int32_t opened = device.openInput(kPreferredRate, &TunerEngine::onInput, this);
    if (opened != TEMBRA_OK) {
        device.close();
        return opened;
    }
    auto analyzer = std::make_shared<TunerAnalyzer>(config, device.sampleRate(), synthetic);
    std::atomic_store(&analyzer_, analyzer);
    activeInput_ = &device;
    callbackAnalyzer_.store(analyzer.get(), std::memory_order_release);
    running_.store(true);
    state_.store(TEMBRA_AUDIO_LISTENING, std::memory_order_release);
    analysisThread_ = std::thread([this] { analysisLoop(); });
    return TEMBRA_OK;
}

void TunerEngine::onInput(void* context, const float* mono, uint32_t frames) {
    auto* engine = static_cast<TunerEngine*>(context);
    TunerAnalyzer* analyzer = engine->callbackAnalyzer_.load(std::memory_order_acquire);
    if (analyzer != nullptr) analyzer->push(mono, frames);
}

void TunerEngine::analysisLoop() {
    while (running_.load(std::memory_order_acquire)) {
        TunerAnalyzer* analyzer = callbackAnalyzer_.load(std::memory_order_acquire);
        if (analyzer == nullptr) break;
        if (!analyzer->analyseAvailable()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(kIdleSleepMillis));
        }
        const bool watchdog = analyzer->watchdogExpired(TunerAnalyzer::nowNanos());
        const bool lost = activeInput_ != nullptr && activeInput_->deviceLost();
        if (watchdog || lost) {
            finishCaptureFromThread();
            break;
        }
    }
}

void TunerEngine::finishCaptureFromThread() {
    running_.store(false);
    // If a host command holds the lock it is already stopping us; it will close and clean up.
    if (!commands_.try_lock()) return;
    if (state() == TEMBRA_AUDIO_LISTENING) {
        if (activeInput_ != nullptr) activeInput_->close();
        callbackAnalyzer_.store(nullptr, std::memory_order_release);
        auto analyzer = std::atomic_load(&analyzer_);
        if (analyzer) analyzer->publishResting();
        state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
    }
    commands_.unlock();
}

void TunerEngine::stopListening() {
    std::lock_guard<std::mutex> lock(commands_);
    stopListeningLocked();
}

void TunerEngine::stopListeningLocked() {
    running_.store(false);
    if (analysisThread_.joinable()) analysisThread_.join();
    if (state() == TEMBRA_AUDIO_LISTENING) {
        if (activeInput_ != nullptr) activeInput_->close();
        state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
    }
    callbackAnalyzer_.store(nullptr, std::memory_order_release);
    auto analyzer = std::atomic_load(&analyzer_);
    if (analyzer) analyzer->publishResting();
    activeInput_ = nullptr;
    syntheticDevice_.reset();
}

void TunerEngine::read(tembra_pitch_reading& out) const {
    auto analyzer = std::atomic_load(&analyzer_);
    if (analyzer) {
        analyzer->read(out);
    } else {
        std::memset(&out, 0, sizeof(out));
    }
    out.state = state();
}

// --- Tones and samples (spec FR-018 to FR-021, research R4, R5) ----------------------------------------------------

int32_t TunerEngine::ensureOutputOpen() {
    if (outputOpen_) return TEMBRA_OK;
    const int32_t opened = platformDevice_->openOutput(kPreferredRate, &TunerEngine::onRender, this);
    if (opened != TEMBRA_OK) {
        platformDevice_->close();
        return opened;
    }
    const uint32_t rate = platformDevice_->sampleRate();
    generator_ = std::make_unique<ToneGenerator>(rate);
    player_ = std::make_unique<SamplePlayer>(rate);
    if (scratch_.size() < kScratchFrames * 2) scratch_.assign(kScratchFrames * 2, 0.0f);
    callbackGenerator_.store(generator_.get(), std::memory_order_release);
    callbackPlayer_.store(player_.get(), std::memory_order_release);
    outputOpen_ = true;
    return TEMBRA_OK;
}

int32_t TunerEngine::startTone(double frequencyHz, uint32_t durationMs) {
    if (!(frequencyHz >= 20.0 && frequencyHz <= 20000.0)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() == TEMBRA_AUDIO_LISTENING) return TEMBRA_ERR_STATE;
    const int32_t opened = ensureOutputOpen();
    if (opened != TEMBRA_OK) return opened;
    if (player_->isActive()) player_->stop();
    generator_->start(frequencyHz, durationMs);  // a running tone at this frequency is only re-armed
    playingHandle_ = -1;
    state_.store(TEMBRA_AUDIO_TONE, std::memory_order_release);
    return TEMBRA_OK;
}

int32_t TunerEngine::loadSample(const uint8_t* bytes, uint32_t length) {
    if (bytes == nullptr || length == 0) return TEMBRA_ERR_ARGUMENT;
    auto decoded = WavDecoder::decode(bytes, length);
    if (!decoded.has_value()) return TEMBRA_ERR_DECODE;
    std::lock_guard<std::mutex> lock(samples_);
    for (size_t i = 0; i < sampleTable_.size(); ++i) {
        if (!sampleTable_[i]) {
            sampleTable_[i] = std::make_unique<DecodedSample>(std::move(*decoded));
            return static_cast<int32_t>(i);
        }
    }
    sampleTable_.push_back(std::make_unique<DecodedSample>(std::move(*decoded)));
    return static_cast<int32_t>(sampleTable_.size() - 1);
}

int32_t TunerEngine::playSample(int32_t handle, bool loop) {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() == TEMBRA_AUDIO_LISTENING) return TEMBRA_ERR_STATE;
    const DecodedSample* sample = nullptr;
    {
        std::lock_guard<std::mutex> samplesLock(samples_);
        if (handle < 0 || static_cast<size_t>(handle) >= sampleTable_.size() || !sampleTable_[static_cast<size_t>(handle)]) return TEMBRA_ERR_ARGUMENT;
        sample = sampleTable_[static_cast<size_t>(handle)].get();
    }
    const int32_t opened = ensureOutputOpen();
    if (opened != TEMBRA_OK) return opened;
    if (generator_->isActive()) generator_->stop();
    player_->play(*sample, loop);
    playingHandle_ = handle;
    state_.store(TEMBRA_AUDIO_TONE, std::memory_order_release);
    return TEMBRA_OK;
}

void TunerEngine::unloadSample(int32_t handle) {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() == TEMBRA_AUDIO_TONE && playingHandle_ == handle) return;  // never while it plays: stop first
    std::lock_guard<std::mutex> samplesLock(samples_);
    if (handle < 0 || static_cast<size_t>(handle) >= sampleTable_.size()) return;
    sampleTable_[static_cast<size_t>(handle)].reset();
}

void TunerEngine::waitForRelease() {
    // The callback applies the 5 ms release; give it a moment before the stream closes so a stop never clicks.
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(kReleaseWaitMillis);
    while (std::chrono::steady_clock::now() < deadline) {
        const bool active = (generator_ && generator_->isActive()) || (player_ && player_->isActive());
        if (!active) return;
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
}

void TunerEngine::stopTone() {
    std::lock_guard<std::mutex> lock(commands_);
    stopToneLocked();
}

void TunerEngine::stopToneLocked() {
    if (!outputOpen_) return;
    if (generator_) generator_->stop();
    if (player_) player_->stop();
    waitForRelease();
    callbackGenerator_.store(nullptr, std::memory_order_release);
    callbackPlayer_.store(nullptr, std::memory_order_release);
    platformDevice_->close();
    outputOpen_ = false;
    playingHandle_ = -1;
    if (state() == TEMBRA_AUDIO_TONE) state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
}

void TunerEngine::onRender(void* context, float* interleavedStereo, uint32_t frames) {
    static_cast<TunerEngine*>(context)->render(interleavedStereo, frames);
}

void TunerEngine::render(float* out, uint32_t frames) {
    // Audio thread: render the active sources, sum them, and report idle when both have finished (Constitution I).
    ToneGenerator* generator = callbackGenerator_.load(std::memory_order_acquire);
    SamplePlayer* player = callbackPlayer_.load(std::memory_order_acquire);
    const bool generatorActive = generator != nullptr && generator->isActive();
    const bool playerActive = player != nullptr && player->isActive();
    if (generatorActive) {
        generator->render(out, frames);
    } else {
        for (uint32_t i = 0; i < frames * 2; ++i) out[i] = 0.0f;
    }
    if (playerActive) {
        if (generatorActive && frames * 2 <= scratch_.size()) {
            player->render(scratch_.data(), frames);
            for (uint32_t i = 0; i < frames * 2; ++i) out[i] += scratch_[i];
        } else if (!generatorActive) {
            player->render(out, frames);
        }
    }
    // Re-check after rendering: a one shot that finished inside this block reports idle now, not a block later.
    const bool stillActive = (generator != nullptr && generator->isActive()) || (player != nullptr && player->isActive());
    if (!stillActive && state() == TEMBRA_AUDIO_TONE) {
        state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
    }
}

#ifdef TEMBRA_BUILD_TESTS
void TunerEngine::setDevice(std::unique_ptr<AudioDevice> device) {
    std::lock_guard<std::mutex> lock(commands_);
    stopListeningLocked();
    stopToneLocked();
    std::atomic_store(&analyzer_, std::shared_ptr<TunerAnalyzer>());
    platformDevice_ = std::move(device);
}
#endif

}  // namespace tembra
