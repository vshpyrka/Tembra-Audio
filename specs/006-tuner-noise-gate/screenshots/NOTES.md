# Verification notes: Tuner Noise Gate

Everything below was run from `app/` through the `gradle-run` wrapper with `--warning-mode all` unless a row says
otherwise. A target or a step that could not be driven is named with the reason and the command the owner can run
(spec FR-024).

## Targets (attached 2026-10-05)

| Target | Id | OS |
|---|---|---|
| Pixel 6 Pro (USB) | `19021FDEE001AH` | Android 17 |
| Pixel 9 Pro Fold emulator | `127.0.0.1:5556` | Android 16 |
| iPhone 17 simulator | `4CD6879C-8FBA-4CEF-B4EC-31968D0144B0` | iOS 26.5 |
| iPad Pro 13-inch (M5) simulator | `C9A67FF1-62FC-4BE6-89F9-685DC39FBD46` | iOS 26.5 |
| iPhone Duo simulator | `EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD` | iOS 27.1 |

The owner's iPhone 14 is skipped by the owner's decision of 2026-10-05; the iPhone 17 simulator stands in.

The wrapper runs a Gradle command as the whole shell command, so `ANDROID_SERIAL=` cannot prefix it. A plain
`checkAndroidDevice` therefore runs on both Android targets in one go; one target is chosen with the launcher
described under the baseline table.

## Baseline before any change (T002)

Commit `33b3e05` (documents only on top of `main` at `e045835`).

| Lane | Target | Result |
|---|---|---|
| `engineHostTest` | workstation | 108 of 108 passed |
| `checkAndroidHost` | workstation | passed (test results from the build cache: nothing had changed since their last green run) |
| `checkAndroidDevice` | fold emulator | `MetronomeTimingEndToEndTest.ticksAreFiveHundredMillisecondsApartAndTheFirstArrivesQuickly` failed, as in feature 005's notes (the emulator's audio clock); every other test passed, including all 485 UI tests. This is the "fails in the same way without this feature" evidence of FR-024 |
| `checkAndroidDevice` | Pixel 6 Pro, first run (both targets together) | the first part of the UI suite failed with "No compose hierarchies found in the app": the phone had been idle and asleep when the lane started |
| `checkAndroidDevice` | Pixel 6 Pro alone, rerun with the phone awake | 655 of 656 passed; `TempoDialTest.draggingOnTheRingMapsTheAngle` failed once with the same "No compose hierarchies found" (the test activity did not come up; a known flake) |
| `checkAndroidDevice` | Pixel 6 Pro alone, from scratch at the owner's request (awake, unlocked, no test packages left over) | 656 of 656 passed: core 76, engine 56, data 39, ui 485 |
| `checkIos` | iPhone 17 | passed |

Two facts about the Pixel 6 Pro found on the way: it reports AC power (`mPlugType=1`), so its "stay awake while
plugged in over USB" setting (2) is not honoured and it sleeps after its 30-minute screen timeout; every device lane
below is preceded by `adb shell input keyevent KEYCODE_WAKEUP` and `adb shell wm dismiss-keyguard`. And a single
Android target can be chosen through the wrapper with a small launcher script named `gradlew-device <serial> <args>`
(the wrapper accepts any `gradlew*` launcher), which sets `ANDROID_SERIAL` and runs `./gradlew`.

## Progress by phase

