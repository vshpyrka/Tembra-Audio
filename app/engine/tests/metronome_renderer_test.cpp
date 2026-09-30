// The real-time click scheduler (research R1, R2, R5, R7), driven directly through MetronomeRenderer::render with
// block sizes of 96, 256 and 1024 frames. The click set holds three one-frame impulses of distinct amplitudes so every
// tick and its level can be read straight off the output.
#include <catch2/catch_test_macros.hpp>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <initializer_list>
#include <vector>

#include "metronome/click_set.h"
#include "metronome/metronome_grid.h"
#include "metronome/metronome_renderer.h"
#include "metronome/metronome_tick.h"
#include "tembra_engine.h"

namespace {

constexpr float kSubAmp = 0.1f;
constexpr float kClickAmp = 0.3f;
constexpr float kAccentAmp = 0.9f;
constexpr std::array<uint32_t, 3> kBlocks{96, 256, 1024};

tembra::ClickSet impulseSet(uint32_t rate) {
    tembra::ClickSet set;
    set.sub = tembra::ClickSample{{kSubAmp}, rate};
    set.click = tembra::ClickSample{{kClickAmp}, rate};
    set.accent = tembra::ClickSample{{kAccentAmp}, rate};
    return set;
}

/** A set whose three levels are [seconds] of constant [value]: the output then equals the sum of voice gains × value. */
tembra::ClickSet constantSet(uint32_t rate, double seconds, float value) {
    const tembra::ClickSample sample{std::vector<float>(static_cast<size_t>(seconds * rate), value), rate};
    tembra::ClickSet set;
    set.sub = sample;
    set.click = sample;
    set.accent = sample;
    return set;
}

float ampOf(uint8_t level) {
    switch (level) {
        case TEMBRA_CLICK_SUB: return kSubAmp;
        case TEMBRA_CLICK_CLICK: return kClickAmp;
        default: return kAccentAmp;
    }
}

tembra::MetronomeGrid makeGrid(double bpm, uint32_t ticksPerBeat, std::initializer_list<uint8_t> levels) {
    tembra::MetronomeGrid grid{};
    grid.bpm = bpm;
    grid.ticksPerBeat = ticksPerBeat;
    grid.ticksPerBar = static_cast<uint32_t>(levels.size());
    std::fill(std::begin(grid.levels), std::end(grid.levels), static_cast<uint8_t>(0));
    std::copy(levels.begin(), levels.end(), std::begin(grid.levels));
    return grid;
}

/** A grid of [beats] beats × [ticksPerBeat]: Accent on beat 1, Click on the other beats, Sub between beats. */
tembra::MetronomeGrid patternGrid(double bpm, uint32_t ticksPerBeat, uint32_t beats) {
    tembra::MetronomeGrid grid{};
    grid.bpm = bpm;
    grid.ticksPerBeat = ticksPerBeat;
    grid.ticksPerBar = ticksPerBeat * beats;
    for (uint32_t i = 0; i < grid.ticksPerBar; ++i) {
        grid.levels[i] = i == 0 ? TEMBRA_CLICK_ACCENT : (i % ticksPerBeat == 0 ? TEMBRA_CLICK_CLICK : TEMBRA_CLICK_SUB);
    }
    return grid;
}

struct Onset {
    int64_t frame;
    float amp;
};

/** Renders blocks, cycling through [kBlocks] unless a fixed size is given, and records every non-zero frame. */
class Driver {
public:
    explicit Driver(tembra::MetronomeRenderer& renderer) : renderer_(renderer), buffer_(2048 * 2) {}

    void render(uint32_t frames) {
        if (buffer_.size() < 2u * frames) buffer_.resize(2u * frames);
        renderer_.render(buffer_.data(), frames);
        last_.assign(frames, 0.0f);
        for (uint32_t i = 0; i < frames; ++i) {
            const float left = buffer_[2 * i];
            last_[i] = left;
            if (left != buffer_[2 * i + 1]) stereoMismatch_ = true;
            if (left != 0.0f) onsets_.push_back(Onset{position_ + i, left});
        }
        position_ += frames;
    }

    /** Renders cycling blocks until at least [frames] frames have been rendered in total. */
    void renderUntil(int64_t frames) {
        while (position_ < frames) {
            render(kBlocks[cycle_ % kBlocks.size()]);
            ++cycle_;
        }
    }

    void renderFixed(uint32_t block, int64_t until) {
        while (position_ < until) render(block);
    }

    int64_t position() const { return position_; }
    const std::vector<Onset>& onsets() const { return onsets_; }
    const std::vector<float>& lastBlock() const { return last_; }
    bool stereoMismatch() const { return stereoMismatch_; }

