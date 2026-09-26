# Contract: Tuner screen (`:shared:ui`)

**Package**: `audio.tembra.ui.tuner` | **Entry**: `DestinationContent(Destination.Tuner)` → `TunerScreen()` | **Depends on**: contracts/tuner-audio-kotlin.md, tuner-domain.md, tuner-preferences.md, microphone-permission.md, reference-sounds.md, design-system-additions.md, data-model.md §3–§8

```kotlin
// TunerViewModel.kt — @Inject @ContributesIntoMap(AppScope::class) @ViewModelKey; entry-scoped (rememberViewModelStoreNavEntryDecorator)
class TunerViewModel(
    private val audio: TunerAudio, private val permission: MicrophonePermission,
    private val preferences: TunerPreferences, private val sounds: ReferenceSoundRepository,
) : ViewModel() {
    val uiState: StateFlow<TunerUiState>
    val telemetry: PitchTelemetry                       // stable handle for draw-phase reads: fun read(into: PitchSnapshot) = audio.readPitch(into)
    fun selectInstrument(instrument: Instrument); fun selectGuitarStrings(count: GuitarStringCount); fun selectPreset(id: String)
    fun selectMode(mode: TunerMode); fun setLoopTone(on: Boolean)
    fun audition(stringIndex: Int); fun stopAudition()
    fun requestMicrophone()                              // NotDetermined / DeniedCanAsk → request(); DeniedOpenSettings → openSystemSettings()
    fun screenVisible(visible: Boolean); fun foreground(started: Boolean)   // ON_START / ON_STOP (research R7)
    fun onResumed()                                      // permission.refresh() only
    fun useTestSignal(signal: TestSignal?)               // debug builds only (research R16): listen through the synthetic device, no permission needed
    override fun onCleared()                             // stopListening(), stopTone(), unload samples
}
// ListeningPolicy.kt — pure (data-model.md §3)
object ListeningPolicy { fun desired(inputs: PolicyInputs): DesiredAudio }

// TunerScreen.kt — stateful
@Composable fun TunerScreen(viewModel: TunerViewModel = metroViewModel(), modifier: Modifier = Modifier)
//   DisposableEffect(Unit) { viewModel.screenVisible(true); onDispose { viewModel.screenVisible(false) } }
//   LifecycleStartEffect(Unit) { viewModel.foreground(true); onStopOrDispose { viewModel.foreground(false) } }   // start/stop, not resume/pause (analysis M1)
//   LifecycleResumeEffect(Unit) { viewModel.onResumed(); onPauseOrDispose { } }
//   val testSignal = LocalTunerTestSignal.current; LaunchedEffect(testSignal) { viewModel.useTestSignal(testSignal) }
//   windowWidth = WindowWidth.from(currentWindowAdaptiveInfo().windowSizeClass)

// TunerScreenContent.kt — stateless; what UI tests drive
@Composable fun TunerScreenContent(state: TunerUiState, callbacks: TunerCallbacks, telemetry: PitchTelemetry, windowWidth: WindowWidth, modifier: Modifier = Modifier)
data class TunerCallbacks(onInstrument, onGuitarStrings, onPreset, onMode, onLoopTone, onAudition, onStopAudition, onRequestMicrophone)
```

## Composition

```
Column(testTag "screen.tuner" (the shell's `screen.<id>` convention; feature 002's tests keep working), padding pageMargin, gap md)
├── TunerSelectors(windowWidth)                                  compact/medium: two rows; expanded: one row
│   ├── TembraSegmentedControl(items Guitar|Cello, icons, style Large)   "tuner.instrument", items "tuner.instrument.<id>"
│   ├── AudioStatePill(state.pill)                                       "tuner.pill" (absent when null; contentDescription = pill.label in sentence case, drawn in capitals; MicActive primary + Mic, ToneOut secondary + VolumeUp, TestSignal tertiary + GraphicEq)
│   ├── TembraSegmentedControl(6-String|7-String, style Small)           "tuner.strings", only when instrument.hasStringCount
│   └── TembraSegmentedControl(Auto Listen|By Ear, icons, style Small)  "tuner.mode"
└── when (state.mode)
    AutoListen → TembraGradientCard(gradient meterCompact, testTag "tuner.autoListen")
        ├── if state.showDeniedState: MicrophoneDeniedState(onRequestMicrophone)           "tuner.denied", button "tuner.denied.action"
        └── else: TunerMeter(telemetry, reading = state.reading, maxWidth by windowWidth)  "tuner.meter" (one Canvas, research R8)
    ByEar → TembraGradientCard(gradient byEarCompact / byEarWide, testTag "tuner.byEar")
        ├── Row: TuningPresetPicker(state.preset, state.presets, onPreset)  "tuner.preset.picker", menu items "tuner.preset.item.<id>"
        │        LoopToneToggle(state.loopTone, onLoopTone)                 "tuner.loop"
        └── StringCardGrid(columns = if expanded 3 else 2)
            └── StringCard(string, playing = audition?.stringIndex == index, onAudition, onStop)   "tuner.card.<numeral>", button "tuner.card.<numeral>.audition"
```

