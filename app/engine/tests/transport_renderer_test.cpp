// The transport renderer (feature 009; contracts/engine-c-abi-v6.md "Renderer rules", research R3, R4, R6, R7),
// driven directly through render() at 96, 256 and 1024 frames with FakeDecoder sources whose samples name their
// frame, and the real DiskStreamer serviced synchronously between blocks so every prime goes through its code.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <memory>
#include <vector>

#include "capture/capture_record.h"
#include "capture/capture_source.h"
#include "fake_decoder.h"
#include "metronome/click_set.h"
#include "metronome/metronome_grid.h"
#include "metronome/metronome_renderer.h"
#include "tembra_engine.h"
#include "timeline/arrangement_snapshot.h"
#include "timeline/clip_source.h"
#include "timeline/disk_streamer.h"
#include "timeline/prime_plan.h"
#include "transport/tempo_map.h"
#include "transport/transport_record.h"
#include "transport/transport_renderer.h"

namespace {

constexpr uint32_t kRate = 48000;
constexpr std::array<uint32_t, 3> kBlocks{96, 256, 1024};
constexpr int64_t kLongSource = 48000LL * 700;  // 11 minutes 40 seconds

tembra::TempoMap tempoOf(double bpm, uint32_t beatsPerBar = 4) {
    tembra::TempoMap tempo;
    tempo.bpm = bpm;
    tempo.beatsPerBar = beatsPerBar;
    tempo.rate = kRate;
    return tempo;
}

constexpr int64_t kRampPeriod = 500000;  // the ramp wraps so [base] + the value stays under the master clamp

/** A source whose left sample is [base] + (frame mod kRampPeriod) / 1e6: the frame is readable from any output sample. */
tembra::FakeDecoder::Sample offsetRamp(float base) {
    return [base](int64_t frame, uint32_t) { return base + static_cast<float>(frame % kRampPeriod) * 1e-6f; };
}

tembra::FakeDecoder::Sample constant(float value) {
    return [value](int64_t, uint32_t) { return value; };
}

struct ClipSpec {
    uint32_t track;
    tembra::ClipSource* source;
    double startBeats;
    int64_t offset;
    int64_t length;
};

class Harness {
public:
    Harness() : renderer_(std::make_unique<tembra::TransportRenderer>(kRate)), streamer_(kRate, &renderer_->readersActiveFlag()) {
        renderer_->setTempo(tempoOf(124.0));
    }

    tembra::ClipSource* addSource(int64_t frames, tembra::FakeDecoder::Sample sample, uint32_t channels = 1) {
        auto decoder = std::make_unique<tembra::FakeDecoder>(frames, channels, kRate, std::move(sample));
        sources_.push_back(std::make_unique<tembra::ClipSource>(std::move(decoder), tembra::fakeInfo(frames, channels, kRate), "fake"));
        std::vector<tembra::ClipSource*> views;
        for (auto& source : sources_) views.push_back(source.get());
        streamer_.setSources(views);
        return sources_.back().get();
    }

    /** Builds, validates and publishes an arrangement of [clips] over [tracks] tracks at unity, audible. */
    void arrange(const std::vector<ClipSpec>& clips, uint32_t tracks = 2) {
        auto raw = std::make_unique<tembra_arrangement>();
        raw->track_count = tracks;
        raw->clip_count = static_cast<uint32_t>(clips.size());
        for (uint32_t t = 0; t < tracks; ++t) {
            raw->tracks[t].gain_linear = 1.0f;
            raw->tracks[t].audible = 1;
        }
        std::vector<tembra::ClipSource*> table;
        for (auto& source : sources_) table.push_back(source.get());
        for (size_t i = 0; i < clips.size(); ++i) {
            raw->clips[i].track = clips[i].track;
            raw->clips[i].source = static_cast<int32_t>(std::find(table.begin(), table.end(), clips[i].source) - table.begin());
            raw->clips[i].start_beats = clips[i].startBeats;
            raw->clips[i].offset_frames = clips[i].offset;
            raw->clips[i].length_frames = clips[i].length;
        }
        snapshot_ = std::make_unique<tembra::ArrangementSnapshot>();
        REQUIRE(tembra::ArrangementSnapshot::validate(*raw, table.data(), table.size()) == TEMBRA_OK);
        snapshot_->fill(*raw, table.data(), table.size(), kRate, ++version_);
        snapshot_->sortClipsByTrackAndStart();
        renderer_->setArrangement(*snapshot_);
        tembra::TrackTable tracksTable;
        tracksTable.version = version_;
        tracksTable.count = tracks;
        for (uint32_t t = 0; t < tracks; ++t) tracksTable.tracks[t] = tembra::TrackState{1.0f, 1u};
        renderer_->setTracks(tracksTable);
        streamer_.invalidate();
    }

    void setTrack(uint32_t index, float gain, bool audible, uint32_t tracks = 2) {
        tembra::TrackTable table;
        table.version = ++version_;
        table.count = tracks;
        for (uint32_t t = 0; t < tracks; ++t) table.tracks[t] = tembra::TrackState{1.0f, 1u};
        table.tracks[index] = tembra::TrackState{gain, audible ? 1u : 0u};
        renderer_->setTracks(table);
    }

