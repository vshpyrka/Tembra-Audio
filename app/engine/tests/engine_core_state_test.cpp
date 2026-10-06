// The engine state machine behind the C ABI (contracts/engine-c-abi-v2.md and engine-c-abi-v3.md "State rules",
// data-model.md §6), driven through a FakeAudioDevice so no audio hardware is needed.
#include <catch2/catch_test_macros.hpp>

#include <memory>

#include "fake_audio_device.h"
#include "tembra_engine.h"
#include "core/engine_core.h"

namespace {

/** Installs a fresh fake device and leaves the engine idle; the engine owns the device, the test keeps a view. */
tembra::FakeAudioDevice* installFake() {
    tembra_tuner_stop();
    tembra_tone_stop();
    tembra_metronome_stop();
    tembra_metronome_unload_custom_sound();
    auto fake = std::make_unique<tembra::FakeAudioDevice>();
    tembra::FakeAudioDevice* view = fake.get();
    tembra::EngineCore::instance().setDevice(std::move(fake));
    return view;
}

}  // namespace

TEST_CASE("the engine starts idle with the documented default config", "[engine][state]") {
    installFake();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(config.window_frames == 4096);
    REQUIRE(config.hop_frames == 1024);
    REQUIRE(config.min_hz == 50.0f);
    REQUIRE(config.max_hz == 1400.0f);
    REQUIRE(config.clarity_threshold == 0.5f);
    REQUIRE(config.peak_ratio == 0.9f);
    REQUIRE(config.median_size == 5);
    REQUIRE(config.watchdog_seconds == 5.0f);
}

