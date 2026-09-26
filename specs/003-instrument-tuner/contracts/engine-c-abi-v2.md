# Contract: Engine C ABI revision 2 (tuner)

**Header**: `app/engine/include/tembra_engine.h` | **Consumers**: `:shared:engine` (JNI shim, cinterop), Catch2 tests | **Supersedes**: feature 001's revision 1 (descriptor functions unchanged)

Rules unchanged: plain C, no C++ types, no callbacks into the host, safe to call from any
non-real-time thread; nothing below is ever called on an audio callback. Every function returns
`TEMBRA_OK` (0) or a negative `tembra_error`.

```c
#define TEMBRA_ENGINE_ABI_VERSION 2u
/* tembra_engine_abi_version / name / version / describe: as revision 1; version string becomes "0.2.0". */

enum tembra_error { TEMBRA_OK = 0, TEMBRA_ERR_STATE = -1, TEMBRA_ERR_DEVICE = -2, TEMBRA_ERR_ARGUMENT = -3, TEMBRA_ERR_DECODE = -4 };
enum tembra_audio_state { TEMBRA_AUDIO_IDLE = 0, TEMBRA_AUDIO_LISTENING = 1, TEMBRA_AUDIO_TONE = 2 };
uint32_t tembra_audio_state(void);                       /* atomic read */

/* --- Microphone analysis (research R1, R4) --- */
typedef struct tembra_tuner_config {
    uint32_t window_frames;      /* 4096 */
    uint32_t hop_frames;         /* 1024; must divide window_frames */
    float    min_hz;             /* 50 */
    float    max_hz;             /* 1400 */
    float    clarity_threshold;  /* 0.5: below it the reading is "no pitch" */
    float    peak_ratio;         /* 0.9: MPM first-peak rule */
    uint32_t median_size;        /* 5 */
    float    watchdog_seconds;   /* 5: stop capture when tembra_tuner_read is not called for this long; 0 disables */
} tembra_tuner_config;
void    tembra_tuner_default_config(tembra_tuner_config* out);
int32_t tembra_tuner_start(const tembra_tuner_config* config);   /* allocates, opens the input stream, starts the analysis thread */
void    tembra_tuner_stop(void);                                 /* joins the thread, closes the stream, frees; idempotent */

typedef struct tembra_pitch_reading {
    uint64_t sequence;       /* +1 per analysed hop; 0 before the first hop */
    uint32_t state;          /* tembra_audio_state at the time of the read */
    uint32_t sample_rate;    /* actual input stream rate */
    float    frequency_hz;   /* smoothed estimate; meaningful only when has_pitch */
    float    clarity;        /* NSDF peak height 0..1 */
    float    input_peak;     /* linear peak of the last hop, 0..1 */
    uint8_t  has_pitch;      /* 0 = resting (silence, noise, out of range, low clarity) */
    uint8_t  synthetic;      /* 1 while a test signal feeds the analyzer */
} tembra_pitch_reading;
void    tembra_tuner_read(tembra_pitch_reading* out);   /* lock-free seqlock read; also the watchdog heartbeat */

/* --- Test signal: a generated tone in place of the microphone (research R16; debug tooling, all builds) --- */
typedef struct tembra_test_signal {
    double   frequency_hz;      /* already includes the cents offset */
    float    amplitude;         /* peak of the mix, 0..1 (0.5) */
    uint32_t harmonics;         /* number of partials including the fundamental (5) */
    float    fundamental_gain;  /* fundamental relative to the second harmonic (0.25 = -12 dB) */
} tembra_test_signal;
int32_t tembra_tuner_start_test_signal(const tembra_tuner_config* config, const tembra_test_signal* signal);  /* as tembra_tuner_start but with the synthetic device */

/* --- Reference sounds (research R5) --- */
int32_t tembra_tone_start(double frequency_hz, uint32_t duration_ms);  /* sine; duration 0 = loop until stopped */
int32_t tembra_sample_load_wav(const uint8_t* bytes, uint32_t length); /* decodes with dr_wav on the calling thread; returns handle >= 0 or error */
int32_t tembra_sample_play(int32_t handle, uint32_t loop);             /* loop 0 = once in full, 1 = seamless loop */
void    tembra_sample_unload(int32_t handle);                          /* never while playing that handle: stop first */
void    tembra_tone_stop(void);                                        /* stops a sine or a sample with a 5 ms release; idempotent */
```

**State rules**
- `tembra_tuner_start` fails with `TEMBRA_ERR_STATE` while `TEMBRA_AUDIO_TONE`; `tembra_tone_start`
  and `tembra_sample_play` fail the same way while `TEMBRA_AUDIO_LISTENING`. The Kotlin side
  always stops before starting (data-model.md §3); the engine enforces it anyway.
- `tembra_tuner_start` returns `TEMBRA_ERR_DEVICE` when the platform stream cannot open (no
  permission, no device); the state stays `IDLE`.
- The watchdog moves `LISTENING` → `IDLE` on its own; the next `tembra_tuner_read` reports
  `state = IDLE`.
- A one-shot tone or sample returns the state to `IDLE` when it ends; `tembra_audio_state`
  reflects it within one callback block.
- `tembra_tone_start` while a sine at the same frequency (within 0.01 Hz) is active does not restart
  it: it re-arms `duration_ms` (0 = loop) from now and returns `TEMBRA_OK` (spec FR-019, analysis M2).
  A different frequency, or a sample, stops the current sound with its 5 ms release and starts the
  new one after it, so switching strings leaves at most 10 ms of near-silence (spec SC-005).
