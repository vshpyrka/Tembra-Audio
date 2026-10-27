# Compose compiler report, feature 009 (T132)

- Date: 2026-10-19
- Command: `./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true --rerun`
- Raw files: `specs/009-timeline-arranger/evidence/compose-report/` (`composables.txt`, `composables.csv`, `classes.txt`)
- Strong skipping is enabled. A restartable composable with an unstable parameter is therefore still `skippable`: stable
  values are compared with `equals`, unstable ones by identity. How to read the lines: `.claude/skills/compose-performance/references/stability.md`.
- Scope "feature 009": the packages `audio.tembra.ui.timeline`, `.timeline.components`, `.projects`,
  `.projects.components`, `.tracksettings`, `.transport`, `.session`, plus the design-system pieces `TembraDialog`,
  `TembraMenu` (and its `TembraMenuRow`), `StopAndLeaveDialog` and `TembraTooltip`.

## Counts

| Scope | Composables | restartable skippable | restartable, not skippable | neither (not restartable, not skippable) |
|---|---|---|---|---|
| Whole `:shared:ui` module (`composables.csv`, 212 rows) | 212 | 184 | 0 | 28 |
| Feature 009 packages | 59 | 59 | 0 | 0 |

Feature 009 breakdown: timeline 2 and timeline.components 24 (26), projects 2 and projects.components 14 (16),
tracksettings 6, transport 5, session 1, design system 5 (`StopAndLeaveDialog`, `TembraDialog`, `TembraMenu`,
`TembraMenuRow`, `TembraTooltip`) = 59. `TembraCard` and `TembraTextField` are also new on the branch, are in the design-system
package outside the task's list, and are both `restartable skippable`.

The 28 "neither" entries of the module are all older code and none is in a feature 009 package: the value-returning
`remember*` helpers (`rememberEngineHealthWriter`, `rememberFrameTick`, `rememberWidestChip`, `rememberGlowPulse`,
`rememberLayoutMode`, `rememberReduceMotion`), `currentVerticalFold`/`currentTabletopFold`, the metronome chrome helpers
(`pocketHeights`, `dialCardChrome`, `landscapeDialChrome`, `compactDialMin`), and the `TembraTheme`, `TembraFonts`,
`TembraTypography`, `TembraIcons` getters and accessors. `FrameTick.kt` (`rememberFrameTick`) arrived with feature 004
(b1cf027) and `TembraChipRow.kt` (`rememberWidestChip`) predates this branch; feature 009 only calls them.

## Non-skippable feature 009 composables

| Composable | File | Why | Verdict |
|---|---|---|---|
| (none) | | `composables.txt` has no feature 009 entry that is `restartable` without `skippable`, and none without `restartable` | The table is empty: 0 rows |

The feature adds no inline composable, no `Modifier`-returning composable helper and no lambda-returning composable that
shows up as non-restartable. The per-frame readers (`PlayheadOverlay`, `TimeReadout`) are skippable and read the
frame-rate value in a `Canvas` draw lambda through `rememberFrameTick` and `TransportTelemetry.read`, so the frame tick
never causes recomposition.

### Skippable composables with an unstable parameter (for the record)

These are skippable under strong skipping, so they are not rows of the table above. Each shows what the unstable
parameter costs.