    const Onset* onsetAt(int64_t frame) const {
        for (const Onset& onset : onsets_) {
            if (onset.frame == frame) return &onset;
        }
        return nullptr;
    }

    std::vector<Onset> onsetsBetween(int64_t from, int64_t to) const {
        std::vector<Onset> out;
        for (const Onset& onset : onsets_) {
            if (onset.frame >= from && onset.frame < to) out.push_back(onset);
        }
        return out;
    }

private:
    tembra::MetronomeRenderer& renderer_;
    std::vector<float> buffer_;
    std::vector<float> last_;
    std::vector<Onset> onsets_;
    int64_t position_ = 0;
    size_t cycle_ = 0;
    bool stereoMismatch_ = false;
};

}  // namespace

TEST_CASE("ten minutes of ticks land on round(i × framesPerTick) without drift", "[metronome][renderer][timing]") {
    // 44.1 kHz makes framesPerTick fractional for most combinations (at 48 kHz most are whole numbers).
    struct Case {
        uint32_t rate;
        double bpm;
    };
    for (const Case c : {Case{48000, 40.0}, Case{48000, 120.0}, Case{48000, 240.0}, Case{44100, 40.0}, Case{44100, 120.0},
                         Case{44100, 240.0}, Case{48000, 97.3}}) {
        for (uint32_t ticksPerBeat : {1u, 2u, 3u, 4u}) {
            INFO("rate " << c.rate << " bpm " << c.bpm << " ticks per beat " << ticksPerBeat);
            const tembra::ClickSet set = impulseSet(c.rate);
            const tembra::MetronomeGrid grid = patternGrid(c.bpm, ticksPerBeat, 3);
            tembra::MetronomeRenderer renderer(c.rate);
            renderer.setGrid(grid);
            renderer.setSet(&set);
            renderer.start();
            Driver driver(renderer);
            driver.renderUntil(static_cast<int64_t>(600) * c.rate);
            REQUIRE_FALSE(driver.stereoMismatch());

            const double framesPerTick = grid.framesPerTick(c.rate);
            const auto& onsets = driver.onsets();
            size_t expectedTicks = 0;
            while (std::llround(static_cast<double>(expectedTicks) * framesPerTick) < driver.position()) ++expectedTicks;
            REQUIRE(onsets.size() == expectedTicks);
            int64_t worst = 0;
            bool levelsMatch = true;
            for (size_t i = 0; i < onsets.size(); ++i) {
                const int64_t expected = std::llround(static_cast<double>(i) * framesPerTick);
                worst = std::max(worst, std::abs(onsets[i].frame - expected));
                if (onsets[i].amp != ampOf(grid.levels[i % grid.ticksPerBar])) levelsMatch = false;
            }
            REQUIRE(worst <= 1);
            REQUIRE(levelsMatch);
            const size_t last = onsets.size() - 1;
            REQUIRE(std::abs(onsets[last].frame - std::llround(static_cast<double>(last) * framesPerTick)) <= 1);
        }
    }
}

TEST_CASE("start emits tick 0 at frame 0 of the first block", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    for (uint32_t block : kBlocks) {
        INFO("block " << block);
        tembra::MetronomeRenderer renderer(48000);
        renderer.setGrid(makeGrid(120.0, 1, {TEMBRA_CLICK_CLICK, TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_SUB}));
        renderer.setSet(&set);
        REQUIRE_FALSE(renderer.isActive());
        Driver driver(renderer);
        driver.render(block);  // not started: silence
        REQUIRE(driver.onsets().empty());
        renderer.start();
        REQUIRE(renderer.isActive());
        const int64_t startFrame = driver.position();
        driver.render(block);
        REQUIRE(driver.lastBlock()[0] == kClickAmp);  // the level at index 0, whatever it is
        REQUIRE(driver.onsets().front().frame == startFrame);
    }
}

TEST_CASE("a tempo change spaces the next tick and never moves the one already rendered", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(120.0, 1, 4));  // 24 000 frames per tick
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.renderFixed(256, 24000 + 1);  // the block holding the tick at 24 000 is rendered
    REQUIRE(driver.onsetAt(24000) != nullptr);
    renderer.setGrid(patternGrid(60.0, 1, 4));  // published while that block was current
    driver.renderFixed(256, 130000);
    REQUIRE(driver.onsetAt(24000) != nullptr);
    REQUIRE(driver.onsetsBetween(24001, 72000).empty());
    REQUIRE(driver.onsetAt(72000) != nullptr);
    REQUIRE(driver.onsetAt(120000) != nullptr);
}

