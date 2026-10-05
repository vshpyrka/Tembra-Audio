# Contract: Tuner preferences, noise gate addition (`:shared:data`)

**Package**: `audio.tembra.data.tuner` | **Depends on**: `:shared:core` (`NoiseGate`), DataStore Preferences 1.2.1 | **Consumers**: `TunerViewModel` | **Extends**: `specs/003-instrument-tuner/contracts/tuner-preferences.md`

Research R7, R12; data-model.md §1, §4.

```kotlin
object TunerPreferenceKeys {
    // … the five existing keys …
    const val NOISE_GATE_DB = "tuner.noiseGateDb"
}

data class TunerPreferencesSnapshot(
    // … the five existing fields …
    val noiseGate: NoiseGate = NoiseGate.Off,
) {
    /** The keys as strings; the gate's key is present only when a level is set. */
    fun encode(): Map<String, String>

    companion object {
        fun decode(values: Map<String, String?>, loopTone: Boolean?, noiseGateDb: Int?): TunerPreferencesSnapshot
    }
}
```

`TunerPreferences`, `FakeTunerPreferences` and `TembraDataStore` keep their shape.

## Rules

- **Write**: `DataStoreTunerPreferences.update` stores `intPreferencesKey("tuner.noiseGateDb")` as
  `noiseGate.levelDb` when a level is set and **removes the key** when the gate is off.
- **Read**: `noiseGate = NoiseGate.fromStored(preferences[noiseGateDb])`. An absent key (a fresh install, or an
  update from a version without the gate), a value outside the scale and −80 itself all read as off; a value between
  two steps reads as the quieter step (spec FR-014).
- The five existing keys and their decoding are untouched; a file written before this feature decodes to the same
  five values plus `NoiseGate.Off`.

## Tests (written first)

| Lane | File | Cases |
|---|---|---|
| `commonTest` (`:shared:core`) | `NoiseGateTest` (new) | the examples of data-model.md §1 for `fromStored` and `ofLevelDb`; `ofStep` coerces; `levelDb`, `fraction` and `percent` at steps 0, 1, 15, 30; `fractionOfLevel` at −100, −80, −50, −20 and 0; the check levels are steps 10, 15 and 20 |
| `commonTest` (`:shared:data`) | `TunerPreferencesSnapshotTest` | default is off; `decode` with null, −50, −51, −19 and −80; `encode` omits the key when off and carries "-50" otherwise; the five existing fields round-trip as before |
| `commonTest` (`:shared:data`) | `FakeTunerPreferencesTest` | an update of `noiseGate` is emitted |
| Android device, iOS | `DataStoreTunerPreferencesTest` | write −50, read it back, reopen the file and read again; write off and the key is gone; a file with only the five old keys reads off; a stored 7 reads off |
