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

// --- The project transport (feature 009, specs/009-timeline-arranger/contracts/engine-c-abi-v6.md "States") ---

namespace {

std::string transportWav() {
    const std::string path = tembra::fixtures::tempPath("tembra-engine-transport-clip.wav");
    REQUIRE(tembra::fixtures::writeTempWav(path, 48000, 1, 16, tembra::fixtures::sine(220.0, 48000.0, 1.0, 0.5)));
    return path;
}

tembra_arrangement* oneClip(tembra_arrangement& arrangement, int32_t source) {
    arrangement = tembra_arrangement{};
    arrangement.track_count = 1;
    arrangement.clip_count = 1;
    arrangement.tracks[0].gain_linear = 1.0f;
    arrangement.tracks[0].audible = 1;
    arrangement.clips[0].track = 0;
    arrangement.clips[0].source = source;
    arrangement.clips[0].start_beats = 0.0;
    arrangement.clips[0].offset_frames = 0;
    arrangement.clips[0].length_frames = 48000;
    return &arrangement;
}

}  // namespace

TEST_CASE("the transport opens only from idle and excludes the other sources", "[engine][transport]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_transport_config config{};
    REQUIRE(tembra_transport_open(nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_open(&config) == TEMBRA_OK);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);
    REQUIRE(fake->outputOpen);
    REQUIRE(fake->requestedRate == 48000);
    REQUIRE(tembra_transport_open(&config) == TEMBRA_ERR_STATE);

    tembra_tuner_config tuner{};
    tembra_tuner_default_config(&tuner);
    REQUIRE(tembra_tuner_start(&tuner) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_sample_play(0, 0) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_metronome_start() == TEMBRA_ERR_STATE);
    REQUIRE(tembra_metronome_preview(TEMBRA_CLICK_CLICK) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);

    // The standalone metronome's configuration stays callable: it is the click the transport uses.
    tembra_metronome_grid grid = validGrid();
    REQUIRE(tembra_metronome_set_grid(&grid) == TEMBRA_OK);
    REQUIRE(tembra_metronome_select_sound(TEMBRA_SOUND_DIGITAL_BEEP) == TEMBRA_OK);
    tembra_metronome_set_output_latency_ns(0);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);

    tembra_tone_stop();  // a stray tone stop leaves the transport open
    tembra_metronome_stop();
    tembra_tuner_stop();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);
    REQUIRE(fake->outputOpen);

    tembra_transport_close();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
    tembra_transport_close();  // idempotent
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("the transport is refused while the tuner, a tone or the metronome runs", "[engine][transport]") {
    installFake();
    tembra_transport_config config{};
    tembra_tuner_config tuner{};
    tembra_tuner_default_config(&tuner);
    REQUIRE(tembra_tuner_start(&tuner) == TEMBRA_OK);
    REQUIRE(tembra_transport_open(&config) == TEMBRA_ERR_STATE);
    tembra_tuner_stop();
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_OK);
    REQUIRE(tembra_transport_open(&config) == TEMBRA_ERR_STATE);
    tembra_tone_stop();
    REQUIRE(tembra_metronome_start() == TEMBRA_OK);
    REQUIRE(tembra_transport_open(&config) == TEMBRA_ERR_STATE);
    tembra_metronome_stop();
    REQUIRE(tembra_transport_open(&config) == TEMBRA_OK);
    tembra_transport_close();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("transport commands need the transport and close returns to idle from any substate", "[engine][transport]") {
    tembra::FakeAudioDevice* fake = installFake();
    REQUIRE(tembra_transport_set_tempo(120, 4, 4) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_transport_play() == TEMBRA_ERR_STATE);
    REQUIRE(tembra_transport_pause() == TEMBRA_ERR_STATE);
    REQUIRE(tembra_transport_seek_beats(1.0) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_transport_set_master(1.0f) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_transport_set_metronome(1) == TEMBRA_ERR_STATE);
    tembra_transport_snapshot snapshot{};
    snapshot.playing = 7;
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 0);
    REQUIRE(snapshot.sample_rate == 0);
    tembra_transport_read(nullptr);  // tolerated

    tembra_transport_config config{};
    REQUIRE(tembra_transport_open(&config) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_tempo(39, 4, 4) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_tempo(120, 33, 4) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_tempo(120, 4, 16) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_tempo(120, 4, 4) == TEMBRA_OK);
    REQUIRE(tembra_transport_seek_beats(-1.0) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_master(1.5f) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_master(0.5f) == TEMBRA_OK);
    REQUIRE(tembra_transport_pause() == TEMBRA_OK);  // idempotent while stopped
    pullMono(fake, 512);
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 0);
    REQUIRE(snapshot.position_frames == 0);
    REQUIRE(snapshot.sample_rate == 48000);
    REQUIRE(snapshot.block_time_ns > 0);

    // An empty project plays: the clock advances and the readout follows it.
    REQUIRE(tembra_transport_play() == TEMBRA_OK);
    REQUIRE(tembra_transport_play() == TEMBRA_OK);  // idempotent
    for (int block = 0; block < 4; ++block) REQUIRE(peakOf(pullMono(fake, 512)) == 0.0f);
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 1);
    REQUIRE(snapshot.position_frames == 3 * 512);  // the last block's start
    REQUIRE(std::abs(snapshot.position_beats - 1536.0 / 24000.0) < 1e-9);
    REQUIRE(snapshot.audible_seconds >= 0.0);
    REQUIRE(tembra_transport_seek_beats(2.0) == TEMBRA_OK);
    pullMono(fake, 512);
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.position_frames == 48000);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);
    tembra_transport_close();  // from playing
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE(fake->closed);
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 0);
    REQUIRE(snapshot.position_frames == 0);

    REQUIRE(tembra_transport_open(&config) == TEMBRA_OK);  // a new open starts at 0, stopped
    pullMono(fake, 512);
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 0);
    REQUIRE(snapshot.position_frames == 0);
    tembra_transport_close();
}

