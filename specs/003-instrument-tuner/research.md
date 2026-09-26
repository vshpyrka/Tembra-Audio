# Research: Instrument Tuner

**Feature**: `003-instrument-tuner` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

Every decision was checked against the constitution (v1.0.3), the research briefs
(`docs/research/tuner-algorithms.md`, `kmp-architecture-tools.md`, `kmp-code-architecture.md`,
`daw-internals.md`), the vendored engine sources, the Stitch HTML under `design/stitch/screens/`,
Google Maven's group index for DataStore and the JetBrains lifecycle documentation. Items that
could not be executed in this repository are marked UNVERIFIED and become the first tasks of the
implementation.

## R1. Where pitch detection runs

**Decision**: in the C++ engine, on a dedicated analysis thread that is not the audio callback.
The platform input callback copies each block of mono float frames into a lock-free
single-producer single-consumer ring buffer; the analysis thread drains it, assembles
4096-frame windows every 1024 frames (75 % overlap), band-limits them, runs the McLeod Pitch
Method (NSDF, first peak at or above 0.9 of the highest peak, parabolic interpolation), gates on
clarity (at least 0.5) and range (50 Hz to 1400 Hz), smooths (median of five then a light
exponential average in the log-frequency domain) and publishes one `tembra_pitch_reading` per
hop through a seqlock (R4). The autocorrelation term of NSDF is computed with an FFT of the
zero-padded window using the already vendored Signalsmith `dsp/fft.h` (MIT); the normalising
term uses prefix sums, so one hop costs about two 8192-point real FFTs, well under one
millisecond on a phone. Kotlin never sees audio samples; it reads the latest reading
(frequency, clarity, sample rate, state) as telemetry.

**Rationale**: Constitution I puts DSP in the one shared engine and allows Kotlin only commands
and telemetry; Constitution II says high-frequency telemetry is read in the draw phase, which
is exactly the needle. The research brief recommends MPM for bowed and plucked strings with
strong overtones (0.5 % error on an 83 Hz string where naive autocorrelation failed) and sizes
the window at 4096 frames for a 60 Hz floor; the 7-string Drop A preset (A1, 55.0 Hz) still
gets 4.7 periods per window, which the brief's lag range (about 36 to 873 samples at 48 kHz)
covers. The decided spectral tools are C++ (KissFFT or pffft, or the Signalsmith FFT that is
already in `engine/third_party`); no Kotlin FFT exists in the decided stack.