TEST_CASE("a large tempo increase emits one tick at the block start, not a burst", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(40.0, 4, 4));  // 18 000 frames per tick
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.renderFixed(256, 76800);  // ticks 0, 18 000, 36 000, 54 000, 72 000 (indices 0–4); 4 800 frames later
    REQUIRE(driver.onsets().size() == 5u);
    REQUIRE(renderer.lastTick().tickIndex == 5u);
    renderer.setGrid(patternGrid(240.0, 4, 4));  // 3 000 frames per tick: the next tick "was due" at 75 000
    driver.render(256);
    REQUIRE(driver.onsets().size() == 6u);  // exactly one tick in this block
    REQUIRE(driver.onsetAt(76800) != nullptr);
    const tembra::MetronomeTick tick = renderer.lastTick();
    REQUIRE(tick.tickIndex == 6u);  // advanced by one, not by the ticks the gap could hold
    REQUIRE(tick.beatIndex == 1u);  // index 5: beat 2's first subdivision
    REQUIRE(tick.tickFrame == 76800);
    driver.renderFixed(256, 90000);
    REQUIRE(driver.onsetsBetween(76801, 79800).empty());
    REQUIRE(driver.onsetAt(79800) != nullptr);
    REQUIRE(driver.onsetAt(82800) != nullptr);
    REQUIRE(driver.onsetAt(85800) != nullptr);
    REQUIRE(driver.onsetAt(85800)->amp == kClickAmp);  // index 8: beat 3, so the bar stayed in step
}

TEST_CASE("a ticks-per-beat change whose boundary has passed lands on the block start", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(40.0, 1, 4));  // 72 000 frames per beat
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.renderFixed(1024, 30720);
    renderer.setGrid(patternGrid(240.0, 2, 4));  // the next beat boundary at the new tempo was at 12 000
    driver.render(1024);
    REQUIRE(driver.onsets().size() == 2u);
    REQUIRE(driver.onsetAt(30720) != nullptr);
    REQUIRE(driver.onsetAt(30720)->amp == kClickAmp);  // beat 2 kept
    REQUIRE(renderer.lastTick().tickIndex == 2u);
}

TEST_CASE("an anchor in the past moves the next tick to the block start without a burst", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(240.0, 4, 4));
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.render(1024);
    renderer.anchor(-10'000'000, 0.0);  // far in the past: a naive catch-up would emit thousands of ticks
    driver.render(1024);
    REQUIRE(driver.onsets().size() == 2u);
    REQUIRE(driver.onsetAt(1024) != nullptr);
    REQUIRE(renderer.lastTick().tickIndex == 2u);
}

TEST_CASE("a set left releasing on a reload stays referenced until its release ends", "[metronome][renderer]") {
    const tembra::ClickSet first = constantSet(48000, 2.0, 0.5f);
    const tembra::ClickSet second = constantSet(48000, 2.0, 0.25f);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(40.0, 1, 4));
    renderer.setSet(&first);
    renderer.start();
    std::vector<float> buffer(2 * 256);
    for (int i = 0; i < 80; ++i) renderer.render(buffer.data(), 256);  // 20 480 frames into the first voice
    REQUIRE(renderer.references(&first));
    // Reload and speed up in one step: the next tick lands at once, so the first voice cannot ramp on schedule and
    // is handed to the releasing voice while the new set starts.
    renderer.setSet(&second);
    renderer.setGrid(patternGrid(240.0, 1, 4));
    renderer.render(buffer.data(), 64);
    REQUIRE(renderer.references(&second));
    REQUIRE(renderer.references(&first));  // still sounding in the releasing voice: must not be freed
    REQUIRE(buffer[0] > 0.25f);             // both voices at frame 0
    for (int i = 0; i < 4; ++i) renderer.render(buffer.data(), 64);  // past the 3 ms release
    REQUIRE_FALSE(renderer.references(&first));
    REQUIRE(renderer.references(&second));
}

TEST_CASE("more beats per bar restart the bar at the next beat boundary", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(120.0, 2, 4));  // 12 000 frames per tick, beats every 24 000
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.renderFixed(256, 24000 + 1);  // ticks 0 (beat 1), 1, 2 (beat 2)
    renderer.setGrid(patternGrid(120.0, 2, 5));
    driver.renderFixed(256, 84000);
    // The subdivision of beat 2 still plays, then the next beat is beat 1 of the new bar.
    REQUIRE(driver.onsetAt(36000) != nullptr);
    REQUIRE(driver.onsetAt(36000)->amp == kSubAmp);
    REQUIRE(driver.onsetAt(48000) != nullptr);
    REQUIRE(driver.onsetAt(48000)->amp == kAccentAmp);
    REQUIRE(driver.onsetAt(60000)->amp == kSubAmp);
    REQUIRE(driver.onsetAt(72000)->amp == kClickAmp);
}

