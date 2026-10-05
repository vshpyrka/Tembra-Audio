# Research: Tuner Noise Gate

All decisions below were made on 2026-10-05 against the code merged to `main` at `e045835` (features 001–005 and the
iPhone Duo experiments). The starting point is `docs/research/tuner-noise-gate.md` (2026-09-29); where this file
departs from that brief it says why. Every path is under `app/` unless it starts with `specs/`, `design/` or `docs/`.

## R1 — Where the gate lives

**Decision**: in the engine, on the analysis thread, inside `TunerAnalyzer::analyseAvailable`
(`engine/src/tuner/tuner_analyzer.cpp`), right after the pre-filter has run on the hop. When the gate is closed the
analyzer does not run the pitch detector for that hop; it pushes a rejected estimate (`PitchEstimate{0, 0}`) into the
existing `PitchSmoother`. The window keeps sliding whatever the gate does.

**Rationale**: the hop is already in hand there and the callback only copies into the ring buffer (Constitution I).
The smoother already holds the last reading through four rejected hops (about 85 ms) and rests on the fifth, so a
closed gate rests the meter in well under the half second of FR-006 with no new timing code, and a note that stops
never hands the meter to the background: the hold shows the note, then the meter rests (User Story 1, scenario 4).
Because the window keeps filling while the gate is closed, a note that opens the gate is detected on a full window and
FR-006's "no added delay" holds.

**Alternatives considered**: masking readings in Kotlin (the brief's "not recommended": the gate's state would be
duplicated in the 10 Hz poll and in the draw-phase reader, and DSP would leave the engine); a gate on the callback
(forbidden work on the real-time thread); folding the level test into `PitchSmoother` (it would mix loudness into a
type that only knows pitch and clarity).

## R2 — What "input level" is

**Decision**: the root mean square of the **analysis window** (the last 4096 frames, 85 ms at 48 kHz) **after** the
band-pass (`BandPass`, 0.9 × 50 Hz to 1.1 × 1400 Hz), as decibels below full scale: `20 · log10(rms)`, floored at
−120 dB. It is refreshed every hop (21 ms) from a running sum of the last four hop energies, so it costs one pass over
the new hop. The gate and the on-screen fill both use this number and nothing else (FR-004). The gate therefore
judges exactly the samples the detector is about to analyse.

**Rationale**:

- *After the filter*, so hum, handling noise and hiss count for less. The filter is second order (12 dB per octave),
  so "less" is exact, not absolute: computed for the two Butterworth sections, a sound one octave beyond a cut-off
  measures 12.3 dB down and two octaves beyond 24.1 dB down. The first draft of the spec said such sound "cannot
  open the gate"; that is not true of this filter, and FR-004 now states what is true (at least 20 dB down two
  octaves out). The practical loss is nil: a pitch outside the hearing range is rejected by the smoother's range
  check whether or not the gate is open.
- *RMS, not peak*: the peak depends on the waveform's crest (3 dB for a sine, 6.8 dB for the five-partial test
  tone, 10 dB and more for noise), so a peak gate treats a hiss and a string at the same loudness differently.
- *Over the window, not over one hop*: a hop holds barely more than one cycle of the lowest strings, so its RMS
  rises and falls with the phase of the wave. Computed on 2026-10-05 for the test tone (peak-to-peak, in dB):

  | Pitch | One hop | Two hops | The window (four hops) |
  |---|---|---|---|
  | A1 (55.0 Hz) | 1.56 | 1.45 | 0.76 |
  | B1 (61.7 Hz) | 2.45 | 1.42 | 0.59 |
  | C2 (65.4 Hz) | 2.49 | 0.95 | 0.64 |
  | E2 (82.4 Hz) | 1.85 | 0.98 | 0.09 |
  | A2 (110 Hz) | 1.45 | 0.76 | 0.38 |

  A one-hop measure would spend most of the 3 dB margin of R3 on its own ripple and make the fill shimmer on a
  steady note. The window's 0.8 dB leaves the margin for the sound.

