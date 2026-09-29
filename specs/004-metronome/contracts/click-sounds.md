# Contract: Click sounds, the custom WAV import and the file picker

**Modules**: `engine/` (recipes, preparation), `:shared:ui` (`audio.tembra.ui.platform.SoundFilePicker`), `:androidApp` and `:shared:ui` `iosMain` (pickers) | **Research**: R3, R4, R5 | **Spec**: FR-018 to FR-025

## Built-in sounds (engine, `ClickSynth`)

| Id | Name | Click recipe (peak −9 dBFS) | Accent (peak −3 dBFS) | Sub (peak −17 dBFS) |
|---|---|---|---|---|
| 0 | Classic Wood | band-passed noise 2–5 kHz, 8 ms, + damped sine 2.2 kHz, 25 ms | tonal partial × 1.5 | same as Click, −8 dB |
| 1 | 808 Rim | partial pair 1.7 kHz / 2.55 kHz, 12 ms, + 1 ms noise transient | partials × 1.5 | −8 dB |
| 2 | Digital Beep | sine 1 000 Hz, 40 ms | sine 1 500 Hz | −8 dB |
| 3 | Cowbell | square partials 587 Hz and 845 Hz, band-passed 400–2 000 Hz, 90 ms | partials × 1.5 | −8 dB |
| 4 | Mechanical | filtered noise 4 ms + 3 kHz resonance 15 ms | resonance × 1.5 | −8 dB |

Every sample: 1–2 ms raised-cosine attack, exponential decay, last 2 ms ramp to exactly zero
(brief §3.4). Rendered at the stream rate when the output first opens; ≤ 120 ms each.

## Custom sound (engine, `CustomSoundLoader` + `ClickSampleBuilder`; data-model.md §4)

Input: the app's copy at `CustomClickSoundStore.path`. Pipeline: dr_wav decode of the first 30 s
(PCM 8/16/24/32 int, 32 float; any channels; 8–192 kHz) → mono → onset (2 ms RMS ≥ −40 dB re
peak, backed 1 ms, 1 ms fade-in) → keep ≤ 2 000 ms → 5 ms fade-out → Click peak −9 dBFS → Accent
+6 dB (limit −1 dBFS), Sub −8 dB → resample to the stream rate → publish `ClickSet`. Refusals:
`NotWav` (dr_wav cannot open), `Silent` (peak < −60 dBFS), `Missing` (path cannot be opened).
`tembra_custom_sound_info` reports what was kept; the UI shows it nowhere in this feature
(available for the plan's verification notes and future settings).

## Picker (`:shared:ui`, `audio.tembra.ui.platform`)

```kotlin
interface SoundFilePicker {
    /** Presents the platform picker for WAV files and copies the chosen file to [destinationPath]. Suspends until the user picks or cancels. */
    suspend fun pickWav(destinationPath: String): SoundPickResult
}
sealed interface SoundPickResult { data class Picked(val displayName: String) : SoundPickResult; data object Cancelled; data class Failed(val reason: SoundPickFailure) }
enum class SoundPickFailure { TooLarge /* > 64 MB */, Unreadable }
class FakeSoundFilePicker(var next: SoundPickResult = Cancelled) : SoundFilePicker   // commonTest; records destinations
```

| | Android (`:androidApp`) | iOS (`:shared:ui` `iosMain`) |
|---|---|---|
| Implementation | `AndroidSoundFilePicker(host: SoundFilePickerHost, context)`; `SoundFilePickerHost` holds the `ActivityResultLauncher<Array<String>>` registered in `MainActivity` with `ActivityResultContracts.OpenDocument()` and attached/detached like `MicrophonePermissionHost`; `pickWav` launches with `arrayOf("audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/wave", "application/octet-stream")` and suspends on a `CompletableDeferred`; on a URI it copies `contentResolver.openInputStream(uri)` to the destination on `Dispatchers.IO` (refusing above 64 MB) and returns the `OpenableColumns.DISPLAY_NAME` without extension | `IosSoundFilePicker()`; presents `UIDocumentPickerViewController(forOpeningContentTypes: [UTType.wav], asCopy = true)` from the key window's root controller, suspends on the delegate; copies the returned temporary URL to the destination with `NSFileManager` (refusing above 64 MB) and returns `lastPathComponent` without extension |
| Cancellation | `Cancelled` when the result is null | `Cancelled` from `documentPickerWasCancelled` |

The ViewModel's `importCustomSound()` (contracts/metronome-screen.md) calls
`picker.pickWav(store.stagingPath)` → `store.commit()` → `audio.loadCustomSound(store.path)` on
`prepareDispatcher` → on `Loaded` persists `sound = Custom(name)`, `customSoundPath`; on a refusal
keeps the previous sound, shows the message and deletes the staging file. `replaceCustomSound()`
is the same flow from the filled Custom entry.

**Copy** (`MetronomeCopy`): `NotWav` "That file isn't a WAV file Tembra can read.", `Silent` "That
file has no sound in it.", `TooLarge` "That file is too large (64 MB at most).", `ImportFailed`
"The file couldn't be read.", `CustomSoundMissing` "Your custom sound is missing, so Classic Wood
is playing.", `PlaybackRefused` "Another app is using the audio right now."

**Tests**: engine tests in contracts/engine-c-abi-v3.md; `FakeSoundFilePicker` in
`MetronomeViewModelTest`; the Android picker is exercised by `AndroidBackgroundPlaybackHostTest`'s
sibling `AndroidSoundFilePickerTest` (`androidTest`: a URI to a test asset copies the bytes and
returns the display name; a 65 MB asset returns `TooLarge`); the iOS picker's copy step is
covered by `IosSoundFilePickerTest` (`iosTest`: `copyPicked(url, destination)` on a temp file).
