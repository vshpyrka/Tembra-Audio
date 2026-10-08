# Research: Performance Pipeline (feature 007)

**Date**: 2026-10-08 | **Spec**: [spec.md](spec.md) | **Source brief**: `docs/research/performance-profiling.md` (the tool landscape; not repeated here)

This file records the decisions the plan rests on, the spikes the spec required before planning, and the facts checked in this repository and on this Mac. Each decision names what was chosen, why, and what was rejected.

## Spikes required by the spec (section 10 of the research brief)

### Spike 1 — real `measure()` run and the result-bundle JSON (done on the iPhone 17 simulator; device half becomes task 1)

Throwaway Swift package with two performance tests, run with `xcodebuild test -scheme PerfSpike-Package -destination 'platform=iOS Simulator,id=…' -resultBundlePath sim.xcresult` on Xcode 26.6:

- **Default iteration count is 5** (five values per metric with default `XCTMeasureOptions`); `options.iterationCount = 20` yields 20 values. Settled; the pipeline sets 20 explicitly.
- `xcrun xcresulttool get test-results metrics --path sim.xcresult --compact` returns a JSON **list** of `TestWithMetrics` (`testIdentifier` like `PerfSpikeTests/testTwentyIterations()`, `testRuns[]` each with `device {deviceId, deviceName}`, `testPlanConfiguration`, `metrics[]`); every metric carries `displayName`, `unitOfMeasurement`, `measurements[]` (per iteration), `identifier`, `polarity` (`prefers smaller`), `maxPercentRegression` (default 10), `maxPercentRelativeStandardDeviation` (default 10).
- Metric identifiers seen: `com.apple.dt.XCTMetric_Clock.time.monotonic` (s), `XCTMetric_CPU.time` (s), `XCTMetric_CPU.cycles` (kC), `XCTMetric_CPU.instructions_retired` (kI), `XCTMetric_Memory.physical` (kB; near zero on the simulator, it is a delta), `XCTMetric_Memory.physical_peak` (kB).
- `xcresulttool export metrics` writes CSV plus `manifest.json`; the CSV formats averages with the host locale's decimal comma (`0,0117 s`). **Decision: the collector reads the JSON, never the CSV.**
- Device run: `xcodebuild test` on `platform=iOS,id=<iPhone 14>` refuses a package test target: "Tool-hosted testing is unavailable on device destinations. Select a host application for the test target". Expected: device performance tests must live in a test bundle hosted by the app (`TembraPerfTests` as a UI-test target of `iosApp`). The extraction path is identical; the first implementation task repeats the measurement on the device through that target.

### Spike 3 — Audio System Trace export schema (done on the iPhone 14)

- `xctrace record --template 'Audio System Trace' --device <udid> --launch -- <bundle> --time-limit 12s --no-prompt` **hung for more than five minutes** without producing a trace (process alive, no output; the phone was unlocked). `xctrace record … --attach <pid>` after `xcrun devicectl device process launch --terminate-existing <bundle>` **completed in about a minute** and saved the trace. **Decision: the iOS collector launches the app with `devicectl` (or lets the XCUITest runner launch it) and attaches `xctrace`; it never uses `--launch`.**
- `xctrace export --toc` lists 64 tables. The ones the pipeline reads: `audio-hal-client-io-cycle-ioproc` (columns `start, duration, process, thread, name` — the client I/O-proc duration per cycle, i.e. the callback duration), `audio-hal-client-io-cycle` and `audio-hal-client-io-cycle-load` (per-cycle intervals and a 100 ms load histogram), `audio-hal-client-poi` (`timestamp, description, concept, detail` — audio events), `device-thermal-state-intervals`, `audio-hal-zero-timestamp-jitter`. Rows were empty because the app sat on the Home screen without audio; the stress journey will populate them.
- Export command shape: `xctrace export --input X.trace --xpath '/trace-toc/run[@number="1"]/data/table[@schema="audio-hal-client-io-cycle-ioproc"]'` → XML with `<col><mnemonic>…` headers and `<row>` elements.

