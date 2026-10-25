# Trial perf rounds (task T131)

Date: 2026-10-19. Everything through `cd app/perf && uv run tracepulse run … --rounds 1 --no-publish`, the Android
artefacts built once through `gradle-run` (`./gradlew -Ptembra.perf=true :androidApp:assemblePerf
:perf-benchmarks:assemblePerf`) and passed with `--no-build --apk … --test-apk …`. Rounds are unpublished: the
labelled sessions after the merge (`--rounds 5`, then `tracepulse sync` and `report`) are the feature's baseline.
Comparison label: `baseline-profiles` (feature 008, Pixel 6 Pro round `20261015T155821Z`).

## What the rounds proved and changed

| Round | Journeys | Outcome |
|---|---|---|
| A `20261019T154042Z` (Pixel 6 Pro) | all five, 20 iterations | completed; found that the Metronome and the Tuner were refused the audio device from a stopped Timeline (metronome journey 6.1 s against 1.8 s, its start never took) → the transport hand-over fix (`PlaybackGuard.transportHostVisible`); the timeline journey's split never ran (the seek at 25 % of the ruler hit the signature pill, then 60 % landed past the clips) |
| B `20261019T162959Z` (Pixel 6 Pro) | all five, 20 iterations | the numbers below; metronome journey 3.0 s; the split still mostly skipped (the saved zoom drifts across iterations) |
| C `20261019T170729Z` (Pixel 6 Pro) | timeline only | seek at 40 %: split in a few iterations only → the journey now restarts playback and waits for bar 2 before the split (both platforms) |
| D `20261019T172133Z` (Pixel 6 Pro) | stress only | failed: the health-write dot, moved into the playback phase, sat on the Timeline's overflow button — its node carried the window-inset padding, so the journey's centre click opened the overflow menu and hid the app's tree. The dot is now its own 8 dp node inside a padded box |
| E `20261019T172342Z` (Pixel 6 Pro) | timeline only | failed: `timeline.clip.first` was gone — the journey's splits persisted in the demo across iterations and rounds (the perf package keeps its data), so the first clip had become fragments. The journey now undoes its split at once, and the Pixel's app data was cleared |
| E2 `20261025T190127Z` (Pixel 6 Pro) | timeline only | completed, 20 iterations, the split in every iteration; see "Reruns" |
| D2 `20261025T191631Z` (Pixel 6 Pro) | stress only | failed: the health-write dot's accessibility node was 28 × 48 dp (Compose grows a clickable's touch bounds to the 48 dp minimum and accessibility reports them; clipped at the screen edge), so the journey's centre tap opened the overflow menu and `nav.item.metronome` vanished behind the pop-up window; reproduced by hand with `uiautomator dump` |
| D3 `20261025T192423Z` (Pixel 6 Pro) | stress only | failed the same way: Material's `LocalMinimumInteractiveComponentSize` does not touch a plain clickable's touch bounds; the dot now provides a `ViewConfiguration` with a zero minimum touch target |
| D4 (Pixel 6 Pro, 2026-10-25) | stress only | see "Reruns" |
| iPhone 14 | all five | the two attempts of 2026-10-19 produced no journeys (the UI test runner could not start automation on a sleeping phone); on 2026-10-25 two more attempts were refused because the runner's profile was not trusted on the phone until the owner trusted it in Settings |
| iPhone 14 `20261025T185916Z` | all five | the tuner (295 s), both start-up measurements (90 s each), the metronome (602 s) and the timeline (1,352 s) journeys passed; after forty minutes on the cable the phone's thermal state read "fair", so the preflight refused the stress journey and the facade self-checks and the pipeline wrote no record (the result bundle stays under `data/rounds/`); see "iPhone 14" below |

What the journeys now do differently from the contract's first draft (`contracts/perf-instrumentation.md` updated): the
seek taps the ruler at 40 % of its width (the tempo and signature pills overlay its left third on a phone); before
the split the journey skips to the start and waits for bar 2, so the moving playhead is inside the first clip
whatever zoom the pinches and the saved view state left; the split is undone at once; the audio-health counters are
written right after the playback phase, while the transport still owns the device, and the Android collector keeps
each counter's peak over the trace (the engine restarts its counters per stream session).

