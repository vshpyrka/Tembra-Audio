# Implementation Plan: Tuner Noise Gate

**Branch**: `006-tuner-noise-gate` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-tuner-noise-gate/spec.md`, and the owner's planning request: check
it on every attached simulator, the Pixel 6 Pro and the Pixel Fold emulator, include a change of screen orientation,
and make sure the control is visible, on screen, not cut and not overlapping anything.

## Summary

Auto Listen gets a "Noise gate" slider. Its handle sets the loudness a sound must reach before the gauge reads it;
its track shows the live input level on the same scale; the start of the track is "Off".

The gate lives in the C++ engine on the analysis thread (research R1): the level of the 85 ms analysis window after
the pre-filter (R2) is compared with the gate level, opening at it and closing 3 dB under it (R3). The level arrives
through one new C function whose value the engine keeps across stops and starts, so a move of the handle never
restarts the microphone (R4), and the reading gains the level and the gate's state for the screen (R5, ABI
revision 4). In Kotlin the level is a small value type in `:shared:core` (R7), remembered as a sixth tuner
preference. The control is a Material slider built like the metronome's tempo slider, sharing its handle, with a
track that draws the level in the draw phase (R9, R10). It sits under the gauge on phones and tablets and in the
settings card sideways and on foldables, with the gauge fitted to the room that is left (R11).

Verification runs on five targets in both orientations in three layers: layout tests that assert the control is on
screen and clear of everything in every form, a structural check of the real screen on Android, and reviewed
captures (R14).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Kotlin Multiplatform), Compose Multiplatform 1.12.1 with `material3` 1.9.0;
C++17 for the engine; one line of Swift in the iOS shell

**Primary Dependencies**: none new. Material 3 `Slider` (already used by `TempoSlider`), DataStore Preferences 1.2.1
(one more key), the existing engine

**Storage**: DataStore Preferences: `tuner.noiseGateDb` (`Int`, absent means off)

**Testing**: Catch2 (`engineHostTest`) for the level, the gate and the ABI; `kotlin.test` in `commonTest` on the JVM
host lane and the iOS simulator; Compose UI tests in `uiTest` on three simulators and two Android targets;
`NoiseGateEndToEndTest` through the real engine on both platforms; `tools/capture-noise-gate.sh` for captures and the
on-screen check

**Target Platform**: Android (phone, foldable) and iOS (iPhone, iPad, iPhone Duo)

**Project Type**: mobile app (KMP) over a shared C++ engine; the change crosses `engine`, `:shared:core`,
`:shared:engine`, `:shared:data`, `:shared:ui` and one debug launch argument in each app shell

**Performance Goals**: the gate adds one pass over each new hop and no allocation on the analysis thread; a move of
the handle reaches the analysis within one hop (21 ms); the fill is drawn every display frame with zero
recompositions; the control recomposes only when the handle moves or the gate opens or closes

**Constraints**: nothing new on the audio callback (Constitution I); listening never restarts when the handle moves
(FR-003); every reading unchanged with the gate off (FR-002); 44 dp touch height; no decibel figure on the control;
zero compiler warnings

**Scale/Scope**: one control in five forms of one screen; about 11 new source files and 30 touched; 5 targets, each
upright and sideways, plus three foldable postures

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict |
|---|---|
| I. Real-time engine isolation | Pass. The gate runs on the analysis thread; the callback still only copies into the ring buffer. The value crosses as one atomic loaded once per hop; the setter takes the command mutex no callback takes. No callback into Kotlin; the screen reads the seqlock snapshot as today. |
| II. Shared control plane, Compose UI | Pass. One control in `commonMain`; the ViewModel is the only caller of the audio seam; the level is read in the draw phase and never passes through `StateFlow`; no new `expect`/`actual`. |
| III. Test-first | Pass. Each layer's tests are listed in its contract and are written first: Catch2 before the analyzer, `commonTest` before the type, the store and the ViewModel, `uiTest` before the control and its placement, the end-to-end test before the bridges are called done. Kotlin tests use `FakeAudioEngine`; the engine tests run without an audio device. |
| IV. Adaptive, input-agnostic UI | Pass, and the centre of the verification: every form by window size and posture, pointer hover and click on the track, keyboard arrows, focus ring, 44 dp, semantics and a test tag, tokens only, captures on Android and iOS at compact and expanded widths in both orientations. |
| V. Document model in beats | N/A: a preference, not project data. |
| VI. Simplicity | Pass. No dependency. One C function instead of a config field plus a setter (R4); the margin is a constant, not a parameter (R3); the existing filter and smoother are reused (R1, R2); the slider reuses Material and the tempo slider's handle (R9); the measurement uses the control itself (R8). The debug gate argument is required by FR-022 and is what makes captures on five targets one command. |
| VII. Code organisation | Pass. `NoiseGate`, `LevelGate`, `NoiseGateSlider`, `LevelSmoother`, `TembraSliderThumb`, `TembraSliderDefaults`, `TestSignalLevel`, `TunerNoiseGateRequest`, `LocalTunerNoiseGate` each in their own file, in their feature or design-system package, `internal` by default. |
| VIII. Skills | Pass. `kotlin-api-design` (`NoiseGate` as a value class whose companion owns the scale and the parsing; the audio seam stays primitive and platform-free), `compose-component-design` (`NoiseGateSlider`: `modifier` first optional parameter on the root, fixed content as primitives, no shape flags; optional slots for the control's place in `AutoListenPane` and the two settings cards), `compose-performance` (frame-rate level read in `drawBehind`, nothing read in composition per frame), `compose-state-and-effects` (state hoisted in the ViewModel, the content composable stays stateless, no new effect), `compose-ui-testing-patterns` (controlled state, injected `MutableInteractionSource`, frozen clock, semantics before pixels, keyboard availability stated per lane), `gradle-run` for every Gradle run. The compose compiler report (`-PcomposeReports=true`) is part of the polish phase. |

Build order: the tuner is already built; this refines it and pulls nothing forward, so no amendment is needed.

Re-check after Phase 1: unchanged. No Complexity Tracking entry is needed.

## Project Structure

### Documentation (this feature)

```text
specs/006-tuner-noise-gate/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── engine-c-abi-v4.md
│   ├── tuner-audio-kotlin.md
│   ├── tuner-preferences.md
│   └── tuner-screen.md
├── checklists/requirements.md
├── screenshots/            # captures + NOTES.md (implementation)
└── tasks.md                # /speckit-tasks
```

### Source Code (repository root)

```text
app/engine/
├── include/tembra_engine.h                      # revision 4: tembra_tuner_set_gate, two reading fields
├── src/engine_info.cpp                          # version 0.4.0
├── src/core/engine_core.{h,cpp}                 # keeps the gate value, forwards it to a live analyzer
├── src/tuner/tuner_abi.cpp                      # the new forwarder with its argument check
├── src/tuner/level_gate.{h,cpp}                 # new: the open/close rule (R3)
├── src/tuner/tuner_analyzer.{h,cpp}             # window level, gate, skip the detector when closed, publish
├── android/src/main/cpp/tembra_jni.cpp          # nativeTunerSetGate; nine-float read
└── tests/
    ├── level_gate_test.cpp                      # new
    ├── tuner_analyzer_test.cpp, engine_core_state_test.cpp, engine_info_test.cpp
    ├── signal_fixtures.h                        # level, fade, mix and wobble helpers
    └── CMakeLists.txt

