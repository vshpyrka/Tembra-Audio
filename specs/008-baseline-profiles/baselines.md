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

## T021 — negative paths of quickstart §2 on the Pixel 6 Pro

| Case | Outcome |
|---|---|
| no `--device`, three devices attached (Pixel, iPhone 14, iPhone Duo simulator) | exit 4: `3 devices attached; name one with --device: …` |
| `--device iphone14` | exit 4: `profile generation is Android-only; iPhone 14 (…) is ios` |
| screen off (`KEYCODE_SLEEP`, `mWakefulness=Dozing`, keyguard showing) | the doctor wakes the screen and `wm dismiss-keyguard` unlocks it (the Pixel has no lock credential), so the command proceeds exactly as a round would; the "locked → 2" refusal needs a PIN-protected device and is covered by the fake-runner test (`test_a_failed_doctor_check_refuses_before_any_gradle_command`) |
| `Anchors.TUNER_ROOT` renamed to `screen.tuner.broken` | exit 3 in 1 min 33 s (`generateBaselineProfile did not complete; committed profile files restored`); `git diff --stat -- androidApp/src/main/generated` empty; rename reverted |
| second generation on the unchanged build (4 min 05 s) | `baseline-prof.txt`: 21 203 rules common, 41 only committed / 45 only regenerated (0.2 %; 14 own-code rules, timing-reached animation and draw helpers); `startup-prof.txt`: 14 503 common, 107 / 113 (0.75 %); the six descriptors present in both; discarded as noise-only (not committed), as the rule says |

## T025 — the shrunk measurement build (R8 on `release`, inherited by `perf`)

`./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf --warning-mode all`: BUILD
SUCCESSFUL in 19 s, **zero** lines matching `warning` in the log (compiler and R8), no keep rule needed so far
(`proguard-rules.pro` holds only its policy header).