## Round B against the feature 008 baseline (Pixel 6 Pro, medians of 20)

| Journey | Series | Median | Baseline median | Change |
|---|---|---:|---:|---:|
| startup | `startup.ttid_ms` | 224.31 | 182.08 | +23 % |
| startup | `startup.ttfd_ms` | 308.34 | 182.08 | +69 % |
| tuner | `journey.duration_ms` | 2,019.86 | 1,412.45 | +43 % |
| tuner | `memory.peak_kb` | 89,102.00 | 85,718.00 | +4 % |
| tuner | `frames.p90_ms` | 8.99 | 10.97 | -18 % |
| metronome | `journey.duration_ms` | 3,025.68 | 1,831.10 | +65 % |
| metronome | `memory.peak_kb` | 95,796.00 | 99,092.00 | -3 % |
| metronome | `frames.p90_ms` | 8.62 | 9.61 | -10 % |
| metronome | `section.metronome.tempoChange.sum_ms` | 1.65 | 1.44 | +15 % |
| timeline | `journey.duration_ms` | 24,218.30 | None | new |
| timeline | `frames.p90_ms` | 11.48 | None | new |
| timeline | `frames.p99_ms` | 14.88 | None | new |
| timeline | `memory.peak_kb` | 169,774.00 | None | new |
| timeline | `section.timeline.projectOpen.sum_ms` | 0.36 | None | new |
| timeline | `section.transport.play.sum_ms` | 3.91 | None | new |
| timeline | `section.transport.seek.sum_ms` | 0.15 | None | new |
| timeline | `section.timeline.zoom.sum_ms` | 0.09 | None | new |
| timeline | `section.timeline.clipSplit.count` | 0.00 | None | new |
| timeline | `section.timeline.clipSplit.sum_ms` | 0.00 | None | new |
| timeline | `section.timeline.playheadFrame.count` | 1,252.00 | None | new |
| timeline | `section.timeline.playheadFrame.sum_ms` | 4.92 | None | new |
| timeline | `section.timeline.waveform.count` | 0.00 | None | new |
| stress | `journey.duration_ms` | 17,129.92 | 4,588.36 | +273 % |
| stress | `memory.peak_kb` | 145,048.00 | 100,832.00 | +44 % |
| stress | `audio.callbacks` | 85.00 | 212.00 | -60 % |
| stress | `audio.avg_us` | 4.00 | None | new |
| stress | `audio.max_callback_us` | 14.50 | 15.00 | -3 % |
| stress | `audio.over_budget` | 0.00 | 0.00 | new |
| stress | `audio.xruns` | 0.00 | 0.00 | new |
| stress | `audio.starvation` | 0.00 | None | new |
| stress | `audio.clips_active` | 0.00 | None | new |
| stress | `section.timeline.scroll.count` | 4.00 | None | new |
| stress | `section.timeline.scroll.sum_ms` | 0.23 | None | new |


- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 2.0, metric: app.size_bytes, observed_pct: 282.15140017558537, state: above_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 2.0, metric: metronome/frames.overrun_p90_ms, observed_abs: -1.2978454999999984, observed_pct: -35.30907978236456, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 25.0, metric: metronome/frames.p50_ms, observed_pct: -19.31947990267732, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 25.0, metric: metronome/frames.p90_ms, observed_pct: -11.414246780944904, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 25.0, metric: metronome/frames.p99_ms, observed_pct: -36.83584085167935, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: metronome/journey.duration_ms, observed_pct: 66.3254439121314, state: above_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 15.0, metric: metronome/memory.end_kb, observed_pct: 6.293089440330555, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 15.0, metric: metronome/memory.peak_kb, observed_pct: 4.053701772679875, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: metronome/section.metronome.load.count, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: metronome/section.metronome.load.sum_ms, observed_pct: 0.5442254812099002, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: metronome/section.metronome.tempoChange.count, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: metronome/section.metronome.tempoChange.sum_ms, observed_pct: 8.20517572906079, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 15.0, metric: startup/startup.ttfd_ms, observed_pct: 73.91801007748657, state: above_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 15.0, metric: startup/startup.ttid_ms, observed_pct: 26.520192545362864, state: above_limit
- limit_pct: 50, metric: stress/audio.avg_us, reason: fewer than 5 clean rounds (0), state: inactive
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 50.0, metric: stress/audio.callbacks, observed_pct: -64.43514644351464, state: within_limit
- limit_pct: 50, metric: stress/audio.clips_active, reason: fewer than 5 clean rounds (0), state: inactive
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 50.0, metric: stress/audio.last_callback_us, observed_pct: -40.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 50.0, metric: stress/audio.max_callback_us, observed_pct: -9.375, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 0.0, metric: stress/audio.over_budget, observed_abs: 0.0, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 0.5, metric: stress/audio.realtime, observed_abs: 0.0, observed_pct: 0.0, state: within_limit
- limit_abs: 1.0, metric: stress/audio.starvation, reason: fewer than 5 clean rounds (0), state: inactive
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 0.0, metric: stress/audio.xruns, observed_abs: 0.0, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 2.0, metric: stress/frames.overrun_p90_ms, observed_abs: -2.013576399999998, observed_pct: -169.8781846258184, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 25.0, metric: stress/frames.p50_ms, observed_pct: 17.3428296231605, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 25.0, metric: stress/frames.p90_ms, observed_pct: 10.962219558370077, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 25.0, metric: stress/frames.p99_ms, observed_pct: -18.22965111833431, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/journey.duration_ms, observed_pct: 276.81868198566934, state: above_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 15.0, metric: stress/memory.end_kb, observed_pct: 30.004884980904166, state: above_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 15.0, metric: stress/memory.peak_kb, observed_pct: 43.80837183478416, state: above_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.metronome.load.count, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.metronome.load.sum_ms, observed_pct: -14.803009483477126, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 0.0, metric: stress/section.metronome.tempoChange.count, observed_abs: 0.0, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_abs: 1.0, metric: stress/section.metronome.tempoChange.sum_ms, observed_abs: 0.0, observed_pct: 0.0, state: within_limit
- limit_pct: 20, metric: stress/section.timeline.playheadFrame.count, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: stress/section.timeline.playheadFrame.sum_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: stress/section.timeline.scroll.count, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: stress/section.timeline.scroll.sum_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: stress/section.transport.play.count, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: stress/section.transport.play.sum_ms, reason: fewer than 5 clean rounds (0), state: inactive
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.tuner.frame.count, observed_pct: 4.761904761904762, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.tuner.frame.sum_ms, observed_pct: 0.22193513774781667, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.tuner.load.count, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.tuner.load.sum_ms, observed_pct: -12.617501695900767, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.tuner.modeSwitch.count, observed_pct: 0.0, state: within_limit
- baseline_rounds: ['20261013T064938Z_baseline-profiles', '20261013T070311Z_baseline-profiles', '20261013T071710Z_baseline-profiles', '20261013T073055Z_baseline-profiles', '20261013T074443Z_baseline-profiles'], limit_pct: 20.0, metric: stress/section.tuner.modeSwitch.sum_ms, observed_pct: 71.8684926597103, state: above_limit
- limit_abs: 2.0, metric: timeline/frames.overrun_p90_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 25, metric: timeline/frames.p50_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 25, metric: timeline/frames.p90_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 25, metric: timeline/frames.p99_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/journey.duration_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 15, metric: timeline/memory.end_kb, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 15.0, metric: timeline/memory.peak_kb, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.engine.loadClip.count, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.engine.loadClip.sum_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.engine.prime.count, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.engine.prime.sum_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.timeline.clipSplit.count, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.timeline.clipSplit.sum_ms, reason: fewer than 5 clean rounds (0), state: inactive
- limit_pct: 20, metric: timeline/section.timeline.playheadFrame.count, reason: fewer than 5 clean rounds (0), state: inactive

