# Feature 010 — screenshots and the final pass

Captures per FR-031 (T057, T061, T066, T070, T080) and the lane results of the final pass (T092) on
every attached target. Run conditions of quickstart §2 applied before every device run; the only
sounding step (the loopback check of §8) is recorded in `../evidence/alignment.md`.

## Final pass (T092)

| Target | Lane | Result | Counts | Notes |
|---|---|---|---|---|
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | ui 466 + core/engine/data/app | 2026-10-28 02:1x, after US1; re-run after US6 pending |
| Engine (C++) | `engineHostTest` | green | 40 Catch2 cases | 2026-10-28 (T044) |
| iPhone 18 Pro simulator | `checkIos` | green, zero warnings | core, engine (incl. NativeAudioEngineRecordingTest), data, ui (every uiTest class) | 2026-10-28 02:5x after US6 |
| iPad Pro 13-inch (M5) simulator | `checkIos` | green, zero warnings | core, engine, data, ui (every uiTest class) | 2026-10-28 02:52, 1 min 14 s, commit 02a4593 |
| iPhone Duo simulator | `checkIos` | green, zero warnings | core, engine, data, ui (every uiTest class; test tasks executed, binaries cached) | 2026-10-28 02:51, 27 s, commit 02a4593 |
| Pixel 6 Pro (19021FDEE001AH) | `checkAndroidDevice` | engine 105, core 246, data 152, app green; ui 975 re-run _pending_ | | 02:14 run: 3 ui failures fixed in tree (meter tolerance, header semantics) |
| Pixel 7 Pro (33241FDH3000NZ) | `checkAndroidDevice` | _pending_ | | |
| Pixel Tablet (3621105H803UFX) | `checkAndroidDevice` | _pending_ | | |
| Fold emulator (emulator-5554) | `checkAndroidDevice` | _pending_ | | |
| OPPO CPH2239 (O7PBYLZP4HMBW8S4) | `checkAndroidDevice` | _pending_ | | brightness by hand (ColorOS refuses adb) |
| Redmi Note 5 (e9446ac2) | `checkAndroidDevice` | _pending_ | | API 28; Play Protect prompt answered "Don't send" |
| iPhone 14 (00008110-001049660E3A401E) | release build + perf | _pending_ | | no simulator lane on a device |

## Captures (T080)

_pending_: Android at 360, 432, 600, 840 and 1024 dp (`wm density`), phone landscape, the fold's
postures; iOS through `ScreenshotMatrix` with the recording states.

## Device checks (T057, T061, T066, T070, T075)

_pending_.
