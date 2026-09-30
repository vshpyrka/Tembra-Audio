# Contract: Design-system additions for the metronome

**Module**: `:shared:ui`, packages `audio.tembra.ui.theme`, `audio.tembra.ui.designsystem`, `audio.tembra.ui.metronome.components` | **Evidence**: research R10, data-model.md §9 | **Spec**: FR-028 to FR-032 | **Skill**: `compose-component-design` (root `modifier` after required parameters and applied to the root layout; slots for caller-varying content; primitives for design-system and semantic values; no Boolean shape flags; defaults in `MetronomeDefaults`)

## Tokens (`theme`)

- `TembraTypeScale`: `tempoNumber`, `sectionLabel` (screen-derived, not in `byThemeName`);
  `TembraTypographyExtras` exposes their `TextStyle`s.
- `TembraSpacing`: `chipHeight`, `pillHeight`, `accentCellBarHeight`, `tempoDialMaxWidth`,
  `tempoDialMaxWidthWide`, `tempoStepper`, `tapTempoHeight`, `playButton`, `soundMenuWidth`,
  `soundTileHeight`, `soundTileGlyphBox`, `wideSettingsMinWidth` (values in data-model.md §9).
- `TembraGradients`: `tempoArc: LinearGlow` (new helper `LinearGlow(start: Offset fraction, end: Offset fraction, stops)` with `brush(size)`, beside `RadialGlow`), `tempoDialCard: RadialGlow`,
  `tempoDialOverlay: LinearGlow`, `accentGlow: Glow`, `playGlow: Glow`. No colour token is added
  (`ThemeJsonParityTest` unchanged; `MetronomeTokensTest` asserts every stop is a scheme colour or
  the documented in-gradient stop `#1E1B2E`).
- `TembraIcon`: `Remove, Add, TouchApp, MusicNote, GraphicEq, RadioButtonChecked, Notifications,
  Sensors, Tune, Check` (fetched by `tools/fetch-material-symbols.sh`; `IconAssetsTest` extended).

## Generic components (`designsystem`)

```kotlin
data class ChipSpec(val id: String, val label: String, val icon: TembraIcon? = null, val interactionSource: MutableInteractionSource? = null)
@Composable fun TembraChipRow(items: List<ChipSpec>, selectedId: String, onSelect: (String) -> Unit, accent: ChipAccent, modifier: Modifier = Modifier, columns: Int = items.size, testTag: String? = null)
//   equal-width chips in `columns` per row (wraps), 32 dp visual / 44 dp target, radius 8, labelMedium; role = Tab + selected; hover text onSurface; focus ring
enum class ChipAccent { Secondary /* secondaryContainer */, Primary /* primaryContainer */ }

@Composable fun TembraPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource? = null, testTag: String? = null)
//   28 dp visual / 44 dp target, radius full, microLabel (the existing 11 sp token; labelSmall is 10 sp); selected secondaryContainer / onSecondaryContainer, else surfaceContainerHigh / onSurfaceVariant; role = Tab

enum class CardDiscs { TopStartBottomEnd, TopEndBottomStart, None }
@Composable fun TembraGradientCard(glow: RadialGlow, modifier: Modifier = Modifier, discs: CardDiscs = CardDiscs.TopStartBottomEnd, overlay: LinearGlow? = null, testTag: String? = null, content: @Composable BoxScope.() -> Unit)
//   existing card + disc placement and an optional vertical overlay brush drawn after the glow; the tuner's calls are unchanged (defaults)
```

## Metronome components (`metronome.components`, `internal`; reusable by the transport feature)