    /** What EngineCore::transportPlay does: prime synchronously while the readers are inactive, then play. */
    void play(const tembra::TempoMap& tempo = tempoOf(124.0)) {
        if (!renderer_->readersActive()) streamer_.prime(positionForPrime(), tembra::planPrime(*snapshot_, tempo, positionForPrime()));
        renderer_->play();
    }

    int64_t positionForPrime() const {
        const tembra::TransportRecord record = renderer_->record();
        return record.seekSequence < renderer_->pendingSeekSequence() ? pendingSeekFrame_ : record.frame;
    }

    void seek(int64_t frame) {
        pendingSeekFrame_ = frame;
        renderer_->seek(frame);
    }

    /** Renders [frames] in [block]-frame blocks (the last one shorter), servicing the streamer after each; left channel. */
    std::vector<float> render(uint32_t frames, uint32_t block) {
        std::vector<float> left;
        left.reserve(frames);
        std::vector<float> stereo(static_cast<size_t>(block) * 2);
        for (uint32_t done = 0; done < frames;) {
            const uint32_t n = std::min(block, frames - done);
            renderer_->render(stereo.data(), n);
            for (uint32_t i = 0; i < n; ++i) left.push_back(stereo[2 * i]);
            streamer_.serviceOnce();
            done += n;
        }
        return left;
    }

    tembra::TransportRenderer& renderer() { return *renderer_; }
    tembra::DiskStreamer& streamer() { return streamer_; }

    // --- Feature 010: the duplex path (the fake device feeds one input block per rendered block) ---------------------

    /** Arms with a capture source of [channels] and [alignment] frames (the host's tembra_transport_arm). */
    tembra::CaptureSource& arm(uint32_t channels = 1, int64_t alignment = 0) {
        capture_ = std::make_unique<tembra::CaptureSource>(kRate);
        capture_->prepare(channels, alignment);
        renderer_->setCapture(capture_.get());
        return *capture_;
    }

    /**
     * Renders [frames] in [block]-frame blocks with an input block per render whose samples encode the input stream's
     * frame index (0.2 + (frame mod 500000) / 1e6, both channels), servicing the streamer after each; left channel.
     */
    std::vector<float> renderDuplex(uint32_t frames, uint32_t block, uint32_t channels = 1, float inputBase = 0.2f, uint32_t inputGeneration = 0, uint32_t outputGeneration = 0) {
        std::vector<float> left;
        left.reserve(frames);
        std::vector<float> stereo(static_cast<size_t>(block) * 2);
        std::vector<float> input(static_cast<size_t>(block) * channels);
        for (uint32_t done = 0; done < frames;) {
            const uint32_t n = std::min(block, frames - done);
            for (uint32_t i = 0; i < n; ++i) {
                const float value = inputBase + static_cast<float>((inputFrames_ + i) % kRampPeriod) * 1e-6f;
                for (uint32_t c = 0; c < channels; ++c) input[i * channels + c] = value;
            }
            tembra::InputBlock in;
            in.interleaved = input.data();
            in.channels = channels;
            in.frames = n;
            in.shortFrames = 0;
            in.inputGeneration = inputGeneration;
            in.outputGeneration = outputGeneration;
            renderer_->render(stereo.data(), n, in);
            inputFrames_ += n;
            for (uint32_t i = 0; i < n; ++i) left.push_back(stereo[2 * i]);
            streamer_.serviceOnce();
            done += n;
        }
        return left;
    }

    /** Everything the callback pushed into the capture fifo so far (mono). */
    std::vector<float> drainCapture() {
        std::vector<float> out(capture_->fifo().available());
        const size_t n = capture_->fifo().read(out.data(), out.size());
        out.resize(n);
        return out;
    }

    int64_t inputFramesFed() const { return inputFrames_; }

private:
    std::unique_ptr<tembra::CaptureSource> capture_;
    int64_t inputFrames_ = 0;
    std::unique_ptr<tembra::TransportRenderer> renderer_;
    tembra::DiskStreamer streamer_;
    std::vector<std::unique_ptr<tembra::ClipSource>> sources_;
    std::unique_ptr<tembra::ArrangementSnapshot> snapshot_;
    uint64_t version_ = 0;
    int64_t pendingSeekFrame_ = 0;
};

/** The frame (mod kRampPeriod) a left sample from offsetRamp([base]) encodes, or -1 when it is not from that source. */
int64_t frameOf(float sample, float base) {
    if (sample < base - 1e-4f || sample > base + 0.5f + 1e-4f) return -1;
    return std::llround((sample - base) * 1e6f);
}

int64_t wrapped(int64_t frame) { return frame % kRampPeriod; }

tembra::ClickSet impulseSet() {
    tembra::ClickSet set;
    set.sub = tembra::ClickSample{{0.1f}, kRate};
    set.click = tembra::ClickSample{{0.3f}, kRate};
    set.accent = tembra::ClickSample{{0.9f}, kRate};
    return set;
}

tembra::MetronomeGrid sevenEight(double bpm) {
    tembra::MetronomeGrid grid;
    grid.bpm = bpm;
    grid.ticksPerBeat = 1;
    grid.ticksPerBar = 7;
    grid.levels[0] = TEMBRA_CLICK_ACCENT;
    for (uint32_t i = 1; i < 7; ++i) grid.levels[i] = TEMBRA_CLICK_CLICK;
    return grid;
}

tembra::MetronomeGrid fourFour(double bpm) {
    tembra::MetronomeGrid grid;
    grid.bpm = bpm;
    grid.ticksPerBeat = 1;
    grid.ticksPerBar = 4;
    grid.levels[0] = TEMBRA_CLICK_ACCENT;
    grid.levels[1] = grid.levels[2] = grid.levels[3] = TEMBRA_CLICK_CLICK;
    return grid;
}

}  // namespace

