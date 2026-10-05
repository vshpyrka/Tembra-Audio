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
| 5, User Story 3 (the live level on the track) | UI module host tests; UI tests on the iPhone 17; `NoiseGateSliderTest` (11 tests) on the Pixel 6 Pro | all passed, zero warnings |
| 6, User Story 4 (remembered, off by default) | `checkAndroidHost`; `checkIos` on the iPhone 17; `checkAndroidDevice` on the Pixel 6 Pro (core 86, engine 67, data 45, ui 512) | all passed, zero warnings; every ViewModel test that existed before passes unedited |
| 7, User Story 5 (every form, keyboard, pointer, screen reader), first pass on all five targets, 2026-10-06 | `checkAndroidHost`; `checkIos` on the iPhone 17 (537 UI tests), the iPad Pro 13-inch (M5) and the iPhone Duo; `checkAndroidDevice` on the Pixel 6 Pro and the fold emulator together | Workstation and the three simulators: all passed, zero warnings. Pixel 6 Pro: core 86, engine 67, data 45, ui 530, all passed. Fold emulator: core 86, data 45; engine 66 of 67 (`MetronomeTimingEndToEndTest`, spacings 490 to 516 ms, as in the baseline); ui 529 of 530: `theSliderIsATouchTargetTallAndItsHandleIsOneInBothDirections` measured the handle's box at 43.9 dp, see "44 dp on the pixel grid" under Findings; the test was corrected and passes in the second pass below |

On the Pixel 6 Pro the app was launched with the test signal `e2-55db` (2026-10-05): with the handle at "Off" the meter
read E2 in tune; a tap at the middle of the track (about −50 dB) rested it ("Listening", pill unlit); a tap back at the
start read E2 again. The "Test signal" pill stayed throughout. On the iPhone 17 simulator the same behaviour is covered
in this phase by the slider's UI tests and the end-to-end test; the app run there follows with the launch argument (T061).

Test-first notes: every new engine and Kotlin test was run and seen failing before its implementation.
The UI tests of User Story 1 were written before the slider existed; their red step was the compile failure
of the missing state and intent. `NoiseGateEndToEndTest` was written after the analyzer applied the gate, so it was checked the other way round: with
the analyzer deliberately made to ignore the gate, its two gate cases failed on the iPhone 17 simulator; with the
line restored they pass.

On the Pixel 6 Pro with the test signal `e2-45db` (2026-10-05): with the handle at the middle of the track the fill is
violet (`primary`) and runs past the handle to about 58% of the track, where −45 dB lies, and the meter reads 82.4 Hz;
with the handle moved past it the fill turns grey (`outline`), ends short of the handle, and the meter shows
"Listening".

Remembered across launches on the Pixel 6 Pro (2026-10-05): the handle was set to the middle of the track with
`e2-45db` playing, the app was force-stopped and started again with the quieter `e2-55db` and no gate argument. The
handle stood where it had been left, the fill was grey and short of it, and the meter showed "Listening" from the
start. The phone's gate was then put back to "Off". The same check on the iPhone 17 simulator follows in T061, where
the launch argument makes the handle settable without touch.

Test-first notes for User Story 5 (2026-10-06): the state and poll cases failed to compile before `gateOpen` and
`soundAboveGate` existed. With only the slider's new parameter added, nine UI tests failed on the iPhone 17 simulator
for the reasons they name: no announced label or state on the slider, no control in the sideways, tabletop and book
forms, and the gauge scrolling in a 320 x 568 window and in a 360 x 548 content area. The size, arrow-key, hover and
vertical-drag cases of the slider passed at once, because Material's slider and the handle of User Story 1 already
behave that way; they stay as guards. The two launch-argument parsers and `useNoiseGate` were likewise seen failing
(unresolved) before they were written.

## Findings

