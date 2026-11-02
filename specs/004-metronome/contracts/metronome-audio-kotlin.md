# Contract: `MetronomeAudio` (Kotlin engine facade)

**Module**: `:shared:engine`, package `audio.tembra.engine` | **Depends on**: contracts/engine-c-abi-v3.md | **Consumers**: `MetronomeViewModel`, tests

```kotlin
// AudioStatus.kt — the two members every audio seam shares (lifted from TunerAudio; TunerAudio's contract is unchanged for its callers)
interface AudioStatus {
    val audioState: AudioState                  // Idle, Listening, Tone, Metronome; read from the engine
    val events: Flow<AudioEvent>                // platform interruptions and route changes; no replay, one collector expected
}
interface TunerAudio : AudioStatus { /* as feature 003 */ }

// MetronomeAudio.kt — the seam the metronome programs against. Every call is off the audio thread.
interface MetronomeAudio : AudioStatus {
    fun setGrid(grid: MetronomeGrid): AudioResult                     // any state; non-blocking publish
    fun selectSound(sound: ClickSoundId): AudioResult
    /** Decodes and prepares synchronously; callers use withContext(Dispatchers.Default). Publishes on success. */
    fun loadCustomSound(path: String): CustomSoundResult
    fun unloadCustomSound()
    fun startMetronome(): AudioResult                                 // platform focus / session inside the actuals; beat 1 at once
    fun stopMetronome()                                               // idempotent; abandons focus / deactivates the session
    fun previewClick(level: ClickLevel = ClickLevel.Click): AudioResult   // Idle only
    fun stopTone()                                                    // ends a sounding preview click (same member as TunerAudio.stopTone); idempotent
    /** A positive value is the output latency to use; 0 = measure the current route again (Android: the device; iOS: the session). */
    fun reportOutputLatencyNanos(nanos: Long)
    /** Lock-free; safe to call every frame from the UI thread. Fills [into] without allocating. */
    fun readTick(into: TickSnapshot)
}

// AudioEngine.kt — extended; describe() unchanged
interface AudioEngine : TunerAudio, MetronomeAudio { fun describe(): EngineDescriptor }

// NativeAudioEngine.kt — unchanged shape
expect class NativeAudioEngine : AudioEngine
// androidMain: actual class NativeAudioEngine(context: Context) : AudioEngine
// iosMain:     actual class NativeAudioEngine() : AudioEngine

enum class AudioState { Idle, Listening, Tone, Metronome }
sealed interface AudioEvent { data object Interrupted; data object InterruptionEnded; data object DeviceLost; data object OutputRouteLost }   // unchanged set; new sources below
data class MetronomeGrid(val tempo: Tempo, val ticksPerBeat: Int, val levels: List<ClickLevel>) { companion object { fun from(grid: TickGrid, tempo: Tempo): MetronomeGrid } }
enum class ClickLevel { Sub, Click, Accent }
enum class ClickSoundId(val native: Int) { ClassicWood(0), Rim808(1), DigitalBeep(2), Cowbell(3), Mechanical(4), Custom(5) }
class TickSnapshot { var tickIndex = 0L; var beatIndex = 0; var level = ClickLevel.Click; var audibleInNanos = 0L; var beatPeriodNanos = 0L; var running = false; var sampleRate = 0 }
sealed interface CustomSoundResult { data class Loaded(val info: CustomSoundInfo); data object NotWav; data object Silent; data object Missing }
```

