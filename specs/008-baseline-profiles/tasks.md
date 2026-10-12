---

description: "Task list for feature 008: baseline profiles"
---

# Tasks: Baseline Profiles

**Input**: Design documents from `/specs/008-baseline-profiles/`

**Prerequisites**: plan.md, spec.md, research.md (D1–D12), data-model.md, contracts/ (cli.md, comparison.schema.json, coverage-rule.md, build-config.md), quickstart.md

**Tests**: Included for every story. The constitution's Principle III (test-first) is non-negotiable: each test task is written first and must fail before its implementation task. Python tests drive the three commands through the `FakeRunner` of `app/perf/tests/conftest.py` on fixtures; the JVM coverage test reads files, no Android runtime; the generator and the comparison are device tests run only by their commands, never by a round. Kotlin tests never link the real engine (none is involved).

**Organization**: Tasks are grouped by user story so each story is an independently testable increment. Paths are relative to the repository root. Every agent-initiated Gradle command goes through the `gradle-run` wrapper (Principle VIII); Kotlin work is routed through `using-chrisbanes-skills` before coding (skill contract named per task). The round command and the labelled session are never modified (spec FR-010).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 generate the profile · US2 ship it (shrunk build, direct installs, verification) · US3 prove it (comparison) · US4 keep it current (coverage rule, documentation)

## Path Conventions

- App module: `app/androidApp/` (`build.gradle.kts`, `proguard-rules.pro`, `baseline-profile-coverage.txt`, `src/main/generated/baselineProfiles/`, `src/test/kotlin/audio/tembra/android/`)
- Benchmarks: `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/` (Gradle `:perf-benchmarks`, gated by `-Ptembra.perf=true`)
- Pipeline: `app/perf/tracepulse/`, tests `app/perf/tests/`, fixtures `app/perf/tests/fixtures/profile/`, outputs `app/perf/data/comparisons/` (git-ignored by the existing `app/perf/data/*` rule)
- Build logic: `app/build-logic/src/main/kotlin/`, root `app/build.gradle.kts`, catalogue `app/gradle/libs.versions.toml`
- Device: Pixel 6 Pro, registry alias `pixel6` (serial `19021FDEE001AH`), API 37; Pixel 7 Pro alias `pixel7` for the session after merge

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: record the numbers the success criteria are measured against, pin the versions, create the fixture home.

