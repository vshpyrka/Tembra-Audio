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
