# Contract: Metronome screen (`:shared:ui`)

**Package**: `audio.tembra.ui.metronome` (+ `.components`) | **Entry**: `DestinationContent(Destination.Metronome)` → `MetronomeScreen()` | **Depends on**: contracts/metronome-audio-kotlin.md, metronome-domain.md, metronome-preferences.md, click-sounds.md, background-playback.md, design-system-additions.md, data-model.md §7–§10 | **Skills**: `compose-state-and-effects` (seams, effects), `compose-performance` (draw-phase reads), `kotlin-concurrency-and-flow` (owners, primitives), `compose-component-design` (components), `compose-ui-testing-patterns` (tests)

```kotlin
// MetronomeViewModel.kt — @Inject @ContributesIntoMap(AppScope::class) @ViewModelKey; entry-scoped (rememberViewModelStoreNavEntryDecorator)
class MetronomeViewModel(
    private val audio: MetronomeAudio, private val host: BackgroundPlaybackHost, private val preferences: MetronomePreferences,
    private val store: CustomClickSoundStore, private val picker: SoundFilePicker,
    private val prepareDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<MetronomeUiState>                      // MutableStateFlow; synchronous .value; initial = defaults with loaded = false
    val messages: Flow<MetronomeMessage>                          // Channel(BUFFERED).receiveAsFlow(): exactly-once, one consumer (the snackbar effect)
    val telemetry: BeatTelemetry                                  // draw-phase handle: litBeat(frameNanos) reads audio.readTick at most once per frame
    fun setTempo(tempo: Tempo); fun stepTempo(delta: Int); fun selectSignature(s: TimeSignature); fun selectSubdivision(s: Subdivision)
    fun cycleBeat(index: Int); fun selectStyle(style: MetronomeStyle); fun selectSound(sound: ClickSound)
    fun importCustomSound(); fun replaceCustomSound(); fun tap(atNanos: Long); fun togglePlayback()
    fun screenVisible(visible: Boolean)                           // true: start the named jobs (below); false: stop the click, cancel the jobs
    fun useDebugSettings(request: MetronomeDebugRequest?)         // debug builds only (research R14)
    override fun onCleared()                                      // stopMetronome(), host.release()
}
```

**Owners and launch sites** (`structured-concurrency`, `flow-state-events`): `init` launches
nothing. `screenVisible(true)` launches, on `viewModelScope`, `observePreferences()` (first
emission sets `loaded = true`, applies the settings, sends the grid and the sound, and loads the
custom sound from its path if one is named, reporting `CustomSoundMissing` once on failure),
`observeAudioEvents()` (`audio.events` → `PlaybackPolicy`), `observeStopRequests()`
(`host.stopRequests`), and `pollAudioState()` (10 Hz: `currentBeat` from `readTick` for
semantics; `DeviceLost` when `audioState` drops to `Idle` while `Running`). Each intent that
touches the engine or preferences is one `viewModelScope.launch` with a named function; the lower
layers (`preferences.update`, `store.commit`, `picker.pickWav`) are `suspend`. Broad catches
re-throw `CancellationException`.

`togglePlayback()`: `Stopped` → `Starting` → `host.keepAlive()` → `audio.startMetronome()` →
`Running` (or `Stopped` + `FocusDenied` / `DeviceUnavailable`); `Running` → `audio.stopMetronome()` →
`host.release()` → `Stopped`. Settings intents while `Running` send `audio.setGrid` /
`audio.selectSound` after updating the state; while `Stopped`, `selectSound` also calls
`audio.previewClick()` (not for the already selected sound). `tap(atNanos)` folds into
`TapTempoSequence`, applies `tempo` when present, sets the caption and restarts the two-second
caption timer (a `viewModelScope` job replaced per tap). `selectSignature` resizes the pattern
(re-applies the selected style, else `resizedTo`), `cycleBeat` clears the style when the pattern
matches none, `selectStyle` rewrites the pattern.

