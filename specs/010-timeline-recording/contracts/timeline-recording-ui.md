# Contract: Timeline recording UI — arm, meter, Record, count-in, monitoring, dialog, copy, tests

**Where**: `:shared:ui`, packages `audio.tembra.ui.timeline`, `.timeline.components`,
`.transport`, `.designsystem`, `.theme`; data model §6, §8; research R8, R16, R17, R18; spec
FR-005 to FR-010, FR-015 to FR-017, FR-022, FR-026 to FR-031. Skill contracts:
`compose-component-design` (every new composable: `modifier` first optional parameter applied
to the root; `ArmButton`, `RecordButton`, `InputMeter`, `MonitorToggle`, `CountInMenu` take
primitives and callbacks, no Boolean shape flags, no slots needed), `compose-state-and-effects`
(the wiring composables unchanged: `TimelineScreen` owns the ViewModel; the content composables
take `TimelineUiState` and `TimelineCallbacks`; the meter's and the growing clip's per-frame
reads are draw-phase, keyed by `InputTelemetry`), `compose-performance` (the meter, the pulse
and the live clip read `tick.longValue` only inside `drawBehind`/`Canvas`; `RecordingUi` is
immutable; the live overview is replaced, never mutated in place), `compose-focus-navigation`
("A", "R", "I", Escape in `TimelineKeyHandling`; the arm toggle after "S" and Record after
play/pause in the traversal order), `compose-ui-testing-patterns` (content composables with
controlled state and a `ScriptedInputTelemetry`; injected interaction sources for hover and
focus; frozen clock; semantics before pixels; keyboard tests declared per lane).

## Components (one file each)

| Composable | Where it appears | Behaviour |
|---|---|---|
| `ArmButton(armed, trackTint, onToggle, modifier)` | compact header beside "S"; expanded header row 2 beside "S" | 44 dp (36 dp in the expanded row like M/S) disc drawn with `drawBehind`: a hollow circle idle, filled in `record` when armed; `Role.Switch`; tag `TimelineTestTags.arm(i)`; tooltip "Arm for recording" |
| `InputMeter(telemetry, armed, modifier)` | compact header: a 4 dp bar along the bottom edge, full width; expanded header: an 8 dp bar beneath `GainReadout` | `graphicsLayer` + `drawBehind`: reads `tick.longValue` and `telemetry.read(tick, snapshot)`; `LevelSmoother` (30 ms) on the peak, a 1.5 s hold mark, −60…0 dB mapped linearly in dB; body colour to −6 dB, `meterWarning` above; a `meterClip` square at the right end while `snapshot.clipped`; `clickable` on the square resets (`onResetClip`); semantics: `contentDescription` "Input level", `stateDescription` "<n> dB" refreshed by the 10 Hz mirror, "clipped" announced once by a `liveRegion` when the latch lights; tag `meter(i)`, `clip(i)` |
| `RecordButton(state, onRecord, modifier)` | transport bar right of play/pause when the bar is ≥ 432 dp (`RecordInBarFrom`); the armed track's compact header in place of the label chip below that | 44 dp `TransportButton` variant: a filled circle in `record`; `Idle` outline, `CountIn` pulses on each beat (a `drawBehind` ring whose radius follows `countInBeatsLeft` changes; steady under reduce motion), `Recording` filled with a glow (none under reduce motion); `contentDescription` "Record" / "Recording" / "Count-in, N beats left"; tag `transport.record` in the bar, `TimelineTestTags.record(i)` in a header |
| `TimeReadout` (extended) | — | while `countInBeatsLeft ≥ 0` draws "Count-in N" in the position's place (both forms); the stacked form now applies below 600 dp (`ReadoutStacksBelow = 600.dp`) only when Record is in the bar; `transport.readout` semantics add "Count-in N" |
| `TransportBar` (extended) | — | `recordInBar = maxWidth >= 432.dp`; budget: six gaps + five 44 dp targets + the 48 dp disc + the speaker button; below 432 dp the revision-6 bar is unchanged (speaker button, four buttons, one-line readout, metronome) |
| `MonitorToggle(state, onToggle, modifier)` | toolbar after the scissors from 600 dp; a checked `MenuItemSpec` "Monitor input" in the compact overflow | `TembraIcon.Headphones`; `Role.Switch`; `On` filled, `SuspendedSpeaker` outlined with a slash and `stateDescription` "needs headphones"; a tap in that state shows the snackbar "Connect headphones to monitor the input"; tag `timeline.toolbar.monitor` / `timeline.toolbar.overflow.monitor` |
| `CountInMenu(value, enabled, onSelect, modifier)` | the metronome sheet, under the time signature | a `TembraMenu` row "Count-in: Off / 1 bar / 2 bars"; disabled (read-only) while a take runs; tag `metronome.sheet.countIn` |
| `TrackHeaderCompact` (extended) | — | row: dot, name, [chip | Record while armed on narrow bars], M, S, Arm, menu; `InputMeter` along the bottom edge while armed; height stays 56 dp |
| `TrackHeader` (extended) | — | row 2: `GainReadout`, M, S, Arm, chevron; `InputMeter` beneath row 2 while armed; column width stays 200 dp |
| `TrackLane` / `ClipPainter` (extended) | — | draws `LiveTakeUi` as a clip whose length is `snapshot.framesCaptured` read per frame (draw phase) and whose waveform is `liveTake.overview`; drawn above the lane's clips; caption the take name; semantics "Recording, Take N, <length>" from the 10 Hz mirror; tag `timeline.clip.take` |
| `StopAndLeaveDialog` (extended) | — | `recording` variant copy and tags |
| `TimelineToolbar` (extended) | — | the monitor toggle placement; FR-018 disables |
| `TrackMenu` (extended) | — | Move up/down/Delete disabled while a take runs |

