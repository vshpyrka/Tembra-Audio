# Contract: Design-system additions for the tuner

**Module**: `:shared:ui`, packages `audio.tembra.ui.theme` and `audio.tembra.ui.designsystem` | **Evidence**: research R9, data-model.md §8 | **Spec**: FR-023 to FR-027

## Tokens (`theme`)

- `TembraTypeScale`: `tunerNote`, `tunerOctave`, `tunerCardNote`, `tunerCardOctave`, `tunerReadout`,
  `microLabel` as `TembraTextStyleSpec` values (not in `byThemeName`: they are screen-derived, like
  the chrome metrics of feature 002); `TembraTypographyExtras` exposes their `TextStyle`s.
- `TembraSpacing`: `segmentHeightLarge`, `segmentHeightSmall`, `meterMaxWidth`, `meterMaxWidthWide`,
  `byEarGridMaxWidth`, `byEarGridMaxWidthWide`, `stringCardHeightWide`, `toggleWidth`, `toggleHeight`,
  `toggleKnob`, `statusDot`.
- `TembraGradients` (new `@Immutable` data class, `LocalTembraGradients`, `TembraTheme.gradients`):
  `RadialGlow(center: Offset fraction, stops: List<Pair<Float, Color>>)` values `meterCompact`,
  `byEarCompact`, `byEarWide`, `discTopStart`, `discBottomEnd`; `needleGlow`, `playingGlow`
  (`Glow(color, radius)`). Drawn with `Brush.radialGradient`; never `Modifier.blur`.
- `TembraIcon`: `Mic, MicOff, Hearing, PlayArrow, Pause, VolumeUp, ExpandMore, ArrowLeft, ArrowRight, Settings`
  (fetched by `tools/fetch-material-symbols.sh`). The `Guitar, Cello` design glyphs were removed on 2026-10-12: the
  instrument selector is text only (spec Clarifications, Session 2026-10-12). `IconAssetsTest` asserts no `@` / `?attr`
  in any file.

## Components (`designsystem`)

```kotlin
data class SegmentSpec(val id: String, val label: String, val icon: TembraIcon? = null)
enum class SegmentedStyle { Large, Small }        // Large: 32 dp segments on surfaceContainerLowest, active secondaryContainer (instrument); Small: 28 dp on surfaceContainerLow, active primaryContainer (mode) or surfaceContainerHigh + primary text (string count, `accent = Subtle`)
@Composable fun TembraSegmentedControl(items: List<SegmentSpec>, selectedId: String, onSelect: (String) -> Unit, style: SegmentedStyle, accent: SegmentAccent = SegmentAccent.Primary, modifier: Modifier = Modifier, testTag: String)
//   Row with role = Tab per item, selected, hover (onSurface text / container step), focus ring, minimumInteractiveComponentSize(); item testTag "$testTag.$id"

@Composable fun TembraToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, testTag: String)
//   32 × 16 dp track (secondary when on, surfaceContainerHighest when off), 12 dp knob, animated over motion.effectiveDuration; toggleable(role = Switch); 44 dp target

@Composable fun TembraStatusPill(text: String, color: Color, icon: TembraIcon? = null, pulsing: Boolean = false, modifier: Modifier = Modifier, testTag: String)
//   pill on surfaceContainerLow, `text` drawn as text.uppercase() in tabularParam while the node's contentDescription is the sentence-case `text` (analysis L1), 6 dp dot (pulses 1 s unless reduce motion)

@Composable fun TembraGradientCard(glow: RadialGlow, modifier: Modifier = Modifier, testTag: String, content: @Composable BoxScope.() -> Unit)
//   rounded 16 dp, surfaceContainerLowest, 1 dp seam outlineVariant at 20 %, radial glow + the two corner discs drawn behind content
```

**Tests** (`uiTest`): `TembraSegmentedControlTest` (selection, semantics, hover, focus, 44 dp, both styles),
`TembraToggleTest` (toggle semantics, reduce motion), `TembraStatusPillTest` (text, dot, no pulse under
reduce motion), `TembraGradientCardTest` (renders content, seam, testTag). `TunerTokensTest`
(`commonTest`) asserts the token values of data-model.md §8.

**Licence inventory**: unchanged for icons (Material Symbols row); `THIRD_PARTY_LICENSES.md` gains the
DataStore and Okio rows (research R13).
