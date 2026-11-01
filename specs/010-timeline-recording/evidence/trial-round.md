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

