# Contract: engine C ABI revision 8 — mixer parameters, pan law, meters

**Where**: `app/engine/include/tembra_engine.h` (`TEMBRA_ENGINE_ABI_VERSION 8u`),
`engine/src/mixer/{mix_params.h, pan_law.h, mix_meter.{h,cpp}, mixer_abi.cpp, track_strip.*,
master_bus.*}`, `engine/src/transport/transport_renderer.*`, `engine/src/core/{engine_core.*,
audio_health.*}`, `engine/src/engine_info.cpp` (version 0.7.0); the JNI shim
`engine/android/src/main/cpp/tembra_jni.cpp`; both actuals of `NativeAudioEngine`;
`FakeAudioEngine`. Research R4, R5, R6, R11, R12; data model §2, §3; spec FR-009 to FR-015,
FR-031, FR-032, SC-002 to SC-004. Revision 7's contract stays in force for everything it
covers; this revision adds, it does not change existing behaviour except where stated.

## Functions added

```c
/* Mixer parameters: lock-free, bounded, any host thread; never take the command mutex.
   Return TEMBRA_ERR_ARGUMENT for an index ≥ 64, a non-finite value or a value out of range,
   TEMBRA_ERR_STATE when the transport is not open; otherwise TEMBRA_OK. */
int32_t tembra_mixer_set_track(uint32_t index, float gain_linear, float pan, uint32_t audible); /* gain 0..2.0; pan -1..1 */
int32_t tembra_mixer_set_pan(uint32_t index, float pan);                                        /* -1 (left) .. 1 (right) */
int32_t tembra_mixer_set_master(float gain_linear);                                             /* 0..1; smoothed over 10 ms */
int32_t tembra_mixer_set_metering(uint32_t enabled);                                            /* 0 stops measuring and publishing */

/* Meter telemetry: lock-free seqlock read, safe every frame; zeros when metering is off or
   the transport is closed. */
void    tembra_mixer_read(tembra_mix_snapshot* out);
int32_t tembra_mixer_reset_clip(uint32_t index);                                                /* 0..63 a strip, 64 the master */
```

`tembra_transport_set_track` and `tembra_transport_set_master` keep their signatures and
forward to the new path (they no longer take `commands_`); every existing caller is
unaffected.

## Structs added and appended fields

```c
#define TEMBRA_MIXER_MASTER_INDEX 64u
typedef struct tembra_mix_snapshot {
    uint64_t block;            /* the callback block the record was published on */
    uint32_t track_count;
    uint32_t metering;         /* 1 while measuring */
    float    peak[TEMBRA_MAX_TRACKS][2];   /* max |x| over the last 64 ms per side, after the fader, pan and audible */
    float    rms[TEMBRA_MAX_TRACKS][2];    /* root mean square over a 300 ms window per side */
    float    master_peak[2];   /* after the master gain and the clamp */
    float    master_rms[2];
    uint64_t clip_mask;        /* bit i set while strip i's latch is lit (|x| ≥ 0.9999 on either side) */
    uint32_t master_clipped;   /* sticky: the clamp engaged since the last reset */
} tembra_mix_snapshot;
```

`tembra_audio_health` appends `uint32_t master_clips;` (blocks in which the clamp engaged;
`sizeof == 60`, `offsetof == 56`; `audio_health_test` static asserts updated).

## Renderer rules

- `TrackStrip` carries two ramps (left, right). Target per side = `fader gain × side gain ×
  audible`, side gains from `PanLaw::gains(pan, stereo)`: mono `√2·cos θ`, `√2·sin θ`
  (θ = (pan + 1)·π/4), stereo `min(1, …)` on each side (balance). Both ramps are the
  existing 10 ms linear `GainRamp` (`snap` on the first value, `setTarget` after), so gain,
  pan, mute and solo changes are all smoothed and land exactly on their targets.
- `applyTracks` reads `MixParams.version` once per block (relaxed load); when it changed it
  reads the 64 × 3 atomics and sets each strip's targets. A new arrangement seeds
  `MixParams` under `commands_` before the table is published; the callback never reads
  the table's gain or audible fields again (they remain for revision-7 compatibility).