### Spike 5 — `testTag` reaches XCUITest (documentation check)

JetBrains' Compose Multiplatform iOS accessibility page (dated 2025-07-22): "properties such as `testTag` will correctly map to native accessibility properties such as `accessibilityIdentifier`", making "semantic data from Compose Multiplatform available to Accessibility Services and XCTest framework"; the page shows `ComposeUIViewController(configure = { accessibilitySyncOptions = AccessibilitySyncOptions.Always(debugLogger = null) })` for testing. **To re-check on 1.12.1 during task 1** (the option has moved between releases); fallback is leaving the default sync mode, which the page says serves XCTest when assistive technologies are not running.

### Spike 6 — iOS app-size report (documentation check)

Apple, "Reducing your app's size": "the only way to get accurate download and installation sizes for your app is to create an app size report on your Mac"; `xcodebuild -exportArchive` with an export options plist whose `thinning` key is `<thin-for-all-variants>` writes a file named `App Thinning Size Report` in the export folder; it "lists the compressed and uncompressed sizes for each of your app's IPA files. The uncompressed size is equivalent to the size of the installed app on the device, and the compressed size is the download size". `xcodebuild -help` confirms `thinning : String` with `<thin-for-all-variants>` or a model identifier such as `iPhone7,1`. **Decision: `app.size_bytes` on iOS = compressed size of the iPhone 14 variant (`iPhone14,7`), uncompressed as an advisory series; the export uses the development method with the local team.**

### Spike 7 — animation signposts from Kotlin (deferred to implementation, with a fallback)

Needs the C shim built first. Task order puts the shim and a device run of `XCTOSSignpostMetric(subsystem:category:name:)` over an animation interval before the frames family is wired; if hitch columns do not appear, the iOS frames family uses `XCTHitchMetric(application:)` (iOS 26+; the iPhone 14 runs 27.0.1).

## Decisions

### D1. Where trace-pulse lives and how it is adopted

- **Decision**: copy `/Volumes/External/Dev/trace-pulse/cli/tracepulse` and its tests into `app/perf/` as a Python project managed with `uv` (`pyproject.toml`, `.python-version` = 3.12, `requires-python >=3.9,<3.14`), keep `detect.py`, `store.py`, `report.py`/`compare.py` and the Skia test vectors, drop `init` (the foreign-project scaffolder) and `module/benchmarks-template/`, and port the regression-injection self-test (`regressions/`) to Tembra journeys.
- **Rationale**: the owner's decision (spec §12); the detection and statistics are validated; `uv` avoids the 3.9/3.14 split on this Mac (system Python 3.9.6, Homebrew 3.14; scipy 1.13.1 has no 3.14 wheel) and installs on the 2015 Mac the same way.
- **Alternatives**: a Gradle plugin (rejected: Python already works and the statistics live in scipy); keeping trace-pulse external and shelling out (rejected by the owner: the tests must live with the app).
- **Dependencies**: PyYAML (MIT), scipy (BSD; pin relaxed to `>=1.13.1,<2` with a unit test guarding the exact Mann-Whitney p-values), perfetto 0.57.2 (Apache-2.0; its `trace_processor_shell` prebuilt is downloaded on first use), Jinja2 (BSD), pytest. None ship in the app binary.

### D2. The Android benchmark module

- **Decision**: a `com.android.test` module `:perf-benchmarks` at `app/perf-benchmarks/` (Macrobenchmark 1.5.0, `targetProjectPath = ":androidApp"`, `experimentalProperties["android.experimental.self-instrumenting"] = true`), **included in `settings.gradle.kts` only when `-Ptembra.perf=true`**, so the default build and lanes never configure it (FR-020).
- **Rationale**: Macrobenchmark requires a separate test module; conditional inclusion is the one way to keep configuration time untouched.
- **Alternatives**: always included (rejected: configuration cost on every build; a test-only module in the fixed module graph); benchmarks inside `:androidApp` (impossible for Macrobenchmark).
- **Constitution**: Principle II fixes the app module graph; this module depends on nothing and nothing depends on it; justified in the plan's Complexity Tracking.

