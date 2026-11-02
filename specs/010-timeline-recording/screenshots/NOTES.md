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
| iPhone 14 (00008110-001049660E3A401E) | perf trial round (T086) | **blocked**: 2026-10-28 "Timed out while enabling automation mode" (locked); 2026-11-01 twice `refused: runner_trusted` (rounds 20261101T192951Z, 20261101T193730Z) | | the owner trusts the TembraPerfTests developer profile (Settings → General → VPN & Device Management) and keeps the phone unlocked, then `uv run tracepulse run --device iphone14 --label 010-recording --rounds 1 --no-publish`; the Release build is installed (724c8ef) |

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
