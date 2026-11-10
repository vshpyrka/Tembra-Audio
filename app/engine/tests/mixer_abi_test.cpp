// The revision-8 mixer ABI (feature 012, research R4, R12; contracts/engine-c-abi-v8.md): the setters store atomics
// and never take the command mutex (a fader step from the main thread cannot wait behind play or record-stop), the
// read is a seqlock copy, every call checks its arguments and the transport state, and the revision-7 track and
// master setters forward to the same lock-free path. Driven through a FakeAudioDevice; the mutex is held from a
// second thread through the engine core's test seam.
#include <catch2/catch_test_macros.hpp>

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstddef>
#include <cstdlib>
#include <limits>
#include <memory>
#include <thread>
#include <vector>

#include "core/engine_core.h"
#include "fake_audio_device.h"
#include "tembra_engine.h"

namespace {

using Clock = std::chrono::steady_clock;

tembra::FakeAudioDevice* installFake() {
    tembra_transport_close();
    tembra_tuner_stop();
    tembra_tone_stop();
    tembra_metronome_stop();
    auto fake = std::make_unique<tembra::FakeAudioDevice>();
    tembra::FakeAudioDevice* view = fake.get();
    tembra::EngineCore::instance().setDevice(std::move(fake));
    return view;
}

void openWithTwoTracks() {
    tembra_transport_config config{};
    REQUIRE(tembra_transport_open(&config) == TEMBRA_OK);
    tembra_arrangement arrangement{};
    arrangement.track_count = 2;
    for (uint32_t t = 0; t < 2; ++t) {
        arrangement.tracks[t].gain_linear = 1.0f;
        arrangement.tracks[t].audible = 1;
    }
    REQUIRE(tembra_transport_set_arrangement(&arrangement) == TEMBRA_OK);
}

void pull(tembra::FakeAudioDevice* fake, int blocks) {
    std::vector<float> stereo(2u * 256u);
    for (int i = 0; i < blocks; ++i) fake->pullOutput(stereo.data(), 256);
}

}  // namespace

TEST_CASE("the mixer setters return within a millisecond while another thread holds the command mutex", "[mixer][abi][rt]") {
    tembra::FakeAudioDevice* fake = installFake();
    openWithTwoTracks();
    pull(fake, 2);
    std::atomic<bool> held{false};
    std::thread holder([&] {
        const std::unique_lock<std::mutex> hold = tembra::EngineCore::instance().holdCommandsForTest();
        held.store(true, std::memory_order_release);
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    });
    while (!held.load(std::memory_order_acquire)) std::this_thread::yield();
    const Clock::time_point start = Clock::now();
    REQUIRE(tembra_mixer_set_pan(0, -0.5f) == TEMBRA_OK);
    REQUIRE(tembra_mixer_set_track(1, 0.5f, 0.25f, 1) == TEMBRA_OK);
    REQUIRE(tembra_mixer_set_master(0.8f) == TEMBRA_OK);
    REQUIRE(tembra_mixer_set_metering(1) == TEMBRA_OK);
    REQUIRE(tembra_mixer_reset_clip(0) == TEMBRA_OK);
    REQUIRE(tembra_mixer_reset_clip(TEMBRA_MIXER_MASTER_INDEX) == TEMBRA_OK);
    tembra_mix_snapshot snapshot{};
    tembra_mixer_read(&snapshot);
    // The revision-7 fast paths forward to the same lock-free block.
    REQUIRE(tembra_transport_set_track(0, 0.7f, 1) == TEMBRA_OK);
    REQUIRE(tembra_transport_set_master(0.9f) == TEMBRA_OK);
    const auto elapsed = Clock::now() - start;
    holder.join();
    // The mutex is held for 200 ms: a setter that waited behind it would take that long. On a development Mac the nine
    // calls take microseconds and a millisecond is the bound; GitHub's shared macOS runners preempt the test thread for
    // milliseconds at a time (PR #22, 2026-11-09), so there the bound is 20 ms, still a tenth of the hold.
    const auto bound = std::getenv("GITHUB_ACTIONS") != nullptr ? std::chrono::milliseconds(20) : std::chrono::milliseconds(1);
    INFO("the nine calls took " << std::chrono::duration_cast<std::chrono::microseconds>(elapsed).count() << " us");
    REQUIRE(elapsed < bound);
    // What was stored while the mutex was held reaches the renderer's parameters.
    const tembra::TransportRenderer* renderer = tembra::EngineCore::instance().transportRendererForTest();
    REQUIRE(renderer != nullptr);
    CHECK(renderer->mixParams().pan(0) == -0.5f);
    CHECK(renderer->mixParams().gain(0) == 0.7f);
    CHECK(renderer->mixParams().gain(1) == 0.5f);
    CHECK(renderer->mixParams().pan(1) == 0.25f);
    CHECK(renderer->mixParams().metering());
    tembra_transport_close();
}

