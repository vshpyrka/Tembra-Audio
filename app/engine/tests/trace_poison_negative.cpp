// Compiled by tests/CMakeLists.txt with try_compile under TEMBRA_TRACING=1 TEMBRA_TRACE_POISON=1: it MUST fail,
// which proves the poison makes the facade unusable from a real-time translation unit.
#include "tembra_trace.h"

void forbidden_on_the_audio_thread() {
    TEMBRA_TRACE_SCOPE("engine.graphSwap");
}
