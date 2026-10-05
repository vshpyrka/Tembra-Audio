# Tuner noise gate: what the tuner does today and how a gate would fit

Research note for a possible tuner setting: a user-adjustable noise gate (an input level
threshold) in Auto Listen. Written 2026-09-29 against the built feature 003 (merged to `main`
in d883141) and constitution v1.2.0. No code was changed; this is the evidence for a future
`/speckit-specify`.

**Status (2026-10-06):** specified and built as feature 006, `specs/006-tuner-noise-gate/` (spec, plan, contracts and
verification notes are there and win over this note). Where the build departs from the recommendation below: the
control is a slider drawn on the live input level, not a three-level "Sensitivity" selector (the owner's choice in
clarification); the gate value is set through its own call, `tembra_tuner_set_gate`, and kept for the engine's life
rather than carried in `tembra_tuner_config`; the level is measured over the analysis window after the band-pass,
not per hop; and an open gate closes 3 dB under its level.

> TL;DR — The engine already runs three fixed gates (a −80 dBFS silence floor, a clarity
> threshold and a range check) and none of them rejects a pitched background sound above that
> floor. A level gate fits as one more field in `tembra_tuner_config`, applied on the analysis
> thread where the hop level is already measured. The Stitch export drew the control once, on
> `tuner-landscape-auto-listen-d`, and feature 003 rejected that screen as outside the spec, so
> the work starts with a spec, not with code.

---

## 1. The pipeline as built

Every path below is under `app/`.

| Stage | Where | What it does |
|---|---|---|
| Platform shell | `engine/src/platform/android/oboe_audio_device.cpp`, `engine/src/platform/apple/remote_io_audio_device.cpp` | Mono float input, 48 kHz preferred. Android asks for the `Unprocessed` input preset and falls back to `VoiceRecognition` (lines 40 to 42); iOS sets `AVAudioSessionModeMeasurement` (`shared/engine/src/iosMain/kotlin/audio/tembra/engine/IosAudioSession.kt:68`). Both keep the platform's gain control and noise suppression off the signal. |
| Callback | `engine/src/tuner/tuner_engine.cpp`, `onInput` | Copies the block into an SPSC ring buffer of 4 × window frames and returns. Nothing else runs on the callback (Constitution I). |
| Analysis thread | `engine/src/tuner/tuner_analyzer.cpp:50`, `analyseAvailable` | Pulls one 1024-frame hop (21 ms) when available, sleeping 4 ms otherwise. Measures the hop's linear peak (line 56) **before** the pre-filter, then filters the hop in place and slides it into the 4096-frame window (85 ms). |
| Pre-filter | `engine/src/tuner/band_pass.cpp` | DC blocker (pole 0.999), second-order Butterworth high-pass at 0.9 × `min_hz` (45 Hz) and low-pass at 1.1 × `max_hz` (1540 Hz). |
| Detector | `engine/src/tuner/mcleod_pitch_detector.cpp` | McLeod Pitch Method: autocorrelation through a double-precision Signalsmith real FFT, NSDF, first key maximum at or above `peak_ratio` (0.9) × the highest, parabolic interpolation. Returns `{hz, clarity}`. A window whose RMS is under 1e-4 (−80 dBFS) returns clarity 0 (`mcleod_pitch_detector.h:32`). |
| Smoother | `engine/src/tuner/pitch_smoother.cpp:28` | Accepts an estimate when clarity ≥ `clarity_threshold` (0.5) and the pitch is inside `[min_hz, max_hz]`. Median of `median_size` (5), an octave-jump guard (3 cents, the jump must repeat), an exponential average in log frequency (α 0.3). Reports a pitch after two accepted hops; holds the last reading through four rejected hops and rests on the fifth (about 100 ms). |
| Reading | `engine/src/tuner/tuner_analyzer.cpp:75`, `publish` | Seqlock-published `tembra_pitch_reading`: `sequence`, `frequency_hz`, `clarity`, `input_peak`, `has_pitch`, `synthetic`. |
| Kotlin | `shared/engine/src/commonMain/kotlin/audio/tembra/engine/TunerAudio.kt`, `shared/ui/src/commonMain/kotlin/audio/tembra/ui/tuner/TunerViewModel.kt` | `readPitch` fills a `PitchSnapshot` without allocating. The ViewModel polls at 10 Hz for the note and the state (`poll`); the meter reads every frame in its draw lambda (`PitchTelemetry`). |

