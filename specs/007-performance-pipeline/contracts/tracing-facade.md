# Contract: tracing facade (Kotlin and C++)

One catalogue of names, one call shape, two platform bindings. Lives in `:shared:engine` (`audio.tembra.engine.perf`) and `engine/include/tembra_trace.h`. Guarded by `PerfBuild.PERF_ENABLED` (Kotlin) and `TEMBRA_TRACING` (C/C++); both are driven by the Gradle property `tembra.perf=true`.

## Kotlin (commonMain, `:shared:engine`)

```kotlin
// PerfSection.kt — the catalogue entries; no string literals here (see "Names" below)
enum class PerfSection(val family: PerfFamily) {
    TUNER_LOAD(TUNER), TUNER_FRAME(TUNER), TUNER_MODE_SWITCH(TUNER),
    METRONOME_LOAD(METRONOME), METRONOME_TICK(METRONOME), METRONOME_TEMPO_CHANGE(METRONOME),
    TIMELINE_SCROLL(TIMELINE), TIMELINE_ZOOM(TIMELINE),
    ENGINE_LOAD_CLIP(ENGINE), ENGINE_GRAPH_SWAP(ENGINE),
    // feature 009 (contracts/perf-instrumentation.md of specs/009-timeline-arranger)
    TIMELINE_PROJECT_OPEN(TIMELINE), TIMELINE_WAVEFORM(TIMELINE), TIMELINE_PLAYHEAD_FRAME(TIMELINE),
    TIMELINE_CLIP_SPLIT(TIMELINE), TIMELINE_PROJECT_SAVE(TIMELINE), TIMELINE_LIBRARY_OPEN(TIMELINE),
    TRANSPORT_PLAY(TRANSPORT), TRANSPORT_SEEK(TRANSPORT), ENGINE_PRIME(ENGINE),
    TRANSPORT_MASTER_POPUP(TRANSPORT),  // feature 009's design iteration D1
}
enum class PerfFamily { TUNER, METRONOME, TIMELINE, TRANSPORT, ENGINE }  // TRANSPORT since feature 009
enum class PerfCounter { AUDIO_CALLBACKS, AUDIO_LAST_US, AUDIO_MAX_US, AUDIO_OVER_BUDGET, AUDIO_XRUNS, AUDIO_REALTIME }

// PerfTrace.kt — expect object; every function is a no-op when !PerfBuild.PERF_ENABLED
expect object PerfTrace {
    inline fun <T> section(section: PerfSection, block: () -> T): T      // begin/end around block (non-composable work only)
    inline fun <T> animation(section: PerfSection, block: () -> T): T    // iOS: animation signpost; Android: plain section
    fun counter(counter: PerfCounter, value: Long)
}
```

**Names.** `app/shared/engine/perf-catalogue.txt` is the single source of signpost names, one line per entry: `<ENUM_NAME> <family> <signpost.name>` (e.g. `TUNER_MODE_SWITCH tuner tuner.modeSwitch`, `AUDIO_XRUNS counter audio.xruns`; feature 009 adds `AUDIO_AVG_US`, `AUDIO_STARVATION` and `AUDIO_CLIPS_ACTIVE`). The Gradle task `generatePerfBuild` generates `PerfBuild.kt` (`const val PERF_ENABLED`) and `PerfNames.kt` (`internal fun PerfSection.signpostName(): String`, `internal fun PerfCounter.signpostName(): String`) into `build/generated/perf/commonMain/kotlin/audio/tembra/engine/perf/`; with `tembra.perf` present the functions return the catalogue strings, otherwise every one returns `""`. `generateTraceNames` generates `app/engine/include/tembra_trace_names.h` from the same file. Consequence: measurement builds carry the names, release and debug builds carry none of the catalogue strings (FR-014, SC-007); enum identifiers such as `TUNER_FRAME` may remain in an unshrunk release dex and are not instrumentation names. A `commonTest` asserts enum ↔ catalogue ↔ header parity.