- Metering runs only while `MixParams.metering` is set: after each strip's `process`,
  `MixMeter::measureStrip` takes the per-side peak and the per-side sum of squares of the
  block it produced and updates the 300 ms mean-square (`ms += (blockMs − ms) × (1 −
  exp(−frames / (0.3 × rate)))`); after the bus's clamp, `measureMaster` does the same on the
  output and sets `master_clips`/`master_clipped` when any sample was clamped; the click is
  added after the clamp and is not metered. `publish` writes the record once per block (one
  `Seqlock::store`). A segment split (`renderPlaying`) accumulates across segments and
  publishes once per block.
- Peak history: 16 per-block peaks per side (the capture meter's 64 ms rule generalised);
  the published `peak` is the max over the history. Latches: `clip_mask` bit i is set when
  either side's block peak ≥ 0.9999 and cleared only by `tembra_mixer_reset_clip(i)` (an
  atomic request applied at the next measurement, the capture pattern).
- Threading: the setters store atomics and return; `tembra_mixer_read` is a seqlock load;
  `tembra_mixer_reset_clip` sets an atomic request; `tembra_mixer_set_metering` sets an atomic
  and, when turning off, the next block publishes a zeroed record. Nothing here allocates,
  locks, traces or blocks; `mix_meter.cpp`, `track_strip.cpp` and `transport_renderer.cpp`
  are in the `TEMBRA_RT_SOURCES` poison list. `mixer_abi.cpp` (non-RT) carries the
  `engine.panApply` trace scope around `tembra_mixer_set_pan`.

## Bindings

- JNI: `nativeMixerSetTrack(int, float, float, boolean)`, `nativeMixerSetPan(int, float)`,
  `nativeMixerSetMaster(float)`, `nativeMixerSetMetering(boolean)`, `nativeMixerRead(IntArray
  ints, FloatArray peaks, FloatArray rms, FloatArray master, LongArray longs)` filling
  pre-allocated arrays with `Set*ArrayRegion` (the `nativeCaptureRead` pattern, allocation-free
  for the draw-phase caller), `nativeMixerResetClip(int)`.
- iOS: the cinterop picks the header up; the actual allocates one `tembra_mix_snapshot` with
  `nativeHeap.alloc` and copies into `MixSnapshot`.
- `ArrangementBuffer` (Android) and the iOS arrangement fill are untouched:
  `tembra_track_state` keeps its layout (`TRACK_SIZE = 8`); pan never enters the arrangement
  struct.

## Kotlin seam (`TimelineAudio`)

`setPan(index, pan)`, `setTrack` (unchanged signature), `setMaster`, `setMetering(enabled)`,
`readMix(into: MixSnapshot)` (main thread only, one scratch per engine), `resetMixClip(index)`;
none suspend. `FakeAudioEngine` records every call and scripts `readMix` per block.

## Catch2 tests (first)

| File | Asserts |
|---|---|
| `pan_law_test.cpp` | centre = 1/1; full left = √2/0; full right = 0/√2; `L² + R² = 2` at nine positions within 1e-5; stereo gains ≤ 1 and equal to 1 at the centre; monotonic |
| `mix_params_test.cpp` | setters are visible to a reader through `version`; argument checks; metering flag |
| `mixer_abi_test.cpp` | with the transport open and `commands_` held by another thread for 200 ms, `tembra_mixer_set_pan` returns within 1 ms; `TEMBRA_ERR_STATE` when closed; index 64 rejected for the setters and accepted by `reset_clip` |
| `mix_meter_test.cpp` | a −6 dBFS sine reads peak 0.5 and rms 0.354 within 1 %; the 64 ms history keeps a one-block transient for 12 blocks of 5 ms; the latch lights at 0.9999 and clears only on reset; the 300 ms window reaches 95 % of a step in 0.9 s; metering off publishes zeros and does no measurement (a call counter) |
| `track_strip_test.cpp` (extended) | two ramps land in 10 ms; a pan step is click-free (max per-sample step ≤ the 10 ms ramp's step); a centred mono track is unchanged from revision 7 (bit-exact) |
| `master_bus_test.cpp` (extended) | `master_clips` counts a clamped block once; the clamp latch |
| `transport_renderer_test.cpp` (extended) | a stereo `renderStereo` helper; a track panned hard left renders silence on the right; the meter record is published every block and is zero when metering is off |
| `audio_health_test.cpp`, `engine_info_test.cpp` | `sizeof == 60`, `offsetof(master_clips) == 56`; ABI 8 |
| `trace_shim_test.cpp`, `trace_poison_test` | 49 names; the new RT sources poisoned |
