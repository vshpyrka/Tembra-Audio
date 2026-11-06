---

description: "Task list for feature 012, Mixer"
---

# Tasks: Mixer

**Input**: Design documents from `/specs/012-mixer/`

**Prerequisites**: plan.md, spec.md (clarified 2026-11-03, FR-001–FR-034 and FR-021a),
research.md (R1–R18), data-model.md (§1–§9), contracts/ (engine-c-abi-v8.md,
mixer-session.md, mixer-ui.md, perf-instrumentation.md, skills-and-audit.md), quickstart.md.
Constitution v1.3.5 (the mixer is feature 012).

**Tests**: Required. Constitution III (test-first, non-negotiable): every implementation task
is preceded by the test that fails without it. Lanes, always with `--warning-mode all` (and
the CI gates `-Ptembra.warningsAsErrors=true --warning-mode fail` once `main` is merged in)
from `app/` and always through the `gradle-run` skill wrapper (Constitution VIII):
`./gradlew engineHostTest` (Catch2, host), `./gradlew checkAndroidHost` (JVM), `./gradlew
checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` (simulator; also the iPad and the Duo),
`app/tools/gradlew-device.sh <serial> checkAndroidDevice` (one Android target per agent),
`app/tools/gradlew-device.sh <serial> :androidApp:connectedDebugAndroidTest`, `cd app/perf &&
uv run pytest`. Kotlin tests never link the engine: `FakeAudioEngine` (extended with the
mixer half), `FakeProjectRepository`, `FakeTimelinePreferences`, `ScriptedMixTelemetry`
(new), `FakeWindowCapabilities` (new). **Every device run applies quickstart §3 first**
(`tools/apply-device-run-conditions.sh <serial>`; the black `Theme.Tembra.DeviceTest`
window); the two sounding steps are quickstart §6 (pan on headphones) and §8 (loopback),
announced before they start.

