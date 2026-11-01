# Feature 010 — screenshots and the final pass

Captures per FR-031 (T057, T061, T066, T070, T080) and the lane results of the final pass (T092) on
every attached target. Run conditions of quickstart §2 applied before every device run; the only
sounding step (the loopback check of §8) is recorded in `../evidence/alignment.md`.

## Final pass (T092)

| Target | Lane | Result | Counts | Notes |
|---|---|---|---|---|
| Host (JVM, every module) | `engineHostTest checkAndroidHost` | green, zero warnings | 929 JVM tests (ui 473, core 246, data 142, engine 68) + 40 Catch2 cases | 2026-10-28 02:56 at commit b737335 |
| Engine (C++) | `engineHostTest` | green | 40 Catch2 cases | 2026-10-28 (T044) |
| iPhone 18 Pro simulator | `checkIos` | green, zero warnings | core, engine (incl. NativeAudioEngineRecordingTest), data, ui (every uiTest class) | 2026-10-28 02:5x after US6 |
| iPad Pro 13-inch (M5) simulator | `checkIos` | green, zero warnings | core, engine, data, ui (every uiTest class) | 2026-10-28 02:52, 1 min 14 s, commit 02a4593 |
| iPhone Duo simulator | `checkIos` | green, zero warnings | core, engine, data, ui (every uiTest class; test tasks executed, binaries cached) | 2026-10-28 02:51, 27 s, commit 02a4593 |
| Pixel 6 Pro (19021FDEE001AH) | `checkAndroidDevice` | green: engine 105, core 246, data 152, app (02:28 lane, commit 99dde16 tree); ui 975 green on the re-run (02:43–02:55, 12 min 23 s) | 1478 device tests | After the lanes the engine's recording test failed twice on an input HAL in a bad state (`in_get_mmap_position: pcm_sync_ptr failed: File descriptor in bad state`, `getInputForAttr EX_SECURITY`) with no client holding the microphone; the device was rebooted at 02:59, was absent from USB until after 03:45 and at 06:01 is back as `unauthorized`: the owner accepts the USB-debugging prompt on it. The device-test wait for the duplex start was made generous (5 s) meanwhile. |
| Pixel 7 Pro (33241FDH3000NZ) | `checkAndroidDevice` | _pending_ | | |
| Pixel Tablet (3621105H803UFX) | `checkAndroidDevice` | _pending_ | | |
| Fold emulator (emulator-5554) | `checkAndroidDevice` | _pending_ | | |
| OPPO CPH2239 (O7PBYLZP4HMBW8S4) | `checkAndroidDevice` | _pending_ | | brightness by hand (ColorOS refuses adb) |
| Redmi Note 5 (e9446ac2) | `checkAndroidDevice` | _pending_ | | API 28; Play Protect prompt answered "Don't send" |
| iPhone 14 (00008110-001049660E3A401E) | perf trial round (T086) | **blocked**: the Perf build-for-testing succeeded (session 20261028T005156Z_010-recording) but the runner reported "Timed out while enabling automation mode" | | the phone is locked; the owner unlocks it (passcode after updates), then `uv run tracepulse run --device iphone14 --label 010-recording --rounds 1 --no-publish` |

## Release builds (T093)

| Target | Build | Installed | Commit |
|---|---|---|---|
| iPhone 18 Pro, iPad Pro 13-inch (M5), iPhone Duo simulators | `tools/release-ios.sh --no-launch` (Release, iphonesimulator) | yes, `audio.tembra.appP5QJJ5Y5V7` | 724c8ef (2026-10-28 03:25–03:38) |
| iPhone 14 | `tools/release-ios.sh --no-launch` (Release, iphoneos) | yes (installed while locked, not launched) | 724c8ef |
| Android targets | `./gradlew :androidApp:assembleRelease` done at 03:35 (R8, the committed baseline profile); `tools/release-android.sh --no-build` install _pending_ until the device lanes finish | | b737335 tree |

## Trial round (T086)

- iPhone 14: blocked by the locked phone (see the final-pass table).
- Pixel 7 Pro (the Pixel 6 Pro being offline): session `20261028T014141Z_010-recording` with the prebuilt perf APKs ran the
  `RecordingJourneyBenchmark` twice and it failed both times (`Tests run: 1, Failures: 1`; the pipeline's instrumentation
  log under `app/perf/data/sessions/20261028T014141Z_010-recording/`). The journey was written against the built UI but never
  driven on a device before this run; the first debugging step is to read that log for the step that did not find its
  anchor (the microphone grant happens through `pm grant` before the round; the count-in readout wait is non-fatal).
  Resolved 06:10: the journey arms `timeline.track.arm.4` right after "+ Track", and on a phone the new row lands below the
  visible lanes (the list did not scroll), so the tap hit another row. `TrackList` now scrolls a newly added, selected
  track into view (the screen test `aNewlyAddedTrackIsScrolledIntoViewOnAPhone`); the perf APKs are rebuilt and the round
  re-run on the Pixel 7 Pro afterwards: that second round (`20261028T041246Z_010-recording`) failed in the timeline journey's
  first step because the Android lanes agent was re-running the engine device tests on the same phone at the same time
  (two instrumentations cannot share UiAutomation; logcat 06:35–06:39); a third round follows once the lanes are done.

## Captures (T080)

_pending_: Android at 360, 432, 600, 840 and 1024 dp (`wm density`), phone landscape, the fold's
postures; iOS through `ScreenshotMatrix` with the recording states.

## Device checks (T057, T061, T066, T070, T075)

_pending_.