The config is the eight-field `tembra_tuner_config` (`engine/include/tembra_engine.h:55`):
window, hop, min and max Hz, clarity threshold, peak ratio, median size, watchdog seconds. It
is validated in `TunerAnalyzer::validate`, crosses JNI as eight scalars
(`engine/android/src/main/cpp/tembra_jni.cpp:48`) and cinterop through `fill`
(`NativeAudioEngine.ios.kt:44`), and is mirrored by `TunerAnalysisConfig` in Kotlin. The
ViewModel starts listening with the defaults (`TunerViewModel.kt:278`); no field is
user-facing. The tuner preferences hold five keys (instrument, string count, preset, mode, loop
tone), none of them an audio tunable (`specs/003-instrument-tuner/contracts/tuner-preferences.md`).

Two facts matter for a gate:

- `input_peak` is published on every hop but nothing in the UI reads it. The only Kotlin
  consumers are the two facades, the fake and one fake test (`grep -rn inputPeak app/shared`).
- The peak is measured on the raw hop, before the band-pass, so rumble and hiss outside the
  hearing range contribute to it.

## 2. What the fixed gates do and do not catch

Three checks reject a reading today, all hard-coded or set once at start:

1. The detector's silence floor at −80 dBFS RMS. That is far below any room's noise floor
   through a phone microphone, so in practice it catches digital silence and a muted input.
2. The clarity threshold of 0.5. It rejects unpitched noise: `engine/tests/mpm_test.cpp`
   ("silence and noise have no clear pitch") feeds white noise at −30 dBFS peak and asserts
   clarity under 0.5, and silence likewise.
3. The range check and the octave guard in the smoother.

What passes: any periodic sound above −80 dBFS with clarity over 0.5, whatever its level. A
voice, a television, music in the next room or the harmonics of mains hum all have clarity.
When the instrument is silent, the tuner names whichever of those is periodic enough. FR-010 of
the spec says the meter must rest on "silence, noise, no single dominant note", and the edge
cases promise no wrong note with the "In tune" pill lit for very quiet input
(`specs/003-instrument-tuner/spec.md`); neither promises rest on a quiet but pitched background,
and no test covers that case.

A noise gate is the standard answer to exactly that case: ignore the input until it is louder
than a threshold the user sets above the background and below the instrument. It does not help
when the background is louder than the instrument, and nothing short of source separation does.

## 3. Where a gate would live

### Recommended: a config field applied on the analysis thread

Add a threshold in dBFS (and, if the spec wants it, a close threshold or a hold time) to
`tembra_tuner_config`, with a default that means "off" so current behaviour is unchanged. In
`analyseAvailable`:

1. Measure the hop level **after** the band-pass rather than before, so out-of-band noise cannot
   hold the gate open. RMS over the hop is steadier than the peak; the design's slider is
   labelled in dB, so either works as long as the on-screen readout uses the same measure.
2. Compare against the threshold with hysteresis (open at the threshold, close a few dB under
   it) so a note hovering around the threshold does not flicker.
3. When the gate is closed, skip the FFT and push a rejected `PitchEstimate{0, 0}` into the
   smoother. The smoother's existing hold and rest logic then applies unchanged, and the reading
   rests inside the same 500 ms budget as today (SC-007).

This keeps the work on the analysis thread, where the hop level is already computed, and off
the callback. The detector stays pure; the fixed −80 dBFS floor remains as the lower bound.

Touch points, all inside feature 003's existing seams (research R2 in
`specs/003-instrument-tuner/research.md` chose "tuning is one struct" for this reason):

