# Contract: Tuner screen, noise gate control (`:shared:ui`)

**Packages**: `audio.tembra.ui.tuner`, `audio.tembra.ui.designsystem` | **Depends on**: `:shared:core` (`NoiseGate`), `:shared:engine` (`PitchSnapshot`), Material 3 `Slider` (1.9.0, as `TempoSlider`) | **Extends**: `specs/003-instrument-tuner/contracts/tuner-screen.md` and `specs/005-tabletop-posture/contracts/tabletop-layout.md`

Research R9 to R14; data-model.md §5, §6, §8, §9. Skill contracts followed: `compose-component-design` (§1, §2, §4),
`compose-performance` deferred reads (§2, §3), `compose-state-and-effects` (§5), `compose-ui-testing-patterns` (§7).

## 1. Design system: one slider handle

```kotlin
// designsystem/TembraSliderDefaults.kt — moved from MetronomeDefaults, values unchanged
internal object TembraSliderDefaults {
    val TrackHeight: Dp = 6.dp
    val ThumbSize: Dp = 16.dp
    const val THUMB_HALO_ALPHA = 0.16f
    const val THUMB_HALO_SCALE = 2f
}

// designsystem/TembraSliderThumb.kt — the thumb TempoSlider draws today, extracted unchanged
@Composable
internal fun TembraSliderThumb(interactionSource: MutableInteractionSource, modifier: Modifier = Modifier)
//   a ThumbSize × touchTargetMin box; a `primary` disc; a halo on hover, press, drag and focus; the focus ring
```

`TempoSlider` calls `TembraSliderThumb` and reads the track height from `TembraSliderDefaults`; its look, its tests
(`TempoDialTest`, `MetronomeControlsTest`, `MetronomeScreenTest`) and its captures do not change.

## 2. `NoiseGateSlider`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoiseGateSlider(
    gate: NoiseGate,
    onGate: (NoiseGate) -> Unit,          // every move of the handle
    onGateSettled: () -> Unit,            // the move has ended (drag released, track tapped, key pressed)
    telemetry: PitchTelemetry,            // read in the draw phase only
    listening: Boolean,                   // runs the frame tick; false draws an empty track
    soundAboveGate: Boolean?,             // announced state; null says nothing
    modifier: Modifier = Modifier,        // applied to the root column
    interactionSource: MutableInteractionSource? = null,
    testTag: String = "tuner.noiseGate",
)
```

Structure, owned by the component (no slots: the label and the end labels are fixed content):

```text
Column(modifier, tag "tuner.noiseGate")
├── Text "NOISE GATE"                       labelSmall, onSurfaceVariant
└── Row
    ├── Text "Off"                          tabularParam, outline
    ├── Slider (weight 1, tag "tuner.noiseGate.slider")
    │     value = gate.step, valueRange 0f..30f, steps = 29
    │     thumb = TembraSliderThumb
    │     track = Box(TrackHeight, CircleShape).drawBehind { track; live fill }
    └── Text "High"                         tabularParam, outline
