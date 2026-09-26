# Contract: Reference sounds and drop-in recordings

**Module**: `:shared:ui`, package `audio.tembra.ui.tuner` (resolver) and the engine (playback) | **Spec**: FR-018, FR-019, FR-022 | **Research**: R5

**Where recordings go** (documented in `app/README.md` and here): `app/shared/ui/src/commonMain/composeResources/files/tuner/<instrument>-<note>.wav`

| Part | Rule |
|---|---|
| `<instrument>` | `guitar` or `cello` (`Instrument.id`) |
| `<note>` | the sharp-spelled note name with `#` written as `sharp`: `E2`, `Fsharp3`, `Asharp3` (`Note.resourceName`) |
| format | WAV, PCM 16/24/32-bit integer or 32-bit float, mono or stereo (mixed to mono), any sample rate (resampled to the stream rate) |
| pitch | the string's target pitch within 1 cent; checked by `recordings_on_pitch_test` |
| length | at least 1 s; looping crossfades the last 10 ms into the first |

Examples: `guitar-E2.wav` replaces the low E of every guitar preset that contains E2; `cello-C2.wav`
replaces the cello C string. A note without a file plays the synthesized tone. No file ships in this feature.

```kotlin
sealed interface ReferenceSound {
    data class Synth(val frequencyHz: Double) : ReferenceSound
    data class Recording(val resourceName: String, val bytes: ByteArray) : ReferenceSound
}
fun interface ResourceReader { suspend fun readOrNull(path: String): ByteArray? }        // ResourceReader.kt
object ComposeResourceReader : ResourceReader                                            // ComposeResourceReader.kt, its own file (Constitution VII): Res.readBytes in try/catch (MissingResourceException → null)
class ReferenceSoundRepository(private val reader: ResourceReader) {
    suspend fun resolve(instrument: Instrument, note: Note): ReferenceSound              // path "files/tuner/${instrument.id}-${note.resourceName}.wav"; result cached per path (hit or miss)
}
```

**ViewModel mapping** (`TunerViewModel.audition(index)`): resolve → `Synth` → `startTone(hz, if (loopTone) 0 else 2000)`;
`Recording` → `loadSample(bytes)` under `withContext(Dispatchers.Default)` (the engine decodes synchronously; handle cached per `resourceName` for the ViewModel's life,
unloaded in `onCleared` and when the instrument changes) → `playSample(handle, loopTone)`.
A one-shot tone or recording ends by itself: the 10 Hz poll sees `audioState == Idle` and clears
`audition` (card returns to "Audition", spec FR-019).

**Tests**: `ReferenceSoundRepositoryTest` (`commonTest`, fake reader): hit → `Recording` with the
bytes, miss → `Synth` with the note's frequency, second call does not re-read, `Fsharp3` naming;
`TunerViewModelTest` covers the mapping with `FakeAudioEngine`; `recordings_on_pitch_test` (Catch2)
scans the directory and checks each file (vacuous pass with a message when empty) and proves
decode, resample and loop with a synthetic WAV it writes itself.
