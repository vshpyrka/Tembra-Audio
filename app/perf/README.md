# Tembra Audio performance pipeline (`tracepulse`)

Rounds of performance measurements on real devices, a device-keyed history on the `perf-history`
branch, the step-fit detector with statistical confirmation, an informational gate and one
self-contained report for both platforms. Feature 007 (`specs/007-performance-pipeline/`); the
decisions are in its `research.md`, the contracts in `contracts/`.

## Provenance

Adopted on 2026-10-08 from the owner's trace-pulse (`/Volumes/External/Dev/trace-pulse`, commit
`34a84d801f3844c3baff611f2b6d087f5d4eac7f`, working tree with 29 uncommitted edits), `cli/tracepulse/`
and `cli/tests/`. Dropped: the `init` scaffolder and the NIA journey configuration. Kept unchanged: the
Skia step-fit detector, the Mann-Whitney confirmation with Benjamini-Hochberg, the status ratchet, the
test vectors. Extended with: the metric catalogue, round records and the history branch, the
device-keyed store, iOS collection, the gate, sessions, the device-grid report.

## Install

```
cd app/perf && uv sync
```

Python 3.12 through `uv` (`requires-python >=3.9,<3.14`, so the 2015 MacBook Pro's system Python
works too); dependencies PyYAML, scipy (`>=1.13.1,<2`, with `tests/test_mannwhitney_pin.py` pinning
the exact p-values trace-pulse validated under 1.13.1), perfetto 0.57.2 (its trace-processor binary
downloads on first use), Jinja2, jsonschema. None of it ships in the app.

## Device setup

- **Android (Pixel 6 Pro / 7 Pro)**: USB, `adb devices` shows `device`, Developer options on, stay
  awake while charging, manual brightness. The round itself pins the display to 60 Hz, turns airplane
  mode on, wakes the screen and dismisses a credential-less keyguard, keeps the screen on, and restores
  the first two when it ends. A PIN-locked phone is refused (unlock it first). Checks and limits:
  battery ≥ 50 %, temperature ≤ 35 °C (per-device override `battery_temp_max_c`, 40 °C on the Pixel
  6 Pro), ≥ 2 GiB free, JDK 21, Android SDK.
