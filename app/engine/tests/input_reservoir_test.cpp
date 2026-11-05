// The duplex input's reservoir (feature 010 follow-up, Android): the output callback reads the input stream
// non-blocking, and an input burst that lands a little after the output callback used to leave a short read, zero-padded
// into the take and counted as an input overrun (the Pixel 6 Pro's soaks: 5 or 6 per take). The reservoir keeps a
// cushion of input read ahead, so late bursts within the cushion are absorbed, the frames stay in order, and the extra
// latency is exactly the cushion, which the device adds to the input latency the alignment uses.
#include <catch2/catch_test_macros.hpp>

#include <cstdint>
#include <vector>

#include "capture/input_reservoir.h"

namespace {

/** An input stream: frames carry their own index (value = index + 1, both channels); [available] is what has arrived. */
struct FakeInput {
    uint32_t channels = 1;
    uint64_t produced = 0;  // frames handed out so far
    uint64_t arrived = 0;   // frames the "hardware" has delivered so far
    uint32_t reads = 0;
    uint32_t lastAsk = 0;

    void arrive(uint64_t frames) { arrived += frames; }

    uint32_t read(float* dst, uint32_t maxFrames) {
        ++reads;
        lastAsk = maxFrames;
        const uint64_t ready = arrived - produced;
        const uint32_t got = static_cast<uint32_t>(ready < maxFrames ? ready : maxFrames);
        for (uint32_t i = 0; i < got; ++i) {
            for (uint32_t c = 0; c < channels; ++c) dst[static_cast<size_t>(i) * channels + c] = static_cast<float>(produced + i + 1);
        }
        produced += got;
        return got;
    }
};

struct Pulled {
    std::vector<float> samples;
    uint32_t shortFrames = 0;
};

Pulled pull(tembra::InputReservoir& reservoir, FakeInput& input, uint32_t frames) {
    Pulled out;
    out.samples.assign(static_cast<size_t>(frames) * input.channels, -1.0f);
    out.shortFrames = reservoir.pull(out.samples.data(), frames, [&](float* dst, uint32_t max) { return input.read(dst, max); });
    return out;
}

}  // namespace

TEST_CASE("the reservoir primes with silence, uncounted, until a block plus the cushion has arrived") {
    tembra::InputReservoir reservoir(4096, 2);
    reservoir.configure(1, 96);
    FakeInput input;

    input.arrive(96);  // one burst: not yet a block plus the cushion
    const Pulled first = pull(reservoir, input, 96);
    CHECK(first.shortFrames == 0);
    CHECK_FALSE(reservoir.primed());
    for (float sample : first.samples) CHECK(sample == 0.0f);

    input.arrive(96);  // now 192 = 96 + 96
    const Pulled second = pull(reservoir, input, 96);
    CHECK(second.shortFrames == 0);
    CHECK(reservoir.primed());
    CHECK(second.samples.front() == 1.0f);  // the first frame the input delivered, nothing skipped
    CHECK(second.samples.back() == 96.0f);
    CHECK(reservoir.heldFrames() == 96);    // the cushion stays behind
}

TEST_CASE("a burst that arrives late but within the cushion is absorbed, with no short read and no gap") {
    tembra::InputReservoir reservoir(4096, 2);
    reservoir.configure(1, 96);
    FakeInput input;
    input.arrive(192);
    pull(reservoir, input, 96);  // primes: frames 1..96 out, 97..192 held

    float expected = 97.0f;
    uint32_t shorts = 0;
    // The input arrives in 96-frame bursts, but every third one only after the next output callback (a late burst).
    for (int block = 0; block < 300; ++block) {
        if (block % 3 != 1) input.arrive(96);
        if (block % 3 == 2) input.arrive(96);  // the late burst lands with the next one
        const Pulled out = pull(reservoir, input, 96);
        shorts += out.shortFrames;
        for (float sample : out.samples) {
            CHECK(sample == expected);
            expected += 1.0f;
        }
    }
    CHECK(shorts == 0);
}

TEST_CASE("input later than the cushion counts the missing frames once and keeps every frame in order") {
    tembra::InputReservoir reservoir(4096, 2);
    reservoir.configure(1, 96);
    FakeInput input;
    input.arrive(192);
    pull(reservoir, input, 96);  // 97..192 held

    // Nothing arrives for two callbacks: the first uses the cushion, the second runs dry.
    const Pulled cushion = pull(reservoir, input, 96);
    CHECK(cushion.shortFrames == 0);
    CHECK(cushion.samples.front() == 97.0f);
    const Pulled dry = pull(reservoir, input, 96);
    CHECK(dry.shortFrames == 96);
    for (float sample : dry.samples) CHECK(sample == 0.0f);

    // The stream catches up; the next frame out is 193, the one after the last delivered: nothing lost or repeated.
    input.arrive(288);
    const Pulled after = pull(reservoir, input, 96);
    CHECK(after.shortFrames == 0);
    CHECK(after.samples.front() == 193.0f);
    CHECK(reservoir.heldFrames() == 96);  // the cushion is rebuilt from the backlog
}

TEST_CASE("the reservoir never reads further ahead than a block plus the cushion") {
    tembra::InputReservoir reservoir(4096, 2);
    reservoir.configure(1, 96);
    FakeInput input;
    input.arrive(10000);  // a backlog in the stream's own buffer
    pull(reservoir, input, 96);
    CHECK(input.produced == 192);  // the rest stays in the stream, as it would without the reservoir
    pull(reservoir, input, 96);
    CHECK(input.produced == 288);
    CHECK(input.lastAsk == 96);
}

TEST_CASE("stereo frames stay interleaved through the reservoir") {
    tembra::InputReservoir reservoir(4096, 2);
    reservoir.configure(2, 48);
    FakeInput input;
    input.channels = 2;
    input.arrive(144);
    const Pulled out = pull(reservoir, input, 96);
    REQUIRE(out.samples.size() == 192);
    CHECK(out.samples[0] == 1.0f);
    CHECK(out.samples[1] == 1.0f);
    CHECK(out.samples[190] == 96.0f);
    CHECK(out.samples[191] == 96.0f);
    const Pulled next = pull(reservoir, input, 48);
    CHECK(next.samples[0] == 97.0f);
}

TEST_CASE("configure empties the reservoir and primes again; a block larger than the capacity is clamped") {
    tembra::InputReservoir reservoir(256, 1);
    reservoir.configure(1, 64);
    FakeInput input;
    input.arrive(1000);
    pull(reservoir, input, 128);
    CHECK(reservoir.primed());
    reservoir.configure(1, 64);
    CHECK_FALSE(reservoir.primed());
    CHECK(reservoir.heldFrames() == 0);
    CHECK(reservoir.cushionFrames() == 64);

    // 512 frames asked of a 256-frame reservoir: it delivers what it can hold and reports the rest as short, safely.
    std::vector<float> big(512, -1.0f);
    FakeInput more;
    more.arrive(10000);
    tembra::InputReservoir small(256, 1);
    small.configure(1, 64);
    const uint32_t shortFrames = small.pull(big.data(), 512, [&](float* dst, uint32_t max) { return more.read(dst, max); });
    CHECK(shortFrames <= 512);
    for (float sample : big) CHECK(sample >= 0.0f);
}
