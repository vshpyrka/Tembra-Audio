# Contract: `TunerAudio` (Kotlin engine facade)

**Module**: `:shared:engine`, package `audio.tembra.engine` | **Depends on**: contracts/engine-c-abi-v2.md | **Consumers**: `TunerViewModel`, tests

```kotlin
// TunerAudio.kt — the seam the tuner feature programs against (research R2). Every call is off the audio thread.
interface TunerAudio {
    fun startListening(config: TunerAnalysisConfig = TunerAnalysisConfig()): AudioResult
    /** Debug tooling (research R16): a generated tone in place of the microphone; same rules and state as startListening. */
    fun startTestSignal(signal: TestSignal, config: TunerAnalysisConfig = TunerAnalysisConfig()): AudioResult
    fun stopListening()
    /** Lock-free; safe to call every frame from the UI thread. Fills [into] without allocating. Also the watchdog heartbeat. */
    fun readPitch(into: PitchSnapshot)
    fun startTone(frequencyHz: Double, durationMs: Int): AudioResult     // 0 = loop until stopTone()
    fun loadSample(wavBytes: ByteArray): SampleHandle?                   // null on decode failure; decodes synchronously: callers use withContext(Dispatchers.Default)
    fun playSample(handle: SampleHandle, loop: Boolean): AudioResult
    fun unloadSample(handle: SampleHandle)
    fun stopTone()
    val audioState: AudioState                                           // Idle, Listening, Tone; read from the engine
    val events: Flow<AudioEvent>                                         // platform interruptions and route changes
}

// AudioEngine.kt — extended; describe() unchanged
interface AudioEngine : TunerAudio { fun describe(): EngineDescriptor }

// NativeAudioEngine.kt — no common constructor any more (research R15)
expect class NativeAudioEngine : AudioEngine
// androidMain: actual class NativeAudioEngine(context: Context) : AudioEngine   (AudioManager for transient focus around tones)
// iosMain:     actual class NativeAudioEngine() : AudioEngine                   (AVAudioSession configuration and notifications)

data class TunerAnalysisConfig(
    val windowFrames: Int = 4096, val hopFrames: Int = 1024, val minHz: Float = 50f, val maxHz: Float = 1400f,
    val clarityThreshold: Float = 0.5f, val peakRatio: Float = 0.9f, val medianSize: Int = 5, val watchdogSeconds: Float = 5f,
)
class PitchSnapshot { var sequence = 0L; var state = AudioState.Idle; var sampleRate = 0; var frequencyHz = 0f; var clarity = 0f; var inputPeak = 0f; var hasPitch = false; var synthetic = false }
enum class AudioState { Idle, Listening, Tone }
sealed interface AudioResult { data object Ok : AudioResult; data class Error(val code: Int) : AudioResult }   // code = tembra_error
value class SampleHandle(val id: Int)
sealed interface AudioEvent { data object Interrupted; data object InterruptionEnded; data object DeviceLost; data object OutputRouteLost }

// TestSignal.kt / TestSignals.kt (data-model.md §9)
data class TestSignal(val note: Note?, val cents: Double, val rawFrequencyHz: Double? = null, val amplitude: Float = 0.5f, val harmonics: Int = 5, val fundamentalGain: Float = 0.25f) {
    val frequencyHz: Double   // rawFrequencyHz ?: note.frequencyHz() * 2^(cents / 1200)
}
data class TestSignalEntry(val id: String, val signal: TestSignal, val expected: ExpectedReading)   // ExpectedReading = Note(note, cents) | Resting
object TestSignals { val catalogue: List<TestSignalEntry>; fun byId(id: String): TestSignalEntry? }
```

**Platform behaviour inside the actuals (research R3, R7)**

| | Android | iOS |
|---|---|---|
| `startTestSignal` | `nativeTunerStartTestSignal(...)`; no focus, no session (no hardware is touched) | `tembra_tuner_start_test_signal`; no session activation |
| `startListening` | `nativeTunerStart(...)`; no audio focus (capture has none) | `AVAudioSession`: category `playAndRecord`, mode `measurement`, `preferredSampleRate` 48 000, `preferredIOBufferDuration` 0.005, `setActive(true)`; then `tembra_tuner_start` |
| `stopListening` | `nativeTunerStop()` | `tembra_tuner_stop`; `setActive(false, notifyOthersOnDeactivation)` |
| `startTone` / `playSample` | request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (`AudioFocusRequest`, usage `MEDIA`, content `MUSIC`); focus loss → `stopTone()` + `Interrupted`; then `nativeToneStart` / `nativeSamplePlay` | category `playback`, `setActive(true)`; then `tembra_tone_start` / `tembra_sample_play` |
| `stopTone` | `nativeToneStop()`; abandon focus | `tembra_tone_stop`; `setActive(false, notifyOthersOnDeactivation)` |
| `events` | focus listener → `Interrupted` (loss) / `InterruptionEnded` (gain after transient loss); engine `DeviceLost` polled from `nativeAudioState` dropping to `Idle` while a start was requested is reported by the ViewModel, not here | `AVAudioSessionInterruptionNotification` → `Interrupted` / `InterruptionEnded` (when `shouldResume`); `AVAudioSessionRouteChangeNotification` with `oldDeviceUnavailable` → `OutputRouteLost` |
| `readPitch` | `nativeTunerRead(floatArray)` into a per-engine `FloatArray(6)` then copied into the snapshot | `tembra_tuner_read(reading.ptr)` then copied |

**`FakeAudioEngine`** (`commonMain`, test double): records every call in order (`calls: List<String>`),
`audioState` follows the calls, `nextSnapshot` (settable) is what `readPitch` copies, `readCount`
counts heartbeats, `emit(event)` pushes into `events`, `failNextStart: AudioResult.Error?` simulates
`TEMBRA_ERR_DEVICE`, `simulateWatchdog()` flips state to `Idle`. `loadSample` returns increasing handles unless `wavBytes` is empty.
`startTestSignal(signal)` records `"startTestSignal"`, keeps `lastTestSignal`, sets `Listening` and marks `nextSnapshot.synthetic = true`.

**Tests**: `FakeAudioEngineTest`, `TestSignalsTest` (`commonTest`: fourteen catalogue ids, unique, `e2-flat-20.signal.frequencyHz` within 0.01 Hz of 81.46, `out-of-range-2khz` has no note); `TunerSignalEndToEndTest` (`androidDeviceTest`, `iosTest`): for every catalogue entry `startTestSignal`, poll `readPitch` every 50 ms until `sequence >= 10` (fail after 3 s), assert `synthetic`, `state == Listening`, and `frequencyHz` within 1 cent of the expected note's offset frequency or `hasPitch == false` for the resting entry; `stopListening` returns the engine to `Idle`; `AndroidAudioFocusTest` (`androidDeviceTest`: `request()` returns true on an emulator or device without another focus holder, `abandon()` does not throw); `IosAudioSessionTest` (`iosTest`: `activateForListening()`, `activateForPlayback()` and `deactivate()` each return true on the simulator); `NativeAudioEngineTest` (`androidDeviceTest`, `iosTest`): `describe()` reports version 0.2.0 and ABI 2, tone start/stop round trip changes
`audioState` and never throws, `readPitch` on an idle engine returns `hasPitch = false` and
`state = Idle`, `loadSample` of an invalid byte array returns null, listening start returns
`Ok` when the runner holds `RECORD_AUDIO` and `Error(TEMBRA_ERR_DEVICE)` otherwise (the test
asserts one of the two and records which).
