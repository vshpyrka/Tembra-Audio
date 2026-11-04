# Contract: Engine C ABI revision 7 — capture, takes, monitoring, pre-roll

**Header**: `app/engine/include/tembra_engine.h`, `TEMBRA_ENGINE_ABI_VERSION 7u`; engine version
`0.6.0`. Every revision-6 function is unchanged; two revision-6 structs gain appended fields.
Conventions as in revision 6 (`tembra_<area>_<verb>`, `int32_t` → `TEMBRA_OK` or a negative
`tembra_error`, `void` idempotent stops, `const struct*` in, `out*` filled, no callback into the
host, nothing ever called on the audio callback). Research R1–R12.

## State

`tembra_audio_state` is unchanged: capture is a mode of `TRANSPORT`. The exclusivity rules of
revision 6 hold; `tembra_transport_close` disarms first (a running take ends with
`TRANSPORT_CLOSED` and is finalised before the stream closes).

## Arming (the input half of the transport's stream)

```c
typedef struct tembra_capture_config { uint32_t max_channels; /* 1 or 2 */ uint32_t reserved; } tembra_capture_config;

int32_t tembra_transport_arm(const tembra_capture_config* config);  /* TRANSPORT only (ERR_STATE); opens the input beside the output (R2, R3), starts the meter; ERR_DEVICE when the input cannot open; idempotent while armed */
void    tembra_transport_disarm(void);                                /* ends a running take (kept, finalised synchronously), turns monitoring off, closes the input; idempotent */
int32_t tembra_transport_set_monitor(uint32_t enabled, uint32_t track_index); /* ERR_STATE unless armed; ERR_ARGUMENT unless track_index < track_count; the engine clears it on every output reopen and reports the clear in the capture snapshot */
```

Android opens the input at the output's rate with `ChannelCount::Unspecified` clamped to
`max_channels`, `LowLatency`, `Exclusive` (fallback `Shared`), `InputPreset::Unprocessed`
(fallback `VoiceRecognition`), capacity 2 × the output's; the output callback reads
`numFrames` non-blocking, zero-pads a short read, and the first two bursts are warm-up. iOS
enables bus 1 on the RemoteIO unit (uninitialise → `EnableIO` → initialise → start; the host has
set the `playAndRecord` session first) and pulls bus 1 with `AudioUnitRender` inside the render
callback.

## Takes

```c
typedef struct tembra_record_config {
    const char* wav_path;        /* created (truncated) on the control thread; 24-bit PCM, the input's channels, the stream rate */
    uint32_t    track_index;     /* the armed track; its clips at or after the take start are silent while recording */
    uint32_t    count_in_beats;  /* 0 = none; ignored while playing */
    int64_t     max_frames;      /* the engine ends the take itself at this length (30 min) */
    uint64_t    min_free_bytes;  /* the engine ends the take itself below this free space (50 MB) */
} tembra_record_config;

int32_t tembra_transport_record_start(const tembra_record_config* config);
/* armed only (ERR_STATE); a take already running → ERR_STATE; file cannot be created → ERR_IO.
   Stopped: the clock moves to start − count_in frames, a pre-roll runs with the click forced on and no clip rendered,
   and at `start` playback and capture begin together on the downbeat (sample-accurate: the block is split at `start`).
   Playing: capture begins at the next block boundary; no pre-roll. The first alignment_frames captured frames are discarded (R9). */

int32_t tembra_transport_record_cancel(void);  /* pre-roll only: pauses, restores the position, deletes the file; ERR_STATE otherwise */

enum tembra_take_stop_reason { TEMBRA_TAKE_STOP_USER = 0, TEMBRA_TAKE_STOP_MAX_LENGTH = 1, TEMBRA_TAKE_STOP_DISK_FULL = 2,
                               TEMBRA_TAKE_STOP_INPUT_LOST = 3, TEMBRA_TAKE_STOP_TRANSPORT_CLOSED = 4, TEMBRA_TAKE_STOP_WRITE_FAILED = 5 };

typedef struct tembra_take_info {
    int64_t  start_frames;      /* transport frame of the first kept sample (the downbeat, or the block where capture began) */
    double   start_beats;       /* start_frames through the tempo map */
    int64_t  frames;            /* kept frames; 0 = discarded (shorter than 0.01 s; the file is deleted) */
    uint32_t channels;
    uint32_t sample_rate;
    int64_t  alignment_frames;  /* discarded at the start */
    uint32_t stop_reason;       /* tembra_take_stop_reason */
} tembra_take_info;

int32_t tembra_transport_record_stop(tembra_take_info* out);
/* ends capture at the next block (a 5 ms fade on the monitor path only; the take keeps its last sample), drains the recorder,
   finalises the WAV header, fills `out`; blocks for at most 1 s (the FIFO holds 4 s but the drain is a memcpy and a write);
   ERR_STATE when no take runs. When the engine ended the take itself (stop_reason in the snapshot ≠ USER and recording == 0),
   the host still calls record_stop to collect `out`. Playback continues after the stop; the host pauses if it wants to. */
```

