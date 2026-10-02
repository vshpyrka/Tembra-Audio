# Research: Tabletop Posture for the Tuner and the Metronome

All decisions below were made on 2026-10-02 against the code merged to `main` at `bcf909b` (features 001–004).

## R1 — How the posture is detected

**Decision**: read `currentWindowAdaptiveInfo().windowPosture` (Material 3 adaptive, the `org.jetbrains.compose.material3.adaptive:adaptive:1.2.0` artifact the app already depends on since feature 002). Tabletop is `posture.isTabletop`, and the fold's position comes from the first entry of `posture.hingeList` that is horizontal (`!isVertical`) and separating; its `bounds` are a `Rect` in window pixels.

**Rationale**: the *Make your app fold aware* guide (read 2026-10-02) gives two ways to detect tabletop: collect `FoldingFeature`s and test `state == HALF_OPENED && orientation == HORIZONTAL`, or read `currentWindowAdaptiveInfoV2().windowPosture.isTabletop`. The second is the one already in our stack and is in common code: `Posture(isTabletop, hingeList)` and `HingeInfo(bounds, isFlat, isVertical, isSeparating, isOccluding)` are declared in `commonMain` of the adaptive library (checked with `javap` on the 1.2.0 Android artifact: public constructors, `isTabletop()`, `getHingeList()`, `getBounds()`). On Android the library fills them from Jetpack WindowManager's `WindowInfoTracker`; on iOS `Posture()` is always the default (not tabletop, no hinges), so the iPhone and iPad never take the new layout with no `expect`/`actual` code. `currentWindowAdaptiveInfo()` is state-backed and recomposes on every fold change, exactly as it already does for rotation (feature 002 R3).

A window that does not span the fold (split screen, a pop-up window on one half) gets no `FoldingFeature` from WindowManager, so `isTabletop` is false there: the spec's first edge case needs no extra code.

**Alternatives considered**: depending on `androidx.window:window` directly (`WindowInfoTracker.windowLayoutInfo`) in an Android-only `actual` — rejected, it duplicates what the adaptive library already exposes in common code and adds a platform seam (Constitution VI). `currentWindowAdaptiveInfoV2()` — not in the 1.2.0 multiplatform artifact; `currentWindowAdaptiveInfo()` carries the same `windowPosture`.

## R2 — Where the split falls inside the screen

**Decision**: a design-system layout `FoldSplit` with two slots (`upper`, `lower`). It takes the fold as a window-pixel span (`TabletopFold(top, bottom)`) and measures the upper slot from its own top to `fold.top − gutter` and the lower slot from `fold.bottom + gutter` to its own bottom. Its own top in the window is read with `Modifier.onPlaced { it.positionInWindow().y }` into a float state that only the layout's measure block reads; the first frame (state not yet known) and a `null` fold split the height in half, as do the UI tests.

**Rationale**: the hinge is reported in window coordinates, while the screen content starts below the status bar and the shell's top bar (and beside the rail). The offset is only known after placement. Reading it in the measure block from a state written in `onPlaced` costs one extra layout pass (no recomposition) when the screen first appears or the shell moves it, and is stable afterwards. This is the one deliberate layout-phase back-write in the feature, documented on the composable (`compose-performance`: the value converges in one frame and is read only in layout). Accompanist's former `FoldAwareColumn` used the same idea (placement-time window coordinates).

The fold position is clamped into the layout's height; a degenerate split (a region under 0) gives that region 0 height and the other the rest, never a negative constraint.

**Alternatives considered**: `onGloballyPositioned` + recomposition (heavier, recomposes the screen); computing the shell's top bar height and passing it down (couples the screens to the shell's chrome and breaks with insets); splitting at 50 % of the content height (the fold is at 50 % of the *window*, about 50 dp lower in the content on the reference device, so the gauge would sit on the hinge).

## R3 — Gutter around the fold

**Decision**: `spacing.md` (12 dp) on each side of the fold's bounds, so the two regions' cards are at least 24 dp plus the hinge's own height apart.

**Rationale**: the design guide asks for "a wider gutter to accommodate the hinge"; the layout's normal gap between cards is `md`, so doubling it across the fold keeps the token scale and reads as a deliberate seam. The emulator's fold has zero height; a physical hinge reports its own height, which is added.

