#include "transport/transport_renderer.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace tembra {

TransportRenderer::TransportRenderer(uint32_t rate)
    : rate_(rate),
      releaseFrames_(std::max<uint32_t>(1, static_cast<uint32_t>(std::llround(kPauseReleaseSeconds * rate)))),
      bus_(rate),
      clipScratch_(kMaxBlockFrames * ClipSource::kMaxChannels, 0.0f),
      stripScratch_(kMaxBlockFrames * 2, 0.0f),
      clickScratch_(kMaxBlockFrames * 2, 0.0f),
      clipStart_(TEMBRA_MAX_CLIPS, 0),
      clipEnd_(TEMBRA_MAX_CLIPS, 0),
      activeClip_(TEMBRA_MAX_TRACKS, -1) {
    tempo_.rate = rate;
    for (TrackStrip& strip : strips_) strip.setRate(rate);
}

// --- Host -------------------------------------------------------------------------------------------------------

void TransportRenderer::silence() {
    clock_.playing = false;
    clock_.frame = 0;
    releasing_ = false;
    releaseRemaining_ = 0;
    clickRunning_ = false;
    // A renderer outlives its transport sessions (one per stream rate): the health counters start each one at zero.
    starvation_.store(0, std::memory_order_relaxed);
    clipsActive_.store(0, std::memory_order_relaxed);
    command_.store(kNone, std::memory_order_release);
    playing_.store(false, std::memory_order_release);
    readersActive_.store(false, std::memory_order_release);
}

// --- Audio thread -----------------------------------------------------------------------------------------------

void TransportRenderer::render(float* out, uint32_t frames) {
    for (uint32_t done = 0; done < frames;) {
        const uint32_t chunk = std::min<uint32_t>(frames - done, static_cast<uint32_t>(kMaxBlockFrames));
        renderBlock(out + static_cast<size_t>(done) * 2, chunk);
        done += chunk;
    }
}

void TransportRenderer::applyTracks(const TrackTable& table) {
    const uint32_t count = std::min<uint32_t>(table.count, TEMBRA_MAX_TRACKS);
    for (uint32_t t = 0; t < count; ++t) {
        const bool audible = table.tracks[t].audible != 0;
        if (stripSet_[t]) {
            strips_[t].setTarget(table.tracks[t].gain, audible);
        } else {
            strips_[t].snap(table.tracks[t].gain, audible);
            stripSet_[t] = true;
        }
    }
}

void TransportRenderer::recomputeClipFrames() {
    const ArrangementSnapshot& arrangement = *arrangement_;
    const uint32_t count = std::min<uint32_t>(arrangement.clipCount, TEMBRA_MAX_CLIPS);
    for (uint32_t i = 0; i < count; ++i) {
        clipStart_[i] = tempo_.beatsToFrame(arrangement.clips[i].startBeats);
        clipEnd_[i] = clipStart_[i] + arrangement.clips[i].outLength;
    }
    // Clips are sorted by track then start: a later start on the same track cuts the earlier clip (FR-027).
    for (uint32_t i = 0; i + 1 < count; ++i) {
        if (arrangement.clips[i + 1].track == arrangement.clips[i].track && clipStart_[i + 1] < clipEnd_[i]) {
            clipEnd_[i] = std::max(clipStart_[i], clipStart_[i + 1]);
        }
    }
}

void TransportRenderer::requestSources(int64_t position) {
    // The first frame every source will be asked for from [position] on (the host does the same in planPrime);
    // a source whose fifo does not already hold it is parked and requested now, so the streamer has it ready.
    const ArrangementSnapshot& arrangement = *arrangement_;
    const uint32_t count = std::min<uint32_t>(arrangement.clipCount, TEMBRA_MAX_CLIPS);
    for (uint32_t i = 0; i < count; ++i) {
        if (arrangement.clips[i].source != nullptr) arrangement.clips[i].source->rtNeededAt = INT64_MAX;
    }
    for (uint32_t i = 0; i < count; ++i) {
        const ClipState& clip = arrangement.clips[i];
        if (clip.source == nullptr || clipEnd_[i] <= position) continue;
        const int64_t neededAt = std::max(clipStart_[i], position);
        const int64_t frame = clipStart_[i] <= position ? clip.outOffset + (position - clipStart_[i]) : clip.outOffset;
        if (neededAt < clip.source->rtNeededAt) {
            clip.source->rtNeededAt = neededAt;
            clip.source->rtNeededFrame = frame;
        }
    }
    for (uint32_t i = 0; i < count; ++i) {
        ClipSource* source = arrangement.clips[i].source;
        if (source == nullptr || source->rtNeededAt == INT64_MAX) continue;
        if (!source->holds(source->rtNeededFrame)) source->park(source->rtNeededFrame, source->rtNeededAt);
        source->rtNeededAt = INT64_MAX;
    }
}

