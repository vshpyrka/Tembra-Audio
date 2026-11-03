# Contract: the session, the document edit and the ViewModel — pan, metering, undo, keys

**Where**: `:shared:core` (`core.project`: `Pan`, `SetPan`, `FaderTaper`, `Track`,
`ProjectEditor`, `ProjectValidator`), `:shared:data` (codec golden, fixtures), `:shared:ui`
(`ui.session`: `ProjectSession`, `MixTelemetry`; `ui.timeline`: `TimelineViewModel`,
`TimelineViewState`, `TimelineUiState`, `MixerUi`, `StripUi`, `MasterUi`, `MixerHeight`,
`StripForm`, `MeterSource`, `TimelineCallbacks`, `TimelineKeyHandling`; `ui.tracksettings`).
Research R7, R8, R12; data model §1, §4, §9; spec FR-005 to FR-008, FR-010, FR-011, FR-014,
FR-016, FR-017, FR-022, FR-023, FR-028, FR-029, SC-009, SC-010. Skill contracts:
`kotlin-api-design` (`Pan` and `FaderTaper` are closed core types that own their arithmetic;
`SetPan` is one edit; `TimelineAudio` gains member functions; no new expect/actual),
`kotlin-control-flow` (the `when` over `ProjectEdit` in `applyToEngine` and `coalesces` gains
its `SetPan` branches with no `else`; `MixerHeight`, `StripForm`, `MeterSource` are exhaustive),
`kotlin-concurrency-and-flow` (no new launch site below the ViewModel; the setters are
synchronous because they are bounded (R4); `setMetering` is synchronous; the only new
`LaunchedEffect` is the sheet's 10 Hz mirror keyed by `open`; no `init` launch, no
`runBlocking`), `compose-state-and-effects` (state in the ViewModel, `MixerUi` immutable,
callbacks built once).

## Document and core

- `Track.pan` appended after `clips`, default `Pan.Centre`; `ProjectEditor.setPan(id, pan)`
  returns a new project or `UnknownTrack`; `ProjectValidator` rejects `|percent| > 100`.
- `Pan.readout`: 0 → "C"; negative → "L <abs>"; positive → "R <n>". `snapToCentre(within)`
  returns `Centre` when `|percent| ≤ within`. `toLinear()` = `percent / 100f`.
- `FaderTaper`: the knots of R3; `dbOf(0f) = GainDb.Silence`; `throwOf(GainDb.Silence) = 0f`;
  `throwOf(dbOf(t)) == t` within 1e-4 for every t in (0.02, 1]; `ticks` = the labelled marks
  +6, +3, 0, −3, −6, −10, −20, −30, −40, −60, ∞ with their throw positions.
- The demo golden and `fixtures/demo-project.json` gain `"pan": 0` on every track; a
  revision-1 document without the key decodes with every track centred
  (`ProjectDocumentCodecTest.oldDocumentReadsCentred`).

## Session

- `coalesces(edit)`: `is SetPan -> last is SetPan && last.id == edit.id`; `closeEditGroup()`
  is called by the ViewModel at every fader, knob and master gesture end so each gesture is
  one undo step (FR-005; today's header readout keeps its behaviour).
- `applyToEngine`: `is SetPan -> audio.setPan(index, pan.toLinear())`; `SetGain` and
  `SetSoloed`/`SetMuted` keep `pushTrack`; `SetMasterVolume` keeps `setMaster`. After
  `setArrangement` (project open, undo/redo that changes the track list) the session pushes
  every track's pan once (`pushPans`), because pan is not in the arrangement struct.
- `setMetering(on)`: a counter; `audio.setMetering(true)` on 0 → 1, `false` on 1 → 0; reset
  to 0 when the transport closes or the project changes.
- `mixTelemetry`: `read(frameNanos, into)` memoised on the frame time, filling one
  `MixSnapshot` scratch through `audio.readMix`; exposed to the screen beside
  `transportTelemetry`.
- `resetTrackClip(id)` → `audio.resetMixClip(index)`; `resetMasterClip()` → index 64. On
  `play()` from a stop the session resets every latch (FR-012's "until playback next starts
  from a stop").

## ViewModel

- `toggleMixer()`: flips `mixer.open`; on open sets `height = Half` (`Full` in landscape) and
  calls `session.setMetering(true)`; on close `setMetering(false)`; emits nothing else.
- `setMixerHeight(h)`, `setPan(id, pan)` (`edit(SetPan)`), `gestureSettled()`
  (`session.closeEditGroup()`), `resetTrackClip`, `resetMasterClip`, `selectStrip(id)`
  (`selectTrack(id)` + a viewport request that scrolls the track's lane into view, the
  existing selection path), `openMixerWindow()` (R10: `windowCapabilities.openMixerWindow()`
  and `mixer.open = false`).
- `mapState` derives `MixerUi`: `strips` from `tracks` (number = index + 1; `meterSource =
  Input` for the armed track), `master` from `masterVolume`, `form` from `layout` and
  `height` (data model §5), `anchors` from the lanes box height the screen reports through
  `barWidth`'s sibling `lanesHeight`, `windowAvailable` from `WindowCapabilities`.
- Keys (`handleTimelineKey`): `X` → `toggleMixer()`; "M"/"O" act on the focused strip's
  track when the sheet has focus (the strip's `onKeyEvent` maps them to its own callbacks
  before the root sees them), otherwise on the selected track as today. After 011 merges the
  entries move to `ShortcutCatalogue` under `ShortcutScope.Mixer` ("X" open/close, arrows,
  Page, Home, M, O) and the sheet registers with `RegisterOverlay`.
- Track settings: `TrackSettingsViewModel.setPan(pan)` → `session.edit(SetPan)`;
  `panSettled()` → `closeEditGroup()`; `TrackSettingsUiState.pan`.

## Tests (first)

- `commonTest` (core): `PanTest` (readout, clamp, snap, linear), `FaderTaperTest` (knots,
  inverse, monotonic, silence), `ProjectEditorSetPanTest`, `ProjectValidatorTest` (+ range).
- `commonTest` (data): `ProjectDocumentCodecTest` (golden with pan; old document centred;
  round trip).
- `commonTest` (ui): `ProjectSessionTest` (`aRunOfPanChangesIsOneUndoStepUntilTheGroupCloses`,
  `aPanEditUsesThePanFastPathOnly`, `pansArePushedAfterAnArrangement`,
  `meteringIsOnWhileAnyHolderIsOpen`, `playFromStopClearsTheLatches`,
  `mixTelemetryReadsOncePerFrame`), `TimelineViewModelTest` (`toggleMixerOpensAtHalfAndMeters`,
  `landscapeOpensFull`, `xTogglesTheMixer`, `stripsFollowTheTracks`,
  `theArmedStripShowsTheInputMeter`, `gestureEndClosesTheGroup`,
  `meteringCallsAreSynchronous` (no dispatch on the test dispatcher)),
  `TimelineUiStateTest` (forms and anchors by layout and height),
  `TrackSettingsViewModelTest` (+ pan).
