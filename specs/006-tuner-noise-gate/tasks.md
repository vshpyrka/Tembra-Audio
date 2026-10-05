---

description: "Task list for feature 006: tuner noise gate"
---

# Tasks: Tuner Noise Gate

**Input**: Design documents from `/specs/006-tuner-noise-gate/`

**Prerequisites**: plan.md, spec.md (FR-001–FR-027, SC-001–SC-013), research.md (R1–R15), data-model.md (§1–§10), contracts/ (`engine-c-abi-v4.md`, `tuner-audio-kotlin.md`, `tuner-preferences.md`, `tuner-screen.md`), quickstart.md.

**Tests**: Required. Constitution III: every implementation task is preceded by the test that fails without it. Lanes run from `app/` with `--warning-mode all`, always through the `gradle-run` wrapper (Constitution VIII): `engineHostTest` (Catch2), `checkAndroidHost` (JVM), `checkIos "-Ptembra.iosSimulator=<name or udid>"`, `ANDROID_SERIAL=<serial> checkAndroidDevice`. Compose UI tests live in `uiTest` and run only on the simulators and the Android targets.

**Skills** (Constitution VIII): route through `using-chrisbanes-skills`; `kotlin-api-design` for `NoiseGate` and the audio seam, `compose-component-design` for `NoiseGateSlider`, `TembraSliderThumb` and the three slots, `compose-performance` (deferred reads) for the live fill, `compose-state-and-effects` for the ViewModel intents and `TunerScreen`, `compose-ui-testing-patterns` for every `uiTest` task, `kotlin-control-flow` where a `when` over `TunerLayout` changes.

**Targets** (FR-024): Pixel 6 Pro `19021FDEE001AH`; Pixel 9 Pro Fold emulator `127.0.0.1:5556`; simulators `iPhone 17`, `iPad Pro 13-inch (M5)`, iPhone Duo `EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD`. The iPhone 14 is skipped by the owner's decision.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency on unfinished tasks)
- **[Story]**: US1–US5 from spec.md
- `engine/` = `app/engine/`
- `:core` = `app/shared/core/src/<sourceSet>/kotlin/audio/tembra/core/tuner/`
- `:engine` = `app/shared/engine/src/<sourceSet>/kotlin/audio/tembra/engine/`
- `:data` = `app/shared/data/src/<sourceSet>/kotlin/audio/tembra/data/tuner/`
- `:ui` = `app/shared/ui/src/<sourceSet>/kotlin/audio/tembra/ui/`
- NOTES = `specs/006-tuner-noise-gate/screenshots/NOTES.md`

---

## Phase 1: Setup

- [X] T001 Create NOTES with the verification matrix of data-model.md §10 (one row per target and orientation or posture; columns: lanes, end-to-end, structural check, captures, live sound, not driven and why). Confirm the five targets are attached (`adb devices -l`, `xcrun simctl list devices booted`) and write their ids and OS versions into it
- [X] T002 Baseline before any change, recorded in NOTES: `engineHostTest`, `checkAndroidHost`, `checkIos "-Ptembra.iosSimulator=iPhone 17"`, and `ANDROID_SERIAL=127.0.0.1:5556 checkAndroidDevice`. Record the fold emulator's `MetronomeTimingEndToEndTest` result on this commit: it is the "fails in the same way without this feature" evidence of FR-024

---

## Phase 2: Foundational (the level, the ABI, the seam, the type, the shared handle)

**Purpose**: what every story needs. After this phase the engine publishes the input level and accepts a gate value, Kotlin can set it and read it, and the domain type and the slider handle exist. The gate does not act yet.

