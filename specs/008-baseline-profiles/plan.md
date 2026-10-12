# Implementation Plan: Baseline Profiles

**Branch**: `008-baseline-profiles` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-baseline-profiles/spec.md`, plus the owner's planning decision of 2026-10-12: shrink the release-like Android builds with R8 (minification and resource shrinking) so the startup profile takes effect.

## Summary

Add Android baseline and startup profiles to the app with the Baseline Profile Gradle plugin (1.5.0, the Macrobenchmark train already in the catalogue): the producer is the existing `:perf-benchmarks` module, whose journeys drive a `BaselineProfileGenerator`; the consumer is `:androidApp`, which commits the generated `baseline-prof.txt` and `startup-prof.txt` under `src/main/generated/baselineProfiles/` and ships them in every release-like build. The release and measurement builds become shrunk R8 builds (owner's decision) so the startup profile lays out the startup code and the app gets smaller. `profileinstaller` makes the profile take effect on direct installs. Three new `tracepulse profile` commands — `generate`, `compare`, `verify` — reuse the pipeline's device protocol, build step, journeys, collector and statistical test, and write nothing into the history: rounds and labelled sessions are untouched (clarified 2026-10-12). A one-file coverage rule and a JVM test in the host lane keep the committed profile from going stale, and a build check refuses to ship without one. Decisions and the facts they rest on: [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (JVM 17 target) for the generator, the comparison benchmark and the coverage test; Gradle 9.7.0 with AGP 9.3.3 and Kotlin DSL for the build wiring; Python 3.12 via `uv` for the `tracepulse` commands; R8 as shipped with AGP 9.3.3.

**Primary Dependencies**: `androidx.benchmark:benchmark-baseline-profile-gradle-plugin:1.5.0` (build only); `androidx.benchmark:benchmark-macro-junit4:1.5.0` and UiAutomator 2.3.0 (already present, test module only); `androidx.profileinstaller:profileinstaller:1.4.1` (the one new shipped dependency, Apache-2.0); `scipy` (already in the pipeline) for the comparison's Mann–Whitney U. No new C++ or iOS dependency.

**Storage**: committed text files `app/androidApp/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`; the coverage rule `app/androidApp/baseline-profile-coverage.txt`; keep rules `app/androidApp/proguard-rules.pro`; comparison and verification outputs under `app/perf/data/comparisons/<id>/` (git-ignored, like rounds); nothing in `tracepulse.db`, the records directory or the `perf-history` branch.

**Testing**: pytest in `app/perf/tests` (the three commands against the fake `Runner`, the comparison statistics and file writer, the collector's method-keyed mapping, exit statuses); `kotlin.test` JVM unit test in `:androidApp` (`BaselineProfileCoverageTest`, joins `checkAndroidHost`); the generator and comparison are device tests run by the commands, each with a self-check (the generator asserts the written profile is non-empty, the comparison asserts a result per journey and state); on-device verification of the shrunk build: one `tracepulse run` round on the Pixel 6 Pro and screenshots of every primary destination at compact and expanded widths; the existing lanes stay green with zero warnings.

**Target Platform**: Android (minSdk 26); profile generation on a non-rooted device needs API 33+ (the owner's Pixel 6 Pro reports API 37; the Pixel 7 Pro is on the same release); comparison and verification on the Pixel 6 Pro (Pixel 7 Pro optional). iOS untouched.

**Project Type**: mobile app build and tooling — Gradle configuration, an Android test module addition, a Python CLI extension, documentation.

**Performance Goals**: cold-start first-frame median ≥ 15 % lower with the profile than without on the Pixel 6 Pro, statistically confirmed (SC-001); TTFD and the tuner/metronome first-open sections lower, nothing worse (SC-002); generation ≤ 15 min (SC-003); comparison ≤ 40 min for two states (SC-004); installed app smaller than before, profile ≤ 3 % of it (SC-007); host lane and default build within 5 % (SC-006/SC-007); labelled session shows only improvements (SC-008).

**Constraints**: rounds and sessions unchanged (FR-010); generation and comparison never run inside a round and never write history (FR-009/FR-010); builds never need a device (FR-005/FR-006); debug build unshrunk and without a profile step (FR-005/FR-005a); zero compiler and shrinker warnings; one type per file; no Kotlin on the audio callback (nothing here touches the engine); iOS and shared-code behaviour unchanged (FR-014).

**Scale/Scope**: one app module, one test module, three CLI commands, four journeys, two profile files of a few hundred KB of text each, a coverage rule of six lines, one keep-rules file; two Pixels; about 10 minutes of device time to generate, about 30 to compare.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How the plan satisfies it |
|---|---|---|
| I. Real-time engine isolation | PASS (n/a) | No engine, bridge or callback change. R8 keeps classes with `native` methods and their names (`proguard-android-optimize.txt`), so the JNI symbols of the existing bridge are unchanged; the C ABI is not touched. |
| II. Shared Kotlin control plane, fixed module graph | PASS | No module is added; `:perf-benchmarks` (already justified in 007) gains two classes; `:androidApp` gains a dependency, build configuration and a host test; no shared module changes. |
| III. Test-first | PASS | pytest first for every `tracepulse profile` behaviour (fake runner, fixtures of benchmark JSON and `dumpsys` output); the coverage test is written before the coverage rule file and the generated profile exist (red: "profile missing"); the generator's and comparison's self-checks fail before their bodies are written; keep rules are added only after a device run fails (the failing run is the red). Kotlin tests never link the real engine (none involved). |
| IV. Adaptive, input-agnostic UI | PASS (verification only) | No UI change; the shrunk build is screenshot-verified at compact and expanded widths on Android against the debug build (SC-010), as the principle requires for any change that can alter what renders. |
| V. Document model in beats | PASS (n/a) | No document changes. |
| VI. Simplicity | PASS with two justified choices | One new shipped dependency (`profileinstaller`, Apache-2.0, justified: direct installs and the comparison's `Partial(Require)` need it); the plugin is build-only. R8 shrinking is the owner's decision (planning input) and is the documented prerequisite of the startup profile — see Complexity Tracking. No filter, no managed device, no new module, no in-app verifier. |
| VII. Code organisation | PASS | `BaselineProfileGenerator.kt`, `ProfileComparisonBenchmark.kt`, `CompareState.kt` (the state enum with its parser) in `audio.tembra.perf`; `BaselineProfileCoverageTest.kt` and `BaselineProfileCoverage.kt` (the rule reader) in `audio.tembra.android`; Python modules `profile_generate.py`, `profile_compare.py`, `profile_verify.py` in `tracepulse`; no TODOs. |
| VIII. Skill-guided Kotlin/Compose | PASS | `kotlin-api-design` for `CompareState` (parsing owned by the type, a `when` with no `else`); `kotlin-control-flow` for the coverage reader's branching; `compose-ui-testing-patterns` is not engaged (no Compose test is written; the journeys are reused as they are); every agent-initiated Gradle command runs through `gradle-run`, including the generation and comparison builds an agent triggers through `tracepulse` (the pipeline itself calls `./gradlew` as user tooling, as in 007). Routed through `using-chrisbanes-skills` at task time. |
| Workflow: every feature instruments itself | PASS (exempt, declared) | Tooling and build work: no journey, section or counter is added; the spec declares it and the labelled session after merge is the standing cadence (expected: improvements from shrinking, no regression). |
| Workflow & gates | PASS | Build order unchanged (audio spine remains next); definition of done per task (failing test first, lanes green, zero warnings including R8's, screenshots for the shrunk build, no forbidden dependency); the pull request carries the comparison summary, the session report and the screenshots (SC-009). |

**Post-design re-check (after Phase 1)**: unchanged — the contracts add no module and no shipped dependency beyond `profileinstaller`; the comparison and generation commands write only under `app/perf/data/comparisons/` and the two committed profile files; the shrunk-build verification is a quickstart step with a screenshot checklist.

## Project Structure

### Documentation (this feature)

```text
specs/008-baseline-profiles/
├── plan.md              # this file
├── research.md          # Phase 0: decisions D1–D12 and the facts they rest on
├── data-model.md        # Phase 1: profile files, coverage rule, generation run, optimisation state, comparison, verification
├── quickstart.md        # Phase 1: validation guide (unit suites, generate, coverage, shrunk build, verify, compare, lanes, session)
├── contracts/
│   ├── cli.md                     # tracepulse profile generate | compare | verify: arguments, exit statuses, outputs
│   ├── comparison.schema.json     # comparison.json written by `profile compare`
│   ├── coverage-rule.md           # baseline-profile-coverage.txt format and the test's contract
│   └── build-config.md            # Gradle wiring: plugin, build types, disabled variants, file locations, keep rules, presence check, lane
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
app/
├── gradle/libs.versions.toml                  # + androidx-profileinstaller 1.4.1; plugin alias baselineprofile (benchmark 1.5.0 train)
├── build.gradle.kts                           # host lane also depends on :androidApp:checkAndroidHost
├── build-logic/src/main/kotlin/
│   └── tembra.android.app.gradle.kts          # registers checkAndroidHost → testDebugUnitTest for the app module
├── androidApp/
│   ├── build.gradle.kts                       # androidx.baselineprofile consumer; release: minify + shrinkResources + proguard files;
│   │                                          #   disabled benchmark*/nonMinifiedPerf variants; baselineProfile(project(":perf-benchmarks")) under -Ptembra.perf;
│   │                                          #   checkBaselineProfileCommitted before merge{Release,Perf}ArtProfile; profileinstaller dependency
│   ├── proguard-rules.pro                     # keep rules, each with the reason (starts with the header only)
│   ├── baseline-profile-coverage.txt          # the coverage rule (contracts/coverage-rule.md)
│   ├── src/main/generated/baselineProfiles/
│   │   ├── baseline-prof.txt                  # generated, committed
│   │   └── startup-prof.txt                   # generated, committed
│   └── src/test/kotlin/audio/tembra/android/
│       ├── BaselineProfileCoverage.kt         # reads the rule file and the profile, returns the uncovered descriptors
│       └── BaselineProfileCoverageTest.kt     # the host-lane check (FR-011)
├── perf-benchmarks/
│   ├── build.gradle.kts                       # + androidx.baselineprofile producer (useConnectedDevices); enables the nonMinifiedRelease variant
│   └── src/main/kotlin/audio/tembra/perf/
│       ├── BaselineProfileGenerator.kt        # BaselineProfileRule over startup (startup profile) + the three journeys
│       ├── ProfileComparisonBenchmark.kt      # the four journeys under the state from the instrumentation argument
│       └── CompareState.kt                    # none | profile | full → CompilationMode; parse(argument)
├── perf/
│   ├── tracepulse/
│   │   ├── __main__.py                        # + `profile` sub-commands
│   │   ├── profile_generate.py                # doctor + eligibility, backup, generateBaselineProfile with ANDROID_SERIAL, coverage test, restore
│   │   ├── profile_compare.py                 # per-state fresh install + instrument, medians, Mann–Whitney, comparison.json/.md
│   │   ├── profile_verify.py                  # APK asset check, install, launch, INSTALL_PROFILE broadcast, bg-dexopt, dumpsys
│   │   └── collect_android.py                 # + journey_of_method() for the comparison class
│   ├── tests/                                 # test_profile_generate.py test_profile_compare.py test_profile_verify.py (+ fixtures)
│   ├── fixtures/                              # comparison benchmarkData.json per state, dumpsys dexopt samples, broadcast outputs
│   └── README.md                              # "Baseline profiles" section; regeneration sentence in "Every feature instruments itself"
└── README.md                                  # "where does X go" rows for the profile files, the coverage rule and the keep rules
CLAUDE.md                                      # one command line for the three profile commands
```

**Structure Decision**: no new module. The generator and the comparison sit with the benchmarks they share journeys with (`:perf-benchmarks`, included only under `-Ptembra.perf`); the profile files, the coverage rule, the keep rules and the coverage test sit in `:androidApp`, the module that ships them; the commands sit in `tracepulse`, which already owns device protocol, build and collection for Android.

## Implementation order (for `/speckit-tasks`)

1. **Baselines and foundation**: record lane and build timings (D12); catalogue entries; `profileinstaller` dependency; consumer and producer plugin wiring with the three unneeded variants disabled; R8 on `release` with an empty rules file; `checkBaselineProfileCommitted`; `checkAndroidHost` for the app module in the lane.
2. **US1 Generate**: `CompareState` is not needed yet; `BaselineProfileGenerator` with its self-check; `tracepulse profile generate` test-first (fake runner: refusal paths, eligibility, backup/restore, Gradle invocation with `ANDROID_SERIAL`, coverage test invocation); first real generation on the Pixel 6 Pro; commit the two files.
3. **US4 Keep current** (pulled forward because generation needs it): `baseline-profile-coverage.txt`, `BaselineProfileCoverage` + test (red before the files exist); lane timing re-measured.
4. **US2 Ship**: R8 verification on the device — a `tracepulse run` round on the shrunk `perf` build, screenshots at compact and expanded widths, keep rules only where a run fails; `tracepulse profile verify` test-first, then on the Pixel; APK asset and size check.
5. **US3 Prove**: `CompareState`, `ProfileComparisonBenchmark`, `collect_android.journey_of_method`, `tracepulse profile compare` test-first (fixtures per state, statistics, file writer, exit statuses), then the real comparison on the Pixel 6 Pro (two states; `--with-full` once for context).
6. **Polish**: documentation (D11), CLAUDE.md line, timings for SC-006/SC-007, quickstart end to end; after merge, the labelled session `baseline-profiles` on each Pixel.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| R8 shrinking and obfuscation of the release-like builds (Principle VI: a product-wide build change, keep rules to maintain) | The startup profile's DEX layout is R8-only; the owner chose to enable it in this feature rather than ship the startup profile dormant; it also shrinks the app (SC-007) | Committing the startup profile without R8 (no effect until a later feature) and R8 without shrinking/obfuscation (undocumented, gain unverified) were both offered and rejected by the owner on 2026-10-12. Mitigation: rules only where a device run proves the need, each commented; the shrunk build is exercised by a full round and screenshot-verified before acceptance. |
| Four extra build types created by the plugin in `:androidApp` (configuration surface) | The plugin creates `nonMinified<Type>` and `benchmark<Type>` for every non-debuggable type and offers no option to restrict them; `nonMinifiedRelease` is the generation target | Gating the consumer plugin behind `-Ptembra.perf` would leave ordinary builds to pick the profile up from a manually registered source directory — a less documented path. Mitigation: the three unneeded variants are disabled in `beforeVariants`; default-build and lane timings are measured, and the gated setup is the recorded fallback if the 5 % budget is exceeded (D1). |
