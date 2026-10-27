# Research: Microphone Recording on the Timeline (feature 010)

Phase 0 of the plan. Each entry states the decision, the rationale and the alternatives
considered. Evidence: the spec and its clarifications of 2026-10-27, the engine and Kotlin maps
made on 2026-10-27 (file references below are `app/…` paths), the Oboe documentation fetched
through Context7 (`/google/oboe`: FullGuide "Reading and writing to an audio stream" and
"Round-trip callback with non-blocking read", `AudioStreamAAudio::calculateLatencyMillis`, the
FullDuplexStream wiki page), Apple's AVFAudio reference (`AVAudioSession.CategoryOptions.
allowBluetoothA2DP`: "A2DP is a stereo, output-only profile … apps using the playAndRecord
category may also allow routing output to paired Bluetooth A2DP devices"; `allowBluetoothHFP`:
"makes Bluetooth Hands-Free Profile (HFP) devices available for audio input"; `inputLatency`
and `outputLatency` "in seconds"), the Android foreground-service documentation
(`develop/background-work/services/fgs/service-types`, `restrictions-bg-start`), and the
research briefs `docs/research/daw-internals.md` §1.7, §8.3 and `docs/DAW-App-Research-Report.md`
§6, §8.

## R1. Recording lives inside the transport, as a duplex mode of the one open stream

**Decision**: arming a track asks the engine to add the input half to the transport's stream
(`tembra_transport_arm`); disarming closes it. The audio state stays `TRANSPORT`; a
`tembra_capture_snapshot` says whether the input is armed, pre-rolling, recording or monitoring.
The tuner's `LISTENING` path is untouched.

**Rationale**: the spec records while the project plays (FR-009, FR-010) and meters while it is
stopped (FR-005), so input and output must be open together; today "at most one stream is
open" (`engine/include/tembra_engine.h:56-61`, `engine/src/core/engine_core.h:36-38`) and the
tuner's capture is exclusive to `IDLE` (`engine_core.cpp:65`). Keeping `TRANSPORT` as the state
leaves the exclusivity table, the Tuner/Metronome hand-over (`Destination.hostsTransport`,
`PlaybackGuard.transportHostVisible`) and feature 009's device tests unchanged.

**Alternatives**: a new top-level `RECORDING` state (every state test and the hand-over would
change for no gain); reusing the tuner's input path (its `SpscRingBuffer` overwrites old audio by
design, `engine/src/tuner/ring_buffer.h:35-37`, and its analysis thread is the wrong consumer).

## R2. Android capture: a second Oboe input stream read non-blocking inside the output callback

**Decision**: `OboeAudioDevice` keeps its output stream and, on arm, opens an input stream at the
output's rate (Float, `ChannelCount::Unspecified` clamped to two, `PerformanceMode::LowLatency`,
`SharingMode::Exclusive` with a `Shared` fallback, `InputPreset::Unprocessed` with the
`VoiceRecognition` fallback the tuner already uses, buffer capacity twice the output's). The
output callback reads `numFrames` from the input with `timeoutNanos = 0`, zero-pads a short read
and hands the block to the engine's render; the first two bursts after arming are a warm-up
cushion (short reads not counted). An input disconnect reopens the input (the Bridge's existing
`onErrorAfterClose` pattern) and ends a running take with `INPUT_LOST`; an output reopen keeps
the input and clears monitoring (R12).

**Rationale**: Oboe's guide: "when doing both input and output, it is common to use a callback
for output and then just do a non-blocking read from the input stream. Then you have both the
input and output data available in one high priority thread" (FullGuide, "Reading and writing to
an audio stream"). It adds no second real-time thread, no cross-thread queue and no latency
beyond one burst, and it leaves the running output stream untouched when a track is armed during
playback (FR-001). The tuner's input settings (`oboe_audio_device.cpp:41-77`) are reused.

**Alternatives**: `oboe::FullDuplexStream` (it must be the output stream's callback from the
moment the output opens, so arming would force an output reopen and a gap; otherwise the same
pattern); a separate input callback (two real-time threads, a FIFO between them, one more burst of
latency; Oboe advises against it).

## R3. iOS capture: both RemoteIO buses on the one unit; arming restarts the unit

**Decision**: `RemoteIoAudioDevice` gains `openDuplex`: `kAudioOutputUnitProperty_EnableIO` on
bus 1 and bus 0, input non-interleaved float at the hardware rate (RemoteIO shares one rate across
buses), output as today; `renderProc` calls `AudioUnitRender` on bus 1 for the same frame count
before rendering the output, so capture and output share one callback and one clock. Because
`EnableIO` can only change on an uninitialised unit, arming (and disarming) uninitialises,
reconfigures and restarts the unit; the Kotlin session switches the `AVAudioSession` to
`playAndRecord` with `[.allowBluetoothA2DP, .defaultToSpeaker]` before arming and back to
`playback` after disarming.

**Rationale**: one unit and one callback are the only way to keep input and output on one clock
for alignment (R9) and monitoring (R12); today `openInput` disables output and `openOutput` never
enables input (`remote_io_audio_device.cpp:54-55, :103`). `.defaultToSpeaker` keeps playback on
the loudspeaker (the `playAndRecord` default is the receiver); `.allowBluetoothA2DP` is what the
spec's clarification requires (R4).

**Known limit, recorded for the owner**: arming while a project plays restarts the unit, which
is a short output gap (expected 10–30 ms; the transport clock and the document are untouched).
FR-001 says arming during playback "MUST NOT interrupt playback"; on Android it does not, on iOS
the gap is a platform constraint, measured in the quickstart and recorded in the verification
notes. Arming while stopped, the common case, is silent anyway.

**Alternatives**: enabling bus 1 whenever the transport opens (no gap, but the microphone would
be open whenever a project is open, which FR-003 forbids and the research report §8 warns breaks
App Store review); `AVAudioEngine` (nothing in the engine uses it; it would be a second I/O
shell).

## R4. Input routing: the built-in microphone with wireless headphones; wired inputs follow the system

**Decision**: iOS: the session's options include `.allowBluetoothA2DP` and exclude
`.allowBluetoothHFP`, so wireless headphones stay stereo outputs and never appear as inputs
(Apple: "allowBluetoothHFP … makes Bluetooth Hands-Free Profile (HFP) devices available for audio
input"; with only the A2DP option "the system … routes output to paired Bluetooth A2DP devices").
Android: the app never starts Bluetooth SCO, so the default input stays the built-in microphone
(or a wired headset or USB interface the system routes to). The `AudioRouteMonitor` (R12) names
the current input and output; a change of the input port ends a running take (FR-021), a change
of output alone does not.

**Rationale**: the clarification of 2026-10-27 (FR-002); HFP drops the whole link to call
quality.

**Alternatives**: `.allowBluetoothHFP` (call-quality capture and playback); an input picker (out
of scope, FR-034).

## R5. The captured block is buffered in an ordered SPSC FIFO, never an overwriting ring

**Decision**: the callback pushes the input block (after the peak measurement and the
monitoring mix) into a `SpscFifo<float>` (`engine/src/timeline/spsc_fifo.h`) sized four
seconds × channels at the stream rate (about 1.5 MB stereo), consumed by the recorder thread
(R6). A short `write` counts an input overrun (the block's missing frames are lost, never
reordered); a short device read after warm-up counts the same way. Both reach the host as
`input_overruns` in the health struct and the capture snapshot.

**Rationale**: a take must be gapless and ordered; the tuner's ring lets the newest audio win,
which is right for a meter and wrong for a recording. `SpscFifo` already has the two-thread
million-frame test and the cache-line separation.

**Alternatives**: the tuner's `SpscRingBuffer`; a mutex-protected queue (forbidden on the
callback).

## R6. The recorder thread writes a crash-safe 24-bit WAV and the live peaks

**Decision**: a `TakeRecorder` thread ("tembra-recorder", modelled on `DiskStreamer`'s thread:
named, condition variable, 10 ms idle wait; user-initiated QoS on Apple and nice 0 on Android,
not real-time) pops the FIFO, converts float to 24-bit little-endian PCM with clamping, appends
to the WAV, and every second rewrites the RIFF and data sizes and flushes, so an abrupt end
leaves a readable file (FR-023). The 44-byte header is written by the engine itself (dr_wav's
writer cannot rewrite sizes mid-stream; miniaudio is built with `MA_NO_ENCODING`). The same
thread accumulates the 256-frame peak buckets of the `.peaks` base level into a lock-free ring
the host polls (R7), checks the frame cap and the free space (`statvfs`) once a second and ends
the take itself with `MAX_LENGTH` or `DISK_FULL`.

**Rationale**: 24-bit PCM keeps everything a 16- or 24-bit converter delivers (FR-024) at
three quarters of float's size (8.6 MB per mono minute); the periodic header rewrite is what
`OverviewBuilder` already does for `.peaks` (`overview_builder.cpp:175-190`); one worker thread
is the pattern Principle I expects (the callback never touches a file).

**Alternatives**: 32-bit float WAV (a third larger, no more information from a microphone);
FLAC (an encoder on the way in, miniaudio has none, dr_flac decodes only); writing on the
streamer thread (it serves the clips' refills; a long write would starve playback).

## R7. The growing clip's waveform comes from an in-memory live overview polled at 10 Hz

**Decision**: the recorder publishes completed base buckets (int8 min/max per channel, 256
frames, the `.peaks` base level) into a ring of 1024 buckets with a monotonic count; the Kotlin
session polls `tembra_capture_read_peaks` ten times a second into a `LiveOverview` that keeps all
four levels incrementally (each coarser bucket is the min/max of four finer ones) and exposes the
`WaveformOverview` shape `ClipPainter` already draws. When the take ends, the ordinary
`tembra_audio_overview_build` runs on the finished WAV and the live overview is dropped, so the
final waveform is by construction "identical to the overview computed from the stored audio"
(FR-011).

**Rationale**: the `.peaks` format stores the levels consecutively, so a coarser level cannot be
appended while the base level grows; polling a file at 10 Hz is I/O churn; a ring of recent
buckets is lock-free, bounded (a 30-minute take is 5 625 buckets in memory on the Kotlin side)
and costs the recorder one atomic store per bucket. The existing `OverviewTracker` polls every
500 ms (`OkioProjectRepository.kt:397`), too slow for FR-010.

**Alternatives**: a progressive `.peaks` file (format), a snapshot of the last block's peaks
(misses buckets between frames).

## R8. The meter is measured on the callback, held and smoothed on the host

**Decision**: the callback computes the per-channel peak of each input block (bounded, branch-free
work) and the engine keeps a 64 ms recent-peak ring and a sticky clip latch (|x| ≥ 0.9999,
cleared by `tembra_capture_reset_clip`), published through a `Seqlock<CaptureRecord>`. Kotlin
draws the meter in the draw phase (`rememberFrameTick`, the tuner's `LevelSmoother`) with a
1.5 s peak hold and the −60…0 dB scale (FR-005); nothing flows through screen state.

**Rationale**: the peak of a 5 ms block is cheaper than the mixing already done there; a 64 ms
window guarantees a 20 Hz reader never misses a transient; the tuner's `NoiseGateSlider`
(`ui/tuner/NoiseGateSlider.kt:116-138`) is the proven draw-phase pattern.

**Alternatives**: measuring on the recorder thread (adds a FIFO's worth of delay to the meter);
a `StateFlow` of levels (forbidden by Principle II).

## R9. Alignment: discard the round-trip latency at the take's start, from reported latencies

**Decision**: the take's first kept frame is the input frame captured `alignment_frames` after
the transport frame at which capture began, where `alignment = input latency + output latency`
for the routes in use; the clip then starts exactly at the downbeat or the Record position.
Android: `calculateLatencyMillis()` on both streams (Oboe derives the input value from the
input stream's hardware timestamp: "hardware is earlier"), sampled at arm and after every
reopen. iOS: `AVAudioSession.inputLatency + outputLatency + 2 × ioBufferDuration`, sampled at
arm and on every route change. The value is published in the capture snapshot and as the
`audio.takeAlignmentMs` counter; the loopback check of SC-001 verifies it on the reference
devices and the verification notes record every other device. No manual offset (clarification
of 2026-10-27).

**Rationale**: with input and output in one callback (R2, R3) the capture frame index equals the
transport frame at block start, so the only unknowns are the two hardware latencies, which both
platforms report; discarding at the start keeps the clip's visual start on the beat.

**Alternatives**: placing the clip `alignment` earlier instead of discarding (the clip would
start a few milliseconds before the playhead, visually odd); a calibration routine (declined).

## R10. The count-in is a transport pre-roll with the click forced on

**Decision**: `tembra_transport_record_start` from stopped sets the clock to
`start − countInFrames` and marks a pre-roll until `start`: the renderer renders no clip
before `start`, forces the metronome voice on through the pre-roll (anchored at the negative
beat position), publishes `count_in_beats_left` in the transport snapshot, and at `start` begins
capture sample-accurately (the block is split at `start` as it is split at clip boundaries) and
lets the click follow the toggle. Cancel during the pre-roll pauses and restores the position.
From playing, capture begins at the next block with no pre-roll.

**Rationale**: sample accuracy (SC-002: the take's first beat one beat after the last click
within 1 ms) is only possible inside the renderer; the click, the tempo map and the anchor
already exist there (`transport_renderer.cpp:217-230`).

**Alternatives**: a Kotlin timer (jitter of a frame or more); the standalone metronome (an
exclusive state).

## R11. Clips under a running take are muted by the renderer; the trim is a document edit at the end

**Decision**: the arrangement carries no change while recording; the renderer, knowing the
armed track and the take's start frame, skips the segments of that track's clips at or after
the start (FR-010). When the take ends, Kotlin applies one `RecordTake` edit (asset merge, clip
insert, trims by the split arithmetic of `ProjectEditor.splitClip`, `ProjectEditor.kt:156-164`)
as one history step and pushes the new arrangement with the take's clip source (FR-011, FR-013).

**Rationale**: the document stays the single authority (Principle V); the renderer rule is a few
comparisons per segment; one edit gives one undo step for free.

## R12. Monitoring: a dedicated strip into the master bus; the host guards the speaker

**Decision**: when monitoring is on, the captured block passes through a `MonitorStrip` (a
`TrackStrip` that follows the armed track's gain and audibility targets) and is added to the
master bus in the same callback, so the musician hears the input through the track's gain, mute
and solo and the master volume (FR-016) with `input + output latency` of delay (SC-004). The
engine clears monitoring on every output stream reopen (a route may have become the speaker);
the Kotlin `AudioRouteMonitor` (interface; Android `AudioManager.getDevices` and
`AudioDeviceCallback`; iOS `currentRoute` and the route-change notification) tells the session
whether the output is the built-in speaker, and the session only enables monitoring on a
non-speaker output, re-enabling it after a route change when the remembered toggle is on
(FR-017).

**Rationale**: a second `GainRamp` instance avoids running the track's ramp twice per block; the
engine has no notion of routes, the host has; clearing on reopen bounds the feedback window to
the host's reaction time (under 50 ms) and only while headphones are unplugged.

**Alternatives**: platform hardware loopback (not available through Oboe; iOS has none for
RemoteIO); monitoring through the track's own strip (double ramp).

## R13. The document gains `countIn`; a take is one `RecordTake` edit

**Decision**: `Project.countIn: CountIn` (`OFF`, `ONE_BAR`, `TWO_BARS`, default `ONE_BAR`,
lenient serializer reading unknown values as `ONE_BAR`) is appended after `tracks` (wire order
rule); the format version stays 1 because the field is optional with a default (contract rule 7
of `specs/009-timeline-arranger/contracts/project-document.md`) and older readers ignore it. The
demo golden file changes (`encodeDefaults = true`). `ProjectEdit.RecordTake(trackId, asset,
clip)` is handled in `ProjectEditor.apply` and `ProjectSession.applyToEngine` (both exhaustive);
`Track.nextTakeName` follows `Track.nextName`.

**Rationale**: FR-013, FR-015, FR-024; the editor's split arithmetic is reused for the trims.

## R14. A take is staged as a dot-file with a manifest, then adopted like an import

**Decision**: the recorder writes `audio/.take-<uuid>.wav` and the session writes
`audio/.take-<uuid>.json` (track id, start beats, take name, started-at, alignment) before
capture begins; both are dot-files, invisible to the project's size and the retire sweep
(`OkioProjectRepository.kt:381-382`). On stop, `TakeAdopter` hashes the file in 64 KiB chunks as
`AudioImporter.copyAndHash` does, renames it to `audio/<sha256>.wav`, inspects it, builds the
`.peaks` inside the `timeline.waveform` section, and returns the `AudioAsset` and the clip
template for `RecordTake`. On project open the repository lists `.take-*.json` manifests with a
readable WAV beside them and adopts each one (FR-023) as a settled edit with no undo step; a
manifest without a readable file is deleted.

**Rationale**: the importer's hashing and overview pipeline already exist; a manifest is the only
way to know, after a crash, which track and position the file belongs to.

## R15. A `RecordingController` beside the session, with a pure `RecordingPolicy` in core

**Decision**: `ProjectSession` gains one collaborator, `RecordingController`
(`ui.session`), owning the armed track, the monitoring state, the recording phase, the
permission flow, the route observation and the take finalisation, and exposing them through
`SessionState.recording`. The transitions (arm, disarm, record, stop, cancel, interruption,
input change, output change, permission revoked, track deleted, project switched, leaving) are a
pure `RecordingPolicy` table in `:shared:core` tested like `PlaybackPolicy`. Launch sites:
`observeRoutes`, `observePermission`, `livePeaks(takeId)` (10 Hz), `finaliseTake(takeId)`,
`stopTakeOn(reason)`; none in `init`.

**Rationale**: the session file is already 700 lines; one owner per concern with named launches
(`kotlin-concurrency-and-flow`); a table keeps the many ways a take can end testable.

## R16. Permission and background rules

**Decision**: the controller injects the existing `MicrophonePermission` (interface, bound
app-wide: `IosMicrophonePermission`, `AndroidMicrophonePermission`); the first arm calls
`request()`; `DeniedCanAsk`/`DeniedOpenSettings` yield `SessionMessage.MicrophoneDenied` shown as a
snackbar with "Open settings" (FR-020); `refresh()` runs on resume. `Info.plist`'s
`NSMicrophoneUsageDescription` becomes the one sentence of FR-019 and `TunerCopy.deniedText`
keeps its own wording. Android: `FOREGROUND_SERVICE_MICROPHONE` is declared,
`PlaybackForegroundService` gets `foregroundServiceType="mediaPlayback|microphone"`, and the
host's `keepAlive` carries a "Recording" notification for takes; the service is always started
from a visible activity (arming is a tap on Timeline), which satisfies the Android 14 while-in-use
rule ("you must call `startForegroundService()` … while your app has a visible activity"). iOS:
the `audio` background mode already declared plus the active `playAndRecord` session keep a take
recording in the background.

## R17. Where Record goes: the bar from 432 dp of bar width, the armed header below it

**Decision**: the transport bar holds Record right of play/pause whenever the bar is at least
432 dp wide (phone landscape, tablets, desktop windows), with the readout stacked below 600 dp
and inline from 600 dp as the clarification chose. In bars narrower than 432 dp (every phone in
portrait) the bar stays exactly as feature 009 built it and Record sits in the armed track's
compact header beside the arm toggle, taking the label chip's place while the track is armed;
on those phones Record appears when a track is armed (the arm toggle is on every header), and
the keyboard "R" still records into the armed track. This is the owner's named fallback
(clarification 4, "Record in the armed track's header beside the arm toggle"), narrowed to the
widths where the chosen layout cannot exist.

**Rationale (arithmetic, not taste)**: `TransportBar.kt:53, :128-132` budgets the readout as
the width left after six gaps, four 44 dp targets and the 48 dp play disc; with the 44 dp speaker
button that is 80 dp at 360 dp. One more 44 dp target and gap leaves 34 dp, and a stacked
"00:12.40" needs about 52 dp; eight 44 dp targets alone are 352 dp. Touch targets may not drop
below 44 dp (constitution IV), so option A of the clarification is infeasible below 432 dp.
The compact header at 360 dp holds dot, name, chip, M, S, arm and menu with about 80 dp for the
name; adding Record while armed and hiding the chip keeps the name at about 72 dp.

**Alternatives**: dropping the one-bar step buttons on phones (option C, not chosen by the
owner); a floating round Record button above the bar's right end while armed (a new pattern the
design does not show; kept as the first candidate if the owner dislikes the header placement);
the position-only readout (option B).

## R18. Tokens and glyphs

**Decision**: `theme.json` and `DawColors` gain `record` (the design's red record circle),
`meterBody` and `meterWarning` (the dual-colour LED meters of the mixer screens), guarded by
`ThemeJsonParityTest`; glyphs: `headphones` via `tools/fetch-material-symbols.sh`, the arm and
Record circles drawn (a filled circle, as `MuteSoloButton` draws its letter), the existing
`TembraIcon.Mic` for the denied message.

## R19. Performance instrumentation names

**Decision** (spec "Performance instrumentation declared by this feature"): sections
`timeline.arm` (arm → first meter frame), `transport.recordStart` (press or pre-roll end → first
frame stored), `transport.countIn` (animation variant over the pre-roll), `timeline.takeFrame`
(the growing clip and the meter per frame), `transport.recordStop` (press → clip finalised and
project saved), `timeline.monitorToggle` (press → first monitored block or the refusal message),
`engine.takeAlignment` (the alignment determination); counters `audio.inputOverruns`,
`audio.recordBacklog`, `audio.takeAlignmentMs`, `audio.monitorDelayMs` beside the existing
`audio.avgUs`/`audio.maxUs` (callback load while a take runs is read from the journey's
recording phase); anchors and the `recording` journey as the spec lists; the `stress` journey
records a take while scrolling. Baseline profile regenerated. The labelled session compares
against the pre-010 rounds through `tracepulse report`'s onset detector and gate (the owner's
instruction: "check the performance and compare with previous results for regressions").

## R20. Test conditions, delegation and the Stitch sync

**Decision**: every device run follows the owner's rules (brightness at the floor, media volume
at zero, the black device-test window); the one step that needs sound, the loopback alignment of
SC-001, is done with a 3.5 mm or USB-C loopback cable where the device has a jack or an
interface, and otherwise acoustically at the lowest audible volume in a separate, announced step.
Lane runs, screenshot captures and the perf session on each device are delegated to Sonnet-class
sub-agents through the `gradle-run` wrapper and the device-lane launcher; engine, session and
renderer code stay with the main agent. After the implementation the new states are uploaded
to the Tembra Audio Stitch project as new screens and the export re-pulled (the workflow recorded
for features 004, 006 and 009).

## R21. Dependencies

**Decision**: none added. dr_wav's writer stays unused in production (the engine writes its own
header); miniaudio's decoders read the take back; Oboe, the RemoteIO shell, Okio, DataStore and
`kotlinx.serialization` are already present. The licence inventory gains no row.
