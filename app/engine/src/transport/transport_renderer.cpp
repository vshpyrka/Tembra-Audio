#include "transport/transport_renderer.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "tembra_engine.h"

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
      activeClip_(TEMBRA_MAX_TRACKS, -1),
      monitor_(rate),
      monitorScratch_(kMaxBlockFrames * 2, 0.0f) {
    tempo_.rate = rate;
    for (TrackStrip& strip : strips_) strip.setRate(rate);
    meter_.setRate(rate);
}

void TransportRenderer::requestRecordStart(int64_t startFrame, int64_t preRollFrames) {
    RecordRequest request;
    request.sequence = ++recordSequence_;
    request.start = startFrame;
    request.preRoll = preRollFrames < 0 ? 0 : preRollFrames;
    recordRequests_.write(request);
    recordCommand_.store(kRecordStart, std::memory_order_seq_cst);
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
    masterClips_.store(0, std::memory_order_relaxed);
    // Feature 012: the mixer's parameters, the meter and its record start over with the session (the arrangement seeds
    // gain and audibility, the session pushes the pans, the sheet turns metering on).
    mixParams_.reset();
    appliedMix_ = UINT64_MAX;
    appliedMixTracks_ = UINT32_MAX;
    meter_.reset();
    meteringOn_ = false;
    meterPublished_ = false;
    clampThisBlock_ = false;
    mixRecord_.store(MixRecord{});
    command_.store(kNone, std::memory_order_release);
    playing_.store(false, std::memory_order_release);
    readersActive_.store(false, std::memory_order_release);
    captureState_ = CaptureState::Idle;
    preRolling_ = false;
    muteFrom_ = INT64_MAX;
    generationsSeen_ = false;
    monitor_.setEnabled(false);
    monitor_.advance(static_cast<uint32_t>(rate_));
    monitoring_.store(false, std::memory_order_release);
    monitorRequested_.store(0, std::memory_order_release);
    recordCommand_.store(kRecordNone, std::memory_order_release);
}

// --- Audio thread -----------------------------------------------------------------------------------------------

void TransportRenderer::render(float* out, uint32_t frames, const InputBlock& input) {
    rendering_.store(true, std::memory_order_seq_cst);
    for (uint32_t done = 0; done < frames;) {
        const uint32_t chunk = std::min<uint32_t>(frames - done, static_cast<uint32_t>(kMaxBlockFrames));
        InputBlock piece = input;
        if (input.interleaved != nullptr) {
            if (done >= input.frames) {
                piece = InputBlock{};
                piece.inputGeneration = input.inputGeneration;
                piece.outputGeneration = input.outputGeneration;
            } else {
                piece.interleaved = input.interleaved + static_cast<size_t>(done) * input.channels;
                piece.frames = std::min<uint32_t>(chunk, input.frames - done);
                piece.shortFrames = done == 0 ? input.shortFrames : 0;
            }
        }
        renderBlock(out + static_cast<size_t>(done) * 2, chunk, piece);
        done += chunk;
    }
    rendering_.store(false, std::memory_order_seq_cst);
}

void TransportRenderer::applyMix(uint32_t trackCount) {
    // Feature 012 (research R4): the strips' targets come from the lock-free parameter block, read once per changed
    // version; the strip applies the pan law for its source kind and ramps both sides over 10 ms.
    const uint32_t count = std::min<uint32_t>(trackCount, TEMBRA_MAX_TRACKS);
    for (uint32_t t = 0; t < count; ++t) {
        const float gain = mixParams_.gain(t);
        const float pan = mixParams_.pan(t);
        const bool audible = mixParams_.audible(t);
        if (stripSet_[t]) {
            strips_[t].setTarget(gain, pan, audible);
        } else {
            strips_[t].snap(gain, pan, audible);
            stripSet_[t] = true;
        }
    }
    // The monitor follows the armed track's fader and audibility (feature 010, research R12); it sits at the centre.
    const int32_t armed = armedTrack_.load(std::memory_order_acquire);
    if (armed >= 0 && static_cast<uint32_t>(armed) < count) {
        const float gain = mixParams_.gain(static_cast<uint32_t>(armed));
        const bool audible = mixParams_.audible(static_cast<uint32_t>(armed));
        if (monitorTrack_ != armed) {
            monitor_.snap(gain, audible);
            monitorTrack_ = armed;
        } else {
            monitor_.setTarget(gain, audible);
        }
    }
}