Rules: sections wrap **non-composable** work only — ViewModel intents, draw-lambda computations, loaders, engine paths — never a composable lambda or composition itself (`section` is a plain inline function; routing composition through it would also violate Principle II); never called from the audio callback (the callback is C++ and never includes `tembra_trace.h`, see below); `section` and `animation` are `inline` so the lambda allocates nothing; startup is measured by the platforms' launch metrics, not by a section. Skill contracts (Principle VIII): `kotlin-api-design` for the `expect` boundary (platform detail behind a common binding), `kotlin-control-flow` for the exhaustive `when` over `PerfFamily` in the parity test.

## Android actual (`androidMain`)

`android.os.Trace.beginSection(section.signpostName())` / `endSection()`; `animation` = `section`; `counter` → `android.os.Trace.setCounter(name, value)` on API ≥ 29, no-op below (minSdk 26). Sections appear to Macrobenchmark as `TraceSectionMetric(<signpost name>, Mode.Sum)` and to trace_processor in the `slice` table; counters in the `counter` table with the same names.

## iOS actual (`iosMain`)

Calls the C shim through cinterop (`tembra_engine.def` gains `tembra_trace.h`):

```c
// engine/include/tembra_trace.h  (public C ABI; compiled only with TEMBRA_TRACING=1, otherwise macros expand to nothing)
void tembra_trace_begin(const char* category, const char* name);            // os_signpost_interval_begin on os_log_create("audio.tembra", category)
void tembra_trace_end(const char* category, const char* name);
void tembra_trace_animation_begin(const char* category, const char* name);  // os_signpost_animation_interval_begin
void tembra_trace_animation_end(const char* category, const char* name);
void tembra_trace_counter(const char* name, long long value);               // os_signpost_event_emit with "%{public}s=%lld"

#define TEMBRA_TRACE_SCOPE(name)           /* C++ RAII begin/end on category "engine"; empty when !TEMBRA_TRACING */
#define TEMBRA_TRACE_COUNTER(name, value)  /* tembra_trace_counter; empty when !TEMBRA_TRACING */
```

Categories: `ui` for Kotlin sections, `engine` for C++ sections, `counter` for counters. The signpost **name is the catalogue name** (`tuner.frame`), so `XCTOSSignpostMetric(subsystem: "audio.tembra", category: "ui", name: "tuner.frame")` measures one section. Interval ids: `os_signpost_id_generate` per begin, matched by name on end (one outstanding interval per name per thread is the contract; nested same-name sections are a programming error caught by the facade test).

Android C++ binding: `ATrace_beginSection`/`ATrace_endSection`/`ATrace_setCounter` (`<android/trace.h>`, `ATrace_setCounter` on API ≥ 29 via `__builtin_available`).

## Build flags

| Property | Android | iOS | Engine |
|---|---|---|---|
| `-Ptembra.perf=true` | build type `perf` (`initWith(release)`, `isProfileable = true`, debug signing), `PerfBuild.PERF_ENABLED = true` | framework built with `PERF_ENABLED = true`; Xcode configuration `Perf` passes the property from the run-script when `$CONFIGURATION == Perf` | CMake `-DTEMBRA_TRACING=ON` |
| absent | release/debug as today, `PERF_ENABLED = false` | same | `TEMBRA_TRACING` off: macros empty, shim not compiled |

## Real-time guard (FR-014)

`engine/src/core/rt/` is the real-time path. A Catch2 test compiles a translation unit that includes every header under `src/core/rt/` with `-DTEMBRA_TRACING=1 -DTEMBRA_TRACE_POISON=1`; `tembra_trace.h` under `TEMBRA_TRACE_POISON` declares the functions `__attribute__((unavailable("not on the audio thread")))`, so any use from the real-time sources fails to compile. The audio callback publishes only atomics (see `engine-audio-health.md`).

## Tests

- `commonTest`: a `PerfTraceSink` test double (`PerfTraceSink.kt`, its own file) records calls; `section` returns the block's value and always ends on exceptions; names are unique and match the catalogue file and the C header list (both generated from `perf-catalogue.txt`, compared as text); with `PERF_ENABLED == false` every `signpostName()` is `""`.
- Android instrumented (`checkAndroidDevice` lane): one section appears in a Perfetto trace captured around the call (`record_android_trace`), asserted through trace_processor in the Python suite with a committed fixture.
- iOS: an XCUITest in `TembraPerfTests` measures a known section with `XCTOSSignpostMetric` and asserts `measurements.count == iterations` (part of task 1 on the device).
