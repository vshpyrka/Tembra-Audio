# Trial round (T109)

The perf artefacts are the `perf` build type built through `gradle-run` (`./gradlew -Ptembra.perf=true
:androidApp:assemblePerf :perf-benchmarks:assemblePerf`) and passed to the pipeline with `--no-build --apk …
--test-apk …`; every round below ran `uv run tracepulse run --device pixel6 --label 012-mixer --rounds 1
--no-publish` from `app/perf` on the Pixel 6 Pro (`19021FDEE001AH`, Android 17, build `CP3A.261005.002.A1`,
60 Hz pinned, airplane mode, run conditions applied first). The SD card held the round folders
(`/Volumes/External/Dev/tembra-audio/perf/data/rounds/<round>/`).

## Round 1, `20261108T233042Z_012-mixer`: the half sheet has no knob

Status `failed`: `MixerJourneyBenchmark failed twice`. Startup, tuner, metronome, timeline and recording completed.
Both attempts of the mixer journey failed on their first iteration after 34 s with

```
AssertionError: perf anchor 'mixer.strip.pan.0' not found within 10000 ms
```

A phone opens the sheet at its half height, which shows the faders and the meters but not the pan knobs (data
model §5), so step 6's drag had nothing to find. The journey (Android and iOS) now taps `mixer.handle` for the full
form when `mixer.strip.pan.0` is absent and taps it back before the close (contract steps 6 and 11).

## Round 2, `20261109T002745Z_012-mixer`: the meters keep UiAutomator from idling

Status `failed`: `detached instrument poll timed out (reachable 1500s, wall 1500s)`, twice. The journey worked
(the handle tap raised the sheet, every later anchor was found) but an iteration took about 75 s against the
timeline journey's 44 s, so attempt 1 reached iteration 18 of 20 when the pipeline's 25-minute instrument window
closed, and attempt 2 the same. The sheet's meters publish their semantics ten times a second while it is open, so
UiAutomator never saw the UI idle and every click acknowledgment and `waitForIdle` ran to its default 10 s.
`MixerJourney.run` now sets `Configurator`'s idle and acknowledgment waits to 500 ms for its run and restores them
afterwards (contract, "Journey `mixer`"; skill review, the idle-wait row).

## Round 3, `20261109T060033Z_012-mixer`: the USB hub dropped the phones

The rebuilt perf APKs (the benchmark APK of 07:59 carries the idle waits; the app APK was up to date). Battery
100 %, 32 °C, all ten device checks passed. Five journeys completed with their traces:

| Journey | Instrument time | Result |
|---|---|---|
| startup | 62 s | OK |
| tuner | 86 s | OK |
| metronome | 120 s | OK |
| timeline | 944 s | OK (20 iterations, about 44 s each) |
| recording | 702 s | OK |

At about 08:44, as the mixer journey's turn came, the USB hub dropped every physical device at once (the five
Android phones and the iPhone 14: `adb devices` listed only the two emulators, `devicectl` showed the iPhone as
paired but not connected). The pipeline's detached poll counts only reachable seconds against its 25-minute
budget and caps the wall time at three hours, so the round sat on the unreachable phone; it was stopped at 10:10
(no `record.json`; the five journeys' logs and traces are in the round folder). The mixer journey has not completed
a measured run yet.

## Round 4, `20261109T082126Z_012-mixer`: the mixer journey measured; the stress journey's stale lane

The phones back on the hub at 10:20 (through an ssh tunnel to adb), `--journeys mixer,stress`, the APKs of the
idle-wait fix. Status `failed` on the stress journey; the mixer journey completed its 20 iterations (`MixerJourneyBenchmark-attempt1.log`,
every trace written). Medians over the 20 iterations (count per iteration, sum per iteration):

| Section or metric | Count | Median | Notes |
|---|---|---|---|
| `journey.mixer` | 1 | 49.3 s (49.1 to 49.7) | 75 s before the 500 ms idle waits |
| `timeline.mixerOpen` | 1 | 0.021 ms | the toggle to the sheet's first strip draw |
| `timeline.mixerClose` | 1 | 0.13 ms | |
| `timeline.mixerFrame` | 2,760 frames | 467 ms in all, 0.17 ms a frame (max 506 ms) | the sheet's draw with the one `MixTelemetry.read` |
| `timeline.faderDrag` | 2 | 10.7 s in all | the two one-second gestures as UiAutomator plays them (200 steps each way) |
| `timeline.panTurn` | 1 | 2.87 s | the knob up and back |
| `transport.masterDrag` | 2 | 12.4 s in all | |
| `engine.panApply` | 27 | 0.135 ms in all, 5 µs each | |
| `timeline.playheadFrame` | 2,812 | 10.0 ms in all | |
| `timeline.projectSave` | 16 | 166 ms in all | the fader, knob, solo, mute and master edits and their undos |
| frames (CPU) | 2,864 | p50 9.6 ms, p90 12.5 ms, p99 14.5 ms | overrun p50 −4.3 ms, p90 −1.5 ms, p99 +1.07 ms |
| memory | | 125 MB anon RSS at the end, 179 MB peak | |

Every mixer section is present in every iteration. The mixer journey carries no counters; `audio.meterFramesDropped`
comes from the stress journey's health dump, which failed: both attempts threw `StaleObjectException` at
`StressJourney.recordWhileScrolling` (`StressJourney.kt:71`) on the first `lane.scroll` after the add, the arm and the
Record tap. The first fix made the scrolling stale-safe (find the lane again) and gave the mixer phase the short idle
waits (`withShortIdleWaits`, shared with the mixer journey).

## Round 5, `20261109T085909Z_012-mixer`: the first lane leaves the list

`--journeys mixer,stress` on the merged branch's APKs (10:58). The mixer journey completed again (20 iterations,
49.3 s median; frames p50 9.56 ms, p90 12.43 ms, p99 14.54 ms; `timeline.mixerFrame` 448 ms an iteration;
`engine.panApply` 0.128 ms). The stress journey failed both attempts with `perf anchor 'timeline.lane.0' not found
within 10000 ms` from the fresh lookup: on the 24-track project the list scrolls the added 25th track into view and the
first lane leaves the lazy list's composition, so it was never stale but gone. The recording phase now scrolls the new
track's lane (as the iOS journey has since feature 010) and the mixer phase flings the list back to its top before the
sheet opens so the first lane stays visible above the half sheet (contract, "Journey `stress`"; the skill review's
row).