### D3. Measurement builds

- **Android**: build type `perf` in `:androidApp` — `initWith(release)`, `isProfileable = true`, debug signing, `matchingFallbacks += "release"`, and the Gradle property `tembra.perf=true` generating `PerfBuild.kt` (`const val PERF_ENABLED = true`) into a generated `commonMain` source directory of `:shared:engine` (false otherwise). Macrobenchmark targets the `perf` build type.
- **iOS**: Xcode configuration `Perf` (duplicated from Release; `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym`), scheme `TembraPerf` with "Debug executable" off and a test plan containing `TembraPerfTests`; the existing run-script `./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode` passes `-Ptembra.perf=true` when `$CONFIGURATION == Perf` so the framework carries `PERF_ENABLED = true`; `enableTraceOSLog()` is called from the iOS entry point under the same constant.
- **Engine**: CMake option `TEMBRA_TRACING` (default OFF) defines `TEMBRA_TRACING=1`; the Android native build sets it for the `perf` build type, the iOS framework build when `tembra.perf=true`.
- **Rationale**: one property drives all three; `const val false` lets Kotlin/Native dead-code elimination and (once enabled) R8 drop the facade bodies and the catalogue strings from release binaries, which is what SC-007 checks with `strings`.
- **Alternative**: a runtime flag (rejected: names stay in release binaries, FR-014).

### D4. The tracing facade

- **Decision**: `audio.tembra.engine.perf` in `:shared:engine` — `expect object PerfTrace` with `section(name: PerfSection, block)`, `animation(name, block)` (iOS: animation signpost; Android: plain section), `counter(name: PerfCounter, value: Long)`; `enum class PerfSection` and `enum class PerfCounter` are the catalogue (FR-012, FR-014 "the catalogue is the only source of names" is enforced by the type system). Android actual: `android.os.Trace.beginSection/endSection` and, on API 29+, `android.os.Trace.setCounter` (minSdk is 26; below 29 counters are no-ops). iOS actual: calls into a C shim `tembra_trace_begin/end/animation_begin/animation_end/counter` compiled into the engine (`os_log_create("audio.tembra", <category>)` + `os_signpost_interval_begin/end`, `os_signpost_animation_interval_begin`, `os_signpost_event_emit` for counters) and bound through the existing cinterop def. Every entry is guarded by `PerfBuild.PERF_ENABLED`.
- **Why not Compose's `androidx.compose.ui.util.trace`**: its iOS mapping emits one fixed signpost name (`CMPInterval`) with the section as the message, so `XCTOSSignpostMetric(name:)` cannot target one section; it stays in use for Compose's own frame phases via `enableTraceOSLog()`.
- **Why not androidx.tracing**: JVM/Android only; `android.os.Trace` needs no dependency (Principle VI).
- **Why `:shared:engine`, not `:shared:core`**: `:shared:core` is pure domain with no platform APIs; the facade is platform SDK access, which the constitution reserves for `expect`/`actual` in the engine module; `:shared:ui` → `:shared:engine` is an allowed edge. The C++ side gets `engine/include/tembra_trace.h` with `TEMBRA_TRACE_SCOPE(name)` and `TEMBRA_TRACE_COUNTER(name, value)` expanding to ATrace (`<android/trace.h>`) on Android and the same shim on iOS, to nothing when `TEMBRA_TRACING` is off.
- **Never on the audio callback**: the engine test target compiles the real-time sources with `TEMBRA_TRACING=1` and a poisoned `tembra_trace.h` that fails the build when included from `src/core/rt/*` (the real-time path); the counters (D5) are the only RT-safe channel.

### D5. Audio-thread health counters

