# Implementation Plan: Performance Pipeline

**Branch**: `007-performance-pipeline` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-performance-pipeline/spec.md`, plus the `/speckit-plan` input: collect metrics, graph them, track regressions at the round they appear, a visual report with human-readable text and a device grid that opens per-device detail, room for more devices, run on every booted iOS simulator too, and measure after each feature lands.

## Summary

Adopt the owner's trace-pulse pipeline into `app/perf/` (Python, `uv`), keep its validated step-fit detector, statistics and report engine, and extend it with: an iOS collector built on Apple's stock tooling (`xcodebuild test-without-building` + `xcresulttool get test-results metrics`, with `xctrace --attach` for the audio thread), a device-keyed history of per-round records synchronised through a `perf-history` branch, an informational per-metric gate, labelled multi-round sessions, simulator runs, and a device-grid report. The app gains a measurement build on both platforms, a single tracing facade (`PerfTrace` in `:shared:engine`, `tembra_trace.h` in the engine) mapped to Android system tracing and iOS `os_signpost`, audio-thread health counters in the engine ABI and telemetry, `ReportDrawnWhen` and journey anchors. Journeys run inside Macrobenchmark (`:perf-benchmarks`, included only with `-Ptembra.perf=true`) and inside XCTest `measure` (`TembraPerfTests` hosted by `iosApp`). No CI workflow or runner is created; the commands are shaped for one. Decisions and spike results: [research.md](research.md).

The plan input adds two things to the spec: simulator rounds (recorded as FR-002a in the spec, non-reference series, gates inactive) and the device-grid navigation (recorded in `contracts/report.md`); more devices need no code change because series are keyed by device id and the registry grows on first sight.

## Technical Context

**Language/Version**: Python 3.12 via `uv` for the pipeline (`requires-python >=3.9,<3.14` so the 2015 Mac can use its system 3.9 if needed); Kotlin 2.4.20 (KMP) for the facade, catalogue, telemetry type and `:perf-benchmarks`; Swift 5.9+ for `TembraPerfTests` and the iOS entry-point change; C/C++17 for the engine ABI additions and the signpost shim; Gradle 9.7.0 with AGP 9.3.3; Xcode 26.6.

**Primary Dependencies**: trace-pulse (copied; PyYAML, scipy ≥1.13.1 <2, perfetto 0.57.2, Jinja2, pytest — none shipped in the app); Jetpack Macrobenchmark 1.5.0 + UiAutomator (test module only); XCTest/XCUITest; `android.os.Trace` and `<android/trace.h>` (platform); `os/signpost.h` (platform); Compose Multiplatform 1.12.1 already present (`ReportDrawnWhen`, `enableTraceOSLog()`). No new runtime dependency in the shipped app.

**Storage**: per-round JSON records on the `perf-history` branch (source of truth; schema `contracts/round-record.schema.json`); derived SQLite cache `app/perf/data/tracepulse.db` (trace-pulse schema + `label`, `device_id`, gate table); raw artifacts under `app/perf/data/rounds/<round_id>/` (git-ignored); `app/perf/reports/report.html`.

**Testing**: pytest (`app/perf/tests`, trace-pulse's 272 tests plus iOS collector, gate, records, catalogue, report golden); `kotlin.test` in `commonTest` of `:shared:engine` (facade sink, catalogue/header parity, audio-health plumbing with `FakeAudioEngine`); Catch2 (`engineHostTest`: ABI round-trip, callback timing, real-time poison); one instrumented check in the `checkAndroidDevice` lane (a section visible in a Perfetto trace); XCUITest performance tests on the iPhone 14 (`TembraPerfTests`, run by the pipeline, with one self-check test asserting measurement counts).

**Target Platform**: Android 8+ (minSdk 26; counters on API 29+) measured on Pixel 6 Pro / 7 Pro (Android 17); iOS 15+ deployment, measured on iPhone 14 (iOS 27.0.1) and booted simulators; the pipeline runs on macOS (Apple Silicon for iOS; the 2015 MacBook Pro for Android only).

**Project Type**: mobile app tooling — a Python CLI, an Android test module, an iOS UI-test target, small changes to the KMP modules and the C++ engine.

**Performance Goals**: a round in ≤ 20 min of device time (SC-001/002); round-median CoV ≤ 3 % for startup and memory under the protocol (SC-003); injected regressions confirmed at onset with zero false confirmations (SC-004); default build and lanes within 5 % of today (SC-008); report ≤ 5 MB for 50 devices × 200 rounds and opens offline.

**Constraints**: nothing Kotlin and no tracing on the audio callback (Principle I; enforced by the poison test); the facade and catalogue absent from release binaries (SC-007; `const val` + DCE); the `:perf-benchmarks` module invisible to the default build; no interactive input in any command; history records trace-free; no CI, farm, Maestro or field telemetry in this feature.

**Scale/Scope**: 3 physical devices + 3 simulators today, designed for tens of devices; 4 journeys; ~25 metric keys per platform; sessions of 5 rounds per device per feature (≈ 75 min of device time per device).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How the plan satisfies it |
|---|---|---|
| I. Real-time engine isolation | PASS | Health counters are relaxed atomics written on the callback from a monotonic clock; `getXRunCount` is sampled by the drain thread; the facade is `__attribute__((unavailable))` in the real-time sources under the poison test; no Kotlin on the callback, no new callbacks into Kotlin (`tembra_engine_audio_health` is a plain non-RT getter). |
| II. Shared Kotlin control plane, fixed module graph | PASS with one justified addition | `PerfTrace`/`PerfSection`/`AudioHealth` live in `:shared:engine` (platform SDK access via `expect`/`actual`, the reserved use); `:shared:ui` → `:shared:engine` is an allowed edge; `:shared:core` untouched; telemetry read on the draw-phase drain, never `StateFlow`. `:perf-benchmarks` is a `com.android.test` module outside the fixed graph — see Complexity Tracking. |
| III. Test-first | PASS | Every piece has a failing test first: pytest fixtures for the iOS collector (spike 1 JSON), gate and records; `commonTest` for the facade sink and audio-health plumbing with the fake engine; Catch2 for the ABI and the poison; the report golden; Kotlin tests never link the real engine. |
| IV. Adaptive, input-agnostic UI | PASS (n/a to app UI) | No visible app change; anchors are `testTag`s (semantics the principle already requires); `ReportDrawnWhen` is invisible. The report is an HTML artefact, keyboard-navigable, themed from `DESIGN.md` tokens. |
| V. Document model in beats | PASS (n/a) | No document changes. |
| VI. Simplicity | PASS with two justified choices | No new shipped dependency (`android.os.Trace`, `os_signpost` are platform; Macrobenchmark is test-only). Own facade instead of Compose's `trace()` and a conditional test module — see Complexity Tracking. `init` scaffolder and `module/benchmarks-template` are deleted from the copied tool. |
| VII. Code organisation | PASS | One type per file (`PerfTrace.kt`, `PerfSection.kt`, `PerfFamily.kt`, `PerfCounter.kt`, `PerfBuild.kt` (generated), `AudioHealth.kt`, `PerfAnchors.kt`); feature packages `engine/perf`, `ui/perf`; no TODOs without issues. |
| VIII. Skill-guided Kotlin/Compose | PASS | `kotlin-api-design` for the `expect` facade and the `AudioHealth` value type; `kotlin-control-flow` for the exhaustive `when` in the catalogue test; `compose-state-and-effects` for placing `ReportDrawnWhen` in the wiring composables keyed by the first-content state; `compose-ui-testing-patterns` for the anchor semantics; `gradle-run` for every agent-initiated Gradle command (building the app, running lanes, producing the artifacts an agent passes to `run --no-build`); the pipeline itself is user tooling and calls `./gradlew --console=plain` directly with the log captured per round; `dataviz` before any report chart code. Routed through `using-chrisbanes-skills` at task time. |
| Workflow: continuous integration | PASS (known repository gap, outside scope) | The constitution expects CI lanes on every push; the repository has none today and the owner excluded CI from this feature (FR-018). Nothing here worsens that; the commands are shaped for a future CI job. |
| Workflow & gates | PASS | Build order unchanged (tooling); definition of done per task (failing test first, lanes green, zero warnings, no forbidden dependency); constitution's CI expectations are untouched (no CI exists yet; out of scope by the owner's decision). |

**Post-design re-check (after Phase 1)**: unchanged — the contracts added no shipped dependency, no new module beyond `:perf-benchmarks`, no path from the callback to the facade.

## Project Structure

### Documentation (this feature)

```text
specs/007-performance-pipeline/
├── plan.md              # this file
├── research.md          # Phase 0: spike results and decisions D1–D11
├── data-model.md        # Phase 1: Device, Round, Journey result, Metric result, Detection, Gate decision, catalogues
├── quickstart.md        # Phase 1: validation guide (unit suites, Android round, iOS round, report/gate, instrumentation, audio health, sessions, lanes)
├── contracts/
│   ├── cli.md                     # tracepulse commands, exit statuses, tracepulse.yml
│   ├── round-record.schema.json   # the history-branch record
│   ├── tracing-facade.md          # PerfTrace / PerfSection / tembra_trace.h, build flags, real-time guard
│   ├── engine-audio-health.md     # C ABI struct + getter, Kotlin AudioHealth, pipeline mapping
│   ├── metric-catalogue.md        # keys per platform, sources, units, gate defaults, comparability
│   └── report.md                  # views (#/, #/device/<id>, #/device/<id>/<metric>), human text, non-functional
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
app/
├── tracepulse.yml                         # registry, journeys, gate overrides, platform settings (contracts/cli.md)
├── perf/                                  # trace-pulse adopted (Python, uv); no Gradle
│   ├── pyproject.toml  .python-version  README.md
│   ├── tracepulse/
│   │   ├── __main__.py  config.py  catalogue.py  devices.py  records.py  history.py   # new: registry, catalogue, record I/O, branch sync
│   │   ├── orchestrate_android.py  collect_android.py  traces_android.py             # from orchestrate/collect/traces (de-templated)
│   │   ├── orchestrate_ios.py  collect_ios.py  traces_ios.py  size_ios.py            # new: devicectl/simctl, xcodebuild, xcresulttool, xctrace, thinning report
│   │   ├── store.py  detect.py  gate.py  report.py  text.py                            # store (+label/device/gate), detect (unchanged), gate (new), report (grid/device/metric), text (plain-language)
│   │   └── templates/report.html.j2  static/report.css  static/report.js
│   ├── tests/            # trace-pulse suite + test_collect_ios.py test_gate.py test_records.py test_history.py test_catalogue.py test_report_grid.py
│   ├── fixtures/         # xcresult-metrics.json (spike 1), engine-health.json, audio-ioproc.xml (spike 3), perfetto counter trace, history fixture
│   └── regressions/      # R1–R4 ported to Tembra journeys + registry.yaml
├── perf-benchmarks/                       # com.android.test, included only with -Ptembra.perf=true
│   ├── build.gradle.kts
│   └── src/main/kotlin/audio/tembra/perf/
│       ├── StartupBenchmark.kt  TunerJourneyBenchmark.kt  MetronomeJourneyBenchmark.kt  StressJourneyBenchmark.kt
│       ├── journeys/TunerJourney.kt  MetronomeJourney.kt  StressJourney.kt  Anchors.kt   # UiAutomator steps over testTags
│       └── PerfMetrics.kt                                                                # metric list per journey
├── iosApp/
│   ├── TembraPerfTests/                   # XCUITest bundle hosted by iosApp (Perf configuration, TembraPerf scheme, test plan)
│   │   ├── Preflight.swift  PerfJourneyTestCase.swift  Journeys/TunerJourney.swift  MetronomeJourney.swift  StressJourney.swift
│   │   ├── StartupPerfTests.swift  TunerPerfTests.swift  MetronomePerfTests.swift  StressPerfTests.swift  FacadeSelfCheckTests.swift
│   │   └── Metrics.swift                  # XCTMetric lists per journey, iterationCount = 20
│   ├── iosApp/iOSApp.swift                # enableTraceOSLog() under PERF_ENABLED; engine-health.json writer hook (measurement build)
│   └── iosApp.xcodeproj                   # + Perf configuration, TembraPerf scheme, TembraPerfTests target, run-script passes -Ptembra.perf
├── shared/engine/
│   ├── build.gradle.kts                   # generatePerfBuild (PerfBuild.kt + PerfNames.kt from -Ptembra.perf and perf-catalogue.txt); generateTraceNames; cinterop def includes tembra_trace.h
│   ├── perf-catalogue.txt                 # single source of signpost names: <ENUM_NAME> <family> <signpost.name>
│   └── src/{commonMain,androidMain,iosMain,commonTest}/kotlin/audio/tembra/engine/perf/
│       ├── PerfTrace.kt (expect)  PerfTrace.android.kt  PerfTrace.ios.kt  PerfTraceSink.kt  PerfSection.kt  PerfFamily.kt  PerfCounter.kt
│       ├── AudioHealth.kt  AudioHealthPublisher.kt  (+ AudioEngine.audioHealth(), FakeAudioEngine.scriptAudioHealth)
│       └── commonTest: PerfBuildTest.kt  PerfTraceTest.kt  PerfSectionCatalogueTest.kt  AudioHealthTelemetryTest.kt
├── shared/ui/src/commonMain/kotlin/audio/tembra/ui/perf/PerfAnchors.kt  AudioHealthTicker.kt   # testTag constants; the one-second ticker (measurement builds); ReportDrawnWhen in Home/Tuner/Metronome wiring composables; devtools "write engine health" action in ui/devtools/
├── engine/
│   ├── include/tembra_engine.h            # + tembra_audio_health, tembra_engine_audio_health()
│   ├── include/tembra_trace.h             # facade C API + macros; poison mode
│   ├── src/platform/trace_android.cpp  trace_apple.c                           # ATrace / os_signpost bindings
│   ├── src/core/rt/…                      # callback writes the health atomics
│   ├── tests/audio_health_test.cpp  trace_poison_test.cpp
│   └── CMakeLists.txt                     # option(TEMBRA_TRACING OFF)
├── androidApp/build.gradle.kts            # buildType perf (initWith release, isProfileable, debug signing)
├── settings.gradle.kts                    # if (providers.gradleProperty("tembra.perf").isPresent) include(":perf-benchmarks")
└── README.md                              # "where does X go" row for perf; pointer to app/perf/README.md
.gitignore                                  # app/perf/data, app/perf/reports, app/perf/.history, app/perf/.venv
```

**Structure Decision**: Python pipeline under `app/perf/` next to the Gradle root (it reads `tracepulse.yml` at `app/` and invokes `./gradlew` and `xcodebuild` from there); the Android benchmark module is a sibling Gradle module gated by a property; the iOS tests are a target of the existing Xcode project; instrumentation lives where platform SDK access already lives (`:shared:engine` and the engine's platform sources), not in `:shared:core`.

## Implementation order (for `/speckit-tasks`)

The phases in `tasks.md` govern; this is the summary of their order.

1. **Setup and foundation** (tasks Phases 1–2): copy trace-pulse into `app/perf/` with `uv`, suite green; catalogue, round records and the `perf-history` branch, device registry, derived store; `PerfBuild`/`PerfNames` generation, Android `perf` build type, CMake option, gated `:perf-benchmarks`; `PerfAnchors` and `ReportDrawnWhen`; CLI skeleton.
2. **US1 Android round** (MVP): `:perf-benchmarks` with four journeys, Android collectors, `run.py` with the per-device lock, first rounds on the Pixel.
3. **US2 iOS round**: Xcode `Perf` configuration, `TembraPerf` scheme, `TembraPerfTests` with preflight and journeys; iOS collectors, size as an opt-in step, simulators; the device run closes spike 1 on the device and spike 5.
4. **US3 report and gate**: `gate.py`, plain-language text, grid/device/metric views, golden.
5. **US4 instrumentation**: `perf-catalogue.txt`, `PerfSection`/`PerfCounter`/`PerfTrace`, C shim + `tembra_trace.h` + poison test, `enableTraceOSLog()`, instrumentation points (non-composable work only), release-binary signpost-string check; spike 7 closes here.
6. **US5 audio health**: ABI struct/getter, callback atomics, Catch2, Kotlin `AudioHealth` + fake, the one-second ticker in the root wiring composable, devtools readout, iOS pull and Audio System Trace.
7. **US6 sessions**: `--rounds`/`--label`, label boundaries and attribution in the report.
8. **Polish**: regression injections R1–R4 ported (incl. the iOS confirmation), docs, lane timing (SC-008), the ten-round noise baseline (SC-003), quickstart end to end.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `:perf-benchmarks`, a `com.android.test` module outside the fixed module graph (Principle II) | Jetpack Macrobenchmark can only run from a separate test module targeting the app; it is the Android measurement harness the owner's tool is built on | Benchmarks inside `:androidApp` are not supported by Macrobenchmark; an external project would break "tests live with the app" (owner's decision). Mitigation: included only with `-Ptembra.perf=true`, depends on nothing, nothing depends on it. |
| Own tracing facade instead of the already-present `androidx.compose.ui.util.trace` (Principle VI "prefer the decided libraries") | Compose's iOS mapping emits one fixed signpost name with the section as a message, so a single section cannot be measured by `XCTOSSignpostMetric(name:)`; the catalogue must be the only source of names (FR-012) | Using Compose's `trace()` on Android and a parallel iOS mechanism would be two facades; patching Compose is not an option. Compose's own `trace()` stays enabled for its frame phases. |
| Generated `PerfBuild.kt` constant and `PerfNames.kt` name table from a Gradle property (a small build-time code generator) | FR-014/SC-007 require instrumentation names absent from release binaries; the Android release build does not shrink, so dead-code elimination cannot be relied on to drop strings; generating the names only into measurement builds is the one way to keep them out | A runtime flag keeps the strings in the binary; enabling R8 for release is a product-wide change outside this feature; a separate source set per build type would duplicate the facade. |