**What the window costs**: a sound that starts exactly at the gate level opens the gate when it has filled the
window, four hops (85 ms) after it starts; 3 dB above, after two hops; 6 dB above, after one. The detector needs
most of a window of the note before it can name it anyway, and the smoother two accepted hops after that, so the
quarter second of FR-006 holds with room to spare; FR-006 now says the gate must not take a note past that limit
(it used to say "must not add a delay", which no level gate can promise for a note exactly at the threshold). When
a note stops dead the window empties in four hops and the smoother rests four hops later: about 170 ms.

**Alternatives considered**: one hop (table above); the existing `input_peak` (raw and pre-filter: rumble counts in
full); in-band energy from the detector's FFT (a true brick wall, but it couples the gate to the detector's
internals, needs a window function to stop leakage and would have to run even when the gate is closed); a second,
steeper filter only for the level (more DSP for a case in which the meter rests anyway).

## R3 — The gate's rule

**Decision**: a three-line state machine in its own type, `LevelGate` (`engine/src/tuner/level_gate.h`), owned by
the analyzer:

| Gate | State before | Level this hop | State after |
|---|---|---|---|
| off | any | any | open |
| set, value changed since the last hop | any | ≥ gate | open |
| set, value changed since the last hop | any | < gate | closed |
| set, unchanged | closed | ≥ gate | open |
| set, unchanged | closed | < gate | closed |
| set, unchanged | open | ≥ gate − 3 dB | open |
| set, unchanged | open | < gate − 3 dB | closed |

The 3 dB closing margin is the engine constant `kCloseMarginDb`. "Value changed" is how FR-003's "each move is
judged afresh" is met: dragging the handle up through a steady tone closes the gate the moment the handle passes the
tone, with no 3 dB linger, so what the user sees (the fill short of the handle) is what the gate does.

**Rationale**: opening at the level and closing a little under it is the standard cure for chatter. 3 dB absorbs
the window's own ripple (up to 0.8 dB, R2) together with the ±1 dB waver of FR-008; a wider one lets a background
just under the gate hold it open after a note (spec, Edge Cases). The margin is a constant, not a parameter, because
nothing in the spec lets the user change it (Constitution VI); the spec allows the build's measurements to settle it
between 2 and 6 dB, which is a one-line change with its test.

**Alternatives considered**: a hold time instead of a margin (a plucked note fades for seconds; a time long enough
to help would also hold the background); a margin passed from Kotlin (a parameter with one caller and one value).

## R4 — How the gate level reaches the engine

**Decision**: one new C function, `int32_t tembra_tuner_set_gate(uint32_t enabled, float open_db)`. The engine keeps
the value for its whole life: it is allowed in every audio state, it survives `tembra_tuner_stop` and applies to the
next `tembra_tuner_start` and `tembra_tuner_start_test_signal` from their first hop. `EngineCore` stores it under its
command mutex and forwards it to a live analyzer, which holds it in one `std::atomic<float>` (a sentinel below
−120 dB means off) that the analysis thread loads once per hop. `tembra_tuner_config` and both start functions are
unchanged.

**Rationale**: FR-003 forbids interrupting listening when the handle moves, which rules out restarting the stream
(the brief's first option: the Oboe or RemoteIO stream closes and reopens and the platform's microphone indicator
blinks). An atomic read per hop is the cheapest lock-free hand-over the ABI rules allow. Keeping the value in the
engine instead of adding a ninth config field as well means there is one path for one value: Kotlin sets the gate
when the preferences load and whenever the handle moves, and FR-015's "in force from the first moment" follows from
the order of two calls. The eight scalars of the JNI start functions stay as they are.

**Alternatives considered**: a config field plus restart (blinks the indicator, drops 85 ms of window); a config
field plus a setter (the brief's recommendation; two ways to say the same thing); `-INFINITY` for off (relies on
infinity surviving every compiler flag; an explicit `enabled` costs one argument).

## R5 — What the engine publishes

**Decision**: `tembra_pitch_reading` gains two fields, `float input_level_db` (R2) after `input_peak` and
`uint8_t gate_open` after `synthetic` (1 while sound is being read: the gate is off or open). `input_peak` stays. The header becomes
ABI revision 4 and the engine version "0.4.0", following features 003 and 004. The JNI read array grows from seven
floats to nine.

