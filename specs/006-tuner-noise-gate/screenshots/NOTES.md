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
- **A gate within its margin of a wobbling background is held open (2026-10-06, iPhone 17 simulator, loudspeaker).**
  With the gate 1 dB over a background that moves by a decibel, one peak opens it and the background keeps it
  open; 3.5 dB over, it stays shut (29 of 30) and shuts again after a peak. The handle has to go more than the
  3 dB margin above the background; the margin itself behaved as designed and stays at 3 dB.
- **Hardware keys do not reach Compose in the iOS simulator from a script (2026-10-06).** Tab and the arrows, sent
  through System Events or as HID events, with the Simulator's keyboard forwarding on, moved nothing; a forwarded
  pointer drew no hover. The earlier note that Tab moved the shell's focus (2026-09-25) could not be reproduced. On
  iOS the keyboard and pointer contract rests on the UI tests of the iOS lane.
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
| `checkAndroidDevice` | fold emulator | run on 2026-10-06 at 10:17 once the emulator was back: core 86, data 45, ui 540 (the corrected handle-size case among them): all passed; engine 66 of 67, the known `MetronomeTimingEndToEndTest` (spacings 488 to 515 ms), as in the baseline. Zero warnings |

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

Fold emulator opened flat (2026-10-06, 10:40), the same key events: Tab took the focus to the slider, the handle showed
its halo and ring (`pixel-9-pro-fold-keyboard-focus.png`), Right moved it two steps for three presses (the first press
seems to have completed the move of focus into the slider) and Left one step back.

iPad simulator: a hardware keyboard does not reach the app from a script. With the Simulator's "Connect Hardware
Keyboard" on, and also with "Send Keyboard Input to Device" on, Tab pressed up to fourteen times (through System
Events and as HID-level events) moved no focus anywhere in the app and arrows moved nothing; the same on the
iPhone 17. A pointer sent to the device ("Send Pointer to Device") and moved over the handle drew no halo. These two
checks are therefore proven on iOS by the UI tests of the iOS lane (`NoiseGateSliderTest`: arrows, hover, press,
focus; `TunerScreenLayoutTest`: focus order), which ran on the iPad Pro and the iPhone Duo, and by the Pixel's and
the emulator's real runs on Android; a person with a Magic Keyboard and a trackpad on a real iPad can confirm them
(quickstart §9). The pointer's hover on Android cannot be made with `adb shell input` either.

### Live sound through the real microphone

The checks that need no deliberate sound were done at night (00:38 EEST, output at 13 %, no tone played); the
loudspeaker checks followed on the morning of 2026-10-06 with the owner at the machine. The tone is quickstart §5's
(196 Hz with four harmonics) from the MacBook's own loudspeaker; the Pixel 6 Pro lay where the owner had left it,
and the iPhone 17 simulator hears the MacBook's microphone, which also hears its own loudspeaker from a few
centimetres. The output volume was put back to 13 % after every run.