- **Decision**: `tembra_engine.h` gains `struct tembra_audio_health { uint64_t callbacks; uint32_t last_us; uint32_t max_us; uint32_t over_budget; uint32_t xruns; uint8_t realtime; }` and `tembra_engine_audio_health(const tembra_engine*, tembra_audio_health*)` (reads atomics; non-RT). The callback writes `last_us`/`max_us` from the monotonic clock around its body (RT-safe), increments `over_budget` when `last_us` exceeds the buffer period, and `xruns` from Oboe's `getXRunCount()` sampled by the drain thread on Android (the callback never calls it) and from the engine's own over-budget detection on iOS; `realtime` = Oboe low-latency mode granted / iOS workgroup join result. Kotlin: `AudioHealth` data class in `:shared:engine` telemetry, `FakeAudioEngine` scripts it.
- **Pipeline**: Android — the telemetry drain publishes the five values as counters through D4 (`PerfCounter.AUDIO_*`) so they land in the Perfetto trace; `traces.py` reads the counter tracks → `audio.*` series. iOS — the stress journey ends by asking the app (measurement build) to write `engine-health.json` in its Documents directory; the collector copies it with `xcrun devicectl device copy from --domain-type appDataContainer --domain-identifier <bundle>` (simulator: `simctl get_app_container <udid> <bundle> data`), plus an `xctrace --attach` Audio System Trace per stress journey → `audio.ioproc_p99_us` from spike 3's table. Both are zeros until the audio spine; the plumbing is tested with the fake engine (SC-010).

### D6. iOS collector

- `xcrun devicectl list devices --json-output` for discovery (physical: `transportType wired`, `pairingState paired`); `xcrun simctl list devices booted -j` for simulators.
- Build once per session: `xcodebuild build-for-testing -project iosApp.xcodeproj -scheme TembraPerf -configuration Perf -destination 'platform=iOS,id=<udid>' -derivedDataPath <round-dir>/DerivedData -allowProvisioningUpdates`; per round: fresh install (`devicectl device uninstall app` + the test-without-building install), one unmeasured warm-up launch (`devicectl device process launch --terminate-existing`), then `xcodebuild test-without-building -xctestrun <…>.xctestrun -destination … -resultBundlePath <round-dir>/perf.xcresult -only-testing:TembraPerfTests`.
- Preflight inside the runner: `setUp` reads `ProcessInfo.processInfo.thermalState` and `UIDevice.current.batteryLevel` (monitoring enabled); a failed check records an `XCTAttachment` named `preflight.json` and fails the test with a message starting `PERF-REFUSED:`; network state from `NWPathMonitor` goes into the same attachment and never fails. The collector exports attachments (`xcresulttool export attachments`) and maps `PERF-REFUSED` to exit status 2 (refused) without storing a round.
- Metrics per journey: startup — `XCTApplicationLaunchMetric(waitUntilResponsive: false)` and `(true)`; journeys — `XCTClockMetric`, `XCTCPUMetric(application:)`, `XCTMemoryMetric(application:)`, `XCTOSSignpostMetric(subsystem: "audio.tembra", category: "ui", name: <section>)` per catalogue section the journey exercises, and the animation signpost (or `XCTHitchMetric`) for scroll/zoom; `iterationCount = 20`.
- Simulators (plan-input addition, FR-002a): `tracepulse run --simulators` runs the same journeys on every booted simulator; records carry `kind: simulator`, `reference: false`; gates are inactive for simulator series by default; the report badges them. Android emulators are out of scope (Google: "strongly discouraged").

### D7. History: per-round records on a history branch

- **Decision**: orphan branch `perf-history`; one file per round at `records/<device-id>/<utc-timestamp>_<label>.json` (schema in `contracts/round-record.schema.json`); the CLI keeps a git worktree of the branch at `app/perf/.history` (git-ignored) and `publish` = write file + commit + push, `sync` = pull + rebuild `data/tracepulse.db` from all records (the SQLite file becomes a derived cache). Device id = Android serial or iOS UDID; the machine (`hostname`) is metadata.
- **Rationale**: the owner's decision; append-only distinct files never conflict (two machines, many devices); raw traces (tens of MB per iteration) stay out of git.
- **Alternatives**: committing the SQLite file (binary merges); a shared network folder (fragile across machines); job artifacts only (no CI yet).

### D8. Gate

