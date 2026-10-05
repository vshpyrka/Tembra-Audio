# Contract: Engine C ABI revision 4 (tuner noise gate)

**Header**: `app/engine/include/tembra_engine.h` | **Consumers**: `:shared:engine` (JNI shim, cinterop), Catch2 tests | **Supersedes**: feature 004's revision 3 (every revision 3 function unchanged; one struct grows)

Rules unchanged: plain C, no C++ types, no callbacks into the host, safe to call from any thread that is not an audio
callback; nothing below is ever called on one. Research R1 to R5; data-model.md §2, §3.

```c
#define TEMBRA_ENGINE_ABI_VERSION 4u
/* tembra_engine_abi_version / name / version / describe: as before; the version string becomes "0.4.0". */

/* --- Noise gate (feature 006) --- */
int32_t tembra_tuner_set_gate(uint32_t enabled, float open_db);
        /* Allowed in every audio state. The value is kept for the life of the engine: a running analysis uses it
           from its next hop, and every later tembra_tuner_start / tembra_tuner_start_test_signal from its first hop.
           enabled == 0: no gate (open_db is ignored). enabled != 0: open_db must be finite and within -120..0,
           otherwise TEMBRA_ERR_ARGUMENT and the previous value stays. Initially: no gate. */

typedef struct tembra_pitch_reading {
    uint64_t sequence;       /* unchanged */
    uint32_t state;          /* unchanged */
    uint32_t sample_rate;    /* unchanged */
    float    frequency_hz;   /* unchanged */
    float    clarity;        /* unchanged; 0 for a hop the gate held back */
    float    input_peak;     /* unchanged: linear peak of the last hop before the pre-filter */
    float    input_level_db; /* NEW: RMS of the analysis window after the pre-filter, in dB below full scale; floor -120 */
    uint8_t  has_pitch;      /* unchanged; 0 also while the gate holds the sound back */
    uint8_t  synthetic;      /* unchanged */
    uint8_t  gate_open;      /* NEW: 1 while sound is being read (no gate, or the gate is open); 0 while it is held back */
} tembra_pitch_reading;
```

`tembra_tuner_config`, `tembra_tuner_start`, `tembra_tuner_start_test_signal`, `tembra_test_signal` and every other
declaration are unchanged.

## Behaviour

- **Level**: after the pre-filter has run on a hop, the analyzer adds the hop's energy to a running sum over the last
  `window_frames / hop_frames` hops and publishes `input_level_db = max(-120, 10 · log10(sum / frames))`. Before the
  first window is full the sum covers the hops received so far.
- **Gate**: `LevelGate::update(gate, level)` (data-model.md §2) decides `gate_open` for the hop. With the gate
  closed the detector is not run; the smoother gets a rejected estimate, so the last reading is held for four hops
  and `has_pitch` then falls to 0, exactly as for an unclear sound today.
- **A change of value** is noticed by the analysis thread on its next hop and judged afresh: open only if
  `level >= open_db`, whatever the state was (spec FR-003).
- **With no gate** every published field except the two new ones is bit-for-bit what revision 3 publishes
  (spec FR-002, SC-002).
- **Threads**: the setter takes the engine's command mutex (never taken by a callback) and stores into one
  `std::atomic<float>` that the analysis thread loads once per hop. The callback is untouched.
- **Resting reading** (not listening, or before the first hop): `input_level_db = -120`, `gate_open = 1`.

## JNI shim (`engine/android/src/main/cpp/tembra_jni.cpp`)

```cpp
JNIEXPORT jint JNICALL Java_audio_tembra_engine_NativeAudioEngine_nativeTunerSetGate(JNIEnv*, jobject, jboolean enabled, jfloat openDb);
// nativeTunerRead now fills nine floats:
// [state, sampleRate, frequencyHz, clarity, inputPeak, hasPitch, synthetic, inputLevelDb, gateOpen]
```

The two start functions keep their eight config scalars.

## Tests (Catch2, `engine/tests`, written first)

| File | Cases |
|---|---|
| `level_gate_test.cpp` (new) | every row of the state table in research R3; a value change while open with the level inside the margin closes; off is always open |
| `tuner_analyzer_test.cpp` | level: an in-band sine at −40 dB reads −40 ± 0.5 dB; tones at 12.5 Hz and 5.6 kHz read at least 20 dB under their true level and never produce a pitch (FR-004). Gate at each check level: a harmonic tone 6 dB under it rests for 10 s at every open-string pitch; at it and above it reads within 250 ms and within 1 cent (SC-001). A quiet tone under the gate with a tone 12 dB louder over it, then the loud one stopping: the note, then rest, never the quiet tone (User Story 1.4). A tone starting 15 dB above the gate and fading at 10 dB/s: unbroken until 3 ± 2 dB under the gate, rest within 500 ms (SC-003). A tone wavering ±1 dB around the gate for 30 s: at most one change (SC-004). White noise above the gate: `gate_open` 1, no pitch (FR-009). `setGate` between hops: effect on the next hop, afresh (FR-003). No gate: the readings of every existing case are unchanged, and a recorded sequence of readings for the standard tone is identical (SC-002) |
| `engine_info_test.cpp` | ABI 4, version "0.4.0" |
| `engine_core_state_test.cpp` | `tembra_tuner_set_gate` is accepted in every state, rejects NaN, +1 and −121 with `TEMBRA_ERR_ARGUMENT`, and a value set while idle gates the first hop of the next start |
| `signal_fixtures.h` | helpers only: scale a signal to an RMS level in dB, a linear fade in dB per second, a sum of two signals, a slow ±dB wobble |
