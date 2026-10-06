# Data Model: Tuner Noise Gate

One value is stored (the gate level, §4); everything else is derived per hop in the engine or per frame on the
screen. Paths are under `app/`.

## 1. NoiseGate (`:shared:core`, `audio.tembra.core.tuner`)

The gate level the user set: off, or a level on the scale. A `@JvmInline value class` over the step number
(research R7).

| Member | Type | Rule |
|---|---|---|
| `step` | `Int` | 0 is off; 1 to 30 are −78 dB to −20 dB. The constructor is private; values come from the companion |
| `isOff` | `Boolean` | `step == 0` |
| `levelDb` | `Int?` | `null` when off, otherwise `MIN_DB + step × STEP_DB` |
| `fraction` | `Float` | `step / STEPS`: where the handle stands on the track, 0 at "Off", 1 at "High" |
| `percent` | `Int` | `fraction × 100`, rounded; the announced value (FR-019) |

Companion:

| Member | Rule |
|---|---|
| `MIN_DB = -80`, `MAX_DB = -20`, `STEP_DB = 2`, `STEPS = 30` | The scale of FR-001 and FR-002; the only place these numbers live (research R8) |
| `Off` | `step = 0` |
| `ofStep(step: Int): NoiseGate` | Coerced into `0..STEPS`; what the slider reports |
| `ofLevelDb(db: Int): NoiseGate?` | `null` outside `MIN_DB + STEP_DB .. MAX_DB`; a value between two steps goes to the quieter one (−51 → −52). `MIN_DB` itself is not a level: it is where "Off" stands |
| `fromStored(db: Int?): NoiseGate` | `null` → `Off`; otherwise `ofLevelDb(db) ?: Off` (FR-014) |
| `fractionOfLevel(levelDb: Float): Float` | `(levelDb − MIN_DB) / (MAX_DB − MIN_DB)`, coerced into 0..1; where a measured level ends on the track. Primitives only: it is called every frame |
| `CheckLevels` | `[-60, -50, -40]` as `NoiseGate`s: the levels the tests and captures use (spec, Terms) |

`fromStored` examples: `null` → Off; −90 → Off; −80 → Off; −79 → Off (halfway between "Off" and −78: the quieter);
−78 → −78; −51 → −52; −20 → −20; −19 → Off; 0 → Off.

## 2. The gate in the engine (`engine/src/tuner`)

**Gate value** (`EngineCore`, then `TunerAnalyzer`): off, or an opening level in dB within −120..0. Set by
`tembra_tuner_set_gate`; kept for the engine's life; initial value off. Handed to the analysis thread as one
`std::atomic<float>` whose sentinel (below −120) means off.

**Input level** (`TunerAnalyzer`): RMS of the analysis window after the band-pass, `20 · log10(rms)`, floor −120 dB;
refreshed every hop from the last `window_frames / hop_frames` hop energies (research R2). Until the first window is
full it is computed over the hops received so far.

**LevelGate** (`level_gate.h`, one instance per analyzer): the state machine of research R3.

| Field | Meaning |
|---|---|
| `open` | Whether sound is being read. Starts `true`; recomputed every hop |
| `seenGate` | The gate value of the previous hop, to notice a change |
| `kCloseMarginDb = 3.0f` | How far under the gate level an open gate closes |

`bool update(float gate, float levelDb)`: off → open; value changed → `level ≥ gate`; closed → opens at
`level ≥ gate`; open → stays open while `level ≥ gate − kCloseMarginDb`.

**Effect on the reading**: gate closed → the detector is not run for that hop and the smoother receives a rejected
estimate; `clarity` is published as 0. Gate open → exactly today's path.

## 3. The reading (`tembra_pitch_reading` ↔ `PitchSnapshot`)

| C field | Kotlin field | Meaning |
|---|---|---|
| `float input_level_db` | `inputLevelDb: Float` | §2's input level for the last hop; −120 before the first hop and when not listening |
| `uint8_t gate_open` | `gateOpen: Boolean` | 1 / `true` while sound is being read (gate off, or open); 1 when not listening |

All existing fields are unchanged. `has_pitch` is 0 whenever the gate is closed for longer than the smoother's hold.

## 4. The stored value (`:shared:data`, `audio.tembra.data.tuner`)

| Key | Type | Written | Read |
|---|---|---|---|
| `tuner.noiseGateDb` | `Int` | `levelDb` when a level is set; the key is removed when the gate is off | `NoiseGate.fromStored(value)` |

`TunerPreferencesSnapshot` gains `noiseGate: NoiseGate = NoiseGate.Off`. A fresh install and an update from a version
without the key both read `Off` (FR-014). The five existing keys are untouched.

## 5. Screen state (`:shared:ui`, `audio.tembra.ui.tuner`)

`TunerUiState` gains:

| Field | Type | Source |
|---|---|---|
| `noiseGate` | `NoiseGate` | The stored value at load, then every `setNoiseGate`; a debug launch value replaces the stored one for that launch (§8) |
| `gateOpen` | `Boolean` | `snapshot.gateOpen` at the 10 Hz poll; `true` when the poll is not running |

Derived:

| Property | Rule |
|---|---|
| `showNoiseGate` | `mode == AutoListen && !showDeniedState` (FR-001) |
| `soundAboveGate: Boolean?` | `null` unless `audio == Listening && !noiseGate.isOff`; otherwise `gateOpen`. Changes only when the gate opens or closes, so it recomposes the control a few times per note, not per frame |