**Rationale**: the fill has to show the gate's own measure, and its look (active or muted, FR-012) and the
announced state (FR-019) have to follow the gate's own state including the margin. Publishing both keeps one copy of
the state machine. The reading is already a seqlock snapshot read in the draw phase, so the fill costs no new
channel.

**Alternatives considered**: deriving "open" in Kotlin from level and gate (wrong inside the 3 dB margin, and a
second state machine); replacing `input_peak` (an ABI removal with no gain; one test uses it).

## R6 — The Kotlin seam

**Decision**: `TunerAudio` gains `fun setNoiseGate(openDb: Float?): AudioResult` (null is off); `PitchSnapshot` gains
`inputLevelDb: Float` and `gateOpen: Boolean`; `FakeAudioEngine` records the call and exposes the last value.
`NativeAudioEngine` stays the only `expect` class and gains no constructor parameter: Android adds
`nativeTunerSetGate(enabled: Boolean, openDb: Float)` to the JNI shim, iOS calls the C function through cinterop.

**Rationale**: the seam mirrors the ABI with primitives, as the rest of `TunerAudio` does; the domain type of R7
lives in `:shared:core` and the ViewModel converts at the call (`kotlin-api-design`: platform detail stays behind
the existing interface, no new seam).

## R7 — The domain type

**Decision**: `NoiseGate` in `:shared:core` (`audio.tembra.core.tuner`), a `@JvmInline value class` over the step
number, 0 for off and 1 to 30 for −78 dB to −20 dB. Its companion owns the scale (`MIN_DB = -80`, `MAX_DB = -20`,
`STEP_DB = 2`), construction (`ofStep`, `ofLevelDb`) and the reading of a stored value (`fromStored(db: Int?)`,
FR-014). Full shape in `data-model.md` §1.

**Rationale**: one field with a domain meaning that must not be mixed up with a decibel number or a slider fraction
(`kotlin-api-design`, value classes); parsing belongs to the target type, not to the preferences store or the
ViewModel (function ownership). The step number is what the slider, the keyboard and the "one step" of the spec all
count in, so it is the stored field; decibels and the track fraction are derived. It is stable for Compose with no
annotation.

**Alternatives considered**: a bare `Int` of decibels (the off case becomes a magic number in four modules); a
sealed `Off | Level(db)` (two types and a `when` at every use for what is one ordered scale; the slider needs the
order).

## R8 — The scale, and when it is measured

**Decision**: the scale is −80 dB to −20 dB in 2 dB steps (spec FR-001, FR-002), held only in `NoiseGate`'s
companion; every test and capture refers to it through the check levels (−60, −50, −40 dB), never through pixel or
step literals. The measurement the spec asked of the plan is done at a checkpoint in the build, after the engine
publishes `input_level_db` and the control exists, and before the feature is accepted.

**Why not now**: before this feature the only loudness the engine publishes is `input_peak`, the raw peak of the hop
before the filter. It is a different quantity from R2's measure: 3 dB above it for a sine, 10 dB and more for room
noise, and it counts rumble in full. No test in the device lane opens the real microphone either (the test package
holds no microphone permission). A number measured that way would not settle the scale.

**What is expected** (to be confirmed, not relied on): Android's compatibility guidance for the unprocessed input
source asks that a 94 dB SPL tone read about −36 dB below full scale (recalled from the CDD, not re-read for this
plan). A plucked guitar at arm's length is about 75 to 85 dB SPL, so roughly −55 to −45 dB there; a television in
the next room 20 to 30 dB under that. The brief's example top level of −30 dB would sit above most playing on such
a phone, which is why the scale reaches down to −80 dB and the check levels are −60, −50 and −40.

**How the checkpoint measures, with no extra code**: the control is the instrument. Launch the debug build with a
gate level (`contracts/tuner-screen.md` §6), let a steady sound play, and read whether the sound is above or below
the gate: from the control's announced state in a layout dump on Android, from the fill's look in a capture on the
simulator. Bisecting the gate level finds the sound's level to one step (2 dB). Three sounds on each of the Pixel 6
Pro and the iPhone 17 simulator: the room as quiet as it gets (Q), a quiet pitched background from a loudspeaker
across the room (B), and a tone from a loudspeaker at arm's length set as loud as a plucked guitar (I).