TEST_CASE("listening opens the input once and never together with a tone", "[engine][state]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_LISTENING);
    REQUIRE(fake->inputOpen);
    REQUIRE(fake->requestedRate == 48000);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_LISTENING);
    tembra_tuner_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
    tembra_tuner_stop();  // idempotent
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("a tone opens the output and blocks listening", "[engine][state]") {
    tembra::FakeAudioDevice* fake = installFake();
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    REQUIRE(fake->outputOpen);
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_ERR_STATE);
    tembra_tone_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
    tembra_tone_stop();  // idempotent
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("a device that cannot open leaves the engine idle", "[engine][state]") {
    tembra::FakeAudioDevice* fake = installFake();
    fake->failOpen = true;
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_ERR_DEVICE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_ERR_DEVICE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("null arguments are rejected", "[engine][state]") {
    installFake();
    REQUIRE(tembra_tuner_start(nullptr) == TEMBRA_ERR_ARGUMENT);
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start_test_signal(&config, nullptr) == TEMBRA_ERR_ARGUMENT);
    tembra_test_signal signal{82.41, 0.5f, 5, 0.25f};
    REQUIRE(tembra_tuner_start_test_signal(nullptr, &signal) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_sample_load_wav(nullptr, 0) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("reading an idle engine yields the resting state", "[engine][state]") {
    installFake();
    tembra_pitch_reading reading{};
    reading.has_pitch = 1;  // must be overwritten
    tembra_tuner_read(&reading);
    REQUIRE(reading.has_pitch == 0);
    REQUIRE(reading.synthetic == 0);
    REQUIRE(reading.state == TEMBRA_AUDIO_IDLE);
    REQUIRE(reading.sequence == 0);
    REQUIRE(reading.input_level_db == -120.0f);
    REQUIRE(reading.gate_open == 1);
    tembra_tuner_read(nullptr);  // tolerated
}

// --- Listening through the fake device, the watchdog, device loss and test signals (T075) ---

#include <chrono>
#include <cmath>
#include <thread>

#include "signal_fixtures.h"

namespace {

/** Polls tembra_tuner_read for up to [millis] until [accept] returns true; returns the last reading. */
template <class Accept>
tembra_pitch_reading pollUntil(int millis, Accept accept) {
    tembra_pitch_reading reading{};
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(millis);
    while (std::chrono::steady_clock::now() < deadline) {
        tembra_tuner_read(&reading);
        if (accept(reading)) return reading;
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    tembra_tuner_read(&reading);
    return reading;
}

double cents(double a, double b) { return 1200.0 * std::log2(b / a); }

}  // namespace

TEST_CASE("frames fed through the device become a reading", "[engine][listening]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_OK);
    const auto tone = tembra::fixtures::harmonicTone(82.41, 48000.0, 8192.0 / 48000.0);
    for (size_t offset = 0; offset < tone.size(); offset += 256) fake->feedInput(tone.data() + offset, 256);
    const tembra_pitch_reading reading = pollUntil(200, [](const tembra_pitch_reading& r) { return r.has_pitch == 1; });
    REQUIRE(reading.has_pitch == 1);
    REQUIRE(std::abs(cents(82.41, reading.frequency_hz)) < 1.0);
    REQUIRE(reading.synthetic == 0);
    REQUIRE(reading.state == TEMBRA_AUDIO_LISTENING);
    tembra_tuner_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("the watchdog stops capture when nobody reads", "[engine][watchdog]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    config.watchdog_seconds = 0.2f;
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_OK);
    tembra_pitch_reading reading{};
    tembra_tuner_read(&reading);
    REQUIRE(reading.state == TEMBRA_AUDIO_LISTENING);
    std::this_thread::sleep_for(std::chrono::milliseconds(400));
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
    tembra_tuner_read(&reading);
    REQUIRE(reading.state == TEMBRA_AUDIO_IDLE);
    tembra_tuner_stop();  // cleans up the finished thread
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("a lost device reports idle", "[engine][listening]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_OK);
    fake->lost = true;
    const tembra_pitch_reading reading = pollUntil(500, [](const tembra_pitch_reading& r) { return r.state == TEMBRA_AUDIO_IDLE; });
    REQUIRE(reading.state == TEMBRA_AUDIO_IDLE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    tembra_tuner_stop();
}

TEST_CASE("a test signal reads within a cent through the synthetic device", "[engine][testsignal]") {
    installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    const double target = 82.41 * std::pow(2.0, -20.0 / 1200.0);
    tembra_test_signal signal{target, 0.5f, 5, 0.25f};
    REQUIRE(tembra_tuner_start_test_signal(&config, &signal) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_LISTENING);
    const tembra_pitch_reading reading = pollUntil(500, [](const tembra_pitch_reading& r) { return r.has_pitch == 1 && r.synthetic == 1; });
    REQUIRE(reading.synthetic == 1);
    REQUIRE(reading.has_pitch == 1);
    REQUIRE(std::abs(cents(target, reading.frequency_hz)) < 1.0);
    REQUIRE(reading.state == TEMBRA_AUDIO_LISTENING);
    tembra_tuner_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("an out-of-range test signal rests and a silly one is rejected", "[engine][testsignal]") {
    installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    tembra_test_signal signal{2000.0, 0.5f, 5, 0.25f};
    REQUIRE(tembra_tuner_start_test_signal(&config, &signal) == TEMBRA_OK);
    std::this_thread::sleep_for(std::chrono::milliseconds(500));
    tembra_pitch_reading reading{};
    tembra_tuner_read(&reading);
    REQUIRE(reading.synthetic == 1);
    REQUIRE(reading.has_pitch == 0);
    REQUIRE(reading.sequence > 0);
    tembra_tuner_stop();
    signal.frequency_hz = 5.0;
    REQUIRE(tembra_tuner_start_test_signal(&config, &signal) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

// --- Tones and samples through the fake device (T114) ---

#include "wav_fixtures.h"

namespace {

std::vector<float> pullMono(tembra::FakeAudioDevice* fake, uint32_t frames) {
    std::vector<float> stereo(frames * 2);
    fake->pullOutput(stereo.data(), frames);
    std::vector<float> mono(frames);
    for (uint32_t i = 0; i < frames; ++i) mono[i] = stereo[2 * i];
    return mono;
}

float peakOf(const std::vector<float>& mono) {
    float p = 0.0f;
    for (float s : mono) p = std::max(p, std::abs(s));
    return p;
}

}  // namespace

TEST_CASE("a tone renders through the output and re-arms without a jump", "[engine][tone]") {
    tembra::FakeAudioDevice* fake = installFake();
    REQUIRE(tembra_tone_start(82.41, 0) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    REQUIRE(fake->outputOpen);
    pullMono(fake, 1024);
    const auto second = pullMono(fake, 1024);
    REQUIRE(peakOf(second) > 0.2f);
    const auto before = pullMono(fake, 480);
    REQUIRE(tembra_tone_start(82.41, 2000) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    const auto after = pullMono(fake, 480);
    float jump = std::abs(after[0] - before.back());
    for (size_t i = 1; i < after.size(); ++i) jump = std::max(jump, std::abs(after[i] - after[i - 1]));
    REQUIRE(jump < 0.02f);
    tembra_tone_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
}

TEST_CASE("samples load, play, refuse bad handles and garbage", "[engine][sample]") {
    tembra::FakeAudioDevice* fake = installFake();
    const std::string path = tembra::fixtures::tempPath("tembra-engine-sample.wav");
    REQUIRE(tembra::fixtures::writeTempWav(path, 44100, 1, 16, tembra::fixtures::sine(220.0, 44100.0, 0.5, 0.5)));
    const auto bytes = tembra::fixtures::readFile(path);
    const int32_t handle = tembra_sample_load_wav(bytes.data(), static_cast<uint32_t>(bytes.size()));
    REQUIRE(handle >= 0);
    REQUIRE(tembra_sample_play(handle, 1) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    pullMono(fake, 1024);
    REQUIRE(peakOf(pullMono(fake, 1024)) > 0.2f);
    tembra_tone_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    tembra_sample_unload(handle);
    REQUIRE(tembra_sample_play(handle, 0) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_sample_play(99, 0) == TEMBRA_ERR_ARGUMENT);
    const uint8_t garbage[3] = {1, 2, 3};
    REQUIRE(tembra_sample_load_wav(garbage, 3) == TEMBRA_ERR_DECODE);
}

TEST_CASE("a one-shot tone returns the engine to idle by itself", "[engine][tone]") {
    tembra::FakeAudioDevice* fake = installFake();
    REQUIRE(tembra_tone_start(440.0, 100) == TEMBRA_OK);
    for (int block = 0; block < 11; ++block) pullMono(fake, 480);  // 110 ms
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    tembra_tone_stop();  // closes the lazily kept stream
    REQUIRE(fake->closed);
}

// --- The metronome (feature 004, contracts/engine-c-abi-v3.md, data-model.md §6) ---

namespace {

tembra_metronome_grid validGrid() {
    tembra_metronome_grid grid{};
    grid.bpm = 120.0;
    grid.ticks_per_beat = 1;
    grid.ticks_per_bar = 4;
    grid.levels[0] = TEMBRA_CLICK_ACCENT;
    grid.levels[1] = TEMBRA_CLICK_CLICK;
    grid.levels[2] = TEMBRA_CLICK_CLICK;
    grid.levels[3] = TEMBRA_CLICK_CLICK;
    return grid;
}

double dbfs(float linear) { return 20.0 * std::log10(static_cast<double>(linear)); }

}  // namespace

TEST_CASE("metronome grids are validated in any state", "[engine][metronome]") {
    installFake();
    tembra_metronome_grid grid = validGrid();
    REQUIRE(tembra_metronome_set_grid(&grid) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(tembra_metronome_set_grid(nullptr) == TEMBRA_ERR_ARGUMENT);

    tembra_metronome_grid bad = validGrid();
    bad.bpm = 39.0;
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    bad = validGrid();
    bad.bpm = 240.5;
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    bad = validGrid();
    bad.ticks_per_beat = 5;
    bad.ticks_per_bar = 10;
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    bad = validGrid();
    bad.ticks_per_bar = 33;
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    bad = validGrid();
    bad.levels[2] = 3;
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    bad = validGrid();
    bad.ticks_per_beat = 3;
    bad.ticks_per_bar = 4;  // not a whole number of beats
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    bad = validGrid();
    bad.ticks_per_bar = 0;
    REQUIRE(tembra_metronome_set_grid(&bad) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("the metronome starts on beat 1 at frame 0 and refuses the other sources", "[engine][metronome]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_metronome_grid grid = validGrid();
    REQUIRE(tembra_metronome_set_grid(&grid) == TEMBRA_OK);
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_DIGITAL_BEEP) == TEMBRA_OK);
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_METRONOME);
    REQUIRE(fake->outputOpen);

    // The first block starts with the level-index-0 click (Accent, −3 dBFS): its raised-cosine attack starts at
    // exactly 0 on frame 0 and the sound is under way from frame 1.
    const auto first = pullMono(fake, 256);
    REQUIRE(first[0] == 0.0f);
    REQUIRE(first[1] != 0.0f);
    REQUIRE(std::abs(dbfs(peakOf(first)) - -3.0) <= 1.0);

    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_sample_play(0, 0) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_metronome_preview(TEMBRA_CLICK_CLICK) == TEMBRA_ERR_STATE);
    tembra_tone_stop();  // a stray tone stop leaves the metronome running
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_METRONOME);
    REQUIRE(fake->outputOpen);

    tembra_metronome_tick tick{};
    tembra_metronome_read(&tick);
    REQUIRE(tick.running == 1u);
    REQUIRE(tick.tick_index == 1u);
    REQUIRE(tick.sample_rate == 48000u);
    REQUIRE(tick.beat_period_ns == 500'000'000);

    tembra_metronome_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
    REQUIRE_FALSE(fake->outputOpen);
    tembra_metronome_stop();  // idempotent
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    tembra_metronome_read(&tick);
    REQUIRE(tick.running == 0u);
}

TEST_CASE("the metronome refuses to start while listening or playing a tone", "[engine][metronome]") {
    installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_OK);
    REQUIRE(tembra_metronome_start() == TEMBRA_ERR_STATE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_LISTENING);
    tembra_tuner_stop();

    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_OK);
    REQUIRE(tembra_metronome_start() == TEMBRA_ERR_STATE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    tembra_tone_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("a metronome whose device cannot open stays idle", "[engine][metronome]") {
    tembra::FakeAudioDevice* fake = installFake();
    fake->failOpen = true;
    REQUIRE(tembra_metronome_start() == TEMBRA_ERR_DEVICE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(tembra_metronome_preview(TEMBRA_CLICK_CLICK) == TEMBRA_ERR_DEVICE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("sound selection checks the custom sound and the range", "[engine][metronome]") {
    installFake();
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_CUSTOM) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_metronome_select_sound(6) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_COWBELL) == TEMBRA_OK);
    tembra_custom_sound_info info{};
    REQUIRE(tembra_metronome_load_custom_sound(nullptr, &info) == TEMBRA_ERR_ARGUMENT);
    const std::string path = tembra::fixtures::tempPath("tembra-engine-custom-click.wav");
    REQUIRE(tembra::fixtures::writeTempWav(path, 44100, 1, 16, tembra::fixtures::sine(1000.0, 44100.0, 0.1, 0.5)));
    REQUIRE(tembra_metronome_load_custom_sound(path.c_str(), nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_metronome_load_custom_sound(path.c_str(), &info) == TEMBRA_OK);
    REQUIRE(info.source_rate == 44100u);
    REQUIRE(info.source_channels == 1u);
    REQUIRE(info.source_bits == 16u);
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_CUSTOM) == TEMBRA_OK);
    tembra_metronome_unload_custom_sound();
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_CUSTOM) == TEMBRA_ERR_STATE);
}

TEST_CASE("a preview plays one click in the tone state and returns to idle", "[engine][metronome]") {
    tembra::FakeAudioDevice* fake = installFake();
    REQUIRE(tembra_metronome_preview(3) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_metronome_preview(TEMBRA_CLICK_CLICK) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    const auto first = pullMono(fake, 480);
    REQUIRE(std::abs(dbfs(peakOf(first)) - -9.0) <= 1.0);
    for (int block = 0; block < 15; ++block) pullMono(fake, 480);  // 160 ms: longer than any built-in click
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);  // idle again, so the metronome may start
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_METRONOME);
    tembra_metronome_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("reading the metronome before any start is at rest", "[engine][metronome]") {
    installFake();
    tembra_metronome_tick tick{};
    tick.tick_index = 99;
    tick.running = 1;
    tembra_metronome_read(&tick);
    REQUIRE(tick.running == 0u);
    REQUIRE(tick.tick_index == 0u);
    tembra_metronome_read(nullptr);  // tolerated
}

TEST_CASE("a device reopen keeps the tick position", "[engine][metronome]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_metronome_grid grid = validGrid();
    grid.bpm = 240.0;  // 12 000 frames per tick
    REQUIRE(tembra_metronome_set_grid(&grid) == TEMBRA_OK);
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_CLASSIC_WOOD) == TEMBRA_OK);
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);
    for (int block = 0; block < 60; ++block) pullMono(fake, 480);  // 28 800 frames: ticks 0, 1 and 2
    tembra_metronome_tick tick{};
    tembra_metronome_read(&tick);
    REQUIRE(tick.tick_index == 3u);
    // The platform closes and reopens the stream after a route change (research R1); 480 frames are lost meanwhile.
    REQUIRE(fake->reopenOutput(480));
    REQUIRE(fake->reopenCount == 1);
    REQUIRE(fake->outputOpen);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_METRONOME);
    tembra_metronome_read(&tick);
    REQUIRE(tick.running == 1u);
    for (int block = 0; block < 20; ++block) pullMono(fake, 480);  // renderer frames to 38 400: tick 3 at 36 000
    tembra_metronome_read(&tick);
    REQUIRE(tick.tick_index == 4u);  // the bar position continued; nothing restarted at beat 1
    REQUIRE(tick.beat_index == 3u);
    REQUIRE(tick.sample_rate == 48000u);
    // Latency: 0 asks the device (7 ms here); a reported value (iOS) replaces it.
    fake->latencyNanos = 7'000'000;
    tembra_metronome_set_output_latency_ns(0);
    tembra_metronome_tick fromDevice{};
    tembra_metronome_read(&fromDevice);
    tembra_metronome_set_output_latency_ns(20'000'000);
    tembra_metronome_tick reported{};
    tembra_metronome_read(&reported);
    const int64_t difference = reported.audible_in_ns - fromDevice.audible_in_ns;
    REQUIRE(difference > 13'000'000 - 5'000'000);
    REQUIRE(difference <= 13'000'000);
    tembra_metronome_set_output_latency_ns(0);
    tembra_metronome_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("a reopen that fails reports the metronome as not running", "[engine][metronome]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_metronome_grid grid = validGrid();
    REQUIRE(tembra_metronome_set_grid(&grid) == TEMBRA_OK);
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);
    pullMono(fake, 480);
    tembra_metronome_tick tick{};
    tembra_metronome_read(&tick);
    REQUIRE(tick.running == 1u);
    fake->reopenFails = true;
    REQUIRE(fake->reopenOutput());
    REQUIRE(fake->deviceLost());
    tembra_metronome_read(&tick);
    REQUIRE(tick.running == 0u);  // the host sees the click has stopped and can stop or restart it
    tembra_metronome_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("the latency read by the beat light is a cache refreshed on request", "[engine][metronome]") {
    tembra::FakeAudioDevice* fake = installFake();
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);
    pullMono(fake, 480);
    fake->latencyNanos = 9'000'000;  // the route changed; nobody asked yet
    tembra_metronome_tick before{};
    tembra_metronome_read(&before);
    tembra_metronome_set_output_latency_ns(0);  // Android: "ask the device"
    tembra_metronome_tick after{};
    tembra_metronome_read(&after);
    const int64_t difference = after.audible_in_ns - before.audible_in_ns;
    REQUIRE(difference > 9'000'000 - 5'000'000);
    REQUIRE(difference <= 9'000'000);
    tembra_metronome_stop();
}

// --- The noise gate value (feature 006, contracts/engine-c-abi-v4.md) ---

#include <limits>

TEST_CASE("the noise gate value is validated and accepted in every state", "[engine][gate]") {
    installFake();
    REQUIRE(tembra_tuner_set_gate(1, -50.0f) == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(1, std::numeric_limits<float>::quiet_NaN()) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_tuner_set_gate(1, std::numeric_limits<float>::infinity()) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_tuner_set_gate(1, 1.0f) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_tuner_set_gate(1, -121.0f) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_tuner_set_gate(1, -120.0f) == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(1, 0.0f) == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(0, 999.0f) == TEMBRA_OK);  // no gate: the level is ignored

    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    REQUIRE(tembra_tuner_start(&config) == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(1, -50.0f) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_LISTENING);
    tembra_tuner_stop();

    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(0, 0.0f) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TONE);
    tembra_tone_stop();

    tembra_metronome_grid grid = validGrid();
    REQUIRE(tembra_metronome_set_grid(&grid) == TEMBRA_OK);
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(1, -40.0f) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_METRONOME);
    tembra_metronome_stop();
    REQUIRE(tembra_tuner_set_gate(0, 0.0f) == TEMBRA_OK);
}