```kotlin
// MetronomeScreen.kt — wiring seam
@Composable fun MetronomeScreen(modifier: Modifier = Modifier, viewModel: MetronomeViewModel = metroViewModel())
//   val debug = LocalMetronomeDebug.current
//   DisposableEffect(viewModel) { viewModel.useDebugSettings(debug); viewModel.screenVisible(true); onDispose { viewModel.screenVisible(false) } }
//   val state by viewModel.uiState.collectAsStateWithLifecycle()
//   val snackbar = remember { SnackbarHostState() }; LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(MetronomeCopy.of(it)) } }
//   layout = MetronomeLayout.from(currentWindowAdaptiveInfo().windowSizeClass)
//   MetronomeScreenContent(state, remember(viewModel) { MetronomeCallbacks(...) }, viewModel.telemetry, layout, snackbar, modifier)
//   No LifecycleStartEffect stops the click: it survives ON_STOP by design (spec FR-027).

// MetronomeScreenContent.kt — stateless; what UI tests drive
@Composable fun MetronomeScreenContent(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, layout: MetronomeLayout, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier)
data class MetronomeCallbacks(onTempo, onStep, onSignature, onSubdivision, onCycleBeat, onStyle, onSound, onImportCustom, onReplaceCustom, onTap: (Long) -> Unit, onTogglePlayback) { companion object { val None } }
fun interface BeatTelemetry { fun litBeat(frameNanos: Long): Int }   // -1 = none; ViewModel implementation memoises per frameNanos
```

## Composition

```
Stacked (width under 600 dp, or under 840 dp at 480 dp or taller):
Column(testTag "screen.metronome", padding pageMargin)
├── Column(verticalScroll, weight 1, gap md)
│   ├── MetronomeSettingsPanel(state, callbacks, layout)                       one card per section, design order
│   │   ├── SectionCard "TIME SIGNATURE" + caption "Common & Complex": TimeSignatureChips   "metronome.signature", items "metronome.signature.<id>"
│   │   ├── SectionCard "RHYTHM SUBDIVISION": SubdivisionChips (labels per beat unit)       "metronome.subdivision", items "metronome.subdivision.<id>"
│   │   ├── SectionCard: AccentCells(state.settings.pattern, telemetry, running)             "metronome.cells", cells "metronome.cell.<n>"
│   │   │                StyleRow(state.settings.style, onStyle)                              "metronome.style", pills "metronome.style.<id>"
│   │   └── ClickSoundPicker(layout = Dropdown, sound, customSound, onSound, onImport, onReplace)   "metronome.sound", menu items "metronome.sound.<id>", "metronome.sound.replace"
│   └── TembraGradientCard(glow = tempoDialCard, testTag "metronome.dialCard")
│       ├── TempoDial(tempo, marking, canStepDown, canStepUp, onTempo, onStep)               "metronome.dial", steppers "metronome.dial.down" / "metronome.dial.up"
│       └── TempoSlider(tempo, onTempo)                                                       "metronome.slider"
└── Row(pinned, gap md): TapTempoButton(caption, onTap) "metronome.tap"; PlayPauseButton(running, onTogglePlayback) "metronome.play"

Wide (840 dp or wider and 480 dp or taller):
Row(testTag "screen.metronome", padding pageMargin, gap md)
├── Column(width max(1/3, wideSettingsMinWidth), verticalScroll): MetronomeSettingsPanel with ClickSoundPicker(layout = Tiles) in a "CLICK TIMBRE" card
└── Column(weight 1, gap md)
    ├── TembraGradientCard(tempoDialCard, discs = TopEndBottomStart, weight 1): TempoDial (max 420 dp, centred) + TempoSlider
    └── Card: Row(max 448 dp, centred): TapTempoButton (weight 1) + PlayPauseButton

Landscape (600 dp or wider, shorter than 480 dp):
Row(testTag "screen.metronome", padding pageMargin × sm, gap md)
├── settings column (36 % of the width within 224–280 dp, verticalScroll): TimeSignatureChips in two rows of three (`columns = 3`), SubdivisionChips, AccentCells + StyleRow, ClickSoundPicker(Dropdown)
└── pane (weight 1): TempoDial sized from the pane with TempoSlider under it, and the tap/play controls placed by whichever arrangement yields the larger dial (B13 ruling, 2026-09-30): the tap/play row under the slider when the pane is tall enough (the Stitch phone-landscape mock; dial = height − slider row − tap/play row − gaps), otherwise tap over play in a column beside the dial card (a phone held sideways has about 290 dp of pane height, where the stacked order pushed the transport off screen and shrank the readout); the dial stays within 160–420 dp and the pane scrolls only when neither arrangement keeps it at 160 dp
```

