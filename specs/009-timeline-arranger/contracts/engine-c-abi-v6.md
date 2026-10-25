# Contract: Engine C ABI revision 6 — transport, clip sources, mixer, offline audio tools

**Header**: `app/engine/include/tembra_engine.h`, `TEMBRA_ENGINE_ABI_VERSION 6u`; engine version
`0.5.0`. Every revision-5 function is unchanged. Naming, error codes and threading rules follow
the header's conventions (`tembra_<area>_<verb>`, `int32_t` → `TEMBRA_OK` or a negative
`tembra_error`, `void` idempotent stops, `const struct*` in, `out*` filled, no callback into the
host, nothing ever called on the audio callback). Research: R2–R7, R10, R11.

## States

```c
enum tembra_audio_state { TEMBRA_AUDIO_IDLE = 0, TEMBRA_AUDIO_LISTENING = 1, TEMBRA_AUDIO_TONE = 2,
                          TEMBRA_AUDIO_METRONOME = 3, TEMBRA_AUDIO_TRANSPORT = 4 };
```

`TRANSPORT` means the output stream is open for a project (stopped or playing). It excludes the
other states: `tembra_transport_open` returns `TEMBRA_ERR_STATE` unless `IDLE`;
`tembra_tuner_start`, `tembra_tone_start`, `tembra_sample_play`, `tembra_metronome_start` and
`tembra_metronome_preview` return `TEMBRA_ERR_STATE` while `TRANSPORT`. The standalone metronome's
`tembra_metronome_set_grid`, `tembra_metronome_select_sound`, `tembra_metronome_load_custom_sound`
and `tembra_metronome_set_output_latency_ns` remain callable in any state (they configure the one
click the transport also uses).

## Transport

```c
typedef struct tembra_transport_config {
    uint32_t reserved;              /* 0; room for later options */
} tembra_transport_config;

int32_t tembra_transport_open(const tembra_transport_config* config);   /* IDLE → TRANSPORT: opens the output at the device rate, creates the renderer, position 0, stopped */
void    tembra_transport_close(void);                                    /* pauses with the 5 ms release, joins the streamer, closes the stream → IDLE; idempotent */

int32_t tembra_transport_set_tempo(uint32_t bpm, uint32_t beats_per_bar, uint32_t beat_unit);  /* 40..240, 1..32, 4|8; applies at the next block; re-anchors the click */
int32_t tembra_transport_set_arrangement(const tembra_arrangement* arrangement);              /* validated, copied into a TripleBuffer snapshot; applies at the next block */
int32_t tembra_transport_set_track(uint32_t index, float gain_linear, uint32_t audible);      /* fast path: one track's gain target (0..2.0) and audibility without a new arrangement */
int32_t tembra_transport_set_master(float gain_linear);                                       /* 0..1; smoothed */
int32_t tembra_transport_set_metronome(uint32_t enabled);                                     /* 1: the click joins the mix at the next beat, anchored; 0: releases the voice */

int32_t tembra_transport_play(void);            /* primes every clip within 2 s of the position (blocking, control thread, target < 50 ms), then starts; ERR_STATE unless TRANSPORT */
int32_t tembra_transport_pause(void);           /* 5 ms release, keeps the position; idempotent while stopped */
int32_t tembra_transport_seek_beats(double beats);  /* any TRANSPORT state; playing: silent re-prime and resume from the new position within 100 ms; stopped: moves the position */
```

### Arrangement

```c
#define TEMBRA_MAX_TRACKS 64u
#define TEMBRA_MAX_CLIPS  1024u

typedef struct tembra_track_state { float gain_linear; uint32_t audible; } tembra_track_state;

typedef struct tembra_clip_state {
    uint32_t track;          /* index into tracks */
    int32_t  source;         /* clip source handle (tembra_clip_source_open) */
    double   start_beats;    /* position in beats of the beat unit */
    int64_t  offset_frames;  /* into the source, at the source rate */
    int64_t  length_frames;  /* at the source rate; > 0 */
} tembra_clip_state;

typedef struct tembra_arrangement {
    uint32_t           track_count;              /* ≤ 64 */
    uint32_t           clip_count;               /* ≤ 1024 */
    tembra_track_state tracks[TEMBRA_MAX_TRACKS];
    tembra_clip_state  clips[TEMBRA_MAX_CLIPS];  /* any order; the engine sorts by start per track */
} tembra_arrangement;
```

Validation (`ERR_ARGUMENT`): counts within bounds, every `track < track_count`, every `source` an
open handle, `offset + length ≤ source frames`, `start_beats ≥ 0`, gains finite. Two clips on
one track may overlap in time after a tempo change; the renderer gives the later start
precedence (spec FR-027) by cutting the earlier clip at the later one's start.

### Clip sources (opened on the host's IO thread, never on the callback)

```c
typedef struct tembra_clip_info { uint32_t channels; uint32_t sample_rate; int64_t frames; uint32_t format; } tembra_clip_info; /* format: tembra_audio_format */

int32_t tembra_clip_source_open(const char* path, tembra_clip_info* out_info);  /* ma_decoder on the file: f32, source channels, output at the stream rate; MP3 with a seek table; returns handle ≥ 0 or ERR_DECODE / ERR_ARGUMENT; allowed in IDLE and TRANSPORT */
void    tembra_clip_source_close(int32_t handle);                                /* ERR-free; refused silently while an arrangement references it (the host removes it first) */
```

