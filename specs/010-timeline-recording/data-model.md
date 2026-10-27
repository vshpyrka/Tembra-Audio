# Data Model: Microphone Recording on the Timeline (feature 010)

Phase 1 of the plan. One type per file (constitution VII); packages are feature 009's. Research
items are cited as R-numbers from [research.md](research.md). Everything here extends feature
009's data model (`specs/009-timeline-arranger/data-model.md`); unchanged types are not repeated.

## 1. Document (`:shared:core`, `audio.tembra.core.project`)

| Type | Fields / shape | Rules |
|---|---|---|
| `Project` (extended) | `countIn: CountIn = CountIn.ONE_BAR` appended after `tracks` | wire order; serialised with a lenient serializer; a project saved by feature 009 reads as `ONE_BAR` (R13) |
| `CountIn` (enum) | `OFF`, `ONE_BAR`, `TWO_BARS`; `bars: Int` (0, 1, 2) | `CountInSerializer` reads an unknown value as `ONE_BAR` (the pattern of `InstrumentLabelSerializer`) |
| `Track` (extended) | `nextTakeName(): String` | "Take N", N = one more than the highest "Take N" clip name on the track, starting at 1 (FR-012); the regex pattern of `Track.nextName` |
| `RecordTake : ProjectEdit` | `trackId: TrackId`, `asset: AudioAsset`, `assetId: AudioAssetId`, `clip: ClipTemplate` (start in beats, offset 0, length = asset frames, name = `nextTakeName()` at record start) | applied by `ProjectEditor.recordTake`: merge the asset; trim every clip on the track overlapping `[start, start + length)` by the split arithmetic (a straddling clip keeps its outside part(s), an inside clip is removed); insert the take clip; the result has no overlap on the track (FR-013). Rejections: `UnknownTrack`, `TakeTooShort` (length < 0.01 s at the asset rate), `ClipLimit` (1024). One history step |
| `ProjectEditor.recordTake` | pure | reuses `splitClip`'s frame arithmetic (`ProjectEditor.kt:156-164`) and `withoutUnusedAssets` |
| `SetCountIn : ProjectEdit` | `countIn` | not pushed to history (like `metronomeOn`): the session applies it with `coalesces` = settings path; `restore()` carries it over on undo/redo |

Derived (`ProjectDerived`): `clipsOverlapping(trackId, startBeats, lengthFrames, rate)` for the
editor and the renderer's muting rule.

## 2. Recording policy (`:shared:core`, `audio.tembra.core.recording`)

Pure state machine tested as a table (R15), the sibling of `PlaybackPolicy`.

| Type | Shape |
|---|---|
| `RecordingState` (sealed) | `Disarmed`, `Arming(trackId)`, `Armed(trackId)`, `PreRoll(trackId, takeId, beatsLeft: Int)`, `Recording(trackId, takeId, startBeats, since: Long)`, `Finalising(trackId, takeId)` |
| `MonitorState` (enum) | `Off`, `On`, `SuspendedSpeaker` (the toggle remembered on but the output is the built-in speaker) |
| `RecordingEvent` (sealed) | `Arm(trackId)`, `Disarm`, `InputOpened(channels, rate)`, `InputFailed`, `Record`, `Stop`, `Cancel`, `PreRollTick(beatsLeft)`, `TakeStarted(startBeats)`, `TakeEnded(reason)`, `TakeFinalised`, `PermissionDenied`, `PermissionRevoked`, `InputChanged`, `OutputChanged(isSpeaker)`, `Interrupted`, `TrackDeleted(trackId)`, `ProjectClosing`, `HostHidden`, `TransportClosed` |
| `RecordingTransition` | `state`, `effects: List<RecordingEffect>` |
| `RecordingEffect` (sealed) | `OpenInput`, `CloseInput`, `StartTake(countIn)`, `StopTake(reason)`, `CancelTake`, `SetMonitor(enabled, trackIndex)`, `RequestPermission`, `ShowDenied`, `Notify(TakeStopReason)`, `KeepAlive(recording = true/false)` |
| `TakeStopReason` (enum) | `User`, `MaxLength`, `DiskFull`, `InputLost`, `Interrupted`, `PermissionRevoked`, `TransportClosed`, `WriteFailed`, `Leaving` |

