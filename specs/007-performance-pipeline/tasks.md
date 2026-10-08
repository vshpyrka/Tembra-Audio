---

description: "Task list for feature 007: performance pipeline"
---

# Tasks: Performance Pipeline

**Input**: Design documents from `/specs/007-performance-pipeline/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (cli, round-record.schema.json, tracing-facade, engine-audio-health, metric-catalogue, report), quickstart.md

**Tests**: Included for every story. The constitution's Principle III (test-first) is non-negotiable: each test task is written first and must fail before its implementation task. Kotlin tests never link the real engine (`FakeAudioEngine`); Python tests run on fixtures; Catch2 runs without an audio device.

**Organization**: Tasks are grouped by user story so each story is an independently testable increment. Paths are relative to the repository root. Every Gradle invocation goes through the `gradle-run` wrapper (Principle VIII); Kotlin and Compose work is routed through `using-chrisbanes-skills` before coding (skill contracts named per task).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 Android round · US2 iOS round · US3 report and gate · US4 instrumentation facade · US5 audio-thread health · US6 sessions per feature

## Path Conventions

- Pipeline: `app/perf/` (Python, `uv`); config `app/tracepulse.yml`
- Android benchmarks: `app/perf-benchmarks/` (Gradle `:perf-benchmarks`, gated by `-Ptembra.perf=true`)
- iOS performance tests: `app/iosApp/TembraPerfTests/`; Xcode project `app/iosApp/iosApp.xcodeproj`
- Facade and telemetry: `app/shared/engine/src/{commonMain,androidMain,iosMain,commonTest}/kotlin/audio/tembra/engine/perf/`
- Engine: `app/engine/include/`, `app/engine/src/`, `app/engine/tests/`
- Anchors: `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/perf/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: adopt trace-pulse into the repository and give every later task a green baseline.

- [X] T001 Copy `/Volumes/External/Dev/trace-pulse/cli/tracepulse/` and `cli/tests/` into `app/perf/tracepulse/` and `app/perf/tests/`, delete `init`-related code (`scaffold`/`module/benchmarks-template` references) and the NIA-specific journey fields, and record the source commit of trace-pulse in `app/perf/README.md`
- [X] T002 Write `app/perf/pyproject.toml` (name `tracepulse`, `requires-python = ">=3.9,<3.14"`, dependencies `PyYAML>=6`, `scipy>=1.13.1,<2`, `perfetto==0.57.2`, `Jinja2>=3,<4`, `jsonschema>=4`, dev `pytest>=7`; script `tracepulse = "tracepulse.__main__:main"`) and `app/perf/.python-version` = `3.12`; run `cd app/perf && uv sync && uv run pytest` and make the copied suite green (fix import paths only)
- [X] T003 [P] Add `app/perf/data/`, `app/perf/reports/`, `app/perf/.history/`, `app/perf/.venv/` to `.gitignore` and create `app/perf/data/.gitkeep`
- [X] T004 [P] Add a unit test in `app/perf/tests/test_mannwhitney_pin.py` asserting the exact two-sided Mann-Whitney p-values trace-pulse relies on (40 v 40 fixture from `app/perf/tests/fixtures/`) so the relaxed scipy pin cannot drift silently
- [X] T005 [P] Create `app/tracepulse.yml` from `contracts/cli.md` (detection, confirm, collection, history, gate, devices with `19021FDEE001AH` → Pixel 6 Pro alias `pixel6` with `battery_temp_max_c: 40.0`, `00008110-001049660E3A401E` → iPhone 14 alias `iphone14`, android, ios, journeys) and a `config.py` loader test in `app/perf/tests/test_config.py` that rejects unknown keys with exit status 4

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the pieces every story needs: the catalogue, the device registry, the round record and its history branch, the derived store, the measurement-build switch, and the app anchors.

**⚠️ CRITICAL**: no user story work begins until this phase is complete.

