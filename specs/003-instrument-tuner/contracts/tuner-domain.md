# Contract: Tuner domain (`:shared:core`)

**Package**: `audio.tembra.core.tuner` | **Depends on**: nothing (pure Kotlin, Constitution II) | **Consumers**: `:shared:data`, `:shared:ui`, tests on JVM and iOS simulator

```kotlin
enum class Instrument(val id: String, val label: String, val hasStringCount: Boolean) { Guitar("guitar", "Guitar", true), Cello("cello", "Cello", false) }
enum class GuitarStringCount(val count: Int, val label: String) { Six(6, "6-String"), Seven(7, "7-String") }
enum class PitchClass(val label: String) { C("C"), CSharp("C#"), D("D"), DSharp("D#"), E("E"), F("F"), FSharp("F#"), G("G"), GSharp("G#"), A("A"), ASharp("A#"), B("B") }

data class Note(val pitchClass: PitchClass, val octave: Int) {
    val midi: Int                                   // (octave + 1) * 12 + pitchClass.ordinal
    val name: String                                // "F#3"
    val resourceName: String                        // "Fsharp3" (research R5 naming rule)
    fun frequencyHz(a4Hz: Double = PitchMath.A4_HZ): Double
    companion object { fun fromMidi(midi: Int): Note; fun parse(name: String): Note }   // parse accepts "F#3" only (sharps)
}
data class TargetString(val numeral: String, val note: Note) { val frequencyHz: Double }
data class TuningPreset(val id: String, val name: String, val instrument: Instrument, val guitarStrings: GuitarStringCount?, val notes: List<Note>) {
    val strings: List<TargetString>                 // lowest first; numerals count down to "I" for the highest
    val summary: String                             // "E2 A2 D3 G3 B3 E4"
}
object TuningPresets {
    val all: List<TuningPreset>                     // exactly the eleven rows of spec FR-003 / data-model.md §1, in order
    fun forInstrument(instrument: Instrument, guitarStrings: GuitarStringCount?): List<TuningPreset>
    fun default(instrument: Instrument, guitarStrings: GuitarStringCount?): TuningPreset
    fun byId(id: String): TuningPreset?
}
enum class TunerMode(val id: String, val label: String) { AutoListen("autoListen", "Auto Listen"), ByEar("byEar", "By Ear") }
enum class PitchDirection { Flat, InTune, Sharp }
data class NoteReading(val note: Note, val cents: Double) { val direction: PitchDirection; val isInTune: Boolean; val referenceHz: Double }
object PitchMath {
    const val A4_HZ = 440.0; const val IN_TUNE_CENTS = 3.0; const val METER_RANGE_CENTS = 50.0; const val MIN_HZ = 50.0; const val MAX_HZ = 1400.0
    fun midiFromHz(hz: Double, a4Hz: Double = A4_HZ): Double         // 69 + 12 log2(hz / a4)
    fun nearest(hz: Double, a4Hz: Double = A4_HZ): NoteReading      // n = ceil(midi - 0.5); cents = 100 (midi - n) ∈ (-50, 50]
}
object RomanNumerals { fun of(value: Int): String }                 // 1..7 → I..VII
```

**Rules verified by tests (`commonTest`, JVM and iOS)**
- `NoteTest`: `Note(E, 2).midi == 40`, `frequencyHz()` within 0.01 Hz of 82.41; `fromMidi(69) == A4`;
  `parse("F#3").resourceName == "Fsharp3"`; `parse("Eb2")` throws (sharps only).
- `PitchMathTest`: every preset string maps to its own note with 0 cents; 82.41 × 2^(20/1200) → E2, +20 ± 0.01 cents;
  the exact midpoint between E2 and F2 → E2 at +50; one cent above the midpoint → F2 at −49;
  `isInTune` true at ±3.0 and false at ±3.01; 55.0 Hz → A1; 659.26 Hz → E5.
- `TuningPresetsTest`: eleven presets; ids unique; `forInstrument(Guitar, Six)` has seven entries
  starting with `guitar6-standard`; `forInstrument(Cello, null)` has two; every preset's `notes.size`
  equals its string count; numerals of `guitar7-standard` are `VII … I` with `I` = E4; every
  note and one-decimal frequency equals the data-model table; `default(Cello, null).id == "cello-standard"`.
- `RomanNumeralsTest`: 1..7.