TEST_CASE("clip sources open in idle and in transport, and a referenced source is not closed", "[engine][transport]") {
    tembra::FakeAudioDevice* fake = installFake();
    const std::string path = transportWav();
    tembra_clip_info info{};
    REQUIRE(tembra_clip_source_open(nullptr, &info) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_clip_source_open(path.c_str(), nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_clip_source_open("/nonexistent/clip.wav", &info) == TEMBRA_ERR_DECODE);
    const int32_t idle = tembra_clip_source_open(path.c_str(), &info);  // IDLE
    REQUIRE(idle >= 0);
    REQUIRE(info.channels == 1);
    REQUIRE(info.sample_rate == 48000);
    REQUIRE(info.frames == 48000);
    REQUIRE(info.format == TEMBRA_FORMAT_WAV);

    tembra_transport_config config{};
    REQUIRE(tembra_transport_open(&config) == TEMBRA_OK);
    const int32_t inTransport = tembra_clip_source_open(path.c_str(), &info);  // TRANSPORT
    REQUIRE(inTransport >= 0);
    REQUIRE(inTransport != idle);

    auto arrangement = std::make_unique<tembra_arrangement>();
    REQUIRE(tembra_transport_set_arrangement(nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_arrangement(oneClip(*arrangement, idle)) == TEMBRA_OK);
    oneClip(*arrangement, idle)->track_count = 65;
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_ERR_ARGUMENT);
    oneClip(*arrangement, 99);
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_ERR_ARGUMENT);
    oneClip(*arrangement, idle)->clips[0].length_frames = 48001;
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_ERR_ARGUMENT);
    oneClip(*arrangement, idle)->clips[0].start_beats = -1.0;
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_ERR_ARGUMENT);
    oneClip(*arrangement, idle)->clips[0].track = 1;
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_ERR_ARGUMENT);
    oneClip(*arrangement, idle);
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_OK);

    // The referenced source stays open: its handle slot is not reused.
    tembra_clip_source_close(idle);
    const int32_t another = tembra_clip_source_open(path.c_str(), &info);
    REQUIRE(another >= 0);
    REQUIRE(another != idle);
    REQUIRE(another != inTransport);

    // Track and master fast paths, the click, play, the readout and the health counters.
    REQUIRE(tembra_transport_set_track(1, 1.0f, 1) == TEMBRA_ERR_ARGUMENT);  // one track only
    REQUIRE(tembra_transport_set_track(0, 2.5f, 1) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_set_track(0, 0.5f, 1) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_metronome(1) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_metronome(0) == TEMBRA_OK);
    REQUIRE(tembra_transport_play() == TEMBRA_OK);
    pullMono(fake, 1024);
    const auto block = pullMono(fake, 1024);
    REQUIRE(peakOf(block) > 0.2f);  // the sine at half gain
    REQUIRE(peakOf(block) < 0.3f);
    tembra_audio_health health{};
    REQUIRE(tembra_engine_audio_health(&health) == 0);
    REQUIRE(health.clips_active == 1);
    REQUIRE(health.starvation == 0);
    REQUIRE(health.callbacks == 2);
    tembra_transport_snapshot snapshot{};
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 1);
    REQUIRE(snapshot.position_frames == 1024);
    REQUIRE(tembra_transport_pause() == TEMBRA_OK);
    pullMono(fake, 1024);
    REQUIRE(peakOf(pullMono(fake, 1024)) == 0.0f);

    // Once no arrangement references it, the source closes and its slot is free again.
    arrangement->clip_count = 0;
    REQUIRE(tembra_transport_set_arrangement(arrangement.get()) == TEMBRA_OK);
    tembra_clip_source_close(idle);
    tembra_clip_source_close(idle);  // idempotent
    tembra_clip_source_close(-1);
    tembra_clip_source_close(1000);
    const int32_t reused = tembra_clip_source_open(path.c_str(), &info);
    REQUIRE(reused == idle);
    tembra_transport_close();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    tembra_clip_source_close(reused);
    tembra_clip_source_close(inTransport);
    tembra_clip_source_close(another);
}


