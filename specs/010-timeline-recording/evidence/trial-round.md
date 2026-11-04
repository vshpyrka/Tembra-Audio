# Trial round (T086)

## iPhone 14

Not measured: the runner was refused on 2026-11-01, twice. Sessions `20261101T192615Z_010-recording` and
`20261101T193718Z_010-recording` (build-for-testing in the Perf configuration succeeded each time), rounds
`20261101T192951Z_010-recording` and `20261101T193730Z_010-recording`. Each round printed "the phone denied the runner as not
trusted" at attempts 1 and 2, then `refused: runner_trusted: the TembraPerfTests runner's profile is not trusted on the phone:
Settings -> General -> VPN & Device Management -> trust the developer profile, then rerun`. No journey ran, so there is no
record.json, no section data and no counters. The phone was connected and paired (devicectl). Owner action: trust the developer
profile on the phone (and unlock it), then rerun
`uv run tracepulse run --device iphone14 --label 010-recording --rounds 1 --no-publish` from `app/perf`.

## Pixel 6 Pro

2026-11-01, round `20261101T195039Z_010-recording` (prebuilt perf APKs, `--no-build --no-publish`): refused by the pipeline's
battery check before any journey ran: `battery: level 8% < 50% (level=8% temp=29.3 C powered=True)`. The phone sits at
8-11% and charges about 1-2% per half hour through the Mac's USB hub (500 mA, `USB powered`), so the round, the profile
generation and its compare (same battery gate) cannot run on it until it is charged to 50% on a proper charger. No journey,
section or counter result exists for this device yet.

## Pixel 7 Pro

2026-11-01, round `20261101T192943Z_010-recording` (prebuilt perf APKs, `--no-build --no-publish`, RECORD_AUDIO granted, run
conditions applied; battery 57%, 25.2 C): status `failed`, `RecordingJourneyBenchmark failed twice`. Journeys that ran:
Startup, Tuner, Metronome, Timeline (all completed, their benchmark JSON and traces are in the round folder); the recording
journey produced no data (`journeys: []` in `record.json`, no sections, no counters, no gate or screening notice).

- Attempt 1 completed 11 iterations (iter000-010, about 33 s each, traces were written) and failed on the twelfth with
  `AssertionError: perf anchor 'timeline.track.meter.4' not found within 10000 ms` at `RecordingJourney.run`
  (RecordingJourney.kt:28, the anchor after `tapArm(4)`).
- Attempt 2 failed with the same anchor on its first iteration (the traces of attempt 1 were discarded with the failure).
- Reading: the arm tap landed (the earlier failure of 2026-10-28 was the off-screen row, fixed) but the meter of track 4 did
  not appear within 10 s. Two candidate causes, not verified: (1) the first-run microphone permission/notification dialog or
  the arm refusal covering the meter after a state change in the demo project persisted between iterations (attempt 2
  failing at iteration 0 right after attempt 1 points to persisted state, e.g. the take or the added track from the
  interrupted iteration shifting the new track's index to 5 so that `.4` is an old row); (2) the arm is refused while the
  previous iteration's engine session is still stopping. Journey not edited, as instructed.
- Not observable from this round: the eleven recording sections, the four counters (`audio.input_overruns`,
  `audio.take_alignment_ms`, `audio.record_backlog`, `audio.monitor_delay_ms`).

## Pixel 7 Pro, second round (journey fixed)

Round `20261101T205913Z_010-recording` (Pixel 7 Pro 33241FDH3000NZ, perf build with the fixed journeys, `--rounds 1 --no-publish`,
status succeeded, battery 65%, 28.4 C, all environment checks passed, no badges). The fix: after the second Record tap the
journey waits for the take node to go and for `timeline.toolbar.undo` to be enabled again (adoption done), disarms
`timeline.track.arm.4`, then pauses, undoes twice and skips to start (same in the stress journey's recording step; the iOS journeys
mirror it and `build-for-testing` for the iPhone 18 Pro simulator succeeded; the iOS journeys were not run).

- Journeys: startup, tuner, metronome, timeline, recording, stress all passed, 20 iterations each (the recording journey no longer
  fails on iteration 12; 20/20, 22.1 s median, 21.9 to 22.7 s).
- Recording sections (all 20 iterations): timeline.arm 2 (0.16 ms), transport.recordStart 1 (1969 ms median, includes the count-in
  wait), transport.countIn 1, timeline.takeFrame 671 frames (93 ms), transport.recordStop 1 (0.1 ms), engine.takeAlignment 1 (66 ms),
  timeline.waveform 1 (11.5 ms), timeline.playheadFrame 566 (2.3 ms), timeline.projectSave 2 to 3 (36 ms median, max 119 ms).
  Absent: timeline.monitorToggle (count 0: without headphones the toggle was not tapped or refused) and transport.play (count 0
  in the recording journey, the pause tap does not run the section there; it is 1 in stress).
- Counters (the stress journey, which carries them; the recording journey has none): audio.input_overruns median 1 (0 to 2 across
  iterations, 13 of 20 iterations at 1 or 2) so NOT zero in this round, audio.starvation 0, audio.xruns 0, audio.over_budget 0,
  audio.record_backlog 672 frames (576 to 768), audio.monitor_delay_ms 0, audio.take_alignment_ms 130 to 132 (not the 5 to 60 ms
  plausibility range; the section engine.takeAlignment is 66 ms and the soak's counter read 17 ms, so this stress value, a take
  of 3 s without count-in, needs a look), audio.max_callback_us median 318 (max 507), audio.avg_us 19.
- Gate notices: 13 above limit against the previous baseline of another build (app.size +285 %, startup ttfd +89 %, ttid +28 %,
  stress audio.callbacks/max_callback_us/duration/memory/metronome.load and tuner/metronome journey durations): the baseline is an
  older, different journey set and a different build, so these are comparisons against a non-comparable reference, not
  regressions measured here. No screening notices.

## Pixel 7 Pro, recording-only validation round (the count-in interval fixed, fd5d08c)

Round `20261101T222912Z_010-recording` (perf APKs of 2026-11-02 00:05, `--journeys recording`, 20 iterations, status
succeeded; battery 70 %, thermal 33.5 °C). The sections now pair as intended: `transport.countIn` 1972 ms median
(the one-bar pre-roll at 124 BPM; p90 2014 ms), `transport.recordStart` 19.8 ms median (the engine call alone; it read
1969 ms in the earlier round), `transport.recordStop` 0.10 ms, `timeline.arm` 2 × 0.16 ms; frames p50 7.8 ms, p90
11.3 ms, p99 15.1 ms, overrun p90 −2.8 ms. The one gate notice is `app.size_bytes +285 %` against the phone's last
published session of 2026-10-15 (before features 009 and 010).


## iPhone 14, 2026-11-02

The phone connected and trusted this time (no `runner_trusted`, no automation-mode timeout). No record.json was produced by
either attempt; no numbers to report.

- Attempt 1, round `20261102T040913Z_010-recording` (full journey set, build-for-testing about 3 min): `RecordingPerfTests.testRecordingJourney`
  PASSED (1172.6 s, the first time the iOS recording journey ran on a device). `StressPerfTests.testStressJourney` FAILED after
  40.7 s at `StressJourney.swift:62`: `Failed to swipe left "timeline.lane.0" ... No matches found ... '"timeline.lane.0" IN identifiers'`.
  `TimelinePerfTests.testTimelineJourney` exceeded the 30 minute execution allowance (the log ends in metronome/master taps at
  t = 1800 s; the test hung or was too slow). Every later test (tuner, startup, metronome, facade self-checks) was then refused:
  `PERF-REFUSED: thermal_state=serious limit=nominal`. xcresult: `app/perf/data/rounds/20261102T040913Z_010-recording/perf.xcresult`.
- Attempt 2 (the one retry), round `20261102T050350Z_010-recording`, `--journeys recording`: refused before the journey,
  `refused: thermal_state: thermal_state=serious limit=nominal` (the phone was still hot from attempt 1).
- Open: let the phone cool to nominal, then run `--journeys recording` alone (then stress separately); the stress journey's
  `timeline.lane.0` lookup needs a look on iOS (lane identifier absent after the add-track/arm step or the screen not on the
  timeline), and the timeline journey's 30 min overrun needs its own investigation. RecordingJourney.swift was not edited.

## Pixel 6 Pro (reference device, 2026-11-02)

Round `20261102T040607Z_010-recording` (Pixel 6 Pro 19021FDEE001AH, the prebuilt perf APKs of 2026-11-02 00:05, `--rounds 1
--no-publish --no-build`; battery 58 %, 27.7 C, all checks passed, badge `noisy`). Status succeeded; startup, tuner, metronome,
timeline, recording and stress all passed, 20 iterations each (recording 22.2 s median, 21.2 to 22.6 s).

- Recording journey sections (medians over 20 iterations; count per iteration, sum per iteration): timeline.arm 2 (0.15 ms),
  transport.recordStart 1 (4.2 ms, max 27.9; the engine call alone), transport.countIn 1 (1975 ms), timeline.takeFrame 674
  frames (68 ms, max 106), transport.recordStop 1 (0.10 ms), engine.takeAlignment 1 (77 ms), timeline.waveform 1 (13.7 ms,
  5.6 to 29.5), timeline.playheadFrame 567 (2.25 ms), timeline.projectSave 2 (34 ms, max 66). Absent: timeline.monitorToggle
  (count 0 in all 20, as on the Pixel 7 Pro: no headphones) and transport.play (count 0 in the recording journey).
- Frames, recording journey: p50 8.51 ms, p90 11.48 ms, p99 14.53 ms (one iteration 23.8 ms), overrun p90 -2.96 ms.
- Stress counters (medians, min to max): audio.input_overruns 6 (5 to 8) so NOT zero (the plan asks for 0; the Pixel 7 Pro read
  1, 0 to 2), audio.take_alignment_ms 132 (130 to 132; above the 5 to 60 ms plausibility range, same as the Pixel 7 Pro's 130 to
  132, while the engine.takeAlignment section reads 77 ms), audio.record_backlog 672 frames (576 to 768), audio.monitor_delay_ms
  0, audio.starvation 0, audio.xruns 0, audio.over_budget 0, audio.max_callback_us 185 (125 to 339).
- Gate notices (14 above limit, all against the phone's last published baselines of 2026-10-13, an older build and journey
  set, so non-comparable): app.size_bytes +285 %, startup ttfd +86 % and ttid +34 %, metronome/tuner journey duration +69 % and
  +36 %, stress audio.callbacks +3993 %, audio.max_callback_us +1053 %, frames.p50 +26 %, journey duration +480 %, memory end/peak
  +44 %/+71 %, metronome.load.sum +71650 %, the stress journey's tuner.frame.count +132 % and the tuner journey's +222 %. No screening notices.

## iPhone 14, recording journey alone (2026-11-02, after the stress-journey fix)

- Fix: `StressJourney.swift` `recordWhileScrolling` now swipes the new track's lane (`Anchors.timelineLane(last)` = `timeline.lane.4`) instead of the lane 0 passed in; `Anchors.timelineLane(_:)` added. `build-for-testing` (TembraPerf, Perf, iPhone 18 Pro simulator) says TEST BUILD SUCCEEDED. The Android `StressJourney.kt` still scrolls `TIMELINE_LANE_FIRST` (not changed; the Android list scrolls differently and that journey passed).
- Attempt 1, round `20261102T060116Z_010-recording`, after a 10 minute cool-down: failed at once with "The test runner failed to initialize for UI testing (Timed out while enabling automation mode)". The phone had been locked (`passcodeRequired: true`; it unlocked about 7 minutes after the owner was asked, per `devicectl device info lockState`).
- Attempt 2, round `20261102T060859Z_010-recording` (phone unlocked): the recording journey PASSED (1175.5 s, per the coordinator), but the round ended `refused: thermal_state=serious limit=nominal`. No `record.json` was written, so there are no section medians, frame/hitch numbers or audio counters from this round.
- Cause of the refusal: the iOS pipeline runs the whole test target regardless of `--journeys`, so the other tests self-refused after the 20 minute recording journey heated the phone. The pipeline is being changed to narrow the run to the selected journey's test class. No third attempt was made.

The iPhone 14 recording-alone round 20261102T072547Z_010-recording was stopped by request (exit 144) during the measured XCTest run, after the build, install and warm-up; the iteration count is unknown and no record.json was written.

## iPhone 14, recording journey alone, narrowed run (2026-11-02, third run)

Round `20261102T085708Z_010-recording` on the iPhone 14 (iOS 27.0.1), command `tracepulse run --device iphone14 --label 010-recording --rounds 1 --no-publish --journeys recording`. Exit status 0. The record was written with the badge `short-iterations`, thermal nominal, network wifi, battery 1.0. XCTest kept 11 of the 20 iterations and dropped 9 (the log has 9 lines "Received unexpected number of metrics"). The gate is within limits; all 14 gate entries are inactive because there are fewer than 5 clean rounds, and none is above the limit.

Medians over the 11 kept iterations:

| Metric | Median |
| --- | --- |
| journey.duration_ms | 40570.6 ms |
| cpu.time_ms | 14060.7 ms |
| memory.peak_kb | 164726.7 kB |
| memory.end_kb | 162809.8 kB |
| section.transport.countIn sum_ms | 1977.7 ms |
| section.timeline.arm sum_ms | 604.3 ms |
| section.transport.recordStop sum_ms | 37.7 ms |
| section.timeline.projectSave sum_ms | 21.9 ms |
| section.engine.takeAlignment sum_ms | 19.7 ms |
| section.transport.recordStart sum_ms | 15.1 ms |
| section.timeline.waveform sum_ms | 5.7 ms |
| section.timeline.takeFrame sum_ms | 0.35 ms |
| section.timeline.playheadFrame sum_ms | 0.004 ms |

The record carries no frames.* metrics (p50, p90, p99) and no section count metrics, only the sum_ms of each section; so frame percentiles and counts are not available from this round. All nine recorded sections are present in every kept iteration (none absent). The sections not in the record at all are the other recording sections and the four audio counters listed as unobservable earlier. Raw data: `app/perf/data/rounds/20261102T085708Z_010-recording/`.