TEST_CASE("the mixer calls need the transport and read zeros without it", "[mixer][abi]") {
    installFake();
    REQUIRE(tembra_mixer_set_track(0, 1.0f, 0.0f, 1) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_mixer_set_pan(0, 0.0f) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_mixer_set_master(1.0f) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_mixer_set_metering(1) == TEMBRA_ERR_STATE);
    REQUIRE(tembra_mixer_reset_clip(0) == TEMBRA_ERR_STATE);
    tembra_mix_snapshot snapshot{};
    snapshot.metering = 7;
    snapshot.track_count = 7;
    snapshot.peak[0][0] = 0.5f;
    tembra_mixer_read(&snapshot);
    CHECK(snapshot.metering == 0);
    CHECK(snapshot.track_count == 0);
    CHECK(snapshot.peak[0][0] == 0.0f);
    CHECK(snapshot.clip_mask == 0);
    tembra_mixer_read(nullptr);  // tolerated
}

TEST_CASE("the mixer setters check their arguments: the index, finiteness and the ranges", "[mixer][abi]") {
    installFake();
    openWithTwoTracks();
    const float nan = std::numeric_limits<float>::quiet_NaN();
    CHECK(tembra_mixer_set_track(64, 1.0f, 0.0f, 1) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_track(0, nan, 0.0f, 1) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_track(0, 2.5f, 0.0f, 1) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_track(0, 1.0f, 1.5f, 1) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_pan(64, 0.0f) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_pan(0, -1.5f) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_pan(0, nan) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_master(1.5f) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_set_master(-0.1f) == TEMBRA_ERR_ARGUMENT);
    CHECK(tembra_mixer_reset_clip(65) == TEMBRA_ERR_ARGUMENT);
    // The index past the last track but under 64 is stored (the renderer applies only the arrangement's tracks).
    CHECK(tembra_mixer_set_pan(63, 1.0f) == TEMBRA_OK);
    CHECK(tembra_mixer_set_track(63, 2.0f, -1.0f, 0) == TEMBRA_OK);
    CHECK(tembra_mixer_reset_clip(63) == TEMBRA_OK);
    CHECK(tembra_mixer_reset_clip(TEMBRA_MIXER_MASTER_INDEX) == TEMBRA_OK);
    tembra_transport_close();
}

TEST_CASE("the read reports the callback's metering, track count and block while the transport runs", "[mixer][abi]") {
    tembra::FakeAudioDevice* fake = installFake();
    openWithTwoTracks();
    tembra_mix_snapshot snapshot{};
    REQUIRE(tembra_mixer_set_metering(1) == TEMBRA_OK);
    pull(fake, 3);
    tembra_mixer_read(&snapshot);
    CHECK(snapshot.metering == 1);
    CHECK(snapshot.track_count == 2);
    CHECK(snapshot.block >= 3);
    CHECK(snapshot.peak[0][0] == 0.0f);  // no clip plays
    REQUIRE(tembra_mixer_set_metering(0) == TEMBRA_OK);
    pull(fake, 2);
    tembra_mixer_read(&snapshot);
    CHECK(snapshot.metering == 0);
    CHECK(snapshot.track_count == 0);
    tembra_transport_close();
    tembra_mixer_read(&snapshot);
    CHECK(snapshot.metering == 0);
    CHECK(snapshot.block == 0);
}

TEST_CASE("the ABI snapshot carries the documented capacities and the health struct its new field", "[mixer][abi]") {
    static_assert(TEMBRA_MIXER_MASTER_INDEX == 64u, "the master follows the 64 strips");
    static_assert(sizeof(tembra_mix_snapshot::peak) == TEMBRA_MAX_TRACKS * 2 * sizeof(float), "peak per track and side");
    static_assert(sizeof(tembra_mix_snapshot::rms) == TEMBRA_MAX_TRACKS * 2 * sizeof(float), "rms per track and side");
    static_assert(sizeof(tembra_audio_health) == 56, "master_clips fills revision 7's padding: the size stays 56");
    static_assert(offsetof(tembra_audio_health, master_clips) == 52, "master_clips is the last field");
    REQUIRE(TEMBRA_ENGINE_ABI_VERSION == 8u);
}