| Layer | Change |
|---|---|
| C ABI | New field(s) in `tembra_tuner_config`; `TEMBRA_ENGINE_ABI_VERSION` to 3 (the header bumps it for any changed function, and the struct is the argument of `tembra_tuner_start`); `defaultConfig` and `validate`; the contract `engine-c-abi-v2.md` succeeded by a v3 document. |
| JNI | One more scalar on `nativeTunerStart` and `nativeTunerStartTestSignal` in `tembra_jni.cpp` and on their `external fun` declarations in `NativeAudioEngine.android.kt`. |
| cinterop | One more assignment in `fill` in `NativeAudioEngine.ios.kt`. |
| Kotlin config | A field on `TunerAnalysisConfig`; `FakeAudioEngine` records the config it was started with so ViewModel tests can assert it. |
| Tests | Catch2 in `engine/tests/tuner_analyzer_test.cpp`: white noise at −30 dBFS with the gate at −20 rests; a tone at −20 dBFS peak with the gate at −40 reads; hysteresis does not flicker at the threshold; the gate off reproduces today's readings. The fixtures in `engine/tests/signal_fixtures.h` (`whiteNoise(dbfs, …)`, `harmonicTone`, `silence`) already generate the inputs. |

### Changing the threshold while listening

The config is read once at `tembra_tuner_start`. Moving a control would then mean stop and
start: the analysis thread is joined, the Oboe or RemoteIO stream closes and reopens, and the
platform's microphone indicator blinks. Two options:

- Restart. Simplest: `DesiredAudio.Listening` becomes a data class carrying the config, so the
  ViewModel's `desired != current` comparison restarts the stream on a change.
- A live setter, `tembra_tuner_set_gate(float dbfs)`, storing an atomic that the analysis thread
  reads each hop. Allowed by the ABI rules (any non-callback thread, no locks), and the control
  stays live. This is the better experience and still small.

### Not recommended: masking in Kotlin

The 10 Hz poll and the draw-phase reader could both ignore readings whose `inputPeak` is under a
threshold, with no ABI change. But the hysteresis state would be duplicated in two readers, the
published peak is pre-filter, and DSP semantics would leave the engine, which the constitution
places in C++ (Principle I).

## 4. The user-facing control

The design exists, once. `design/stitch/screens/tuner-landscape-auto-listen-d/index.html`
(lines 336 to 352) draws a card titled "NOISE GATE / SENSITIVITY" with a gradient slider, tick
labels "−60 dB (High)", "−40 dB (Optimal)", "−10 dB (Low)" and a live readout
"SIGNAL LOCK (−42 dB)". No other tuner screen has it, and `design/stitch/DESIGN.md` defines no
slider component. Feature 003 rejected that screen together with `tuner-landscape-by-ear-c`
because they "add a noise gate, A4 calibration, timbre and octave-shift controls and a 'Stop
tone' bar that the spec does not have" (`specs/003-instrument-tuner/screenshots/NOTES.md`,
"Rejected" paragraph; research R9).

Points a spec and plan would have to settle:

- **Shape of the control.** A three-level segmented "Sensitivity" (for example −60, −45 and
  −30 dBFS) reuses the selector pattern the screen already has (`TunerSelectorControls.kt`,
  `SegmentWidths.Equal`, `minimumInteractiveComponentSize`, `testTag`) and needs no new
  design-system component. A continuous slider matches the Stitch drawing but needs a design
  pass for Material 3 `Slider` against `theme.json`.
- **Placement.** Auto Listen only. Portrait: under the selector bar. Phone landscape: in
  `TunerSettingsColumn`, which already scrolls when it runs out of height (research R9 dropped
  the overlines for that reason).
- **A level indication.** A threshold is hard to set blind. The draw-phase snapshot already
  carries `inputPeak` every frame, so a small level bar in the meter's Canvas costs no
  recomposition (the research R8 pattern). The Stitch readout "SIGNAL LOCK (−42 dB)" is that
  indicator.
- **Persistence.** A sixth key, for example `tuner.gateDb` as a float, on
  `TunerPreferencesSnapshot`, `DataStoreTunerPreferences`, `FakeTunerPreferences` and the
  contract, with the decode rule "absent or out of range yields off"; tests on `commonTest`,
  `androidDeviceTest` and `iosTest` like the existing five keys. The ViewModel builds
  `TunerAnalysisConfig` from the snapshot instead of the defaults.