- [ ] T001 Record the before-feature baselines per quickstart §0 into `specs/008-baseline-profiles/baselines.md`: median of three warm-daemon runs of `./gradlew checkAndroidHost --warning-mode all` and `./gradlew :androidApp:assembleDebug` (through `gradle-run`), plus the Pixel 6 Pro `app.size_bytes` median and round duration of the last published label from `cd app/perf && uv run tracepulse status --series --last 5`
- [ ] T002 Add to `app/gradle/libs.versions.toml`: version `androidx-profileinstaller = "1.4.1"`, library `androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "androidx-profileinstaller" }`, and plugin alias `baselineprofile = { id = "androidx.baselineprofile", version.ref = "androidx-benchmark" }` (1.5.0, contracts/build-config.md); add the plugin to the root `app/build.gradle.kts` `plugins { alias(libs.plugins.baselineprofile) apply false }` so both modules can apply it
- [ ] T003 [P] Create `app/perf/tests/fixtures/profile/` with: `benchmarkData-none.json`, `benchmarkData-profile.json`, `benchmarkData-full.json` (Macrobenchmark `benchmarkData.json` shapes for class `audio.tembra.perf.ProfileComparisonBenchmark`, methods `startup`, `tuner`, `metronome`, `stress`, 20 samples each, with `profile` medians about 20 % below `none` for `timeToInitialDisplayMs` and `full` a further 10 % below), `dumpsys-dexopt-speed-profile.txt`, `dumpsys-dexopt-verify.txt`, `broadcast-result-1.txt`, `broadcast-result-6.txt`, `unzip-l-with-prof.txt`, `unzip-l-without-prof.txt`, `getprop-sdk-37.txt`, `getprop-sdk-31.txt`
- [ ] T004 [P] Copy `specs/008-baseline-profiles/contracts/comparison.schema.json` to `app/perf/tracepulse/schema/comparison.schema.json` (the 007 precedent for `round-record.schema.json`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the build wiring every story needs (consumer and producer plugin, the installer dependency, the variants), the coverage check that generation and the lane both use, and the CLI entry.

**⚠️ CRITICAL**: no user story work begins until this phase is complete.

- [ ] T005 Add `implementation(libs.androidx.profileinstaller)` to `app/androidApp/build.gradle.kts` with a comment naming FR-006 (direct installs) and the comparison's `Partial(Require)`; build `:androidApp:assembleDebug --warning-mode all` through `gradle-run` and confirm zero warnings and a merged `androidx.startup` provider in `app/androidApp/build/intermediates/merged_manifest/debug/AndroidManifest.xml`
- [ ] T006 Apply the consumer plugin in `app/androidApp/build.gradle.kts` (`alias(libs.plugins.baselineprofile)`) with `baselineProfile { automaticGenerationDuringBuild = false; saveInSrc = true; mergeIntoMain = true; warnings { disabledVariants = false } }`, no `filter`; in `androidComponents.beforeVariants` disable every variant whose build type is `benchmarkRelease`, `benchmarkPerf` or `nonMinifiedPerf`; under `providers.gradleProperty("tembra.perf").isPresent` add `dependencies { baselineProfile(project(":perf-benchmarks")) }` (D1, contracts/build-config.md)
- [ ] T007 Apply the producer plugin in `app/perf-benchmarks/build.gradle.kts` (`alias(libs.plugins.baselineprofile)`, `baselineProfile { useConnectedDevices = true }`) and extend its `beforeVariants` to enable `nonMinifiedRelease` beside `perf` and `release`; run `./gradlew -Ptembra.perf=true :androidApp:tasks --all --warning-mode all` through `gradle-run` and confirm `generateBaselineProfile` exists, no `benchmark*`/`nonMinifiedPerf` task appears, and zero warnings
- [ ] T008 Verify the default build is untouched: `./gradlew :androidApp:assembleDebug --dry-run` lists no `*ArtProfile*`, `*BaselineProfile*`, `minify*` or `*R8*` task, and `./gradlew tasks --all` without `-Ptembra.perf` lists no `:perf-benchmarks` task; time `checkAndroidHost` and `assembleDebug` again as in T001 and append the numbers to `specs/008-baseline-profiles/baselines.md` (if either exceeds the T001 median by more than 5 %, switch to the D1 fallback: apply the consumer plugin only under `-Ptembra.perf` and register `src/main/generated/baselineProfiles` as a `baselineProfiles` source directory for ordinary builds; record which setup is in force)
- [ ] T009 Write the failing JVM test `app/androidApp/src/test/kotlin/audio/tembra/android/BaselineProfileCoverageTest.kt` (`kotlin.test`, JUnit 4 runner as the other host tests): with an empty profile every rule's label is reported uncovered; with a profile containing `Laudio/tembra/ui/tuner/TunerScreenKt;` and `HSPLaudio/tembra/ui/home/HomeScreenKt;->HomeScreen(Landroidx/compose/runtime/Composer;I)V` those two labels are covered; a missing profile file fails with the message naming `uv run tracepulse profile generate --device <id>`; the rule file ignores blank lines and `#` comments (contracts/coverage-rule.md). Add `testImplementation(libs.kotlin.test)` (and the JUnit dependency the shared modules use) to `app/androidApp/build.gradle.kts`
- [ ] T010 Implement `app/androidApp/src/test/kotlin/audio/tembra/android/BaselineProfileCoverage.kt` (one type: `parseRules(lines)`, `uncovered(rules, profileLines)` — a descriptor is covered when a profile line, stripped of its leading `H`/`S`/`P` flags, equals it or starts with it followed by `->`; a subject `when` over the line shapes with no `else`, skill `kotlin-control-flow`) and make T009 pass; the test reads `baseline-profile-coverage.txt` and `src/main/generated/baselineProfiles/baseline-prof.txt` relative to the module directory
- [ ] T011 Create `app/androidApp/baseline-profile-coverage.txt` with the six entries of contracts/coverage-rule.md (`app.start`, `app.activity`, `shell`, `home`, `tuner`, `metronome`) and confirm the descriptors against the real classes (`TembraApplication`, `MainActivity`, `NavigationShellKt`, `HomeScreenKt`, `TunerScreenKt`, `MetronomeScreenKt`): `./gradlew :androidApp:testDebugUnitTest --tests "*BaselineProfileCoverageTest"` now fails only with "baseline profile missing" (red until T020)
- [ ] T012 Register `checkAndroidHost` in `app/build-logic/src/main/kotlin/tembra.android.app.gradle.kts` (group `verification`, depends on `testDebugUnitTest`) and add `":androidApp:checkAndroidHost"` to the `checkAndroidHost` lane in `app/build.gradle.kts` (the other two lanes unchanged); `./gradlew checkAndroidHost` now runs the coverage test
- [ ] T013 Write failing tests in `app/perf/tests/test_cli.py` for a `profile` sub-command group with `generate`, `compare`, `verify` (arguments per contracts/cli.md: `--device`, `--keep-build`; `--with-full`, `--iterations`, `--journeys`, `--no-build`, `--apk`, `--test-apk`; `--apk`, `--build-type`); `profile compare --journeys nope` and `profile verify --build-type debug` exit 4; `profile` without a sub-command exits 4 with the three names listed
- [ ] T014 Add the `profile` subparsers to `app/perf/tracepulse/__main__.py` dispatching to `profile_generate.main`, `profile_compare.main`, `profile_verify.main` (modules created as stubs returning 4) to make T013 pass

**Checkpoint**: the plugin is wired, the default build is measured, the coverage test exists and is red, the CLI knows the three commands.

---

## Phase 3: User Story 1 — Generate the profile from the app's journeys with one command (Priority: P1) 🎯 MVP

**Goal**: `uv run tracepulse profile generate --device pixel6` produces `baseline-prof.txt` and `startup-prof.txt` from the four journeys, with the pipeline's device rules and restore-on-failure, and the files are committed.

**Independent Test**: quickstart §2 — on a fresh checkout with the Pixel attached, the command exits 0 in ≤ 15 min, both files exist and are non-empty, the six coverage labels are found, the coverage test is green; `--device` omitted with two devices → 4, locked device → 2, API < 33 → 2, a broken journey → 3 with the committed files unchanged.

### Tests for User Story 1

- [ ] T015 [P] [US1] Write failing tests in `app/perf/tests/test_profile_generate.py` with `FakeRunner`: (a) a failed `doctor` check exits 2 and runs no Gradle command; (b) `getprop ro.build.version.sdk` = 31 (fixture `getprop-sdk-31.txt`) exits 2 with "needs API 33+" and 37 proceeds; (c) the Gradle invocation is `./gradlew --console=plain -Ptembra.perf=true :androidApp:generateBaselineProfile` run from `app/` with `ANDROID_SERIAL=<serial>` in its environment; (d) after a successful task the coverage test is run as `./gradlew --console=plain :androidApp:testDebugUnitTest --tests "*BaselineProfileCoverageTest"`; (e) a failing Gradle task or coverage test restores the backed-up profile files byte for byte (and deletes them when they were absent before) and exits 3; (f) success prints the two files' rule counts and the covered labels; (g) no command reads stdin
- [ ] T016 [P] [US1] Write the device test class `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/BaselineProfileGenerator.kt` (`@RunWith(AndroidJUnit4::class)`, `@get:Rule val rule = BaselineProfileRule()`, one `@Test fun generate()`): `rule.collect(packageName = PerfMetrics.PACKAGE, includeInStartupProfile = true) { pressHome(); startActivityAndWait() }` then `rule.collect(packageName = PerfMetrics.PACKAGE, includeInStartupProfile = false) { pressHome(); startActivityAndWait(); TunerJourney().run(device); MetronomeJourney().run(device); StressJourney().run(device) }` with the plugin defaults for `maxIterations`/`stableIterations` (D2); document in the KDoc that a round never runs it (one `am instrument -e class` per journey class) and that the plugin's generation filters to `BaselineProfileRule`

### Implementation for User Story 1

- [ ] T017 [US1] Implement `app/perf/tracepulse/profile_generate.py` (`main(args, cfg, runner)`: registry lookup → `orchestrate_android.doctor` → eligibility via `getprop` → backup of the two files to a temporary directory → Gradle task with the environment → coverage test → summary; restore on any failure; exit statuses 0/2/3/4 per contracts/cli.md) to make T015 pass
- [ ] T018 [US1] Build the generation variant once through `gradle-run` to catch wiring errors before touching the device: `./gradlew -Ptembra.perf=true :androidApp:assembleNonMinifiedRelease :perf-benchmarks:assembleNonMinifiedRelease --warning-mode all` — zero warnings, both APKs produced
- [ ] T019 [US1] Run the first generation on the Pixel 6 Pro from a terminal (the device step is user tooling, not a Gradle command the agent runs directly): `cd app/perf && time uv run tracepulse profile generate --device pixel6`; record the wall time (SC-003 ≤ 15 min) in `specs/008-baseline-profiles/baselines.md`; if the command fails on a journey, fix the cause (anchor timing under profile collection) and rerun
- [ ] T020 [US1] Commit `app/androidApp/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`; confirm `./gradlew checkAndroidHost` is green (T011 turns green here) and that `baseline-prof.txt` contains rules for the six descriptors and for framework code (`Landroidx/compose/`), per spec US1 scenario 2
- [ ] T021 [US1] Exercise the negative paths of quickstart §2 on the Pixel and record the exit statuses in `specs/008-baseline-profiles/baselines.md`: locked device (lock it, run → 2 naming the check), a broken journey (temporarily rename `Anchors.TUNER_ROOT`'s tag in `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/Anchors.kt`, run → 3, `git diff -- app/androidApp/src/main/generated` empty; revert the rename), and a second generation on the unchanged build (both files differ only in order and timing-reached rules; the six descriptors present in both)

**Checkpoint**: a committed, coverage-checked profile exists; generation is one non-interactive command with the pipeline's rules.

---

## Phase 4: User Story 2 — Ship the profile in the Android app (Priority: P2)

**Goal**: release-like builds (release, perf) carry the profile, are shrunk with R8, refuse to build without a committed profile, take effect on direct installs, and can be verified on a device with `uv run tracepulse profile verify --device pixel6`.

**Independent Test**: quickstart §3 and §4 — the shrunk `perf` APK contains `assets/dexopt/baseline.prof`, a full `tracepulse run` round succeeds on it, every primary destination renders at compact and expanded widths like the debug build, `assembleRelease` fails without the profile with the generation message, `assembleDebug` runs no profile or R8 task, and `profile verify` reports present / recognised (1) / optimised (speed-profile) with exit 0.

### Tests for User Story 2

- [ ] T022 [P] [US2] Write failing tests in `app/perf/tests/test_profile_verify.py` with `FakeRunner`: (a) `unzip -l` output without `assets/dexopt/baseline.prof` (fixture `unzip-l-without-prof.txt`) fails the "present" step with exit 3; with it (`unzip-l-with-prof.txt`) the profile share is computed from the listed sizes and reported with one decimal; (b) the step order is install → `am start` → wait → broadcast → `cmd package compile -r bg-dexopt audio.tembra.app` → `dumpsys package dexopt` → `am force-stop`; (c) broadcast `result=1` and `result=2` pass, `result=6` fails with "no profile in the APK"; (d) `dumpsys` showing `status=speed-profile` passes, `status=verify` (fixture `dumpsys-dexopt-verify.txt`) fails naming the status; (e) `--build-type debug` is rejected (4); (f) the awake/unlocked/adb checks run, the thermal and battery checks do not
- [ ] T023 [P] [US2] Write a failing Gradle-level check for FR-006 as a shell script `app/tools/check-baseline-profile-gate.sh`: moves the two profile files aside, expects `./gradlew :androidApp:assembleRelease` to fail with a message containing `tracepulse profile generate`, expects `./gradlew -Ptembra.perf=true :androidApp:assembleNonMinifiedRelease` to still succeed, moves the files back; red until T026

### Implementation for User Story 2

- [ ] T024 [US2] Enable shrinking in `app/androidApp/build.gradle.kts`: `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }` (the `perf` type inherits through its existing `initWith`), and create `app/androidApp/proguard-rules.pro` with the header comment stating the policy (a rule only when a device run on the shrunk build fails, with the code path and the symptom) and the two known candidates as comments (kotlinx.serialization keep rules for `audio.tembra.**`; Room `_Impl` constructors), per contracts/build-config.md
- [ ] T025 [US2] Build the shrunk measurement build through `gradle-run`: `./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf --warning-mode all 2>&1 | tee /tmp/perf-build.log`; zero compiler and R8 warnings (`grep -ci warning` = 0 — fix or justify every R8 note by a rule with a comment, never by `-dontwarn` wildcards); `unzip -l` of the APK shows `assets/dexopt/baseline.prof`; record its size and share of the APK (≤ 3 %, FR-013) in `specs/008-baseline-profiles/baselines.md`
- [ ] T026 [US2] Add the `checkBaselineProfileCommitted` task to `app/androidApp/build.gradle.kts` (fails when `src/main/generated/baselineProfiles/baseline-prof.txt` is missing or has no rule line, message "no committed baseline profile: run `uv run tracepulse profile generate --device <id>` from app/perf"), wired in `androidComponents.onVariants` as a dependency of `merge<Variant>ArtProfile` for build types `release` and `perf` only (never `nonMinifiedRelease`, never debug); run `app/tools/check-baseline-profile-gate.sh` (T023) → green
- [ ] T027 [US2] Verify the shrunk build on the device with a full round (the journeys exercise Home, Tuner, Metronome and the stress path on the shrunk `perf` build): `cd app/perf && uv run tracepulse run --device pixel6 --label baseline-profiles-shrink-check --no-publish --no-build --apk ../androidApp/build/outputs/apk/perf/androidApp-perf.apk --test-apk ../perf-benchmarks/build/outputs/apk/perf/perf-benchmarks-perf.apk`; exit 0 with all four journeys; on a crash read `adb logcat` for the missing class or member, add one commented keep rule to `app/androidApp/proguard-rules.pro`, rebuild (T025) and rerun
- [ ] T028 [US2] Screenshot-verify every primary destination on the shrunk `perf` build against the debug build (constitution IV, SC-010): install both on the Pixel 6 Pro (compact) and on the tablet or fold emulator at expanded width, open Home, Timeline, Tuner (Auto Listen and By Ear, with a reference tone playing), Effects, Pads, Projects, Metronome (running, settings open), Profile, and capture each with `android screen capture --output specs/008-baseline-profiles/screenshots/<width>-<destination>-<build>.png`; any difference (text, icon, sound, resource, crash) is answered with one commented keep rule and a repeat of T025–T028; list the pairs checked in `specs/008-baseline-profiles/baselines.md`
- [ ] T029 [US2] Implement `app/perf/tracepulse/profile_verify.py` (`main(args, cfg, runner)`: registry lookup → the three device-state checks → build `assemble<BuildType>` through the pipeline's Gradle step or use `--apk` → APK inspection with `unzip -l` → install → launch → wait 5 s → broadcast → forced optimisation pass → `dumpsys` → force-stop → summary line; exit statuses per contracts/cli.md) to make T022 pass
- [ ] T030 [US2] Run `cd app/perf && uv run tracepulse profile verify --device pixel6` on the Pixel 6 Pro: expect "profile present (<bytes>, <share> %)", "recognised (result 1)", "optimised (speed-profile)", exit 0 within two minutes of the install (SC-005); run it a second time and expect "recognised (result 2)"; record both in `specs/008-baseline-profiles/baselines.md`
- [ ] T031 [US2] Confirm the debug build is unchanged after T024–T026: `./gradlew :androidApp:assembleDebug --dry-run` lists no `*ArtProfile*`, `*BaselineProfile*`, `minify*`, `*R8*` or `checkBaselineProfileCommitted` task; time `assembleDebug` as in T001 and append to `specs/008-baseline-profiles/baselines.md`; time `./gradlew :androidApp:assembleRelease --warning-mode all` once (release is expected slower under R8; it is recorded, not budgeted)

**Checkpoint**: the shipped build is shrunk, carries the profile, refuses to build without one, and a device confirms the profile is recognised and compiled.

---

## Phase 5: User Story 3 — Prove that the profile helps (Priority: P3)

**Goal**: `uv run tracepulse profile compare --device pixel6` measures the same build in the `none` and `profile` states (and `full` on request), writes `comparison.json` + `comparison.md` with medians, change, direction and the Mann–Whitney verdict, never touches the history, and the Pixel 6 Pro shows the ≥ 15 % cold-start gain.

**Independent Test**: quickstart §5 — the command exits 0 in ≤ 40 min for the two default states; `comparison.md` shows `startup.ttid_ms` at least 15 % lower in `profile` with `confirmed: true`, TTFD and `tuner.load`/`metronome.load` lower and confirmed, no metric worse and confirmed; `tracepulse status` shows no new round; `--with-full` adds the gap-closed column.

### Tests for User Story 3

- [ ] T032 [P] [US3] Write failing tests in `app/perf/tests/test_collect_android.py` (append): `journey_of_method("audio.tembra.perf.ProfileComparisonBenchmark", "startup")` → `startup`, `"tuner"` → `tuner`, `"metronome"` → `metronome`, `"stress"` → `stress`; an unknown method raises `CollectError`; a comparison fixture file parses into per-journey `JourneyResult`s with the same catalogue keys as a round's (`startup.ttid_ms`, `startup.ttfd_ms`, `frames.p50_ms`, `memory.peak_kb`, `journey.duration_ms`, `tuner.load`, …)
- [ ] T033 [P] [US3] Write failing tests in `app/perf/tests/test_profile_compare.py` with `FakeRunner` and the T003 fixtures: (a) states run in order `none`, `profile`, and `full` only with `--with-full`; (b) before each state the app is uninstalled and both APKs installed; (c) exactly one `am instrument` per state with `-e class audio.tembra.perf.ProfileComparisonBenchmark -e tembra.compare.state <state> -e androidx.benchmark.iterations <N>` and the `additionalTestOutputDir`; (d) a state whose instrumentation fails exits 3 naming the state and keeps the earlier state's files; (e) a state missing a requested journey exits 3 naming it; (f) medians, `change_pct` (signed), `direction` from the catalogue's `higher_is_worse`, Mann–Whitney two-sided `p_value` and `confirmed = p < 0.01` match hand-computed values on the fixtures; (g) `gap_closed_pct` present only with `full`, `null` when `none` and `full` medians are equal; (h) `comparison.json` validates against `tracepulse/schema/comparison.schema.json` and `comparison.md` holds one table row per metric and the `startup.ttid_ms` headline; (i) `--journeys startup` restricts the journeys and the required-families check; (j) nothing is written under `data/records/`, `data/unpublished/` or into `tracepulse.db` (the store file is untouched before and after); (k) a failed `doctor` exits 2 before any build
- [ ] T034 [P] [US3] Write the JVM-free Kotlin test for the state parser: `app/perf-benchmarks` has no host test source set, so the contract is a device self-check inside the benchmark class — in `ProfileComparisonBenchmark` (T036) the first statement of each test reads the state with `CompareState.parse(InstrumentationRegistry.getArguments().getString("tembra.compare.state"))` and an unknown or missing value fails with the three accepted values listed; verify by running the class once with `-e tembra.compare.state bogus` in T038 and expecting that failure

### Implementation for User Story 3

- [ ] T035 [P] [US3] Create `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/CompareState.kt`: `enum class CompareState(val argument: String) { None("none"), Profile("profile"), Full("full") }` with `fun compilationMode(): CompilationMode` (a subject `when` with no `else`: `CompilationMode.None()`, `CompilationMode.Partial(BaselineProfileMode.Require)`, `CompilationMode.Full()`) and a companion `fun parse(argument: String?): CompareState` that throws `IllegalArgumentException("tembra.compare.state must be one of none, profile, full")` (parsing owned by the type, skill `kotlin-api-design`)
- [ ] T036 [US3] Create `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/ProfileComparisonBenchmark.kt` (`MacrobenchmarkRule`; tests `startup` (cold, `StartupTimingMetric`), `tuner`, `metronome`, `stress` (warm, `PerfMetrics.journey(name)`, `trace("journey.<name>")` around the journey, the same setup block as the existing benchmarks); `compilationMode = CompareState.parse(...).compilationMode()`, `iterations = PerfMetrics.ITERATIONS`); KDoc states it is run only by `tracepulse profile compare`
- [ ] T037 [US3] Add `journey_of_method(class_name, method)` to `app/perf/tracepulse/collect_android.py` and let the collector key comparison files by method when the class is `ProfileComparisonBenchmark` (rounds keep `journey_of(class_name)` untouched) to make T032 pass
- [ ] T038 [US3] Implement `app/perf/tracepulse/profile_compare.py` (`main(args, cfg, runner)`: registry lookup → `doctor` → build or `--no-build` artefacts → per state: uninstall, install, one detached `am instrument` polled as `run` does, pull `benchmarkData.json` to `data/comparisons/<id>/<state>/` → collect → statistics with `scipy.stats.mannwhitneyu` (two-sided) → `comparison.json` validated with `jsonschema` and `comparison.md` → stdout table and headline; exit statuses per contracts/cli.md) to make T033 pass; the comparison id is `<yyyymmdd>T<hhmmss>Z-<device_id>`
- [ ] T039 [US3] Build the artefacts through `gradle-run` (`./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf --warning-mode all`), then run the state-parser self-check of T034 on the Pixel: `adb shell am instrument -w -e class audio.tembra.perf.ProfileComparisonBenchmark#startup -e tembra.compare.state bogus audio.tembra.perf/androidx.test.runner.AndroidJUnitRunner` fails listing `none, profile, full`
- [ ] T040 [US3] Run the comparison on the Pixel 6 Pro from a terminal: `cd app/perf && time uv run tracepulse profile compare --device pixel6 --no-build --apk ../androidApp/build/outputs/apk/perf/androidApp-perf.apk --test-apk ../perf-benchmarks/build/outputs/apk/perf/perf-benchmarks-perf.apk`; exit 0 in ≤ 40 min (SC-004); copy `comparison.md` to `specs/008-baseline-profiles/evidence/comparison-pixel6.md`; check SC-001 (`startup.ttid_ms` ≥ 15 % lower, confirmed) and SC-002 (TTFD, `tuner.load`, `metronome.load` lower and confirmed; nothing worse and confirmed); a shortfall is written into the evidence file as a finding with the measured number, never hidden
- [ ] T041 [US3] Run once more with `--with-full` and copy the result to `specs/008-baseline-profiles/evidence/comparison-pixel6-with-full.md` (the gap-closed column); confirm `uv run tracepulse status` lists no new round and `git -C app/perf/.history status --short` is empty (the comparison never enters the history, FR-009)

**Checkpoint**: the gain is measured, confirmed and on record; the history is untouched.

---

## Phase 6: User Story 4 — Keep the profile current as features land (Priority: P4)

**Goal**: the coverage check guards the committed profile in the host lane, and the documentation states when and how to regenerate, verify and compare.

**Independent Test**: quickstart §6 — `./gradlew checkAndroidHost` passes within 5 % of the T001 timing; pointing the `home` rule at a missing screen makes it fail naming `home (Laudio/tembra/ui/home/MissingScreenKt;)`; the README sections exist and say what FR-012 requires.

### Tests for User Story 4

- [ ] T042 [US4] Run the negative lane check of quickstart §6 (edit the `home` line of `app/androidApp/baseline-profile-coverage.txt` to `Laudio/tembra/ui/home/MissingScreenKt;`, `./gradlew checkAndroidHost` fails naming the label and descriptor, `git checkout` the file, the lane passes again) and record the lane timing against T001 (SC-006, within 5 %) in `specs/008-baseline-profiles/baselines.md`; if over budget, replace the JVM test with a Gradle verification task of the same logic in `app/build-logic/src/main/kotlin/` (D8 fallback) and re-measure

### Implementation for User Story 4

- [ ] T043 [P] [US4] Add the "Baseline profiles" section to `app/perf/README.md`: what the two profiles are, the three commands with their exit statuses, where the files live, when to regenerate (the startup path, a primary destination, a journey or a main interaction changed; before a release), when to commit (covered code changed; not for noise-only differences), that generation is never part of a round, the plain `adb` verification steps (install, launch, `am broadcast … INSTALL_PROFILE …`, `cmd package compile -r bg-dexopt audio.tembra.app`, `dumpsys package dexopt`), how to read `comparison.md`, the keep-rule policy of `proguard-rules.pro`, and the coverage rule file
- [ ] T044 [P] [US4] Extend the "Every feature instruments itself" section of `app/perf/README.md` with the FR-012 sentence: a feature that adds a primary destination, a journey or a main interaction regenerates the profile in the same feature (and appends to `baseline-profile-coverage.txt` when it adds a destination the journeys visit)
- [ ] T045 [P] [US4] Add rows to the "where does X go" table in `app/README.md` for the profile files (`androidApp/src/main/generated/baselineProfiles/`), the coverage rule (`androidApp/baseline-profile-coverage.txt`) and the keep rules (`androidApp/proguard-rules.pro`), and one line to the command list in `CLAUDE.md`: `uv run tracepulse profile generate|compare|verify --device pixel6` with a pointer to `app/perf/README.md`

**Checkpoint**: the profile cannot go stale silently, and a maintainer can regenerate, verify and compare from the documentation alone.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: the lane gate, the evidence bundle, and the session after merge.

- [ ] T046 Run the full lanes and the warning gate per quickstart §7 through `gradle-run`: `./gradlew checkAndroidHost checkIos --warning-mode all`, `./gradlew engineHostTest`, `./gradlew :androidApp:assembleRelease --warning-mode all 2>&1 | grep -ci warning` = 0, `cd app/perf && uv run pytest`; confirm `git diff --stat -- app/iosApp app/shared` is empty (FR-014)
- [ ] T047 [P] Assemble the pull-request evidence in `specs/008-baseline-profiles/evidence/README.md`: the comparison summaries (T040, T041), the verification output (T030), the screenshot pairs (T028), the before/after timings and sizes (`baselines.md`), and the principle each non-obvious decision serves (shrinking → spec FR-005a/planning input; `profileinstaller` → Principle VI justification; no new module → Principle II)
- [ ] T048 Run quickstart §1–§7 end to end on a clean checkout of the branch (fresh `uv sync`, fresh Gradle daemon) and fix any step that does not match its expected outcome
- [ ] T049 After the merge to `main`, run the labelled session per quickstart §8: `uv run tracepulse run --device pixel6 --label baseline-profiles --rounds 5` and the same on `pixel7` (wake the Pixel 7 Pro before the session), then `uv run tracepulse sync && uv run tracepulse report --open`; confirm no family is flagged in the worse direction, every onset at the boundary is an improvement, `app.size_bytes` is lower than the previous label (SC-008), and attach the report to the pull request (SC-009)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (it is the "before" measurement); T002–T004 can run in parallel after it
- **Foundational (Phase 2)**: depends on Setup; T005 → T006 → T007 → T008 in order (each builds on the previous wiring); T009 → T010 → T011 → T012 in order; T013 → T014; the two chains are independent of each other
- **User Stories (Phase 3+)**: all depend on Phase 2
  - **US1 (Phase 3)**: T015 and T016 in parallel; T017 after T015; T018 after T016 and T007; T019 after T017 and T018; T020 after T019 (turns T011 green); T021 after T020
  - **US2 (Phase 4)**: depends on US1's committed profile (T020). T022 and T023 in parallel; T024 → T025 → T026 → T027 → T028 in order (the shrunk build must exist before it is verified); T029 after T022 and T025; T030 after T029 and T028; T031 after T026
  - **US3 (Phase 5)**: depends on US2's shrunk `perf` build (T025) and the installer dependency (T005). T032, T033, T034 in parallel; T035 before T036; T037 after T032; T038 after T033, T035, T037; T039 after T036; T040 after T038 and T039; T041 after T040
  - **US4 (Phase 6)**: T042 after T020; T043–T045 in parallel, after T040 (the documented numbers and commands must exist)
- **Polish (Phase 7)**: T046 after every story; T047 after T041 and T030; T048 after T046 and T047; T049 after the merge

### User Story Dependencies

- **US1 (P1)**: needs only Phase 2 — the MVP
- **US2 (P2)**: needs US1's committed profile (the build check and the APK inspection require it)
- **US3 (P3)**: needs US2's shrunk measurement build and the installer dependency; independent of US4
- **US4 (P4)**: the coverage test itself is foundational (T009–T012); the story's own tasks need US1 (the profile to check) and US3 (the commands to document)

### Within Each User Story

- Test tasks are written and fail before their implementation tasks
- Python command modules are implemented after their `FakeRunner` tests; device runs follow the Gradle build of the artefacts
- Keep rules are added only after a device run on the shrunk build fails (T027/T028), each with a comment
- Nothing in any story modifies `run.py`, the four existing benchmark classes or the history modules

### Parallel Opportunities

- Phase 1: T002, T003, T004 together
- Phase 2: the build chain (T005–T008), the coverage chain (T009–T012) and the CLI chain (T013–T014) in parallel
- US1: T015 ∥ T016
- US2: T022 ∥ T023; T029 can proceed while T027–T028 run on the device
- US3: T032 ∥ T033 ∥ T034 ∥ T035; T037 while T036 is written
- US4: T043 ∥ T044 ∥ T045
- Polish: T047 while T046 runs

---

## Parallel Example: User Story 1

```bash
# Tests and the device test class together:
Task: "Write failing tests in app/perf/tests/test_profile_generate.py (FakeRunner: refusals, eligibility, Gradle env, coverage test, restore)"
Task: "Write app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/BaselineProfileGenerator.kt"

# Then sequentially: profile_generate.py → assembleNonMinifiedRelease → generate on pixel6 → commit the files → negative paths
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: baselines and versions
2. Phase 2: plugin wiring, installer dependency, coverage test (red), CLI entry
3. Phase 3: `tracepulse profile generate` and the first committed profile
4. **STOP and VALIDATE**: quickstart §2 (generation ≤ 15 min, coverage green, negative paths)
5. The profile is committed even before shrinking lands; a release build already carries it (without DEX layout until Phase 4)

### Incremental Delivery

1. Setup + Foundational → the build knows about profiles, the default build is measured
2. US1 → a committed profile (MVP)
3. US2 → shrunk release-like builds, build gate, device verification (`profile verify`)
4. US3 → the comparison and the measured gain
5. US4 → documentation and the standing rule
6. Polish → lanes, evidence, the labelled session after merge

### Notes

- [P] tasks = different files, no dependencies
- Device steps (T019, T021, T027, T028, T030, T040, T041, T049) run from a terminal on the Mac with the Pixel attached; keep the device awake and unlocked (the Pixel 6 Pro reports AC power, so stay-awake-on-USB must be checked before each)
- A shortfall against SC-001 is a finding to record in the evidence, not a reason to change the comparison
- Never commit under `app/perf/data/`; the comparison outputs are evidence copied into `specs/008-baseline-profiles/evidence/`
- Commit after each task or logical group with a conventional message; the spec-kit hooks commit at phase boundaries
