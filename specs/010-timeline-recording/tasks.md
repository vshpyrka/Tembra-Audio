---

description: "Task list for feature 010, Microphone Recording on the Timeline"
---

# Tasks: Microphone Recording on the Timeline

**Input**: Design documents from `/specs/010-timeline-recording/`

**Prerequisites**: plan.md, spec.md (clarified 2026-10-27, FR-001–FR-034), research.md (R1–R21), data-model.md (§1–§9), contracts/ (engine-c-abi-v7.md, recording-session.md, take-storage.md, timeline-recording-ui.md, perf-instrumentation.md), quickstart.md. Constitution v1.3.4 (recording is feature 010).

**Tests**: Required. Constitution III (test-first, non-negotiable) and spec FR-032: every implementation task is preceded by the test that fails without it. Lanes, always with `--warning-mode all` from `app/` and always through the `gradle-run` skill wrapper (Constitution VIII): `./gradlew engineHostTest` (Catch2, host), `./gradlew checkAndroidHost` (JVM), `./gradlew checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` (simulator; also the iPad and the Duo), `app/tools/gradlew-device.sh <serial> checkAndroidDevice` (one Android target at a time; the device lock serialises), `./gradlew :androidApp:connectedDebugAndroidTest`, `cd app/perf && uv run pytest`. Kotlin tests never link the engine: `FakeAudioEngine` (extended with the capture half), `FakeProjectRepository`, `FakeMicrophonePermission`, `FakeAudioRouteMonitor` (new), `FakeBackgroundPlaybackHost`, `FakeTimelinePreferences`. **Every device run applies quickstart §2 first**: brightness at the floor, the music stream and notifications silent, the black `Theme.Tembra.DeviceTest` window; the loopback alignment check (T090) is the one sounding step, run with a cable where possible and announced before it starts.

**Skills** (Constitution VIII): before writing or reviewing any Kotlin or Compose task below, route through `using-chrisbanes-skills` and load the owner named by the task's contract: `kotlin-concurrency-and-flow` and `kotlin-api-design` for the controller, the policy, the seam and the storage types (contracts/recording-session.md, engine-c-abi-v7.md "Kotlin seam", take-storage.md), `kotlin-control-flow` for the `when`s over `RecordingEvent`, `RecordingEffect`, `TakeStopReason` and the new `ProjectEdit` case, `compose-state-and-effects` for the screen wiring, `compose-component-design` for every component (contracts/timeline-recording-ui.md), `compose-performance` for the draw-phase meter, pulse and live clip, `compose-focus-navigation` for the keyboard map, `compose-ui-testing-patterns` for every `uiTest` class, `gradle-run` for every Gradle command.