| Target | Check | Result |
|---|---|---|
| Pixel 6 Pro, its own microphone | launched with the gate off and no test signal | "Mic active" on the pill, Android's green microphone indicator in the status bar, `appops` reports `RECORD_AUDIO` running, source `UNPROCESSED` |
| Pixel 6 Pro | the quiet room, three captures a second apart | no fill at all: the room is under −80 dB, the start of the scale |
| Pixel 6 Pro | the handle moved six times by touch while listening (−52, −40, −70, −32, −52 dB, "Off") | after every move the pill still said "Mic active" and the recording session was the same one (929): the stream was not restarted (FR-003, SC-005) |
| iPhone 17 simulator, the Mac's microphone (input volume 76) | launched with the gate off | "Mic active"; the fill in its active colour |
| iPhone 17 simulator | the quiet room, thirteen captures | the fill ends between −60.5 and −66 dB, mostly near −64 |
| iPhone 17 simulator | relaunched with the gate at −64 dB (at the room's level) | fill active in all three captures while the level moved between −59.8 and −66.1 dB: once open, the gate stayed open down to 3 dB under its level, with no flicker in those captures |
| iPhone 17 simulator | relaunched with the gate at −58 dB (over the room) | fill muted in all three captures (−60.3 to −62.9 dB) |

**The loudspeaker checks (quickstart §5, FR-025, SC-010), 2026-10-06 morning.** The levels are read off the track in
captures a second apart; "named" means the gauge shows a note.

| Step | Pixel 6 Pro (its microphone) | iPhone 17 simulator (the Mac's microphone) |
|---|---|---|
| B, the quiet background, gate off | −71.9 to −70.9 dB, named (output 50 %, `afplay -v 0.25`) | −49.8 to −49.0 dB, named (output 13 %, `-v 1`; the daytime room alone reads −50 here) |
| behind a gate above B for 30 s | gate −68 dB (3 dB over B's top): resting in 30 of 30 captures, never named; level −76 to −70.6 dB | first with the gate at −48 dB, 1 dB over B's top: named in 30 of 30, see the margin note. Then with the gate at −46 dB: resting in 29 of 30, named once |
| a louder tone over B | resting at the first capture (about a second; the tone starts late), named at the second and third, −61.6 dB | named at the first capture, −23.6 dB |
| the louder tone stopped | the first capture a second later still showed the note with the fill already muted; resting from the second | resting from the first capture |

**The margin, seen on the simulator.** A gate 1 dB above a background that wobbles by 1 dB is opened by the
background's own peak and then held open for as long as the background stays within 3 dB under the gate: the gauge
named it for the whole thirty seconds. Set 3.5 dB above the background's top, the gate stayed closed in 29 of 30
captures and the one opening (a peak of the room) closed again at once. The margin does what it is for, on the
condition that the user puts the handle more than the margin above the background; quickstart §5 now says so. No
flicker was seen on a steady sound near the gate on either target.

### The scale checkpoint (research R8, quickstart §6)

| Sound | Pixel 6 Pro | iPhone 17 simulator (the Mac's microphone) |
|---|---|---|
| Q, the quietest room | under −80 dB (night and morning) | −60.5 to −66 dB at night; about −50 dB in the morning |
| B, a pitched background | −71 dB | −49.5 dB |
| I, the stand-in for an instrument (half output volume, full gain) | −63.4 to −63.7 dB; with the gate one step under (−66 dB) the fill was active and the note named, one step over (−62 dB) muted and resting | −27.2 dB; one step under (−30 dB) active and named, one step over (−26 dB) muted and resting |

Against the rule (keep the scale if Q ≤ −60 dB and −50 dB ≤ I ≤ −20 dB on both targets): the Pixel's Q passes by a
wide margin and its I fails (−63.5 dB, under −50 dB): the tone came from the MacBook's loudspeaker at whatever
distance the phone lay, not 30 cm, so by the rule the stand-in is not proof and **one pluck of a real guitar at
arm's length on the Pixel 6 Pro is needed from the owner**: launch with `tembra.tuner.noiseGate off`, pluck, read the
fill's end (or capture and run the probe). Through the Mac's microphone I is in range and Q passes at night and
fails by day; that microphone, with its input at 76 % and the laptop's own fans, is not a phone's. Everything heard
on both targets lies on the scale (from under −80 dB to −24 dB), so nothing so far asks for new ends.

### Remembered across launches

| Target | Result |
|---|---|
| Pixel 6 Pro | done on 2026-10-05 (above); again today the level set by the keyboard was at "Off" after a relaunch with no gate argument |
| iPhone 17, iPad Pro, iPhone Duo | at night, launched with a test signal and no gate argument on builds updated over ones that never had the setting: the handle at "Off" and the −45 dB signal read (FR-014's default). In the morning, a level set by a real click into the simulator window (the iPhone 17 at 30 % of the track, −62 dB; the iPad at the middle; the Duo at 30 % through Device Hub), the app closed and opened again with the quieter signal and no gate argument: the handle where it was left each time, the −55 dB signal held back where the level was above it (`iphone-17-remembered.png`, `ipad-pro-13-remembered.png`, `iphone-duo-remembered-devicehub.png`). When the morning began, the iPhone 17 and the Duo already held −50 dB and the emulator −54 dB, set between the night and the owner's "proceed"; the checks above start from a different level for that reason |
| Fold emulator | opened flat: a tap at the middle, the app closed and reopened with the quieter signal: the handle at the middle and the signal held back (`pixel-9-pro-fold-remembered.png`); a tap at "Off" and a restart: "Off" |

A fresh install was not made on any target: uninstalling would have removed the owner's remembered choices from that
target. The default on a device that has no stored value is what the three simulator launches above show.

### Not driven, and why

1. The hardware keyboard and the pointer inside the iOS simulators' app (above): proven by the UI tests of the iOS
   lane and by the Android targets; a person with a Magic Keyboard and a trackpad on a real iPad can confirm them.
2. The pointer's hover on Android: `adb shell input` cannot make one; proven by `theHandleShowsHoverPressAndFocus`.
3. The instrument's level on the Pixel 6 Pro with a real guitar (the scale checkpoint above): the owner's pluck.
4. For a person, as in quickstart §9: a real guitar and cello with a television on; VoiceOver and TalkBack reading
   and adjusting the control on a physical device; the owner's iPhone 14 (skipped by the owner's decision); a
   physical foldable.

Two things stopped the night's session and were finished in the morning with the owner at the machine: the Mac's
screen had locked (its display sleeps after ten minutes without the owner; nothing that goes through the Simulator
or Device Hub windows works then, and the lock was not worked around) and the fold emulator had gone offline (it
does not run on this Mac).

## Verification matrix (data-model.md §10)

"Lanes" is the last full run on that target: PASS, or what failed. "End-to-end" is `NoiseGateEndToEndTest` inside
that run. Captures are named under "Captures and the layout check".

| Target | Orientation / posture | Lanes | End-to-end | Structural check | Captures | Live sound | Not driven, and why |
|---|---|---|---|---|---|---|---|
| Pixel 6 Pro | upright | final state: core 86, data 45, ui 540 passed; engine 66 of 67, then 67 of 67 on the rerun (see "Final lanes") | pass | pass | 3, reviewed; turn, largest font, keyboard | quiet room under −80 dB; six moves of the handle, the stream never restarted; background rested 30 of 30 behind the gate; louder tone read; I −63.5 dB | a real guitar's level (the owner) |
| Pixel 6 Pro | sideways | the same run | pass | pass | 3, reviewed; largest font | as upright | as upright |
| Fold emulator | flat, upright | final state: core 86, data 45, ui 540 passed; engine 66 of 67 (metronome timing, as the baseline) | pass | pass | 3, reviewed; remembered level; keyboard focus | not attempted: no microphone from the workstation reaches it, and §3 stands in | pointer hover (adb cannot make one) |
| Fold emulator | flat, sideways | the same run | pass | pass | 3, reviewed | the same | the same |
| Fold emulator | tabletop | the same run | pass | pass | 3, reviewed; ten fold cycles | the same | the same |
| Fold emulator | book | the same run | pass | pass | 3, reviewed; ten fold cycles | the same | the same |
| iPhone 17 | upright | `checkIos` passed in the final state | pass | layout tests | 3, reviewed; turn, largest font; remembered level | the Mac's microphone: quiet room −61 to −66 dB (night), −50 dB (day); background behind the gate 29 of 30; louder tone read at once; I −27 dB | keyboard and pointer in the simulator app (UI tests stand in) |
| iPhone 17 | sideways | the same run | pass | layout tests | 3, reviewed | as upright | as upright |
| iPad Pro 13-inch (M5) | upright | `checkIos` passed in the final state | pass | layout tests | 3, reviewed; turn; remembered level | not attempted (the same microphone as the iPhone 17) | keyboard and pointer in the simulator app (UI tests stand in) |
| iPad Pro 13-inch (M5) | sideways | the same run | pass | layout tests | 3, reviewed | the same | the same |
| iPhone Duo | closed, upright | `checkIos` passed in the final state | pass | layout tests | 3, reviewed; remembered level | not attempted (the same microphone) | the same |
| iPhone Duo | open, upright | the same run | pass | layout tests | 3, reviewed | the same | the same |
| iPhone Duo | open, sideways | the same run | pass | layout tests | 3, reviewed | the same | the same |

## Recaptured 2026-10-16: the shell has no top bar

The navigation shell's top bar was removed on 2026-10-16 (spec 002 FR-007 reversed at the owner's request), so every
capture here that showed the shell was retaken from that day's build, under the same file name, on the same target
and in the same state. Content now starts right under the status bar (56 dp / pt higher). Other differences come from
features that landed after the original captures and are named below. `*-before*` images and captures from targets
that no longer exist were kept as they were. Android: Pixel 6 Pro `19021FDEE001AH`, the debug APK (no `tembra.perf`
property), `adb exec-out screencap`, app state set with the debug launch extras and UiAutomator taps. iOS: the Debug app
built with the Xcode 27.1 SDK on the iPhone 17 / iPad Pro 13" (M5) (iOS 26.5) / iPhone Duo (iOS 27.1) simulators,
`simctl io screenshot`, app state set with the debug launch arguments, the app's stored preferences (written with the
app stopped, backed up and restored) and real mouse clicks; rotation through the Simulator menu or Device Hub.

### Android, Pixel 6 Pro (13 retaken, 2 left)

Left: `pixel-6-pro-live-background-gated.png`, `pixel-6-pro-live-instrument-over-background.png` (need a real
instrument and room sound).

| File | How | Differs beyond the top bar |
|---|---|---|
| `pixel-6-pro-{upright,sideways}-{off,below,above}.png` | as `tools/capture-noise-gate.sh`: off = `e2-in-tune` + `noiseGate off`, below = `e2-55db` + `-50`, above = `e2-45db` + `-50`; upright / rotation 1. Layout dump: sideways control (491,692)-(1279,846), meter (1650,168)-(2831,1286), apart | text-only selector; sideways the column and gauge start at the top |
| `pixel-6-pro-turn-{1-upright,2-sideways,3-upright}.png` | `e2-45db`, `-50`, upright → rotation 1 → rotation 0 | same; handle at the middle each time |
| `pixel-6-pro-upright-keyboard-focus.png` | `e2-45db`, gate off, `input keyevent KEYCODE_TAB` × 5 (focus reached the slider on the 5th) | same |
| `pixel-6-pro-upright-largest-font.png` | font 2.0, `e2-45db`, `-50` | **yes (follows from the bar)**: the control is now whole without scrolling |
| `pixel-6-pro-upright-largest-font-scrolled.png` | the same after a swipe on the card | the card no longer scrolls, so it shows the same view as the unscrolled one |
| `pixel-6-pro-sideways-largest-font.png` | font 2.0, rotation 1, `e2-45db`, `-50` | more of the gauge visible |

### iOS simulators (30 retaken, 3 left)

Retaken: iPhone 17 (cello, as the originals) upright and sideways off/below/above, turn 1–3, remembered, largest font;
iPad Pro (guitar) upright and sideways off/below/above, turn 1–3, remembered; Duo (guitar) closed upright, open upright
and open sideways off/below/above (Device Hub: Open, then Rotate Right for upright). Off/below/above use the capture
tool's launches (`e2-in-tune`/off, `e2-55db`/−50, `e2-45db`/−50); turns `e2-45db`/−50 rotated while running.
Remembered: the stored gate set to the originals' level (iPhone −62 dB, iPad −50 dB) and launched with `e2-55db` and no
gate argument (instead of a click on the track).

Left: `iphone-17-live-background-gated.png`, `iphone-17-live-instrument-over-background.png` (real sound, as asked) and
`iphone-duo-remembered-devicehub.png` (726 × 816: a Device Hub window shot with the device bezel, not an app capture).

Differs beyond the top bar: `iphone-17-upright-largest-font` now shows the whole noise gate control at the foot (the
card gained the 56 pt); that follows from the top bar.

### Android, Pixel 9 Pro Fold emulator (17 retaken, 0 left)

The Pixel 9 Pro Fold emulator was recreated the same afternoon (AVD `Pixel_9_Pro_Fold`, Android 16, Google APIs arm64, installed with `sdkmanager`), so its captures were retaken too: 17 retaken, 0 left.

Target: `emulator-5554` (AVD `Pixel_9_Pro_Fold`, Android 16, recreated today). Inner display id unchanged:
`4619827259835644672` (display 0, 2076 x 2152 px at 390 dpi). App: the debug APK
`app/androidApp/build/outputs/apk/debug/androidApp-debug.apk` (13:16 build, no `tembra.perf`), installed with
`adb install -r`, then `pm grant` RECORD_AUDIO and POST_NOTIFICATIONS. Captures with
`adb exec-out screencap -p -d 4619827259835644672`. Navigation by UiAutomator dumps and taps at node centres.

Initial emulator state (recorded before any change, restored at the end): `device_state` OPENED (2) with no override,
`accelerometer_rotation 1`, `user_rotation 0` (ROTATION_0), `font_scale 1.0`, no `wm size` override. Unlike the old
AVD, the new one boots flat and upright, not half-opened and rotated.

Postures used: flat = `cmd device_state state 2` + rotation 0; flat sideways = state 2 + rotation 1; tabletop = state
1 + rotation 1 (the old AVD's tabletop was ROTATION_90 as well); book = state 1 + rotation 0. Rotation locked with
`accelerometer_rotation 0` + `user_rotation <n>`. At the end: `device_state state reset`, `user_rotation 0`,
`accelerometer_rotation 1`, `font_scale 1.0`, `wm size reset`. The app's own preferences were put back to the
fresh-install values (guitar, 6-String, Auto Listen, gate Off; metronome 4/4 at 120 BPM) and the app left open on
the Metronome tab.

Differences common to every retake (not repeated below): no top bar, so the content starts right under the status
bar (56 dp = 136 px higher) and cards that fill the height are 56 dp taller; the status bar's clock and icons are
light and readable (the originals show dark icons on the dark bar); the instrument selector is text only (no
pencil/cello icons, the app-icons change of 2026-10-12). The Auto Listen captures of 005 also show the noise gate
control (feature 006 came later).

A plain launch without extras opens Home; a launch with `tembra.tuner.testSignal` opens the Tuner.

All guitar, Auto Listen. The twelve state captures were made with the repo's own tool,
`app/tools/capture-noise-gate.sh --android emulator-5554 --display 4619827259835644672 --name pixel-9-pro-fold
--orientation <o> [--label <l>] --out <scratch>` (off = `e2-in-tune` + `noiseGate off`, below = `e2-55db` + `-50`,
above = `e2-45db` + `-50`), staged in scratch, compared, then copied over the originals. Its layout check passed
every time.

| File | How | Differs beyond the top bar |
|---|---|---|
| `pixel-9-pro-fold-upright-{off,below,above}.png` | `device_state 2`, tool `--orientation upright`. Control (549,1913,1664,2020), the same as the original | no |
| `pixel-9-pro-fold-sideways-{off,below,above}.png` | `device_state 2`, tool `--orientation sideways` (rotation 1). Control (587,1837,1702,1944), the same | no |
| `pixel-9-pro-fold-tabletop-{off,below,above}.png` | `device_state 1`, tool `--orientation sideways --label tabletop`. Control (470,1684,1819,1791), the same; gauge (729,185)-(1580,980) | the upper card is taller, so the gauge is larger |
| `pixel-9-pro-fold-book-{off,below,above}.png` | `device_state 1`, tool `--orientation upright --label book`. Control (242,1292,943,1399), 68 px higher than the original (242,1360,943,1467): the centred controls column gained the 56 dp | gauge card taller |
| `pixel-9-pro-fold-cycle-10-{flat,tabletop,book}.png` | launched flat with `e2-45db` + `-50`, then flat → tabletop → book ten times (thirty changes) with a layout dump and a capture after each; the last three kept. Every change: one control, one gauge, the handle at 0.500 of the slider in all three postures (the original noted 0.499 in book) | no |
| `pixel-9-pro-fold-keyboard-focus.png` | flat, `e2-55db` + `-50`; `KEYCODE_TAB` x 11 (focus goes through the six rail items, Guitar, Cello, Auto Listen and By Ear first), then `KEYCODE_DPAD_RIGHT` x 2: the handle at −46 dB (64 px right of the middle, where the original's is), focus halo and ring shown, "Listening", grey fill short of the handle | no (the slider region is pixel-for-pixel the original's) |
| `pixel-9-pro-fold-remembered.png` | flat, launched with `e2-45db` and no gate argument, a tap at the middle of the track (−50 dB), force-stop, relaunched with `e2-55db` and no gate argument: the handle at the middle, "Listening", fill grey and short of it. Afterwards a tap at the track's start set "Off", which survived a restart | no |

Not failures, but worth knowing: a tap at x 560 (inside the slider node but in Material's own 10 dp padding) did
not move the handle; x 625 did.

Nothing failed.

### Every remaining capture retaken, 2026-10-16 (the owner's request: update all recorded screenshots)

The files kept above as records of past states (`*-before*`, `stacked-cut`, `bar-insets`, the 005 `-r1` set), the
live-sound captures, the notification crop and the Device Hub window shot were retaken too, in the original
configuration with today's build, so no image here shows the removed top bar any more. Where a file recorded a
defect or a first build, it now shows today's app in the same configuration, and the caption written for it above
describes the original image, which stays in git: `git show 42d8e92:<path>` (main at the start of this work).
The live captures used quickstart §5's 196 Hz tone with four harmonics from the MacBook's loudspeaker, as the
originals did; the output volume was put back to 31 % after every run.

#### Android (Pixel 6 Pro, fold emulator): `pixel-6-pro-live-background-gated.png`, `pixel-6-pro-live-instrument-over-background.png`

**Sound.** The quickstart §5 tone (196 Hz with four harmonics, 40 s, 48 kHz mono 16-bit), generated as
`tembra-g3.wav` in this folder (`make_tone.py`). It played from the MacBook's own loudspeaker with `afplay`, with the
output at 50 % as in the original run (read as 31 first and set back to 31 after every run). The Pixel 6 Pro was not
moved. Every run held the speaker lock.

**Reading the level.** The noise gate track on row 2658 runs from x 234 to x 1176 on the −80 to −20 dB scale (the
fill is drawn from the track's start to `fractionOfLevel(level)`). I checked this on the existing
`pixel-6-pro-upright-above` and `-below` captures, which read −45.2 and −55.2 dB, their `e2-45db` and `e2-55db`
signals. Fill colour: primary (206,189,255) means the sound is read, and outline (148,142,157) means the gate holds it
back. Script: `level.py`.

**Measurements, all portrait, guitar, Auto Listen, no test signal.**

| Run | Launch | Sound | Result |
|---|---|---|---|
| quiet room | `noiseGate off` | none | 6 captures: no fill visible (below about −78 dB), gauge resting |
| background B | `noiseGate off` | `afplay -v 0.25` at 50 % | 10 captures: fill read at −72.9 to −72.1 dB, G3 196.0 Hz named (the original's B read −71.9 to −70.9) |
| behind the gate | `noiseGate -68` (4.1 dB over B's top, the same level as the original) | B | 12 of 12 captures resting ("Listening"), fill held back at −72.9 to −72.5 dB, handle at −68 dB |
| instrument over B | `noiseGate -68` | B, plus the same tone at `-v 1` (12 dB louder) started 3 s later | from 1.5 s after the louder tone started: 6 of 6 captures named G3, 196.0 Hz, in tune, fill read past the handle at −63.2 to −64.4 dB (the original read −61.6) |

**`pixel-6-pro-live-background-gated.png`** is capture 6 of the gated run. It shows MIC ACTIVE, the IN TUNE pill over
"Listening", the needle at rest, and the noise gate handle at −68 dB with a muted grey fill ending short of it at
−72.6 dB. Same as the old file (whose fill ends at about −72.5 dB).

**`pixel-6-pro-live-instrument-over-background.png`** is capture 1 of the louder run. It shows G3, "196.0 Hz •
REFERENCE 196.0 HZ", the needle at 0, and the fill in the read colour past the handle, to −63.2 dB. Same as the old
file (G3, 196.0 Hz, fill to −61.6 dB).

**Differences beyond the top bar, both files:** the instrument selector is text only (no pencil and cello icons), the
status bar time differs, and the louder tone reads 1.6 dB lower than in the original run.

#### iOS (iPhone 17, iPhone Duo simulators): 3 retaken, 0 left

| File | How | Shows now | Differs from the old one |
|---|---|---|---|
| `iphone-duo-remembered-devicehub.png` (726 × 816) | Duo closed, upright. Stored gate set to −62 dB (the old image's handle at 30 % of the track; the original was set by a click through Device Hub), guitar / 6 / Auto Listen; relaunched with `--tembra-tuner-test-signal=e2-55db` and no gate argument. Device Hub's window (id 3253) was 1227 × 848 pt, which drew the device 7 % larger than the old shot; resized to 1227 × 800 pt the display's scale matched the old one exactly (0.354 image px per device px, found by matching the end-rail column), then `screencapture -o -l 3253` and a crop at (1117, 400) 726 × 816 px puts the display at the old place (117.7, 47.9). Window put back to 1227 × 848. Test signal, so no microphone and no speaker lock. | the device in its bezel: Guitar / Cello, TEST SIGNAL pill, Auto Listen, E2 "In tune", 82.4 Hz / reference 82.4 Hz, noise gate handle at −62 dB with the active fill past it (−55 dB); end rail with Tuner lit | top bar gone, so the selectors start at the top and the gauge is larger; instrument chips text only (no icons). Handle and fill at the same places as before. |
| `iphone-17-live-background-gated.png` (1206 × 2622) | iPhone 17 portrait, cello / Auto Listen, real microphone (MacBook Pro Microphone, input volume 76, untouched), no test signal. Room with the gate off, 28 captures in two runs: −63.1 to −42.6 dB; resting in all 8 of the first run, but in the second the gauge named a note in 15 of 20 (G4 / C♯5 / A♯4, 390–560 Hz, changing) (sound in the room while the speaker lock was free, so not the other agent's tone; probably other audio on the Mac, left alone). Gate set more than 3 dB above the room's top (quickstart §5): `--tembra-tuner-noise-gate=-40`, 30 captures a second apart: **resting in 30 of 30**, fill muted at −68 to −44 dB. Kept: capture 27 (fill −50.1 dB). Lock held for the whole run. | "MIC ACTIVE", "Listening", needle at rest, handle at −40 dB, grey (muted) fill ending at −50.1 dB | top bar gone; text-only chips. The gate is at −40 dB instead of −46 dB: today's room peaked at −42.6 dB (the old room read about −50), so the handle sits further right. |
| `iphone-17-live-instrument-over-background.png` (1206 × 2622) | same launch (gate −40), output volume raised from 31 to 50 for the run and `afplay -v 1` of the 196 Hz tone with four harmonics (`tembra-g3.wav`, quickstart §5's recipe, made in this scratch dir) from the MacBook's own speakers; six captures from 1.5 s after the start: named in 6 of 6 at −27.1 to −27.8 dB; kept capture 3 (−27.8 dB). Tone stopped, volume back to 31, lock released. | G3, "In tune", 196.0 Hz / reference 196.0 Hz, handle at −40 dB, violet (active) fill to −27.8 dB | top bar gone; text-only chips; gate −40 dB instead of −46; the tone reads −27.8 dB instead of −24.2 (the old output volume for this step isn't written down; 50 % is §6's instrument stand-in, which read −27.2 dB then). |

Level probe (`probe.py`): track row y 2196, ends x 196 (−80 dB) and 986 (−20 dB); on the old images it reads handle −46.0,
background −50.3 and instrument −24.2 dB, which agrees with the old NOTES.

## Recaptured 2026-10-17: icon-only navigation, no Home (Android)

Home was removed on 2026-10-17 (the app opens on Timeline) and the navigation items show icons only: the Material 3
Expressive bottom bar (active item on a lavender pill) on the phone, a collapsed 96 dp rail (active item on a circle)
on wide windows. Every Android capture here was retaken under the same name, on the same target and in the same
state, from the debug APK of that morning (`:androidApp:assembleDebug`, no `tembra.perf`) with RECORD_AUDIO and
POST_NOTIFICATIONS granted by `pm grant`.

### Android, Pixel 6 Pro (15 retaken, 0 left)

Pixel 6 Pro `19021FDEE001AH` (Android 17), fresh install (the app had been uninstalled from the phone, so the stored
gate was Off), `adb exec-out screencap -p`. Upright captures differ from the 2026-10-16 ones only in the bar (1.4 % of
the frame; the tuner is pixel-identical). Sideways the collapsed rail is wider than the old labelled one, so the
settings column starts further right and is narrower: the control now spans (603, 692)–(1351, 846) instead of
(491, 692)–(1279, 846), still clear of the gauge (1686, 168)–(2867, 1286).

| File | How | Differs beyond the navigation |
|---|---|---|
| `pixel-6-pro-{upright,sideways}-{off,below,above}.png` | retaken with `tools/capture-noise-gate.sh --android 19021FDEE001AH --name pixel-6-pro --orientation upright|sideways --out <scratch>` (off = `e2-in-tune` + gate off, below = `e2-55db` + −50, above = `e2-45db` + −50); its layout check passed all six times | upright no; sideways the narrower column above |
| `pixel-6-pro-turn-{1-upright,2-sideways,3-upright}.png` | retaken: `e2-45db`, −50, upright → `wm user-rotation lock 1` → `lock 0` | no; E2 and the handle at the middle each time |
| `pixel-6-pro-upright-keyboard-focus.png` | retaken: `e2-45db`, gate off, `KEYCODE_TAB` × 5; the dump showed the focus on `tuner.noiseGate.slider` after the 5th (the bar's items come after the slider, as before) | no (focus ring and halo as before) |
| `pixel-6-pro-upright-largest-font.png` | retaken: font 2.0, `e2-45db`, −50 | the bar does not grow with the font any more (no labels), so the meter card is taller: the gauge sits lower and the control is lower in the card; the readout row is still cut at the card's edges (pre-existing) |
| `pixel-6-pro-upright-largest-font-scrolled.png` | retaken: the same after a swipe up on the card | the card does not scroll, so it shows the same view as the unscrolled one (as on 2026-10-16) |
| `pixel-6-pro-sideways-largest-font.png` | retaken: font 2.0, rotation 1, `e2-45db`, −50 | the old rail's labels were cut ("Projec…" at the bottom); the icon-only rail fits whole; the TUNER CONTROLS heading is ellipsised a letter earlier in the narrower column |
| `pixel-6-pro-live-background-gated.png` | retaken under the speaker lock: portrait, guitar, Auto Listen, no test signal, `--es tembra.tuner.noiseGate -68`; the quickstart §5 tone (196 Hz, four harmonics, `tembra-g3.wav` from the 2026-10-16 run) from the MacBook's loudspeaker at `afplay -v 0.25` with the output volume at 50 % (it was 31 % and was put back). The quiet room first (gate off): no fill. Background B with the gate off: −74.6 to −74.3 dB, G3 named. Behind the gate: 12 of 12 captures resting, fill held back at −74.3 to −74.2 dB; capture 6 kept | the background reads about 1.5 dB lower than on 2026-10-16 (−74.3 against −72.9 to −72.1 dB), so the gate at −68 dB sits 6 dB above it instead of 4; the picture is the same (Listening, handle at −68 dB, grey fill short of it) |
| `pixel-6-pro-live-instrument-over-background.png` | the same run (lock held throughout): the same tone at `-v 1` added over B; from 1.5 s after it started, 6 of 6 captures named G3, 196.0 Hz, in tune, fill read past the handle at −64.8 to −65.7 dB; capture 1 kept (−64.8 dB) | the louder tone reads −64.8 dB against −63.2 dB on 2026-10-16; otherwise the same picture |

### Android, Pixel 9 Pro Fold emulator (17 retaken, 0 left)

Target `emulator-5554` (AVD `Pixel_9_Pro_Fold`, Android 16), inner display `4619827259835644672`, the same debug APK
installed with `adb install -r -t` over the existing install (stored gate Off). Initial state, restored at the end:
`device_state` 2 with no override, `accelerometer_rotation 1`, `user_rotation 0`, font 1.0, no `wm size` override; the
app left on Timeline with guitar, 6-String, Auto Listen, gate Off. Postures as on 2026-10-16: flat = `device_state 2` +
rotation 0; flat sideways = state 2 + rotation 1; tabletop = state 1 + rotation 1; book = state 1 + rotation 0.

`tools/capture-noise-gate.sh` could not be used on the emulator today: its layout check runs `uiautomator dump`, which
fails ("null root node returned by UiTestAutomationBridge") or hangs while a test signal animates the meter. The app
rendered the meter at under 1 frame per second on the emulator (`dumpsys gfxinfo`: 50th percentile 1500 ms, 98 % janky
frames; qemu at 200–340 % host CPU), and in that state the test-signal reading wobbles from frame to frame (e2-in-tune
read 82.1–83.1 Hz in single frames). So the same launches were made by a script (force-stop, `am start` with
`--es tembra.tuner.testSignal <id> --es tembra.tuner.noiseGate <gate>`), and each capture is a frame picked from a burst
of up to 20 against a known-good reference of the same posture and reading (meter box and readout band, under 1 % and
0.4 % of their pixels different): the new `upright-off` and `sideways-above` (both 82.4 Hz), feature 005's new
`android-fold-tabletop-autolisten-r1.png` (the same tabletop state, 82.4 Hz) and the old book captures (the card after
the hinge did not move). A first pass that matched against the old captures kept seven wrong frames (upright-above
81.6 Hz, sideways-off 82.2, tabletop-off 83.1, book-off 82.5, book-above 82.6, tabletop-above with the content not
yet drawn, book-below caught in the flat layout mid-change); those seven were retaken, and every kept frame shows
E2 at 82.4 Hz (off, above) or "Listening" (below). The layout itself was checked on the dumps that did work (static
screens) and by eye: in flat upright the control now spans (588, 1913)–(1703, 2020), 39 px right of the original's
(549, 1913)–(1664, 2020), because the collapsed rail is wider; it is inside the display and clear of the gauge and the
selectors in all seventeen captures.

| File | How | Differs beyond the navigation |
|---|---|---|
| `pixel-9-pro-fold-upright-{off,below,above}.png` | retaken: state 2, rotation 0, off / below / above launches | the card is narrower (wider rail): gauge and control 39 px further right; otherwise no |
| `pixel-9-pro-fold-sideways-{off,below,above}.png` | retaken: state 2, rotation 1 | as above |
| `pixel-9-pro-fold-tabletop-{off,below,above}.png` | retaken: state 1, rotation 1 | as above (upper and lower cards) |
| `pixel-9-pro-fold-book-{off,below,above}.png` | retaken: state 1, rotation 0 | the controls card before the hinge is narrower; the gauge card after it is unchanged |
| `pixel-9-pro-fold-cycle-10-{flat,tabletop,book}.png` | retaken: launched flat with `e2-45db` + −50, then flat → tabletop → book ten times (thirty changes, 3 s apart); the tenth cycle's three postures captured (frames picked as above, no layout dump) | no; one control, one gauge, the handle at the middle in all three |
| `pixel-9-pro-fold-keyboard-focus.png` | retaken: flat, `e2-55db` + −50; `KEYCODE_TAB` until the dump showed `tuner.noiseGate.slider` focused, then `KEYCODE_DPAD_RIGHT` × 2: handle at −46 dB (its centre 64 px right of the slider's, as in the original), focus halo and ring, "Listening", grey fill short of the handle | **yes, in the Tab order (not in the picture)**: the focus no longer goes through the rail first. On 2026-10-16 it took 11 Tabs (the six rail items, then Guitar, Cello, Auto Listen, By Ear); now the second Tab is already on Cello and the fifth on the slider, so the new rail's items are no longer ahead of the content in keyboard order. A first attempt with five quick Tabs on the slow emulator left the focus on Auto Listen and was discarded |
| `pixel-9-pro-fold-remembered.png` | retaken: flat, launched with `e2-45db` and no gate argument, a tap at the middle of the slider (x 1145, −50 dB), force-stop, relaunched with `e2-55db` and no gate argument: the handle at the middle, "Listening", fill grey and short of it. Afterwards a tap at the track's start (x 664) set Off, which survived a restart (E2 read, handle at Off) | no |

## Recaptured 2026-10-17: Home removed, icon-only navigation (iOS)

All 33 iOS captures were retaken under the same file names, on the same targets and in the same states. The builds were
Debug, made from the worktree `nav-icons-only` with the Xcode 27.1 SDK. Every capture with a rail was taken a second
time from the build with 41fffa2, in which the collapsed rail centres its items: all 10 iPad, all 10 Duo, and the 4
iPhone 17 sideways ones. The iPhone 17 upright, turn-1 / turn-3, live and largest-font captures, which show the bottom
bar, are from the first build.

- **Targets:** iPhone 17 and iPad Pro 13-inch (M5) (iOS 26.5); iPhone Duo (iOS 27.1; outer display `819E92C0…`,
  inner `D5FE96E5…`).
- **Launches (the capture tool's):** off = `e2-in-tune` with the gate off, below = `e2-55db` with −50, above =
  `e2-45db` with −50. The turns used `e2-45db` with −50 and were rotated while running. Remembered used the stored gate
  (iPhone −62 dB, iPad −50 dB) and a launch with `e2-55db` and no gate argument.
- **Instrument:** cello on the iPhone 17, guitar elsewhere, as in the originals.
- **Rotation:** the Simulator menu for the iPhone and iPad. The Duo used Device Hub's Open, Rotate Right and Closed
  buttons, pressed through the accessibility API, and was put back to Closed and upright.

| File | How | Differs beyond the navigation |
|---|---|---|
| `iphone-17-upright-{off,below,above}`, `iphone-17-remembered` | retaken: portrait, cello | no |
| `iphone-17-sideways-{off,below,above}` | retaken (build with 41fffa2): Landscape Left, cello | **yes, from the wider rail**: the settings column is narrower, so the header reads "TUNER CONTRO…" beside the TEST SIGNAL pill and the gate track is shorter. The handle and fill sit at the same fractions. `-off` also shows the Dynamic Island's black pill, which simctl draws in some captures. |
| `iphone-17-turn-1-upright`, `-turn-3-upright` | retaken: portrait → Landscape Left → portrait while running | no |
| `iphone-17-turn-2-sideways` | retaken (build with 41fffa2): the sideways step of the same turn sequence, run again | the same narrower column as the sideways set |
| `iphone-17-upright-largest-font` | retaken: largest text size, `e2-45db`, −50; text size back to `medium` | no. The bar stays one row of icons, so the card is taller. The readout row is still cut at the card edges; that is pre-existing. |
| `iphone-17-live-background-gated` | retaken: portrait, cello, Auto Listen, real microphone, no test signal. Today's room alone read −75 to −72 dB, so the background was the original B (2026-10-06): quickstart §5's 196 Hz tone at output 13 %, `afplay -v 1`, which read −50.8 to −50.2 dB (named) with the gate off. With `--tembra-tuner-noise-gate=-40` (as the existing image): resting in 20 of 20 captures, fill muted at −50.8 to −50.0 dB. Capture 1 kept (−50.0 dB). Output volume back to 31. | no (handle −40 dB, muted fill to −50.0 dB; the old one was −50.1) |
| `iphone-17-live-instrument-over-background` | retaken: same launch, the tone at output 50 %, `afplay -v 1`: G3, 196.0 Hz, in tune in 6 of 6 captures at −26.5 to −27.2 dB. Capture 6 kept (−27.2 dB). Output volume back to 31. | no (fill to −27.2 dB instead of −27.8) |
| `ipad-pro-13-upright-{off,below,above}`, `-remembered`, `-turn-1-upright`, `-turn-3-upright` | retaken (build with 41fffa2): portrait, guitar | no |
| `ipad-pro-13-sideways-{off,below,above}`, `-turn-2-sideways` | retaken (build with 41fffa2): Landscape Left | no |
| `iphone-duo-closed-upright-{off,below,above}` | retaken (build with 41fffa2): Duo closed, upright, outer display | no |
| `iphone-duo-open-sideways-{off,below,above}` | retaken (build with 41fffa2): Device Hub Open, inner display | no |
| `iphone-duo-open-upright-{off,below,above}` | retaken (build with 41fffa2): Open, then one Rotate Right | no |
| `iphone-duo-remembered-devicehub` (726 × 816) | retaken (build with 41fffa2): Duo closed, upright, stored gate −62 dB, launched with `e2-55db` and no gate argument. Device Hub's window was set to 1227 × 800 pt, captured with `screencapture -x -o -l 3880` and cropped at (1117, 400), 726 × 816. Rail matching puts the display at (117.7, 47.9) at 0.354 scale, as in the existing image. The window was put back to 1227 × 848. | no |

## Recaptured 2026-10-17 (2): hand-drawn Tuner and Metronome glyphs (Android)

The Tuner and Metronome navigation items now show hand-drawn glyphs, a tuning fork with a sound arc on each side and a
pyramid metronome, instead of Material's music_note and av_timer (branch `experiment/nav-glyphs`, 041819b). All 32
Android captures were retaken under the same names, on the same targets and in the same states as the morning's pass,
from the debug APK built from HEAD 2a5d7dc (`:androidApp:assembleDebug` through the gradle-run wrapper), with
RECORD_AUDIO and POST_NOTIFICATIONS granted by `pm grant`.

### Android, Pixel 6 Pro (15 retaken, 0 left)

Pixel 6 Pro `19021FDEE001AH` (Android 17), fresh install (stored gate Off), `adb exec-out screencap -p`. Every capture
was compared with the committed one pixel by pixel (threshold 40/255, status bar excluded): outside the bar or rail
they are identical. Four first captures (`upright-off`, `turn-3-upright`, `upright-largest-font-scrolled`,
`sideways-largest-font`) had caught the IN TUNE pill's dot in another phase of its pulse and were retaken by taking
frames until the content matched the committed image outside the navigation.

| File | How | Differs beyond the two glyphs |
|---|---|---|
| `pixel-6-pro-{upright,sideways}-{off,below,above}.png` | retaken with `tools/capture-noise-gate.sh --android 19021FDEE001AH --name pixel-6-pro --orientation upright|sideways --out <scratch>`; its layout check passed all six times (upright control (122, 2581, 1288, 2735), sideways (603, 692, 1351, 846), as in the morning) | no |
| `pixel-6-pro-turn-{1-upright,2-sideways,3-upright}.png` | retaken: `e2-45db`, −50, upright → `wm user-rotation lock 1` → `lock 0` | no |
| `pixel-6-pro-upright-keyboard-focus.png` | retaken: `e2-45db`, gate off, `KEYCODE_TAB` × 5; the dump showed `tuner.noiseGate.slider` focused | no |
| `pixel-6-pro-upright-largest-font.png`, `-scrolled.png` | retaken: font 2.0, `e2-45db`, −50; then a swipe up on the card | no |
| `pixel-6-pro-sideways-largest-font.png` | retaken: font 2.0, rotation 1 | no |
| `pixel-6-pro-live-background-gated.png` | retaken under the speaker lock: portrait, no test signal, `noiseGate -68`; quickstart §5's 196 Hz tone (`tembra-g3.wav`) from the MacBook at `afplay -v 0.25`, output volume 50 % (31 % before and after). Quiet room first: the room was not quiet this time (three of four frames showed a fill at −67 to −76 dB and a passing note), so the background was louder than in the morning. Background B, gate off: −73.6 to −73.2 dB, G3 named (one frame −60.5 dB from a room sound). Behind the gate: 12 of 12 resting, fill held back at −73.4 to −71.8 dB; capture 6 kept (−73.3 dB) | no: same picture (Listening, handle at −68 dB, grey fill short of it); the fill ends about 1 dB further right than in the morning's capture (−73.3 against −74.3 dB) |
| `pixel-6-pro-live-instrument-over-background.png` | the same run: the tone at `-v 1` added; 6 of 6 named G3, 196.0 Hz, in tune, fill read past the handle at −63.8 to −64.8 dB; capture 1 kept (−63.8 dB) | no: same picture; the fill ends 1 dB further right (−63.8 against −64.8 dB) |

### Android, Pixel 9 Pro Fold emulator (17 retaken, 0 left)

Target `emulator-5554` (Android 16), inner display `4619827259835644672`, the same debug APK installed with
`adb install -r -t` over the existing install. Initial state, restored at the end: `device_state` 2 with no override,
`accelerometer_rotation 1`, `user_rotation 0`, font 1.0, no `wm size` override; the app left on Timeline with guitar,
6-String, Auto Listen, gate Off. Postures: flat = state 2 + rotation 0; flat sideways = state 2 + rotation 1;
tabletop = state 1 + rotation 1; book = state 1 + rotation 0.

The emulator was far faster than in the morning (median frame 65 ms against 1500 ms) and `uiautomator dump` worked on
the static screens again. The launches were scripted as in the morning (force-stop, `am start --es
tembra.tuner.testSignal <id> --es tembra.tuner.noiseGate <gate>`), and each capture is the first of up to 15 frames
that matched the morning's verified capture outside the rail (status bar and gesture handle excluded). A first run
showed By Ear in every frame: feature 005's captures had left the tuner in By Ear, and a test-signal launch keeps the
stored mode. Auto Listen was tapped and the run repeated. Fifteen of the seventeen are identical to the committed
images outside the rail; `cycle-10-flat` and `sideways-above` differ by 11 and 17 px of anti-aliasing at the slider
handle.

| File | How | Differs beyond the two glyphs |
|---|---|---|
| `pixel-9-pro-fold-{upright,sideways,tabletop,book}-{off,below,above}.png` | retaken: off = `e2-in-tune` + gate off, below = `e2-55db` + −50, above = `e2-45db` + −50, in each posture | `upright-off`: needle exactly upright now. The committed frame had it a hair off centre, and the new one matches today's `upright-above` (same reading, 82.4 Hz). The others: no |
| `pixel-9-pro-fold-cycle-10-{flat,tabletop,book}.png` | retaken: launched flat with `e2-45db` + −50, then flat → tabletop → book ten times (thirty changes, 3 s apart); the tenth cycle's three postures captured | no |
| `pixel-9-pro-fold-keyboard-focus.png` | retaken: flat, `e2-55db` + −50; `KEYCODE_TAB` with a dump after each: Guitar, Cello, Auto Listen, By Ear, then the slider on the 5th; `KEYCODE_DPAD_RIGHT` × 2: handle at (1156, 1913)–(1263, 2020), −46 dB, as in the morning | no |
| `pixel-9-pro-fold-remembered.png` | retaken: flat, launched with `e2-45db` and no gate argument, a tap at the slider's middle (x 1145), force-stop, relaunched with `e2-55db` and no gate argument: handle at the middle, "Listening", fill grey and short of it. Afterwards a tap at the track's start (x 664) set Off, which survived a restart | no |

## Recaptured 2026-10-17 (2): hand-drawn Tuner and Metronome glyphs (iOS)

All 33 iOS captures were retaken under the same names, on the same targets and in the same states. The build was Debug,
made from 041819b (`experiment/nav-glyphs`) with the Xcode 27.1 SDK: Tuner shows a hand-drawn tuning fork and Metronome
a hand-drawn pyramid metronome.

- **Targets:** iPhone 17 (cello) and iPad Pro 13-inch (M5) (guitar), iOS 26.5; iPhone Duo (guitar), iOS 27.1.
- **Launches:** off = `e2-in-tune` with the gate off; below = `e2-55db` with −50; above = `e2-45db` with −50. The turns
  used `e2-45db` with −50, rotated while running. Remembered used the stored gate (iPhone −62 dB, iPad −50 dB) and a
  launch with `e2-55db` and no gate argument.
- **Rotation:** the Simulator menu for the iPhone and iPad; Device Hub's Open, Rotate Right and Closed for the Duo,
  which was put back to Closed and upright.
- **Check:** each image was compared with the previous one (HEAD) outside the navigation and the status bar.

| File | How | Differs beyond the two glyphs |
|---|---|---|
| `iphone-17-upright-{off,below,above}`, `-remembered`, `-turn-1-upright`, `-turn-3-upright` | retaken: portrait | no |
| `iphone-17-sideways-{off,below,above}`, `-turn-2-sideways` | retaken: Landscape Left | no. The Dynamic Island's black pill, which simctl draws in some captures only, is now in `-above` and `-below` and not in `-off`. |
| `iphone-17-upright-largest-font` | retaken: largest text size, `e2-45db`, −50; back to `medium` | no |
| `iphone-17-live-background-gated` | retaken: portrait, cello, real microphone, no test signal. Background B, quickstart §5's tone at output 13 % (`afplay -v 1`), read −51.0 to −49.5 dB (named) with the gate off. With the gate at −40: resting in 20 of 20 captures, muted fill at −51.1 to −50.1 dB; capture 19 kept (−50.1 dB). Volume back to 31. | no (fill to −50.1 dB instead of −50.0) |
| `iphone-17-live-instrument-over-background` | retaken: same launch, the tone at output 50 %: G3, 196.0 Hz, in tune in 6 of 6 captures at −27.4 dB; capture 6 kept. Volume back to 31. | no (fill to −27.4 dB instead of −27.2) |
| `ipad-pro-13-upright-{off,below,above}`, `-remembered`, `-turn-1-upright`, `-turn-3-upright` | retaken: portrait | no |
| `ipad-pro-13-sideways-{off,below,above}`, `-turn-2-sideways` | retaken: Landscape Left | `-turn-2-sideways` shows iOS's home indicator at the bottom centre, which the previous one did not; the rest no |
| `iphone-duo-closed-upright-{off,below,above}` | retaken: Duo closed, upright, outer display | no |
| `iphone-duo-open-sideways-{off,below,above}`, `iphone-duo-open-upright-{off,below,above}` | retaken: Open (inner display), then one Rotate Right | `iphone-duo-open-upright-off` shows iOS's home indicator at the bottom; the rest no |
| `iphone-duo-remembered-devicehub` (726 × 816) | retaken: Duo closed, upright, stored gate −62 dB, `e2-55db`. Device Hub window at 1227 × 800 pt, `screencapture -o -l 3880`, cropped at (1117, 400). The display sits at (117.7, 47.9) at 0.354 scale, as before. Window back to 1227 × 848. | no |