TEST_CASE("a clip at beat 4 at 124 BPM starts at frame 92903 at every block size", "[transport][renderer]") {
    for (uint32_t block : kBlocks) {
        INFO("block " << block);
        Harness h;
        tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f));
        h.arrange({{0, source, 4.0, 0, 48000}});
        h.play();
        const auto left = h.render(100000, block);
        for (int64_t i = 0; i < 92903; ++i) REQUIRE(left[static_cast<size_t>(i)] == 0.0f);
        REQUIRE(frameOf(left[92903], 0.2f) == 0);
        REQUIRE(frameOf(left[92903 + 1000], 0.2f) == 1000);
        REQUIRE(h.renderer().starvation() == 0);
    }
}

TEST_CASE("a seek into a clip starts mid-clip on the exact source frame", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f));
    h.arrange({{0, source, 4.0, 1000, 100000}});
    const tembra::TempoMap tempo = tempoOf(124.0);
    const int64_t target = tempo.beatsToFrame(6.0);  // 139355: 46452 frames into the clip
    h.seek(target);
    h.render(256, 256);  // the seek applies at this block (stopped: silence)
    REQUIRE(h.renderer().record().frame == target);
    h.play();
    const auto left = h.render(4096, 256);
    REQUIRE(frameOf(left[0], 0.2f) == 1000 + (target - 92903));
    REQUIRE(frameOf(left[4095], 0.2f) == 1000 + (target - 92903) + 4095);
    REQUIRE(h.renderer().starvation() == 0);
}

TEST_CASE("a clip split at an arbitrary beat renders sample-identical to the whole", "[transport][renderer]") {
    const tembra::TempoMap tempo = tempoOf(124.0);
    const int64_t wholeStart = tempo.beatsToFrame(4.0);
    const int64_t splitFrame = tempo.beatsToFrame(4.7);
    const int64_t firstLength = splitFrame - wholeStart;  // 16258
    auto renderWhole = [&] {
        Harness h;
        tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f), 2);
        h.arrange({{0, source, 4.0, 500, 48000}});
        h.play();
        return h.render(200000, 256);
    };
    auto renderSplit = [&] {
        Harness h;
        tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f), 2);
        h.arrange({{0, source, 4.7, 500 + firstLength, 48000 - firstLength}, {0, source, 4.0, 500, firstLength}});
        h.play();
        return h.render(200000, 256);
    };
    const auto whole = renderWhole();
    const auto split = renderSplit();
    REQUIRE(whole.size() == split.size());
    REQUIRE(whole == split);
    REQUIRE(frameOf(whole[static_cast<size_t>(splitFrame)], 0.2f) == 500 + firstLength);
    REQUIRE(frameOf(whole[static_cast<size_t>(wholeStart + 48000 - 1)], 0.2f) == 500 + 48000 - 1);
    REQUIRE(whole[static_cast<size_t>(wholeStart + 48000)] == 0.0f);
}

TEST_CASE("two clips overlapping on one track give the later start precedence", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* a = h.addSource(kLongSource, offsetRamp(0.2f));
    tembra::ClipSource* b = h.addSource(kLongSource, offsetRamp(0.45f));
    // At 120 BPM clip A (beat 0, 1 s) ends at beat 2; B at beat 1.5 cuts it at frame 36000 (research R3, FR-027).
    const tembra::TempoMap tempo = tempoOf(120.0);
    h.renderer().setTempo(tempo);
    h.arrange({{0, a, 0.0, 0, 48000}, {0, b, 1.5, 0, 24000}});
    h.play(tempo);
    const auto left = h.render(70000, 256);
    REQUIRE(frameOf(left[35999], 0.2f) == 35999);
    REQUIRE(frameOf(left[36000], 0.45f) == 0);
    REQUIRE(frameOf(left[36000 + 23999], 0.45f) == 23999);
    REQUIRE(left[60000] == 0.0f);  // A does not resume after B
    REQUIRE(h.renderer().starvation() == 0);
}

TEST_CASE("pause releases over 5 ms and keeps the position", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.5f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    h.play();
    h.render(4096, 1024);
    REQUIRE(h.renderer().readersActive());
    h.renderer().pause();
    const auto tail = h.render(1024, 1024);
    REQUIRE(tail[0] > 0.49f);
    REQUIRE(std::abs(tail[120] - 0.25f) < 0.01f);  // half way through the 240-frame release
    REQUIRE(tail[240] == 0.0f);
    REQUIRE(tail[1023] == 0.0f);
    for (size_t i = 1; i < 240; ++i) REQUIRE(tail[i] <= tail[i - 1]);
    const tembra::TransportRecord record = h.renderer().record();
    REQUIRE(record.frame == 4096);
    REQUIRE(record.playing == 0);
    REQUIRE_FALSE(h.renderer().readersActive());
    const auto silent = h.render(1024, 1024);
    REQUIRE(silent[0] == 0.0f);
    h.renderer().pause();  // idempotent while stopped
    REQUIRE(h.renderer().record().frame == 4096);
    // Play resumes from the pause frame, re-primed by the host; the record names the block's start.
    h.play();
    const auto resumed = h.render(1024, 1024);
    REQUIRE(resumed[0] == 0.5f);
    REQUIRE(h.renderer().record().frame == 4096);
    h.render(1024, 1024);
    REQUIRE(h.renderer().record().frame == 4096 + 1024);
}