// --- Capture, takes and monitoring (feature 010, specs/010-timeline-recording/contracts/engine-c-abi-v7.md) ---

#include <cstdio>
#include <fstream>

namespace {

tembra_capture_config monoCapture() {
    tembra_capture_config config{};
    config.max_channels = 1;
    return config;
}

tembra_record_config recordInto(const std::string& path, uint32_t track = 0, uint32_t countIn = 0) {
    tembra_record_config config{};
    config.wav_path = path.c_str();
    config.track_index = track;
    config.count_in_beats = countIn;
    config.max_frames = 30LL * 60 * 48000;
    config.min_free_bytes = 50ULL * 1024 * 1024;
    return config;
}

/** Pulls one block of [frames] with an input block of the same length at [level] fed first (the duplex callback). */
void pullDuplex(tembra::FakeAudioDevice* fake, uint32_t frames, float level) {
    std::vector<float> input(frames, level);
    fake->feedInputWhileRendering(input.data(), 1, frames);
    pullMono(fake, frames);
}

bool fileExists(const std::string& path) { return std::ifstream(path).good(); }

std::string takePath(const char* name) {
    const std::string path = tembra::fixtures::tempPath(name);
    std::remove(path.c_str());
    return path;
}

}  // namespace

TEST_CASE("arming needs the transport, is idempotent and fails cleanly when the input cannot open", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_ERR_STATE);  // IDLE
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    fake->failInputOpen = true;
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_ERR_DEVICE);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);
    REQUIRE(fake->outputOpen);
    tembra_capture_snapshot snapshot{};
    snapshot.armed = 7;
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.armed == 0);
    fake->failInputOpen = false;
    fake->inputChannelsAvailable = 2;
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    REQUIRE(fake->duplexInputOpen);
    REQUIRE(fake->inputOpenCount == 1);
    REQUIRE(fake->inputRequestedMaxChannels == 1);
    REQUIRE(fake->inputRequestedRate == 48000);
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);  // idempotent
    REQUIRE(fake->inputOpenCount == 1);
    pullMono(fake, 256);
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.armed == 1);
    REQUIRE(snapshot.input_channels == 1);
    REQUIRE(snapshot.sample_rate == 48000);
    REQUIRE(snapshot.recording == 0);
    // The tuner and the other sources are still refused while armed.
    tembra_tuner_config tuner{};
    tembra_tuner_default_config(&tuner);
    REQUIRE(tembra_tuner_start(&tuner) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_metronome_start() == TEMBRA_ERR_STATE);
    tembra_transport_disarm();
    REQUIRE_FALSE(fake->duplexInputOpen);
    REQUIRE(fake->inputCloseCount == 1);
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_TRANSPORT);
    REQUIRE(fake->outputOpen);
    tembra_transport_disarm();  // idempotent
    REQUIRE(fake->inputCloseCount == 1);
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.armed == 0);
    tembra_capture_read(nullptr);  // tolerated
    tembra_transport_close();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
}

