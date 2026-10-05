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

// --- The closing margin (feature 006, User Story 2; research R3) ---

TEST_CASE("an open gate stays open until the level is 3 dB under it", "[gate][margin]") {
    REQUIRE(LevelGate::kCloseMarginDb == 3.0f);
    LevelGate gate;
    REQUIRE(gate.update(-50.0f, -49.0f));        // opens at the gate level or above
    REQUIRE(gate.update(-50.0f, -51.0f));        // 1 dB under it: still open
    REQUIRE(gate.update(-50.0f, -53.0f));        // exactly 3 dB under: still open
    REQUIRE_FALSE(gate.update(-50.0f, -53.1f));  // past the margin: closed
    REQUIRE_FALSE(gate.update(-50.0f, -51.0f));  // a closed gate stays closed inside the margin
    REQUIRE_FALSE(gate.update(-50.0f, -50.1f));
    REQUIRE(gate.update(-50.0f, -50.0f));        // and opens again only at the gate level
}

TEST_CASE("a changed gate value ignores the margin", "[gate][margin]") {
    LevelGate gate;
    REQUIRE(gate.update(-60.0f, -55.0f));
    REQUIRE(gate.update(-60.0f, -62.0f));        // inside the margin of an unchanged gate: open
    REQUIRE_FALSE(gate.update(-59.0f, -60.5f));  // the value changed: judged afresh, although only 1.5 dB under it
    REQUIRE(gate.update(-62.0f, -60.5f));        // changed again, now under the sound: open
    REQUIRE(gate.update(-62.0f, -64.5f));        // and from here on the margin holds it
    REQUIRE_FALSE(gate.update(-62.0f, -65.5f));
}