**Decision rule**: keep the scale if, on both targets, Q ≤ −60 dB and −50 dB ≤ I ≤ −20 dB, so that the working
span between background and instrument lies inside the scale and covers at least a third of it. Otherwise propose
new ends (kept at least 50 dB apart) through `/speckit-clarify`, change the three constants and re-run the lanes;
nothing else moves. The same data settle the closing margin of R3.

## R9 — The control

**Decision**: `NoiseGateSlider` (`shared/ui/.../tuner/NoiseGateSlider.kt`), built on Material 3's `Slider` exactly as
the metronome's `TempoSlider` is (same call shape, `material3` 1.9.0, already in the build): `value` is the step,
`valueRange = 0f..30f`, `steps = 29`, a custom `track` slot and a custom `thumb` slot. A label row ("NOISE GATE",
`labelSmall`) sits above a row of "Off", the slider and "High", as the tempo slider sits between "40" and "240".

- **Shared handle.** The thumb `TempoSlider` draws (16 dp disc in a 44 dp tall box, `primary`, a 16 % halo on hover,
  press, drag and focus, the focus ring) moves into the design system as `TembraSliderThumb` with its constants in
  `TembraSliderDefaults`; `TempoSlider` uses it with no visual change. Two sliders, one handle (FR-020).
- **What Material gives for free**: dragging, a tap on the track, the arrow keys in steps, the `setProgress` action
  for screen readers, and standing aside for a vertical scroll that starts on it. FR-018 is tested, not assumed.
- **Semantics**: `contentDescription` "Noise gate"; `stateDescription` "Off", or the place as a whole percentage,
  followed while listening behind a gate by "sound above the gate" or "sound below the gate" (FR-019).

**Rationale**: the app already has this slider and its tests; a second component with the same parts is the cheap
and consistent choice (`compose-component-design`: `modifier` first optional parameter on the root, no slots because
the label and the end labels are fixed design-system content, no Boolean shape flags).

**Correction to the spec**: the spec called this "the design system's first slider". `TempoSlider` (feature 004)
is the first; FR-020 and the Assumptions now say so.

**Alternatives considered**: a hand-written draggable track (re-implements keys, steps and accessibility); the end
labels under the track as in the clarification mock-up (16 dp taller in every form and unlike the tempo slider; the
spec only asks for a label at each end).

## R10 — The live fill

**Decision**: the `track` slot is a `Box` whose `drawBehind` reads the frame tick of `rememberFrameTick(listening)`
and then `PitchTelemetry.read(snapshot)`, and draws the track, then the fill from 0 to
`NoiseGate.fractionOfLevel(level)`. The level passes through `LevelSmoother`, a small non-state holder like
`NeedleSmoother` (a 30 ms approach in the decibel domain: the engine's value changes 47 times a second and the
display draws 60 to 120, so this only removes the steps; with the 85 ms window of R2 the fill settles on a new level
within a fifth of a second). The look comes from `snapshot.gateOpen`: `primary` when open, `outline` when closed. Not listening draws an
empty track.

**Rationale**: this is the meter's own pattern (feature 003 R8, Constitution II): frame-rate telemetry is read in the
draw phase and never through `StateFlow`, so the fill invalidates drawing and nothing recomposes
(`compose-performance`, deferred reads). Two readers per frame (meter and track) are two lock-free reads on the main
thread, which is the thread `readPitch` requires.

**The one thing to verify**: Material lays the track out from the first to the last position of the handle's
centre. The fill's fraction therefore lines up with the handle at the same value; a test places a level and a gate
at the same decibel value and asserts the fill's end under the handle's centre (SC-006). If a Material version
insets the track differently, the fill applies the same inset.

## R11 — Placement, and keeping the gauge whole

| Form (`TunerLayout`) | Where the control goes | What keeps everything on screen |
|---|---|---|
| `Stacked` (upright phone, medium band) | Foot of the Auto Listen card, under the gauge, full card width | The gauge is fitted to the height left above the control |
| `Wide` (tablet, opened foldable) | Foot of the Auto Listen card, centred, at most `meterMaxWidthWide` (480 dp) | Same |
| `Landscape` (phone sideways) | `TunerSettingsColumn`, after the mode selector | Column content about 213 dp against about 295 dp available; it already scrolls at large fonts |
| `Tabletop` | `TunerTabletopSettings`, after the mode control | The lower half scrolls when too tall (feature 005, FR-007); the upper half keeps only the gauge |
| `Wide` with a book-posture hinge | `TunerTabletopSettings` before the hinge, after the mode control | Same card as tabletop |

