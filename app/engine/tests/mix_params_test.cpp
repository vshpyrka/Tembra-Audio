// The mixer's lock-free parameter block (feature 012, research R4; contracts/engine-c-abi-v8.md): per-track gain,
// pan and audibility as atomics with a version word the callback compares once per block, so a fader step from the
// main thread never waits behind the command mutex; the setters validate and store, nothing more.
#include <catch2/catch_test_macros.hpp>

#include <atomic>
#include <cmath>
#include <limits>

#include "mixer/mix_params.h"
#include "timeline/arrangement_snapshot.h"

TEST_CASE("every track starts at unity, centred, audible, with metering off", "[mixer][params]") {
    tembra::MixParams params;
    for (uint32_t t = 0; t < tembra::MixParams::kTracks; ++t) {
        REQUIRE(params.gain(t) == 1.0f);
        REQUIRE(params.pan(t) == 0.0f);
        REQUIRE(params.audible(t));
    }
    REQUIRE_FALSE(params.metering());
}

TEST_CASE("a setter's values are visible to a reader after the version changes", "[mixer][params]") {
    tembra::MixParams params;
    const uint64_t before = params.version();
    REQUIRE(params.setTrack(3, 0.5f, -0.25f, false));
    REQUIRE(params.version() != before);
    CHECK(params.gain(3) == 0.5f);
    CHECK(params.pan(3) == -0.25f);
    CHECK_FALSE(params.audible(3));
    CHECK(params.gain(2) == 1.0f);  // the others untouched

    const uint64_t afterTrack = params.version();
    REQUIRE(params.setPan(3, 0.75f));
    REQUIRE(params.version() != afterTrack);
    CHECK(params.pan(3) == 0.75f);
    CHECK(params.gain(3) == 0.5f);  // pan alone leaves the gain

    const uint64_t afterPan = params.version();
    REQUIRE(params.setGainAndAudible(3, 1.5f, true));
    REQUIRE(params.version() != afterPan);
    CHECK(params.gain(3) == 1.5f);
    CHECK(params.audible(3));
    CHECK(params.pan(3) == 0.75f);  // gain and audibility leave the pan (the revision-7 fast path)
}

TEST_CASE("set_track rejects an index of 64 or more, a non-finite value and a value outside its range", "[mixer][params]") {
    tembra::MixParams params;
    const uint64_t before = params.version();
    const float nan = std::numeric_limits<float>::quiet_NaN();
    const float infinity = std::numeric_limits<float>::infinity();
    CHECK_FALSE(params.setTrack(64, 1.0f, 0.0f, true));
    CHECK_FALSE(params.setTrack(0, nan, 0.0f, true));
    CHECK_FALSE(params.setTrack(0, 1.0f, infinity, true));
    CHECK_FALSE(params.setTrack(0, -0.01f, 0.0f, true));
    CHECK_FALSE(params.setTrack(0, 2.01f, 0.0f, true));
    CHECK_FALSE(params.setTrack(0, 1.0f, -1.01f, true));
    CHECK_FALSE(params.setTrack(0, 1.0f, 1.01f, true));
    CHECK_FALSE(params.setPan(64, 0.0f));
    CHECK_FALSE(params.setPan(0, nan));
    CHECK_FALSE(params.setPan(0, 1.5f));
    CHECK_FALSE(params.setGainAndAudible(64, 1.0f, true));
    CHECK_FALSE(params.setGainAndAudible(0, 2.5f, true));
    CHECK(params.version() == before);  // nothing stored, nothing announced
    CHECK(params.gain(0) == 1.0f);
    CHECK(params.pan(0) == 0.0f);
    // The limits themselves are accepted.
    CHECK(params.setTrack(63, 2.0f, 1.0f, true));
    CHECK(params.setTrack(63, 0.0f, -1.0f, false));
}

TEST_CASE("the metering flag is a plain switch", "[mixer][params]") {
    tembra::MixParams params;
    params.setMetering(true);
    REQUIRE(params.metering());
    params.setMetering(false);
    REQUIRE_FALSE(params.metering());
}

TEST_CASE("seeding from a track table takes gain and audibility, keeps pan and bumps the version", "[mixer][params]") {
    tembra::MixParams params;
    REQUIRE(params.setPan(1, -1.0f));
    const uint64_t before = params.version();
    tembra::TrackTable table;
    table.count = 2;
    table.tracks[0] = tembra::TrackState{0.5f, 1u};
    table.tracks[1] = tembra::TrackState{1.5f, 0u};
    params.seed(table);
    REQUIRE(params.version() != before);
    CHECK(params.gain(0) == 0.5f);
    CHECK(params.audible(0));
    CHECK(params.gain(1) == 1.5f);
    CHECK_FALSE(params.audible(1));
    CHECK(params.pan(1) == -1.0f);
    CHECK(params.pan(0) == 0.0f);
}

TEST_CASE("reset returns every track to unity, centre and audible and turns metering off", "[mixer][params]") {
    tembra::MixParams params;
    REQUIRE(params.setTrack(5, 0.25f, 1.0f, false));
    params.setMetering(true);
    const uint64_t before = params.version();
    params.reset();
    REQUIRE(params.version() != before);
    CHECK(params.gain(5) == 1.0f);
    CHECK(params.pan(5) == 0.0f);
    CHECK(params.audible(5));
    CHECK_FALSE(params.metering());
}

TEST_CASE("the parameter atomics are lock-free on this platform", "[mixer][params]") {
    std::atomic<float> gain{1.0f};
    std::atomic<uint64_t> version{0};
    REQUIRE(gain.is_lock_free());
    REQUIRE(version.is_lock_free());
}