TEST_CASE("the alignment is the input plus the output latency at the stream rate", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    fake->inputLatencyNs = 7'000'000;
    fake->latencyNanos = 13'000'000;
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    pullMono(fake, 256);
    tembra_capture_snapshot snapshot{};
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.alignment_frames == 960);
    REQUIRE(snapshot.input_latency_ns == 7'000'000);
    REQUIRE(snapshot.output_latency_ns == 13'000'000);
    // A reported input latency (iOS) replaces the device's.
    tembra_capture_set_input_latency_ns(11'000'000);
    pullMono(fake, 256);
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.alignment_frames == 1152);
    REQUIRE(snapshot.input_latency_ns == 11'000'000);
    tembra_capture_set_input_latency_ns(0);
    tembra_transport_disarm();
    tembra_transport_close();
}

TEST_CASE("a take is recorded from the next block, stopped by the host and described by the take info", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    fake->inputLatencyNs = 1'000'000;  // 48 frames of alignment
    fake->latencyNanos = 0;
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_tempo(120, 4, 4) == TEMBRA_OK);
    const std::string path = takePath("tembra-engine-take.wav");
    tembra_record_config record = recordInto(path);
    tembra_take_info info{};
    REQUIRE(tembra_transport_record_start(nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_ERR_STATE);  // not armed
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_ERR_STATE);     // no take
    REQUIRE(tembra_transport_record_cancel() == TEMBRA_ERR_STATE);
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    REQUIRE(tembra_transport_seek_beats(2.0) == TEMBRA_OK);  // frame 48000
    pullDuplex(fake, 512, 0.0f);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_OK);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_ERR_STATE);  // a take already runs
    for (int block = 0; block < 10; ++block) pullDuplex(fake, 480, 0.25f);  // 4800 frames of input
    tembra_capture_snapshot snapshot{};
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.recording == 1);
    REQUIRE(snapshot.pre_roll == 0);
    REQUIRE(snapshot.frames_captured == 4800 - 48);
    REQUIRE(snapshot.take_start_frames == 48000);
    REQUIRE(snapshot.peak[0] == 0.25f);
    tembra_transport_snapshot transportSnapshot{};
    tembra_transport_read(&transportSnapshot);
    REQUIRE(transportSnapshot.playing == 1);
    REQUIRE(transportSnapshot.recording == 1);
    REQUIRE(transportSnapshot.count_in_beats_left == -1);
    REQUIRE(tembra_transport_record_stop(nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_OK);
    REQUIRE(info.frames == 4800 - 48);
    REQUIRE(info.start_frames == 48000);
    REQUIRE(std::abs(info.start_beats - 2.0) < 1e-9);
    REQUIRE(info.channels == 1);
    REQUIRE(info.sample_rate == 48000);
    REQUIRE(info.alignment_frames == 48);
    REQUIRE(info.stop_reason == TEMBRA_TAKE_STOP_USER);
    tembra_audio_info audio{};
    REQUIRE(tembra_audio_inspect(path.c_str(), &audio) == TEMBRA_OK);
    REQUIRE(audio.frames == 4800 - 48);
    REQUIRE(audio.channels == 1);
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_ERR_STATE);  // collected once
    tembra_transport_read(&transportSnapshot);
    REQUIRE(transportSnapshot.playing == 1);  // playback continues after the stop
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.armed == 1);
    // The live peak buckets are served from the ring: 4752 frames = 18 buckets of 256.
    tembra_peak_bucket buckets[32];
    uint64_t next = 0;
    REQUIRE(tembra_capture_read_peaks(0, buckets, 32, &next) == 18);
    REQUIRE(next == 18);
    REQUIRE(buckets[0].max[0] == 32);  // 0.25 × 127
    REQUIRE(tembra_capture_read_peaks(0, nullptr, 32, &next) == 0);
    tembra_transport_disarm();
    tembra_transport_close();
    std::remove(path.c_str());
}

