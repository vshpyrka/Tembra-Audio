# Data Model: Instrument Tuner

**Feature**: `003-instrument-tuner` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Types are Kotlin unless stated; one type per file (Constitution VII). Domain types live in
`:shared:core` (`audio.tembra.core.tuner`), engine types in `:shared:engine`
(`audio.tembra.engine`), preference types in `:shared:data` (`audio.tembra.data.tuner`), UI
state in `:shared:ui` (`audio.tembra.ui.tuner`). Evidence for design values is
[research.md](research.md) R9.

## 1. Instrument, strings, notes and presets (`:shared:core`)

| Type | Fields | Rules |
|---|---|---|
| `enum class Instrument` | `Guitar("guitar", "Guitar")`, `Cello("cello", "Cello")`; `id`, `label`, `icon` name (`guitar`, `cello`), `hasStringCount` (Guitar only) | order is display order (spec FR-002) |
| `enum class GuitarStringCount` | `Six(6, "6-String")`, `Seven(7, "7-String")` | shown for Guitar only |
| `enum class PitchClass` | `C, CSharp, D, DSharp, E, F, FSharp, G, GSharp, A, ASharp, B`; `label` uses `#` (`C#`) | sharp spelling everywhere (spec FR-009) |
| `data class Note(pitchClass, octave)` | `midi` (C4 = 60), `name` (`"E2"`, `"F#3"`), `frequencyHz(a4 = 440.0) = 440 · 2^((midi − 69)/12)`, `fromMidi(midi)`, `parse("F#3")`, `resourceName` (`"Fsharp3"`) | octave = midi / 12 − 1 |
| `data class TargetString(numeral, note)` | `numeral` Roman (`"I"` = highest string), `frequencyHz` | derived from a preset |
| `data class TuningPreset(id, name, instrument, guitarStrings: GuitarStringCount?, notes: List<Note>)` | `strings: List<TargetString>` lowest first with numerals counting down to `I`; `summary` (`"E2 A2 D3 G3 B3 E4"`); `lowestHz`, `highestHz` | `notes.size` equals the string count; ids are stable strings (`guitar6-standard`, `guitar6-drop-d`, `guitar7-standard`, `guitar7-drop-a`, `cello-standard`, `cello-bach-5`, …) |
| `object TuningPresets` | `all`, `forInstrument(instrument, guitarStrings)`, `default(instrument, guitarStrings)`, `byId(id)` | exactly the table of spec FR-003, first entry is the default; tests assert every note and frequency |
| `enum class TunerMode` | `AutoListen("Auto Listen")`, `ByEar("By Ear")` | |
| `object PitchMath` | `A4_HZ = 440.0`, `IN_TUNE_CENTS = 3.0`, `METER_RANGE_CENTS = 50.0`, `MIN_HZ = 50.0`, `MAX_HZ = 1400.0`; `midiFromHz(hz)`, `nearest(hz): NoteReading` | halfway rule: `n = ceil(midiFloat − 0.5)`, so cents ∈ (−50, +50] and an exact midpoint shows the lower note at +50 (spec FR-009) |
| `data class NoteReading(note, cents)` | `direction: PitchDirection`, `isInTune = abs(cents) ≤ IN_TUNE_CENTS`, `referenceHz` | |
| `enum class PitchDirection` | `Flat`, `InTune`, `Sharp` | |
| `object RomanNumerals` | `of(1..7)` | |

Preset table (frequencies at A4 = 440 Hz, one decimal as shown on the cards):

| id | name | strings (lowest → highest) | Hz |
|---|---|---|---|
| `guitar6-standard` | Standard | E2 A2 D3 G3 B3 E4 | 82.4 110.0 146.8 196.0 246.9 329.6 |
| `guitar6-drop-d` | Drop D | D2 A2 D3 G3 B3 E4 | 73.4 110.0 146.8 196.0 246.9 329.6 |
| `guitar6-half-step-down` | Half-Step Down | D#2 G#2 C#3 F#3 A#3 D#4 | 77.8 103.8 138.6 185.0 233.1 311.1 |
| `guitar6-d-standard` | D Standard | D2 G2 C3 F3 A3 D4 | 73.4 98.0 130.8 174.6 220.0 293.7 |
| `guitar6-dadgad` | DADGAD | D2 A2 D3 G3 A3 D4 | 73.4 110.0 146.8 196.0 220.0 293.7 |
| `guitar6-open-g` | Open G | D2 G2 D3 G3 B3 D4 | 73.4 98.0 146.8 196.0 246.9 293.7 |
| `guitar6-open-d` | Open D | D2 A2 D3 F#3 A3 D4 | 73.4 110.0 146.8 185.0 220.0 293.7 |
| `guitar7-standard` | Standard 7-String | B1 E2 A2 D3 G3 B3 E4 | 61.7 82.4 110.0 146.8 196.0 246.9 329.6 |
| `guitar7-drop-a` | Drop A | A1 E2 A2 D3 G3 B3 E4 | 55.0 82.4 110.0 146.8 196.0 246.9 329.6 |
| `cello-standard` | Standard | C2 G2 D3 A3 | 65.4 98.0 146.8 220.0 |
| `cello-bach-5` | Bach Suite No. 5 | C2 G2 D3 G3 | 65.4 98.0 146.8 196.0 |

