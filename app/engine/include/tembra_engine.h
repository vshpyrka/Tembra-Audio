/*
 * Tembra Audio engine C ABI, revision 2.
 *
 * This header is the only interface the Kotlin side ever sees (JNI shim on Android, cinterop on
 * iOS). Rules: plain C, no C++ types, no callbacks into the host, every returned pointer has static
 * storage and is never freed by the caller, every function is safe from any thread that is not an
 * audio callback and nothing here is ever called on one. Revision 1 (descriptor functions) is
 * specs/001-project-scaffold/contracts/engine-c-abi.md; revision 2 (the tuner) is
 * specs/003-instrument-tuner/contracts/engine-c-abi-v2.md.
 */
#ifndef TEMBRA_ENGINE_H
#define TEMBRA_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ABI revision of this header. Incremented whenever a function is added, removed or changed. */
#define TEMBRA_ENGINE_ABI_VERSION 2u
uint32_t tembra_engine_abi_version(void);

/* Human-readable engine name, e.g. "Tembra Engine". Static storage. */
const char* tembra_engine_name(void);

/* Engine semantic version "major.minor.patch", e.g. "0.2.0". Static storage. */
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
    TEMBRA_ERR_DECODE = -4    /* the bytes are not a WAV dr_wav can decode */
};

/* At most one stream is open: listening and a tone never overlap (spec FR-006). */
enum tembra_audio_state {
    TEMBRA_AUDIO_IDLE = 0,
    TEMBRA_AUDIO_LISTENING = 1,
    TEMBRA_AUDIO_TONE = 2
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
    float    input_peak;   /* linear peak of the last hop, 0..1 */
    uint8_t  has_pitch;    /* 0 = resting (silence, noise, out of range, low clarity) */
    uint8_t  synthetic;    /* 1 while a test signal feeds the analyzer */
} tembra_pitch_reading;
void    tembra_tuner_read(tembra_pitch_reading* out); /* lock-free seqlock read; also the watchdog heartbeat */

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

#ifdef __cplusplus
}
#endif

#endif /* TEMBRA_ENGINE_H */