- `tembra_tuner_start_test_signal` follows every rule of `tembra_tuner_start`; `TEMBRA_ERR_ARGUMENT`
  for a null signal or a frequency outside 20 Hz to 20 kHz.

**Internal C++ (engine/src, not part of the ABI, tested directly by Catch2)**

| File | Type | Responsibility |
|---|---|---|
| `src/platform/audio_device.h` | `tembra::AudioDevice` (interface) | `openInput(rate, onFrames)`, `openOutput(rate, render)`, `close()`, `actualSampleRate()`; implementations `NullAudioDevice` (host), `OboeAudioDevice` (`if(ANDROID)`), `RemoteIoAudioDevice` (`if(IOS)`), `SyntheticAudioDevice` (every platform: generates the harmonic test tone on its own thread and delivers 256-frame blocks at 48 kHz to the input callback in real time; `src/platform/synthetic_audio_device.{h,cpp}`, research R16) |
| `src/tuner/ring_buffer.h` | `tembra::SpscRingBuffer<float>` | single producer (callback), single consumer (analysis thread), power-of-two capacity ≥ 4 × window |
| `src/tuner/seqlock.h` | `tembra::Seqlock<T>` | writer `store`, reader `load` (retry on odd or changed sequence) |
| `src/tuner/pitch_detector.h` | `tembra::PitchDetector` (interface), `PitchEstimate{hz, clarity}` | the algorithm seam (research R2) |
| `src/tuner/mcleod_pitch_detector.{h,cpp}` | `tembra::McLeodPitchDetector` | NSDF via FFT autocorrelation (Signalsmith `dsp/fft.h`) + prefix sums, first peak ≥ `peak_ratio` × max, parabolic interpolation, lag range from min/max Hz |
| `src/tuner/band_pass.{h,cpp}` | `tembra::BandPass` | DC blocker + 2nd-order high-pass 50 Hz + low-pass 1400 Hz biquads |
| `src/tuner/pitch_smoother.{h,cpp}` | `tembra::PitchSmoother` | median of N, exponential average in log frequency (α 0.3), octave-jump guard (2 hops), hold of 4 hops before resting |
| `src/tuner/tuner_analyzer.{h,cpp}` | `tembra::TunerAnalyzer` | owns the above; `push(frames)` from the callback; `analyseAvailable()` from the thread; publishes `tembra_pitch_reading` through the seqlock; watchdog |
| `src/tuner/tone_generator.{h,cpp}` | `tembra::ToneGenerator` | sine, double phase accumulator, 5 ms raised-cosine ramps, amplitude 0.25, one-shot or loop, click-free stop |
| `src/tuner/sample_player.{h,cpp}` | `tembra::SamplePlayer` | in-memory PCM, linear resampler to the stream rate, 10 ms loop crossfade, once or loop, ramped stop |
| `src/tuner/wav_decoder.{h,cpp}` | `tembra::WavDecoder` | dr_wav to mono float + rate |
| `src/tuner/tuner_engine.{h,cpp}` | `tembra::TunerEngine` (process singleton behind the C ABI) | state machine, owns device, analyzer, generator, player, sample table |

**Threading**: `push` only from the input callback; `render` only from the output callback;
everything else from the host thread that calls the C ABI. No mutex is taken on either callback;
the analysis thread sleeps 4 ms between checks.

**Platform bindings**
- Android JNI (`tembra_jni.cpp`), class `audio.tembra.engine.NativeAudioEngine`:
  `nativeTunerStart(int window, int hop, float minHz, float maxHz, float clarity, float peakRatio, int median, float watchdog): Int`,
  `nativeTunerStop()`, `nativeTunerRead(FloatArray out /* [state, sampleRate, frequencyHz, clarity, inputPeak, hasPitch] */): Long /* sequence */`,
  `nativeToneStart(double hz, int durationMs): Int`, `nativeSampleLoadWav(ByteArray): Int`,
  `nativeSamplePlay(int handle, boolean loop): Int`, `nativeSampleUnload(int)`, `nativeToneStop()`,
  `nativeAudioState(): Int`, `nativeTunerStartTestSignal(double hz, float amplitude, int harmonics, float fundamentalGain, int window, int hop, float minHz, float maxHz, float clarity, float peakRatio, int median, float watchdog): Int`. `nativeTunerRead` fills seven floats (the seventh is `synthetic`) with `SetFloatArrayRegion` on the caller's array; no allocation per call.
- iOS cinterop: the struct and functions are generated from the header; the Kotlin actual keeps one
  `nativeHeap.alloc<tembra_pitch_reading>()` and one `tembra_tuner_config` for its lifetime.
  `tembra_engine.def` adds `linkerOpts = -lc++ -framework AudioToolbox -framework CoreAudio`.

**Tests (Catch2, `engine/tests/`)**: `engine_info_test` (ABI 2, version 0.2.0, describe unchanged
in shape); `seqlock_test`; `ring_buffer_test`; `band_pass_test`; `mpm_test` (every semitone A1 to E5,
offsets, octave, silence, noise, a 2 kHz tone above the range, a hard-clipped E2); `tuner_analyzer_test`
(hop assembly, response within 250 ms, resting within 500 ms, octave guard, consecutive readings on a
steady tone within 1 cent, watchdog); `tone_generator_test` (pitch, ramps, loop seam, click-free stop,
re-arm without restart, at most 10 ms of near-silence when switching frequency); `sample_player_test`;
`recordings_on_pitch_test`; `tuner_engine_state_test` (state rules above with `NullAudioDevice` replaced
by a `FakeAudioDevice` that drives callbacks synchronously, plus the synthetic device producing a reading
within 1 cent with `synthetic == 1`). Details in research R12.
