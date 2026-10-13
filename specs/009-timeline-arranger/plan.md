# Implementation Plan: Timeline Arranger

**Branch**: `009-timeline-arranger` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-timeline-arranger/spec.md` (clarified
2026-10-13) and the user's planning brief: record, add, split and move tracks; the metronome,
mixer, track settings and effects placeholder; abstractions and data models that save to a
server ("for instance using FlatBuffers"); the designs for tracks and waveforms; tests and
performance verified with the pipeline; the app's design schema and colours; adaptive UI tested
on every attached device and simulator; calls, other apps taking the audio and the screen going
off; a confirmation when leaving the tab; separate components and separation of concerns; own UI
controls; the best way to work with audio and whether to copy files; metronome reuse; effects
applicable to a track later. Recording and the mixer sheet are features 011 and 010 by the
spec's first clarification and the constitution's amended build order (v1.3.1); this plan
builds the seams they attach to (research R1) and none of their user-facing surface.

## Summary

Timeline becomes the arranger and Projects the library. One C++ transport inside the existing
engine core plays every clip of a project together: a `TransportClock` in frames, a one-entry
`TempoMap` that turns clip starts in beats into frames at render time, clip sources decoded on
a reader thread straight from the user's original WAV, FLAC or MP3 copies (miniaudio's bundled
decoders, no decoded cache) into per-clip lock-free fifos, per-track strips (10 ms gain ramps,
audibility from the solo/mute table computed in Kotlin) summed by a master bus, and the
metronome's existing scheduler anchored to the transport so the project tempo drives the same
click the Metronome destination owns. The playhead, ruler chip and readout read one seqlock
snapshot per display frame. The project is an immutable, versioned, `@Serializable` document in
`:shared:core` (stable ids, beats, audio by SHA-256 content id, nothing device-specific) edited
by pure functions with a 50-step snapshot history, saved as readable JSON through
`kotlinx.serialization` with a two-second autosave into per-project folders under the app's
documents directory, indexed for the library by one JSON index; FlatBuffers is not usable from
Kotlin/Native, so the same classes serialise to ProtoBuf or CBOR when the server arrives. A
singleton `ProjectSession` owns the open project, its history, autosave, the engine binding and
the playback policy (calls, another app's audio, lost routes pause and never resume; the screen
going off changes nothing); thin ViewModels per destination map it into immutable UI states;
the shell asks the session before leaving Timeline while playing (the stop-and-leave dialog).
The UI is Compose Multiplatform throughout: a Canvas ruler and lazy Canvas lanes drawing peak-mip
waveform overviews built at import, a playhead overlay, in-house transport, header, clip, gain,
card and chart components from the design tokens, with four adaptive layouts, keyboard, pointer
and screen-reader support. Everything is test-first on the four lanes; one new `timeline`
benchmark journey, nine new catalogue sections, three audio counters and a regenerated baseline
profile make it measurable. Technology evidence: [research.md](research.md) R1–R19.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform; Kotlin/Native for iOS); C++17 for the
engine (transport, streaming, mixer, decoding, overviews); Swift only for the iOS perf journey
and picker glue; Python for the perf pipeline tables and the fixture/demo scripts.

**Primary Dependencies** (research R19): added — the `kotlinx.serialization` Gradle plugin and
`kotlinx-serialization-json` 1.11.0 in `:shared:core` and `:shared:data` (the JSON artefact is
already in the catalogue for a test), `kotlinx-collections-immutable` 0.4.0 in `:shared:core`;
enabled — miniaudio 0.11.25's WAV/FLAC/MP3 decoders and resampler (already vendored; the
`MA_NO_DECODING`/`MA_NO_WAV`/`MA_NO_FLAC`/`MA_NO_MP3` defines are dropped). Already present and
used: Oboe 1.11.0, dr_wav, Compose Multiplatform 1.12.1, Material 3 1.9.0 and adaptive 1.2.0,
lifecycle 2.11.0, Navigation 3 1.1.2, Metro 1.4.4, DataStore 1.2.1, Okio 3.9.1 (+
`okio-fakefilesystem` in tests), Catch2 3.16. Platform: Android `OpenDocument`/
`OpenMultipleDocuments`, the existing `mediaPlayback` foreground service (renamed), audio focus;
iOS `UIDocumentPickerViewController` with multiple selection, security-scoped access, the
`.playback` session and `audio` background mode (already declared). No Room (Complexity #1),
no FlatBuffers (R8), no Turbine.

**Storage**: `AppDirectories.documents/projects/<id>/project.json` + `view.json` (device-side zoom
and grid) + `audio/<sha256>.<ext>` (byte-for-byte originals); `documents/projects/library.json` index;
`AppDirectories.cache/projects/<id>/<sha256>.peaks` overviews (rebuildable); the metronome's
DataStore preferences are read, not changed; the demo audio ships as Compose resources.

**Testing**: Catch2 on the host for the tempo map, clock, fifo, streamer, clip scheduling,
strips, bus, decoder matrix, overview builder and engine state; `kotlin.test` +
`kotlinx-coroutines-test` in `commonTest` (JVM and iOS simulator) for the document, edits,
history, policy, repository on a fake filesystem, session, ViewModels, geometry, layouts and
tokens; `runComposeUiTest` in `uiTest` (simulator and device) for the three screens and every
component; `androidDeviceTest`/`iosTest` for the engine round trip, pickers, files and the
service host; `uv run pytest` for the pipeline; manual checks for calls, other apps, routes,
lock screen and the playhead against the audible output ([quickstart.md](quickstart.md)).

**Target Platform**: Android 8.0+ phones, foldables, tablets and desktop-mode windows; iOS 16+
iPhone and iPad; output at the device rate (48 kHz requested on Android, hardware rate on iOS);
reference performance devices Pixel 6 Pro, Pixel 7 Pro and iPhone 14.

**Project Type**: mobile app; shared Compose UI in `:shared:ui`, document and geometry in
`:shared:core`, engine facade in `:shared:engine`, storage in `:shared:data`, C++ in `engine/`,
pickers and the service in the apps, benchmark journeys in `perf-benchmarks` and
`TembraPerfTests`.

**Performance Goals** (spec SC-001 to SC-010, SC-014): zero dropouts and zero starvation with
eight simultaneous clips for ten minutes; play within 100 ms, seek resume within 100 ms, pause
silent within 10 ms; clip starts within 1 ms and the click within 1 ms of its beat; playhead
within 30 ms of the audible position at the display frame rate; a 5-minute WAV's overview within
3 s, a 60-minute project's cached overviews within 2 s; autosave within 2 s; the 200-project
library within 1 s and search within a frame; the callback allocation-, lock- and I/O-free; no
per-frame recomposition (draw-phase telemetry reads only).

**Constraints**: Principle I (nothing Kotlin on the callback; the streamer and the control
thread are the only non-RT engine threads); at most one stream (TRANSPORT excludes the tuner,
tones and the standalone metronome); originals kept and played, caches rebuildable
(clarification 2); the document carries no path (FR-049); tokens only; one type per file, no
composable nested in another's file; zero warnings; Kotlin tests never link the engine; the
transport bar on Timeline and the track settings screen only; playback stops when leaving
Timeline after the dialog, continues under screen lock and backgrounding, never resumes by
itself after a call, another app or a route loss; bounded arrangement (64 tracks, 1024 clips).

**Scale/Scope**: about 30 new C++ files (three modules, ABI revision 6 with 15 functions and
7 structs, 13 Catch2 files); about 150 new Kotlin files across the four shared modules and the
apps (core 35, engine 12, data 18, ui 80, platform 8); two Swift journey files; ~20 vector
assets; ~50 new test classes on the JVM/iOS lanes, ~18 UI test classes, ~8 device test classes;
pipeline tables in nine Python files plus tests; the regenerated baseline profile; catalogue and
licence rows; README rows.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | The callback loads snapshots from `TripleBuffer`s and atomics, pops pre-decoded frames from per-clip `SpscFifo`s, runs bounded arithmetic (tempo map, ramps, sum, clamp), renders the already-prepared click voice and publishes a `Seqlock`; decoding, priming, file I/O and overview building run on the streamer and IO threads; no callback into Kotlin; new RT sources are in the poison list; one engine serves both platforms through the existing Oboe and RemoteIO shells with variable frame counts (research R2–R7; contracts/engine-c-abi-v6.md). | PASS |
| II. Shared Kotlin Control Plane, Compose UI | Document, edits, history, tempo map and geometry in `:shared:core`; the `TimelineAudio` seam and snapshots in `:shared:engine`; repository, index and import in `:shared:data`; session, ViewModels and screens in `:shared:ui` `commonMain`; dependencies inward. One ViewModel per destination with one immutable `UiState` in a `StateFlow`, intents as plain functions; screens never touch the engine — the playhead reads `TransportTelemetry` in draw lambdas only, semantics poll at 10 Hz (R7, R13). `expect`/`actual` unchanged (`NativeAudioEngine`); the picker, host and directories are interfaces bound per platform. | PASS |
| III. Test-First | Every contract lists its failing tests before code; timing proven offline (clip starts, split join, anchored click), the document round trip and portability on both lanes, the session against fakes, the screens with scripted telemetry, the engine round trip on devices; Kotlin tests use `FakeAudioEngine` (extended) and never the engine (R17). | PASS |
| IV. Adaptive, Input-Agnostic UI | Four layouts from the window size class, height and posture; pinned headers and ruler; hover, tooltips, focus rings, the full keyboard map (FR-057), wheel and trackpad scroll/zoom, 44 dp targets, semantics on every control and on clips and the playhead; tokens only with the six track tints added as tokens with evidence; screenshots at five widths plus landscape, fold postures, iPad and the Duo on both platforms (contracts/timeline-screen.md, design-system-additions.md; quickstart §9). | PASS |
| V. Document Model in Beats | The Kotlin document is the single source of truth; the engine holds a derived arrangement snapshot and is never the authority; clip starts are beats, converted by the tempo map at render time; edits are pure `ProjectEditor` functions; undo is a history of immutable snapshots; the format is versioned and forward-compatible with explicit migrations (contracts/project-document.md). | PASS |
| VI. Simplicity | Two libraries from the decided stack added, no new third party beyond enabling vendored decoders; no decoded cache; one tempo entry; no pan, meters, limiter, recording or effects fields — only the strip/bus shape they will extend (R1); own components only where Material has none (Complexity #2); exclusions of FR-064 honoured. Room deferred (Complexity #1). | PASS |
| VII. Code Organisation | One type per file, no nested public types, no composable in another's file; packages `core.project`, `core.timeline`, `engine` (+ `perf`), `data.projects`, `ui.session`, `ui.timeline` (+ `components`), `ui.transport`, `ui.tracksettings`, `ui.projects` (+ `components`), `ui.designsystem`; C++ under `engine/src/transport`, `timeline`, `mixer`; `internal` by default. | PASS |
| VIII. Skill-Guided Kotlin and Compose | Routed through `using-chrisbanes-skills` on 2026-10-13; the plan's choices cite `kotlin-concurrency-and-flow` (one app-lifetime `sessionScope` with named launch sites, no `init` launches, `StateFlow` for session and UI state, buffered `Channel` + `receiveAsFlow` for one-consumer messages and the dialog request, event delivery contract documented — contracts/project-session.md), `kotlin-api-design` (value classes `Beats`, `GainDb`, `Percent`, the ids; sealed `ProjectEdit` applied by its owner `ProjectEditor`; `TimelineAudio`, `AudioFilePicker`, `ProjectRepository` as interfaces with platform bindings, no new expect/actual), `kotlin-control-flow` (exhaustive `when` over edits, policy events and pick results, no catch-all), `compose-state-and-effects` (wiring/content/components for every screen; `LaunchedEffect(viewModel)`, `DisposableEffect(viewModel)`, overview polling keyed by asset id), `compose-component-design` (every component: `modifier` first optional on the root, slots for the ruler's left pills and the card's actions, primitives for tints and sizes), `compose-performance` (frame-rate reads in `drawBehind`/`Canvas` only, persistent collections and value classes keep `UiState` stable, no back-writes), `compose-focus-navigation` (traversal order and shortcuts), `compose-ui-testing-patterns` (content composables with controlled state, injected interaction sources, frozen clock, semantics before pixels). Every Gradle command runs through `gradle-run`. | PASS |
| Technology & Architecture Constraints | KMP + Compose, multiplatform ViewModel, Navigation 3 (a second `NavKey` for track settings), Metro, DataStore (read), `kotlinx.serialization` JSON project files, Okio, `kotlinx.collections.immutable`; engine C++ with a C ABI, Oboe/RemoteIO, miniaudio and dr_wav; nothing forbidden; design tokens from `DESIGN.md`/`theme.json`; the transport bar on the DAW screens this feature builds; the Metronome destination untouched and its sound set, models and scheduler reused, never duplicated (R4); product name unchanged. | PASS |
| Development Workflow & Quality Gates | Build order (v1.3.1): … metronome, **audio spine with transport and timeline (009)**, mixer (010), recording (011), effects … — this plan implements exactly step 009 and pulls nothing forward (no pan, meters, recording, effects, loops, speed). Instrumentation: the `timeline` journey, the extended `stress`, nine sections, three counters, anchors, the regenerated profile, the labelled sessions after merge (contracts/perf-instrumentation.md). Definition of done maps to the four lanes, screenshots on both platforms, the compose report and the licence rows (quickstart §2, §9, §10). | PASS |

**Post-design re-check (after Phase 1)**: unchanged. Two deliberate choices a reviewer could
read as deviations are recorded in Complexity Tracking. No spec change was needed during
planning; the plan's one open verification (MP3 seek-table support in the vendored miniaudio,
research R5) has a contained fallback that touches only the storage layout.

## Project Structure

### Documentation (this feature)

```text
specs/009-timeline-arranger/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R19 with evidence (surveys, NotebookLM, docs tool, brief, designs)
├── data-model.md        # Phase 1: document, edits, history, geometry, seam, storage, session/UI state, C++ structures, tokens, perf names
├── quickstart.md        # Phase 1: lanes, every user story, interruptions, adaptive and input checks, performance
├── contracts/
│   ├── engine-c-abi-v6.md           # states, transport, arrangement, clip sources, telemetry, health, offline tools, .peaks format, renderer rules, threading, bindings, Catch2 tests
│   ├── project-document.md          # format v1 guarantees, shape, evolution rules, validation, tests
│   ├── project-storage.md           # folders, ProjectRepository, import pipeline, library index, AudioFilePicker, autosave, tests
│   ├── timeline-audio-kotlin.md     # TimelineAudio seam, actuals, FakeAudioEngine, background host rename, device tests
│   ├── project-session.md           # ProjectSession collaborators, state, playback policy table, engine binding, stop-and-leave guard, messages, tests
│   ├── timeline-screen.md           # files, layouts, gestures, keyboard, semantics, copy, design conformance, tests
│   ├── projects-library-screen.md   # files, behaviour, semantics, design conformance, tests
│   ├── perf-instrumentation.md      # catalogue, anchors, the timeline journey, stress extension, baseline profile, labelled session
│   └── design-system-additions.md   # tokens, icons, new design-system components, reduce motion, tests
├── checklists/requirements.md
├── fixtures/            # created during implementation: make_fixtures.py → tiny WAV/FLAC/MP3 tones, demo-project.json golden
├── screenshots/         # created during implementation (quickstart §9) with NOTES.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── gradle/libs.versions.toml                        + kotlinx-serialization plugin, kotlinx-collections-immutable
├── THIRD_PARTY_LICENSES.md                          + kotlinx-collections-immutable row; miniaudio row notes decoders enabled
├── README.md                                        + rows: transport, clip streaming, mixer strips, project document, repository, session, timeline, projects
├── tracepulse.yml                                   journeys + timeline
├── engine/
│   ├── include/tembra_engine.h                      ABI 6 (contracts/engine-c-abi-v6.md); tembra_trace_names.h regenerated
│   ├── CMakeLists.txt                               + src/transport/*, src/timeline/*, src/mixer/*; miniaudio decoders enabled
│   ├── src/engine_info.cpp                          version 0.5.0, ABI 6
│   ├── src/core/engine_core.{h,cpp}                 + TRANSPORT state, transport renderer ownership, MasterBus in render(), streamer lifecycle
│   ├── src/core/miniaudio_impl.cpp                  MA_NO_DECODING/WAV/FLAC/MP3 removed
│   ├── src/core/audio_health.{h,cpp}                + avg_us, starvation, clips_active
│   ├── src/transport/transport_clock.h, tempo_map.h, transport_record.h, transport_renderer.{h,cpp}, transport_abi.cpp
│   ├── src/timeline/spsc_fifo.h, clip_source.{h,cpp}, arrangement_snapshot.h, disk_streamer.{h,cpp}, audio_decoder.{h,cpp}, overview_builder.{h,cpp}, timeline_abi.cpp
│   ├── src/mixer/gain_ramp.h, track_strip.{h,cpp}, master_bus.{h,cpp}
│   ├── src/metronome/metronome_abi.cpp              set_grid/select_sound allowed in TRANSPORT
│   ├── android/src/main/cpp/tembra_jni.cpp          + nativeTransport*/nativeClipSource*/nativeAudio* forwarders
│   └── tests/                                       + tempo_map_test, transport_clock_test, spsc_fifo_test, disk_streamer_test, clip_source_test, track_strip_test, master_bus_test,
│                                                      transport_renderer_test, audio_decoder_test, overview_builder_test; engine_core_state_test, engine_info_test, trace_poison updated; fixtures
├── shared/core/
│   ├── build.gradle.kts                             + serialization plugin, kotlinx-collections-immutable
│   └── src/commonMain/kotlin/audio/tembra/core/
│       ├── project/ Project.kt, ProjectDocument.kt, ProjectId.kt, TrackId.kt, ClipId.kt, AudioAssetId.kt, AudioAsset.kt, AudioFormat.kt, Track.kt, Clip.kt, Beats.kt, GainDb.kt,
│       │            Percent.kt, MusicalKey.kt, InstrumentLabel.kt, GridSize.kt, ZoomLevel.kt, TempoMap.kt, ProjectEdit.kt (+ one file per subtype with behaviour), EditResult.kt,
│       │            ProjectEditor.kt, EditHistory.kt, ProjectDerived.kt, PlaybackPolicy.kt, PlaybackState.kt, PlaybackEvent.kt, PlaybackTransition.kt, LibraryQuery.kt, LibraryFilter.kt, LibrarySort.kt
│       └── timeline/ TimelineViewport.kt, RulerLabels.kt, RulerRow.kt, Snap.kt, ClipHit.kt, TimeFormat.kt
│   └── commonTest/… one test per type above
├── shared/engine/
│   ├── perf-catalogue.txt                           + 9 sections, 3 counters; PerfFamily TRANSPORT
│   ├── src/commonMain/kotlin/audio/tembra/engine/   TimelineAudio.kt, AudioEngine.kt (+ TimelineAudio), AudioState.kt (+ Transport), ArrangementSnapshot.kt, TrackRender.kt, ClipRender.kt,
│   │                                                 ClipSourceHandle.kt, ClipSourceResult.kt, TransportOpenResult.kt, PlayResult.kt, TransportSnapshot.kt, AudioInfo.kt, AudioHealth.kt (+3), FakeAudioEngine.kt (+ transport)
│   │                                                 perf/PerfSection.kt, PerfCounter.kt, PerfFamily.kt
│   ├── src/androidMain/…/NativeAudioEngine.android.kt (+ transport natives, ArrangementBuffer.kt), AndroidAudioFocus (Gain for transport)
│   ├── src/iosMain/…/NativeAudioEngine.ios.kt (+ transport functions)
│   └── tests: commonTest FakeAudioEngineTest (+ transport), PerfSectionCatalogueTest (+ TRANSPORT); androidDeviceTest + iosTest NativeAudioEngineTransportTest, TransportTimingEndToEndTest
├── shared/data/
│   ├── build.gradle.kts                             + serialization plugin + json
│   ├── src/commonMain/kotlin/audio/tembra/data/projects/ ProjectRepository.kt, OkioProjectRepository.kt, FakeProjectRepository.kt, ProjectFolder.kt, ProjectViewState.kt, DemoAudioSource.kt, ProjectDocumentCodec.kt,
│   │                                                       ProjectDocumentMigrations.kt, ProjectValidator.kt, LibraryIndex.kt, LibraryEntry.kt, CardOverview.kt, CardOverviewBuilder.kt,
│   │                                                       OpenedProject.kt, AudioImporter.kt, ImportResult.kt, ImportRefusal.kt, PickedFile.kt, WaveformOverview.kt, WaveformOverviewReader.kt,
│   │                                                       OverviewStatus.kt, DemoProjectInstaller.kt, AutosaveScheduler.kt
│   └── tests: commonTest OkioProjectRepositoryTest, ProjectDocumentCodecTest, ProjectDocumentMigrationsTest, ProjectValidatorTest, AudioImporterTest, CardOverviewBuilderTest,
│              WaveformOverviewReaderTest, DemoProjectInstallerTest, AutosaveSchedulerTest; androidDeviceTest + iosTest ProjectRepositoryFileTest
├── shared/ui/
│   ├── src/commonMain/composeResources/drawable/ic_*.xml (script; design-system-additions.md); files/demo/{drums,bass,keys,pad}.wav (tools/make-demo-audio.py)
│   ├── src/commonMain/kotlin/audio/tembra/ui/
│   │   ├── App.kt (+ session start), di/AppBindings.kt (+ repository, session, TimelineAudio, AudioFilePicker, sessionScope), di/AppGraph.kt
│   │   ├── navigation/ ShellUiState.kt (List<NavKey>), ShellViewModel.kt (guarded select/back), TembraNavDisplay.kt (+ TrackSettingsKey entry), NavigationShellContent.kt (+ dialog host), DestinationContent.kt
│   │   ├── session/ ProjectSession.kt, SessionState.kt, SessionMessage.kt, ArrangementMapper.kt, ClipSourceRegistry.kt, OverviewTracker.kt, MetronomeBridge.kt, PlaybackGuard.kt, DialogRequest.kt, AppStartup.kt, AppLifecycleObserver.kt
│   │   ├── platform/ AudioFilePicker.kt, AudioPickRequest.kt, AudioPickResult.kt (SoundFilePicker removed; metronome call sites updated)
│   │   ├── theme/ DawColors.kt (+ trackTints and clip tokens), TembraSpacing.kt (+ timeline sizes), TembraIcon.kt (+ glyphs)
│   │   ├── designsystem/ TembraMenu.kt, TembraTooltip.kt, TembraTextField.kt, TembraDialog.kt, TembraCard.kt, StopAndLeaveDialog.kt
│   │   ├── timeline/ … (contracts/timeline-screen.md file list incl. TimelineKeyHandling.kt) and timeline/components/ …
│   │   ├── transport/ TransportBar.kt, TransportUiState.kt, TransportCallbacks.kt, TransportButton.kt, TimeReadout.kt, MasterVolumeSlider.kt, MetronomeToggle.kt
│   │   ├── tracksettings/ TrackSettingsScreen.kt, TrackSettingsViewModel.kt, TrackSettingsUiState.kt, TrackSettingsCallbacks.kt, TrackSettingsForm.kt, TrackSettingsInlineRow.kt, TrackSettingsDraft.kt, EffectsPlaceholder.kt, TrackSettingsKey.kt
│   │   ├── projects/ … (contracts/projects-library-screen.md file list incl. LibraryViewStateHolder.kt) and projects/components/ …
│   │   └── perf/PerfAnchors.kt (+ 20), EngineHealthJson.kt (+3), AudioHealthTicker.kt (+3)
│   ├── src/iosMain/kotlin/audio/tembra/ui/ IosAppGraph.kt (+ IosAudioFilePicker, repository), platform/IosAudioFilePicker.kt
│   ├── src/commonTest/…/ session/*, timeline/*, transport/*, tracksettings/*, projects/*, navigation/ShellViewModelGuardTest, platform/FakeAudioFilePicker.kt, theme/TimelineTokensTest.kt
│   ├── src/androidHostTest/…/theme/{IconAssetsTest,ThemeJsonParityTest}.kt (extended)
│   └── src/uiTest/…/ timeline/*, transport/*, tracksettings/*, projects/*, designsystem/*, navigation/NavigationShellTest (Profile placeholder)
├── androidApp/
│   ├── src/main/AndroidManifest.xml                 service class renamed PlaybackForegroundService
│   ├── src/main/kotlin/audio/tembra/android/ AndroidAppGraph.kt (+ picker, repository), MainActivity.kt (OpenDocument + OpenMultipleDocuments launchers),
│   │                                          PlaybackForegroundService.kt, AudioFilePickerHost.kt, AndroidAudioFilePicker.kt
│   ├── baseline-profile-coverage.txt                + timeline, projects
│   ├── src/main/generated/baselineProfiles/{baseline-prof,startup-prof}.txt   regenerated
│   └── src/androidTest/…/AndroidBackgroundPlaybackHostTest.kt (rename), AudioFilePickerHostTest.kt
├── iosApp/
│   ├── TembraPerfTests/Anchors.swift (+), Metrics.swift (+ category), Journeys/TimelineJourney.swift, TimelinePerfTests.swift, Journeys/StressJourney.swift (extended), iosApp.xcodeproj (pbxproj)
├── perf-benchmarks/src/main/kotlin/audio/tembra/perf/ journeys/Anchors.kt (+), journeys/TimelineJourney.kt, TimelineJourneyBenchmark.kt, journeys/StressJourney.kt (extended),
│                                                        PerfMetrics.kt (+ timeline), BaselineProfileGenerator.kt (+), ProfileComparisonBenchmark.kt (+ timeline)
├── perf/tracepulse/ config.py, catalogue.py (+ journey, +3 audio rows), orchestrate_android.py, collect_android.py, collect_ios.py, report.py, traces_android.py; tests/*; README.md
└── tools/ make-demo-audio.py (new), fetch-material-symbols.sh (run for the new glyphs)
```

**Structure Decision**: no new Gradle module. The document and every pure rule go to
`:shared:core` so the engine seam, the data layer and the UI all depend on one vocabulary; the
C ABI and the Kotlin seam to `engine/` and `:shared:engine` (the only code that touches audio);
files, the index and import to `:shared:data`; the session and the three features to
`:shared:ui`, each in its own package with its components beside it, and generic pieces in
`designsystem` only once two features need them. The Android picker host and the renamed service
stay in `:androidApp` (activity result registry, manifest); the iOS picker in `iosMain` of
`:shared:ui`, as feature 004 did. The perf journeys live where feature 007 put them.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| #1 The library index is a JSON file (`library.json`) although the constitution's stack names Room KMP "for the library index" | Two hundred entries are read in one pass well inside SC-010; the only operations are list, filter by date, sort and substring search, all pure in-memory functions already needed for the UI; Room would add KSP, a schema and migrations for no query the feature runs. Room remains the decided answer when the index needs queries (sync state, collaboration); the index is an isolated `LibraryIndex` type so swapping its store touches one class. | Adopting Room now is the "future-proofing layer" Principle VI forbids; it also makes the index a second source of truth with a schema to migrate alongside the document. |
| #2 Own `TransportBar`, `TimelineRuler`, `TrackLane`, `ClipPainter`, `GainReadout`, `ProjectOverviewChart`, `TembraMenu`, `TembraTooltip` instead of Material components (Principle VI "prefer the platform"; the user asked for own controls) | Material has no ruler, lane, clip, waveform, drag-readout or chart; the design fixes their shapes and tints; the menu and tooltip are promoted from ad-hoc uses because three screens now need the same hover/focus rows. Sliders, text fields, dialogs, sheets and buttons still wrap Material. | Restyling Material cannot draw a timeline; three private menus would drift. |
| #3 A `ProjectSession` singleton outside the ViewModels (Principle II says one ViewModel per destination holds state) | The open project must outlive a destination's entry-scoped ViewModel: the track settings entry, the library and the next features (mixer, recording) all act on the same document, history and engine binding, and playback must survive navigation inside the dialog's rules. ViewModels remain the only state holders screens see; the session is their shared dependency, injected like a repository. | Holding the document in `TimelineViewModel` loses it when the user opens track settings or the library; duplicating it per ViewModel makes two authorities. |
