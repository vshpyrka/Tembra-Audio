---

description: "Task list for feature 005: tabletop posture"
---

# Tasks: Tabletop Posture for the Tuner and the Metronome

**Input**: Design documents from `/specs/005-tabletop-posture/`

**Prerequisites**: plan.md, spec.md (FR-001–FR-010, SC-001–SC-005), research.md (R1–R7), data-model.md (§1–§4), contracts/tabletop-layout.md, quickstart.md.

**Tests**: Required. Constitution III: every implementation task is preceded by the test that fails without it. Lanes from `app/` with `--warning-mode all`, always through the `gradle-run` wrapper (Constitution VIII): `checkAndroidHost` (JVM), `checkIos "-Ptembra.iosSimulator=<name>"` (iPhone 17 and iPad Pro 13-inch (M5)), `checkAndroidDevice` with `ANDROID_SERIAL` per attached Android target (Pixel 6 Pro `19021FDEE001AH`, Pixel 9 Pro Fold emulator `127.0.0.1:5556`).

**Skills** (Constitution VIII): route through `using-chrisbanes-skills`; `compose-component-design` for `FoldSplit`, `compose-performance` for its one layout-phase read (research R2), `compose-state-and-effects` for the posture read in the wiring composables, `kotlin-control-flow` for the exhaustive `when` on the new `Tabletop` entries, `kotlin-api-design` for `TabletopFold`, `compose-ui-testing-patterns` for the `uiTest` classes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency on unfinished tasks)
- **[Story]**: US1–US4 from spec.md
- `:shared:ui` = `app/shared/ui/src/<sourceSet>/kotlin/audio/tembra/ui/`

---

## Phase 1: Setup

- [X] T001 Confirm `currentWindowAdaptiveInfo().windowPosture`, `Posture` and `HingeInfo` compile from `commonMain` of `:shared:ui` against `compose-material3-adaptive` 1.2.0 (research R1) — no catalog change; record the check in `specs/005-tabletop-posture/screenshots/NOTES.md`

---

## Phase 2: Foundational (fold detection and the split layout)

**Purpose**: what every story needs — the fold value, the region arithmetic, the two-slot layout and the `Tabletop` layout entries.

- [X] T002 [P] Failing tests for `TabletopFold.from(posture)` in `:shared:ui` `commonTest/.../posture/TabletopFoldTest.kt`: `null` for `Posture()`; `null` when `isTabletop` is false even with a horizontal hinge; the first horizontal separating hinge's `bounds.top`/`bounds.bottom` when tabletop; vertical hinges ignored; `require(bottom >= top)`
- [X] T003 [P] Failing tests for `FoldSplitGeometry` in `commonTest/.../designsystem/FoldSplitGeometryTest.kt` per data-model §3: half split when `fold == null` or `originY` is `NaN` ("`(height − 2·gutter) / 2`"); `upperHeight = fold.top − originY − gutter` "clamped to `0..height`"; `lowerTop = fold.bottom − originY + gutter` "clamped to `upperHeight..height`"; fold above or below the layout; zero-height fold
- [X] T004 [P] Failing tabletop cases in `commonTest/.../tuner/TunerLayoutTest.kt` and `commonTest/.../metronome/MetronomeLayoutTest.kt`: `from(windowSizeClass, fold)` is `Tabletop` for any size class when `fold != null`, the existing band otherwise
- [X] T005 [P] Implement `TabletopFold` (`top`, `bottom` in window px, `from(Posture)`) and `@Composable currentTabletopFold()` in `commonMain/.../posture/TabletopFold.kt` (T002 green)
- [X] T006 [P] Implement `FoldSplitGeometry` in `commonMain/.../designsystem/FoldSplitGeometry.kt` (T003 green)
- [X] T007 Implement `FoldSplit(fold, modifier, gutter, upper, lower)` in `commonMain/.../designsystem/FoldSplit.kt`: one `Layout` with two slot contents measured at `Constraints.fixed(width, upperHeight)` / `(width, lowerHeight)` from `FoldSplitGeometry`; origin read with `Modifier.onPlaced { positionInWindow().y }` into a float state read only in measure (research R2, documented); upper composed first for traversal (FR-010)
- [X] T008 Add `Tabletop` to `TunerLayout` (`commonMain/.../tuner/TunerLayout.kt`) and `MetronomeLayout` (`commonMain/.../metronome/MetronomeLayout.kt`) with `from(windowSizeClass, fold)`; handle the new entry in every existing exhaustive `when` (temporarily as the landscape form) so the module compiles (T004 green)
- [X] T009 Run `checkAndroidHost` and `checkIos` (iPhone 17): green, zero warnings

**Checkpoint**: fold detection and the split are tested; no screen uses them yet.

---

## Phase 3: User Story 1 — Tune with the gauge standing up (P1) 🎯 MVP

**Goal**: Auto Listen in tabletop: meter (or denied panel) above the fold, instrument/pill/mode below.

**Independent Test**: `TunerTabletopLayoutTest` Auto Listen cases; emulator half-opened with the test signal.

