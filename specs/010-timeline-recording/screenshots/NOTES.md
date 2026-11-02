# Feature 010 — screenshots and the final pass

Captures per FR-031 (T057, T061, T066, T070, T080) and the lane results of the final pass (T092) on
every attached target. Run conditions of quickstart §2 applied before every device run; the only
sounding step (the loopback check of §8) is recorded in `../evidence/alignment.md`.

## Final pass (T092)

| Target | Lane | Result | Counts | Notes |
|---|---|---|---|---|
| Host (JVM, every module) | `engineHostTest checkAndroidHost` | green, zero warnings | 929 JVM tests (ui 473, core 246, data 142, engine 68) + 40 Catch2 cases | 2026-10-28 02:56 at commit b737335 |
| Engine (C++) | `engineHostTest` | green | 40 Catch2 cases | 2026-10-28 (T044) |
| iPhone 18 Pro simulator | `checkIos` | green, zero warnings | core, engine (incl. NativeAudioEngineRecordingTest with the arm settle), data, ui (every uiTest class) | 2026-11-01 21:43 at 691dcbc + the header-test click fix (also green 2026-10-28 at 02a4593, 9288305) |
| iPad Pro 13-inch (M5) simulator | `checkIos` | green, zero warnings | core, engine, data, ui (every uiTest class) | 2026-10-28 02:52, 1 min 14 s, commit 02a4593 |
| iPhone Duo simulator | `checkIos` | green, zero warnings | core, engine, data, ui (every uiTest class; test tasks executed, binaries cached) | 2026-10-28 02:51, 27 s, commit 02a4593 |
| Pixel 6 Pro (19021FDEE001AH) | `checkAndroidDevice` | green: engine 105, core 246, data 152, app (02:28 lane, commit 99dde16 tree); ui 975 green on the re-run (02:43–02:55, 12 min 23 s) | 1478 device tests | After the lanes the engine's recording test failed twice on an input HAL in a bad state (`in_get_mmap_position: pcm_sync_ptr failed: File descriptor in bad state`, `getInputForAttr EX_SECURITY`) with no client holding the microphone; the device was rebooted at 02:59, was absent from USB until after 03:45 and at 06:01 is back as `unauthorized`: the owner accepts the USB-debugging prompt on it. The device-test wait for the duplex start was made generous (5 s) meanwhile. |
| Pixel 7 Pro (33241FDH3000NZ) | `checkAndroidDevice` | green after the engine re-run | core 246, data 152, engine 105 (the arm test failed in the full lane, 105/105 on the re-run), ui 976 | 2026-11-01, `final-pass-android.md` |
| Pixel Tablet (3621105H803UFX) | `checkAndroidDevice` | green after the engine re-run | core 246, data 152, engine 105 (105/105 on the re-run), ui 976 | 2026-11-01, `final-pass-android.md` |
| Fold emulator (emulator-5554) | `checkAndroidDevice` | recording test green on the re-run; two timing/probe flakes and three screenshot-capture flakes of earlier features remain | core 246, data 152, engine 105 (2 flakes), ui 976 (3 capture flakes) | 2026-11-01, the full lane took 2 h 27 min, `final-pass-android.md` |
| OPPO CPH2239 (O7PBYLZP4HMBW8S4) | `checkAndroidDevice` | green with two engine recording tests skipped (ColorOS refuses the runner's microphone grant); TrackHeaderTest green on the re-run | core 246, data 152, engine 105 (2 skipped), ui 977 | 2026-11-01, brightness by hand (ColorOS refuses adb), `final-pass-android.md` |
| Redmi Note 5 (e9446ac2) | `checkAndroidDevice` | green, 0 failures | core 246, data 152, engine 105, ui 977 | 2026-11-01 06:25, 9 min 27 s; API 28; Play Protect prompt answered "Don't send" |
| iPhone 14 (00008110-001049660E3A401E) | perf trial round (T086) | green on the third narrowed run of 2026-11-02: round `20261102T085708Z`, the recording journey alone, 11 of 20 iterations kept (XCTest drops an iteration whose signpost set differs from the first; badge `short-iterations`) | countIn 1978 ms, arm 604 ms, recordStart 15 ms, recordStop 38 ms, takeAlignment 20 ms, projectSave 22 ms, waveform 5.7 ms; journey 40.6 s; peak memory 165 MB; no frames family (the count-in interval draws no Core Animation frames) | 2026-11-01 twice `refused: runner_trusted`, then trusted; 2026-11-02 two journeys passed but the rounds were refused on thermal (the whole target ran) until `--journeys` narrowed the run (8def2ce); the phone's stuck spinner at 08:00 needed `devicectl device reboot`; `evidence/trial-round.md` |

Re-run of 2026-11-02 after the track-list fix (2176267): the host lane (`checkAndroidHost`, zero warnings, 09:0x) and
the simulator lane on the iPhone 18 Pro (`checkIos`, 980 tests in 133 suites, 0 failures, 09:21) green; the pipeline's
own suite 353 green, 1 skipped, the one pre-existing failure (the stale `regressions/r2-frames.patch`).

## Release builds (T093)

| Target | Build | Installed | Commit |
|---|---|---|---|
| iPhone 18 Pro, iPad Pro 13-inch (M5), iPhone Duo simulators | `tools/release-ios.sh --no-launch` (Release, iphonesimulator) | yes, `audio.tembra.appP5QJJ5Y5V7` | 44b58bc (2026-11-01 22:58–23:10; the first attempt at 22:55 raced a concurrent simulator build and was retried) |
| iPhone 14 | `tools/release-ios.sh --no-launch` (Release, iphoneos) | yes (installed while locked, not launched) | 44b58bc (2026-11-01 23:10) |
| Pixel Tablet, Redmi Note 5, OPPO CPH2239, fold emulator | `./gradlew :androidApp:assembleRelease` at 22:55 (R8, the committed baseline profile) + `tools/release-android.sh --no-build --no-launch` | yes (the Redmi runs from the APK with the JIT, as before: its profile broadcast returns 1) | 20e974f (2026-11-01 22:57) |
| Pixel 6 Pro | `tools/release-android.sh --no-build --no-launch --serial 19021FDEE001AH` | yes, 2026-11-02 00:30, compiled from the baseline profile | 20e974f tree (the 22:55 APK) |
| Pixel 7 Pro | `tools/release-android.sh --no-build --no-launch --serial 33241FDH3000NZ` | yes, 2026-11-02 00:40, compiled from the baseline profile | 20e974f tree (the 22:55 APK) |

Second pass of 2026-11-02 (the regenerated profile a831049 and the track-list fix 2176267 inside), installed AND launched,
each target reporting `audio.tembra.app/audio.tembra.android.MainActivity` as the focused activity:

| Target | Build | Installed and launched | Commit |
|---|---|---|---|
| Pixel 6 Pro, Pixel Tablet, OPPO CPH2239, fold emulator | `./gradlew :androidApp:assembleRelease` at 08:22 (R8, the regenerated baseline profile, 25 MB) + `tools/release-android.sh --no-build` | yes, 08:30, compiled from the baseline profile (speed-profile); the OPPO refused the brightness settings (dimmed by hand), its profile compile worked this time | 0630113 tree |
| Redmi Note 5 | same | yes, 08:30, runs from the APK with the JIT (profile broadcast result 1); it refused `set_dnd` | 0630113 tree |
| Pixel 7 Pro | same, after its reference round | yes, 08:40, compiled from the baseline profile (speed-profile) | 0630113 tree |
| iPhone 14, iPhone 18 Pro, iPad Pro 13-inch (M5), iPhone Duo | `tools/release-ios.sh` (Release, iphoneos + iphonesimulator) | yes, 09:17, launched on all four (`audio.tembra.appP5QJJ5Y5V7`, pids reported by devicectl and launchctl) | c9dbb96 tree |

## Owner findings of 2026-11-02 (Phase 10: T098–T101)

The owner's hands-on check of the Release build on the iPad Pro 13-inch (M5) simulator and the iPhone 14 after T093 surfaced
three defects. The evidence was read from the simulator's app container, the CoreAudio log and the feature's own captures;
each fix was written test-first (the test watched failing, then passing) and is listed in `tasks.md` Phase 10.

| Finding | Evidence | Fix | Test |
|---|---|---|---|
| The first take after launch on iOS is a stereo WAV whose channel 1 is all zeros (the take plays left-only; the owner read it as "not recording") | The owner's take of 19:29 and every first take in the iPad and iPhone 18 Pro simulator containers (10-28 too): `ch0` live at −34 dBFS peak, `ch1` 0/157199 non-zero samples; the CoreAudio log shows the first arm's client format at 2 ch against a 1-ch hardware input and later arms at 1 ch; `configureDuplexLocked` reads bus 1's input-scope stream format on a unit that has never run with its input, a 2-channel default | `arm()` bounds the client channels by `AVAudioSession.inputNumberOfChannels` after `activateForRecording()` (`inputChannelLimit`, `IosAudioSession.inputChannels()`) | `InputChannelLimitTest` (pure clamp), `NativeAudioEngineRecordingTest.theFirstArmReportsTheRoutesInputChannels` (end to end; skips in the bare simulator test process, which has no output unit) |
| The input meter never shows in the expanded (pinned) header on tablets and the iPad | The iPad screenshot of 19:31 and the feature's own `pixel-tablet/1024dp-armedRest.png` and `ipad-pro-13-m5/armedLoud.png`: no bar beneath the gain row; 44 + 44 + 4 + 8 + 8 dp of padding exceed the 96 dp lane, so Compose measured the meter at 0 dp (node present, nothing drawn) | `TrackHeader` overlays the meter along the bottom edge inside a `Box`, as `TrackHeaderCompact` does; `meterHeightExpanded` 4 dp (the bottom padding); contract rows updated | `TrackHeaderTest.expandedArmedHeaderShowsTheMeterAlongItsBottomEdge` (failed "not displayed", then green) |
| Arming on the iPhone 14 drops frames | The session scope is `Dispatchers.Main.immediate`; the iOS `arm()` ran the session switch and the RemoteIO restart on the caller's thread (`timeline.arm` 604 ms in the trial round `20261102T085708Z`) while the Android actual hops to IO; `disarm()` restarts the unit the same way | `NativeAudioEngine.ios.kt` runs `arm()` and `disarm()` under an injected serial blocking dispatcher (`Dispatchers.IO.limitedParallelism(1)`); `TimelineAudio.disarm()` is `suspend`; the controller launches `closeInput` on the session scope (never cancelled; the engine core's command lock serialises a close against a transport close) | `NativeAudioEngineBlockingDispatchTest` (asserts the hop on the refused path too, so it runs in the bare simulator process; watched failing with the hop disabled) |