## 2. Tuner preferences (`:shared:data`)

`data class TunerPreferencesSnapshot(instrument = Guitar, guitarStrings = Six, presetId = "guitar6-standard", mode = AutoListen, loopTone = true)`
persisted as DataStore preference keys `tuner.instrument`, `tuner.guitarStrings`,
`tuner.presetId`, `tuner.mode` (strings: the enum ids) and `tuner.loopTone` (boolean). Reading
an unknown value yields the default for that field; a `presetId` that does not belong to the
stored instrument and string count yields that pair's default preset (validated in the
ViewModel through `TuningPresets`). `interface TunerPreferences { val snapshot: Flow<TunerPreferencesSnapshot>; suspend fun update(transform) }`.

## 3. Audio state and the listening policy (`:shared:engine`, `:shared:ui`)

`enum class AudioState { Idle, Listening, Tone }` is what the engine reports
(`tembra_audio_state`). At most one stream is open; `Listening` and `Tone` never overlap
(spec FR-006).

`ListeningPolicy.desired(inputs): DesiredAudio` (pure, `:shared:ui`):

| Input | Source |
|---|---|
| `mode` | preferences / selector |
| `screenVisible` | `TunerScreen` `DisposableEffect` (entry composition) |
| `foreground` | `LifecycleStartEffect` (`ON_START` / `ON_STOP`; not pause, research R7) |
| `testSignal` | `TestSignal?` from the debug launch argument (§9); null in production |
| `permission` | `MicrophonePermissionStatus` |
| `audition` | `AuditionState?` |
| `interrupted` | set by `AudioEvent.Interrupted`, cleared by `InterruptionEnded` or leaving/re-entering |

| Result | When |
|---|---|
| `DesiredAudio.Tone(sound, loop)` | `mode == ByEar && audition != null && screenVisible && foreground && !interrupted` |
| `DesiredAudio.Listening` | `mode == AutoListen && screenVisible && foreground && permission == Granted && !interrupted` |
| `DesiredAudio.TestListening(signal)` | as above but `testSignal != null`, regardless of `permission` (uses `startTestSignal`) |
| `DesiredAudio.Idle` | otherwise |

Transitions applied by `TunerViewModel.applyAudio()`: any change first stops the current stream
(`stopListening()` or `stopTone()`), then starts the desired one; a desired `Tone` that equals
the current audition is a no-op; `Idle` after `Tone` sets `audition = null` (spec FR-020: a
tone never restarts by itself). Events: `DeviceLost` → re-apply; `OutputRouteLost` → `audition = null`;
`Interrupted` → `interrupted = true`; `InterruptionEnded` → `interrupted = false` then re-apply
(spec FR-016). The engine's watchdog (research R7) reports `Idle` while the policy wants
`Listening`; the 10 Hz poll re-applies.

## 4. Pitch snapshot and pitch reading

`class PitchSnapshot` (mutable, `:shared:engine`, one instance per reader, filled by
`TunerAudio.readPitch(into)` without allocation): `sequence: Long`, `state: AudioState`,
`sampleRate: Int`, `frequencyHz: Float`, `clarity: Float`, `inputPeak: Float`, `hasPitch: Boolean`,
`synthetic: Boolean` (true while a test signal feeds the analyzer).
`hasPitch == false` is the resting state (spec FR-010). Mirrors `tembra_pitch_reading`
(contracts/engine-c-abi-v2.md).