**Alternatives considered**: a common-Kotlin port of MPM fed by raw audio over the FIFO (the
brief's option (a)); rejected because it moves DSP out of the engine, needs a hand-written
Kotlin FFT (Constitution VI) and turns raw audio into "telemetry"; YIN (equally valid; MPM
chosen for its no-low-pass robustness on rich harmonics and its natural clarity measure; the
detector seam in R2 makes YIN a drop-in later); pYIN (offline-grade, HMM latency); HPS and
cepstrum (bin-limited at low strings).

**Latency budget** (spec FR-012, SC-001): window 85 ms + hop 21 ms + median settling about
3 hops (64 ms) + one display frame (17 ms) ≈ 190 ms at 48 kHz, 210 ms at 44.1 kHz.

## R2. The seam for adjusting the implementation

**Decision**: three layers, each replaceable on its own.

1. C++: `tembra::PitchDetector` (pure virtual `estimate(const float* window, size_t n, float
   sampleRate) -> PitchEstimate{hz, clarity}`) with `McLeodPitchDetector` as the only
   implementation; `TunerAnalyzer` owns the ring buffer, pre-filter, detector, gate and
   smoother and takes every tunable in one `tembra_tuner_config` struct (window, hop, min/max
   Hz, clarity threshold, peak ratio, median size, watchdog seconds) that the C ABI exposes with
   defaults. Swapping the algorithm is one class; tuning is one struct.
2. Kotlin engine facade: `interface TunerAudio` in `:shared:engine` (start/stop listening, read
   pitch, start/stop tone, load/play samples, audio state, events). `AudioEngine` extends it;
   `NativeAudioEngine` actuals implement it over JNI and cinterop; `FakeAudioEngine` scripts
   readings and records calls for tests. The tuner ViewModel depends on `TunerAudio` only, so a
   different engine (or an experimental pure-Kotlin analyser) can be injected without touching
   the UI.
3. Kotlin domain: `PitchMath` in `:shared:core` (frequency to nearest semitone, cents, halfway
   rule, sharp spelling, in-tune tolerance, meter range) and `TuningPresets`. Tolerance, note
   naming, presets and the meter scale are Kotlin constants under unit tests; changing them
   never touches C++.

**Rationale**: the user asked for "a basic or common abstraction over the tuner implementation";
this is the smallest set of seams that separates algorithm, platform I/O and musical rules
(Constitution II interfaces and DI, VI no speculative layers).

**Alternatives considered**: one `TunerEngine` class doing everything in Kotlin with the C++
engine as a thin capture (rejected by R1); a strategy registry of detectors selectable at run
time (no consumer, Constitution VI).

## R3. Platform audio I/O shells

**Decision**:
- Android: Oboe 1.11.0 (already consumed through Prefab). Input stream: `Direction::Input`,
  `AudioFormat::Float`, mono, requested 48 000 Hz, `PerformanceMode::LowLatency`,
  `SharingMode::Exclusive` (Oboe falls back to shared), `InputPreset::Unprocessed` (falls back
  to `VoiceRecognition` if the device rejects it; both keep automatic gain control and noise
  suppression off the signal), `Usage`/`ContentType` defaults; an `AudioStreamDataCallback`
  that only copies frames into the ring buffer and an `AudioStreamErrorCallback` whose
  `onErrorAfterClose` marks the device lost (R7). Output stream for tones: `Direction::Output`,
  float, stereo, requested 48 000 Hz, low latency; the callback calls the tone renderer.
  Streams are opened when needed and closed when idle; never both at once (spec FR-006).
- iOS: a RemoteIO Audio Unit (`kAudioUnitSubType_RemoteIO`) driven from C++ with the Core
  Audio C API in `engine/src/platform/apple/remote_io_audio_device.cpp`, compiled only when
  CMake's `IOS` is set. Input enabled on bus 1 with an input callback that calls
  `AudioUnitRender` into a pre-allocated buffer and pushes to the ring buffer; output on bus 0
  with a render callback that calls the tone renderer. Stream format Float32, non-interleaved,
  mono in, stereo out, at the session's sample rate. The `AVAudioSession` is configured from
  Kotlin `iosMain` inside the `NativeAudioEngine` actual before the unit starts: category
  `playAndRecord` with mode `measurement` for listening (no processing on the mic), category
  `playback` for tones, `preferredSampleRate` 48 000, `preferredIOBufferDuration` 0.005;
  `setActive(false, notifyOthersOnDeactivation)` when the tuner goes idle so other apps' audio
  resumes (spec "does not overlap with other audio").
- macOS host (Catch2): a `NullAudioDevice`; the DSP classes are tested offline.

**Rationale**: the constitution names Oboe and AVAudioEngine or RemoteIO for I/O and requires
a variable frame count per callback, which both callbacks pass straight into the ring buffer.
RemoteIO in C++ keeps the iOS shell inside the engine sources with no Objective-C++ file;
the Kotlin/Native `platform.AVFAudio` bindings cover session configuration and notifications.
The cinterop definition gains `-framework AudioToolbox -framework CoreAudio` so the static
engine archive links.

**Alternatives considered**: miniaudio's device layer for both platforms (already vendored,
public domain; rejected because the constitution names Oboe and RemoteIO and miniaudio is
decided for files only); AVAudioEngine with a source node and an input tap (works, but the tap
delivers fixed 4800-frame buffers on some iOS versions and adds an Objective-C layer; the
RemoteIO callback gives the frame count the constitution requires); Android `AudioRecord`
from Kotlin (Kotlin on the capture path, forbidden by Constitution I).

## R4. Threads, the FIFO and the telemetry snapshot

**Decision**:
- Audio callback thread (platform-owned): copy in/out, no allocation, no locks, no logging.
- Analysis thread (`std::thread`, created by `tembra_tuner_start`, joined by `tembra_tuner_stop`):
  loops `while (running) { if (ring.available() >= hop) analyse(); else sleep_for(4 ms); }`; a
  sleep instead of a condition variable keeps the callback free of any wake-up primitive that
  could block. All buffers are allocated in `tembra_tuner_start` before the stream opens.
- Reading: `tembra_pitch_reading` is written by the analysis thread and read by the UI thread
  through a seqlock (an atomic sequence counter bracketing the copy); `tembra_tuner_read` never
  blocks and is safe to call every frame. The same function stamps a "last read" timestamp used
  by the privacy watchdog (R7).
- Tone renderer: a `ToneGenerator` (phase accumulator in double, 5 ms raised-cosine attack and
  release, amplitude 0.25 = -12 dBFS, one-shot length from `duration_ms` with release starting
  before the end, loop when 0) and a `SamplePlayer` (decoded PCM in memory, linear-interpolating
  resampler to the stream rate, 10 ms equal-power crossfade at the loop point, one-shot plays
  the full length). Commands from Kotlin (start/stop) are atomics read by the callback; the
  callback applies the release ramp itself so a stop never clicks (spec FR-021).
- Android JNI: one `nativeTunerRead(FloatArray)` call per frame fills a pre-allocated 8-float
  array and returns the sequence as a `long`; no object is created per frame. iOS: one
  `nativeHeap`-allocated `tembra_pitch_reading` reused for the engine's lifetime.

**Rationale**: Constitution I (nothing blocking in the callback, atomics and SPSC FIFOs only)
and the brief's pipeline (ring buffer at least twice the window, hop-driven analysis).

## R5. Reference tones and drop-in recordings

**Decision**:
- Built-in tone: `ToneGenerator` sine at the target frequency (R4), accurate to the phase
  accumulator's double precision (well under 0.01 cent), one-shot 2000 ms, loop until stopped.