**Decision for the two card forms**: `AutoListenPane` stops centring the gauge in a scrolling box. It measures the
card, gives the control its 64 dp at the foot, and sizes the gauge with the existing
`MeterLayout.widthForHeight(…, MeterSize.Regular)` from the height that is left, capped at `meterMaxWidth` /
`meterMaxWidthWide` and never under `meterMinWidth` (200 dp). Only when even that does not fit (a very short
window, the largest fonts) does the card scroll, with the control still at the end of the content.

**Rationale**: this is what FR-016 and the short-window edge case ask (the gauge shrinks, nothing is cut, scrolling
is the last resort), and it is the fitting the landscape and tabletop gauges already use. The control is hidden with
the denied panel and in By Ear by one derived flag (`TunerUiState.showNoiseGate`).

## R12 — State, the ViewModel and persistence

**Decision**:

- `TunerUiState` gains `noiseGate: NoiseGate` and `gateOpen: Boolean` (the 10 Hz mirror of the reading), and the
  derived `showNoiseGate` and `soundAboveGate: Boolean?` (null when not listening or when the gate is off).
- `TunerViewModel.setNoiseGate(gate)` updates the state and calls `audio.setNoiseGate` at once; it writes nothing.
  `noiseGateSettled()` persists the current value; the slider calls it from `onValueChangeFinished`, so a drag
  writes once. A test proves an arrow key and a tap on the track settle too; if Material does not report them, the
  control calls settle itself after those two inputs.
- On load the ViewModel sets the engine's gate from the stored value **before** the first `applyAudio`, so the first
  hop is gated (FR-015). `ListeningPolicy` and `DesiredAudio` do not change: the gate never starts or stops a
  stream.
- The preference is a sixth key, `tuner.noiseGateDb` (an `Int`; absent means off), decoded by
  `NoiseGate.fromStored` (`contracts/tuner-preferences.md`).

**Orientation**: the Android activity handles `orientation|screenSize|screenLayout|smallestScreenSize` itself, and
on iOS a rotation is a window resize, so turning the device recomposes the screen with a new `TunerLayout` and
touches neither the ViewModel nor the stream. The gate level lives in the ViewModel's state and in the engine, so it
needs nothing to survive a rotation or a fold; the tests and the device checks of R14 prove it.

## R13 — Test signals with a level, and a gate for a launch

**Decision**:

- `TestSignalLevel.amplitudeFor(levelDb, harmonics, fundamentalGain)` in `:shared:engine` turns a level in R2's
  measure into the peak amplitude `tembra_test_signal` takes, using the crest of the same five-partial mix the
  synthetic device builds (2.18, or 6.8 dB, for the default mix). The C struct does not change.
- The catalogue gains `e2-70db`, `e2-55db`, `e2-45db` and `e2-30db` (in-tune E2 at those levels; 18 entries). The
  launch argument also accepts `NOTE:CENTS@LEVEL`, for example `E2:0@-55`.
- A second debug launch argument carries a gate level for that launch: the extra `tembra.tuner.noiseGate` on Android
  and `--tembra-tuner-noise-gate=` on iOS, value `off` or a decibel number on the scale. It overrides the stored
  level in the ViewModel for that launch, is not persisted and is ignored by release builds, like the test signal.
- The end-to-end test (`NoiseGateEndToEndTest`, Android device lane and iOS lane) drives the real engine through its
  facade: four gate values × four signals, asserting pitch or rest, `gateOpen` and `inputLevelDb` within 1 dB.

**Rationale**: the levels sit 5 dB or more from every check level, well outside the 3 dB margin and the 1 dB the
conversion can be off by, so each of the sixteen outcomes is unambiguous (`data-model.md` §7). The gate argument is
what lets one command produce the FR-023 captures on five targets and what R8's measurement bisects with; without it
every capture would need the handle dragged by a script on five kinds of device.

## R14 — Proving it on five targets, in both orientations

