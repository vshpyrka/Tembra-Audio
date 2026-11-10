// The real-time guard (feature 007, contracts/tracing-facade.md, FR-014): the real-time sources compile with the
// tracing facade poisoned (every function __attribute__((unavailable))), so none of them can use it; and the
// negative check proves the poison bites: a translation unit that calls TEMBRA_TRACE_SCOPE under
// TEMBRA_TRACE_POISON fails to compile (tests/CMakeLists.txt runs it with try_compile and records the outcome).
// Since feature 009 the list (TEMBRA_RT_SOURCES) also holds the transport's real-time units: timeline/clip_source.cpp
// (the fifo pop), mixer/track_strip.cpp, mixer/master_bus.cpp and transport/transport_renderer.cpp; the disk
// streamer, the decoder and the overview builder stay out of it because they trace on their own threads. Since
// feature 010 it also holds the capture path the callback runs: capture/capture_source.cpp, capture/peak_ring.h
// (through the source) and mixer/monitor_strip.cpp; the take recorder thread and the WAV writer stay out. Since
// feature 012 it also holds the mixer's meter, mixer/mix_meter.cpp (with mix_params.h, pan_law.h, recent_peak.h and
// mix_record.h through the strip, the renderer and the meter); mixer_abi.cpp stays out because it traces the pan apply.
#include <catch2/catch_test_macros.hpp>

#ifndef TEMBRA_POISON_NEGATIVE_FAILED
#error "tests/CMakeLists.txt must define TEMBRA_POISON_NEGATIVE_FAILED from the try_compile of trace_poison_negative.cpp"
#endif

TEST_CASE("the real-time sources build with the tracing facade poisoned", "[trace][rt]") {
    // Linking this test executable against tembra_rt_poison proves the build succeeded; nothing to run. The object
    // library lists, besides the feature-007 units, clip_source.cpp, track_strip.cpp, master_bus.cpp and
    // transport_renderer.cpp (feature 009, T047, T049, T050), capture_source.cpp, peak_ring.h and
    // monitor_strip.cpp (feature 010, T016), and mix_meter.cpp (feature 012, T005).
    SUCCEED("tembra_rt_poison compiled every real-time source with TEMBRA_TRACING=1 TEMBRA_TRACE_POISON=1");
}

TEST_CASE("a translation unit that traces under the poison fails to compile", "[trace][rt]") {
    REQUIRE(TEMBRA_POISON_NEGATIVE_FAILED == 1);
}
