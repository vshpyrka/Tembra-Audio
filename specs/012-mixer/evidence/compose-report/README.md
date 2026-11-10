# Compose compiler report (feature 012; T097)

- Command: `./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true --rerun --warning-mode all` (the `assembleRelease`
  task of the task list does not exist on the KMP library; the report is produced by the compile, as features 009 and
  010 recorded; `--rerun` because the report destination is not a compile input), run 2026-11-08 on the branch's state
  after User Story 7.
- Source: `app/shared/ui/build/compose_compiler/tembra_shared:ui-composables.txt` and `…-classes.txt`; the entries for
  the feature's composables and state classes are copied into `composables.txt` and `classes.txt` beside this file.

## Composables

Every composable the feature added or extended is restartable and skippable. The parameters the compiler could not
prove stable are listed; none defeats skipping: the callers pass remembered instances (`MeterFeed`, `MeterReading`, the
telemetry readers) or the graph's singletons (`TimelineCallbacks` is itself stable; `MixTelemetry`, `InputTelemetry` and
`AppGraph` are interfaces the compiler cannot infer). The three `remember*` helpers return values, so they are neither
restartable nor skippable by design.

| Composable | Skippable | Parameters not inferred stable |
|---|---|---|
| `MixerSheet` | yes | `callbacks: TimelineCallbacks`, `telemetry: MixTelemetry`, `inputTelemetry: InputTelemetry` |
| `MixerHandle` | yes | — |
| `MixerStripRow` | yes | `callbacks: TimelineCallbacks`, `feed: MeterFeed` |
| `MixerStrip` | yes | `callbacks: TimelineCallbacks`, `feed: MeterFeed` |
| `StripBody` | yes | `callbacks: TimelineCallbacks`, `feed: MeterFeed`, `reading: MeterReading` |
| `StripLabel` | yes | — |
| `MasterStrip` | yes | `callbacks: TimelineCallbacks`, `feed: MeterFeed` |
| `Fader` | yes | `scale: FaderScale? = @static <expression>` (a sealed interface of two objects) |
| `PanKnob` | yes | — |
| `LevelMeter` | yes | `reading: MeterReading` |
| `VuDial` | yes | `unstable levels: MixSnapshot` (the engine's snapshot, one remembered instance per surface) |
| `MixerWindowContent` | yes | `callbacks: TimelineCallbacks`, `telemetry: MixTelemetry`, `inputTelemetry: InputTelemetry` |
| `MixerWindowScreen` | yes | `viewModel: MixerWindowViewModel? = @dynamic <expression>` |
| `MixerWindow` | yes | `appGraph: AppGraph` |
| `GainReadout` | yes | — |
| `PanSlider` | yes | — |
| `TrackHeader` | yes | `inputTelemetry: InputTelemetry? = @static null` |
| `rememberMeterFeed`, `rememberMixerLevelMirror`, `rememberFrameTick` | n/a (return values) | `inputTelemetry: InputTelemetry`, `unstable levels: MixSnapshot` |

## Classes

| Class | Stability | Note |
|---|---|---|
| `MixerUi`, `StripUi`, `MasterUi`, `MixerAnchors`, `MixerWindowUiState` | stable | `@Immutable` data classes over value classes and immutable lists |
| `MeterLevel` | stable | the mirror's per-strip value |
| `TimelineCallbacks` | stable | a data class of function types |
| `MeterFeed`, `MixerLevelMirror` | unstable | hold the frame tick's `LongState` and the mirror's `MutableState`s; one remembered instance per surface, passed by identity |

The row's items are keyed by track id with one `contentType`; the strips' lambdas capture only the stable `StripUi` and
`TimelineCallbacks`, so the strong-skipping compiler memoises them without `remember` (data-model.md §4, research R6).