| Measure | Value |
|---|---|
| `androidApp-perf.apk` | **6 966 412 bytes** (vs 14 922 467 unshrunk: −53 %) |
| `assets/dexopt/baseline.prof` | **8 443 bytes = 0.12 %** of the APK (FR-013 budget 3 %); `baseline.profm` 538 bytes |
| profile rules through the pipeline | 24 274 merged (`mergePerfArtProfile`: the committed 21 244 plus the libraries' own) → 50 388 after wildcard expansion → **12 550 rewritten by R8** to obfuscated names (`minifyPerfWithR8`), 31 of them still naming `tembra` (kept names) → compiled to the 8 443-byte binary |
| startup profile (FR-005) | `mergePerfStartupProfile` **executed** (not skipped, not NO-SOURCE), merged output `intermediates/merged_startup_profile/perf/…/startup-prof.txt` = **14 609 rules**; `minifyPerfWithR8` ran after it |
| DEX layout | `classes.dex` 1 363 376 bytes, 1 559 classes, 10 427 method ids, holds `MainActivity`; `classes2.dex` 1 268 068 bytes, 1 577 classes, 9 819 method ids, no `MainActivity` |
| `resources.arsc` | 130 724 bytes (resource shrinking on) |

## T031 — the debug build after shrinking (2026-10-12)

`./gradlew :androidApp:assembleDebug --dry-run --warning-mode all --no-configuration-cache`: zero lines matching
`artprofile|baselineprofile|minify|r8|checkBaselineProfileCommitted`, zero deprecations (the first dry run
surfaced "`val name by registering { }` … deprecated, removed in Gradle 10" from the new check task; it is
`tasks.register("checkBaselineProfileCommitted")` now).

| Measure | Runs | Median |
|---|---|---|
| `:androidApp:assembleDebug`, up to date (a round was polling the Pixel over adb at the time) | 0.652 / 0.499 / 0.471 s | 0.50 s — re-measured quietly in T046 |
| `:androidApp:assembleRelease --warning-mode all`, R8 (recorded, not budgeted) | one run | **15.6 s**, zero warnings; `androidApp-release-unsigned.apk` 6 952 244 bytes with `assets/dexopt/baseline.prof` 8 436 bytes, `classes.dex` 1 357 464, `classes2.dex` 1 263 188 |

## T027 — one round on the shrunk `perf` build (Pixel 6 Pro, 2026-10-12)

`uv run tracepulse run --device pixel6 --label baseline-profiles-shrink-check --no-publish --no-build --apk … --test-apk …`:
exit 0, status **succeeded**, all four journeys, 20 iterations, no quality badge, 13 min 35 s wall (14.4 min before),
app size 6 966 412 bytes. No keep rule was needed: `proguard-rules.pro` still holds only its policy header.

| Series (round median) | last five published rounds | shrunk build | change |
|---|---|---|---|
| startup/startup.ttid_ms | 500.1 ms | **176.7 ms** | −64.7 % |
| tuner/journey.duration_ms | 1355 ms | 1385 ms | +2.2 % |
| tuner/section.tuner.load.sum_ms | 71.5 | 71.2 | −0.5 % |
| tuner/section.tuner.modeSwitch.sum_ms | 205.5 | 205.3 | −0.1 % |
| tuner/frames.p50_ms | 7.34 | 7.01 | −4.5 % |
| tuner/memory.peak_kb | 88 786 | 85 036 | −4.2 % |
| metronome/journey.duration_ms | 1834 | 1805 | −1.6 % |
| stress/journey.duration_ms | 4408 | 4468 | +1.4 % |
| stress/frames.p99_ms | 24.6 | 21.9 | −11.1 % |
| stress/memory.peak_kb | 100 962 | 99 076 | −1.9 % |
| stress/section.tuner.modeSwitch.sum_ms | 278.4 | 457.4 | **+64.3 % — gate flag, see below** |

The one gate flag is not a slowdown: that section is **bimodal in both builds**. Per iteration the six switches cost
either ≈275 ms or ≈478 ms (published round: 471, 278, 282, 276, 272, 479, 483, …; shrunk: 479, 281, 478, 286, 479,
278, …) — the two mode values are identical before and after shrinking; the published round had 6 of 20 iterations
in the slow mode (median in the fast mode, 278), this round 11 of 20 (median in the slow mode, 457). The same
section in the tuner journey is unchanged (205 ms). It is a finding to watch in the labelled session after the merge
(SC-008): a persistent shift of the slow-mode frequency would be a real change, one round cannot say.

## T028 — screenshots of every primary destination, shrunk `perf` build vs debug build (Pixel 6 Pro)

`specs/008-baseline-profiles/screenshots/<width>-<destination>-<build>.png`, 14 pairs. Compact = portrait (412 dp
wide, bottom bar); expanded = the same phone in landscape (≈891 dp wide, the expanded window class: left rail,
two panes) because the fold/tablet AVD of feature 005 no longer exists on this Mac and the layouts are driven by
window size, not device type. Effects and Pads are not navigation destinations yet (the enum held Home, Timeline,
Tuner, Metronome, Projects, Profile at the time; Home was removed on 2026-10-17), so the set is: home, timeline, tuner-autoListen, tuner-byEar-tone (string VI
auditioned), metronome-running-settings (running, settings open), projects, profile.

**Retaken 2026-10-16** (the shell lost its top bar): all 28 images on the Pixel 6 Pro, 28 retaken, 0 left. The `expanded-*` ones were this Pixel held sideways at native density, as before. Debug and perf pairs differ only by
the perf build's 8 dp "write engine health" dot at the top end corner (it replaced the waveform icon in the old top bar)
and the live tuner meter; the By Ear pairs now show string VI "Playing" and the "TONE OUT" pill.

Pixel difference per pair (status bar excluded, threshold 40/255): every pair ≤ 0.13 % except the two Auto Listen
pairs at 0.81 % / 0.96 % — the live meter reacting to room noise. Nothing missing in the shrunk build: text, icons,
fonts, the audition tone pill, the metronome's settings and sound name, the rail. (A first Auto Listen capture of the
debug build showed By Ear: the tuner remembers its last mode across the two installs, which share the package's
data; the script now selects Auto Listen explicitly.)

