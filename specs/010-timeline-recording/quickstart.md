# Quickstart: Microphone Recording on the Timeline (feature 010)

Validation guide for the plan. Commands run from `app/` unless stated; every Gradle command
goes through the `gradle-run` wrapper with `--warning-mode all`. Contracts: [engine ABI
v7](contracts/engine-c-abi-v7.md), [session](contracts/recording-session.md),
[storage](contracts/take-storage.md), [UI](contracts/timeline-recording-ui.md),
[perf](contracts/perf-instrumentation.md); entities in [data-model.md](data-model.md).

## 1. Prerequisites

- The toolchain of feature 009 (`app/README.md`): Gradle 9.7.0 wrapper, Xcode 27.1 as the
  default `Xcode.app`, the iPhone 18 Pro, iPad and iPhone Duo simulators, the Pixel 6 Pro and
  Pixel 7 Pro, the Pixel Tablet, the fold emulator, the OPPO and the Redmi.
- The `recording` journey needs the microphone granted by the runner (already done by the
  pipeline for Android, the simulator and the iPhone 14).
- A 3.5 mm or USB-C loopback cable (headphone output → microphone input) for §8, where the
  device offers a jack or an interface; otherwise the acoustic variant.
- Headphones with a microphone (wired) and wireless headphones for §7.

## 2. Run conditions on devices (the owner's rules of 2026-10-25, applied to every device run)

Before any lane, capture or perf round on a physical device:

```sh
adb -s <serial> shell input keyevent KEYCODE_WAKEUP; adb -s <serial> shell wm dismiss-keyguard
adb -s <serial> shell settings put system screen_brightness_mode 0
adb -s <serial> shell settings put system screen_brightness 0        # the OPPO refuses: dim it by hand
adb -s <serial> shell media volume --stream 3 --set 0                 # music stream silent
adb -s <serial> shell cmd notification set_dnd on                     # no sounds from the system
```

The iPhone 14 is dimmed by `PerfJourneyTestCase`; set its ringer to silent and the volume to
zero by hand; the Mac stays muted. Device UI tests run behind the black
`Theme.Tembra.DeviceTest` window. The only step that makes a sound is §8's loopback check,
run with a cable where possible, and announced before it starts.

## 3. Build, test lanes, gates

| Lane | Command | Must show |
|---|---|---|
| Engine | `./gradlew engineHostTest` | the Catch2 files of the ABI contract green, `trace_poison` green with the new RT sources |
| JVM | `./gradlew checkAndroidHost` | `RecordingPolicyTest`, `RecordingControllerTest`, `TakeAdopterTest`, `LiveOverviewTest`, the editor's `recordTake` tests, the document golden (updated), tokens parity |
| Simulator | `./gradlew checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` (and the iPad, the Duo) | the same plus every `uiTest` class of the UI contract |
| Device | `gradlew-device <serial> checkAndroidDevice` per Android target (serialised; the device lock) | the same `uiTest` classes, `NativeAudioEngineRecordingTest`, `AndroidMicrophonePermissionTest` |
| Perf | `./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf`; `cd perf && uv run pytest` | the perf build with the new sections; the pipeline suite with the sixth journey |
| Compose report | `./gradlew :shared:ui:assembleRelease -PcomposeReports=true` | every new composable skippable; `RecordingUi`, `LiveTakeUi` stable |
| Warnings | every command with `--warning-mode all` | zero |

`gradlew-device` is the launcher of feature 009 (`ANDROID_SERIAL="$1"; shift; exec ./gradlew "$@"`),
kept at `~/.cache/tembra-009-scratch/gradlew-device`; this feature adds it to the repository
as `app/tools/gradlew-device.sh` so sub-agents find it.

Delegation: the lane runs, the screenshot captures (§9) and the per-device perf rounds (§10)
are handed to Sonnet-class sub-agents, one target per agent, each with the serial, the
commands above, the run conditions of §2 and the instruction to report the exact failing test
names; the main agent keeps the engine, the session and the renderer code and reviews every
sub-agent result against the contract's expected list.

## 4. US1 — arm and record a take (P1)

1. Open the demo project; tap "+ Track"; tap the new track's arm toggle. Expect: the toggle
   lit, the meter on the header within 300 ms, the platform's microphone indicator on.
