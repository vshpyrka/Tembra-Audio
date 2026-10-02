# Implementation Plan: Tabletop Posture for the Tuner and the Metronome

**Branch**: `005-tabletop-posture` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-tabletop-posture/spec.md`

## Summary

When a foldable is half-opened with a horizontal fold, the Tuner and the Metronome split their content at the fold:
what the musician watches (the meter, the string cards, the tempo dial and the accent cells) above it, every setting
below it. The posture comes from Material 3 adaptive's `windowPosture` in common code (research R1); a new
design-system layout `FoldSplit` places two slots around the fold's window position with a gutter (R2, R3); the tuner
and metronome layout enums gain a `Tabletop` entry whose panes reuse the landscape meter, the landscape string grid and
the tempo dial sized from height (R4, R5). iOS and non-foldable Android never report tabletop and keep their layouts.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Kotlin Multiplatform), Compose Multiplatform 1.12.1

**Primary Dependencies**: `org.jetbrains.compose.material3.adaptive:adaptive` 1.2.0 (already a dependency of
`:shared:ui` since feature 002) for `currentWindowAdaptiveInfo().windowPosture`; no new dependency

**Storage**: N/A (the posture is derived, never stored)

**Testing**: `kotlin.test` in `commonTest` (JVM host lane + iOS simulator), Compose UI tests in `uiTest` (iOS
simulators and attached Android devices), real-posture checks on the Pixel 9 Pro Fold emulator via `cmd device_state`

**Target Platform**: Android foldables (behaviour); Android phones/tablets, iPhone, iPad (no change)

**Project Type**: mobile app (KMP), changes confined to `:shared:ui`

**Performance Goals**: no recomposition per frame from the split; the fold's offset back-write costs one extra layout
pass when the screen is first placed or moves (R2); the meter's 10 Hz reading and the cells' draw-phase lighting keep
their current invalidation scope

**Constraints**: nothing in the fold's area or its 12 dp gutters; 44 dp touch targets in the lower region; existing
layouts unchanged; zero compiler warnings

**Scale/Scope**: 2 screens × 1 new layout; ~4 new files, ~10 touched files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict |
|---|---|
| I. Real-time audio | Pass — UI-only; no engine or callback change. Posture changes do not touch the ViewModels (FR-008). |
| II. One shared engine | Pass — untouched. |
| III. Test-first | Pass — geometry, fold derivation and layout selection get failing `commonTest` tests first; region membership gets failing `uiTest` tests before the panes are built. |
| IV. Adaptive UI | Pass — this feature *is* Principle IV for foldables; the layout is driven by the window's posture, verified by screenshots on the emulator and regression captures elsewhere. Every new region is tagged; touch targets stay 44 dp; tokens only (`spacing.md` gutter). |
| V. Beats | N/A. |
| VI. Simplicity | Pass — no new dependency (R1 rejects `androidx.window` direct use); no platform seam (posture is common); landscape panes reused rather than new ones. |
| VII. Code organisation | Pass — `TabletopFold`, `FoldSplit`, `FoldSplitGeometry` each in their own file; tuner/metronome tabletop panes in their feature packages. |
| VIII. Skills | Pass — `compose-component-design` (`FoldSplit`: `modifier` first optional param on the root, slots for content), `compose-performance` (the one layout-phase read of an `onPlaced` value, R2; cells still lit in draw), `compose-state-and-effects` (posture read in the wiring composables, content stays stateless), `kotlin-control-flow` (exhaustive `when` on the new enum entries), `compose-ui-testing-patterns` (drive content with controlled layout and fold), `gradle-run` for every Gradle run. Compose compiler report with `-PcomposeReports=true` is part of the polish phase. |

Re-check after Phase 1: unchanged; no Complexity Tracking entry needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-tabletop-posture/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/tabletop-layout.md
├── checklists/requirements.md
├── screenshots/            # captures + NOTES.md (implementation)
└── tasks.md                # /speckit-tasks
```

### Source Code (repository root)

```text
app/shared/ui/src/
├── commonMain/kotlin/audio/tembra/ui/
│   ├── posture/TabletopFold.kt                 # new: fold span + from(Posture) + currentTabletopFold()
│   ├── designsystem/FoldSplit.kt               # new: two-slot layout around the fold
│   ├── designsystem/FoldSplitGeometry.kt       # new: pure region arithmetic
│   ├── designsystem/FoldSplitRow.kt            # D1: the split on its side, at a book-posture hinge
│   ├── designsystem/TembraTopBar.kt            # D1: top and side insets only (feature 002 defect)
│   ├── posture/VerticalFold.kt                 # D1: the book-posture hinge
│   ├── tuner/TunerLayout.kt                    # + Tabletop, from(windowSizeClass, fold)
│   ├── tuner/TunerScreen.kt                    # reads the fold, passes layout + fold
│   ├── tuner/TunerScreenContent.kt             # Tabletop branch: FoldSplit(mode pane | settings card)
│   ├── tuner/TunerTabletopSettings.kt          # new: lower-region settings card
│   ├── tuner/AutoListenPane.kt, ByEarPane.kt, StringCardGrid.kt, StringCard.kt, TunerSelectors.kt  # handle Tabletop
│   ├── metronome/MetronomeLayout.kt            # + Tabletop
│   ├── metronome/MetronomeScreen.kt            # reads the fold
│   ├── metronome/MetronomeScreenContent.kt     # Tabletop branch: FoldSplit(dial + cells | settings + transport)
│   ├── metronome/MetronomeSettingsPanel.kt     # Tabletop branch: bar-shape card without cells + sound pill
│   └── metronome/components/AccentCells.kt     # optional barHeight for the tall tabletop cells
├── commonTest/kotlin/audio/tembra/ui/
│   ├── posture/TabletopFoldTest.kt
│   ├── designsystem/FoldSplitGeometryTest.kt
│   ├── tuner/TunerLayoutTest.kt, metronome/MetronomeLayoutTest.kt   # + tabletop cases
└── uiTest/kotlin/audio/tembra/ui/
    ├── tuner/TunerTabletopLayoutTest.kt
    └── metronome/MetronomeTabletopLayoutTest.kt
```

**Structure Decision**: everything stays in `:shared:ui` (Constitution VII feature packages); the fold type gets a
small `posture` package because both features and the design system use it.

## Design iteration D1 (2026-10-02)

The user's review of the first build changed the lower regions (one metronome controls card, a stacked tuner column,
centred headings), added the book-posture hinge split for the wide metronome and surfaced a top-bar inset defect;
research R8 records the decisions, spec "Design iteration D1" and FR-011/FR-012 the behaviour, tasks T030–T036 the
work. The Constitution Check is unchanged: no dependency, the hinge comes from the same common `windowPosture`.

## Design iteration D2 (2026-10-02)

The wide By Ear grid (a feature 003 layout, reached here on the opened foldable): square cards, a working 576 dp cap,
two-by-two for the cello, cards fitted to the height (`WideStringGrid`, `FittedByEarBlock`). Research R9, FR-013,
tasks T037–T043. New file `commonMain/.../tuner/WideStringGrid.kt`; token `stringCardMinWide` replaces
`stringCardHeightWide`. Constitution Check unchanged.

## Complexity Tracking

None.