`TunerCallbacks` gains `onNoiseGate: (NoiseGate) -> Unit` (every move of the handle) and
`onNoiseGateSettled: () -> Unit` (the move has ended).

## 6. ViewModel behaviour

| Moment | What happens, in order |
|---|---|
| Load (`start`) | Read the stored snapshot → pick the debug launch value if there is one → if that gate is not off, `audio.setNoiseGate(levelDb)` (the engine starts with no gate, so "off" needs no call) → state `loaded` → `applyAudio()` (listening may start) |
| ViewModel cleared | After the stream is stopped: if the gate is not off, `audio.setNoiseGate(null)`. The engine keeps a gate for the life of the process and the ViewModel for the life of its entry; a gate this ViewModel set must not outlive it, or the next entry could show "Off" over a gated engine |
| `setNoiseGate(gate)` | No-op if unchanged → state → `audio.setNoiseGate(gate.levelDb)`; nothing is written |
| `noiseGateSettled()` | `preferences.update { it.copy(noiseGate = state.noiseGate) }` through the existing `persist` launch site |
| Poll (10 Hz) | `gateOpen = snapshot.gateOpen` while the engine is listening, `true` in any other state, beside the existing `audio` and `reading`; when the poll stops, `gateOpen` goes back to `true` |
| Mode switch, leaving, backgrounding, interruption, rotation, fold | Nothing: the value stays in the state and in the engine; a later `startListening` is gated from its first hop |

`ListeningPolicy`, `PolicyInputs` and `DesiredAudio` are unchanged.

## 7. Test signals with a level (`:shared:engine`)

`TestSignalLevel.amplitudeFor(levelDb: Float, harmonics: Int = 5, fundamentalGain: Float = 0.25f): Float` returns the
peak amplitude whose mix has that RMS: `10^(levelDb / 20) × crest`, where `crest` is the peak over the RMS of the
partials `[fundamentalGain, 1, 0.5, 0.3, 0.2, …]` summed in phase, probed over 4096 points as the synthetic device
does (2.18 for the defaults). The pre-filter takes 0.02 dB off an E2 mix; the tests allow 1 dB.

New catalogue entries (after the existing fourteen; all in-tune E2, expected reading E2 at 0 cents with the gate
off):

| Id | Level | Peak amplitude |
|---|---|---|
| `e2-70db` | −70 dB | 0.00069 |
| `e2-55db` | −55 dB | 0.00388 |
| `e2-45db` | −45 dB | 0.01227 |
| `e2-30db` | −30 dB | 0.06899 |

Outcome of each signal at each gate value (R = reads E2, · = rests); the end-to-end test asserts all sixteen:

| Gate | `e2-70db` | `e2-55db` | `e2-45db` | `e2-30db` |
|---|---|---|---|---|
| off | R | R | R | R |
| −60 dB | · | R | R | R |
| −50 dB | · | · | R | R |
| −40 dB | · | · | · | R |

Every signal is at least 5 dB from every gate value it is tested against, outside the 3 dB margin.

## 8. Debug launch arguments (debug builds only)

| Argument | Android extra | iOS launch argument | Value | Parsed by |
|---|---|---|---|---|
| Test signal (feature 003), now with an optional level | `tembra.tuner.testSignal` | `--tembra-tuner-test-signal=` | a catalogue id, `NOTE:CENTS` or `NOTE:CENTS@LEVEL` (`E2:0@-55`; level −120..0) | `TunerTestSignalRequest.parse` |
| Gate level for this launch | `tembra.tuner.noiseGate` | `--tembra-tuner-noise-gate=` | `off`, or a whole decibel number on the scale (`-50`) | `TunerNoiseGateRequest.parse` → `TunerNoiseGateRequest?`, which holds the `NoiseGate` (anything else is ignored) |

The gate value reaches the ViewModel through `LocalTunerNoiseGate`, as the test signal does through
`LocalTunerTestSignal`. It replaces the stored value for that launch, is never written, and a later move of the
handle in that launch is persisted normally.

## 9. Copy (`TunerCopy`)

| Constant | Text | Used |
|---|---|---|
| `noiseGate` | "Noise gate" | The label, drawn in capitals; the slider's announced label, as written. The drawn text is hidden from accessibility so the control is one element (FR-019) |
| `off` (exists) | "Off" | Start label (drawn, hidden from accessibility); the announced value at step 0 |
| `gateHigh` | "High" | End label (drawn, hidden from accessibility) |
| `gatePercent(n)` | "n percent" | The announced value at steps 1 to 30 |
| `soundAboveGate` | "sound above the gate" | Announced state while listening behind an open gate |
| `soundBelowGate` | "sound below the gate" | Announced state while listening behind a closed gate |

## 10. Verification matrix (what the notes must hold, FR-024)

One row per target and orientation or posture, each with: lanes run and result, end-to-end result, structural check
(Android), capture file names, live-sound result, and anything that could not be driven with its reason.

| Target | Orientations and postures |
|---|---|
| Pixel 6 Pro | upright, sideways |
| Pixel 9 Pro Fold emulator | opened flat upright and sideways, tabletop, book |
| iPhone 17 simulator | upright, sideways |
| iPad Pro 13-inch (M5) simulator | upright, sideways |
| iPhone Duo simulator | closed upright, open upright and sideways (the poses Device Hub offers) |
