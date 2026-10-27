// The transport clock (feature 009, research R3): a frame position advanced per block on the callback, a playing
// flag, and a seek request the host posts and the callback applies at its next block prologue.
#include <catch2/catch_test_macros.hpp>

#include <cstdint>

#include "transport/transport_clock.h"

TEST_CASE("advance accumulates frames", "[transport][clock]") {
    tembra::TransportClock clock;
    REQUIRE(clock.frame == 0);
    REQUIRE_FALSE(clock.playing);
    clock.advance(96);
    clock.advance(256);
    clock.advance(1024);
    REQUIRE(clock.frame == 1376);
}

TEST_CASE("a seek request applies at the next applyPending", "[transport][clock]") {
    tembra::TransportClock clock;
    clock.advance(5000);
    REQUIRE_FALSE(clock.applyPending());  // nothing pending
    const uint64_t first = clock.requestSeek(92903);
    REQUIRE(first == 1);
    REQUIRE(clock.frame == 5000);          // not yet: the callback applies it
    REQUIRE(clock.applyPending());
    REQUIRE(clock.frame == 92903);
    REQUIRE(clock.appliedSeekSequence() == 1);
    REQUIRE_FALSE(clock.applyPending());  // consumed
    REQUIRE(clock.frame == 92903);

    // Two requests before a block: the newest wins, with its own sequence.
    REQUIRE(clock.requestSeek(10) == 2);
    REQUIRE(clock.requestSeek(20) == 3);
    REQUIRE(clock.applyPending());
    REQUIRE(clock.frame == 20);
    REQUIRE(clock.appliedSeekSequence() == 3);
    REQUIRE(clock.pendingSeekSequence() == 3);
}

TEST_CASE("playing and stopped flags are plain state the renderer owns", "[transport][clock]") {
    tembra::TransportClock clock;
    clock.playing = true;
    REQUIRE(clock.playing);
    clock.playing = false;
    REQUIRE_FALSE(clock.playing);
}

TEST_CASE("the position is never negative", "[transport][clock]") {
    tembra::TransportClock clock;
    clock.requestSeek(-500);
    REQUIRE(clock.applyPending());
    REQUIRE(clock.frame == 0);
    clock.advance(10);
    REQUIRE(clock.frame == 10);
}