`data class MeterReading(note: Note, cents: Double, direction: PitchDirection, isInTune: Boolean, frequencyHz: Double, referenceHz: Double)`
(`:shared:ui`): the 10 Hz sample of a snapshot through `PitchMath.nearest`, `null` when
resting; `stateDescription` text: `"E2, 4 cents sharp"`, `"F#3, in tune"`, or `"Listening"`; cents are
rounded to the nearest integer.

## 5. Microphone permission status (`:shared:ui`)

`enum class MicrophonePermissionStatus { NotDetermined, Granted, DeniedCanAsk, DeniedOpenSettings }`

| From | Event | To |
|---|---|---|
| `NotDetermined` | entering Auto Listen → `request()` | `Granted` or a denied state |
| `DeniedCanAsk` | "Allow microphone" → `request()` | `Granted` or `DeniedOpenSettings` (Android) |
| `DeniedOpenSettings` | "Allow microphone" → `openSystemSettings()`; on resume `refresh()` | `Granted` if changed in settings |
| `Granted` | `refresh()` on resume | a denied state if revoked (spec FR-015) |

iOS never returns `DeniedCanAsk`.

## 6. Audition state and reference sound (`:shared:ui`)

`data class AuditionState(stringIndex: Int, loop: Boolean, sound: ReferenceSound)`; at most one.
`sealed interface ReferenceSound { data class Synth(frequencyHz: Double); data class Recording(resourceName: String, bytes: ByteArray) }`.
Resolution: `ReferenceSoundRepository.resolve(instrument, note)` returns `Recording` when
`files/tuner/<instrument.id>-<note.resourceName>.wav` exists in the Compose resources, else
`Synth(note.frequencyHz())` (research R5). Engine mapping: `Synth` → `startTone(hz, durationMs = if (loop) 0 else 2000)`;
`Recording` → `loadSample(bytes)` (handle cached per resource name) then `playSample(handle, loop)`.

## 7. `TunerUiState` (`:shared:ui`)

```kotlin
data class TunerUiState(
    val loaded: Boolean = false,                         // preferences read; screen shows nothing audio-related before
    val instrument: Instrument = Instrument.Guitar,
    val guitarStrings: GuitarStringCount = GuitarStringCount.Six,
    val preset: TuningPreset = TuningPresets.default(Guitar, Six),
    val presets: List<TuningPreset>,                     // for the current instrument and string count
    val mode: TunerMode = TunerMode.AutoListen,
    val loopTone: Boolean = true,
    val microphone: MicrophonePermissionStatus = NotDetermined,
    val audio: AudioState = AudioState.Idle,
    val interrupted: Boolean = false,
    val reading: MeterReading? = null,                   // 10 Hz sample; null = resting
    val audition: AuditionState? = null,
    val testSignal: TestSignal? = null,                  // debug launch argument (§9)
) {
    val pill: AudioStatePill?      // TestSignal when audio == Listening && testSignal != null, MicActive when audio == Listening, ToneOut when audio == Tone, else null (spec FR-006)
    val showDeniedState: Boolean   // mode == AutoListen && microphone is a denied state
    val strings: List<TargetString> get() = preset.strings
}
enum class AudioStatePill(val label: String) { MicActive("Mic active"), ToneOut("Tone out"), TestSignal("Test signal") }   // label = accessibility text; drawn in capitals
```

Intents (plain functions on `TunerViewModel`): `selectInstrument`, `selectGuitarStrings`,
`selectPreset`, `selectMode`, `setLoopTone`, `audition(stringIndex)`, `stopAudition()`,
`requestMicrophone()`, `screenVisible(Boolean)`, `foreground(Boolean)`, `useTestSignal(TestSignal?)`; every selector intent
persists through `TunerPreferences.update`. Instrument or string-count changes select the
default preset, replace `presets` and stop any tone (spec FR-004).

## 8. Meter geometry and new design tokens (`:shared:ui`)

`MeterGeometry` (pure, unit-tested): normalised to the design's 400 × 230 view box scaled to
the available width (max 340 dp compact, 480 dp expanded, aspect 1.8 : 1): pivot (0.5, 0.913),
outer radius 0.4 of the width, end marks at ±90°, quarter marks at ±45°, top mark at 0°,
in-tune zone = ±`IN_TUNE_CENTS` × 1.8°, dashed circle radius 0.2875, inner arc radius 0.225
between ±68°, dashed inner arc between ±36°, needle length 0.43, pivot ring 0.03, dot 0.0125.
`needleAngle(cents) = (cents.coerceIn(−50, 50)) × 1.8°`; a per-frame smoothing of the drawn
angle with a time constant of 80 ms.

