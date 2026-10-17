# Implementation Plan: Metronome

**Branch**: `004-metronome` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-metronome/spec.md`, clarified 2026-09-29, and
the user's planning brief (shareable components; time signature, subdivision, accents, styles,
sound selection, the BPM dial with the tempo name, the slider, tap tempo, play/pause; the
current official Android and iOS guidance for background playback, incoming calls and other apps
taking the audio; the three Stitch screens followed visually with no new colours or gradients;
WAV as the custom-sound format with import; the installed Compose skills; and, mid-plan, the
instruction to build on the metronome research brief, the report and the NotebookLM findings).

## Summary

The Metronome destination becomes a global, standalone metronome (constitution v1.2.0). The
click is timed by the one C++ engine inside the output callback, the brief's "counter in the
audio callback": an absolute frame counter and a double-precision tick position recomputed from
the live tempo, so there is no drift and a tempo change reaches the next tick. Kotlin computes
the bar as a flat list of tick levels (time signature × subdivision × accent cells, with the
style as a preset that fills the cells) and publishes it, with the tempo, as an immutable
snapshot the callback loads once per block; five built-in click sounds are synthesised at open
in three levels (Accent, Click, Sub), and the user's own WAV is copied into the app's files,
decoded with dr_wav, onset-aligned, trimmed to two seconds, loudness-matched, resampled with
miniaudio and published the same way. One sounding voice is faded 3 ms before the next tick so
the click stays crisp at any tempo and the sample is never pitch-shifted. The beat light reads a
seqlock tick snapshot in the draw phase with the output latency compensated. Background
playback follows the official platform guidance fetched for this plan: an Android
`mediaPlayback` foreground service plus `AUDIOFOCUS_GAIN`, and the iOS `.playback` category
with the `audio` background mode; calls, another app's playback and unplugged headphones stop
the click and never restart it. Tempo (dial, steppers, slider, tap, keyboard, wheel), bar
shape, sound choice and the custom sound live in DataStore preferences. The screen follows the
three Stitch screens with tokens only (two gradient tokens added, no new colour) and is built
from reusable, caller-placeable components that the transport feature will reuse. Technology
evidence and alternatives: [research.md](research.md) R1–R16.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform; Kotlin/Native for iOS); C++17 for the engine
(scheduler, click synthesis, WAV preparation, platform shells); a few lines of Swift for the
debug launch argument; Bash for the glyph script.

**Primary Dependencies** (research R16): no new library. Already present and used: Oboe 1.11.0
(Prefab), dr_wav and miniaudio (vendored; miniaudio's resampler is now compiled in), Compose
Multiplatform 1.12.1, Material 3 1.9.0 and adaptive 1.2.0, lifecycle 2.11.0, Navigation 3
1.1.2, Metro 1.4.4, DataStore Preferences 1.2.1; Okio (already transitive through DataStore)
becomes a catalog entry and a direct dependency of `:shared:data`, with `okio-fakefilesystem`
in `commonTest`. Platform: Android `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
and a `mediaPlayback` service; iOS `UIBackgroundModes` `audio`; iOS `UIDocumentPickerViewController`
and Android `ActivityResultContracts.OpenDocument` for the WAV picker.

**Storage**: DataStore Preferences file `tembra.preferences_pb` (eight metronome keys, shared
instance with the tuner); the prepared custom sound's copy at
`<AppDirectories.documents>/metronome/custom.wav`; nothing else persisted.

**Testing**: Catch2 on the macOS host for the scheduler's timing over ten rendered minutes, the
click synthesis levels, the custom-sound preparation on generated fixtures, the triple buffer
and the engine state rules (offline, no device); `kotlin.test` + `kotlinx-coroutines-test` in
`commonTest` (JVM and iOS simulator) for the domain (grid, styles, tap tempo, markings), the
ViewModel with fakes, preferences, the sound store on a fake file system, tokens and dial
geometry; `runComposeUiTest` in `uiTest` (iOS simulator and Android device) for the screen at
three layouts and every component; `androidDeviceTest` / `iosTest` for the real engine's
metronome round trip and timing, focus and session classes, DataStore and file round trips;
`androidTest` for the foreground-service host; manual checks for lock screen, calls, other
apps, headphones and the beat light (quickstart §5–§8). Details: research R15.

**Target Platform**: Android 8.0+ (API 26; foreground-service type rules of API 34+ honoured)
phones, foldables, tablets and desktop-mode windows; iOS 16+ iPhone and iPad. Output at the
device's rate (48 kHz requested on Android with Oboe's conversion; hardware rate on iOS).