TEST_CASE("a new ticks-per-beat keeps the beat and lands the next tick on the beat boundary", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    // Beat 2 carries a Sub so the landing tick's level proves the beat index was kept.
    renderer.setGrid(makeGrid(120.0, 2, {TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_SUB, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_SUB,
                                         TEMBRA_CLICK_CLICK, TEMBRA_CLICK_SUB, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_SUB}));
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.renderFixed(256, 24000 + 1);  // ticks at 0, 12 000 and beat 2 at 24 000
    // Three ticks per beat; beat 3 (index 6) is marked Sub, its subdivisions Click.
    renderer.setGrid(makeGrid(120.0, 3, {TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK,
                                         TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_SUB, TEMBRA_CLICK_CLICK,
                                         TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK}));
    driver.renderFixed(256, 90000);
    REQUIRE(driver.onsetsBetween(24001, 48000).empty());  // the pending subdivision of beat 2 is dropped
    REQUIRE(driver.onsetAt(48000) != nullptr);
    REQUIRE(driver.onsetAt(48000)->amp == kSubAmp);  // beat 3 of the new grid
    REQUIRE(driver.onsetAt(56000) != nullptr);
    REQUIRE(driver.onsetAt(56000)->amp == kClickAmp);
    REQUIRE(driver.onsetAt(64000) != nullptr);
    REQUIRE(driver.onsetAt(72000) != nullptr);
}

TEST_CASE("a levels-only change keeps the position", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(makeGrid(120.0, 1, {TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK}));
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.renderFixed(1024, 24000 + 1);
    renderer.setGrid(makeGrid(120.0, 1, {TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_SUB, TEMBRA_CLICK_SUB, TEMBRA_CLICK_SUB}));
    driver.renderFixed(1024, 100000);
    REQUIRE(driver.onsetAt(48000)->amp == kSubAmp);
    REQUIRE(driver.onsetAt(72000)->amp == kSubAmp);
    REQUIRE(driver.onsetAt(96000)->amp == kAccentAmp);
}

TEST_CASE("stop renders a 5 ms raised-cosine release and then silence", "[metronome][renderer]") {
    const tembra::ClickSet set = constantSet(48000, 2.0, 0.5f);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(120.0, 1, 4));
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.render(1000);
    REQUIRE(driver.lastBlock()[999] == 0.5f);
    renderer.stop();
    driver.render(480);
    const auto& block = driver.lastBlock();
    REQUIRE(std::abs(block[0] - 0.5f) < 0.01f);
    REQUIRE(std::abs(block[120] - 0.25f) < 0.01f);
    for (size_t i = 1; i < 240; ++i) REQUIRE(block[i] <= block[i - 1]);
    for (size_t i = 240; i < block.size(); ++i) REQUIRE(block[i] == 0.0f);
    REQUIRE_FALSE(renderer.isActive());
    driver.render(256);
    for (float s : driver.lastBlock()) REQUIRE(s == 0.0f);
}

TEST_CASE("a long sound fades over 3 ms to zero exactly at the next tick", "[metronome][renderer]") {
    const tembra::ClickSet set = constantSet(48000, 2.0, 0.5f);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(240.0, 4, 4));  // 3 000 frames per tick
    renderer.setSet(&set);
    renderer.start();
    std::vector<float> out;
    std::vector<float> buffer(2 * 1024);
    for (int cycle = 0; cycle < 18; ++cycle) {
        const uint32_t block = kBlocks[static_cast<size_t>(cycle) % kBlocks.size()];
        renderer.render(buffer.data(), block);
        for (uint32_t i = 0; i < block; ++i) out.push_back(buffer[2 * i]);
    }
    const size_t ramp = 144;  // 3 ms at 48 kHz
    for (size_t tick : {3000u, 6000u}) {
        INFO("tick at " << tick);
        REQUIRE(std::abs(out[tick - ramp] - 0.5f) < 1e-3f);
        for (size_t f = tick - ramp + 1; f < tick; ++f) REQUIRE(out[f] <= out[f - 1]);
        REQUIRE(out[tick - 1] < 1e-3f);
        REQUIRE(out[tick] == 0.5f);  // the old voice is exactly silent: only the new one sounds
        // After the ramp only one voice is audible (−80 dBFS is 1e-4 of full scale).
        for (size_t f = tick; f + ramp < tick + 3000 && f < out.size(); ++f) REQUIRE(std::abs(out[f] - 0.5f) < 1e-4f);
    }
    // Before the first ramp the first voice plays at full gain.
    for (size_t f = 0; f < 3000 - ramp; ++f) REQUIRE(out[f] == 0.5f);
}