Rules encoded by the table: one armed track at a time (`Arm(other)` from `Armed` = `CloseInput`
+ `OpenInput`, never two inputs); `Record` from `Disarmed` is refused by the policy (the
ViewModel arms first, FR-008); `Stop`, `Cancel` and every ending event from `Recording`/`PreRoll`
lead to `Finalising` → `Armed` (the track stays armed, FR-011); `HostHidden`, `ProjectClosing`,
`TrackDeleted(armed)`, `TransportClosed` and `PermissionRevoked` end the take (kept) and disarm;
`OutputChanged(isSpeaker = true)` sets `MonitorState.SuspendedSpeaker` and emits
`SetMonitor(false)`; `OutputChanged(false)` with the remembered toggle on emits
`SetMonitor(true)`; `InputChanged` during a take ends it with `InputLost` and keeps the arm;
`Interrupted` ends the take with `Interrupted` (the playback policy pauses).

## 3. Engine seam (`:shared:engine`, `audio.tembra.engine`)

| Type | Fields | Notes |
|---|---|---|
| `TimelineAudio` (extended) | see contracts/engine-c-abi-v7.md "Kotlin seam" | arm, disarm, monitor, record start/cancel/stop, capture read, peaks read, clip reset, free space |
| `ArmResult` (sealed) | `Armed(channels: Int, sampleRate: Int)`, `InputUnavailable`, `NotTransport` | |
| `RecordConfig` | `wavPath`, `trackIndex`, `countInBeats`, `maxFrames`, `minFreeBytes` | `maxFrames` = 30 min at the stream rate; `minFreeBytes` = 50 MB |
| `RecordStartResult` (sealed) | `Started`, `NotArmed`, `TakeRunning`, `FileFailed` | |
| `TakeInfo` | `startFrames`, `startBeats`, `frames`, `channels`, `sampleRate`, `alignmentFrames`, `stopReason: TakeStopReason` | `frames == 0` means discarded (shorter than 0.01 s; the engine deleted the file) |
| `CaptureSnapshot` (mutable, one per reader) | `armed`, `recording`, `preRoll`, `monitoring`, `inputChannels`, `sampleRate`, `peak: FloatArray(2)`, `clipped`, `framesCaptured`, `takeStartFrames`, `alignmentFrames`, `inputLatencyNanos`, `outputLatencyNanos`, `stopReason`, `inputOverruns`, `backlogFrames` | filled by `readCapture` from the seqlock; read in draw lambdas and by the 10 Hz mirror (R8) |
| `PeakBuckets` (mutable buffer) | `min: ByteArray`, `max: ByteArray`, `count`, `firstIndex`, `nextIndex` | 256-frame base buckets per channel (R7) |
| `TransportSnapshot` (extended) | `countInBeatsLeft: Int` (−1 none), `recording: Boolean` | drawn by the readout and the Record button pulse |
| `AudioHealth` (extended) | `inputOverruns`, `recordBacklog`, `inputXruns` | three new counters |
| `AudioEvent` (extended) | `InputRouteChanged`, `OutputRouteChanged(isSpeaker: Boolean)` | emitted by the actuals from the platform route callbacks (R12); the `AudioRouteMonitor` below is the query side |
| `FakeAudioEngine` (extended) | `armResult`, `nextCapture`, `scriptedPeaks: (fromIndex) -> PeakBuckets`, `recordStopResult`, `takeWriter: (path) -> Unit`, `calls` entries `arm/disarm/setMonitor/recordStart/recordCancel/recordStop/resetClip` | writes a tiny valid WAV through `takeWriter` so the adoption path is testable on the JVM |

## 4. Platform seams (`:shared:ui/platform`, bindings in `iosMain` and `:androidApp`)