**Project Type**: mobile app; shared Compose Multiplatform UI in `:shared:ui`, domain in
`:shared:core`, engine facade in `:shared:engine`, preferences and the sound store in
`:shared:data`, C++ in `engine/`, the foreground service and the pickers in the apps.

**Performance Goals**: every tick within 1 ms of nominal with no drift (spec SC-001; offline
within one sample); a tempo, grid or sound change audible on the next tick (SC-002); beat 1
within 100 ms of play (SC-007); custom-sound preparation under 2 s for a 30 s file (SC-004);
beat light within 30 ms of the audible click on a wired route (SC-006); the callback does no
allocation, locking or logging; the screen recomposes nothing per beat (the light is a draw-phase
read).

**Constraints**: nothing Kotlin on the audio callback (Constitution I); at most one stream open,
the metronome, listening and tones mutually exclusive in the engine state machine; every visual
value from tokens, no new colours (user's brief; research R10); one type per file; zero compiler
and lint warnings; Kotlin tests never link the engine; the click keeps running while the app is
in the background but never restarts by itself; the design details corrected on purpose are 44 dp
touch targets on the design's 28–32 dp chips and pills and no drop shadows (research R10).

**Scale/Scope**: about 20 new C++ files (engine core move, metronome scheduler and sounds, ABI,
tests), C ABI revision 3 with 9 functions and 3 structs, about 75 new Kotlin files across the
four shared modules and the apps (domain 13, engine 8, data 9, UI 40, platform 7), 10 vector
assets, 12 new test classes on the JVM/iOS lanes and 6 UI test classes, manifest and plist
entries, one catalog row, README rows.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | The output callback only loads two atomic snapshot indices, does bounded arithmetic on a double tick position, mixes at most two pre-rendered voices and writes a seqlock; commands are snapshots published from the control thread (three-slot buffers); no callback into Kotlin; the click set and the custom sound are prepared before publication on the calling thread; one engine core serves both platforms with Oboe and RemoteIO as thin shells; variable frame counts flow through unchanged (research R1, R2, R5, R7). | PASS |
| II. Shared Kotlin Control Plane, Compose UI | Domain in `:shared:core`, facade and actuals in `:shared:engine`, preferences and the store in `:shared:data`, screen, ViewModel and components in `:shared:ui` `commonMain`; dependencies point inward. One `MetronomeViewModel`, one immutable `MetronomeUiState` in a `StateFlow`, plain-function intents, the screen never touches the engine (only `BeatTelemetry.litBeat` in the draw phase, the telemetry rule). `expect`/`actual` stays on `NativeAudioEngine`; the foreground-service host and the file picker are interfaces bound per platform (research R6, R12). | PASS |
| III. Test-First | Every contract lists failing tests before code: Catch2 for timing, sounds, preparation and state; `commonTest` for domain, ViewModel, preferences, store, tokens and geometry; `uiTest` for the screen and components; device/simulator tests for the real engine, focus, session, service host, DataStore and files; fakes for the engine, host, picker, store and preferences; timing proven offline (research R15). | PASS |
| IV. Adaptive, Input-Agnostic UI | Layouts keyed on the window size class (stacked column with a pinned tap/play row; wide two-column; phone landscape settings column beside the dial), hover, focus ring, keyboard on chips, cells, pills, picker, steppers, slider and dial (arrows and page keys), wheel on the dial, `minimumInteractiveComponentSize()` for 44 dp, `Tab`/`Button`/`Slider` roles, `stateDescription` on cells and the play button, `testTag` on every control; tokens only; screenshots at compact, expanded and landscape on both platforms plus the state matrix (quickstart §8). | PASS |
| V. Document Model in Beats | No document; the metronome is global and its settings are preferences (constitution v1.2.0). The tick grid is expressed in ticks per beat, never in samples, and converted through the tempo at render time. | PASS (not exercised on a document) |
| VI. Simplicity | No new library (Okio already resolved; miniaudio and dr_wav vendored); sounds synthesised rather than bundled; one voice instead of a pool; no notification-permission flow; the debug switch is a launch argument like the existing two; custom components only where Material cannot match the design (Complexity Tracking #2); exclusions of spec FR-036 honoured. | PASS |
| VII. Code Organisation | One type per file; packages `audio.tembra.core.metronome`, `audio.tembra.engine`, `audio.tembra.data.metronome`, `audio.tembra.ui.metronome` (+ `.components`), `audio.tembra.ui.platform`, `audio.tembra.ui.designsystem`, `audio.tembra.ui.theme`; C++ under `engine/src/core`, `engine/src/metronome`, `engine/src/platform`; `internal` by default. | PASS |
| VIII. Skill-Guided Kotlin and Compose | Routed through `using-chrisbanes-skills` on 2026-09-29; the plan's choices cite `compose-component-design` (every component: root modifier, slots for the note glyph and the dial centre, primitives for levels and layouts; contracts/metronome-screen.md, design-system-additions.md), `compose-state-and-effects` (wiring/content/plain seams, `DisposableEffect(viewModel)`, `LaunchedEffect(running)`, no `Unit` keys hiding identity), `compose-performance` (frame-rate read in `drawBehind`, no back-writes), `kotlin-concurrency-and-flow` (named launch sites on `viewModelScope`, suspend lower layers, `Channel` for one-shot messages, cold event flows), `kotlin-api-design` (value class `Tempo`, sealed `ClickSound`, interfaces with platform bindings for the host and the picker). Every Gradle command runs through `gradle-run`. Compose UI tests follow `compose-ui-testing-patterns` (scripted telemetry, injected interaction sources, frozen clock). | PASS |
| Technology & Architecture Constraints | Kotlin Multiplatform + Compose, multiplatform ViewModel, Navigation 3 entry-scoped ViewModel, Metro, DataStore, Okio; engine C++ with a C ABI, Oboe and RemoteIO, dr_wav, miniaudio; no forbidden library; tokens from `theme.json` plus screen-derived tokens with evidence (research R10); Metronome stays where feature 002 placed it; the Metronome destination is global and its parts are built for the transport's reuse (constitution v1.2.0; research R2, R3, R11); product name unchanged. The transport bar is absent from every metronome screen (feature 002's decision). | PASS |
| Development Workflow & Quality Gates | Build order (v1.2.0): scaffold, navigation shell, tuner, **metronome**, audio spine … The click path obeys Principle I so the spine and the transport can grow from it (research R1: the engine core, the scheduler's anchor hook); no later feature is pulled forward (no transport lock, no tempo map). Definition of done maps to the four lanes plus screenshots (quickstart §2, §8); the plan names the skill contract per public composable and coroutine owner (contracts). | PASS |

**Post-design re-check (after Phase 1)**: unchanged. Three interpretations a reviewer could
read as deviations are recorded in Complexity Tracking with the simpler alternative and why it
was rejected. One spec correction was made during planning (headphones unplugged stop the
click, following the official guidance the user asked for; spec FR-027 and User Story 6 revised
and recorded in the spec's Clarifications).

## Project Structure

### Documentation (this feature)

```text
specs/004-metronome/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R16 with evidence (brief, report, NotebookLM, platform docs, Stitch)
├── data-model.md        # Phase 1: domain, grid, tap tempo, sounds, preferences, engine snapshots, playback policy, UiState, geometry and tokens, debug request
├── quickstart.md        # Phase 1: build/run/test, every user story, background and interruption checks, screenshots, input, troubleshooting
├── contracts/
│   ├── engine-c-abi-v3.md           # C ABI revision 3, engine core, scheduler, click sounds, threading, JNI and cinterop bindings, Catch2 tests
│   ├── metronome-audio-kotlin.md    # MetronomeAudio, AudioStatus, AudioEngine, NativeAudioEngine actuals (focus, session, becoming-noisy), FakeAudioEngine
│   ├── metronome-domain.md          # Tempo, markings, signature, subdivision, levels, patterns, styles, TickGrid, TapTempoSequence, ClickSound, tests
│   ├── metronome-preferences.md     # MetronomePreferences, shared DataStore instance, CustomClickSoundStore
│   ├── click-sounds.md              # built-in recipes and levels, custom import pipeline, SoundFilePicker, failure copy
│   ├── background-playback.md       # BackgroundPlaybackHost, MetronomeForegroundService, focus and session rules, manifest and plist
│   ├── metronome-screen.md          # MetronomeViewModel, policy, MetronomeScreen composition per layout, semantics, copy, tests
│   └── design-system-additions.md   # tokens, gradients, icons, TembraPill, TembraChipRow, TembraGradientCard discs, the reusable metronome components
├── checklists/requirements.md
├── screenshots/         # created during implementation (quickstart §8)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── gradle/libs.versions.toml                        + okio (com.squareup.okio:okio at the version DataStore 1.2.1 resolves), okio-fakefilesystem (tests)
├── THIRD_PARTY_LICENSES.md                          Okio row: direct dependency, pinned version
├── README.md                                        + rows: engine core, metronome scheduler and click sounds, background playback host, sound file picker, custom sound store
├── engine/
│   ├── include/tembra_engine.h                      ABI 3: TEMBRA_AUDIO_METRONOME, TEMBRA_ERR_SILENT, click level/sound enums, grid/info/tick structs, tembra_metronome_* functions
│   ├── CMakeLists.txt                               + src/core/*, src/metronome/*; miniaudio implementation unit (MA_NO_DEVICE_IO, MA_NO_DECODING, MA_NO_ENCODING)
│   ├── src/engine_info.cpp                          version 0.3.0
│   ├── src/core/engine_core.{h,cpp}                 tembra::EngineCore: device, state machine, tone + sample + metronome render sum (moved from src/tuner/tuner_engine.{h,cpp})
│   ├── src/core/miniaudio_impl.cpp                  the single MINIAUDIO_IMPLEMENTATION unit
│   ├── src/metronome/click_sample.h, click_set.h, triple_buffer.h, metronome_grid.h, metronome_tick.h
│   ├── src/metronome/click_synth.{h,cpp}            the five recipes × three levels (research R3)
│   ├── src/metronome/click_sample_builder.{h,cpp}   onset, trim, fades, normalise, level derivation (research R4)
│   ├── src/metronome/sound_resampler.{h,cpp}        miniaudio ma_resampler wrapper (offline)
│   ├── src/metronome/custom_sound_loader.{h,cpp}    dr_wav decode (first 30 s) → builder → ClickSet
│   ├── src/metronome/metronome_renderer.{h,cpp}     the RT scheduler and two-voice mixer (research R1, R5), seqlock telemetry (R7)
│   ├── src/metronome/metronome_abi.cpp              tembra_metronome_* → EngineCore
│   ├── src/tuner/tuner_abi.cpp                      → EngineCore (tuner_engine.{h,cpp} removed)
│   ├── src/platform/audio_device.h                  + virtual int64_t outputLatencyNanos() const (0 = unknown)
│   ├── src/platform/android/oboe_audio_device.{h,cpp}   reopen the output in onErrorAfterClose; outputLatencyNanos from calculateLatencyMillis on the control thread
│   ├── src/platform/apple/remote_io_audio_device.{h,cpp} outputLatencyNanos from the value reported through the ABI
│   ├── android/src/main/cpp/tembra_jni.cpp          + nativeMetronome* forwarders
│   └── tests/                                       + triple_buffer_test, click_synth_test, click_sample_builder_test, custom_sound_loader_test (generated WAV fixtures via wav_fixtures.h), metronome_renderer_test; tuner_engine_state_test → engine_core_state_test (+ the metronome state rules); engine_info_test updated
├── shared/core/src/commonMain/kotlin/audio/tembra/core/metronome/
│   │   Tempo.kt, TempoMarking.kt, TimeSignature.kt, Subdivision.kt, BeatLevel.kt, AccentPattern.kt, MetronomeStyle.kt,
│   │   SyncopationReading.kt, TickGrid.kt, TapTempoSequence.kt, ClickSound.kt, BuiltInClickSound.kt, MetronomeSettings.kt
│   └── commonTest/…/metronome/ TempoTest, TempoMarkingTest, TimeSignatureTest, SubdivisionTest, AccentPatternTest, MetronomeStyleTest, TickGridTest, TapTempoSequenceTest, ClickSoundTest
├── shared/engine/
│   ├── build.gradle.kts                              engine task inputs + src/core, src/metronome
│   ├── src/commonMain/kotlin/audio/tembra/engine/    AudioStatus.kt (audioState + events, lifted from TunerAudio), TunerAudio.kt (: AudioStatus), MetronomeAudio.kt (: AudioStatus),
│   │                                                 AudioEngine.kt (: TunerAudio, MetronomeAudio), AudioState.kt (+ Metronome), MetronomeGrid.kt, ClickLevel.kt, ClickSoundId.kt,
│   │                                                 CustomSoundInfo.kt, CustomSoundResult.kt, TickSnapshot.kt, FakeAudioEngine.kt (extended)
│   ├── src/androidMain/…/NativeAudioEngine.android.kt (+ metronome natives; Gain focus; becoming-noisy), AndroidAudioFocus.kt (+ FocusType), BecomingNoisyReceiver.kt
│   ├── src/iosMain/…/NativeAudioEngine.ios.kt (+ metronome functions; reports outputLatency), IosAudioSession.kt (+ outputLatencyNanos(), headphones check on route change)
│   ├── src/commonTest/…/FakeAudioEngineTest.kt (+ metronome); androidDeviceTest + iosTest: NativeAudioEngineTest (+ ABI 3, metronome round trip), MetronomeTimingEndToEndTest.kt;
│   │   androidDeviceTest/…/AndroidAudioFocusTest.kt (+ Gain), BecomingNoisyReceiverTest.kt; iosTest/…/IosAudioSessionTest.kt (+ interruption and route injection)
├── shared/data/
│   ├── build.gradle.kts                              + okio; commonTest + okio-fakefilesystem
│   ├── src/commonMain/kotlin/audio/tembra/data/      TembraDataStore.kt (preferences() documented single-instance; tunerPreferences() removed),
│   │                                                 metronome/MetronomePreferencesSnapshot.kt, MetronomePreferenceKeys.kt, MetronomePreferences.kt, DataStoreMetronomePreferences.kt, FakeMetronomePreferences.kt,
│   │                                                 metronome/CustomClickSoundStore.kt, OkioCustomClickSoundStore.kt, FakeCustomClickSoundStore.kt
│   └── tests: commonTest MetronomePreferencesSnapshotTest, FakeMetronomePreferencesTest, OkioCustomClickSoundStoreTest; androidDeviceTest + iosTest DataStoreMetronomePreferencesTest, CustomClickSoundStoreFileTest
├── shared/ui/
│   ├── src/commonMain/composeResources/drawable/ic_{remove,add,touch_app,music_note,graphic_eq,radio_button_checked,notifications,sensors,tune,check}_24.xml (script)
│   ├── src/commonMain/kotlin/audio/tembra/ui/
│   │   ├── App.kt (+ metronomeDebug), navigation/DestinationContent.kt (Metronome → MetronomeScreen())
│   │   ├── di/AppBindings.kt (+ single DataStore<Preferences>, MetronomePreferences, CustomClickSoundStore, MetronomeAudio), di/AppGraph.kt (+ metronomePreferences, backgroundPlaybackHost, soundFilePicker)
│   │   ├── theme/ TembraTypeScale.kt (+ tempoNumber, sectionLabel), TembraTypographyExtras.kt, TembraSpacing.kt (+ tokens), TembraGradients.kt (+ tempoArc, tempoDialCard, tempoDialOverlay, accentGlow, playGlow), LinearGlow.kt, TembraIcon.kt (+ 10)
│   │   ├── designsystem/ TembraPill.kt, TembraChipRow.kt, ChipSpec.kt, CardDiscs.kt, TembraGradientCard.kt (+ discs)
│   │   ├── platform/ BackgroundPlaybackHost.kt, SoundFilePicker.kt, SoundPickResult.kt, SoundPickFailure.kt
│   │   └── metronome/ MetronomeScreen.kt (replaces the placeholder), MetronomeScreenContent.kt, MetronomeViewModel.kt, MetronomeUiState.kt, MetronomeCallbacks.kt, MetronomeLayout.kt,
│   │              MetronomeMessage.kt, BeatTelemetry.kt, TapCaption.kt, MetronomeCopy.kt, MetronomeDebugRequest.kt, LocalMetronomeDebug.kt, MetronomeSettingsPanel.kt,
│   │              components/ TimeSignatureChips.kt, SubdivisionChips.kt, AccentCells.kt, AccentCell.kt, StyleRow.kt, ClickSoundPicker.kt, ClickSoundPickerLayout.kt, ClickSoundTile.kt, ClickSoundGlyphs.kt,
│   │                          TempoDial.kt, TempoDialGeometry.kt, TempoReadout.kt, TempoStepper.kt, TempoSlider.kt, TapTempoButton.kt, PlayPauseButton.kt, MetronomeDefaults.kt
│   ├── src/iosMain/kotlin/audio/tembra/ui/ IosAppGraph.kt (+ IosBackgroundPlaybackHost, IosSoundFilePicker), MainViewController.kt (+ metronomeDebug), platform/IosBackgroundPlaybackHost.kt, platform/IosSoundFilePicker.kt
│   ├── src/iosTest/kotlin/audio/tembra/ui/platform/IosBackgroundPlaybackHostTest.kt, IosSoundFilePickerTest.kt
│   ├── src/commonTest/…/ platform/FakeBackgroundPlaybackHost.kt, FakeSoundFilePicker.kt, metronome/{MetronomeViewModelTest,MetronomeUiStateTest,MetronomeLayoutTest,TempoDialGeometryTest,MetronomeDebugRequestTest}.kt, theme/MetronomeTokensTest.kt
│   ├── src/androidHostTest/…/theme/IconAssetsTest.kt (extended)
│   └── src/uiTest/…/ metronome/{MetronomeScreenTest,TempoDialTest,AccentCellsTest,ClickSoundPickerTest,ScriptedBeatTelemetry}.kt, designsystem/{TembraPillTest,TembraChipRowTest}.kt
├── androidApp/
│   ├── src/main/AndroidManifest.xml                  + FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK; <service .MetronomeForegroundService foregroundServiceType="mediaPlayback" exported="false">
│   ├── src/main/kotlin/audio/tembra/android/ AndroidAppGraph.kt (+ AndroidBackgroundPlaybackHost, SoundFilePickerHost, AndroidSoundFilePicker), MainActivity.kt (OpenDocument launcher attach/detach; "tembra.metronome.syncopation" extra in debug),
│   │                                          MetronomeForegroundService.kt, AndroidBackgroundPlaybackHost.kt, SoundFilePickerHost.kt, AndroidSoundFilePicker.kt
│   ├── src/main/res/drawable/ic_stat_metronome.xml, res/values/strings.xml (channel and notification text)
│   │       (ic_stat_metronome was the av_timer path; since 2026-10-17 the hand-drawn metronome glyph, written by tools/make-nav-glyphs.py)
│   └── src/androidTest/kotlin/audio/tembra/android/AndroidBackgroundPlaybackHostTest.kt
└── iosApp/iosApp/Info.plist                          + UIBackgroundModes [audio]; ContentView.swift reads --tembra-metronome-syncopation= in #if DEBUG
```

**Structure Decision**: no new Gradle module. The domain goes to `:shared:core` (pure, tested
on both lanes) so the transport can import it; the scheduler, the sounds and the C ABI to
`engine/` and `:shared:engine` (the only place that touches audio); preferences and the sound
store to `:shared:data`; everything visual to `:shared:ui`, the metronome in its own feature
package with its reusable components in `metronome/components` and the two generic pieces
promoted to `designsystem`. The foreground service, the Android file picker and its host sit in
`:androidApp` because they need the activity's result registry and a manifest entry; the iOS
host and picker sit in `iosMain` of `:shared:ui` because they need no shell code, as feature
003 did for the microphone permission.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| #1 `TunerEngine` is renamed and moved to `EngineCore` (`src/core`) although the tuner is finished and merged | The engine's device, state machine and render sum must be owned by something that is not the tuner once a second feature renders; the constitution's v1.2.0 amendment asks that the metronome's click path be the thing the audio spine and the transport grow from. The move is mechanical (one class, its test file renamed); the tuner's DSP classes and the ABI functions are untouched. | Adding the metronome renderer inside `TunerEngine` works but leaves a class named for one feature owning every stream; a second singleton with its own device would break the one-stream rule. |
| #2 Custom `TempoDial`, `TembraChipRow`, `TembraPill`, `AccentCells`, `ClickSoundPicker` tiles instead of Material's `Slider`-only, `FilterChip`, `AssistChip`, `Card` (Constitution VI "prefer the platform") | The design fixes a 275° gradient ring with steppers inside, 32 dp chips with filled selection, 28 dp pills, 64 dp level bars with glows and 44 dp tiles; Material's chips are 32 dp outlined with leading-icon slots that cannot take the fill and radius, and no Material component is a ring. The slider does use Material's `Slider` with restyled colours and sizes. Semantics, hover, focus and 44 dp targets use standard modifiers as in features 002 and 003. | Restyling Material's chips reaches neither the fills nor the heights; a Material progress indicator has no gradient and no drag. |
| #3 The scheduler's `anchor(transportFrame, beatPosition)` exists in the C++ class API although no feature calls it yet | Constitution v1.2.0 requires the click scheduler to be reusable by the transport; the anchor is the one entry point that feature needs, and having it in the class (about ten lines, tested) keeps the double-domain position design honest. It is not in the C ABI, so no unused public surface ships. | Leaving it for the transport risks a scheduler shape that cannot be re-anchored without a rewrite. |
