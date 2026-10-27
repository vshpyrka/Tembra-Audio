# Contract: `TimelineAudio` — the Kotlin seam over ABI revision 6, actuals, fake, background host

**Where**: `:shared:engine` (`audio.tembra.engine`), the two `NativeAudioEngine` actuals,
`FakeAudioEngine`; the background host in `:shared:ui/platform` and `:androidApp`. Research R2,
R7, R12; data model §3; the metronome's `MetronomeAudio` contract is the model.

## Interface

```kotlin
/** The transport and timeline half of the engine. Main-thread entry points; suspend members hop to the IO dispatcher. */
interface TimelineAudio : AudioStatus {
    suspend fun openTransport(): TransportOpenResult          // Opened | Busy(AudioState) | DeviceFailed
    fun closeTransport()
    suspend fun openClipSource(path: String): ClipSourceResult // Opened(handle, info) | Undecodable
    fun closeClipSource(handle: ClipSourceHandle)
    fun setTempo(tempo: Tempo, signature: TimeSignature)
    fun setArrangement(snapshot: ArrangementSnapshot)
    fun setTrack(index: Int, gainLinear: Float, audible: Boolean)
    fun setMaster(gainLinear: Float)
    fun setMetronome(enabled: Boolean)                         // the grid goes through MetronomeAudio.setGrid, as the Metronome destination does
    suspend fun play(): PlayResult                             // Started | Failed(reason) — suspends for the prime (< 50 ms)
    fun pause()
    suspend fun seek(beats: Beats)
    fun readTransport(into: TransportSnapshot)                 // lock-free; call from draw lambdas; main thread only (one reused buffer)
    suspend fun inspect(path: String): AudioInfo?              // offline
    suspend fun buildOverview(audioPath: String, overviewPath: String): Boolean  // offline, long; IO dispatcher
}
```

`AudioEngine : TunerAudio, MetronomeAudio, TimelineAudio`. `AppBindings` adds
`provideTimelineAudio(engine: AudioEngine): TimelineAudio = engine` like the other two seams.

`AudioStatus.events` gains nothing new: `Interrupted`, `InterruptionEnded`, `FocusLost`,
`OutputRouteLost`, `Watchdog` already exist; the session's policy consumes them (R12).

## Actuals

- **Android** (`NativeAudioEngine.android.kt`): the arrangement is marshalled into one
  direct `ByteBuffer` (`ArrangementBuffer`, native order, the C layout) reused per instance and
  passed to `nativeTransportSetArrangement`; `readTransport` fills a `LongArray(4)`/`DoubleArray(2)`
  pair through one JNI call. `openTransport` requests `AUDIOFOCUS_GAIN` (reusing
  `AndroidAudioFocus` with `FocusType.Gain`) and registers the `BecomingNoisyReceiver`; focus
  loss emits `FocusLost`, transient loss `Interrupted`. `closeTransport` abandons focus.
- **iOS** (`NativeAudioEngine.ios.kt`): `IosAudioSession.activateForPlayback()` before
  `tembra_transport_open`; reports `outputLatency + ioBufferDuration` through
  `tembra_metronome_set_output_latency_ns` (shared by both renderers); interruptions and route
  changes map as today.
- Both: `inspect` and `buildOverview` run on `Dispatchers.IO`; `openClipSource` too (MP3 seek
  table generation can take hundreds of milliseconds for a long file).

## `FakeAudioEngine` additions

Records `"openTransport"`, `"openClipSource(<path>)"`, `"setArrangement(tracks=N,clips=M)"`,
`"setTrack(i,g,a)"`, `"setMaster(g)"`, `"setMetronome(b)"`, `"play"`, `"pause"`,
`"seek(<beats>)"`, `"closeTransport"`; scriptable `inspectResults: Map<String, AudioInfo?>`,
`clipSourceFailures`, `failNextPlay`, `transportScript: (frameNanos) -> TransportSnapshot`
(for the ScriptedTransportTelemetry in UI tests), `overviewWriter` (writes a tiny valid `.peaks`
into the fake filesystem so the UI path is exercised). `audioState` moves to `Transport` on
`openTransport` and back to `Idle` on `closeTransport`.

## Background playback host (generalised from feature 004)

`BackgroundPlaybackHost` keeps its interface (`keepAlive()`, `release()`, `stopRequests`). The
Android implementation's service is renamed `PlaybackForegroundService` with a
`PlaybackNotification(title, subtitle)` payload (`"Playing"`, `"<project name>"`;
`"Metronome"`, `"<tempo> BPM"`) and keeps the Stop action; manifest entries unchanged except the
class name. The metronome's call sites pass their payload; its tests are updated for the rename
only. iOS stays the no-op host; `UIBackgroundModes audio` already covers the transport.

## Device/simulator tests (first)

- `NativeAudioEngineTransportTest` (Android device, iOS simulator): open, open two generated WAV
  sources, set a two-clip arrangement, play, read positions advancing, seek, pause, close; the
  state exclusivity with the tuner and the standalone metronome; `inspect` on the three formats;
  `buildOverview` writes a valid header.
- `TransportTimingEndToEndTest`: with the real engine and a loopback-free check, the
  `audible_seconds` readout advances within 2 % of wall time over 10 s.
- `AndroidAudioFocusTest` (+ Gain for the transport), `BecomingNoisyReceiverTest` unchanged.
- `IosAudioSessionTest`: interruption injection while `Transport` emits `Interrupted`.