TEST_CASE("a gate set while idle holds back a test signal from its first hop, and a live change needs no restart", "[engine][gate]") {
    installFake();
    tembra_tuner_config config{};
    tembra_tuner_default_config(&config);
    // An in-tune E2 whose level is about -55 dB: a peak of 0.00388 (the crest of the five-partial mix is 2.18).
    tembra_test_signal quiet{82.41, 0.00388f, 5, 0.25f};

    REQUIRE(tembra_tuner_set_gate(1, -50.0f) == TEMBRA_OK);
    REQUIRE(tembra_tuner_set_gate(1, 5.0f) == TEMBRA_ERR_ARGUMENT);  // refused: -50 dB stays in force
    REQUIRE(tembra_tuner_start_test_signal(&config, &quiet) == TEMBRA_OK);

    bool everRead = false;
    tembra_pitch_reading reading = pollUntil(2000, [&everRead](const tembra_pitch_reading& r) {
        if (r.has_pitch == 1) everRead = true;
        return r.sequence >= 20;
    });
    REQUIRE(reading.sequence >= 20);
    REQUIRE_FALSE(everRead);
    REQUIRE(reading.has_pitch == 0);
    REQUIRE(reading.gate_open == 0);
    REQUIRE(std::abs(reading.input_level_db - (-55.0f)) < 1.0f);

    // The handle moves under the sound: it is read, and the stream was not restarted (the sequence only rises).
    const uint64_t before = reading.sequence;
    REQUIRE(tembra_tuner_set_gate(1, -60.0f) == TEMBRA_OK);
    reading = pollUntil(500, [](const tembra_pitch_reading& r) { return r.has_pitch == 1; });
    REQUIRE(reading.has_pitch == 1);
    REQUIRE(reading.gate_open == 1);
    REQUIRE(reading.sequence > before);
    REQUIRE(std::abs(cents(82.41, reading.frequency_hz)) < 1.0);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_LISTENING);
    tembra_tuner_stop();

    // The value outlives the stop: with the gate back over the sound, the next start is held back again.
    REQUIRE(tembra_tuner_set_gate(1, -50.0f) == TEMBRA_OK);
    REQUIRE(tembra_tuner_start_test_signal(&config, &quiet) == TEMBRA_OK);
    everRead = false;
    reading = pollUntil(2000, [&everRead](const tembra_pitch_reading& r) {
        if (r.has_pitch == 1) everRead = true;
        return r.sequence >= 20;
    });
    REQUIRE_FALSE(everRead);
    REQUIRE(reading.gate_open == 0);
    tembra_tuner_stop();
    REQUIRE(tembra_tuner_set_gate(0, 0.0f) == TEMBRA_OK);
}