| Composable | File (under `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/`) | Unstable parameter | Why it is unstable | Consequence |
|---|---|---|---|---|
| `ProjectOverviewChart` | `projects/components/ProjectOverviewChart.kt` | `overview: CardOverview` | Class in `:shared:data` (not in `classes.txt`); holds two `ByteArray` properties (`peaks`, `colors`). It overrides `equals` with `contentEquals`, but the compiler cannot treat arrays as stable, so strong skipping compares by identity | The card skips while the same instance is passed. The instance changes only when the project's overview changes, which is a real change. Acceptable |
| `MetronomeSettingsSheet` | `timeline/components/MetronomeSettingsSheet.kt` | `settings: MetronomeSettings` | `data class` in `:shared:core` (`core/metronome/MetronomeSettings.kt`), which is not Compose-compiled, so the compiler infers nothing about it (it is not in `classes.txt`) | Identity comparison; a sheet that is open only while the user edits. Acceptable |
| `PlayheadOverlay` | `timeline/components/PlayheadOverlay.kt` | `viewport: TimelineViewport` (also `telemetry: TransportTelemetry`, an interface from this module, no marker) | `data class` in `:shared:core` (`core/timeline/TimelineViewport.kt`) with `val` Double, Int and `Beats` fields, but the module is not Compose-compiled. `TransportTelemetry` is a `fun interface` implemented by the session | Recomposes only when the viewport changes (scroll, zoom, resize), where the line must move anyway. The per-frame position is read in the draw lambda. Acceptable |
| `TimelineRuler` | `timeline/components/TimelineRuler.kt` | `viewport: TimelineViewport`, `tempoMap: TempoMap` | Both are `data class`es in `:shared:core` (`TimelineViewport`; `TempoMap(tempo, timeSignature)`) | The ruler's ticks depend on both; it recomposes on a real viewport or tempo change. Acceptable |
| `TrackLane` | `timeline/components/TrackLane.kt` | `viewport: TimelineViewport` | As above | A lane has to be redrawn when the viewport moves. `track: TrackUi` and the callbacks are stable, so a lane skips when only the position ticks (10 Hz `positionBeats` is not a lane parameter). Acceptable |

Parameters with no `stable`/`unstable` marker in the report (`callbacks: TimelineCallbacks`, `ProjectsCallbacks`,
`TransportCallbacks`, `selection: Selection`, `telemetry: TransportTelemetry`, `viewModel:` on the screens) are
runtime-checked or interface types. They do not stop skipping.

## Unstable classes in the feature 009 packages (`classes.txt`)

| Class | Unstable member | Crosses a composable boundary? |
|---|---|---|
| `projects.ProjectsViewModel` | `ViewModel` subclass | Yes, only as the defaulted `viewModel = metroViewModel()` parameter of `ProjectsScreen`. Its state is read through `uiState.collectAsStateWithLifecycle()`; nothing below the screen receives it |
| `timeline.TimelineViewModel` | `ViewModel` subclass | Same: `TimelineScreen` parameter, state read with `collectAsStateWithLifecycle()` |
| `tracksettings.TrackSettingsViewModel` | `ViewModel` subclass | Same: parameter of `TrackSettingsScreen` and `TrackSettingsInlineRow` (the report shows it twice, lines 1131 and 1137) |
| `session.ProjectSession` | holds the live project, engine bridge and mutable state | Yes, one parameter: `AppLifecycleObserver(session)`, a skippable effect-only composable that renders nothing |
| `session.SessionState` | `project: Project?`, `viewState: ProjectViewState`, `overviews: PersistentMap<…, WaveformOverview>` | No. Held in a `StateFlow` inside the session and mapped by the ViewModels |
| `timeline.TimelineViewState` | `viewport: TimelineViewport`, `tap: TapTempoSequence`, `metronomeSettings: MetronomeSettings` | No. Internal to `TimelineViewModel`; the screen receives `TimelineUiState` (stable) and its fields as parameters |
| `session.ArrangementResult.Mapped` | `snapshot: ArrangementSnapshot` | No. Result of the mapper, consumed in the session |
| `session.AppStartup`, `session.ClipSourceRegistry`, `session.OverviewTracker`, `projects.ProjectsSeeder` | service classes with mutable members (`ClipSourceRegistry.open` is a `LinkedHashMap`) | No. None appears as a parameter in `composables.txt` |

The `TimelineViewport`, `TempoMap`, `MetronomeSettings` and `CardOverview` types that do cross as parameters are in the
table above; they are declared in `:shared:core` and `:shared:data`, so `classes.txt` (module `:shared:ui` only) does not list them.

## Verdict

The report is clean for feature 009. All 59 composables of the feature are `restartable skippable`; none is merely
restartable or non-restartable, so there is nothing to fix on the per-frame or per-keystroke paths. The timeline lanes,
the playhead overlay, the time readout and the projects grid cards are all skippable. The two per-frame readers draw
the frame-rate position inside `Canvas` and never recompose for it. The five unstable parameters (`TimelineViewport`,
`TempoMap`, `MetronomeSettings`, `CardOverview`, and `TransportTelemetry` as an interface) compare by identity, change only
when the data really changes, and need no annotation. No stability configuration entry is recommended. Adding
`stable` promises for the `:shared:core` types would be possible only after checking every property, and would buy nothing
measurable here.
