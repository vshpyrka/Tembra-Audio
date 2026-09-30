#include "metronome/metronome_renderer.h"

#include <algorithm>
#include <cmath>
#include <ctime>

namespace tembra {

namespace {

constexpr double kPi = 3.14159265358979323846;

}  // namespace

MetronomeRenderer::MetronomeRenderer(uint32_t rate)
    : rate_(rate),
      rampFrames_(std::max<uint32_t>(1, static_cast<uint32_t>(std::llround(kPreTickRampSeconds * rate)))),
      stopFrames_(std::max<uint32_t>(1, static_cast<uint32_t>(std::llround(kStopReleaseSeconds * rate)))),
      maxGainStep_(2.0f / static_cast<float>(rampFrames_)) {}

int64_t MetronomeRenderer::nowNanos() {
#if defined(__APPLE__)
    return static_cast<int64_t>(clock_gettime_nsec_np(CLOCK_UPTIME_RAW));
#else
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return static_cast<int64_t>(now.tv_sec) * 1000000000LL + now.tv_nsec;
#endif
}

// --- Host thread ------------------------------------------------------------------------------------------------

void MetronomeRenderer::setGrid(const MetronomeGrid& grid) { gridSlots_.write(grid); }

void MetronomeRenderer::setSet(const ClickSet* set) { setSlots_.write(set); }

void MetronomeRenderer::start() { command_.store(kStart, std::memory_order_release); }

void MetronomeRenderer::stop() { command_.store(kStop, std::memory_order_release); }

void MetronomeRenderer::preview(tembra_click_level level) { previewRequest_.store(static_cast<int32_t>(level), std::memory_order_release); }

void MetronomeRenderer::anchor(int64_t frame, double beatPosition) {
    AnchorRequest request;
    request.sequence = ++anchorSequence_;
    request.frame = frame;
    request.beatPosition = beatPosition;
    anchorSlots_.write(request);
}

bool MetronomeRenderer::isActive() const {
    // Pending commands first: the callback marks itself active before it consumes one.
    if (command_.load(std::memory_order_acquire) != kNone) return true;
    if (previewRequest_.load(std::memory_order_acquire) >= 0) return true;
    return active_.load(std::memory_order_acquire);
}

bool MetronomeRenderer::references(const ClickSet* set) const {
    return voiceSets_[0].load(std::memory_order_acquire) == set || voiceSets_[1].load(std::memory_order_acquire) == set;
}

void MetronomeRenderer::silence() {
    voice_ = Voice{};
    releasing_ = Voice{};
    ticking_ = false;
    restartAtBeat_ = false;
    command_.store(kNone, std::memory_order_release);
    previewRequest_.store(-1, std::memory_order_release);
    running_.store(false, std::memory_order_release);
    active_.store(false, std::memory_order_release);
    voiceSets_[0].store(nullptr, std::memory_order_release);
    voiceSets_[1].store(nullptr, std::memory_order_release);
}

void MetronomeRenderer::readTick(int64_t latencyNs, int64_t nowNs, tembra_metronome_tick* out) const {
    if (out == nullptr) return;
    const MetronomeTick record = tick_.load();
    out->tick_index = record.tickIndex;
    out->beat_index = record.beatIndex;
    out->level = record.level;
    const int64_t offsetNs = std::llround(static_cast<double>(record.tickFrame - record.blockStartFrame) * 1e9 / static_cast<double>(rate_));
    out->audible_in_ns = record.blockTimeNs + offsetNs + latencyNs - nowNs;
    out->beat_period_ns = record.bpm > 0.0 ? std::llround(60e9 / record.bpm) : 0;
    out->running = running_.load(std::memory_order_acquire) ? 1u : 0u;
    out->sample_rate = rate_;
}

// --- Audio thread -----------------------------------------------------------------------------------------------

const ClickSample& MetronomeRenderer::sampleFor(const ClickSet& set, uint32_t level) {
    if (level == TEMBRA_CLICK_SUB) return set.sub;
    if (level == TEMBRA_CLICK_ACCENT) return set.accent;
    return set.click;
}

void MetronomeRenderer::beginRun() {
    ticking_ = true;
    restartAtBeat_ = false;
    emitted_ = 0;
    nextTick_ = 0;
    lastTickFrame_ = static_cast<double>(framePos_) - grid_.framesPerTick(rate_);
    record_ = MetronomeTick{};
    record_.bpm = grid_.bpm;
    running_.store(true, std::memory_order_release);
}

void MetronomeRenderer::endRun() {
    ticking_ = false;
    running_.store(false, std::memory_order_release);
    release(voice_, stopFrames_);
    release(releasing_, stopFrames_);
}

void MetronomeRenderer::release(Voice& voice, uint32_t frames) {
    if (!voice.active || voice.releasing) return;
    voice.releasing = true;
    voice.releaseFrom = voice.gain;
    voice.releasePosition = 0;
    voice.releaseLength = frames;
}

void MetronomeRenderer::applyGrid(const MetronomeGrid& next) {
    const MetronomeGrid previous = grid_;
    grid_ = next;
    if (!ticking_) return;
    if (next.ticksPerBeat != previous.ticksPerBeat) {
        // Keep the beat, drop the rest of the current beat's subdivisions, land on the next beat boundary (R1).
        const uint32_t oldTpb = previous.ticksPerBeat;
        const uint32_t inBeat = nextTick_ % oldTpb;
        const double oldFramesPerTick = 60.0 / next.bpm * static_cast<double>(rate_) / static_cast<double>(oldTpb);
        const double ticksToBoundary = inBeat == 0 ? 1.0 : static_cast<double>(oldTpb - inBeat + 1);
        const double boundary = lastTickFrame_ + ticksToBoundary * oldFramesPerTick;
        uint32_t nextBeat = (nextTick_ / oldTpb + (inBeat == 0 ? 0u : 1u)) % std::max<uint32_t>(1, previous.beats());
        if (next.beats() != previous.beats() || nextBeat >= next.beats()) nextBeat = 0;
        nextTick_ = nextBeat * next.ticksPerBeat;
        lastTickFrame_ = boundary - next.framesPerTick(rate_);
        restartAtBeat_ = false;
    } else if (next.ticksPerBar != previous.ticksPerBar) {
        restartAtBeat_ = true;  // the next beat boundary becomes beat 1
    }
    // A tempo change needs nothing: the next tick is recomputed from lastTickFrame_ with the new tempo.
}

void MetronomeRenderer::applyAnchor(const AnchorRequest& request) {
    const double framesPerTick = grid_.framesPerTick(rate_);
    const double ticks = std::max(0.0, request.beatPosition) * static_cast<double>(grid_.ticksPerBeat);
    const double nextWhole = std::ceil(ticks - 1e-9);
    const double nextFrame = static_cast<double>(request.frame) + (nextWhole - ticks) * framesPerTick;
    nextTick_ = static_cast<uint32_t>(static_cast<uint64_t>(nextWhole) % grid_.ticksPerBar);
    lastTickFrame_ = nextFrame - framesPerTick;
    restartAtBeat_ = false;
}

void MetronomeRenderer::startVoice(const ClickSample& sample, const ClickSet* set) {
    if (voice_.active) {
        // On schedule the pre-tick ramp has brought the old voice to zero; otherwise release what is left.
        if (voice_.releasing || voice_.gain > maxGainStep_) {
            releasing_ = voice_;
            if (!releasing_.releasing) release(releasing_, rampFrames_);
        }
    }
    voice_ = Voice{};
    if (sample.pcm.empty()) return;
    voice_.pcm = sample.pcm.data();
    voice_.length = static_cast<uint32_t>(sample.pcm.size());
    voice_.set = set;
    voice_.active = true;
}

void MetronomeRenderer::emitTick(int64_t tickFrame, double exactFrame, const ClickSet* set) {
    if (restartAtBeat_ && nextTick_ % grid_.ticksPerBeat == 0) {
        nextTick_ = 0;
        restartAtBeat_ = false;
    }
    const uint32_t index = nextTick_ < grid_.ticksPerBar ? nextTick_ : nextTick_ % grid_.ticksPerBeat;
    const uint32_t level = grid_.levels[index];
    if (set != nullptr) startVoice(sampleFor(*set, level), set);
    lastTickFrame_ = exactFrame;
    ++emitted_;
    record_.tickIndex = emitted_;
    record_.beatIndex = index / grid_.ticksPerBeat;
    record_.level = level;
    record_.tickFrame = tickFrame;
    record_.bpm = grid_.bpm;
    nextTick_ = (index + 1) % grid_.ticksPerBar;
}

float MetronomeRenderer::voiceSample(Voice& voice, int64_t frame, int64_t nextTick) {
    if (!voice.active) return 0.0f;
    float gain;
    if (voice.releasing) {
        if (voice.releasePosition >= voice.releaseLength) {
            voice.active = false;
            return 0.0f;
        }
        const double shape = 0.5 + 0.5 * std::cos(kPi * static_cast<double>(voice.releasePosition) / static_cast<double>(voice.releaseLength));
        gain = static_cast<float>(voice.releaseFrom * shape);
        ++voice.releasePosition;
    } else {
        // Pre-tick ramp (R5): only when the voice would still be sounding at the next tick.
        float target = 1.0f;
        if (nextTick != kNoTick) {
            const int64_t distance = nextTick - frame;
            const int64_t remaining = static_cast<int64_t>(voice.length - voice.position);
            if (distance > 0 && distance <= static_cast<int64_t>(rampFrames_) && remaining > distance) {
                target = static_cast<float>(0.5 - 0.5 * std::cos(kPi * static_cast<double>(distance) / static_cast<double>(rampFrames_)));
            }
        }
        const float step = std::clamp(target - voice.gain, -maxGainStep_, maxGainStep_);
        voice.gain += step;
        gain = voice.gain;
    }
    const float out = voice.pcm[voice.position] * gain;
    if (++voice.position >= voice.length) voice.active = false;
    return out;
}

void MetronomeRenderer::renderSegment(float* out, uint32_t from, uint32_t to, int64_t nextTick) {
    for (uint32_t i = from; i < to; ++i) {
        const int64_t frame = framePos_ + i;
        const float sample = voiceSample(voice_, frame, nextTick) + voiceSample(releasing_, frame, kNoTick);
        out[2 * i] = sample;
        out[2 * i + 1] = sample;
    }
}

void MetronomeRenderer::render(float* out, uint32_t frames) {
    const int64_t blockTimeNs = nowNanos();
    if (command_.load(std::memory_order_acquire) != kNone || previewRequest_.load(std::memory_order_acquire) >= 0) {
        active_.store(true, std::memory_order_release);
    }
    const uint32_t command = command_.exchange(kNone, std::memory_order_acq_rel);
    const int32_t preview = previewRequest_.exchange(-1, std::memory_order_acq_rel);

    const MetronomeGrid& published = gridSlots_.latest();
    if (published != grid_) applyGrid(published);
    const ClickSet* set = setSlots_.latest();
    if (command == kStart) {
        beginRun();
    } else if (command == kStop) {
        endRun();
    }
    const AnchorRequest& anchorRequest = anchorSlots_.latest();
    if (anchorRequest.sequence != appliedAnchor_) {
        appliedAnchor_ = anchorRequest.sequence;
        if (ticking_) applyAnchor(anchorRequest);
    }
    if (preview >= 0 && set != nullptr) startVoice(sampleFor(*set, static_cast<uint32_t>(preview)), set);

    const int64_t blockEnd = framePos_ + frames;
    uint32_t cursor = 0;
    int64_t pendingTick = kNoTick;
    if (ticking_) {
        const double framesPerTick = grid_.framesPerTick(rate_);
        // Bounded: framesPerTick is at least rate / 16, so a block holds at most frames / framesPerTick + 1 ticks.
        for (;;) {
            const double exact = lastTickFrame_ + framesPerTick;
            const int64_t tickFrame = std::llround(exact);
            if (tickFrame >= blockEnd) {
                pendingTick = tickFrame;
                break;
            }
            const uint32_t offset = tickFrame <= framePos_ ? 0u : static_cast<uint32_t>(tickFrame - framePos_);
            renderSegment(out, cursor, offset, tickFrame);
            emitTick(tickFrame, exact, set);
            cursor = offset;
        }
    }
    renderSegment(out, cursor, frames, pendingTick);

    record_.blockStartFrame = framePos_;
    record_.blockTimeNs = blockTimeNs;
    tick_.store(record_);
    framePos_ = blockEnd;
    voiceSets_[0].store(voice_.active ? voice_.set : nullptr, std::memory_order_release);
    voiceSets_[1].store(releasing_.active ? releasing_.set : nullptr, std::memory_order_release);
    blocks_.fetch_add(1, std::memory_order_acq_rel);
    if (!ticking_ && !voice_.active && !releasing_.active) active_.store(false, std::memory_order_release);
}

}  // namespace tembra
