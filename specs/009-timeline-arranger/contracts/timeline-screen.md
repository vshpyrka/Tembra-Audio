# Contract: the Timeline screen, transport bar, track settings, metronome sheet

**Where**: `:shared:ui`, packages `audio.tembra.ui.timeline` (+ `components`),
`audio.tembra.ui.transport`, `audio.tembra.ui.tracksettings`, navigation additions. Research
R13–R15; spec User Stories 1–6 and 8; data model §2, §5, §7. Skill contracts:
`compose-state-and-effects` (wiring / content / components; effects keyed by the semantic input),
`compose-component-design` (modifier first optional, slots, primitive parameters),
`compose-performance` (frame-rate reads in draw only), `compose-ui-testing-patterns`.

## Files (one type per file; no composable nested in another's file)

```
ui/timeline/
  TimelineScreen.kt            wiring: metroViewModel(), ReportFirstContentDrawn, messages → snackbar, LaunchedEffect(viewModel) { screenVisible }, DisposableEffect(viewModel) { onDispose { flushSave } }
  TimelineScreenContent.kt     stateless: (state, callbacks, telemetry, layout, modifier); composes toolbar, ruler, lanes, overlay, transport bar per layout
  TimelineViewModel.kt         @Inject @ContributesIntoMap(AppScope) @ViewModelKey; maps ProjectSession.state + view state → TimelineUiState; intents
  TimelineUiState.kt, TimelineCallbacks.kt, TimelineLayout.kt, TimelineCopy.kt, TimelineSheet.kt, Selection.kt, TransportTelemetry.kt
  components/
    TimelineToolbar.kt         + Track, Add audio…, Add audio as new track, scissors, undo, redo, TempoPill, overflow per layout (D1, T151: compact overflow = Add audio…, Add audio as new track, "Snap: <value>" → SnapSheet, "Overview strip" (checked while shown; a tap shows or hides it), Metronome settings; full row adds GridPicker "Snap <value>" and the metronome button, its overflow only "Overview strip")
    GridPicker.kt              MenuPill over GridSize, labelled "Snap 1/16" (full toolbar)
    SnapSheet.kt               ModalBottomSheet "Snap to grid": the five GridSize rows, the current one checked (TimelineSheet.GridMenu; compact overflow)
    TimelineRuler.kt           Canvas: bar row + time row (RulerLabels) only, playhead chip; leftSlot (expanded: the empty 200 dp over the header column); nothing overlays the canvas; tap/drag → onSeek/onScrub; hover time
    TempoPill.kt, RulerPill.kt "124 BPM" pill in the toolbar at every width → TempoDialog (D1; the ruler carries no pills)
    OverviewStrip.kt           the whole project, clip blocks, playhead, a draggable window whose edges zoom; between the lanes and the transport bar, composed unless the device hid it; inset outside the tagged node by margin (default the 16 dp page margin) plus the part of gestureInsets (default WindowInsets.systemGestures) its ancestors have not consumed: the rail, the Duo's end column and the header column consume what they cover (D1, T151, T153)
    TrackList.kt               LazyColumn of TrackRow; vertical scroll; expanded inline settings row
    TrackRow.kt                header + lane in one row (header pinned in expanded layouts)
    TrackHeader.kt             expanded: colour dot, name, InstrumentChip, GainReadout, M, S, chevron, menu
    TrackHeaderCompact.kt      compact: colour dot, name, chip, M, S, menu (56 dp)
    TrackLane.kt               Canvas: clips via ClipPainter; gestures (tap, long-press drag, plain drag → scroll)
    ClipPainter.kt             DrawScope extension: rounded block, caption band, waveform columns from WaveformOverview, selection outline, unavailable style
    PlayheadOverlay.kt         Canvas over ruler + lanes; reads telemetry in draw (PerfTrace.section(TIMELINE_PLAYHEAD_FRAME))
    GainReadout.kt             "−2.4dB"; drag vertically / arrows / page keys; double tap → 0 dB
    InstrumentChip.kt, InstrumentLabelMenu.kt, TrackMenu.kt, ClipMenu.kt
    EmptyTimeline.kt           "Add track" / "Add audio" invitation
    TempoDialog.kt             TempoDial + TempoStepper ×2 + TempoSlider + TapTempoButton (metronome components)
    MetronomeSettingsSheet.kt  ModalBottomSheet: the tempo read-only ("124 BPM, from the project") beside the project's TimeSignatureMenu (D1), ClickSoundPicker, SubdivisionTiles, AccentCells, StyleMenu
  TimelineKeyHandling.kt       a Modifier.onPreviewKeyEvent owner on the screen root mapping the FR-057 keys to callbacks (US8)
ui/transport/
  TransportBar.kt              (state: TransportUiState, callbacks, telemetry, modifier): MasterVolumeSlider, skip, step back, PlayPauseButton (metronome's), step forward, TimeReadout, MetronomeToggle
  TransportUiState.kt, TransportCallbacks.kt, TransportButton.kt, MasterVolumeSlider.kt, MetronomeToggle.kt
  MasterVolumePopup.kt         below 600 dp of bar width: the 44 dp speaker button ("80 percent") and the pop-up above the bar with the 200 dp slider and its percentage (D1)
  TimeReadout.kt               a Canvas: reads TransportTelemetry in draw and paints "mm:ss.hh / mm:ss.hh" with drawText in tabularTimecode (never a Text recomposed per frame); semantics text from the 10 Hz UiState value, announced at 1 Hz
ui/tracksettings/
  TrackSettingsScreen.kt       nested entry (compact): title = track name; TransportBar at the bottom
  TrackSettingsForm.kt         shared by the screen and the inline row: name field, InstrumentLabelMenu, GainReadout slider, M/S toggles, Delete track, EffectsPlaceholder
  TrackSettingsInlineRow.kt    expanded: the form under the header, chevron collapses
  TrackSettingsDraft.kt        the unsaved field values handed between the nested screen and the inline row through ProjectSession.settingsDraft when the window crosses 840 dp
  EffectsPlaceholder.kt        heading "Effects" + one sentence (TimelineCopy.effectsLater)
  TrackSettingsKey.kt          NavKey(trackId)
ui/navigation/
  ShellUiState.kt (backStack: List<NavKey>), ShellViewModel.kt (suspend select/back through PlaybackGuard), TembraNavDisplay.kt (+ entry<TrackSettingsKey>), NavigationShellContent.kt (+ StopAndLeaveDialog host)
ui/designsystem/
  StopAndLeaveDialog.kt, TembraMenu.kt (dropdown with hover/focus), TembraTooltip.kt (promoted from the tuner's tooltip usage), TembraTextField.kt
```