uint32_t TransportRenderer::renderSegment(int64_t position, uint32_t frames, float* out) {
    const ArrangementSnapshot& arrangement = *arrangement_;
    const uint32_t trackCount = std::min<uint32_t>(arrangement.trackCount, TEMBRA_MAX_TRACKS);
    const uint32_t clipCount = std::min<uint32_t>(arrangement.clipCount, TEMBRA_MAX_CLIPS);
    bus_.begin(frames);
    for (uint32_t t = 0; t < trackCount; ++t) activeClip_[t] = -1;
    for (uint32_t i = 0; i < clipCount; ++i) {
        if (clipStart_[i] <= position && position < clipEnd_[i] && arrangement.clips[i].track < trackCount) {
            activeClip_[arrangement.clips[i].track] = static_cast<int32_t>(i);
        }
    }
    uint32_t active = 0;
    for (uint32_t t = 0; t < trackCount; ++t) {
        if (activeClip_[t] < 0) {
            strips_[t].advance(frames);
            continue;
        }
        const uint32_t i = static_cast<uint32_t>(activeClip_[t]);
        const ClipState& clip = arrangement.clips[i];
        if (clip.source == nullptr) {
            strips_[t].advance(frames);
            continue;
        }
        const int64_t sourceFrame = clip.outOffset + (position - clipStart_[i]);
        if (!clip.source->pop(sourceFrame, frames, clipScratch_.data())) starvation_.fetch_add(1, std::memory_order_relaxed);
        strips_[t].process(clipScratch_.data(), clip.source->channels(), stripScratch_.data(), frames);
        bus_.add(stripScratch_.data(), frames);
        ++active;
    }
    bus_.finish(out, frames);
    return active;
}

uint32_t TransportRenderer::renderPlaying(int64_t position, uint32_t frames, float* out) {
    const uint32_t clipCount = std::min<uint32_t>(arrangement_->clipCount, TEMBRA_MAX_CLIPS);
    const int64_t end = position + frames;
    uint32_t active = 0;
    for (uint32_t cursor = 0; cursor < frames;) {
        const int64_t a = position + cursor;
        int64_t b = end;
        for (uint32_t i = 0; i < clipCount; ++i) {
            const int64_t s = clipStart_[i];
            const int64_t e = clipEnd_[i];
            if (s > a && s < b) b = s;
            if (e > a && e < b) b = e;
        }
        const uint32_t n = static_cast<uint32_t>(b - a);
        active = std::max(active, renderSegment(a, n, out + static_cast<size_t>(cursor) * 2));
        cursor += n;
    }
    return active;
}

