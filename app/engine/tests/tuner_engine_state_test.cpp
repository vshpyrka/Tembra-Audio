// The engine state machine behind the C ABI (contracts/engine-c-abi-v2.md "State rules"), driven through a
// FakeAudioDevice so no audio hardware is needed.
#include <catch2/catch_test_macros.hpp>

#include <memory>

#include "fake_audio_device.h"
#include "tembra_engine.h"
#include "tuner/tuner_engine.h"

namespace {

/** Installs a fresh fake device and leaves the engine idle; the engine owns the device, the test keeps a view. */
tembra::FakeAudioDevice* installFake() {
    tembra_tuner_stop();
    tembra_tone_stop();
    auto fake = std::make_unique<tembra::FakeAudioDevice>();
    tembra::FakeAudioDevice* view = fake.get();
    tembra::TunerEngine::instance().setDevice(std::move(fake));
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