TEST_CASE("a seek while playing resumes within 100 ms of audio at the new position", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    h.play();
    h.render(48000, 256);
    const tembra::TempoMap tempo = tempoOf(124.0);
    const int64_t target = tempo.beatsToFrame(60.0);
    h.seek(target);
    const auto left = h.render(4800, 256);  // 100 ms
    size_t resumed = left.size();
    for (size_t i = 0; i < left.size(); ++i) {
        if (left[i] != 0.0f) {
            resumed = i;
            break;
        }
    }
    REQUIRE(resumed < left.size());
    REQUIRE(frameOf(left[resumed], 0.2f) == wrapped(target + static_cast<int64_t>(resumed)));
    REQUIRE(frameOf(left.back(), 0.2f) == wrapped(target + 4799));
    REQUIRE(h.renderer().record().frame == target + 4800 - 192);  // the last (192-frame) block's start
}

TEST_CASE("silence() starts the next transport session's health counters at zero", "[transport][renderer]") {
    // The engine keeps one renderer per stream rate across transport sessions; a seek's silent parks must not be
    // reported as the next session's starvation.
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    h.play();
    h.render(48000, 256);
    h.seek(tempoOf(124.0).beatsToFrame(60.0));
    h.render(4800, 256);
    REQUIRE(h.renderer().starvation() > 0);  // the parked source rendered silence until the streamer served it
    REQUIRE(h.renderer().clipsActive() == 1);
    h.renderer().silence();
    REQUIRE(h.renderer().starvation() == 0);
    REQUIRE(h.renderer().clipsActive() == 0);
}

TEST_CASE("an arrangement swap applies at a block boundary", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* a = h.addSource(kLongSource, offsetRamp(0.2f));
    tembra::ClipSource* b = h.addSource(kLongSource, offsetRamp(0.45f));
    h.arrange({{0, a, 0.0, 0, kLongSource}});
    h.play();
    h.render(2048, 256);
    h.arrange({{0, b, 0.0, 0, kLongSource}});
    const auto left = h.render(512, 256);
    // Source B was never primed: the first block parks it (silence) and the streamer serves the request; from then on B.
    size_t first = left.size();
    for (size_t i = 0; i < left.size(); ++i) {
        if (left[i] != 0.0f) {
            first = i;
            break;
        }
    }
    REQUIRE(first % 256 == 0);
    REQUIRE(frameOf(left[first], 0.45f) == 2048 + static_cast<int64_t>(first));
    REQUIRE(frameOf(left[511], 0.45f) == 2048 + 511);
}

TEST_CASE("the anchored metronome clicks on the clip start at beat 0 and stays within one frame for ten minutes", "[transport][renderer][metronome]") {
    Harness h;
    tembra::MetronomeRenderer metronome(kRate);
    const tembra::ClickSet set = impulseSet();
    metronome.setGrid(fourFour(124.0));
    metronome.setSet(&set);
    // The metronome's own frame counter is not the transport's: it has rendered before (the standalone metronome).
    std::vector<float> scratch(2 * 1000);
    metronome.render(scratch.data(), 1000);
    h.renderer().setMetronome(&metronome);
    h.renderer().setMetronomeEnabled(true);
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.5f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    h.play();
    const tembra::TempoMap tempo = tempoOf(124.0);
    const uint32_t tenMinutes = 600u * kRate;
    const auto left = h.render(tenMinutes, 1024);
    REQUIRE(std::abs(left[0] - (0.5f + 0.9f)) < 1e-5f);  // beat 1 and the clip on the same frame
    REQUIRE(std::abs(left[1] - 0.5f) < 1e-5f);
    std::vector<int64_t> onsets;
    for (size_t i = 0; i < left.size(); ++i) {
        if (left[i] > 0.55f) onsets.push_back(static_cast<int64_t>(i));
    }
    int64_t beats = 0;
    while (tempo.beatsToFrame(static_cast<double>(beats)) < static_cast<int64_t>(tenMinutes)) ++beats;
    REQUIRE(static_cast<int64_t>(onsets.size()) == beats);
    for (size_t k = 0; k < onsets.size(); ++k) {
        const int64_t expected = tempo.beatsToFrame(static_cast<double>(k));
        INFO("beat " << k);
        REQUIRE(std::abs(onsets[k] - expected) <= 1);
        REQUIRE(left[static_cast<size_t>(onsets[k])] > (k % 4 == 0 ? 1.3f : 0.75f));  // accent on beat 1, click elsewhere
    }
}