## Layouts (`TimelineLayout` from window size class, height and posture)

| Layout | Toolbar | Headers | Settings | Transport |
|---|---|---|---|---|
| Compact (< 600 dp) | + Track, scissors, undo, redo, overflow | compact, 56 dp, inline in the lane row | nested screen | bottom, above the nav bar |
| Medium (600–839) | full when it fits, else compact | compact | nested screen | bottom |
| Landscape (≥ 600, < 480 tall) | compact | compact | nested screen | bottom, 56 dp |
| Expanded (≥ 840, ≥ 480 tall) | full | 200 dp pinned column with gain readout and chevron | inline row | bottom, spans content width |

At least three tracks visible above the transport on a phone in portrait (lane height 88 dp
compact, 96 dp expanded; the ruler 44 dp; toolbar 48 dp; transport 64 dp compact, 72 dp expanded).
Nothing scrolls horizontally except the timeline content.

## Gestures (clarifications 3 and 4)

| Where | Touch | Pointer |
|---|---|---|
| Lane, empty or on a clip | tap: select clip / clear; plain drag: scroll; long press on clip then drag: move (clip lifts); two-finger pinch: zoom around centroid | click: select; press-drag on clip: move; wheel: scroll; modifier + wheel / trackpad pinch: zoom; hover: clip hover state |
| Ruler | tap: seek (snap); drag marker: silent scrub (snapped), resume on release; pinch: zoom | same; hover shows time under pointer |
| Overview strip (D1, T151; unless hidden) | drag the window scrolls, drag an edge zooms, tap outside centres; the inset at either side is no target | same |
| Headers | tap: select track; M/S toggles; chevron (expanded); menu | hover states, tooltips on icon buttons |

Scroll and zoom run inside `PerfTrace.animation(TIMELINE_SCROLL / TIMELINE_ZOOM)`; `following`
is suspended by a manual scroll and restored by play, skip-to-start or a ruler tap (FR-012).

## Keyboard (FR-057)