app/shared/core/src/
├── commonMain/kotlin/audio/tembra/core/tuner/NoiseGate.kt          # new
└── commonTest/kotlin/audio/tembra/core/tuner/NoiseGateTest.kt      # new

app/shared/engine/src/
├── commonMain/kotlin/audio/tembra/engine/
│   ├── TunerAudio.kt, PitchSnapshot.kt, FakeAudioEngine.kt, TestSignals.kt
│   └── TestSignalLevel.kt                                           # new
├── androidMain/…/NativeAudioEngine.android.kt, iosMain/…/NativeAudioEngine.ios.kt
├── commonTest/…/FakeAudioEngineTest.kt, TestSignalsTest.kt, TestSignalLevelTest.kt (new)
├── androidDeviceTest/…/NoiseGateEndToEndTest.kt (new), NativeAudioEngineTest.kt
└── iosTest/…/NoiseGateEndToEndTest.kt (new), NativeAudioEngineTest.kt

app/shared/data/src/
├── commonMain/kotlin/audio/tembra/data/tuner/
│   └── TunerPreferenceKeys.kt, TunerPreferencesSnapshot.kt, DataStoreTunerPreferences.kt
└── commonTest + androidDeviceTest + iosTest: the three existing preference tests

app/shared/ui/src/
├── commonMain/kotlin/audio/tembra/ui/
│   ├── designsystem/TembraSliderThumb.kt, TembraSliderDefaults.kt   # new: the handle both sliders share
│   ├── metronome/components/TempoSlider.kt, MetronomeDefaults.kt    # use the shared handle; no visual change
│   ├── tuner/NoiseGateSlider.kt, LevelSmoother.kt                   # new
│   ├── tuner/TunerNoiseGateRequest.kt, LocalTunerNoiseGate.kt       # new: the debug launch value
│   ├── tuner/AutoListenPane.kt                                      # footer slot; gauge fitted above it
│   ├── tuner/TunerSettingsColumn.kt, TunerTabletopSettings.kt       # autoListenControls slot
│   ├── tuner/TunerScreenContent.kt, TunerScreen.kt                  # build the control once, place it per layout
│   ├── tuner/TunerUiState.kt, TunerCallbacks.kt, TunerViewModel.kt, TunerCopy.kt, TunerTestSignalRequest.kt
│   └── App.kt                                                       # tunerNoiseGate parameter and local
├── iosMain/kotlin/audio/tembra/ui/MainViewController.kt             # tunerNoiseGate argument
├── commonTest/kotlin/audio/tembra/ui/tuner/
│   ├── TunerViewModelTest.kt, TunerUiStateTest.kt, TunerTestSignalRequestTest.kt
│   └── LevelSmootherTest.kt, TunerNoiseGateRequestTest.kt           # new
└── uiTest/kotlin/audio/tembra/ui/tuner/
    ├── NoiseGateSliderTest.kt, TunerNoiseGateLayoutTest.kt          # new
    └── TunerScreenLayoutTest.kt, TunerTabletopLayoutTest.kt, TunerHingeLayoutTest.kt,
        TunerScreenByEarTest.kt, TunerScreenDeniedTest.kt