**Platform behaviour inside the actuals** (research R6; feature 003's precedent for tones)

| | Android | iOS |
|---|---|---|
| `startMetronome` | `AndroidAudioFocus.request(FocusType.Gain)` (`AUDIOFOCUS_GAIN`, `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`, `setWillPauseWhenDucked(false)`); denied → `AudioResult.Error(FOCUS_DENIED = -100)` without touching the engine; register `BecomingNoisyReceiver`; then `nativeMetronomeStart()`; on engine error abandon focus and unregister | `IosAudioSession.activateForPlayback()` (`.playback`, `.default`, no options; `setActive(true)`); `reportOutputLatencyNanos(session.outputLatency + ioBufferDuration)`; then `tembra_metronome_start`; on error deactivate |
| `stopMetronome` | `nativeMetronomeStop()`; unregister the receiver; `abandonAudioFocusRequest` | `tembra_metronome_stop`; `setActive(false, notifyOthersOnDeactivation)` |
| `previewClick` | only when `audioState == Idle`: transient focus as for tones (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), then `nativeMetronomePreview`; the actual then checks `nativeAudioState()` on the main thread every 100 ms (at most 3 s, a custom click lasts up to 2 s) and, once the state is back to `Idle`, closes the output stream the preview opened (`nativeToneStop()`, the engine's documented end of a preview) and abandons the transient focus | only when `audioState == Idle`: `activateForPlayback`, then `tembra_metronome_preview`; the actual then checks `tembra_audio_state()` on the main queue every 100 ms (at most 3 s, a custom click lasts up to 2 s) and, once the state is back to `Idle`, closes the output unit the preview opened (`tembra_tone_stop()`; a running unit makes `setActive(false)` fail as busy) and deactivates (`notifyOthersOnDeactivation`) |
| `events` | focus listener: `AUDIOFOCUS_LOSS` and `AUDIOFOCUS_LOSS_TRANSIENT` → `Interrupted` (focus abandoned by the ViewModel's stop); `…_CAN_DUCK` ignored (system ducks); `AUDIOFOCUS_GAIN` after a transient loss → `InterruptionEnded` (the ViewModel ignores it for the metronome); `BecomingNoisyReceiver` → `OutputRouteLost` | interruption `.began` → `Interrupted`; `.ended` → `InterruptionEnded` (ignored by the metronome); route change `.oldDeviceUnavailable` → `OutputRouteLost` unless the previous route's outputs were only `builtInSpeaker` / `builtInReceiver` (wired, USB-C, Bluetooth, AirPlay, CarPlay and line-out losses all stop the click, as Android's becoming-noisy broadcast does); `.newDeviceAvailable` → nothing (the click continues); `outputLatency` re-reported on every route change while running |
| `reportOutputLatencyNanos` | `nativeMetronomeSetOutputLatencyNs(nanos)`; 0 = measure the current route again (the engine asks the Oboe stream) | a positive value is forwarded; 0 = measure the current route again: `IosAudioSession.outputLatencyNanos()` is forwarded instead, so a refresh never wipes the compensation |
| `readTick` | `nativeMetronomeRead(longArray)` into a per-engine `LongArray(7)` then copied into the snapshot | `tembra_metronome_read(tick.ptr)` then copied |
| `loadCustomSound` | `nativeMetronomeLoadCustomSound(path, intArray, floatArray)`; `-4` → `NotWav`, `-5` → `Silent`, `-3` → `Missing` | `tembra_metronome_load_custom_sound(path, info.ptr)` with the same mapping |

`AndroidAudioFocus` gains `enum class FocusType { TransientMayDuck, Gain }`; the tuner keeps
`TransientMayDuck`. Platform resources are acquired only by the call that moves the engine out
of `Idle` and released only by the call that returns it there: a refused start, tone or preview
(the engine answers `TEMBRA_ERR_STATE`) MUST NOT abandon focus, unregister the receiver or
deactivate the session of the stream that is running. The receiver is registered with
`RECEIVER_NOT_EXPORTED` on API 33+ and without a flag below (no androidx.core dependency). `BecomingNoisyReceiver` (androidMain) is a `BroadcastReceiver` for
`AudioManager.ACTION_AUDIO_BECOMING_NOISY` registered with `ContextCompat.registerReceiver`
(`RECEIVER_NOT_EXPORTED`) only while the metronome runs.

**`FakeAudioEngine`** (`commonMain`, extended): records `"setGrid(bpm=120, ticks=4, levels=ACCC)"`,
`"selectSound(Rim808)"`, `"loadCustomSound(path)"`, `"startMetronome"`, `"stopMetronome"`,
`"previewClick(Click)"`, `"reportOutputLatencyNanos(n)"`; `audioState` follows the calls
(`Metronome` after a successful start); `nextTick` (settable `TickSnapshot`) is what `readTick`
copies; `customSoundResult: CustomSoundResult` (settable, default `Loaded`) is what
`loadCustomSound` returns; `failNextStart` applies to `startMetronome` too; `emit(event)`
pushes into `events`.

**Tests**: `FakeAudioEngineTest` (`commonTest`: recording, state, tick copy, custom result);
`NativeAudioEngineTest` (`androidDeviceTest`, `iosTest`): `describe()` reports version 0.3.0 and
ABI 3; `setGrid` with a valid grid returns `Ok` and an invalid one `Error(-3)`; metronome start/stop
round trip changes `audioState` and never throws; `readTick` on an idle engine returns
`running = false`; `loadCustomSound("/nonexistent.wav")` returns `Missing`; `previewClick` while
idle returns `Ok`; after a preview the release check closes the stream (Android: no started media player remains) and releases focus / the session; iOS `reportOutputLatencyNanos(0)` forwards the session's latency. `MetronomeTimingEndToEndTest` (`androidDeviceTest`, `iosTest`): grid 120 BPM
× 1, start, poll `readTick` every 20 ms for five seconds, assert `tickIndex` increases by one at a
time and the wall-clock spacing between successive `audibleInNanos` crossings is 500 ms ± 1 ms
(measured on the monotonic clock), then stop. `AndroidAudioFocusTest` (+ `Gain` request granted and
abandoned; an injected `AUDIOFOCUS_LOSS` reaches the listener), `BecomingNoisyReceiverTest`
(`androidDeviceTest`: sending the broadcast emits `OutputRouteLost`), `IosAudioSessionTest`
(+ `activateForPlayback` then `outputLatencyNanos() > 0` on the simulator; an injected
interruption notification emits `Interrupted`; an injected route-change notification with a
headphones, USB audio or AirPlay previous route emits `OutputRouteLost`, one with only the built-in speaker or receiver does not).

Threading since feature 010 (T103): `MetronomeViewModel` calls `startMetronome`, `stopMetronome`, `stopTone` (before a
start) and `previewClick` on its `audioDispatcher` (`EngineDispatchers.blocking` by default, the engine's one serial
dispatcher shared with the tuner and the transport), never on the main thread: the session activation and the stream open
took about 100 ms on an iPhone 14 and dropped frames on the Play tap. The screen shows Starting and Stopped at once; a
pause that lands during a start queues its stop behind the start on the same dispatcher.
