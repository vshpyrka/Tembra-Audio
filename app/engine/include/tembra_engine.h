/*
 * Tembra Audio engine C ABI, revision 6.
 *
 * This header is the only interface the Kotlin side ever sees (JNI shim on Android, cinterop on
 * iOS). Rules: plain C, no C++ types, no callbacks into the host, every returned pointer has static
 * storage and is never freed by the caller, every function is safe from any thread that is not an
 * audio callback and nothing here is ever called on one. Revision 1 (descriptor functions) is
 * specs/001-project-scaffold/contracts/engine-c-abi.md; revision 2 (the tuner) is
 * specs/003-instrument-tuner/contracts/engine-c-abi-v2.md; revision 3 (the metronome: TEMBRA_ERR_SILENT,
 * TEMBRA_AUDIO_METRONOME and the tembra_metronome_* functions; every revision 2 function unchanged) is
 * specs/004-metronome/contracts/engine-c-abi-v3.md; revision 4 (the tuner's noise gate: tembra_tuner_set_gate and
 * the input_level_db and gate_open fields of tembra_pitch_reading; every revision 3 function unchanged) is
 * specs/006-tuner-noise-gate/contracts/engine-c-abi-v4.md; revision 5 (the audio-thread health counters:
 * tembra_audio_health and tembra_engine_audio_health; every revision 4 function unchanged) is
 * specs/007-performance-pipeline/contracts/engine-audio-health.md; revision 6 (the project transport:
 * TEMBRA_AUDIO_TRANSPORT, the tembra_transport_* and tembra_clip_source_* functions, the offline tembra_audio_inspect and
 * tembra_audio_overview_build, and the avg_us, starvation and clips_active fields of tembra_audio_health; every
 * revision 5 function unchanged) is specs/009-timeline-arranger/contracts/engine-c-abi-v6.md.
 */
#ifndef TEMBRA_ENGINE_H
#define TEMBRA_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ABI revision of this header. Incremented whenever a function is added, removed or changed. */
#define TEMBRA_ENGINE_ABI_VERSION 6u
uint32_t tembra_engine_abi_version(void);

/* Human-readable engine name, e.g. "Tembra Engine". Static storage. */
const char* tembra_engine_name(void);

/* Engine semantic version "major.minor.patch", e.g. "0.4.0". Static storage. */
const char* tembra_engine_version(void);

/*
 * One-line description: "<name> <version> (<lib> <ver>; <lib> <ver>; ...)".
 * Lists every third-party native library compiled into the engine with its version.
 * Static storage, ASCII, no trailing newline.
 */
const char* tembra_engine_describe(void);

/* Every command below returns TEMBRA_OK or a negative tembra_error. */
enum tembra_error {
    TEMBRA_OK = 0,
    TEMBRA_ERR_STATE = -1,    /* the command is not allowed in the current audio state */
    TEMBRA_ERR_DEVICE = -2,   /* the platform stream could not open (no permission, no device) */
    TEMBRA_ERR_ARGUMENT = -3, /* null pointer or a value outside its range */
    TEMBRA_ERR_DECODE = -4,   /* the bytes are not a WAV dr_wav can decode */
    TEMBRA_ERR_SILENT = -5    /* the file decodes but holds no sound above -60 dBFS */
};

/*
 * At most one stream is open: listening, a tone, the metronome and the transport never overlap (spec FR-006 of
 * feature 003; data-model.md §6). TRANSPORT (revision 6) means the output stream is open for a project, stopped or
 * playing; tembra_transport_open returns TEMBRA_ERR_STATE unless IDLE, and tembra_tuner_start, tembra_tone_start,
 * tembra_sample_play, tembra_metronome_start and tembra_metronome_preview return TEMBRA_ERR_STATE while TRANSPORT.
 */
enum tembra_audio_state {
    TEMBRA_AUDIO_IDLE = 0,
    TEMBRA_AUDIO_LISTENING = 1,
    TEMBRA_AUDIO_TONE = 2,
    TEMBRA_AUDIO_METRONOME = 3,
    TEMBRA_AUDIO_TRANSPORT = 4
};
uint32_t tembra_audio_state(void); /* atomic read */