A source is streamed by the `DiskStreamer`; its fifo holds 16 384 frames per channel; `prime`
positions the decoder at the first frame the arrangement needs and fills the fifo; the callback
pops; a dry fifo renders silence and bumps `starvation`.

### Telemetry

```c
typedef struct tembra_transport_snapshot {
    uint32_t playing;
    int64_t  position_frames;      /* at block start, stream rate */
    double   position_beats;       /* position_frames through the tempo map */
    double   audible_seconds;      /* position + elapsed since block − output latency, at call time */
    int64_t  block_time_ns;        /* monotonic time of the block that produced position_frames */
    int64_t  latency_ns;           /* the output latency used */
    uint32_t sample_rate;
} tembra_transport_snapshot;

void tembra_transport_read(tembra_transport_snapshot* out);  /* lock-free seqlock read, safe every frame; zeros when not TRANSPORT */
```

### Health (revision 6 fields appended to `tembra_audio_health`)

`uint32_t avg_us` (mean callback duration since open), `uint32_t starvation` (fifo underruns since the transport opened),
`uint32_t clips_active` (clips rendered in the last block). Existing fields unchanged.

## Offline audio tools (any thread, no stream needed)

```c
enum tembra_audio_format { TEMBRA_FORMAT_UNKNOWN = 0, TEMBRA_FORMAT_WAV = 1, TEMBRA_FORMAT_FLAC = 2, TEMBRA_FORMAT_MP3 = 3 };

typedef struct tembra_audio_info { uint32_t format; uint32_t channels; uint32_t sample_rate; int64_t frames; } tembra_audio_info;

int32_t tembra_audio_inspect(const char* path, tembra_audio_info* out);          /* opens and closes a decoder; ERR_DECODE for anything miniaudio cannot read */
int32_t tembra_audio_overview_build(const char* path, const char* out_path);     /* decodes once, writes the .peaks file (format below); flushes the header every 10 s of audio; returns when complete */
```

### `.peaks` file format (little-endian)

Header (32 bytes): magic `"TPK1"`, `uint16 version` (1), `uint16 channels`, `uint32 levels` (4),
`uint32 base_bucket` (256), `int64 frames`, `uint64 completed_base_buckets`. Then for each level
(256, 1024, 4096, 16 384 frames per bucket): `int8 min, int8 max` per channel per bucket, levels
stored consecutively; a reader draws only the first `completed_base_buckets` of the base level
(and the derived count of each coarser level) while the writer is still running.

## Renderer rules (C++ `TransportRenderer::render(out, frames)`)

1. Prologue: load the newest arrangement, tempo and track-state snapshots; apply a pending
   seek (set the clock, mark every source for re-prime — the streamer, not the callback, primes);
   apply play/pause commands.
2. If playing: compute the frames of every clip start and end inside `[pos, pos + frames)`
   through the tempo map, split the block at those frames, and for each segment pop each
   active clip's frames from its fifo (silence if dry), run the track strip (gain ramp ×
   audibility), sum into the master bus; the metronome voice (if enabled) renders into scratch
   and is added after the bus; advance the clock by `frames`.
3. If stopped: output silence (the release tail of the previous block excepted).
4. Publish the `TransportRecord` seqlock; increment `blocks_`.
5. No allocation, lock, I/O or tracing (the file is in `TEMBRA_RT_SOURCES`).

## Threading

Control thread: every `tembra_transport_*`, `tembra_clip_source_*` call (under `EngineCore`'s
`commands_` mutex, never on the callback). Streamer thread: decoding and fifo writes; `prime` is
synchronous for `play` and `seek` (bounded by two seconds of audio per clip). Callback: pops,
mixes, publishes. Retired arrangements and sources are freed on the control thread once
`blocksRendered()` has moved past their retirement by two blocks (`retireLocked`/`sweepRetiredLocked`).

## Bindings

- JNI (`tembra_jni.cpp`): `nativeTransportOpen/Close/SetTempo/SetArrangement(ByteBuffer)/SetTrack/SetMaster/SetMetronome/Play/Pause/SeekBeats/Read(long[]/double[])`, `nativeClipSourceOpen/Close`, `nativeAudioInspect`, `nativeAudioOverviewBuild`. The arrangement crosses JNI as a direct `ByteBuffer` laid out as the C struct (no per-clip JNI calls).
- cinterop: direct calls; the arrangement is a `tembra_arrangement` allocated once per `NativeAudioEngine` and refilled.

## Catch2 tests (written first)

`tempo_map_test`, `transport_clock_test`, `spsc_fifo_test`, `disk_streamer_test` (fake decoder,
prime/stream/starve), `clip_source_test` (decoder on generated WAV/FLAC/MP3 fixtures; seek
accuracy; rates 8 k–192 k), `track_strip_test` (ramp length 10 ms ± 1 block, audibility),
`master_bus_test` (sum, clamp, master ramp), `transport_renderer_test` (clip starts within one
frame at 96/256/1024; mid-clip start; a split pair renders identically to the whole; overlap
precedence; metronome anchored within one frame of the audio; pause release; seek while playing),
`audio_decoder_test` (inspect matrix: refusals and acceptances), `overview_builder_test` (bucket
values, pyramid, partial header), `engine_core_state_test` (+ TRANSPORT exclusivity),
`engine_info_test` (ABI 6, version 0.5.0), `trace_poison` (new RT sources listed).