Lanes and release after Phase 10 (T101, 2026-11-02 evening, commit 55f6c16):

| Target | Lane / build | Result |
|---|---|---|
| Host (JVM, every module) + C++ | `checkAndroidHost engineHostTest --warning-mode all` | green, zero warnings: 929 JVM tests (ui 473, engine 68, core 246, data 142) |
| iPhone 18 Pro simulator | `checkIos --warning-mode all` | green, zero warnings: engine 105, data 152, ui 981 (core up to date, unchanged) |
| Engine Android device tests | `:shared:engine:compileAndroidDeviceTest` | compiles (the `runBlocking` teardown); the device lane itself was not re-run (no physical Android attached; the fold emulator's full lane takes 2 h 27 min) |
| Fold emulator (emulator-5554) | `:androidApp:assembleRelease` + `tools/release-android.sh --no-build` | release build installed 20:12, compiled from the baseline profile (speed-profile), launched |
| iPhone 14, iPhone 18 Pro, iPad Pro 13-inch (M5), iPhone Duo | `tools/release-ios.sh` (Release, iphoneos + iphonesimulator) | installed and launched on all four, 20:17 |
| iPad Pro 13-inch (M5) simulator, end to end | `ScreenshotMatrix.testRecordingStates` (`TEMBRA_STATES=takeDone`, Perf build): adds a track, arms it (the first arm after launch), records a take through the real UI | the take's WAV is mono (1 channel, 2.60 s, live audio at −40.6 dBFS peak, every sample non-zero), where every first take before the fix was stereo with a silent channel; `e2e-takeDone.png` shows the meter along the armed header's bottom edge. The Release build was reinstalled on the iPad afterwards. |

Audit of the other engine calls on the main-immediate session scope (the owner's question of 2026-11-02, after T100): every
override of both actuals was mapped to its dispatcher and its heavy native or session calls. Off the main thread already:
`openClipSource`, `play`, `seek`, `recordStart`, `recordStop`, `inspect`, `buildOverview`, now `arm` and `disarm`; the tuner's
start/stop/mode switch (the view model's serial `audioDispatcher`; `tuner.modeSwitch` 130 ms and `tuner.load` 106 ms on the
iPhone 14 in round 20261012T101350Z are the cost of a session activation plus a unit start on that phone, paid off the
screen's thread); sound loading (`prepareDispatcher`, `decodeDispatcher`). Still on the caller's thread, which is the main
thread, and of the same shape as the arm: iOS `openTransport` (session activation + RemoteIO create/init/start; the Android
actual hops to IO), `closeTransport` on both platforms (unit teardown + session deactivation on iOS, Oboe close on Android),
the metronome view model's `startMetronome`, `stopMetronome` and the preview click (session activation/deactivation on iOS,
stream open/close on Android), and `startTone`/`stopTone` from the metronome's preview. None of these has a perf section on
iOS, so no round measured them; `timeline.projectOpen` is 0.4 ms on the Pixel 7 Pro because Android's open is dispatched.
Cheap by construction and left on the caller's thread: `pause`, `setArrangement`, `setTempo`, `setTrack`, `setMonitor`,
`recordCancel`, `closeClipSource` (engine commands through the lock-free FIFO or a file close). Recorded in the 010 spec's
session 2026-11-02 for the next feature; not changed here.

The audit's three remaining rows were fixed the same evening, test-first (T102–T104):

| Path | Fix | Test |
|---|---|---|
| iOS `openTransport` (session activation + RemoteIO create/init/start inline) and `closeTransport` on both platforms (unit teardown + session deactivation on iOS, Oboe close on Android) | one serial dispatcher for the whole app, `EngineDispatchers.blocking` in `:shared:engine`; the iOS open and both closes run on it; `TimelineAudio.closeTransport()` is suspend; `ProjectSession.detachTransport` launches the close under `transportMutex` (the hand-over is ordered on that dispatcher instead of synchronous; the tuner's `audioDispatcher` now defaults to the same one, so its start queues behind the close) | `NativeAudioEngineBlockingDispatchTest.openTransportAndCloseTransportHopToTheBlockingDispatcher` (iOS: watched failing with "openTransport ran on the caller's thread (DeviceFailed)"), its Android device twin, `ProjectSessionTest` adapted to the ordered hand-over |
| Metronome `startMetronome`, `stopMetronome`, `previewClick` and the preview's `stopTone` from the view model on the main thread | `MetronomeViewModel(audioDispatcher = EngineDispatchers.blocking)`: Starting and Stopped shown at once, the engine start and stop and the preview on the dispatcher inside atomic non-cancellable launches (a cleared screen still settles the engine), Running only when nothing stopped the click during the start | `MetronomeViewModelTest.playStartsAndPauseStopsTheEngineOnTheAudioDispatcherNotTheCallersThread`, `aPauseDuringTheStartLeavesTheEngineIdleAndTheScreenStopped` (watched failing on the missing parameter); 14 existing cases adapted (the queued start now lands on `runCurrent()`) |

Lanes and release after T102–T103 (T104, 2026-11-02 night):

| Target | Lane / build | Result |
|---|---|---|
| Host (JVM, every module) + C++ | `checkAndroidHost engineHostTest --warning-mode all` | green, zero warnings: 931 JVM tests (ui 475, engine 68, core 246, data 142) |
| iPhone 18 Pro simulator | `checkIos --warning-mode all` | green, zero warnings: core 246, engine 106, data 152, ui 983 (the open/close dispatcher test asserts on the refused path) |
| Fold emulator (emulator-5554) | `:shared:engine:connectedAndroidDeviceTest` filtered to `NativeAudioEngineBlockingDispatchTest` | green: the transport opens and closes on the injected blocking dispatcher (1 test; the full device lane was not re-run) |
Also seen, not a defect: the simulator's status bar draws no microphone indicator (the Pixel Tablet shows Android's green pill), and
the simulator records whatever the Mac's microphone hears at its input volume (76 at the time; the owner's take peaked at −34 dBFS).

## Performance findings (T086, 2026-11-01/02)

- Pixel 7 Pro round `20261101T205913Z_010-recording`: all six journeys, the recording journey 20/20 (22.1 s median;
  frames p50 7.8 ms, p90 11.1 ms, p99 15.2 ms). Every recording section fired except `timeline.monitorToggle` (no
  headphones: the toggle only explains) and `transport.play` (the journey pauses, it never presses play).
- `transport.recordStart` measured 1.97 s and `transport.countIn` 0.02 ms in that round: the count-in's animation
  interval was launched inside the record-start section and suspended there, and Android's thread-local sections
  mis-paired around the suspension. Fixed in fd5d08c (async sections for animation intervals from API 29, the span
  launched after the section closes); the recording-only round `20261101T222912Z` validates it: countIn 1972 ms,
  recordStart 19.8 ms, 20/20 iterations.
- Counters (only the stress journey publishes them): `audio.input_overruns` median 1 (0–2) while recording three
  seconds right after arming under the stress scroll; `audio.starvation`, `audio.xruns`, `audio.over_budget` 0;
  `audio.take_alignment_ms` 130–132 (the Oboe input opened at 118 ms latency in that process) against 17 ms in the
  soak's process (a low-latency input): the counter mirrors what the streams report, the loopback check of §8 is the
  judge of either. An "overrun" here is a short non-blocking read of the input inside the output callback once the
  two warm-up bursts are over (`OboeAudioDevice::onAudioReady`, `CaptureSource::onInput`): the block is zero-padded
  and the lag stays as a one-burst offset; a reservoir read that keeps a burst in hand would absorb it. Follow-up for
  the next iteration, not changed here: the clean soak and the recording journey report 0.
- Gate notices (13 above limit, among them app size +285 % and ttfd +89 %) compare with the Pixel 7 Pro's last
  published session of 2026-10-15 (before features 009 and 010); the reference device's post-merge sessions decide.
- Clean soak on the Pixel 7 Pro (perf build, fresh process, demo + one track, 24:40 take): overruns 0, starvation 0,
  xruns 0, backlog 1152 frames, one callback over budget at 2.98 ms (avg 17 µs). The 30-minute cap was reached only in
  the first soak on the Pixel 6 Pro's release build (the take ended by itself; a later Record started "Take 2").
- Pixel 6 Pro round `20261102T040607Z_010-recording` (2026-11-02, 58 % battery, prebuilt perf APKs): all six journeys,
  the recording journey 20/20 (22.2 s median; frames p50 8.5 ms, p90 11.5 ms, p99 14.5 ms; countIn 1975 ms, recordStart
  4.2 ms, takeAlignment 77 ms, projectSave 34 ms). Stress counters: `audio.input_overruns` 6 (5 to 8) against the Pixel 7
  Pro's 1, `audio.take_alignment_ms` 132, starvation, xruns and over-budget 0, max callback 185 µs (`evidence/trial-round.md`).
- The profile compare on the Pixel 6 Pro lost the recording journey's arm tap once in 49 iterations: the new track was
  still animating into view when the tap landed, the row slid out from under the pointer and Compose cancelled the press
  (no input stream was opened, the meter never appeared). `TrackList` now scrolls the new track into view instantly
  (2176267); the rerun passed 40/40 and the verify reports the regenerated profile present, recognised and compiled
  speed-profile. The profile does not change the recording journey's duration or frame times (the count-in dominates);
  projectSave -41 % and recordStop -49 % are the confirmed gains (`evidence/profile.md`, T087).
- The app-size gate notice (6.97 MB to 26.8 MB on the perf APK) is feature 009's four demo WAV files (17 MB under
  `composeResources/files/demo/`, commit 23dee07), not recording: the native libraries and dex add 9 MB in total.