TEST_CASE("anchor moves the next tick to the beat position", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(makeGrid(120.0, 1, {TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_SUB, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK}));
    renderer.setSet(&set);
    renderer.start();
    Driver driver(renderer);
    driver.render(1024);
    renderer.anchor(1024, 0.25);  // a quarter of a beat into beat 1 at frame 1 024
    driver.renderFixed(1024, 80000);
    REQUIRE(driver.onsetsBetween(1, 19024).empty());
    REQUIRE(driver.onsetAt(19024) != nullptr);  // 0.75 beat after the anchor
    REQUIRE(driver.onsetAt(19024)->amp == kSubAmp);  // the tick after 0.25 is beat 2 (index 1)
    REQUIRE(driver.onsetAt(43024) != nullptr);
}

TEST_CASE("the tick record carries the telemetry and readTick compensates latency", "[metronome][renderer][telemetry]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    tembra_metronome_tick before{};
    renderer.readTick(0, 0, &before);
    REQUIRE(before.tick_index == 0u);
    REQUIRE(before.running == 0u);

    renderer.setGrid(patternGrid(120.0, 1, 4));
    renderer.setSet(&set);
    renderer.start();
    std::vector<float> buffer(2 * 256);
    renderer.render(buffer.data(), 256);
    tembra::MetronomeTick first = renderer.lastTick();
    REQUIRE(first.tickIndex == 1u);
    REQUIRE(first.beatIndex == 0u);
    REQUIRE(first.level == TEMBRA_CLICK_ACCENT);
    REQUIRE(first.tickFrame == 0);
    REQUIRE(first.blockStartFrame == 0);
    REQUIRE(first.blockTimeNs > 0);
    REQUIRE(first.bpm == 120.0);

    renderer.render(buffer.data(), 256);
    const tembra::MetronomeTick second = renderer.lastTick();
    REQUIRE(second.tickIndex == 1u);
    REQUIRE(second.tickFrame == 0);
    REQUIRE(second.blockStartFrame == 256);
    REQUIRE(second.blockTimeNs >= first.blockTimeNs);

    const int64_t latencyNs = 12'000'000;
    const int64_t nowNs = second.blockTimeNs + 3'000'000;
    tembra_metronome_tick out{};
    renderer.readTick(latencyNs, nowNs, &out);
    const int64_t expected = second.blockTimeNs +
                             std::llround(static_cast<double>(second.tickFrame - second.blockStartFrame) * 1e9 / 48000.0) +
                             latencyNs - nowNs;
    REQUIRE(out.audible_in_ns == expected);
    REQUIRE(out.tick_index == 1u);
    REQUIRE(out.beat_index == 0u);
    REQUIRE(out.level == TEMBRA_CLICK_ACCENT);
    REQUIRE(out.beat_period_ns == 500'000'000);
    REQUIRE(out.running == 1u);
    REQUIRE(out.sample_rate == 48000u);

    // Through the next beat: the record advances to beat 2.
    std::vector<float> big(2 * 1024);
    for (int i = 0; i < 24; ++i) renderer.render(big.data(), 1024);
    const tembra::MetronomeTick later = renderer.lastTick();
    REQUIRE(later.tickIndex == 2u);
    REQUIRE(later.beatIndex == 1u);
    REQUIRE(later.level == TEMBRA_CLICK_CLICK);
    REQUIRE(later.tickFrame == 24000);
}

TEST_CASE("preview plays one voice at the requested level without ticking", "[metronome][renderer]") {
    const tembra::ClickSet set = impulseSet(48000);
    tembra::MetronomeRenderer renderer(48000);
    renderer.setGrid(patternGrid(120.0, 1, 4));
    renderer.setSet(&set);
    renderer.preview(TEMBRA_CLICK_SUB);
    REQUIRE(renderer.isActive());
    Driver driver(renderer);
    driver.render(256);
    REQUIRE(driver.onsets().size() == 1);
    REQUIRE(driver.onsets().front().amp == kSubAmp);
    REQUIRE_FALSE(renderer.isActive());
    driver.render(48000);
    REQUIRE(driver.onsets().size() == 1);
    REQUIRE(renderer.lastTick().tickIndex == 0u);
}
