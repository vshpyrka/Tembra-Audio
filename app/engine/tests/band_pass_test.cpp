// The pre-filter in front of the detector: passes the hearing range, removes rumble, hiss and DC.
#include <catch2/catch_test_macros.hpp>

#include <cmath>
#include <vector>

#include "signal_fixtures.h"
#include "tuner/band_pass.h"

using namespace tembra;
using namespace tembra::fixtures;

namespace {

/** Peak of the last half of the filtered signal relative to the input peak, in dB (negative = attenuated). */
double gainDb(double hz) {
    BandPass filter(48000);
    auto signal = sine(hz, 48000.0, 1.0, 0.5);
    filter.process(signal.data(), signal.size());
    float peak = 0.0f;
    for (size_t i = signal.size() / 2; i < signal.size(); ++i) peak = std::max(peak, std::abs(signal[i]));
    return 20.0 * std::log10(peak / 0.5);
}

}  // namespace

TEST_CASE("rumble and hiss are attenuated by more than 20 dB", "[bandpass]") {
    REQUIRE(gainDb(10.0) < -20.0);
    REQUIRE(gainDb(5000.0) < -20.0);
}

TEST_CASE("the hearing range passes within a decibel", "[bandpass]") {
    REQUIRE(std::abs(gainDb(82.41)) < 1.0);
    REQUIRE(std::abs(gainDb(1000.0)) < 1.0);
}

TEST_CASE("a DC offset decays away within half a second", "[bandpass]") {
    BandPass filter(48000);
    std::vector<float> signal(48000, 0.5f);
    filter.process(signal.data(), signal.size());
    for (size_t i = 24000; i < signal.size(); ++i) REQUIRE(std::abs(signal[i]) < 0.01f);
}

TEST_CASE("processing is in place and block-size independent", "[bandpass]") {
    BandPass whole(48000);
    BandPass blocks(48000);
    auto a = sine(220.0, 48000.0, 0.1);
    auto b = a;
    whole.process(a.data(), a.size());
    for (size_t offset = 0; offset < b.size(); offset += 256) {
        blocks.process(b.data() + offset, std::min<size_t>(256, b.size() - offset));
    }
    for (size_t i = 0; i < a.size(); ++i) REQUIRE(std::abs(a[i] - b[i]) < 1e-6f);
}