The owner asked for three things when planning: every attached target, a change of orientation, and proof that the
control is on screen, uncut and clear of everything else. They are met in three layers.

1. **Automated layout tests** (`uiTest`, which runs on each simulator and each Android target):
   `TunerNoiseGateLayoutTest` renders Auto Listen in every form at phone-upright, phone-sideways, tablet-upright,
   tablet-sideways and iPhone Duo outer-display sizes, at the default and the largest font, and asserts for the
   control: displayed; unclipped bounds equal to clipped bounds; inside the root; at least 44 dp tall; no
   intersection with the gauge, the selectors, the pill or the card's edge; and, when the content scrolls, reachable
   by scrolling. A second group swaps the form under an unchanged state (upright ↔ sideways, flat ↔ tabletop ↔ book)
   and asserts the control is still there with the same value. One test uses the window the device really has.
2. **A structural check on the real screen** for the two Android targets: `uiautomator dump` in each orientation and
   posture, with the capture script asserting that the node tagged `tuner.noiseGate` lies inside the display and
   intersects neither `tuner.meter` nor a selector. The iOS simulators have no such dump; layer 1 runs on them at
   their real window size and layer 3 covers the rest.
3. **Captures reviewed by eye** on every target, upright and sideways (FR-023), written into
   `specs/006-tuner-noise-gate/screenshots/NOTES.md` with one row per target and orientation.

| Target | Orientation and posture driven by | Lanes |
|---|---|---|
| Pixel 6 Pro (`19021FDEE001AH`) | `settings put system user_rotation 0/1` | `checkAndroidDevice`, end-to-end, app |
| Pixel 9 Pro Fold emulator (`127.0.0.1:5556`) | rotation as above; `cmd device_state state 1/2/reset` for half-opened and flat; inner display by id for captures | `checkAndroidDevice`, end-to-end, app |
| iPhone 17 simulator | Simulator's Device ▸ Orientation menu through AppleScript | `checkIos -Ptembra.iosSimulator=`, end-to-end, app |
| iPad Pro 13-inch (M5) simulator | same | same |
| iPhone Duo simulator (iOS 27.1) | Device Hub's rotation and pose buttons (clicked through CGEvent) | same, built with Xcode 27.1 beta for the app run |

Known limits, to be reported per FR-024 and not hidden: the fold emulator's `MetronomeTimingEndToEndTest` fails on
its audio clock with or without this feature (feature 005 notes); its microphone hears the workstation only with
host audio input enabled, and its console did not answer on 2026-10-05, so the live-sound check there may fall back
to the test signal; the iPhone 14 is skipped by the owner's decision.

## R15 — The design after acceptance (FR-027)

**Decision**: once the owner accepts the build, `design/stitch/DESIGN.md` gets a "Slider" component entry that
covers both sliders (track, handle, states, the live fill variant), and the Stitch project's Auto Listen screens
(upright phone, tablet, phone landscape, foldable tabletop) are replaced by new screens made from the app's
captures. Stitch cannot edit a screen in place through its API (verified 2026-09-29): the route is local HTML edit,
upload as a new screen, patch the project's screen instances, hide the old version, re-pull the export
(`design/stitch/README.md`).

## What planning changed in the spec

| Spec text | Change | Reason |
|---|---|---|
| FR-004, Terms, Edge Cases, FR-021, Key Entities | Sound outside the hearing range "counts for less" (20 dB at two octaves), no longer "cannot open the gate" | R2: the filter is second order |
| FR-020, Assumptions "Shape of the control", Clarifications | Not the first slider; it must match the tempo slider | R9 |
| Assumptions "The scale" | The measurement is a checkpoint in the build, not a planning step | R8 |
| FR-022 | A debug launch argument for the gate level | R13 |
| FR-023, FR-024, SC-008, Clarifications | Orientation change and on-screen proof on every target | The owner's planning request, R14 |
| FR-014 | A stored value exactly halfway between two steps goes to the quieter one | R7: stored values are whole decibels, so "nearer" needed a tie rule |
| FR-006; User Story 3, scenario 1 | The gate "must not take a note past" the quarter second (was "must not add a delay"); the fill follows within a fifth of a second (was a tenth) | R2: the level is measured over the 85 ms window |
