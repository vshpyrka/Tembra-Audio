// The os_signpost shim (feature 007): with TEMBRA_TRACING=1 the Apple binding begins and ends intervals, animation
// intervals and counters without crashing on the macOS host; the Android binding is a link-only concern of the
// NDK build. The engine test build passes -DTEMBRA_TRACING=ON so this exercises the real binding.
#include <catch2/catch_test_macros.hpp>

#include "tembra_trace.h"
#include "tembra_trace_names.h"

TEST_CASE("the shim reports whether it records", "[trace][shim]") {
#if defined(TEMBRA_TRACING) && TEMBRA_TRACING
    REQUIRE(tembra_trace_enabled() == 1);
#else
    REQUIRE(tembra_trace_enabled() == 0);
#endif
}

TEST_CASE("intervals, animation intervals and counters do not crash", "[trace][shim]") {
    tembra_trace_begin("ui", TEMBRA_TRACE_TUNER_MODE_SWITCH);
    tembra_trace_end("ui", TEMBRA_TRACE_TUNER_MODE_SWITCH);
    tembra_trace_animation_begin("ui", TEMBRA_TRACE_METRONOME_TEMPO_CHANGE);
    tembra_trace_animation_end("ui", TEMBRA_TRACE_METRONOME_TEMPO_CHANGE);
    tembra_trace_counter(TEMBRA_TRACE_AUDIO_XRUNS, 0);
    {
        TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_LOAD_CLIP);
        TEMBRA_TRACE_COUNTER(TEMBRA_TRACE_AUDIO_CALLBACKS, 12);
    }
    SUCCEED();
}

TEST_CASE("the generated name table matches the catalogue count", "[trace][shim]") {
    REQUIRE(TEMBRA_TRACE_NAME_COUNT == 16);
}