- **Material keeps 10 dp of its own on each side of a slider (2026-10-05, UI tests).** The slider's node is 20 dp
  wider than the room the handle and the track use, so the track's ends are 32 dp inside the node with a 44 dp
  handle, not 22 dp. On screen nothing changes (the handle's box starts where the "Off" label ends), but a test that
  assumes the track's place is wrong away from the middle: the slider's tests measure it from the handle's real
  position.

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

- **A scroll asked for through semantics is animated (2026-10-06, UI tests).** The layout test holds the frame clock
  (the tuner draws every frame while it listens). `performScrollTo` asks the container to scroll, which starts a
  spring, and then waits for the node to be whole; with the clock held the spring never moves and the test spun at
  full CPU until it was killed. The test now asks for the scroll once and runs the clock for a second and a half.
- **44 dp on the pixel grid (2026-10-06, fold emulator).** At the emulator's density of 2.4375 a 44 dp box is
  107.25 px and is laid out as 107 px, which reads back as 43.9 dp. The handle's box is what was asked for; the test
  now allows the half dp that Compose's own size assertions allow. On the Pixel 6 Pro (3.5) and the simulators it is
  44 dp exactly.
- **The app's root does not take a `:shared:core` type (2026-10-06, build).** `:androidApp` depends on `:shared:ui`,
  `:shared:engine` and `:shared:data` only, so `App(tunerNoiseGate: NoiseGate?)` did not compile there. The launch
  value travels as `TunerNoiseGateRequest`, a type of `:shared:ui` that holds the gate, the way the metronome's
  `MetronomeDebugRequest` does.
- **The mode is remembered, so a target left in By Ear shows no control (2026-10-06, fold emulator).** The emulator
  had By Ear stored from feature 005. The capture tool's layout check failed there, as it should; it now says so in
  words. The emulator was switched to Auto Listen by a tap on the selector.
- **Simulator captures now come upright (2026-10-06).** With the CoreSimulator of the Xcode 27.1 beta in charge,
  `simctl io screenshot` of a simulator held sideways is already wider than tall (2622 x 1206 on the iPhone 17);
  earlier it was the upright framebuffer with the screen lying inside it. The tool turns a sideways capture only when
  it is taller than wide.
- **The gauge's least width is a matter of its text (2026-10-06, largest font on the Pixel 6 Pro).** `meterMinWidth`
  (200 dp) was set for the standard font size. Fitting the gauge over the control made it possible for a phone at
  the largest font to show a 238 dp gauge whose "Flat" and "Sharp" overlapped. Over the control the least width is
  now 200 dp times the font scale, capped at the card's width; when the gauge cannot have that and the control
  together, the card scrolls. The sideways and tabletop gauges of features 003 and 005 keep their rule (they scroll
  at that font size already) and were not changed.
- **The Mac locks itself in the middle of a long run (2026-10-06).** Its display sleeps after ten minutes without
  the owner and the session locks; scripted clicks and keystrokes do not count as the owner. Every step that needs
  the Simulator or Device Hub window has to be done before that, or with the owner at the machine.
- **On a large tablet the control is far from the gauge (2026-10-06, iPad Pro capture).** It sits at the foot of the
  card, as decided in clarification, and the gauge is centred in the room above it; on the 13-inch iPad that leaves
  about a third of the card empty between them. Nothing is cut or overlapped. It is a matter of taste for the owner:
  the alternative is to keep the two together as one centred block.

## Verification on the targets (2026-10-06)

Every target held the debug build of the working tree. The Android app was installed with `:androidApp:installDebug`;
the iOS app was built with `xcodebuild` (Xcode 26.6 for the iPhone 17 and the iPad Pro, the Xcode 27.1 beta for the
iPhone Duo, into `build-duo`) and installed with `simctl install`. Both `xcodebuild` runs print the linker warning
about `libicu` being built for a newer simulator version and the "no AppIntents.framework" note; both are in the
notes of 2026-10-03 (`docs/research/iphone-duo.md`) and neither comes from this feature. The Gradle lanes had zero
warnings.

### Final lanes (after the last change to the code)

| Lane | Target | Result |
|---|---|---|
| `engineHostTest` | workstation | 128 of 128, up to date: no engine source changed since User Story 2 |
| `checkAndroidHost` | workstation | passed, zero warnings |
| `checkIos` | iPhone 17 | passed (547 UI tests), zero warnings |
| `checkIos` | iPad Pro 13-inch (M5) | passed, zero warnings |
| `checkIos` | iPhone Duo | passed, zero warnings |
| `checkAndroidDevice` | Pixel 6 Pro | core 86, data 45, ui 540: all passed, zero warnings. engine: 66 of 67; `NativeAudioEngineTest.theStreamProbeSeesAnOpenOutput` ("the probe did not see the tone's output stream") failed once. The engine module was run again at once on the same build and passed 67 of 67. The test is feature 004's, no engine source has changed since User Story 2, and it had passed in every earlier run of this feature on this phone; the cause of the one failure was not found |
| `checkAndroidDevice` | fold emulator | not run in the final state: the emulator was offline. Its last run is the first pass of User Story 5 above |

SC-002: no assertion of an earlier test about a reading or a screen was edited in this phase; the three layout test
files of the tuner gained cases and kept every existing one.

### Compose compiler report (`-PcomposeReports=true`, `:shared:ui`)

`NoiseGateSlider`, `TembraSliderThumb`, `AutoListenPane`, its `MeterOverFooter` and `FittedMeter`,
`TunerSettingsColumn`, `TunerTabletopSettings` and `TunerScreenContent` are all restartable and skippable. Every
parameter this feature added is stable (the gate, the two callbacks, the two flags, the announced state, the slots,
the interaction source). The parameters the report does not mark stable were there before: `telemetry:
PitchTelemetry` (an interface, so its stability is decided at run time; it is one instance for the ViewModel's
life), `reading: MeterReading?` (the 10 Hz reading, which is meant to recompose the gauge) and, in the two settings
cards, `preset` and `presets` (they hold lists and stay the same instances between selector changes). With strong
skipping these are compared by instance, so the control is not recomposed by a new reading: `TunerScreenContent`
builds it from the fields it shows, not from the whole state.

### Captures and the layout check

`tools/capture-noise-gate.sh` made three captures for each row: `-off` (`e2-in-tune`, gate off), `-below`
(`e2-55db`, gate −50) and `-above` (`e2-45db`, gate −50). Each was looked at: in `-off` the note is E2, the handle is
at "Off" and the fill is violet to the end of the track (the signal is louder than the scale's top); in `-below` the
gauge says "Listening" and the fill is grey and stops short of the handle; in `-above` the note is E2 and the fill is
violet and runs past the handle. A small probe that reads the handle's place and the fill's end off a capture gave
the handle at −50.0 dB and the fill's end at −55.1 dB and −45.1 dB on the Pixel 6 Pro and on the iPhone 17: the fill
ends where the signal's level is on the handle's own scale.

| Target | Form | Files (`…-off/-below/-above.png`) | Where the control is | Layout check (Android) |
|---|---|---|---|---|
| Pixel 6 Pro | upright | `pixel-6-pro-upright-…` | foot of the card, under the gauge, the card's width | control (122, 2581, 1288, 2735) inside 1440 x 3120, clear of the gauge and the four selectors |
| Pixel 6 Pro | sideways | `pixel-6-pro-sideways-…` | settings column, under the mode selector | control (491, 888, 1279, 1042) inside 3120 x 1440, clear |
| Fold emulator | opened flat, upright | `pixel-9-pro-fold-upright-…` | foot of the card, centred, 480 dp at most | control (549, 1913, 1664, 2020) inside 2076 x 2152, clear |
| Fold emulator | opened flat, sideways | `pixel-9-pro-fold-sideways-…` | the same | control (587, 1837, 1702, 1944) inside 2152 x 2076, clear |
| Fold emulator | tabletop | `pixel-9-pro-fold-tabletop-…` | controls card below the fold, under the mode selector; the gauge alone above it | control (470, 1684, 1819, 1791) inside 2152 x 2076, clear |
| Fold emulator | book | `pixel-9-pro-fold-book-…` | controls card before the hinge, under the mode selector; the gauge after it | control (242, 1360, 943, 1467) inside 2076 x 2152, clear |
| iPhone 17 | upright | `iphone-17-upright-…` | foot of the card, under the gauge | no layout dump on iOS; the layout tests ran on this simulator |
| iPhone 17 | sideways | `iphone-17-sideways-…` | settings column, under the mode selector | the same |
| iPad Pro 13-inch (M5) | upright | `ipad-pro-13-upright-…` | foot of the card, centred | the same |
| iPad Pro 13-inch (M5) | sideways | `ipad-pro-13-sideways-…` | foot of the card, centred | the same |
| iPhone Duo | closed, upright (outer display) | `iphone-duo-closed-upright-…` | foot of the card, under the gauge in its compact form; the rail at the end edge | the same |
| iPhone Duo | open, upright | `iphone-duo-open-upright-…` | foot of the card, the card's width | the same |
| iPhone Duo | open, sideways | `iphone-duo-open-sideways-…` | foot of the card, centred, under the compact gauge | the same |

Review against FR-016, FR-020 and SC-008: in all thirty-nine captures the control is whole, inside the screen, not
cut by an edge, a bar, the rail or the fold, and clear of the gauge, the selectors and the pill; the handle and the
track are those of the metronome's tempo slider. Two things are noted for the owner under Findings: the distance
between the gauge and the control on a large tablet, and the control being wider than the gauge on the Duo's open
display held upright (there the card's inner width is about 520 dp and the gauge's cap is 340 dp).

How the targets were turned: the Android targets through `user_rotation` (the tool) and `cmd device_state state 1|2`;
the iPhone 17 and the iPad Pro through the Simulator's Device ▸ Orientation menu by AppleScript; the iPhone Duo
through Device Hub's toolbar (the "Open" pose button, then the rotate button), pressed with System Events clicks at
the buttons' places in the window.

### Turning and folding while listening behind the gate (`e2-45db`, gate −50)

| Target | What was done | Result |
|---|---|---|
| Pixel 6 Pro | upright → sideways → upright (`pixel-6-pro-turn-1…3`) | E2 on the gauge each time, the handle at the middle each time |
| Pixel 6 Pro | the handle then moved by touch to about −40 dB, above the sound; upright → sideways → upright | "Listening" and a grey fill short of the handle each time: a value set by touch survives the turn and the launch value does not come back |
| Fold emulator | flat → tabletop → book → flat, ten times, with the layout dump and a capture after every change (thirty changes; `pixel-9-pro-fold-cycle-10-…` are the last three) | every time: one control, inside the display, clear of the gauge and the selectors; the fill in its active colour (the sound is still read); the handle at 0.500 of the slider in flat and tabletop and 0.499 in book, with no spread over the ten visits (SC-012) |
| iPhone 17 | upright → sideways → upright (`iphone-17-turn-1…3`) | E2 and the handle at the middle each time |
| iPad Pro | upright → sideways → upright (`ipad-pro-13-turn-1…3`) | the same |
| iPhone Duo | closed → open (sideways) → turned upright, the app left running | E2 and the handle at the middle after each change, seen in the Device Hub window |

### The largest font size

Pixel 6 Pro with `font_scale 2.0`, iPhone 17 with the content size `accessibility-extra-extra-extra-large`.

**A defect was found and fixed here.** Over the control the gauge had been fitted to the height left in its compact
form at 238 dp, and at twice the font size its "Flat" and "Sharp" labels were drawn over each other (both
platforms). The gauge's least width over the control is now `meterMinWidth` times the font scale, at most the card's
width; a failing case in `TunerNoiseGateLayoutTest` came first (320 x 568 at font scale 2: "the gauge is 200 dp wide,
under the 264 dp its text needs"). After the fix (`pixel-6-pro-upright-largest-font.png`,
`…-largest-font-scrolled.png`, `iphone-17-upright-largest-font.png`): the gauge takes the card's width with "Flat"
and "Sharp" apart, the card scrolls, the control's label shows at the foot and after a scroll the control is whole
with every label whole. Sideways (`pixel-6-pro-sideways-largest-font.png`) the control is whole in the settings
column and the gauge scrolls in its own card, as it did before this feature.

Still as before this feature, and written into feature 003's notes: at the largest font size the gauge's readout row
("82.4 Hz • Reference 82.4 Hz") is wider than a phone and is cut by the card's edges.

### Keyboard and pointer

Pixel 6 Pro, hardware-key events through `adb shell input keyevent`: five presses of Tab took the focus from the
selectors to the slider, the handle showed its halo and the focus ring (`pixel-6-pro-upright-keyboard-focus.png`),
three presses of Right moved the handle from "Off" to the third step (−74 dB by the probe) and one of Left moved it
back one (−76 dB). The level was then put back to "Off" with two more.

Not driven: the pointer's hover on Android (`adb shell input` cannot make one; the halo under an injected hover is
asserted by `NoiseGateSliderTest.theHandleShowsHoverPressAndFocus` on every target), and the keyboard and the pointer
on the iPad simulator and on the fold emulator opened flat; see "Not driven".

### Live sound through the real microphone

It was after midnight at the workstation (00:38 EEST) and its output volume stood at 13 %. No test tone was played:
the loudspeaker checks are under "Not driven". What needs no deliberate sound was done.

| Target | Check | Result |
|---|---|---|
| Pixel 6 Pro, its own microphone | launched with the gate off and no test signal | "Mic active" on the pill, Android's green microphone indicator in the status bar, `appops` reports `RECORD_AUDIO` running, source `UNPROCESSED` |
| Pixel 6 Pro | the quiet room, three captures a second apart | no fill at all: the room is under −80 dB, the start of the scale |
| Pixel 6 Pro | the handle moved six times by touch while listening (−52, −40, −70, −32, −52 dB, "Off") | after every move the pill still said "Mic active" and the recording session was the same one (929): the stream was not restarted (FR-003, SC-005) |
| iPhone 17 simulator, the Mac's microphone (input volume 76) | launched with the gate off | "Mic active"; the fill in its active colour |
| iPhone 17 simulator | the quiet room, thirteen captures | the fill ends between −60.5 and −66 dB, mostly near −64 |
| iPhone 17 simulator | relaunched with the gate at −64 dB (at the room's level) | fill active in all three captures while the level moved between −59.8 and −66.1 dB: once open, the gate stayed open down to 3 dB under its level, with no flicker in those captures |
| iPhone 17 simulator | relaunched with the gate at −58 dB (over the room) | fill muted in all three captures (−60.3 to −62.9 dB) |

### The scale checkpoint (research R8, quickstart §6): not finished

| Sound | Pixel 6 Pro | iPhone 17 simulator (the Mac's microphone) |
|---|---|---|
| Q, the quietest room | under −80 dB | −60.5 to −66 dB |
| B, a pitched background | not measured | not measured |
| I, the instrument's stand-in | not measured | not measured |

Q is within the rule (at most −60 dB) on both, by a wide margin on the phone and by a narrow one through the Mac's
microphone, whose own noise and input gain are not a phone's. The instrument's level decides whether the scale's
ends are kept, and it was not measured; the checkpoint stays open for the owner.

### Remembered across launches

| Target | Result |
|---|---|
| Pixel 6 Pro | done on 2026-10-05 (above); again today the level set by the keyboard was at "Off" after a relaunch with no gate argument |
| iPhone 17, iPad Pro, iPhone Duo | launched with a test signal and no gate argument on builds updated over ones that never had the setting: the handle is at "Off" and the −45 dB signal is read (FR-014's default). Setting a level and reading it back after a restart was not driven (it needs input into the simulator window); the stored value itself is covered on each simulator by `DataStoreTunerPreferencesTest` in its `checkIos` run |
| Fold emulator | not driven: it went offline |

A fresh install was not made on any target: uninstalling would have removed the owner's remembered choices from that
target. The default on a device that has no stored value is what the three simulator launches above show.

### Not driven, why, and how to finish

1. **The Mac's screen locked at about 00:43** (its display sleeps after ten minutes without the owner). From then on
   AppleScript and System Events could not reach the Simulator or Device Hub windows, and the lock was not worked
   around. Waiting for an unlocked screen:
   - keyboard and pointer on the iPad Pro simulator (T064): Tab to the slider, arrows one step, the focus ring; then
     I/O ▸ Input ▸ Send Pointer to Device and a move over the handle for the halo;
   - a level set by the keyboard on the iPhone 17, the iPad Pro and the iPhone Duo, the app closed and opened again
     with no gate argument (T067);
   - the handle moved while the simulator listens through the microphone, with "Mic active" and the Mac's own
     microphone indicator watched (T065).
2. **The fold emulator went offline at about the same time** (`adb: device offline`, then nothing listening on
   127.0.0.1:5556; it does not run on this Mac, so it could not be started again from here). Before that it had run
   its lanes, its four forms of captures and the ten fold cycles. Waiting for it: the corrected handle-size test
   (it measured 43.9 dp there in the first pass; `:shared:ui:connectedAndroidDeviceTest` with
   `-Pandroid.testInstrumentationRunnerArguments.class=audio.tembra.ui.tuner.NoiseGateSliderTest`), a level
   remembered across a restart (T067), and the keyboard on its inner display opened flat (T064).
3. **Loudspeaker tones were not played at night** (T065, T066): the pitched background named with the gate off and
   resting the gauge for 95 % of thirty seconds behind a gate above it; a louder tone read within a quarter of a
   second; the levels B and I. Quickstart §5 now has the command that writes the tone; with it and
   `afplay -v <volume>` the two tasks take about ten minutes.
4. **For a person**, as in quickstart §9: a real guitar and cello with a television on; VoiceOver and TalkBack
   reading and adjusting the control on a physical device; the owner's iPhone 14 (skipped by the owner's decision);
   a physical foldable.

## Verification matrix (data-model.md §10)

"Lanes" is the last full run on that target: PASS, or what failed. "End-to-end" is `NoiseGateEndToEndTest` inside
that run. Captures are named under "Captures and the layout check".

| Target | Orientation / posture | Lanes | End-to-end | Structural check | Captures | Live sound | Not driven, and why |
|---|---|---|---|---|---|---|---|
| Pixel 6 Pro | upright | final state: core 86, data 45, ui 540 passed; engine 66 of 67, then 67 of 67 on the rerun (see "Final lanes") | pass | pass | 3, reviewed; turn, largest font, keyboard | microphone: quiet room under −80 dB; six moves of the handle, the stream never restarted | loudspeaker tones (night) |
| Pixel 6 Pro | sideways | the same run | pass | pass | 3, reviewed; largest font | as upright | as upright |
| Fold emulator | flat, upright | first pass: core 86, data 45, engine 66 of 67 (metronome timing, as the baseline), ui 529 of 530 (handle size by rounding, since corrected) | pass | pass | 3, reviewed | not attempted | second pass, remembered level, keyboard: the emulator went offline |
| Fold emulator | flat, sideways | the same run | pass | pass | 3, reviewed | not attempted | the same |
| Fold emulator | tabletop | the same run | pass | pass | 3, reviewed; ten fold cycles | not attempted | the same |
| Fold emulator | book | the same run | pass | pass | 3, reviewed; ten fold cycles | not attempted | the same |
| iPhone 17 | upright | `checkIos` passed in the final state | pass | layout tests | 3, reviewed; turn, largest font | the Mac's microphone: quiet room −60.5 to −66 dB; gate under and over it | loudspeaker tones (night); keyboard, remembered level, handle moved while listening (screen locked) |
| iPhone 17 | sideways | the same run | pass | layout tests | 3, reviewed | as upright | as upright |
| iPad Pro 13-inch (M5) | upright | `checkIos` passed in the final state | pass | layout tests | 3, reviewed; turn | not attempted | keyboard, pointer, remembered level (screen locked); loudspeaker tones |
| iPad Pro 13-inch (M5) | sideways | the same run | pass | layout tests | 3, reviewed | not attempted | the same |
| iPhone Duo | closed, upright | `checkIos` passed in the final state | pass | layout tests | 3, reviewed | not attempted | remembered level (screen locked); loudspeaker tones |
| iPhone Duo | open, upright | the same run | pass | layout tests | 3, reviewed | not attempted | the same |
| iPhone Duo | open, sideways | the same run | pass | layout tests | 3, reviewed | not attempted | the same |