**Semantics** (spec FR-030): chips and pills `role = Tab` + `selected`; cells `role = Button`,
`contentDescription = "Beat 3"`, `stateDescription = level.label` (+ ", playing" when lit at the
10 Hz sample); the dial is one node with Material's slider semantics (Compose has no `Role.Slider`; B13 ruling): `progressBarRangeInfo(40..240)`,
`stateDescription = "120 beats per minute, Moderato"`, `setProgress` action; steppers `role =
Button` with `contentDescription` "Decrease tempo" / "Increase tempo" (also their tooltips),
`disabled` at the ends; slider Material semantics; tap button `contentDescription = "Tap tempo"`,
`stateDescription = caption`, announces the new tempo through `liveRegion = Polite`; play button
`contentDescription` "Start metronome" / "Stop metronome"; picker pill `contentDescription =
"Click sound, Classic Wood"`, entries with `selected`; the Custom entry "Custom, empty" /
"Custom, <name>", the Replace action a button. Every control `testTag` as above.

**Input** (spec FR-029): hover through `hoverable` + `indication`, focus ring `focusRingWidth`,
`Modifier.minimumInteractiveComponentSize()` on chips, pills, cells and picker entries; the
dial handles `onKeyEvent` (`DirectionUp/Right` +1, `DirectionDown/Left` −1, `PageUp` +10,
`PageDown` −10) and `Modifier.scrollable(Orientation.Vertical)` for the wheel; the slider gets the
same keys through Material's defaults plus a `PageUp/Down` handler; the tap button is a
`Button` with `onClick` (space/enter tap).

**Reduce motion** (spec FR-032): the tap flash, the accent glow pulse and the play glow are static
when `TembraTheme.motion.reduceMotion`; the lit cell still lights.

**Copy** (`MetronomeCopy.kt`): section labels "Time signature", "Common & Complex", "Rhythm
subdivision", "Style", "Click timbre" (drawn uppercase, sentence case to accessibility as feature
003's pill), "BPM", tap title "Tap tempo", captions "Sync finger rhythm" / "Tap again" / "%d
taps", play "Start metronome" / "Stop metronome", picker "Custom", "Replace", and the messages of
contracts/click-sounds.md.

## Tests

- `commonTest`: `MetronomeViewModelTest` (research R15's list; `StandardTestDispatcher`,
  `Dispatchers.setMain`, `FakeAudioEngine`, `FakeBackgroundPlaybackHost`,
  `FakeMetronomePreferences`, `FakeCustomClickSoundStore`, `FakeSoundFilePicker`),
  `PlaybackPolicyTest`, `MetronomeUiStateTest` (derived labels, marking, grid, step limits),
  `MetronomeLayoutTest`, `TempoDialGeometryTest`, `MetronomeDebugRequestTest`.
- Existing feature 002/003 tests that render `Destination.Metronome` as a placeholder
  (`NavigationShellTest`, `AndroidBackTest` after feature 003 switched them to Metronome) move to
  `Destination.Projects` / `nav.item.projects` / `screen.projects`, the remaining placeholder
  destination, because `MetronomeScreen` now needs a `LocalMetroViewModelFactory`.
- `uiTest` (`compose-ui-testing-patterns`: plain content composable with controlled state; hover,
  press and focus through injected `MutableInteractionSource`s; `mainClock.autoAdvance = false`
  before pixel assertions; semantics before pixels; keyboard tests declared per lane with
  `supportsKeyboardInputMode`): `MetronomeScreenTest` (three layouts; every callback fires;
  7/8 cells wrap; x/8 labels; Custom empty and filled; snackbar; large font
  `assertDisplayedWithin`), `TempoDialTest` (drag on the ring → tempo; wheel; keys; steppers
  disabled at 40 and 240; hold repeats after 400 ms with the frozen clock), `AccentCellsTest`
  (`ScriptedBeatTelemetry` lights cell 2; nothing lit when stopped; glow static under reduce
  motion), `ClickSoundPickerTest` (dropdown and tiles; selection; Replace), `TembraPillTest`,
  `TembraChipRowTest`.