Space play/pause; Home skip to start; ←/→ one grid step (bar when Off), Shift+←/→ one bar; S
split; Delete/Backspace delete clip; platform undo/redo; +/− zoom; M/O mute/solo the selected
track; Tab order toolbar → headers → clips → transport; focus ring from `drawFocusRing`.

## Semantics (FR-058)

Track row: `contentDescription` "Track 2, Guitar, −2.4 dB, muted"; clip: "drums, track 1, bar 1
beat 1, 31 seconds", `Role.Button`; playhead overlay: `stateDescription` position (10 Hz value);
readout: `liveRegion = Polite` off during playback (it updates per frame visually; the semantics
value updates at 1 Hz to keep screen readers usable); faders and gain readouts: `progressBarRangeInfo`
+ `setProgress`; every control `testTag` from `TimelineTestTags` (anchors from `PerfAnchors`).

D1 tags (2026-10-26, T141; the zoom verdict of 2026-10-27, T144 and T151): added `timeline.toolbar.tempo`,
`timeline.toolbar.overflow.button` and its rows `timeline.toolbar.overflow.<addAudio | addAudioNewTrack | snap |
overviewStrip | metronomeSettings>` (`overviewStrip` `selected` while the strip shows, its check `<row>.check`),
`timeline.toolbar.metronomeSettings`, `timeline.overview` and `timeline.overview.window` (present unless the device hid
the strip), `timeline.snapSheet` and `timeline.snapSheet.<bar | quarter | eighth | sixteenth | off>`, `timeline.metronomeSheet.signature`
(rows `<tag>.<signature id>`), `transport.master.button` and `transport.master.popup` (phones), `transport.master.percent`;
removed `timeline.ruler.tempo`, `timeline.ruler.signature`, `timeline.toolbar.zoom`, and with the zoom buttons (T144)
`timeline.zoom.out / .level / .in` and `timeline.toolbar.overflow.zoomControl.<buttons | overviewStrip>`. The journeys'
`timeline.ruler` and `timeline.lane.0` are unchanged.

## Copy (`TimelineCopy`)

"Add track", "Add audio…", "Add audio as new track", "Split", "Undo", "Redo", "Snap", "Snap 1/16" (pill), "Snap: 1/16"
(overflow), "Snap to grid", "Overview strip" (overflow), "Overview" and "bars 6 to 15 in view" (the strip's description and state),
"Track settings", "Move up", "Move down", "Delete track", "Delete clip", "Metronome settings",
"Effects", "Effects for this track arrive in a later version.", "Stop playback?", "You are leaving
the timeline while it is playing.", "Stop and leave", "Keep playing", "Could not start playback",
"<n> file(s) could not be added: <name> (<reason>)", "No tracks yet", "Add a track or audio to begin".

## Design conformance

Clip: corner radius `shapes.medium`, caption band `surfaceLevel2` at 70 % over the tint, waveform
columns in the tint at full alpha, selection outline `colors.primary` 2 dp, unavailable: `outline`
fill with the name only. Ruler: `tabularTimecode`; playhead `daw.playhead` 1.5 dp with the
rounded chip. Headers per the pulled phone screen for compact (square 44 dp M/S tinted by the
track) and the arranger tablet screen for expanded. Transport per the arranger tablet bar.

## Tests (first)

- `commonTest`: `TimelineViewModelTest` (every intent through a fake session: select, split
  availability, move constraints surfaced, zoom/scroll state, following rules, sheet open/close,
  message mapping), `TimelineLayoutTest`, `RulerLabelsTest` (sweep of zooms), `SnapTest`,
  `ClipHitTest`, `TimeFormatTest`, `TransportUiStateTest`, `TrackSettingsViewModelTest`,
  `ShellViewModelGuardTest`.
- `uiTest`: `TimelineScreenTest` per layout (frozen clock; scripted telemetry; semantics first,
  pixels second), `TrackLaneGestureTest` (plain drag scrolls, long-press drag moves, tap selects),
  `TimelineRulerTest`, `TransportBarTest` (hover/focus through injected interaction sources,
  keyboard where the lane allows), `TrackHeaderTest`, `GainReadoutTest`, `TempoDialogTest`,
  `MetronomeSettingsSheetTest`, `TrackSettingsScreenTest` (nested and inline), `StopAndLeaveDialogTest`,
  `NavigationShellTest` (Profile replaces Projects as the placeholder destination).
- Screenshots per spec FR-062 under `specs/009-timeline-arranger/screenshots/` with `NOTES.md`.