- [X] T010 [US1] Failing UI tests in `uiTest/.../tuner/TunerTabletopLayoutTest.kt` (root `requiredSize(883 × 740 dp)`, `layout = Tabletop`, `fold = null`): tags `tuner.tabletop.upper` / `.lower` exist; `tuner.meter` inside upper; `tuner.settings` with instrument, pill and mode controls inside lower; upper bottom + `2 × spacing.md` ≤ lower top; denied state puts the grant button in upper
- [X] T011 [US1] `TunerTabletopSettings` in `commonMain/.../tuner/TunerTabletopSettings.kt`: gradient card tagged `tuner.settings`, the wide selector row (instrument, pill, spacer, mode) and, in By Ear, a second `FlowRow` (string count when `instrument.hasStringCount`, preset picker weighted, loop toggle); scrolls vertically when taller than its region
- [X] T012 [US1] Tabletop branch in `commonMain/.../tuner/TunerScreenContent.kt`: `FoldSplit(fold, upper = ModePane, lower = TunerTabletopSettings)` with the region tags; `TunerScreenContent` gains `fold: TabletopFold? = null`
- [X] T013 [US1] `AutoListenPane` treats `Tabletop` as `Landscape` (fitted compact meter sized from height) in `commonMain/.../tuner/AutoListenPane.kt`; `TunerSelectors` handles `Tabletop` as `Wide`
- [X] T014 [US1] `TunerScreen` reads `currentTabletopFold()` and passes `TunerLayout.from(windowSizeClass, fold)` and `fold` in `commonMain/.../tuner/TunerScreen.kt` (T010 green on the iPhone 17 simulator)

**Checkpoint**: US1 works on the emulator in tabletop.

---

## Phase 4: User Story 2 — Tune by ear with the string cards standing up (P1)

- [X] T015 [US2] Failing By Ear cases in `TunerTabletopLayoutTest.kt`: every `tuner.string.*` card inside upper for 6- and 7-string presets and cello; string count, preset picker and loop toggle inside lower; a card tap still calls `onAudition`
- [X] T016 [US2] `ByEarPane` uses the landscape filled grid for `Tabletop` and `StringCardGrid`/`StringCard` treat `Tabletop` as `Landscape` in `commonMain/.../tuner/ByEarPane.kt`, `StringCardGrid.kt`, `StringCard.kt` (T015 green)

---

## Phase 5: User Story 3 — Watch the beat standing up (P1)

- [X] T017 [US3] Failing UI tests in `uiTest/.../metronome/MetronomeTabletopLayoutTest.kt` (root 883 × 740 dp, `fold = null`): tags `metronome.tabletop.upper` / `.lower`; `metronome.dialCard` and `metronome.cells` (every `metronome.cell.N`) inside upper; `metronome.settings`, `metronome.bar`, the slider, tap tempo, play and the sound pill inside lower; no slider inside `metronome.dialCard`; cell click calls `onCycleBeat`; play toggles
- [X] T018 [US3] `AccentCells` gains an optional `barHeight: Dp` override (default per form) in `commonMain/.../metronome/components/AccentCells.kt`; existing callers unchanged
- [X] T019 [US3] `MetronomeSettingsPanel` `Tabletop` branch in `commonMain/.../metronome/MetronomeSettingsPanel.kt`: bar-shape card WITHOUT cells (signature menu + subdivision tiles line, style row), then the sound pill
- [X] T020 [US3] Tabletop branch in `commonMain/.../metronome/MetronomeScreenContent.kt`: `FoldSplit` with upper = row of a dial card holding only the ring (sized from the region height, `DialMin`..`CompactDialMax`) and an accent card with tall `AccentCells`; lower = row of the settings panel (scrolling) and a column with the slider over the tap and play row; `MetronomeScreenContent` gains `fold: TabletopFold? = null`; snackbar unchanged
- [X] T021 [US3] `MetronomeScreen` reads `currentTabletopFold()` in `commonMain/.../metronome/MetronomeScreen.kt` (T017 green)

---

## Phase 6: User Story 4 — Everything else stays as it is (P2)