**TunerMeter** draws everything from the snapshot each frame while listening (`PitchFrameLoop`),
and from `state.reading` (or the resting state) when not: status pill ("In tune" lit or unlit),
note + octave (or "Listening"), the gauge (data-model.md §8), needle with smoothed angle, "82.4 Hz"
readout and reference chip (hidden when resting), Flat/Sharp row with the highlighted side.
Semantics on the meter node: `contentDescription = "Tuner meter"`, `stateDescription` from
`state.reading` ("E2, 4 cents sharp" / "F#3, in tune" / "Listening"), `liveRegion = Polite`.

**Selectors and buttons**: hover state, focus ring (`focusRingWidth`), keyboard activation,
44 dp targets via `minimumInteractiveComponentSize()`; segmented items `role = Tab, selected`;
toggle `role = Switch, toggleable`; cards' buttons `role = Button` with `contentDescription`
"Audition E2" / "Stop E2"; card `stateDescription` "Playing" when sounding. Reduce motion: the
pill dot does not pulse and the Playing level animation is static (spec FR-027); the needle still moves.

**Copy** (`TunerCopy.kt`, created with the state types in Phase 3 and asserted by tests): "Mic active", "Tone out", "Test signal", "In tune", "Listening", "Reference" (the chip: "REFERENCE 82.4 HZ"), "Flat", "Sharp", "Tuning Preset", "Loop Tone", "Active", "Off", "Audition", "Playing", "Tuner meter", denied title
"Microphone needed", denied text "Tembra Audio listens to your instrument to show its tuning.
Allow the microphone to use Auto Listen.", denied button "Allow microphone".

## Tests

- `commonTest`: `ListeningPolicyTest` (every row of data-model.md §3), `TunerViewModelTest`
  (`FakeAudioEngine`, `FakeMicrophonePermission`, `FakeTunerPreferences`, fake `ResourceReader`,
  `runTest` with a test dispatcher): first load applies preferences; entering Auto Listen with
  `NotDetermined` requests once; `Granted` + visible + foreground → `startListening` exactly once;
  leaving the screen, backgrounding or switching to By Ear → `stopListening` and pill null;
  denied states never start listening and By Ear still auditions; `audition` → `startTone(hz, 0)`
  with loop on, `startTone(hz, 2000)` with loop off; second `audition` stops then starts; instrument
  change stops the tone and selects the default preset; preferences updated on every selector;
  `Interrupted` stops, `InterruptionEnded` resumes; watchdog `Idle` while wanted → restarted on the
  next poll; one-shot end clears `audition`; `readPitch` sampled into `reading` at 10 Hz and `null`
  when `hasPitch == false`; `onCleared` stops everything. `MeterGeometryTest`, `TunerUiStateTest`, `TunerTestSignalRequestTest` (catalogue id, `E2:-20`, `F#3:+5`, invalid → null); `TunerViewModelTest` also covers `useTestSignal` (records `startTestSignal`, never `request()`, pill `TestSignal`).
- Existing feature-002 tests: `NavigationShellTest` (the five cases that render `ShellUiState(Destination.Tuner)` and assert `screen.tuner`) and `AndroidBackTest` (clicks `nav.item.tuner`) switch to `Destination.Metronome` / `nav.item.metronome` / `screen.metronome`, because `TunerScreen` now obtains a ViewModel through `metroViewModel()` and those tests provide no `LocalMetroViewModelFactory` (analysis H1).
- `uiTest`: `TunerScreenTest` (compact and expanded: selector rows, string-count selector hidden for
  Cello, denied state and its button invoking the callback, cards for every preset with the right
  numerals, notes and Hz, Playing card state, preset dropdown selects, toggle toggles, hover, focus
  order, 44 dp on every control, large-font render with `assertDisplayedWithin` on every label);
  `TunerMeterTest` (scripted `PitchTelemetry`: in-tune pill lit at 2 cents, Flat highlighted at −20,
  Sharp at +20, needle clamped at +80, resting state, semantics text).
