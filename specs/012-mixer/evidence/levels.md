# Levels, pan law and latency (T111; SC-002, SC-003, SC-004)

**Status: not measured yet.** This is the feature's loopback step (quickstart §8): it plays a sine, a burst and a
stem through the device's output into its input and records them, so it sounds. The implementation run of
2026-11-08/09 worked under the owner's no-sound rule and leaves the measurement to the owner's session; the
procedure, the tooling and what is already proven offline are below, with the table to fill.

## What is proven without a device (Catch2, `app/engine/tests`, every target's `engineHostTest`)

- `pan_law_test.cpp`: a mono source at the centre passes at unity on both sides bit-exact; full left and full right
  put √2 on one side and exact silence on the other; the power sum `L² + R²` is 2 at nine positions (SC-004's
  rule, up to the loopback path's own error); the law is monotonic; a stereo source is balanced with gains never
  above 1; a pan outside −1..1 is held at the extremes.
- `mix_params_test.cpp`: a setter's value is visible to the audio thread after the version changes; the atomics are
  lock-free on every platform built. `transport_renderer_test.cpp`: a parameter change is applied on the next
  block and ramps to its target, and a strip's rendered gain matches its target within 0.1 dB after the ramp
  (SC-006); `master_bus_test.cpp`: the master gain ramps over 10 ms. These ramps are the mechanism SC-002's "no
  step larger than the ramp's" relies on.
- `mix_meter_test.cpp`: a −6 dBFS sine reads a peak of 0.5 and an rms of 0.354 within 1 %; the 300 ms mean-square
  window reaches 95 % of a step within 0.9 s (the VU's ballistics behind SC-003's "0 VU within 1 VU after
  300 ms"); the clip latch lights at 0.9999 and clears only on a reset; the master is measured after the clamp.
- On devices, `NativeAudioEngineMixerTest` (Android) and the iOS engine tests drive `setTrack`/`setPan` through the
  real engine and read the meter snapshot back (every lane row in `../screenshots/NOTES.md`).

## Procedure (quickstart §8), devices: Pixel 6 Pro `19021FDEE001AH`, iPhone 14

1. The measurement project: three tracks from the fixtures (a −18 dBFS sine, a full-scale mono burst, a stereo
   stem). Loopback the output into the input (a cable where possible, the room otherwise, noted in the table).
2. SC-003: the sine alone at 0 dB, centred: the strip's peak and the master read the sine's level within 0.5 dB; the
   VU reads 0 VU within 1 VU after 300 ms; the meter lights within 50 ms of the recording's onset (`screenrecord`
   frame against the loopback file).
3. SC-004: record the sine centred, hard left, hard right; `app/tools/measure-pan-law.py centre.wav left.wav
   right.wav [--within 0.5] [--floor -60]`: `L² + R²` equal within 0.5 dB, the opposite side below −60 dB.
4. SC-002: sweep a fader from silence to +6 dB over two seconds and a knob from full left to full right while the
   sine plays; `app/tools/measure-pan-law.py --sweep sweep.wav --hz 440`: largest step no larger than the 10 ms
   ramp's, click count 0, the first level change within 20 ms of the gesture (video frame against the file).

The script's synthetic-file tests: `python3 -m unittest discover -s app/tools/tests`.

## Results

| Check | Device, route | Value | Limit | Pass | When |
|---|---|---|---|---|---|
| SC-003 strip peak vs. the sine | | | ±0.5 dB | | |
| SC-003 master peak vs. the sine | | | ±0.5 dB | | |
| SC-003 VU after 300 ms | | | 0 VU ± 1 | | |
| SC-003 meter onset lag | | | ≤ 50 ms | | |
| SC-004 `L² + R²` centre / left / right | | | within 0.5 dB | | |
| SC-004 opposite side, hard left | | | < −60 dB | | |
| SC-002 largest step in the fader sweep | | | ≤ the ramp's | | |
| SC-002 largest step in the pan sweep | | | ≤ the ramp's | | |
| SC-002 click count | | | 0 | | |
| SC-002 first change after the gesture | | | ≤ 20 ms | | |