- What recording costs, measured on the Pixel 7 Pro against a reference round of the pre-010 code (`f47014e`, the
  branch's merge base, built in a detached worktree and measured the same morning with its own pipeline; round
  `20261102T055511Z_009-main-reference`, record copied under `app/perf/data/rounds/`). Medians of one round each:
  app size +0.6 % (26.64 to 26.80 MB); startup ttid 238 to 248 ms (+4 %), ttfd 349 to 365 ms (+5 %); the tuner,
  metronome and timeline journeys unchanged (1783/1782 ms, 2970/2987 ms, 29.49/29.53 s) with frame p50/p90 within 2 %
  and p99 within 10 % either way; timeline memory peak +2.6 %. The stress journey is longer by design (16.3 to 25.9 s:
  it now records a three-second take and undoes it) and its memory peak grows 149 to 181 MB (+21 %), the recording
  path's buffers and the take's adoption: a follow-up to size, not a regression of the playing journeys. The gate's
  "+86 % ttfd" and "+285 % size" notices against the 2026-10-13 baselines are therefore feature 009's (and ttfd is now
  reported after the timeline's first project draw, not at the first frame).
- iPhone 14 (round `20261102T085708Z`, the recording journey alone, 11 of 20 iterations kept): countIn 1978 ms, arm
  604 ms (the section wraps the input's start on iOS; on Android it wraps the controller call, 0.15 ms), recordStart
  15 ms, recordStop 38 ms, takeAlignment 20 ms, projectSave 22 ms, waveform 5.7 ms, journey 40.6 s, peak memory 165 MB,
  59 fps and no hitches during the count-in. Two follow-ups for the pipeline: XCTest discarded 7 then 9 of 20
  iterations because one signpost kind was present in iteration 0 and absent later (the collector now records the
  kept iterations under the badge `short-iterations`, 9b5d9ac; the kind is still to be identified, the candidates
  are the sections that fire only on a first open or on an optional tap), and the count-in interval draws no Core
  Animation frames for XCTest, so the frames family is absent on iOS as it is for the metronome's tempo change.

## Captures (T080)

_pending_: Android at 360, 432, 600, 840 and 1024 dp (`wm density`), phone landscape, the fold's
postures; iOS through `ScreenshotMatrix` with the recording states.

## Device checks (T057, T061, T066, T070, T075)

What the automated runs cover: every `uiTest` class of the feature on the Pixel 6 Pro, the Pixel 7 Pro, the Pixel
Tablet, the Redmi and the three simulators (the arm toggle, Record in both placements, the growing take, the meter's
levels from scripted telemetry, the count-in menu and readout, the monitor toggle's three states, the dialog variant,
the denied snackbar, the keyboard map); the engine's duplex capture on the Pixels (arm, a 2-bar take with a count-in,
the WAV header, the alignment); the recording perf journey (arm → count-in → take → stop → undo) on the Pixels and the
iPhone 14 in the trial round; the captures of §9 at the five widths and the fold postures.

What needs a person, a cable or a call (the owner's rows of quickstart §4–§7, not run here):
- §4.2 the waveform drawing as you speak (the room was silent under the run conditions), §4.7 Record while playing by ear.
- §5 the meter following a real level within 50 ms, the hold and the clip latch at full scale (needs sound into the mic).
- §6 the count-in heard at 124 BPM in 4/4 and 7/8 with the toggle off and on (sound off during the runs).
- §7 "Monitoring on headphones", "Speaker guard", "Unplug while monitoring", "Wireless headphones" (headphones), "Wired
  headset microphone mid-take", "Call during a take" ×10, "Screen lock" ×10, "Force quit" ×10, "Permission revoked while
  armed", "iOS arm during playback" gap by loopback, "30-minute cap and space" (a debug `maxFrames`, a filled volume).
- §8 the loopback alignment and the monitoring delay (the one sounding step; `app/tools/measure-take-alignment.py`).
- T081 TalkBack and VoiceOver by ear; T094 the owner's placement verdict for Record at 360 dp.