TEST_CASE("the metronome enabled while stopped renders silence (FR-038) and joins at the next beat when enabled mid-playback", "[transport][renderer][metronome]") {
    Harness h;
    tembra::MetronomeRenderer metronome(kRate);
    const tembra::ClickSet set = impulseSet();
    metronome.setGrid(fourFour(124.0));
    metronome.setSet(&set);
    h.renderer().setMetronome(&metronome);
    h.renderer().setMetronomeEnabled(true);
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.0f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    const auto stopped = h.render(8192, 1024);
    for (float sample : stopped) REQUIRE(sample == 0.0f);
    REQUIRE_FALSE(metronome.isActive());

    h.renderer().setMetronomeEnabled(false);
    h.play();
    const tembra::TempoMap tempo = tempoOf(124.0);
    const int64_t enableAt = tempo.beatsToFrame(1.5);
    const auto before = h.render(static_cast<uint32_t>(enableAt), 256);
    for (float sample : before) REQUIRE(sample == 0.0f);
    h.renderer().setMetronomeEnabled(true);
    const auto after = h.render(100000, 256);
    size_t first = after.size();
    for (size_t i = 0; i < after.size(); ++i) {
        if (after[i] != 0.0f) {
            first = i;
            break;
        }
    }
    REQUIRE(first < after.size());
    REQUIRE(std::abs(static_cast<int64_t>(first) + enableAt - tempo.beatsToFrame(2.0)) <= 1);
    REQUIRE(after[first] > 0.25f);  // beat 3 of the bar: a click, not an accent
    REQUIRE(after[first] < 0.35f);
    // Switching it off releases the voice; pause stops the click too.
    h.renderer().setMetronomeEnabled(false);
    h.render(1024, 256);
    h.render(1024, 256);
    REQUIRE_FALSE(metronome.isRunning());
}

TEST_CASE("a tempo change re-derives the clip frames and re-anchors the click", "[transport][renderer][metronome]") {
    Harness h;
    tembra::MetronomeRenderer metronome(kRate);
    const tembra::ClickSet set = impulseSet();
    metronome.setGrid(fourFour(120.0));
    metronome.setSet(&set);
    h.renderer().setMetronome(&metronome);
    h.renderer().setMetronomeEnabled(true);
    h.renderer().setTempo(tempoOf(120.0));
    tembra::ClipSource* source = h.addSource(kLongSource, offsetRamp(0.2f));
    h.arrange({{0, source, 8.0, 0, 48000}});  // at 120 BPM: frame 192000; at 60 BPM: 384000
    h.play(tempoOf(120.0));
    h.render(48000, 1024);  // one second: beats 0 and 1 clicked
    metronome.setGrid(fourFour(60.0));
    h.renderer().setTempo(tempoOf(60.0));
    const auto left = h.render(400000, 1024);
    // The clip now starts at 384000 (= 336000 into this run), not at 192000 (checked beside the beats, not on them).
    REQUIRE(frameOf(left[192000 + 100 - 48000], 0.2f) == -1);
    REQUIRE(left[192000 + 100 - 48000] == 0.0f);
    REQUIRE(frameOf(left[384000 + 100 - 48000], 0.2f) == 100);
    // The click continues at the 60 BPM grid aligned to the transport's beats: beat 3 at 144000 (96000 here).
    REQUIRE(left[144000 - 48000] > 0.25f);
    REQUIRE(left[144000 - 48000 + 1] == 0.0f);
}

TEST_CASE("a track strip's rendered gain matches its target within 0.1 dB after the ramp (SC-006)", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.5f));
    h.arrange({{1, source, 0.0, 0, kLongSource}});
    h.setTrack(1, 0.25f, true);
    h.play();
    auto left = h.render(4096, 256);
    REQUIRE(std::abs(20.0 * std::log10(left.back() / 0.125)) < 0.1);
    h.setTrack(1, 1.5f, true);
    left = h.render(4096, 256);
    REQUIRE(std::abs(20.0 * std::log10(left.back() / 0.75)) < 0.1);
    h.setTrack(1, 1.5f, false);
    left = h.render(4096, 256);
    REQUIRE(left.back() == 0.0f);
    h.renderer().setMaster(0.5f);
    h.setTrack(1, 1.0f, true);
    left = h.render(4096, 256);
    REQUIRE(std::abs(20.0 * std::log10(left.back() / 0.25)) < 0.1);
}

TEST_CASE("the transport record is published every block", "[transport][renderer]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.5f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    REQUIRE(h.renderer().blocksRendered() == 0);
    h.render(256, 256);
    tembra::TransportRecord record = h.renderer().record();
    REQUIRE(record.blocks == 1);
    REQUIRE(record.playing == 0);
    REQUIRE(record.frame == 0);
    REQUIRE(record.rate == kRate);
    REQUIRE(std::abs(record.framesPerBeat - 23225.806451612903) < 1e-9);
    REQUIRE(record.blockTimeNs > 0);
    h.play();
    h.render(96, 96);
    record = h.renderer().record();
    REQUIRE(record.blocks == 2);
    REQUIRE(record.playing == 1);
    REQUIRE(record.frame == 0);  // the position at the block's start
    h.render(1024, 1024);
    record = h.renderer().record();
    REQUIRE(record.blocks == 3);
    REQUIRE(record.frame == 96);
    REQUIRE(h.renderer().blocksRendered() == 3);
    REQUIRE(h.renderer().clipsActive() == 1);
    // A block larger than the renderer's scratch is rendered in bounded pieces (4096, 4096, 1808).
    h.render(10000, 10000);
    REQUIRE(h.renderer().record().frame == 96 + 1024 + 10000 - 1808);
    REQUIRE(h.renderer().blocksRendered() == 6);
}

// --- Feature 010: the duplex path, the pre-roll, capture start, muting, monitoring, cancel and stop ----------------
// (contracts/engine-c-abi-v7.md "Renderer rules"; research R2, R9, R10, R11, R12).

