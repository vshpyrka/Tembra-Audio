# Implementation Plan: Mixer

**Branch**: `012-mixer` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-mixer/spec.md` (clarified 2026-11-03: the
sheet on every window size, no arm control in the strip, the master in percent, the sheet
opened by the button only, two heights) and the owner's planning brief: make a plan for the
mixer; make sure nothing runs on the main thread; check the implementation against the Chris
Banes best-practice skills; test everything on all available devices and emulators. The
constitution is v1.3.5 (recording 010, large screens and desktop 011, the mixer 012).

## Summary

The mixer is a non-modal sheet with two resting heights inside the Timeline screen's lanes
box, built on `anchoredDraggable`, so the toolbar, the ruler and the transport bar stay in
place and usable on every window size; one strip per track plus a pinned master strip, each
strip a vertical fader with a console taper, a two-bar meter beside it, a pan knob, a VU dial,
Solo and Mute. The engine gains a lock-free parameter block (per-track gain, pan and audible
as atomics with a version counter) read once per block by the callback, so a fader step from
the main thread is a few atomic stores and can never wait behind play or record-stop, which
today hold the command mutex for up to a second; pan uses a centre-compensated constant-power
law so every existing project plays unchanged, with balance for stereo sources; each strip
gets two smoothed ramps. Meters are measured on the callback only while the sheet is open
(peak, a 300 ms mean square, a 64 ms peak history, sticky latches per strip and for the
master) and published through one seqlock record the sheet reads once per frame in the draw
phase, the pattern of the playhead and the input meter; semantics come from one 10 Hz mirror.
`pan` joins the document after `clips`; `SetPan` coalesces like gain and every gesture end
closes the undo group. The separate desktop window of FR-021a is planned as the last,
cuttable phase on Android only. Seven sections, two counters, seventeen anchors, a `mixer`
journey and the extended `stress` journey make it measurable; the main-thread audit is five
deliverables (construction table, Catch2 and facade timing tests, a StrictMode guard in debug
builds, a Perfetto query of the labelled round); the Chris Banes skills are contracts per
package with a review task per phase; every target the repository knows runs its lane, the
release build and the screenshots. Technology evidence: [research.md](research.md) R1–R18.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (Multiplatform; Kotlin/Native for iOS); C++17 for the
engine (parameters, pan law, meter, ABI); Swift for the iOS journey and screenshot states;
Python for the pipeline tables and the level-measurement script.

**Primary Dependencies** (research R18): none added. Already present and used: Compose
Multiplatform 1.12.1 (Foundation's `anchoredDraggable`, the multiplatform `BackHandler`,
`LazyRow`), lifecycle `ViewModel`, Navigation 3 (untouched), Metro, DataStore (no new key),
`kotlinx.serialization` (the new field), Okio, Catch2 3.16, Oboe/RemoteIO (unchanged), the
seqlock, `GainRamp` and `TripleBuffer` of the engine. From feature 011 after the merge: the
shortcut catalogue, the overlay stack, the scrollbar, the wheel on sliders, hover and pointer
helpers, focus restoration, the desktop emulators.

**Storage**: `project.json` gains `pan` per track (format version 1, appended after `clips`,
default 0); the demo golden and the fixture updated; no new preference; the sheet's state is
session-only.

**Testing**: Catch2 on the host for the pan law, the parameter block, the ABI's lock-freedom
(the command-mutex hold), the meter (peak, RMS window, history, latches, metering off), the
strip's two ramps, the stereo render, the health field and the ABI revision; `kotlin.test` +
`kotlinx-coroutines-test` in `commonTest` for `Pan`, `FaderTaper`, the edit, the codec golden
and the old-document read, the session's coalescing, group close, metering refcount, latch
reset and per-frame read, the ViewModel's toggle, X, strips, input source and synchronous
metering, the UI state's forms and anchors; `runComposeUiTest` in `uiTest` for the fader,
the knob, the meter, the VU dial, the strip, the master, the sheet (anchors, drag, back,
widths 360/600/840/1024 dp, landscape, largest font, the transport bar reachable, one read per
frame), the row at 24 strips, the screen, the keys per lane, the settings row and the header
readout; device and simulator tests for the engine's pan and meters through `readMix` and for
the main-thread cost; `uv run pytest` for the pipeline; the manual, loopback and screenshot
checks of [quickstart.md](quickstart.md) §5–§9.

**Target Platform**: Android 8.0+ phones, foldables, tablets and desktop windows (the separate
window on Android desktop windowing only, cuttable); iOS 16+ iPhone and iPad; reference
devices Pixel 6 Pro, Pixel 7 Pro, iPhone 14; every target of quickstart §2.

**Project Type**: mobile app; C++ in `engine/`, the seam in `:shared:engine`, the types and
the edit in `:shared:core`, the golden in `:shared:data`, the sheet and the controls in
`:shared:ui`, the window host and the StrictMode guard in `:androidApp`, journeys in
`perf-benchmarks` and `TembraPerfTests`.

**Performance Goals** (spec SC-002 to SC-006): gesture to sound within 20 ms with no audible
step; meters within 50 ms, within 0.5 dB, the VU settled within 300 ms; pan within 0.5 dB of
constant power; 24 strips with live meters at the display rate within the gate, zero
underruns, starvation and dropped meter frames on the reference devices; the sheet open and
closed within 300 ms; the startup family unchanged within the gate; every mixer call on the
main thread under 50 µs median.

**Constraints**: Principle I (the callback reads atomics and measures; no lock, allocation or
trace in the RT files; the poison build); Principle II (meters in draw lambdas, never through
`StateFlow`; one read per frame); the transport bar usable while the sheet is open (FR-016);
no mixer destination (FR-016); the master stays a percentage; no effects, automation, limiter,
trim or reordering (FR-033); tokens only; one type per file; zero warnings; Kotlin tests never
link the engine; the owner's run conditions on every device; the two sounding steps
announced; 010 and 011 merged first.

**Scale/Scope**: about 7 new C++ files (`mix_params.h`, `pan_law.h`, `mix_meter.{h,cpp}`,
`mixer_abi.cpp`, `mix_record.h`, `recent_peak.h`) and 9 extended (`tembra_engine.h` ABI 8,
`engine_info.cpp`, `engine_core.*`, `audio_health.*`, `track_strip.*`, `master_bus.*`,
`transport_renderer.*`, the JNI shim) with 6 new and 7 extended Catch2 files; about 45 new
Kotlin files (core 4: `Pan`, `SetPan`, `FaderTaper`, their tests' subjects; engine 3:
`MixSnapshot`, the perf enums' rows, the fake's script; ui 28: `MixerSheet`, `MixerHandle`,
`MixerStripRow`, `MixerStrip`, `MasterStrip`, `MixerLevelMirror`, `StripLevel`, `MixerUi`,
`StripUi`, `MasterUi`, `MixerHeight`, `StripForm`, `MeterSource`, `MixerAnchors`, `MixerViewState`,
`MixerCopy`, `MixerTestTags`, `Fader`, `PanKnob`, `LevelMeter`, `VuDial`, `PanSlider`, `PanReadout`,
`MixTelemetry`, `MeterFrameCounter`, `WindowCapabilities` + two bindings, `MixerWindowContent`,
`MixerWindowViewModel` (R10)) and about 25 extended; `MixerWindowActivity` and the StrictMode
guard in `:androidApp`; two Swift journey files and the matrix states; one Python script
(`tools/measure-pan-law.py`); ~35 new test classes; pipeline tables in nine Python files; the
regenerated baseline profile; catalogue rows; README rows; the Stitch upload of eight states.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | The callback's new work is bounded: one relaxed load of the parameter version per block, 192 atomic loads when it changed, two ramps per strip, a compare and a multiply-add per sample for metering, one seqlock store per block; the host writes atomics and reads a seqlock; no FIFO is needed because the latest target is the only one that matters; the new RT files join the poison list; one engine, both shells (research R4, R5, R6; contracts/engine-c-abi-v8.md). | PASS |
| II. Shared Kotlin Control Plane, Compose UI | `Pan`, `FaderTaper`, `SetPan` in `:shared:core`; the seam in `:shared:engine`; the sheet, the controls, the state and the session changes in `:shared:ui` `commonMain`; meters read `MixTelemetry` in `drawWithContent`/`drawBehind` only, the 10 Hz mirror serves semantics; one ViewModel per destination kept (the mixer is Timeline's); `WindowCapabilities` an interface with bindings, no new expect/actual (R6, R8, R10). | PASS |
| III. Test-First | Every contract lists its failing tests first; the pan law, the meter, the lock-freedom and the ramps proven offline in Catch2; the document, the session and the ViewModel on the JVM; the controls and the sheet with scripted telemetry and a frozen clock; the engine's pan and cost on devices; Kotlin tests use `FakeAudioEngine` (extended with scripted meters). | PASS |
| IV. Adaptive, Input-Agnostic UI | The sheet's anchors and forms by layout class (data model §5) with every target ≥ 44 dp; hover, tooltips, the wheel, focus groups, X and the control keys, the traversal order; semantics on every control, meter and the handle; tokens only; screenshots at five widths, landscape, both heights, the fold, the iPad, the Duo and the desktop window, default and largest font, on both platforms (contracts/mixer-ui.md; quickstart §9). | PASS |
| V. Document Model in Beats | Nothing musical moves; `pan` is a per-track document field with a default; `SetPan` is a pure edit and one history snapshot per gesture; the format stays version 1 and forward-compatible (R7). | PASS |
| VI. Simplicity | No dependency added; atomics instead of a second command queue; one seqlock record for every meter; no new ViewModel for the sheet; no preference; the exclusions of FR-033 honoured; the separate window last and cuttable (Complexity #2). The custom sheet instead of Material's modal sheet is the one piece built rather than reused (Complexity #1). | PASS |
| VII. Code Organisation | One type per file; packages `core.project`, `engine`, `ui.session`, `ui.timeline`, `ui.mixer` (new), `ui.designsystem`, `ui.tracksettings`, `ui.platform`; C++ under `engine/src/mixer` and `engine/src/transport`; `internal` by default. | PASS |
| VIII. Skill-Guided Kotlin and Compose | Routed through `using-chrisbanes-skills` on 2026-11-03 (`compose-performance` and `kotlin-concurrency-and-flow` loaded for the plan); contracts/skills-and-audit.md names the owning skill and rules per package and adds a review task per phase with `evidence/skill-review.md`; the plan's choices follow `compose-performance` (frame-rate reads in draw and `offset { }` only, immutable UI types, keyed items), `compose-state-and-effects` (state in the ViewModel, callbacks built once), `compose-component-design` (`modifier` first, primitives and callbacks), `compose-animations` (`animateTo`, `Animatable`, reduce motion), `compose-focus-navigation` (focus groups, keys on the focused control), `compose-ui-testing-patterns` (scripted telemetry, injected interaction sources, frozen clock), `kotlin-concurrency-and-flow` (no new launch site below the ViewModel; synchronous bounded setters; no `init` launch or `runBlocking`), `kotlin-api-design` (closed core types, member functions, interfaces with bindings), `kotlin-control-flow` (exhaustive `when`, no `else`); every Gradle command through `gradle-run`. | PASS |
| Technology & Architecture Constraints | KMP + Compose, multiplatform ViewModel, Navigation 3 untouched, Metro, DataStore untouched, `kotlinx.serialization` (one field); engine C++ with a C ABI (revision 8), Oboe/RemoteIO unchanged; nothing forbidden; design tokens from `DESIGN.md`/`theme.json` (none added); the transport bar on Timeline unchanged and usable under the sheet; the Metronome destination untouched; product name unchanged. | PASS |
| Development Workflow & Quality Gates | Build order (v1.3.5): … recording (010), large screens and desktop (011), **mixer (012)**, effects … — this plan implements exactly step 012 and pulls nothing forward (no inserts, sends, automation, limiter). Instrumentation: seven sections, two counters, seventeen anchors, the `mixer` journey, the extended `stress`, the regenerated profile, the labelled sessions on every available device including the desktop emulator (contracts/perf-instrumentation.md). Definition of done maps to the lanes on every target, the screenshots on both platforms, the compose report, the loopback measurements, the audit file, the skill review file and the Stitch sync (quickstart §2–§13). | PASS |

**Post-design re-check (after Phase 1)**: unchanged. Two deliberate choices a reviewer could
read as deviations are recorded in Complexity Tracking. One spec wording is flagged for
`/speckit-analyze` rather than changed here: SC-004's "summed to mono" is measured as the
power sum `L² + R²` (research R5). No spec change was made during planning.

## Project Structure

### Documentation (this feature)

```text
specs/012-mixer/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R18 with evidence (code maps, the mutex finding, the pan law, the audit, the device matrix)
├── data-model.md        # Phase 1: document, engine seam, C++ structures, session/UI state, layout rules, tokens, perf names, transitions
├── quickstart.md        # Phase 1: targets, run conditions, lanes, every user story, the loopback step, screenshots, perf, the audit, the skill review, the window, the Stitch sync
├── contracts/
│   ├── engine-c-abi-v8.md       # mixer setters, pan law, meters, renderer rules, bindings, the Kotlin seam, Catch2 tests
│   ├── mixer-session.md         # Pan, SetPan, FaderTaper, the session's coalescing/metering/telemetry, the ViewModel, keys, tests
│   ├── mixer-ui.md              # sheet, handle, row, strips, fader, knob, meters, layout rules, keyboard, semantics, copy, tests
│   ├── perf-instrumentation.md  # catalogue, anchors, the mixer journey, stress extension, profile, sessions, the trace query
│   └── skills-and-audit.md      # skill contracts per package, the per-phase review, the five-part main-thread audit
├── checklists/requirements.md
├── evidence/            # created during implementation: levels.md, main-thread-audit.md, skill-review.md, profile.md, perf-session.md, compose-report/
├── screenshots/         # created during implementation (quickstart §9) with NOTES.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── README.md                                        + rows: the mixer sheet, the pan law, the measurement script
├── tools/measure-pan-law.py (new), fetch-material-symbols.sh (run: instant_mix)
├── tracepulse.yml                                   journeys + mixer
├── engine/
│   ├── include/tembra_engine.h                      ABI 8 (contracts/engine-c-abi-v8.md); tembra_trace_names.h regenerated (49)
│   ├── src/engine_info.cpp                          version 0.7.0, ABI 8
│   ├── src/core/engine_core.{h,cpp}                 + setMixTrack/Pan/Master, setMetering, readMix, resetMixClip (no commands_), the arrangement seeds MixParams
│   ├── src/core/audio_health.{h,cpp}                + master_clips
│   ├── src/mixer/mix_params.h, pan_law.h, mix_meter.{h,cpp}, mix_record.h, recent_peak.h, mixer_abi.cpp
│   ├── src/mixer/track_strip.{h,cpp}                two ramps, pan, stereo balance
│   ├── src/mixer/master_bus.{h,cpp}                 clamp count and latch hook
│   ├── src/transport/transport_renderer.{h,cpp}     applyTracks from MixParams; measure after process and after the bus; publish per block
│   ├── android/src/main/cpp/tembra_jni.cpp          + nativeMixerSetTrack/SetPan/SetMaster/SetMetering/Read/ResetClip
│   └── tests/                                       + pan_law_test, mix_params_test, mixer_abi_test, mix_meter_test; track_strip_test, master_bus_test,
│                                                      transport_renderer_test (renderStereo), audio_health_test, engine_info_test, trace_shim_test, trace_poison extended
├── shared/core/src/commonMain/kotlin/audio/tembra/core/project/
│   ├── Pan.kt, SetPan.kt, FaderTaper.kt; Track.kt (+ pan), ProjectEditor.kt (+ setPan), ProjectValidator.kt (+ range)
│   └── commonTest/… PanTest, FaderTaperTest, ProjectEditorSetPanTest, ProjectValidatorTest (+)
├── shared/engine/
│   ├── perf-catalogue.txt                           + 7 sections, 2 counters
│   ├── src/commonMain/kotlin/audio/tembra/engine/   TimelineAudio.kt (+6), MixSnapshot.kt, perf/AudioHealth.kt (+1), PerfSection.kt (+7), PerfCounter.kt (+2), AudioHealthPublisher.kt (+2), FakeAudioEngine.kt (+ mix)
│   ├── src/androidMain/…/NativeAudioEngine.android.kt (+ natives, allocation-free read), src/iosMain/…/NativeAudioEngine.ios.kt (+ calls, one struct)
│   └── tests: commonTest FakeAudioEngineTest (+), PerfSectionCatalogueTest; androidDeviceTest + iosTest NativeAudioEngineMixerTest, NativeAudioEngineMixerCostTest, NativeAudioEngineBlockingDispatchTest (+)
├── shared/data/src/commonTest/…/projects/DemoProjectGolden.kt (+ pan), ProjectDocumentCodecTest.kt (+ old document)
├── shared/ui/
│   ├── src/commonMain/composeResources/drawable/ic_instant_mix_24.xml
│   ├── src/commonMain/kotlin/audio/tembra/ui/
│   │   ├── session/ MixTelemetry.kt, ProjectSession.kt (+ pan, metering refcount, readMixFrame, latch reset on play, pushPans)
│   │   ├── mixer/ MixerSheet.kt, MixerHandle.kt, MixerStripRow.kt, MixerStrip.kt, MasterStrip.kt, MixerLevelMirror.kt, StripLevel.kt, MixerUi.kt, StripUi.kt, MasterUi.kt,
│   │   │          MixerHeight.kt, StripForm.kt, MeterSource.kt, MixerAnchors.kt, MixerCopy.kt, MixerTestTags.kt, MixerWindowContent.kt (R10), MixerWindowViewModel.kt (R10)
│   │   ├── designsystem/ Fader.kt, PanKnob.kt, LevelMeter.kt, VuDial.kt
│   │   ├── timeline/ TimelineViewModel.kt (+ toggleMixer, setMixerHeight, setPan, gestureSettled, clips, selectStrip, openMixerWindow), TimelineViewState.kt (+ mixer), MixerViewState.kt,
│   │   │             TimelineUiState.kt (+ mixer), TrackUi.kt (+ pan), TimelineCallbacks.kt (+10), TimelineKeyHandling.kt (+ X), TimelineCopy.kt, TimelineTestTags.kt, TimelineScreen.kt, TimelineScreenContent.kt (+ sheet, lanesHeight)
│   │   ├── timeline/components/ TimelineToolbar.kt (+ Mixer button), TrackHeader.kt (+ PanReadout), PanReadout.kt
│   │   ├── tracksettings/ PanSlider.kt, TrackSettingsForm.kt (+ row), TrackSettingsUiState.kt, TrackSettingsCallbacks.kt, TrackSettingsViewModel.kt (+ pan), TrackSettingsTestTags.kt
│   │   ├── platform/ WindowCapabilities.kt
│   │   ├── theme/ TembraIcon.kt (+ InstantMix)
│   │   └── perf/ PerfAnchors.kt (+17), EngineHealthJson.kt (+2), AudioHealthTicker.kt (+2), MeterFrameCounter.kt
│   ├── src/iosMain/kotlin/audio/tembra/ui/ IosAppGraph.kt (+ capabilities), platform/IosWindowCapabilities.kt
│   ├── src/commonTest/…/ session/ProjectSessionTest (+6), timeline/TimelineViewModelTest (+7), TimelineUiStateTest (+), theme/TimelineTokensTest (+), perf/PerfAnchorsCatalogueTest (+17), IconAssetsTest (+)
│   └── src/uiTest/…/ designsystem/{FaderTest,PanKnobTest,LevelMeterTest,VuDialTest}, mixer/{MixerStripTest,MasterStripTest,MixerSheetTest,MixerStripRowTest,ScriptedMixTelemetry},
│                     timeline/{TimelineScreenTest+,TimelineKeyHandlingTest+,TrackHeaderTest+}, tracksettings/TrackSettingsFormTest+
├── androidApp/
│   ├── src/main/AndroidManifest.xml                 + MixerWindowActivity (R10)
│   ├── src/main/kotlin/audio/tembra/android/ TembraApplication.kt (+ StrictMode in debug), AndroidWindowCapabilities.kt, MixerWindowActivity.kt (R10), AndroidAppGraph.kt (+)
│   ├── src/main/generated/baselineProfiles/{baseline-prof,startup-prof}.txt   regenerated
│   └── src/androidTest/…/AndroidWindowCapabilitiesTest.kt
├── iosApp/TembraPerfTests/ Anchors.swift (+17), Metrics.swift (+ mixer), Journeys/MixerJourney.swift, MixerPerfTests.swift, Journeys/StressJourney.swift (extended), ScreenshotMatrix.swift (+7 states), iosApp.xcodeproj
├── perf-benchmarks/src/main/kotlin/audio/tembra/perf/ journeys/Anchors.kt (+17), journeys/MixerJourney.kt, MixerJourneyBenchmark.kt, journeys/StressJourney.kt (extended), PerfMetrics.kt (+ mixer), BaselineProfileGenerator.kt (+), ProfileComparisonBenchmark.kt (+ mixer)
├── perf/tracepulse/ config.py, catalogue.py (+ journey, +2 audio rows), orchestrate_android.py, collect_android.py, collect_ios.py, report.py, traces_android.py, schema/*.json; tests/*; README.md
└── design/stitch/ DESIGN.md (mixer prose), screens.json (+8 uploaded screens), screens/mixer/* (pulled)
```

**Structure Decision**: no new Gradle module. The pure types and the edit go to `:shared:core`;
the ABI and the seam to `engine/` and `:shared:engine` (the only code that touches audio);
the sheet and its strips to a new `ui.mixer` package beside `ui.timeline` (they are Timeline's
overlay, not a destination), the reusable controls to `ui.designsystem` so the track settings
and a future effects screen can use the fader and the knob; `WindowCapabilities` follows
feature 010's monitor pattern (an interface in `ui.platform`, the Android binding in
`:androidApp` because it needs the activity and the package manager, the iOS binding in
`iosMain`). The journeys live where feature 007 put them. One measurement script joins
`app/tools/` beside the alignment script.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| #1 A custom two-anchor sheet (`anchoredDraggable`) instead of the project's `ModalBottomSheet` pattern (Principle VI "build only what the spec requires") | The spec's sheet has two resting heights, leaves the toolbar visible and the transport bar usable, keeps the lanes visible at half height and survives a layout-class change with its drag; Material's modal sheet covers the window, scrims it, has one Material-chosen partial height and lives in its own window (research R1). | `ModalBottomSheet` with `skipPartiallyExpanded = false` (no control over the half height; the transport bar is scrimmed); a nested destination (replaces the timeline, which FR-018 forbids). |
| #2 A second activity for the mixer window (FR-021a) in an app that has one activity and one back stack, delivered last and cuttable | The owner asked for the window as an option; a second window of the app on Android desktop windowing is a second task and host, not a destination inside Navigation 3; keeping it a separate host leaves the shell untouched and makes the phase removable (research R10). | Activity embedding (one window split, not a second window); a floating overlay (not a window). |
| #3 Lock-free atomics for the mixer's parameters beside the existing `TrackTable` TripleBuffer (two mechanisms for track state) | The command mutex is held for up to a second by record-stop; a fader step from the main thread must never wait behind it (the owner's brief); the table stays for the arrangement's structure and revision-7 callers (research R4). | A second mutex (a second writer to the table's TripleBuffer breaks its single-writer rule); a dispatcher hop per step (the sound lags a second behind a drag during record-stop and the main thread still owns 64 conflated sends). |

## Workflow notes carried into `/speckit-tasks` (the owner's brief)

- **Order**: merge `main` (with 010 and 011) into `012-mixer` first; then the engine (tests
  first), the core types and the document, the session and the ViewModel, the controls, the
  sheet, the settings and header, the instrumentation and journeys, the verification on every
  target, the window phase last (cuttable), the Stitch sync.
- **Main thread**: the five parts of the audit (skills contract §C) are tasks with the
  evidence file as their output; the Catch2 `commands_`-hold test and the facade cost test are
  written before the ABI functions; the StrictMode guard lands with the first UI task so every
  device lane reads logcat for it.
- **Skill review**: one review task closes each phase and writes `evidence/skill-review.md`;
  the final review covers the whole diff and the compose report.
- **Every target**: one lane task per target of quickstart §2 (Sonnet-class sub-agents, one
  target each, through `gradle-run` and `tools/gradlew-device.sh`, run conditions first,
  exact failing test names back); the release build on every attached device and simulator;
  the screenshot set on every form factor; the labelled sessions on `pixel6`, `iphone14`, the
  Pixel 7 Pro, the Pixel Tablet and the desktop emulator; every skipped target named with the
  reason in `screenshots/NOTES.md`.
- **Sounding steps**: §6 (pan on headphones) and §8 (loopback) only, announced before they
  start; everything else under the owner's run conditions.
- **Stitch**: the eight new states uploaded to the Tembra Audio project and the export
  re-pulled as the last implementation task (quickstart §13).