- [X] T003 [P] Test helpers only, in `engine/tests/signal_fixtures.h`: `scaledToRmsDb(signal, db)` (scale to an RMS level in dB below full scale), `fadedDbPerSecond(signal, sampleRate, dbPerSecond)`, `mixed(a, b)` (sample-wise sum), `wobbledDb(signal, sampleRate, depthDb, hz)` (slow ± level wobble)
- [X] T004 Failing Catch2 cases (contracts/engine-c-abi-v4.md): in `engine/tests/engine_info_test.cpp` ABI revision 4 and version "0.4.0"; in `engine/tests/tuner_analyzer_test.cpp` an in-band sine at −40 dB RMS publishes `input_level_db` −40 ± 0.5, tones at 12.5 Hz and 5.6 kHz publish at least 20 dB under their true level and never a pitch (FR-004), `gate_open` is 1 with no gate, the resting reading has `input_level_db == -120` and `gate_open == 1`; in `engine/tests/engine_core_state_test.cpp` `tembra_tuner_set_gate` returns `TEMBRA_OK` in every audio state and `TEMBRA_ERR_ARGUMENT` for NaN, +1 and −121 with "the previous value stays"
- [X] T005 Header revision 4 in `engine/include/tembra_engine.h` exactly as contracts/engine-c-abi-v4.md: `TEMBRA_ENGINE_ABI_VERSION 4u`, `int32_t tembra_tuner_set_gate(uint32_t enabled, float open_db)`, `float input_level_db` after `input_peak` and `uint8_t gate_open` after `synthetic` in `tembra_pitch_reading`, the header comment naming this contract; version "0.4.0" in `engine/src/engine_info.cpp`
- [X] T006 Window level in `engine/src/tuner/tuner_analyzer.{h,cpp}`: after `filter_.process` add the hop's energy to a running sum over the last `window_frames / hop_frames` hops and publish `input_level_db = max(-120, 10 · log10(sum / frames))`; "before the first window is full the sum covers the hops received so far"; `publish` and `publishResting` fill both new fields (`gate_open = 1` for now, resting level −120)
- [X] T007 Gate value plumbing: `EngineCore::setGate(bool enabled, float openDb)` in `engine/src/core/engine_core.{h,cpp}` stores the value under the command mutex ("initially: no gate"; kept across `stopListening`) and forwards it to a live analyzer; `TunerAnalyzer::setGate` holds it in one `std::atomic<float>` (a sentinel below −120 means off) and takes the stored value at construction; the forwarder in `engine/src/tuner/tuner_abi.cpp` rejects a non-finite `open_db` or one outside −120..0 when `enabled != 0` (T004 green; `engineHostTest`)
- [X] T008 [P] Failing tests in `:engine` `commonTest/`: `FakeAudioEngineTest.kt` (`setNoiseGate` is recorded in `calls` as `setNoiseGate(off)` / `setNoiseGate(-50.0)`, `noiseGateDb` exposes the last value, `readPitch` copies `inputLevelDb` and `gateOpen`); `TestSignalLevelTest.kt` (new: crest of the default mix 2.18 ± 0.01, `amplitudeFor(-30f)` 0.0690 ± 0.0005, 20 dB less level is a tenth of the amplitude); `TestSignalsTest.kt` (eighteen unique ids in order; `e2-70db`, `e2-55db`, `e2-45db`, `e2-30db` are in-tune E2 with amplitudes 0.00069, 0.00388, 0.01227, 0.06899)
- [X] T009 [P] Failing cases in `:engine` `androidDeviceTest/NativeAudioEngineTest.kt` and `iosTest/NativeAudioEngineTest.kt`: ABI revision 4; `setNoiseGate` returns `AudioResult.Ok` for null, −50 and 0 and an `AudioResult.Error` for 1 and −121; an idle engine reads `gateOpen == true` and `inputLevelDb == -120f`
- [X] T010 JNI in `engine/android/src/main/cpp/tembra_jni.cpp`: `Java_audio_tembra_engine_NativeAudioEngine_nativeTunerSetGate(JNIEnv*, jobject, jboolean enabled, jfloat openDb)`; `nativeTunerRead` fills nine floats `[state, sampleRate, frequencyHz, clarity, inputPeak, hasPitch, synthetic, inputLevelDb, gateOpen]` (with T011 it turns T009 green on Android)
- [X] T011 Kotlin seam (contracts/tuner-audio-kotlin.md): `fun setNoiseGate(openDb: Float?): AudioResult` on `TunerAudio` in `:engine` `commonMain/TunerAudio.kt`; `inputLevelDb` (default −120f) and `gateOpen` (default true) with `copyFrom` in `commonMain/PitchSnapshot.kt`; `FakeAudioEngine.kt`; `androidMain/NativeAudioEngine.android.kt` (`nativeTunerSetGate(openDb != null, openDb ?: 0f)`, `READING_FIELDS = 9`); `iosMain/NativeAudioEngine.ios.kt` (`tembra_tuner_set_gate`, the two fields; no `AVAudioSession` call) (T008's fake cases green; T009 green on iOS)
- [X] T012 [P] `TestSignalLevel.amplitudeFor(levelDb, harmonics = 5, fundamentalGain = 0.25f)` in `:engine` `commonMain/TestSignalLevel.kt` (`10^(levelDb / 20) × crest`, crest probed over 4096 points for the partials `[fundamentalGain, 1, 0.5, 0.3, 0.2, …]` as `SyntheticAudioDevice` builds them) and the four catalogue entries after the existing fourteen in `commonMain/TestSignals.kt` with `ExpectedReading.Note(E2, 0.0)` (T008's level and catalogue cases green)
- [X] T013 [P] Failing `NoiseGateTest.kt` in `:core` `commonTest/`: `fromStored` examples "`null` → Off; −90 → Off; −80 → Off; −79 → Off; −78 → −78; −51 → −52; −20 → −20; −19 → Off; 0 → Off"; `ofLevelDb` is `null` outside −78..−20; `ofStep` coerces into 0..30; `levelDb`, `fraction`, `percent` at steps 0, 1, 15, 30; `fractionOfLevel` at −100, −80, −50, −20, 0 gives 0, 0, 0.5, 1, 1; `CheckLevels` are steps 10, 15, 20
- [X] T014 [P] `NoiseGate` in `:core` `commonMain/NoiseGate.kt` per data-model.md §1: `@JvmInline value class` with a private constructor over `step`; companion `MIN_DB = -80`, `MAX_DB = -20`, `STEP_DB = 2`, `STEPS = 30`, `Off`, `ofStep`, `ofLevelDb` ("`null` outside `MIN_DB + STEP_DB .. MAX_DB`; a value between two steps goes to the quieter one"), `fromStored`, `fractionOfLevel` (primitives only), `CheckLevels` (T013 green)
- [X] T015 [P] Shared slider handle (contracts/tuner-screen.md §1), a refactor under the metronome's existing tests: `:ui` `commonMain/designsystem/TembraSliderDefaults.kt` (`TrackHeight = 6.dp`, `ThumbSize = 16.dp`, `THUMB_HALO_ALPHA = 0.16f`, `THUMB_HALO_SCALE = 2f`, moved from `MetronomeDefaults`) and `commonMain/designsystem/TembraSliderThumb.kt` (the thumb `TempoSlider` draws, extracted: the disc is always `ThumbSize` across and centred in its box; the box is `ThumbSize × touchTargetMin` unless the caller's modifier gives a larger one); `commonMain/metronome/components/TempoSlider.kt` and `MetronomeDefaults.kt` use them; `TempoDialTest`, `MetronomeControlsTest` and `MetronomeScreenTest` pass unmodified
- [X] T016 Lanes green with zero warnings: `engineHostTest`, `checkAndroidHost`, `checkIos` on the iPhone 17, `checkAndroidDevice` on the Pixel 6 Pro (T009 green on both platforms)

**Checkpoint**: the level is published and a gate value can be set and read back; every existing reading is unchanged.

---

## Phase 3: User Story 1 — Keep a quiet background off the meter (P1) 🎯 MVP

**Goal**: a gate level set on a slider under the gauge holds back sound quieter than it; louder sound is read as before; moving the handle never interrupts listening.

**Independent Test**: `NoiseGateEndToEndTest` (sixteen combinations and the live change); on a device, launch with `e2-55db`, set the handle past the fill and the gauge rests; bring it back and E2 returns.

### Tests for User Story 1 (write first, see them fail)

- [X] T017 [P] [US1] `engine/tests/level_gate_test.cpp` (new; add to `engine/tests/CMakeLists.txt`): no gate is always open; a closed gate opens at `level >= gate` and stays closed under it; a changed value is judged afresh (open with the level under the new gate closes on that hop; closed with the level at or above it opens); an open gate with the level 6 dB or more under an unchanged gate closes
- [X] T018 [P] [US1] Gate cases in `engine/tests/tuner_analyzer_test.cpp` at each check level (−60, −50, −40 dB): a harmonic tone 6 dB under the gate gives no pitch for 10 s at every open-string pitch of feature 003's presets (A1, B1, C2, D2, D#2, E2, G2, G#2, A2, C3, C#3, D3, F3, F#3, G3, A3, A#3, B3, D4, D#4, E4); at the gate level and 6 dB above it the pitch is published within 250 ms and within 1 cent (SC-001); a quiet tone 6 dB under the gate with a tone 12 dB louder over it reads the loud note, and when the loud one stops the reading rests within 500 ms and never names the quiet tone (User Story 1.4); white noise above the gate has `gate_open == 1` and no pitch (FR-009); `setGate` between hops takes effect on the next hop, afresh (FR-003); with no gate a recorded sequence of readings for the standard tone is identical to the one recorded before T024 (SC-002). In `engine/tests/engine_core_state_test.cpp`: a gate set while idle holds back the first hop of the next test-signal start
- [X] T019 [P] [US1] `NoiseGateEndToEndTest.kt` (new) in `:engine` `androidDeviceTest/` and `iosTest/` per contracts/tuner-audio-kotlin.md: for each gate in `[null, -60f, -50f, -40f]` and each of the four level signals, set the gate, start the test signal, wait for twenty hops and assert the outcome table of data-model.md §7 (E2 within 1 cent or no pitch), `gateOpen` equal to "reads" and `inputLevelDb` within 1 dB of the signal's level; then the live change: `e2-55db` with no gate reads, `setNoiseGate(-50f)` rests within 500 ms, `setNoiseGate(null)` reads within 250 ms, with `audioState` Listening throughout and `sequence` rising without ever going back (a restarted stream would start again from 0: FR-003, SC-005); and the rate: `sequence` advances by at least 20 in one second (SC-006)
- [X] T020 [P] [US1] Failing `:ui` `commonTest/tuner/` cases: `TunerUiStateTest.kt` (`noiseGate` defaults to `NoiseGate.Off`; `showNoiseGate` is true only for Auto Listen without the denied state); `TunerViewModelTest.kt` (`setNoiseGate` puts the value in the state and calls the audio seam at once, visible in `FakeAudioEngine.calls`; it writes nothing to `FakeTunerPreferences`; an unchanged value does nothing; the value survives By Ear and back, `screenVisible(false/true)`, `foreground(false/true)` and an interruption, and no later call resets the engine's gate)
- [X] T021 [P] [US1] Failing `:ui` `uiTest/tuner/NoiseGateSliderTest.kt` (new), basic cases: the label "NOISE GATE", "Off" and "High" are shown; tags `tuner.noiseGate` (root) and `tuner.noiseGate.slider`; a click at a third of the track reports step 10; a horizontal swipe reports rising steps; the handle stops at 0 and 30; `onGate` is not called for an unchanged step; no text of the control contains "dB" (FR-013, FR-026)
- [X] T022 [P] [US1] Failing placement cases: in `:ui` `uiTest/tuner/TunerScreenTest.kt` Auto Listen in `Stacked` and in `Wide` shows `tuner.noiseGate` inside `tuner.autoListen` and below `tuner.meter`, and moving it raises `onNoiseGate`; in `uiTest/tuner/TunerScreenByEarTest.kt` and `uiTest/tuner/TunerScreenDeniedTest.kt` no node is tagged `tuner.noiseGate`

### Implementation for User Story 1

- [X] T023 [US1] `LevelGate` in `engine/src/tuner/level_gate.{h,cpp}` (add the `.cpp` to `engine/CMakeLists.txt`): `bool update(float gate, float levelDb)` with the off, opening and "value changed" rows of research R3; an open gate closes when the level is under the gate (the closing margin arrives with User Story 2) (T017 green)
- [X] T024 [US1] Apply the gate in `engine/src/tuner/tuner_analyzer.cpp`: load the atomic once per hop, `LevelGate::update(gate, level)`; when closed do not run the detector, push `PitchEstimate{0, 0}` into the smoother and publish `clarity` 0; publish `gate_open` (T018 green; `engineHostTest`)
- [X] T025 [US1] State and copy in `:ui` `commonMain/tuner/`: `noiseGate: NoiseGate = NoiseGate.Off` and derived `showNoiseGate` in `TunerUiState.kt`; `onNoiseGate: (NoiseGate) -> Unit` and `onNoiseGateSettled: () -> Unit` in `TunerCallbacks.kt` (and in `None`); `noiseGate = "Noise gate"` and `gateHigh = "High"` in `TunerCopy.kt`
- [X] T026 [US1] `TunerViewModel.setNoiseGate(gate)` in `:ui` `commonMain/tuner/TunerViewModel.kt` (no-op if unchanged → state → `audio.setNoiseGate(gate.levelDb?.toFloat())`; nothing written) and `onNoiseGate = viewModel::setNoiseGate` in `commonMain/tuner/TunerScreen.kt` (T020 green)
- [X] T027 [US1] `NoiseGateSlider` in `:ui` `commonMain/tuner/NoiseGateSlider.kt` with the signature and structure of contracts/tuner-screen.md §2: root `Column(modifier)`, label, `Row` of "Off", Material `Slider` (`value = gate.step`, `valueRange = 0f..30f`, `steps = 29`, `TembraSliderThumb` in a `touchTargetMin × touchTargetMin` box tagged `tuner.noiseGate.handle` (the owner's decision after analysis: the handle's own touch box is 44 dp in both directions), a plain `TrackHeight` track in `surfaceContainerHighest`), "High"; `onGate(NoiseGate.ofStep(round(value)))` only when it differs; `onValueChangeFinished → onGateSettled()`; tokens only (T021 green)
- [X] T028 [US1] Placement in the card forms: `footer: (@Composable () -> Unit)? = null` on `AutoListenPane` in `:ui` `commonMain/tuner/AutoListenPane.kt` (for `Stacked` and `Wide` a column with the gauge's area above and the footer at the card's foot; `Wide` centres it at `widthIn(max = meterMaxWidthWide)`; no footer with the denied panel; `Landscape` and `Tabletop` unchanged); `TunerScreenContent.kt` builds the control once when `state.showNoiseGate` and passes it as the footer for `Stacked` and `Wide` (T022 green)
- [X] T029 [US1] Lanes: `engineHostTest`, `checkAndroidHost`, `checkIos` on the iPhone 17, `checkAndroidDevice` on the Pixel 6 Pro (T019 green on both). Install the debug build on the Pixel 6 Pro and the iPhone 17 simulator, launch with the test signal `e2-55db`, move the handle past the fill and back, and record in NOTES that the gauge rests and returns while the "Test signal" pill stays

**Checkpoint**: User Story 1 works end to end on a phone held upright and on a tablet.

---

## Phase 4: User Story 2 — A ringing note stays on the meter (P1)

**Goal**: an open gate closes only 3 dB under the gate level, so a fading note is read until it is clearly below the gate and a note hovering at the gate does not flicker.

**Independent Test**: the fade and waver cases of `tuner_analyzer_test.cpp`.

- [X] T030 [P] [US2] Failing rows in `engine/tests/level_gate_test.cpp`: an open gate stays open while `level >= gate − 3` and closes under it; `kCloseMarginDb == 3.0f`; a value change still ignores the margin
- [X] T031 [P] [US2] Failing cases in `engine/tests/tuner_analyzer_test.cpp`: a tone that starts 15 dB above the gate and fades at 10 dB per second is read without a gap until it is 3 ± 2 dB under the gate, and the reading rests within 500 ms after that (SC-003); a tone wavering ±1 dB around the gate level for 30 s changes between a reading and rest at most once (SC-004, FR-008); a steady tone 10 dB above the gate gives the same readings as with no gate for 10 s (User Story 2.3); after the fade has rested, a new tone above the gate is read within 250 ms (User Story 2.4)
- [X] T032 [US2] The closing margin in `engine/src/tuner/level_gate.{h,cpp}`: `kCloseMarginDb = 3.0f`, open stays open while `level >= gate − kCloseMarginDb` (T030, T031 and every US1 case green; `engineHostTest`)
- [X] T033 [US2] `NoiseGateEndToEndTest` on the iPhone 17 simulator and the Pixel 6 Pro still passes its sixteen combinations (every signal is at least 5 dB from every gate it meets)

**Checkpoint**: both P1 stories hold in the engine and through the bridges.

---

## Phase 5: User Story 3 — Set the gate by eye (P2)

**Goal**: the slider's track fills to the live input level on the handle's own scale, active while sound is read and muted while it is held back.

**Independent Test**: `NoiseGateSliderTest` fill cases; a capture with `e2-45db` and the gate at −50 dB shows the fill past the handle.

- [X] T034 [P] [US3] Failing `:ui` `commonTest/tuner/LevelSmootherTest.kt` (new): the first value is returned as given; within 2 dB of a 30 dB step after 100 ms of frames; a pause longer than 100 ms counts as one long frame and does not jump
- [X] T035 [P] [US3] Failing fill cases in `:ui` `uiTest/tuner/NoiseGateSliderTest.kt` with a scripted `PitchTelemetry` and the frame clock stepped: level −50 dB with the gate at −50 dB and `gateOpen` true ends under the handle's centre (± 2 dp) in `primary`; level −60 dB with `gateOpen` false ends short of the handle in `outline`; gate off draws `primary`; `listening = false` draws no fill and the handle still moves; with the motion duration scale at zero (reduce motion) the fill still follows the level (FR-011, FR-012, FR-020, SC-006)
- [X] T036 [US3] `LevelSmoother` in `:ui` `commonMain/tuner/LevelSmoother.kt` (a 30 ms approach in decibels, like `NeedleSmoother`) (T034 green)
- [X] T037 [US3] The live fill in `:ui` `commonMain/tuner/NoiseGateSlider.kt`: the track's `drawBehind` reads `rememberFrameTick(listening)`, then `telemetry.read(snapshot)`, `LevelSmoother.advance(snapshot.inputLevelDb, tick)` and draws from 0 to `NoiseGate.fractionOfLevel(level) × width`, `primary` when `snapshot.gateOpen` else `outline`, nothing when not listening; no State is read in composition at frame rate (T035 green)
- [X] T038 [US3] `checkIos` on the iPhone 17 and `checkAndroidDevice` on the Pixel 6 Pro; on both, launch with `e2-45db`, set the handle near the middle and record in NOTES that the fill ends past the handle and turns muted when the handle passes it

**Checkpoint**: the control shows why the gauge rests.

---

## Phase 6: User Story 4 — Off by default and remembered (P2)

**Goal**: the gate level is a sixth tuner preference, off on a fresh install, in force from the first moment of listening after a relaunch.

**Independent Test**: `DataStoreTunerPreferencesTest` on both platforms; quickstart §7 on a device.

- [ ] T039 [P] [US4] Failing `:data` `commonTest/` cases: `TunerPreferencesSnapshotTest.kt` (default `NoiseGate.Off`; `decode(values, loopTone, noiseGateDb)` with null, −50, −51, −19 and −80; `encode` omits `tuner.noiseGateDb` when off and carries "-50" otherwise; the five existing fields round-trip as before) and `FakeTunerPreferencesTest.kt` (an update of `noiseGate` is emitted)
- [ ] T040 [P] [US4] Failing `DataStoreTunerPreferencesTest.kt` cases in `:data` `androidDeviceTest/` and `iosTest/`: write −50, read it back, reopen the file and read again; write off and "the key is removed"; a file with only the five old keys reads off; a stored 7 reads off
- [ ] T041 [P] [US4] Failing `:ui` `commonTest/tuner/TunerViewModelTest.kt` cases: on load the stored gate is sent to the audio seam before `startListening` (order in `FakeAudioEngine.calls`), also when it is off; `noiseGateSettled()` writes the current value to `FakeTunerPreferences` once; a fresh snapshot loads `NoiseGate.Off`
- [ ] T042 [US4] `NOISE_GATE_DB = "tuner.noiseGateDb"` in `:data` `commonMain/TunerPreferenceKeys.kt`; `noiseGate: NoiseGate = NoiseGate.Off`, `encode` and `decode(values, loopTone, noiseGateDb: Int?)` in `commonMain/TunerPreferencesSnapshot.kt`; `commonMain/DataStoreTunerPreferences.kt` stores `intPreferencesKey("tuner.noiseGateDb")` as `noiseGate.levelDb` when a level is set and removes the key when the gate is off, and reads `NoiseGate.fromStored` (T039, T040 green)
- [ ] T043 [US4] `:ui` `commonMain/tuner/TunerViewModel.kt`: `start()` takes `stored.noiseGate` into the state and calls `audio.setNoiseGate` before the first `applyAudio()`; `noiseGateSettled()` persists through the existing `persist` launch site; `onNoiseGateSettled = viewModel::noiseGateSettled` in `commonMain/tuner/TunerScreen.kt` (T041 green)
- [ ] T044 [US4] Settle cases in `:ui` `uiTest/tuner/NoiseGateSliderTest.kt`: one horizontal drag raises `onGateSettled` exactly once; a click on the track raises it once. If Material does not report the click as finished, `NoiseGateSlider` calls `onGateSettled()` after it (contracts/tuner-screen.md §2)
- [ ] T045 [US4] Lanes: `checkAndroidHost`, `checkIos` on the iPhone 17, `checkAndroidDevice` on the Pixel 6 Pro. Quickstart §7 on both: set the handle, stop the app, start it with no arguments; the handle is where it was; record in NOTES

**Checkpoint**: the level is remembered and gates the first note after a relaunch.

---

## Phase 7: User Story 5 — Fits every window, pointer, keyboard and screen reader (P3)

**Goal**: the control is in every form of the screen, wholly visible and clear of everything in every window size, orientation and posture; hover, focus, keyboard and screen reader work.

**Independent Test**: `TunerNoiseGateLayoutTest` on every simulator and Android target.

### Tests for User Story 5 (write first, see them fail)

- [ ] T046 [P] [US5] `:ui` `uiTest/tuner/TunerNoiseGateLayoutTest.kt` (new) per contracts/tuner-screen.md §7. **On screen in every form**: for window sizes 320×568, 360×740, 466×678, 800×360, 1024×1366 and 1366×1024 dp at font scales 1.0 and 2.0, with the `TunerLayout` each gives: `tuner.noiseGate` is displayed (after scrolling to it where the content scrolls); its unclipped bounds equal its bounds in the root and lie inside the root; it intersects neither `tuner.meter`, the selectors nor the pill; it is below the gauge in `Stacked` and `Wide` and inside `tuner.settings` in `Landscape`; the gauge's bounds are whole; at 800×360 and font scale 1.0 the settings column shows it without scrolling; at 360×548 and 386×540 (the content area a 360×740 phone and the iPhone Duo's outer display leave under the shell's bars) at font scale 1.0 the gauge is whole without scrolling, at least `meterMinWidth` wide, with the control under it. **Tabletop and book** with a scripted fold: the control is in the lower / start region and clear of the fold. **Orientation and posture change**: one composition whose layout goes upright → sideways → upright and flat → tabletop → book → flat with the state unchanged keeps the control present with the same step and announced value. **This device's window**: the same assertions with no forced size
- [ ] T047 [P] [US5] Failing additions in `:ui` `uiTest/tuner/`: `TunerScreenLayoutTest.kt` (keyboard focus reaches the control after the gauge in `Stacked` and `Wide` and after the mode selector in `Landscape`; existing assertions unchanged); `TunerTabletopLayoutTest.kt` (the control is in `tuner.tabletop.lower` under the mode control; the upper region holds only the gauge); `TunerHingeLayoutTest.kt` (the control is in the controls card before the hinge)
- [ ] T048 [P] [US5] Failing input and accessibility cases in `:ui` `uiTest/tuner/NoiseGateSliderTest.kt`: the slider node is at least 44 dp tall and the handle's box `tuner.noiseGate.handle` is at least 44 dp in both dimensions with the 16 dp disc centred in it; Right and Left move one step and stop at the ends; a key press raises `onGateSettled`; hover, press and focus through an injected `MutableInteractionSource` show the halo and the focus ring (clock frozen); semantics: `contentDescription` "Noise gate", state "Off" at step 0 and "50 percent" at step 15, followed by ", sound above the gate" / ", sound below the gate" for `soundAboveGate` true / false and nothing for null; `setProgress` moves the handle; inside a vertically scrolling parent a vertical swipe that starts on the control scrolls and leaves the step unchanged (FR-018, FR-019)
- [ ] T049 [P] [US5] Failing `:ui` `commonTest/tuner/` cases: `TunerUiStateTest.kt` (`soundAboveGate` is null when idle, when the gate is off and in By Ear, and equals `gateOpen` otherwise); `TunerViewModelTest.kt` (the 10 Hz poll mirrors `snapshot.gateOpen` into the state)

### Implementation for User Story 5

- [ ] T050 [US5] `gateOpen: Boolean = true` and derived `soundAboveGate: Boolean?` in `:ui` `commonMain/tuner/TunerUiState.kt`; the poll in `TunerViewModel.kt` sets `gateOpen`; `gatePercent(n)`, `soundAboveGate` and `soundBelowGate` in `TunerCopy.kt` (T049 green)
- [ ] T051 [US5] Semantics and input in `:ui` `commonMain/tuner/NoiseGateSlider.kt`: `contentDescription`, the composed `stateDescription`, no live region; settle after a key press if Material does not report it; whatever FR-018's scroll case needs (T048 green)
- [ ] T052 [US5] `autoListenControls: (@Composable () -> Unit)? = null` after `ModeControl` in `:ui` `commonMain/tuner/TunerSettingsColumn.kt` and `commonMain/tuner/TunerTabletopSettings.kt`; `TunerScreenContent.kt` passes the control there for `Landscape`, `Tabletop` and `Wide` with a vertical fold, and as the footer otherwise (T047 green)
- [ ] T053 [US5] The fitted gauge in `:ui` `commonMain/tuner/AutoListenPane.kt` (research R11): with a footer, measure the footer first; if the regular gauge fits the height left at its full cap (`meterMaxWidth` / `meterMaxWidthWide`) use it; otherwise use `MeterSize.Compact`, sized from the height left with `MeterLayout.widthForHeight(…, MeterSize.Compact)` and capped at the same width; only when even the compact gauge would be under `meterMinWidth` does the card's content scroll as a whole with the footer last (T046 green)
- [ ] T054 [US5] First full pass of the lanes on all five targets: `checkAndroidHost`; `checkIos` on the iPhone 17, the iPad Pro and the iPhone Duo; `checkAndroidDevice` on the Pixel 6 Pro and the fold emulator. Record each in NOTES

**Checkpoint**: every story is built; the layout tests prove the control on screen in every form.

---

## Phase 8: Verification on the five targets, and polish

**Purpose**: the debug arguments and the capture tool, then FR-023 to FR-025 and the owner's planning request on real screens.

- [ ] T055 [P] Failing `:ui` `commonTest/tuner/` cases: `TunerNoiseGateRequestTest.kt` (new: "off" → `NoiseGate.Off`; "-50" → −50 dB; "-51" → −52 dB; "", "loud", "-19" and "5" → null); `TunerTestSignalRequestTest.kt` (the four new ids; `E2:0@-55` is E2 with `TestSignalLevel.amplitudeFor(-55f)`; `E2:0@1` and `E2:0@x` are null); `TunerViewModelTest.kt` (`useNoiseGate(gate)` before `screenVisible(true)` replaces the stored value, is sent to the audio seam before `startListening` and is not written; a later `setNoiseGate` + `noiseGateSettled` is written)
- [ ] T056 Debug launch arguments (contracts/tuner-screen.md §6, data-model.md §8): `:ui` `commonMain/tuner/TunerNoiseGateRequest.kt` and `LocalTunerNoiseGate.kt` (new); `@LEVEL` in `commonMain/tuner/TunerTestSignalRequest.kt`; `tunerNoiseGate: NoiseGate? = null` in `commonMain/App.kt`; a non-null value opens the Tuner in `commonMain/navigation/NavigationShell.kt`; `TunerViewModel.useNoiseGate` and its hand-over beside `useTestSignal` in `commonMain/tuner/TunerScreen.kt`; `iosMain/MainViewController.kt` (`tunerNoiseGate: String?`); `app/iosApp/iosApp/ContentView.swift` (`--tembra-tuner-noise-gate=`, Debug only); `app/androidApp/src/main/kotlin/audio/tembra/android/MainActivity.kt` (extra `tembra.tuner.noiseGate`, debug only) (T055 green; `checkAndroidHost`, `checkIos` on the iPhone 17)
- [ ] T057 `app/tools/capture-noise-gate.sh` per contracts/tuner-screen.md §8: `--android <serial> [--display <id>]` or `--ios "<simulator>"`, `--orientation upright|sideways`; three launches (`e2-in-tune` gate off; `e2-55db` gate −50; `e2-45db` gate −50) stored as `<target>-<orientation>-off|below|above.png` under `specs/006-tuner-noise-gate/screenshots/`; on Android it sets `user_rotation` and fails unless the `uiautomator dump` node described "Noise gate" lies inside the display and intersects neither "Tuner meter" nor a selector (the nodes whose text is "Guitar", "Cello", "Auto Listen" or "By Ear"); on iOS it turns sideways captures upright with `sips`
- [ ] T058 Quickstart §1 on all five targets with zero warnings; compare the fold emulator's `MetronomeTimingEndToEndTest` with the baseline of T002; fill the lanes and end-to-end columns of NOTES (SC-009)
- [ ] T059 Pixel 6 Pro: install; `capture-noise-gate.sh` upright and sideways with the structural check passing; turn it upright → sideways → upright while listening behind the gate (`e2-45db`, gate −50) and confirm the note stays and the handle keeps its place; NOTES
- [ ] T060 Pixel 9 Pro Fold emulator: install; captures and structural checks on the inner display opened flat upright and sideways, in tabletop and in book posture; ten changes flat → tabletop → book → flat while listening behind the gate (SC-012); NOTES
- [ ] T061 iPhone 17 and iPad Pro simulators: build and install; captures upright and sideways (rotate through the Simulator's Device ▸ Orientation menu); rotate while listening behind the gate; NOTES
- [ ] T062 iPhone Duo simulator: build with Xcode 27.1 beta and install; captures on the closed outer display upright and on the open display upright and sideways (Device Hub's pose and rotation buttons); NOTES, naming any pose that could not be driven and why
- [ ] T063 Review every capture of T059–T062 against FR-016, FR-020 and SC-008: the control wholly on screen, not cut by an edge, a bar or the fold, clear of the gauge, the selectors and the pill, and matching the tempo slider's handle and track. Each defect gets a failing case in `TunerNoiseGateLayoutTest.kt` first, then the fix, then a new capture
- [ ] T064 Once per platform (quickstart §4): the largest font size (the column or card scrolls, the labels are whole), and a pointer and a keyboard on the iPad simulator and the fold emulator opened flat (hover halo, focus ring, arrows move one step); NOTES
- [ ] T065 Live sound (quickstart §5, FR-025, SC-010) on the Pixel 6 Pro and each simulator: a quiet pitched background from a loudspeaker is named with the gate off and rests the gauge for at least 95% of thirty seconds behind a gate above it, while a louder tone is still read within a quarter of a second. While listening through the real microphone, move the handle several times and confirm the platform's microphone indicator stays on and "Mic active" never disappears (FR-003, SC-005). Note whether the gauge ever flickers on a steady sound near the gate, or names the background after a note because the background held the gate open: that is the evidence for the 3 dB margin. Attempt it on the fold emulator; if its microphone gets nothing, say so in NOTES. Restore the workstation's output volume afterwards
- [ ] T066 The scale checkpoint (quickstart §6, research R8) on the Pixel 6 Pro and through the iPhone 17 simulator. Launch with the gate off and the real microphone, and read each sound's level from where the fill ends on the track in three captures a second apart (the track is the scale: −80 dB at "Off", −20 dB at "High"); confirm it by relaunching with `tembra.tuner.noiseGate` one step under and one step over that level (the fill is active, then muted). Three sounds, written into NOTES: the quietest room (Q), a pitched background across the room (B), and a stand-in for an instrument (I): a 196 Hz harmonic tone from the workstation's loudspeaker at half output volume, 30 cm from the target. Keep the scale if on both targets Q ≤ −60 dB and −50 dB ≤ I ≤ −20 dB. If only I fails, the stand-in is not proof: ask the owner for one pluck of a real guitar at arm's length and use that number. If Q fails, or the real instrument fails, stop and report the numbers with proposed ends for `/speckit-clarify`. The 3 dB margin stays unless T065 saw flicker or a background holding the gate open; then report that too
- [ ] T067 Remembered across launches (quickstart §7, SC-007) on the fold emulator, the iPad Pro and the iPhone Duo (the Pixel 6 Pro and the iPhone 17 were done in T045); a fresh install shows the handle at "Off"
- [ ] T068 [P] Compose compiler report (`-PcomposeReports=true`) for `:shared:ui`: `NoiseGateSlider`, `TembraSliderThumb`, `AutoListenPane`, `TunerSettingsColumn` and `TunerTabletopSettings` are skippable with stable parameters, or the reason is written into NOTES
- [ ] T069 [P] Documents: a line "extended by feature 006" with a link in `specs/003-instrument-tuner/spec.md` (at FR-005, FR-010 and FR-023) and `specs/005-tabletop-posture/spec.md` (at FR-003), and in `specs/003-instrument-tuner/contracts/tuner-audio-kotlin.md`, `tuner-preferences.md`, `tuner-screen.md`, `specs/004-metronome/contracts/engine-c-abi-v3.md` and `specs/005-tabletop-posture/contracts/tabletop-layout.md`; a status line at the top of `docs/research/tuner-noise-gate.md` pointing at this spec; the new tool and the two launch arguments in `app/README.md` where the existing ones are listed
- [ ] T070 Final gate: every lane of quickstart §1 green with zero compiler and lint warnings after the last change; one type per file; no decibel figure on the control; NOTES complete for every row of the matrix, with every skipped step named
- [ ] T071 After the owner accepts the build (FR-027): a "Slider" component entry in `design/stitch/DESIGN.md` covering the tempo slider and the noise gate slider (track, handle, states, the live fill)
- [ ] T072 After the owner accepts the build (FR-027, SC-013): new Auto Listen screens (upright phone, tablet, phone landscape, foldable tabletop) uploaded to the Stitch project from the captures, the old versions hidden, the export under `design/stitch/` re-pulled (`design/stitch/README.md`)

---

## Dependencies

Phase 1 → Phase 2 → US1 → US2 (the margin extends US1's `LevelGate`). US3 needs US1's slider; US4 needs US1's state and intent; US5 needs US1's slider and placement, and uses US3's fill and US4's settle in its tests. Phase 8 needs every story. T071 and T072 wait for the owner's acceptance.

Within a story: tests first, then engine before bridges, state before the control, the control before its placement, lanes last.

## Parallel examples

- Phase 2: T003 beside T008, T009, T013 and T015; then T012 and T014 together.
- US1: T017, T018, T019, T020, T021 and T022 are six test files written together; then T023 → T024 beside T025 → T026 and T027.
- US4 (T039–T043, `:shared:data` and the ViewModel) can run beside US3 (T034–T037, the slider's track).
- US5: T046, T047, T048 and T049 together; then T050, T052 and T053 touch different files.
- Phase 8: T068 and T069 beside the device work; T059–T062 one target at a time (one workstation, one loudspeaker).

## Implementation strategy

MVP = Phases 1–3: a gate that works on a phone held upright, set on a plain slider under the gauge. Then the closing margin (US2, still P1 and small), the live fill (US3), remembering (US4), every form and input (US5), and the five-target verification. The scale checkpoint (T066) can stop the feature for a decision; nothing else depends on the owner until acceptance.

## Notes

- A task that says "failing" is done when the test is written and seen to fail for the right reason; the implementation task that names it turns it green.
- Commit after each phase's lanes are green.
- A target or step that cannot be driven is written into NOTES with the reason and the command the owner can run; it is never dropped (FR-024).
