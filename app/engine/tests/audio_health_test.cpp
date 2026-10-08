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
    // uint64 + 4 x uint32 + uint8 + 3 reserved = 28 bytes of fields, padded to the uint64 alignment: 32 bytes.
    static_assert(sizeof(tembra_audio_health) == 32, "uint64 + 4 x uint32 + uint8 + 3 reserved, 8-byte aligned");
    static_assert(offsetof(tembra_audio_health, callbacks) == 0, "callbacks first");
    static_assert(offsetof(tembra_audio_health, last_us) == 8, "last_us after callbacks");
    static_assert(offsetof(tembra_audio_health, xruns) == 20, "xruns is the fourth uint32");
    static_assert(offsetof(tembra_audio_health, realtime) == 24, "realtime after the four uint32 fields");
    static_assert(offsetof(tembra_audio_health, _reserved) == 25, "three reserved bytes follow realtime");
    REQUIRE(TEMBRA_ENGINE_ABI_VERSION == 5u);
    REQUIRE(tembra_engine_abi_version() == 5u);
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
