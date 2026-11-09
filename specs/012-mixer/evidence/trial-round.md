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

**Open**: round 6 with the lane fix: `audio.meterFramesDropped = 0` and `audio.masterClips` from the stress
journey's health dump.

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