- **Decision**: `gate.py` computes, per metric with ≥ N clean rounds (default 5) on the series, the change of the round median against the median of the last N clean round medians; above the per-metric limit in the worse direction → `above_limit` decision recorded on the round; command output prints a summary; exit status unchanged. Catalogue defaults: startup 15 %, memory 15 %, app size 2 %, sections 20 %, frames 25 %, audio 50 %; all overridable in `tracepulse.yml`. Simulator series: inactive by default.
- **Rationale**: the owner wants notices, not failures; per-family defaults track the measured noise (1–2 % for startup/memory, more for frames).

### D9. Report

- **Decision**: one self-contained HTML (Jinja2 template, inline SVG and vanilla JS, data embedded as JSON) with hash navigation: device grid (`#/`), device page (`#/device/<id>`), metric drill-down (`#/device/<id>/<metric>`); human-readable headlines generated from the detection and gate state (reusing trace-pulse's `PLAIN_*` dictionaries and `_metric_headline`); label (feature) boundaries drawn on every chart; simulator and non-reference badges; charts written under the `dataviz` skill. Contract in `contracts/report.md`.

### D10. Journeys and anchors

- **Decision**: four journeys, same names on both platforms: `startup` (cold), `tuner` (open Tuner, Auto Listen, switch to By Ear, back), `metronome` (open Metronome, start, change tempo, stop), `stress` (tuner and metronome together; audio health readout). Anchors are `Modifier.testTag` values listed in `PerfAnchors.kt` (`:shared:ui`), exposed to UiAutomator through `testTagsAsResourceId = true` on the root and to XCUITest through the accessibility identifier mapping (spike 5). `ReportDrawnWhen` on Home, Tuner and Metronome once their first content is composed.

### D11. Android device discovery and the device registry

- **Decision**: `tracepulse.yml` has a `devices:` map keyed by id with `platform`, `model`, `kind`, `reference`, per-device overrides (temperature limits as the Pixel 6 Pro already needs); `tracepulse devices` lists attached devices (`adb devices -l`, `devicectl list devices`, `simctl list devices booted`) and known ones; an unknown attached device is added to the registry on first `run` with its model read from the device, so new devices need no manual step.

## Facts checked in this repository and on this Mac

- Kotlin 2.4.20, AGP 9.3.3, Compose Multiplatform 1.12.1; `android-minSdk = 26`, `compileSdk 37`; `:androidApp` release build type has `isMinifyEnabled = false`; convention plugins live in `app/build-logic/src/main/kotlin/` (`tembra.android.app`, `tembra.kmp.library`, `tembra.kmp.compose`, `tembra.android.native`).
- iOS: `iosApp.xcodeproj` runs `./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode`; frameworks are looked up under `shared/ui/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`; `TembraShared` is a static framework; `Config.xcconfig` carries the local `TEAM_ID` (skip-worktree); the device bundle identifier carries the team suffix.
- Engine: CMake `tembra_engine_core` with `TEMBRA_BUILD_TESTS` option; cinterop def `shared/engine/src/nativeInterop/cinterop/tembra_engine.def`; no telemetry yet.
- Root lanes: `checkAndroidHost`, `checkIos`, `checkAndroidDevice`, `engineHostTest`; Gradle runs through the `gradle-run` wrapper (Principle VIII).
- Devices today: Pixel 6 Pro `19021FDEE001AH` on USB; iPhone 14 `00008110-001049660E3A401E`, iOS 27.0.1, Developer Mode on, paired, CoreDevice tunnel connected; booted simulators iPhone 17, iPad Pro 13-inch (M5), iPhone Duo.
- Xcode 26.6: `xcresulttool get test-results metrics` schema 0.1.0 and `export metrics`; `devicectl device copy from --domain-type appDataContainer --domain-identifier`; `devicectl device info lockState` (no battery/thermal from the host); xctrace templates include Audio System Trace, Animation Hitches, App Launch, Time Profiler.
- trace-pulse: `cli/pyproject.toml` dependencies `PyYAML>=6, scipy==1.13.1, perfetto==0.57.2, Jinja2>=3,<4`, `pytest`; suite 272 green at Phase 4c; `compare.py` already renders a human-first page.