namespace {

/** Frames of [left] above the click threshold. */
std::vector<int64_t> onsetsOf(const std::vector<float>& left, float threshold = 0.25f) {
    std::vector<int64_t> onsets;
    for (size_t i = 0; i < left.size(); ++i) {
        if (left[i] > threshold) onsets.push_back(static_cast<int64_t>(i));
    }
    return onsets;
}

}  // namespace

TEST_CASE("a pre-roll of one bar in 4/4 and two bars in 7/8 renders the count-in clicks, no clip and the beats left", "[transport][renderer][capture]") {
    struct Case { tembra::MetronomeGrid grid; uint32_t beatsPerBar; uint32_t bars; };
    const Case cases[] = {{fourFour(124.0), 4, 1}, {sevenEight(124.0), 7, 2}};
    for (const Case& c : cases) {
        INFO("beats per bar " << c.beatsPerBar << ", bars " << c.bars);
        Harness h;
        tembra::MetronomeRenderer metronome(kRate);
        const tembra::ClickSet set = impulseSet();
        metronome.setGrid(c.grid);
        metronome.setSet(&set);
        h.renderer().setMetronome(&metronome);
        h.renderer().setMetronomeEnabled(false);  // the toggle is off: the count-in clicks anyway
        const tembra::TempoMap tempo = tempoOf(124.0, c.beatsPerBar);
        h.renderer().setTempo(tempo);
        tembra::ClipSource* source = h.addSource(kLongSource, constant(0.5f));
        h.arrange({{0, source, 0.0, 0, kLongSource}});
        h.arm(1, 0);
        h.renderer().setArmedTrack(1);  // another track is armed: track 0's clip is audible after the start
        const uint32_t beats = c.beatsPerBar * c.bars;
        const int64_t preRoll = tempo.beatsToFrame(static_cast<double>(beats));
        h.renderer().requestRecordStart(0, preRoll);
        std::vector<int32_t> beatsLeft;
        std::vector<float> left;
        for (int64_t done = 0; done < preRoll;) {
            const uint32_t n = static_cast<uint32_t>(std::min<int64_t>(256, preRoll - done));
            const auto part = h.renderDuplex(n, n);
            left.insert(left.end(), part.begin(), part.end());
            const tembra::TransportRecord record = h.renderer().record();
            REQUIRE(record.preRoll == 1);
            REQUIRE(record.recording == 0);
            REQUIRE(record.playing == 1);
            REQUIRE(record.frame == -preRoll + done);
            beatsLeft.push_back(record.countInBeatsLeft);
            done += n;
        }
        // The clicks: one per beat with the accent first; no clip output (the clip at beat 0 waits for the start).
        const std::vector<int64_t> onsets = onsetsOf(left);
        REQUIRE(onsets.size() == beats);
        for (uint32_t k = 0; k < beats; ++k) {
            INFO("count-in beat " << k);
            REQUIRE(std::abs(onsets[k] - tempo.beatsToFrame(static_cast<double>(k))) <= 1);
            REQUIRE(left[static_cast<size_t>(onsets[k])] > (k % c.beatsPerBar == 0 ? 0.85f : 0.25f));
            REQUIRE(left[static_cast<size_t>(onsets[k])] < (k % c.beatsPerBar == 0 ? 0.95f : 0.35f));
        }
        for (size_t i = 0; i < left.size(); ++i) {
            if (std::find(onsets.begin(), onsets.end(), static_cast<int64_t>(i)) == onsets.end()) REQUIRE(left[i] == 0.0f);
        }
        REQUIRE(beatsLeft.front() == static_cast<int32_t>(beats));
        REQUIRE(beatsLeft.back() == 1);
        for (size_t i = 1; i < beatsLeft.size(); ++i) REQUIRE(beatsLeft[i] <= beatsLeft[i - 1]);
        // At the start the clip plays, the take runs and the click is silent because the toggle is off.
        const auto after = h.renderDuplex(4096, 256);
        REQUIRE(std::abs(after[0] - 0.5f) < 1e-5f);
        REQUIRE(after[1] == 0.5f);
        REQUIRE(onsetsOf(after, 0.55f).empty());
        const tembra::TransportRecord record = h.renderer().record();
        REQUIRE(record.preRoll == 0);
        REQUIRE(record.recording == 1);
        REQUIRE(record.countInBeatsLeft == -1);
        REQUIRE(record.takeStartFrame == 0);
    }
}

TEST_CASE("the metronome follows the toggle after the pre-roll", "[transport][renderer][capture][metronome]") {
    Harness h;
    tembra::MetronomeRenderer metronome(kRate);
    const tembra::ClickSet set = impulseSet();
    metronome.setGrid(fourFour(124.0));
    metronome.setSet(&set);
    h.renderer().setMetronome(&metronome);
    h.renderer().setMetronomeEnabled(true);
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.0f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    h.arm(1, 0);
    h.renderer().setArmedTrack(0);
    const tembra::TempoMap tempo = tempoOf(124.0);
    const int64_t preRoll = tempo.beatsToFrame(4.0);
    h.renderer().requestRecordStart(0, preRoll);
    const auto countIn = h.renderDuplex(static_cast<uint32_t>(preRoll), 1024);
    REQUIRE(onsetsOf(countIn).size() == 4);
    const auto after = h.renderDuplex(static_cast<uint32_t>(tempo.beatsToFrame(4.0)), 1024);
    const auto onsets = onsetsOf(after);
    REQUIRE(onsets.size() == 4);  // beats 0..3 of the take: the click continues with the toggle on
    REQUIRE(onsets[0] == 0);
    REQUIRE(after[0] > 0.85f);    // the downbeat is an accent: the bar restarts exactly at the start
}

