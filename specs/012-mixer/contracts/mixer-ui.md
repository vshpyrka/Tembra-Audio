# Contract: the mixer UI — sheet, strips, fader, knob, meters, layout, keys, semantics, tests

**Where**: `:shared:ui`, packages `audio.tembra.ui.mixer` (new), `.timeline`,
`.timeline.components`, `.tracksettings`, `.designsystem`, `.theme`. Research R1, R2, R3, R6,
R9, R13, R16; data model §4, §5, §6; spec FR-001 to FR-004, FR-007, FR-008, FR-010 to FR-030,
FR-034, SC-001, SC-006 to SC-008. Skill contracts: `compose-component-design` (every new
composable: `modifier` as the first optional parameter applied to the root; primitives and
callbacks, no Boolean shape flags, no slots), `compose-state-and-effects` (content
composables take `MixerUi` and callbacks; the sheet's drag state is `remember`ed and
synchronised from `MixerUi.height` through a keyed `LaunchedEffect`), `compose-performance`
(the per-frame read in `drawWithContent` at the sheet root; `LevelMeter` and `VuDial` read the
shared `MixSnapshot` in `drawBehind`; the sheet offset read in `offset { }`; `key(track.id)`
items; the scale ticks `remember`ed; no snapshot writes from draw), `compose-animations`
(`anchoredDraggable.animateTo` between anchors; an `Animatable` for the double-tap return;
reduce motion: no animation, the value jumps), `compose-focus-navigation` (`focusGroup()` per
strip; Solo → Mute → pan → fader by composition; keys on the focused control through
`onKeyEvent`; the handle focusable; Escape through the overlay stack after 011),
`compose-ui-testing-patterns` (controlled state, `ScriptedMixTelemetry`, injected interaction
sources, frozen clock, semantics before pixels, keyboard tests declared per lane).

## Components (one file each, package `ui.mixer` unless stated)