Tokens added to the theme (data, tested for parity with research R9 in `TunerTokensTest`):

| Token | Value |
|---|---|
| `TembraTypographyExtras.tunerNote` | Space Grotesk 64 sp / 64 sp, weight 700, −0.02 em |
| `TembraTypographyExtras.tunerOctave` | Space Grotesk 32 sp / 32 sp, weight 700, `primary` |
| `TembraTypographyExtras.tunerCardNote` / `tunerCardOctave` | Space Grotesk 32 / 20 sp, weight 700 |
| `TembraTypographyExtras.tunerReadout` | Space Grotesk 18 sp / 24 sp, weight 600, 0.05 em, tabular |
| `TembraTypographyExtras.microLabel` | Space Grotesk 11 sp / 14 sp, weight 600, 0.04 em |
| `TembraSpacing.segmentHeightLarge` / `segmentHeightSmall` | 32 dp / 28 dp |
| `TembraSpacing.meterMaxWidth` / `meterMaxWidthWide` | 340 dp / 480 dp |
| `TembraSpacing.byEarGridMaxWidth` / `byEarGridMaxWidthWide` | 384 dp / 576 dp |
| `TembraSpacing.stringCardHeightWide` | 144 dp |
| `TembraSpacing.toggleWidth` / `toggleHeight` / `toggleKnob` | 32 / 16 / 12 dp |
| `TembraSpacing.statusDot` | 6 dp |
| `TembraGradients.meterCompact` | radial at (0.6, 0.45): `#A78BFA` 12 % → `#D946EF` 6 % at 35 % → transparent at 70 % |
| `TembraGradients.byEarCompact` | radial at (0.5, 0.3): `#AB88FF` 6 % → `#85145A` 6 % at 40 % → transparent at 75 % |
| `TembraGradients.byEarWide` | radial at (0.6, 0.5): `#A78BFA` 10 % → `#85145A` 8 % at 45 % → transparent at 75 % |
| `TembraGradients.discTopStart` / `discBottomEnd` | `primaryContainer` 10 % / `secondaryContainer` 15 %, radius 112 dp, centred 64 dp outside the card corners |
| `TembraGradients.needleGlow` | `#FFAFD3` 70 %, 8 dp |
| `TembraGradients.playingGlow` | `#85145A` 40 %, 18 dp |

`TembraIcon` gains `Mic, MicOff, Hearing, PlayArrow, Pause, VolumeUp, ExpandMore, ArrowLeft, ArrowRight, Settings, Guitar, Cello`.

## 9. Test signal (`:shared:engine`, `:shared:ui`; research R16)

`data class TestSignal(val note: Note, val cents: Double, val amplitude: Float = 0.5f, val harmonics: Int = 5, val fundamentalGain: Float = 0.25f)`
with `frequencyHz = note.frequencyHz() * 2^(cents / 1200)`; `TestSignals.catalogue: List<TestSignalEntry(id, signal, expected)>` where
`expected` is `Reading(note, cents)` or `Resting`:

| id | signal | expected reading |
|---|---|---|
| `e2-in-tune` | E2, 0 | E2, 0 |
| `e2-flat-20` | E2, −20 | E2, −20 |
| `e2-sharp-20` | E2, +20 | E2, +20 |
| `e2-flat-45` | E2, −45 | E2, −45 (needle near the flat end) |
| `a2-sharp-40` | A2, +40 | A2, +40 |
| `d3-flat-5` | D3, −5 | D3, −5 (just outside tolerance: "Flat" lit) |
| `g3-in-tune`, `b3-in-tune`, `e4-in-tune` | G3 / B3 / E4, 0 | in tune |
| `c2-in-tune`, `b1-in-tune`, `a1-in-tune` | C2 / B1 / A1, 0 | in tune (low strings, correct octave) |
| `e5-in-tune` | E5, 0 | in tune (top of the range) |
| `out-of-range-2khz` | frequency 2000 Hz (no note) | resting |

`TunerTestSignalRequest.parse(argument: String?): TestSignal?` (`:shared:ui`): `null` → null; a catalogue id → its
signal; `NOTE:CENTS` (`E2:-20`, `F#3:+5`, `A1:0`) → `TestSignal(Note.parse, cents)`; anything else → null with the
argument ignored. Provided to the screen through `LocalTunerTestSignal`; `App(appGraph, developerView, tunerTestSignal)`.
