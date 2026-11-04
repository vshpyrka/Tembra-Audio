# Compose compiler report (feature 010; T088)

- Command: `./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true --rerun-tasks --warning-mode all` (the
  `assembleRelease` task of the task list does not exist on the KMP library; the report is produced by the compile,
  as feature 009 recorded), run 2026-10-28 on the branch's state after User Story 6.
- Source: `app/shared/ui/build/compose_compiler/tembra_shared:ui-composables.txt` and `…-classes.txt`; the entries
  for the feature's composables and state classes are copied into `composables.txt` and `classes.txt` beside this file.

## Composables

Every composable the feature added or extended is restartable and skippable. The parameters the compiler could not
prove stable are listed; none of them defeats skipping (the callers pass remembered or singleton instances).

| Composable | Skippable | Parameters not inferred stable |
|---|---|---|
| `ArmButton` | yes | `unused stable trackTint: Color` |
| `CountInMenu` | yes | — |
| `InputMeter` | yes | `telemetry: InputTelemetry` |
| `MenuPill` | yes | `entries: List<MenuEntry>` |
| `MetronomeSettingsSheet` | yes | `unstable settings: MetronomeSettings` |
| `MonitorToggle` | yes | — |
| `RecordButton` | yes | `telemetry: TransportTelemetry? = @static null` |
| `StopAndLeaveDialog` | yes | — |
| `TimeReadout` | yes | `telemetry: TransportTelemetry` |
| `TimelineScreenContent` | yes | `callbacks: TimelineCallbacks`, `telemetry: TransportTelemetry`, `inputTelemetry: InputTelemetry? = @dynamic <expression>` |
| `TimelineToolbar` | yes | — |
| `TrackHeader` | yes | `inputTelemetry: InputTelemetry? = @static null` |
| `TrackHeaderCompact` | yes | `inputTelemetry: InputTelemetry? = @static null` |
| `TrackLane` | yes | `unstable viewport: TimelineViewport`, `selection: Selection`, `unstable tempoMap: TempoMap? = @static null`, `inputTelemetry: InputTelemetry? = @static null` |
| `TrackList` | yes | `callbacks: TimelineCallbacks`, `inputTelemetry: InputTelemetry? = @dynamic <expression>` |
| `TrackRow` | yes | `callbacks: TimelineCallbacks`, `inputTelemetry: InputTelemetry? = @dynamic <expression>` |
| `TransportBar` | yes | `callbacks: TransportCallbacks`, `telemetry: TransportTelemetry` |

Why those parameters are not inferred stable, and why it is fine:

- `telemetry: TransportTelemetry`, `inputTelemetry: InputTelemetry` are interfaces (`fun interface InputTelemetry`),
  so the compiler reports runtime stability. Both are singletons for the life of the session (`ProjectSession.transportTelemetry`,
  `RecordingController.inputTelemetry`), passed as the same reference every composition; equality by reference keeps the
  composables skipping. This is the pattern feature 009 chose for the playhead's telemetry.
- `callbacks: TimelineCallbacks` / `TransportCallbacks` are data classes of lambdas; the compiler reports them as
  runtime-stable (the classes are stable, see below). `TimelineScreen` remembers one instance per ViewModel and layout.
- `viewport: TimelineViewport`, `tempoMap: TempoMap`, `selection: Selection` come from `:shared:core`, a module the
  Compose compiler does not analyse (no stability configuration file in this project); they are immutable data classes
  of value classes. Feature 009's report carried the same note for `viewport`.
- `settings: MetronomeSettings` on `MetronomeSettingsSheet` is feature 004's class from `:shared:core`, unchanged here.
- `entries: List<MenuEntry>` on `MenuPill` is feature 004's parameter; `CountInMenu` remembers the list once.
- `ArmButton.trackTint` is reported unused: the arm circle is drawn in the `record` token whatever the track's tint
  (the contract's `ArmButton(armed, trackTint, onToggle, modifier)` signature is kept for the mixer's reuse).

## State classes

| Class | Stability |
|---|---|
| `ClipUi` | stable |
| `LiveTakeUi` | stable |
| `RecordingUi` | stable |
| `TimelineCallbacks` | stable |
| `TimelineUiState` | stable |
| `TrackUi` | stable |
| `TransportCallbacks` | stable |
| `TransportUiState` | stable |

`RecordingUi` and `LiveTakeUi` are marked `@Immutable` (every field is a value, an enum, a sealed interface of
immutable data classes, or an overview the controller replaces and never mutates in place). `TimelineUiState`,
`TrackUi`, `ClipUi`, `TransportUiState` and the callback holders were stable already.
