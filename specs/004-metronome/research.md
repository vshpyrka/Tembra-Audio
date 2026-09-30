# Research: Metronome (feature 004)

**Spec**: [spec.md](spec.md) (clarified 2026-09-29) | **Constitution**: v1.2.0 | **Date**: 2026-09-29

Every decision below cites its evidence: the metronome research brief
(`docs/research/metronome-design.md`, "brief" from here on), the consolidated report
(`docs/DAW-App-Research-Report.md` §5, "report"), the NotebookLM deep report
(`docs/research/notebooklm-deep-report-en.md` §4–§6, "NotebookLM"), the stack decisions
(`docs/research/kmp-code-architecture.md`, `kmp-architecture-tools.md`), the official platform
documentation fetched on 2026-09-29 (quoted where a rule matters), the three Stitch metronome
screens, and the feature 003 contracts this feature extends. The constitution's v1.2.0 amendment
binds the shape of the reusable parts (R2, R3, R11).

## R1. Where the click is timed: a frame counter in the audio callback

**Decision**: the click is scheduled by the C++ engine inside the output callback, model (A) of
the brief §2.2: the renderer keeps an absolute output-frame counter `framePos`, a
`double` `lastTickFrame` (the unrounded position of the last emitted tick) and derives
`nextTickFrame = lastTickFrame + framesPerTick` every block from the *current* tempo, rounding
only at emit time (`llround`). `framesPerTick = 60.0 / bpm × sampleRate / ticksPerBeat`; the
tempo counts the beat named by the signature's lower number (spec FR-002, clarified 2026-09-29),
so no `den / 4` factor is applied. Ticks whose rounded position falls inside the current block
start a voice at that offset; the accumulator never advances by "previous + interval" in
integers, so long-term error stays under half a sample (brief §4, report §5).

**Tempo, grid and sound changes** (spec FR-013): the control thread publishes an immutable grid
snapshot (R2); the callback loads it once per block. Because `nextTickFrame` is recomputed from
`lastTickFrame` with the live tempo, a tempo change reaches the very next tick unless that tick
already lies inside the block being rendered, which is the brief's "never move a click already
in the current block". Sound changes swap the click set pointer and apply to the next voice.
Grid changes map the position rather than nudging it: a change of beats per bar restarts the
bar at the next beat boundary (that beat becomes beat 1); a change of ticks per beat keeps the
beat index and lands the next tick on the next beat boundary; a change of levels only keeps
everything. The beat boundary is `nextBeatFrame = lastBeatFrame + framesPerBeat`, tracked in the
same double domain.

**Start and stop** (spec FR-011): `start` resets `tickIndex = 0`, `lastTickFrame = −framesPerTick`
relative to the first block so the first tick (beat 1, Accent) lands at frame 0 of the first
rendered block; the stream opens through the existing `AudioDevice::openOutput` of feature 003, so
beat 1 sounds within the stream's start latency (well under 100 ms on both platforms). `stop`
arms a 5 ms raised-cosine release on any sounding voice, renders it in the next block and then
closes the stream; nothing pops.

**Rationale**: Constitution I (nothing but bounded arithmetic and mixing on the callback; one
shared engine); the brief and the report reject every timer-based scheme (jitter, cumulative
drift, wrong clock) and choose the callback counter for KMP because the arithmetic is shared and
tempo response is one buffer (2–10 ms) instead of a 250 ms look-ahead window. NotebookLM §4
reaches the same design ("schedule the next beat while the current buffer is still being
processed", accents and normal beats pre-loaded as float PCM triggered by beat index) and
rejects `AVAudioPlayerNode` completion handlers for timing.

**Alternatives considered**: model (B), `AVAudioPlayerNode.scheduleBuffer(at:)` on iOS with an
emulated queue on Android (framework owns mixing, but two implementations and a 250 ms tempo
lag; the brief prefers (A) for a DAW-synced metronome); a Kotlin coroutine timer (violates
Constitution I and drifts); the tuner's `ToneGenerator` re-triggered from Kotlin (same clock
problem).