- **iOS (iPhone 14)**: USB to the Apple Silicon Mac, paired, Developer Mode on, unlocked and awake. The
  first run installs two apps (the measurement build and the `TembraPerfTests` runner); the runner's
  profile must be trusted once on the phone (Settings → General → VPN & Device Management) or the round
  is refused with "not been explicitly trusted" (exit 2). A trusted phone denies a runner launch the
  same way for minutes at a time (2026-10-12: the same products denied at 11:37, launched at 11:41;
  denied at 12:14 and 12:15, launched at 12:21), so a round retries the measured run twice, two
  minutes apart, before it refuses; the denied attempts' logs stay under the round as
  `xcodebuild-test-attempt<n>.log`. The selected Xcode's iOS SDK must be at
  least as new as the phone's iOS: Xcode 26.6 (SDK 26.5) installs and launches the app on an iOS
  27.0.1 phone through `devicectl` but every XCUITest launch dies with CoreDevice error 10004 ("the
  process identifier of the launched application could not be determined"); `doctor` refuses the
  pairing (`xcode_supports_device`) and the fix is `DEVELOPER_DIR=/Applications/Xcode-27.1-beta.app/
  Contents/Developer uv run tracepulse run …` (the Xcode version lands in the round's fingerprint).
  Thermal state (must be `nominal`) and battery (≥ 50 %) are checked inside the runner, because the
  host cannot read them; the network state is recorded on the round and never refused (turn Wi-Fi off
  before a session anyway).
- **Simulators**: every booted iOS simulator can run the same journeys (`--simulators`); their series
  are marked `simulator` and non-reference, gates stay inactive, and they produce no frames family.
  Android emulators are listed but discouraged (Google: "strongly discouraged" for benchmarks).

## Commands

All from `app/perf/` (or `uv run --project app/perf tracepulse …`). Non-interactive; configuration in
`app/tracepulse.yml` (unknown keys are an error).

```
uv run tracepulse devices                               # attached (adb, devicectl, booted simctl) + registry; marks new ones
uv run tracepulse doctor --device pixel6                # protocol checks only; exit 0 or 2
uv run tracepulse run --device pixel6 --label <feature> [--rounds 5] [--journeys startup,tuner] [--iterations 20]
                                                        # one round (or a session): doctor → build → install → warm-up →
                                                        # journeys → collect → record → publish → sync → analyze → report
uv run tracepulse run --device pixel6 --label <feature> --no-build --apk <app.apk> --test-apk <benchmarks.apk>
                                                        # reuse artifacts built elsewhere (an agent builds through the gradle-run wrapper)
uv run tracepulse run --device iphone14 --label <feature> [--size] [--no-build --xctestrun <path>]
uv run tracepulse run --simulators --label <feature>    # every booted simulator, sequentially
uv run tracepulse run --device pixel6 --label release-baseline --build-type release      # SC-007 comparison (Android)
uv run tracepulse run --device iphone14 --label release-baseline --configuration Release # SC-007 comparison (iOS)
uv run tracepulse size --device iphone14               # archive + thinned export; cached per commit (minutes)
uv run tracepulse publish [--device <id>]              # push records a failed push parked under data/unpublished/
uv run tracepulse sync                                 # pull perf-history, rebuild data/tracepulse.db
uv run tracepulse analyze                              # detections (ratchet) + gate decisions over the rebuilt history
uv run tracepulse report [--open] [--out <file>]       # reports/report.html, self-contained
uv run tracepulse status [--series [--label <label>] [--last N]]  # cache, unpublished records, branch state (+ CoV per series: one label, last N rounds)
uv run tracepulse inject R1 | revert R1 | revert --all  # the regression-injection self-test
uv run tracepulse profile generate --device pixel6      # baseline + startup profile from the journeys into androidApp/src/main/generated/baselineProfiles/
uv run tracepulse profile compare --device pixel6 [--with-full] [--journeys …] [--iterations N] [--no-build --apk … --test-apk …]
                                                        # one build, no profile vs shipped profile (+ fully optimised): data/comparisons/<id>/comparison.md
uv run tracepulse profile verify --device pixel6 [--apk …] [--build-type perf|release]
                                                        # the installed build carries, recognises and is compiled from its profile
```

`--json` on any command prints one JSON object on stdout (progress lines go to stderr) for a future CI job.

`--build-type release` on Android is the `perf` Gradle build type (release optimisations, profileable,
debug signing — what Macrobenchmark can install and trace) built with `-Ptembra.perf=release`, which
compiles the instrumentation out (`PerfBuild.PERF_ENABLED` false, no `TEMBRA_TRACING`); the plain
`release` type is unsigned and not profileable, so it cannot be measured. The round's record says
`build_type: release`. On iOS `--configuration Release` is the Release configuration itself (its
Gradle phase passes no `tembra.perf`), and the stress journey skips the absent "write engine health"
control.

### Exit statuses

| Code | Meaning |
|---|---|
| 0 | success — gate flags never change it |
| 2 | refused: a device check failed before measuring; nothing stored or published |
| 3 | round failed: measurement started and did not complete; a `failed` record (no samples) is published |
| 4 | usage or configuration error (unknown device, two devices and none named, `--label` missing, unknown key) |
| 5 | publish failed: the record is kept under `data/unpublished/`; `tracepulse publish` retries |

### Where things live

| What | Where | In git? |
|---|---|---|
| Round records (source of truth) | branch `perf-history`, `records/<device_id>/<round_id>.json` | yes, append-only; never rebase or rewrite it |
| Worktree of that branch | `app/perf/.history/` | no |
| Derived cache | `app/perf/data/tracepulse.db` (rebuilt by `sync`) | no |
| Raw artifacts (traces, result bundles, logs) | `app/perf/data/rounds/<round_id>/` (~110 MB per Android round) | no — they stay on the producing machine |
| Session builds | `app/perf/data/sessions/<id>/` | no |
| Rendered report | `app/perf/reports/report.html` | no |
| Per-device lock | `app/perf/data/locks/<device_id>` | no |

The Android measurement build is `./gradlew --console=plain -Ptembra.perf=true :androidApp:assemblePerf
:perf-benchmarks:assemblePerf` (log kept under the round or session, never printed in full); the iOS one is
`xcodebuild build-for-testing -scheme TembraPerf -configuration Perf`.

## What a round measures

Four journeys on both platforms, same names: `startup` (cold), `tuner` (open, note readout, By Ear and
back), `metronome` (start, tempo up ×5, down ×5, stop), `stress` (metronome running while the tuner
listens; ends by writing the engine's audio-health counters). 20 iterations each. The catalogue
(`tracepulse/catalogue.py`, contract `metric-catalogue.md`) names every series per platform with its unit,
source, gate default and screening gate; families marked non-comparable (startup, frames, memory,
cpu) never share an axis across platforms; sections and audio health are the same instrumentation
on both.

What each platform can and cannot check: Android enforces the whole device protocol from the host
(battery, temperature, 60 Hz, airplane mode, awake, unlocked); iOS checks thermal state and battery
inside the runner, cannot pin the refresh rate or set airplane mode, and records the network state
instead of refusing. Android frames are per-frame percentiles (Macrobenchmark); iOS frames are
hitch-based (`XCTOSSignpostMetric` animation intervals) and exist only while the interval holds
frames: the metronome's `tempoChange` is a 0.1 ms state update with no drawn animation on iOS, so its
hitch columns report zero frames in every iteration and the collector drops the family with a warning
instead of publishing a 60 fps "deficit" (the first phone round, `20261011T165432Z`, carries that
artefact; records are never rewritten). The family returns when a journey's interval spans a real
animation. `startup.ttfd_ms` is app-declared on Android (`ReportFirstContentDrawn`) and OS-defined on
iOS. An iOS journey's `journey.duration_ms` is mostly XCUITest's own cost (about a second per element
query on a Compose tree), which is why the metronome journey reads 22 s on the phone against 1.9 s on
the Pixel; it is stable, and comparable only with itself. End-of-journey memory (`memory.end_kb`) is
resident anonymous memory on Android (`MemoryUsageMetric(Last, RssAnon)`) and the physical-memory
delta on iOS. It was the ART heap size until 2026-10-11: the noise baseline showed that value swinging
9–46 MB inside a round and 4–16 % between round medians — it measures when GC last ran, not the app —
so the catalogue entry carries `since: 2026-10-11T20:10:00Z` and the cache loads the Pixel's series from
there (the records keep the old values; a `since` is how a source change avoids showing as a step).

A record's `build.type` names the build measured (`perf` / `release`; `Perf` / `Release` on iOS). Only
the measurement build compiles the tracing facade in, so a `release-baseline` round publishes no
`section.*` series: Macrobenchmark reports an absent trace section as 0, and the first two release
rounds on the Pixel (`20261011T204339Z`, `20261011T205838Z`, before the type was recorded) carry those
zeros in their records; the cache ignores the sections of a record without a type in which not one
section was hit, so they never reach a chart or a gate baseline (they did once: the +65 % stress
`modeSwitch` flag on the first round of the second noise session was measured against them).

On a phone a round ends with the audio-trace pass: the app is launched with `devicectl`, `xctrace
record --instrument 'Audio Client' --instrument 'Audio Server' --all-processes` records the HAL
system-wide for 75 s (never `--launch`, which hung in spike 3), and `AudioTracePass` drives one
unmeasured stress journey on the running process; `audio.trace`, `audio-ioproc.xml` and
`audio-io-cycle-client.xml` land under the round and `audio.ioproc_p99_us` (advisory) joins the
stress journey. The measured run skips that class. System-wide rather than `--attach <pid>`, the
spike-3 design: the Xcode 27.1 beta's xctrace finds no process on the iOS 27.0.1 phone by pid or by
name ("Cannot find process for provided pid", every R1 round of 2026-10-12), and an attached
recording with Xcode 26.6's xctrace (which does attach) reaches only the first 10 s of the HAL's
stream. Neither fills the client-side `audio-hal-client-io-cycle-ioproc` table under the real
journey, so the series comes from the HAL server's `audio-hal-io-cycle-client` view of the same
interval (the client I/O-proc as audiomxd times it, ~100 cycles/s). That table names no client on
iOS 27 ("Unknown"), so the pass trusts that the app is the phone's only HAL client — the phone is
otherwise idle during a round. The two audio instruments alone make 35 MB of 75 s; the
`Audio System Trace` template's kernel instruments made 0.5 GB and a full disk refused to save it.
The first rounds with the pass in this shape (`20261012T093131Z` and `20261012T101350Z`, the last two
R1 rounds): `AudioTracePass` passed under the recording both times, 37.4 and 36.5 MB traces, an empty
client table beside 10 230 and 8 452 server cycles, cycle medians 772 µs and p99 1 547 and 1 976 µs
(max 3.2 ms). The HAL cycles every 5.0 ms on this phone (240 frames at 48 kHz, from the table's
`start` column), so the I/O proc takes 15 % of its budget typically and 30–40 % at the p99 while the
stress journey runs the tuner and the metronome together. Two values make no baseline yet: the gate
reports the series `inactive` until five clean rounds carry it.
Until 2026-10-12 every `devicectl … process
launch` on the phone was refused — `--device` came after the bundle id, where devicectl reads
arguments for the app — and `process terminate` was given `--pid-or-name`, which this devicectl does
not have; both failed silently, so no phone round before the R1 session had a warm-up launch (the
startup median of 20 launches did not notice) and the first R1 round's audio-trace pass could not
start. `--device` now precedes the positionals and the warm-up terminates the pid devicectl reported.

Known Macrobenchmark behaviour (seen on the Pixel, round `20261008T175034Z`): single metrics (memory,
trace sections, frame count) come back with fewer runs than iterations (15 of 20) while the sampled
frame metrics keep all 20; Macrobenchmark intersects single metrics per iteration and drops the
iterations where one is missing. Records keep each metric's real samples and declare the journey's
iteration count.

## Reading the report

`#/` is a grid of device cards (platform, model, `simulator` / `non-reference` badges, last round and
label, open gate flags, onsets in the last session, a one-line headline). A card opens
`#/device/<id>`: one chart per series grouped by family, median line with the interquartile band,
dashed label boundaries (the feature each round ran under), onset markers by status (confirmed ●,
suspect ○, first hint ◌, improved ▼, environment-suspect band), gate flags ◆ with the limit line of
their comparison window, fingerprint changes (OS version, network state) as dashed lines, one sentence
under each chart, the rounds table and "What changed". A chart's "open" goes to
`#/device/<id>/<series>`: the per-round distributions, the detection record and the catalogue note.
The report is one self-contained file (771 314 bytes at 32 rounds across three devices, about 24 kB per
round: per-round aggregates and decisions, never samples) that makes zero network requests and works
from `file://` with the network off (checked in Chrome's developer tools, quickstart §4). The 1 % gate
experiment on 2026-10-11 (`gate.limits_pct` journey.duration_ms and memory.peak_kb at 1 for one round,
`20261011T201444Z`) exited 0, printed `gate: 3 above limit — …` and put the three flags at the top of
the grid; the limits went back to the catalogue defaults afterwards.

The gate (`gate.py`): per metric, the change of the round's median against the median of the last N
clean rounds (N = `gate.clean_rounds`, 5) in the worse direction, against the catalogue limit
(overridable in `tracepulse.yml`). `above_limit` is a notice in the output and the report, never a
failure; `inactive` names its reason (fewer than N clean rounds, simulator series, disabled); improvements
are never flagged; a clean round succeeded without a `noisy`/`thermal` badge and outside an
environment-suspect group — a gate flag does not make a round unclean. A flag reads in its limit's own
terms: `+18 % (limit 15 %)` for a percentage limit, `+2.5 ms (limit 2 ms)` for an absolute one (frame
overrun, hitches, audio flags); the percentage is taken against the magnitude of the baseline, so a
rise from −0.5 ms to +2 ms is positive. The first live flag was exactly that: Pixel `noise-baseline`
round 8, stress `frames.overrun_p90_ms` +2.5 ms over its 2 ms limit, back within limits in round 9.
Two refinements came out of the noise sessions (catalogue `floor_abs`, `gate_scale_pct`): a
percentage limit on a section flags only a change of at least 1 ms (the stress journey's `tuner.load`
section is 0.03 ms on the iPhone and swung +22 % between rounds), and the absolute overrun limit grows
to 25 % of the baseline when the baseline is large (the tuner journey's overrun P90 is the mode-switch
frame, 48–59 ms across 23 Pixel rounds, which the 2 ms limit flagged every third round); the record's
`limit_abs` is the limit that applied, so the chart's limit line is still the real one. The iPhone
session added two more: the audio callback durations (2–17 µs in an engine that renders nothing yet;
`last_callback_us` swung +175 % between rounds) flag only a change of at least 100 µs, 2 % of the 5 333 µs
budget at 256 frames; and a percentage of a zero baseline was no limit at all — from 0 the gate could
never flag the first xrun — so from 0 any rise past the floor flags, in absolute terms. The detector's
screening gate honours the same floor: the iPhone card's headline had been "last audio callback duration
got 50 % slower (first hint)" for a 2 → 3 µs step.

## Adding a journey, a section, a counter

1. **Section**: add `<ENUM_NAME> <family> <family.camelName>` to `app/shared/engine/perf-catalogue.txt`
   and the entry to `PerfSection` (or `PerfCounter`); run
   `./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true` and commit the header;
   wrap the non-composable work (`PerfTrace.section(PerfSection.X) { … }`, `animation` for
   scroll/zoom, `TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_X)` in the engine's non-real-time code); list the
   name under the journey that exercises it in `perf-benchmarks/.../PerfMetrics.kt` and
   `iosApp/TembraPerfTests/Metrics.swift`. The parity test and the poison test keep enum, catalogue,
   header and the real-time path honest. Never from the audio callback.
2. **Anchor**: a `testTag` on the control, named in `PerfAnchors` (`:shared:ui`), mirrored in
   `perf-benchmarks/.../journeys/Anchors.kt` and `TembraPerfTests/Anchors.swift`.
3. **Journey**: a class in `perf-benchmarks/.../journeys/` + a benchmark class, a struct in
   `TembraPerfTests/Journeys/` + a test case, the name in `tracepulse.yml` `journeys:` and in
   `collect_android.BENCHMARK_CLASS_OF` / `collect_ios._TEST_JOURNEYS`. One journey definition, several
   registrations (feature 008, spec FR-002): the same journey class is also run by
   `BaselineProfileGenerator.journeys()` and gets its own test in `ProfileComparisonBenchmark` (named after
   the journey; add the method to `collect_android._COMPARISON_METHODS`). When the journey visits a new
   primary destination, append `<label> <class descriptor>` to `androidApp/baseline-profile-coverage.txt`
   and regenerate the profile in the same feature.

## Every feature instruments itself (FR-022, constitution)

Every feature after this one that adds a user-facing interaction or an engine path adds, **in the same
feature**, its performance instrumentation: catalogue sections for its main interactions, journey
anchors, a benchmark journey (new or an extension of an existing one) on both platforms, and any
audio-health counters it needs. Its specification declares which journeys and sections it adds; its
plan and tasks include them. After it merges it is measured with a labelled session on each available
device, and that session's report is part of the pull-request evidence:

```
uv run tracepulse run --device pixel6 --label <feature> --rounds 5
uv run tracepulse run --device iphone14 --label <feature> --rounds 5
ssh <mac2> 'cd …/app/perf && uv run tracepulse run --device <serial> --label <feature> --rounds 5'   # Android from the 2015 Mac
uv run tracepulse sync && uv run tracepulse report --open
```

A single round only attributes an onset to the feature; confirmation needs the detector's later rounds,
so sessions of five are the practice.

A feature that adds a primary destination, a journey or a main interaction also **regenerates the baseline
profile in the same feature** (`uv run tracepulse profile generate --device pixel6`, feature 008, spec FR-012)
and commits the two regenerated files; a feature that adds a destination the journeys visit appends it to
`androidApp/baseline-profile-coverage.txt` first, so the host lane's coverage check names it until the
profile covers it. Regeneration is never part of a round.

## Baseline profiles (feature 008)

A **baseline profile** is the list of the app's hot code paths (startup, the primary destinations, their
interactions) that ships inside the Android app as `assets/dexopt/baseline.prof`; ART compiles those paths
when the app is installed, so the first launches are fast instead of the tenth. The **startup profile** is the
subset recorded during cold start; R8 uses it to lay the startup classes out together in `classes.dex`. Both
are generated from the pipeline's journeys, committed as text, and shipped by every release-like build
(`release` and the `perf` measurement build), which are shrunk with R8 since this feature. The debug build
carries no profile and is not shrunk. iOS has no equivalent and is untouched.

| What | Where |
|---|---|
| the two profiles (generated, committed, never edited by hand) | `androidApp/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt` |
| the coverage rule the host lane checks (`BaselineProfileCoverageTest`) | `androidApp/baseline-profile-coverage.txt` — `<label> <class descriptor>` per line |
| the keep rules of the shrunk build, each with its reason | `androidApp/proguard-rules.pro` |
| the generator and the comparison class | `perf-benchmarks/.../BaselineProfileGenerator.kt`, `ProfileComparisonBenchmark.kt`, `CompareState.kt` |
| comparison outputs and generation logs | `app/perf/data/comparisons/<id>/`, `app/perf/data/profile-generate/<id>/` (git-ignored) |

The three commands share the registry, the device protocol (`doctor`), the measurement state of a round
(airplane mode, pinned refresh rate, screen on — restored afterwards) and the build step, and they **never**
write to the history, the records, the store or the report. Exit statuses: 0 done; 2 refused (a check
failed, nothing ran); 3 failed (started and did not complete; `generate` restores the committed files);
4 usage. A round never generates or evaluates a profile: rounds keep measuring the fully optimised state.

**Generate** — `uv run tracepulse profile generate --device pixel6` (about 3 minutes on the Pixel 6 Pro; needs
API 33+ on a non-rooted device). It backs the two files up, runs
`./gradlew -Ptembra.perf=true :androidApp:generateBaselineProfile` with `ANDROID_SERIAL` naming the device
(the plugin builds and installs the `nonMinifiedRelease` app, drives `BaselineProfileGenerator` until three
iterations add no new rule, merges, and writes the files), then runs the coverage test; on any failure the
backups come back byte for byte and the output names the failing step and the Gradle log. The generator
grants the microphone itself, and on Android 13+ notifications, which the metronome asks for on its first Play (the plugin's install grants neither, and AGP uninstalls the app afterwards); a round's install step grants both too.
**When to regenerate**: when the startup path, a primary destination, a journey or a main interaction
changed, and before a release. **When to commit** the regenerated files: when covered code changed; a
regeneration on an unchanged app differs only in order and in timing-reached rules (0.2–0.8 % of the lines
on 2026-10-12) and is not committed.

**Verify** — `uv run tracepulse profile verify --device pixel6` builds the `perf` APK (or takes `--apk`),
checks it carries `assets/dexopt/baseline.prof` and reports its share of the APK (budget 3 %, FR-013),
installs it, launches once, waits five seconds, and reports whether the device *recognised* the profile
(the installer's broadcast: result 1 installed, 2 already installed, 6 no profile in the APK) and, after the
forced optimisation pass, whether the app is *optimised* from it (`speed-profile`). The same as plain `adb`:

```
adb install -r -t androidApp/build/outputs/apk/perf/androidApp-perf.apk
adb shell am start -W -n audio.tembra.app/audio.tembra.android.MainActivity ; sleep 5
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n audio.tembra.app/androidx.profileinstaller.ProfileInstallReceiver
adb shell cmd package compile -r bg-dexopt audio.tembra.app
adb shell dumpsys package dexopt | grep -A2 audio.tembra.app        # expect status=speed-profile
```

`--build-type release` inspects the unsigned release APK only (no signing config, so nothing is installed).

**Compare** — `uv run tracepulse profile compare --device pixel6 [--with-full]` measures one `perf` build
in the states `none` (compilation reset: the worst case after a direct install) and `profile` (the shipped
profile compiled ahead of time), each from a fresh install, 20 iterations per journey, about 30 minutes
for the two; `--with-full` adds the fully optimised state the rounds measure as a reference. It writes
`data/comparisons/<id>/comparison.json` (schema `tracepulse/schema/comparison.schema.json`) and
`comparison.md`: one row per series (`<journey>/<metric>`) with the median per state, the change of
`profile` against `none` in percent, its direction (`better`/`worse`), whether a two-sided Mann–Whitney test
confirms it at p < 0.01, and with `full` the share of the none→full gap the profile closes. The headline is
the cold-start first frame: `startup.ttid_ms: <none> → <profile> ms (<change> %, confirmed)`. The command
exits 0 whatever the numbers say; a worse metric is a finding to record in the feature's evidence, not a
reason to change the comparison. `--no-build --apk … --test-apk …` reuses artifacts an agent built through
`gradle-run`.

**Keep rules** (`androidApp/proguard-rules.pro`): the shrunk build is accepted only after a round's four
journeys complete on it and every primary destination is screenshot-verified at compact and expanded widths
against the debug build. A rule is added only when such a run fails, with a comment naming the code path
and the symptom; never a `-dontwarn` wildcard, never shrinking turned off for a build. Zero R8 warnings is
part of the warning gate.

## Audio-thread health

`tembra_engine_audio_health` (C ABI revision 5) copies the counters the callback writes with relaxed
atomics: callbacks, last/max callback duration, over-budget count, xruns (Oboe's counter, sampled off
the callback), real-time status. `AudioHealthTicker` publishes them once per second through
`PerfTrace.counter` in measurement builds; on Android they become counter tracks in each stress
iteration's trace (`audio.*` series, one sample per iteration), on iOS the stress journey's last tap
writes `engine-health.json`, which the collector pulls from the app container. Zeros with the current
engine; the audio spine fills them.

## Lane times (SC-008)

Measured on 2026-10-11 on the Apple Silicon Mac through `gradle-run`, `--rerun-tasks --warning-mode all`,
configuration cache reused, main (`bc3a7d7`, before this feature, in a detached worktree) against this
branch, both with zero warnings; the second of two steady-state runs each, the first in brackets:

| Lane | Before | After | Tests |
|---|---|---|---|
| `engineHostTest` | 39 s (test time 32.3 s) | 35 s (test time 32.6 s) | 128 → 137 Catch2 cases |
| `checkAndroidHost` | 5.9 s (11.2 s) | 5.3 s (17.7 s with a cold configuration cache) | + perf facade tests |
| `checkIos` | 29.7 s (25.6 s) | 29.2 s (29.4 s) | + perf facade and anchor tests |

Run-to-run spread on this machine is about ±15 %; the feature's extra tests and the two generator tasks
(`generatePerfBuild`, `generateTraceNames`) do not show above it.

## Measurements so far (Pixel 6 Pro, 2026-10-08)

- A round takes 13 m 40 s of device time with `--no-build` (SC-001); the measurement build adds a few
  minutes the first time. Cold start TTID/TTFD median 241.6 ms; tuner journey 1.39 s; metronome
  journey 1.84 s; frames P50 7.2 ms (tuner), peak RSS 88 MB (tuner) / 109 MB (stress); APK 14.9 MB.
- With the instrumented build (round `20261008T175034Z`): `tuner.load` 70 ms, `tuner.modeSwitch` 206 ms
  for two switches, `metronome.tempoChange` 1.7 ms for ten steps, `tuner.frame` 17 frames per journey.
- Quality allowances calibrated on these rounds and the five below: `timeToFullDisplayMs` 0.45,
  `frameCount` 0.75, `journey.tunerSumMs` 0.25 (the tuner journey waits on anchors); section keys are
  outside the `noisy` badge (see `quality_flags`). The first eight Pixel rounds were published with the
  badge under the old rule and so never count as gate baselines; the `noise-baseline` session does.
  `memoryHeapSizeLastKb` carried 0.55 until the end-of-journey memory source changed (see "What a round
  measures").
- iOS: the facade self-check passed on the iPhone 17 simulator (20 measurements of `tuner.modeSwitch`,
  average 127 ms).

## Measurements so far (five-round session, Pixel 6 Pro, 2026-10-11)

`run --device pixel6 --label 007-performance-pipeline --rounds 5`: one build, five rounds of 14 m 30 s,
all succeeded, 20 iterations each, published as they finished (session `20261011T162444Z`). Round
medians across the five rounds, with their spread (coefficient of variation):

| Series | Medians | Spread |
|---|---|---|
| `startup/startup.ttid_ms` | 252.7, 254.3, 254.5, 252.9, 251.8 | 0.4 % |
| `tuner/journey.duration_ms` | 1423, 1422, 1410, 1384, 1389 | 1.1 % |
| `metronome/journey.duration_ms` | 1870, 1772, 1816, 1773, 1886 | 2.6 % |
| `stress/journey.duration_ms` | 4701, 4505, 4575, 4561, 4454 | 1.8 % |
| `tuner/frames.p50_ms` | 6.9, 7.2, 7.2, 7.2, 7.5 | 2.9 % |
| `tuner/memory.peak_kb` | 89 298, 88 864, 89 328, 85 876, 89 928 | 1.6 % |
| `tuner/section.tuner.load.sum_ms` | 72.4, 71.7, 72.3, 71.1, 71.3 | 0.7 % |
| `tuner/section.tuner.modeSwitch.sum_ms` | 207.5, 205.3, 208.3, 204.0, 204.5 | 0.8 % |
| `stress/audio.callbacks` | 152, 210, 201, 166, 156 | 13 % (how many callbacks fell inside the iteration) |
| `stress/audio.max_callback_us` | 14, 18, 12, 13, 17 | — (the slowest callback of the iteration; budget 5 333 µs at 256 frames) |

`audio.xruns` 0 and `audio.realtime` 1 in every iteration; the Android audio counters flow from the
engine through the Perfetto counter tracks (quickstart §6, Android half). APK 14 922 467 bytes in
every round. The SSH half of the cadence (quickstart §7) was not run: the second Mac is not
provisioned; the five-round session ran locally.

## Measurements so far (iPhone 14, iOS 27.0.1, Xcode 27.1, 2026-10-11)

The first complete phone round (`20261011T165432Z_007-performance-pipeline`) took 37 minutes: every
journey, 20 measurements per metric in the result bundle (spike 1 closed on the device), every anchor
found (spike 5 closed: Compose Multiplatform 1.12.1 maps `testTag` to the accessibility identifier on a
phone with the default `accessibilitySyncOptions`), thermal `nominal`, battery 100 %, Wi-Fi on
(recorded, not refused). Medians: cold launch to first frame 523 ms and to responsive 524 ms;
`tuner.load` 111 ms, `tuner.modeSwitch` 135 ms for two switches, `metronome.tempoChange` 0.12 ms for
ten steps; peak physical memory 131 MB in the stress journey and 125 MB in the metronome journey
(in the bundle; the `memory.*` series start with the next round, see the collector note above);
engine health at the end of the stress journey: 304 callbacks, slowest 17 µs, no over-budget
callbacks, no xruns, real-time thread. Journey durations (tuner 7.8 s, metronome 22.3 s, stress 21.8 s)
are XCUITest's element-query cost on the Compose tree, stable and comparable with themselves only.

Getting there took four rounds that are on the branch as failed records: two launch failures under
Xcode 26.6 (CoreDevice 10004; the phone runs iOS 27.0.1, `doctor` now refuses the pairing) and two
rounds lost to the microphone alert that iOS shows once per install (now answered by the runner before
anything is measured).

## Noise baseline (SC-003), both devices, 2026-10-11/12

Ten rounds of an unchanged build under the label `noise-baseline`, twice on the Pixel 6 Pro (session
`20261011T174045Z`, end-of-journey memory still the heap size; session `20261011T211432Z`, resident
anonymous memory) and once on the iPhone 14 (`20261011T174054Z`); all thirty rounds succeeded with
no badge, the Pixel at 32 °C and 100 % battery, the iPhone `nominal` at 100 %, both while the host
ran Gradle lanes and Xcode builds (neither device's series moved with host load). Spread = coefficient
of variation of the ten round medians (`uv run tracepulse --json status --series --label noise-baseline
--last 10`, the global `--json` before the command; the Pixel's first session is reconstructed from the
records because its heap-size values are no longer loaded). The threshold is 3 %.

| Series | Pixel, session 1 | Pixel, session 2 | iPhone |
|---|---|---|---|
| `startup.ttid_ms` | 248.4 ms, 0.75 % | 248.9 ms, 1.06 % | 517.5 ms, 0.36 % |
| `startup.ttfd_ms` | 248.4 ms, 0.75 % | 248.9 ms, 1.06 % | 528.4 ms, 0.56 % |
| `tuner/memory.peak_kb` | 90.5 MB, 1.59 % | 88.9 MB, 2.68 % | 128.0 MB, 2.09 % |
| `metronome/memory.peak_kb` | 98.8 MB, **3.17 %** | 97.5 MB, 2.82 % | 125.1 MB, 0.26 % |
| `stress/memory.peak_kb` | 107.6 MB, 1.71 % | 101.9 MB, 2.49 % | 131.1 MB, 1.47 % |
| `tuner/memory.end_kb` | heap 21.5 MB, **3.87 %** | 86.4 MB, 1.78 % | 125.6 MB, 2.47 % |
| `metronome/memory.end_kb` | heap 32.2 MB, **5.35 %** | 91.9 MB, 2.02 % | 121.4 MB, 0.54 % |
| `stress/memory.end_kb` | heap 26.4 MB, **16.30 %** | 94.4 MB, 1.24 % | 129.4 MB, 1.62 % |

The first Pixel session failed the threshold on every end-of-journey memory series and sat on it for
the metronome's peak: the heap size measures when ART last collected, not the app (see "What a round
measures"), which is why the source changed; with resident anonymous memory every `startup.*` and
`memory.*` series on both devices is under 3 % (worst 2.82 % on the Pixel, 2.47 % on the iPhone). The
journeys themselves: Pixel 1.1–2.6 %, iPhone 0.06–0.07 % (XCUITest's own cost dominates and is very
steady). The iPhone rounds carry no frames family: the only animation interval in the app today
(`metronome.tempoChange`) draws nothing, and the tuner's needle is a per-frame draw-lambda section
(`tuner.frame`), not an interval — the family appears when a feature wraps a drawn animation
(spec US3 scenario 3; a tuner follow-up, not a pipeline one).

What the gate flagged in thirty clean rounds, and what became of it: stress `section.tuner.load.sum_ms`
+22 % twice on the iPhone (a 0.03 ms section → the 1 ms floor); `metronome.tempoChange` +23 % once
(0.12 ms → same floor); `audio.last_callback_us` +175 % once (4 → 11 µs → the 100 µs floor); tuner
`frames.overrun_p90_ms` +2.7…+7.3 ms four times on the Pixel (the mode-switch frame at 48–59 ms → the
limit scales to 25 %); stress `section.tuner.modeSwitch.sum_ms` +28…+65 % five times on the Pixel. That
last one is real and stays: the stress journey's six mode switches sum to one of three plateaus —
280–294, 373–375 or 459–473 ms in all 26 measurement rounds — so the round median jumps by a plateau
depending on how many of the twenty iterations took the slow path; the tuner journey's two switches
(205 ms, 0.8 %) never do. Something in the switch costs about 90 ms more while the metronome is running; the gate
reports the jump truthfully and the tuner's owner has the series. The first flag of the second Pixel
session (+65 %) was also measured against the two release rounds' zero sections, which the cache now
ignores.

## Release baseline (SC-007), both devices, 2026-10-11/12

One round of the uninstrumented build under the label `release-baseline`, compared with the
measurement build's noise-baseline medians above. Pixel 6 Pro (`--build-type release`, rounds
`20261011T204339Z` and `20261011T205838Z`): cold start 238.8 and 241.8 ms against 248.4 / 248.9 ms,
the measurement build **+2.9…+4.2 %** slower depending on the pairing. iPhone 14
(`--configuration Release`, round `20261012T110450Z`, session `20261012T110213Z`): first frame 515.5 ms
and responsive 524.2 ms against 517.5 / 528.4 ms, the measurement build **+0.4 % / +0.8 %**; the rest
of the round reads like a Perf round (tuner journey 7.84 s, peak memory 127.9 / 125.6 / 131.1 MB,
I/O-proc p99 1 708 µs). Both inside the 5 % of SC-007. The Release records carry no `section.*`
series; the two Pixel rounds predate the `build.type` field (see "What a round measures"), the
iPhone's says `Release`. `tools/check-release-strings.sh` passed on 2026-10-11 (tree `264f440`), and the iOS Release
binary of round `20261012T110450Z` (tree `6155d43`, the catalogue unchanged since 2026-10-08) was
scanned again by hand with the script's `strings -a | grep -xF` loop: none of the 16 names. The
first iPhone Release round (`20261012T091328Z`) never measured — the transient trust denial below.

## The injection self-test on the phones (SC-004), 2026-10-12

`inject R1` (a 250 ms spin and a 512 KiB write at launch, `regressions/r1-startup.patch`) on a clean
tree, then `run --label 007-performance-pipeline` on both devices, then `revert R1`. Pixel 6 Pro, six
rounds (session `20261011T233930Z`): cold start 502.2, 497.6, 498.7, 503.9, 502.6, 500.1 ms against
the 248.9 ms baseline, the gate flagging `startup.ttid_ms`/`startup.ttfd_ms` +102 % on the first three
rounds (then the injected rounds became its baseline, as designed); `analyze` reports both startup
series **CONFIRMED +101.4 %** with the onset at the injection round `20261011T233937Z` and the
ground-truth match set, and no other confirmed or suspect event on any series of the Pixel.

iPhone 14, six rounds over four sessions: `20261011T235708Z` 753.1 / 761.8 ms, then `20261012T063936Z`
740.1 / 752.8, `071850Z` 742.1 / 748.4, `075728Z` 749.0 / 756.3, `093131Z` 743.8 / 750.5 and
`101350Z` 747.2 / 763.1 (`startup.ttid_ms` / `startup.ttfd_ms`) against the 517.5 / 528.4 ms
baseline; the gate flagged both series +40…+46 % on the first three rounds and nothing from the
fourth, as on the Pixel. `analyze` after `revert R1`: **`startup.ttid_ms` CONFIRMED +44.3 %,
`startup.ttfd_ms` CONFIRMED +42.8 %**, onset at the injection round `20261011T235708Z`, ground-truth
match set. The detector also confirmed `tuner`, `metronome` and `stress/cpu.instructions_k` +27.5,
+23.0 and +18.4 % at the same onset — about 2.5 G retired instructions more per journey, the spin's
own cost: every iPhone journey iteration relaunches the app before `startMeasuring`, and
`XCTCPUMetric(application:)` still counts them. R1 lists those three series under
`tolerated_side_effects` in the registry, which the ground-truth lookup now honours (no verdict,
neither a hit nor a false alarm); nothing else on either device rose above PRELIMINARY.

What the six rounds cost: after the first one the phone's `testmanagerd` stopped enabling automation
("The test runner failed to initialize for UI testing (Timed out while enabling automation mode)",
two failed rounds, host logs identical to a healthy round) and a `devicectl device reboot` left it at
the lock screen until the user swiped; then round `20261012T083611Z` (unpublished) and the first
Release round were refused `runner_trusted` by a phone that *had* trusted the developer — the denial is
transient, which is why a round now retries it twice two minutes apart; the audio-trace pass of the
first four rounds found no process to attach to, which is why it records system-wide (above).