TEST_CASE("a take under a hundredth of a second is discarded with its file", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    const std::string path = takePath("tembra-engine-short-take.wav");
    tembra_record_config record = recordInto(path);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_OK);
    REQUIRE(fileExists(path));
    pullDuplex(fake, 256, 0.1f);  // 256 frames: 5 ms
    tembra_take_info info{};
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_OK);
    REQUIRE(info.frames == 0);
    REQUIRE_FALSE(fileExists(path));
    REQUIRE(tembra_transport_pause() == TEMBRA_OK);
    tembra_transport_disarm();
    tembra_transport_close();
}

TEST_CASE("an unwritable take path is refused with ERR_IO and the arm stays", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    const std::string bad = "/nonexistent-tembra-dir/take.wav";
    tembra_record_config record = recordInto(bad);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_ERR_IO);
    pullMono(fake, 256);
    tembra_capture_snapshot snapshot{};
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.armed == 1);
    REQUIRE(snapshot.recording == 0);
    tembra_transport_snapshot transportSnapshot{};
    tembra_transport_read(&transportSnapshot);
    REQUIRE(transportSnapshot.playing == 0);
    tembra_transport_disarm();
    tembra_transport_close();
}

TEST_CASE("closing the transport while recording ends the take with TRANSPORT_CLOSED and finalises the file first", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    const std::string path = takePath("tembra-engine-closed-take.wav");
    tembra_record_config record = recordInto(path);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_OK);
    for (int block = 0; block < 20; ++block) pullDuplex(fake, 480, 0.3f);
    tembra_transport_close();
    REQUIRE(tembra_audio_state() == TEMBRA_AUDIO_IDLE);
    REQUIRE_FALSE(fake->duplexInputOpen);
    tembra_audio_info audio{};
    REQUIRE(tembra_audio_inspect(path.c_str(), &audio) == TEMBRA_OK);
    REQUIRE(audio.frames == 9600);
    // The host collects the take once, from IDLE.
    tembra_take_info info{};
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_OK);
    REQUIRE(info.stop_reason == TEMBRA_TAKE_STOP_TRANSPORT_CLOSED);
    REQUIRE(info.frames == 9600);
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_ERR_STATE);
    std::remove(path.c_str());
}