How to read the gate's ten "above limit" verdicts (they compare with a build that had a placeholder Timeline):

- `app.size_bytes` +282 %: the perf APK carries the demo project's four WAV loops (17 MB) and miniaudio's decoders.
  A deliberate cost of the bundled demo (spec FR-002); the release-build baseline after the merge sets the new limit.
- `startup.ttfd_ms` +69 % (308 ms), `ttid_ms` +23 %: the start destination now opens the last project and reports
  fully drawn only when its waveforms are on screen (spec FR-017 of feature 007 applied to the Timeline), not when a
  placeholder sentence is.
- `tuner/journey.duration_ms` +43 % and `metronome/journey.duration_ms` +65 %: the audio device changes hands when
  the user leaves a stopped Timeline (the engine's transport closes synchronously, the tuner's or metronome's stream
  opens next) and comes back on return. Before the fix the metronome never started at all; the remaining cost is the
  stream close and open, measured here for the first time.
- `stress/journey.duration_ms` +273 % and `memory.peak_kb` +44 %: the stress journey is the extended one of feature
  009 (five seconds of lane scrolling while the demo plays with the click, then the tuner and the metronome); the
  catalogue already notes the step at the 009 label boundary. Memory peaks at 145–170 MB with the demo's four
  overviews and clip fifos resident.
- `stress/audio.*`: in round B the counters at the end of the journey described the metronome's stream, not the
  playback (the engine restarts its health counters per stream session, and the hand-over ends the journey on another
  stream). Round D measures them during playback; the Android collector keeps each counter's peak over the trace.

