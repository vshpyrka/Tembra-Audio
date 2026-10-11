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
  is refused with "not been explicitly trusted" (exit 2). The selected Xcode's iOS SDK must be at
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
uv run tracepulse status [--series [--label <label>]]  # cache, unpublished records, branch state (+ CoV per series, one label)
uv run tracepulse inject R1 | revert R1 | revert --all  # the regression-injection self-test
```

`--json` on any command prints one JSON object on stdout (progress lines go to stderr) for a future CI job.

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
hitch-based (`XCTOSSignpostMetric` animation intervals). `startup.ttfd_ms` is app-declared on Android
(`ReportFirstContentDrawn`) and OS-defined on iOS.

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

The gate (`gate.py`): per metric, the change of the round's median against the median of the last N
clean rounds (N = `gate.clean_rounds`, 5) in the worse direction, against the catalogue limit
(overridable in `tracepulse.yml`). `above_limit` is a notice in the output and the report, never a
failure; `inactive` names its reason (fewer than N clean rounds, simulator series, disabled); improvements
are never flagged; a clean round succeeded without a `noisy`/`thermal` badge and outside an
environment-suspect group — a gate flag does not make a round unclean.

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
   `collect_android.BENCHMARK_CLASS_OF` / `collect_ios._TEST_JOURNEYS`.

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

## Audio-thread health

`tembra_engine_audio_health` (C ABI revision 5) copies the counters the callback writes with relaxed
atomics: callbacks, last/max callback duration, over-budget count, xruns (Oboe's counter, sampled off
the callback), real-time status. `AudioHealthTicker` publishes them once per second through
`PerfTrace.counter` in measurement builds; on Android they become counter tracks in each stress
iteration's trace (`audio.*` series, one sample per iteration), on iOS the stress journey's last tap
writes `engine-health.json`, which the collector pulls from the app container. Zeros with the current
engine; the audio spine fills them.

## Measurements so far (Pixel 6 Pro, 2026-10-08)

- A round takes 13 m 40 s of device time with `--no-build` (SC-001); the measurement build adds a few
  minutes the first time. Cold start TTID/TTFD median 241.6 ms; tuner journey 1.39 s; metronome
  journey 1.84 s; frames P50 7.2 ms (tuner), peak RSS 88 MB (tuner) / 109 MB (stress); APK 14.9 MB.
- With the instrumented build (round `20261008T175034Z`): `tuner.load` 70 ms, `tuner.modeSwitch` 206 ms
  for two switches, `metronome.tempoChange` 1.7 ms for ten steps, `tuner.frame` 17 frames per journey.
- Quality allowances calibrated on these rounds: `memoryHeapSizeLastKb` 0.45, `frameCount` 0.6 (the
  tuner journey waits on anchors). Revisit at the noise baseline (SC-003, `noise-baseline` sessions).
- iOS: the facade self-check passed on the iPhone 17 simulator (20 measurements of `tuner.modeSwitch`,
  average 127 ms); the iPhone 14 round builds and installs, and waits for the runner profile to be
  trusted on the phone.
