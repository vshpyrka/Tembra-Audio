# Implementation Plan: Microphone Recording on the Timeline

**Branch**: `010-timeline-recording` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-timeline-recording/spec.md` (clarified
2026-10-27) and the owner's planning brief: plan the recording functionality on Timeline; run
tests with low brightness and no sound from the devices; delegate simple work such as test runs
to sub-agents on cheaper models (Sonnet); add profiling to the new functionality, check the
performance and compare it with the previous results for regressions; update the Google Stitch
designs when done. The constitution is v1.3.4 (recording is feature 010, the mixer 011).

## Summary

Arming a track turns the transport's one open stream into a duplex stream: Android opens a
second Oboe input stream that the existing output callback reads non-blocking, iOS enables the
RemoteIO unit's input bus beside its output bus, so capture, playback, monitoring and the meter
share one callback and one clock. The callback measures each input block (peak, clip latch),
pushes it into an ordered lock-free FIFO and, when monitoring, sums it through a dedicated strip
into the master bus; a "tembra-recorder" thread drains the FIFO into a crash-safe 24-bit WAV
(header refreshed every second) and publishes 256-frame peak buckets the Kotlin side polls at
10 Hz into a live overview for the growing clip. A take starts as a transport pre-roll (the
count-in: the clock runs from `start − N bars` with the click forced on and no clip rendered)
and begins capture sample-accurately on the downbeat; the first `input + output latency` frames
are discarded so the clip starts on the beat, with both latencies taken from Oboe's timestamps
and `AVAudioSession`. When the take ends, the file is hashed, renamed and overviewed exactly as
an import, and one `RecordTake` edit adds the asset, inserts the clip and trims the clips it
covers as one undo step; a manifest beside the staging file lets a crashed take be adopted on
the next open. A `RecordingController` beside `ProjectSession` runs a pure `RecordingPolicy`
table over the permission, route, interruption and leave events; `TimelineViewModel` derives the
arm toggle, the meter, Record, the count-in, the monitoring toggle and the FR-018 availability
map. Record sits in the transport bar from 432 dp of bar width (readout stacked below 600 dp)
and in the armed track's compact header below it, the owner's fallback, because eight 44 dp
targets cannot share a 360 dp bar with a readout. Seven sections, four counters, ten anchors,
a `recording` journey and the extended `stress` journey make the feature measurable; the
labelled session's report is the regression comparison the owner asked for. Technology
evidence: [research.md](research.md) R1–R21.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (Multiplatform; Kotlin/Native for iOS); C++17 for the
engine (capture, recorder, WAV writer, monitor strip, pre-roll); Swift for the iOS journey;
Python for the pipeline tables and the alignment measurement script.

**Primary Dependencies** (research R21): none added. Already present and used: Oboe 1.11.0
(a second input stream, `calculateLatencyMillis` on both), the RemoteIO shell (bus 1), miniaudio
0.11.25 decoders (reading takes back), dr_wav (test fixtures only; the engine writes its own
header), Compose Multiplatform 1.12.1, lifecycle, Navigation 3, Metro, DataStore (one new key),
Okio, `kotlinx.serialization` (the manifest and the new document field), Catch2 3.16. Platform:
Android `AudioManager` device enumeration and `AudioDeviceCallback`, the existing foreground
service with the `microphone` type added; iOS `AVAudioSession` `playAndRecord` with
`[.allowBluetoothA2DP, .defaultToSpeaker]`, route-change notifications, the `audio` background
mode already declared.

**Storage**: `AppDirectories.documents/projects/<id>/audio/.take-<uuid>.wav` + `.json` while a
take runs; `audio/<sha256>.wav` and `<cache>/<sha256>.peaks` after adoption (feature 009's
layout); `project.json` gains `countIn` (format version 1, optional field); the DataStore key
`timeline.monitorInput`.

**Testing**: Catch2 on the host for the WAV writer, the peak ring, the capture source, the
recorder, the monitor strip, the renderer's pre-roll, capture start, muting, monitor mix and
cancel paths, the engine state rules and the health counters; `kotlin.test` +
`kotlinx-coroutines-test` in `commonTest` (JVM and simulator) for the policy table, the
controller, the editor's `RecordTake`, the manifest, the adopter, the live overview, the
repository's recovery, the ViewModel's availability map and the document golden;
`runComposeUiTest` in `uiTest` for every component and the screens at 360, 432, 599, 600, 840
and 1024 dp; device tests for the real capture path, the permission and the routes; `uv run
pytest` for the pipeline; the manual device table and the loopback alignment in
[quickstart.md](quickstart.md) §7–§8.

**Target Platform**: Android 8.0+ phones, foldables, tablets and desktop-mode windows (the
`microphone` foreground-service type and `FOREGROUND_SERVICE_MICROPHONE` for API 34+); iOS 16+
iPhone and iPad; the input at the stream rate (48 kHz requested on Android, the hardware rate on
iOS), one or two channels; reference devices Pixel 6 Pro, Pixel 7 Pro, iPhone 14.

**Project Type**: mobile app; C++ in `engine/`, the seam in `:shared:engine`, the policy and
the edit in `:shared:core`, storage in `:shared:data`, the controller and the UI in
`:shared:ui`, the service and the route monitor binding in `:androidApp`, the iOS route monitor
in `iosMain`, journeys in `perf-benchmarks` and `TembraPerfTests`.

**Performance Goals** (spec SC-001 to SC-011): takes aligned within 10 ms on the reference
devices; capture within 100 ms of the press and the clip within a frame; stop within 10 ms,
finalised within 1 s; a 30-minute take beside eight playing tracks with zero input overruns and
zero underruns; monitoring under 30 ms on the reference devices; the meter within 50 ms and
within 300 ms of arming; the live clip updated at 10 Hz with no per-frame recomposition; the
startup family unchanged within the gate.

**Constraints**: Principle I (the callback reads the input, measures, pushes and mixes; the
recorder thread owns the file; nothing Kotlin on either); the input open only while armed
(FR-003; App Store); the built-in microphone with wireless headphones (FR-002); monitoring never
on the built-in speaker (FR-017); the document the only authority (the renderer mutes, the edit
trims); takes lossless (FR-024); the 30-minute and free-space limits (FR-025); tokens only; one
type per file; zero warnings; Kotlin tests never link the engine; the owner's run conditions on
every device (brightness floor, silence, black window) with the loopback check as the one
sounding step; iOS arming while playing restarts the unit (research R3, a measured platform
limit against FR-001's "without interrupting playback").

**Scale/Scope**: about 14 new C++ files (capture source, peak ring, WAV writer, take recorder,
monitor strip, their headers; `transport_renderer`, `engine_core`, both device shells, the JNI
shim, `fake_audio_device` and seven Catch2 files extended), ABI revision 7 with 10 functions and
5 structs; about 60 new Kotlin files (core 12: `CountIn`, its serializer, `RecordTake`,
`RecordingState`/`Event`/`Effect`/`Transition`/`Policy`, `MonitorState`, `TakeStopReason`;
engine 10: results, config, snapshots, buckets, events; data 8: manifest, adopter, live
overview, free space, results; ui 30: controller, UI states, six components, copy, tags,
anchors, telemetry, route monitor + two bindings) and about 25 files extended; two Swift journey
files; one Python script; ~30 new test classes; pipeline tables in nine Python files; the
regenerated baseline profile; catalogue rows; README rows; the Stitch upload of eight states.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | The callback's new work is bounded: one non-blocking read (Android) or `AudioUnitRender` (iOS), a per-channel peak, an `SpscFifo` push, a strip and a bus add; the recorder thread owns conversion, the file, the header refresh, the free-space check and the peak ring; commands travel as atomics and the existing TripleBuffers; the capture snapshot is a seqlock; the new RT files join the poison list; one engine, both shells (research R1–R12; contracts/engine-c-abi-v7.md). | PASS |
| II. Shared Kotlin Control Plane, Compose UI | The policy and the edit in `:shared:core`; the seam in `:shared:engine`; staging, adoption and the live overview in `:shared:data`; the controller, the ViewModel additions and every composable in `:shared:ui` `commonMain`; the meter, the Record pulse and the growing clip read `InputTelemetry`/`CaptureSnapshot` in draw lambdas only, the 10 Hz mirror serves semantics; `AudioRouteMonitor` is an interface with platform bindings, no new expect/actual. | PASS |
| III. Test-First | Every contract lists its failing tests first; timing proven offline (pre-roll, capture start, alignment discard, muting, monitor mix, gapless recorder); the policy as a table; the adopter and recovery on a fake filesystem; the screens with scripted telemetry; capture on devices; Kotlin tests use `FakeAudioEngine` (extended with a take writer). | PASS |
| IV. Adaptive, Input-Agnostic UI | Record's placement by bar width with every target ≥ 44 dp (research R17); the meter in both header forms without a height change; hover, tooltips, focus, "A"/"R"/"I"/Escape, traversal order; semantics on every new control, the live clip and the meter; tokens only; screenshots at six widths plus landscape, fold postures, the iPad and the Duo (contracts/timeline-recording-ui.md; quickstart §9). | PASS |
| V. Document Model in Beats | The take's start is beats through the tempo map; `countIn` is a document field with a default; `RecordTake` is a pure edit and one history snapshot; the renderer only mutes, the document decides; the format stays version 1 and forward-compatible (research R13). | PASS |
| VI. Simplicity | No dependency added; the engine writes a 44-byte header instead of adopting an encoder; no take lanes, picker, offset control or calibration; the live overview is a ring and a list, not a second file format; the exclusions of FR-034 honoured. The Record placement is the owner's named fallback where the chosen layout is impossible (Complexity #1). | PASS |
| VII. Code Organisation | One type per file; packages `core.recording`, `core.project` (edit), `engine` (+ `perf`), `data.projects`, `ui.session`, `ui.timeline.components`, `ui.transport`, `ui.platform`; C++ under `engine/src/capture` and `engine/src/transport`; `internal` by default. | PASS |
| VIII. Skill-Guided Kotlin and Compose | Routed through `using-chrisbanes-skills` on 2026-10-27; the plan's choices cite `kotlin-concurrency-and-flow` (the controller's five named launch sites on the session scope, no `init` launches, `StateFlow` for `RecordingUi`, the session's `Channel` for messages, the 10 Hz poll and the per-frame read documented as hot/no-replay — contracts/recording-session.md), `kotlin-api-design` (`CountIn`, `TakeStopReason`, `RecordingState` as closed types; `RecordingPolicy` owns the transitions; `TakeManifest` parses itself; `AudioRouteMonitor` as an interface with bindings), `kotlin-control-flow` (exhaustive `when` over `RecordingEvent`, `RecordingEffect`, `TakeStopReason`, `ProjectEdit`'s new case, no `else`), `compose-state-and-effects` (wiring unchanged, content composables take immutable state and callbacks, effects keyed by `InputTelemetry`), `compose-component-design` (`modifier` first and on the root for `ArmButton`, `InputMeter`, `RecordButton`, `MonitorToggle`, `CountInMenu`; primitives and callbacks, no shape flags), `compose-performance` (draw-phase reads only; immutable `RecordingUi`/`LiveTakeUi`; the live overview replaced, never mutated), `compose-focus-navigation` (the three keys, Escape, the traversal order), `compose-ui-testing-patterns` (scripted input telemetry, injected interaction sources, frozen clock, semantics first, keyboard tests declared per lane). Every Gradle command runs through `gradle-run`. | PASS |
| Technology & Architecture Constraints | KMP + Compose, multiplatform ViewModel, Navigation 3 untouched, Metro, DataStore (one key), `kotlinx.serialization` (manifest, field), Okio; engine C++ with a C ABI, Oboe/RemoteIO; nothing forbidden; design tokens from `DESIGN.md`/`theme.json` (four added with evidence); the transport bar on Timeline and the track settings screen only; the Metronome destination untouched, its click reused for the count-in, never duplicated (R10); product name unchanged. | PASS |
| Development Workflow & Quality Gates | Build order (v1.3.4): … audio spine with transport and timeline (009), **recording (010)**, mixer (011), effects … — this plan implements exactly step 010 and pulls nothing forward (no pan, playback meters, faders, take lanes, effects). Instrumentation: seven sections, four counters, ten anchors, the `recording` journey, the extended `stress`, the regenerated profile, the labelled sessions after the merge with the report read as the regression comparison (contracts/perf-instrumentation.md). Definition of done maps to the lanes, the screenshots on both platforms, the compose report, the device table, the loopback check and the Stitch sync (quickstart §3, §7–§11). | PASS |

**Post-design re-check (after Phase 1)**: unchanged. Two deliberate choices a reviewer could
read as deviations are recorded in Complexity Tracking; one spec statement is affected by a
platform limit (iOS arming during playback restarts the audio unit, research R3) and is
measured rather than silently accepted. No spec change was made during planning; the owner's
confirmation of the Record placement below 432 dp (Complexity #1) is requested with this plan.

## Project Structure

### Documentation (this feature)

```text
specs/010-timeline-recording/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R21 with evidence (the code maps, Oboe and Apple references, the Android FGS rules)
├── data-model.md        # Phase 1: document, policy, engine seam, platform seams, storage, session/UI state, C++ structures, tokens, perf names
├── quickstart.md        # Phase 1: run conditions, lanes, every user story, the device table, the loopback check, adaptive checks, performance, the Stitch sync
├── contracts/
│   ├── engine-c-abi-v7.md           # arm/disarm, monitor, takes, telemetry, renderer rules, threading, bindings, the Kotlin seam, Catch2 tests
│   ├── recording-session.md         # RecordingController, the policy table, FR-018 availability, permission, background, the dialog, messages, tests
│   ├── take-storage.md              # staging files, manifest, adoption, crash recovery, free space, the live overview, preferences, tests
│   ├── timeline-recording-ui.md     # components, layout rules by bar width, keyboard, semantics and anchors, copy, design conformance, tests
│   └── perf-instrumentation.md      # catalogue, anchors, the recording journey, stress extension, baseline profile, the labelled session and comparison
├── checklists/requirements.md
├── evidence/            # created during implementation: alignment.md, profile.md, perf-session.md, compose report
├── screenshots/         # created during implementation (quickstart §9) with NOTES.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── README.md                                        + rows: capture, recorder, recording session, the device-lane launcher
├── tools/gradlew-device.sh (new), measure-take-alignment.py (new), fetch-material-symbols.sh (run: headphones)
├── tracepulse.yml                                   journeys + recording
├── engine/
│   ├── include/tembra_engine.h                      ABI 7 (contracts/engine-c-abi-v7.md); tembra_trace_names.h regenerated (36)
│   ├── CMakeLists.txt                               + src/capture/*
│   ├── src/engine_info.cpp                          version 0.6.0, ABI 7
│   ├── src/core/engine_core.{h,cpp}                 + arm/disarm, monitor, record start/cancel/stop, capture read, determineAlignment, duplex render entry
│   ├── src/core/audio_health.{h,cpp}                + input_overruns, record_backlog, input_xruns
│   ├── src/capture/capture_source.{h,cpp}, peak_ring.h, wav_writer.{h,cpp}, take_recorder.{h,cpp}, capture_record.h, capture_abi.cpp
│   ├── src/mixer/monitor_strip.{h,cpp}
│   ├── src/transport/transport_renderer.{h,cpp}     pre-roll, capture start split, muting, monitor mix, count_in_beats_left
│   ├── src/transport/transport_clock.h              pre-roll start (negative positions)
│   ├── src/platform/audio_device.h                  + openInputBeside, closeInput, inputLatencyNanos, inputChannels
│   ├── src/platform/android/oboe_audio_device.{h,cpp}   second input stream, non-blocking read in the output callback, input reopen, both latencies
│   ├── src/platform/apple/remote_io_audio_device.{h,cpp} openDuplex (bus 1 + bus 0), AudioUnitRender in renderProc, restart on arm/disarm
│   ├── android/src/main/cpp/tembra_jni.cpp          + nativeTransportArm/Disarm/SetMonitor/RecordStart/RecordCancel/RecordStop, nativeCapture*, nativeStorageFreeBytes
│   └── tests/                                       + wav_writer_test, peak_ring_test, capture_source_test, take_recorder_test, monitor_strip_test;
│                                                      transport_renderer_test, engine_core_state_test, audio_health_test, engine_info_test, trace_shim_test, trace_poison, fake_audio_device extended
├── shared/core/src/commonMain/kotlin/audio/tembra/core/
│   ├── project/ Project.kt (+ countIn), CountIn.kt, CountInSerializer.kt, RecordTake.kt, SetCountIn.kt, ProjectEditor.kt (+ recordTake), Track.kt (+ nextTakeName), EditRejection.kt (+2), ProjectDerived.kt (+ clipsOverlapping)
│   └── recording/ RecordingState.kt, RecordingEvent.kt, RecordingEffect.kt, RecordingTransition.kt, RecordingPolicy.kt, MonitorState.kt, TakeStopReason.kt
│   └── commonTest/… one test per type; ProjectEditorRecordTakeTest; the demo golden updated
├── shared/engine/
│   ├── perf-catalogue.txt                           + 7 sections, 4 counters
│   ├── src/commonMain/kotlin/audio/tembra/engine/   TimelineAudio.kt (+10), ArmResult.kt, RecordConfig.kt, RecordStartResult.kt, TakeInfo.kt, CaptureSnapshot.kt, PeakBuckets.kt,
│   │                                                 TransportSnapshot.kt (+2), AudioEvent.kt (+2), perf/AudioHealth.kt (+3), PerfSection.kt, PerfCounter.kt, AudioHealthPublisher.kt, FakeAudioEngine.kt (+ capture)
│   ├── src/androidMain/…/NativeAudioEngine.android.kt (+ natives, PeakBucketBuffer.kt, route events), src/iosMain/…/NativeAudioEngine.ios.kt (+ calls), IosAudioSession.kt (+ activateForRecording, latencies)
│   └── tests: commonTest FakeAudioEngineTest (+ capture), PerfSectionCatalogueTest; androidDeviceTest + iosTest NativeAudioEngineRecordingTest
├── shared/data/src/commonMain/kotlin/audio/tembra/data/
│   ├── projects/ ProjectFolder.kt (+2 paths), TakeManifest.kt, TakeAdopter.kt, AdoptedTake.kt, LiveOverview.kt, TakeFreeSpace.kt, ProjectRepository.kt (+ pendingTakes, discardTake), OkioProjectRepository.kt, FakeProjectRepository.kt
│   ├── timeline/ TimelinePreferences.kt (+ monitorInput), TimelinePreferenceKeys.kt, DataStoreTimelinePreferences.kt, FakeTimelinePreferences.kt
│   └── tests: TakeManifestTest, TakeAdopterTest, LiveOverviewTest, OkioProjectRepositoryTest (+ recovery), DataStoreTimelinePreferencesTest
├── shared/ui/
│   ├── src/commonMain/composeResources/drawable/ic_headphones_24.xml
│   ├── src/commonMain/kotlin/audio/tembra/ui/
│   │   ├── di/AppBindings.kt (+ RecordingController, AudioRouteMonitor), di/AppGraph.kt (+ routeMonitor)
│   │   ├── session/ RecordingController.kt, RecordingUi.kt, LiveTakeUi.kt, InputTelemetry.kt, ProjectSession.kt (+ forwarding, RecordTake, confirmLeave variant), SessionState.kt (+ recording), SessionMessage.kt (+4), DialogRequest.kt (+ recording), AppLifecycleObserver.kt (+ resume)
│   │   ├── platform/ AudioRouteMonitor.kt, AudioRoute.kt
│   │   ├── theme/ DawColors.kt (+4 tokens), TembraIcon.kt (+ Headphones)
│   │   ├── designsystem/ StopAndLeaveDialog.kt (+ variant)
│   │   ├── timeline/ TimelineViewModel.kt (+ arm/record/monitor/countIn/resetClip/requestMicrophone, availability), TimelineUiState.kt, TrackUi.kt, TimelineCallbacks.kt, TimelineCopy.kt, TimelineTestTags.kt, TimelineKeyHandling.kt (+ A, R, I, Escape), TimelineScreen.kt, TimelineScreenContent.kt
│   │   ├── timeline/components/ ArmButton.kt, InputMeter.kt, MonitorToggle.kt, CountInMenu.kt, TrackHeaderCompact.kt, TrackHeader.kt, TrackRow.kt, TrackLane.kt, ClipPainter.kt (+ live take), TimelineToolbar.kt, TrackMenu.kt, MetronomeSettingsSheet.kt
│   │   ├── transport/ RecordButton.kt, RecordButtonState.kt, TransportBar.kt (+ Record, RecordInBarFrom 432), TimeReadout.kt (+ count-in), TransportUiState.kt, TransportCallbacks.kt, TransportCopy.kt
│   │   ├── tracksettings/ TrackSettingsScreen.kt, TrackSettingsScreenContent.kt (+ callbacks), TrackSettingsViewModel.kt (FR-018 delete gate)
│   │   ├── projects/ProjectsViewModel.kt (open through the guard)
│   │   ├── navigation/NavigationShellContent.kt (+ dialog variant)
│   │   └── perf/PerfAnchors.kt (+10), EngineHealthJson.kt (+4), AudioHealthTicker.kt (+4), TraceFirstMeasure.kt (+ traceFirstDraw)
│   ├── src/iosMain/kotlin/audio/tembra/ui/ IosAppGraph.kt (+ route monitor), platform/IosAudioRouteMonitor.kt
│   ├── src/commonTest/…/ core.recording tests live in core; session/RecordingControllerTest, ProjectSessionTest (+), timeline/TimelineViewModelTest (+), TimelineUiStateTest, platform/FakeAudioRouteMonitor.kt, theme/TimelineTokensTest (+), perf/PerfAnchorsCatalogueTest (46)
│   └── src/uiTest/…/ timeline/components/{ArmButtonTest,InputMeterTest,MonitorToggleTest,CountInMenuTest}, transport/{RecordButtonTest,TransportBarTest+,TimeReadoutTest+}, timeline/{TrackHeaderCompactTest+,TrackHeaderTest+,TrackLaneTest+,TimelineScreenTest+,TimelineKeyHandlingTest+,ScriptedInputTelemetry}, designsystem/StopAndLeaveDialogTest+
├── androidApp/
│   ├── src/main/AndroidManifest.xml                 + FOREGROUND_SERVICE_MICROPHONE; service foregroundServiceType mediaPlayback|microphone
│   ├── src/main/kotlin/audio/tembra/android/ PlaybackForegroundService.kt (+ type, recording notification), AndroidAudioRouteMonitor.kt, AndroidAppGraph.kt (+)
│   ├── src/main/generated/baselineProfiles/{baseline-prof,startup-prof}.txt   regenerated
│   └── src/androidTest/…/AndroidMicrophonePermissionTest.kt (+), AndroidAudioRouteMonitorTest.kt
├── iosApp/
│   ├── iosApp/Info.plist                            NSMicrophoneUsageDescription (FR-019)
│   ├── TembraPerfTests/Anchors.swift (+10), Metrics.swift (+ recording), Journeys/RecordingJourney.swift, RecordingPerfTests.swift, Journeys/StressJourney.swift (extended), ScreenshotMatrix.swift (+ states), iosApp.xcodeproj
├── perf-benchmarks/src/main/kotlin/audio/tembra/perf/ journeys/Anchors.kt (+10), journeys/RecordingJourney.kt, RecordingJourneyBenchmark.kt, journeys/StressJourney.kt (extended), PerfMetrics.kt (+ recording), BaselineProfileGenerator.kt (+), ProfileComparisonBenchmark.kt (+ recording)
├── perf/tracepulse/ config.py, catalogue.py (+ journey, +4 audio rows), orchestrate_android.py, collect_android.py, collect_ios.py, report.py, traces_android.py, schema/*.json; tests/*; README.md
└── design/stitch/ theme.json (+4 tokens), DESIGN.md (meter and record prose), screens.json (+8 uploaded screens), screens/timeline-recording/* (re-pulled)
```

**Structure Decision**: no new Gradle module. The pure parts (the edit, the policy, the count-in
type) go to `:shared:core`; the ABI and the seam to `engine/` and `:shared:engine` (the only code
that touches audio); staging, adoption and the live overview to `:shared:data` beside the
importer whose pipeline they reuse; the controller to `ui.session` beside the session it
serves, the components to the packages of the screens they extend. The route monitor follows
feature 009's picker pattern: an interface in `ui.platform`, the Android binding in
`:androidApp` (it needs `AudioManager`), the iOS binding in `iosMain` of `:shared:ui`. The
journeys live where feature 007 put them. Two small tools join `app/tools/` so sub-agents and
the owner can run device lanes and the alignment measurement from the repository.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| #1 Record is in the armed track's compact header below 432 dp of bar width, not in the transport bar as FR-007 and clarification 4 chose | Eight 44 dp targets are 352 dp before any gap or readout; the bar's readout budget at 360 dp falls from 80 dp to 34 dp with one more target, and a stacked "00:12.40" needs about 52 dp (research R17). Constitution IV forbids smaller targets. The owner named this placement as the fallback to try after a manual check; the plan applies it only where the arithmetic leaves no other option and keeps Record in the bar from 432 dp. The label chip hides while a compact header is armed so the name keeps 72 dp. | Dropping the one-bar step buttons on phones (option C) removes transport controls the owner kept; a position-only readout (option B) hides the length the owner kept; a floating round Record button is a pattern the design does not show (kept as the first candidate if the owner dislikes the header). |
| #2 A `RecordingController` beside `ProjectSession` instead of growing the session (Principle VI "build only what the spec requires"; 009's Complexity #3 already justified one shared owner) | The session is 700 lines; recording adds a state machine with nineteen events, five launch sites, the permission, the routes and the adoption. One collaborator with one job keeps each launch site's owner visible (`kotlin-concurrency-and-flow`) and the policy testable as a table; the session stays the only object screens' ViewModels see. | Inlining it makes `ProjectSession` the second thousand-line class and mixes two state machines in one `when`. |
| #3 The iOS audio unit restarts when a track is armed, which can gap the output while a project plays (FR-001 says arming "MUST NOT interrupt playback") | `EnableIO` on a RemoteIO bus can only change on an uninitialised unit; keeping the input bus enabled whenever the transport is open would hold the microphone open with no track armed, which FR-003 forbids and the research report §8 flags for App Store review. The gap is measured (quickstart §7) and recorded; the transport clock and the document are unaffected. | A second audio unit for input (two clocks, no shared callback, alignment and monitoring break); `AVAudioEngine` (a second I/O shell). |

## Workflow notes carried into `/speckit-tasks` (the owner's brief)

- **Device runs**: every lane, capture and perf round on a device applies quickstart §2 first
  (brightness floor, music stream and notifications silent, the black test window); the loopback
  alignment check of §8 is the one sounding step and is run with a cable where the device allows
  it, announced before it starts.
- **Delegation**: lane runs per target, screenshot captures and per-device perf rounds are
  tasks for Sonnet-class sub-agents (one target each, through `gradle-run` and
  `tools/gradlew-device.sh`, reporting exact failing test names); engine, renderer, session and
  policy code and every review stay with the main agent; the sub-agent split for component work
  follows feature 009's D1 pattern (disjoint files, detached worktrees, one integrator).
- **Profiling and the regression comparison**: the seven sections, four counters and the
  `recording` journey are implemented with the feature (not after); a trial round proves them;
  after the merge the labelled sessions on every device feed `tracepulse report`, whose "last
  session" onsets and gate notices against the previous rounds are read and either explained or
  fixed before the pull request closes (contracts/perf-instrumentation.md).
- **Stitch**: the eight new states are uploaded to the Tembra Audio Stitch project and the
  export re-pulled as the last implementation task (quickstart §11).