## 5. Process and constraints

- **Spec first.** Constitution VI: build only what the specification requires and "prefer
  deleting code over adding configuration". The spec has to state the user problem from §2 (a
  pitched background that the fixed gates pass) rather than offer the control for its own sake.
  FR-031 of feature 003 lists what the tuner must not include and a noise gate is not on it;
  FR-010 stays as written.
- **Numbering and order.** Feature 004 is the metronome (spec written 2026-09-29 on
  `004-metronome`), so this is 005 or later as its own feature, or an amendment to 003 through
  `/speckit-specify` and `/speckit-clarify`. The constitution's build order puts the metronome
  next; where a tuner enhancement slots in is a build-order decision for the owner.
- **Test-first** (Constitution III): the Catch2 cases in §3 before the analyzer change; ViewModel
  tests with `FakeAudioEngine` and `FakeTunerPreferences`; a Compose UI test for the control on
  both simulator lanes; screenshots against the Stitch reference if the slider is chosen.
- **Skills** (Constitution VIII): the Kotlin and Compose pieces go through the chrisbanes skills
  as feature 003's did.

## 6. Risks

- **Plucked decay.** A guitar note falls 20 to 30 dB over a few seconds. With the gate near
  −40 dBFS the reading can rest while the player is still watching the needle. Hysteresis with a
  hold, or conservative level presets, is needed; a bowed cello sustains and is unaffected. This
  is the main reason to keep the default off.
- **Device variance.** Android devices that ignore `Unprocessed` and apply gain control raise
  the noise floor, so one absolute dBFS default behaves differently across phones. That argues
  for a user-set level plus the level indication, not for a tuned constant.
- **Two level measures.** If the readout shows the raw peak and the gate measures the filtered
  level, the numbers disagree. Use one measure for both.
- **Scope.** A gate only helps when the instrument is louder than the background. The spec should
  say so in its edge cases rather than promise silence in a noisy room.

## 7. Effort

Engine and ABI are the smallest part: the hop level and the resting logic already exist. The
data layer is one key and its tests on three lanes. The UI is the largest part because the
control appears in three layouts with tests and screenshots. Including the spec, plan and tasks
cycle, a few days.

## Sources

Everything above was read from the repository on 2026-09-29:

- Engine: `app/engine/include/tembra_engine.h`; `app/engine/src/tuner/{tuner_engine,tuner_analyzer,mcleod_pitch_detector,band_pass,pitch_smoother}.{h,cpp}`; `app/engine/src/platform/android/oboe_audio_device.cpp`; `app/engine/src/platform/apple/remote_io_audio_device.cpp`; `app/engine/android/src/main/cpp/tembra_jni.cpp`; `app/engine/tests/{mpm_test,tuner_analyzer_test}.cpp`; `app/engine/tests/signal_fixtures.h`.
- Kotlin: `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/{TunerAudio,TunerAnalysisConfig,PitchSnapshot}.kt`; `app/shared/engine/src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt`; `app/shared/engine/src/iosMain/kotlin/audio/tembra/engine/{NativeAudioEngine.ios,IosAudioSession}.kt`; `app/shared/data/src/commonMain/kotlin/audio/tembra/data/tuner/*.kt`; `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/tuner/{TunerViewModel,ListeningPolicy,DesiredAudio,MeterReading,PitchTelemetry,TunerSettingsColumn}.kt`.
- Feature 003: `specs/003-instrument-tuner/{spec,plan,research}.md`; `specs/003-instrument-tuner/contracts/{engine-c-abi-v2,tuner-audio-kotlin,tuner-preferences}.md`; `specs/003-instrument-tuner/screenshots/NOTES.md`.
- Design: `design/stitch/screens/tuner-landscape-auto-listen-d/index.html`; `design/stitch/DESIGN.md`.
- Governance and prior research: `.specify/memory/constitution.md` (v1.2.0); `docs/research/tuner-algorithms.md`, whose "Concrete recommended configuration" lists a clarity gate and no level gate.