The timeline journey is new, so its series have no baseline: project open 0.4 ms (the project is already open at
launch; round A's 153 ms was a real open), play 3.9 ms, seek 0.15 ms, two zooms 0.09 ms, the playhead's draw-phase
read 4 µs per frame over 1 252 frames, frame CPU P90 11.5 ms / P99 14.9 ms on a 60 Hz pinned Pixel 6 Pro, 24.3 s per
iteration, memory peak 166–170 MB.

## Reruns (Pixel 6 Pro, 2026-10-25)

The artefacts were rebuilt through `gradle-run` after the hand-over and the health-dot fixes
(`./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf`), the perf package's data was
cleared (`pm clear audio.tembra.app`) and each journey ran alone for one unpublished round of 20 iterations with
`--no-build --apk ... --test-apk ...`.

Round E2 `20261025T190127Z_009-timeline`, journey `timeline`:

| Journey | Series | Median |
|---|---|---|
| timeline | journey.duration_ms | 26,429.36 |
| timeline | frames.p90_ms | 11.44 |
| timeline | frames.p99_ms | 14.71 |
| timeline | memory.peak_kb | 167,754.00 |
| timeline | section.timeline.projectOpen.sum_ms | 0.34 |
| timeline | section.transport.play.sum_ms | 3.53 |
| timeline | section.transport.seek.count | 3.00 |
| timeline | section.transport.seek.sum_ms | 0.22 |
| timeline | section.timeline.zoom.sum_ms | 0.09 |
| timeline | section.timeline.clipSplit.count | 1.00 |
| timeline | section.timeline.clipSplit.sum_ms | 0.33 |
| timeline | section.timeline.playheadFrame.count | 1,385.00 |
| timeline | section.timeline.playheadFrame.sum_ms | 5.55 |
| timeline | section.timeline.waveform.count | 0.00 |

Split count per iteration: 1..1 (median 1)

The split landed in every iteration (the earlier rounds C and E lost it), with three seeks per iteration and no
waveform rebuilds (`section.timeline.waveform.count` 0, the overviews are cached after the first open).

Round D4 `20261025T195003Z_009-timeline`, journey `stress`:

| Journey | Series | Median |
|---|---|---|
| stress | journey.duration_ms | 16,955.29 |
| stress | memory.peak_kb | 148,516.00 |
| stress | audio.callbacks | 5,243.50 |
| stress | audio.avg_us | 16.00 |
| stress | audio.max_callback_us | 156.00 |
| stress | audio.over_budget | 0.00 |
| stress | audio.xruns | 0.00 |
| stress | audio.starvation | 0.00 |
| stress | audio.clips_active | 4.00 |
| stress | section.timeline.scroll.count | 4.00 |
| stress | section.timeline.scroll.sum_ms | 0.22 |

`audio.starvation`, `audio.xruns` and `audio.over_budget` read 0 in every one of the 20 iterations (max 0),
`audio.clips_active` is 4, the average callback takes 16 µs and the slowest 156 µs median (287 µs in the worst
iteration) against a 2.67 ms block budget at 48 kHz and 128 frames.

### Gates against the feature 008 baseline

- `app.size_bytes` +282 %: the perf build now bundles the demo project's four audio loops (about 17 MB) and the
  timeline feature's code. The gate's 2 % limit is for later features against this feature's own baseline.
- `stress/audio.callbacks` +2094 % and `stress/audio.max_callback_us` +875 %: the stress journey gained a transport
  phase (the demo plays with the click on while the lanes scroll for five seconds), so far more audio callbacks run
  and each renders four clips; the feature 008 stress journey only clicked the metronome. This is the expected
  `stress` step at the label boundary that T136 says to note in the report.
- The rest are inactive, fewer than 5 clean rounds: the first labelled sessions after the merge (`--rounds 5`)
  create the feature's own baseline.

### iPhone 14

Round `20261025T185916Z_009-timeline` (all five journeys, the runner trusted that afternoon by the owner): the tuner,
start-up, metronome and timeline journeys passed, 20 iterations each, and the stress journey was refused by the
runner's own preflight after forty minutes of measurement on the charging cable (`thermal_state=fair limit=nominal`);
without the stress journey the round has no counters and the pipeline keeps the result bundle but writes no record. A
rerun with `--journeys timeline,stress` and the same test build followed after a twelve-minute pause
(`20261025T201637Z`): the pipeline runs the whole test target whatever `--journeys` says (the option filters the
collected journeys afterwards), so the start-up measurements and the timeline journey ran first (the timeline passed
again, 1,349 s, 20 iterations) and the tuner's preflight found the phone at "fair" after 26 minutes, before the stress
journey's turn. The timeline journey therefore completes on the iPhone 14 (twice, with its sections in the result
bundles); the three audio counters come from the stress journey's health write, which is run alone through
`xcodebuild test-without-building … -only-testing:TembraPerfTests/StressPerfTests` with the same `.xctestrun` after a
cool-down; its counters are recorded below. For the labelled sessions after the merge, run the five journeys as two
sessions or let the phone cool between rounds (the preflight's limit is "nominal").

The stress class alone, 2026-10-25 23:01 to 23:15 (a fresh `build-for-testing` with the dot's exact touch bounds; the
18:59 build still opened the overflow menu on the dot tap, as D2 and D3 did on the Pixel): `testStressJourney` passed,
20 iterations, 1,031 s. The health file the last iteration wrote (`Documents/engine-health.json`, pulled with
`devicectl device copy from`, kept as `iphone14-engine-health.json` beside this file):

| Counter | Value |
|---|---|
| `audio.starvation` | 0 |
| `audio.xruns` | 0 |
| `audio.over_budget` | 0 |
| `audio.clips_active` | 4 |
| `audio.callbacks` | 595 |
| `audio.avg_us` | 32 |
| `audio.max_callback_us` | 98 |
| `audio.realtime` | 1 (stream open) |

The counters describe one iteration's playback phase (the file is overwritten per iteration; a pipeline round samples
it the same way). Fewer callbacks than the Pixel's 5,243 for the same phase: the iPhone's output stream runs larger
blocks. No pipeline record exists for the iPhone yet; the labelled sessions after the merge create it.