void TransportRenderer::finishBus(float* out, uint32_t frames) {
    bus_.finish(out, frames);
    if (bus_.clampEngaged()) clampThisBlock_ = true;
}

void TransportRenderer::renderMonitor(uint32_t frames, const float* inputSlice, uint32_t inputChannels) {
    if (!monitor_.active() || inputSlice == nullptr || inputChannels == 0) {
        monitor_.advance(frames);
        return;
    }
    monitor_.process(inputSlice, inputChannels, monitorScratch_.data(), frames);
    bus_.add(monitorScratch_.data(), frames);
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

uint32_t TransportRenderer::renderSegment(int64_t position, uint32_t frames, float* out, const float* inputSlice, uint32_t inputChannels, bool clips) {
    const ArrangementSnapshot& arrangement = *arrangement_;
    const uint32_t trackCount = std::min<uint32_t>(arrangement.trackCount, TEMBRA_MAX_TRACKS);
    const uint32_t clipCount = std::min<uint32_t>(arrangement.clipCount, TEMBRA_MAX_CLIPS);
    bus_.begin(frames);
    for (uint32_t t = 0; t < trackCount; ++t) activeClip_[t] = -1;
    if (clips) {
        for (uint32_t i = 0; i < clipCount; ++i) {
            if (clipStart_[i] <= position && position < clipEnd_[i] && arrangement.clips[i].track < trackCount) {
                activeClip_[arrangement.clips[i].track] = static_cast<int32_t>(i);
            }
        }
    }
    // The armed track's clips at or after the take start are being replaced: silent while the take runs (research R11).
    const int32_t armed = armedTrack_.load(std::memory_order_acquire);
    if (armed >= 0 && static_cast<uint32_t>(armed) < trackCount && position >= muteFrom_) activeClip_[armed] = -1;
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
        if (meteringOn_) meter_.measureStrip(t, stripScratch_.data(), frames);  // post-fader, post-pan (feature 012)
        bus_.add(stripScratch_.data(), frames);
        ++active;
    }
    renderMonitor(frames, inputSlice, inputChannels);
    finishBus(out, frames);
    return active;
}

uint32_t TransportRenderer::renderPlaying(int64_t position, uint32_t frames, float* out, const InputBlock& input, uint32_t inputOffset) {
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
        // The pre-roll ends and the take begins at preRollStart_: a boundary like a clip edge (research R10).
        if (preRolling_ && preRollStart_ > a && preRollStart_ < b) b = preRollStart_;
        if (muteFrom_ != INT64_MAX && muteFrom_ > a && muteFrom_ < b) b = muteFrom_;
        const uint32_t n = static_cast<uint32_t>(b - a);
        const bool clips = !(preRolling_ && a < preRollStart_);
        const uint32_t inputAt = inputOffset + cursor;
        const float* slice = (input.interleaved != nullptr && inputAt + n <= input.frames) ? input.interleaved + static_cast<size_t>(inputAt) * input.channels : nullptr;
        active = std::max(active, renderSegment(a, n, out + static_cast<size_t>(cursor) * 2, slice, input.channels, clips));
        cursor += n;
    }
    return active;
}

