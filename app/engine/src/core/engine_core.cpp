#include "core/engine_core.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

#include "metronome/click_synth.h"
#include "metronome/custom_sound_loader.h"
#include "timeline/audio_decoder.h"
#include "timeline/prime_plan.h"

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

EngineCore& EngineCore::instance() {
    static EngineCore engine;
    return engine;
}

EngineCore::EngineCore() : platformDevice_(makePlatformDevice()) { telemetryDevice_.store(platformDevice_.get()); }

void EngineCore::defaultConfig(tembra_tuner_config& out) {
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

int32_t EngineCore::startListening(const tembra_tuner_config& config) {
    if (!TunerAnalyzer::validate(config)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_IDLE) return TEMBRA_ERR_STATE;
    return beginCapture(config, *platformDevice_, false);
}

int32_t EngineCore::startTestSignal(const tembra_tuner_config& config, const tembra_test_signal& signal) {
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

int32_t EngineCore::beginCapture(const tembra_tuner_config& config, AudioDevice& device, bool synthetic) {
    // A thread left over from a watchdog or device-loss stop is joined before anything new starts.
    running_.store(false);
    if (analysisThread_.joinable()) analysisThread_.join();
    callbackAnalyzer_.store(nullptr, std::memory_order_release);

    const int32_t opened = device.openInput(kPreferredRate, &EngineCore::onInput, this);
    if (opened != TEMBRA_OK) {
        device.close();
        return opened;
    }
    auto analyzer = std::make_shared<TunerAnalyzer>(config, device.sampleRate(), synthetic, gateDb_);
    std::atomic_store(&analyzer_, analyzer);
    activeInput_ = &device;
    callbackRate_.store(device.sampleRate(), std::memory_order_relaxed);
    health_.streamOpened(device.realtime());
    callbackAnalyzer_.store(analyzer.get(), std::memory_order_release);
    running_.store(true);
    state_.store(TEMBRA_AUDIO_LISTENING, std::memory_order_release);
    analysisThread_ = std::thread([this] { analysisLoop(); });
    return TEMBRA_OK;
}

void EngineCore::onInput(void* context, const float* mono, uint32_t frames) {
    auto* engine = static_cast<EngineCore*>(context);
    const uint64_t started = AudioHealth::nowMicros();
    TunerAnalyzer* analyzer = engine->callbackAnalyzer_.load(std::memory_order_acquire);
    if (analyzer != nullptr) analyzer->push(mono, frames);
    engine->health_.record(started, AudioHealth::nowMicros(), frames, engine->callbackRate_.load(std::memory_order_relaxed));
}

void EngineCore::analysisLoop() {
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

void EngineCore::finishCaptureFromThread() {
    running_.store(false);
    // If a host command holds the lock it is already stopping us; it will close and clean up.
    if (!commands_.try_lock()) return;
    if (state() == TEMBRA_AUDIO_LISTENING) {
        if (activeInput_ != nullptr) activeInput_->close();
        health_.streamClosed();
        callbackAnalyzer_.store(nullptr, std::memory_order_release);
        auto analyzer = std::atomic_load(&analyzer_);
        if (analyzer) analyzer->publishResting();
        state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
    }
    commands_.unlock();
}

void EngineCore::stopListening() {
    std::lock_guard<std::mutex> lock(commands_);
    stopListeningLocked();
}

void EngineCore::stopListeningLocked() {
    running_.store(false);
    if (analysisThread_.joinable()) analysisThread_.join();
    if (state() == TEMBRA_AUDIO_LISTENING) {
        if (activeInput_ != nullptr) activeInput_->close();
        health_.streamClosed();
        state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
    }
    callbackAnalyzer_.store(nullptr, std::memory_order_release);
    auto analyzer = std::atomic_load(&analyzer_);
    if (analyzer) analyzer->publishResting();
    activeInput_ = nullptr;
    syntheticDevice_.reset();
}

int32_t EngineCore::setGate(bool enabled, float openDb) {
    std::lock_guard<std::mutex> lock(commands_);
    gateDb_ = enabled ? openDb : TunerAnalyzer::kGateOff;
    auto analyzer = std::atomic_load(&analyzer_);
    if (analyzer) analyzer->setGate(gateDb_);
    return TEMBRA_OK;
}

void EngineCore::read(tembra_pitch_reading& out) const {
    auto analyzer = std::atomic_load(&analyzer_);
    if (analyzer) {
        analyzer->read(out);
    } else {
        std::memset(&out, 0, sizeof(out));
        out.input_level_db = TunerAnalyzer::kLevelFloorDb;
        out.gate_open = 1;
    }
    out.state = state();
}

// --- Tones and samples (spec FR-018 to FR-021, research R4, R5) ----------------------------------------------------

int32_t EngineCore::ensureOutputOpen() {
    if (outputOpen_) return TEMBRA_OK;
    const int32_t opened = platformDevice_->openOutput(kPreferredRate, &EngineCore::onRender, this);
    if (opened != TEMBRA_OK) {
        platformDevice_->close();
        return opened;
    }
    const uint32_t rate = platformDevice_->sampleRate();
    generator_ = std::make_unique<ToneGenerator>(rate);
    player_ = std::make_unique<SamplePlayer>(rate);
    if (scratch_.size() < kScratchFrames * 2) scratch_.assign(kScratchFrames * 2, 0.0f);
    callbackRate_.store(rate, std::memory_order_relaxed);
    health_.streamOpened(platformDevice_->realtime());
    onOutputOpened(rate);
    callbackGenerator_.store(generator_.get(), std::memory_order_release);
    callbackPlayer_.store(player_.get(), std::memory_order_release);
    callbackMetronome_.store(metronome_.get(), std::memory_order_release);
    outputOpen_ = true;
    return TEMBRA_OK;
}

void EngineCore::onOutputOpened(uint32_t rate) {
    // The callback does not see the metronome yet, so the click tables may be rewritten here (research R3: the stream
    // rate is stable for the session, so this renders once per rate, not once per open).
    if (builtInRate_ != rate) {
        for (size_t i = 0; i < kBuiltInSounds; ++i) {
            const auto sound = static_cast<tembra_click_sound>(i);
            builtIns_[i].sub = ClickSynth::render(sound, TEMBRA_CLICK_SUB, rate);
            builtIns_[i].click = ClickSynth::render(sound, TEMBRA_CLICK_CLICK, rate);
            builtIns_[i].accent = ClickSynth::render(sound, TEMBRA_CLICK_ACCENT, rate);
        }
        builtInRate_ = rate;
    }
    if (custom_ && customRate_ != rate) {
        // Re-prepare the custom sound from the app's copy at the new rate (research R4); a vanished copy falls back.
        auto set = std::make_unique<ClickSet>();
        tembra_custom_sound_info info{};
        if (CustomSoundLoader::load(customPath_.c_str(), rate, &info, set.get()) == TEMBRA_OK) {
            custom_ = std::move(set);
            customInfo_ = info;
            customRate_ = rate;
        } else {
            custom_.reset();
            customPath_.clear();
            customRate_ = 0;
            if (selectedSound_ == TEMBRA_SOUND_CUSTOM) selectedSound_ = TEMBRA_SOUND_CLASSIC_WOOD;
        }
    }
    if (!metronome_ || metronome_->sampleRate() != rate) {
        if (metronome_) retiredRenderers_.push_back(std::move(metronome_));
        metronome_ = std::make_unique<MetronomeRenderer>(rate);
    }
    metronome_->setGrid(grid_);
    metronome_->setSet(selectedSetLocked());
    telemetryMetronome_.store(metronome_.get(), std::memory_order_release);
}

void EngineCore::closeOutputLocked() {
    callbackGenerator_.store(nullptr, std::memory_order_release);
    callbackPlayer_.store(nullptr, std::memory_order_release);
    callbackMetronome_.store(nullptr, std::memory_order_release);
    callbackTransport_.store(nullptr, std::memory_order_release);
    platformDevice_->close();  // returns after the last callback
    health_.streamClosed();
    outputOpen_ = false;
    playingHandle_ = -1;
    if (metronome_) metronome_->silence();
    sweepRetiredLocked(true);
}

int32_t EngineCore::startTone(double frequencyHz, uint32_t durationMs) {
    if (!(frequencyHz >= 20.0 && frequencyHz <= 20000.0)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() == TEMBRA_AUDIO_LISTENING || state() == TEMBRA_AUDIO_METRONOME || state() == TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    const int32_t opened = ensureOutputOpen();
    if (opened != TEMBRA_OK) return opened;
    if (player_->isActive()) player_->stop();
    if (metronome_->isActive()) metronome_->stop();  // a preview click gives way
    generator_->start(frequencyHz, durationMs);  // a running tone at this frequency is only re-armed
    playingHandle_ = -1;
    state_.store(TEMBRA_AUDIO_TONE, std::memory_order_release);
    return TEMBRA_OK;
}

int32_t EngineCore::loadSample(const uint8_t* bytes, uint32_t length) {
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

int32_t EngineCore::playSample(int32_t handle, bool loop) {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() == TEMBRA_AUDIO_LISTENING || state() == TEMBRA_AUDIO_METRONOME || state() == TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    const DecodedSample* sample = nullptr;
    {
        std::lock_guard<std::mutex> samplesLock(samples_);
        if (handle < 0 || static_cast<size_t>(handle) >= sampleTable_.size() || !sampleTable_[static_cast<size_t>(handle)]) return TEMBRA_ERR_ARGUMENT;
        sample = sampleTable_[static_cast<size_t>(handle)].get();
    }
    const int32_t opened = ensureOutputOpen();
    if (opened != TEMBRA_OK) return opened;
    if (generator_->isActive()) generator_->stop();
    if (metronome_->isActive()) metronome_->stop();
    player_->play(*sample, loop);
    playingHandle_ = handle;
    state_.store(TEMBRA_AUDIO_TONE, std::memory_order_release);
    return TEMBRA_OK;
}

void EngineCore::unloadSample(int32_t handle) {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() == TEMBRA_AUDIO_TONE && playingHandle_ == handle) return;  // never while it plays: stop first
    std::lock_guard<std::mutex> samplesLock(samples_);
    if (handle < 0 || static_cast<size_t>(handle) >= sampleTable_.size()) return;
    sampleTable_[static_cast<size_t>(handle)].reset();
}

void EngineCore::waitForRelease() {
    // The callback applies the 5 ms release; give it a moment before the stream closes so a stop never clicks.
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(kReleaseWaitMillis);
    while (std::chrono::steady_clock::now() < deadline) {
        const bool active = (generator_ && generator_->isActive()) || (player_ && player_->isActive()) ||
                            (metronome_ && metronome_->isActive()) || (transport_ && transport_->readersActive());
        if (!active) return;
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
}

void EngineCore::stopTone() {
    std::lock_guard<std::mutex> lock(commands_);
    // The metronome and the transport are stopped by their own stop and close only.
    if (state() == TEMBRA_AUDIO_METRONOME || state() == TEMBRA_AUDIO_TRANSPORT) return;
    stopToneLocked();
}

void EngineCore::stopToneLocked() {
    if (!outputOpen_) return;
    if (generator_) generator_->stop();
    if (player_) player_->stop();
    if (metronome_ && metronome_->isActive()) metronome_->stop();  // a preview click
    waitForRelease();
    closeOutputLocked();
    if (state() == TEMBRA_AUDIO_TONE) state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
}

void EngineCore::onRender(void* context, float* interleavedStereo, uint32_t frames) {
    auto* engine = static_cast<EngineCore*>(context);
    const uint64_t started = AudioHealth::nowMicros();
    engine->render(interleavedStereo, frames);
    engine->health_.record(started, AudioHealth::nowMicros(), frames, engine->callbackRate_.load(std::memory_order_relaxed));
}

bool EngineCore::readAudioHealth(tembra_audio_health& out) {
    // Host thread: the platform stream's own xrun counter is sampled here, never on the callback (Oboe's rule).
    AudioDevice* device = telemetryDevice_.load(std::memory_order_acquire);
    if (device != nullptr) health_.setXruns(device->xrunCount());
    TransportRenderer* transport = telemetryTransport_.load(std::memory_order_acquire);
    if (transport != nullptr && state() == TEMBRA_AUDIO_TRANSPORT) {
        health_.setTransportCounters(transport->starvation(), transport->clipsActive());
    } else {
        health_.setTransportCounters(0, 0);
    }
    return health_.read(out);
}

void EngineCore::render(float* out, uint32_t frames) {
    // Audio thread (Constitution I). The transport, when open, is the whole mix (it renders the click itself).
    TransportRenderer* transport = callbackTransport_.load(std::memory_order_acquire);
    if (transport != nullptr) {
        transport->render(out, frames);
        return;
    }
    // Otherwise render the active sources, sum them, and report idle when all have finished.
    ToneGenerator* generator = callbackGenerator_.load(std::memory_order_acquire);
    SamplePlayer* player = callbackPlayer_.load(std::memory_order_acquire);
    MetronomeRenderer* metronome = callbackMetronome_.load(std::memory_order_acquire);
    const bool generatorActive = generator != nullptr && generator->isActive();
    const bool playerActive = player != nullptr && player->isActive();
    const bool metronomeActive = metronome != nullptr && metronome->isActive();
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
    if (metronomeActive) {
        if (!generatorActive && !playerActive) {
            metronome->render(out, frames);
        } else {
            // Summed through the pre-allocated scratch in bounded chunks (the renderer overwrites what it is given).
            for (uint32_t done = 0; done < frames;) {
                const uint32_t chunk = std::min<uint32_t>(frames - done, static_cast<uint32_t>(scratch_.size() / 2));
                metronome->render(scratch_.data(), chunk);
                for (uint32_t i = 0; i < chunk * 2; ++i) out[done * 2 + i] += scratch_[i];
                done += chunk;
            }
        }
    }
    // Re-check after rendering: a one shot that finished inside this block reports idle now, not a block later.
    const bool stillActive = (generator != nullptr && generator->isActive()) || (player != nullptr && player->isActive()) ||
                             (metronome != nullptr && metronome->isActive());
    if (!stillActive && state() == TEMBRA_AUDIO_TONE) {
        state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
    }
}

// --- Metronome (research R1-R5, R7; data-model.md §6) -----------------------------------------------------------

const ClickSet* EngineCore::selectedSetLocked() const {
    if (selectedSound_ == TEMBRA_SOUND_CUSTOM && custom_) return custom_.get();
    const uint32_t index = selectedSound_ < kBuiltInSounds ? selectedSound_ : TEMBRA_SOUND_CLASSIC_WOOD;
    return &builtIns_[index];
}

int32_t EngineCore::setGrid(const tembra_metronome_grid& grid) {
    if (!MetronomeGrid::validate(grid)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    grid_ = MetronomeGrid::from(grid);
    if (metronome_) metronome_->setGrid(grid_);
    return TEMBRA_OK;
}

int32_t EngineCore::selectSound(uint32_t sound) {
    if (sound > TEMBRA_SOUND_CUSTOM) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (sound == TEMBRA_SOUND_CUSTOM && !custom_) return TEMBRA_ERR_STATE;
    selectedSound_ = sound;
    if (metronome_) metronome_->setSet(selectedSetLocked());
    return TEMBRA_OK;
}

int32_t EngineCore::loadCustomSound(const char* path, tembra_custom_sound_info* info) {
    if (path == nullptr || info == nullptr) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    const uint32_t rate = metronome_ ? metronome_->sampleRate() : kPreferredRate;
    auto set = std::make_unique<ClickSet>();
    tembra_custom_sound_info prepared{};
    const int32_t result = CustomSoundLoader::load(path, rate, &prepared, set.get());
    if (result != TEMBRA_OK) return result;  // the previous custom sound, if any, stays
    // Publish the new set first, then retire the old one: the callback swaps on its next voice (no gap).
    std::unique_ptr<ClickSet> previous = std::move(custom_);
    custom_ = std::move(set);
    customPath_ = path;
    customRate_ = rate;
    customInfo_ = prepared;
    if (selectedSound_ == TEMBRA_SOUND_CUSTOM && metronome_) metronome_->setSet(custom_.get());
    retireLocked(std::move(previous));
    sweepRetiredLocked(!outputOpen_);
    *info = prepared;
    return TEMBRA_OK;
}

void EngineCore::unloadCustomSound() {
    std::lock_guard<std::mutex> lock(commands_);
    if (selectedSound_ == TEMBRA_SOUND_CUSTOM) {
        selectedSound_ = TEMBRA_SOUND_CLASSIC_WOOD;
        if (metronome_) metronome_->setSet(selectedSetLocked());
    }
    retireLocked(std::move(custom_));
    customPath_.clear();
    customRate_ = 0;
    customInfo_ = tembra_custom_sound_info{};
    sweepRetiredLocked(!outputOpen_);
}

void EngineCore::retireLocked(std::unique_ptr<ClickSet> set) {
    // A set the callback may still be playing is freed only once it has provably moved off it (sweepRetiredLocked);
    // with the stream closed nothing plays it and it goes at once.
    if (!set || !outputOpen_ || !metronome_) return;
    retiredSets_.push_back(RetiredSet{std::move(set), metronome_->blocksRendered()});
}

void EngineCore::sweepRetiredLocked(bool outputClosed) {
    if (outputClosed || !metronome_) {
        retiredSets_.clear();
        return;
    }
    // Two blocks after the retire, a whole block has started after the replacement was published; the voices it
    // left behind are what references() reports.
    const uint64_t blocks = metronome_->blocksRendered();
    retiredSets_.erase(std::remove_if(retiredSets_.begin(), retiredSets_.end(),
                                      [&](const RetiredSet& retired) {
                                          return blocks >= retired.retiredAtBlock + 2 && !metronome_->references(retired.set.get());
                                      }),
                       retiredSets_.end());
}

int32_t EngineCore::startMetronome() {
    std::lock_guard<std::mutex> lock(commands_);
    const uint32_t current = state();
    if (current == TEMBRA_AUDIO_METRONOME) return TEMBRA_OK;
    if (current != TEMBRA_AUDIO_IDLE) return TEMBRA_ERR_STATE;
    const int32_t opened = ensureOutputOpen();
    if (opened != TEMBRA_OK) return opened;
    metronome_->setGrid(grid_);
    metronome_->setSet(selectedSetLocked());
    metronome_->start();
    platformDevice_->refreshOutputLatency();  // control thread; the device also refreshes after a reopen
    state_.store(TEMBRA_AUDIO_METRONOME, std::memory_order_release);
    return TEMBRA_OK;
}

void EngineCore::stopMetronome() {
    std::lock_guard<std::mutex> lock(commands_);
    stopMetronomeLocked();
}

void EngineCore::stopMetronomeLocked() {
    if (state() != TEMBRA_AUDIO_METRONOME) return;
    metronome_->stop();  // 5 ms release rendered by the callback
    waitForRelease();
    closeOutputLocked();
    state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
}

int32_t EngineCore::previewClick(uint32_t level) {
    if (level > TEMBRA_CLICK_ACCENT) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_IDLE) return TEMBRA_ERR_STATE;
    const int32_t opened = ensureOutputOpen();
    if (opened != TEMBRA_OK) return opened;
    metronome_->setSet(selectedSetLocked());
    metronome_->preview(static_cast<tembra_click_level>(level));
    state_.store(TEMBRA_AUDIO_TONE, std::memory_order_release);  // back to IDLE from the callback when the click ends
    return TEMBRA_OK;
}

void EngineCore::setOutputLatencyNs(int64_t nanos) {
    std::lock_guard<std::mutex> lock(commands_);
    reportedLatencyNs_.store(nanos < 0 ? 0 : nanos, std::memory_order_release);
    platformDevice_->setReportedLatencyNanos(nanos < 0 ? 0 : nanos);
    if (nanos <= 0 && outputOpen_) platformDevice_->refreshOutputLatency();  // 0 = ask the device (contract)
}

int64_t EngineCore::outputLatencyNanos() const {
    // Atomics only: no mutex and no device call on the reader's thread (contract: the read takes no mutex).
    const int64_t reported = reportedLatencyNs_.load(std::memory_order_acquire);
    if (reported > 0) return reported;
    const AudioDevice* device = telemetryDevice_.load(std::memory_order_acquire);
    return device != nullptr ? device->outputLatencyNanos() : 0;
}

void EngineCore::readTick(tembra_metronome_tick& out) {
    MetronomeRenderer* metronome = telemetryMetronome_.load(std::memory_order_acquire);
    if (metronome == nullptr) {
        std::memset(&out, 0, sizeof(out));
        return;
    }
    const int64_t latency = outputLatencyNanos();
    metronome->readTick(latency, MetronomeRenderer::nowNanos(), &out);
    // A stream the platform closed and could not reopen stops the click: report it so the host can stop (R1).
    const AudioDevice* device = telemetryDevice_.load(std::memory_order_acquire);
    const bool lost = device != nullptr && device->deviceLost();
    out.running = state() == TEMBRA_AUDIO_METRONOME && !lost ? 1u : 0u;
}

// --- The project transport (feature 009, contracts/engine-c-abi-v6.md; research R2-R7) ---------------------------

std::vector<ClipSource*> EngineCore::sourceTableLocked() const {
    std::vector<ClipSource*> table;
    table.reserve(clipSources_.size());
    for (const auto& source : clipSources_) table.push_back(source.get());  // nullptr marks a closed handle
    return table;
}

std::vector<ClipSource*> EngineCore::openSourcesLocked() const {
    std::vector<ClipSource*> open;
    for (const auto& source : clipSources_) {
        if (source) open.push_back(source.get());
    }
    return open;
}

void EngineCore::ensureTransportLocked(uint32_t rate) {
    // Built once per stream rate like the metronome renderer; a replaced one is kept for a concurrent read.
    if (!transport_ || transport_->sampleRate() != rate) {
        if (transport_) retiredTransports_.push_back(std::move(transport_));
        transport_ = std::make_unique<TransportRenderer>(rate);
    }
    transport_->setMetronome(metronome_.get());
    telemetryTransport_.store(transport_.get(), std::memory_order_release);
}

int32_t EngineCore::openTransport(const tembra_transport_config& config) {
    if (config.reserved != 0) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_IDLE) return TEMBRA_ERR_STATE;
    const int32_t opened = ensureOutputOpen();  // a lazily kept tone stream is reused as it is
    if (opened != TEMBRA_OK) return opened;
    const uint32_t rate = platformDevice_->sampleRate();
    transportRate_ = rate;
    ensureTransportLocked(rate);
    // Sources opened while idle decode for the preferred rate; the stream's actual rate wins (research R5). A file
    // that cannot be re-opened keeps its decoder.
    for (auto& source : clipSources_) {
        if (!source || source->outputRate() == rate) continue;
        int32_t error = TEMBRA_OK;
        auto decoder = AudioDecoder::open(source->path().c_str(), rate, nullptr, &error);
        if (decoder) source->setDecoder(std::move(decoder));
    }
    hostTempo_ = TempoMap{};
    hostTempo_.rate = rate;
    hostArrangement_ = std::make_unique<ArrangementSnapshot>();
    hostArrangement_->version = ++arrangementVersion_;
    hostTracks_ = TrackTable{};
    hostTracks_.version = ++arrangementVersion_;
    transport_->silence();
    transport_->setTempo(hostTempo_);
    transport_->setArrangement(*hostArrangement_);
    transport_->setTracks(hostTracks_);
    transport_->setMaster(1.0f);
    transport_->setMetronomeEnabled(false);
    lastSeekSequence_ = transport_->seek(0);
    lastSeekFrame_ = 0;
    hostPlaying_ = false;
    streamer_ = std::make_unique<DiskStreamer>(rate, &transport_->readersActiveFlag());
    streamer_->setSources(openSourcesLocked());
    streamer_->start();
    platformDevice_->refreshOutputLatency();
    callbackTransport_.store(transport_.get(), std::memory_order_release);
    state_.store(TEMBRA_AUDIO_TRANSPORT, std::memory_order_release);
    return TEMBRA_OK;
}

void EngineCore::closeTransport() {
    std::lock_guard<std::mutex> lock(commands_);
    closeTransportLocked();
}

void EngineCore::closeTransportLocked() {
    if (state() != TEMBRA_AUDIO_TRANSPORT) return;
    transport_->pause();  // the 5 ms release, rendered by the callback
    hostPlaying_ = false;
    waitForRelease();
    if (streamer_) streamer_->stop();  // no producer touches a fifo from here on
    callbackTransport_.store(nullptr, std::memory_order_release);
    closeOutputLocked();  // returns after the last callback; the metronome is silenced there too
    transport_->silence();
    streamer_.reset();
    hostArrangement_.reset();  // the arrangement went with the stream: its sources may be closed now
    sweepRetiredSourcesLocked(true);
    state_.store(TEMBRA_AUDIO_IDLE, std::memory_order_release);
}

int32_t EngineCore::setTransportTempo(uint32_t bpm, uint32_t beatsPerBar, uint32_t beatUnit) {
    if (bpm < 40 || bpm > 240 || beatsPerBar < 1 || beatsPerBar > 32 || (beatUnit != 4 && beatUnit != 8)) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    hostTempo_.bpm = static_cast<double>(bpm);
    hostTempo_.beatsPerBar = beatsPerBar;
    hostTempo_.beatUnit = beatUnit;
    hostTempo_.rate = transportRate_;
    transport_->setTempo(hostTempo_);  // the callback re-derives the clip frames and re-anchors the click
    streamer_->invalidate();
    return TEMBRA_OK;
}

int32_t EngineCore::setTransportArrangement(const tembra_arrangement& arrangement) {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    const std::vector<ClipSource*> table = sourceTableLocked();
    const int32_t valid = ArrangementSnapshot::validate(arrangement, table.data(), table.size());
    if (valid != TEMBRA_OK) return valid;
    auto snapshot = std::make_unique<ArrangementSnapshot>();
    snapshot->fill(arrangement, table.data(), table.size(), transportRate_, ++arrangementVersion_);
    snapshot->sortClipsByTrackAndStart();
    transport_->setArrangement(*snapshot);
    hostArrangement_ = std::move(snapshot);
    hostTracks_.version = ++arrangementVersion_;
    hostTracks_.count = std::min<uint32_t>(arrangement.track_count, TEMBRA_MAX_TRACKS);
    for (uint32_t t = 0; t < hostTracks_.count; ++t) hostTracks_.tracks[t] = hostArrangement_->tracks[t];
    transport_->setTracks(hostTracks_);
    streamer_->invalidate();
    sweepRetiredSourcesLocked(false);
    return TEMBRA_OK;
}

int32_t EngineCore::setTransportTrack(uint32_t index, float gain, bool audible) {
    if (!std::isfinite(gain) || gain < 0.0f || gain > 2.0f) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    if (index >= hostTracks_.count) return TEMBRA_ERR_ARGUMENT;
    hostTracks_.tracks[index] = TrackState{gain, audible ? 1u : 0u};
    hostTracks_.version = ++arrangementVersion_;
    transport_->setTracks(hostTracks_);
    return TEMBRA_OK;
}

int32_t EngineCore::setTransportMaster(float gain) {
    if (!std::isfinite(gain) || gain < 0.0f || gain > 1.0f) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    transport_->setMaster(gain);
    return TEMBRA_OK;
}

int32_t EngineCore::setTransportMetronome(bool enabled) {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    metronome_->setGrid(grid_);  // the standalone metronome's grid and sound are the click (research R4)
    metronome_->setSet(selectedSetLocked());
    transport_->setMetronomeEnabled(enabled);
    return TEMBRA_OK;
}

int64_t EngineCore::transportPositionLocked() const {
    // A seek the callback has not applied yet is still the position the host asked for.
    const TransportRecord record = transport_->record();
    return record.seekSequence < lastSeekSequence_ ? lastSeekFrame_ : record.frame;
}

int32_t EngineCore::transportPlay() {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    sweepRetiredSourcesLocked(false);
    if (hostPlaying_) return TEMBRA_OK;
    const int64_t position = transportPositionLocked();
    // The synchronous prime (research R5; target < 50 ms) is safe only while the callback cannot be popping: after
    // a release tail that is still running, the callback parks what it needs and the streamer thread primes it.
    if (!transport_->readersActive() && !transport_->commandPending() && streamer_->primedPosition() != position) {
        streamer_->prime(position, planPrime(*hostArrangement_, hostTempo_, position));
    }
    transport_->play();
    hostPlaying_ = true;
    platformDevice_->refreshOutputLatency();  // control thread; the device also refreshes after a reopen
    return TEMBRA_OK;
}

int32_t EngineCore::transportPause() {
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    transport_->pause();  // idempotent while stopped: the callback ignores it
    hostPlaying_ = false;
    sweepRetiredSourcesLocked(false);
    return TEMBRA_OK;
}

int32_t EngineCore::transportSeekBeats(double beats) {
    if (!std::isfinite(beats) || beats < 0.0) return TEMBRA_ERR_ARGUMENT;
    std::lock_guard<std::mutex> lock(commands_);
    if (state() != TEMBRA_AUDIO_TRANSPORT) return TEMBRA_ERR_STATE;
    const int64_t frame = hostTempo_.beatsToFrame(beats);
    lastSeekSequence_ = transport_->seek(frame);
    lastSeekFrame_ = frame;
    // Playing: the callback moves the clock, parks the sources and the streamer re-primes them (silent, < 100 ms).
    // Stopped: the thread pre-primes the new position so the next play starts at once.
    if (!hostPlaying_) streamer_->requestReprime(frame, planPrime(*hostArrangement_, hostTempo_, frame));
    return TEMBRA_OK;
}

void EngineCore::readTransport(tembra_transport_snapshot& out) {
    TransportRenderer* transport = telemetryTransport_.load(std::memory_order_acquire);
    if (transport == nullptr || state() != TEMBRA_AUDIO_TRANSPORT) {
        std::memset(&out, 0, sizeof(out));
        return;
    }
    const TransportRecord record = transport->record();
    const int64_t latency = outputLatencyNanos();
    out.playing = record.playing;
    out.position_frames = record.frame;
    out.position_beats = record.framesPerBeat > 0.0 ? static_cast<double>(record.frame) / record.framesPerBeat : 0.0;
    out.block_time_ns = record.blockTimeNs;
    out.latency_ns = latency;
    out.sample_rate = record.rate;
    double seconds = record.rate > 0 ? static_cast<double>(record.frame) / static_cast<double>(record.rate) : 0.0;
    if (record.playing != 0) {
        // The block's first frame reaches the listener latency after it was rendered; the rest follows wall time.
        const int64_t elapsed = MetronomeRenderer::nowNanos() - record.blockTimeNs - latency;
        seconds += static_cast<double>(elapsed) / 1e9;
        if (seconds < 0.0) seconds = 0.0;
    }
    out.audible_seconds = seconds;
}

int32_t EngineCore::openClipSource(const char* path, tembra_clip_info& info) {
    if (path == nullptr) return TEMBRA_ERR_ARGUMENT;
    uint32_t rate = kPreferredRate;
    {
        std::lock_guard<std::mutex> lock(commands_);
        if (outputOpen_) rate = platformDevice_->sampleRate();
    }
    // Decoded outside the command mutex: an MP3 seek table can take a while and nothing else should wait for it.
    int32_t error = TEMBRA_OK;
    tembra_clip_info prepared{};
    std::unique_ptr<AudioDecoder> decoder = AudioDecoder::open(path, rate, &prepared, &error);
    if (!decoder) return error == TEMBRA_OK ? TEMBRA_ERR_DECODE : error;
    auto source = std::make_unique<ClipSource>(std::move(decoder), prepared, path);
    std::lock_guard<std::mutex> lock(commands_);
    if (outputOpen_ && platformDevice_->sampleRate() != source->outputRate()) {
        // The stream opened at another rate while the file was being decoded: decode again for the actual rate.
        auto again = AudioDecoder::open(path, platformDevice_->sampleRate(), nullptr, &error);
        if (again) source->setDecoder(std::move(again));
    }
    int32_t handle = -1;
    for (size_t i = 0; i < clipSources_.size(); ++i) {
        if (!clipSources_[i]) {
            handle = static_cast<int32_t>(i);
            break;
        }
    }
    if (handle < 0) {
        clipSources_.push_back(nullptr);
        handle = static_cast<int32_t>(clipSources_.size() - 1);
    }
    clipSources_[static_cast<size_t>(handle)] = std::move(source);
    if (streamer_) streamer_->setSources(openSourcesLocked());
    info = prepared;
    return handle;
}

void EngineCore::closeClipSource(int32_t handle) {
    std::lock_guard<std::mutex> lock(commands_);
    if (handle < 0 || static_cast<size_t>(handle) >= clipSources_.size() || !clipSources_[static_cast<size_t>(handle)]) return;
    ClipSource* source = clipSources_[static_cast<size_t>(handle)].get();
    if (hostArrangement_ && hostArrangement_->references(source)) return;  // the host removes it from the arrangement first
    std::unique_ptr<ClipSource> closing = std::move(clipSources_[static_cast<size_t>(handle)]);
    if (streamer_) streamer_->setSources(openSourcesLocked());  // no producer touches it from here on
    retireSourceLocked(std::move(closing));
    sweepRetiredSourcesLocked(state() != TEMBRA_AUDIO_TRANSPORT);
}

void EngineCore::retireSourceLocked(std::unique_ptr<ClipSource> source) {
    // A snapshot the callback is still rendering may name the source for up to two blocks after the arrangement
    // that dropped it was published; with no transport stream nothing reads it and it goes at once.
    if (!source) return;
    if (state() != TEMBRA_AUDIO_TRANSPORT || !transport_) return;
    retiredSources_.push_back(RetiredSource{std::move(source), transport_->blocksRendered()});
}

void EngineCore::sweepRetiredSourcesLocked(bool outputClosed) {
    if (outputClosed || !transport_) {
        retiredSources_.clear();
        return;
    }
    const uint64_t blocks = transport_->blocksRendered();
    retiredSources_.erase(std::remove_if(retiredSources_.begin(), retiredSources_.end(),
                                         [&](const RetiredSource& retired) { return blocks >= retired.retiredAtBlock + 2; }),
                          retiredSources_.end());
}

#ifdef TEMBRA_BUILD_TESTS
void EngineCore::setDevice(std::unique_ptr<AudioDevice> device) {
    std::lock_guard<std::mutex> lock(commands_);
    stopListeningLocked();
    stopMetronomeLocked();
    closeTransportLocked();
    stopToneLocked();
    std::atomic_store(&analyzer_, std::shared_ptr<TunerAnalyzer>());
    gateDb_ = TunerAnalyzer::kGateOff;
    platformDevice_ = std::move(device);
    telemetryDevice_.store(platformDevice_.get(), std::memory_order_release);
    // Forget the metronome's telemetry and click tables so every test starts from "before any start". Tests read
    // telemetry on this thread only, so freeing the renderers here is safe.
    callbackMetronome_.store(nullptr, std::memory_order_release);
    telemetryMetronome_.store(nullptr, std::memory_order_release);
    metronome_.reset();
    retiredRenderers_.clear();
    retiredSets_.clear();
    builtInRate_ = 0;
    reportedLatencyNs_.store(0);
    // The transport and its sources likewise: a test's clip sources never outlive it.
    callbackTransport_.store(nullptr, std::memory_order_release);
    telemetryTransport_.store(nullptr, std::memory_order_release);
    transport_.reset();
    retiredTransports_.clear();
    streamer_.reset();
    clipSources_.clear();
    retiredSources_.clear();
    hostArrangement_.reset();
    hostPlaying_ = false;
}
#endif

}  // namespace tembra