| Composable | Behaviour |
|---|---|
| `MixerSheet(ui: MixerUi, callbacks, telemetry: MixTelemetry, inputTelemetry, layout, lanesHeight, modifier)` | a `Box` aligned to the lanes box's bottom with `anchoredDraggable` over `Hidden/Half/Full` (R1); the drag offset is read in the layout phase: between the anchors the content is measured at the visible height (the strips fit what shows), below the lowest open anchor it keeps that anchor's height and slides out of the box, clipped at its bottom edge, so the strips' tint bands leave with the sheet (the owner's finding of 2026-11-09: a collapsing sheet left them on the edge until the close's last frame); `BackHandler(ui.open)`; `drawWithContent` performs `telemetry.read(tick, snapshot)` inside `PerfTrace.section(TIMELINE_MIXER_FRAME)` then draws the children; holds the `MixerLevelMirror` (`LaunchedEffect(ui.open)` at 10 Hz); `traceFirstDraw(TIMELINE_MIXER_OPEN)` on the first strip's meter; tag `mixer.sheet`; semantics `paneTitle` "Mixer" |
| `MixerHandle(height, onDragToggle, modifier)` | the "Mixer" grabber row: a 32 dp bar with the label and, when `ui.windowAvailable`, the "Open in a window" icon button (`mixer.window.open`); focusable; Enter toggles Half/Full; tag `mixer.handle` |
| `MixerStripRow(strips, form, callbacks, …)` | a `LazyRow` keyed by id, 72 dp items, `contentPadding` for the pinned master; tag `mixer.strips`; `tembraScrollbar` after 011 |
| `MixerStrip(strip: StripUi, form, selected, callbacks, telemetry, inputTelemetry, modifier)` | the frame: tint band at the base, number at the top (`tabularParam`), `VuDial` and `PanKnob` in `Full`, `MuteSoloButton` × 2 (36 dp, the header's component reused), `Fader` with `LevelMeter` beside it, the name (ellipsised) at the bottom; `focusGroup()`; `selectable` on the number and name → `onStripSelect`; secondary click → the track menu (011's `onSecondaryClick`); `MeterSource.Input` swaps `LevelMeter` for 010's `InputMeter` pattern (the input snapshot through `inputTelemetry`) |
| `MasterStrip(master: MasterUi, callbacks, telemetry, modifier)` | the same frame: "Master" label, `Fader` in percent mode (0…100, readout "80 %", `MasterVolumeSlider`'s steps), `LevelMeter` on the master fields, the clip latch; no knob or toggles |
| `Fader(value: Float, onValue, onSettled, description, readout: String, modifier, scale: FaderScale, interactionSource?, testTag?, section)` (`designsystem`) | vertical: a rail, the lit track below the handle, the 44 × 28 dp handle, the scale ticks (`remember(range, height)`); drag 1:1 in throw space (`awaitEachGesture`, keeps following off the rail), double tap → `onValue(unity)` animated; wheel ±1 step; arrows ±step, Page ±page, Home → unity; hover tint and the focus ring; semantics: `contentDescription` (the track and "gain"/"master volume"), `stateDescription = readout`, `progressBarRangeInfo` + `setProgress` in the value domain (Compose has no slider role; the range semantics stand in) |
| `PanKnob(pan: Pan, onPan, onSettled, modifier, interactionSource?, testTag?)` (`designsystem`) | a 40 dp dial with an arc and a pointer; vertical drag 2 dp per 1 %; release within 5 % snaps to the centre; double tap → centre; wheel ±1 %, arrows ±1 %, Page ±10 %, Home → centre; semantics like the fader with the "L 35 / C / R 20" readout |
| `LevelMeter(telemetry: MixTelemetry, index: Int (64 = master), mirror: StripLevel, onResetClip, modifier)` (`designsystem`) | two 6 dp bars in `drawBehind` through `LevelHold` and `LevelSmoother`, −60…0 dB linear in dB, `meterBody` to −6 dB, `meterWarning` above, the hold mark, the `meterClip` square lit from `clipMask`/`masterClipped`; `clickable` on the square → `onResetClip`; semantics from the mirror ("Level", "<n> dB", "clipped" once through a live region) |
| `VuDial(telemetry, index, modifier)` (`designsystem`) | a 48 dp half-dial drawn in `drawBehind`: the needle from the published RMS mapped to −20…+3 VU (0 VU = −18 dBFS), the arc red above 0 VU (`meterWarning`); semantics "VU", `stateDescription` from the mirror |
| `PanSlider(pan, onPan, onSettled, …)` (`tracksettings`) | a `TembraSlider` −100…100, the readout beside it, `stateDescription = readout` |
| `PanReadout(pan, onPan, …)` (`timeline.components`) | the expanded header's row-2 readout after `GainReadout`: tap-less, a vertical drag like `GainReadout`, double tap to centre |
| `TimelineToolbar` (extended) | a `TransportButton(TembraIcon.InstantMix, "Mixer", onMixerToggle, checked = open, testTag = timeline.toolbar.mixer)` in every width (compact keeps it in the row: the mixer is a primary action; the overflow entry `timeline.toolbar.overflow.mixer` mirrors it) |
| `TimelineScreenContent` (extended) | passes `mixTelemetry`, `lanesHeight` and the sheet into the lanes box; the snackbar host pads above the sheet while it is open |
| `TrackSettingsForm`, `TrackHeader` (extended) | the pan row; the pan readout |

## Layout rules (data model §5; FR-018 to FR-022, FR-025, FR-026)

- Compact and Medium: Half ≥ 256 dp (research R2's 240 dp plus the handle row, so the fader keeps its 120 dp throw); Full = the lanes box; three strips plus the master at
  360 dp; the row scrolls sideways; the master stays pinned.
- Landscape: one anchor (Full); `FullNoVu`.
- Expanded: Half = lanes box − 192 dp (two expanded lanes stay visible) and ≥ 256 dp; Full =
  the lanes box; `Full` form; as many strips as fit, never stretched.
- Crossing a breakpoint keeps `open`, maps the height (landscape forces Full), keeps the
  row's scroll position (`rememberLazyListState` keyed by nothing) and a drag in progress
  (the pointer input survives; the anchors recompute from the new lanes height).
- Short sheets (< 480 dp): drop the VU dial, then the knob (Compact form), before the fader
  falls under 120 dp.
- Largest font size: the number, readouts and name scale; the strip widens to its text;
  nothing clips (the name ellipsises last).

## Keyboard (FR-028, FR-029)

`X` toggles the sheet (root handler; `ShortcutCatalogue` after 011). Tab order inside the
sheet: handle → per strip the knob (full form), Solo, Mute, fader → master fader (the number
and name labels select on a tap but are no Tab stops). Fader: arrows 0.5 dB, Page
6 dB, Home 0 dB; knob: arrows 1 %, Page 10 %, Home centre; master: arrows 1 %, Page 10 %.
"M"/"O" on a focused strip toggle that track. Escape (011's overlay stack) and Back close.

## Semantics and anchors (FR-030)

`MixerTestTags`: `sheet`, `handle`, `strips`, `strip(i)`, `fader(i)`, `pan(i)`, `solo(i)`,
`mute(i)`, `meter(i)`, `clip(i)`, `name(i)`, `masterFader`, `masterMeter`, `masterClip`,
`windowOpen`; `TrackSettingsTestTags.pan`. `PerfAnchors` adds `TIMELINE_MIXER`,
`TIMELINE_OVERFLOW_MIXER`, `MIXER_SHEET`, `MIXER_HANDLE`, `MIXER_STRIPS`,
`MIXER_STRIP_FADER_FIRST` … `MIXER_STRIP_CLIP_FIRST` (`mixer.strip.<part>.0`),
`MIXER_MASTER_FADER`, `MIXER_MASTER_METER`, `MIXER_MASTER_CLIP`, `MIXER_WINDOW_OPEN`,
`TRACK_SETTINGS_PAN` (the exact count is asserted by `PerfAnchorsCatalogueTest`). Every
fader and knob announces "<track>, gain, <readout>" / "<track>, pan, <readout>"; the master
"Master volume, <n> percent"; meters "Level, <n> dB" on request and "clipped" once.

## Copy (`MixerCopy`)

"Mixer", "Open mixer", "Close mixer", "Gain", "Pan", "Master volume", "Level", "VU",
"Clipped, tap to reset", "Open in a window", "C", "L %d", "R %d".

## Design conformance

Tokens only (R16); the glyph fetched; the fader, knob, meter and dial drawn; the design
table's verdicts honoured (strips numbered and named, the meter beside the fader as
`tablet-split-v2`, Solo lit in lavender, Mute dim, the master's three meter colours, no
per-strip speaker, no FX dial, no bottom navigation). Screenshots per FR-034 at 360, 600,
840 and 1024 dp plus phone landscape, both sheet heights, the fold's postures, the iPad, the
Duo and the desktop emulator, meters live, default and largest font, on both platforms.

## Tests (first)

- `uiTest`: `FaderTest` (drag 1:1, off-rail follow, double tap, wheel, keys, semantics,
  hover and focus through an injected interaction source, reduce motion), `PanKnobTest`
  (drag, snap, double tap, keys, readout), `LevelMeterTest` (scripted levels: silent, −20 dB,
  full scale → latch; reset; the hold after 1.5 s of frozen-clock advance; both colours),
  `VuDialTest` (−18 dBFS → 0 VU; above → warning arc), `MixerStripTest` (both forms; every
  target ≥ 44 dp; the input source swap; "M"/"O"), `MasterStripTest`, `MixerSheetTest`
  (opens at Half, drag to Full and back, swipe down closes, Back closes, landscape single
  anchor, 360/600/840/1024 dp and the largest font with no clipping, the transport bar
  reachable, the per-frame section wraps one read), `MixerStripRowTest` (24 strips, pinned
  master, scroll to the end), `TimelineScreenTest` (extended: the toolbar button, the sheet
  over the lanes, the snackbar above it), `TimelineKeyHandlingTest` (extended: X; per lane),
  `TrackSettingsFormTest` (extended: pan row), `TrackHeaderTest` (extended: pan readout),
  `ScriptedMixTelemetry` (helper: `silent`, `level(trackIndex, peakDb, rmsDb)`, `clipped(i)`,
  `master(...)`).
- `commonTest`: `TimelineUiStateTest` (forms/anchors), `TimelineTokensTest`,
  `PerfAnchorsCatalogueTest`, `IconAssetsTest` (the new glyph).