void TransportRenderer::renderBlock(float* out, uint32_t frames) {
    const int64_t blockTimeNs = MetronomeRenderer::nowNanos();

    // 1. The newest snapshots.
    const ArrangementSnapshot& arrangement = arrangementSlots_.latest();
    arrangement_ = &arrangement;
    const TempoMap& tempo = tempoSlots_.latest();
    bool tempoChanged = false;
    if (tempo != tempo_) {
        tempo_ = tempo;
        tempoChanged = true;
    }
    const TrackTable& tracks = trackSlots_.latest();
    if (tracks.version != appliedTracks_) {
        applyTracks(tracks);
        appliedTracks_ = tracks.version;
    }
    bool clipsMoved = false;
    if (framesVersion_ != arrangement.version || tempoChanged) {
        recomputeClipFrames();
        framesVersion_ = arrangement.version;
        clipsMoved = true;
    }
    const float master = masterTarget_.load(std::memory_order_acquire);
    if (master != masterApplied_) {
        masterApplied_ = master;
        bus_.setMaster(master);
    }

    // 2. A pending seek: the clock moves, a release tail is cut, and the sources are asked for the new position.
    bool anchor = false;
    if (clock_.applyPending()) {
        anchor = true;
        if (releasing_) {
            releasing_ = false;
            releaseRemaining_ = 0;
            readersActive_.store(false, std::memory_order_release);
        }
        if (clock_.playing) requestSources(clock_.frame);
    }

    // 3. Play and pause.
    const uint32_t command = command_.exchange(kNone, std::memory_order_acq_rel);
    if (command == kPlay && !clock_.playing) {
        clock_.playing = true;
        releasing_ = false;
        releaseRemaining_ = 0;
        readersActive_.store(true, std::memory_order_release);
        playing_.store(true, std::memory_order_release);
        anchor = true;
        requestSources(clock_.frame);
    } else if (command == kPause && clock_.playing) {
        clock_.playing = false;
        playing_.store(false, std::memory_order_release);
        releasing_ = true;
        releaseRemaining_ = releaseFrames_;
    } else if (clipsMoved && clock_.playing) {
        requestSources(clock_.frame);  // a new arrangement or tempo while playing: sources may need new positions
    }

    // 4. The click: started on play, anchored on play, seek and tempo change, released on pause and when disabled.
    MetronomeRenderer* metronome = metronome_.load(std::memory_order_acquire);
    if (metronome != nullptr) {
        if (clock_.playing && metronomeEnabled_.load(std::memory_order_acquire)) {
            if (!clickRunning_) {
                metronome->start();
                clickRunning_ = true;
                anchor = true;
            }
            if (anchor || tempoChanged) metronome->anchor(metronome->framePosition(), tempo_.frameToBeats(clock_.frame));
        } else if (clickRunning_) {
            metronome->stop();
            clickRunning_ = false;
        }
    }

    // 5. The mix.
    const int64_t blockStart = clock_.frame;
    uint32_t active = 0;
    if (clock_.playing) {
        active = renderPlaying(blockStart, frames, out);
        clock_.advance(frames);
    } else if (releasing_) {
        const uint32_t done = releaseFrames_ - releaseRemaining_;
        const uint32_t tail = std::min(frames, releaseRemaining_);
        active = renderPlaying(blockStart + done, tail, out);
        for (uint32_t i = 0; i < tail; ++i) {
            const float gain = static_cast<float>(releaseFrames_ - (done + i)) / static_cast<float>(releaseFrames_);
            out[2 * i] *= gain;
            out[2 * i + 1] *= gain;
        }
        if (tail < frames) std::memset(out + static_cast<size_t>(tail) * 2, 0, static_cast<size_t>(frames - tail) * 2 * sizeof(float));
        releaseRemaining_ -= tail;
        if (releaseRemaining_ == 0) {
            releasing_ = false;
            readersActive_.store(false, std::memory_order_release);
        }
    } else {
        std::memset(out, 0, static_cast<size_t>(frames) * 2 * sizeof(float));
    }

    // 6. The click after the bus: never attenuated by the master (research R4).
    if (metronome != nullptr) {
        metronome->render(clickScratch_.data(), frames);
        if (clickRunning_ || metronome->isActive()) {
            for (uint32_t i = 0; i < frames * 2; ++i) out[i] += clickScratch_[i];
        }
    }

    // 7. Publish.
    TransportRecord record;
    record.frame = blockStart;
    record.playing = clock_.playing ? 1u : 0u;
    record.rate = rate_;
    record.blockTimeNs = blockTimeNs;
    record.framesPerBeat = tempo_.framesPerBeat();
    record.seekSequence = clock_.appliedSeekSequence();
    record.blocks = blocks_.load(std::memory_order_relaxed) + 1;
    record_.store(record);
    clipsActive_.store(active, std::memory_order_relaxed);
    blocks_.fetch_add(1, std::memory_order_acq_rel);
}

}  // namespace tembra
