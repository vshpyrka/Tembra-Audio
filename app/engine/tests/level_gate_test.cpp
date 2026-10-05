// The noise gate's rule on its own (feature 006, research R3; contracts/engine-c-abi-v4.md).
#include <catch2/catch_test_macros.hpp>

#include "tuner/level_gate.h"

using tembra::LevelGate;

TEST_CASE("no gate is always open", "[gate]") {
    LevelGate gate;
    REQUIRE(gate.open());
    REQUIRE(gate.update(LevelGate::kOff, -120.0f));
    REQUIRE(gate.update(LevelGate::kOff, -30.0f));
    REQUIRE(gate.update(LevelGate::kOff, -120.0f));
    REQUIRE(LevelGate::isOff(LevelGate::kOff));
    REQUIRE_FALSE(LevelGate::isOff(-120.0f));
    REQUIRE_FALSE(LevelGate::isOff(-50.0f));
}

TEST_CASE("a closed gate opens when the level reaches it and not before", "[gate]") {
    LevelGate gate;
    REQUIRE_FALSE(gate.update(-50.0f, -70.0f));
    REQUIRE_FALSE(gate.open());
    REQUIRE_FALSE(gate.update(-50.0f, -50.1f));
    REQUIRE(gate.update(-50.0f, -50.0f));
    REQUIRE(gate.open());
    REQUIRE(gate.update(-50.0f, -20.0f));
}

TEST_CASE("an open gate closes once the level is well under it", "[gate]") {
    LevelGate gate;
    REQUIRE(gate.update(-50.0f, -40.0f));
    REQUIRE_FALSE(gate.update(-50.0f, -56.0f));
    REQUIRE_FALSE(gate.update(-50.0f, -56.0f));
}

TEST_CASE("a changed gate value is judged afresh", "[gate]") {
    LevelGate gate;
    REQUIRE(gate.update(-60.0f, -55.0f));
    // The handle passes the sound: closed on that very hop, however open the gate was.
    REQUIRE_FALSE(gate.update(-50.0f, -55.0f));
    // And back under it: open again on that hop.
    REQUIRE(gate.update(-60.0f, -55.0f));
    // One decibel over the sound is enough to close it: a change never lingers.
    REQUIRE_FALSE(gate.update(-54.0f, -55.0f));
    // Turning the gate off reads everything.
    REQUIRE(gate.update(LevelGate::kOff, -55.0f));
}
