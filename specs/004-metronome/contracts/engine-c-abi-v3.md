# Contract: Engine C ABI revision 3 (metronome)

> Superseded by feature 006's revision 4 ([contract](../../006-tuner-noise-gate/contracts/engine-c-abi-v4.md)): every revision 3 function is unchanged; the reading gains the input level and the gate's state, and `tembra_tuner_set_gate` is new.

**Header**: `app/engine/include/tembra_engine.h` | **Consumers**: `:shared:engine` (JNI shim, cinterop), Catch2 tests | **Supersedes**: feature 003's revision 2 (every revision 2 function unchanged)

Rules unchanged: plain C, no C++ types, no callbacks into the host, safe to call from any
non-real-time thread; nothing below is ever called on an audio callback. Every function returns
`TEMBRA_OK` (0) or a negative `tembra_error`.

```c
#define TEMBRA_ENGINE_ABI_VERSION 3u
/* tembra_engine_abi_version / name / version / describe: as before; version string becomes "0.3.0". */

enum tembra_error { TEMBRA_OK = 0, TEMBRA_ERR_STATE = -1, TEMBRA_ERR_DEVICE = -2, TEMBRA_ERR_ARGUMENT = -3, TEMBRA_ERR_DECODE = -4,
                    TEMBRA_ERR_SILENT = -5 /* the file decodes but holds no sound above -60 dBFS */ };
enum tembra_audio_state { TEMBRA_AUDIO_IDLE = 0, TEMBRA_AUDIO_LISTENING = 1, TEMBRA_AUDIO_TONE = 2, TEMBRA_AUDIO_METRONOME = 3 };

/* --- Metronome (research R1–R5, R7) --- */
enum tembra_click_level { TEMBRA_CLICK_SUB = 0, TEMBRA_CLICK_CLICK = 1, TEMBRA_CLICK_ACCENT = 2 };
enum tembra_click_sound { TEMBRA_SOUND_CLASSIC_WOOD = 0, TEMBRA_SOUND_808_RIM = 1, TEMBRA_SOUND_DIGITAL_BEEP = 2,
                          TEMBRA_SOUND_COWBELL = 3, TEMBRA_SOUND_MECHANICAL = 4, TEMBRA_SOUND_CUSTOM = 5 };

typedef struct tembra_metronome_grid {
    double   bpm;             /* 40..240; the beat is the signature's lower-number note */
    uint32_t ticks_per_beat;  /* 1, 2, 3 or 4 */
    uint32_t ticks_per_bar;   /* beats × ticks_per_beat, 1..32 */
    uint8_t  levels[32];      /* tembra_click_level per tick, tick 0 = beat 1 */
} tembra_metronome_grid;
int32_t tembra_metronome_set_grid(const tembra_metronome_grid* grid);   /* any state; published to the renderer; applies from the next block */
int32_t tembra_metronome_select_sound(uint32_t sound);                  /* tembra_click_sound; TEMBRA_SOUND_CUSTOM needs a loaded custom sound */

typedef struct tembra_custom_sound_info {
    uint32_t source_rate; uint32_t source_channels; uint32_t source_bits;   /* as decoded (bits 32 for float) */
    uint32_t kept_frames;  float trimmed_ms; float kept_ms;                  /* after onset alignment and the 2 s cap, at the source rate */
} tembra_custom_sound_info;
int32_t tembra_metronome_load_custom_sound(const char* wav_path, tembra_custom_sound_info* out_info);
        /* decodes (dr_wav, first 30 s) and prepares on the calling thread; publishes the custom ClickSet;
           TEMBRA_ERR_ARGUMENT (null / cannot open), TEMBRA_ERR_DECODE (not PCM WAV), TEMBRA_ERR_SILENT */
void    tembra_metronome_unload_custom_sound(void);                     /* falls back to Classic Wood if custom was selected */

int32_t tembra_metronome_start(void);   /* opens the output stream, prepares the built-in sets at its rate on first open, state METRONOME, beat 1 at frame 0 of the first block */
void    tembra_metronome_stop(void);    /* 5 ms release, closes the stream, state IDLE; idempotent */
int32_t tembra_metronome_preview(uint32_t level);   /* one click of the selected sound at `level`; IDLE only; state TONE until it ends */
void    tembra_metronome_set_output_latency_ns(int64_t nanos);   /* iOS reports AVAudioSession.outputLatency + ioBufferDuration; Android passes 0 = ask the device */

typedef struct tembra_metronome_tick {
    uint64_t tick_index;      /* +1 per emitted tick since start; 0 before the first */
    uint32_t beat_index;      /* 0-based beat of the last emitted tick */
    uint32_t level;           /* tembra_click_level of that tick */
    int64_t  audible_in_ns;   /* when that tick reaches the listener, relative to now (negative = already audible) */
    int64_t  beat_period_ns;  /* 60e9 / bpm at the time of the tick */
    uint32_t running;         /* 1 while state == METRONOME */
    uint32_t sample_rate;     /* stream rate */
} tembra_metronome_tick;
void    tembra_metronome_read(tembra_metronome_tick* out);   /* lock-free seqlock read; computes audible_in_ns against the monotonic clock at call time */
```

