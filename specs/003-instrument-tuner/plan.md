# Implementation Plan: Instrument Tuner

**Branch**: `003-instrument-tuner` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-instrument-tuner/spec.md`, clarified 2026-09-26, and the user's planning brief (guitar 6/7-string and cello, presets, Auto Listen and By Ear, the chromatic meter widget with the in-tune pill and Flat/Sharp labels, a first-class microphone permission flow with a visible mic state and a way to re-request, stopping the microphone on navigation for privacy and clean audio routing, the Stitch visuals including the gradients, multi-device verification with no overlaps, shared modules for both platforms, and a common abstraction over the tuner implementation).

## Summary

The Tuner destination becomes a working chromatic tuner with two modes. **Auto Listen** captures the
microphone through the one C++ engine: the platform callback (Oboe on Android, a RemoteIO Audio Unit
on iOS) copies frames into a lock-free ring buffer, an engine analysis thread runs the McLeod Pitch
Method on 4096-frame windows every 1024 frames and publishes a smoothed reading through a seqlock; a
`Canvas` meter reads that telemetry in the draw phase every frame and draws the note, the live
frequency, the reference chip, the arc with the needle, the "In tune" pill and the highlighted Flat or
Sharp label, with the design's radial-gradient card behind it. **By Ear** shows one card per string of
the selected tuning; auditioning plays a click-free synthesized tone from the engine's output stream,
looping or once, unless a recording named by instrument and note has been dropped into a documented
resources folder. Instrument, string count, preset, mode and the loop toggle live in a DataStore
preferences file. Microphone access is an injected `MicrophonePermission` interface (Android in the
app module with the activity result launcher, iOS with `AVAudioSession`): entering Auto Listen asks at
once, a denied state replaces the meter with an "Allow microphone" action that re-requests or opens
the settings, and a grant made in the settings starts listening on return. The microphone is held by a
pure `ListeningPolicy` (mode, screen visible, app foreground, permission, interruption) so leaving the
tab, backgrounding or switching to By Ear releases it immediately, `AVAudioSession` is deactivated
with `notifyOthersOnDeactivation` and Android transient focus is used only for tones; the engine adds
a privacy backstop that stops capture when nobody reads the meter for five seconds. The adjustable
seams are the C++ `PitchDetector` interface plus a single config struct, the Kotlin `TunerAudio`
interface with a fake, and `PitchMath`/`TuningPresets` in the domain. For verification without an instrument, debug builds accept a test signal (a note and an offset in
cents) that a synthetic input device feeds through the same ring buffer, analyzer and telemetry
path as the microphone; an end-to-end test asserts every catalogue entry on both platforms and a
script captures the meter state for each. Technology evidence and alternatives:
[research.md](research.md) R1–R16.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform; Kotlin/Native for iOS); C++17 for the engine
(analysis, tone, sample player, platform shells); Swift 5 for a few lines in the iOS shell; Bash for
the glyph script.

**Primary Dependencies** (research R13): added `androidx.datastore:datastore-preferences-core:1.2.1`
(pulls `datastore-core-okio` and Okio 3.x). Already present and used: Oboe 1.11.0 (Prefab),
Signalsmith `dsp/fft.h` and dr_wav (vendored), Compose Multiplatform 1.12.1, Material 3 1.9.0 and
adaptive 1.2.0, lifecycle 2.11.0 (`LifecycleStartEffect` for foreground, `LifecycleResumeEffect` for the permission refresh), Navigation 3 1.1.2, Metro 1.4.4, compose
resources. iOS system frameworks AudioToolbox, CoreAudio (RemoteIO) and AVFAudio (session,
permission). No pitch-detection, permission or audio-session library.

**Storage**: DataStore Preferences file `<AppDirectories.preferences>/tembra.preferences_pb`
(five keys); recordings, if any, are bundled Compose resources; nothing else persisted.

**Testing**: Catch2 on the macOS host for DSP accuracy, analyzer behaviour, tones, samples,
recordings on pitch and the engine state machine (offline, no device); `kotlin.test` +
`kotlinx-coroutines-test` in `commonTest` (JVM and iOS simulator) for domain, policy, ViewModel,
preferences, tokens and geometry; `runComposeUiTest` in `uiTest` (iOS simulator and Android device)
for both modes, the meter, the components, semantics, hover, focus, 44 dp and large-font layout;
`androidDeviceTest` / `iosTest` for the real engine's tone path, idle snapshot and the DataStore
round trip; device and simulator tests for the platform permission, session and focus classes (analysis C1);
`TunerSignalEndToEndTest` feeding the test-signal catalogue through the real engine on both
platforms (research R16); a scripted screenshot matrix of the meter states
(`tools/capture-tuner-states.sh`); manual checks for prompts, interruptions, routes and the OS
microphone indicator (quickstart §6–§9). Details: research R12.

**Target Platform**: Android 8.0+ (API 26) phones, foldables, tablets and desktop-mode windows;
iOS 16+ iPhone and iPad. Microphone input at the device's native rate (48 kHz requested, 44.1 kHz
accepted).

**Project Type**: mobile app; shared Compose Multiplatform UI in `:shared:ui`, domain in
`:shared:core`, engine facade in `:shared:engine`, preferences in `:shared:data`, C++ in `engine/`.

**Performance Goals**: reading within 250 ms of a note starting (window 85 ms + hop 21 ms + smoothing,
research R1); analysis under 1 ms per hop on a phone core (FFT autocorrelation); needle redrawn every
display frame with no recomposition outside the meter node; tone start within 100 ms; microphone
released within 1 s of leaving Auto Listen; the audio callbacks do no allocation, locking or logging.

**Constraints**: nothing Kotlin on either audio callback (Constitution I); at most one stream open,
never listening and a tone together (spec FR-006); every visual value from tokens; one type per file;
zero compiler and lint warnings; Kotlin tests never link the engine; tests independent of animation
timing; the meter must not clip, overlap or scroll horizontally at any width or font scale; the two
design details corrected on purpose are the 44 dp touch targets on 28/32 dp segments and no drop
shadow on the note (research R9).

**Scale/Scope**: about 22 new C++ files (engine core, tuner DSP, platform shells, tests), C ABI
revision 2 with 11 functions and 2 structs, about 60 new Kotlin files across the four shared modules
and the Android app (domain 10, engine 8, data 6, UI 30, platform 6), 12 vector assets, 8 new test
classes on the JVM/iOS lanes and 6 UI test classes, manifest and plist entries, catalog and licence
rows, README rows.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | Capture and playback callbacks live in C++ platform shells and only copy frames to or from the ring buffer and the tone renderer; the analyzer runs on its own engine thread; commands are atomics and the reading is a seqlock; Kotlin calls the C ABI only from ordinary threads and reads telemetry (`tembra_tuner_read`) with no callback into Kotlin; buffers are allocated in `tembra_tuner_start` before the stream opens; one engine serves both platforms with Oboe and RemoteIO as thin I/O (research R1, R3, R4). Variable frame counts flow straight into the ring buffer. | PASS |
| II. Shared Kotlin Control Plane, Compose UI | Domain in `:shared:core`, facade and actuals in `:shared:engine`, preferences in `:shared:data`, screen and ViewModel in `:shared:ui` `commonMain`; dependencies point inward. One `TunerViewModel` with a single `TunerUiState` `StateFlow`, plain-function intents, no screen touches the engine (only `telemetry.read` in the draw phase, which is the telemetry rule, not a command). High-frequency telemetry is read inside the meter's draw lambda and never through `StateFlow` (research R8); the 10 Hz `reading` is for semantics and tests. `expect`/`actual` stays on `NativeAudioEngine` (platform SDK access); permission and preferences are interfaces injected with Metro. | PASS |
| III. Test-First | Every contract lists failing tests before code: Catch2 for DSP and engine state, `commonTest` for domain, policy, ViewModel, preferences, tokens and geometry, `uiTest` for screens and components, device/simulator tests for the real engine and DataStore; Kotlin tests use `FakeAudioEngine`, `FakeMicrophonePermission`, `FakeTunerPreferences`; the engine's accuracy is proven offline without an audio device (research R12); the platform permission, session and focus classes have device or simulator tests (analysis C1), and the test-signal end-to-end test exercises the whole capture path on real devices without a microphone (R16). | PASS |
| IV. Adaptive, Input-Agnostic UI | Layouts keyed on the window size class (compact/medium two selector rows and two-column cards; expanded one row, centred meter, three columns; phone landscape, a window 600 dp or wider and under 480 dp tall, a settings column beside the content with a height-fitted meter and two card rows, added 2026-09-28 from the Stitch landscape screens); hover, focus ring, keyboard activation, `minimumInteractiveComponentSize()` for 44 dp on the design's 28/32/16 dp controls, `Tab`/`Switch`/`Button` roles, `stateDescription` on the meter and cards, `testTag` on every control; no literal values (new tokens in data-model.md §8); screenshots of both modes at compact and expanded on both platforms plus the denied and large-font captures reviewed against the Stitch references (quickstart §8). | PASS |
| V. Document Model in Beats | No document, no musical time; A4 = 440 Hz is a domain constant. | PASS (not exercised) |
| VI. Simplicity | One new dependency, DataStore, from the decided stack; no permission, pitch or audio-session library; the FFT and WAV decoder are already vendored; components are custom only where Material cannot match the design (Complexity Tracking #2); the engine watchdog is twenty lines (#3); no chromatic/string-lock switch, no custom tunings, no reference-pitch control (spec FR-031). | PASS |
| VII. Code Organisation | One type per file; packages `audio.tembra.core.tuner`, `audio.tembra.engine`, `audio.tembra.data.tuner`, `audio.tembra.ui.tuner`, `audio.tembra.ui.platform`, `audio.tembra.ui.designsystem`, `audio.tembra.ui.theme`; C++ under `engine/src/tuner` and `engine/src/platform`; `internal` by default. | PASS |
| Technology & Architecture Constraints | Kotlin Multiplatform + Compose, multiplatform ViewModel, Navigation 3 entry-scoped ViewModel, Metro, DataStore, Okio (transitive); engine C++ with a C ABI, Oboe and RemoteIO, Signalsmith FFT, dr_wav; no forbidden library; design tokens from `theme.json` plus screen-derived tokens with evidence (research R9); Tuner stays a primary destination; product name unchanged. The transport bar the constraints mention is absent from every tuner design screen and belongs to the transport feature that now follows the tuner in the build order, as feature 002 recorded for its placeholders. | PASS |
| Development Workflow & Quality Gates | Build order (v1.0.3): scaffold, navigation shell, **tuner**, audio spine … The tuner's input and output paths are built for the tuner and obey Principle I so the audio spine can grow from them (research R15); no later feature is pulled forward (no transport, timeline, mixer). Definition of done maps to the four lanes plus screenshots (quickstart §2, §8). | PASS |

**Post-design re-check (after Phase 1)**: unchanged. Four interpretations a reviewer could read as
deviations are recorded in Complexity Tracking with the simpler alternative and why it was rejected.

## Project Structure

### Documentation (this feature)

```text
specs/003-instrument-tuner/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R15 with evidence
├── data-model.md        # Phase 1: domain, preferences, audio state and policy, snapshot, permission, audition, UiState, geometry and tokens
├── quickstart.md        # Phase 1: build/run/test, both modes, presets, recordings, permission, privacy checks, screenshots, input, troubleshooting
├── contracts/
│   ├── engine-c-abi-v2.md           # C ABI revision 2, internal C++ classes, threading, JNI and cinterop bindings, Catch2 tests
│   ├── tuner-audio-kotlin.md        # TunerAudio, AudioEngine, NativeAudioEngine actuals (session, focus, events), FakeAudioEngine
│   ├── tuner-domain.md              # Instrument, Note, presets, PitchMath, tests
│   ├── tuner-preferences.md         # TunerPreferences, DataStore wiring, AppDirectories.preferences
│   ├── reference-sounds.md          # recordings folder and naming rule, ReferenceSoundRepository, on-pitch test
│   ├── microphone-permission.md     # MicrophonePermission, Android and iOS implementations, denied state
│   ├── tuner-screen.md              # TunerViewModel, ListeningPolicy, TunerScreen composition, semantics, copy, tests
│   └── design-system-additions.md   # tokens, gradients, icons, TembraSegmentedControl/Toggle/StatusPill/GradientCard
├── checklists/requirements.md
├── screenshots/         # created during implementation (quickstart §8)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── gradle/libs.versions.toml                        + androidx-datastore 1.2.1 (datastore-preferences-core)
├── THIRD_PARTY_LICENSES.md                          + DataStore and Okio rows
├── README.md                                        + rows: platform permission, preference, reference recording, design glyph; recordings folder
├── engine/
│   ├── include/tembra_engine.h                      ABI 2: tuner config/reading/start/stop/read, tone/sample functions, audio state, errors
│   ├── CMakeLists.txt                               + src/tuner/*, src/platform/*, if(ANDROID)/if(IOS) shells, TEMBRA_TUNER_RECORDINGS_DIR for tests
│   ├── src/engine_info.cpp                          version 0.2.0
│   ├── src/platform/audio_device.h, null_audio_device.{h,cpp}, synthetic_audio_device.{h,cpp}   (test-signal input, research R16)
│   ├── src/platform/android/oboe_audio_device.{h,cpp}          input (Unprocessed, LowLatency, Exclusive) + output streams, error callback
│   ├── src/platform/apple/remote_io_audio_device.{h,cpp}       RemoteIO unit, input and render callbacks
│   ├── src/tuner/ring_buffer.h, seqlock.h, pitch_detector.h, mcleod_pitch_detector.{h,cpp}, band_pass.{h,cpp},
│   │              pitch_smoother.{h,cpp}, tuner_analyzer.{h,cpp}, tone_generator.{h,cpp}, sample_player.{h,cpp},
│   │              wav_decoder.{h,cpp}, tuner_engine.{h,cpp}, tuner_abi.cpp
│   ├── android/src/main/cpp/tembra_jni.cpp          + nativeTuner*/nativeTone*/nativeSample*/nativeAudioState
│   └── tests/                                       + signal_fixtures.h, fake_audio_device.h, mpm_test, band_pass_test, ring_buffer_test, tuner_analyzer_test,
│                                                      tone_generator_test, sample_player_test, recordings_on_pitch_test, seqlock_test, tuner_engine_state_test; engine_info_test updated
├── shared/core/src/commonMain/kotlin/audio/tembra/core/tuner/
│   │   Instrument.kt, GuitarStringCount.kt, PitchClass.kt, Note.kt, TargetString.kt, TuningPreset.kt, TuningPresets.kt,
│   │   TunerMode.kt, PitchDirection.kt, NoteReading.kt, PitchMath.kt, RomanNumerals.kt
│   └── commonTest/…/tuner/ NoteTest, PitchMathTest, TuningPresetsTest, RomanNumeralsTest
├── shared/engine/
│   ├── build.gradle.kts                              engine task inputs + src/tuner, src/platform; engineHostTest passes the recordings dir
│   ├── src/nativeInterop/cinterop/tembra_engine.def  linkerOpts + AudioToolbox, CoreAudio
│   ├── src/commonMain/kotlin/audio/tembra/engine/    TunerAudio.kt, AudioEngine.kt (extends TunerAudio), NativeAudioEngine.kt (expect, no ctor),
│   │                                                 TunerAnalysisConfig.kt, PitchSnapshot.kt, AudioState.kt, AudioResult.kt, SampleHandle.kt, AudioEvent.kt, FakeAudioEngine.kt (extended),
│   │                                                 TestSignal.kt, TestSignals.kt (catalogue, research R16)
│   ├── src/androidMain/…/NativeAudioEngine.android.kt (context, audio focus), AndroidAudioFocus.kt
│   ├── src/iosMain/…/NativeAudioEngine.ios.kt (AVAudioSession, notifications), IosAudioSession.kt
│   ├── src/commonTest/…/FakeAudioEngineTest.kt, TestSignalsTest.kt; androidDeviceTest + iosTest: NativeAudioEngineTest extended, TunerSignalEndToEndTest.kt;
│   │   androidDeviceTest/…/AndroidAudioFocusTest.kt; iosTest/…/IosAudioSessionTest.kt
├── shared/data/
│   ├── build.gradle.kts                              + datastore-preferences-core
│   ├── src/commonMain/kotlin/audio/tembra/data/      AppDirectories.kt (+ preferences), tuner/TunerPreferencesSnapshot.kt, TunerPreferenceKeys.kt, TunerPreferences.kt,
│   │                                                 DataStoreTunerPreferences.kt, FakeTunerPreferences.kt, TembraDataStore.kt
│   ├── src/androidMain/…/AndroidAppDirectories.kt, iosMain/…/IosAppDirectories.kt   (+ preferences)
│   └── tests: commonTest TunerPreferencesSnapshotTest, FakeTunerPreferencesTest; androidDeviceTest + iosTest DataStoreTunerPreferencesTest; AppDirectoriesTest extended
├── shared/ui/
│   ├── src/commonMain/composeResources/drawable/ic_{mic,mic_off,hearing,play_arrow,pause,volume_up,expand_more,arrow_left,arrow_right,settings}_24.xml (script); the design glyphs ic_guitar_24.xml / ic_cello_24.xml were removed 2026-10-12
│   ├── src/commonMain/composeResources/files/tuner/  (empty; documented drop-in folder, .gitkeep)
│   ├── src/commonMain/kotlin/audio/tembra/ui/
│   │   ├── App.kt (+ tunerTestSignal), navigation/NavigationShell.kt (+ start on Tuner when a test signal is present)
│   │   ├── di/AppBindings.kt (TunerPreferences, ReferenceSoundRepository; audio engine provider moved), di/AppGraph.kt (+ tunerPreferences, microphonePermission)
│   │   ├── theme/ TembraTypeScale.kt, TembraTypographyExtras.kt, TembraTypography.kt, TembraSpacing.kt (+ tokens), TembraGradients.kt, LocalTembraGradients.kt, RadialGlow.kt, Glow.kt, TembraTheme.kt (+ gradients), TembraIcon.kt (+ 12)
│   │   ├── designsystem/ SegmentSpec.kt, SegmentedStyle.kt, SegmentAccent.kt, TembraSegmentedControl.kt, TembraToggle.kt, TembraStatusPill.kt, TembraGradientCard.kt
│   │   ├── platform/ MicrophonePermission.kt, MicrophonePermissionStatus.kt
│   │   └── tuner/ TunerScreen.kt (replaces the placeholder), TunerScreenContent.kt, TunerViewModel.kt, TunerUiState.kt, TunerCallbacks.kt, AudioStatePill.kt,
│   │              ListeningPolicy.kt, PolicyInputs.kt, DesiredAudio.kt, MeterReading.kt, AuditionState.kt, ReferenceSound.kt, ResourceReader.kt, ComposeResourceReader.kt,
│   │              ReferenceSoundRepository.kt, TunerTestSignalRequest.kt, LocalTunerTestSignal.kt,
│   │              PitchTelemetry.kt, PitchFrameLoop.kt, TunerSelectors.kt, TunerSelectorControls.kt, TunerSettingsColumn.kt, TunerLayout.kt, AutoListenPane.kt, TunerMeter.kt, MeterGeometry.kt, MeterLayout.kt, MeterSize.kt, MicrophoneDeniedState.kt,
│   │              ByEarPane.kt, TuningPresetPicker.kt, LoopToneToggle.kt, StringCardGrid.kt, StringCard.kt, TunerCopy.kt
│   ├── src/iosMain/kotlin/audio/tembra/ui/ IosAppGraph.kt (+ NativeAudioEngine(), IosMicrophonePermission), MainViewController.kt (+ tunerTestSignal), platform/IosMicrophonePermission.kt
│   ├── src/iosTest/kotlin/audio/tembra/ui/platform/IosMicrophonePermissionTest.kt
│   ├── src/commonTest/…/ platform/FakeMicrophonePermission.kt, platform/MicrophonePermissionStatusTest.kt, tuner/{ListeningPolicyTest,TunerViewModelTest,ReferenceSoundRepositoryTest,MeterGeometryTest,TunerUiStateTest,TunerTestSignalRequestTest}.kt, theme/TunerTokensTest.kt
│   ├── src/androidHostTest/…/theme/IconAssetsTest.kt (extended)
│   └── src/uiTest/…/ tuner/{TunerScreenTest,TunerMeterTest}.kt, designsystem/{TembraSegmentedControlTest,TembraToggleTest,TembraStatusPillTest,TembraGradientCardTest}.kt
├── tools/capture-tuner-states.sh                     new: launches the app with each catalogue test signal and captures the meter (research R16)
├── androidApp/
│   ├── build.gradle.kts                              + androidTestImplementation(libs.androidx.test.runner)
│   ├── src/androidTest/kotlin/audio/tembra/android/AndroidMicrophonePermissionTest.kt
│   ├── src/main/AndroidManifest.xml                  + RECORD_AUDIO
│   └── src/main/kotlin/audio/tembra/android/ AndroidAppGraph.kt (+ NativeAudioEngine(context), MicrophonePermissionHost, AndroidMicrophonePermission), MainActivity.kt (launcher + host attach/detach; "tembra.tuner.testSignal" extra in debug),
│                                              MicrophonePermissionHost.kt, AndroidMicrophonePermission.kt
└── iosApp/iosApp/Info.plist                          + NSMicrophoneUsageDescription; ContentView.swift reads --tembra-tuner-test-signal= in #if DEBUG
```

**Structure Decision**: no new Gradle module. The domain goes to `:shared:core` (pure, tested on
both lanes), the engine facade and the C ABI to `:shared:engine` and `engine/` (the only place that
touches audio), preferences to `:shared:data` (the persistence module gets its first real
implementation, DataStore), and everything visual to `:shared:ui` with the tuner in its own feature
package and reusable pieces in `designsystem` and `theme`, as `app/README.md` prescribes. The
Android permission implementation sits in `:androidApp` because it needs the activity's result
registry; the iOS one sits in `iosMain` of `:shared:ui` because `AVAudioSession` needs no shell code.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| #1 The engine's C ABI gains tuner-specific functions (start listening, read pitch, tones, samples) before the audio spine's general command FIFO and telemetry model exist | The tuner needs one input path and one output path now; a minimal, explicit ABI is smaller and safer than a premature general command bus, and the classes behind it (audio device shells, ring buffer, seqlock, renderer) are what the audio spine will reuse (research R15). | Building the audio spine's command FIFO and graph first would pull a later feature forward against the build order; capturing in Kotlin would violate Principle I. |
| #2 Custom `TembraSegmentedControl`, `TembraToggle`, `TembraStatusPill`, `TembraGradientCard` instead of Material's `SegmentedButton`, `Switch`, `AssistChip`, `Card` (Constitution VI "prefer the platform") | The design fixes 28/32 dp segments with filled active states, a 32 × 16 dp toggle and gradient cards; Material's components are 40 dp outlined, 52 × 32 dp and flat, and cannot be restyled to those sizes. Semantics, hover, focus and 44 dp targets use standard modifiers, as in feature 002's bar and rail. | Restyling Material's components reaches neither the sizes nor the fills. |
| #3 Engine-side privacy watchdog (stop capture when the reading is not read for five seconds) in addition to the Kotlin lifecycle policy | The user asked for the best way to guarantee the microphone stops on navigation; the policy is deterministic, the watchdog makes a Kotlin lifecycle bug unable to keep the microphone open. About twenty lines on the analysis thread, off by default only through the config. | Relying on the lifecycle alone leaves a single point of failure for a privacy property. |
| #5 A synthetic input device and a debug launch argument (test signals) ship in the engine and the shells although no user feature needs them | The user asked to feed generated sounds at chosen pitches into the tuner, test the values and capture the states; the synthetic `AudioDevice` reuses the whole capture path (ring buffer, analyzer, seqlock, JNI/cinterop, meter), so the end-to-end test and the screenshot matrix prove the real pipeline on devices without a microphone. About 120 lines of C++ and 60 of Kotlin; unreachable in release builds. | Kotlin-level scripted telemetry (already used by `TunerMeterTest`) proves only the drawing, not the engine path; a loopback audio device does not exist on phones. |
| #4 The meter draws its texts in a `Canvas` with `TextMeasurer` rather than `Text` composables | Frame-rate updates through composables would recompose on every hop (Constitution II's telemetry rule); one draw-phase node keeps the needle, note, readout and labels on the same clock. Accessibility is preserved with a `stateDescription` fed at 10 Hz. | Two clocks (canvas needle, composable texts) show visible mismatch and still recompose at 20–47 Hz. |