TEST_CASE("capture begins exactly at the start: the first kept frame is the input frame alignment after it, at every block size", "[transport][renderer][capture]") {
    for (uint32_t block : kBlocks) {
        INFO("block " << block);
        Harness h;
        tembra::ClipSource* source = h.addSource(kLongSource, constant(0.0f));
        h.arrange({{0, source, 0.0, 0, kLongSource}});
        tembra::CaptureSource& capture = h.arm(1, 960);
        h.renderer().setArmedTrack(0);
        const tembra::TempoMap tempo = tempoOf(124.0);
        const int64_t start = tempo.beatsToFrame(8.0);
        h.seek(start);
        h.renderDuplex(block, block);  // the seek applies
        REQUIRE(h.renderer().record().frame == start);
        const int64_t inputAtPreRoll = h.inputFramesFed();
        const int64_t preRoll = tempo.beatsToFrame(4.0);  // 92903 frames: the split falls inside a block at every size
        capture.beginTake();
        h.renderer().requestRecordStart(start, preRoll);
        h.renderDuplex(static_cast<uint32_t>(preRoll + 20 * block), block);
        const tembra::TransportRecord record = h.renderer().record();
        REQUIRE(record.recording == 1);
        REQUIRE(record.takeStartFrame == start);
        const std::vector<float> kept = h.drainCapture();
        // The input frame delivered in the same callback as transport frame [start] is inputAtPreRoll + preRoll.
        REQUIRE(frameOf(kept.front(), 0.2f) == wrapped(inputAtPreRoll + preRoll + 960));
        REQUIRE(static_cast<int64_t>(kept.size()) == 20 * block - 960);
        for (size_t i = 1; i < kept.size(); ++i) REQUIRE(frameOf(kept[i], 0.2f) == wrapped(inputAtPreRoll + preRoll + 960 + static_cast<int64_t>(i)));
        REQUIRE(capture.framesCaptured() == static_cast<int64_t>(kept.size()));
        REQUIRE(capture.inputOverruns() == 0);
    }
}

TEST_CASE("Record while playing begins at the next block and the armed track's clips fall silent from the take start", "[transport][renderer][capture]") {
    Harness h;
    tembra::ClipSource* a = h.addSource(kLongSource, constant(0.5f));
    tembra::ClipSource* b = h.addSource(kLongSource, constant(0.25f));
    h.arrange({{0, a, 0.0, 0, kLongSource}, {1, b, 0.0, 0, kLongSource}});
    tembra::CaptureSource& capture = h.arm(1, 100);
    h.renderer().setArmedTrack(0);
    h.play();
    const auto before = h.renderDuplex(2048, 256);
    REQUIRE(std::abs(before.back() - 0.75f) < 1e-5f);  // both clips
    const int64_t inputAtRequest = h.inputFramesFed();
    capture.beginTake();
    h.renderer().requestRecordStart(0, 0);
    const auto after = h.renderDuplex(2048, 256);
    const tembra::TransportRecord record = h.renderer().record();
    REQUIRE(record.recording == 1);
    REQUIRE(record.preRoll == 0);
    REQUIRE(record.takeStartFrame == 2048);
    // The armed track's clip is silent at and after the take start; the other track plays on.
    for (float sample : after) REQUIRE(std::abs(sample - 0.25f) < 1e-5f);
    const std::vector<float> kept = h.drainCapture();
    REQUIRE(kept.size() == 2048 - 100);
    REQUIRE(frameOf(kept.front(), 0.2f) == wrapped(inputAtRequest + 100));
    // Stopping ends the capture at the block boundary; playback continues with the armed track still silent.
    h.renderer().requestRecordStop();
    const auto afterStop = h.renderDuplex(512, 256);
    REQUIRE(h.renderer().record().recording == 0);
    REQUIRE(h.renderer().record().playing == 1);
    REQUIRE(h.drainCapture().empty());
    REQUIRE(std::abs(afterStop.back() - 0.75f) < 1e-5f);  // the clip under the take plays again once the take ended
}

TEST_CASE("the monitor mix follows the armed track's gain, audibility and the master, and fades out in 5 ms", "[transport][renderer][capture]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.0f));
    h.arrange({{1, source, 0.0, 0, kLongSource}});
    h.arm(1, 0);
    h.renderer().setArmedTrack(1);
    h.setTrack(1, 0.5f, true);
    h.renderer().setMonitor(true);
    // The input samples encode their frame index (0.8 + frame / 1e6): the level heard follows the last frame fed.
    const auto lastInput = [&] { return 0.8f + static_cast<float>((h.inputFramesFed() - 1) % kRampPeriod) * 1e-6f; };
    // Stopped: the input is heard through the strip while nothing plays.
    auto left = h.renderDuplex(2048, 256, 1, 0.8f);
    REQUIRE(std::abs(left.back() - lastInput() * 0.5f) < 1e-4f);
    REQUIRE(h.renderer().record().playing == 0);
    REQUIRE(h.renderer().monitoring());
    h.setTrack(1, 0.5f, false);
    left = h.renderDuplex(2048, 256, 1, 0.8f);
    REQUIRE(left.back() == 0.0f);
    h.setTrack(1, 1.0f, true);
    h.renderer().setMaster(0.5f);
    left = h.renderDuplex(2048, 256, 1, 0.8f);
    REQUIRE(std::abs(left.back() - lastInput() * 0.5f) < 1e-4f);
    // Playing: the monitor adds to the clips (the clip on the armed track is silent only while a take runs).
    h.play();
    left = h.renderDuplex(2048, 256, 1, 0.8f);
    REQUIRE(std::abs(left.back() - lastInput() * 0.5f) < 1e-4f);
    // Off: a 5 ms fade, no step larger than 0.01.
    h.renderer().setMonitor(false);
    left = h.renderDuplex(512, 512, 1, 0.8f);
    REQUIRE(left[0] > 0.35f);
    REQUIRE(left[300] == 0.0f);
    for (size_t i = 1; i < left.size(); ++i) REQUIRE(std::abs(left[i] - left[i - 1]) <= 0.01f);
    REQUIRE_FALSE(h.renderer().monitoring());
}