/* --- Microphone analysis (research R1, R4) --- */
typedef struct tembra_tuner_config {
    uint32_t window_frames;     /* 4096 */
    uint32_t hop_frames;        /* 1024; must divide window_frames */
    float    min_hz;            /* 50 */
    float    max_hz;            /* 1400 */
    float    clarity_threshold; /* 0.5: below it the reading is "no pitch" */
    float    peak_ratio;        /* 0.9: MPM first-peak rule */
    uint32_t median_size;       /* 5 */
    float    watchdog_seconds;  /* 5: stop capture when tembra_tuner_read is not called for this long; 0 disables */
} tembra_tuner_config;
void    tembra_tuner_default_config(tembra_tuner_config* out);
int32_t tembra_tuner_start(const tembra_tuner_config* config); /* allocates, opens the input stream, starts the analysis thread */
void    tembra_tuner_stop(void);                               /* joins the thread, closes the stream, frees; idempotent */

typedef struct tembra_pitch_reading {
    uint64_t sequence;     /* +1 per analysed hop; 0 before the first hop */
    uint32_t state;        /* tembra_audio_state at the time of the read */
    uint32_t sample_rate;  /* actual input stream rate */
    float    frequency_hz; /* smoothed estimate; meaningful only when has_pitch */
    float    clarity;      /* NSDF peak height 0..1 */
    float    input_peak;   /* linear peak of the last hop before the pre-filter, 0..1 */
    float    input_level_db; /* RMS of the analysis window after the pre-filter, in dB below full scale; floor -120 */
    uint8_t  has_pitch;    /* 0 = resting (silence, noise, out of range, low clarity, held back by the gate) */
    uint8_t  synthetic;    /* 1 while a test signal feeds the analyzer */
    uint8_t  gate_open;    /* 1 while sound is being read (no gate, or the gate is open); 0 while it is held back */
} tembra_pitch_reading;
void    tembra_tuner_read(tembra_pitch_reading* out); /* lock-free seqlock read; also the watchdog heartbeat */

/* --- Noise gate (feature 006, research R1-R5) --- */
int32_t tembra_tuner_set_gate(uint32_t enabled, float open_db);
        /* Allowed in every audio state. The value is kept for the life of the engine: a running analysis uses it from
           its next hop, and every later tembra_tuner_start / tembra_tuner_start_test_signal from its first hop.
           enabled == 0: no gate (open_db is ignored). enabled != 0: open_db must be finite and within -120..0,
           otherwise TEMBRA_ERR_ARGUMENT and the previous value stays. Initially: no gate. */

/* --- Test signal: a generated tone in place of the microphone (research R16; debug tooling, all builds) --- */
typedef struct tembra_test_signal {
    double   frequency_hz;     /* already includes the cents offset */
    float    amplitude;        /* peak of the mix, 0..1 (0.5) */
    uint32_t harmonics;        /* number of partials including the fundamental (5) */
    float    fundamental_gain; /* fundamental relative to the second harmonic (0.25 = -12 dB) */
} tembra_test_signal;
int32_t tembra_tuner_start_test_signal(const tembra_tuner_config* config, const tembra_test_signal* signal); /* as tembra_tuner_start but with the synthetic device */

/* --- Reference sounds (research R5) --- */
int32_t tembra_tone_start(double frequency_hz, uint32_t duration_ms);  /* sine; duration 0 = loop until stopped; same frequency re-arms */
int32_t tembra_sample_load_wav(const uint8_t* bytes, uint32_t length); /* decodes with dr_wav on the calling thread; returns handle >= 0 or error */
int32_t tembra_sample_play(int32_t handle, uint32_t loop);             /* loop 0 = once in full, 1 = seamless loop */
void    tembra_sample_unload(int32_t handle);                          /* never while playing that handle: stop first */
void    tembra_tone_stop(void);                                        /* stops a sine or a sample with a 5 ms release; idempotent */

/* --- Metronome (research R1-R5, R7) --- */
enum tembra_click_level { TEMBRA_CLICK_SUB = 0, TEMBRA_CLICK_CLICK = 1, TEMBRA_CLICK_ACCENT = 2 };
enum tembra_click_sound {
    TEMBRA_SOUND_CLASSIC_WOOD = 0,
    TEMBRA_SOUND_808_RIM = 1,
    TEMBRA_SOUND_DIGITAL_BEEP = 2,
    TEMBRA_SOUND_COWBELL = 3,
    TEMBRA_SOUND_MECHANICAL = 4,
    TEMBRA_SOUND_CUSTOM = 5
};