## Telemetry

```c
typedef struct tembra_capture_snapshot {
    uint32_t armed, recording, pre_roll, monitoring;
    uint32_t input_channels, sample_rate;
    float    peak[2];            /* max |x| over the last 64 ms per channel; 0 when silent */
    uint32_t clipped;            /* sticky: |x| ≥ 0.9999 seen since the last reset */
    int64_t  frames_captured;    /* kept frames so far; grows during a take */
    int64_t  take_start_frames;
    int64_t  alignment_frames;
    int64_t  input_latency_ns, output_latency_ns;
    uint32_t stop_reason;        /* set when the engine ended the take itself; USER otherwise */
    uint32_t input_overruns;     /* FIFO-full and post-warm-up short reads since arm */
    uint32_t backlog_frames;     /* frames waiting in the FIFO at the last block */
} tembra_capture_snapshot;

void tembra_capture_read(tembra_capture_snapshot* out);   /* seqlock read, safe every frame; zeros when not armed */
void tembra_capture_reset_clip(void);                     /* clears `clipped` */

typedef struct tembra_peak_bucket { int8_t min[2]; int8_t max[2]; } tembra_peak_bucket;  /* the .peaks base level: 256 frames */
uint32_t tembra_capture_read_peaks(uint64_t from_index, tembra_peak_bucket* out, uint32_t max_count, uint64_t* out_next_index);
/* copies the buckets [from_index, count) into out (the ring keeps 1024); when from_index has been overwritten the copy starts at the
   oldest kept bucket and *out_next_index − returned < from_index tells the host it missed some (it repeats the previous bucket) */

int64_t tembra_storage_free_bytes(const char* path);      /* statvfs of the containing volume; any thread; −1 on error */

/* Added by the implementation (2026-10-28): iOS reports the session's input latency after the duplex activation, so the
   alignment (input + output latency) is known before the first take; Android's Oboe stream reports its own. */
void tembra_capture_set_input_latency_ns(int64_t nanos);   /* host thread; 0 clears the override */
```

### Appended fields (revision 7)

- `tembra_transport_snapshot`: `int32_t count_in_beats_left` (−1 when no pre-roll runs),
  `uint32_t recording`.
- `tembra_audio_health`: `uint32_t input_overruns`, `uint32_t record_backlog` (the largest
  `backlog_frames` since the take began), `uint32_t input_xruns` (the input stream's own count
  on Android; 0 on iOS).

## Renderer rules (additions to revision 6's `TransportRenderer::render`)

1. Prologue also loads the capture command word (`Idle`/`Armed`/`PreRoll`/`Recording`/`Stopping`)
   and the monitor flag; an output reopen since the last block clears the monitor flag.
2. Pre-roll: while `clock < start`, no clip renders; the metronome voice renders regardless of
   the toggle, anchored at the (negative) beat position; `count_in_beats_left` is published; at
   the block containing `start` the block is split there, capture begins at the split and the
   click follows the toggle afterwards.
3. Recording: the input block of this callback (zero-padded when short) is measured (per-channel
   peak, clip latch, the 64 ms ring), pushed into the FIFO from the take's start offset (after
   `alignment_frames` have been discarded), and, when monitoring, run through `MonitorStrip`
   into the bus after the clips. Segments of the armed track's clips at or after
   `take_start_frames` are skipped while recording.
4. Stopping: capture ends at the block boundary; the monitor path fades 5 ms; the record is
   published with `recording = 0`.