app/androidApp/src/main/kotlin/audio/tembra/android/MainActivity.kt  # debug extra tembra.tuner.noiseGate
app/iosApp/iosApp/ContentView.swift                                  # debug argument --tembra-tuner-noise-gate=
app/tools/capture-noise-gate.sh                                      # new: captures and the on-screen check
design/stitch/DESIGN.md                                              # after acceptance (FR-027)
```

**Structure Decision**: the feature follows the existing layering end to end, one small change per layer, with the
new types in the feature packages that own them (`core/tuner`, `ui/tuner`) and the shared handle in the design
system because two features now use it.

## Order of work

Each step starts with its failing tests (contracts list them) and ends with its lanes green.

1. **Engine**: fixtures, `LevelGate`, the window level and the gate in the analyzer, the C function, ABI 4. Lane:
   `engineHostTest`.
2. **Bridges and seam**: JNI and cinterop, `TunerAudio`, `PitchSnapshot`, `FakeAudioEngine`, test-signal levels and
   catalogue, `NoiseGateEndToEndTest`. Lanes: host, iOS simulator, Android device.
3. **Domain and store**: `NoiseGate`, the sixth key. Lanes: host, iOS, device.
4. **State**: `TunerUiState`, callbacks, the ViewModel's three intents and load order. Lane: host, iOS.
5. **Control**: extract the shared handle (the metronome's tests stay green), `LevelSmoother`, `NoiseGateSlider`.
   Lane: `uiTest` on a simulator and a device.
6. **Placement**: the three slots, the fitted gauge, `TunerNoiseGateLayoutTest` in every form and orientation.
7. **Debug arguments and the capture tool**.
8. **Verification on the five targets** (quickstart §1 to §5, §7), including the scale checkpoint (§6). A scale that
   fails the rule of research R8 stops here for `/speckit-clarify`.
9. **Polish**: compose compiler report, zero warnings, the notes, the contracts of features 003 and 005 pointed at
   this feature where they describe the tuner's screen, preferences and ABI.
10. **After the owner accepts**: `DESIGN.md` and the Stitch screens (FR-027).

## Spec amendments made while planning

Seven, listed with their reasons at the end of [research.md](research.md): what the filter really does to sound
outside the range (FR-004), the slider is not the app's first (FR-020), the scale is measured at a checkpoint in the
build, a debug launch argument for the gate (FR-022), orientation and on-screen proof on every target (FR-023,
FR-024, SC-008), a rule for a stored value halfway between two steps (FR-014), and two wordings that follow from
measuring the level over the analysis window (FR-006, User Story 3).

## Complexity Tracking

None.