- [X] T006 Write failing tests in `app/perf/tests/test_catalogue.py` for `tracepulse/catalogue.py`: every key in `contracts/metric-catalogue.md` exists per platform with unit, source, `higher_is_worse`, comparable flag, default gate limit and screening gate; `tracepulse.yml` may override limits and disable keys but adding a key raises
- [X] T007 Implement `app/perf/tracepulse/catalogue.py` (dataclass `Metric`, `CATALOGUE: dict[(platform, key)]`, `apply_overrides(config)`) to make T006 pass
- [X] T008 [P] Write failing tests in `app/perf/tests/test_records.py`: a round record validates against `specs/007-performance-pipeline/contracts/round-record.schema.json` (copy the schema to `app/perf/tracepulse/schema/round-record.schema.json`); `round_id` pattern `^[0-9]{8}T[0-9]{6}Z_[A-Za-z0-9._-]+$`; `status` ∈ {`succeeded`, `failed`} (refused rounds are never written); `journeys[].journey` ∈ {startup, tuner, metronome, stress}; `label` non-empty; `machine` is metadata only
- [X] T009 Implement `app/perf/tracepulse/records.py` (`RoundRecord` dataclass, `to_json`, `from_json`, `validate`, `record_path(device_id, round_id)` = `records/<device_id>/<round_id>.json`) to make T008 pass
- [X] T010 [P] Write failing tests in `app/perf/tests/test_history.py` using a temporary bare git repository: `history.publish(record)` writes the file in a worktree of branch `perf-history` (created as an orphan when missing), commits with message `perf: <device_id> <round_id>` and pushes; `history.sync()` pulls and returns all records; two publishes for different devices from two worktrees do not conflict; a failed push leaves the record under `data/unpublished/` and `publish` retries it (exit status 5 semantics)
- [X] T011 Implement `app/perf/tracepulse/history.py` (worktree at `app/perf/.history`, `git worktree add`, orphan branch creation, publish/sync/pending) to make T010 pass
- [X] T012 [P] Write failing tests in `app/perf/tests/test_devices.py`: `devices.attached()` parses fixtures of `adb devices -l`, `xcrun devicectl list devices --json-output`, `xcrun simctl list devices booted -j` into `Device(device_id, platform, kind, model)`; unknown attached devices are added to the registry with `reference = kind == "device"`; aliases resolve; two attached devices and no `--device` raise a usage error (exit 4)
- [X] T013 Implement `app/perf/tracepulse/devices.py` (registry read/write in `tracepulse.yml`, discovery via the three tools, alias resolution) to make T012 pass
- [X] T014 [P] Write failing tests in `app/perf/tests/test_store_rebuild.py`: the SQLite store gains `device_id`, `label`, `kind`, `reference`, `machine` on `rounds` and a `gate_decisions` table; `store.rebuild(records)` recreates `data/tracepulse.db` from a list of records deterministically; series are keyed `(device_id, metric)` and never mix devices or platforms
- [X] T015 Extend `app/perf/tracepulse/store.py` (schema migration, `rebuild`, device-keyed series queries) to make T014 pass; keep `detect.py` untouched apart from reading series by `device_id`
- [X] T016 Write failing test `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine/perf/PerfBuildTest.kt` asserting `PerfBuild.PERF_ENABLED` is `false` in the default build and `true` when the Gradle property `tembra.perf` is set (the test reads a system property the Gradle task sets for the JVM test run)
- [X] T017 Add to `app/shared/engine/build.gradle.kts` a task `generatePerfBuild` that writes `build/generated/perf/commonMain/kotlin/audio/tembra/engine/perf/PerfBuild.kt` with `const val PERF_ENABLED = <tembra.perf present>` and `PerfNames.kt` (an `internal fun PerfSection.signpostName(): String` and `PerfCounter.signpostName()` generated from `app/shared/engine/perf-catalogue.txt`, lines `<ENUM_NAME> <family> <signpost.name>`; real strings only when `tembra.perf` is present, `""` otherwise, so no catalogue string exists in non-measurement binaries), and registers the directory as a `commonMain` source; make T016 pass
- [X] T018 Add build type `perf` to `app/androidApp/build.gradle.kts` (`initWith(getByName("release"))`, `isProfileable = true`, `signingConfig = signingConfigs.getByName("debug")`, `matchingFallbacks += listOf("release")`) and the CMake option `option(TEMBRA_TRACING "Compile the tracing facade" OFF)` in `app/engine/CMakeLists.txt` wired to `-DTEMBRA_TRACING=ON` from `:engine:android` when the build type is `perf` and from the iOS framework build when `tembra.perf` is set
- [X] T019 Gate the benchmark module in `app/settings.gradle.kts`: `if (providers.gradleProperty("tembra.perf").isPresent) include(":perf-benchmarks")`; verify `./gradlew projects` (through `gradle-run`) does not list it by default
- [X] T020 [P] Write failing Compose UI tests in `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/perf/PerfAnchorsTest.kt` (skill `compose-ui-testing-patterns`): the Home, Tuner and Metronome content composables expose the anchors listed in `PerfAnchors` (`home.root`, `nav.tuner`, `nav.metronome`, `tuner.root`, `tuner.modeAutoListen`, `tuner.modeByEar`, `tuner.noteReadout`, `metronome.root`, `metronome.startStop`, `metronome.tempoUp`, `metronome.tempoDown`, `metronome.tempoReadout`, `devtools.writeHealth`) as `testTag` semantics
- [X] T021 Implement `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/perf/PerfAnchors.kt` (an `object` of `const val` tags) and apply the tags in the existing screens' content composables (tuner, metronome, home, navigation items), set `Modifier.semantics { testTagsAsResourceId = true }` on the app root in `app/shared/ui/src/androidMain/`, and make T020 pass
- [X] T022 [P] Write failing Compose UI tests in `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/perf/ReportDrawnTest.kt` asserting the Home, Tuner and Metronome wiring composables call `ReportDrawnWhen` with a predicate that becomes true once their first content state is non-loading (skill `compose-state-and-effects`: keyed by the semantic first-content state)
- [X] T023 Add `ReportDrawnWhen { uiState.isFirstContentReady }` to the Home, Tuner and Metronome wiring composables in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/{home,tuner,metronome}/` and make T022 pass
- [X] T024 Wire exit statuses and the command skeleton in `app/perf/tracepulse/__main__.py` per `contracts/cli.md` (`devices`, `doctor`, `run`, `publish`, `sync`, `analyze`, `report`, `status`, `inject`, `revert`; codes 0/2/3/4/5; `--json`) with tests in `app/perf/tests/test_cli.py` that each subcommand parses and that `run` without `--label` exits 4

**Checkpoint**: `uv run pytest` green; `./gradlew checkAndroidHost checkIos --warning-mode all` green with zero warnings; `:perf-benchmarks` absent from the default build.

---

## Phase 3: User Story 1 - Run an Android performance round with one command (Priority: P1) 🎯 MVP

**Goal**: `uv run tracepulse run --device pixel6 --label <feature>` produces a complete Android round (checks, measurement build, fresh install, warm-up, four journeys × 20 iterations, per-iteration values and traces, record published, detection, report) with distinct exit statuses.

**Independent Test**: quickstart §2 on the Pixel 6 Pro: a round appears in the history with values for every journey, a report file exists, a second run appends a second round; a hot or low-battery phone is refused with exit 2 before building; two attached devices and no `--device` exit 4.

### Tests for User Story 1 ⚠️ write first, must fail

- [X] T025 [P] [US1] Write failing tests in `app/perf/tests/test_collect_android.py` with a `benchmarkData.json` fixture produced by Macrobenchmark 1.5.0 (copy `app/perf/tests/fixtures/benchmarkData-1.5.json` from trace-pulse's fixtures and rename metrics): `StartupTimingMetric` → `startup.ttid_ms`/`startup.ttfd_ms`, `FrameTimingMetric` → `frames.p50_ms`/`p90`/`p99`/`overrun_p90_ms`, `MemoryUsageMetric` → `memory.peak_kb`/`memory.end_kb`, `TraceSectionMetric` → `section.<name>.sum_ms`/`.count`, journey name from the benchmark class; 20 samples per metric; `coefficientOfVariation` → `noisy` badge above `cov_max`
- [X] T026 [P] [US1] Write failing tests in `app/perf/tests/test_orchestrate_android.py` with a `FakeRunner` scripting `adb`/`gradlew`/`am instrument` calls: order is doctor → `./gradlew --console=plain -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf` (log captured to `<round>/gradle.log`, never printed in full) → uninstall + install → one warm-up `am start` + force-stop → one `am instrument -w` per journey class with `additionalTestOutputDir` → pull → APK size → record → publish; a doctor failure exits 2 before any build; a journey failure records `status: failed` with `failure_reason` and exits 3; `--no-publish` skips the push; `--no-build --apk <app.apk> --test-apk <benchmarks.apk>` skips the Gradle step and installs the given artifacts (the path an agent uses after building through the `gradle-run` wrapper, and the path a future CI job uses); `--build-type release` builds and installs the release variant for the SC-007 comparison
- [X] T027 [P] [US1] Write failing tests in `app/perf/tests/test_run_pipeline.py` for the `run` orchestration shared by both platforms: builds `RoundRecord` with `build.commit`/`dirty` from `git`, `machine.hostname`, `checks[]`, `badges`, and calls `store.rebuild` → `detect` → `report` after `publish` and `sync`; a per-device lock file `data/locks/<device_id>` is held for the round or session and a second invocation on the same device exits 4 naming the holder's pid and start time (spec edge case: two rounds on one device)

### Implementation for User Story 1

- [X] T028 [US1] Create `app/perf-benchmarks/build.gradle.kts` (`com.android.test`, `targetProjectPath = ":androidApp"`, `experimentalProperties["android.experimental.self-instrumenting"] = true`, `testBuildType = "perf"`, dependencies `androidx.benchmark:benchmark-macro-junit4:1.5.0`, `androidx.test.uiautomator:uiautomator`, `androidx.test.ext:junit`, versions added to `app/gradle/libs.versions.toml`) and `src/main/AndroidManifest.xml`
- [X] T029 [P] [US1] Implement UiAutomator journeys over `PerfAnchors` in `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/Anchors.kt`, `TunerJourney.kt` (open Tuner, wait for `tuner.noteReadout`, switch to By Ear, back), `MetronomeJourney.kt` (open Metronome, start, tempo up ×5, tempo down ×5, stop), `StressJourney.kt` (tuner then metronome running together; the final `devtools.writeHealth` tap is added in T072), each a class with one `run(device: UiDevice)` function (Principle VII: one type per file)
- [X] T030 [US1] Implement `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/StartupBenchmark.kt` (COLD, `StartupTimingMetric`, 20 iterations), `TunerJourneyBenchmark.kt`, `MetronomeJourneyBenchmark.kt`, `StressJourneyBenchmark.kt` (`FrameTimingMetric`, `MemoryUsageMetric(Max, RssAnon)`, `MemoryUsageMetric(Last, HeapSize)`, `TraceSectionMetric` for the journey's catalogue sections from `PerfMetrics.kt`, `CompilationMode.Full()`, `iterations = 20`, package `audio.tembra.app`)
- [X] T031 [US1] Implement `app/perf/tracepulse/collect_android.py` (de-templated from `collect.py`: package and benchmark class names from `tracepulse.yml`, catalogue key mapping, badges) to make T025 pass
- [X] T032 [US1] Implement `app/perf/tracepulse/orchestrate_android.py` (de-templated from `orchestrate.py`; `doctor` with the per-device overrides; `./gradlew --console=plain` invoked directly by the tool with the log captured to the round directory — the `gradle-run` wrapper binds agent-run Gradle, not this user tooling; `--no-build`/`--apk`/`--test-apk` and `--build-type`; `am instrument` per class; `adb pull`; APK size from `app/androidApp/build/outputs/apk/<buildType>/`) to make T026 pass
- [X] T033 [US1] Implement the shared `run` pipeline in `app/perf/tracepulse/run.py` (platform dispatch, record assembly, per-device lock file, publish → sync → rebuild → detect → report, exit statuses) and wire `__main__.py` to make T027 pass
- [X] T034 [US1] Adapt `app/perf/tracepulse/traces_android.py` (from `traces.py`): per-iteration Perfetto traces → main-thread top slices for the drill-down and the counter tracks table read (used by US5), with a fixture test in `app/perf/tests/test_traces_android.py`
- [ ] T035 [US1] Run quickstart §2 on the Pixel 6 Pro twice (`--label 007-performance-pipeline`), confirm two records on `perf-history`, `report.html` renders the Pixel series, exit statuses 0/2/4 behave as specified; record the round duration in `app/perf/README.md`

**Checkpoint**: User Story 1 works end to end on a Pixel from one command; the SQLite cache is rebuilt from the branch.

---

## Phase 4: User Story 2 - Run an iOS performance round with one command (Priority: P2)

**Goal**: `uv run tracepulse run --device iphone14 --label <feature>` produces an iOS round from Apple's stock tooling into the same history; `--simulators` runs every booted simulator as non-reference series.

**Independent Test**: quickstart §3 on the iPhone 14: a round appears in the iPhone series with 20 values per metric, the report shows the iPhone next to the Pixel, a second run appends; a thermal state other than nominal or battery below the minimum refuses with exit 2; a network path is recorded, never refused; simulator rounds carry the `simulator` badge.

### Tests for User Story 2 ⚠️ write first, must fail

- [X] T036 [P] [US2] Write failing tests in `app/perf/tests/test_collect_ios.py` with the spike-1 fixture `app/perf/tests/fixtures/xcresult-metrics.json` (the `xcresulttool get test-results metrics` list from research.md) plus a device-shaped variant: metric identifiers map to catalogue keys (`XCTMetric_Clock.time.monotonic` → `journey.duration_ms` ×1000, `XCTMetric_CPU.time` → `cpu.time_ms`, `XCTMetric_CPU.instructions_retired` → `cpu.instructions_k`, `XCTMetric_Memory.physical_peak` → `memory.peak_kb`, `XCTMetric_Memory.physical` → `memory.end_kb`, application-launch metrics → `startup.ttid_ms`/`startup.ttfd_ms`, signpost metrics by name → `section.<name>.sum_ms`, hitch columns → `frames.hitch_ratio_ms_per_s`/`frames.hitch_count`/`frames.fps_deficit`); `measurements.count == 20`; the `preflight.json` attachment (fixture) yields `fingerprint.thermal_state`, `battery_level`, `network_state`; a test failure message starting `PERF-REFUSED:` maps to exit 2 and no record
- [X] T037 [P] [US2] Write failing tests in `app/perf/tests/test_orchestrate_ios.py` with a `FakeRunner`: discovery via `devicectl list devices --json-output` (fixture) and `simctl list devices booted -j`; one `xcodebuild build-for-testing -project app/iosApp/iosApp.xcodeproj -scheme TembraPerf -configuration Perf -destination platform=iOS,id=<udid> -derivedDataPath <round>/DerivedData -allowProvisioningUpdates` per session; per round `devicectl device uninstall app`, install, `devicectl device process launch --terminate-existing` warm-up, `xcodebuild test-without-building -xctestrun <path> -destination … -resultBundlePath <round>/perf.xcresult -only-testing:TembraPerfTests`, `xcresulttool get test-results metrics --compact`, `xcresulttool export attachments`; simulators use `platform=iOS Simulator,id=…` and `simctl` for install/launch; the bundle id gains the team suffix read from `app/iosApp/Configuration/Config.xcconfig`
- [X] T038 [P] [US2] Write failing tests in `app/perf/tests/test_size_ios.py`: `size_ios.measure(archive_dir)` runs `xcodebuild -exportArchive -exportOptionsPlist <generated plist with method development, thinning "<thin-for-all-variants>">` once per build and parses `App Thinning Size Report.txt` (fixture) into `app.size_bytes` (compressed, variant `iPhone14,7`) and `app.size_uncompressed_bytes`

### Implementation for User Story 2

- [X] T039 [US2] Add to `app/iosApp/iosApp.xcodeproj` a `Perf` build configuration duplicated from Release (`DEBUG_INFORMATION_FORMAT = dwarf-with-dsym`), a shared scheme `TembraPerf` (Run: Perf, Debug executable off; Test: test plan `TembraPerf.xctestplan` with `TembraPerfTests`, code coverage and sanitizers off), and change the framework run-script to `./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode $( [ "$CONFIGURATION" = Perf ] && echo -Ptembra.perf=true )` (keep `Config.xcconfig` skip-worktree)
- [X] T040 [US2] Add the `TembraPerfTests` UI-testing bundle target (host application `iosApp`) with `app/iosApp/TembraPerfTests/Metrics.swift` (per-journey `[XCTMetric]` lists; `iterationCount = 20` in `XCTMeasureOptions`), `PerfJourneyTestCase.swift` (base class: launches the app with `-perf` argument, `measure(metrics:options:block:)` wrapper) and `Journeys/TunerJourney.swift`, `MetronomeJourney.swift`, `StressJourney.swift` driving the app through `app.descendants(matching: .any)[<PerfAnchors tag>]` accessibility identifiers (mirror of T029)
- [X] T041 [P] [US2] Implement `app/iosApp/TembraPerfTests/Preflight.swift`: in `setUp`, read `ProcessInfo.processInfo.thermalState` (require `.nominal`), `UIDevice.current.batteryLevel` with monitoring enabled (require ≥ 0.5 on devices; skip the check on simulators), `NWPathMonitor` current path → `network_state`; attach `preflight.json` (`XCTAttachment`, `lifetime = .keepAlways`); on a failed check call `XCTFail("PERF-REFUSED: <check>=<value> limit=<limit>")` and `continueAfterFailure = false`
- [X] T042 [US2] Implement `StartupPerfTests.swift` (`XCTApplicationLaunchMetric(waitUntilResponsive: false)` and `(true)` as two tests, 20 iterations, cold launch via `app.launch()` after `app.terminate()`), `TunerPerfTests.swift`, `MetronomePerfTests.swift`, `StressPerfTests.swift` (each one `measure` over its journey with `XCTClockMetric`, `XCTCPUMetric(application:)`, `XCTMemoryMetric(application:)`, and the journey's `XCTOSSignpostMetric` sections from `Metrics.swift`), all in `app/iosApp/TembraPerfTests/`
- [X] T043 [US2] Implement `app/perf/tracepulse/collect_ios.py` (JSON parsing, catalogue mapping, attachments, `PERF-REFUSED` detection, simulator flag) to make T036 pass
- [X] T044 [US2] Implement `app/perf/tracepulse/orchestrate_ios.py` (discovery, build-for-testing cache per session, uninstall/install, warm-up, test-without-building, result-bundle path, simulator variant, `--simulators` loop over booted simulators, `--configuration Release` for the SC-007 comparison, `--no-build --xctestrun <path>` to reuse a build) to make T037 pass
- [X] T045 [US2] Implement `app/perf/tracepulse/size_ios.py` (archive + export with the generated options plist, cache keyed by build commit) to make T038 pass, exposed as a separate `tracepulse size --device <id>` command and as `run --size` (opt-in, off by default so the archive never pushes a round past the SC-002 budget; the session summary says when size was skipped), and wire the iOS platform into `run.py`
- [ ] T046 [US2] Run quickstart §3 on the iPhone 14 (closes spike 1 on the device: verify 20 `measurements` per metric in the real bundle; closes spike 5: confirm `PerfAnchors` tags resolve as accessibility identifiers on Compose Multiplatform 1.12.1, adjusting `accessibilitySyncOptions` in the iOS entry point if needed), then `--simulators` over the booted simulators; record findings in `app/perf/README.md`

**Checkpoint**: Android and iOS rounds share one history; the report shows both devices and the simulators as non-reference.

---

## Phase 5: User Story 3 - Read one report for both platforms, with a coarse gate (Priority: P3)

**Goal**: one self-contained `report.html` with a device grid, per-device pages, per-metric pages, label boundaries, plain-language headlines; an informational gate per metric.

**Independent Test**: quickstart §4: grid → device → metric navigation works offline with zero external requests; setting a 1 % limit on `journey.duration_ms` and running a round flags it in the output and the report while the command exits 0; an improvement is never flagged; fewer than N clean rounds shows `inactive`.

### Tests for User Story 3 ⚠️ write first, must fail

- [X] T047 [P] [US3] Write failing tests in `app/perf/tests/test_gate.py`: with the catalogue defaults and a fixture series, `gate.decide(series, config)` returns `inactive` (reason `fewer than 5 clean rounds`) below N; `within_limit` when the median change ≤ limit; `above_limit` with `limit_pct`, `observed_pct`, `baseline_rounds` (ids of the last N clean rounds) when exceeded in the worse direction; never `above_limit` for improvements; `inactive` (reason `simulator series`) for `kind == simulator` unless `gate.simulators: active`; clean = `status succeeded` and no `noisy`/`thermal` badge and not environment-suspect; a gate flag does not make a round unclean
- [X] T048 [P] [US3] Write failing tests in `app/perf/tests/test_text.py` for `tracepulse/text.py`: device headline for the states "nothing changed in the last 5 rounds", "startup got 18 % slower in <label> (suspect)", "2 metrics above their limit in the latest round: …", "preliminary only: 4 of 11 rounds needed"; per-chart sentence per status (confirmed/suspect/first hint/improved/steady) naming the label at onset
- [X] T049 [P] [US3] Write the failing golden test `app/perf/tests/test_report_grid.py`: render the fixture history in `app/perf/tests/fixtures/history-3-devices/` (Pixel 6 Pro, iPhone 14, iPhone 17 simulator; two labels; one confirmed onset; two gate flags; one OS-version change) and compare to `app/perf/tests/fixtures/report-golden.html`; assert the HTML contains no `http://`/`https://` URLs except inside `<a>` to raw-artifact paths, contains `#/device/<id>` links for every device, and marks the simulator card `non-reference`