- [X] T022 [US4] Full lanes: `checkAndroidHost`; `checkIos` on iPhone 17 and on iPad Pro 13-inch (M5); `checkAndroidDevice` on the Pixel 6 Pro and on the fold emulator — all existing tuner/metronome tests unmodified and green (the emulator's engine timing test fails with engine sources identical to `main`: screenshots/NOTES.md)
- [X] T023 [US4] Regression captures of Tuner (both modes) and Metronome on the Pixel 6 Pro (portrait, landscape), iPhone 17, iPad Pro into `specs/005-tabletop-posture/screenshots/`, compared with the 003/004 captures; differences recorded in `NOTES.md`

---

## Phase 7: Polish & real-posture verification

- [X] T024 Emulator real-posture captures (quickstart §2): half-opened Tuner Auto Listen (test signal), By Ear, Metronome running; opened (flat); stored as `fold-tabletop-*.png` / `fold-flat-*.png`
- [X] T025 Ten flat ↔ half-opened changes with the metronome running and the tuner listening (SC-002, SC-003); largest font in tabletop (SC-005); outcomes in `NOTES.md`
- [X] T026 Compose compiler report (`-PcomposeReports=true`) for `:shared:ui`; `FoldSplit` and the new panes skippable, or explained in `NOTES.md`
- [X] T027 Skill review of the diff (`using-chrisbanes-skills`), fixes applied
- [X] T028 Stitch: three "Foldable Tabletop" screens from the captures uploaded to project `12040171611712589599`, placed on the canvas, export re-pulled into `design/stitch/screens/` and `screens.json` (research R7)

---

## Phase 8: Design iteration D1 (2026-10-02, the user's review on the emulator)

- [X] T030 [US3] One metronome controls card below the fold: `TabletopControlDeck` in `commonMain/.../metronome/MetronomeScreenContent.kt` (centred "METRONOME CONTROLS" heading over a seam; signature, tiles and sound pill; style; slider; tap and play), tagged `metronome.settings` inside the `metronome.tabletop.lower` box; `MetronomeSettingsPanel` drops its tabletop card and treats `Tabletop` as `Stacked`; tests `onlyTheDialAndTheCellsAreAboveTheFold` (updated) and `theControlsCardIsHeadedAndReadsTopToBottom`
- [X] T031 [US1] [US2] Stacked tuner controls: `TunerTabletopSettings` centres its heading with the pill at `CenterEnd` and stacks instrument, mode, string count, preset and loop tone in one column capped at `byEarGridMaxWidthWide`; test `theControlsStackUnderACentredHeading`
- [X] T032 [P] `VerticalFold` (`commonMain/.../posture/VerticalFold.kt`) with `from(Posture)` and `currentVerticalFold()`; test `commonTest/.../posture/VerticalFoldTest.kt` (first vertical separating hinge; flat or horizontal hinges ignored; `require(right >= left)`)
- [X] T033 [US5] `FoldSplitRow` (`commonMain/.../designsystem/FoldSplitRow.kt`, `FoldSplitGeometry` on the x axis) and the book-posture branch of `WideMetronome`; `MetronomeScreen` passes `currentVerticalFold()`; test `uiTest/.../metronome/MetronomeHingeLayoutTest.kt`
- [X] T034 FR-012: `TembraTopBar` pads `statusBars ∪ displayCutout` on `Top + Horizontal` only (the emulator rotated 180° reports its cutout on the bottom edge)
- [X] T035 Lanes: JVM, iPhone 17, iPad Pro, Android device on the Pixel 6 Pro and the fold emulator; captures `android-fold-d1-*.png` and NOTES
- [X] T036 Spec, research R8, data model, contract and plan updated for D1 (Stitch screens not refreshed: the user asked for code and spec-kit documents only)

---

## Phase 9: Design iteration D2 (2026-10-02, the wide By Ear grid)

- [X] T037 [US6] Square wide cards: `StringCard` uses `squareOrTaller` for `Wide`; `StringCardGrid` applies its width cap before the caller's modifier; token `stringCardHeightWide` removed (`TunerTokensTest`)
- [X] T038 [US6] Centred grid: the wide `ByEarPane` centres the controls and the grid; a short last row is centred; test `TunerScreenLayoutTest.wideCardsAreSquareInACentredGrid`
- [X] T039 [US6] Cello 2 + 2: two wide columns when that fills every row and three would not; the cello grid narrows so its cards keep their size
- [X] T040 [P] [US6] `WideStringGrid` (`commonMain/.../tuner/WideStringGrid.kt`) with `arrange(...)`; test `commonTest/.../tuner/WideStringGridTest.kt` (tall keeps base columns at full size, slightly short shrinks in base columns, short spreads 7 as 4 + 3, cello keeps 2 + 2, never under 128 dp); token `stringCardMinWide` = 128 dp
- [X] T041 [US6] `FittedByEarBlock` in `ByEarPane` hands the grid width and height budgets; test `TunerScreenLayoutTest.aShorterWideWindowFitsAllSevenCards`
- [X] T042 Runs and lanes: the app on the iPhone 17 and iPad Pro simulators, the iPhone 14 and the Pixel 6 Pro; JVM, iPhone 17, iPad Pro and Android device lanes; captures `*-d2-*.png`
- [X] T043 Stitch: tabletop screens refreshed to D1 (v2), new "Metronome (Foldable Book)" and "Tuner By Ear (Foldable Open)" screens; spec, research R9, data model, contract, plan and NOTES updated

## Dependencies

Phase 1 → Phase 2 → US1 → US2 (shares `TunerTabletopSettings`) ; US3 depends only on Phase 2 ; US4 after US1–US3 ; Polish last.

## Parallel examples

- T002, T003, T004 together; then T005, T006 together.
- US3 (T017–T021) can run beside US1/US2 (different packages).

## Implementation strategy

MVP = Phases 1–3 (Auto Listen in tabletop). Then By Ear, the metronome, the regression sweep, real-posture verification and Stitch.
