# Baselines and device-step outcomes (feature 008)

Written during implementation (tasks.md T001, T008, T019, T021, T025, T028, T030, T031, T042). Timings are the
`gradle-run` wrapper's `elapsed_seconds` (wall clock around `./gradlew … --console=plain --no-scan`), warm daemon,
configuration cache on (`org.gradle.configuration-cache=true`, `org.gradle.caching=true` in `app/gradle.properties`),
Mac host. Device: Pixel 6 Pro `19021FDEE001AH` (alias `pixel6`).

## T001 — before any change (2026-10-12, main at c71a190 / branch 008-baseline-profiles)

| Measure | Runs | Median |
|---|---|---|
| `./gradlew checkAndroidHost --warning-mode all`, tree up to date | 0.419 / 0.410 / 0.403 s | **0.41 s** |
| `./gradlew checkAndroidHost --warning-mode all`, every host test task `--rerun` (9 executed) | 0.924 / 0.983 / 0.931 s | **0.93 s** |
| `./gradlew :androidApp:assembleDebug`, tree up to date | 0.425 / 0.423 / 0.422 s | **0.42 s** |

Note: a warm, up-to-date lane is sub-second under the configuration cache, so the 5 % budget of SC-006/SC-007 is
±20 ms of scheduling noise on these numbers; the forced-tests variant (second row) is the figure the new
`:androidApp:testDebugUnitTest` can actually move and is re-measured with the same command plus
`:androidApp:testDebugUnitTest --rerun` after the feature. The `clean<Task>` rule does not exist under Gradle 9.7
(`cleanAndroidHostTest` is not found), hence `--rerun`.

Pixel 6 Pro, last published label `007-performance-pipeline` (five rounds, commit 397d53b1, build type `perf`,
2026-10-11/12):

| Measure | Values | Median |
|---|---|---|
| `app.size_bytes` (perf APK, unshrunk) | 14 922 467 × 5 | **14 922 467 bytes** (14.2 MiB) |
| round duration (started → finished) | 14.1 / 14.7 / 14.4 / 14.2 / 14.9 min | **14.4 min** |
| `startup.ttid_ms` round median (full compilation) | 497.6 / 498.7 / 503.9 / 502.6 / 500.1 ms | **500.1 ms** |

The 3 % profile budget (FR-013) against this size is 447 674 bytes; the after-feature size is the shrunk APK and
is expected below 14.9 MB.

## T008 — after the plugin wiring (consumer on `:androidApp`, producer on `:perf-benchmarks`, `profileinstaller`)

Wiring note: the plugin compiles against AGP, and AGP lives in the `build-logic` classloader (the convention
plugins declare it as `implementation`); a plugin declared in the root `plugins {}` block loads in the root
classloader and fails with `NoClassDefFoundError: com/android/build/api/AndroidPluginVersion`, and a plugin on
`build-logic`'s classpath is not resolvable by id from a module script ("not found in any of the following
sources"). So the plugin is a `build-logic` dependency applied by two convention plugins: `tembra.android.app`
(consumer) and the new `tembra.android.benchmarks` (`com.android.test` + producer), which also ends the second
AGP instance `:perf-benchmarks` loaded through its versioned `alias(libs.plugins.androidTest)` until now.

Checks: `./gradlew -Ptembra.perf=true :androidApp:tasks --all --warning-mode all` → `generateBaselineProfile`
and `assembleNonMinifiedRelease` present, zero `benchmarkRelease`/`benchmarkPerf`/`nonMinifiedPerf` tasks, zero
warnings; `./gradlew :androidApp:assembleDebug --dry-run` → zero lines matching `artprofile|baselineprofile|minify|r8`;
`./gradlew tasks --all` without the property → zero `:perf-benchmarks` tasks; merged debug manifest carries
`androidx.profileinstaller.ProfileInstallerInitializer` and `ProfileInstallReceiver`.

| Measure | Runs | Median | vs T001 |
|---|---|---|---|
| `checkAndroidHost --warning-mode all`, up to date | 0.460 / 0.424 / 0.425 s | **0.425 s** | +3.7 % (15 ms) |
| `:androidApp:assembleDebug`, up to date, first six samples | 0.600 / 0.484 / 0.469 / 0.467 / 0.453 / 0.453 s | 0.46 s | +9 % nominal |

The nominal `assembleDebug` excess prompted a matched A/B on the same daemon (wiring stashed, then restored):

| State (same daemon, consecutive) | Runs | Median |
|---|---|---|
| before (wiring stashed) | 0.447 / 0.436 / 0.459 s | **0.447 s** |
| after (wiring restored) | 0.446 / 0.448 / 0.450 s | **0.448 s** |

Difference +0.2 %: the earlier gap was daemon state (T001 ran on a fresher daemon), not the plugin. The
unconditional consumer plugin (D1's primary setup) is in force; the D1 fallback is not needed.

## T019/T020 — first generation on the Pixel 6 Pro (2026-10-12)

Three runs of `uv run tracepulse profile generate --device pixel6` (wall clock from `time`):

| Run | Result | Wall | What it taught |
|---|---|---|---|
| 1 | exit 0, `baseline-prof.txt` 21 157 rules / 2 274 679 bytes, **no `startup-prof.txt`** | 3 min 46 s | The plugin finds a test's profile files through the per-test `additionaltestoutput.benchmark.message_<class>.<method>.txt` link; a second `collect` in the same `@Test` overwrites it, so only the second collect's (dated) baseline file was merged and the device's `-startup-prof.txt` (14 735 lines) was dropped. D2's "one test, two collects" shape was wrong: the generator now has `startup()` and `journeys()`. |
| 2 | exit 3, files restored byte for byte | 2 min 02 s | `journeys` failed: `perf anchor 'screen.tuner' not found within 10000 ms` — the microphone permission dialog (`GrantPermissionsActivity`) covered the tuner. AGP's connected-test task installs the app itself and uninstalls it afterwards; run 1 had inherited the pipeline's earlier install, which carried `pm grant RECORD_AUDIO`. The generator now grants `RECORD_AUDIO` in a `@Before` through UiAutomator's shell command. The failed run also exercised the restore path: `baseline-prof.txt` came back identical. |
| 3 | exit 0 | **3 min 00 s** (SC-003 ≤ 15 min) | `baseline-prof.txt` 21 244 rules / 2 284 586 bytes (2 781 own rules, 13 196 `Landroidx/compose/`); `startup-prof.txt` 14 610 rules / 1 474 576 bytes; all six coverage labels found; the startup profile names `TembraApplication`, `MainActivity`, `NavigationShellKt`, `HomeScreenKt` and not the tuner or metronome screens. |

Also found: `profile generate` now clears the device's `additional_test_output` directory before the task (a round
does the same per class) so stale dated files of an earlier run are never merged.

`./gradlew checkAndroidHost --warning-mode all` with the committed profile: green, zero warnings (T020).