```

| Aspect | Contract |
|---|---|
| Height | The label row plus `touchTargetMin` (44 dp) for the slider: about 64 dp at the default font size. Width is the caller's |
| Value | `onGate(NoiseGate.ofStep(round(value)))`, only when it differs from `gate`; the handle stops at 0 and 30 |
| Input | Drag, tap or click on the track, Left/Right (and Up/Down) arrows one step: all from Material's `Slider`. A vertical drag that starts on it scrolls a scrolling parent and leaves the value alone (FR-018) |
| Settled | `onValueChangeFinished` → `onGateSettled()`. If a test shows Material does not report a key press or a track tap as finished, the component calls `onGateSettled()` after that input itself |
| Fill | In `drawBehind`: read the frame tick, `telemetry.read(snapshot)`, `LevelSmoother.advance(snapshot.inputLevelDb, tick)`, then draw from 0 to `NoiseGate.fractionOfLevel(level) × width`. `primary` when `snapshot.gateOpen`, `outline` otherwise; nothing when `!listening`. No State is read in composition at frame rate |
| Alignment | The fill's end and the handle's centre coincide when the level equals the gate level (SC-006); verified by a test |
| Semantics | `contentDescription = "Noise gate"`; `stateDescription = "Off"` or "N percent", followed by ", sound above the gate" / ", sound below the gate" when `soundAboveGate` is not null. Material's range info and `setProgress` stay. No live region: a change of level is never announced |
| Feedback | Hover, press, drag and focus through `TembraSliderThumb`; the focus ring of the design system |
| Reduce motion | Unaffected: the fill is information |
| Tokens | `TembraTheme.colors` (`primary`, `outline`, `onSurfaceVariant`, `surfaceContainerHighest`), `typography.labelSmall`, `typographyExtras.tabularParam`, `spacing.xs` / `sm` / `touchTargetMin`; no literal colour or size |

## 3. `LevelSmoother`

```kotlin
/** The drawn level between frames: a 30 ms approach in decibels; the first value is taken as it is. */
internal class LevelSmoother { fun advance(targetDb: Float, frameNanos: Long): Float }
```

A plain class held with `remember`, like `NeedleSmoother`; a pause longer than 100 ms counts as one long frame.

## 4. Where the control goes

`TunerScreenContent` builds the control once, when `state.showNoiseGate`, and hands it to the place the layout
needs. Each receiver takes an optional slot whose absence removes its space:

```kotlin
internal fun AutoListenPane(…, modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null)
internal fun TunerSettingsColumn(…, modifier: Modifier = Modifier, autoListenControls: (@Composable () -> Unit)? = null)
internal fun TunerTabletopSettings(…, modifier: Modifier = Modifier, autoListenControls: (@Composable () -> Unit)? = null)
```

| `TunerLayout` | Receiver | Placement |
|---|---|---|
| `Stacked` | `AutoListenPane(footer = …)` | At the foot of the card, under the gauge, the card's inner width |
| `Wide` | `AutoListenPane(footer = …)` | At the foot of the card, centred, `widthIn(max = meterMaxWidthWide)` |
| `Landscape` | `TunerSettingsColumn(autoListenControls = …)` | After `ModeControl`, the column's width |
| `Tabletop` | `TunerTabletopSettings(autoListenControls = …)` | After `ModeControl`, in the centred column |
| `Wide` with a vertical fold | `TunerTabletopSettings(autoListenControls = …)` | Same card, before the hinge |

`AutoListenPane` with a footer (research R11): the footer is measured first; the gauge gets the height that is left
and is sized from it with `MeterLayout.widthForHeight(…, MeterSize.Regular)`, capped at `meterMaxWidth` /
`meterMaxWidthWide`, never under `meterMinWidth`. When the height left is under the smallest gauge, the card's
content scrolls as a whole with the footer last. With the denied panel the footer is not passed. Without a footer
(landscape, tabletop, book) the pane behaves exactly as today.

Order for the keyboard and the screen reader follows what is seen: after the gauge in `Stacked` and `Wide`, after
the mode control in the three settings forms.

## 5. State and intents

`TunerUiState`, `TunerCallbacks` and the ViewModel's order of calls are in data-model.md §5 and §6. New ViewModel
intents: `setNoiseGate(gate: NoiseGate)`, `noiseGateSettled()`, `useNoiseGate(gate: NoiseGate?)` (the debug launch
value, handed over in the same effect as `useTestSignal`, before `screenVisible(true)`). No new coroutine owner: the
write goes through the existing `persist` launch site.

## 6. Debug launch arguments

| Piece | Change |
|---|---|
| `TunerTestSignalRequest.parse` | Also accepts the four new catalogue ids and `NOTE:CENTS@LEVEL` (level −120..0, converted with `TestSignalLevel.amplitudeFor`) |
| `TunerNoiseGateRequest.parse(argument: String?): NoiseGate?` (new) | "off" → `NoiseGate.Off`; a whole number → `NoiseGate.ofLevelDb`; anything else → null |
| `LocalTunerNoiseGate` (new) | `staticCompositionLocalOf<NoiseGate?> { null }`, provided by `App` |
| `App(…, tunerNoiseGate: NoiseGate? = null)` | Provides the local; a non-null value opens the app on the Tuner in Auto Listen for that launch, as a test signal does |
| `MainActivity` | In debug: `intent.getStringExtra("tembra.tuner.noiseGate")` |
| `MainViewController(…, tunerNoiseGate: String? = null)` and `ContentView.swift` | In Debug: the value of `--tembra-tuner-noise-gate=` |

Release builds never read either argument.

## 7. Tests (written first)

`commonTest`:

| File | Cases |
|---|---|
| `TunerViewModelTest` | on load the stored gate is sent to the audio seam before `startListening` (call order in `FakeAudioEngine.calls`); `setNoiseGate` sends the value at once and writes nothing; `noiseGateSettled` writes it; an unchanged value does nothing; the value survives By Ear and back, `screenVisible(false/true)`, `foreground(false/true)` and an interruption, and each restart of listening follows a gate already set; a debug launch value replaces the stored one and is not written; the poll mirrors `gateOpen` |
| `TunerUiStateTest` | `showNoiseGate` by mode and permission; `soundAboveGate` is null when idle, when the gate is off and in By Ear, and follows `gateOpen` otherwise |
| `LevelSmootherTest` (new) | first value as given; within 2 dB of a 30 dB step after 100 ms; a long pause does not jump |
| `TunerNoiseGateRequestTest` (new), `TunerTestSignalRequestTest` | the forms of §6 and their rejects |

`uiTest` (each simulator and each Android target):

| File | Cases |
|---|---|
| `NoiseGateSliderTest` (new) | renders the label, "Off" and "High"; the slider node is at least 44 dp tall; Right and Left move one step and stop at the ends; a click at a third of the track reports step 10; a horizontal swipe reports rising steps and one settle; key and click also settle; hover, press and focus through an injected `MutableInteractionSource` show the halo and the ring (clock frozen); semantics: label, "Off", "50 percent", the two states, `setProgress`; with a scripted telemetry at −50 dB and the gate at −50 dB the fill ends under the handle's centre (± 2 dp) and is `primary`; at −60 dB it ends short and is `outline`; not listening draws no fill; inside a vertically scrolling parent a vertical swipe scrolls and leaves the step unchanged |
| `TunerNoiseGateLayoutTest` (new) | **On screen in every form and orientation.** For window sizes 320×568, 360×740, 466×678 (iPhone Duo outer display), 800×360, 1024×1366 and 1366×1024, at font scales 1.0 and 2.0, with the form `TunerLayout` gives each: the control is displayed (after scrolling to it where the content scrolls); its unclipped bounds equal its bounds in the root and lie inside the root; it does not intersect `tuner.meter`, the selectors or the pill; it sits below the gauge in `Stacked` and `Wide` and inside `tuner.settings` otherwise; the gauge's bounds are whole. Tabletop and book with a scripted fold: the control is in the lower / start region and clear of the fold. **Orientation and posture change**: one composition whose layout parameter goes upright → sideways → upright and flat → tabletop → book → flat with the state unchanged: the control is present each time with the same step and the same announced value. **This device's window**: the same assertions with no forced size |
| `TunerScreenLayoutTest`, `TunerTabletopLayoutTest`, `TunerHingeLayoutTest` | focus order includes the control at its place; every existing assertion still holds |
| `TunerScreenByEarTest`, `TunerScreenDeniedTest` | no node tagged `tuner.noiseGate` |

Keyboard cases state per lane whether hardware-key input can run there, as the existing tuner tests do (they enter
keyboard mode with `enterKeyboardMode`); none is skipped with a silent return.

## 8. Capture tool (`app/tools/capture-noise-gate.sh`)

```text
capture-noise-gate.sh --android <serial> [--display <id>] | --ios "<simulator name>"
                      --orientation upright|sideways [--out <dir>] [--settle <seconds>]
```

For the named target and orientation it launches the debug build three times and stores, under
`specs/006-tuner-noise-gate/screenshots/`:

| File | Launch | Expected |
|---|---|---|
| `<target>-<orientation>-off.png` | `e2-in-tune`, gate `off` | E2 shown, fill active, handle at "Off" |
| `<target>-<orientation>-below.png` | `e2-55db`, gate `-50` | "Listening", fill muted and short of the handle |
| `<target>-<orientation>-above.png` | `e2-45db`, gate `-50` | E2 shown, fill active and past the handle |

On Android it sets the rotation itself (`user_rotation`), and after each launch reads `uiautomator dump` and fails
unless the node described "Noise gate" lies inside the display and intersects neither the node described "Tuner
meter" nor a selector (research R14, layer 2). On iOS it turns sideways captures upright with `sips`; the simulator
is rotated beforehand (quickstart §4). It prints one line per capture for the notes.