void TransportRenderer::renderBlock(float* out, uint32_t frames, const InputBlock& input) {
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
    // Feature 012: the mixer's parameters (one version load per block) and the metering switch.
    const uint64_t mixVersion = mixParams_.version();
    if (mixVersion != appliedMix_ || arrangement.trackCount != appliedMixTracks_) {
        applyMix(arrangement.trackCount);
        appliedMix_ = mixVersion;
        appliedMixTracks_ = arrangement.trackCount;
    }
    const bool metering = mixParams_.metering();
    if (metering && !meteringOn_) meter_.reset();  // turned on: the meters start from silence, not from stale history
    meteringOn_ = metering;
    clampThisBlock_ = false;
    if (meteringOn_) meter_.beginBlock(frames);
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

    // 3a. Capture (feature 010): the armed source, the platform's reopens, monitoring and the record commands.
    CaptureSource* capture = capture_.load(std::memory_order_acquire);
    const bool armed = capture != nullptr;
    if (!armed) {
        captureState_ = CaptureState::Idle;
        preRolling_ = false;
        muteFrom_ = INT64_MAX;
        generationsSeen_ = false;
    } else if (captureState_ == CaptureState::Idle) {
        captureState_ = CaptureState::Armed;
        muteFrom_ = INT64_MAX;
        generationsSeen_ = false;
    }
    if (!generationsSeen_) {
        seenInputGeneration_ = input.inputGeneration;
        seenOutputGeneration_ = input.outputGeneration;
        generationsSeen_ = true;
    } else {
        if (input.outputGeneration != seenOutputGeneration_) {
            seenOutputGeneration_ = input.outputGeneration;
            monitorRequested_.store(0, std::memory_order_release);  // the route may be the speaker now (research R12)
        }
        if (input.inputGeneration != seenInputGeneration_) {
            seenInputGeneration_ = input.inputGeneration;
            if (captureState_ == CaptureState::Recording && capture != nullptr) capture->endTake(TEMBRA_TAKE_STOP_INPUT_LOST);
        }
    }
    const bool monitorWanted = armed && monitorRequested_.load(std::memory_order_acquire) != 0;
    monitor_.setEnabled(monitorWanted);
    monitoring_.store(monitorWanted, std::memory_order_release);
    const uint32_t recordCommand = recordCommand_.exchange(kRecordNone, std::memory_order_seq_cst);
    uint32_t keepFrom = kNoSplit;  // the offset within this block where capture begins, when it begins here
    if (recordCommand == kRecordStart && captureState_ == CaptureState::Armed) {
        const RecordRequest& request = recordRequests_.latest();
        appliedRecordSequence_ = request.sequence;
        if (!clock_.playing && request.preRoll > 0) {
            preRollReturnFrame_ = clock_.frame;
            preRollStart_ = request.start;
            clock_.frame = request.start - request.preRoll;
            preRolling_ = true;
            captureState_ = CaptureState::PreRoll;
        } else {
            captureState_ = CaptureState::Recording;
            takeStart_ = clock_.frame;
            muteFrom_ = takeStart_;
            keepFrom = 0;
        }
        if (!clock_.playing) {
            clock_.playing = true;
            releasing_ = false;
            releaseRemaining_ = 0;
            readersActive_.store(true, std::memory_order_release);
            playing_.store(true, std::memory_order_release);
            anchor = true;
            requestSources(preRolling_ ? preRollStart_ : clock_.frame);
        }
    } else if (recordCommand == kRecordCancel && captureState_ == CaptureState::PreRoll) {
        clock_.playing = false;
        clock_.frame = preRollReturnFrame_;
        preRolling_ = false;
        captureState_ = CaptureState::Armed;
        playing_.store(false, std::memory_order_release);
        readersActive_.store(false, std::memory_order_release);
        releasing_ = false;
        releaseRemaining_ = 0;
    } else if (recordCommand == kRecordStop || recordCommand == kRecordCancel) {
        if (captureState_ == CaptureState::PreRoll) {
            // A stop during the pre-roll is a cancel: nothing was captured.
            clock_.playing = false;
            clock_.frame = preRollReturnFrame_;
            preRolling_ = false;
            playing_.store(false, std::memory_order_release);
            readersActive_.store(false, std::memory_order_release);
            releasing_ = false;
            releaseRemaining_ = 0;
        }
        if (captureState_ == CaptureState::Recording || captureState_ == CaptureState::PreRoll) {
            captureState_ = CaptureState::Armed;
            muteFrom_ = INT64_MAX;
        }
    }
    if (captureState_ == CaptureState::Recording && capture != nullptr && capture->engineStopRequested()) {
        captureState_ = CaptureState::Armed;  // the recorder thread (cap, disk, write failure) or the input reopen ended it
        muteFrom_ = INT64_MAX;
    }
    // The pre-roll ends inside this block: the take begins at that offset (sample-accurate, research R10).
    const int64_t blockStart = clock_.frame;
    uint32_t preRollEndsAt = kNoSplit;
    if (captureState_ == CaptureState::PreRoll && clock_.playing) {
        if (preRollStart_ <= blockStart) preRollEndsAt = 0;
        else if (preRollStart_ < blockStart + static_cast<int64_t>(frames)) preRollEndsAt = static_cast<uint32_t>(preRollStart_ - blockStart);
        if (preRollEndsAt != kNoSplit) {
            keepFrom = preRollEndsAt;
            takeStart_ = preRollStart_;
            muteFrom_ = takeStart_;
        }
    }

    // 4. The click: started on play, anchored on play, seek and tempo change, released on pause and when disabled.
    // During the count-in it runs whatever the toggle says (FR-015) and, with the toggle off, stops at the start.
    MetronomeRenderer* metronome = metronome_.load(std::memory_order_acquire);
    const bool clickWanted = metronomeEnabled_.load(std::memory_order_acquire);
    uint32_t clickCut = kNoSplit;
    if (metronome != nullptr) {
        if (clock_.playing && (clickWanted || preRolling_)) {
            if (!clickRunning_) {
                metronome->start();
                clickRunning_ = true;
                anchor = true;
            }
            if (anchor || tempoChanged) metronome->anchor(metronome->framePosition(), tempo_.frameToBeats(clock_.frame));
            if (preRollEndsAt != kNoSplit && !clickWanted) clickCut = preRollEndsAt;
        } else if (clickRunning_) {
            metronome->stop();
            clickRunning_ = false;
        }
    }

    // 5. The mix.
    uint32_t active = 0;
    if (clock_.playing) {
        active = renderPlaying(blockStart, frames, out, input, 0);
        clock_.advance(frames);
    } else if (releasing_) {
        const uint32_t done = releaseFrames_ - releaseRemaining_;
        const uint32_t tail = std::min(frames, releaseRemaining_);
        active = renderPlaying(blockStart + done, tail, out, input, 0);
        for (uint32_t i = 0; i < tail; ++i) {
            const float gain = static_cast<float>(releaseFrames_ - (done + i)) / static_cast<float>(releaseFrames_);
            out[2 * i] *= gain;
            out[2 * i + 1] *= gain;
        }
        if (tail < frames) {
            // The rest of the block: the monitor alone (the input is heard while stopped, research R12).
            bus_.begin(frames - tail);
            const float* slice = (input.interleaved != nullptr && input.frames >= frames) ? input.interleaved + static_cast<size_t>(tail) * input.channels : nullptr;
            renderMonitor(frames - tail, slice, input.channels);
            finishBus(out + static_cast<size_t>(tail) * 2, frames - tail);
        }
        releaseRemaining_ -= tail;
        if (releaseRemaining_ == 0) {
            releasing_ = false;
            readersActive_.store(false, std::memory_order_release);
        }
    } else if (monitor_.active() && input.interleaved != nullptr && input.frames >= frames) {
        bus_.begin(frames);
        renderMonitor(frames, input.interleaved, input.channels);
        finishBus(out, frames);
    } else {
        monitor_.advance(frames);
        std::memset(out, 0, static_cast<size_t>(frames) * 2 * sizeof(float));
    }
    if (preRollEndsAt != kNoSplit) {
        preRolling_ = false;
        captureState_ = CaptureState::Recording;
    }
    // 5a. The master after the clamp, before the click (feature 012): the clamp count for the health counters whether or
    // not the sheet is open; the meter's master measurement and latch only while metering is on.
    if (clampThisBlock_) masterClips_.fetch_add(1, std::memory_order_relaxed);
    if (meteringOn_) meter_.measureMaster(out, frames, clampThisBlock_);

    // 6. The click after the bus: never attenuated by the master (research R4). With the toggle off the count-in's
    // click is stopped at the take's start, before the downbeat tick would sound.
    if (metronome != nullptr) {
        if (clickCut != kNoSplit) {
            if (clickCut > 0) metronome->render(clickScratch_.data(), clickCut);
            metronome->stop();
            clickRunning_ = false;
            metronome->render(clickScratch_.data() + static_cast<size_t>(clickCut) * 2, frames - clickCut);
            for (uint32_t i = 0; i < frames * 2; ++i) out[i] += clickScratch_[i];
        } else {
            metronome->render(clickScratch_.data(), frames);
            if (clickRunning_ || metronome->isActive()) {
                for (uint32_t i = 0; i < frames * 2; ++i) out[i] += clickScratch_[i];
            }
        }
    }

    // 6a. Capture: the meter from every block, the kept frames while the take runs (research R5, R8, R9).
    if (capture != nullptr && input.interleaved != nullptr && input.frames > 0) {
        capture->measure(input.interleaved, input.channels, input.frames, input.shortFrames);
        if (captureState_ == CaptureState::Recording && keepFrom != kNoSplit) {
            capture->capture(input.interleaved, input.channels, input.frames, keepFrom);
        } else if (captureState_ == CaptureState::Recording) {
            capture->capture(input.interleaved, input.channels, input.frames, 0);
        }
    }

    // 7. Publish.
    const uint64_t blocks = blocks_.load(std::memory_order_relaxed) + 1;
    TransportRecord record;
    record.frame = blockStart;
    record.playing = clock_.playing ? 1u : 0u;
    record.rate = rate_;
    record.blockTimeNs = blockTimeNs;
    record.framesPerBeat = tempo_.framesPerBeat();
    record.seekSequence = clock_.appliedSeekSequence();
    record.blocks = blocks;
    record.recording = captureState_ == CaptureState::Recording ? 1u : 0u;
    record.preRoll = captureState_ == CaptureState::PreRoll ? 1u : 0u;
    if (captureState_ == CaptureState::PreRoll && record.framesPerBeat > 0.0) {
        record.countInBeatsLeft = static_cast<int32_t>(std::ceil(static_cast<double>(preRollStart_ - blockStart) / record.framesPerBeat - 1e-9));
        if (record.countInBeatsLeft < 1) record.countInBeatsLeft = 1;
    }
    record.takeStartFrame = takeStart_;
    record_.store(record);
    if (capture != nullptr) {
        capture->publish(true, captureState_ == CaptureState::Recording, captureState_ == CaptureState::PreRoll, monitorWanted, takeStart_, blocks);
    }
    // Feature 012: one meter record per block while metering is on; one zeroed record when it turns off.
    if (meteringOn_) {
        meter_.publish(mixRecord_, blocks, arrangement.trackCount);
        meterPublished_ = true;
    } else if (meterPublished_) {
        meter_.publishSilent(mixRecord_, blocks);
        meterPublished_ = false;
    }
    clipsActive_.store(active, std::memory_order_relaxed);
    blocks_.fetch_add(1, std::memory_order_acq_rel);
}

}  // namespace tembra
