---

description: "Task list for feature 009, Timeline Arranger"
---

# Tasks: Timeline Arranger

**Input**: Design documents from `/specs/009-timeline-arranger/`

**Prerequisites**: plan.md, spec.md (clarified 2026-10-13, FR-001–FR-064, FR-008a), research.md (R1–R19), data-model.md (§1–§8), contracts/ (engine-c-abi-v6.md, project-document.md, project-storage.md, timeline-audio-kotlin.md, project-session.md, timeline-screen.md, projects-library-screen.md, perf-instrumentation.md, design-system-additions.md), quickstart.md.

**Tests**: Required. Constitution III (test-first, non-negotiable) and spec FR-061: every implementation task is preceded by the test that fails without it. Lanes, always with `--warning-mode all` from `app/` and always through the `gradle-run` skill wrapper (Constitution VIII): `./gradlew engineHostTest` (Catch2, host), `./gradlew checkAndroidHost` (JVM), `./gradlew checkIos -Ptembra.iosSimulator="iPhone 17"` (simulator), `./gradlew checkAndroidDevice` or `gradlew-device <serial>` (device), `./gradlew :androidApp:connectedDebugAndroidTest` (app module), `cd app/perf && uv run pytest` (pipeline). Kotlin tests never link the engine; they use `FakeAudioEngine` (extended here), `FakeProjectRepository`, `FakeAudioFilePicker`, `FakeBackgroundPlaybackHost`, `FakeMetronomePreferences`. The real engine is exercised only by the `:shared:engine` device and simulator lanes.

**Skills** (Constitution VIII): before writing or reviewing any Kotlin or Compose task below, route through `using-chrisbanes-skills` and load the owner named by the task's contract: `kotlin-concurrency-and-flow` and `kotlin-api-design` for the session, seams and domain types (contracts/project-session.md, timeline-audio-kotlin.md, project-document.md), `kotlin-control-flow` for the `when`s over edits, events and pick results, `compose-state-and-effects` for every screen wiring, `compose-component-design` for every component (contracts/timeline-screen.md, projects-library-screen.md, design-system-additions.md), `compose-performance` for the draw-phase playhead and lane painting, `compose-focus-navigation` for the keyboard map, `compose-ui-testing-patterns` for every `uiTest` class, `gradle-run` for every Gradle command.

**Organization**: Setup adds the catalogue entries, enables the vendored decoders, the perf names, anchors, tokens, glyphs, fixtures and the demo audio. Foundational builds what every story needs: the document and its pure rules, the engine's transport/timeline/mixer modules behind C ABI revision 6, the Kotlin `TimelineAudio` seam, the repository and import pipeline, the `ProjectSession`, the generic design-system pieces, the navigation changes and DI. Each user story is then an independently testable increment: US1 (play the demo with the playhead) is the MVP; US2 tracks and import; US3 ruler, zoom and grid; US4 clip editing and undo; US5 the metronome at the project tempo; US6 track settings and the effects placeholder; US7 the Projects library and persistence; US8 adaptive, pointer, keyboard and screen-reader proof. Polish carries the benchmark journey, the baseline profile, documentation, licences and the labelled sessions.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US8 from spec.md
- Every task names its files; paths are repository-relative

## Path Conventions

Gradle root `app/`. `CORE` = `app/shared/core/src/commonMain/kotlin/audio/tembra/core`, `CORE_TEST` = `app/shared/core/src/commonTest/kotlin/audio/tembra/core`; `ENG` = `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine`, `ENG_TEST` = `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine`; `DATA` = `app/shared/data/src/commonMain/kotlin/audio/tembra/data`, `DATA_TEST` = `app/shared/data/src/commonTest/kotlin/audio/tembra/data`; `UI` = `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui`, `UI_TEST` = `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui`, `UI_UITEST` = `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui`; `ENGINE` = `app/engine`; `PERF` = `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf`; `IOSPERF` = `app/iosApp/TembraPerfTests`; `PIPE` = `app/perf/tracepulse`.

---

## Phase 1: Setup (catalogue, decoders, perf names, anchors, tokens, glyphs, fixtures, demo audio)

**Purpose**: the dependency set, the engine build inputs, the fixed name catalogues and the assets every later task depends on (research R16, R18, R19; contracts/perf-instrumentation.md, design-system-additions.md).