**State rules**
- `tembra_metronome_start` fails with `TEMBRA_ERR_STATE` while `LISTENING` or `TONE`, and with
  `TEMBRA_ERR_DEVICE` when the stream cannot open (state stays `IDLE`). `tembra_tuner_start`,
  `tembra_tone_start`, `tembra_sample_play` and `tembra_metronome_preview` fail with
  `TEMBRA_ERR_STATE` while `METRONOME`.
- `tembra_metronome_set_grid` validates (`bpm` 40–240, `ticks_per_beat` ∈ {1,2,3,4},
  `ticks_per_bar` = k × `ticks_per_beat` ≤ 32, levels ≤ 2) → `TEMBRA_ERR_ARGUMENT`. Published
  through the three-slot buffer; the renderer maps its position per research R1.
- `tembra_metronome_select_sound(CUSTOM)` without a loaded custom sound → `TEMBRA_ERR_STATE`.
- `tembra_metronome_load_custom_sound` while running swaps the set on the next tick without a gap.
- `tembra_metronome_read` before any start returns `running = 0`, `tick_index = 0`.
- The metronome ignores `deviceLost()`: `OboeAudioDevice` reopens the output itself in
  `onErrorAfterClose`; the renderer keeps its position and the stream rate is unchanged (R1).

**Internal C++ (engine/src, not part of the ABI, tested directly by Catch2)**

| File | Type | Responsibility |
|---|---|---|
| `src/core/engine_core.{h,cpp}` | `tembra::EngineCore` (process singleton; was `TunerEngine`) | owns the `AudioDevice`, the atomic state, the tone generator, the sample player and the `MetronomeRenderer`; `render()` sums the three into the output block; `ensureOutputOpen()`; `onOutputOpened(rate)` prepares the built-in click sets; test hook `setDevice()` unchanged |
| `src/core/miniaudio_impl.cpp` | — | the one `MINIAUDIO_IMPLEMENTATION` unit with `MA_NO_DEVICE_IO`, `MA_NO_DECODING`, `MA_NO_ENCODING`, `MA_NO_GENERATION` |
| `src/metronome/click_sample.h` | `tembra::ClickSample { std::vector<float> pcm; uint32_t rate; }` | immutable mono PCM |
| `src/metronome/click_set.h` | `tembra::ClickSet { ClickSample sub, click, accent; }` | one prepared sound |
| `src/metronome/triple_buffer.h` | `tembra::TripleBuffer<T>` | one writer, one reader; `write(const T&)`, `const T* latest()` on the reader; never blocks, never allocates after construction |
| `src/metronome/metronome_grid.h` | `tembra::MetronomeGrid` (POD mirror of the ABI struct + `framesPerTick(rate)`) | validated copy |
| `src/metronome/click_synth.{h,cpp}` | `tembra::ClickSynth` | `render(sound, level, rate) → ClickSample` (research R3 recipes, envelopes, levels) |
| `src/metronome/click_sample_builder.{h,cpp}` | `tembra::ClickSampleBuilder` | `prepare(mono, sourceRate, targetRate, &info) → ClickSet` (onset, trim, fades, normalise, Accent/Sub derivation, resample) |
| `src/metronome/sound_resampler.{h,cpp}` | `tembra::SoundResampler` | miniaudio `ma_resampler` (linear, lpf order 8) offline |
| `src/metronome/custom_sound_loader.{h,cpp}` | `tembra::CustomSoundLoader` | dr_wav decode of the first 30 s, downmix, → builder |
| `src/metronome/metronome_tick.h` | `tembra::MetronomeTick` (RT-written record: tick index, beat, level, tick frame, block start frame, block time ns, bpm) | the seqlock payload |
| `src/metronome/metronome_renderer.{h,cpp}` | `tembra::MetronomeRenderer` | RT scheduler: grid and set slots, `framePos`, `lastTickFrame` (double), `tickIndex`, one voice + one releasing voice, 3 ms pre-tick release, 5 ms stop release, seqlock publish per block; host API `setGrid`, `setSet`, `start`, `stop`, `anchor(frame, beatPosition)`, `readTick(latencyNs, nowNs)` |