TEST_CASE("an output reopen clears monitoring and an input reopen ends the take with INPUT_LOST", "[transport][renderer][capture]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.0f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    tembra::CaptureSource& capture = h.arm(1, 0);
    h.renderer().setArmedTrack(0);
    h.renderer().setMonitor(true);
    h.renderDuplex(512, 256, 1, 0.8f);
    REQUIRE(h.renderer().monitoring());
    h.renderDuplex(512, 256, 1, 0.8f, 0, 1);  // the platform reopened the output
    REQUIRE_FALSE(h.renderer().monitoring());
    h.renderer().setMonitor(true);  // the host may enable it again
    h.renderDuplex(512, 256, 1, 0.8f, 0, 1);
    REQUIRE(h.renderer().monitoring());
    h.play();
    capture.beginTake();
    h.renderer().requestRecordStart(0, 0);
    h.renderDuplex(512, 256, 1, 0.8f, 0, 1);
    REQUIRE(h.renderer().record().recording == 1);
    h.renderDuplex(512, 256, 1, 0.8f, 1, 1);  // the platform reopened the input
    REQUIRE(h.renderer().record().recording == 0);
    REQUIRE(capture.stopReason() == TEMBRA_TAKE_STOP_INPUT_LOST);
    REQUIRE(h.renderer().record().playing == 1);
    REQUIRE(h.renderer().monitoring());  // the arm and the monitoring stay
}

TEST_CASE("cancel during the pre-roll pauses and restores the position", "[transport][renderer][capture]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.5f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    tembra::CaptureSource& capture = h.arm(1, 0);
    h.renderer().setArmedTrack(1);
    const tembra::TempoMap tempo = tempoOf(124.0);
    const int64_t start = tempo.beatsToFrame(8.0);
    h.seek(start);
    h.renderDuplex(256, 256);
    capture.beginTake();
    h.renderer().requestRecordStart(start, tempo.beatsToFrame(4.0));
    h.renderDuplex(2048, 256);
    REQUIRE(h.renderer().record().preRoll == 1);
    h.renderer().requestRecordCancel();
    const auto left = h.renderDuplex(512, 256);
    const tembra::TransportRecord record = h.renderer().record();
    REQUIRE(record.playing == 0);
    REQUIRE(record.preRoll == 0);
    REQUIRE(record.recording == 0);
    REQUIRE(record.frame == start);
    REQUIRE(record.countInBeatsLeft == -1);
    for (float sample : left) REQUIRE(sample == 0.0f);
    REQUIRE(h.drainCapture().empty());
    REQUIRE(capture.framesCaptured() == 0);
}

TEST_CASE("the capture record is published every block while armed and zeroed when disarmed", "[transport][renderer][capture]") {
    Harness h;
    tembra::ClipSource* source = h.addSource(kLongSource, constant(0.0f));
    h.arrange({{0, source, 0.0, 0, kLongSource}});
    tembra::CaptureSource& capture = h.arm(2, 48);
    h.renderer().setArmedTrack(0);
    h.renderDuplex(256, 256, 2, 0.3f);
    tembra::CaptureRecord record = capture.record();
    REQUIRE(record.armed == 1);
    REQUIRE(record.recording == 0);
    REQUIRE(record.channels == 2);
    REQUIRE(record.rate == kRate);
    REQUIRE(record.peak[0] > 0.29f);
    REQUIRE(record.peak[1] > 0.29f);
    REQUIRE(record.alignmentFrames == 48);
    REQUIRE(record.blocks == 1);
    h.play();
    capture.beginTake();
    h.renderer().requestRecordStart(0, 0);
    h.renderDuplex(512, 256, 2, 0.3f);
    record = capture.record();
    REQUIRE(record.recording == 1);
    REQUIRE(record.framesCaptured == 512 - 48);
    REQUIRE(record.backlogFrames == 512 - 48);
    REQUIRE(record.takeStartFrames == 0);  // play and Record arrived in the same block: the take starts where playback did
    REQUIRE(record.blocks == 3);
    h.renderer().requestRecordStop();
    h.renderer().setCapture(nullptr);  // disarmed: the next blocks publish nothing new
    h.renderDuplex(256, 256, 2, 0.3f);
    REQUIRE(capture.record().blocks == 3);
    REQUIRE(h.renderer().record().recording == 0);
}