| Phase | Lanes | Result |
|---|---|---|
| 2, foundational (level, gate value, revision 4, Kotlin seam, gate type, shared handle) | `engineHostTest` 112 of 112; `checkAndroidHost`; `checkIos` on the iPhone 17; `checkAndroidDevice` on the Pixel 6 Pro (core 86, engine 64, data 39, ui 485) | all passed, zero warnings |
| 3, User Story 1, engine part (gate rule, gated analyzer) | `engineHostTest` 123 of 123; `:shared:engine` iOS tests with `NoiseGateEndToEndTest` on the iPhone 17 | all passed, zero warnings |
| 3, User Story 1, screen part (state, intent, slider, placement under the meter) | `checkAndroidHost`; UI tests on the iPhone 17; `checkAndroidDevice` on the Pixel 6 Pro (core 86, engine 67, data 39, ui 495) | all passed, zero warnings |
| 4, User Story 2 (the 3 dB closing margin) | `engineHostTest` 128 of 128; the engine module's tests on the iPhone 17 and on the Pixel 6 Pro (67 of 67), each with `NoiseGateEndToEndTest` | all passed, zero warnings |

On the Pixel 6 Pro the app was launched with the test signal `e2-55db` (2026-10-05): with the handle at "Off" the meter
read E2 in tune; a tap at the middle of the track (about −50 dB) rested it ("Listening", pill unlit); a tap back at the
start read E2 again. The "Test signal" pill stayed throughout. On the iPhone 17 simulator the same behaviour is covered
in this phase by the slider's UI tests and the end-to-end test; the app run there follows with the launch argument (T061).

Test-first notes: every new engine and Kotlin test was run and seen failing before its implementation.
The UI tests of User Story 1 were written before the slider existed; their red step was the compile failure
of the missing state and intent. `NoiseGateEndToEndTest` was written after the analyzer applied the gate, so it was checked the other way round: with
the analyzer deliberately made to ignore the gate, its two gate cases failed on the iPhone 17 simulator; with the
line restored they pass.

## Findings

- **"At the gate level" is known to about half a decibel (2026-10-05, engine tests).** After the filter a tone scaled
  to exactly the gate level measures a few hundredths of a decibel under or over it, and the window's level ripples by
  up to 0.8 dB on the lowest strings. The tests therefore read "at the gate level" as 1 dB above it; with the 3 dB
  margin a tone wavering a decibel either way around the gate opens once and stays open.
- **Without the margin the fade test still passed.** The smoother's own hold keeps a reading about 85 ms past the
  gate's closing, which at 10 dB a second is already 1 dB under the gate, inside SC-003's 3 ± 2 dB. The margin is
  proven by the gate rule's own tests and by the waver test, which fails without it.

- **A drag trails the finger by the touch slop (2026-10-05, UI test).** Material's slider starts a drag once the
  platform's touch slop is passed, so a finger that travels twelve steps moves the handle about nine. The metronome's
  tempo slider behaves the same way. A touch on the track still puts the handle exactly where the finger is.

- **The gate does not clean the sound (2026-10-05, engine test).** With a pitched background 12 dB under a note
  (G3 under E2), the reading of the note settles about 7 cents flat, with or without a gate: the detector hears the
  mix. The first reading, 45 ms after the note starts, is about 30 cents flat and converges over a quarter of a
  second. The gate stops the background being named when the instrument is silent; it cannot remove it while both
  sound (spec FR-026). The test `a note over a quiet background is read, and the background is never named` pins
  this behaviour.

## Verification matrix (data-model.md §10)

| Target | Orientation / posture | Lanes | End-to-end | Structural check | Captures | Live sound | Not driven, and why |
|---|---|---|---|---|---|---|---|
| Pixel 6 Pro | upright | | | | | | |
| Pixel 6 Pro | sideways | | | | | | |
| Fold emulator | flat, upright | | | | | | |
| Fold emulator | flat, sideways | | | | | | |
| Fold emulator | tabletop | | | | | | |
| Fold emulator | book | | | | | | |
| iPhone 17 | upright | | | | | | |
| iPhone 17 | sideways | | | | | | |
| iPad Pro 13-inch (M5) | upright | | | | | | |
| iPad Pro 13-inch (M5) | sideways | | | | | | |
| iPhone Duo | closed, upright | | | | | | |
| iPhone Duo | open, upright | | | | | | |
| iPhone Duo | open, sideways | | | | | | |