/* The bar as a flat list of tick levels, computed in Kotlin (research R2). */
typedef struct tembra_metronome_grid {
    double   bpm;             /* 40..240; the beat is the signature's lower-number note */
    uint32_t ticks_per_beat;  /* 1, 2, 3 or 4 */
    uint32_t ticks_per_bar;   /* beats x ticks_per_beat, 1..32 */
    uint8_t  levels[32];      /* tembra_click_level per tick, tick 0 = beat 1 */
} tembra_metronome_grid;
int32_t tembra_metronome_set_grid(const tembra_metronome_grid* grid); /* any state; published to the renderer; applies from the next block (R1 maps the position) */
int32_t tembra_metronome_select_sound(uint32_t sound);                /* tembra_click_sound; TEMBRA_SOUND_CUSTOM needs a loaded custom sound (else TEMBRA_ERR_STATE) */

/* What the custom sound preparation kept (research R4). */
typedef struct tembra_custom_sound_info {
    uint32_t source_rate;     /* as decoded */
    uint32_t source_channels;
    uint32_t source_bits;     /* 32 for float */
    uint32_t kept_frames;     /* after onset alignment and the 2 s cap, at the source rate */
    float    trimmed_ms;      /* leading silence removed */
    float    kept_ms;
} tembra_custom_sound_info;
int32_t tembra_metronome_load_custom_sound(const char* wav_path, tembra_custom_sound_info* out_info);
        /* decodes (dr_wav, first 30 s) and prepares on the calling thread; publishes the custom click set (R4);
           TEMBRA_ERR_ARGUMENT (null / cannot open), TEMBRA_ERR_DECODE (not PCM WAV), TEMBRA_ERR_SILENT */
void    tembra_metronome_unload_custom_sound(void);  /* falls back to Classic Wood if custom was selected */

int32_t tembra_metronome_start(void);  /* opens the output stream, prepares the built-in sets at its rate on first open, state METRONOME, beat 1 at frame 0 of the first block (R1) */
void    tembra_metronome_stop(void);   /* 5 ms release, closes the stream, state IDLE; idempotent (R1, R5) */
int32_t tembra_metronome_preview(uint32_t level);  /* one click of the selected sound at `level`; IDLE only; state TONE until it ends */
void    tembra_metronome_set_output_latency_ns(int64_t nanos);  /* iOS reports AVAudioSession.outputLatency + ioBufferDuration; Android passes 0 = ask the device (R7) */

/* The last emitted tick, latency-compensated for the beat light (research R7). */
typedef struct tembra_metronome_tick {
    uint64_t tick_index;      /* +1 per emitted tick since start; 0 before the first */
    uint32_t beat_index;      /* 0-based beat of the last emitted tick */
    uint32_t level;           /* tembra_click_level of that tick */
    int64_t  audible_in_ns;   /* when that tick reaches the listener, relative to now (negative = already audible) */
    int64_t  beat_period_ns;  /* 60e9 / bpm at the time of the tick */
    uint32_t running;         /* 1 while state == METRONOME */
    uint32_t sample_rate;     /* stream rate */
} tembra_metronome_tick;
void    tembra_metronome_read(tembra_metronome_tick* out);  /* lock-free seqlock read; computes audible_in_ns against the monotonic clock at call time */

/* --- Audio-thread health (feature 007, contracts/engine-audio-health.md; revision 6 fields appended) --- */
typedef struct tembra_audio_health {
    uint64_t callbacks;     /* total render and capture callbacks since stream start      */
    uint32_t last_us;       /* duration of the most recent callback, microseconds         */
    uint32_t max_us;        /* maximum callback duration since stream start               */
    uint32_t over_budget;   /* callbacks whose duration exceeded the buffer period        */
    uint32_t xruns;         /* underruns + overruns reported by the platform stream       */
    uint8_t  realtime;      /* 1 when the audio thread holds real-time status             */
    uint8_t  _reserved[3];
    uint32_t avg_us;        /* revision 6: mean callback duration since stream start, microseconds */
    uint32_t starvation;    /* revision 6: clip fifos found dry by the callback (silence rendered)  */
    uint32_t clips_active;  /* revision 6: clips rendered in the last block                        */
} tembra_audio_health;
/* Non-real-time. Copies the counters into *out (the callback writes them with atomic stores; the xrun count is
   sampled from the platform stream on the calling thread). Returns 0, -1 when no stream is open, or
   TEMBRA_ERR_ARGUMENT for a null pointer. The engine is process-wide, hence no handle argument. */
int32_t tembra_engine_audio_health(tembra_audio_health* out);

/* --- Project transport (revision 6, specs/009-timeline-arranger/contracts/engine-c-abi-v6.md; research R2-R7) --- */
typedef struct tembra_transport_config {
    uint32_t reserved;              /* 0; room for later options */
} tembra_transport_config;