2. Set the count-in to Off in the metronome sheet; press Record. Expect: playback and a growing
   clip within 100 ms, the waveform drawing as you speak, the readout's length growing past the
   demo's end.
3. Press Record again. Expect: the take ends without a click, "Take 1" finalised within 1 s,
   playback continuing, the track still armed, the project saved (relaunch shows the clip).
4. Press Record, then Pause. Expect: the take ends and playback pauses in place.
5. Record "Take 2" over "Take 1" from bar 3 to bar 7. Expect: Take 1 trimmed to bars 1–3, no
   overlap, one undo restores Take 1 and removes Take 2, redo brings Take 2 back.
6. With no track armed and a track selected, press Record. Expect: that track arms and
   records. In an empty project, press Record. Expect: "Track 1" is created, armed and records.
7. Press Record while playing. Expect: the take starts at the heard position with no count-in.

## 5. US2 — the meter (P1)

Arm a track; play quietly, loudly, then overload. Expect: the meter follows within 50 ms, the
hold mark stays about 1.5 s, the clip square lights at full scale and stays until tapped or the
track is disarmed; compact header: a bar along the bottom edge, 56 dp height unchanged;
expanded header: beneath the gain readout.

## 6. US3 — the count-in (P2)

Count-in 1 bar at 124 BPM in 4/4, metronome toggle off, transport stopped; press Record.
Expect: four clicks with the accent first, the Record control pulsing per beat, the readout
"Count-in 4 … 1", the playhead still, the take and playback starting on the next beat, the
click silent afterwards. With the toggle on: the click continues. Two bars in 7/8: fourteen
clicks. Record, Pause, space or Escape during the count-in: cancelled, nothing recorded.

## 7. US4 and US5 — monitoring, permission, interruptions, routes (device table)

| Check | Steps | Expect |
|---|---|---|
| Monitoring on headphones | wired headphones; arm; toggle "Monitor input" | the instrument heard through the track's gain, mute, solo and the master; stays on during a take |
| Speaker guard | no headphones; tap the toggle | stays off, snackbar "Connect headphones to monitor the input" |
| Unplug while monitoring | monitoring on; unplug | monitoring stops at once; a running take continues |
| Wireless headphones | pair AirPods or similar; arm; record | the built-in microphone records (speak near the phone), playback stays full quality through the headphones; the headset's microphone is never used (iOS: no HFP route in Settings' audio route) |
| Wired headset microphone mid-take | record; plug a wired headset with a mic | the take ends, kept, "Take ended: the microphone changed"; the next take uses the headset mic |
| Permission denied | reset the permission; arm; deny | the track stays unarmed; snackbar with "Open settings"; grant in settings, return, arm → armed without a prompt |
| Permission revoked while armed | revoke in settings during a take | the take ends and is kept; disarmed; the message shown |
| Call during a take | record; receive a call | the take ends and is kept to the call; playback stops; nothing resumes; the track stays armed and the meter returns after the call |
| Screen lock | record; lock the screen two minutes | the take continues; the platform's ongoing-recording indication shown |
| Force quit | record 20 s; force-quit | relaunch shows the take with at least the first 18 s |
| Leaving | record; tap Tuner | "Stop recording and leave" / "Keep recording"; confirm → take kept, paused, Tuner listens; cancel → recording continues |
| Tuner hand-over | arm; tap Tuner | disarmed before the Tuner listens; the Tuner works as before |
| iOS arm during playback | play; arm | a short output gap (measure it with a loopback recording; expected ≤ 30 ms); the position continues; record the value in the notes |
| 30-minute cap and space | record 30 min / fill the disk below 50 MB | the take ends itself, kept, with the notice; Record refused below 200 MB |

## 8. Alignment and monitoring delay (SC-001, SC-004; the one sounding step)

1. Loopback cable from the headphone output to the microphone input (or the interface's
   loopback). Where no cable is possible: the device's speaker at the lowest audible volume and
   its built-in microphone, announced in the session before starting.