TEST_CASE("an input reopen ends the take with INPUT_LOST and keeps the arm; an output reopen clears monitoring", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_monitor(1, 0) == TEMBRA_ERR_STATE);  // not armed
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_monitor(1, 5) == TEMBRA_ERR_ARGUMENT);  // no such track
    tembra_arrangement arrangement{};
    arrangement.track_count = 1;
    arrangement.tracks[0].gain_linear = 1.0f;
    arrangement.tracks[0].audible = 1;
    REQUIRE(tembra_transport_set_arrangement(&arrangement) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_monitor(1, 0) == TEMBRA_OK);
    pullDuplex(fake, 480, 0.4f);
    pullDuplex(fake, 480, 0.4f);
    tembra_capture_snapshot snapshot{};
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.monitoring == 1);
    std::vector<float> input(480, 0.4f);
    fake->feedInputWhileRendering(input.data(), 1, 480);
    const auto heard = pullMono(fake, 480);
    REQUIRE(std::abs(heard.back() - 0.4f) < 2e-3f);  // the input through the unity strip, while stopped
    REQUIRE(fake->reopenOutput());
    pullDuplex(fake, 480, 0.4f);
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.monitoring == 0);
    REQUIRE(snapshot.armed == 1);
    // A take, then the input is reopened by the platform.
    const std::string path = takePath("tembra-engine-input-lost.wav");
    tembra_record_config record = recordInto(path);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_OK);
    for (int block = 0; block < 10; ++block) pullDuplex(fake, 480, 0.3f);
    fake->reopenInput();
    pullDuplex(fake, 480, 0.3f);
    tembra_capture_read(&snapshot);
    REQUIRE(snapshot.recording == 0);
    REQUIRE(snapshot.armed == 1);
    REQUIRE(snapshot.stop_reason == TEMBRA_TAKE_STOP_INPUT_LOST);
    tembra_take_info info{};
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_OK);
    REQUIRE(info.stop_reason == TEMBRA_TAKE_STOP_INPUT_LOST);
    REQUIRE(info.frames == 4800);
    tembra_audio_health health{};
    REQUIRE(tembra_engine_audio_health(&health) == 0);
    REQUIRE(health.input_overruns == 0);
    REQUIRE(health.record_backlog > 0);
    tembra_transport_disarm();
    tembra_transport_close();
    std::remove(path.c_str());
}

TEST_CASE("a count-in pre-rolls from stopped, cancel restores the position, and the snapshot counts the beats", "[engine][capture]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_transport_config transport{};
    REQUIRE(tembra_transport_open(&transport) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_tempo(120, 4, 4) == TEMBRA_OK);
    const tembra_capture_config config = monoCapture();
    REQUIRE(tembra_transport_arm(&config) == TEMBRA_OK);
    REQUIRE(tembra_transport_seek_beats(8.0) == TEMBRA_OK);  // frame 192000
    pullDuplex(fake, 480, 0.0f);
    const std::string path = takePath("tembra-engine-count-in.wav");
    tembra_record_config record = recordInto(path, 0, 4);
    REQUIRE(tembra_transport_record_start(&record) == TEMBRA_OK);
    pullDuplex(fake, 480, 0.0f);
    tembra_transport_snapshot snapshot{};
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 1);
    REQUIRE(snapshot.recording == 0);
    REQUIRE(snapshot.count_in_beats_left == 4);
    REQUIRE(snapshot.position_frames == 96000);  // one bar before the start
    for (int block = 0; block < 100; ++block) pullDuplex(fake, 480, 0.0f);  // one second: beat 3 of the count-in
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.count_in_beats_left == 2);
    REQUIRE(tembra_transport_record_cancel() == TEMBRA_OK);
    pullDuplex(fake, 480, 0.0f);
    tembra_transport_read(&snapshot);
    REQUIRE(snapshot.playing == 0);
    REQUIRE(snapshot.position_frames == 192000);
    REQUIRE(snapshot.count_in_beats_left == -1);
    REQUIRE_FALSE(fileExists(path));
    REQUIRE(tembra_transport_record_cancel() == TEMBRA_ERR_STATE);
    tembra_take_info info{};
    REQUIRE(tembra_transport_record_stop(&info) == TEMBRA_ERR_STATE);
    tembra_transport_disarm();
    tembra_transport_close();
}

TEST_CASE("the free-space probe answers for a real directory and refuses a bad path", "[engine][capture]") {
    REQUIRE(tembra_storage_free_bytes(tembra::fixtures::tempPath("").c_str()) > 0);
    REQUIRE(tembra_storage_free_bytes("/nonexistent-tembra-dir/x") == -1);
    REQUIRE(tembra_storage_free_bytes(nullptr) == -1);
}
