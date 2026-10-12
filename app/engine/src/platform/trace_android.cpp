// ATrace binding of the tracing facade (feature 007); compiled on Android.
#include "tembra_trace.h"

#if defined(TEMBRA_TRACING) && TEMBRA_TRACING
// minSdk is 26: ATrace_setCounter (API 29) is weak-linked and guarded with __builtin_available below.
#define __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__
#include <android/trace.h>

extern "C" {

void tembra_trace_begin(const char* category, const char* name) {
    (void)category;
    ATrace_beginSection(name);
}

void tembra_trace_end(const char* category, const char* name) {
    (void)category;
    (void)name;
    ATrace_endSection();
}

void tembra_trace_animation_begin(const char* category, const char* name) {
    (void)category;
    ATrace_beginSection(name);
}

void tembra_trace_animation_end(const char* category, const char* name) {
    (void)category;
    (void)name;
    ATrace_endSection();
}

void tembra_trace_counter(const char* name, long long value) {
    if (__builtin_available(android 29, *)) {
        ATrace_setCounter(name, static_cast<int64_t>(value));
    }
}

int tembra_trace_enabled(void) { return 1; }

}  // extern "C"

#else

extern "C" {
void tembra_trace_begin(const char*, const char*) {}
void tembra_trace_end(const char*, const char*) {}
void tembra_trace_animation_begin(const char*, const char*) {}
void tembra_trace_animation_end(const char*, const char*) {}
void tembra_trace_counter(const char*, long long) {}
int tembra_trace_enabled(void) { return 0; }
}

#endif
