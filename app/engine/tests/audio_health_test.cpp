// Audio-thread health counters (feature 007, contracts/engine-audio-health.md): the ABI getter, the callback timing
// written with relaxed atomics from a monotonic clock, the budget rule and the struct layout.
#include <catch2/catch_test_macros.hpp>

#include <cstddef>
#include <memory>
#include <vector>

#include "core/audio_health.h"
#include "core/engine_core.h"
#include "fake_audio_device.h"
#include "tembra_engine.h"

namespace {

tembra::FakeAudioDevice* installFake() {
    auto fake = std::make_unique<tembra::FakeAudioDevice>();
    tembra::FakeAudioDevice* view = fake.get();
    tembra::EngineCore::instance().setDevice(std::move(fake));
    return view;
}

}  // namespace

TEST_CASE("the health struct has the documented layout", "[health][abi]") {
    // uint64 + 4 x uint32 + uint8 + 3 reserved = 28 bytes of revision-5 fields, then the three revision-6 uint32
    // (feature 009: avg_us, starvation, clips_active) = 40 bytes, a multiple of the uint64 alignment.
    static_assert(sizeof(tembra_audio_health) == 40, "revision 5's 28 bytes + 3 x uint32, 8-byte aligned");
    static_assert(offsetof(tembra_audio_health, callbacks) == 0, "callbacks first");
    static_assert(offsetof(tembra_audio_health, last_us) == 8, "last_us after callbacks");
    static_assert(offsetof(tembra_audio_health, xruns) == 20, "xruns is the fourth uint32");
    static_assert(offsetof(tembra_audio_health, realtime) == 24, "realtime after the four uint32 fields");
    static_assert(offsetof(tembra_audio_health, _reserved) == 25, "three reserved bytes follow realtime");
    static_assert(offsetof(tembra_audio_health, avg_us) == 28, "avg_us is the first revision-6 field");
    static_assert(offsetof(tembra_audio_health, starvation) == 32, "starvation follows avg_us");
    static_assert(offsetof(tembra_audio_health, clips_active) == 36, "clips_active is the last field");
    REQUIRE(TEMBRA_ENGINE_ABI_VERSION == 6u);
    REQUIRE(tembra_engine_abi_version() == 6u);
}

TEST_CASE("a scripted callback within its period updates the counters without an over-budget mark", "[health]") {
    tembra::AudioHealth health;
    health.streamOpened(true);
    // 128 frames at 48 kHz is a 2.67 ms period; a 1.5 ms callback is within budget.
    health.record(1'000'000, 1'001'500, 128, 48000);
    tembra_audio_health out{};
    REQUIRE(health.read(out));
    CHECK(out.callbacks == 1);
    CHECK(out.last_us == 1500);
    CHECK(out.max_us == 1500);
    CHECK(out.over_budget == 0);
    CHECK(out.realtime == 1);
    health.record(2'000'000, 2'000'900, 128, 48000);
    REQUIRE(health.read(out));
    CHECK(out.callbacks == 2);
    CHECK(out.last_us == 900);
    CHECK(out.max_us == 1500);
}

TEST_CASE("a callback longer than its period counts as over budget", "[health]") {
    tembra::AudioHealth health;
    health.streamOpened(false);
    health.record(0, 3000, 128, 48000);
    tembra_audio_health out{};
    REQUIRE(health.read(out));
    CHECK(out.over_budget == 1);
    CHECK(out.max_us == 3000);
    CHECK(out.realtime == 0);
    health.addXruns(2);
    REQUIRE(health.read(out));
    CHECK(out.xruns == 2);
}

TEST_CASE("the revision-6 fields carry the running mean and the transport's counters", "[health]") {
    tembra::AudioHealth health;
    health.streamOpened(true);
    health.record(0, 1000, 128, 48000);
    health.record(0, 3000, 128, 48000);
    tembra_audio_health out{};
    REQUIRE(health.read(out));
    CHECK(out.avg_us == 2000);
    CHECK(out.starvation == 0);
    CHECK(out.clips_active == 0);
    health.setTransportCounters(3, 2);
    REQUIRE(health.read(out));
    CHECK(out.starvation == 3);
    CHECK(out.clips_active == 2);
    health.streamOpened(true);  // a new stream starts the counters over
    REQUIRE(health.read(out));
    CHECK(out.avg_us == 0);
    CHECK(out.starvation == 0);
}

TEST_CASE("the ABI getter reports -1 without a stream and 0 with one", "[health][abi]") {
    tembra::FakeAudioDevice* fake = installFake();
    tembra_audio_health out{};
    REQUIRE(tembra_engine_audio_health(nullptr) == TEMBRA_ERR_ARGUMENT);
    REQUIRE(tembra_engine_audio_health(&out) == -1);
    REQUIRE(tembra_tone_start(440.0, 0) == TEMBRA_OK);
    std::vector<float> block(2u * 256u);
    fake->pullOutput(block.data(), 256);
    fake->pullOutput(block.data(), 256);
    REQUIRE(tembra_engine_audio_health(&out) == 0);
    CHECK(out.callbacks == 2);
    CHECK(out.realtime == 0);  // the fake device holds no real-time status
    tembra_tone_stop();
    REQUIRE(tembra_engine_audio_health(&out) == -1);
}