### T028 screenshots, retaken 2026-10-17: icon-only navigation, no Home

Home was removed on 2026-10-17 (the app opens on Timeline; destinations Timeline, Tuner, Metronome, Projects, Profile)
and the navigation items show icons only (Material 3 Expressive bottom bar on compact windows, a collapsed 96 dp rail
on wide ones). The set is now 12 pairs: timeline, tuner-autoListen, tuner-byEar-tone, metronome-running-settings,
projects, profile, each compact (portrait, bottom bar) and expanded (the Pixel held sideways at native density,
≈891 dp, rail). Target: Pixel 6 Pro `19021FDEE001AH` (Android 17). Builds of that morning from
`worktree-nav-icons-only`, each in its own Gradle call through the gradle-run wrapper: `:androidApp:assembleDebug`
and `-Ptembra.perf=true :androidApp:assemblePerf` (shrunk). The debug APK was installed first, the perf APK over it
with `adb install -r -t` (same package and data), RECORD_AUDIO and POST_NOTIFICATIONS granted with `pm grant` after
each install, and the debug build reinstalled at the end. One script made both sets: cold launch (Timeline), Projects,
Profile, Tuner → By Ear → string VI's Audition (captured "Playing", then stopped), Auto Listen tapped explicitly (four
frames a second apart on the real microphone, under the speaker lock; all at rest, one kept), Metronome → Play
(captured running, then stopped); first in portrait, then with `wm user-rotation lock 1`.

| File | How | Differs beyond the navigation |
|---|---|---|
| `compact-home-debug.png`, `compact-home-perf.png`, `expanded-home-debug.png`, `expanded-home-perf.png` | **removed** (`git rm`): Home no longer exists, so these states cannot be retaken | — |
| `compact-{timeline,tuner-autoListen,tuner-byEar-tone,metronome-running-settings,projects,profile}-{debug,perf}.png` (12) | retaken as above, portrait | no: the content matches the 2026-10-16 images; only the bar differs (and which beat cell is lit in the running metronome) |
| `expanded-{…}-{debug,perf}.png` (12) | retaken as above, rotation 1 | the collapsed rail is wider than the old labelled rail, so the content starts further right and the cards are slightly narrower; otherwise no |

