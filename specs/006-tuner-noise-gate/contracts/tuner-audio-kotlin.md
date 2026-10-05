# Contract: Tuner audio seam, noise gate additions (`:shared:engine`)

**Package**: `audio.tembra.engine` | **Depends on**: `:shared:core` | **Consumers**: `TunerViewModel`, the engine test lanes | **Extends**: `specs/003-instrument-tuner/contracts/tuner-audio-kotlin.md` (everything there stands)

Research R6, R13; data-model.md §3, §7.

```kotlin
interface TunerAudio : AudioStatus {
    // … existing members unchanged …

    /**
     * The level a sound must reach to be read, in dB below full scale; null means no gate. Allowed in every state
     * and kept by the engine across stops and starts, so a value set before [startListening] gates its first hop.
     * A level outside -120..0 is refused and the previous value stays.
     */
    fun setNoiseGate(openDb: Float?): AudioResult
}

class PitchSnapshot {
    // … existing fields unchanged …
    var inputLevelDb: Float = -120f   // the level the gate judges (window RMS after the pre-filter)
    var gateOpen: Boolean = true      // false while the gate holds the sound back
    // copyFrom copies both
}

/** Turns a level in the gate's own measure into the peak amplitude a [TestSignal] takes (data-model.md §7). */
object TestSignalLevel {
    fun amplitudeFor(levelDb: Float, harmonics: Int = 5, fundamentalGain: Float = 0.25f): Float
}

// TestSignals.catalogue gains, after the existing fourteen (order kept, 18 entries):
//   e2-70db, e2-55db, e2-45db, e2-30db   — TestSignal.Pitched(E2, 0.0, amplitude = TestSignalLevel.amplitudeFor(level))
//   expected = ExpectedReading.Note(E2, 0.0)   (the catalogue's expectation is always "with no gate")
```

## Implementations

| Type | Change |
|---|---|
| `NativeAudioEngine` (Android) | `setNoiseGate` → `nativeTunerSetGate(openDb != null, openDb ?: 0f)`; `readPitch` reads nine floats (`READING_FIELDS = 9`) and fills the two new fields |
| `NativeAudioEngine` (iOS) | `setNoiseGate` → `tembra_tuner_set_gate(if (openDb != null) 1u else 0u, openDb ?: 0f)`; `readPitch` copies `input_level_db` and `gate_open`. It does not touch the `AVAudioSession` |
| `FakeAudioEngine` | `var noiseGateDb: Float?` (last value set, initially null); the call is recorded as `setNoiseGate(off)` or `setNoiseGate(-50.0)` in `calls`; `nextSnapshot` carries the two new fields like the others |

No `expect`/`actual` declaration changes shape: `NativeAudioEngine` stays `expect class NativeAudioEngine : AudioEngine`
with the constructors it has.

## Tests (written first)

| Lane | File | Cases |
|---|---|---|
| `commonTest` | `FakeAudioEngineTest` | the call is recorded with its value; the last value is exposed; `readPitch` copies `inputLevelDb` and `gateOpen` |
| `commonTest` | `TestSignalLevelTest` (new) | the crest of the default mix is 2.18 ± 0.01; `amplitudeFor(-30f)` is 0.0690 ± 0.0005; 20 dB less level is a tenth of the amplitude |
| `commonTest` | `TestSignalsTest` | eighteen unique ids in order; the four new ones are in-tune E2 with the amplitudes of data-model.md §7 |
| Android device, iOS | `NativeAudioEngineTest` | ABI revision 4; `setNoiseGate` returns Ok for null, −50 and 0, and an argument error for 1 and −121; an idle engine reads `gateOpen == true`, `inputLevelDb == -120` |
| Android device, iOS | `NoiseGateEndToEndTest` (new) | for each gate in `[null, -60, -50, -40]` and each of the four level signals: set the gate, start the test signal, wait for twenty hops; assert the outcome table of data-model.md §7 (E2 within 1 cent, or no pitch), `gateOpen` equal to "reads", and `inputLevelDb` within 1 dB of the signal's level. Then one live change: start `e2-55db` with no gate (reads), set −50 (rests within 500 ms), set null (reads within 250 ms), with `audioState` Listening throughout (FR-003) |
| Android device, iOS | `TunerSignalEndToEndTest` | unchanged code; it now walks eighteen entries with no gate |