**Route changes and the stream rate**: `OboeAudioDevice` opens the output at 48 kHz with Oboe's
sample-rate conversion (`SRC quality Medium`, already so in feature 003), so the stream's rate
does not change when the device does; `RemoteIoAudioDevice` fixes the client format at open and
Core Audio converts to the hardware rate. The click set (R3, R4) is therefore prepared once per
open at `device.sampleRate()` and never re-prepared mid-session. On Android a disconnect (Oboe:
"if a callback is registered, Oboe will automatically close the stream in a separate thread upon
disconnection"; "opening a new stream is a valid action within onErrorAfterClose") is handled
inside `OboeAudioDevice`: `onErrorAfterClose` reopens the output with the same settings and
render callback, so plugging headphones in continues the click with its bar position intact
(spec FR-027); feature 003's `deviceLost()` flag is kept for the input side. iOS RemoteIO follows
the route by itself.

**One engine core** (Complexity Tracking #1): feature 003 put the device, the state machine and
the renderers in `tembra::TunerEngine`. Adding the metronome there would make a tuner class own
the metronome; the plan moves that ownership into `src/core/engine_core.{h,cpp}`
(`tembra::EngineCore`, the process singleton the C ABI files forward to), which sums the tone,
the sample player and the new `MetronomeRenderer` in one render callback and adds the state
`TEMBRA_AUDIO_METRONOME`. The tuner's classes and tests keep their names; only the owner moves.
This is the "audio spine grows from it" direction the constitution asks for.

**Transport reuse** (constitution v1.2.0): the scheduler exposes `anchor(transportFrame,
beatPosition)` in its class API from day one but the C ABI does not expose it yet; the
transport feature adds the ABI call and the tempo-map read. The double-domain position makes the
brief §5 re-anchoring formula a two-line change.

## R2. The grid the engine walks: per-tick levels computed in Kotlin

**Decision**: Kotlin computes the bar as a flat list of tick levels and sends it whole:
`tembra_metronome_grid { double bpm; uint32_t ticks_per_beat; uint32_t ticks_per_bar;
uint8_t levels[32]; }` (at most 7 beats × 4 ticks = 28 entries; 32 reserved). The engine knows
nothing about time signatures, styles or cells; it plays level `levels[tickIndex]` at every tick.
`TickGrid.from(settings, syncopationReading)` in `:shared:core` is the pure function that builds
the list from the time signature, subdivision, accent cells and, in debug builds, the Syncopated
reading of spec FR-037 ("Off-beat ticks" puts Accent on the ticks between beats and Sub on the
beats; the cells are untouched). It is unit-tested against every worked example of the spec.

The snapshot is published through a three-slot buffer inside the engine (fixed storage, no
allocation): the control thread fills the slot the reader is not using and stores its index in an
atomic; the callback loads the index once per block. Same mechanism for the click set (R3): the
prepared PCM lives in three slots of `ClickSet { ClickSample accent, click, sub; }`, each sample a
mono float buffer at the stream rate. A retired slot is only rewritten after the reader has
published that it moved on (a generation counter), which is the brief's "deferred reclaim"
without a free queue.

**Rationale**: brief §7.2 ("immutable snapshots published by atomic pointer swap; the RT thread
loads once per block and never locks") and §5 (accent pattern "an array of length num × subdiv
of accent levels, copy-on-write, published atomically"). Feature 003's renderers already pass
commands as per-renderer atomics rather than a queue (`ToneGenerator::command_`,
`SamplePlayer::requested_`); the three-slot buffer is the same idea for a struct too large for
one atomic. Keeping style and cell semantics in
Kotlin (Constitution II: business logic in the control plane) makes the engine reusable by the
transport unchanged: the transport will send a grid computed from the project's time signature.

**Alternatives considered**: sending signature, subdivision and cells separately and expanding in
C++ (duplicates domain rules in two languages; the FR-037 readings would need a C++ switch);
heap-allocated snapshots with a deferred-free queue (the brief's variant; more code than the
three-slot buffer for one writer and one reader).

## R3. Built-in click sounds: synthesised at load, three levels each

**Decision**: the five built-in sounds are rendered once, in C++, at the stream's sample rate when
the output opens (the rate is stable for the session, R1), into `ClickSample`s (mono float,
≤ 120 ms):

| Sound | Recipe (brief §3.4 envelope rules: raised-cosine attack, exponential decay, last 2 ms to zero) |
|---|---|
| Classic Wood | band-passed noise burst (2–5 kHz) 8 ms + a 2.2 kHz damped sine 25 ms, the woodblock "tok" |
| 808 Rim | 1.7 kHz square-ish partial pair (1.0/1.5 ratio) 12 ms + click transient, the rimshot |
| Digital Beep | pure sine 1000 Hz, 40 ms, 2 ms attack, exponential decay (the brief's reference click) |
| Cowbell | two detuned square partials 587 Hz and 845 Hz (the 1.44 ratio of the classic 808 cowbell), band-passed, 90 ms |
| Mechanical | short filtered noise 4 ms + 3 kHz resonance 15 ms, a pendulum escapement tick |

Levels: **Click** is the recipe at peak −9 dBFS; **Accent** is the same recipe with the tonal
partial(s) raised by a fifth (× 1.5, the brief's 1500/1000 Hz accent rule) at peak −3 dBFS;
**Sub** is the Click recipe at −17 dBFS. Accent is therefore 6 dB above Click and Sub 8 dB below
it (spec FR-018, SC-005), with 3 dB of headroom for the moment a release tail overlaps a new
tick (R5). The custom sound (R4) has no recipe to raise, so its Accent is the prepared sample at
+6 dB (peak-limited to −1 dBFS) and its Sub at −8 dB.

**Rationale**: Constitution VI (no bundled recordings to licence or ship; ~150 lines of DSP that
Catch2 can measure), spec FR-018 (five named sounds, recognisably different, permissive), brief
§3.4 (pre-render once; the callback only mixes finished PCM). Names follow the tablet screen's
tiles.

**Alternatives considered**: bundling recordings (needs sourcing under a permissive licence,
resampling at load anyway, and 5 × 3 files); one sound with gain-only levels (the brief's
"cheap fallback"; kept for the custom sound only).

**Transport reuse**: the `ClickSet` and `ClickSampleBuilder` are what the transport's metronome
toggle plays; nothing in them refers to the Metronome screen.

## R4. Custom WAV: decode with dr_wav, prepare once, keep the app's own copy

**Decision**: the platform file picker (R6 sidebar, `SoundFilePicker`) returns the file's name
and bytes; `:shared:data` writes the bytes unchanged to `<AppDirectories.documents>/metronome/custom.wav`
with Okio (atomic rename from `custom.wav.tmp`) and records the display name in preferences
(R13). The engine then loads that path through `tembra_metronome_load_custom_sound(path, &info)`
on the calling thread (Kotlin calls it on `Dispatchers.Default`, never on the main thread), which:

1. decodes with the already vendored dr_wav (`drwav_open_file`, `drwav_read_pcm_frames_f32`):
   PCM 8/16/24/32-bit integer and 32-bit float, any channel count, 8–192 kHz (spec FR-023);
   anything dr_wav cannot open returns `TEMBRA_ERR_DECODE` ("not a WAV or unsupported");
2. downmixes to mono (average of channels);
3. finds the onset: the first frame where the 2 ms RMS exceeds −40 dBFS relative to the file's
   peak, backed up 1 ms and started with a 1 ms raised-cosine fade-in so the transient lands on
   the beat with no click (spec FR-023 "first transient on the beat"); a file whose peak is below
   −60 dBFS is refused as silent (`TEMBRA_ERR_ARGUMENT`, "no sound");
4. keeps at most 2.0 s after the onset and ends with a 5 ms raised-cosine fade-out; a file
   shorter than 5 ms of sound is kept whole with the fade-out over its last half;
5. normalises the Click level to peak −9 dBFS (matching R3), derives Accent (+6 dB, limited to
   −1 dBFS) and Sub (−8 dB);
6. resamples to the stream rate with miniaudio's `ma_resampler` (linear algorithm with its
   8th-order low-pass; miniaudio is already vendored and public domain / MIT-0) when the rates
   differ; the source-rate PCM is kept so the next open at another rate re-resamples without
   touching the file;
7. publishes the new `ClickSet` (R2) and fills `tembra_custom_sound_info { uint32_t
   source_rate, source_channels, kept_frames; float trimmed_ms; }` for the UI.

Preparation of a 30 s stereo 96 kHz file is under a second on a phone (decode dominates); the
running click is unaffected because the callback keeps reading the previous slot until the swap.
At launch the ViewModel asks the engine to load the stored path if preferences name a custom
sound; a missing or damaged copy yields an error and the fallback to Classic Wood with one message
(spec FR-024).

**Rationale**: brief §3.1 (decode to float, resample off the audio thread once, store immutable),
§3.2 (swap by atomic publication), `kmp-architecture-tools.md` §5 (dr_libs and miniaudio are the
decided permissive file and resampling tools; libsndfile and FFmpeg are to be avoided),
NotebookLM §6 (non-destructive resampling so content never pitch-shifts), spec FR-023 to FR-025.
One C++ implementation serves both platforms (Constitution I: one engine, no per-platform DSP).

**Alternatives considered**: decoding in Kotlin or with platform codecs (`MediaExtractor`,
`AVAudioFile`): two implementations and a second WAV parser; Signalsmith or a hand-written
windowed-sinc resampler (better than miniaudio's linear+LPF on paper, but the custom sound is a
transient a few hundred milliseconds long, prepared once, and miniaudio is already in the tree,
Constitution VI); passing the platform URI to the engine (SAF content URIs and security-scoped
iOS URLs are not file paths; copying first also satisfies FR-024).

## R5. Overlap: one sounding voice, released 3 ms before the next tick

**Decision** (clarified 2026-09-29, option A): the renderer holds one sounding voice and one
releasing voice. Every block it computes `nextTickFrame` (R1); if the sounding voice would still
be playing at that frame, its gain follows a 3 ms raised-cosine ramp to zero ending exactly at
`nextTickFrame`, after which it becomes the releasing voice for the ramp's remainder and the new
voice starts at full gain. A sound shorter than the interval plays whole; at slow tempos silence
follows it; the sample is never resampled, pitch-shifted or time-stretched by the tempo (brief
§4 "changing tempo changes spacing, not the click"). At 240 BPM with four ticks per beat the
interval is 62.5 ms, so a 2 s custom sound plays its first 59.5 ms and fades.

**Rationale**: spec FR-019 and the clarification; one voice keeps the mix bounded (no voice pool,
no pile-up, no clipping guard beyond the 3 dB headroom of R3) which is the simplest RT-safe
shape (Constitution VI). The brief's voice pool (§7.1) is the polyphonic alternative the user
did not choose.

**Alternatives considered**: a pool of eight voices letting tails overlap (rejected by the user);
truncating at import to the shortest interval (loses the ring at slow tempos).

## R6. Keeping the click alive in the background and stopping it for calls and other apps

The user asked for the current official guidance on both platforms. Fetched on 2026-09-29:
Android "Manage audio focus" (`developer.android.com/media/optimize/audio-focus`), "Foreground
service types" and "Declare foreground services" (`developer.android.com/develop/background-work/services/fgs/…`),
Apple "Handling audio interruptions", "Responding to audio route changes" and "Configuring your app
for media playback" (`developer.apple.com/documentation/avfaudio/…`, `…/avfoundation/…`), and the
Oboe guide on disconnected streams and `getTimestamp`. Quotes are the pages' words.

### Android

- **Audio focus** is requested "immediately before starting to play", with
  `AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)` and `AudioAttributes` `USAGE_MEDIA` +
  `CONTENT_TYPE_MUSIC`; the request must return `AUDIOFOCUS_REQUEST_GRANTED` before the click
  starts. Loss handling per the page: on `AUDIOFOCUS_LOSS` "another app is playing audio. Your
  app should pause playback immediately, as it won't ever receive an AUDIOFOCUS_GAIN callback";
  on `AUDIOFOCUS_LOSS_TRANSIENT` (an incoming call, an assistant) the page allows pausing and
  resuming on `AUDIOFOCUS_GAIN`; the spec (FR-027, clarified) says the click never restarts by
  itself, so **both losses stop the click and abandon focus** (`abandonAudioFocusRequest`),
  which the page requires "when playback stops". `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` (a
  notification sound) keeps playing and lets the system duck ("by having the system implement
  ducking, you don't have to implement ducking in your app"; `setWillPauseWhenDucked` stays
  false). Android 12+: "when an app requests audio focus while another app has the focus and is
  playing, the system forces the playing app to fade out" and mutes media-usage apps during an
  incoming call, so even a late listener cannot leak clicks into a ringtone. Android 15+: an app
  "cannot request audio focus unless it's the top app or running a foreground service", which
  the play button satisfies (pressed while visible) and the service (below) preserves.
- **Foreground service**: to keep the process and the Oboe stream alive after the screen locks or
  the user switches apps, the app starts `MetronomeForegroundService` (in `:androidApp`, an OS
  entry point) with `startForegroundService` when the click starts and stops it when the click
  stops. Manifest: `<service android:foregroundServiceType="mediaPlayback"
  android:exported="false">`, permissions `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; the service calls `startForeground(id, notification,
  FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` within its first seconds ("Beginning with Android 14
  (API level 34), you must declare an appropriate service type for each foreground service…
  and also request the appropriate foreground service permission for that type"; an undeclared
  type throws `MissingForegroundServiceTypeException`). The type's description on the page is
  exactly this use: "Continue audio or video playback from the background." The notification is
  a small ongoing "Metronome · running" card with a Stop action (a `PendingIntent` to the
  service); on Android 13+ it is shown only when the user has granted notifications, which this
  feature does not request (the service and the system's active-app indicator still work;
  spec Principle VI). The service holds nothing: the engine singleton in the app graph keeps
  running because the process stays foreground-privileged.
- **Headphones unplugged**: the page set does not cover `ACTION_AUDIO_BECOMING_NOISY`, but the
  Android media guidance and Apple's route-change page (below) both pause on that event; see the
  deviation note at the end of this section.
- **Stream disconnect** (Oboe): with an error callback registered "Oboe will automatically close
  the stream in a separate thread upon disconnection" and "opening a new stream is a valid action
  within `onErrorAfterClose`". The engine reopens the output on the new device from that
  callback (as feature 003's `OboeAudioDevice` already does for the tone), re-prepares the click
  set if the rate changed (R3, R4) and keeps the grid position, so plugging headphones in
  continues the click on the new route (spec FR-027).

### iOS

- **Session**: category `.playback` (Apple: with it "the system doesn't silence your app's audio
  when someone sets the Ring/Silent switch to silent mode" and "your app can play background
  audio if you're using the Audio… background mode"), mode `.default`, no `.mixWithOthers` so
  our activation interrupts other apps and theirs interrupts us; `setActive(true)` is deferred
  until the click starts ("defer this call until your app begins audio playback"), and
  `setActive(false, options: .notifyOthersOnDeactivation)` follows a stop. `UIBackgroundModes`
  gains `audio` in `Info.plist` ("your app's audio continues when people switch to another app or
  lock their iOS device"). The engine renders silence between ticks, so the session stays active
  and the app is not suspended.
- **Interruptions** (calls, FaceTime, Siri, alarms, another app's playback): observe
  `AVAudioSession.interruptionNotification`; on `.began` stop the click and show play
  (`AVAudioSessionInterruptionTypeKey`); on `.ended` do nothing even when
  `AVAudioSessionInterruptionOptionKey` contains `.shouldResume`, because the spec never
  auto-resumes. Apple's page notes that new code on iOS 26+ can adopt
  `didBecomeInactiveNotification` / `resumptionRecommendationNotification` "because they
  represent the interrupted-versus-active state directly"; with a 16.0 deployment target the
  legacy notification is used and the newer one is a follow-up when the target rises.
- **Route changes**: observe `AVAudioSession.routeChangeNotification`; on `.oldDeviceUnavailable`
  check the previous route (`AVAudioSessionRouteChangePreviousRouteKey`) for an output of
  `portType == .headphones` (or `.bluetoothA2DP`); Apple: "When users disconnect their headphones,
  they don't want to automatically share what they're listening to with others. Applications
  should respect this implicit privacy request and automatically pause playback". On
  `.newDeviceAvailable` the engine's RemoteIO unit follows the new route and the click continues.
  Sample-rate changes on a route change re-prepare the click set (R3, R4).

### Mapping to the spec and the one deviation

| Event | Android signal | iOS signal | Behaviour |
|---|---|---|---|
| Screen locks or user switches app | process kept alive by the foreground service; focus held | background mode `audio`, session active | click continues (FR-027) |
| Incoming or active call | `AUDIOFOCUS_LOSS_TRANSIENT` (system also mutes media) | interruption `.began` | stop, show play, no auto-resume (FR-027) |
| Another app starts playing (YouTube) | `AUDIOFOCUS_LOSS` (Android 12+ fades us out first) | interruption `.began` | stop, show play (FR-027) |
| Short notification sound | `…_CAN_DUCK`: system ducks | no interruption for `.playback` | keep playing |
| Headphones plugged in | Oboe disconnect → reopen | `.newDeviceAvailable` | continue on the new route (FR-027) |
| Headphones unplugged | `ACTION_AUDIO_BECOMING_NOISY` broadcast | `.oldDeviceUnavailable` with headphones in the previous route | **stop, show play** (platform guidance; see below) |
| User leaves the Metronome destination | ViewModel intent | same | stop (FR-027) |
| Notification Stop action | service intent → `StopRequested` | n/a | stop |

**Deviation from spec FR-027 recorded here**: the spec says "when the output route changes the
click MUST continue on the new route, or stop and show play if it cannot". Both platforms'
official guidance says to pause when headphones are unplugged, for privacy and to avoid
blasting the speaker; the user asked this plan to follow the official guidance. The plan
therefore stops the click on headphone removal and continues it when a device is added; the
spec's FR-027 and the corresponding edge case are corrected in the same commit as this plan
(a planning-time clarification, noted in the spec's Clarifications).

### Where the platform code lives

Feature 003 set the precedent that the engine actuals own the audio session and the focus
around a stream (`NativeAudioEngine.android.kt` requests focus through `AndroidAudioFocus`
before a tone; `NativeAudioEngine.ios.kt` calls `IosAudioSession.activateForPlayback()` and
turns interruption and route-change notifications into `AudioEvent`s). The metronome follows it:
`MetronomeAudio.start()` in the Android actual requests `AUDIOFOCUS_GAIN` (a `FocusType`
parameter added to `AndroidAudioFocus`, whose listener now maps every loss to
`AudioEvent.Interrupted`) and registers an `ACTION_AUDIO_BECOMING_NOISY` receiver that emits
`AudioEvent.OutputRouteLost`; the iOS actual activates `.playback` and its existing observers
emit `Interrupted` on `.began` and `OutputRouteLost` on `.oldDeviceUnavailable` with headphones
in the previous route. `stop()` abandons focus / deactivates the session. The only new platform
seam is the process keeper the foreground service needs: `BackgroundPlaybackHost` (`:shared:ui`,
`audio.tembra.ui.platform`, next to `MicrophonePermission`): `fun keepAlive(): Boolean`,
`fun release()`, `val stopRequests: Flow<Unit>` (the notification's Stop action). Android
implementation `AndroidBackgroundPlaybackHost(context)` in `:androidApp` (starts and stops
`MetronomeForegroundService`, an OS entry point that belongs in the app module); iOS
implementation `IosBackgroundPlaybackHost` in `:shared:ui` `iosMain` returns `true` and does
nothing, the `audio` background mode being a plist declaration. This is the `kotlin-api-design`
multiplatform-boundaries shape: a common interface with platform bindings because the Android
side needs a `Context`, a service class and a fake in tests, and the two platforms differ in
kind, not in a leaf.

## R7. The beat light: output-latency-compensated telemetry read in the draw phase

**Decision**: the callback records, per rendered block, `(blockStartFrame, callbackMonotonicNs)`
and, per tick, `(tickIndex, beatIndex, level, tickFrame)` into a seqlock snapshot (feature 003's
`Seqlock<T>`). `tembra_metronome_read(out)` (control thread, lock-free) fills
`tembra_metronome_tick { uint64_t tick_index; uint32_t beat_index; uint32_t level;
int64_t audible_in_ns; int64_t beat_period_ns; uint32_t running; }` where
`audible_in_ns = callbackNs + (tickFrame − blockStartFrame) / rate + outputLatencyNs − nowNs`
computed in the engine on the same monotonic clock (`CLOCK_MONOTONIC` on Android, matching
Oboe's `getTimestamp(CLOCK_MONOTONIC)`; `mach_absolute_time` scaled by `mach_timebase_info` on
iOS). `outputLatencyNs` is refreshed on the control thread at start and on every route change:
Android from Oboe's `calculateLatencyMillis()` / `getTimestamp` ("not safe to call from the data
callback on Android < R" — hence the control thread); iOS from `AVAudioSession.outputLatency` +
`ioBufferDuration`. Bluetooth latency is therefore read from the device, not assumed (brief §6).

In `:shared:ui` the tuner's pattern is reused unchanged: `rememberFrameTick(active = running)`
(feature 003's `PitchFrameLoop.kt`, `withInfiniteAnimationFrameNanos`, pausable by a test
clock) yields a `LongState`; each accent cell's `drawBehind` reads `tick.longValue` and asks
`BeatTelemetry.litBeat(frameNanos)`, which reads the engine snapshot at most once per frame
(memoised on the frame stamp) and returns the tick's beat when `audible_in_ns ≤ 0` and
`−audible_in_ns < min(150 ms, 0.6 × beat period)`, else `−1`. Only the draw phase invalidates
(`compose-performance` deferred reads; Constitution II's telemetry rule); nothing is written to
snapshot state from the draw lambda. For semantics and tests a 10 Hz sampled `currentBeat` is
also exposed through `MetronomeUiState`, as the tuner does with its reading, and `uiTest`
scripts `BeatTelemetry` the way `ScriptedTelemetry` scripts the pitch.

**Rationale**: brief §6 ("for visual sync you must delay the UI event by the output latency so
the flash matches the audible click") and §7.3; NotebookLM §5 (the audio thread pushes beat
events into a lock-free queue, the UI polls during the frame tick); spec FR-014, SC-006.

**Alternatives considered**: a callback into Kotlin per tick (forbidden by Constitution I);
timing the light from the tempo in Kotlin (drifts from the audio clock and ignores latency);
exposing raw timestamps and converting in Kotlin (two clock conversions, one per platform, for
no gain).

## R8. Tempo controls: dial, steppers, slider, keyboard and the marking table

**Decision**:
- **Dial** (`TempoDial`): a `Canvas` draws the design's ring: track from 12 o'clock clockwise over
  275° (the SVG dash `460 140` on a 603-unit circumference), 6 dp `surfaceContainerHigh` with
  round caps; fill arc = `(tempo − 40) / 200 × 275°` in the arc gradient
  `primary → primaryContainer → secondary` (`#cebdff → #a78bfa → #ffafd3`, the SVG's
  `metronomeArcGradient`), 6 dp, with an 8 dp 50 % copy underneath as the glow (the SVG blur
  filter is replaced by a wider translucent stroke, as in feature 003, never `Modifier.blur`);
  eight 8 dp tick marks at 45° in `outlineVariant` at 50 %. The ring radius is 0.4 of the box
  (96 / 240). Dragging anywhere on the ring maps the pointer angle from the top, clockwise, onto
  the 275° track (`angle / 275° × 200 + 40`, clamped, whole numbers; touches inside the number
  area do not drag). A `Modifier.scrollable`/pointer-scroll handler changes the tempo by one per
  notch; with focus, `DirectionUp/Right` and `DirectionDown/Left` change by one, `PageUp`/`PageDown`
  by ten (`compose-focus-navigation` key handling). The number is `tempoNumber`
  (64 sp bold, −0.05 em, tabular), "BPM" `labelMedium` `secondary`, the marking `labelMedium`
  `tertiary` (`#d0bcff`).
- **Steppers** inside the ring: 44 dp round `surfaceContainerHigh`, icon 22 dp; press-and-hold
  repeats after 400 ms every 80 ms via a `rememberCoroutineScope` job cancelled on release
  (`compose-state-and-effects`: user-event coroutine); disabled at the range ends.
- **Slider**: Material `Slider` restyled with the design's 6 dp track (`surfaceContainerHighest`)
  and `primary` thumb, `valueRange 40f..240f`, `steps` so values are whole numbers; end labels
  `tabularParam` `outline`. Keyboard behaves like the dial.
- **Marking table**: `TempoMarking` enum with the spec's nine ranges; `TempoMarking.of(tempo)`
  picks the slower name on a shared boundary (120 → Moderato). Displayed as
  "Moderato (108-120)".

**Rationale**: spec FR-002 to FR-005; the Stitch phone and tablet dials share the same SVG;
Constitution IV (pointer, wheel, keyboard first-class; 44 dp targets).

**Alternatives considered**: a Material `CircularProgressIndicator` (no gradient, no drag); a
knob that changes tempo by vertical drag (not what the design suggests).

## R9. Tap tempo arithmetic

**Decision**: `TapTempoSequence` (pure, `:shared:core`): `tap(atNanos)` appends; a gap over
2 000 ms starts a new sequence; the tempo is `60e9 / mean(last ≤ 8 intervals)` rounded to the
nearest whole BPM and clamped to 40–240; `tempo` is `null` with fewer than two taps. The ViewModel
feeds it the frame clock time from the button's `onClick` (spec FR-015 to FR-017) and applies the
result like any tempo change; the caption shows "Tap again" after the first tap and "n taps"
afterwards, returning to "Sync finger rhythm" two seconds after the last tap (a `viewModelScope`
timer keyed on the last tap).

**Rationale**: spec clarifications; the mean of a short window follows a change of mind within a
bar while filtering finger jitter (SC-003: eight taps with ±20 ms jitter land within 1 BPM).

## R10. Design metrics from the Stitch HTML and the colour audit

The three screens' colours were checked against `design/stitch/theme.json` (54 named colours):
`metronome/phone` and `metronome/tablet` use theme colours only (their Tailwind configs are the
theme, every literal hex in the body is a theme value; the `rgba(…)` values in shadows and
gradients are theme colours with alpha). The `metronome/phone-landscape` file, uploaded rather
than generated, uses twenty off-token greys and roses (`#202024`, `#c9c5d0`, `#b82a67`, …); it
is followed for layout only and every colour maps to the nearest token of the phone screen
(same widgets, `design/stitch/README.md`). **No new colour is added.** Two gradient tokens are
added because they do not exist yet: `tempoArc` (the ring) and `tempoDialCard` (the dial card's
radial glow, whose midpoint `rgba(30,27,46,0.4)` is an in-gradient stop, as `#D946EF` was for
the tuner's `meterCompact`). The tablet dial card's two blurred corner discs
(`secondaryContainer` 20 % top-right, `primaryContainer` 20 % bottom-left) are drawn with the
existing `TembraGradientCard` disc tokens mirrored by a new `discs` parameter defaulting to the
tuner's placement.

| Element | Markup (phone unless noted) | Value used |
|---|---|---|
| section card | `bg-surface-container-low rounded-xl p-space-sm shadow-sm` | `surfaceContainerLow`, radius 12, padding 8; no shadow (DESIGN.md) |
| section label | `font-label-sm text-outline tracking-wider uppercase` "TIME SIGNATURE"; caption `font-tabular-param text-[10px] text-outline` "Common & Complex" | `labelSmall` uppercase `outline`, letter-spacing 0.05 em; caption `microLabel` 10 sp `outline` |
| time-signature chips | `grid-cols-6 gap-1.5`, `py-1.5 rounded-lg font-label-md`; selected `bg-secondary-container text-on-secondary-container font-semibold`; others `bg-surface-container-high text-on-surface-variant hover:text-on-surface`; landscape `grid-cols-3` two rows | `TembraChipRow`: equal-width chips, gap 6, 32 dp visual height (44 dp target), radius 8, `labelMedium`; selected `secondaryContainer` / `onSecondaryContainer`; hover text `onSurface` |
| subdivision chips | `grid-cols-4 gap-1.5`, note glyph `text-[16px]` + label; selected `bg-primary-container text-on-primary-container` | same chip row with a 16 dp leading glyph; selected `primaryContainer` / `onPrimaryContainer` |
| accent cells | `grid-cols-4 gap-2`; cell `p-1.5 rounded-lg bg-surface-container-lowest`; bar box `h-16 bg-surface-container rounded-md p-1`; bars: Accent `bg-secondary h-[95%]` glow `0 0 12px rgba(255,175,211,0.5)`, Click `bg-primary h-[62%]`, Sub `bg-primary/70 h-[42%]`; labels `text-[10px]`: Accent `text-secondary font-bold`, Click `text-on-surface font-medium`, Sub `text-on-surface-variant`; tablet identical | `AccentCells`: one cell per beat, gap 8, cell padding 6, radius 8, `surfaceContainerLowest`; bar box 64 dp `surfaceContainer` radius 6 padding 4; bar heights 95 / 62 / 42 % in `secondary` (glow `secondary` 50 %, 12 dp) / `primary` / `primary` 70 %; labels `microLabel` 10 sp; lit beat: bar box outlined `secondary` and the bar at 100 % alpha (R7); at 5 and 7 beats the row wraps to two rows when a cell would drop under 44 dp |
| style row | `pt-1 px-1 border-t border-surface-container`; "STYLE" `text-[10px] uppercase font-semibold text-outline`; pills `px-2.5 py-1 text-[11px] rounded-full`, selected `bg-secondary-container text-on-secondary-container`, others `bg-surface-container-high text-on-surface-variant` | `StyleRow`: 1 dp top seam `surfaceContainer`; label `microLabel`; `TembraPill` 28 dp visual (44 dp target), padding 10 × 4, `labelSmall` 11 sp; selected `secondaryContainer` / `onSecondaryContainer`; none selected when the cells match no style |
| sound picker, compact | pill `bg-surface-container-high hover:bg-surface-container-highest py-1.5 px-3 rounded-full`, `graphic_eq` 18 `primary`, name `labelMedium`, `expand_more` 16 `outline`; menu `w-40 bg-surface-container-highest rounded-xl p-1` with check on the current item; landscape "SOUND ENGINE" overline + name in a `rounded-lg` card | `ClickSoundPicker(layout = Dropdown)`: the pill (36 dp visual, 44 dp target) opening a `DropdownMenu` 160 dp wide on `surfaceContainerHighest`, radius 12, items 40 dp with a trailing check glyph; the landscape overline is dropped as in feature 003 (the control names itself) |
| sound picker, expanded | tablet "CLICK TIMBRE" card, `grid-cols-2 gap-1.5`; tile `p-2.5 rounded-lg`: selected `bg-secondary-container/20 border border-secondary`, icon box `w-7 h-7 rounded bg-secondary-container text-secondary`, name `labelMedium font-semibold text-secondary`; others `bg-surface-container-high border-outline-variant/20 hover:border-outline`, icon box `bg-surface-container-highest text-on-surface-variant group-hover:text-primary`; Custom tile `add` glyph, dashed border | `ClickSoundPicker(layout = Tiles)`: two columns, gap 6, tile 44 dp, radius 8; icon box 28 dp radius 4; glyphs `graphic_eq`, `radio_button_checked`, `notifications`, `sensors`, `tune`, `add`; Custom tile dashed `outlineVariant` border when empty and the file name when filled |
| dial card | `rounded-xl p-space-md` on `surface-container-low` with `radial-gradient(circle, rgba(167,139,250,0.08) 0%, rgba(30,27,46,0.4) 40%, rgba(19,19,22,0.95) 100%)` and an overlay `from-primary/10 via-transparent to-secondary/10` top→bottom; tablet adds the two blurred discs | `TembraGradientCard(glow = tempoDialCard)` plus the vertical overlay drawn in the same card (`tempoDialOverlay`: `primary` 10 % → transparent → `secondary` 10 %); discs on expanded only |
| dial | `max-w-[270px] aspect-square` (tablet `max-w-[420px]`); SVG as in R8; steppers `w-11 h-11 rounded-full bg-surface-container-high` icon 22; number `text-[64px] font-bold tracking-tighter`, "BPM" `labelMedium font-semibold text-secondary`, marking `labelMedium text-tertiary` | `TembraSpacing.tempoDialMaxWidth` 270 dp compact, `tempoDialMaxWidthWide` 420 dp; geometry in R8 |
| slider | `h-1.5 bg-surface-container-highest rounded-lg accent-primary`, labels `tabular-param text-outline` "40" / "240" | 6 dp track, `primary` thumb 16 dp, labels `tabularParam` `outline` |
| tap tempo | `flex-1 h-16 rounded-xl bg-surface-container-high active:bg-surface-container-highest`; ripple `bg-primary/20`; "TAP TEMPO" `headline-sm font-bold tracking-wider text-primary` + `touch_app` 20; caption `label-sm text-outline` | `TapTempoButton`: 64 dp tall, radius 12, `surfaceContainerHigh`, pressed `surfaceContainerHighest`, a `primary` 20 % flash for 120 ms per tap (off under reduce motion); title `headlineSmall` `primary`; caption `labelSmall` `outline` |
| play / pause | `w-16 h-16 rounded-full bg-secondary-container hover:bg-secondary text-on-secondary-container`, icon 34 filled, glow `0 0 24px rgba(255,175,211,0.35)` | `PlayPauseButton`: 64 dp, `secondaryContainer`, hover `secondary`, icon 34 dp `PlayArrow` / `Pause` filled, glow token `playGlow` (`secondary` 35 %, 24 dp; static under reduce motion) |
| compact column | `px-margin space-y-space-md pb-20` under the shell; the tap/play row last | one `Column` with `verticalScroll`, gap `md`, page margin; the tap/play row pinned below the scroll area (`TembraSpacing.pinnedRowHeight` 64 dp + gap) |
| expanded | tablet `grid-cols-12 gap-3`: left `col-span-4` (signature, subdivision, accents, click timbre), right `col-span-8` (dial card `flex-1`, then a card with tap/play centred `max-w-md`) | `MetronomeLayout.Wide`: left column 1/3 of the width (min 320 dp), right column the rest; the tap/play card under the dial card |
| phone landscape | uploaded screen: rail, `w-72` controls column (signature 2 × 3, subdivision, accents + style, sound dropdown), main pane with the dial (`h-full` ring), slider, tap/play row | `MetronomeLayout.Landscape`: controls column 36 % of the content width within 224–280 dp (feature 003's landscape tokens), scrolling; the dial sized from the pane height so dial, slider and the tap/play row fit without scrolling |

Two design details are corrected rather than copied, as in feature 003: chips and pills keep
their drawn height but get a 44 dp touch target (`minimumInteractiveComponentSize`), and no drop
shadows are drawn (DESIGN.md), glows being radial or stroke gradients.

## R11. Reusable components and where they live

**Decision**: every control the transport and timeline feature will reuse is a plain, stateless
composable in `audio.tembra.ui.metronome.components` (`internal` to `:shared:ui`, which is the
only module with Compose), following `compose-component-design`: `modifier` first optional
parameter applied to the root; state in, callbacks out; semantic and design-system values as
primitives (`selected`, `enabled`, `level`); no Boolean shape flags; caller-varying regions as
slots. The set: `TembraChipRow` (generic equal-width chip selector used by the time-signature
and subdivision rows; `leading: (@Composable (T) -> Unit)? = null` slot for the note glyph),
`AccentCells`, `StyleRow`, `ClickSoundPicker` (with `ClickSoundPickerLayout.Dropdown | Tiles`,
a design-system constraint, not a shape flag), `TempoDial` (with `steppers` inside; the number
area is a slot `center: @Composable () -> Unit` defaulting to `TempoReadout`), `TempoSlider`,
`TapTempoButton` (`caption` primitive: the design fixes its position), `PlayPauseButton`, and
the screen-level `MetronomeSettingsPanel` that stacks the first four for the compact and landscape
columns. Two generic pieces move to `audio.tembra.ui.designsystem`: `TembraPill` (the style pills
and the sound pill) and the `discs` parameter on `TembraGradientCard`. Defaults and tokens live in
`MetronomeDefaults`.

**Rationale**: the user asked for shareable components; constitution v1.2.0 names the sound
picker, tempo model and bar-shape model as reusable by the transport; `compose-component-design`
(caller-placeable roots, slots for variable content, primitives for constrained values). Keeping
them in the metronome package with `internal` visibility follows Constitution VII (feature
packages) while staying reachable from the transport package in the same module.

**Alternatives considered**: a separate Gradle module for components (the architecture brief
splits modules only on build-time or team pressure); public visibility (nothing outside
`:shared:ui` composes).

**Stability outcome (T077, 2026-09-30)**: `./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true`
(`compose-performance`, stability reference; strong skipping is on with Kotlin 2.4.20). In
`build/compose_compiler/tembra_shared:ui-composables.txt`, `TembraChipRow`, its private `Chip`,
`TembraPill` and `TembraGradientCard` are all `restartable skippable`; no parameter of the three
is reported `unstable`. `TembraPill` and `TembraGradientCard` are stable in every parameter
(`RadialGlow`, `CardDiscs`, `LinearGlow`, the content lambda and `MutableInteractionSource`
included). `TembraChipRow.items: List<ChipSpec>` and `Chip.item: ChipSpec` carry no marker (their
stability is settled at runtime, as for `TembraNavigationBar` and `TembraSegmentedControl` since
feature 002); `classes.txt` reports `ChipSpec` as `stable` in every field. No type needed
changing and no stability annotation was added.

## R12. Screen ownership, effects and coroutines

**Decision** (`compose-state-and-effects`, `kotlin-concurrency-and-flow`):
- `MetronomeScreen(viewModel = metroViewModel())` is the wiring seam: collects `uiState` with
  `collectAsStateWithLifecycle`, owns `DisposableEffect(Unit)` for `screenVisible`, a
  no frame loop of its own (`rememberFrameTick(running)` lives in `AccentCells`, R7), and hands `MetronomeScreenContent`
  immutable state, `MetronomeCallbacks` and the `BeatTelemetry` handle. `LifecycleStartEffect`
  is *not* used to stop the click (the click survives the background by design); it only
  refreshes the interruption subscription.
- `MetronomeScreenContent(state, callbacks, telemetry, layout, modifier)` is the plain composable
  tests and previews drive; the components below it take primitives and lambdas.
- `MetronomeViewModel(audio: MetronomeAudio, host: BackgroundPlaybackHost, preferences:
  MetronomePreferences, sounds: CustomClickSoundStore, picker: SoundFilePicker,
  prepareDispatcher: CoroutineDispatcher = Dispatchers.Default)` (annotated like
  `TunerViewModel`: `@Inject @ContributesIntoMap(AppScope::class) @ViewModelKey`, entry-scoped by
  `rememberViewModelStoreNavEntryDecorator`) exposes one `MutableStateFlow<MetronomeUiState>`
  as `StateFlow` (synchronous `.value`, one initial value: the defaults with `loaded = false`
  until preferences arrive, as the tuner does) and plain-function intents. Launch sites: every
  intent that touches the engine or preferences launches on `viewModelScope` (the UI
  state-holder exception of `structured-concurrency`); `init` registers nothing and launches
  nothing; the first `screenVisible(true)` starts the named jobs `observePreferences()`,
  `observeAudioEvents()`, `observeStopRequests()` and `pollAudioState()` (10 Hz, as the tuner).
  `screenVisible(false)` stops the click (spec FR-027: leaving the destination stops it) and
  cancels the jobs; `onCleared` (the entry leaves the back stack) stops everything. The click
  deliberately survives `ON_STOP`: no `LifecycleStartEffect` stops it. `importCustomSound()`
  runs the picker (`suspend`), the store write (`withContext(Dispatchers.IO)`) and the engine
  load (`withContext(prepareDispatcher)`) inside one `viewModelScope.launch`, re-throwing
  `CancellationException`, and reports the outcome into the state and the message channel.
- Events: `MetronomeAudio.events` is feature 003's `TunerAudio.events` contract (no replay, one
  collector; an interruption that happens while nobody collects has no click to stop), extended
  with the Android becoming-noisy source; `BackgroundPlaybackHost.stopRequests` is a hot
  process-wide `MutableSharedFlow(extraBufferCapacity = 1)` fed by the service's Stop action
  (one collector, the ViewModel; a request with no collector is dropped, which is right because
  there is then no click to stop). The one-shot user messages ("Not a WAV file", the fallback notice) are a
  `Channel(BUFFERED).receiveAsFlow()` consumed by a `LaunchedEffect` in the wiring composable
  that shows a snackbar (exactly-once handoff to one consumer, `flow-state-events`).
- Stepper hold repeat and the tap-caption reset are user-event coroutines: the former in
  `rememberCoroutineScope` inside `TempoDial`, the latter a `viewModelScope` job replaced on
  each tap.

**Rationale**: the three seams and effect keys of `compose-state-and-effects`; visible owners and
`suspend` lower layers of `structured-concurrency`; primitives chosen by contract in
`flow-state-events`; Constitution II and VIII.

## R13. Preferences and the custom sound store

**Decision**: `MetronomePreferences` in `:shared:data` on the existing DataStore file. Today
`TembraDataStore.tunerPreferences(dir, scope)` builds a new `DataStore` per call and DataStore
allows one instance per file, so `AppBindings` gains a `@SingleIn(AppScope::class)`
`DataStore<Preferences>` provider from which both `DataStoreTunerPreferences` and
`DataStoreMetronomePreferences` are built (a four-line refactor of the tuner wiring; its tests
are unchanged). Keys `metronome.tempo` (int), `metronome.signature` (string id),
`metronome.subdivision` (string id), `metronome.levels` (string, one letter per beat: `A`, `C`,
`S`), `metronome.style` (string id or absent), `metronome.sound` (string id: `classic-wood`,
`808-rim`, `digital-beep`, `cowbell`, `mechanical`, `custom`), `metronome.customName` (string),
`metronome.customPath` (string). Decoding rules mirror feature 003: an unknown id yields the
default; a `levels` string whose length differs from the signature's beats is re-fitted with the
FR-010 rule. `CustomClickSoundStore` (`:shared:data`, Okio `FileSystem.SYSTEM`; Okio becomes a
direct catalog dependency at the version DataStore 1.2.1 already resolves, R16): `save(name,
bytes): String` (atomic write to `<documents>/metronome/custom.wav`, returns the path),
`exists(path)`, `delete()`; `commonTest` uses Okio's `FakeFileSystem` (`okio-fakefilesystem`,
test only). The playback state is never persisted (FR-026).

**Rationale**: `kmp-code-architecture.md` §6 ("Preferences… DataStore: audio device settings,
metronome defaults"; app-specific files on Android, Documents on iOS through `AppDirectories`);
constitution v1.2.0 (the metronome's settings live in the app's preferences, not in a project).

## R14. The debug-only Syncopated reading switch

**Decision**: the app's developer tooling is launch arguments read only in debug builds
(feature 002's `tembra.developer.view`, feature 003's `tembra.tuner.testSignal`); the existing
`DeveloperViewScreen` replaces the navigation shell, so a control there could not be heard on
the metronome screen. The switch is therefore a debug launch argument:
`MetronomeDebugRequest.parse("even-beats" | "off-beat-ticks" | "all-but-one")` from the Android
extra `tembra.metronome.syncopation` (behind `BuildConfig.DEBUG`) and the iOS argument
`--tembra-metronome-syncopation=` (behind `#if DEBUG`), carried to `MetronomeScreen` through a
`LocalMetronomeDebug` composition local exactly as `LocalTunerTestSignal` carries the test
signal, and handed to the ViewModel by `useDebugSettings(request)`. Release builds never parse
the argument, so `TickGrid.from` receives `EvenBeats`.

**Rationale**: spec FR-037 (a developer-only switch in debug builds); consistency with the two
existing debug arguments; no control on the shipped screen (Constitution VI).

## R15. Test strategy

| Lane | New tests |
|---|---|
| `engine/tests` (Catch2, host) | `metronome_scheduler_test` (ten minutes rendered offline at 40, 120 and 240 BPM with every subdivision: every tick within one sample of nominal, last tick within one sample; tempo change reaches the next tick; grid changes map the position per R1; start lands beat 1 at frame 0; stop release; 3 ms pre-tick fade with a 2 s sample at 240 BPM × 4), `click_sample_builder_test` (five sounds × three levels: peak levels within 0.5 dB of R3, Accent ≥ 6 dB over Click, Sub ≥ 6 dB under, first and last sample zero, length ≤ 120 ms), `custom_sound_test` (fixtures generated in the test: leading silence trimmed to within 1 ms of the onset, 3 s tail cut to 2 s with a fade, stereo 24-bit 96 kHz and 8-bit 8 kHz accepted, float 32-bit accepted, silent file refused, non-WAV bytes refused, resampled length within one frame of expected), `triple_buffer_test`, `metronome_engine_state_test` (state rules with `FakeAudioDevice`; telemetry `audible_in_ns` arithmetic with a fixed latency), `engine_info_test` updated (ABI 3, version 0.3.0) |
| `commonTest` `:shared:core` | `TempoTest`, `TempoMarkingTest` (every tempo 40–240, boundaries), `TimeSignatureTest`, `SubdivisionTest` (labels per unit), `AccentPatternTest` (cycle, resize rule), `MetronomeStyleTest` (every style × signature, matching), `TickGridTest` (every worked example of the spec, the three Syncopated readings), `TapTempoSequenceTest` (single tap, window, rounding, 2 s cut-off, clamping, jitter within 1 BPM) |
| `commonTest` `:shared:engine` | `FakeAudioEngineTest` extended (grid, sound, custom load, preview, tick snapshots) |
| `commonTest` `:shared:data` | `MetronomePreferencesSnapshotTest`, `FakeMetronomePreferencesTest`, `CustomClickSoundStoreTest` (with Okio's `FakeFileSystem`); `DataStoreMetronomePreferencesTest` and `CustomClickSoundStoreFileTest` in `androidDeviceTest` and `iosTest` |
| `commonTest` `:shared:ui` | `MetronomeViewModelTest` (`FakeAudioEngine`, `FakeBackgroundPlaybackHost`, `FakeMetronomePreferences`, `FakeSoundFilePicker`, `FakeCustomClickSoundStore`: defaults, every intent persists, play keeps the process alive then starts, pause stops then releases, leaving the screen stops, `Interrupted`/`OutputRouteLost`/`DeviceLost`/stop request stop without restart, a refused start (focus denied, `TEMBRA_ERR_DEVICE`) keeps the state stopped with a message, tempo changes while running only send a grid, signature re-applies the style, cell edit clears the style, custom import happy path and each refusal, missing copy falls back to Classic Wood with one message, preview only while stopped, tap sequence, debug reading changes the grid), `MetronomeUiStateTest`, `MetronomeLayoutTest`, `TempoDialGeometryTest` (angle ↔ tempo), `MetronomeDebugRequestTest`, `MetronomeTokensTest` |
| `androidHostTest` `:shared:ui` | `IconAssetsTest` extended (new glyphs) |
| `uiTest` (iOS simulator + Android device) | `MetronomeScreenTest` (compact, expanded and landscape layouts; every chip, cell, pill, picker entry, stepper, slider, dial, tap and play control: click/semantics/hover through an injected `MutableInteractionSource`/focus/44 dp; 7/8 cells wrap; x/8 labels; large font), `TempoDialTest` (drag angle → tempo, wheel, keys, disabled steppers at the ends), `AccentCellsTest` (scripted `BeatTelemetry` lights the right cell; static under reduce motion for glows), `ClickSoundPickerTest` (both layouts, Custom empty and filled, Replace), `TembraPillTest` |
| `androidDeviceTest` / `iosTest` `:shared:engine` | `NativeAudioEngineTest` extended (ABI 3; metronome start/stop round trip; grid and sound calls never throw; custom load of an invalid path returns the error); `MetronomeTimingEndToEndTest` (starts the real engine at 120 BPM for five seconds and asserts the tick telemetry advances monotonically with periods within 1 ms of 500 ms) |
| `androidDeviceTest` / `iosTest` `:shared:engine` (platform classes) | `AndroidAudioFocusTest` extended (`FocusType.Gain` request granted and abandoned; a simulated `AUDIOFOCUS_LOSS` reaches the listener), `AndroidBecomingNoisyReceiverTest` (a broadcast emits `OutputRouteLost`), `IosAudioSessionTest` extended (`activateForPlayback` for the metronome; an injected interruption notification emits `Interrupted`, an injected route change with headphones emits `OutputRouteLost`) |
| `androidTest` `:androidApp`, `iosTest` `:shared:ui` | `AndroidBackgroundPlaybackHostTest` (`keepAlive()` starts `MetronomeForegroundService` in the foreground with the `mediaPlayback` type on an emulator; `release()` stops it; the Stop action's intent emits a stop request), `IosBackgroundPlaybackHostTest` (`keepAlive()` returns true; the bundle's `UIBackgroundModes` contains `audio`) |
| manual (quickstart §5–§8) | screen lock and app switch for ten minutes, phone call, YouTube, headphones in and out, notification Stop, the beat light against a recording, screenshots |

Kotlin tests never link the engine (Constitution III); timing is proven offline in C++.

## R16. Dependencies, ABI revision and build changes (Constitution VI)

| Item | Change | Licence |
|---|---|---|
| dr_wav, miniaudio | already vendored; miniaudio's resampler compiled into the engine (`MA_NO_DEVICE_IO`, decoding off) | MIT-0 / public domain |
| Okio | already transitive (not in the catalog); `com.squareup.okio:okio` becomes a catalog entry at the version DataStore 1.2.1 resolves and a direct `commonMain` dependency of `:shared:data`; `okio-fakefilesystem` in `commonTest` only | Apache-2.0 |
| Android `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | manifest permissions and the `mediaPlayback` service | platform |
| iOS `UIBackgroundModes` `audio` | `Info.plist` | platform |
| C ABI | revision 3, engine version 0.3.0: `tembra_metronome_*` (contracts/engine-c-abi-v3.md); revision 2 functions unchanged | — |
| Material Symbols glyphs | `remove`, `add`, `touch_app`, `music_note`, `graphic_eq`, `radio_button_checked`, `notifications`, `sensors`, `tune`, `check` (fetched by `tools/fetch-material-symbols.sh`; `play_arrow`, `pause`, `expand_more` exist) | Apache-2.0 (existing row) |

No new library. Licence inventory rows: Okio's row loses "transitive" and gains its pinned
version; miniaudio's row is unchanged; the README's module table gains the metronome rows.

**Resolution proof (T001-T009, 2026-09-30)**: `dependencyInsight` on `:shared:data` `androidRuntimeClasspath`
showed DataStore 1.2.1 resolving `com.squareup.okio:okio:3.9.1`, pinned in the catalog as `okio = "3.9.1"`.
`:shared:data:compileKotlinIosSimulatorArm64 :shared:ui:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
--warning-mode all` succeeds with zero warnings. The engine's own `MA_NO_DEVICE_IO` define in
`third_party_check.cpp` was removed because the CMake compile definition now supplies it (it raised
`-Wmacro-redefined`); `src/core` and `src/metronome` carry a `.gitkeep` so the Gradle `inputs.dir` declarations exist.
