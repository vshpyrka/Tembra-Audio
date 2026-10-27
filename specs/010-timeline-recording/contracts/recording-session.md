# Contract: `RecordingController`, `RecordingPolicy`, permission, routes, background, the dialog

**Where**: `:shared:ui`, package `audio.tembra.ui.session` (the controller), `:shared:core`
`audio.tembra.core.recording` (the pure policy); data model §2, §4, §6; research R12, R15, R16,
R17; spec FR-001 to FR-004, FR-008, FR-009, FR-011, FR-016 to FR-023. Skill contracts:
`kotlin-concurrency-and-flow` (the controller launches only on the session scope, every launch
site named, `StateFlow` for state, the session's `Channel` for one-shot messages, the 10 Hz
peak poll and the per-frame telemetry read documented as hot/no-replay), `kotlin-api-design`
(the policy as a sealed event/state table owned by `RecordingPolicy`; `AudioRouteMonitor` as an
interface with platform bindings, no new expect/actual), `kotlin-control-flow` (exhaustive
`when` over `RecordingEvent`, `RecordingEffect`, `TakeStopReason`, no `else`).

## Responsibilities

| Collaborator | Job |
|---|---|
| `RecordingController` (`@Inject`, owned by `ProjectSession`) | holds `RecordingState`, `MonitorState`, the live take; applies `RecordingPolicy` to events and runs the effects against `TimelineAudio`, `MicrophonePermission`, `AudioRouteMonitor`, `BackgroundPlaybackHost`, `TimelinePreferences` and the repository; exposes `state: StateFlow<RecordingUi>`, `inputTelemetry: InputTelemetry` |
| `RecordingPolicy` (`core`) | pure `onEvent(state, monitor, event): RecordingTransition` |
| `TakeAdopter` (`data`) | contracts/take-storage.md |
| `LiveOverview` (`data`) | the growing overview (R7) |
| `ProjectSession` (extended) | forwards `arm/disarm/record/stop/cancel/setMonitor/setCountIn/resetClip`; feeds the controller `TrackDeleted`, `ProjectClosing`, `HostHidden`, `TransportClosed`, `Interrupted`; applies the `RecordTake` edit the controller hands back; the stop-and-leave variant |

Launch sites (session scope, named): `observeRoutes` (collects `AudioRouteMonitor.route` →
`OutputChanged`/`InputChanged`), `observePermission` (status → `PermissionRevoked` when a granted
status becomes denied while armed), `observeCaptureEnd` (the 10 Hz mirror of
`CaptureSnapshot`: `recording` falling with a non-user `stop_reason` → `TakeEnded(reason)`;
`count_in_beats_left` → `PreRollTick`), `livePeaks(takeId)` (10 Hz `readPeaks` into
`LiveOverview`, replaces `RecordingUi.liveTake.overview`), `finaliseTake(takeId)` (`recordStop`
on IO → `TakeAdopter.adopt` → `RecordTake` edit → `TakeFinalised`), `armJob` (`arm` on IO →
`InputOpened`/`InputFailed`). No `init` launches; `start()` is called from the session's
`start()`.

## State machine (the policy table; every row is a test)

| From | Event | To | Effects |
|---|---|---|---|
| Disarmed | Arm(t), permission Granted | Arming(t) | OpenInput, KeepAlive(false) |
| Disarmed | Arm(t), NotDetermined / DeniedCanAsk | Disarmed | RequestPermission (the controller re-sends Arm on Granted) |
| Disarmed | Arm(t), DeniedOpenSettings | Disarmed | ShowDenied |
| Arming(t) | InputOpened | Armed(t) | SetMonitor(remembered && !speaker) |
| Arming(t) | InputFailed | Disarmed | Notify(InputLost) |
| Armed(t) | Arm(t) | Disarmed | CloseInput (a tap on the lit toggle disarms) |
| Armed(t) | Arm(u ≠ t) | Arming(u) | CloseInput, OpenInput |
| Armed(t) | Record, stopped, countIn ≠ OFF | PreRoll(t, take, beats) | StartTake(countIn), KeepAlive(recording) |
| Armed(t) | Record, stopped, countIn = OFF, or playing | Recording(t, take, start) after TakeStarted | StartTake(0), KeepAlive(recording) |
| PreRoll | Cancel / Stop / Record / Interrupted / HostHidden | Armed(t) | CancelTake, KeepAlive(false) |
| PreRoll | PreRollTick(n) | PreRoll(…, n) | — |
| PreRoll | TakeStarted(start) | Recording(t, take, start) | — |
| Recording | Stop / Record | Finalising | StopTake(User) |
| Recording | TakeEnded(reason) (engine-initiated) | Finalising | StopTake(reason), Notify(reason) |
| Recording | Interrupted / InputChanged / PermissionRevoked / TrackDeleted(t) / ProjectClosing / HostHidden / TransportClosed | Finalising | StopTake(reason), Notify(reason) (then, for the last five, Disarm after TakeFinalised) |
| Finalising | TakeFinalised | Armed(t) (or Disarmed when a disarming reason is pending) | KeepAlive(false) |
| PreRoll / Recording | Arm(any) / Disarm | same | — (FR-018: arming and disarming are unavailable while a take runs) |
| Finalising | Record / Stop / Cancel | same | — (ignored until TakeFinalised) |
| Armed / Recording | OutputChanged(speaker = true) | same, monitor SuspendedSpeaker | SetMonitor(false) |
| Armed / Recording | OutputChanged(speaker = false), remembered on | same, monitor On | SetMonitor(true) |
| Armed | Disarm / TrackDeleted(t) / ProjectClosing / HostHidden / TransportClosed / PermissionRevoked | Disarmed | CloseInput |
| any armed state | MonitorToggle | same | speaker → ShowMonitorNeedsHeadphones; else SetMonitor(!on) and remember |