**Main thread** (the owner's brief; contracts/skills-and-audit.md §C): every engine call the
mixer makes from the main thread is an atomic store or a seqlock copy; the Catch2 hold test
(T019) and the facade cost test (T026) are written before the ABI functions; the StrictMode
guard (T007) lands in Setup so every device lane reads logcat for it; the audit file is T124.

**Skills** (Constitution VIII; contracts/skills-and-audit.md §A, §B): before writing or
reviewing any Kotlin or Compose task below, route through `using-chrisbanes-skills` and load
the owner named for the task's package: `kotlin-api-design` for `Pan`, `FaderTaper`, `SetPan`,
the seam and `WindowCapabilities`; `kotlin-concurrency-and-flow` for the session and the
ViewModel (no new launch site; synchronous bounded setters); `kotlin-control-flow` for the
`when`s over `ProjectEdit`, `MixerHeight`, `StripForm`, `MeterSource`;
`compose-state-and-effects` for the ViewModel and screen wiring; `compose-component-design`,
`compose-performance`, `compose-animations`, `compose-focus-navigation` for every composable
in `ui.mixer` and `ui.designsystem`; `compose-ui-testing-patterns` for every `uiTest` class;
`gradle-run` for every Gradle command. Each phase ends with a review task that records its
findings in `specs/012-mixer/evidence/skill-review.md`.

**Delegation**: lane runs per target, screenshot captures and per-device perf rounds are
tasks for Sonnet-class sub-agents, one target each, given the serial, the exact commands,
quickstart §3 and the instruction to report failing test names verbatim; engine, session,
sheet and control code and every review stay with the main agent; component work may be
split across agents in detached worktrees on disjoint files with one integrator.

**Organization**: Setup merges `main` and adds the fixed names, the glyph, the StrictMode
guard and the measurement script. Foundational builds what every story needs behind tests:
the engine's lock-free parameters, pan law and meters under C ABI revision 8, the Kotlin
seam, `Pan`/`SetPan`/`FaderTaper`, the document field, the session's pan, metering and
telemetry. Each user story is then an independently testable increment: US1 (the sheet with
faders) is the MVP; US2 the meters; US3 pan; US4 mute, solo and the master; US5 windows,
pointer, keyboard and screen readers; US6 many tracks; US7 the separate window (cuttable).
Polish carries the journeys, the pipeline, the profile, the lanes and release builds on every
target, the level measurements, the audit and review evidence, docs, the Stitch sync and the
labelled sessions.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US7 from spec.md
- Every task names its files; paths are repository-relative

## Path Conventions

Gradle root `app/`. `CORE` = `app/shared/core/src/commonMain/kotlin/audio/tembra/core`,
`CORE_TEST` = `app/shared/core/src/commonTest/kotlin/audio/tembra/core`; `ENG` =
`app/shared/engine/src/commonMain/kotlin/audio/tembra/engine`, `ENG_TEST` =
`app/shared/engine/src/commonTest/kotlin/audio/tembra/engine`, `ENG_DEVTEST` =
`app/shared/engine/src/androidDeviceTest/kotlin/audio/tembra/engine`, `ENG_IOSTEST` =
`app/shared/engine/src/iosTest/kotlin/audio/tembra/engine`; `DATA_TEST` =
`app/shared/data/src/commonTest/kotlin/audio/tembra/data`; `UI` =
`app/shared/ui/src/commonMain/kotlin/audio/tembra/ui`, `UI_TEST` =
`app/shared/ui/src/commonTest/kotlin/audio/tembra/ui`, `UI_UITEST` =
`app/shared/ui/src/uiTest/kotlin/audio/tembra/ui`, `UI_IOS` =
`app/shared/ui/src/iosMain/kotlin/audio/tembra/ui`; `ENGINE` = `app/engine`; `MIXER` =
`app/engine/src/mixer`; `ETEST` = `app/engine/tests`; `PERF` =
`app/perf-benchmarks/src/main/kotlin/audio/tembra/perf`; `IOSPERF` =
`app/iosApp/TembraPerfTests`; `PIPE` = `app/perf/tracepulse`; `ANDROID` =
`app/androidApp/src/main/kotlin/audio/tembra/android`; `EVID` = `specs/012-mixer/evidence`;
`SHOTS` = `specs/012-mixer/screenshots`.

---

## Phase 1: Setup (branch, catalogue, anchors, glyph, StrictMode, tools)

**Purpose**: the merged base, the fixed name catalogues, the glyph, the debug guard and the
measurement script every later task depends on (research R11, R12, R15, R16;
contracts/perf-instrumentation.md, skills-and-audit.md).

- [X] T001 Merge `main` (with `010-timeline-recording` and `011-large-screens-desktop` merged) into `012-mixer`; resolve conflicts in favour of `main` for `.github/`, `app/androidApp/build.gradle.kts` (`com.tembraaudio`), `CLAUDE.md` and `app/README.md` while keeping this branch's constitution v1.3.5 and `specs/012-mixer/`; run `./gradlew checkAndroidHost` and `./gradlew engineHostTest` green before any other task; record the merge commit in `specs/012-mixer/screenshots/NOTES.md` (created)
- [X] T002 In `app/shared/engine/perf-catalogue.txt` add `TIMELINE_MIXER_OPEN timeline timeline.mixerOpen`, `TIMELINE_MIXER_CLOSE timeline timeline.mixerClose`, `TIMELINE_MIXER_FRAME timeline timeline.mixerFrame`, `TIMELINE_FADER_DRAG timeline timeline.faderDrag`, `TIMELINE_PAN_TURN timeline timeline.panTurn`, `TRANSPORT_MASTER_DRAG transport transport.masterDrag`, `ENGINE_PAN_APPLY engine engine.panApply`, `AUDIO_MASTER_CLIPS counter audio.masterClips`, `AUDIO_METER_FRAMES_DROPPED counter audio.meterFramesDropped` (one dot per section name); add the entries to `ENG/perf/PerfSection.kt` and `ENG/perf/PerfCounter.kt`; set `ETEST/trace_shim_test.cpp` to 49; run `./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true` and commit `ENGINE/include/tembra_trace_names.h`
- [X] T003 [P] In `UI/perf/PerfAnchors.kt` add `TIMELINE_MIXER = "timeline.toolbar.mixer"`, `TIMELINE_OVERFLOW_MIXER = "timeline.toolbar.overflow.mixer"`, `MIXER_SHEET = "mixer.sheet"`, `MIXER_HANDLE = "mixer.handle"`, `MIXER_STRIPS = "mixer.strips"`, `MIXER_STRIP_FADER_FIRST = "mixer.strip.fader.0"`, `MIXER_STRIP_PAN_FIRST = "mixer.strip.pan.0"`, `MIXER_STRIP_SOLO_FIRST = "mixer.strip.solo.0"`, `MIXER_STRIP_MUTE_FIRST = "mixer.strip.mute.0"`, `MIXER_STRIP_METER_FIRST = "mixer.strip.meter.0"`, `MIXER_STRIP_CLIP_FIRST = "mixer.strip.clip.0"`, `MIXER_STRIP_NAME_FIRST = "mixer.strip.name.0"`, `MIXER_MASTER_FADER = "mixer.master.fader"`, `MIXER_MASTER_METER = "mixer.master.meter"`, `MIXER_MASTER_CLIP = "mixer.master.clip"`, `MIXER_WINDOW_OPEN = "mixer.window.open"`, `TRACK_SETTINGS_PAN = "trackSettings.pan"` and to `all`; raise the count in `UI_TEST/perf/PerfAnchorsCatalogueTest.kt` by 17; mirror them in `PERF/journeys/Anchors.kt` and `IOSPERF/Anchors.swift`
- [X] T004 [P] Run `app/tools/fetch-material-symbols.sh instant_mix` (fall back to the existing `TembraIcon.Tune` if the pinned commit lacks the glyph, noting it in `SHOTS/NOTES.md`); add `TembraIcon.InstantMix` in `UI/theme/TembraIcon.kt`; extend `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/IconAssetsTest.kt` with the file
- [X] T005 [P] In `ENGINE/CMakeLists.txt` confirm the `src/mixer/*.cpp` glob covers the new files; in `ETEST/CMakeLists.txt` add `pan_law_test.cpp`, `mix_params_test.cpp`, `mixer_abi_test.cpp`, `mix_meter_test.cpp` to the test list and `src/mixer/mix_meter.cpp` to `TEMBRA_RT_SOURCES`; set `ETEST/engine_info_test.cpp` to expect ABI 8 and version "0.7.0" (red until T034)
- [X] T006 [P] Add `UI/mixer/MixerCopy.kt` ("Mixer", "Open mixer", "Close mixer", "Gain", "Pan", "Master volume", "Level", "VU", "Clipped, tap to reset", "Open in a window", "C", "L %d", "R %d") and `UI/mixer/MixerTestTags.kt` (`sheet`, `handle`, `strips`, `strip(i)`, `fader(i)`, `pan(i)`, `solo(i)`, `mute(i)`, `meter(i)`, `clip(i)`, `name(i)`, `masterFader`, `masterMeter`, `masterClip`, `windowOpen`, dotted like `TimelineTestTags`); add `pan` to `UI/tracksettings/TrackSettingsTestTags.kt`
- [X] T007 [P] In `ANDROID/TembraApplication.kt` install `StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())` when `BuildConfig.DEBUG`; add `app/androidApp/src/test/kotlin/audio/tembra/android/StrictModePolicyTest.kt` asserting the policy is installed in debug and absent in release (contracts/skills-and-audit.md §C part 4)
- [X] T008 [P] Add `app/tools/measure-pan-law.py` (the alignment script's sibling: reads two-channel WAV recordings, reports per-recording `L² + R²` in dB, the right channel's level for a hard-left recording, the largest sample step and a click count for a sweep; `--json`) with `app/tools/tests/test_measure_pan_law.py` on synthetic files; add its row to `app/README.md`

---

## Phase 2: Foundational (engine parameters, pan law, meters, ABI 8, seam, document, session)

**Purpose**: everything more than one story needs, each guarded by a test written first. No
stream opens on a host machine: the renderer runs against `FakeAudioDevice`; the device and
simulator lanes cover the real engine (research R4–R7, R12; contracts/engine-c-abi-v8.md,
mixer-session.md).

**⚠️ CRITICAL**: no user story work starts until T041, T042 and T043 are green.

### Tests (write first, watch them fail)

- [X] T009 [P] `ETEST/pan_law_test.cpp`: `PanLaw::gains` mono centre 1/1, full left √2/0, full right 0/√2, `L² + R² = 2` within 1e-5 at nine positions, monotonic; stereo gains ≤ 1 and 1/1 at the centre
- [X] T010 [P] `ETEST/mix_params_test.cpp`: a setter's values are visible to a reader after `version` changes; `set_track` rejects index ≥ 64, non-finite, gain outside 0..2.0, pan outside −1..1; the metering flag
- [X] T011 [P] `ETEST/mixer_abi_test.cpp`: with the transport open on `FakeAudioDevice` and `EngineCore::commands_` held from a second thread for 200 ms (a `TEMBRA_TESTING` seam `holdCommandsForTest`), `tembra_mixer_set_pan` returns `TEMBRA_OK` within 1 ms; `TEMBRA_ERR_STATE` when closed; index 64 rejected by the setters and accepted by `tembra_mixer_reset_clip`
- [X] T012 [P] `ETEST/mix_meter_test.cpp`: a −6 dBFS sine reads peak 0.5 and rms 0.354 within 1 %; the 64 ms history keeps a one-block transient for 12 blocks of 5 ms; the latch lights at 0.9999 and clears only on reset; the 300 ms window reaches 95 % of a step within 0.9 s; metering off publishes zeros and performs no measurement (a call counter); the record is published once per block across a segment split
- [X] T013 [P] Extend `ETEST/track_strip_test.cpp`: two ramps land in 10 ms; a pan step's largest per-sample step ≤ the ramp's; a centred mono track renders bit-exact to revision 7; a stereo source panned +50 % attenuates the left and never boosts the right
- [X] T014 [P] Extend `ETEST/master_bus_test.cpp`: `master_clips` counts a clamped block once; the master latch lights on a clamp and clears on reset
- [X] T015 [P] Extend `ETEST/transport_renderer_test.cpp`: a `renderStereo(frames)` helper beside `render`; a track panned hard left renders silence on the right within −60 dB; the meter record is published every block and zero when metering is off; `applyTracks` picks up a `MixParams` change on the next block
- [X] T016 [P] Extend `ETEST/audio_health_test.cpp` (`sizeof == 60`, `offsetof(master_clips) == 56`) and `ETEST/engine_core_state_test.cpp` (the mixer setters return `TEMBRA_ERR_STATE` before `openTransport`; `setTransportArrangement` seeds `MixParams`)
- [X] T017 [P] `CORE_TEST/project/PanTest.kt` (`readout` "C"/"L 35"/"R 20", `clamped`, `snapToCentre(5)`, `toLinear`), `CORE_TEST/project/FaderTaperTest.kt` (the knots 1.0 → +6, 0.7 → 0, 0.5 → −12, 0.2 → −40, 0.02 → −60, below → silence; `throwOf(dbOf(t)) == t` within 1e-4; monotonic; `ticks`), `CORE_TEST/project/ProjectEditorSetPanTest.kt`, `CORE_TEST/project/ProjectValidatorTest.kt` (+ `|percent| > 100` rejected)
- [X] T018 [P] Extend `DATA_TEST/projects/ProjectDocumentCodecTest.kt`: the golden with `"pan": 0` on every track (update `DATA_TEST/projects/DemoProjectGolden.kt` and `specs/009-timeline-arranger/fixtures/demo-project.json`), `oldDocumentReadsCentred` (a revision-1 document without the key), round trip of `Pan(-35)`
- [X] T019 [P] `ENG_DEVTEST/NativeAudioEngineMixerCostTest.kt` and `ENG_IOSTEST/NativeAudioEngineMixerCostTest.kt`: with the transport open, 1,000 `setPan`, `setTrack` and `readMix` calls on the main thread: median < 50 µs, max < 1 ms; extend `NativeAudioEngineBlockingDispatchTest` in both: the mixer setters and `readMix` do not dispatch (`CountingDispatcher.dispatches` unchanged)
- [X] T020 [P] `ENG_DEVTEST/NativeAudioEngineMixerTest.kt` and `ENG_IOSTEST/NativeAudioEngineMixerTest.kt`: a demo clip panned hard left reads `readMix` right-side peak < −60 dB after 200 ms; `setMetering(false)` zeros the snapshot; `resetMixClip` clears a latch
- [X] T021 [P] Extend `ENG_TEST/FakeAudioEngineTest.kt`: `setPan`, `setMetering`, `readMix` through `scriptedMix`, `resetMixClip` recorded in `calls`
- [X] T022 [P] Extend `UI_TEST/session/ProjectSessionTest.kt`: `aRunOfPanChangesIsOneUndoStepUntilTheGroupCloses`, `aPanEditUsesThePanFastPathOnly` (no `setArrangement`), `pansArePushedAfterAnArrangement`, `meteringIsOnWhileAnyHolderIsOpen` (0→1 on, 1→0 off, reset on close), `playFromStopClearsTheLatches`, `mixTelemetryReadsOncePerFrame`

### Implementation

- [X] T023 [P] `MIXER/pan_law.h`: `PanLaw::gains(float pan, bool stereo, float& left, float& right)` per contracts/engine-c-abi-v8.md (θ = (pan + 1)·π/4; mono √2·cos θ / √2·sin θ; stereo min(1, …))
- [X] T024 [P] `MIXER/mix_params.h`: `MixParams` with 64 × (`std::atomic<float> gain`, `std::atomic<float> pan`, `std::atomic<uint32_t> audible`), `std::atomic<uint64_t> version`, `std::atomic<uint32_t> metering`; `set(index, gain, pan, audible)`, `setPan`, `snapshotInto(TrackTargets&)`; static_assert `std::atomic<float>::is_always_lock_free`
- [X] T025 [P] `MIXER/recent_peak.h` (a 16-entry per-block peak history returning the max, the capture meter's 64 ms rule generalised), `MIXER/mix_record.h` (`MixRecord` per the contract, about 1.2 KB), `MIXER/mix_meter.{h,cpp}` (`measureStrip`, `measureMaster`, `publish`, `reset`, `requestReset(index)`; no allocation, no lock)
- [X] T026 `MIXER/track_strip.{h,cpp}`: two `GainRamp`s, `setTarget(gain, pan, audible, stereo)` through `PanLaw`, `process` applying the side gains; `MIXER/master_bus.{h,cpp}`: `clampEngaged()` per block and the latch hook (T013, T014 green)
- [X] T027 `ENGINE/src/transport/transport_renderer.{h,cpp}`: `MixParams` and `Seqlock<MixRecord>` members; `applyTracks` reads the params when `version` changed; `measureStrip` after each `strips_[t].process`, `measureMaster` after the bus's clamp (before the click), `publish` once per block; metering off skips all three (T012, T015 green)
- [X] T028 `ENGINE/src/core/engine_core.{h,cpp}`: `setMixTrack`, `setMixPan`, `setMixMaster`, `setMetering`, `readMix`, `resetMixClip` without `commands_` (state checked through the atomic state); `setTransportArrangement` seeds `MixParams`; the `holdCommandsForTest` seam under `TEMBRA_TESTING`; `ENGINE/src/core/audio_health.{h,cpp}` + `master_clips` (T011, T016 green)
- [X] T029 `ENGINE/include/tembra_engine.h`: revision 8 with `tembra_mixer_set_track`, `tembra_mixer_set_pan`, `tembra_mixer_set_master`, `tembra_mixer_set_metering`, `tembra_mixer_read`, `tembra_mixer_reset_clip`, `tembra_mix_snapshot`, `TEMBRA_MIXER_MASTER_INDEX`, the health field; `MIXER/mixer_abi.cpp` forwarders with argument checks and the `engine.panApply` trace scope; `ENGINE/src/transport/transport_abi.cpp`: `set_track`/`set_master` forward to the mixer path; `ENGINE/src/engine_info.cpp` 0.7.0 / 8 (T005, T010 green); record the revision in `ENGINE/README.md` (010's revision-7 contract stays as written)
- [X] T030 `ENGINE/android/src/main/cpp/tembra_jni.cpp`: `nativeMixerSetTrack`, `nativeMixerSetPan`, `nativeMixerSetMaster`, `nativeMixerSetMetering`, `nativeMixerRead(IntArray, FloatArray, FloatArray, FloatArray, LongArray)` with `Set*ArrayRegion` only, `nativeMixerResetClip`; `app/shared/engine/src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt`: the externs, the pre-allocated arrays, `setPan`, `setMetering`, `readMix`, `resetMixClip`; `app/shared/engine/src/androidDeviceTest/.../NativeAudioEngineTest.kt` ABI 8
- [X] T031 `app/shared/engine/src/iosMain/kotlin/audio/tembra/engine/NativeAudioEngine.ios.kt`: one `nativeHeap.alloc<tembra_mix_snapshot>()`, the six calls; `app/shared/engine/src/iosTest/.../NativeAudioEngineTest.kt` ABI 8 (T019, T020 green on both lanes after T033)
- [X] T032 [P] `ENG/MixSnapshot.kt` (data model §2: `trackCount`, `peak: FloatArray(128)`, `rms: FloatArray(128)`, `masterPeak`, `masterRms`, `clipMask: Long`, `masterClipped`, `block`, `metering`, `copyFrom`, `peakDb`, `rmsDb`), `ENG/perf/AudioHealth.kt` + `masterClips`, `ENG/perf/AudioHealthPublisher.kt` + the two counters
- [X] T033 `ENG/TimelineAudio.kt`: `setPan(index, pan)`, `setMetering(enabled)`, `readMix(into)`, `resetMixClip(index)` with their KDoc ("lock-free; safe every frame; main thread only, one scratch per engine"); `ENG/FakeAudioEngine.kt`: `pans`, `metering`, `scriptedMix`, the `calls` entries (T021 green)
- [X] T034 [P] `CORE/project/Pan.kt` (`@JvmInline @Serializable value class Pan(val percent: Int)`; `Centre`, `MAX = 100`, `readout`, `clamped`, `snapToCentre(within = 5)`, `toLinear`), `CORE/project/SetPan.kt`, `CORE/project/FaderTaper.kt` (the knots and `ticks`), `CORE/project/Track.kt` (`pan: Pan = Pan.Centre` appended after `clips`), `CORE/project/ProjectEditor.kt` (`setPan`), `CORE/project/ProjectValidator.kt` (the range) (T017, T018 green)
- [X] T035 `UI/session/MixTelemetry.kt` (`fun interface MixTelemetry { fun read(frameNanos: Long, into: MixSnapshot) }`); `UI/session/ProjectSession.kt`: `coalesces` + `SetPan`, `applyToEngine` + `is SetPan -> audio.setPan(index, pan.toLinear())`, `pushPans()` after `setArrangement`, `setMetering(on)` refcount, `mixTelemetry` memoised on the frame time, `resetTrackClip`, `resetMasterClip`, latch reset on `play()` from a stop; `UI/session/ArrangementMapper.kt` unchanged (T022 green)
- [X] T036 Append the `pan` row to `specs/009-timeline-arranger/contracts/project-document.md` (field 9 of `Track`, after `clips`, integer −100…100, default 0, added by feature 012) and a line to `app/README.md`'s document section
- [X] T037 [P] Extend `UI/timeline/TrackUi.kt` with `pan: Pan` (and `description` with the pan readout when not centred), `UI/timeline/TimelineViewModel.kt`'s `mapState` to fill it; extend `UI_TEST/timeline/TimelineUiStateTest.kt` for the field
- [X] T038 Run `./gradlew engineHostTest` and `./gradlew checkAndroidHost` through `gradle-run`; fix until green with zero warnings
- [ ] T039 Run `./gradlew checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` (T019, T020, T031 on the simulator) and `tools/gradlew-device.sh 19021FDEE001AH checkAndroidDevice` (the Pixel 6 Pro, run conditions first) through `gradle-run`; fix until green
- [X] T040 Skill review of Phase 2 (contracts/skills-and-audit.md §B): route `CORE/project/*`, `ENG/*`, `UI/session/*` changes through `using-chrisbanes-skills` (`kotlin-api-design`, `kotlin-concurrency-and-flow`, `kotlin-control-flow`); record findings and fixes in `EVID/skill-review.md` (created)
- [X] T041 Checkpoint gate: `engineHostTest` green (T009–T016)
- [X] T042 Checkpoint gate: `checkAndroidHost` green (T017, T018, T021, T022, T037)
- [ ] T043 Checkpoint gate: `checkIos` on the iPhone 18 Pro and `checkAndroidDevice` on the Pixel 6 Pro green (T019, T020)

**Checkpoint**: the engine pans, meters and takes lock-free parameters behind ABI 8 on both
platforms; the document holds pan; the session edits, meters and reads it — user stories can
begin.

---

## Phase 3: User Story 1 - Balance the tracks with faders (Priority: P1) 🎯 MVP

**Goal**: the Mixer button opens a two-height sheet over the lanes with one strip per track and
a pinned master frame; faders with the console taper move the sound at once without clicks,
double-tap to unity, two at a time, each gesture one undo step, saved with the project (spec
US1; FR-001 to FR-005, FR-016 to FR-018, FR-024, FR-031).

**Independent Test**: quickstart §5 on the Pixel 6 Pro and the iPhone 18 Pro simulator: two
faders under two fingers, a double tap, two undo steps, a reopen with the levels kept.

### Tests for User Story 1 (write first, watch them fail)

- [X] T044 [P] [US1] `UI_UITEST/designsystem/FaderTest.kt`: a drag moves the value 1:1 in throw space through `FaderTaper`; the drag keeps following when the pointer leaves the rail sideways; a double tap calls `onValue(0 dB)` and animates (frozen clock); `onSettled` once per gesture; wheel ±0.5 dB; arrows, Page, Home; `progressBarRangeInfo` and `setProgress` in dB; `stateDescription` "+0.0dB"; hover and focus through an injected `MutableInteractionSource`; reduce motion jumps
- [X] T045 [P] [US1] `UI_UITEST/mixer/MixerStripTest.kt` (compact form: number, Solo, Mute, fader, name; every target ≥ 44 dp; `focusGroup` order Solo → Mute → fader; tags) and `UI_UITEST/mixer/MixerSheetTest.kt` (opens at `Half` with the lanes visible above, drags to `Full` and back, a swipe down from `Half` closes, Back closes, the handle label "Mixer", the master frame pinned at the right, three strips plus the master at 360 dp, the transport bar's play button still clickable while open)
- [X] T046 [P] [US1] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `toggleMixerOpensAtHalfAndMeters` (`setMetering(true)` called synchronously on the test dispatcher), `toggleAgainClosesAndStopsMetering`, `xTogglesTheMixer`, `stripsFollowTheTracks` (add, remove, rename), `gestureEndClosesTheGroup`, `aFaderDragIsOneUndoStep`
- [X] T047 [P] [US1] Extend `UI_UITEST/timeline/TimelineScreenTest.kt` (the toolbar's Mixer button at 360 and 840 dp; the sheet over the lanes; the snackbar host padded above it) and `UI_UITEST/timeline/TimelineKeyHandlingTest.kt` (X toggles; declared per lane)

### Implementation for User Story 1

- [X] T048 [P] [US1] `UI/mixer/MixerHeight.kt`, `UI/mixer/StripForm.kt`, `UI/mixer/MeterSource.kt`, `UI/mixer/MixerAnchors.kt` (half and full heights from the layout and the lanes box: Compact/Medium half = max(240 dp, 45 %); Expanded half = max(240 dp, lanes − 192 dp); Landscape full only), `UI/timeline/MixerViewState.kt` (`open = false`, `height = Half`), `UI/mixer/MixerUi.kt`, `UI/mixer/StripUi.kt`, `UI/mixer/MasterUi.kt` (data model §4, all `@Immutable`)
- [X] T049 [US1] `UI/timeline/TimelineViewState.kt` + `mixer`; `UI/timeline/TimelineUiState.kt` + `mixer: MixerUi`; `UI/timeline/TimelineCallbacks.kt` + `onMixerToggle`, `onMixerHeight`, `onPan`, `onGainSettled`, `onPanSettled`, `onMasterSettled`, `onResetTrackClip`, `onResetMasterClip`, `onStripSelect`, `onOpenMixerWindow`; `UI/timeline/TimelineViewModel.kt`: `toggleMixer`, `setMixerHeight`, `gestureSettled`, `mapState` deriving `MixerUi` (strips from tracks, `form` from layout and height, `anchors`), `lanesHeight` beside `barWidth`; `UI/timeline/TimelineScreen.kt` wires the callbacks (T046 green)
- [X] T050 [US1] `UI/designsystem/Fader.kt` per contracts/mixer-ui.md (vertical rail, lit track, 44 × 28 dp handle, `remember`ed ticks, `awaitEachGesture` drag in throw space, double tap through an `Animatable`, wheel, keys, semantics, hover tint, focus ring, `PerfTrace.animation(TIMELINE_FADER_DRAG)` over the gesture; percent mode for the master) (T044 green)
- [X] T051 [P] [US1] `UI/mixer/MixerHandle.kt` (the 32 dp "Mixer" grabber, focusable, Enter toggles the height; the window button slot hidden until US7) and `UI/mixer/MixerStripRow.kt` (`LazyRow` keyed by id, 72 dp items, `mixer.strips`)
- [X] T052 [US1] `UI/mixer/MixerStrip.kt` (compact form first: tint band, number, `MuteSoloButton` × 2 at 36 dp, `Fader`, the name; `focusGroup`; `selectable` on number and name; `key(id)`; the meter and VU slots arrive in US2, the knob in US3) and `UI/mixer/MasterStrip.kt` frame (label "Master", the percent `Fader`; its meter in US2) (T045 strip part green)
- [X] T053 [US1] `UI/mixer/MixerSheet.kt`: `anchoredDraggable` over `Hidden/Half/Full` from `MixerAnchors`, `offset { }` from the drag state, a keyed `LaunchedEffect` syncing `MixerUi.height` → `animateTo` (none under reduce motion), `onMixerHeight` on settle, `onMixerToggle` on `Hidden`, `BackHandler(ui.open)`, `PerfTrace.section(TIMELINE_MIXER_CLOSE)` around the close, `paneTitle` "Mixer", tag `mixer.sheet`; the per-frame `drawWithContent` read lands in US2 (T045 sheet part green)
- [X] T054 [US1] `UI/timeline/components/TimelineToolbar.kt`: the Mixer `TransportButton` (`TembraIcon.InstantMix`, checked = open, `timeline.toolbar.mixer`) in every width plus the compact overflow entry `timeline.toolbar.overflow.mixer`; `UI/timeline/TimelineScreenContent.kt`: the sheet inside the lanes box aligned to its bottom, `lanesHeight` reported to the ViewModel, the snackbar host padded above the open sheet; `UI/timeline/TimelineKeyHandling.kt`: `Key.X` → `onMixerToggle` (T047 green)
- [ ] T055 [US1] Run `checkAndroidHost`, `checkIos` (iPhone 18 Pro) and `checkAndroidDevice` (Pixel 6 Pro) through `gradle-run`; quickstart §5 steps 1–4 by hand on the Pixel 6 Pro and the simulator; capture `SHOTS/android/pixel6pro/mixer-half-360.png` and `mixer-full-360.png` with `android screen capture`
- [X] T056 [US1] Skill review of Phase 3: `UI/mixer/*`, `UI/designsystem/Fader.kt`, `UI/timeline/*` through `using-chrisbanes-skills` (`compose-component-design`, `compose-performance`, `compose-animations`, `compose-focus-navigation`, `compose-state-and-effects`, `compose-ui-testing-patterns`); findings into `EVID/skill-review.md`

**Checkpoint**: the mixer opens, faders mix the project on both platforms, every gesture is
one undo step — the MVP.

---

## Phase 4: User Story 2 - See the levels while the project plays (Priority: P1)

**Goal**: two-bar post-fader meters with hold and clip latch on every strip, a VU dial, the
master's summed meter with its own latch, the armed track's input meter in its strip, all read
once per frame in the draw phase (spec US2; FR-010 to FR-015, FR-032).

**Independent Test**: quickstart §7: meters move with their tracks, hold 1.5 s, the master
latch lights on a clip and clears on tap or play-from-stop, the armed strip shows the input
meter, everything falls silent within a second of a stop.

### Tests for User Story 2 (write first, watch them fail)

- [X] T057 [P] [US2] `UI_UITEST/mixer/ScriptedMixTelemetry.kt` (`silent`, `level(index, peakDb, rmsDb)`, `clipped(index)`, `master(peakDb, rmsDb, clipped)`, a block counter) and `UI_UITEST/designsystem/LevelMeterTest.kt`: silent, −20 dB, full scale → the latch; reset on tap; the hold after 1.5 s of frozen-clock advance; `meterBody` to −6 dB and `meterWarning` above; the master index 64; semantics "Level", "<n> dB", "clipped" once
- [X] T058 [P] [US2] `UI_UITEST/designsystem/VuDialTest.kt`: −18 dBFS rms → 0 VU; −38 → −20 VU pinned; −15 → +3 VU with the warning arc; smooth motion under the frame clock; `stateDescription`
- [X] T059 [P] [US2] Extend `UI_UITEST/mixer/MixerSheetTest.kt` (`drawWithContent` performs exactly one `MixTelemetry.read` per frame for any number of strips; `TIMELINE_MIXER_FRAME` wraps it; the mirror refreshes at 10 Hz) and `UI_UITEST/mixer/MixerStripTest.kt` (full form: VU above the fader, the meter beside it; an armed strip shows the input meter through `ScriptedInputTelemetry` and returns to the playback meter when disarmed)
- [X] T060 [P] [US2] Extend `UI_TEST/timeline/TimelineViewModelTest.kt` (`theArmedStripShowsTheInputMeter`, `resetTrackClipReachesTheEngine`, `resetMasterClipUsesIndex64`) and `UI_TEST/perf/` with `MeterFrameCounterTest.kt` (a frame later than twice the display period counts once)

### Implementation for User Story 2

- [X] T061 [P] [US2] `UI/designsystem/LevelMeter.kt` (two 6 dp bars in `drawBehind` reading the shared `MixSnapshot` through `LevelHold` and `LevelSmoother`, −60…0 dB, the hold mark, the `meterClip` square `clickable` → `onResetClip`, semantics from a `StripLevel`) and `UI/designsystem/VuDial.kt` (the half-dial and needle in `drawBehind`, −20…+3 VU with 0 VU at −18 dBFS, the warning arc) (T057, T058 green)
- [X] T062 [P] [US2] `UI/mixer/MixerLevelMirror.kt` and `UI/mixer/StripLevel.kt` (one `LaunchedEffect(open)` at 10 Hz filling a `SnapshotStateList<StripLevel>` plus the master; "clipped" announced once through a live region) and `UI/perf/MeterFrameCounter.kt` (the dropped-frame counter published through `AudioHealthPublisher` as `AUDIO_METER_FRAMES_DROPPED`); extend `UI/perf/AudioHealthTicker.kt` and `UI/perf/EngineHealthJson.kt` with the two counters
- [X] T063 [US2] `UI/mixer/MixerSheet.kt`: the `drawWithContent` per-frame read inside `PerfTrace.section(TIMELINE_MIXER_FRAME)` with `rememberFrameTick(ui.open)`; `traceFirstDraw(TIMELINE_MIXER_OPEN)` on the first strip's meter; `UI/mixer/MixerStrip.kt`: the `LevelMeter` beside the fader in both forms, the `VuDial` in `Full`, `MeterSource.Input` swapping in the input meter path (`inputTelemetry` from feature 010); `UI/mixer/MasterStrip.kt`: the master `LevelMeter` and latch (T059 green)
- [X] T064 [US2] `UI/timeline/TimelineViewModel.kt`: `resetTrackClip`, `resetMasterClip`, `meterSource` per strip from the armed track; `UI/timeline/TimelineScreen.kt` passes `session.mixTelemetry` and the input telemetry into the sheet (T060 green)
- [ ] T065 [US2] Run the three lanes through `gradle-run`; quickstart §7 by hand on the Pixel 6 Pro with headphones at a low volume (announced); capture `SHOTS/android/pixel6pro/mixer-clipped-360.png` and `mixer-armed-360.png`
- [X] T066 [US2] Skill review of Phase 4 (`compose-performance` on the draw path and the mirror, `compose-component-design`, `compose-ui-testing-patterns`); findings into `EVID/skill-review.md`

**Checkpoint**: the musician sees what is too hot and what is buried while the project plays.

---

## Phase 5: User Story 3 - Place the tracks in the stereo field with pan (Priority: P2)

**Goal**: the pan knob in every strip, the pan slider in the track settings, the readout in the
expanded header, centre snap, double tap, keys, one undo step per turn, saved with the project
(spec US3; FR-006 to FR-009, FR-027, FR-028).

**Independent Test**: quickstart §6 on headphones (announced): hard left and right at equal
loudness, snap and double tap to the centre, undo, the settings slider and the header readout
agree; an old document opens centred.

### Tests for User Story 3 (write first, watch them fail)

- [X] T067 [P] [US3] `UI_UITEST/designsystem/PanKnobTest.kt`: a vertical drag of 2 dp per 1 %; a release within 5 % settles on "C"; a double tap centres; wheel ±1 %; arrows ±1 %, Page ±10 %, Home centre; `progressBarRangeInfo` −100…100 and `setProgress`; `stateDescription` "L 35"; hover and focus
- [X] T068 [P] [US3] Extend `UI_UITEST/tracksettings/TrackSettingsFormTest.kt` (the Pan row after Gain, `trackSettings.pan`, readout) and `UI_UITEST/timeline/TrackHeaderTest.kt` (row 2 shows `PanReadout` after `GainReadout` in the 200 dp column; nothing clips at the largest font)
- [X] T069 [P] [US3] Extend `UI_TEST/timeline/TimelineViewModelTest.kt` (`setPanEditsTheTrack`, `aKnobTurnIsOneUndoStep`) and `UI_TEST/tracksettings/TrackSettingsViewModelTest.kt` (`setPan`, `panSettled`)

### Implementation for User Story 3

- [X] T070 [US3] `UI/designsystem/PanKnob.kt` per contracts/mixer-ui.md (`PerfTrace.animation(TIMELINE_PAN_TURN)` over the gesture) (T067 green)
- [X] T071 [P] [US3] `UI/tracksettings/PanSlider.kt` (a `TembraSlider` −100…100 with the readout), `UI/tracksettings/TrackSettingsUiState.kt` + `pan`, `UI/tracksettings/TrackSettingsCallbacks.kt` + `onPan`, `onPanSettled`, `UI/tracksettings/TrackSettingsViewModel.kt` + `setPan`/`panSettled`, `UI/tracksettings/TrackSettingsForm.kt` the row after Gain (T068 form part, T069 settings part green)
- [X] T072 [P] [US3] `UI/timeline/components/PanReadout.kt` (the `GainReadout` pattern: vertical drag, double tap to centre, keys, semantics) and `UI/timeline/components/TrackHeader.kt` row 2 (T068 header part green)
- [X] T073 [US3] `UI/mixer/MixerStrip.kt`: the `PanKnob` in the `Full` form above Solo/Mute; `UI/timeline/TimelineViewModel.kt`: `setPan(id, pan)` → `edit(SetPan)`, `gestureSettled` (T069 green)
- [ ] T074 [US3] Run the three lanes through `gradle-run`; quickstart §6 by hand (announced) on the Pixel 6 Pro and the iPhone 18 Pro simulator; capture `SHOTS/android/pixel6pro/track-settings-pan-360.png`
- [X] T075 [US3] Skill review of Phase 5 (`compose-component-design`, `compose-focus-navigation`, `kotlin-control-flow`); findings into `EVID/skill-review.md`

**Checkpoint**: tracks sit left, centre or right without a jump in loudness.

---

## Phase 6: User Story 4 - Mute, solo and the master from one surface (Priority: P2)

**Goal**: Solo and Mute in the strips are the headers' S and M; the master strip's percentage
fader agrees with the speaker popup; tapping a strip selects its track and scrolls the lane
into view; "M"/"O" act on the focused strip (spec US4; FR-010, FR-011, FR-023, FR-028, SC-010).

**Independent Test**: quickstart §5 steps 5–7; the randomised toggle test of SC-010 green.

### Tests for User Story 4 (write first, watch them fail)

- [ ] T076 [P] [US4] `UI_UITEST/mixer/MasterStripTest.kt` ("Master" label; the percent fader 0…100 with steps of 1, Page 10; readout "80 %"; `mixer.master.fader`; the master meter and latch; no knob or toggles) and extend `UI_UITEST/mixer/MixerStripTest.kt` (Solo lit lavender when soloed, Mute dim; "M"/"O" on the focused strip call the strip's callbacks; tapping the name calls `onStripSelect`)
- [ ] T077 [P] [US4] Extend `UI_TEST/timeline/TimelineViewModelTest.kt`: `stripAndHeaderStatesNeverDisagree` (200 randomised mute/solo toggles across `toggleMute`/`toggleSolo` and the strips' callbacks, compared through `TrackUi` and `StripUi`), `masterDragIsOneUndoStep`, `selectStripSelectsTheTrackAndRequestsAScroll`, `openingTheMixerScrollsToTheSelectedStrip`

### Implementation for User Story 4

- [ ] T078 [US4] `UI/mixer/MasterStrip.kt`: the percent `Fader` wired to `onMasterVolume` and `onMasterSettled` with `PerfTrace.animation(TRANSPORT_MASTER_DRAG)`; `UI/mixer/MixerStrip.kt`: `MuteSoloButton` wired to `onMute`/`onSolo`, the `onKeyEvent` for M/O, `onStripSelect` on number and name (T076 green)
- [ ] T079 [US4] `UI/timeline/TimelineViewModel.kt`: `selectStrip(id)` (`selectTrack` + a viewport request that brings the lane into view through the existing selection path), the initial `scrollToStrip` index in `MixerUi.selectedIndex`, `masterSettled`; `UI/mixer/MixerStripRow.kt`: `LaunchedEffect(selectedIndex)` → `animateScrollToItem` when the sheet opens (T077 green)
- [ ] T080 [US4] Run the three lanes through `gradle-run`; quickstart §5 steps 5–7 by hand on the Pixel 6 Pro
- [ ] T081 [US4] Skill review of Phase 6 (`compose-state-and-effects`, `kotlin-control-flow`); findings into `EVID/skill-review.md`

**Checkpoint**: the mixer and the timeline are one truth.

---

## Phase 7: User Story 5 - Fits every window, pointer, keyboard and screen reader (Priority: P2)

**Goal**: the sheet's forms and heights per window class, phone landscape, the expanded
window's two visible lanes, breakpoint crossings, the largest font, hover, tooltips, the wheel,
secondary click, the full key map, the shortcut sheet entries, Escape through the overlay
stack, screen-reader semantics, the screenshot set (spec US5; FR-019 to FR-022, FR-025 to
FR-030, FR-034, SC-007, SC-008).

**Independent Test**: the screenshot set of FR-034 on both platforms, the keyboard run on the
iOS lane and the desktop emulator, TalkBack and VoiceOver by ear (quickstart §9).

### Tests for User Story 5 (write first, watch them fail)

- [ ] T082 [P] [US5] Extend `UI_UITEST/mixer/MixerSheetTest.kt`: anchors at 360, 600, 840 and 1024 dp (half ≥ 240 dp; expanded half leaves 192 dp of lanes), landscape (600 × 400: one anchor, `FullNoVu`), a 440 dp-tall sheet drops the VU first and the knob second before the fader falls under 120 dp, the largest font (nothing clipped; the name ellipsises), a layout-class change keeps `open`, the height mapping and the row's scroll offset
- [ ] T083 [P] [US5] `UI_UITEST/mixer/MixerSemanticsTest.kt`: every fader announces "<track>, gain, <readout>", every knob "<track>, pan, <readout>", the master "Master volume, 80 percent", meters "Level" with "clipped" once; the Tab order handle → Solo, Mute, pan, fader per strip → master; Escape closes through the overlay stack (011's `RegisterOverlay`); the shortcut sheet lists the Mixer group
- [ ] T084 [P] [US5] Extend `UI_UITEST/designsystem/FaderTest.kt` and `PanKnobTest.kt` for tooltips and the hover layer (011's `hoverArea`), and `UI_UITEST/mixer/MixerStripTest.kt` for a secondary click opening the track menu at the pointer (011's `onSecondaryClick`) and the row's `tembraScrollbar`

### Implementation for User Story 5

- [ ] T085 [US5] `UI/mixer/MixerAnchors.kt` and `UI/mixer/MixerSheet.kt`: the per-layout anchors (data model §5), the landscape single anchor, the `FullNoVu` form by sheet height, the breakpoint crossing through `focusRestoringRoot` (011) and a `rememberSaveable` row state; `UI/mixer/MixerStrip.kt`: the drop order (T082 green)
- [ ] T086 [P] [US5] `UI/designsystem/Fader.kt`, `UI/designsystem/PanKnob.kt`, `UI/mixer/MixerHandle.kt`: `TembraTooltip`, `hoverArea`, `pointerHoverIcon(PointerIcon.Hand)`, the wheel in the control's own steps (011 FR-014); `UI/mixer/MixerStrip.kt`: `onSecondaryClick` → the track menu at the pointer; `UI/mixer/MixerStripRow.kt`: `tembraScrollbar` (T084 green)
- [ ] T087 [US5] `UI/shortcuts/ShortcutCatalogue.kt` (011): `ShortcutScope.Mixer` with "X" (open/close), the fader and knob keys, M and O; `UI/shortcuts/TimelineShortcuts.kt` + `toggleMixer()`; `UI/mixer/MixerSheet.kt`: `RegisterOverlay(onDismiss)`; the semantics of T083 (T083 green)
- [ ] T088 [US5] Run the three lanes through `gradle-run` plus `checkIos` on the iPad Pro 13-inch (M5) and the iPhone Duo (UDID `8AF0F092-16BB-4286-8F8F-E0AB208CBA8A`)
- [ ] T089 [P] [US5] Screenshots, Android (quickstart §9): the Pixel 6 Pro at 360/600/840 dp via `wm density` (640/384/274), phone landscape, the states `mixer-half`, `mixer-full`, `mixer-landscape`, `mixer-clipped`, `mixer-armed`, `track-settings-pan`, default and `font_scale 2.0`; the Pixel Tablet at 1024/1200 dp; the fold emulator `Pixel_9_Pro_Fold` open (`-d 4619827259835644672`) and closed; into `SHOTS/android/<device>/` with a README table
- [ ] T090 [P] [US5] Screenshots, iOS: extend `IOSPERF/ScreenshotMatrix.swift` with the seven states; run on the iPhone 18 Pro, the iPad Pro 13-inch (M5) and the Duo (open and closed, the Pocket end-rail layout) with `TEST_RUNNER_TEMBRA_CAPTURE_DIR`; into `SHOTS/ios/<sim>/` with a README table
- [ ] T091 [US5] Compare every capture against `design/stitch/screens/mixer-timeline/*/screenshot.jpg` and `design/stitch/theme.json` (Principle IV); record verdicts and any token or spacing fix in `SHOTS/NOTES.md`; fix and recapture
- [ ] T092 [US5] Keyboard and pointer pass on the iOS lane (iPad with a keyboard and a trackpad) and on `Tembra_Desktop_API34` (`emulator-5580`, no touchscreen): X, Tab order, arrows, Page, Home, M, O, Escape, Back, hover, tooltips, wheel, secondary click, the scrollbar; results into `SHOTS/NOTES.md`
- [ ] T093 [US5] TalkBack (Pixel 6 Pro, `settings put secure enabled_accessibility_services com.google.android.marvin.talkback/…TalkBackService`, reset to `""`) and VoiceOver (iPhone 14) by ear: a fader, a knob, Solo, Mute, a meter's level and "clipped", the master; findings into `SHOTS/NOTES.md` (the owner may do this step)
- [ ] T094 [US5] Skill review of Phase 7 (`compose-focus-navigation`, `compose-component-design`, `compose-ui-testing-patterns`); findings into `EVID/skill-review.md`

**Checkpoint**: the feature holds at every width and for every input.

---

## Phase 8: User Story 6 - Many tracks stay smooth (Priority: P3)

**Goal**: 24 strips with live meters at the display rate, the row scrolled end to end, the
master pinned, the sheet open and closed ten times while playing without a stall (spec US6;
SC-005; FR-001, FR-024).

**Independent Test**: the 24-track project of the stress phase on the Pixel 6 Pro: the frame
counters and the audio counters of the labelled session (T110); the row test green.

### Tests for User Story 6 (write first, watch them fail)

- [ ] T095 [P] [US6] `UI_UITEST/mixer/MixerStripRowTest.kt`: 24 strips, the master pinned at the far end after a scroll to the last strip, items keyed by id (a removed middle track keeps the others' state), one composition per visible strip per level change (a recomposition counter)
- [ ] T096 [P] [US6] Extend `UI_UITEST/mixer/MixerSheetTest.kt`: ten open/close cycles with the frozen clock advance under 300 ms each; the per-frame read count stays one per frame with 24 strips

### Implementation for User Story 6

- [ ] T097 [US6] `UI/mixer/MixerStripRow.kt` and `UI/mixer/MixerStrip.kt`: `key(id)`, `contentType`, no lambda allocation per item in the row's content (callbacks captured once in `remember`), the strip's `drawBehind` reads only its own slice of the snapshot; the compose compiler report (`./gradlew :shared:ui:assembleRelease -PcomposeReports=true`) shows every `ui.mixer` and the four `designsystem` composables skippable and `MixerUi`/`StripUi`/`MasterUi` stable; the report into `EVID/compose-report/` (T095, T096 green)
- [ ] T098 [US6] `PERF/journeys/StressJourney.kt` and `IOSPERF/Journeys/StressJourney.swift`: the mixer phase (open, five seconds live with the scroll and the metronome, one fader drag, close) before `devtools.writeHealth`; a 24-track variant of the demo built by the journey from `specs/009-timeline-arranger/fixtures/` through the import path
- [ ] T099 [US6] Skill review of Phase 8 (`compose-performance` with the compiler report as evidence); findings into `EVID/skill-review.md`

**Checkpoint**: a big project mixes as smoothly as a small one.

---

## Phase 9: User Story 7 - The mixer in its own window on a desktop (Priority: P3, cuttable)

**Goal**: on Android desktop windowing, "Open in a window" on the sheet's handle opens the
console strips in a second window over the same session; the two windows agree; the project's
close closes both; absent elsewhere (spec US7; FR-021a; research R10). **This phase is
cuttable**: if T100's spike shows the platform refuses a second window of the app, or the owner
stops before it, the feature is complete without it and T106 records the deferral.

**Independent Test**: quickstart §12 on `Tembra_Desktop_API37`.

### Tests for User Story 7 (write first, watch them fail)

- [ ] T100 [US7] Spike (half a day, recorded in `EVID/window-spike.md`): on `Tembra_Desktop_API37` (user 10) start a second activity of the app with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK` and `ActivityOptions.setLaunchBounds`; confirm a second free-form window appears and both activities see the same application graph; decide go / cut
- [ ] T101 [P] [US7] `UI_TEST/platform/FakeWindowCapabilities.kt`; extend `UI_TEST/timeline/TimelineViewModelTest.kt` (`openMixerWindowClosesTheSheetAndAsksTheCapabilities`, `windowButtonHiddenWithoutCapabilities`); `UI_TEST/mixer/MixerWindowViewModelTest.kt` (strips follow the session; a fader edit in the window is one undo step; the state ends when the project closes)
- [ ] T102 [P] [US7] `app/androidApp/src/androidTest/kotlin/audio/tembra/android/AndroidWindowCapabilitiesTest.kt` (`multipleWindows` true on a free-form device, false on a phone; skipped with "no free-form windows on this device" like 011's desktop tests) and extend `UI_UITEST/mixer/MixerSheetTest.kt` (the handle shows `mixer.window.open` only when `windowAvailable`)

### Implementation for User Story 7

- [ ] T103 [P] [US7] `UI/platform/WindowCapabilities.kt` (interface: `multipleWindows`, `openMixerWindow()`), `UI_IOS/platform/IosWindowCapabilities.kt` (false), `ANDROID/AndroidWindowCapabilities.kt` (free-form window management or desktop windowing → true; launches `MixerWindowActivity` with the flags and bounds of T100), bindings in `UI/di/AppBindings.kt`, `UI_IOS/IosAppGraph.kt`, `ANDROID/AndroidAppGraph.kt`
- [ ] T104 [US7] `UI/mixer/MixerWindowViewModel.kt` (`@ViewModelKey`; `uiState` from the session: strips, master, metering held while the window lives), `UI/mixer/MixerWindowContent.kt` (the `MixerStripRow` and `MasterStrip` in the full form filling the window, the same telemetry, its own `focusRestoringRoot`), `ANDROID/MixerWindowActivity.kt` (hosts the content over the application graph; finishes when `session.state` has no project; `onNewIntent` brings it to the front), `app/androidApp/src/main/AndroidManifest.xml` (the activity, `resizeableActivity`, `launchMode="singleTask"`, `taskAffinity` of its own); `UI/mixer/MixerHandle.kt`: the "Open in a window" button; `UI/timeline/TimelineViewModel.kt`: `openMixerWindow()`; Mixer button and X bring the window to the front while it is open (T101, T102 green)
- [ ] T105 [US7] Quickstart §12 on `Tembra_Desktop_API37` (`emulator-5582`, user 10): both windows, the fader in one and the readout in the other, undo in either, X from the timeline window, the project's close; captures into `SHOTS/android/desktop-api37/`; `tools/gradlew-device.sh emulator-5582 :androidApp:connectedDebugAndroidTest`
- [ ] T106 [US7] Skill review of Phase 9 (`kotlin-api-design` for the capabilities seam, `compose-state-and-effects` for the second host) or, if the phase was cut, the deferral note with T100's finding in `SHOTS/NOTES.md` and `EVID/skill-review.md`

**Checkpoint**: a desktop musician mixes in a second window, or the feature is complete without it and says so.

---

## Phase 10: Polish & Cross-Cutting (journeys, pipeline, profile, every target, measurements, audit, docs, Stitch, sessions)

**Purpose**: the measurement the constitution demands, the proof on every available device,
emulator and simulator the owner asked for, the audit and review evidence, and the pull
request (contracts/perf-instrumentation.md, skills-and-audit.md; quickstart §2–§4, §8–§11, §13).

- [ ] T107 [P] `PERF/journeys/MixerJourney.kt` (the 14 steps of the perf contract), `PERF/MixerJourneyBenchmark.kt`, `PERF/PerfMetrics.kt` (`sectionsOf("mixer")`), `PERF/BaselineProfileGenerator.kt` (+ mixer), `PERF/ProfileComparisonBenchmark.kt` (`mixer()`); `IOSPERF/Journeys/MixerJourney.swift`, `IOSPERF/MixerPerfTests.swift`, `IOSPERF/Metrics.swift` (`sections(of: "mixer")`, `engineSections`), `app/iosApp/iosApp.xcodeproj/project.pbxproj` entries; `app/tracepulse.yml` journeys + mixer
- [ ] T108 [P] Pipeline: `PIPE/config.py` (`DEFAULT_JOURNEYS`), `PIPE/catalogue.py` (`JOURNEYS_MEASURED`, `_AUDIO` rows `masterClips` informational and `meterFramesDropped` 20 % gate floor 2), `PIPE/orchestrate_android.py` (`BENCHMARK_CLASS_OF`), `PIPE/collect_android.py`, `PIPE/collect_ios.py` (`_TEST_JOURNEYS`, `order`, `_HEALTH_KEYS`), `PIPE/traces_android.py` (`AUDIO_COUNTER_KEYS`), `PIPE/report.py` (`_JOURNEY_ORDER`), `app/perf/schema/round-record.schema.json`, `comparison.schema.json`, `app/perf/tests/*` and `tests/fixtures/engine-health.json`, `specs/007-performance-pipeline/contracts/metric-catalogue.md`, `app/perf/README.md`; `cd app/perf && uv run pytest` green
- [ ] T109 Build the measurement artefacts (`./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf` through `gradle-run`) and run the trial round `uv run tracepulse run --device pixel6 --label 012-mixer --rounds 1 --no-publish --no-build --apk … --test-apk …` (SD card mounted): the `mixer` journey completes with every section present and `audio.meterFramesDropped = 0`; `EVID/trial-round.md`
- [ ] T110 `uv run tracepulse profile generate --device pixel6`; commit `app/androidApp/src/main/generated/baselineProfiles/{baseline-prof,startup-prof}.txt`; `profile verify`; `profile compare --journeys mixer` into `EVID/profile.md`; `app/androidApp/baseline-profile-coverage.txt` rows for the mixer
- [ ] T111 [P] Level measurements (quickstart §8, the loopback step, announced): the three-track measurement project, SC-003 (0 dB centred within 0.5 dB, 0 VU at −18 dBFS, ≤ 50 ms), SC-004 (`tools/measure-pan-law.py`: `L² + R²` equal within 0.5 dB; the opposite side below −60 dB), SC-002 (the sweep: no step larger than the ramp's, click count 0, first change within 20 ms) on the Pixel 6 Pro and the iPhone 14; `EVID/levels.md`
- [ ] T112 [P] Device lane, Pixel 7 Pro: `tools/apply-device-run-conditions.sh 33241FDH3000NZ` then `tools/gradlew-device.sh 33241FDH3000NZ checkAndroidDevice`; results and any skipped test with its reason into `SHOTS/NOTES.md`
- [ ] T113 [P] Device lane, Pixel Tablet `3621105H803UFX` (as T112; plus the 1024/1200 dp captures if T089 did not)
- [ ] T114 [P] Device lane, OPPO CPH2239 `O7PBYLZP4HMBW8S4` (brightness by hand; the two engine recording tests skip as in 010; the mixer tests must run)
- [ ] T115 [P] Device lane, Redmi Note 5 `e9446ac2` (refuses do-not-disturb; Play Protect "Don't send"; no Perfetto)
- [ ] T116 [P] Device lane, fold emulator `Pixel_9_Pro_Fold` (`emulator-5554`, booted with audio; about 2.5 h; PixelCopy flakes retried once)
- [ ] T117 [P] Device lane, desktop emulator `Tembra_Desktop_API37` (`emulator-5582`, user 10, `-no-window` allowed) and `:androidApp:connectedDebugAndroidTest` on `Tembra_Desktop_API34` (`emulator-5580`); one desktop emulator at a time
- [ ] T118 [P] Simulator lanes: `./gradlew checkIos -Ptembra.iosSimulator="iPhone 18 Pro"`, `="iPad Pro 13-inch (M5)"`, `=8AF0F092-16BB-4286-8F8F-E0AB208CBA8A` (the Duo) through `gradle-run`; results into `SHOTS/NOTES.md`
- [ ] T119 Release builds on every attached target: `tools/release-android.sh` (the five devices and the three emulators that are up) and `tools/release-ios.sh` (the iPhone 14, the iPhone 18 Pro, the iPad, the Duo); launch and open the mixer on each; the table of targets, OS versions and outcomes in `SHOTS/NOTES.md`; any target skipped named with the reason
- [ ] T120 [P] Docs: `app/README.md` rows (the mixer sheet, the pan law, the measurement script, ABI 8), `app/engine/README.md` (revision 8, the lock-free parameter path), `design/stitch/DESIGN.md` (mixer prose: strips, fader, knob, meters, VU; prose only), `CLAUDE.md` (the measurement script line)
- [ ] T121 [P] `EVID/main-thread-audit.md`: the construction table (every engine call the mixer makes from the main thread with its bound; the calls it never makes there), the T019 and T011 results per target, the StrictMode logcat read on the Pixel 6 Pro (`adb logcat -s StrictMode` during T112-style lane on the Pixel 6 Pro and a five-minute manual mixer session; zero mixer lines; pre-existing lines reported), the Perfetto query placeholder filled by T125 (contracts/skills-and-audit.md §C)
- [ ] T122 Final skill review of the whole diff against `main` (contracts/skills-and-audit.md §B step 5) with the compose compiler report; `EVID/skill-review.md` closed with a summary table (findings per skill, fixed / recorded)
- [ ] T123 Stitch sync (quickstart §13): render the eight states (sheet half and full on the phone at 390 px, the tablet at 1280, the desktop window, the strip at rest, clipped and armed, the settings with pan) and upload each as a new screen to project `12040171611712589599` with the `stitch-design:upload-to-stitch` script; `PATCH /v1/projects/{p}?updateMask=screenInstances` beside the timeline screens; re-pull `design/stitch/screens.json` and `design/stitch/screens/mixer/*`; note the slugs in `design/stitch/README.md`
- [ ] T124 Owner checks (not an agent task): the console taper by feel, the half height on the owner's phone, the VU dial's look, the pan law's extremes on headphones, TalkBack/VoiceOver (T093); decisions appended to `spec.md`'s Clarifications as a design-iteration session and turned into tasks here
- [ ] T125 Pull request and the labelled sessions after the merge: the PR names the principle behind each non-obvious decision (plan.md's Constitution Check and Complexity Tracking), the skill contracts, the screenshots, the compose report, `EVID/levels.md`, `EVID/profile.md`, `EVID/main-thread-audit.md`, `EVID/skill-review.md`, the trial round and the window decision; after the merge, `uv run tracepulse run --device pixel6 --label 012-mixer --rounds 5`, the same on `iphone14`, the Pixel 7 Pro, the Pixel Tablet by serial and `emulator-5582` (quickstart §3 before each), then `sync` and `report --open`; read the "last session" block and write every SUSPECT/CONFIRMED onset and `above_limit` notice with its cause or fix into `EVID/perf-session.md`; run the Perfetto main-thread query of the perf contract on the Pixel 6 Pro round and complete `EVID/main-thread-audit.md`; `timeline.mixerFrame` within one frame at 24 strips, zero `audio.meterFramesDropped`, `audio.starvation` and `audio.xruns` on the reference devices, the startup family within the gate — after T107–T123

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (the merged base), then T002–T008 in parallel.
- **Foundational (Phase 2)**: depends on Setup; tests T009–T022 in parallel, then the engine
  chain T023 → T024/T025 → T026 → T027 → T028 → T029 → T030/T031, the Kotlin chain T032 →
  T033 → T034 → T035 → T036/T037, the gates T038–T043. **Blocks every user story.**
- **User stories (Phases 3–9)**: US1 after the gates; US2 after US1 (the strip and sheet files);
  US3 after US1 (the strip's knob slot) and may run beside US2 on disjoint files; US4 after US1
  and US2 (the master's meter); US5 after US1–US4 (it polishes them) and after 011's helpers
  are in the merged base; US6 after US2 and US4; US7 after US5 (the handle button) and is
  cuttable.
- **Polish (Phase 10)**: T107–T108 after US6 (the stress phase), T109–T111 after every story,
  T112–T119 after the last code change, T120–T124 after everything, T125 last.

### Within Each User Story

- Tests are written first and fail before implementation.
- Core and engine types before the session, the session before the ViewModel, the ViewModel
  before the composables, the composables before the screen wiring, the lanes before the
  skill review, the review closes the phase.

### Parallel Opportunities

- Setup: T002–T008 together after T001.
- Foundational tests T009–T022 together; T023, T024, T025 together; T030 and T031 together;
  T032, T034, T037 together.
- US1: T044–T047 together; T048, T051 together; T050 beside T052.
- US2: T057, T058, T060 together; T061, T062 together.
- US3: T067–T069 together; T071, T072 together.
- US5: T082–T084 together; T086 beside T085; T089, T090 together.
- US7: T101–T103 together after T100.
- Polish: T107, T108 together; T111–T118 one target per sub-agent at the same time (different
  devices; one desktop emulator at a time); T120, T121 together.

---

## Parallel Example: User Story 1

```bash
# Tests first, together:
Task: "FaderTest in UI_UITEST/designsystem/FaderTest.kt"
Task: "MixerStripTest and MixerSheetTest in UI_UITEST/mixer/"
Task: "TimelineViewModelTest additions in UI_TEST/timeline/TimelineViewModelTest.kt"
Task: "TimelineScreenTest and TimelineKeyHandlingTest additions in UI_UITEST/timeline/"

# Then the state types and the row/handle together, the fader beside the strip:
Task: "MixerHeight, StripForm, MeterSource, MixerAnchors, MixerViewState, MixerUi, StripUi, MasterUi in UI/mixer and UI/timeline"
Task: "MixerHandle and MixerStripRow in UI/mixer/"
Task: "Fader in UI/designsystem/Fader.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (the merged base and the fixed names), Phase 2 (the engine, the seam, the document,
   the session, with the gates green on the host, the simulator and the Pixel 6 Pro).
2. Phase 3: the sheet with faders.
3. **STOP and VALIDATE**: quickstart §5 steps 1–4 on the Pixel 6 Pro and the iPhone 18 Pro
   simulator; the three lanes green.

### Incremental Delivery

1. US1 → faders mix the project (MVP).
2. US2 → the meters; US3 → pan; US4 → mute, solo, the master.
3. US5 → every window, pointer, keyboard and screen reader, with the screenshot set.
4. US6 → 24 tracks; US7 → the separate window (cuttable).
5. Polish → journeys, profile, every target, measurements, the audit and review files, docs,
   Stitch, the pull request and the labelled sessions.

### Parallel Team Strategy

- The main agent: the engine, the seam, the session, the sheet, the controls, every review.
- Sonnet-class sub-agents: one target each for the lanes (T039, T055, T065, T074, T080, T088,
  T112–T118), the release builds (T119), the captures (T089, T090, T105) and the perf rounds
  (T109, T125); component tests may be split across agents in detached worktrees on disjoint
  files with one integrator.

---

## Notes

- [P] tasks = different files, no dependencies.
- Every device run applies quickstart §3 first; the two sounding steps (§6, §8) are announced.
- Commit after each task or logical group with a conventional message; the after-implement
  hook commits the rest.
- Stop at any checkpoint to validate the story independently; the window phase (9) may be
  cut without leaving the feature incomplete.
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence.