2. Open the demo project, metronome on, count-in 1 bar; arm a new track; record five minutes.
3. Export nothing: open the take's WAV from the project folder (`adb pull` /
   the simulator's container / the iPhone's Files) and measure each click's onset against the
   beat grid (`tools/measure-take-alignment.py <wav> --bpm 124 --rate 48000`, written in this
   feature: onset detection on the click's transient, the offset per beat, the mean and the
   maximum).
4. Expect on the Pixel 6 Pro and the iPhone 14: every click within 10 ms of its beat, first to
   last. Record the mean offset per device and route in `evidence/alignment.md`; every other
   device's number goes there too (the spec's assumption).
5. Monitoring delay: monitoring on, a single click fed to the input through the cable, the
   output recorded by the same loopback; the delay between the input click and its monitored
   copy ≤ 30 ms on the reference devices.
6. Compare the `audio.takeAlignmentMs` and `audio.monitorDelayMs` counters of the perf round
   with the measured values; a disagreement above 5 ms is a bug in the latency sampling.

## 9. Adaptive, input and accessibility checks (US6)

Screenshots per FR-031 on Android (360, 432, 600, 840, 1024 dp through `wm density`, phone
landscape, the fold's postures) and the iOS simulators (iPhone 18 Pro, iPad, the Duo) with the
`ScreenshotMatrix` states extended: armed at rest / loud / clipped, count-in, recording, the
finished take, a take over clips before and after, monitoring on and suspended, the denied
snackbar, the stop-recording dialog, the sheet with the count-in, the bar with Record at 432 dp,
the armed compact header at 360 dp, the largest font. Keyboard (iOS lane and desktop mode):
A, R, I, Escape, space, tab order. TalkBack and VoiceOver by ear: the arm toggle, Record's
three states, the meter's level and "clipped", the growing clip, the monitor toggle's reason.

## 10. Performance

1. Build the measurement artefacts (§3) and run a trial round: `cd perf && uv run tracepulse run
   --device pixel6 --label 010-recording --rounds 1 --no-publish --no-build --apk … --test-apk …`;
   the `recording` journey must complete with every section present and
   `audio.inputOverruns = 0`.
2. `uv run tracepulse profile generate --device pixel6`; commit both profiles; `profile verify`;
   `profile compare --journeys recording` into `evidence/profile.md`.
3. After the merge: the labelled sessions of the perf contract on every available device
   (`--rounds 5`), `sync`, `report --open`; read the "last session" block: every SUSPECT or
   CONFIRMED onset inside the session and every `above_limit` notice is either explained in
   `evidence/perf-session.md` (with the counter or section, the device and the cause) or fixed
   before the pull request is closed. The startup family must stay within the gate.

## 11. Stitch sync (after the implementation)

Following features 005 (T028) and 006 (T072): render the new states from the app's captures as
Stitch screens (phone 390 wide, tablet 1280): the armed header with the meter, Record in the bar
and in the compact header, the count-in readout, the monitoring toggle on and suspended, the
denied snackbar, the stop-recording dialog and the sheet's count-in; upload each as a new screen
to project `12040171611712589599` with the `stitch-design:upload-to-stitch` script; place them
with `PATCH /v1/projects/{p}?updateMask=screenInstances` (keyed on `sourceScreen`), hide the
superseded instances; re-pull `design/stitch/screens.json` and the slugs (`design/stitch/README.md`
§Re-pull); add the four tokens to the design-system prose (seeds and prose only, never a full
`update_design_system`).

## 12. Troubleshooting

- Arm fails on Android with `ERR_DEVICE`: another app holds the microphone exclusively (a
  recorder, a call); or the Exclusive request failed and the Shared fallback did too — check
  `logcat` for `AAudio`.
- iOS records silence: the session was not `playAndRecord` before the unit restarted; check
  `IosAudioSession.activateForRecording` ran before `arm`.
- Takes land late on one device: compare `audio.takeAlignmentMs` with §8's measurement; on
  Android check both streams' `calculateLatencyMillis` succeed (a stream without timestamps
  returns an error and the engine falls back to the buffer size).
- The live clip does not grow: `readPeaks` returns a gap (the ring overran because the 10 Hz
  poll stalled); the controller repeats the previous bucket — check the main thread for a jank.
- A `.take-*.json` without a file after a crash: discarded on the next open; nothing to do.