**Delegation** (the owner's brief): lane runs per target, screenshot captures and per-device perf rounds are tasks for Sonnet-class sub-agents, one target each, given the serial, the exact commands, quickstart §2 and the instruction to report failing test names verbatim; engine, renderer, session, policy code and every review stay with the main agent; component work may be split across agents in detached worktrees on disjoint files with one integrator, as feature 009's D1 did.

**Organization**: Setup adds the catalogue names, anchors, tokens, the glyph, the platform declarations and two small tools. Foundational builds what every story needs: the engine's capture modules behind C ABI revision 7, the Kotlin seam, the document field and the `RecordTake` edit, the recording policy, the staging/adoption/live-overview storage, the route monitor and the `RecordingController` inside the session. Each user story is then an independently testable increment: US1 (arm and record a take) is the MVP; US2 the meter; US3 the count-in; US4 monitoring; US5 permission, interruptions and the screen off; US6 adaptive, keyboard and screen-reader proof. Polish carries the journeys, the pipeline, the profile, the alignment measurement, docs, the release build for the owner's checks, the Stitch sync and the labelled sessions.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US6 from spec.md
- Every task names its files; paths are repository-relative

## Path Conventions

Gradle root `app/`. `CORE` = `app/shared/core/src/commonMain/kotlin/audio/tembra/core`, `CORE_TEST` = `app/shared/core/src/commonTest/kotlin/audio/tembra/core`; `ENG` = `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine`, `ENG_TEST` = `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine`; `DATA` = `app/shared/data/src/commonMain/kotlin/audio/tembra/data`, `DATA_TEST` = `app/shared/data/src/commonTest/kotlin/audio/tembra/data`; `UI` = `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui`, `UI_TEST` = `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui`, `UI_UITEST` = `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui`; `ENGINE` = `app/engine`; `CAPTURE` = `app/engine/src/capture`; `PERF` = `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf`; `IOSPERF` = `app/iosApp/TembraPerfTests`; `PIPE` = `app/perf/tracepulse`; `ANDROID` = `app/androidApp/src/main/kotlin/audio/tembra/android`.

---

## Phase 1: Setup (catalogue, anchors, tokens, glyph, declarations, tools)

**Purpose**: the fixed name catalogues, the design tokens, the platform declarations and the two tools every later task depends on (research R16–R19; contracts/perf-instrumentation.md, timeline-recording-ui.md).

- [X] T001 In `app/shared/engine/perf-catalogue.txt` add `TIMELINE_ARM timeline timeline.arm`, `TRANSPORT_RECORD_START transport transport.recordStart`, `TRANSPORT_COUNT_IN transport transport.countIn`, `TIMELINE_TAKE_FRAME timeline timeline.takeFrame`, `TRANSPORT_RECORD_STOP transport transport.recordStop`, `TIMELINE_MONITOR_TOGGLE timeline timeline.monitorToggle`, `ENGINE_TAKE_ALIGNMENT engine engine.takeAlignment`, `AUDIO_INPUT_OVERRUNS counter audio.inputOverruns`, `AUDIO_RECORD_BACKLOG counter audio.recordBacklog`, `AUDIO_TAKE_ALIGNMENT_MS counter audio.takeAlignmentMs`, `AUDIO_MONITOR_DELAY_MS counter audio.monitorDelayMs` (one dot per section name: `collect_ios._SECTION_RE`); add the entries to `ENG/perf/PerfSection.kt` and `ENG/perf/PerfCounter.kt`; set `trace_shim_test` to 36 in `ENGINE/tests/trace_shim_test.cpp`; run `./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true` and commit `ENGINE/include/tembra_trace_names.h`
- [X] T002 [P] In `UI/perf/PerfAnchors.kt` add `TIMELINE_TRACK_ARM_FIRST = "timeline.track.arm.0"`, `TIMELINE_TRACK_METER_FIRST = "timeline.track.meter.0"`, `TIMELINE_TRACK_RECORD_FIRST = "timeline.track.record.0"`, `TIMELINE_TRACK_CLIP_FIRST = "timeline.track.clip.0"`, `TRANSPORT_RECORD = "transport.record"`, `TIMELINE_MONITOR = "timeline.toolbar.monitor"`, `TIMELINE_OVERFLOW_MONITOR = "timeline.toolbar.overflow.monitor"`, `METRONOME_COUNT_IN = "metronome.sheet.countIn"`, `TIMELINE_CLIP_TAKE = "timeline.clip.take"`, `SNACKBAR_MICROPHONE_SETTINGS = "snackbar.microphone.settings"`, `DIALOG_STOP_RECORDING_CONFIRM = "dialog.stopRecording.confirm"` and to `all`; set `UI_TEST/perf/PerfAnchorsCatalogueTest.kt` to 47; mirror them in `PERF/journeys/Anchors.kt` and `IOSPERF/Anchors.swift`
- [X] T003 [P] In `design/stitch/theme.json` add `dawSemanticColors.record` (the record circle's red sampled from `design/stitch/screens/mixer-timeline/tablet-split-v2/screenshot.jpg`, rounded to the palette), `meterBody`, `meterWarning` (the dual-colour LED meter of `phone-drawer`), `meterClip` with provenance comments; extend `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/ThemeJsonParityTest.kt` and `UI_TEST/theme/TimelineTokensTest.kt` for the four names (red), then add them to `UI/theme/DawColors.kt` (green); add `meterHeightCompact` 4 dp and `meterHeightExpanded` 8 dp to `UI/theme/TembraSpacing.kt`
- [X] T004 [P] Run `app/tools/fetch-material-symbols.sh headphones`; add `TembraIcon.Headphones` in `UI/theme/TembraIcon.kt`; extend `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/IconAssetsTest.kt` with the file
- [X] T005 [P] In `ENGINE/CMakeLists.txt` add a `CONFIGURE_DEPENDS` glob for `src/capture/*.cpp`; in `app/shared/engine/build.gradle.kts` add the folder to the engine task inputs; set `ENGINE/tests/engine_info_test.cpp` to expect ABI 7 and version "0.6.0" (red until T032)
- [X] T006 [P] Create `app/tools/gradlew-device.sh` (`serial="$1"; shift; cd "$(dirname "$0")/.."; ANDROID_SERIAL="$serial" exec ./gradlew "$@"`, executable) and `app/tools/apply-device-run-conditions.sh <serial>` (the five `adb shell` commands of quickstart §2, tolerating the OPPO's refusal); add both to `app/README.md` § Build and run
- [X] T007 [P] In `app/iosApp/iosApp/Info.plist` set `NSMicrophoneUsageDescription` to "Tembra Audio listens to your instrument to show its tuning and records it into your projects." (FR-019); in `app/androidApp/src/main/AndroidManifest.xml` add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>` and set the service's `android:foregroundServiceType="mediaPlayback|microphone"`; note both in `specs/003-instrument-tuner/spec.md` FR-014 as "wording superseded by feature 010 FR-019 (2026-10-27)"
- [X] T008 Run `./gradlew engineHostTest :shared:core:compileKotlinIosSimulatorArm64 checkAndroidHost --warning-mode all` and confirm every existing test stays green except the intended reds (T003, T005), with zero warnings

---

## Phase 2: Foundational (engine capture, ABI 7, seam, document edit, policy, storage, route monitor, controller)

**Purpose**: everything more than one story needs, each guarded by a test written first. No stream opens on a host machine: the capture path runs against `FakeAudioDevice`; the device and simulator lanes cover the real engine.

**⚠️ CRITICAL**: no user story work starts until T044, T045 and T046 are green.

### Tests (write first, watch them fail)

- [X] T009 [P] Create `ENGINE/tests/wav_writer_test.cpp`: the 44-byte header fields for 1 and 2 channels at 48 000 Hz, 24-bit; float → int24 little-endian conversion with clamping at ±1.0; after `refreshSizes()` the file is readable by `AudioDecoder` with the frames so far; `finish()` leaves the final sizes; a write failure (a closed `FILE*`) is reported, not thrown
- [X] T010 [P] Create `ENGINE/tests/peak_ring_test.cpp`: 1024 `PeakBucket` capacity; a writer and a reader on two threads over one million buckets lose nothing while the reader keeps up; a stalled reader observes the overrun through `nextIndex` and the oldest kept bucket; bucket values are the int8 min/max of 256 frames per channel, matching `OverviewBuilder`'s base level
- [X] T011 [P] Create `ENGINE/tests/capture_source_test.cpp`: per-channel block peak; the 64 ms recent-peak ring holds a transient for at least 64 ms of blocks; the clip latch sets at |x| ≥ 0.9999 and clears only on `resetClip()`; FIFO pushes begin after `alignment_frames` are discarded; a full FIFO counts one overrun per short write and loses the tail, never reorders; a short device read counts as an overrun after the two-burst warm-up and not before; the published `CaptureRecord` reflects every field of data-model §7
- [X] T012 [P] Create `ENGINE/tests/take_recorder_test.cpp` with a frame-numbered signal pushed through `SpscFifo`: the drained WAV equals the pushed samples (no gap, no repeat, int24 precision); the header is readable mid-take after the 1 s refresh (a hook reads the file between refreshes); the frame cap ends the take with `MAX_LENGTH`; a fake free-space probe below `min_free_bytes` ends it with `DISK_FULL`; a write failure ends it with `WRITE_FAILED`; `record_backlog` records the largest queue; the peak ring receives one bucket per 256 frames; drain on stop completes within 1 s with 4 s queued
- [X] T013 [P] Create `ENGINE/tests/monitor_strip_test.cpp`: mono input duplicated through gain into the bus; stereo passes; `audible = 0` silences; gain changes ramp over 10 ms; the 5 ms fade on stop has no discontinuity > 0.01
- [X] T014 [P] Extend `ENGINE/tests/transport_renderer_test.cpp` with the duplex `Harness` (the fake device feeds an input block per rendered block): a pre-roll of one bar in 4/4 and two bars in 7/8 renders the right click count with the accent first, no clip output and `count_in_beats_left` from N down to 1; capture begins exactly at `start` (the first kept frame is the input frame `alignment_frames` after `start`) at 96, 256 and 1024 frames per block; Record while playing begins at the next block; the armed track's clips are silent at and after `take_start_frames` and audible before; the monitor mix follows the track's gain, mute and solo and the master; cancel during the pre-roll restores the position; the stop fade; the metronome follows the toggle after the pre-roll; `recording` and `count_in_beats_left` published every block
- [X] T015 [P] Extend `ENGINE/tests/engine_core_state_test.cpp` and `ENGINE/tests/fake_audio_device.h` (`feedInputWhileRendering`, `inputLatencyNanos`, `reopenInput`, `reopenOutput` keeps the input): `tembra_transport_arm` returns `ERR_STATE` outside `TRANSPORT` and `ERR_DEVICE` when the fake refuses the input; arm is idempotent; `disarm` closes the input and is idempotent; `transport_close` while recording ends the take with `TRANSPORT_CLOSED` and the file is finalised before `IDLE`; an input reopen ends a take with `INPUT_LOST` and keeps the arm; an output reopen clears monitoring and the snapshot says so; `record_start` without arm → `ERR_STATE`, with a take running → `ERR_STATE`, with an unwritable path → `ERR_IO`; `record_stop` of a take under 0.01 s returns `frames = 0` and deletes the file; `tembra_storage_free_bytes` returns a positive number for the temp dir and −1 for a bad path; the tuner is still refused while `TRANSPORT`; `alignment_frames` equals the fake device's input plus output latency at the stream rate (7 ms + 13 ms at 48 kHz → 960 frames)
- [X] T016 [P] Extend `ENGINE/tests/audio_health_test.cpp` for `input_overruns`, `record_backlog`, `input_xruns`; extend `ENGINE/tests/trace_poison_test.cpp` so `capture_source.cpp`, `monitor_strip.cpp` and `peak_ring.h` are expected in the poison build (the negative try_compile still fails); the ABI 7 expectation of T005
- [X] T017 [P] Create `CORE_TEST/project/CountInTest.kt` (`OFF`, `ONE_BAR`, `TWO_BARS`; `bars` 0/1/2), `CountInSerializerTest.kt` (an unknown value reads as `ONE_BAR`; a feature 009 document without the field reads as `ONE_BAR`), `TrackNextTakeNameTest.kt` ("Take 1" on an empty track, one more than the highest "Take N", "Take 1" when only other names exist), `ProjectEditorRecordTakeTest.kt`: the trim matrix of FR-013 (no clip, a clip straddling the start keeps its part before, one straddling the end keeps its part after, one inside is removed, one straddling both keeps two outside clips, several at once), the asset merged once, the clip named and placed, `TakeTooShort` below 0.01 s, `UnknownTrack`, `ClipLimit` at 1024, no overlap on the track afterwards, one history snapshot through `EditHistory.push`; a `ProjectDocumentCodec` round trip of a project with a `RecordTake` applied (the take's asset by content id, no device path, identical on both lanes; SC-007); update `specs/009-timeline-arranger/fixtures/demo-project.json` and `app/shared/data/src/commonTest/kotlin/audio/tembra/data/projects/DemoProjectGolden.kt` for the appended `"countIn": "ONE_BAR"`
- [X] T018 [P] Create `CORE_TEST/recording/RecordingPolicyTest.kt`: every row of contracts/recording-session.md "State machine", including the queued `Record` during `Arming`, `Arm` of a second track, every ending event from `Recording` and from `PreRoll`, the five disarming reasons after `TakeFinalised`, the speaker rules for `OutputChanged`, `MonitorToggle` on the speaker, `Arm` and `Disarm` during `PreRoll` and `Recording` leaving the state unchanged, `Record` during `Finalising` ignored, and that no row has an `else`
- [X] T019 [P] Extend `ENG_TEST/FakeAudioEngineTest.kt` for the capture half (`arm` result, `recordStart` writes a readable WAV through `takeWriter`, `recordStop` result, `readCapture` copies `nextCapture`, `readPeaks` serves `scriptedPeaks`, the `calls` log); extend `ENG_TEST/perf/PerfSectionCatalogueTest.kt` for the eleven new names and `ENG_TEST/perf/AudioHealthPublisherTest.kt` for the four counters
- [X] T020 [P] Create `DATA_TEST/projects/TakeManifestTest.kt` (round trip, unknown field ignored), `TakeAdopterTest.kt` (hash and rename, a duplicate hash reuses the copy, inspect disagreement leaves the staging files, overview built inside `TIMELINE_WAVEFORM`, `Discarded` on zero frames, the clip template's name and start from the manifest), `LiveOverviewTest.kt` (levels equal `WaveformOverviewReader`'s for the same buckets, a reported gap repeats the previous bucket, `levelFor` at every zoom, a 30-minute take stays under 2 MB); extend `DATA_TEST/projects/OkioProjectRepositoryTest.kt` (`pendingTakes` lists only manifests with a readable WAV, `discardTake`, dot-files still invisible to size and retire) and `DATA_TEST/timeline/DataStoreTimelinePreferencesTest.kt` (`timeline.monitorInput`, default false)
- [X] T021 [P] Create `UI_TEST/platform/FakeAudioRouteMonitor.kt` and `UI_TEST/session/RecordingControllerTest.kt` (contracts/recording-session.md "Tests": arm requests the permission once; granted → `arm` on the engine → `inputTelemetry` readable; denied → `MicrophoneDenied`; `record` from stopped writes the manifest before `recordStart` with `countInBeats = bars × beatsPerBar`; pre-roll ticks reach the state; `recordStop` → adoption → one `RecordTake` edit → `Armed`; a take under 0.01 s leaves no edit; the engine-initiated reasons; `HostHidden` during a take ends it and disarms; the route rules; `keepAlive` carries `recording`; monitoring remembered on the device; `Interrupted` ends the take); extend `UI_TEST/session/ProjectSessionTest.kt` (`confirmLeave` with `recording = true`, both answers; `TrackDeleted`/`ProjectClosing` forwarding; `setCountIn` is not an undo step and survives undo/redo; `RecordTake` pushes the arrangement with the new source; a pending take is adopted on open with no undo step); create `UI_TEST/timeline/TimelineUiStateTest.kt` (the FR-018 availability map; `RecordButtonState` `Hidden` below 432 dp, `Idle`/`CountIn`/`Recording`; `monitorInToolbar` from 600 dp)
- [X] T022 [P] Create `app/shared/engine/src/androidDeviceTest/kotlin/audio/tembra/engine/NativeAudioEngineRecordingTest.kt` and `app/shared/engine/src/iosTest/kotlin/audio/tembra/engine/NativeAudioEngineRecordingTest.kt` (open the transport, arm, the capture snapshot reports channels and a rate, a 2-bar take at 120 BPM with count-in 1 bar yields a WAV of 4 s ± one block whose header `inspect` agrees, `alignmentFrames > 0`, the peak ring delivers buckets, disarm closes the input), `app/androidApp/src/androidTest/kotlin/audio/tembra/android/AndroidAudioRouteMonitorTest.kt` (the speaker is reported when nothing is connected), extend `AndroidMicrophonePermissionTest.kt` (the recording purpose reuses the same permission)

### Implementation

- [X] T023 [P] Create `CAPTURE/wav_writer.h` and `.cpp` (24-bit PCM LE, the 44-byte header, `refreshSizes`, `finish`, error reporting)
- [X] T024 [P] Create `CAPTURE/peak_ring.h` (1024 buckets, atomic count, the overrun-detecting reader) and `CAPTURE/capture_record.h` (the seqlock record of data-model §7)
- [X] T025 Create `CAPTURE/capture_source.h` and `.cpp` (RT: the input scratch, peak, the 64 ms ring, the clip latch, the alignment discard, the `SpscFifo` producer, the command word; no allocation after `prepare`) — after T024
- [X] T026 Create `CAPTURE/take_recorder.h` and `.cpp` ("tembra-recorder" thread modelled on `DiskStreamer`: condition variable, 10 ms idle wait, user-initiated QoS / nice 0; FIFO consumer, int24 conversion, `WavWriter`, the 1 s header refresh and flush, the `PeakRing`, the frame cap, `statvfs` once a second, stop reasons, drain on stop) — after T023, T024
- [X] T027 [P] Create `ENGINE/src/mixer/monitor_strip.h` and `.cpp` (a `TrackStrip` instance following the armed track's targets, the 5 ms stop fade)
- [X] T028 Extend `ENGINE/src/platform/audio_device.h` (`openInputBeside(rate, maxChannels, InputCallback, ctx)`, `closeInput()`, `inputLatencyNanos()`, `inputChannels()`, `inputXruns()`) and `ENGINE/src/platform/android/oboe_audio_device.h`/`.cpp` (the second input stream: Float, `Unspecified` channels clamped to `maxChannels`, `LowLatency`, `Exclusive` → `Shared`, `Unprocessed` → `VoiceRecognition`, capacity 2 × the output's; the output callback's non-blocking `read(numFrames, 0)` with zero padding and the two-burst warm-up; `calculateLatencyMillis` on both streams in `measureLatencyLocked`; the input's `onErrorAfterClose` reopens the input and raises `inputReopened_`; an output reopen keeps the input and raises `outputReopened_`; both xrun counts)
- [X] T029 [P] Extend `ENGINE/src/platform/apple/remote_io_audio_device.h`/`.cpp` (`openDuplex`: uninitialise, `EnableIO` on bus 1 and bus 0, input non-interleaved float at the hardware rate with `maxChannels`, `AudioUnitRender` on bus 1 inside `renderProc` for the same frame count into the pre-allocated input buffer, initialise and start; `closeInput` restores output-only the same way; `inputLatencyNanos` from the host-reported value)
- [X] T030 Extend `ENGINE/src/transport/transport_clock.h` (a pre-roll start before `start`, positions may be negative while pre-rolling) and `ENGINE/src/transport/transport_renderer.h`/`.cpp` (the capture command word and monitor flag in the prologue; the pre-roll rule; the capture start split; the armed track's muting at and after `take_start_frames`; `CaptureSource::onInput` with this callback's block; the `MonitorStrip` into the bus after the clips; `count_in_beats_left` and `recording` in the published record; the clear of monitoring on an output reopen) — after T025, T027
- [X] T031 Extend `ENGINE/src/core/engine_core.h`/`.cpp` (`arm`, `disarm`, `setMonitor`, `recordStart` (the file created on the control thread, the manifest is the host's), `recordCancel`, `recordStop` (drain, `tembra_take_info`), `readCapture`, `resetClip`, `readPeaks`, `determineAlignment` inside `TEMBRA_TRACE_ENGINE_TAKE_ALIGNMENT` at arm and after a reopen (`input + output latency` at the stream rate), `storageFreeBytes`; `closeTransportLocked` disarms first; `onRender` passes the input block) and `ENGINE/src/core/audio_health.h`/`.cpp` (`input_overruns`, `record_backlog`, `input_xruns`) — after T026, T028, T029, T030
- [X] T032 Extend `ENGINE/include/tembra_engine.h` to ABI 7 (`tembra_capture_config`, `tembra_transport_arm/disarm/set_monitor`, `tembra_record_config`, `tembra_transport_record_start/cancel/stop`, `tembra_take_stop_reason`, `tembra_take_info`, `tembra_capture_snapshot`, `tembra_capture_read/reset_clip/read_peaks`, `tembra_peak_bucket`, `tembra_storage_free_bytes`; the appended `count_in_beats_left`/`recording` and the three health fields), create `CAPTURE/capture_abi.cpp`, set `ENGINE/src/engine_info.cpp` to 0.6.0 / ABI 7, add `capture_source.cpp`, `monitor_strip.cpp`, `peak_ring.h` to `TEMBRA_RT_SOURCES` and the new test files to `ENGINE/tests/CMakeLists.txt` — after T031
- [X] T033 Extend `ENGINE/android/src/main/cpp/tembra_jni.cpp` (`nativeTransportArm/Disarm/SetMonitor/RecordStart/RecordCancel/RecordStop`, `nativeCaptureRead`, `nativeCaptureResetClip`, `nativeCaptureReadPeaks` with a direct `ByteBuffer`, `nativeStorageFreeBytes`, the health array 9 → 12, the transport read arrays +2) and `app/shared/engine/src/nativeInterop/cinterop/tembra_engine.def` — after T032
- [X] T034 [P] Create `ENG/ArmResult.kt`, `RecordConfig.kt`, `RecordStartResult.kt`, `TakeInfo.kt` (its `stopReason` is `audio.tembra.core.recording.TakeStopReason`; no engine-side mirror), `CaptureSnapshot.kt`, `PeakBuckets.kt`; extend `ENG/TimelineAudio.kt` (the ten members of the "Kotlin seam"), `ENG/TransportSnapshot.kt` (`countInBeatsLeft`, `recording`), `ENG/AudioEvent.kt` (`InputRouteChanged`, `OutputRouteChanged(isSpeaker)`), `ENG/perf/AudioHealth.kt` (+3), `ENG/perf/AudioHealthPublisher.kt` (+4 counters), `ENG/FakeAudioEngine.kt` (the capture half with `armResult`, `nextCapture`, `scriptedPeaks`, `recordStopResult`, `takeWriter`)
- [X] T035 Extend `app/shared/engine/src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt` (the natives, `PeakBucketBuffer.kt` for the direct buffer, `HEALTH_FIELDS` 12, the route events from an `AudioDeviceCallback` registered while the transport is open, `keepAlive` untouched) — after T033, T034
- [X] T036 Extend `app/shared/engine/src/iosMain/kotlin/audio/tembra/engine/IosAudioSession.kt` (`activateForRecording()`: `playAndRecord`, `.measurement`, `[.allowBluetoothA2DP, .defaultToSpeaker]`, 48 kHz preferred, 5 ms buffer; `inputLatencyNanos() = inputLatency + ioBufferDuration`; route-change notifications → `InputRouteChanged`/`OutputRouteChanged(isSpeaker)` from `currentRoute`) and `NativeAudioEngine.ios.kt` (`arm` activates for recording then calls the engine; `disarm` returns to playback; `setReportedLatencies` on arm and on every route change; the new calls and snapshot structs allocated once) — after T032, T034
- [X] T037 [P] Create `CORE/project/CountIn.kt`, `CountInSerializer.kt`, `RecordTake.kt`, `SetCountIn.kt`; extend `CORE/project/Project.kt` (`countIn: CountIn = CountIn.ONE_BAR` appended after `tracks`, `@Serializable(with = CountInSerializer::class)`), `Track.kt` (`nextTakeName`), `ProjectEditor.kt` (`recordTake` by the split arithmetic, the `SetCountIn` case; both in the exhaustive `apply`), `EditRejection.kt` (`TakeTooShort`, kept `UnknownTrack`/`ClipLimit`), `ProjectDerived.kt` (`clipsOverlapping`)
- [X] T038 [P] Create `CORE/recording/RecordingState.kt`, `RecordingEvent.kt`, `RecordingEffect.kt`, `RecordingTransition.kt`, `MonitorState.kt`, `TakeStopReason.kt`, `RecordingPolicy.kt` (the table of contracts/recording-session.md as one exhaustive `when` per state)
- [X] T039 Extend `DATA/projects/ProjectFolder.kt` (`takeStagingPath`, `takeManifestPath`), create `DATA/projects/TakeManifest.kt`, `TakeAdopter.kt`, `AdoptedTake.kt`, `LiveOverview.kt`, `TakeFreeSpace.kt`; extend `DATA/projects/ProjectRepository.kt`, `OkioProjectRepository.kt` (`pendingTakes`, `discardTake` under the write mutex on IO) and `FakeProjectRepository.kt` — after T034, T037
- [X] T040 [P] Extend `DATA/timeline/TimelinePreferences.kt` (`monitorInput`, `setMonitorInput`), `TimelinePreferenceKeys.kt` (`timeline.monitorInput`), `DataStoreTimelinePreferences.kt`, `FakeTimelinePreferences.kt`
- [X] T041 [P] Create `UI/platform/AudioRouteMonitor.kt`, `AudioRoute.kt`; `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/platform/IosAudioRouteMonitor.kt` (`currentRoute` + the route-change notification; `outputIsSpeaker` when the output port type is the built-in speaker or receiver); `ANDROID/AndroidAudioRouteMonitor.kt` (`AudioManager.getDevices` + `AudioDeviceCallback`; the speaker when no wired, USB or Bluetooth A2DP output is present); bind in `UI/di/AppGraph.kt`, `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/IosAppGraph.kt`, `ANDROID/AndroidAppGraph.kt`
- [X] T042 Create `UI/session/RecordingController.kt`, `RecordingUi.kt`, `LiveTakeUi.kt`, `InputTelemetry.kt`; extend `UI/session/SessionState.kt` (`recording`), `SessionMessage.kt` (`MicrophoneDenied`, `MonitorNeedsHeadphones`, `TakeEnded`, `RecordRefused`), `DialogRequest.kt` (`StopAndLeave(recording)`), `ProjectSession.kt` (forwarding; `TrackDeleted`, `ProjectClosing`, `HostHidden` from `transportHostVisible(false)`, `TransportClosed` from `detachTransport`, `Interrupted` from the engine events; `applyToEngine`'s `RecordTake` case opens the source first; `setCountIn` like `setMetronome` with the `restore()` carry-over; `confirmLeave` sets `recording`; the pending-take adoption after `install` with no history push; the 200 MB check before `recordStart`), `AppLifecycleObserver.kt` (`ON_RESUME` → `permission.refresh()`), `UI/di/AppBindings.kt` (the controller with the session scope) — after T038, T039, T040, T041
- [X] T043 [P] Extend `ANDROID/PlaybackForegroundService.kt` (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or FOREGROUND_SERVICE_TYPE_MICROPHONE` on API 34+, the recording notification text), `ANDROID/AndroidBackgroundPlaybackHost.kt` and `UI/platform/PlaybackNotification.kt` (`recording(name)`); extend `app/androidApp/src/androidTest/kotlin/audio/tembra/android/AndroidBackgroundPlaybackHostTest.kt` for the recording notification
- [X] T044 Gate (host): `./gradlew engineHostTest checkAndroidHost --warning-mode all` green with zero warnings: T009–T021 pass against T023–T043
- [X] T045 Gate (simulator): `./gradlew checkIos -Ptembra.iosSimulator="iPhone 18 Pro" --warning-mode all` green, including the iOS `NativeAudioEngineRecordingTest` (the simulator's microphone granted with `xcrun simctl privacy booted grant microphone audio.tembra.app`)
- [X] T046 Gate (device): quickstart §2 on the Pixel 6 Pro, then `app/tools/gradlew-device.sh <pixel6-serial> :shared:engine:connectedAndroidDeviceTest :androidApp:connectedDebugAndroidTest --warning-mode all` green (`pm grant audio.tembra.app android.permission.RECORD_AUDIO` first); record the measured `alignmentFrames` of the device test in `specs/010-timeline-recording/evidence/alignment.md`

**Checkpoint**: the engine records a take behind ABI 7 on both platforms, the document can hold a take, the controller runs the policy — user stories can begin.

---

## Phase 3: User Story 1 - Arm a track and record a take along with the project (Priority: P1) 🎯 MVP

**Goal**: arm from the header, press Record, see the clip grow with its waveform, end the take, play it back in time, record over it and undo (spec US1; FR-001, FR-007 to FR-014).

**Independent Test**: quickstart §4 on the Pixel 6 Pro and the iPhone 18 Pro simulator: "Take 1" on the demo's new track, in time with the other tracks, surviving a relaunch, undone and redone.

### Tests for User Story 1 (write first, watch them fail)

- [X] T047 [P] [US1] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `arm(trackId)` forwards and lights `TrackUi.armed`; `record()` with no armed track arms the selected one; in an empty project adds "Track 1", selects and arms it; `record()` while `Recording` stops; `pause()` while recording ends the take; the FR-018 availability map flips while a take runs; while a take runs `skipToStart`, `stepBar` and ruler seeks are refused and the transport's seeking controls report disabled (FR-009); the readout's length is max(project length, the live take's end) (FR-010); `RecordButtonState` from the bar width; `resetClip`
- [X] T048 [P] [US1] Create `UI_UITEST/timeline/ScriptedInputTelemetry.kt`, `UI_UITEST/timeline/components/ArmButtonTest.kt` (idle/armed, `Role.Switch`, tooltip, hover and focus through an injected `MutableInteractionSource`), `UI_UITEST/transport/RecordButtonTest.kt` (the four states and descriptions); extend `UI_UITEST/transport/TransportBarTest.kt` (`requiredWidth` 360, 432, 599, 600 dp → Record hidden/shown, readout one-line/stacked, every target ≥ 44 dp, nothing clipped), `UI_UITEST/timeline/TrackHeaderCompactTest.kt` (the armed row at 360 dp: chip hidden, Record shown, name ≥ 72 dp; the meter node present), `UI_UITEST/timeline/TrackHeaderTest.kt` (Arm in row 2 at 36 dp), `UI_UITEST/timeline/TrackLaneTest.kt` (the live take drawn above the clips, length from the scripted snapshot, caption "Take 2", semantics "Recording, Take 2, 00:04.10"), `UI_UITEST/timeline/TimelineScreenTest.kt` (arm → meter node; record → `timeline.clip.take`; stop → a clip named "Take 1"; the disabled toolbar while recording; a ruler tap and a playhead drag ignored while recording); `TransportBarTest` also: skip-to-start and the step buttons disabled while recording, the length readout growing with the live take

### Implementation for User Story 1

- [X] T049 [P] [US1] Create `UI/timeline/components/ArmButton.kt` (contracts/timeline-recording-ui.md; `modifier` first optional on the root `Box`, `drawBehind` circle, `toggleable` with `Role.Switch`, tag `TimelineTestTags.arm(i)`)
- [X] T050 [P] [US1] Create `UI/transport/RecordButton.kt` and `RecordButtonState.kt` (a `TransportButton` variant in `DawColors.record`; the pulse ring reads the beat change in `drawBehind`; steady under reduce motion; descriptions per state)
- [X] T051 [P] [US1] Extend `UI/transport/TransportBar.kt` (`RecordInBarFrom = 432.dp`, Record right of play/pause, `ReadoutStacksBelow = 600.dp` applied only when Record is in the bar, the new budget: six gaps + five 44 dp targets + the 48 dp disc + the speaker button), `TransportUiState.kt` (`record`, `countInBeatsLeft`, `seekEnabled`, `length` as max(project length, live take end)), `TransportCallbacks.kt` (`onRecord`), `TransportCopy.kt`; pass the callbacks through `UI/tracksettings/TrackSettingsScreen.kt` and `TrackSettingsScreenContent.kt`
- [X] T052 [P] [US1] Extend `UI/timeline/components/TrackHeaderCompact.kt` (Arm beside S; Record in the chip's place while `recordHere && armed`; the meter slot along the bottom edge, filled by US2), `TrackHeader.kt` (Arm in row 2; the meter slot beneath row 2), `TrackRow.kt` (the new callbacks), `TrackUi.kt` (`armed`, `recordHere`, the description with "armed")
- [X] T053 [P] [US1] Extend `UI/timeline/components/TrackLane.kt` and `ClipPainter.kt` (`drawLiveTake`: the length from `InputTelemetry` read in the draw lambda inside `PerfTrace.section(TIMELINE_TAKE_FRAME)`, the waveform from `LiveTakeUi.overview`, drawn above the clips; the semantics node from the 10 Hz mirror; tag `timeline.clip.take`)
- [X] T054 [US1] Extend `UI/timeline/TimelineViewModel.kt` (`arm`, `record` with the arm-or-add rule, `stopRecording`, `resetClip`; `mapState` derives `TrackUi.armed/recordHere`, `TransportUiState.record` from `TimelineLayout`'s bar width, `takeRunning` and the FR-018 availability map; the transport's seeking controls, ruler taps and the playhead drag refused while a take runs (FR-009); `TransportUiState.length` from the live take's end read by the 10 Hz mirror (FR-010); `TRANSPORT_RECORD_START`/`TRANSPORT_RECORD_STOP` sections around the intents), `TimelineUiState.kt`, `TimelineCallbacks.kt` (`onArm`, `onRecord`, `onResetClip`), `TimelineTestTags.kt` (`arm(i)`, `meter(i)`, `clip(i)`, `record(i)`, `takeClip`, `record`), `TimelineCopy.kt` — after T049–T053
- [X] T055 [US1] Extend `UI/timeline/TimelineScreen.kt` and `TimelineScreenContent.kt` (the `InputTelemetry` from the session's controller passed to the lanes and headers; `SessionMessage.TakeEnded`/`RecordRefused` as snackbars; the disabled toolbar entries while recording) — after T054
- [X] T056 [P] [US1] Extend `UI/timeline/components/TrackMenu.kt` (Move up/down/Delete disabled while a take runs), `UI/tracksettings/TrackSettingsViewModel.kt` (`confirmDelete` refused while recording on that track), `UI/projects/ProjectsViewModel.kt` (`open`, `restoreDemo`, `openInTimeline` through `PlaybackGuard.confirmLeave()` so a running take shows the dialog)
- [X] T057 [US1] Device check: quickstart §2 then §4 scenarios 1–7 on the Pixel 6 Pro (`app/tools/gradlew-device.sh` lane for the US1 `uiTest` classes, then the app by hand: `adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity`) and on the iPhone 18 Pro simulator; capture armed, recording and finished-take screenshots at 360 dp and 1024 dp into `specs/010-timeline-recording/screenshots/` with `NOTES.md`

**Checkpoint**: a take can be recorded, replayed in time, recorded over and undone on both platforms — the MVP.

---

## Phase 4: User Story 2 - See the input level before and while recording (Priority: P1)

**Goal**: the live meter on the armed header with hold and clip latch in both header forms (spec US2; FR-005, FR-006).

**Independent Test**: quickstart §5: the meter follows within 50 ms, holds about 1.5 s, latches at full scale until tapped or disarmed, without changing the header's height.

### Tests for User Story 2 (write first, watch them fail)

- [X] T058 [P] [US2] Create `UI_TEST/timeline/LevelHoldTest.kt` (the −60…0 dB mapping, the 1.5 s hold with a virtual clock, the clip latch mirrors the snapshot) and `UI_UITEST/timeline/components/InputMeterTest.kt` (scripted telemetry: silent → empty; −20 dB → 2/3 of the track; full scale → the clip square lit; tap resets; the hold mark after 1.5 s of `mainClock.advanceTimeBy`; compact 4 dp along the bottom edge and expanded 8 dp beneath the gain readout; the header's measured height unchanged; reduce motion still moves; semantics "Input level", "<n> dB", "clipped" announced once)

### Implementation for User Story 2

- [X] T059 [P] [US2] Create `UI/timeline/components/InputMeter.kt` (contracts/timeline-recording-ui.md: `graphicsLayer` + `drawBehind` reading `rememberFrameTick` and `telemetry.read`, `LevelSmoother` on the peak, `LevelHold`, the two meter colours and the clip square, the reset tap, the semantics) and `UI/timeline/LevelHold.kt`; extend `UI/perf/TraceFirstMeasure.kt` with `traceFirstDraw(section)` and wrap the meter's first draw in `TIMELINE_ARM`
- [X] T060 [US2] Fill the meter slots of `UI/timeline/components/TrackHeaderCompact.kt` and `TrackHeader.kt`; wire `onResetClip` through `TrackRow.kt`; `TimelineViewModel.resetClip` → the controller → `TimelineAudio.resetClip` — after T059
- [X] T061 [US2] Device check: quickstart §5 on the Pixel 6 Pro and the iPhone 18 Pro simulator; screenshots at rest, loud and clipped in both header forms into `specs/010-timeline-recording/screenshots/`

**Checkpoint**: the musician can set a level before recording.

---

## Phase 5: User Story 3 - Start on the beat: the count-in (Priority: P2)

**Goal**: Off / 1 bar / 2 bars in the metronome sheet, the counted start with the click forced on, the readout countdown, cancel paths (spec US3; FR-015).

**Independent Test**: quickstart §6 at 124 BPM in 4/4 and 7/8, with the toggle off and on, cancelled by Record, Pause, space and Escape.

### Tests for User Story 3 (write first, watch them fail)

- [X] T062 [P] [US3] Create `UI_UITEST/timeline/components/CountInMenuTest.kt` (three values, disabled while a take runs, tag `metronome.sheet.countIn`); extend `UI_UITEST/transport/TimeReadoutTest.kt` ("Count-in 4" in the position's place, both forms, semantics), `UI_UITEST/timeline/components/MetronomeSettingsSheetTest.kt` (the row under the signature), `UI_UITEST/transport/RecordButtonTest.kt` (the pulse on a beat change; steady under reduce motion); extend `UI_TEST/timeline/TimelineViewModelTest.kt` (`setCountIn` → `SetCountIn` edit not in history; `record()` from stopped with 1 bar → the controller receives `countInBeats = 4`; `PreRoll` state → `RecordButtonState.CountIn`; Record/Pause during `PreRoll` cancel)

### Implementation for User Story 3

- [X] T063 [P] [US3] Create `UI/timeline/components/CountInMenu.kt` and add it to `UI/timeline/components/MetronomeSettingsSheet.kt` under the time signature; `TimelineCallbacks.onCountIn`; `TimelineUiState.countIn`
- [X] T064 [P] [US3] Extend `UI/transport/TimeReadout.kt` (the count-in text from `TransportSnapshot.countInBeatsLeft` read in the draw lambda; `TransportCopy.countIn`) and `UI/transport/RecordButton.kt` (the per-beat pulse from the same field)
- [X] T065 [US3] Extend `UI/timeline/TimelineViewModel.kt` (`setCountIn` → `session.setCountIn`; the `countInBeats = project.countIn.bars × signature.beatsPerBar` passed with `record()`; `pause()`/`record()`/space/Escape during `PreRoll` → `cancelRecording`) and `UI/session/ProjectSession.kt`/`RecordingController.kt` (`TRANSPORT_COUNT_IN` as an animation section over the pre-roll) — after T063, T064
- [X] T066 [US3] Device check: quickstart §6 on the Pixel 6 Pro (1 bar 4/4, 2 bars 7/8, the four cancel paths) and the simulator; the count-in screenshot

**Checkpoint**: takes start on the downbeat.

---

## Phase 6: User Story 4 - Hear myself: input monitoring (Priority: P2)

**Goal**: the headphones toggle in the toolbar or the overflow, through the track's gain, mute, solo and the master, never on the built-in speaker, remembered on the device (spec US4; FR-016, FR-017).

**Independent Test**: quickstart §7's monitoring rows with wired headphones, the speaker, an unplug and wireless headphones; SC-004's delay measured in T090.

### Tests for User Story 4 (write first, watch them fail)

- [X] T067 [P] [US4] Create `UI_UITEST/timeline/components/MonitorToggleTest.kt` (Off/On/SuspendedSpeaker, `Role.Switch`, the suspended state's description, the tap on the speaker shows the snackbar through the callback) and extend `UI_UITEST/timeline/components/TimelineToolbarTest.kt` (the toggle after the scissors from 600 dp; the checked overflow row below 600 dp); extend `UI_TEST/timeline/TimelineViewModelTest.kt` (`toggleMonitor` on the speaker → `MonitorNeedsHeadphones`; otherwise the preference flips and the controller's `SetMonitor` runs; `monitorInToolbar` by width)

### Implementation for User Story 4

- [X] T068 [P] [US4] Create `UI/timeline/components/MonitorToggle.kt`; add it to `UI/timeline/components/TimelineToolbar.kt` after the scissors in the full row and as a checked `MenuItemSpec` ("Monitor input", id `monitor`) in the compact overflow; `TimelineCallbacks.onMonitorToggle`; `TimelineUiState.monitor`/`monitorInToolbar`; `TimelineCopy` strings
- [X] T069 [US4] Extend `UI/timeline/TimelineViewModel.kt` (`toggleMonitor` → the controller's `MonitorToggle` event, `TIMELINE_MONITOR_TOGGLE` around it) and `UI/session/RecordingController.kt` (`SetMonitor` → `TimelineAudio.setMonitor(enabled, trackIndex)`; the remembered `TimelinePreferences.monitorInput`; re-enable after `OutputChanged(false)`; `SuspendedSpeaker` while the route is the speaker; monitoring off on `Disarm`) — after T068
- [ ] T070 [US4] Device check: quickstart §7 rows "Monitoring on headphones", "Speaker guard", "Unplug while monitoring", "Wireless headphones" on the Pixel 6 Pro and the iPhone 14 (wired headphones with the music stream at a low level for this row only, then back to zero); screenshots of the toggle on and suspended

**Checkpoint**: singers and electric instruments can hear themselves safely.

---

## Phase 7: User Story 5 - Microphone permission, interruptions and the screen off (Priority: P2)

**Goal**: the permission flow with "Open settings", takes that survive the screen off and end cleanly on calls, input changes, revocation, leaving and termination (spec US5; FR-003, FR-019 to FR-025).

**Independent Test**: quickstart §7's remaining rows on real devices.

### Tests for User Story 5 (write first, watch them fail)

- [X] T071 [P] [US5] Extend `UI_TEST/session/RecordingControllerTest.kt` (already covering the policy rows: add the snackbar message content per `MicrophonePermissionStatus`, `refresh()` on resume re-arms nothing by itself, the 200 MB refusal, the 30-minute and disk-full reasons' messages); extend `UI_UITEST/designsystem/StopAndLeaveDialogTest.kt` (the recording variant's copy and tags), `UI_TEST/navigation/ShellViewModelGuardTest.kt` (a pre-roll leaves without a dialog and cancels; a take asks), `UI_UITEST/timeline/TimelineScreenTest.kt` (the denied snackbar with the "Open settings" action, tag `snackbar.microphone.settings`)

### Implementation for User Story 5

- [X] T072 [P] [US5] Extend `UI/session/RecordingController.kt` (the permission branch per status; `MicrophoneDenied(openSettings)`; `refresh()` wiring), `UI/timeline/TimelineScreenContent.kt` (the snackbar action calling `onRequestMicrophone` → `permission.openSystemSettings()` or `request()`), `TimelineCallbacks.onRequestMicrophone`, `TimelineCopy`
- [X] T073 [P] [US5] Extend `UI/designsystem/StopAndLeaveDialog.kt` (the `recording` variant: "Stop recording?", "The take is kept up to now.", "Stop recording and leave" / "Keep recording", tags `dialog.stopRecording.confirm`/`.cancel`), `UI/navigation/NavigationShellContent.kt` (the variant), `UI/navigation/ShellViewModel.kt` (a pre-roll cancels without a dialog), `UI/session/FakePlaybackGuard.kt` in `UI_TEST`
- [X] T074 [US5] Wire the background and interruption paths: `RecordingController` calls `host.keepAlive(PlaybackNotification.recording(name))` on `TakeStarted` and `keepAlive(playing)`/`release()` on `TakeFinalised` per the playback state; `Interrupted` from `AudioStatus.events` ends the take before the playback policy pauses; `InputRouteChanged` ends it with `InputLost`; `PermissionRevoked` from the status flow — after T072, T073
- [ ] T075 [US5] Device check: quickstart §7 rows "Wired headset microphone mid-take", "Permission denied", "Permission revoked while armed", "Call during a take", "Screen lock", "Force quit", "Leaving", "Tuner hand-over", "iOS arm during playback" (measure the gap with a loopback recording; record the value in `specs/010-timeline-recording/evidence/notes.md`), "30-minute cap and space" (the cap with a shortened `maxFrames` in a debug build; the space check with a filled volume on the emulator) on the Pixel 6 Pro and the iPhone 14, the call, force-quit and screen-lock rows ten times each with a tally in the notes (SC-006); screenshots of the denied snackbar and the stop-recording dialog

**Checkpoint**: nothing a phone does loses a take.

---

## Phase 8: User Story 6 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

**Goal**: the new controls at every width, with hover, focus, the three keys, Escape, and screen-reader semantics (spec US6; FR-026 to FR-031).

**Independent Test**: the screenshot set of FR-031 on both platforms, the keyboard run on the iOS lane and desktop mode, TalkBack and VoiceOver by ear.

### Tests for User Story 6 (write first, watch them fail)

- [X] T076 [P] [US6] Extend `UI_UITEST/timeline/TimelineKeyHandlingTest.kt` ("A" arms the selected track, "R" records, "I" toggles monitoring, Escape cancels a pre-roll, space during a take pauses, the FR-018 keys are no-ops while recording; declared per lane through `supportsKeyboardInputMode`), `UI_UITEST/timeline/TimelineScreenTest.kt` (tab order: Arm after S, Record after play/pause, the monitor toggle after the scissors; the largest font at 360 dp with the armed row), `UI_UITEST/timeline/TrackHeaderCompactTest.kt` and `TrackHeaderTest.kt` (semantics of FR-029 verbatim), `UI_UITEST/transport/TransportBarTest.kt` (phone landscape 640×360 dp: Record in the bar, two tracks and the ruler visible in `TimelineScreenTest`)

### Implementation for User Story 6

- [X] T077 [P] [US6] Extend `UI/timeline/TimelineKeyHandling.kt` (A, R, I, Escape; the no-ops while recording) and the traversal order through `Modifier.focusProperties`/order in `TrackHeaderCompact.kt`, `TrackHeader.kt`, `TransportBar.kt`, `TimelineToolbar.kt`
- [X] T078 [P] [US6] The semantics of FR-029 on `ArmButton.kt`, `RecordButton.kt`, `InputMeter.kt` (the `liveRegion` for "clipped"), `MonitorToggle.kt`, `CountInMenu.kt`, the live take node in `TrackLane.kt`, the snackbar actions; hover states and tooltips (`TembraTooltip`) on every icon-only control
- [X] T079 [P] [US6] Reduce motion: `RecordButton.kt`'s pulse and glow off under `LocalReduceMotion`; the meter and the live waveform unaffected (a `uiTest` case in `InputMeterTest` and `RecordButtonTest`)
- [X] T080 [US6] Screenshots per FR-031 and quickstart §9: Android through the capture recipe at 360, 432, 600, 840 and 1024 dp (`wm density`), phone landscape and the fold's postures; iOS through `IOSPERF/ScreenshotMatrix.swift` with the new states (`armedRest`, `armedLoud`, `armedClipped`, `countIn`, `recording`, `takeDone`, `takeOverClips`, `monitorOn`, `monitorSuspended`, `micDenied`, `stopRecordingDialog`, `sheetCountIn`, `barRecord432`, `largestFont`) on the iPhone 18 Pro, the iPad and the Duo; store under `specs/010-timeline-recording/screenshots/` with `NOTES.md` and the design comparison — after T077–T079
- [ ] T081 [US6] TalkBack (Pixel 6 Pro) and VoiceOver (iPhone 14) by ear: the arm toggle, Record's three states, the meter's level and "clipped", the growing clip, the monitor toggle's reason, the count-in; findings into `screenshots/NOTES.md` (the owner may do this step)

**Checkpoint**: the feature holds at every width and for every input.

---

## Phase 9: Polish & Cross-Cutting (journeys, pipeline, profile, alignment, docs, release build, Stitch, sessions)

**Purpose**: the measurement the constitution demands, the regression comparison and the Stitch sync the owner asked for, and the evidence for the pull request.

- [X] T082 [P] Create `PERF/journeys/RecordingJourney.kt` (the ten steps of contracts/perf-instrumentation.md; `tapIfPresent` for `transport.record` vs `timeline.track.record.<last>`; the readout waits "Count-in" then "00:0[7-9]"; undo twice) and `PERF/RecordingJourneyBenchmark.kt` (`PerfMetrics.journey("recording")`, 20 iterations, `trace("journey.recording")`); extend `PERF/PerfMetrics.kt` (`sectionsOf("recording")` with the eleven sections), `PERF/BaselineProfileGenerator.kt` (`journeys()` + recording), `PERF/ProfileComparisonBenchmark.kt` (`recording()`), `PERF/journeys/StressJourney.kt` (add a track, arm, record three seconds while scrolling, stop, undo twice, before `devtools.writeHealth`)
- [X] T083 [P] Create `IOSPERF/Journeys/RecordingJourney.swift` and `IOSPERF/RecordingPerfTests.swift` (`measureJourney("recording")`); extend `IOSPERF/Metrics.swift` (`sections(of: "recording")`, `engineSections` with `engine.takeAlignment`), `IOSPERF/Journeys/StressJourney.swift`, `IOSPERF/PerfJourneyTestCase.swift` (the microphone alert answered on Timeline too), `app/iosApp/iosApp.xcodeproj/project.pbxproj` (the two files)
- [X] T084 [P] Register the journey and the counters in the pipeline: `app/tracepulse.yml` (`journeys` + recording), `PIPE/config.py` (`DEFAULT_JOURNEYS`), `PIPE/catalogue.py` (`JOURNEYS_MEASURED`; four `_AUDIO` rows: `inputOverruns` expected 0 flagged on any increase, `recordBacklog` 50 % with a 2048-frame floor, `takeAlignmentMs` and `monitorDelayMs` 20 % with a 2 ms floor), `PIPE/orchestrate_android.py` (`BENCHMARK_CLASS_OF`), `PIPE/collect_android.py` (`_JOURNEY_OF_CLASS`, `_REQUIRED`, `_COMPARISON_METHODS`), `PIPE/collect_ios.py` (`_TEST_JOURNEYS`, `order`, `_HEALTH_KEYS`), `PIPE/report.py` (`_JOURNEY_ORDER`), `PIPE/traces_android.py` (`AUDIO_COUNTER_KEYS` with `audio.input_overruns`, `audio.record_backlog`, `audio.take_alignment_ms`, `audio.monitor_delay_ms`), `PIPE/schema/round-record.schema.json` and `comparison.schema.json` (the journey enum), `app/perf/tests/fixtures/engine-health.json`, the pytest fixtures and assertions, `specs/007-performance-pipeline/contracts/metric-catalogue.md`, `app/perf/README.md`; `cd app/perf && uv run pytest` green
- [X] T085 [P] Extend `UI/perf/EngineHealthJson.kt` and `UI/perf/AudioHealthTicker.kt` with the four counters (`takeAlignmentMs` and `monitorDelayMs` from the capture snapshot's latencies)
- [X] T086 Trial round: build through `gradle-run` (`./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf --warning-mode all`), apply quickstart §2, then `cd app/perf && uv run tracepulse run --device pixel6 --label 010-recording --rounds 1 --no-publish --no-build --apk … --test-apk …` and the iPhone 14 equivalent (`xcodebuild build-for-testing -scheme TembraPerf -configuration Perf`, `--no-build --xctestrun …`); every section of the `recording` journey present on both, `audio.inputOverruns = 0`, `audio.takeAlignmentMs` plausible (5–60 ms); findings into `specs/010-timeline-recording/evidence/trial-round.md` — after T082–T085
- [X] T087 Baseline profile: `cd app/perf && uv run tracepulse profile generate --device pixel6` (the app cleared first: `adb shell pm clear audio.tembra.app`), commit `app/androidApp/src/main/generated/baselineProfiles/{baseline-prof,startup-prof}.txt`, then `profile verify --device pixel6` and `profile compare --device pixel6 --journeys recording` into `specs/010-timeline-recording/evidence/profile.md` — after T086
- [X] T088 [P] Compose compiler report: `./gradlew :shared:ui:assembleRelease -PcomposeReports=true --warning-mode all`; copy the reports for the new composables into `specs/010-timeline-recording/evidence/compose-report/` and explain any non-skippable composable or unstable parameter in `evidence/compose-report/README.md`
- [X] T089 [P] Create `app/tools/measure-take-alignment.py` (stdlib + numpy if present: reads a WAV, detects click onsets by an energy threshold, computes each onset's offset to the nearest beat at `--bpm` and `--rate`, prints mean, max and per-beat offsets; `--monitor <wav>` mode prints the delay between the first two transients) and the `specs/010-timeline-recording/evidence/alignment.md` template (device, route, mean, max, the counter's value)
- [ ] T090 Loopback alignment and monitoring delay (quickstart §8, the one sounding step, announced): the Pixel 6 Pro and the iPhone 14 with a loopback cable (or acoustically at the lowest audible volume where no cable fits), a five-minute take with the click, `measure-take-alignment.py`; every click within 10 ms of its beat; the monitoring delay ≤ 30 ms; the counters within 5 ms of the measurement; the Pixel 7 Pro, the tablet, the OPPO, the Redmi and the fold emulator measured and recorded without a pass/fail (the spec's assumption); results into `evidence/alignment.md` — after T089
- [X] T091 [P] Docs: `app/README.md` (rows: capture, recorder, recording session, the route monitor, the two tools; the run conditions of quickstart §2 as a paragraph), `app/engine/README.md` (the recorder thread and the duplex rule), `app/engine/third_party/README.md` (dr_wav's writer stays test-only), `app/THIRD_PARTY_LICENSES.md` ("Last reviewed" 2026-10-27, no new row), `app/perf/README.md` (the `recording` journey, the four counters), `CLAUDE.md` (the three new tools in the commands list; the build-order and version lines were corrected on 2026-10-27 after `/speckit-analyze`)
- [X] T092 Final lanes on every target (delegated per target to Sonnet-class sub-agents with the serial or simulator name, the commands of quickstart §3 and §2's conditions): host, the iPhone 18 Pro, the iPad and the Duo simulators, the Pixel 6 Pro, the Pixel 7 Pro, the Pixel Tablet, the fold emulator, the OPPO (dimmed by hand) and the Redmi; zero warnings; each result with its counts into `specs/010-timeline-recording/screenshots/NOTES.md` § Final pass, naming any target skipped and why — after every story's tasks
- [X] T093 Release build on every attached target for the owner's checks: `app/tools/release-android.sh` and `app/tools/release-ios.sh` (the profile of T087 compiled in), with quickstart §2 applied; record the installed commit per target in `screenshots/NOTES.md` — after T087, T092
- [ ] T094 Owner checks (not an agent task): Record's placement at 360 dp (Complexity #1 of plan.md: the armed header vs a floating button), the iOS arm-during-playback gap from T075, SC-010's two-minute walkthrough (arm, record with the count-in, listen, record a second take), TalkBack/VoiceOver (T081); decisions appended to spec.md's Clarifications as a design-iteration session and turned into tasks here
- [ ] T095 Stitch sync (quickstart §11): render the eight states from the captures of T080 (phone 390 wide, tablet 1280; headless Chrome at 500 px for the phone) and upload each as a new screen to project `12040171611712589599` with the `stitch-design:upload-to-stitch` script; `PATCH /v1/projects/{p}?updateMask=screenInstances` to place them beside the timeline screens and hide the superseded instances; re-pull `design/stitch/screens.json` and `design/stitch/screens/timeline-recording/*`; add the four tokens and the meter/record prose to `design/stitch/DESIGN.md` (seeds and prose only); note the new slugs in `design/stitch/README.md` — after T080, T094
- [ ] T096 Soak (SC-003): an eight-track project (the demo plus four imported stems from `specs/009-timeline-arranger/fixtures/`) with a 30-minute take on a ninth track, on the Pixel 6 Pro and the iPhone 14 under quickstart §2 (the input is the room), the health counters read at the end through `devtools.writeHealth` or the health JSON: zero `audio.inputOverruns`, `audio.starvation` and `audio.xruns`; the take plays back without a dropout at its start, middle and end; results into `specs/010-timeline-recording/evidence/notes.md` — after T092
- [ ] T097 Pull request and the labelled sessions after the merge: the PR names the principle behind each non-obvious decision (plan.md's Constitution Check and Complexity Tracking), the skill contracts, the screenshots, the compose report, `evidence/alignment.md`, `evidence/profile.md`, the soak of T096 and the trial round; after the merge, `uv run tracepulse run --device pixel6 --label 010-recording --rounds 5`, the same on `iphone14` and the Pixel 7 Pro by serial (quickstart §2 before each), then `sync` and `report --open`; read the "last session" block and write every SUSPECT/CONFIRMED onset and `above_limit` notice with its cause or fix into `specs/010-timeline-recording/evidence/perf-session.md`; the startup family within the gate, zero `audio.inputOverruns`, `audio.starvation` and `audio.xruns` on the reference devices — after T093, T095, T096

## Phase 10: Owner findings of 2026-11-02 (recording checks on the iPad simulator and the iPhone 14)

**Purpose**: three defects the owner's hands-on check surfaced after T093 (`screenshots/NOTES.md` § Owner findings); each is fixed test-first and the Release app is rebuilt for the iPhone 14 and every emulator and simulator.

- [x] T098 [US1] iOS first-arm channel count: `NativeAudioEngineRecordingTest.theFirstArmReportsTheRoutesInputChannels` (the first arm after the unit is created must report `AVAudioSession.inputNumberOfChannels`, 1 on the simulators and the phones; today it reports 2 and the take is a stereo WAV with a silent channel 1), then `NativeAudioEngine.ios.kt` `arm()` clamps `maxChannels` by the active route's input channels after `activateForRecording()` (`IosAudioSession.inputChannels()`), since the RemoteIO hardware format on bus 1 is a 2-channel default until the unit has run once (`remote_io_audio_device.cpp` `configureDuplexLocked`)
- [x] T099 [US2] Expanded header meter: `TrackHeaderTest.expandedArmedHeaderShowsTheMeterAlongItsBottomEdge` (the meter displayed, 4 dp, on the header's bottom edge inside the 96 dp lane; today 44 + 44 + 4 + 8 + 8 dp of padding exceed the lane and Compose measures it at 0 dp), then `TrackHeader.kt` overlays `InputMeter` along the bottom edge inside a `Box` as `TrackHeaderCompact` does, `meterHeightExpanded` 4 dp; contract `timeline-recording-ui.md` row updated
- [x] T100 [US1] iOS arm and disarm off the main thread: `NativeAudioEngineBlockingDispatchTest` (arm and disarm hop to the engine's injected blocking dispatcher), then `NativeAudioEngine.ios.kt` runs the session switch, the unit restart and the settle poll of `arm()` and the restart of `disarm()` under `Dispatchers.IO.limitedParallelism(1)` (serial, so a close never overlaps an open), `TimelineAudio.disarm()` becomes `suspend` (the controller launches `closeInput` on the session scope, never cancelled; `FakeAudioEngine`, the Android actual and the device tests follow), the trial round's 604 ms `timeline.arm` on the iPhone 14 no longer blocks the main thread
- [x] T101 Lanes and release after Phase 10: `checkAndroidHost`, `checkIos` on the iPhone 18 Pro, `engineHostTest`; `tools/release-ios.sh` (the iPhone 14 and the three booted simulators) and `tools/release-android.sh` (the fold emulator); results into `screenshots/NOTES.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies; T008 proves the toolchain before Phase 2.
- **Foundational (Phase 2)**: depends on Setup; blocks every story. The gates are T044 (host), T045 (simulator), T046 (device).
- **User stories (Phases 3–8)**: all depend on Phase 2. US1 is the MVP; US2 fills the meter slots US1 leaves in the headers; US3 extends US1's Record button and readout; US4 extends US1's toolbar and the controller; US5 extends the controller and the dialog and can run beside US3/US4; US6 needs every component.
- **Polish (Phase 9)**: T082–T085 can start after Phase 2 (the anchors exist) and are finished after US6; T086 after them; T087 after T086; T090 after T089 and US4; T092 after every story; T093 after T087 and T092; T095 after T080 and T094; T096 (soak) after T092; T097 last.

### Within Each User Story

Tests first and failing → components (parallel) → ViewModel and controller wiring → screen wiring → device check and screenshots.

### Parallel Opportunities

- Phase 1: T002–T007 in parallel after T001.
- Phase 2 tests T009–T022 all in parallel; implementation groups in parallel: capture files (T023, T024 → T025, T026; T027), device shells (T028, T029), then T030 → T031 → T032 → T033; the Kotlin seam T034 (→ T035, T036 after T033/T032); domain T037, T038; data T039 (after T034, T037), T040; platform T041, T043; then T042; then the three gates.
- US1: T047–T048 parallel; T049–T053 parallel; T056 parallel with T054–T055.
- US3, US4 and US5 can run as three workers after US1 and US2 (disjoint components; the controller edits are serialised through one integrator, as 009's D1 did).
- Polish: T082–T085, T088, T089, T091 in parallel.

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "TimelineViewModelTest (US1 cases) in UI_TEST/timeline/TimelineViewModelTest.kt"
Task: "ArmButtonTest, RecordButtonTest, TransportBarTest+, TrackHeaderCompactTest+, TrackHeaderTest+, TrackLaneTest+, TimelineScreenTest+ in UI_UITEST/…"

# Components together (disjoint files; three agents in detached worktrees, one integrator):
Task: "ArmButton in UI/timeline/components/ + TrackHeaderCompact/TrackHeader/TrackRow extensions"
Task: "RecordButton, RecordButtonState and the TransportBar budget in UI/transport/"
Task: "TrackLane/ClipPainter live take in UI/timeline/components/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) and Phase 2 (Foundational): the engine records behind ABI 7, the document holds a take, the controller runs the policy.
2. Phase 3 (US1): a take recorded, replayed in time, recorded over and undone on both platforms.
3. **STOP and VALIDATE**: quickstart §4, the device-lane engine test's `alignmentFrames`, a relaunch, the undo.

### Incremental Delivery

US1 (record) → US2 (meter) → US3 (count-in) → US4 (monitoring) → US5 (permission, interruptions, background) → US6 (adaptive proof) → Polish (journeys, pipeline, profile, alignment, docs, release build, owner checks, Stitch, sessions). Each story is demonstrable on its own and never breaks the previous one.

### Parallel Team Strategy

After Phase 2: worker A on US1 → US2 → US6; worker B on US3 → US5; worker C on US4, then the perf journeys (T082–T085) and the pipeline. Lane runs, captures and perf rounds go to Sonnet-class sub-agents per target; the main agent integrates and reviews.

---

## Notes

- Every device run starts with quickstart §2 (`app/tools/apply-device-run-conditions.sh <serial>`): brightness at the floor, the music stream and notifications silent, the black test window; the OPPO is dimmed by hand; the iPhone 14 is dimmed by the perf runner. T090 is the only task that makes a sound, with a cable where possible, announced first; T070's headphone rows use a low level on wired headphones only.
- The Record placement below 432 dp of bar width is the owner's fallback applied by arithmetic (plan.md Complexity #1); T094 is where the owner confirms it or asks for the floating button, which would be a design-iteration phase appended here, as 009's D1 was.
- iOS arming while a project plays restarts the audio unit (plan.md Complexity #3); T075 measures the gap. If it exceeds 30 ms, the finding goes to the owner with the only alternative (the input bus always enabled, which FR-003 forbids).
- The 10 Hz live-overview poll, the per-frame meter and the growing clip read the capture seqlock; nothing about levels or lengths goes through `StateFlow` (constitution II).
- The perf package keeps its data between iterations: the `recording` journey undoes its take and its track; `pm clear` precedes a round after any seeded data.
- Section names keep exactly one dot (the iOS collector's regex); counters use `audio.camelCase` in the catalogue and snake_case in the pipeline.
- The take's WAV is 24-bit at the stream rate; the spec's 30-minute cap and the 200 MB / 50 MB space rules are the engine's `max_frames` and `min_free_bytes` plus the controller's pre-check.
- Commit after each task with the conventional message style; the spec-kit hooks commit after each phase command.