## Layout rules (FR-026)

- Bars under 432 dp wide: the 009 bar; Record in the armed compact header; the label chip hidden
  while armed (shown again on disarm); the name keeps at least 72 dp at 360 dp.
- Bars 432–599 dp: Record in the bar, the readout stacked (position above length).
- Bars 600 dp and wider: Record in the bar, the readout on one line, the master slider inline.
- Expanded headers (≥ 840 dp): Arm in row 2 at 36 dp like M and S; the meter beneath.
- Phone landscape (≥ 600 wide, < 480 tall): the bar holds Record; the ruler and two tracks stay
  visible.
- Largest font size: the compact header's name ellipsises before anything clips; the stacked
  readout shrinks to fit as `TimeReadout` already does.

## Keyboard (FR-028)

`TimelineKeyHandling`: "A" → `onArm(selectedTrack)`, "R" → `onRecord()`, "I" →
`onMonitorToggle()`, Escape during a pre-roll → `onRecord()` (cancel), space during a take →
pause (ends the take, the existing play toggle); the keys FR-018 disables are no-ops while a
take runs. Traversal: Arm after "S" in each header; Record after play/pause in the bar; the
monitor toggle after the scissors.

## Semantics and anchors (FR-029)

`TimelineTestTags`: `arm(i)`, `meter(i)`, `clip(i)`, `record(i)`, `takeClip`, `monitor`,
`overflowMonitor`, `countIn`, `record` (bar), `denied.action`, `stopRecording.confirm/cancel`.
`PerfAnchors` adds `TIMELINE_TRACK_ARM_FIRST = "timeline.track.arm.0"`,
`TIMELINE_TRACK_METER_FIRST = "timeline.track.meter.0"`, `TIMELINE_TRACK_RECORD_FIRST =
"timeline.track.record.0"`, `TIMELINE_TRACK_CLIP_FIRST = "timeline.track.clip.0"`, `TRANSPORT_RECORD = "transport.record"`, `TIMELINE_MONITOR =
"timeline.toolbar.monitor"`, `TIMELINE_OVERFLOW_MONITOR = "timeline.toolbar.overflow.monitor"`,
`METRONOME_COUNT_IN = "metronome.sheet.countIn"`, `TIMELINE_CLIP_TAKE = "timeline.clip.take"`,
`SNACKBAR_MICROPHONE_SETTINGS = "snackbar.microphone.settings"`,
`DIALOG_STOP_RECORDING_CONFIRM = "dialog.stopRecording.confirm"` (47 anchors). Every new
window root (the sheet already has it) carries `anchorRoot()`.

## Copy (`TimelineCopy`, `TransportCopy`)

"Arm for recording", "Record", "Recording", "Count-in", "Count-in %d" (readout), "Monitor
input", "Connect headphones to monitor the input", "Microphone access is needed to record",
"Open settings", "Stop recording and leave", "Keep recording", "Stop recording?", "The take is
kept up to now.", "Take ended: …" ×5, "Not enough space to record (200 MB needed)", "Clipped,
tap to reset", "Input level", "Off" / "1 bar" / "2 bars".

## Design conformance

Tokens only: `record`, `meterBody`, `meterWarning`, `meterClip` from `theme.json`
(`ThemeJsonParityTest`); the headphones glyph fetched by the script; the record and arm circles
drawn; the design table's verdicts honoured (the headphones toggle after the scissors, Record
as a filled circle, the meter's two colours from the mixer screens). Screenshots per FR-031 at
360, 432 (the bar boundary), 600, 840 and 1024 dp plus phone landscape on both platforms.

## Tests (first)

- `uiTest`: `ArmButtonTest` (states, semantics, hover/focus through an injected interaction
  source), `InputMeterTest` (scripted telemetry: silent, −20 dB, full scale → clip latch; reset
  on tap; the hold mark after 1.5 s of frozen-clock advance; both placements; reduce motion
  still moves), `RecordButtonTest` (the four states, descriptions, the pulse under and without
  reduce motion), `TransportBarTest` (extended: 360, 432, 599 and 600 dp widths → Record
  hidden/shown, readout one-line/stacked, every target ≥ 44 dp, nothing clipped),
  `TimeReadoutTest` (extended: "Count-in 4"), `MonitorToggleTest` (three states, placement by
  width), `CountInMenuTest`, `TrackHeaderCompactTest` (extended: armed row at 360 dp with the
  chip hidden and Record shown, name ≥ 72 dp, the meter present, largest font),
  `TrackHeaderTest` (extended), `TrackLaneTest` (extended: the live take drawn and described),
  `StopAndLeaveDialogTest` (extended: the recording variant), `TimelineScreenTest` (extended:
  arm → meter, record → live clip, FR-018 availability, the denied snackbar with its action),
  `TimelineKeyHandlingTest` (extended: A, R, I, Escape; declared per lane).
- `commonTest`: `TimelineUiStateTest` (the availability map, `RecordButtonState` by width),
  `TimelineTokensTest` (the four tokens), `PerfAnchorsCatalogueTest` (47).
- Device: `AndroidMicrophonePermissionTest` (extended for the recording purpose),
  `NativeAudioEngineRecordingTest` (arm, meter, a 2-bar take, adoption), the route tests in
  the quickstart.