- [X] T001 In `app/gradle/libs.versions.toml` add `kotlinx-collections-immutable = "0.4.0"`, the library `kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinx-collections-immutable" }`, and the plugin `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`; comment each with "research R19"
- [X] T002 In `app/shared/core/build.gradle.kts` apply `alias(libs.plugins.kotlin.serialization)` and add `api(libs.kotlinx.serialization.json)` and `api(libs.kotlinx.collections.immutable)` to `commonMain.dependencies`; in `app/shared/data/build.gradle.kts` apply the same plugin and add `implementation(libs.kotlinx.serialization.json)`; confirm the convention plugin's "core depends on nothing" guard in `app/build-logic/src/main/kotlin/` is amended to allow these two Kotlin-only libraries, with the rule text updated
- [X] T003 [P] In `ENGINE/src/core/miniaudio_impl.cpp` remove `MA_NO_DECODING`, `MA_NO_WAV`, `MA_NO_FLAC`, `MA_NO_MP3` (keep `MA_NO_DEVICE_IO`, `MA_NO_ENCODING`, `MA_NO_GENERATION`); in `ENGINE/third_party/README.md` note the decoders are compiled in since feature 009; verify `ma_decoder_config` has `seekPointCount` in the vendored `miniaudio.h` and record the result in research R5 (if absent, record the MP3-cache fallback as the decision)
- [X] T004 [P] In `ENGINE/CMakeLists.txt` add `CONFIGURE_DEPENDS` globs for `src/transport/*.cpp`, `src/timeline/*.cpp`, `src/mixer/*.cpp` (empty globs are fine; the real-time files join `TEMBRA_RT_SOURCES` in `ENGINE/tests/CMakeLists.txt` when they are created: T047, T049, T050); in `app/shared/engine/build.gradle.kts` add the three folders to the engine task inputs
- [X] T005 [P] In `app/shared/engine/perf-catalogue.txt` add `TIMELINE_PROJECT_OPEN timeline timeline.projectOpen`, `TIMELINE_WAVEFORM timeline timeline.waveform`, `TIMELINE_PLAYHEAD_FRAME timeline timeline.playheadFrame`, `TIMELINE_CLIP_SPLIT timeline timeline.clipSplit`, `TIMELINE_PROJECT_SAVE timeline timeline.projectSave`, `TIMELINE_LIBRARY_OPEN timeline timeline.libraryOpen`, `TRANSPORT_PLAY transport transport.play`, `TRANSPORT_SEEK transport transport.seek`, `ENGINE_PRIME engine engine.prime`, `AUDIO_AVG_US counter audio.avgUs`, `AUDIO_STARVATION counter audio.starvation`, `AUDIO_CLIPS_ACTIVE counter audio.clipsActive`; add `TRANSPORT` to `ENG/perf/PerfFamily.kt`, the entries to `ENG/perf/PerfSection.kt` and `ENG/perf/PerfCounter.kt`, the `TRANSPORT` keyword to `familyKeyword` in `ENG_TEST/perf/PerfSectionCatalogueTest.kt`; run `./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true` and commit `ENGINE/include/tembra_trace_names.h`
- [X] T006 [P] In `UI/perf/PerfAnchors.kt` add the 20 constants of contracts/perf-instrumentation.md (`nav.item.projects`, `screen.projects`, `projects.search`, `projects.card.demo`, `nav.item.timeline`, `screen.timeline`, `timeline.toolbar.addTrack`, `timeline.toolbar.split`, `timeline.toolbar.undo`, `timeline.ruler`, `timeline.lane.0`, `timeline.clip.first`, `timeline.track.menu.0`, `timeline.track.menu.settings`, `timeline.settings.close`, `transport.play`, `transport.skipStart`, `transport.metronome`, `transport.readout`, `dialog.stopAndLeave.confirm`) and to `all`; update the count in `UI_TEST/perf/PerfAnchorsCatalogueTest.kt` to 33; mirror them in `PERF/journeys/Anchors.kt` and `IOSPERF/Anchors.swift` (waveform readiness is the `stateDescription` "ready" on `screen.timeline`, not an anchor)
- [X] T007 [P] Run `app/tools/fetch-material-symbols.sh content_cut undo redo skip_previous skip_next fast_rewind fast_forward grid_on zoom_in zoom_out search sort content_copy library_music expand_more expand_less more_vert audio_file playlist_add mic piano`; add a `TembraIcon` value per glyph in `UI/theme/TembraIcon.kt`; extend `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/IconAssetsTest.kt` with the new files
- [X] T008 [P] In `design/stitch/theme.json` add `dawSemanticColors.trackTints` (six entries: the existing `lavender` and `rose` values plus `dustyRose`, `peach`, `mint`, `violet` sampled from `design/stitch/screens/mixer-timeline/tablet-split-console/screenshot.jpg` lanes 3–6 and rounded to the palette's lightness), `clipCaptionBand`, `playheadChip`, `selectionOutline`, `unavailableClip` with a provenance comment; create `UI_TEST/theme/TimelineTokensTest.kt` asserting `DawColors.trackTints.size == 6` and each token resolves; extend `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/ThemeJsonParityTest.kt` for the new names (red); then add the tokens to `UI/theme/DawColors.kt` and the sizes `laneHeightCompact` 88 dp, `laneHeightExpanded` 96 dp, `rulerHeight` 44 dp, `toolbarHeight` 48 dp, `transportHeightCompact` 64 dp, `transportHeightExpanded` 72 dp, `headerColumnWidth` 200 dp, `cardOverviewHeight` 56 dp to `UI/theme/TembraSpacing.kt` (green); record `TembraShapes.clip = medium` in `UI/theme/TembraShapes.kt`
- [X] T009 [P] Create `specs/009-timeline-arranger/fixtures/make_fixtures.py` (Python, stdlib + `ffmpeg` on PATH for FLAC/MP3) writing to the same folder: `tone-48k-stereo.wav`, `tone-44k1-mono-16.wav`, `tone-8bit-8k.wav`, `tone-float32-192k.wav`, `silence-5s.wav`, `tone-4ch.wav`, `tone.flac`, `tone.mp3`, `long-31min.wav` (generated at test time, not committed; the script writes it to a temp dir on request); commit the small files and a `fixtures/README.md` naming each file's purpose and licence (generated, project-owned)
- [X] T010 [P] Create `app/tools/make-demo-audio.py` synthesising four 16-bar loops at 124 BPM in 4/4 (drums, bass, keys, pad; 30.97 s each, 48 kHz, 16-bit, mono drums/bass, stereo keys/pad) into `app/shared/ui/src/commonMain/composeResources/files/demo/{drums,bass,keys,pad}.wav`; commit the files and the script; note in `app/README.md` that `files/demo/` is regenerated by the script
- [X] T011 [P] In `app/THIRD_PARTY_LICENSES.md` add the `kotlinx-collections-immutable` row (Apache-2.0, 0.4.0) and amend the miniaudio row ("decoders for WAV, FLAC, MP3 and the resampler compiled in since feature 009; public domain / MIT-0")
- [X] T012 Run `./gradlew :shared:core:compileKotlinIosSimulatorArm64 :shared:data:compileKotlinIosSimulatorArm64 engineHostTest --warning-mode all` and confirm the dependency set resolves, the engine still builds with decoders enabled and every existing test stays green

---

## Phase 2: Foundational (document, engine transport, seam, storage, session, generic components, navigation, DI)

**Purpose**: everything more than one story needs, each guarded by a test written first. No stream opens on a host machine: the renderers run against `FakeAudioDevice`; device and simulator lanes cover the real engine and the pickers.

**⚠️ CRITICAL**: no user story work starts until T074, T075 and T076 are green.

### Tests (write first, watch them fail)

- [ ] T013 [P] Create `ENGINE/tests/tempo_map_test.cpp`: `framesPerBeat(124, 48000) == 23225.806…`, `beatsToFrame`/`frameToBeats` round-trip within 1e-9 beats, bar and beat-in-bar for 4/4, 6/8 and 7/8, beat unit 8 halves the frames per beat
- [ ] T014 [P] Create `ENGINE/tests/transport_clock_test.cpp`: `advance(n)` accumulates; a seek request applies at the next `applyPending()`; playing/stopped flags; position never negative
- [ ] T015 [P] Create `ENGINE/tests/spsc_fifo_test.cpp`: power-of-two capacity, `write`/`read` partial counts, `available()`, no overwrite when full (returns 0 written), producer and consumer on two threads for one million frames with no loss
- [ ] T016 [P] Create `ENGINE/tests/disk_streamer_test.cpp` with a `FakeDecoder` (in-memory ramp): `prime(position)` fills each source to capacity or EOF; `stream()` refills while the consumer drains; a source whose fifo runs dry increments `starved`; `stop()` joins; seek re-primes from the new frame; sources within 2 s of the position are primed first
- [ ] T017 [P] Create `ENGINE/tests/clip_source_test.cpp` on the fixtures from T009 (path from `TEMBRA_FIXTURES_DIR`): WAV 48k stereo, 44.1k mono (output at 48k: frame count within ±2 of `frames × 48000 / 44100`), 8-bit 8k, float32 192k, FLAC and MP3 open with the right `tembra_clip_info`; `seek(frame)` lands within one output frame on WAV and FLAC and within one MP3 frame (1152 samples) on MP3; the 4-channel WAV is reported with `channels == 4`
- [ ] T018 [P] Create `ENGINE/tests/track_strip_test.cpp`: a gain step from 1.0 to 0.5 ramps linearly over 10 ms ± one block at 96, 256 and 1024 frames with no sample discontinuity > 0.01; `audible = 0` ramps to silence; mono input duplicates to both channels at unity; stereo passes through
- [ ] T019 [P] Create `ENGINE/tests/master_bus_test.cpp`: sum of two strips, master ramp over 10 ms, clamp to ±1.0 on a +6 dB sum, silence in when nothing active
- [ ] T020 [P] Create `ENGINE/tests/transport_renderer_test.cpp` driving `render()` at 96/256/1024 frames with `FakeDecoder` sources: a clip at beat 4 at 124 BPM starts at frame 92903 (±0); mid-clip start after a seek into the clip; a clip split at an arbitrary beat renders sample-identical to the whole; two clips overlapping after a tempo change give the later precedence; pause releases over 5 ms; seek while playing resumes within 100 ms of audio; the arrangement swap applies at a block boundary; the anchored metronome (a `MetronomeRenderer` with a 4/4 grid) emits beat 1 on the same frame as the clip start at beat 0 and stays within one frame over ten rendered minutes; the metronome enabled while stopped renders silence (FR-038); enabling it mid-playback produces the first click on the next beat boundary; a track strip's rendered gain matches its target within 0.1 dB after the ramp (SC-006); `TransportRecord` is published every block
- [ ] T021 [P] Create `ENGINE/tests/audio_decoder_test.cpp`: `inspect` on every fixture returns format, channels, rate, frames; a text file and a truncated WAV return `ERR_DECODE`
- [ ] T022 [P] Create `ENGINE/tests/overview_builder_test.cpp`: the base level has `ceil(frames/256)` buckets with int8 min/max per channel matching a brute-force computation; levels 1024/4096/16384 are the min/max of their children; the header's `completed_base_buckets` is updated at least every 10 s of audio (verify on a 31-minute generated WAV with a hook that reads the header mid-build); a silent file yields all-zero buckets; a 5-minute stereo 48 kHz WAV builds in under 1.5 s on the host (SC-007 budget is 3 s on a phone; the host bound is the early warning)
- [ ] T023 [P] Extend `ENGINE/tests/engine_core_state_test.cpp` with `FakeAudioDevice`: `tembra_transport_open` only from IDLE; while TRANSPORT the tuner, tone, sample play, `metronome_start` and `metronome_preview` return `ERR_STATE`; `metronome_set_grid` and `select_sound` succeed in TRANSPORT; `transport_close` from any TRANSPORT substate returns to IDLE and is idempotent; `clip_source_open` works in IDLE and TRANSPORT; a referenced source is not closed
- [ ] T024 [P] Extend `ENGINE/tests/engine_info_test.cpp` to expect ABI 6 and version "0.5.0"; extend `ENGINE/tests/trace_poison_test.cpp` so the four new RT files (`clip_source.cpp`, `track_strip.cpp`, `master_bus.cpp`, `transport_renderer.cpp`) are expected in the poison build once T047, T049 and T050 add them to `TEMBRA_RT_SOURCES` (the negative try_compile still fails)
- [ ] T025 [P] Create `CORE_TEST/project/ProjectTest.kt`, `TrackTest.kt`, `ClipTest.kt`, `BeatsTest.kt`, `GainDbTest.kt`, `PercentTest.kt`, `MusicalKeyTest.kt`, `InstrumentLabelTest.kt`, `GridSizeTest.kt`, `AudioAssetTest.kt`: the constraints of data-model.md §1 verbatim — project name 1–60 trimmed, description ≤ 120, track name 1–40 with empty reverting to "Track N", `colorIndex` 0–5, `GainDb` −∞…+6.0 with readouts "+0.8dB"/"−2.4dB"/"−∞" and `toLinear()`, `Percent` 0–100, 24 keys with "F#maj"/"Dmin" names, 12 labels, `GridSize.beatsOf(signature)`, `offset + length ≤ frames`, `lengthFrames > 0`, `Track.nextName`
- [ ] T026 [P] Create `CORE_TEST/project/TempoMapTest.kt`: beats ↔ seconds ↔ frames at 40, 124, 240 BPM in 4/4, 6/8 and 7/8; `barOf`, `beatInBar`; `Project.lengthBeats`/`lengthSeconds` for none, one and several clips; `audibleTracks` truth table (no solo; one solo; several solos; muted + soloed silent)
- [ ] T027 [P] Create `CORE_TEST/project/ProjectEditorTest.kt`: every `ProjectEdit` subtype per data-model.md §1 — add track defaults and naming; rename and label; gain/mute/solo; move up/down at the ends rejected; delete track clears solo; import to track and as new tracks (one edit) placing the clip at the playhead, or at the first free position after it when the playhead lies inside a clip (FR-044); split inside only, > 0.01 s from edges, both halves' offsets and lengths, same name; move stops against neighbours and never before 0; cross-track move needs a free span else `Rejected`; delete clip; tempo and signature changes leave beats unchanged; master volume; project rename/description/key validation; every rejection reason named
- [ ] T028 [P] Create `CORE_TEST/project/EditHistoryTest.kt`: push/undo/redo; capacity 50 drops the oldest; a push after undo clears redo; `canUndo`/`canRedo`
- [ ] T029 [P] Create `CORE_TEST/project/PlaybackPolicyTest.kt`: every row of contracts/project-session.md's policy table, including "InterruptionEnded never resumes" and "Stopped + Interrupted → Stopped"
- [ ] T030 [P] Create `CORE_TEST/project/LibraryQueryTest.kt`: All/Recent (seven days from an injected now), LastEdited desc / Name asc case-insensitive, substring query on name and description case-insensitive, "M of N" and "N projects" texts, empty result
- [ ] T031 [P] Create `CORE_TEST/timeline/TimelineViewportTest.kt`, `RulerLabelsTest.kt`, `SnapTest.kt`, `ClipHitTest.kt`, `TimeFormatTest.kt`: beats ↔ x mapping; `zoomAround` keeps the centroid fixed; clamps at fit (whole project and ≥ 32 bars) and closest (one beat ≥ width); FR-014 label rules swept over 40 zoom levels (no two labels in a row overlap, bars every power of two, "bar.beat" then sixteenths, minutes → seconds → tenths → hundredths, ticks one level below); snap for every `GridSize` incl. `Off`; clip hit vs empty vs marker; "00:42.20" and "5.1" formats with hundredths truncated, never rounded (30.9677 s → "00:30.96")
- [ ] T032 [P] Create `DATA_TEST/projects/ProjectDocumentCodecTest.kt`: round trip of the demo and a 32-track project; `fixtures/demo-project.json` golden text equality (key order, indentation); unknown keys ignored; `formatVersion: 2` refused with `DocumentTooNew`; `-Infinity` gain; full-precision `startBeats`; the no-path regex guard; `SerialDescriptor` element order equals the contract's field table
- [ ] T033 [P] Create `DATA_TEST/projects/ProjectDocumentMigrationsTest.kt` (a registered fake migration 0→1 runs; 1 is a no-op) and `ProjectValidatorTest.kt` (duplicate ids, missing asset entry, `offset + length > frames`, tempo/signature out of range, > 64 tracks, > 1024 clips, names out of length — each named)
- [ ] T034 [P] Create `DATA_TEST/projects/OkioProjectRepositoryTest.kt` on `FakeFileSystem`: create → open → save round trip with staging and rename; `library()` rebuilds a missing or stale index and emits after every save; `duplicate` names "<name> copy", "<name> copy 2", new project/track/clip ids, same asset ids, overviews copied; `delete` removes both folders and clears `openProjectId`; `installDemo` idempotent, `restoreDemo` replaces; `sizeOnDevice` excludes the cache; unused assets deleted only through `save(project, retiredAssets)`; `viewState(id)`/`saveViewState(id, ProjectViewState)` round-trip zoom and grid through `view.json` and never touch `project.json`
- [ ] T035 [P] Create `DATA_TEST/projects/AudioImporterTest.kt` with a scripted `TimelineAudio.inspect`: WAV/FLAC/MP3 accepted; 4 channels → `TooManyChannels`; 31 min → `TooLong`; undecodable → `Unreadable`; a 600 MB source → `TooLarge` before decoding; the same bytes twice → one asset, two results; multiple pick with a refusal in the middle returns all results in order; the staging file never survives a failure; the overview build is requested once per new asset
- [ ] T036 [P] Create `DATA_TEST/projects/WaveformOverviewReaderTest.kt` (parses a generated `.peaks`; partial `completed_base_buckets`; `levelFor(framesPerPixel)` picks 256/1024/4096/16384), `CardOverviewBuilderTest.kt` (48 spans; peak after gain and mute; loudest track's colour; empty project → empty), `AutosaveSchedulerTest.kt` (virtual time: 500 ms debounce, 2 s cap, `flush`, coalescing while a save is in flight), `DemoProjectInstallerTest.kt` (document content: 124 BPM, 4/4, "Dmin", four tracks with labels, five clips, length 30.9677 s shown as "00:30.96")
- [X] T037 [P] Extend `ENG_TEST/FakeAudioEngineTest.kt` for the transport members of contracts/timeline-audio-kotlin.md: every call recorded with its arguments, `audioState` Transport after `openTransport` and Idle after `closeTransport`, scripted `inspectResults`, `failNextPlay`, `transportScript`, the fake `.peaks` writer
- [ ] T038 [P] Create `UI_TEST/session/ProjectSessionTest.kt` with `FakeAudioEngine`, `FakeProjectRepository`, `FakeBackgroundPlaybackHost`, `FakeMetronomePreferences`, `StandardTestDispatcher`: `open(id)` calls `openTransport`, `openClipSource` per asset, `setTempo`, `setMaster`, `setArrangement`, `setGrid`, `setOpen` in that order; a missing file lands in `missingAssets`; gain edit → `setTrack` only; a solo change → `setTrack` for every track; tempo edit → `setTempo` then `setArrangement`; undo/redo re-push; import opens sources before the arrangement; retirement closes sources and passes `retiredAssets`; `Interrupted`/`FocusLost`/`OutputRouteLost`/host stop → `pause` then `release`, never a resume; `confirmLeave` true pauses and waits for Stopped, false leaves playing; opening another project pauses first; autosave 500 ms / 2 s with virtual time; an app-background signal (`AppLifecycleObserver.onStop`) flushes the pending save at once (FR-046); messages for refusals and start failure
- [ ] T039 [P] Create `UI_TEST/session/ArrangementMapperTest.kt` (truth table → `audible`; `GainDb` → linear; clips ordered per track; 65 tracks → a `SessionMessage`, not a crash) and `UI_TEST/session/MetronomeBridgeTest.kt` (grid from the global settings and the project tempo/signature; sound selection forwarded; `setMetronome` toggles)
- [ ] T040 [P] Create `UI_TEST/navigation/ShellViewModelGuardTest.kt`: `select(Tuner)` while the guard reports playing awaits `confirmLeave`; `true` navigates, `false` stays; no prompt when stopped; no prompt between Timeline and `TrackSettingsKey`; `back()` from `TrackSettingsKey` returns to Timeline without a prompt; `back()` from Timeline to Home prompts while playing; `backStack` is `List<NavKey>`
- [ ] T041 [P] Create `UI_UITEST/designsystem/TembraMenuTest.kt`, `TembraTooltipTest.kt`, `TembraTextFieldTest.kt`, `TembraDialogTest.kt`, `TembraCardTest.kt`: semantics (roles, labels, `maxLength` enforcement, error text), hover and focus through an injected `MutableInteractionSource`, 44 dp rows, dialog actions, frozen clock
- [ ] T042 [P] Create `app/shared/engine/src/androidDeviceTest/kotlin/audio/tembra/engine/NativeAudioEngineTransportTest.kt` and `app/shared/engine/src/iosTest/kotlin/audio/tembra/engine/NativeAudioEngineTransportTest.kt`: `openTransport` → Transport; two generated WAV sources open with the right `AudioInfo`; a two-clip arrangement; `play` → `readTransport` positions advance monotonically; `seek(8.0)` lands within 0.05 beats; `pause` keeps the position; `closeTransport` → Idle; tuner start refused while Transport; `inspect` on the three formats; `buildOverview` writes a header with magic `TPK1`
- [ ] T043 [P] Create `app/shared/engine/src/androidDeviceTest/kotlin/audio/tembra/engine/TransportTimingEndToEndTest.kt` (and the iOS twin): over 10 s of real playback `audible_seconds` advances within 2 % of wall time and the health counters report zero `starvation`
- [ ] T044 [P] Create `app/shared/ui/src/iosTest/kotlin/audio/tembra/ui/platform/IosAudioFilePickerTest.kt` (injected picker result: `pickOne` returns one `PickedFile` whose opener yields the bytes; `pickMany` preserves order; cancel → `Cancelled`; security-scoped access released after the copy) and `app/androidApp/src/androidTest/kotlin/audio/tembra/android/AudioFilePickerHostTest.kt` (fake launcher: `pickOne` resolves on `onResult(uri)`, `pickMany` on a list, null → `Cancelled`; MIME list passed through); rename `AndroidBackgroundPlaybackHostTest.kt` expectations to `PlaybackForegroundService` with the notification title and subtitle

### Implementation

- [ ] T045 [P] Create `ENGINE/src/transport/tempo_map.h` (`tembra::TempoMap`: bpm, beatsPerBar, beatUnit, rate; `framesPerBeat`, `beatsToFrame`, `frameToBeats`, `barOf`, `beatInBar`) and `ENGINE/src/transport/transport_clock.h` (`TransportClock`: `int64_t frame`, `bool playing`, atomic seek request, `advance`, `applyPending`); T013, T014 pass
- [ ] T046 [P] Create `ENGINE/src/timeline/spsc_fifo.h` (`SpscFifo<float>`, power-of-two, `write`/`read` partial, `available`, cache-line separated indices, never blocks); T015 passes
- [ ] T047 [P] Create `ENGINE/src/timeline/audio_decoder.{h,cpp}` (`AudioDecoder` over `ma_decoder`: `inspect(path, info)`, `open(path, outRate, seekPoints = 1000 for MP3)` wrapped in `TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_LOAD_CLIP)`, `read(frames, out)`, `seek(frame)`, `channels`, `frames`; `Decoder` interface for the test fake) and `ENGINE/src/timeline/clip_source.{h,cpp}` (`ClipSource`: decoder, `SpscFifo`, `primedFrom`, `eof`, `starved`, `pop(frames, out)` RT-safe); add `src/timeline/clip_source.cpp` to `TEMBRA_RT_SOURCES` in `ENGINE/tests/CMakeLists.txt`; T017, T021 pass
- [ ] T048 Create `ENGINE/src/timeline/disk_streamer.{h,cpp}` (`DiskStreamer`: one `std::thread`, `prime(position, sources, window = 2 s)` synchronous, `stream()` loop refilling free space per active source, 10 ms sleep when all full, `requestReprime`, `stop()` joins; `TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_PRIME)` around `prime`); T016 passes (depends on T046, T047)
- [ ] T049 [P] Create `ENGINE/src/mixer/gain_ramp.h` (`GainRamp`: target, current, per-block linear step for 10 ms at the rate), `ENGINE/src/mixer/track_strip.{h,cpp}` (`TrackStrip::process(in, channels, out, frames)`), `ENGINE/src/mixer/master_bus.{h,cpp}` (`MasterBus::add`, `finish(out, frames)` with master ramp and ±1 clamp); add `src/mixer/track_strip.cpp` and `src/mixer/master_bus.cpp` to `TEMBRA_RT_SOURCES` in `ENGINE/tests/CMakeLists.txt`; T018, T019 pass
- [ ] T050 Create `ENGINE/src/timeline/arrangement_snapshot.h` (POD `ArrangementSnapshot` with `TrackState[64]`, `ClipState[1024]`, counts; `validate(sources)` returning `tembra_error`; `sortClipsByTrackAndStart()`), `ENGINE/src/transport/transport_record.h` (`TransportRecord`), and `ENGINE/src/transport/transport_renderer.{h,cpp}` implementing contracts/engine-c-abi-v6.md "Renderer rules" with `TripleBuffer<ArrangementSnapshot>`, `TripleBuffer<TempoMap>`, `TripleBuffer<TrackStateUpdate>`, atomic play/pause/seek commands, per-clip `TrackStrip` routing into `MasterBus`, the anchored `MetronomeRenderer*` voice summed after the bus, `Seqlock<TransportRecord>`, `blocksRendered()`; add `src/transport/transport_renderer.cpp` to `TEMBRA_RT_SOURCES` in `ENGINE/tests/CMakeLists.txt`; T020 and T024 pass (depends on T045–T049)
- [ ] T051 Create `ENGINE/src/timeline/overview_builder.{h,cpp}` (`OverviewBuilder::build(path, outPath)` writing the `.peaks` format with int8 min/max per channel at 256/1024/4096/16384 and a header flush every 10 s of audio; `TEMBRA_TRACE_SCOPE` not used — called from Kotlin's IO thread which traces); T022 passes (depends on T047)
- [ ] T052 Extend `ENGINE/src/core/engine_core.{h,cpp}`: `TEMBRA_AUDIO_TRANSPORT` state; `openTransport`/`closeTransport` owning a `TransportRenderer` created in `onOutputOpened` at the stream rate (retired like the metronome renderer), a `DiskStreamer`, a `ClipSource` table under the `commands_` mutex with reference checks; `render()` dispatches to the transport renderer when TRANSPORT; the existing tone/sample/metronome paths unchanged; `setTransportTempo/Arrangement/Track/Master/Metronome`, `transportPlay` (prime then start), `transportPause`, `transportSeekBeats` (re-prime, resume), `readTransport` computing `audible_seconds` with the output latency; `inspect` and `buildOverview` as free functions; extend `ENGINE/src/core/audio_health.{h,cpp}` with `avg_us`, `starvation`, `clips_active`; T023 passes
- [ ] T053 Create `ENGINE/src/transport/transport_abi.cpp` and `ENGINE/src/timeline/timeline_abi.cpp` (the `tembra_transport_*`, `tembra_clip_source_*`, `tembra_audio_inspect`, `tembra_audio_overview_build` forwarders with argument checks) and extend `ENGINE/include/tembra_engine.h` to revision 6 exactly as contracts/engine-c-abi-v6.md (states, structs, enums, functions, health fields, revision comment naming `specs/009-timeline-arranger/contracts/engine-c-abi-v6.md`); `ENGINE/src/engine_info.cpp` → 0.5.0 / ABI 6; `ENGINE/src/metronome/metronome_abi.cpp` allows `set_grid`/`select_sound` in TRANSPORT; T024 passes; `./gradlew engineHostTest` green
- [X] T054 Extend `ENGINE/android/src/main/cpp/tembra_jni.cpp` with `nativeTransportOpen/Close/SetTempo/SetArrangement(ByteBuffer)/SetTrack/SetMaster/SetMetronome/Play/Pause/SeekBeats/Read(long[], double[])`, `nativeClipSourceOpen/Close`, `nativeAudioInspect(String, int[], long[])`, `nativeAudioOverviewBuild(String, String)`; the arrangement buffer is read directly as the C struct (`GetDirectBufferAddress`); update `app/shared/engine/src/nativeInterop/cinterop/tembra_engine.def` if it lists headers explicitly
- [ ] T055 [P] Create `CORE/project/ProjectId.kt`, `TrackId.kt`, `ClipId.kt`, `AudioAssetId.kt`, `Beats.kt`, `GainDb.kt`, `Percent.kt`, `MusicalKey.kt`, `InstrumentLabel.kt`, `GridSize.kt`, `ZoomLevel.kt`, `AudioFormat.kt`, `AudioAsset.kt`, `Clip.kt`, `Track.kt`, `Project.kt`, `ProjectDocument.kt`, `IdGenerator.kt` per data-model.md §1 (`@Serializable`, explicit `@SerialName`, `@JvmInline value class` for the single-field types, persistent collections; `kotlin-api-design`); T025 passes
- [ ] T056 [P] Create `CORE/project/TempoMap.kt` and `CORE/project/ProjectDerived.kt` (`lengthBeats`, `lengthSeconds`, `audibleTracks`, `unusedAssets`, `Track.nextName`); T026 passes
- [ ] T057 Create `CORE/project/ProjectEdit.kt` (sealed) with one file per subtype that carries behaviour (`AddTrack.kt`, `RenameTrack.kt`, `SetLabel.kt`, `SetGain.kt`, `SetMuted.kt`, `SetSoloed.kt`, `MoveTrack.kt`, `DeleteTrack.kt`, `ImportClips.kt`, `SplitClip.kt`, `MoveClip.kt`, `DeleteClip.kt`, `SetTempo.kt`, `SetTimeSignature.kt`, `SetMasterVolume.kt`, `RenameProject.kt`, `SetDescription.kt`, `SetKey.kt`), `EditResult.kt`, `EditRejection.kt`, `ProjectEditor.kt` (exhaustive `when`, `kotlin-control-flow`); T027 passes (depends on T055, T056)
- [ ] T058 [P] Create `CORE/project/EditHistory.kt`; T028 passes
- [ ] T059 [P] Create `CORE/project/PlaybackState.kt`, `PlaybackEvent.kt`, `PlaybackTransition.kt`, `PlaybackPolicy.kt` (pure, exhaustive); T029 passes
- [ ] T060 [P] Create `CORE/project/LibraryQuery.kt`, `LibraryFilter.kt`, `LibrarySort.kt`, `LibrarySummary.kt` (the pure card fields the query sorts and filters); T030 passes
- [ ] T061 [P] Create `CORE/timeline/TimelineViewport.kt`, `RulerLabels.kt`, `RulerRow.kt`, `RulerLabel.kt`, `Snap.kt`, `ClipHit.kt`, `TimeFormat.kt`; T031 passes
- [X] T062 [P] Create `ENG/TimelineAudio.kt`, `ArrangementSnapshot.kt`, `TrackRender.kt`, `ClipRender.kt`, `ClipSourceHandle.kt`, `ClipSourceResult.kt`, `TransportOpenResult.kt`, `PlayResult.kt`, `TransportSnapshot.kt`, `AudioInfo.kt`; extend `ENG/AudioState.kt` (+ `Transport`), `ENG/AudioHealth.kt` (+3), `ENG/AudioEngine.kt` (`: TimelineAudio`); extend `ENG/FakeAudioEngine.kt` per contracts/timeline-audio-kotlin.md; T037 passes
- [ ] T063 Extend `app/shared/engine/src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt` with the transport natives, `ArrangementBuffer.kt` (direct `ByteBuffer` marshalling of the C layout), focus `Gain` on `openTransport`, `FocusLost`/`Interrupted` mapping, `BecomingNoisyReceiver` registration; extend `app/shared/engine/src/iosMain/kotlin/audio/tembra/engine/NativeAudioEngine.ios.kt` with the cinterop calls, `activateForPlayback()` and the latency report; `inspect`, `buildOverview`, `openClipSource` on `Dispatchers.IO`; T042, T043 pass on the device and simulator lanes (depends on T053, T054, T062)
- [ ] T064 [P] Create `DATA/projects/ProjectDocumentCodec.kt` (the `Json` configuration of contracts/project-document.md), `ProjectDocumentMigrations.kt`, `DocumentTooNew.kt`, `ProjectValidator.kt`, `ValidationFailure.kt`; commit `specs/009-timeline-arranger/fixtures/demo-project.json`; T032, T033 pass
- [ ] T065 [P] Create `DATA/projects/WaveformOverview.kt`, `WaveformOverviewReader.kt`, `OverviewStatus.kt`, `CardOverview.kt`, `CardOverviewBuilder.kt`, `AutosaveScheduler.kt`; T036 passes (except the installer case)
- [ ] T066 Create `DATA/projects/ProjectRepository.kt`, `ProjectFolder.kt`, `LibraryIndex.kt`, `LibraryEntry.kt`, `OpenedProject.kt`, `ProjectViewState.kt` (zoom and grid, device-side `view.json`), `OkioProjectRepository.kt` (stage-then-rename, index rebuild, `library()` flow, `duplicate`, `delete`, `sizeOnDevice`, `setOpen`, `save(project, retiredAssets)`, `viewState`/`saveViewState`), `FakeProjectRepository.kt` (in `commonMain`); T034 passes (depends on T064, T065)
- [ ] T067 Create `DATA/projects/AudioImporter.kt`, `ImportResult.kt`, `ImportRefusal.kt`, `PickedFile.kt` (SHA-256 through `HashingSource`, 500 MB guard → `TooLarge`, inspect, dedup, rename, overview build wrapped in `PerfTrace.section(TIMELINE_WAVEFORM)`); `DemoProjectInstaller.kt` and the `DemoAudioSource.kt` interface (contracts/project-storage.md; the Compose resource reader bound in `:shared:ui`); T035 and the installer case of T036 pass (depends on T066)
- [ ] T068 [P] Create `UI/platform/AudioFilePicker.kt`, `AudioPickRequest.kt`, `AudioPickResult.kt`; delete `UI/platform/SoundFilePicker.kt`, `SoundPickResult.kt`, `SoundPickFailure.kt` and update the metronome's `MetronomeViewModel.importCustomSound` and `UI_TEST/metronome/MetronomeViewModelTest.kt` (constructor and picker fake) to `pickOne(AudioPickRequest(wavTypes))`; delete `UI_TEST/platform/FakeSoundFilePicker.kt` and create `UI_TEST/platform/FakeAudioFilePicker.kt`; implement `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/platform/IosAudioFilePicker.kt` (`UIDocumentPickerViewController`, `allowsMultipleSelection`, security-scoped copy) and in `:androidApp` `AudioFilePickerHost.kt` + `AndroidAudioFilePicker.kt` (`OpenDocument` and `OpenMultipleDocuments` launchers registered in `MainActivity.kt`); rename `MetronomeForegroundService` → `PlaybackForegroundService` with `PlaybackNotification(title, subtitle)` in `app/androidApp/src/main/kotlin/audio/tembra/android/` and `AndroidManifest.xml`; T044 passes; the metronome's existing tests stay green
- [ ] T069 [P] Create `UI/designsystem/TembraMenu.kt`, `MenuItemSpec.kt`, `TembraTooltip.kt`, `TembraTextField.kt`, `TembraDialog.kt`, `DialogAction.kt`, `TembraCard.kt` per contracts/design-system-additions.md (`compose-component-design`: root modifier, slots, primitives); T041 passes
- [ ] T070 Create `UI/session/ProjectSession.kt`, `SessionState.kt`, `SessionMessage.kt`, `ArrangementMapper.kt`, `ClipSourceRegistry.kt`, `OverviewTracker.kt`, `MetronomeBridge.kt`, `PlaybackGuard.kt`, `DialogRequest.kt`, `TransportTelemetry.kt`, `AppStartup.kt` per contracts/project-session.md (`kotlin-concurrency-and-flow`: one injected `sessionScope`, named launch sites, no `init` launches, `Channel` + `receiveAsFlow` for messages and dialog requests; `PerfTrace.section(TRANSPORT_PLAY / TRANSPORT_SEEK / TIMELINE_PROJECT_OPEN / TIMELINE_PROJECT_SAVE)` around the respective paths); T038, T039 pass (depends on T057–T062, T066, T067)
- [ ] T071 Extend `UI/navigation/ShellUiState.kt` (`backStack: List<NavKey>`), `UI/navigation/ShellViewModel.kt` (guarded `select`/`back` through `PlaybackGuard`), `UI/navigation/TembraNavDisplay.kt` (second `entry<TrackSettingsKey>` placeholder content until US2), `UI/navigation/NavigationShellContent.kt` (hosts `StopAndLeaveDialog` from the session's `DialogRequest`); create `UI/designsystem/StopAndLeaveDialog.kt` (actions tagged `dialog.stopAndLeave.confirm` and `dialog.stopAndLeave.cancel`) and `UI/tracksettings/TrackSettingsKey.kt`; T040 passes
- [ ] T072 Extend `UI/di/AppBindings.kt` (`ProjectRepository`, `AudioImporter`, `ProjectSession`, `TimelineAudio` seam cast, `AudioFilePicker`, `@Named("session") CoroutineScope`, `DemoAudioSource`) and `UI/di/AppGraph.kt`; `app/androidApp/src/main/kotlin/audio/tembra/android/AndroidAppGraph.kt` and `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/IosAppGraph.kt` provide the pickers; `UI/App.kt` starts the session once (`LaunchedEffect(session) { startup.start() }`) and installs `UI/session/AppLifecycleObserver.kt` (a `LifecycleEventObserver` on `LocalLifecycleOwner` that calls `session.flush()` on `ON_STOP`, FR-046) plus `UI/projects/LibraryViewStateHolder.kt` (`@SingleIn(AppScope)`; search, filter, sort kept while the app runs, FR-053)
- [ ] T073 Update `UI/navigation/DestinationContent.kt` so Timeline and Projects still render their placeholders (replaced in US1 and US7), and change `UI_UITEST/navigation/NavigationShellTest.kt` to use Profile as its placeholder destination; keep `UI_TEST/navigation/DestinationTest.kt` passing by updating the placeholder expectations when the screens land (US1, US7)
- [ ] T074 Run `./gradlew engineHostTest checkAndroidHost --warning-mode all`: every Phase 2 host test green, zero warnings
- [ ] T075 Run `./gradlew checkIos -Ptembra.iosSimulator="iPhone 17" --warning-mode all`: the same set plus `IosAudioFilePickerTest` and `NativeAudioEngineTransportTest` green
- [ ] T076 Run `./gradlew checkAndroidDevice :androidApp:connectedDebugAndroidTest --warning-mode all` on the Pixel 6 Pro (awake, unlocked): `NativeAudioEngineTransportTest`, `TransportTimingEndToEndTest`, `AudioFilePickerHostTest`, `AndroidBackgroundPlaybackHostTest` green

**Checkpoint**: Foundation ready; user story implementation can begin.

---

## Phase 3: User Story 1 - Play a project and watch the playhead (Priority: P1) 🎯 MVP

**Goal**: Timeline shows the demo project with waveforms and plays it: transport bar, playhead across ruler and lanes, the position/length readout, pause, skip-to-start, ruler tap and silent scrub, master volume, playback under screen lock, the stop-and-leave dialog (spec FR-001–FR-012, FR-008a, FR-027–FR-030 drawing, FR-045, FR-047).

**Independent Test**: quickstart §3 on both platforms; `transport_renderer_test` and `TransportTimingEndToEndTest` prove the timing; `TimelineScreenTest` proves the screen.

### Tests for User Story 1 (write first, watch them fail)

- [ ] T077 [P] [US1] Create `UI_TEST/timeline/TimelineViewModelTest.kt` (US1 cases): first state from a fake session shows the demo project, `isFirstContentReady` once every visible overview is `Ready`; `play`/`pause`/`skipToStart`/`stepBar` forward to the session; `seekAt(x)` snaps through the viewport and sets `following = true`; `scrubStart/Move/End` pause silently and resume per clarification 4; `setMasterVolume`; the readout fields "00:00.00 / 00:30.96"; `message` mapping
- [ ] T078 [P] [US1] Create `UI_TEST/transport/TransportUiStateTest.kt` (derived labels, enabled flags, metronome toggle state) and `UI_TEST/timeline/TimelineLayoutTest.kt` (Compact < 600, Medium 600–839, Landscape ≥ 600 & < 480 tall, Expanded ≥ 840 & ≥ 480; iPhone Duo end rail → Compact)
- [ ] T079 [P] [US1] Create `UI_UITEST/transport/TransportBarTest.kt` (semantics of every button, readout text from scripted telemetry at 1 Hz, master slider adjustable with arrow keys in 1 % steps and Page keys in 10 % steps, hover/focus via injected interaction sources, play toggles to pause), `UI_UITEST/timeline/TimelineRulerTest.kt` (labels rendered for a given viewport, tap → `onSeek(beats)`, drag → scrub callbacks, the playhead chip text "5.1"), `UI_UITEST/timeline/TrackLaneTest.kt` (US1: clips drawn with caption and waveform columns from a fake overview; placeholder while `Building`; unavailable style), `UI_UITEST/timeline/PlayheadOverlayTest.kt` (position from `ScriptedTransportTelemetry` read in draw; moves under reduce motion), `UI_UITEST/timeline/TimelineScreenTest.kt` (US1: the demo at Compact and Expanded with frozen clock; semantics before pixels; `screen.timeline` tag; first-content report)
- [ ] T080 [P] [US1] Create `UI_UITEST/timeline/ScriptedTransportTelemetry.kt` (a `TransportTelemetry` fed by a script of snapshots per frame time) and `UI_UITEST/designsystem/StopAndLeaveDialogTest.kt` (title, text, both actions by tag `dialog.stopAndLeave.confirm`/`.cancel`, result)

### Implementation for User Story 1

- [ ] T081 [P] [US1] Create `UI/timeline/TimelineUiState.kt`, `TimelineCallbacks.kt`, `TimelineLayout.kt`, `TimelineCopy.kt`, `TimelineSheet.kt`, `Selection.kt`, `TimelineTestTags.kt`; `UI/transport/TransportUiState.kt`, `TransportCallbacks.kt`; T078 passes
- [ ] T082 [P] [US1] Create `UI/transport/TransportButton.kt`, `TimeReadout.kt` (a `Canvas` that reads the telemetry in draw and paints the text with `drawText` in `tabularTimecode`; a `Text` recomposed per frame is forbidden by the telemetry rule; semantics text updated at 1 Hz from `UiState`), `MasterVolumeSlider.kt` (on `TembraSliderDefaults`), `MetronomeToggle.kt`, `TransportBar.kt` (reuses the metronome's `PlayPauseButton` with `testTag = PerfAnchors.TRANSPORT_PLAY`); T079's `TransportBarTest` passes
- [ ] T083 [P] [US1] Create `UI/timeline/components/TimelineRuler.kt`, `TempoPill.kt`, `SignaturePill.kt` (pills read-only in US1; tap wired in US5), the ruler painter using `RulerLabels`, the playhead chip, tap/drag handling; T079's `TimelineRulerTest` passes
- [ ] T084 [P] [US1] Create `UI/timeline/components/ClipPainter.kt` (DrawScope extension: rounded block in the track tint, caption band, waveform columns from `WaveformOverview.levelFor`, selection outline, unavailable style), `TrackLane.kt` (one `Canvas` per lane; US1: draw + tap; gestures completed in US3/US4), `TrackRow.kt`, `TrackList.kt` (LazyColumn), `TrackHeaderCompact.kt` and `TrackHeader.kt` (US1: colour dot, name, chip, M/S display; toggles wired in US2); T079's `TrackLaneTest` passes
- [ ] T085 [P] [US1] Create `UI/timeline/components/PlayheadOverlay.kt` (reads `TransportTelemetry` inside `drawBehind`/`Canvas` with `rememberFrameTick(playing)` under `PerfTrace.section(TIMELINE_PLAYHEAD_FRAME)`; `stateDescription` from the 10 Hz `UiState` position); T079's `PlayheadOverlayTest` passes
- [ ] T086 [US1] Create `UI/timeline/TimelineViewModel.kt` (`@Inject @ContributesIntoMap(AppScope::class) @ViewModelKey`; collects `ProjectSession.state`, owns viewport/selection/sheet view state, exposes `uiState: StateFlow<TimelineUiState>`, `messages`, `telemetry`; intents of US1), `TimelineScreenContent.kt` (toolbar placeholder row, ruler, track list, overlay, transport bar per `TimelineLayout`), `TimelineScreen.kt` (wiring; `ReportFirstContentDrawn(state.isFirstContentReady)`; `DisposableEffect(viewModel) { onDispose { viewModel.flushSave() } }`), `components/EmptyTimeline.kt`; replace the placeholder in `UI/navigation/DestinationContent.kt`; update `DestinationTest` expectations; T077 and T079's `TimelineScreenTest` pass (depends on T081–T085)
- [ ] T087 [US1] Wire the demo in `UI/session/AppStartup.kt` and `UI/session/ProjectSession.kt`: `AppStartup.start()` installs the demo on first launch (`installDemo()`), opens the most recently opened project or the demo (FR-002, FR-047), and the session reports `TIMELINE_PROJECT_OPEN`; extend `UI_TEST/session/ProjectSessionTest.kt` so that first launch opens the demo and a later launch the last opened project
- [ ] T088 [US1] Wire background playback and the dialog end to end in `UI/session/ProjectSession.kt`, `UI/navigation/NavigationShellContent.kt` and `app/androidApp/src/main/kotlin/audio/tembra/android/PlaybackForegroundService.kt`: the session calls `keepAlive()` before `play()` and `release()` after `Pausing → Stopped`; the Android notification shows "Playing" / project name; the shell's dialog host renders `StopAndLeaveDialog` from `DialogRequest`; T040 and T080 remain green; manual check quickstart §3 steps 5–6 on the Pixel and the iPhone
- [ ] T089 [US1] Run quickstart §3 on the Pixel 6 Pro, the iPhone 14, the iPhone 17 simulator and the iPad simulator; record the output and screen for SC-002/SC-003 (play within 100 ms, playhead within 30 ms on a wired route); store the first screenshots `android-compact-demo-stopped.png`, `android-compact-demo-playing.png`, `ios-compact-demo-playing.png`, `ios-expanded-demo-playing.png` under `specs/009-timeline-arranger/screenshots/` with a `NOTES.md` row each

**Checkpoint**: the demo project plays with a truthful playhead on both platforms; the MVP is demonstrable.

---

## Phase 4: User Story 2 - Build the project from tracks (Priority: P1)

**Goal**: add, rename, label, gain, mute, solo, reorder and delete tracks; import audio into a track or as new tracks (one or many files); the track settings form (spec FR-019–FR-026, FR-044, User Story 2 scenarios).

**Independent Test**: quickstart §4 steps 1–6 on both platforms; `ProjectEditorTest`, `AudioImporterTest` and `TimelineScreenTest` (US2 cases) prove the rules.

### Tests for User Story 2 (write first, watch them fail)

- [ ] T090 [P] [US2] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `addTrack` selects the new track; `selectTrack`; header `toggleMute/toggleSolo`; `setGain` from the header; track menu actions `moveUp/moveDown/deleteTrack(confirm)`; `addAudio(toTrack)` and `addAudioAsNewTrack` call the picker (`pickOne` / `pickMany`) and the session import; no selection + toolbar "Add audio…" behaves as new track; refusals surface as one message
- [ ] T091 [P] [US2] Create `UI_TEST/tracksettings/TrackSettingsViewModelTest.kt`: fields from the session's track; `rename` (empty reverts to "Track N"), `setLabel` (12 + None), `setGain`, mute, solo, `deleteTrack`; each field one edit; unknown track id → closes
- [ ] T092 [P] [US2] Create `UI_UITEST/timeline/TrackHeaderTest.kt` (compact and expanded: semantics "Track 2, Guitar, −2.4 dB, muted", M/S toggles, menu entries incl. unavailable Move up at the top, hover/focus), `UI_UITEST/timeline/GainReadoutTest.kt` (readout text, drag sets, arrows 0.5 dB, Page keys 6 dB, double tap → 0 dB, "−∞" at silence), `UI_UITEST/timeline/InstrumentLabelMenuTest.kt` (12 labels + None), `UI_UITEST/tracksettings/TrackSettingsScreenTest.kt` (US2: the nested screen with the form and the transport bar; back)

### Implementation for User Story 2

- [ ] T093 [P] [US2] Create `UI/timeline/components/GainReadout.kt`, `InstrumentChip.kt`, `InstrumentLabelMenu.kt` (on `TembraMenu`), `TrackMenu.kt` (Track settings, Add audio…, Move up, Move down, Delete track), `DeleteTrackDialog.kt`; wire M/S toggles and the gain drag into `TrackHeader.kt`/`TrackHeaderCompact.kt`; T092's header, gain and menu tests pass
- [ ] T094 [P] [US2] Create `UI/timeline/components/TimelineToolbar.kt` (US2: "+ Track", "Add audio…", "Add audio as new track" and the overflow; scissors/undo/redo wired in US4, grid/zoom in US3) with `PerfAnchors.TIMELINE_ADD_TRACK`
- [ ] T095 [US2] Extend `UI/timeline/TimelineViewModel.kt` with the US2 intents (session edits; picker calls through `AudioFilePicker`; import through the session; message for refusals); T090 passes
- [ ] T096 [US2] Create `UI/tracksettings/TrackSettingsViewModel.kt`, `TrackSettingsUiState.kt`, `TrackSettingsCallbacks.kt`, `TrackSettingsForm.kt` (name `TembraTextField` maxLength 40, `InstrumentLabelMenu`, gain slider + `GainReadout`, M/S toggles, Delete track), `TrackSettingsScreen.kt` (title = track name, `TransportBar` at the bottom, back); replace the placeholder entry in `TembraNavDisplay.kt`; T091 and T092's screen test pass (the Effects section arrives in US6)
- [ ] T097 [US2] Run quickstart §4 steps 1–6 and the multiple-file import on the Pixel 6 Pro and the iPhone 14 with WAV, FLAC and MP3 files from `fixtures/` copied to the devices; confirm refusals name the reason; screenshots `android-compact-track-settings.png`, `ios-compact-import-refused.png`

**Checkpoint**: a project can be built from tracks and imported audio and reopens identically after relaunch.

---

## Phase 5: User Story 3 - Find the way around time: ruler, zoom and grid (Priority: P1)

**Goal**: adaptive ruler labels, pinch/wheel/trackpad zoom with the readout and "Fit project", horizontal and vertical scrolling with pinned ruler and headers, follow-the-playhead, the grid picker and snapping (spec FR-013–FR-018).

**Independent Test**: quickstart §5 on both platforms; `RulerLabelsTest` and `TimelineViewportTest` prove the rules; `TrackLaneGestureTest` proves the gestures.

### Tests for User Story 3 (write first, watch them fail)

- [ ] T098 [P] [US3] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `zoomAround`, `zoomStep(+/−)`, `fitProject`, `scrollBy` suspends following, `setGrid`, zoom and grid persisted through the session's `saveViewState` (device-side, non-undo, never in the document), ruler taps snap per grid, the viewport clamps
- [ ] T099 [P] [US3] Create `UI_UITEST/timeline/TrackLaneGestureTest.kt` (pinch → `onZoom(centroid, factor)`; plain horizontal drag → `onScroll`; wheel → scroll, modifier + wheel → zoom; trackpad pinch → zoom) and `UI_UITEST/timeline/GridPickerTest.kt`, `ZoomReadoutTest.kt`; extend `TimelineScreenTest` with the widest and closest zoom renders and the pinned ruler/header check at Expanded

### Implementation for User Story 3

- [ ] T100 [P] [US3] Create `UI/timeline/components/GridPicker.kt` (on `MenuPill`: Bar, 1/4, 1/8, 1/16, Off) and `ZoomReadout.kt` ("1×", tap → menu with "Fit project"); add them to `TimelineToolbar.kt` (overflow in Compact)
- [ ] T101 [US3] Implement the gesture layer in `UI/timeline/components/TrackLane.kt` and `TimelineRuler.kt` (`detectTransformGestures` for pinch, horizontal drag → scroll, `onPointerEvent(Scroll)` with the modifier → zoom, trackpad pinch) wrapped in `PerfTrace.animation(TIMELINE_ZOOM / TIMELINE_SCROLL)`; `TrackList.kt` keeps the ruler pinned and, at Expanded, the header column pinned; follow-the-playhead paging in `TimelineViewModel`; T098, T099 pass
- [ ] T102 [US3] Run quickstart §5 on the Pixel 6 Pro (touch), the iPad simulator with a trackpad, and Android desktop mode with a mouse; capture ten zoom levels of the ruler into `screenshots/android-ruler-zoom-<n>.png`

**Checkpoint**: the timeline is navigable at every zoom with correct labels and snapping.

---

## Phase 6: User Story 4 - Split, move and delete clips, and undo it all (Priority: P2)

**Goal**: clip selection, split at the playhead, long-press move along and across tracks with constraints, delete, undo/redo from the toolbar and shortcuts (spec FR-031–FR-035).

**Independent Test**: quickstart §4 steps 7–8; `ProjectEditorTest` and `transport_renderer_test` prove the sound; `TrackLaneGestureTest` (US4) proves the gestures.

### Tests for User Story 4 (write first, watch them fail)

- [ ] T103 [P] [US4] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `selectClip`, `clearSelection`, `canSplit` rules (no selection, playhead outside, within 0.01 s of an edge → false), `split` goes through the session as `SplitClip` under `PerfTrace.section(TIMELINE_CLIP_SPLIT)`, `moveClipStart/Move/End` builds a `MoveClip` only on release with the snapped start and the target track, rejected moves snap back with no edit, `deleteClip`, `undo`/`redo` availability from the session
- [ ] T104 [P] [US4] Extend `UI_UITEST/timeline/TrackLaneGestureTest.kt`: tap selects; plain drag on a clip scrolls (clip stays); long press → lift state then drag → `onMove`; release → `onMoveEnd`; under reduce motion the lift snaps without animation (FR-060); keyboard S / Delete / Backspace on the focused lane; and `UI_UITEST/timeline/ClipMenuTest.kt`

### Implementation for User Story 4

- [ ] T105 [US4] Complete the clip gestures in `UI/timeline/components/TrackLane.kt` (long-press-then-drag move with the lifted clip drawn by `ClipPainter` at the drag position, auto-scroll at the edges, drop target highlighting of the lane under the finger), `ClipMenu.kt` (Delete clip), scissors/undo/redo in `TimelineToolbar.kt` with `PerfAnchors.TIMELINE_SPLIT`/`TIMELINE_UNDO`; US4 intents in `TimelineViewModel.kt`; T103, T104 pass
- [ ] T106 [US4] Run quickstart §4 steps 7–8 on the Pixel 6 Pro and the iPhone 14; record a split's before/after output and compare sample-identical (SC-005); screenshots `android-compact-clip-selected.png`, `android-compact-clip-split.png`

**Checkpoint**: clips can be split, moved, deleted and every edit undone, during playback too.

---

## Phase 7: User Story 5 - Play along with the metronome at the project tempo (Priority: P2)

**Goal**: the transport's metronome toggle drives the Metronome destination's click at the project tempo and signature; the tempo dialog and signature menu from the ruler; the metronome settings sheet reusing the metronome's components; the standalone destination unchanged (spec FR-036–FR-040).

**Independent Test**: quickstart §6; `transport_renderer_test` (anchored click) and `MetronomeBridgeTest` prove the timing and the shared settings.

### Tests for User Story 5 (write first, watch them fail)

- [ ] T107 [P] [US5] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `toggleMetronome` (persisted per project, non-undo), `openTempoDialog`/`setTempo(dragging)`/`commitTempo` (one undo step on release, ruler redraw), `tapTempo` through `TapTempoSequence`, `openSignatureMenu`/`setTimeSignature`, `openMetronomeSettings` (sheet state) and its changes going to `MetronomePreferences`
- [ ] T108 [P] [US5] Create `UI_UITEST/timeline/TempoDialogTest.kt` (dial, two steppers, slider, tap button with `timeline.*` tags), `MetronomeSettingsSheetTest.kt` (picker, tiles, cells, style; tempo and signature read-only), `TempoPillTest.kt`/`SignaturePillTest.kt`; extend `UI_UITEST/transport/TransportBarTest.kt` with the toggle's long press

### Implementation for User Story 5

- [ ] T109 [P] [US5] Create `UI/timeline/components/TempoDialog.kt` (reusing `TempoDial`, `TempoStepper`, `TempoSlider`, `TapTempoButton` with overridden `testTag`s) and `MetronomeSettingsSheet.kt` (`ModalBottomSheet` with `ClickSoundPicker`, `SubdivisionTiles`, `AccentCells`, `StyleMenu`; read-only tempo/signature row); wire `TempoPill`/`SignaturePill` taps and the toggle's long press; T108 passes
- [ ] T110 [US5] Extend `UI/timeline/TimelineViewModel.kt` with the US5 intents and the `MetronomeBridge` interactions (toggle on during playback anchors at the next beat through the session's `setMetronome`; tempo commit → `SetTempo` edit; signature → `SetTimeSignature`); T107 passes
- [ ] T111 [US5] Run quickstart §6 on both platforms including the ten-minute recordings at 40, 124 and 240 BPM in 4/4 and 7/8 (SC-004) and the "same settings in the Metronome destination" check; screenshots `android-compact-tempo-dialog.png`, `ios-compact-metronome-sheet.png`

**Checkpoint**: the click is locked to the project and configured through the metronome's own controls.

---

## Phase 8: User Story 6 - Open a track's settings and the place for its effects (Priority: P3)

**Goal**: the Effects placeholder section; the inline expansion at Expanded with one track open at a time; back returns to the same scroll and zoom; a resize across 840 dp moves the open settings between the two forms with edits intact (spec FR-041–FR-043).

**Independent Test**: quickstart §7 step 1; `TrackSettingsScreenTest` (US6 cases) and `TimelineScreenTest` (inline row).

### Tests for User Story 6 (write first, watch them fail)

- [ ] T112 [P] [US6] Extend `UI_UITEST/tracksettings/TrackSettingsScreenTest.kt`: the "Effects" heading with exactly one sentence `TimelineCopy.effectsLater` and no controls; `UI_UITEST/tracksettings/TrackSettingsInlineRowTest.kt` (expanded: chevron expands under the header, a second track collapses the first, the form inside); extend `TimelineViewModelTest` with `expandTrack`/`collapse` and the Compact ↔ Expanded hand-off keeping the draft (`TrackSettingsDraft` moves between the nested entry and the inline row)

### Implementation for User Story 6

- [ ] T113 [P] [US6] Create `UI/tracksettings/EffectsPlaceholder.kt` and add it to `TrackSettingsForm.kt`; create `UI/tracksettings/TrackSettingsInlineRow.kt` and the chevron in `TrackHeader.kt`; `TrackRow.kt` renders the inline row at Expanded for `expandedTrack`
- [ ] T114 [US6] Implement the layout hand-off in `UI/timeline/TimelineViewModel.kt`, `UI/tracksettings/TrackSettingsViewModel.kt`, `UI/session/ProjectSession.kt` and a new `UI/tracksettings/TrackSettingsDraft.kt`: `TimelineViewModel` owns `expandedTrack` and the draft is shared through `ProjectSession.settingsDraft`, so a window crossing 840 dp opens the inline row or pushes `TrackSettingsKey` with the same draft; back from the nested screen restores scroll and zoom (kept in the ViewModel); T112 passes
- [ ] T115 [US6] Run quickstart §7 step 1 on the Pixel Fold emulator (closed → open) and a desktop-mode window; screenshots `android-expanded-settings-inline.png`, `ios-expanded-settings-inline.png`

**Checkpoint**: settings open as a screen or inline, the effects placeholder is in place.

---

## Phase 9: User Story 7 - Keep projects: the library (Priority: P3)

**Goal**: the Projects destination lists every project with cards, search, filters and sort; create, open/resume, rename, describe, key, duplicate, delete, restore demo; autosave and resume on launch; the portable document proven across platforms (spec FR-046–FR-054).

**Independent Test**: quickstart §7 steps 2–4; `OkioProjectRepositoryTest`, `ProjectDocumentCodecTest` and `ProjectsScreenTest` prove it.

### Tests for User Story 7 (write first, watch them fail)

- [ ] T116 [P] [US7] Create `UI_TEST/projects/ProjectsViewModelTest.kt`: entries from a fake repository; `setQuery`/`setFilter`/`setSort` combinations and the count text; the query, filter and sort live in `LibraryViewStateHolder` and survive a ViewModel recreation (FR-053); `open(id)` → session open then shell select Timeline; `newProject` → create → open; menu intents (rename validation 1–60, description ≤ 120, key 24 + None, duplicate, delete with confirm, delete of the open project opens the next most recent or creates one, restore demo); `isFirstContentReady` under `PerfTrace.section(TIMELINE_LIBRARY_OPEN)`
- [ ] T117 [P] [US7] Create `UI_TEST/projects/RelativeTimeTest.kt` ("2 m ago", "Yesterday 22:40", "3 days ago", the date beyond a week) and `ProjectsLayoutTest.kt` (one column < 840, two ≥ 840, three ≥ 1200)
- [ ] T118 [P] [US7] Create `UI_UITEST/projects/ProjectsScreenTest.kt` (six cards: demo badge, active badge with Resume, with and without description/key; search with results and without; filter chips; sort; menu open; dialogs), `ProjectCardTest.kt` (merged semantics, hover elevation, focus, Enter opens), `ProjectOverviewChartTest.kt` (48 bars at compact, a line at expanded, tints per span, empty project)

### Implementation for User Story 7

- [ ] T119 [P] [US7] Create `UI/projects/components/ProjectCard.kt`, `ProjectBadge.kt`, `ProjectStat.kt`, `ProjectCardMenu.kt`, `ProjectOverviewChart.kt`, `LibrarySearchField.kt`, `LibraryFilterChips.kt`, `SortMenu.kt`, `RelativeTime.kt`, `RenameProjectDialog.kt`, `DescribeProjectDialog.kt`, `KeyPickerDialog.kt`, `DeleteProjectDialog.kt`; T118's card and chart tests pass
- [ ] T120 [US7] Create `UI/projects/ProjectsViewModel.kt`, `ProjectsUiState.kt`, `ProjectsCallbacks.kt`, `ProjectsLayout.kt`, `ProjectsCopy.kt`, `ProjectsDialog.kt`, `ProjectsScreenContent.kt`, `ProjectsScreen.kt`; replace the placeholder in `DestinationContent.kt`; update `DestinationTest`; T116–T118 pass
- [ ] T121 [US7] Prove portability: copy a project folder from the Pixel to the iPhone simulator's documents (and the reverse) and open it (identical arrangement and sound); run the force-quit loop of quickstart §7 step 4 twenty times on each platform; seed 200 projects with a debug launch argument (`tembra.projects.seed=200`, parsed like the existing debug extras) and time `timeline.libraryOpen` (≤ 1 s) and a search keystroke (one frame) on the Pixel 6 Pro and the iPhone 14 (SC-010); record results in `screenshots/NOTES.md`; screenshots `android-compact-library.png`, `android-expanded-library.png`, `ios-expanded-library.png`, `android-compact-library-search-empty.png`

**Checkpoint**: projects persist, reopen and are managed from the library.

---

## Phase 10: User Story 8 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

**Goal**: the four layouts verified on every device and simulator, the keyboard map, hover and tooltips, screen-reader semantics, the largest font and reduce motion (spec FR-055–FR-060).

**Independent Test**: quickstart §9 on every attached device and simulator, with the state matrix of FR-062 captured.

### Tests for User Story 8 (write first, watch them fail)

- [ ] T122 [P] [US8] Create `UI_UITEST/timeline/TimelineKeyboardTest.kt` (space, Home, ←/→ grid step, Shift+←/→ bar, S, Delete/Backspace, undo/redo shortcuts, +/−, M/O, Tab order toolbar → headers → clips → transport; declare per lane whether keyboard tests run, never a silent return) and `UI_UITEST/projects/ProjectsKeyboardTest.kt`
- [ ] T123 [P] [US8] Create `UI_UITEST/timeline/TimelineSemanticsTest.kt` (track, clip, playhead, readout, master volume descriptions and `progressBarRangeInfo`; `testTag` on every control; meters absent) and `UI_UITEST/timeline/TimelineLargeFontTest.kt` (font scale 2.0: no clipped text, chips wrap, readout intact, three lanes visible at 360 dp)

### Implementation for User Story 8

- [ ] T124 [US8] Implement the keyboard map and focus order in `UI/timeline/TimelineKeyHandling.kt` (a `Modifier.onPreviewKeyEvent` owner on the screen root dispatching to callbacks; `compose-focus-navigation`), tooltips on every icon-only control (`TembraTooltip`), hover states on clips (`ClipPainter` hover tint) and the ruler's hover time; T122, T123 pass
- [ ] T125 [US8] Capture the full matrix of spec FR-062 on Android (Pixel 6 Pro, Pixel Fold emulator tabletop and open, desktop-mode 1200 dp window) and iOS (iPhone 17, iPhone 14, iPad, iPhone Duo end rail): demo stopped and playing, widest and closest zoom, selected clip and split result, grid picker open, settings screen and inline, tempo dialog, metronome sheet, empty project, unavailable clip, import refusal, stop-and-leave dialog, library with six cards, card menu, search with/without results, largest font; TalkBack and VoiceOver walkthroughs noted; everything under `specs/009-timeline-arranger/screenshots/` with `NOTES.md` rows

**Checkpoint**: every layout and input path verified on every available target.

---

## Phase 11: Polish & Cross-Cutting (benchmark journey, baseline profile, docs, licences, sessions)

**Purpose**: the instrumentation the constitution requires, the regenerated profile, documentation and the labelled sessions (contracts/perf-instrumentation.md; quickstart §10).

- [ ] T126 [P] Create `PERF/journeys/TimelineJourney.kt` (the ten steps of contracts/perf-instrumentation.md with `UiObject2.pinchOpen/pinchClose` on `timeline.lane.0`), `PERF/TimelineJourneyBenchmark.kt`, add `"timeline"` to `PERF/PerfMetrics.sectionsOf` with the ten sections, `TimelineJourney().run(device)` to `BaselineProfileGenerator.journeys()`, `fun timeline()` to `ProfileComparisonBenchmark.kt`; extend `PERF/journeys/StressJourney.kt` (open the demo, play with the click on, scroll five seconds, confirm the stop-and-leave dialog by `dialog.stopAndLeave.confirm`, then the existing steps, `writeHealth` last)
- [ ] T127 [P] Create `IOSPERF/Journeys/TimelineJourney.swift` and `IOSPERF/TimelinePerfTests.swift`; add the `"timeline"` case and a `section(name, category:)` variant (engine names on `"engine"`) to `IOSPERF/Metrics.swift`; extend `IOSPERF/Journeys/StressJourney.swift` in parallel with Android; add the files to `app/iosApp/iosApp.xcodeproj/project.pbxproj`; both journeys wait for `screen.timeline`'s `stateDescription == "ready"` before playing
- [ ] T128 [P] Pipeline: add `timeline` to `PIPE/config.py` `DEFAULT_JOURNEYS`, `app/tracepulse.yml`, `PIPE/catalogue.py` `JOURNEYS_MEASURED`, `PIPE/orchestrate_android.py` `BENCHMARK_CLASS_OF`, `PIPE/collect_android.py` `_JOURNEY_OF_CLASS`/`_REQUIRED`/`_COMPARISON_METHODS`, `PIPE/collect_ios.py` `_TEST_JOURNEYS` and `order`, `PIPE/report.py` `_JOURNEY_ORDER`; add the three audio counters to `PIPE/catalogue.py` `_AUDIO` (gate 50 % / screening 10 %, `floor_abs` 100 µs for `avgUs`), `PIPE/traces_android.py` `AUDIO_COUNTER_KEYS`, `PIPE/collect_ios.py` `_HEALTH_KEYS`, `app/perf/tests/fixtures/engine-health.json`; update `app/perf/tests/test_config.py` and every test asserting the four-journey list; `uv run pytest` green
- [ ] T129 [P] Extend `UI/perf/EngineHealthJson.kt` and `UI/perf/AudioHealthTicker.kt` with `avgUs`, `starvation`, `clipsActive`; extend their tests
- [ ] T130 Append `timeline Laudio/tembra/ui/timeline/TimelineScreenKt;` and `projects Laudio/tembra/ui/projects/ProjectsScreenKt;` to `app/androidApp/baseline-profile-coverage.txt` (host lane red), then `cd app/perf && uv run tracepulse profile generate --device pixel6`; commit `app/androidApp/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`; run `profile verify` and `profile compare --journeys timeline` and record the summaries in `specs/009-timeline-arranger/evidence/profile.md`
- [ ] T131 Build the measurement artefacts through `gradle-run` (`./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf`) and run one trial round `uv run tracepulse run --device pixel6 --label 009-timeline --rounds 1 --no-publish --no-build --apk … --test-apk …`: the `timeline` journey completes, its sections and the three counters appear, `audio.starvation` and `audio.xruns` read 0; fix the journey or anchors until it does; repeat the trial on the iPhone 14 with `DEVELOPER_DIR` for Xcode 27.1 beta and `--no-build --xctestrun …`
- [ ] T132 [P] Run `./gradlew :shared:ui:assemble -PcomposeReports=true`; store the report under `specs/009-timeline-arranger/evidence/compose-report/` and explain every new composable that is not skippable in `evidence/compose-report.md`
- [ ] T133 [P] Documentation: `app/README.md` rows for the transport, clip streaming, mixer strips, the project document, repository, session, timeline, transport, track settings and projects packages and the demo audio script; `ENGINE/README.md` (transport, timeline and mixer modules, the streamer thread, the RT-source list, ABI 6 "Adding a function" note); `app/perf/README.md` ("What a round measures" with `timeline`, the `BENCHMARK_CLASS_OF` location fix, the stress change and the counters); `specs/007-performance-pipeline/contracts/metric-catalogue.md` and `tracing-facade.md` (journeys column, `transport` family, counters); `design/stitch/README.md` (tokens added for the track palette)
- [ ] T134 Run every lane one last time with `--warning-mode all` (`engineHostTest`, `checkAndroidHost`, `checkIos` on the iPhone 17 and iPad simulators, `checkAndroidDevice` on the Pixel 6 Pro, `:androidApp:connectedDebugAndroidTest`, `uv run pytest`): all green, zero compiler and lint warnings, one type per file, no composable nested in another's file (grep for a second `@Composable fun` per file under `ui/timeline`, `ui/transport`, `ui/tracksettings`, `ui/projects`)
- [ ] T135 Run quickstart §8 (interruptions and routing) on the Pixel 6 Pro and the iPhone 14 — incoming call, YouTube in split screen, headphones, Bluetooth, ten minutes screen off, the dialog — and the SC-013 walkthrough (a person who has not used the app creates a project, adds two labelled tracks, imports a file onto each, splits a clip, balances with gain and plays with the metronome on, timed, target under three minutes); record the outcomes in `specs/009-timeline-arranger/screenshots/NOTES.md`
- [ ] T136 Prepare the pull request: screenshots and `NOTES.md`, the compose report, the profile evidence, the trial-round summary, the principle each non-obvious decision serves (plan Complexity #1–#3, research R5, R8, R9, R13) and the skill contracts per public composable and coroutine owner; after the merge run the labelled sessions `uv run tracepulse run --device pixel6 --label 009-timeline --rounds 5`, the same on `iphone14` and on the Pixel 7 Pro by serial from the second Mac, then `uv run tracepulse sync && uv run tracepulse report --open`; attach the report and note the expected `stress` duration step at the label boundary

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies; T012 proves the toolchain before Phase 2.
- **Foundational (Phase 2)**: depends on Setup; blocks every story. The gates are T074 (host), T075 (simulator), T076 (device).
- **User stories (Phases 3–10)**: all depend on Phase 2. US1 is the MVP; US2 needs US1's screen skeleton (toolbar, headers); US3 needs US1's ruler and lanes; US4 needs US3's lane gestures; US5 needs US1's transport bar and US3's ruler pills; US6 needs US2's settings form; US7 needs only Phase 2 and US1's session wiring (it can run in parallel with US2–US6); US8 needs every screen.
- **Polish (Phase 11)**: depends on all stories; T130 (profile) after T125; T136 after everything.

### Within Each User Story

Tests first and failing → components (parallel) → ViewModel/intents → wiring → device check and screenshots.

### Parallel Opportunities

- Phase 1: T003–T011 in parallel after T001–T002.
- Phase 2 tests T013–T044 all in parallel; implementation groups in parallel: engine (T045–T047, T049 then T048, T050–T054), domain (T055–T061), seam (T062 then T063), data (T064–T065 then T066–T067), platform (T068), design system (T069); then T070–T073 in sequence.
- US1: T077–T080 parallel; T081–T085 parallel; then T086–T089.
- US7 can run alongside US2–US6 by a second worker.
- Polish: T126–T129, T132, T133 in parallel.

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "TimelineViewModelTest (US1 cases) in UI_TEST/timeline/TimelineViewModelTest.kt"
Task: "TransportBarTest, TimelineRulerTest, TrackLaneTest, PlayheadOverlayTest, TimelineScreenTest in UI_UITEST/…"
Task: "ScriptedTransportTelemetry and StopAndLeaveDialogTest in UI_UITEST/…"

# Components together:
Task: "TransportBar and its parts in UI/transport/"
Task: "TimelineRuler, TempoPill, SignaturePill in UI/timeline/components/"
Task: "ClipPainter, TrackLane, TrackRow, TrackList, TrackHeader(Compact) in UI/timeline/components/"
Task: "PlayheadOverlay in UI/timeline/components/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) and Phase 2 (Foundational) — the engine transport, the document, the session.
2. Phase 3 (US1): the demo plays with a truthful playhead on both platforms.
3. **STOP and VALIDATE**: quickstart §3, SC-001 to SC-003 recordings, the dialog and background checks.

### Incremental Delivery

US1 (play) → US2 (tracks and import) → US3 (ruler and zoom) → US4 (editing and undo) → US5 (metronome) → US6 (settings and effects placeholder) → US7 (library, can run in parallel from US2 onward) → US8 (adaptive proof) → Polish (journey, profile, docs, sessions). Each story is demonstrable on its own and never breaks the previous one.

### Parallel Team Strategy

After Phase 2: worker A on US1 → US3 → US4; worker B on US7 → US2 → US6; worker C on US5 after US1 and US3, then US8 and the perf journey.

---

## Notes

- [P] = different files, no dependency on an unfinished task.
- Every Gradle command goes through `gradle-run`; every Kotlin/Compose task through its routed skill.
- Commit after each task or logical group through the spec-kit hook; conventional messages.
- Research R5's MP3 verification (T003) is the one place a storage decision can still change; it touches `AudioDecoder` and `ProjectFolder` only.
- Never add pan, meters, a limiter, recording, effects fields or a second view: they belong to 010, 011 and later features.
- Follow-up outside this feature (analysis F23): the constitution's release-branch device smoke test (launch, open the demo project, press play, playhead moves) becomes buildable once US1 lands; it belongs to the CI work, not to these tasks.