## Round 6, `20261109T093719Z_012-mixer`: the list stays where the recording left it

The recording phase passed with the new track's lane. The mixer phase then failed both attempts on the first lane
after the list was "flung" to its top: a `UiObject2.fling` on one lane travels a lane's height and barely moves a
25-track list. Measured by hand on the Pixel 6 Pro without playback (`stress_repro.py`, `uiautomator dump`): after
the add the composed lanes are 20 to 24 and the list is 12,100 px long; three downward swipes of 900 px over the list
bring lanes 0 to 4 back. The journey now swipes down over the track list itself (`timeline.tracks`, a quarter from its
top to an eighth from its bottom, 12 steps) until the first lane is composed again. The mixer journey completed a
third time (20 iterations, 49 s).

## Round 7, `20261109T102048Z_012-mixer`: both journeys green; the pull did not make it

`--journeys stress,mixer` on the merged branch's APKs with the list swipe. Status `succeeded`, gate within limits,
no badges, thermal 36 °C, battery 100 %. The stress journey ran its 20 iterations (39.7 s median; frames p50 9.08 ms,
p90 11.99 ms, p99 15.49 ms; the mixer phase's `timeline.mixerFrame` 520 frames and 95 ms an iteration) and the mixer
journey its 20. But the record carries no journeys: the phones reach adb through an ssh tunnel today, which pulls at
0.6 MB/s, and a class's 1 GB of traces did not fit the pipeline's ten-minute pull window; `adb pull` returned 124,
the pipeline ignored it, found two traces and no JSON, and wrote a "succeeded" record without data (round 6 and round
5 had the same silent pull behind their failures). Two pipeline changes: a pull that fails or times out now fails the
round ("ran, but its output did not reach the Mac"), and the pull window is 40 minutes (`PULL_TIMEOUT_S`, with the
reason in the code and the perf README); `uv run pytest`: 367 passed.

The mixer journey's JSON was still on the phone and was pulled by hand (8 s) into the round folder; the pipeline's own
collector (`collect_round`) read it offline. Medians over the 20 iterations:

| Catalogue metric | Median | Min to max |
|---|---|---|
| `journey.duration_ms` | 49,370 ms | 49,210 to 66,060 |
| `frames.p50_ms` / `p90` / `p99` | 9.60 / 12.46 / 14.49 ms | p99 14.37 to 14.73 |
| `frames.overrun_p90_ms` | −1.49 ms | −1.69 to −1.36 |
| `memory.end_kb` / `memory.peak_kb` | 134,900 / 183,600 kB | peak 172,200 to 191,200 |
| `section.timeline.mixerOpen` | 1 × 0.022 ms | |
| `section.timeline.mixerClose` | 1 × 0.146 ms | |
| `section.timeline.mixerFrame` | 2,757 × 488 ms in all (0.18 ms a frame) | 436 to 675 ms |
| `section.timeline.faderDrag` | 2 × 10,770 ms in all | |
| `section.timeline.panTurn` | 1 × 2,870 ms | |
| `section.transport.masterDrag` | 2 × 12,380 ms in all | |
| `section.engine.panApply` | 27 × 0.133 ms in all | |
| `section.timeline.playheadFrame` | 2,806 × 9.9 ms | |
| `section.timeline.projectSave` | 16 × 299 ms | 15 to 22 saves |
| `section.transport.play` | 1 × 3.6 ms | |

Every section the contract lists for the journey is present in all 20 iterations with a constant count. The stress
journey's traces (the source of `audio.meterFramesDropped` and `audio.masterClips`: the pipeline reads the counter
tracks of each iteration's trace) were cleared from the phone before the mixer class ran, so the counters come from
the next round.

## Round 8, `20261109T112441Z_012-mixer`: the counters, and what they found

`--journeys stress` alone. The journey ran its 20 iterations (39.7 s median; frames p50 9.11 ms, p90 12.07 ms, p99
14.51 ms; `timeline.mixerFrame` 521 frames and 99.5 ms an iteration; memory peak 202 MB). The pull failed again, this
time with `adb pull` returning 1 part-way through the first trace: the tunnel drops the phone for a moment now and
then, and a 43 MB transfer does not survive it (the round failed loudly, as the pipeline change intends). The twenty
traces came over in 4 MB chunks, each checked against the phone's md5 (`chunked_pull.py`, 26 minutes, 15 retries),
and the pipeline's own reader (`counter_series`) took the counters from them. Medians over the 20 iterations, min to
max:

| Counter | Median | Range |
|---|---|---|
| `audio.meter_frames_dropped` | **0** | 0 |
| `audio.master_clips` | **0** | 0 |
| `audio.xruns` | 0 | 0 |
| `audio.over_budget` | 0 | 0 |
| `audio.input_overruns` | 0 | 0 to 1 |
| `audio.clips_active` | 24 | 24 |
| `audio.callbacks` | 16,910 | 16,610 to 17,910 |
| `audio.avg_us` / `audio.max_callback_us` | 65.5 / 405 µs | 61 to 85 / 325 to 509 |
| `audio.record_backlog` | 672 frames | 576 to 672 |
| `audio.take_alignment_ms` | 135 | 135 to 137 |
| `audio.starvation` | **358,400** | 358,300 to 358,500 |

No meter frame dropped and no master clip in 20 iterations of 24 strips: the two counters this feature added read as
the spec asks. `audio.starvation`, which the spec also wants at zero (SC on the 24-track project; it read 20 to 40
on the demo project's journeys and 0 on the recording journey), did not: the counter track in one trace rises by
about 11,600 a second for the whole 31 s of playback, 23 of the 24 active clips starved on every callback. Cause,
found in the session rather than the engine: `ClipSourceRegistry` held one engine source per asset however many
clips used it, and a source has one read position; the stress project's 24 clips are the demo's four loops six times
over, so five clips in six asked the shared fifo for frames it had already passed, parked it and starved. The fix:
`ArrangementMapper` hands an asset's sources out first-fit over its clips by start (a source is free again one
second after its clip ends; the two halves of a split continue one source, so the demo keeps its four), the
registry's `ensure` opens as many as `sourcesNeeded` asks for (at most one per track) on open, import and take, and
an edit that makes clips of one asset overlap tops them up on the session's scope and pushes the arrangement again;
short of sources, clips share one and still play, as every clip did before. The skill review has the row; the 009
session contract's two rows are updated.

**Open**: round 9, `--journeys stress` with the fix: `audio.starvation` back to the prime's few dozen.

## iOS: the journey on the iPhone 18 Pro simulator (2026-11-09, 08:19 to 08:49)

Not a round (the iPhone 14 sessions are the owner's, T125): `xcodebuild test-without-building` of
`MixerPerfTests/testMixerJourney` from the rebuilt `TembraPerf` Perf bundle, as a check of the Swift journey's
handle step. The journey ran its warm-up and 20 iterations without a step failing: the knob was found after the
handle tap on every iteration (`mixer.strip.pan.0` queried 400 times, `mixer.handle` 234), solo, mute, the strips
swipe, the master drag and the undo loop all completed. An iteration took about 96 s, with the Mac loaded by two
emulator lanes and a Kotlin/Native build at the same time, and XCTest's per-test allowance of the Perf plan
(30 minutes) ended the test at t = 1804 s during the 20th iteration's pan step ("Restarting after unexpected exit,
crash, or test timeout"; `Test-TembraPerf-2026.11.09_08-18-52-+0200.xcresult`). `TembraPerf.xctestplan` now allows
an hour per test (the pipeline's outer window is two hours since PR #16); feature 011's T143 (XCUITest's
quiescence waits on the physical iPhone while playback animates) still applies to the iPhone 14 sessions.