- Recordings (spec FR-022): the app looks for `files/tuner/<instrument>-<note>.wav` among the
  Compose resources of `:shared:ui` (`shared/ui/src/commonMain/composeResources/files/tuner/`),
  `<instrument>` being `guitar` or `cello` and `<note>` the sharp-spelled note name with `#`
  written as `sharp` (`guitar-E2.wav`, `guitar-Fsharp3.wav`, `cello-C2.wav`; resource paths
  cannot carry `#`). `ReferenceSoundRepository` (in `:shared:ui`, package `tuner`) calls
  `Res.readBytes(path)` once per name, caches the bytes or the miss (a `MissingResourceException`
  means "no recording"), and resolves each audition to `ReferenceSound.Recording(bytes)` or
  `ReferenceSound.Synth(frequencyHz)`. The ViewModel loads a recording into the engine with
  `loadSample` (WAV decoded by the vendored dr_wav on the calling thread, never on the callback)
  and plays it with `playSample(handle, loop)`; handles are unloaded when the preset changes or
  the ViewModel is cleared. No recording ships with this feature.
- On-pitch test: the Catch2 target receives `TEMBRA_TUNER_RECORDINGS_DIR` (the resources
  directory, passed by the Gradle `engineHostTest` task) and, for every `*.wav` found, decodes
  it, runs `TunerAnalyzer` offline and fails if the estimate is more than 1 cent from the note
  named in the file. With no recordings the test passes vacuously and says so. The same test
  writes its own synthetic WAV to a temporary directory to prove the decode, resample and loop
  path.

**Rationale**: the user chose a synthesized tone and asked for a documented place to drop real
sounds later; Compose resources are the one bundle both platforms share, and a naming rule plus
a try-read needs no manifest. Decoding with dr_wav keeps Kotlin free of audio parsing.

**Alternatives considered**: a manifest JSON listing recordings (extra step for the user);
synthesized pluck models (not asked); WAV parsing in Kotlin (duplicates dr_wav).

## R6. Microphone permission

**Decision**: an interface in `:shared:ui` (`audio.tembra.ui.platform.MicrophonePermission`)
with `status: StateFlow<MicrophonePermissionStatus>` (`NotDetermined`, `Granted`,
`DeniedCanAsk`, `DeniedOpenSettings`), `suspend fun request()`, `fun refresh()` and
`fun openSystemSettings()`, injected into `TunerViewModel` through Metro.
- Android implementation in `:androidApp` (`AndroidMicrophonePermission`), because launching
  the system prompt needs an `Activity`: `MainActivity` registers
  `ActivityResultContracts.RequestPermission` in `onCreate` and hands the launcher to the
  implementation through a small `MicrophonePermissionHost`; `refresh()` reads
  `checkSelfPermission`; after a denial, `shouldShowRequestPermissionRationale` decides
  between `DeniedCanAsk` (the prompt can be shown again) and `DeniedOpenSettings` (Android's
  "don't ask again" state); `openSystemSettings()` starts
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the package. `RECORD_AUDIO` is declared in
  the app manifest.
- iOS implementation in `:shared:ui` `iosMain` (`IosMicrophonePermission`):
  `AVAudioSession.sharedInstance().recordPermission` maps `undetermined`, `granted`, `denied`
  (iOS never re-prompts, so denied is always `DeniedOpenSettings`);
  `requestRecordPermission` shows the prompt; `openSystemSettings()` opens
  `UIApplicationOpenSettingsURLString`. `NSMicrophoneUsageDescription` carries the wording of
  spec FR-014. The deployment target is iOS 16, so the `AVAudioSession` API is used rather than
  iOS 17's `AVAudioApplication`; Kotlin/Native emits no deprecation warning for it (the
  zero-warning gate is unaffected) and a comment notes the iOS 17 replacement.
- Flow (spec FR-014, FR-015, user's request): entering Auto Listen with `NotDetermined`
  requests at once; `Granted` starts listening; either denied state replaces the meter with the
  denied panel whose one button reads "Allow microphone" and either re-requests (`DeniedCanAsk`)
  or opens the settings (`DeniedOpenSettings`); `refresh()` runs on every lifecycle resume so a
  grant made in the settings starts listening on return.
- Test double `FakeMicrophonePermission` in `commonTest` of `:shared:ui` (UI tests inherit it).

**Rationale**: Constitution II (interfaces and DI for business logic; platform SDK access in
platform source sets); Constitution VI (no permission library). Keeping the Android class in
the app module keeps `:shared:ui`'s Android source set free of Activity APIs.

**Alternatives considered**: moko-permissions or Calf (Apache-2.0, but a dependency plus their
own binding composables for about 120 lines of code); a Compose `rememberLauncherForActivityResult`
in an Android-only composable (would need an `expect` composable, which Constitution II reserves
for SDK access classes, and couples the request to composition).

## R7. Microphone lease, privacy backstop, interruptions and routing

**Decision**: the microphone is held only while a pure policy says so, and the engine guards
against the policy failing.
- `ListeningPolicy` (pure Kotlin, `:shared:ui` `tuner` package, unit-tested): desired audio =
  `Tone` when an audition is active in By Ear; `Listening` when mode is Auto Listen, the Tuner
  entry is visible, the app is resumed, permission is granted and no interruption is active;
  otherwise `Idle`. The ViewModel applies transitions to `TunerAudio` (stop before start, never
  both streams) and updates the pill.
- Visibility comes from `TunerScreen`'s `DisposableEffect` (Navigation 3 disposes the entry's
  content on a destination switch, so leaving the tab is an immediate `screenVisible(false)`),
  foreground from `LifecycleStartEffect` (`ON_START` / `ON_STOP`: Compose Multiplatform maps
  `willEnterForeground` / `didEnterBackground` to them on iOS and Android's activity lifecycle does
  the same), and `ViewModel.onCleared` stops everything when the entry-scoped store is cleared.
  `ON_PAUSE` is deliberately not used: iOS raises it for Control Centre, the app switcher and call
  banners and Android for an unfocused multi-window pane, and it would drop the microphone or kill
  a tone that spec FR-020 forbids restarting; `permission.refresh()` still runs on `ON_RESUME`
  (analysis M1). The result is the immediate release the spec's SC-007 asks for.
