/*
 * Tembra Audio tracing facade, C ABI (feature 007, contracts/tracing-facade.md).
 *
 * The Kotlin facade (iOS) and the engine's non-real-time C++ call these to record named intervals and counters
 * under the catalogue's signpost names (app/shared/engine/perf-catalogue.txt). With TEMBRA_TRACING=1 (measurement
 * builds) they map to os_signpost on Apple platforms and ATrace on Android; otherwise they are empty stubs and the
 * C++ macros expand to nothing, so no catalogue name exists in a release binary (FR-014). Never on the audio
 * callback: under TEMBRA_TRACE_POISON every function is unavailable, which is how the engine's test target proves
 * the real-time sources contain none of it.
 */
#ifndef TEMBRA_TRACE_H
#define TEMBRA_TRACE_H

#ifdef __cplusplus
extern "C" {
#endif

#if defined(TEMBRA_TRACE_POISON)
#define TEMBRA_TRACE_UNAVAILABLE __attribute__((unavailable("tracing is not allowed on the audio thread")))
#else
#define TEMBRA_TRACE_UNAVAILABLE
#endif

/* Interval begin/end on os_log_create("audio.tembra", category); the name is the catalogue name (e.g. "tuner.frame"). */
TEMBRA_TRACE_UNAVAILABLE void tembra_trace_begin(const char* category, const char* name);
TEMBRA_TRACE_UNAVAILABLE void tembra_trace_end(const char* category, const char* name);
/* Animation interval (iOS attributes hitches to it; a plain section on Android). */
TEMBRA_TRACE_UNAVAILABLE void tembra_trace_animation_begin(const char* category, const char* name);
TEMBRA_TRACE_UNAVAILABLE void tembra_trace_animation_end(const char* category, const char* name);
/* Counter sample: an os_signpost event "<name>=<value>" on Apple, ATrace_setCounter on Android. */
TEMBRA_TRACE_UNAVAILABLE void tembra_trace_counter(const char* name, long long value);
/* 1 when the shim records anything (TEMBRA_TRACING=1), 0 in inert builds. */
int tembra_trace_enabled(void);

#ifdef __cplusplus
}

#if defined(TEMBRA_TRACING) && TEMBRA_TRACING
namespace tembra {
/** RAII section on category "engine"; empty in inert builds. */
class TraceScope {
public:
    explicit TraceScope(const char* name) : name_(name) { tembra_trace_begin("engine", name_); }
    ~TraceScope() { tembra_trace_end("engine", name_); }
    TraceScope(const TraceScope&) = delete;
    TraceScope& operator=(const TraceScope&) = delete;
private:
    const char* name_;
};
}  // namespace tembra
#define TEMBRA_TRACE_SCOPE(name) ::tembra::TraceScope tembraTraceScope_##__LINE__(name)
#define TEMBRA_TRACE_COUNTER(name, value) tembra_trace_counter((name), (long long)(value))
#else
#define TEMBRA_TRACE_SCOPE(name) ((void)0)
#define TEMBRA_TRACE_COUNTER(name, value) ((void)0)
#endif
#endif /* __cplusplus */

#endif /* TEMBRA_TRACE_H */
