// The real-time guard (feature 007, contracts/tracing-facade.md, FR-014): the real-time sources compile with the
// tracing facade poisoned (every function __attribute__((unavailable))), so none of them can use it; and the
// negative check proves the poison bites: a translation unit that calls TEMBRA_TRACE_SCOPE under
// TEMBRA_TRACE_POISON fails to compile (tests/CMakeLists.txt runs it with try_compile and records the outcome).
#include <catch2/catch_test_macros.hpp>

#ifndef TEMBRA_POISON_NEGATIVE_FAILED
#error "tests/CMakeLists.txt must define TEMBRA_POISON_NEGATIVE_FAILED from the try_compile of trace_poison_negative.cpp"
#endif

TEST_CASE("the real-time sources build with the tracing facade poisoned", "[trace][rt]") {
    // Linking this test executable against tembra_rt_poison proves the build succeeded; nothing to run.
    SUCCEED("tembra_rt_poison compiled every real-time source with TEMBRA_TRACING=1 TEMBRA_TRACE_POISON=1");
}

TEST_CASE("a translation unit that traces under the poison fails to compile", "[trace][rt]") {
    REQUIRE(TEMBRA_POISON_NEGATIVE_FAILED == 1);
}