### Implementation for User Story 3

- [X] T050 [US3] Implement `app/perf/tracepulse/gate.py` (decisions per the data model, written into `gate_decisions` and into the round record's `gate[]` before publish) to make T047 pass, and print the gate summary block in `run.py` output without changing the exit status
- [X] T051 [P] [US3] Implement `app/perf/tracepulse/text.py` (extend trace-pulse's `PLAIN_*` dictionaries and `_metric_headline` with labels and gate states) to make T048 pass
- [X] T052 [US3] Extend `app/perf/tracepulse/detect.py` output with `label_at_onset` (the onset round's label) and expose detections, gate decisions and fingerprint changes per device for the report, with a test in `app/perf/tests/test_detect_labels.py`
- [X] T053 [US3] Implement the report under the `dataviz` skill: `app/perf/tracepulse/templates/report.html.j2`, `static/report.css` (tokens from `design/stitch/DESIGN.md`, light and dark), `static/report.js` (hash router `#/`, `#/device/<id>`, `#/device/<id>/<metric>`; grid cards; charts as inline SVG with median line, IQR band, label boundaries, onset markers by status, gate marks and limit line; rounds table; flagged and changes lists) and `report.py` embedding the data as JSON; make T049 pass
- [X] T054 [US3] Add `tracepulse report --open` (opens the file with the OS default browser) and the per-metric catalogue notes (unit, source, mismatch text) on the metric page; re-run T049
- [ ] T055 [US3] Run quickstart §4 against the real history (Pixel + iPhone + simulators): navigation, offline check in the browser, the 1 % gate experiment, restore the limit; record the report size in `app/perf/README.md`

**Checkpoint**: the report is the single place to read both platforms; gates flag without failing.

---

## Phase 6: User Story 4 - Instrument once, measure the same named section on both platforms (Priority: P4)

**Goal**: `PerfTrace`/`PerfSection` in Kotlin and `tembra_trace.h` in C++ map to Android system tracing and iOS `os_signpost`; a section wrapped once appears as `section.<name>.*` on both platforms; nothing of it in release builds; nothing of it on the audio callback.

**Independent Test**: quickstart §5: wrap the By Ear switch in `PerfSection.TUNER_MODE_SWITCH`, run one round per platform → `section.tuner.modeSwitch.sum_ms` on both device pages; `strings` on the release APK libraries/dex and the release iOS binary contain no catalogue name; the poison test fails the build if a real-time source includes the facade.

### Tests for User Story 4 ⚠️ write first, must fail

- [X] T056 [P] [US4] Write failing tests in `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine/perf/PerfSectionCatalogueTest.kt`: every `PerfSection` entry has exactly one line in `app/shared/engine/perf-catalogue.txt` (read as a test resource) whose signpost name is unique and of the form `<family>.<camelCase>` with the family matching the entry's `PerfFamily`; the exhaustive `when` over `PerfFamily` (skill `kotlin-control-flow`) covers TUNER, METRONOME, TIMELINE, ENGINE; the generated C name list `app/engine/include/tembra_trace_names.h` equals the catalogue in order; with `PERF_ENABLED == false`, `signpostName()` is `""` for every entry
- [X] T057 [P] [US4] Write failing tests in `PerfTraceTest.kt` (same package) against a `PerfTraceSink` test double installed through an internal `PerfTrace.sinkForTest`: `section` returns the block value and ends the interval when the block throws; `animation` begins/ends an animation interval; `counter` records `(name, value)`; with `PerfBuild.PERF_ENABLED == false` every call is a no-op and allocates nothing (assert the sink is never called)
- [X] T058 [P] [US4] Write the failing Catch2 tests `app/engine/tests/trace_poison_test.cpp` (compiles a unit that includes every header under `app/engine/src/core/rt/` with `TEMBRA_TRACING=1 TEMBRA_TRACE_POISON=1` and asserts it builds, plus a negative compile check that a translation unit calling `TEMBRA_TRACE_SCOPE` under `TEMBRA_TRACE_POISON` fails to compile, via CMake `try_compile`) and `trace_shim_test.cpp` (the Apple shim begins/ends intervals without crashing on macOS; the Android shim is a link-only check)
- [X] T059 [P] [US4] Write the failing XCUITest `app/iosApp/TembraPerfTests/FacadeSelfCheckTests.swift`: `measure(metrics: [XCTOSSignpostMetric(subsystem: "audio.tembra", category: "ui", name: "tuner.modeSwitch")])` over the By Ear switch yields `measurements.count == 20`; and an animation-interval test over the metronome tempo change asserting hitch columns are present (spike 7; if absent, switch the frames family to `XCTHitchMetric(application:)` and record the outcome in research.md)

### Implementation for User Story 4

- [X] T060 [P] [US4] Implement `PerfFamily.kt`, `PerfSection.kt` (entries carry only their `PerfFamily`, no string literals), `PerfCounter.kt` in `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/perf/` per `contracts/tracing-facade.md`, write `app/shared/engine/perf-catalogue.txt` (the single source of signpost names), and a Gradle task `generateTraceNames` in `app/shared/engine/build.gradle.kts` writing `app/engine/include/tembra_trace_names.h` from the catalogue (checked in; the task fails the build when the file is stale); make T056 pass
- [X] T061 [US4] Implement `PerfTrace.kt` (`expect object` with `inline` `section`/`animation` and `counter`, all guarded by `PerfBuild.PERF_ENABLED`, skill `kotlin-api-design`: platform detail behind a common binding), `PerfTrace.android.kt` (`android.os.Trace.beginSection/endSection`, `setCounter` on API ≥ 29), `PerfTrace.ios.kt` (calls the cinterop functions from T062), the `PerfTraceSink` seam; make T057 pass
- [X] T062 [US4] Implement `app/engine/include/tembra_trace.h` (C API, `TEMBRA_TRACE_SCOPE`/`TEMBRA_TRACE_COUNTER` macros, `TEMBRA_TRACE_POISON` mode with `__attribute__((unavailable))`), `app/engine/src/platform/trace_apple.c` (`os_log_create("audio.tembra", category)`, `os_signpost_interval_begin/end`, `os_signpost_animation_interval_begin`, `os_signpost_event_emit`), `app/engine/src/platform/trace_android.cpp` (`ATrace_*`, `ATrace_setCounter` behind `__builtin_available(android 29, *)`), CMake sources under `TEMBRA_TRACING`, and add `tembra_trace.h` to `app/shared/engine/src/nativeInterop/cinterop/tembra_engine.def`; make T058 pass
- [X] T063 [US4] Instrument the catalogue points — sections wrap non-composable work only (ViewModel intents, draw-lambda computations, loaders), never composable lambdas or composition itself: `PerfSection.TUNER_LOAD` around the tuner ViewModel's path from screen entry to its first-content state, `TUNER_MODE_SWITCH` around the mode-switch intent, `TUNER_FRAME` around the per-frame pitch-to-needle computation inside the tuner's draw lambda (in `app/shared/ui/.../tuner/`); `METRONOME_LOAD` and `METRONOME_TEMPO_CHANGE` (animation variant) around the metronome ViewModel's load and tempo-change intents (in `.../metronome/`); `TEMBRA_TRACE_SCOPE("engine.loadClip")` placeholders in the engine's non-RT loader path; startup is measured by the platforms' launch metrics, not by a section; call `enableTraceOSLog()` in the iOS entry (`app/shared/ui/src/iosMain/.../MainViewController.kt`) when `PerfBuild.PERF_ENABLED`
- [X] T064 [US4] Add `TraceSectionMetric(section, Mode.Sum)` per journey in `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/PerfMetrics.kt` and the matching `XCTOSSignpostMetric` list in `app/iosApp/TembraPerfTests/Metrics.swift`; make T059 pass on the iPhone 14
- [X] T065 [P] [US4] Add `app/perf/tools/check-release-strings.sh` (builds `:androidApp:assembleRelease` with `./gradlew --console=plain` and `xcodebuild -configuration Release`, runs `strings` on the APK's native libraries and `apkanalyzer dex packages`/`strings` on the dex and on the iOS app binary, and fails if any signpost name from `app/shared/engine/perf-catalogue.txt` appears; enum identifiers such as `TUNER_FRAME` are allowed to remain because the release build does not shrink) and a pytest wrapper `app/perf/tests/test_release_strings.py` marked `@pytest.mark.device` (skipped unless `TRACEPULSE_FULL=1`)
- [ ] T066 [US4] Run quickstart §5 on both devices; confirm the facade adds no new compiler warnings (`--warning-mode all`) and the measurement-build startup median is within 5 % of a release-build round produced with `run --build-type release` (Android) and `run --configuration Release` (iOS) under a `release-baseline` label (SC-007); record in `app/perf/README.md`

**Checkpoint**: one catalogue, two platforms, release binaries clean, real-time path provably free of the facade.

---

## Phase 7: User Story 5 - Audio-thread health is measurable from the first day of the audio spine (Priority: P5)

**Goal**: `tembra_audio_health` in the C ABI, `AudioHealth` in Kotlin telemetry, counters published through the facade, `audio.*` series on both platforms from the stress journey (zeros today).

**Independent Test**: quickstart §6: `FakeAudioEngine.scriptAudioHealth` reaches the sink as six counters; Python fixtures (`engine-health.json`, a counter-track Perfetto trace, the spike-3 `audio-ioproc.xml`) produce the `audio.*` series with the scripted values; a device round yields `audio.*` rows.

### Tests for User Story 5 ⚠️ write first, must fail

- [X] T067 [P] [US5] Write failing Catch2 tests `app/engine/tests/audio_health_test.cpp`: `tembra_engine_audio_health` returns -1 with no stream and 0 with one; a scripted callback of 1.5 ms on a 2.67 ms period updates `callbacks`, `last_us`, `max_us` and leaves `over_budget` at 0; a 3 ms callback increments `over_budget`; the struct layout is `uint64 + 4×uint32 + uint8 + 3 reserved` (static_assert on `sizeof`)
- [X] T068 [P] [US5] Write failing tests in `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine/perf/AudioHealthTelemetryTest.kt`: `FakeAudioEngine.scriptAudioHealth(AudioHealth(…))` makes `audioHealth()` return it; `AudioHealthPublisher.publish(engine, PerfTrace)` emits exactly six counters (`audio.callbacks`, `audio.lastUs`, `audio.maxUs`, `audio.overBudget`, `audio.xruns`, `audio.realtime`) with the scripted values into the sink; the publisher is a plain function with no coroutine of its own (skill `kotlin-concurrency-and-flow`: the launch site is the app's telemetry loop)
- [X] T069 [P] [US5] Write failing tests in `app/perf/tests/test_audio_series.py`: `traces_android.counter_series(trace)` on `fixtures/perfetto-audio-counters.perfetto-trace` yields the six `audio.*` values (last value per track); `collect_ios.engine_health(path)` on `fixtures/engine-health.json` yields the same keys; `traces_ios.ioproc_p99(xml)` on `fixtures/audio-ioproc.xml` (spike-3 shape: `start, duration, process, thread, name`) returns `audio.ioproc_p99_us`

### Implementation for User Story 5

- [X] T070 [US5] Add `tembra_audio_health` and `tembra_engine_audio_health()` to `app/engine/include/tembra_engine.h` (revision bump), the atomics and clock reads in `app/engine/src/core/rt/` (relaxed stores at callback entry/exit; `over_budget` when `last_us > period_us`), `xruns` written from the non-RT drain, `realtime` set at stream start; make T067 pass
- [X] T071 [US5] Implement `AudioHealth.kt` (data class), `AudioEngine.audioHealth(): AudioHealth?` in the `expect` facade with JNI/cinterop actuals, `FakeAudioEngine.scriptAudioHealth`, and `AudioHealthPublisher.kt` in `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/perf/`; make T068 pass
- [X] T072 [US5] Call `AudioHealthPublisher.publish` once per second in measurement builds: no app-level telemetry loop exists yet, so add `AudioHealthTicker.kt` in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/perf/`, a composable effect `LaunchedEffect(PerfBuild.PERF_ENABLED)` in the root wiring composable (`app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/App.kt`) that ticks every second while enabled — the named launch site per `kotlin-concurrency-and-flow`; when the audio spine adds its telemetry loop, the ticker is folded into it; add the `devtools.writeHealth` action to the existing debug devtools screen in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/devtools/` (visible only when `PERF_ENABLED`) that writes `Documents/engine-health.json` (`{"schema":1,"journey":"stress","health":{…}}`) through an `expect fun writeEngineHealth(json: String)` with `iosMain` (NSFileManager Documents) and `androidMain` (app files dir) actuals; append the final `devtools.writeHealth` tap to `StressJourney.kt` (T029) and `StressJourney.swift` (T040); capture the devtools entry at compact and expanded widths on the Android emulator and the iOS simulator (constitution DoD)
- [X] T073 [US5] Implement `traces_android.counter_series`, `collect_ios.engine_health` (pull via `xcrun devicectl device copy from --device <udid> --source Documents/engine-health.json --domain-type appDataContainer --domain-identifier <bundle> --destination <round>/`; simulators via `simctl get_app_container`), and `app/perf/tracepulse/traces_ios.py` (`xctrace record --template 'Audio System Trace' --device <udid> --attach <pid> --time-limit <journey>` started by `orchestrate_ios` for the stress journey, `xctrace export --xpath '/trace-toc/run[@number="1"]/data/table[@schema="audio-hal-client-io-cycle-ioproc"]'`, p99 of `duration`); make T069 pass and map all keys through the catalogue
- [ ] T074 [US5] Run quickstart §6 on both devices: `audio.*` rows present (zeros), `audio.trace` saved under the iPhone round, `engine-health.json` pulled; record in `app/perf/README.md`

**Checkpoint**: the audio spine will inherit a measured seam, not build one.

---

## Phase 8: User Story 6 - Measure every feature as it lands (Priority: P6)

**Goal**: `--rounds N` sessions under a feature label, stopping at the first refusal or failure, publishing each completed round; label boundaries and onset attribution in the report; runnable over SSH.

**Independent Test**: quickstart §7: a five-round session over SSH on the second Mac publishes five records; `sync` + `report` on the Apple Silicon Mac shows them under the label on the Pixel series next to the iPhone series; a session with a gate flag still exits 0 and lists the flagged rounds.

### Tests for User Story 6 ⚠️ write first, must fail

- [X] T075 [P] [US6] Write failing tests in `app/perf/tests/test_sessions.py`: `run --rounds 5` builds once and repeats install → warm-up → journeys → collect → record → publish five times with `session.index`/`session.size`; stops at the first refusal (exit 2) or failure (exit 3) after publishing the completed rounds; `--label` is required and propagates into every record; the final output lists gate flags across the session
- [ ] T076 [P] [US6] Extend `app/perf/tests/test_report_grid.py` with assertions that label boundaries are drawn (one marker per label change per series) and that a detection whose onset round carries a label is described as "in <label>" on the device page and the changes list

### Implementation for User Story 6

- [X] T077 [US6] Implement sessions in `app/perf/tracepulse/run.py` (`--rounds`, single build per session, per-round publish, stop-at-failure, session summary) to make T075 pass
- [ ] T078 [US6] Render label boundaries and label-attributed headlines in `report.html.j2`/`report.js`/`text.py` to make T076 pass
- [ ] T079 [US6] Document the per-feature cadence and the SSH form in `app/perf/README.md` ("after merging a feature: `uv run tracepulse run --device <id> --label <feature> --rounds 5` per device; over SSH from the 2015 Mac for Android; iOS from the Apple Silicon Mac") and run quickstart §7 once (SSH half only if the second Mac is provisioned; otherwise run the five-round session locally and note it)

**Checkpoint**: every feature from here on gets a labelled session per device.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T080 [P] Port the regression-injection self-test: `app/perf/regressions/registry.yaml` and patches `r1-startup.patch` (300 ms blocking work in `MainActivity`/`iOSApp` init), `r2-frames.patch` (recomposition storm in the tuner readout gated on interaction), `r3-memory.patch` (40 MB deterministic allocation during the metronome journey), `r4-size.patch` (bundled 2 MB asset) against this repository; `inject`/`revert` tests in `app/perf/tests/test_inject.py`
- [ ] T081 [P] Write `app/perf/README.md` fully (install with `uv`, device setup incl. Developer Mode and Trust, every command, exit statuses, where data/reports/history live, how to add a journey and a section name, what each platform can and cannot check, simulator caveat, the per-feature cadence, and a "Every feature instruments itself" section stating FR-022: each later feature adds its catalogue sections, anchors, benchmark journey on both platforms and audio-health counters in the same feature, declares them in its spec, and is measured with a labelled session per device after it lands) and add the "performance round / benchmark / section" rows to the "where does X go" table in `app/README.md`
- [ ] T082 [P] Add `docs/README.md` and `CLAUDE.md` pointers: `app/perf/` commands under "Commands", the `perf-history` branch under "Things that bite" (never rebase it; records are append-only)
- [ ] T083 Verify SC-008: time `./gradlew checkAndroidHost`, `checkIos`, `engineHostTest` (through `gradle-run`, `--warning-mode all`) before and after the feature on this Mac; confirm within 5 % and zero warnings; record in `app/perf/README.md`
- [ ] T084 Run the full quickstart (§1–§9) end to end on the Pixel 6 Pro, the iPhone 14 and the booted simulators under the label `007-performance-pipeline`, including the iOS half of SC-004 (`inject R1`, six iPhone rounds, a confirmed `startup.*` onset at the injection round, `revert R1`); fix anything found; final `uv run pytest`, `checkAndroidHost`, `checkIos`, `engineHostTest` green
- [ ] T085 Remove anything copied from trace-pulse that this repository does not use (NIA patches, A52/Pixel chain scripts, `compare.py` if folded into `report.py`), keep `app/perf/` free of commented-out code and TODOs without issue references (Principle VII)
- [ ] T086 Verify SC-003: run a ten-round session of an unchanged build on the Pixel 6 Pro and on the iPhone 14 under the label `noise-baseline`, compute the coefficient of variation of the round medians per `startup.*` and `memory.*` series from the records (`uv run tracepulse status --json`), confirm ≤ 3 %, and record the measured values in `app/perf/README.md` (quickstart §9)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup; blocks every story (catalogue, records, history, store, measurement-build switch, anchors, CLI skeleton)
- **US1 (Phase 3)**: after Foundational; the MVP
- **US2 (Phase 4)**: after Foundational; shares `run.py` (T033) with US1 — do T033 first or in US1
- **US3 (Phase 5)**: after US1 or US2 has produced real rounds (needs series); gate and text are independent of platforms
- **US4 (Phase 6)**: after Foundational (T017 `PerfBuild`, T018 build type/CMake); its measurements need US1/US2 collectors to surface `section.*` keys
- **US5 (Phase 7)**: after US4 (counters go through `PerfTrace`) and US1/US2 (collectors)
- **US6 (Phase 8)**: after US1/US2 (rounds) and US3 (report labels)
- **Polish (Phase 9)**: after all desired stories

### Within Each Story

- Test tasks first; confirm they fail; then implementation; then the on-device quickstart task
- Python: fixtures → collector → orchestrator → `run.py` wiring
- Kotlin/C++: catalogue/enum → facade → bindings → instrumentation points
- The on-device verification task of each story is the story's checkpoint

### Parallel Opportunities

- Phase 1: T003, T004, T005 in parallel after T002
- Phase 2: T006/T008/T010/T012/T014 (tests) in parallel; T016–T019 (build switch) in parallel with T020–T023 (anchors); T024 after T006–T015; note T017's `PerfNames` generation depends on `perf-catalogue.txt`, created in T060 — until then the generator emits an empty name table
- US1: T025/T026/T027 in parallel; T028 → T029 ∥ T030; T031 ∥ T032 → T033 → T034 → T035
- US2: T036/T037/T038 in parallel; T039 → T040 → T041 ∥ T042; T043 ∥ T044 ∥ T045 → T046
- US3: T047/T048/T049 in parallel; T050 ∥ T051 → T052 → T053 → T054 → T055
- US4: T056/T057/T058/T059 in parallel; T060 → T061 ∥ T062 → T063 → T064 → T065 ∥ T066
- US5: T067/T068/T069 in parallel; T070 ∥ T071 → T072 → T073 → T074
- US6: T075 ∥ T076 → T077 ∥ T078 → T079
- Polish: T080/T081/T082 in parallel → T083 → T084 → T085 → T086

---

## Parallel Example: User Story 1

```bash
# Failing tests first, together:
Task: "T025 collect_android fixture tests in app/perf/tests/test_collect_android.py"
Task: "T026 orchestrate_android FakeRunner tests in app/perf/tests/test_orchestrate_android.py"
Task: "T027 run pipeline tests in app/perf/tests/test_run_pipeline.py"

# Then the benchmark module and the collectors in parallel:
Task: "T029 UiAutomator journeys in app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/"
Task: "T031 collect_android.py"   Task: "T032 orchestrate_android.py"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 (adopt trace-pulse, suite green) → Phase 2 (catalogue, records, history branch, store, measurement build, anchors)
2. Phase 3: Android round in one command on the Pixel 6 Pro
3. **STOP and VALIDATE** with quickstart §2; two records on `perf-history`; report renders

### Incremental Delivery

1. + US2: the iPhone 14 and the simulators join the same history
2. + US3: the grid report and the informational gate
3. + US4: the facade; `section.*` series on both platforms; release binaries clean
4. + US5: audio-health seam ready for the audio spine
5. + US6: labelled sessions; the cadence for every future feature
6. Polish: self-test injections, docs, lane timing, full quickstart

### Notes

- Every agent-initiated Gradle command (building the app, running lanes, or building the artifacts for a `run --no-build --apk … --test-apk …` round) goes through the `gradle-run` skill wrapper (`scripts/gradle_run.py create|run|finish`); the pipeline itself calls `./gradlew --console=plain` directly because it is user tooling; every Kotlin/Compose task names its skill contract (`kotlin-api-design`, `kotlin-control-flow`, `kotlin-concurrency-and-flow`, `compose-state-and-effects`, `compose-ui-testing-patterns`); report charts under `dataviz`
- No CI workflow, farm, Maestro or field telemetry in any task (spec Assumptions); the `perf-history` branch is append-only
- Commit after each task or logical group (the spec-kit hooks do this per phase)