| Type | Shape |
|---|---|
| `AudioRouteMonitor` (interface) | `val route: StateFlow<AudioRoute>`; Android binding on `AudioManager.getDevices(GET_DEVICES_OUTPUTS/INPUTS)` + `AudioDeviceCallback`; iOS binding on `AVAudioSession.currentRoute` + `AVAudioSessionRouteChangeNotification`; fake in tests |
| `AudioRoute` | `outputIsSpeaker: Boolean`, `outputName: String`, `inputName: String`, `inputId: String` |
| `MicrophonePermission` (existing) | injected into the controller unchanged |
| `BackgroundPlaybackHost` (existing) | `keepAlive(PlaybackNotification)` gains `PlaybackNotification.recording(name)`; Android: the service type `mediaPlayback|microphone` |

## 5. Storage (`:shared:data`, `audio.tembra.data.projects`)

| Type | Shape | Rules |
|---|---|---|
| `ProjectFolder` (extended) | `takeStagingPath(token) = audio/.take-<token>.wav`, `takeManifestPath(token) = audio/.take-<token>.json` | dot-files: invisible to size and retire (R14) |
| `TakeManifest` (`@Serializable`) | `token`, `trackId`, `startBeats`, `takeName`, `startedAt`, `alignmentFrames`, `sampleRate`, `channels` | written before capture, deleted after adoption |
| `TakeAdopter` | `adopt(folder, manifest, takeInfo): AdoptedTake` | hash (64 KiB chunks) → rename to `audio/<sha256>.wav` → inspect → `.peaks` build inside `timeline.waveform` → `AudioAsset` + `ClipTemplate`; an existing copy with the same hash is reused |
| `AdoptedTake` | `assetId`, `asset`, `clip: ClipTemplate`, `audioPath`, `overviewPath` | |
| `ProjectRepository` (extended) | `pendingTakes(id): List<TakeManifest>` (manifests with a readable WAV beside them), `discardTake(id, token)` | crash recovery on open (FR-023) |
| `LiveOverview` | base buckets (int8 min/max per channel) appended from `PeakBuckets`; levels 1024/4096/16384 derived incrementally; `asOverview(): WaveformOverview` | the growing clip's drawing source (R7) |
| `TakeFreeSpace` | `freeBytes(path)` through the engine's `freeBytes` | the 200 MB refusal before `recordStart` (FR-025) |

## 6. Session and UI state (`:shared:ui`)

| Type | Fields |
|---|---|
| `SessionState` (extended) | `recording: RecordingUi` |
| `RecordingUi` | `armedTrack: TrackId?`, `phase: RecordingState`, `monitor: MonitorState`, `inputChannels: Int`, `liveTake: LiveTakeUi?`, `permission: MicrophonePermissionStatus` |
| `LiveTakeUi` | `trackId`, `clipName`, `startBeats`, `overview: WaveformOverview` (from `LiveOverview`, replaced at 10 Hz) ; the length is read per frame from `CaptureSnapshot.framesCaptured` |
| `SessionMessage` (extended) | `MicrophoneDenied(openSettings: Boolean)`, `MonitorNeedsHeadphones`, `TakeEnded(reason)`, `RecordRefused(reason: LowSpace)` |
| `DialogRequest.StopAndLeave` (extended) | `recording: Boolean` → the copy variant (FR-022) |
| `TrackUi` (extended) | `armed: Boolean`, `recordHere: Boolean` (compact bars only, R17) |
| `TransportUiState` (extended) | `record: RecordButtonState` (`Hidden` below 432 dp of bar width, `Idle`, `CountIn`, `Recording`), `countInBeatsLeft` (−1 none) |
| `TimelineUiState` (extended) | `monitor: MonitorState`, `monitorInToolbar: Boolean`, `countIn: CountIn`, `takeRunning: Boolean` (drives FR-018 availability: `canUndo`, `canRedo`, `canSplit`, `canAddTrack`, `canImport`, `canEditTempo`, `canEditSignature`, `canArm`) |
| `TimelineCallbacks` (extended) | `onArm(trackId)`, `onRecord()`, `onMonitorToggle()`, `onCountIn(countIn)`, `onResetClip(trackId)`, `onRequestMicrophone()` |
| `InputTelemetry` (fun interface) | `read(frameTime, into: CaptureSnapshot)` once per distinct frame time, the pattern of `TransportTelemetry` |
| `TimelinePreferences` (extended) | `monitorInput: Flow<Boolean>` (key `timeline.monitorInput`, default false) |

