// The tempo map (feature 009, research R3): one constant tempo converting beats of the signature's unit note to
// frames at the stream rate and back, plus the bar arithmetic the ruler and the click share.
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <cstdint>

#include "transport/tempo_map.h"

TEST_CASE("frames per beat is 60 / bpm x rate", "[transport][tempo]") {
    tembra::TempoMap map;
    map.bpm = 124.0;
    map.rate = 48000;
    REQUIRE(std::abs(map.framesPerBeat() - 23225.806451612903) < 1e-9);
    map.bpm = 120.0;
    REQUIRE(map.framesPerBeat() == 24000.0);
    map.rate = 44100;
    REQUIRE(map.framesPerBeat() == 22050.0);
}

TEST_CASE("beats and frames round-trip within 1e-9 beats", "[transport][tempo]") {
    tembra::TempoMap map;
    map.bpm = 124.0;
    map.rate = 48000;
    for (double beats : {0.0, 1.0, 4.0, 7.25, 123.456, 10000.001}) {
        const int64_t frame = map.beatsToFrame(beats);
        const double back = map.frameToBeats(frame);
        // The frame is rounded to the nearest sample, which is at most half a frame (2e-5 beats) away; feeding the
        // rounded frame back and forth must be stable to 1e-9.
        REQUIRE(std::abs(back - beats) <= 0.5 / map.framesPerBeat() + 1e-12);
        REQUIRE(map.beatsToFrame(back) == frame);
        REQUIRE(std::abs(map.frameToBeats(map.beatsToFrame(back)) - back) < 1e-9);
    }
    REQUIRE(map.beatsToFrame(4.0) == 92903);  // the clip at beat 4 at 124 BPM of transport_renderer_test
}

TEST_CASE("bars and beats in the bar follow the signature", "[transport][tempo]") {
    tembra::TempoMap four;
    four.beatsPerBar = 4;
    four.beatUnit = 4;
    REQUIRE(four.barOf(0.0) == 0);
    REQUIRE(four.barOf(3.999) == 0);
    REQUIRE(four.barOf(4.0) == 1);
    REQUIRE(four.barOf(9.5) == 2);
    REQUIRE(four.beatInBar(9.5) == 1.5);

    tembra::TempoMap six;
    six.beatsPerBar = 6;
    six.beatUnit = 8;
    REQUIRE(six.barOf(5.9) == 0);
    REQUIRE(six.barOf(6.0) == 1);
    REQUIRE(six.barOf(13.0) == 2);
    REQUIRE(six.beatInBar(13.0) == 1.0);

    tembra::TempoMap seven;
    seven.beatsPerBar = 7;
    seven.beatUnit = 8;
    REQUIRE(seven.barOf(6.99) == 0);
    REQUIRE(seven.barOf(7.0) == 1);
    REQUIRE(seven.barOf(21.0) == 3);
    REQUIRE(seven.beatInBar(22.5) == 1.5);
    REQUIRE(seven.barOf(-0.5) == 0);  // never negative
}

TEST_CASE("the beat unit names the note the tempo counts, so it does not change the frames per beat", "[transport][tempo]") {
    // Research R3 and feature 004 (spec FR-007, clarified): in x/8 the tempo counts eighth notes, so 120 BPM in 6/8
    // and 120 BPM in 4/4 both place a beat every 24 000 frames; the click grid uses the same rule and the two stay
    // aligned. The unit only labels the ruler.
    tembra::TempoMap quarter;
    quarter.bpm = 120.0;
    quarter.beatUnit = 4;
    tembra::TempoMap eighth = quarter;
    eighth.beatUnit = 8;
    eighth.beatsPerBar = 6;
    REQUIRE(quarter.framesPerBeat() == eighth.framesPerBeat());
    REQUIRE(quarter.beatsToFrame(3.0) == eighth.beatsToFrame(3.0));
}

TEST_CASE("tempo maps compare by value", "[transport][tempo]") {
    tembra::TempoMap a;
    tembra::TempoMap b = a;
    REQUIRE(a == b);
    b.bpm = 90.0;
    REQUIRE(a != b);
    b = a;
    b.beatsPerBar = 3;
    REQUIRE(a != b);
}