5. Publish `CaptureRecord` through its seqlock beside `TransportRecord`.
6. No allocation, lock, I/O or tracing (the new files `capture_source.cpp`, `monitor_strip.cpp`
   and `peak_ring.h` join `TEMBRA_RT_SOURCES`; the recorder thread's files do not).

## Threading

Control thread: every `tembra_transport_*` and `tembra_capture_*` call under `commands_`
(never the callback). Callback: the input read (Android) or `AudioUnitRender` (iOS), the peak,
the FIFO push, the monitor mix. Recorder thread ("tembra-recorder", user-initiated QoS /
nice 0): FIFO pop, int24 conversion, file writes, the 1 s header refresh and flush, the peak
ring, the frame cap and the free-space check; it ends the take itself by setting the stop reason
and the command word, which the callback observes at its next block. `record_stop` joins the
drain. Retired input scratch and strips are freed on the control thread after two blocks, as the
arrangement is.

## Bindings

- JNI (`tembra_jni.cpp`): `nativeTransportArm/Disarm/SetMonitor/RecordStart(String, int, int, long, long)/RecordCancel/RecordStop(long[]/int[])`,
  `nativeCaptureRead(long[]/float[]/int[])`, `nativeCaptureResetClip`, `nativeCaptureReadPeaks(long, ByteBuffer, int, long[])`,
  `nativeStorageFreeBytes(String)`. The peak buckets cross JNI as one direct `ByteBuffer` reused per instance; the health array grows from 9 to 12.
- cinterop: direct calls; the snapshot structs are allocated once per `NativeAudioEngine`.

## Kotlin seam (`TimelineAudio`, `:shared:engine`)

```kotlin
suspend fun arm(maxChannels: Int): ArmResult            // off the caller's thread; Android opens the input stream on IO, iOS switches the session and restarts the unit on its serial blocking dispatcher, maxChannels bounded by the route's input channels (T098)
suspend fun disarm()                                     // iOS: the unit restarts without its input and the session switches back, on the same blocking dispatcher (T100)
// Revision 6's openTransport() and closeTransport() (now suspend) run on the same dispatcher, `EngineDispatchers.blocking`, one serial
// dispatcher for the whole app that the tuner's and the metronome's view models share (T102, T103), so starts, stops, opens and
// closes keep the order they were asked in across screens.
fun setMonitor(enabled: Boolean, trackIndex: Int)
suspend fun recordStart(config: RecordConfig): RecordStartResult
fun recordCancel()
suspend fun recordStop(): TakeInfo                       // IO dispatcher (blocks ≤ 1 s)
fun readCapture(into: CaptureSnapshot)                   // lock-free; main thread; draw lambdas and the 10 Hz mirror
fun readPeaks(fromIndex: Long, into: PeakBuckets): Long  // returns the next index
fun resetClip()
fun freeBytes(path: String): Long
```

`AudioEvent` gains `InputRouteChanged` and `OutputRouteChanged(isSpeaker)`, emitted by the
actuals from the platform's route callbacks; `AudioRouteMonitor` (`:shared:ui/platform`) is
the query side. The Android actual adds `FOREGROUND_SERVICE_MICROPHONE` handling only through
the existing `BackgroundPlaybackHost` (`keepAlive(PlaybackNotification.recording(name))`); the
iOS actual's `arm` calls `IosAudioSession.activateForRecording()` (`playAndRecord`,
`.measurement`, `[.allowBluetoothA2DP, .defaultToSpeaker]`, 48 kHz preferred, 5 ms buffer) and
`disarm` returns to `activateForPlayback()`; both report `inputLatency + outputLatency +
2 × ioBufferDuration` to the engine through `setReportedLatencies(inputNs, outputNs)`.

`FakeAudioEngine`: `arm` records the call and returns `armResult` (default `Armed(1, 48000)`);
`recordStart` writes a tiny valid WAV at `wavPath` through `takeWriter` and marks a take
running; `recordStop` returns `recordStopResult` (default a 2 s take at the start frame);
`readCapture` copies `nextCapture`; `readPeaks` serves `scriptedPeaks`; every call lands in
`calls`.

## Catch2 tests (written first)

- `wav_writer_test`: header bytes, int24 conversion and clamping, `refreshSizes` leaves a file
  `AudioDecoder` reads at any moment, `finish`.
- `peak_ring_test`: writer/reader with a million buckets, overrun detection, bucket values.
- `capture_source_test`: peak per channel, the 64 ms ring, the clip latch and reset, FIFO push
  with the alignment discard, overrun count on a full FIFO, zero-padded short reads counted after
  warm-up only.
- `take_recorder_test`: drains the FIFO into a file identical to the pushed samples (no gap, no
  repeat; a frame-numbered signal), the frame cap, the free-space stop (a fake `statvfs`), the
  write failure path, the backlog counter, the 1 s header refresh (the file is readable mid-take).
- `monitor_strip_test`: mono and stereo input through gain and audibility into the bus; the fade
  on stop.
- `transport_renderer_test` (extended): pre-roll of one and two bars in 4/4 and 7/8 (click
  count, no clip output, `count_in_beats_left`), capture begins exactly at `start` (the first
  kept input frame is the one aligned to `start + alignment`), Record while playing begins at the
  next block, the armed track's clips are silent at and after the take start and audible before
  it, the monitor mix follows gain, mute and solo, the cancel path restores the position, the
  stop fade.
- `engine_core_state_test` (extended): arm refused outside `TRANSPORT`, disarm on close, a take
  ended by `TRANSPORT_CLOSED`, the fake device's duplex path (feed input while pulling output),
  an input reopen ends the take with `INPUT_LOST`, an output reopen clears monitoring.
- `audio_health_test` (extended): the three new counters.
- `engine_info_test` (ABI 7, version 0.6.0), `trace_poison` (new RT sources listed),
  `trace_shim_test` (the new catalogue count).
- `fake_audio_device` gains `feedInputWhileRendering`, `inputLatencyNanos`, `reopenInput`.