**Threading**: `render` only from the output callback; every `tembra_metronome_*` function from
the host thread under `EngineCore::commands_` (a host-only mutex, never taken on a callback);
`tembra_metronome_read` takes no mutex (seqlock). `load_custom_sound` allocates and resamples on
the calling thread before publishing.

**Platform bindings**
- Android JNI (`tembra_jni.cpp`), class `audio.tembra.engine.NativeAudioEngine`:
  `nativeMetronomeSetGrid(double bpm, int ticksPerBeat, int ticksPerBar, ByteArray levels): Int`,
  `nativeMetronomeSelectSound(int): Int`, `nativeMetronomeLoadCustomSound(String path, IntArray info /* [rate, channels, bits, keptFrames] */, FloatArray ms /* [trimmed, kept] */): Int`,
  `nativeMetronomeUnloadCustomSound()`, `nativeMetronomeStart(): Int`, `nativeMetronomeStop()`,
  `nativeMetronomePreview(int level): Int`, `nativeMetronomeSetOutputLatencyNs(long)`,
  `nativeMetronomeRead(LongArray out /* [tickIndex, beatIndex, level, audibleInNs, beatPeriodNs, running, sampleRate] */)`. No allocation per read (`SetLongArrayRegion` on the caller's array).
- iOS cinterop: the structs and functions are generated from the header; the Kotlin actual keeps
  one `nativeHeap.alloc<tembra_metronome_tick>()`, one `tembra_metronome_grid` and one
  `tembra_custom_sound_info` for its lifetime.

**Tests (Catch2, `engine/tests/`)**: `engine_info_test` (ABI 3, version 0.3.0);
`triple_buffer_test` (reader never sees a torn write; writer never overwrites the slot being
read); `click_synth_test` (five sounds × three levels: peak within 0.5 dB of R3, Accent − Click ≥ 6
dB, Click − Sub ≥ 6 dB, first and last sample 0, length ≤ 120 ms, sounds pairwise distinct by
spectral centroid); `click_sample_builder_test` (generated fixtures: 100 ms leading silence
trimmed to within 1 ms of the onset with a 1 ms fade-in; 3 s tail kept to 2 s with a 5 ms
fade-out; 4 ms transient kept whole; levels; 96 → 48 kHz resample length within one frame; silent
input refused); `custom_sound_loader_test` (WAV fixtures written with `wav_fixtures.h`: 8/16/24/32
int and float, mono and stereo, 8 kHz and 192 kHz decode; a 40 s file reads 30 s; non-WAV bytes
refused); `metronome_renderer_test` (`FakeAudioDevice` `pullOutput` in blocks of 96, 256 and 1024
frames: ten minutes at 40, 120 and 240 BPM × every subdivision with every tick's onset frame within
one sample of `round(i × framesPerTick)` and the last within one sample; a tempo change reaches the
next tick and never moves the current one; grid changes map the position per R1; start lands beat
1 at frame 0; stop leaves a 5 ms release and silence; 3 ms pre-tick fade with a 2 s set at 240 × 4;
`anchor()` moves the next tick to the beat boundary; telemetry `audible_in_ns` equals block time +
offset + latency − now for a fixed clock); `engine_core_state_test` (the state table of
data-model.md §6, including refusals; preview returns to IDLE; a route reopen keeps the position).
