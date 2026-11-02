# feat(recording): microphone recording on the Timeline (feature 010)

Arm a track from its header, press Record (in the transport bar from 432 dp, in the armed compact header below) and a
take grows on the timeline with its waveform, aligned to the beat by the device's reported latencies; a count-in of
Off / 1 bar / 2 bars, an input meter with hold and clip latch, input monitoring through the track's strip (never on the
built-in speaker), the permission flow with "Open settings", takes that survive the screen off and end cleanly on
calls, input changes, revocation, leaving and termination, the A/R/I/Escape keys, and every control at every width.
Spec, plan and tasks: `specs/010-timeline-recording/` (97 tasks; the open ones are the owner's rows listed below).

## What changed, and the principle behind each non-obvious decision

- **One duplex callback (Constitution I, research R2).** The input block is read non-blocking inside the output
  callback immediately before the render of the same block (Oboe second stream; RemoteIO bus 1); the capture source
  pushes into an SPSC fifo the `tembra-recorder` thread drains into a 24-bit WAV. Nothing Kotlin runs on the callback.
- **Pre-roll inside the engine (Principle II, FR-009/FR-015).** The count-in moves the clock to `start − countIn`
  with the click forced on; capture begins at the downbeat in the same block it is reached. The session stays
  `Stopped` through the pre-roll so leaving cancels without a dialog (FR-022) and becomes `Playing` at the first
  captured frame.
- **Alignment by the engine (FR-011).** Every take is shifted by input + output latency as the streams report them
  (`tembra_capture_set_input_latency_ns` added to ABI 7 so iOS can report its input latency before the first take).
  Measured: 131 ms on both Pixels where the input opened at 118 ms latency, 17 ms in a process whose input opened
  low-latency; the loopback check of quickstart §8 is the judge (owner, the one sounding step).
- **Record arms first (FR-008).** `RecordingController.record(countIn, armFirst)` arms the selected or a new track and
  starts the take once the input is open; the policy is a pure function (`RecordingPolicy`, 245 core tests).
- **FR-009/FR-018 refusals live in the ViewModel and the content** (seek controls disabled, the toolbar's structural
  entries disabled, the keys no-ops; undo refused while a take finalises).
- **The meter and the growing take read the engine per frame in the draw phase** (`InputTelemetry`, `rememberFrameTick`),
  never through `StateFlow`; the 10 Hz mirror feeds semantics only. Every feature composable is skippable; `RecordingUi`
  and `LiveTakeUi` are `@Immutable` (compose report in `evidence/compose-report/`).
- **A newly added track scrolls into view** (`TrackList`): on a phone "+ Track" landed below the lanes, which the
  recording journey (and a user recording right after adding) could not reach.
- **Android foreground service**: the microphone type is requested only for the "Recording" notification and only
  with the permission granted (Android 14 refuses it otherwise); a running media-playback service takes the type
  through a second start command when a take begins (FR-021).
- **Skill contracts**: `compose-component-design` (modifier first on the root, primitives, no boolean shape flags),
  `compose-state-and-effects`, `compose-performance`, `compose-focus-navigation`, `compose-ui-testing-patterns`
  (semantics before pixels, scripted telemetry, frozen clock), `kotlin-concurrency-and-flow` (named launches, the
  controller's scope, `StateFlow` as renderable truth, the 10 Hz mirror idle while nothing is armed).

## Verification

- Host: `engineHostTest checkAndroidHost` green, zero warnings (929 JVM tests: ui 473, core 246, data 142, engine 68;
  40 Catch2 cases incl. the trace poison build of the RT sources).
- Simulators: `checkIos` green on the iPhone 18 Pro, the iPad Pro 13-inch (M5) and the iPhone Duo (every `uiTest`
  class, the iOS `NativeAudioEngineRecordingTest` arming the simulator's input).
- Devices (`checkAndroidDevice`, `screenshots/final-pass-android.md`): Pixel 6 Pro green (1478 device tests, then
  977 again after the fixes), Pixel 7 Pro and Pixel Tablet green after the engine re-run, Redmi Note 5 green, OPPO
  CPH2239 green with the two engine recording tests skipped (ColorOS refuses the runner's grant), fold emulator:
  recording test green, two timing/probe flakes and three screenshot-capture flakes of earlier features remain.
- Perf (`evidence/trial-round.md`): Pixel 7 Pro round `20261101T205913Z` ran all six journeys, the recording journey
  20/20 (22.1 s median; frames p50 7.8 ms, p90 11.3 ms, p99 15.1 ms); the recording-only round `20261101T222912Z`
  validates the count-in interval (1972 ms) and record start (19.8 ms). Counters in the stress journey:
  `audio.input_overruns` median 1 (short non-blocking reads right after arming under the stress scroll; a reservoir
  read is the follow-up), starvation/xruns/over-budget 0, alignment 130–132 ms. Pixel 6 Pro round `20261102T040607Z`:
  all six journeys, the recording journey 20/20 (22.2 s median; frames p50 8.5 ms, p90 11.5 ms, p99 14.5 ms), input
  overruns 6 under the stress scroll. The profile compare on it exposed a tap cancelled by the new track's
  scroll-into-view animation once in 49 iterations (fixed in 2176267: an instant scroll); the rerun passed 40/40, the
  regenerated profile verifies and leaves the recording journey's times unchanged (`evidence/profile.md`). The app-size
  gate notice (7 MB to 27 MB) is feature 009's demo WAV files, not this feature. Against a reference round of the
  pre-010 code on the same Pixel 7 Pro (`20261102T055511Z_009-main-reference`, built from the merge base f47014e):
  app +0.6 %, startup ttid +4 % and ttfd +5 % (10 to 16 ms), the tuner, metronome and timeline journeys and their frame
  times unchanged within 2 %; the stress journey records now, so it is longer and its memory peak is +21 % (a
  follow-up to size).
- Soak (`evidence/notes.md`): a 24:40 take on the Pixel 7 Pro's perf build: overruns 0, starvation 0, xruns 0,
  backlog 1152 frames, one callback over budget at 2.98 ms. The 30-minute cap ended a take by itself on the Pixel 6 Pro.
- Screenshots: `screenshots/ios/` (14 states on three simulators) and `screenshots/android/` (five widths, the fold
  postures); release builds at this branch installed on all ten targets (`screenshots/NOTES.md` § Release builds).
- Pipeline: the `recording` journey and the four counters registered (`uv run pytest`: 352 green; one pre-existing
  failure, the stale `regressions/r2-frames.patch`).

## Open for the owner (quickstart §4–§8, T081, T090, T094, T095, T097)

- Trust the TembraPerfTests developer profile on the iPhone 14 (Settings → General → VPN & Device Management), keep it
  unlocked, then `uv run tracepulse run --device iphone14 --label 010-recording --rounds 1 --no-publish`.
- The rows that need sound, headphones or a call: the meter by ear, the count-in by ear, monitoring on headphones /
  speaker guard / unplug / wireless, the headset mic mid-take, calls, screen lock and force quit ×10, the loopback
  alignment and monitoring delay (`app/tools/measure-take-alignment.py`), TalkBack/VoiceOver, Record's placement at
  360 dp, the Stitch sync, the post-merge `--rounds 5` sessions.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01UE6witv9RPuqF9AcYShHJe