- Privacy backstop ("dead man's switch"): the analysis thread stops the input stream by itself
  when `tembra_tuner_read` has not been called for `watchdog_seconds` (default 5). The meter's
  draw-phase read is the heartbeat, so a microphone that nobody is looking at turns itself off
  even if a Kotlin lifecycle bug ever kept the session alive. The ViewModel's 10 Hz poll
  (R8) sees `state == Idle` and, if the policy still wants listening, restarts it; in normal
  operation the backstop never fires because Kotlin stops first.
- Interruptions: iOS `AVAudioSessionInterruptionNotification` (began: stop; ended with
  `shouldResume`: `InterruptionEnded`) and `AVAudioSessionRouteChangeNotification`
  (`oldDeviceUnavailable`: `OutputRouteLost`, which stops a tone); Android `AudioManager`
  focus loss while a tone plays (`Interrupted`), Oboe `onErrorAfterClose` (`DeviceLost`, the
  policy re-opens). Android 10+ gives the microphone to the foreground app and silences
  others, so a call or another recorder shows the resting state rather than an error; the
  policy reacts to focus and lifecycle, not to silence.
- Routing: tones request transient audio focus on Android (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`)
  and abandon it on stop; on iOS the session is deactivated with `notifyOthersOnDeactivation`
  whenever the tuner is idle and the categories above keep listening and tones on separate
  routes. Listening never requests focus (capture has no focus semantics on Android).

**Rationale**: the user asked for the best way to stop listening on navigation for privacy and
to avoid overlapping other audio; the lease-by-policy is deterministic and testable, the
backstop costs about twenty lines in the analysis thread, and the OS microphone indicator
(Android privacy dot, iOS orange dot) is the visible proof used in quickstart §7.

**Alternatives considered**: a foreground service or background capture (never wanted);
polling the permission and lifecycle from the engine (the engine must not know Kotlin state);
an explicit `keepAlive()` call (the read already is one).

## R8. Telemetry to the meter without recomposition storms

**Decision**: `TunerMeter` is one `Canvas` that draws the whole meter card content (status
pill, note and octave, arc, marks, needle, frequency readout, reference chip, Flat and Sharp
labels with their highlight) from a `PitchSnapshot` read inside the draw lambda. A
`PitchFrameLoop` composable runs `withFrameNanos` while listening and writes a frame tick into
a `mutableLongStateOf` that only the draw lambda reads, so each display frame invalidates the
draw phase alone; the needle angle is smoothed per frame with a short critically damped
approach (about 80 ms) so it never jumps between hops and still tracks within FR-012. Text is
drawn with `TextMeasurer` and the theme's text styles (fonts and font scale apply). The
ViewModel additionally samples `readPitch` at 10 Hz into `TunerUiState.reading` for the
accessibility `stateDescription`, the 10 Hz "in tune" hysteresis and tests; nothing at frame
rate flows through `StateFlow` (Constitution II).

**Rationale**: the brief's telemetry pattern (frame loop while active, reads only in draw
lambdas); text at 47 Hz through composables is exactly the recomposition storm the constitution
forbids, while a canvas redraw of one node per frame is cheap.

**Alternatives considered**: `Text` composables fed by a 20 Hz `StateFlow` (still a screen-level
recomposition per update); drawing only the needle in a canvas and keeping texts as composables
(two clocks for one instrument, visible mismatch).

## R9. Design metrics from the Stitch HTML (evidence for data-model.md §8)

From `instrument-tuner/phone/index.html` (Auto Listen; the tablet file is the timeline's markup,
its screenshot confirms the same components at one-row width) and `tuner-by-ear-cello/phone`,
`tuner-by-ear-7-string-guitar/tablet` (By Ear):

| Element | Markup | Value used |
|---|---|---|
| instrument selector | container `p-1 rounded-xl bg-surface-container-lowest gap-1`; segment `h-8 px-space-md rounded-lg` icon `w-4 h-4`; active `bg-secondary-container text-on-secondary-container font-label-md`; inactive `text-on-surface-variant hover:text-on-surface` | container padding 4, radius 12, `surfaceContainerLowest`; segment 32 dp tall, padding 12, radius 8, icon 16; active `secondaryContainer` / `onSecondaryContainer`, `labelMedium` |
| string-count and mode selectors | container `rounded-lg bg-surface-container-low p-0.5`; segment `h-7 px-space-sm rounded-md font-label-sm`; string active `bg-surface-container-high text-primary font-semibold`; mode active `bg-primary-container text-on-primary-container`, icon `text-[14px]` | container padding 2, radius 8, `surfaceContainerLow`; segment 28 dp tall, padding 8, radius 4; string active `surfaceContainerHigh` / `primary`; mode active `primaryContainer` / `onPrimaryContainer`; icon 14; `labelSmall` |
| audio-state pill | `px-space-sm py-1 rounded-full bg-surface-container-low text-primary font-tabular-param` with a 6 px pulsing dot; By Ear variant with `volume_up` and `text-secondary` | pill, padding 8 × 4, `surfaceContainerLow`; text `tabularParam` uppercase; dot 6 dp; `primary` for "Mic active", `secondary` for "Tone out" |
| meter card | `p-space-md py-6 rounded-2xl bg-surface-container-lowest border border-outline-variant/20` with `radial-gradient(circle at 60% 45%, rgba(167,139,250,0.12) 0%, rgba(217,70,239,0.06) 35%, transparent 70%)` and two 224 px blurred discs (`primary-container/10` top-left, `secondary-container/15` bottom-right, `blur-3xl`) | radius 16, padding 12 × 24, `surfaceContainerLowest`, 1 dp seam `outlineVariant` at 20 %; gradient and discs as radial brushes (data-model.md §8) |
| status pill | `px-3 py-1 rounded-full bg-surface-container-high/80 border border-primary/20 text-primary text-xs font-semibold uppercase tracking-wider`, 6 px pulsing dot | pill, padding 12 × 4, `surfaceContainerHigh` at 80 %, border `primary` at 20 %, `labelSmall` uppercase, dot 6 dp; unlit: `onSurfaceVariant` text, `outlineVariant` border, no dot pulse |
| note | `text-[64px] font-bold text-on-surface` (Space Grotesk) + octave `text-[32px] text-primary font-bold ml-0.5`, `drop-shadow-[0_2px_12px_rgba(0,0,0,0.8)]` | `tunerNote` 64 sp bold, `tunerOctave` 32 sp bold `primary`, gap 2 dp; the shadow is dropped (DESIGN.md forbids drop shadows) |
| gauge | `max-w-[340px] aspect-[1.8/1]`, `viewBox 0 0 400 230`: outer arc centre (200, 210) radius 160 from (40, 210) to (360, 210), `surfaceContainerHighest` width 4; end marks `outline` width 2.5 (18 px outward); quarter marks `outlineVariant` width 2 at ±45°; top mark `secondary` width 3.5 (24 px); centre zone arc radius 160 width 8 `primary` at 35 % spanning the in-tune tolerance; dashed circle radius 115 `outlineVariant` at 30 % dash 4/6 width 1.5; inner arc radius 90 width 10 `surfaceContainerHigh` at 90 % between ±68°; dashed inner arc radius 90 width 5 dash 8/8 `secondaryContainer` at 60 % between ±36°; needle `secondary` width 3 from radius −2 to radius 172 with an 8 px glow `rgba(255,175,211,0.7)`; pivot ring radius 12 stroke 3 `secondary`, dot radius 5 `primary` | `MeterGeometry` in normalised units (data-model.md §8); max width 340 dp compact, 480 dp expanded; needle angle = cents × 1.8°, clamped to ±90° |
| readouts | `font-tabular-timecode text-[18px] font-semibold tracking-wider` "82.4 Hz"; separator `•` `outlineVariant`; chip `px-3 py-1 rounded-full bg-surface-container-highest/60 border-outline-variant/30` with 8 px `secondary` dot and `text-[11px] text-secondary uppercase` | `tunerReadout` 18 sp tabular semibold; chip padding 12 × 4, `surfaceContainerHighest` at 60 %, border `outlineVariant` at 30 %, text `microLabel` 11 sp uppercase `secondary` |
| Flat / Sharp row | `pt-2 px-1 border-t border-outline-variant/20`; `arrow_left` / `arrow_right` 20 px `text-outline`; labels `font-label-md text-on-surface-variant` | top seam `outlineVariant` at 20 %, padding-top 8; arrows 20 dp `outline`; highlighted side: label and arrow in `secondary`, `labelMedium` |
| By Ear card | phone `aspect-square p-3 rounded-xl bg-surface-container-low border-outline-variant/20`, tablet `h-36 p-3.5 bg-surface-container-lowest`; numeral `text-[11px] font-bold text-outline tracking-wider` (tablet `labelMedium`); Hz `font-tabular-timecode text-[11px] text-on-surface-variant`; note `text-[32px] font-bold` + octave `text-[20px] text-primary` (tablet headline-lg + headline-sm); button `w-full py-1.5 px-2 rounded-lg bg-surface-container-high/60 border-outline-variant/20 text-[11px] font-semibold`, icon 14 px; playing card `border-secondary` glow `shadow-[0_0_18px_rgba(133,20,90,0.4)]`, button `bg-secondary-container/60 text-on-secondary-container` "Playing" with `pause` | compact: square cards, padding 12, radius 12, `surfaceContainerLow`; expanded: 144 dp tall, padding 14, `surfaceContainerLowest`; numeral `microLabel` bold `outline`; Hz `tabularParam`; note `tunerCardNote` 32 sp + `tunerCardOctave` 20 sp (expanded: `headlineLarge` + `headlineSmall`); button full width, 36 dp visual, radius 8, `surfaceContainerHigh` at 60 %; playing: border `secondary`, glow `secondaryContainer` at 40 % 18 dp, button `secondaryContainer` at 60 % |
| preset picker | `px-3 py-2 rounded-xl bg-surface-container-low border-outline-variant/30`, `music_note` 18 px `primary`, overline `text-[9px] uppercase text-outline` "Tuning Preset", value `font-label-md font-semibold` with the string list, `expand_more` 18 px `outlineVariant` | padding 12 × 8, radius 12, `surfaceContainerLow`, border `outlineVariant` at 30 %; overline `microLabel` 9 sp; value `labelMedium`; dropdown of the preset list |
| loop toggle | `px-3 py-2 rounded-xl bg-surface-container-low border-outline-variant/30`; overline "Loop Tone" `text-[9px] text-secondary uppercase`, value "Active" `text-[11px]`; track `w-8 h-4 rounded-full bg-secondary`, knob `w-3 h-3 bg-on-secondary-container` | same container; track 32 × 16 dp `secondary` when on (`surfaceContainerHighest` when off), knob 12 dp `onSecondaryContainer`; value "Active" / "Off" |
| grids | phone `grid-cols-2 gap-space-sm max-w-sm`; tablet `grid-cols-3 gap-4 max-w-xl` | 2 columns, gap 8, max 384 dp; 3 columns, gap 16, max 576 dp |
| By Ear card background | `radial-gradient(circle at 50% 30%, rgba(171,136,255,0.06) 0%, rgba(133,20,90,0.06) 40%, transparent 75%)`, tablet `circle at 60% 50%, rgba(167,139,250,0.1) 0%, rgba(133,20,90,0.08) 45%, transparent 75%` | compact and expanded gradient tokens (data-model.md §8) |

Two design details are corrected rather than copied: the 28 dp and 32 dp segments and the
16 dp toggle get a 44 dp touch target through `Modifier.minimumInteractiveComponentSize()`
while keeping the drawn size (Constitution IV), and the note's drop shadow is dropped
(DESIGN.md: no drop shadows). The design's blurred discs are drawn as radial gradient brushes
rather than `Modifier.blur`, which is a no-op on Android 11 and older.

## R10. Preferences

**Decision**: DataStore Preferences multiplatform, `androidx.datastore:datastore-preferences-core:1.2.1`
(latest stable on Google Maven on 2026-09-26; pulls `datastore-core-okio` and Okio 3.x
transitively), in `:shared:data`: `TunerPreferences` (`snapshot: Flow<TunerPreferencesSnapshot>`,
`suspend fun update(transform)`), `DataStoreTunerPreferences` over a `DataStore<Preferences>`
created with `PreferenceDataStoreFactory.createWithPath { "<preferences>/tembra.preferences_pb".toPath() }`,
and `FakeTunerPreferences` (in-memory, `commonMain` beside the interface like `FakeAudioEngine`).
`AppDirectories` gains `preferences` (Android `filesDir/datastore`, iOS
`Library/Application Support/datastore`, created on first use). Keys: `tuner.instrument`,
`tuner.guitarStrings`, `tuner.presetId`, `tuner.mode`, `tuner.loopTone` (strings and a boolean);
unknown or invalid values fall back to the defaults of spec FR-005.

**Rationale**: the decided preferences stack (`kmp-code-architecture.md` §2 and §6); reactive
`Flow` reads, transactional writes, one file for all future preferences.

**Alternatives considered**: `multiplatform-settings` (synchronous; rejected by the brief);
a JSON file with Okio (hand-written transactions).

## R11. Layout, components and touch targets

**Decision**: `TunerScreenContent(state, callbacks, telemetry, windowWidth)` with
`windowWidth = WindowWidth.from(currentWindowAdaptiveInfo().windowSizeClass)` (as feature 002).
Compact and medium: selectors in two rows (instrument + pill; string count + mode), content
card fills the remaining height, By Ear grid of two columns. Expanded: selectors in one row,
meter centred at 480 dp max width, three-column grid at 576 dp max width; no supporting pane
(Destination.Tuner keeps `hasSupportingPane = false`). New reusable design-system components:
`TembraSegmentedControl` (Tab semantics, hover, focus ring, 44 dp targets), `TembraToggle`
(the 32 × 16 design switch with `toggleable` semantics), `TembraStatusPill`, `TembraGradientCard`
(rounded card with seam and radial-gradient background). The preset picker uses Material's
`DropdownMenu` anchored to a design-styled button; the By Ear grid is a plain `Column` of
`Row`s sized by column count (at most seven cards, no lazy list needed).

**Rationale**: Constitution IV (window size classes, hover, focus, 44 dp, semantics) and the
002 precedent for custom chrome when Material's components cannot match the design geometry
(Material's `SegmentedButton` is 40 dp outlined, `Switch` is 52 × 32 dp).

## R12. Test strategy

| Lane | New tests |
|---|---|
| `engine/tests` (Catch2, host) | `mpm_test` (synthetic sines with strong overtones and a weak fundamental at every semitone A1 to E5, ±10/±20/±40 cent offsets, silence, white noise: SC-001 to SC-003 offline), `tuner_analyzer_test` (hop assembly, gate, smoothing, resting within 500 ms, watchdog), `tone_generator_test` (frequency within 1 cent, ramps, loop seam, stop ramp), `sample_player_test` (WAV decode, resample, loop crossfade), `recordings_on_pitch_test` (R5), `seqlock_test`, `engine_info_test` updated (ABI 2, version 0.2.0) |
| `commonTest` `:shared:core` | `NoteTest`, `PitchMathTest` (halfway rule, sharp spelling, tolerance, every preset string), `TuningPresetsTest` (the table of spec FR-003, numerals, defaults) |
| `commonTest` `:shared:engine` | `FakeAudioEngineTest` (scripted readings, call recording, state, test signals), `TestSignalsTest` (catalogue and `NOTE:CENTS` parsing) |
| `commonTest` `:shared:data` | `TunerPreferencesSnapshotTest` (defaults, invalid values), `FakeTunerPreferencesTest`; `DataStoreTunerPreferencesTest` in `androidDeviceTest` and `iosTest` (round trip on a real file) |
| `commonTest` `:shared:ui` | `ListeningPolicyTest`, `TunerTestSignalRequestTest`, `MicrophonePermissionStatusTest`, `TunerViewModelTest` (with `FakeAudioEngine`, `FakeMicrophonePermission`, `FakeTunerPreferences`, `FakeResourceReader`: permission flow, mode switches stop audio, audition/loop/stop rules, preferences round trip, 10 Hz reading, watchdog restart), `ReferenceSoundRepositoryTest`, `MeterGeometryTest`, `TunerUiStateTest` |
| `androidHostTest` `:shared:ui` | `IconAssetsTest` extended (ten new glyphs + the two design glyphs), `ThemeJsonParityTest` unchanged |
| `uiTest` (iOS simulator + Android device) | `TunerScreenTest` (both modes at compact and expanded, selectors, denied state and its action, cards and Playing state, preset dropdown, toggle, hover, focus, 44 dp, semantics, no overlap at large font), `TunerMeterTest` (draws from a scripted snapshot: needle side, labels, pill states, resting), `TembraSegmentedControlTest`, `TembraToggleTest`, `TembraStatusPillTest` |
| `androidDeviceTest` / `iosTest` `:shared:engine` | `NativeAudioEngineTest` extended: tone start/stop without a microphone, state transitions, `readPitch` idle snapshot; listening tests run only when the test process holds `RECORD_AUDIO` (granted by the instrumentation runner); `TunerSignalEndToEndTest`: every catalogue test signal through the real engine (R16); `AndroidAudioFocusTest` (request granted, abandon); `IosAudioSessionTest` (activate for listening and playback, deactivate) |
| `androidTest` `:androidApp`, `iosTest` `:shared:ui` | `AndroidMicrophonePermissionTest` (`refresh()` under a runner-granted permission gives `Granted`; the settings intent carries the package URI), `IosMicrophonePermissionTest` (`refresh()` yields one of the four statuses; `request()` resolves on the simulator) — analysis C1 |
| manual (quickstart §3 to §9) | real guitar and cello, permission prompts, interruptions, route changes, OS microphone indicator, screenshots including the scripted test-signal state matrix (R16) |

Kotlin tests never link the engine (Constitution III); accuracy is proven offline in C++.

## R13. Dependencies added (Constitution VI) and licence inventory

| Catalog alias | Coordinates | Version | Licence | Why |
|---|---|---|---|---|
| `androidx-datastore-preferences-core` | `androidx.datastore:datastore-preferences-core` | 1.2.1 | Apache-2.0 | R10 |
| (transitive) | `androidx.datastore:datastore-core-okio`, `com.squareup.okio:okio` | 1.2.1, 3.x | Apache-2.0 | R10 |
| (system) | AudioToolbox, CoreAudio, AVFAudio frameworks | iOS SDK | Apple SDK | R3 |
| (already vendored) | Signalsmith `dsp/fft.h`, dr_wav | 1.1.0, 0.14.5 | MIT, MIT-0 | R1, R5 |

No other library. UNVERIFIED until the first build task: that `datastore-preferences-core`
1.2.1 resolves for `iosArm64`, `iosSimulatorArm64` and the Android KMP library target under
Kotlin 2.4.20 without a klib conflict with the existing catalog.

## R14. Icons and the two design glyphs

**Decision**: `tools/fetch-material-symbols.sh mic mic_off hearing play_arrow pause volume_up expand_more arrow_left arrow_right settings`
(all ten exist at the pinned commit `27e9ef1d`, verified 2026-09-26) plus two hand-written
vector assets `ic_guitar_24.xml` and `ic_cello_24.xml` whose path data is the inline SVG the
Stitch instrument selector uses (24 × 24 view box, single path, `fillColor #FFFFFFFF`), with a
header comment naming `design/stitch/screens/instrument-tuner/phone/index.html` as the source.
`TembraIcon` gains `Mic`, `MicOff`, `Hearing`, `PlayArrow`, `Pause`, `VolumeUp`, `ExpandMore`,
`ArrowLeft`, `ArrowRight`, `Settings`, `Guitar`, `Cello`. `IconAssetsTest` accepts either the
Material Symbols header or the design-glyph header. The licence inventory row for Material
Symbols is unchanged; the two design glyphs are the project's own artwork.

**Rationale**: spec FR-026 and the user's instruction to follow the design and hand-make missing
icons; Material Symbols has no guitar or cello glyph.

## R15. Engine version, ABI revision and build changes

- `TEMBRA_ENGINE_ABI_VERSION` 1 → 2; `TEMBRA_ENGINE_VERSION` 0.1.0 → 0.2.0;
  `engine_info_test` and `EngineDescriptorParserTest` updated first (test-first).
- `engine/CMakeLists.txt`: new sources under `src/tuner/` (analyzer, detector, smoother, tone
  generator, sample player, wav decoder, seqlock, ring buffer) and `src/platform/`
  (`audio_device.h`, `null_audio_device.cpp`; `android/oboe_audio_device.cpp` under
  `if(ANDROID)`; `apple/remote_io_audio_device.cpp` under `if(IOS)` linking
  `AudioToolbox` and `CoreAudio`); `TEMBRA_TUNER_RECORDINGS_DIR` compile definition for tests.
- `:shared:engine`: cinterop `linkerOpts` add `-framework AudioToolbox -framework CoreAudio`;
  the `buildEngine*` tasks add `src/platform` and `src/tuner` as inputs; `engineHostTest` passes
  the recordings directory.
- `NativeAudioEngine` becomes `expect class NativeAudioEngine : AudioEngine` without a common
  constructor; the Android actual takes the application `Context` (audio focus), the iOS actual
  none; `AppBindings.provideAudioEngine` moves into the two platform graphs.
- Android manifest: `RECORD_AUDIO`. iOS `Info.plist`: `NSMicrophoneUsageDescription`.
- `THIRD_PARTY_LICENSES.md`: DataStore and Okio rows. `app/README.md`: "where does X go" rows
  for a platform permission, a preference, a reference recording and a design glyph.

## R16. Test signals and the screenshot matrix

**Decision**: a `SyntheticAudioDevice : AudioDevice` in the engine generates a plucked-string-like
tone (fundamental at `fundamental_gain` 0.25 plus harmonics 2 to 5 at 1.0, 0.5, 0.3, 0.2, the same
mix the Catch2 tests use) on its own real-time thread and delivers 256-frame blocks at 48 kHz to the
input callback, exactly as Oboe or RemoteIO would. `tembra_tuner_start_test_signal(config, signal)`
starts listening with that device instead of the platform device; the reading carries
`synthetic = 1`; everything downstream (ring buffer, analyzer, seqlock, JNI/cinterop, ViewModel poll,
meter) is the production path. Kotlin exposes `TunerAudio.startTestSignal(config, signal)` with
`TestSignal(note, cents, amplitude, harmonics, fundamentalGain)` and a catalogue `TestSignals`
(ids `e2-in-tune`, `e2-flat-20`, `e2-sharp-20`, `e2-flat-45`, `a2-sharp-40`, `d3-flat-5`,
`g3-in-tune`, `b3-in-tune`, `e4-in-tune`, `c2-in-tune`, `b1-in-tune`, `a1-in-tune`, `e5-in-tune`,
`out-of-range-2khz`). Debug builds read a launch argument (`tembra.tuner.testSignal` extra on
Android inside `BuildConfig.DEBUG`; `--tembra-tuner-test-signal=` on iOS inside `#if DEBUG`, the
mechanism feature 002 used for the developer view) accepting an id or `NOTE:CENTS`; when present the
shell starts on Tuner, the ViewModel uses `startTestSignal` in place of `startListening`, needs no
permission and shows the pill "Test signal". `tools/capture-tuner-states.sh --android|--ios` loops
over the catalogue, launches the app with each id, waits two seconds and captures
`<platform>-compact-autoListen-<id>.png`; `TunerSignalEndToEndTest` (device and simulator lanes of
`:shared:engine`) starts each catalogue entry on the real engine, polls `readPitch` until ten hops
have arrived and asserts the frequency within 1 cent (or `hasPitch == false` for the 2 kHz entry).

**Rationale**: the user asked to feed generated sounds at different pitches into the tuner, test the
values and capture screenshots; a synthetic input device proves the whole engine and UI path on real
devices deterministically, which neither the Catch2 offline tests nor Kotlin scripted telemetry do,
and a launch argument keeps it out of the UI and out of release builds (Constitution VI, Complexity
Tracking #5).

**Alternatives considered**: golden-image comparison (no infrastructure in the project; the
constitution asks for reviewed screenshots, and the pixel assertions in `TunerMeterTest` already guard
the drawing); playing WAV files into a loopback device (no such device on phones); a hidden UI panel
(reachable from the UI, forbidden by the developer-view precedent).