`Record` while `Disarmed` never reaches the policy: `TimelineViewModel.record()` arms the
selected track (or adds one through the existing `AddTrack` edit, selects and arms it) and
records once `Armed` is observed (FR-008). `Record` while `Arming` is queued the same way.

## Availability while a count-in or take runs (FR-018)

`TimelineViewModel.mapState` derives from `takeRunning = phase is PreRoll || Recording ||
Finalising`: `canUndo = history && !takeRunning`, the same for redo, split, clip move and delete
(the lane ignores drags), "+ Track", import entries, Move up/down, Delete track, the tempo pill,
the signature and count-in menus, "Restore demo", arming. `ProjectsViewModel.open` and
`openInTimeline` route through `PlaybackGuard.confirmLeave()` as navigation does, so opening a
project while recording shows the stop-recording dialog. Mute, solo, gain, master, zoom, scroll,
the metronome toggle, monitoring and the other settings fields stay live.

## Permission (FR-019, FR-020)

`MicrophonePermission.request()` on the first arm; `DeniedCanAsk` asks again next time;
`DeniedOpenSettings` → `SessionMessage.MicrophoneDenied(openSettings = true)` → the Timeline
snackbar "Microphone access is needed to record" with the action "Open settings"
(`permission.openSystemSettings()`); `refresh()` on `ON_RESUME` through `AppLifecycleObserver`
(which gains `ON_START`/`ON_RESUME` forwarding to the controller). `Info.plist`
`NSMicrophoneUsageDescription` = "Tembra Audio listens to your instrument to show its tuning and
records it into your projects."; Android `RECORD_AUDIO` is already declared.

## Background (FR-021)

`BackgroundPlaybackHost.keepAlive(PlaybackNotification.recording(projectName))` while a take
runs (and `playing` otherwise, as today); Android: `FOREGROUND_SERVICE_MICROPHONE` in the
manifest, `foregroundServiceType="mediaPlayback|microphone"` on `PlaybackForegroundService`,
`startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
FOREGROUND_SERVICE_TYPE_MICROPHONE)` on API 34+, always called while Timeline is visible (arming
and Record are taps on it). iOS: `playAndRecord` active plus the `audio` background mode. The
`AppLifecycleObserver`'s `ON_STOP` flush is unchanged; a take never stops on backgrounding.

## The stop-and-leave dialog (FR-022)

`DialogRequest.StopAndLeave(recording: Boolean, answer)`; `ProjectSession.confirmLeave()` sets
`recording = controller.state.value.phase is Recording`; `StopAndLeaveDialog` takes the copy
variant ("Stop recording and leave" / "Keep recording"; tags `dialog.stopRecording.confirm` /
`.cancel` beside the existing ones); on confirm the session sends `HostHidden`
(→ `StopTake(Leaving)`, awaits `TakeFinalised`), then pauses and navigates. A pre-roll cancels
without a dialog (`ShellViewModel.leaveGuarded`: `guard.isPlaying` is false during a pre-roll;
the guard's `transportHostVisible(false)` sends `HostHidden`).

## Messages (`SessionMessage`)

`MicrophoneDenied(openSettings)`, `MonitorNeedsHeadphones`, `TakeEnded(reason)` (one line per
reason: "Take ended: the call took the audio", "… the microphone changed", "… 30 minutes
reached", "… the device is out of space", "… recording failed"), `RecordRefused` ("Not enough
space to record: 200 MB needed").

## Tests (first, `commonTest`)

- `RecordingPolicyTest`: every row above, including the queued `Record` during `Arming`, two
  arms in a row, every ending reason from `Recording` and from `PreRoll`, the speaker rules.
- `RecordingControllerTest` with `FakeAudioEngine`, `FakeMicrophonePermission`,
  `FakeAudioRouteMonitor`, `FakeBackgroundPlaybackHost`, `FakeTimelinePreferences`,
  `FakeProjectRepository`: arm requests the permission once; granted → `arm` on the engine →
  meter telemetry readable; denied → message; `record` from stopped writes the manifest before
  `recordStart` and uses `countInBeats = bars × beatsPerBar`; the pre-roll ticks reach the
  state; `recordStop` → adoption → one `RecordTake` edit in the history → `Armed`; a take under
  0.01 s leaves no edit and no file; the engine-initiated stop reasons; `HostHidden` during a
  take ends it and disarms; the route rules; `keepAlive` carries `recording`; monitoring is
  remembered on the device; an interruption ends the take and the playback policy pauses.
- `ProjectSessionTest` (extended): `confirmLeave` with `recording = true` and both answers;
  `TrackDeleted` and `ProjectClosing` forwarding; `setCountIn` is not an undo step and survives
  undo/redo; `RecordTake` pushes the arrangement with the new source.
- `TimelineViewModelTest` (extended): `record()` arms the selected track, adds a track in an
  empty project, the FR-018 availability map, the count-in setting intent, the Record button
  state per bar width (`Hidden`/`Idle`/`CountIn`/`Recording`), the monitor toggle placement.