```kotlin
@Composable fun TimeSignatureChips(selected: TimeSignature, onSelect: (TimeSignature) -> Unit, modifier: Modifier = Modifier, columns: Int = 6, testTag: String = "metronome.signature")
@Composable fun SubdivisionChips(selected: Subdivision, unit: BeatUnit, onSelect: (Subdivision) -> Unit, modifier: Modifier = Modifier, testTag: String = "metronome.subdivision")
//   both are thin wrappers over TembraChipRow; the subdivision chip's note glyph is the row's `icon`

@Composable fun AccentCells(pattern: AccentPattern, running: Boolean, telemetry: BeatTelemetry, onCycle: (Int) -> Unit, modifier: Modifier = Modifier, testTag: String = "metronome.cells")
//   one AccentCell per beat, gap 8, wraps to two rows when a cell would be under 44 dp; each cell reads `tick.longValue` (rememberFrameTick(running)) and telemetry.litBeat(frame) inside drawBehind
@Composable fun AccentCell(index: Int, level: BeatLevel, lit: () -> Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource? = null, testTag: String? = null)
//   surfaceContainerLowest card; bar box 64 dp; bar height by level (95/62/42 %), colours secondary / primary / primary 70 %; accentGlow on Accent; label microLabel; `lit` is a provider read in the draw phase only

@Composable fun StyleRow(selected: MetronomeStyle?, onSelect: (MetronomeStyle) -> Unit, modifier: Modifier = Modifier, testTag: String = "metronome.style")
//   top seam + "STYLE" label + three TembraPills; none selected when `selected` is null

enum class ClickSoundPickerLayout { Dropdown, Tiles }
@Composable fun ClickSoundPicker(sound: ClickSound, customSound: CustomSoundState, layout: ClickSoundPickerLayout, onSelect: (ClickSound) -> Unit, onImportCustom: () -> Unit, onReplaceCustom: () -> Unit, modifier: Modifier = Modifier, testTag: String = "metronome.sound")
//   Dropdown: pill (graphic_eq 18 dp primary, name labelMedium, expand_more 16 dp outline) + DropdownMenu 160 dp on surfaceContainerHighest, items with a trailing Check, the Custom item with a Replace trailing button when filled
//   Tiles: two-column grid of ClickSoundTile (44 dp, glyph box 28 dp); Custom tile dashed when empty ("+"), name when filled, Replace as a small trailing icon button
@Composable fun ClickSoundTile(label: String, icon: TembraIcon, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dashed: Boolean = false, trailing: (@Composable () -> Unit)? = null, interactionSource: MutableInteractionSource? = null, testTag: String? = null)
//   `dashed` is the design-system state of the empty Custom tile (a constrained value, not a shape flag); `trailing` is the slot for the Replace action (an icon-only button whose contentDescription and tooltip read "Replace custom sound", spec FR-029)

@Composable fun TempoDial(tempo: Tempo, marking: TempoMarking, canStepDown: Boolean, canStepUp: Boolean, onTempo: (Tempo) -> Unit, onStep: (Int) -> Unit, modifier: Modifier = Modifier, center: @Composable () -> Unit = { TempoReadout(tempo, marking) }, testTag: String = "metronome.dial")
//   square Box: Canvas ring (TempoDialGeometry, tempoArc brush, glow stroke, ticks), pointer drag on the ring band, scrollable wheel, key handler; steppers (TempoStepper) at the left and right inside the ring; `center` slot defaults to the readout so the transport can put its own readout there
@Composable fun TempoReadout(tempo: Tempo, marking: TempoMarking, modifier: Modifier = Modifier)   // "120" tempoNumber + "BPM" labelMedium secondary; marking.display labelMedium tertiary
@Composable fun TempoStepper(icon: TembraIcon, enabled: Boolean, contentDescription: String, onStep: () -> Unit, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource? = null, testTag: String? = null)
//   44 dp round surfaceContainerHigh; press-and-hold repeat (400 ms, then every 80 ms) through rememberCoroutineScope, cancelled on release; tooltip = contentDescription
@Composable fun TempoSlider(tempo: Tempo, onTempo: (Tempo) -> Unit, modifier: Modifier = Modifier, testTag: String = "metronome.slider")
//   Material Slider 40f..240f with 199 steps, 6 dp track surfaceContainerHighest, primary thumb; end labels tabularParam outline

@Composable fun TapTempoButton(caption: String, onTap: (frameNanos: Long) -> Unit, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource? = null, testTag: String = "metronome.tap")
//   64 dp tall surfaceContainerHigh card; title "TAP TEMPO" headlineSmall primary with touch_app; caption labelSmall outline; a primary 20 % flash per tap (120 ms; static under reduce motion); onTap receives withFrameNanos time
@Composable fun PlayPauseButton(running: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, interactionSource: MutableInteractionSource? = null, testTag: String = "metronome.play")
//   64 dp round secondaryContainer, hover secondary, PlayArrow / Pause 34 dp filled, playGlow; contentDescription follows `running`

@Composable fun MetronomeSettingsPanel(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, pickerLayout: ClickSoundPickerLayout, signatureColumns: Int, modifier: Modifier = Modifier)
//   stacks the section cards in the design order; used by all three layouts; lives in `audio.tembra.ui.metronome` (screen-level), not in `components`
object MetronomeDefaults { /* bar heights, glyph sizes, repeat delays, section label style */ }
```

**Tests** (`uiTest`): `TembraChipRowTest` (selection, wrap at `columns`, semantics, hover, focus,
44 dp), `TembraPillTest` (selected/unselected colours, semantics), `TembraGradientCardTest`
extended (disc placements, overlay); component tests listed in contracts/metronome-screen.md.
`MetronomeTokensTest` (`commonTest`) asserts the token values of data-model.md §9 and that no new
colour literal appears in `TembraColorScheme` or `DawColors`.

**Licence inventory**: icons unchanged (Material Symbols row); `THIRD_PARTY_LICENSES.md` Okio
row updated per research R16.
