// The pan law (feature 012, research R5; contracts/engine-c-abi-v8.md "Renderer rules"): centre-compensated constant
// power for mono sources (1/1 at the centre, √2/0 and 0/√2 at the extremes, L² + R² = 2 everywhere, so an existing
// project plays unchanged and a panned track keeps its loudness), balance for stereo sources (the same curve capped at
// 1: the opposite side attenuates, nothing is boosted).
#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_floating_point.hpp>

#include <cmath>

#include "mixer/pan_law.h"

using Catch::Matchers::WithinAbs;

namespace {

constexpr float kRoot2 = 1.41421356f;

struct Sides {
    float left;
    float right;
};

Sides gains(float pan, bool stereo) {
    Sides out{};
    tembra::PanLaw::gains(pan, stereo, out.left, out.right);
    return out;
}

}  // namespace

TEST_CASE("a mono source at the centre passes at unity on both sides, bit-exact", "[mixer][pan]") {
    const Sides centre = gains(0.0f, false);
    REQUIRE(centre.left == 1.0f);
    REQUIRE(centre.right == 1.0f);
}

TEST_CASE("full left and full right put √2 on one side and exact silence on the other", "[mixer][pan]") {
    const Sides left = gains(-1.0f, false);
    REQUIRE_THAT(left.left, WithinAbs(kRoot2, 1e-6));
    REQUIRE(left.right == 0.0f);
    const Sides right = gains(1.0f, false);
    REQUIRE(right.left == 0.0f);
    REQUIRE_THAT(right.right, WithinAbs(kRoot2, 1e-6));
}

TEST_CASE("the power sum L² + R² is 2 at nine positions", "[mixer][pan]") {
    for (int step = -4; step <= 4; ++step) {
        const float pan = static_cast<float>(step) * 0.25f;
        const Sides sides = gains(pan, false);
        INFO("pan " << pan);
        REQUIRE_THAT(sides.left * sides.left + sides.right * sides.right, WithinAbs(2.0, 1e-5));
    }
}

TEST_CASE("the law is monotonic: the left side falls and the right side rises with pan", "[mixer][pan]") {
    Sides previous = gains(-1.0f, false);
    for (int step = -19; step <= 20; ++step) {
        const Sides sides = gains(static_cast<float>(step) * 0.05f, false);
        REQUIRE(sides.left <= previous.left);
        REQUIRE(sides.right >= previous.right);
        previous = sides;
    }
}

TEST_CASE("a stereo source is balanced: gains never exceed 1 and are 1/1 at the centre", "[mixer][pan]") {
    const Sides centre = gains(0.0f, true);
    REQUIRE(centre.left == 1.0f);
    REQUIRE(centre.right == 1.0f);
    for (int step = -4; step <= 4; ++step) {
        const Sides sides = gains(static_cast<float>(step) * 0.25f, true);
        REQUIRE(sides.left <= 1.0f);
        REQUIRE(sides.right <= 1.0f);
    }
    // +50 %: the left attenuates to √2·cos(3π/8) and the right stays at unity.
    const Sides half = gains(0.5f, true);
    REQUIRE_THAT(half.left, WithinAbs(0.5412, 1e-3));
    REQUIRE(half.right == 1.0f);
    const Sides left = gains(-1.0f, true);
    REQUIRE(left.left == 1.0f);
    REQUIRE(left.right == 0.0f);
}

TEST_CASE("a pan outside −1..1 is held at the extremes", "[mixer][pan]") {
    const Sides beyondLeft = gains(-3.0f, false);
    REQUIRE_THAT(beyondLeft.left, WithinAbs(kRoot2, 1e-6));
    REQUIRE(beyondLeft.right == 0.0f);
    const Sides beyondRight = gains(7.0f, true);
    REQUIRE(beyondRight.left == 0.0f);
    REQUIRE(beyondRight.right == 1.0f);
}