Debug against perf, per pair (status bar excluded, threshold 40/255): 0.02 % for eleven pairs (the perf build's 8 dp
"write engine health" dot at the top end corner) and 0.19 % for `expanded-metronome-running-settings` (the running
metronome's beat light caught at a different moment). The two Auto Listen pairs no longer differ beyond the dot (both meters at rest in a quiet room).
Nothing is missing in the shrunk build: text, icons, fonts, the TONE OUT pill and "Playing", the metronome's settings
and sound name, the bar and the rail.

## T030 — `uv run tracepulse profile verify --device pixel6` (Pixel 6 Pro, 2026-10-12)

Run 1 (builds `assemblePerf` itself, up to date): `profile present (8443 bytes, 0.1 %); recognised (result 1);
optimised (speed-profile)`, exit 0, **10.1 s** wall for the whole command (SC-005 ≤ 2 min). Run 2: identical, exit 0.
A result of 2 ("already installed") was expected on the repeat and did not appear: `verify` reinstalls with
`adb install -r` by contract, which renews the package's update time, and the receiver's `INSTALL_PROFILE` path
forces the write anyway (a manual repeat of the broadcast within one install also answered 1), so 2 can only come
from the app's own first-launch write, which the broadcast does not observe. 1 and 2 both pass, as the contract
says. `dumpsys package dexopt` after the forced pass: `arm64: [status=speed-profile] [reason=cmdline]`.

## T042/T046 — lane and default-build timings after the feature (SC-006, SC-007; 2026-10-12)

Negative lane check (quickstart §6): with `home` pointing at `Laudio/tembra/ui/home/MissingScreenKt;`,
`./gradlew checkAndroidHost` fails with `baseline profile does not cover: home (Laudio/tembra/ui/home/MissingScreenKt;)`;
restored, it passes again and `testDebugUnitTest` re-executes (it had come FROM-CACHE before the rule file and the
profile were declared as its inputs, commit 96067b3).

| Measure | Before | After | Change |
|---|---|---|---|
| `checkAndroidHost --rerun-tasks --warning-mode all`, steady-state second run (feature 007's lane protocol, ±15 % spread) | **4.35 s** (main c71a190, detached worktree; 11.2 s cold) | **4.46 s** (5.17 s first) | **+2.4 %** |
| `checkAndroidHost`, every host test task `--rerun` (the app's test included after) | 0.93 s (T001) | 1.011 / 0.993 s → 1.00 s | +7.5 % (70 ms: one more JVM test task executes) |
| `checkAndroidHost`, up to date | 0.41 s (T001) | 0.524 / 0.456 / 0.488 s → 0.49 s | +19 % (78 ms: 104 instead of 53 tasks to check — the app module's debug compile chain feeds its unit test; no native task joins the lane) |
| `:androidApp:assembleDebug`, up to date | 0.423 s (T001); 0.447 s same-daemon A/B (T008) | 0.594 / 0.444 / 0.458 s → 0.46 s | +2.5 % vs the same-daemon before; the debug task graph is unchanged (dry run) |
| `:androidApp:assembleRelease --warning-mode all` (R8; recorded, not budgeted) | — | 15.6 s, zero warnings | — |

Under the project's lane protocol the host lane is within the 5 % budget (+2.4 %), so the JVM coverage test stays
(no D8 fallback). The up-to-date figures are sub-second task-graph checks; their relative change is the new
module in the lane, not test work.

Lanes after the feature: `checkAndroidHost checkIos --warning-mode all` green in 39 s with zero warnings;
`engineHostTest` green (up to date, unchanged engine); `uv run pytest` 346 passed, 1 skipped;
`git diff --stat main...HEAD -- app/iosApp app/shared` empty (FR-014).

## T048 — quickstart on a clean checkout (detached worktree of 1b073c8, fresh `uv sync`, Gradle daemon stopped first; 2026-10-12)

| Step | Outcome |
|---|---|
| §1 `uv run pytest` | 347 passed, 1 skipped (31 s on the fresh venv) |
| §1 `:androidApp:testDebugUnitTest --tests "*BaselineProfileCoverageTest"` | green, zero warnings, fresh daemon (8.4 s) |
| §2 `profile generate --device pixel6` | exit 0 in 3 min 46 s; 21 223 / 14 610 rules; six labels; the regenerated files differ by noise only and were restored, not committed |
| §3 shrunk build | zero warnings; `assets/dexopt/baseline.prof` 8 443 bytes, `classes.dex` 1 363 376, `classes2.dex` 1 268 068 — the same bytes as the main checkout's APK (the APK itself differs by 1.1 KB of packaging metadata) |
| §3 gate script, debug dry run | `assembleRelease` refuses without the profile, `assembleNonMinifiedRelease` builds; the debug dry run lists no profile, minify, R8 or check task |
| §3 round, §5 comparison | **not repeated** on the clean checkout: identical commit and identical DEX/profile bytes; run from this checkout once (round) and three times (comparison), recorded above |
| §4 `profile verify --device pixel6` | present (8443 bytes, 0.1 %); recognised (result 1); optimised (speed-profile); 10.2 s |
| §6 negative lane check | fails naming `home (Laudio/tembra/ui/home/MissingScreenKt;)`, passes once restored |
| §7 `checkAndroidHost checkIos --warning-mode all` | green in 40 s, zero warnings |
| §7 `engineHostTest`, `assembleRelease --warning-mode all` | 137/137 Catch2 cases, release built under R8, zero warnings |

No quickstart step needed a correction; the worktree was removed afterwards.

## T049 — the labelled session after the merge (`main` at bda88af, Pixel 6 Pro, 2026-10-13)

`uv run tracepulse run --device pixel6 --label baseline-profiles --rounds 5 --no-build --apk … --test-apk …`
(artifacts built through `gradle-run`, zero warnings), then `sync`, `analyze`, `report --open`. Five rounds
`20261013T064938Z` … `20261013T074443Z_baseline-profiles`, every one `succeeded`, no badge, network none,
battery 100 %, thermal 21.6 → 25.3 °C, all five published to `perf-history` (`records/19021FDEE001AH/`); the
history worktree is clean afterwards. Exit 0. **The Pixel 7 Pro was not attached, so its half of the session
is still owed**; the iPhone 14 is not part of this feature (the iOS app is untouched, FR-014).

| SC-008 criterion | Outcome |
|---|---|
| no interactive step | none (the runner answers nothing; the device stayed awake and unlocked) |
| same device time within 5 % | 13.57 min mean per round (13.37–14.03) vs 14.21 min for the six `007-performance-pipeline` rounds of 2026-10-12: **−4.6 %** |
| no family flagged above its gate limit in the worse direction | one flag in round 3 (`20261013T071710Z`): `stress/section.tuner.modeSwitch.sum_ms` +35 % (limit 20 %) — the bimodal section (T027): 10 of 20 iterations in the slow mode (≈ 470 ms) against 2–9 in every other round of both labels, both modes unchanged (fast 266–292 ms, slow 462–494 ms); rounds 1, 2, 4, 5 within limits on every series. Not a regression; see below |
| every onset at the label boundary is an improvement | 10 onsets at rounds 1–2, all `down`: `app.size_bytes` −53.3 % (IMPROVED), `startup.ttid_ms`/`ttfd_ms` −63.7 %, `tuner/frames.p99_ms` −12.9 %, `metronome/frames.p99_ms` −16.1 %, `stress/frames.p99_ms` −14.9 %, `stress/frames.p90_ms` −6.4 %, `stress/frames.overrun_p90_ms` (−1.4 → −3.6 ms), `tuner/frames.p50_ms` −8.2 %, `metronome/memory.end_kb` −8.0 % — all PRELIMINARY until the detector's later rounds confirm them; no onset in the worse direction |
| `app.size_bytes` lower than the previous label | 14 922 467 → **6 966 412 bytes** |

Medians over the five rounds against the six 007 rounds: cold start 500.8 → 178.3 ms; tuner journey 1354 → 1380 ms,
metronome journey 1829 → 1828 ms, stress journey 4419 → 4535 ms (within the journeys' noise, not detected);
`tuner.load` 71.8 → 71.4 ms; `tuner.modeSwitch` 205.3 → 205.5 ms (two switches); frames p99 21.3 → 18.7 ms
(tuner), 21.1 → 18.8 ms (metronome), 24.7 → 21.9 ms (stress); peak RSS 88.5 → 85.4 MB (tuner), 100.6 → 99.7 MB
(stress); `audio.xruns` 0 in both. The report is `app/perf/reports/report.html` (rebuilt from the synced history;
not committed, like every report).

The flagged section: the stress journey's six mode switches alternate between two settled costs (≈ 275 ms and
≈ 475 ms per switch); the gate compares medians, so a round whose iterations split evenly between the modes
reports the mid-point (378 ms) as a +35 % step. The section's iteration values of round 3 —
`469 267 470 275 482 465 464 278 475 276 464 274 474 275 283 477 476 292 273 266` — show no value outside
either mode. Whether the slow mode is the engine's capture restart or a Compose recomposition is a question for
the tuner's next feature, not for the profile: it was present in every round before this feature (T027, the 007
session) and its two values did not move.