## R4 — What goes in each region

| Screen | Upper region | Lower region |
|---|---|---|
| Tuner, Auto Listen | The Auto Listen card with the fitted compact meter (the landscape `FittedMeter`, which sizes the meter to the card's height) or the microphone-denied panel | One settings card (`tuner.settings`): instrument, audio-state pill, mode — the wide selector row |
| Tuner, By Ear | The By Ear card with the landscape string grid (two rows of ⌈n/2⌉ cards sharing the height) | The same settings card plus a second row: string count (guitar), tuning preset picker, loop-tone toggle |
| Metronome | The dial card holding only the ring (readout, tempo name, − / + and drag; no slider), sized from the region's height, beside an accent card with the cells drawn tall | Two columns: the bar-shape card without cells (signature menu, subdivision tiles, style row) and the sound pill; the slider over the tap and play row |

**Rationale**: these are the user's assignments (spec FR-003 to FR-005). Reusing the landscape forms for the upper content is natural: landscape already solves "fit this into a short, wide card" for the meter and the string cards. The tempo dial component already takes any size; the landscape pane sizes it from height. The accent cells get a `barHeight` so the tabletop card draws bars tall enough to read from a music stand (the existing 64 dp bar box is sized for a settings card).

**Narrow windows (added during the T027 review)**: a flip-style foldable half-opened also reports tabletop, with a
window about 412 dp wide. Side by side, the dial and the accent card would get about 190 dp each and the cells would
wrap into two rows of tall bars that do not fit. Under `MetronomeDefaults.TabletopSideBySideMinWidth` (600 dp, the
medium width breakpoint) each half therefore stacks its two cards, and the stacked accent card keeps the 64 dp bar box.

**Alternatives considered**: putting the slider in the upper region with the dial (it is part of the dial card everywhere else) — rejected, the user listed the slider among the bottom controls. Moving the − / + steps out of the dial — rejected, they are drawn inside the ring and are part of the dial widget.

## R5 — Layout selection

**Decision**: `TunerLayout` and `MetronomeLayout` each gain a `Tabletop` entry. `from(windowSizeClass, tabletop)` returns `Tabletop` whenever the posture is tabletop, regardless of the size class; otherwise the existing bands. The screens' wiring composables read the posture once and pass both the layout and the `TabletopFold?` to the content composable.

**Rationale**: the layout enums are the existing single switch every pane reads (`when (layout)` without `else`, `kotlin-control-flow`), so the compiler lists every place the new form must be handled. Window size still matters inside the halves (a 7-string grid, large fonts), which the regions handle by scrolling.

## R6 — Testing tabletop without a physical foldable

**Decision**:
- Pure geometry (`FoldSplit` region arithmetic, `TabletopFold.from(Posture)`, layout selection) in `commonTest` on the JVM and the iOS simulator.
- Compose UI tests in `uiTest` drive `TunerScreenContent` / `MetronomeScreenContent` with `layout = Tabletop` and a `null` fold (half split), asserting region membership by bounds: every upper element's bottom is above every lower element's top, and the regions' tags exist. These run on the iOS simulators and on every attached Android device.
- On the Pixel 9 Pro Fold emulator the real posture is switched with `adb shell cmd device_state state <0|1|2>` (CLOSED / HALF_OPENED / OPENED) and `cmd device_state state reset`, with screenshots of each screen from the inner display (`screencap -d <display id>`).

**Rationale**: `WindowLayoutInfoPublisherRule` / `TestWindowLayoutInfo` (the guide's testing tools) are Android-only `window-testing` artifacts; driving the stateless content with a layout and fold keeps the tests common and follows `compose-ui-testing-patterns` (drive the plain content composable with controlled state). The emulator covers the real-posture path end to end.

## R7 — Stitch screens

**Decision**: after the layouts are built and captured, three new Stitch screens ("Tembra Audio - Tuner Auto Listen (Foldable Tabletop)", "… Tuner By Ear (Foldable Tabletop)", "… Metronome (Foldable Tabletop)") are produced as HTML from the app's captures and uploaded to project `12040171611712589599` with the upload workflow used for the metronome on 2026-10-01 (upload, place via `screenInstances`, re-pull the export into `design/stitch/screens/`).

**Rationale**: `edit_screens` does not persist (verified 2026-09-29); uploads do.

## R8 — Design iteration D1: one controls card, stacked tuner controls, the hinge in book posture

Decided with the user on 2026-10-02 after trying the first build on the emulator (spec "Design iteration D1").

**Decision**:
- Metronome, below a tabletop fold: one `surfaceContainerLow` card, `TabletopControlDeck`, tagged `metronome.settings`:
  a centred "METRONOME CONTROLS" heading (`labelSmall`, `onSurfaceVariant`) over a seam, then a `FlowRow` with the
  signature menu, the subdivision tiles and the sound pill pushed to the end, the `StyleRow`, the `TempoSlider` and the
  tap and play row. The compact-card branch the panel had for tabletop is gone; `MetronomeSettingsPanel` now treats
  `Tabletop` like `Stacked`, and the tabletop form never calls it.
- Tuner, below a tabletop fold: the heading is centred in a `Box` with the audio-state pill at `CenterEnd`; the
  selectors and the By Ear controls stack in one column at full width, capped at `byEarGridMaxWidthWide` (576 dp) and
  centred.
- Book posture: `VerticalFold.from(posture)` takes the first hinge that is vertical and separating
  (`HingeInfo.bounds` left/right in window px; flat hinges are ignored). `FoldSplitRow` is `FoldSplit` on its side: it
  reads its own left edge in the window in `onPlaced` and reuses `FoldSplitGeometry` on the x axis, so the start slot
  ends `spacing.md` before the hinge and the end slot starts `spacing.md` after it. Only the Metronome's `Wide` layout
  uses it (the user's request); without a vertical fold it keeps its one-third column.
- Top bar: `TembraTopBar` pads `statusBars ∪ displayCutout` only on `Top + Horizontal`. On the emulator rotated 180°
  the cutout is reported on the bottom edge (`DisplayCutout{insets=Rect(0, 0 - 0, 136)}`, `sideOverrides`
  `ROTATION_180: bottom`), which the full union added as 56 dp under the title.

**Rationale**: the user's visual review; the hinge API is the same `windowPosture` already used for tabletop (R1).

**Alternatives considered**: applying the hinge split to the wide tuner too — not requested; the gauge still sits
across the fold there (noted in `screenshots/NOTES.md`).

## R9 — Design iteration D2: the wide By Ear grid

Decided with the user on 2026-10-02 (spec "Design iteration D2", FR-013).

**Findings**: on the fold opened flat (852 × 883 dp) the wide grid filled the whole card (about 830 dp) with 255 × 160 dp
cards. `StringCardGrid` applied `widthIn(max = byEarGridMaxWidthWide)` *after* the caller's `fillMaxWidth()`, and a
`widthIn` inside a `fillMaxWidth` cannot narrow it, so the 576 dp cap never took effect; wide cards were only
`heightIn(min = 144.dp)`. With the window shortened to 852 × 697 dp (`adb shell wm size 2076x1700`) the seven-string
grid ran under the card and scrolled.

**Decision**:
- The cap is applied before the caller's modifier; wide cards use the stacked layout's `squareOrTaller`; the
  `stringCardHeightWide` token (the design's `h-36`) is gone, `stringCardMinWide` (128 dp, the smallest card whose
  numeral, note and Audition button still fit) replaces it.
- `WideStringGrid.arrange(count, baseColumns, full, gap, widthBudget, heightBudget, min)`: the base columns (three,
  or two when that fills every row and three would not: the cello) with square cards shrunk to fit, never over the
  three-column size; if that would go under `min`, the first larger column count that keeps `min`; else the largest
  cards, raised to `min`, and the parent scrolls. Wide cards get exactly that width; rows are centred
  (`Arrangement.spacedBy(gap, CenterHorizontally)`).
- `FittedByEarBlock` (a `SubcomposeLayout` in `ByEarPane`) measures the controls row, hands the grid the card's width
  and the height left as budgets, and centres the block vertically when it is shorter than the card; it sits in a
  `verticalScroll` so the fallback still scrolls. The stacked (phone portrait) branch is unchanged.

**Alternatives considered**: non-square cards when height-limited (rejected: the user asked for 1:1); a smaller card
typography below 128 dp (more code for a case only very short large windows reach).