## 7. C++ structures (`engine/src/capture`, `engine/src/transport`)

| Type | Role |
|---|---|
| `CaptureSource` (RT) | owns the input scratch, the per-block peak and the 64 ms recent-peak ring, the clip latch, the `SpscFifo<float>` producer side, and the capture state atomics (`Idle`, `Armed`, `PreRoll`, `Recording`, `Stopping`); `onInput(block, frames)` is called by the renderer with the block of the same callback |
| `TakeRecorder` (thread) | the FIFO consumer: int24 conversion, `WavWriter`, `PeakRing` publishing, 1 s header refresh and flush, frame cap, `statvfs`, stop reasons, drain on stop |
| `WavWriter` | 44-byte RIFF/PCM header, 24-bit LE, `refreshSizes()`, `finish()`; used by `TakeRecorder` and by `wav_fixtures` in tests |
| `PeakRing` | 1024 `PeakBucket{int8 min[2], max[2]}` + atomic `count`; writer: store bucket then release-increment; reader: acquire count, copy, re-check the count to detect an overrun of the ring |
| `CaptureRecord` + `Seqlock<CaptureRecord>` | the snapshot behind `tembra_capture_read` |
| `MonitorStrip` | a `TrackStrip` instance following the armed track's gain/audible targets; summed into `MasterBus` after the clips (R12) |
| `TransportRenderer` (extended) | pre-roll (clock before `start`, clips skipped, click forced), capture start split at `start`, muting of the armed track's segments at or after `takeStart` while recording, monitor mix, `count_in_beats_left` in the published record |
| `AudioDevice` (extended) | `openInputBeside(rate, maxChannels, InputCallback)`, `closeInput()`, `inputLatencyNanos()`, `inputChannels()`; Oboe: the second stream read non-blocking in the output callback (R2); RemoteIO: `openDuplex` with both buses (R3) |
| `AudioHealth` (extended) | `inputOverruns`, `recordBacklog`, `inputXruns` |

## 8. Tokens and icons (`design/stitch/theme.json`, `DawColors`, `TembraIcon`)

`record` (the design's red record circle), `meterBody`, `meterWarning` (above −6 dB), `meterClip`
(the latch); glyphs `headphones` (fetched), `record` and `arm` (filled circles drawn, not
fetched); copy in `TimelineCopy`/`TransportCopy`: "Arm for recording", "Record", "Recording",
"Count-in", "Monitor input", "Connect headphones to monitor the input", "Microphone access is
needed to record", "Open settings", "Stop recording and leave", "Keep recording", "Take ended:
…" (reasons), "Not enough space to record", "Clipped, tap to reset".

## 9. Performance names (`perf-catalogue.txt`; one dot per section name)

Sections: `timeline.arm`, `transport.recordStart`, `transport.countIn` (animation),
`timeline.takeFrame`, `transport.recordStop`, `timeline.monitorToggle`, `engine.takeAlignment`.
Counters: `audio.inputOverruns`, `audio.recordBacklog`, `audio.takeAlignmentMs`,
`audio.monitorDelayMs`. Anchors: `timeline.track.arm.<i>`, `timeline.track.meter.<i>`,
`timeline.track.clip.<i>`, `timeline.track.record.<i>` (compact), `transport.record`,
`timeline.toolbar.monitor`, `timeline.toolbar.overflow.monitor`, `metronome.sheet.countIn`,
`timeline.clip.take`, `snackbar.microphone.settings`, `dialog.stopRecording.confirm`,
`dialog.stopRecording.cancel`. Journey: `recording` (new); `stress` extended.