int32_t tembra_transport_open(const tembra_transport_config* config);   /* IDLE -> TRANSPORT: opens the output at the device rate, creates the renderer, position 0, stopped */
void    tembra_transport_close(void);                                    /* pauses with the 5 ms release, joins the streamer, closes the stream -> IDLE; idempotent */

int32_t tembra_transport_set_tempo(uint32_t bpm, uint32_t beats_per_bar, uint32_t beat_unit);  /* 40..240, 1..32, 4|8; applies at the next block; re-anchors the click */
int32_t tembra_transport_set_track(uint32_t index, float gain_linear, uint32_t audible);      /* fast path: one track's gain target (0..2.0) and audibility without a new arrangement */
int32_t tembra_transport_set_master(float gain_linear);                                       /* 0..1; smoothed over 10 ms */
int32_t tembra_transport_set_metronome(uint32_t enabled);                                     /* 1: the click joins the mix at the next beat, anchored; 0: releases the voice */

int32_t tembra_transport_play(void);                /* primes every clip within 2 s of the position (blocking, control thread, target < 50 ms), then starts; ERR_STATE unless TRANSPORT */
int32_t tembra_transport_pause(void);               /* 5 ms release, keeps the position; idempotent while stopped */
int32_t tembra_transport_seek_beats(double beats);  /* any TRANSPORT state; playing: silent re-prime and resume from the new position within 100 ms; stopped: moves the position */

/* The arrangement: the derived real-time view of the project (Constitution V); the Kotlin document stays the authority. */
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
    uint32_t           track_count;              /* <= 64 */
    uint32_t           clip_count;               /* <= 1024 */
    tembra_track_state tracks[TEMBRA_MAX_TRACKS];
    tembra_clip_state  clips[TEMBRA_MAX_CLIPS];  /* any order; the engine sorts by start per track */
} tembra_arrangement;
/* Validated (ERR_ARGUMENT: counts within bounds, every track < track_count, every source an open handle,
   offset + length <= source frames, start_beats >= 0, gains finite), copied into a triple-buffer snapshot; applies at
   the next block. Two clips on one track may overlap after a tempo change: the later start takes precedence. */
int32_t tembra_transport_set_arrangement(const tembra_arrangement* arrangement);

/* --- Clip sources (opened on the host's IO thread, never on the callback) --- */
enum tembra_audio_format { TEMBRA_FORMAT_UNKNOWN = 0, TEMBRA_FORMAT_WAV = 1, TEMBRA_FORMAT_FLAC = 2, TEMBRA_FORMAT_MP3 = 3 };

typedef struct tembra_clip_info { uint32_t channels; uint32_t sample_rate; int64_t frames; uint32_t format; } tembra_clip_info; /* format: tembra_audio_format */

int32_t tembra_clip_source_open(const char* path, tembra_clip_info* out_info);  /* ma_decoder on the file: f32, source channels, output at the stream rate; MP3 with a seek table; returns handle >= 0 or ERR_DECODE / ERR_ARGUMENT; allowed in IDLE and TRANSPORT */
void    tembra_clip_source_close(int32_t handle);                                /* error-free; refused silently while an arrangement references it (the host removes it first) */

/* --- Transport telemetry --- */
typedef struct tembra_transport_snapshot {
    uint32_t playing;
    int64_t  position_frames;      /* at block start, stream rate */
    double   position_beats;       /* position_frames through the tempo map */
    double   audible_seconds;      /* position + elapsed since block - output latency, at call time */
    int64_t  block_time_ns;        /* monotonic time of the block that produced position_frames */
    int64_t  latency_ns;           /* the output latency used */
    uint32_t sample_rate;
} tembra_transport_snapshot;

void tembra_transport_read(tembra_transport_snapshot* out);  /* lock-free seqlock read, safe every frame; zeros when not TRANSPORT */

/* --- Offline audio tools (any thread, no stream needed) --- */
typedef struct tembra_audio_info { uint32_t format; uint32_t channels; uint32_t sample_rate; int64_t frames; } tembra_audio_info;

int32_t tembra_audio_inspect(const char* path, tembra_audio_info* out);          /* opens and closes a decoder; ERR_DECODE for anything miniaudio cannot read */
int32_t tembra_audio_overview_build(const char* path, const char* out_path);     /* decodes once, writes the .peaks file (header "TPK1", int8 min/max per channel at 256/1024/4096/16384 frames per bucket); flushes the header every 10 s of audio; returns when complete */

#ifdef __cplusplus
}
#endif

#endif /* TEMBRA_ENGINE_H */
