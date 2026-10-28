#!/usr/bin/env python3
"""Measure a recorded take's alignment to the beat, or a monitoring delay (feature 010, quickstart §8; T089).

Take mode: reads a WAV of a loopback-recorded click track, finds the click onsets by an energy threshold and prints
each onset's offset to the nearest beat at ``--bpm`` and the file's rate (mean, max and the per-beat list). The take
must start on a beat (the engine aligns it to the count-in's end), so the first onset defines beat 0 unless
``--anchor-first`` is off.

Monitor mode (``--monitor``): reads a WAV holding the direct sound and its monitored copy and prints the delay between
the first two transients, which is the input-to-output monitoring delay when the recording captured both.

Standard library only; numpy is used when present (faster on long files) but not required.

    tools/measure-take-alignment.py take.wav --bpm 120 [--threshold 0.2] [--json]
    tools/measure-take-alignment.py --monitor loopback.wav [--threshold 0.2]
"""
from __future__ import annotations

import argparse
import json
import math
import struct
import sys
import wave
from dataclasses import dataclass

try:  # optional
    import numpy as _np  # type: ignore
except Exception:  # pragma: no cover - numpy is optional
    _np = None


@dataclass
class Audio:
    rate: int
    channels: int
    samples: list[float]  # mono, −1…1


def read_wav(path: str) -> Audio:
    with wave.open(path, "rb") as wav:
        rate = wav.getframerate()
        channels = wav.getnchannels()
        width = wav.getsampwidth()
        frames = wav.getnframes()
        raw = wav.readframes(frames)
    if width == 2:
        count = len(raw) // 2
        ints = struct.unpack(f"<{count}h", raw)
        scale = 32768.0
    elif width == 4:
        # 32-bit float or 32-bit int: the engine writes 16-bit, dr_wav tests may write float; try float first.
        count = len(raw) // 4
        try:
            ints = struct.unpack(f"<{count}f", raw)
            scale = 1.0
        except struct.error:
            ints = struct.unpack(f"<{count}i", raw)
            scale = 2147483648.0
    elif width == 3:
        count = len(raw) // 3
        ints = [int.from_bytes(raw[i * 3:i * 3 + 3], "little", signed=True) for i in range(count)]
        scale = 8388608.0
    else:
        raise SystemExit(f"unsupported sample width {width}")
    mono = []
    for i in range(0, len(ints), channels):
        mono.append(sum(ints[i:i + channels]) / channels / scale)
    return Audio(rate, channels, mono)


def onsets(audio: Audio, threshold: float, window_ms: float = 2.0, hold_ms: float = 60.0) -> list[int]:
    """Sample indices where the short-window energy first crosses [threshold] × the file's peak energy, [hold_ms] apart."""
    window = max(1, int(audio.rate * window_ms / 1000))
    hold = int(audio.rate * hold_ms / 1000)
    samples = audio.samples
    if _np is not None:
        arr = _np.asarray(samples, dtype=_np.float64)
        energy = _np.convolve(arr * arr, _np.ones(window) / window, mode="same")
        peak = float(energy.max()) if energy.size else 0.0
        if peak <= 0:
            return []
        gate = threshold * peak
        found: list[int] = []
        i = 0
        n = len(energy)
        while i < n:
            if energy[i] >= gate:
                found.append(i)
                i += hold
            else:
                i += 1
        return found
    energy = []
    acc = 0.0
    for i, s in enumerate(samples):
        acc += s * s
        if i >= window:
            acc -= samples[i - window] ** 2
        energy.append(acc / window)
    peak = max(energy) if energy else 0.0
    if peak <= 0:
        return []
    gate = threshold * peak
    found = []
    i = 0
    while i < len(energy):
        if energy[i] >= gate:
            found.append(i)
            i += hold
        else:
            i += 1
    return found


def take_alignment(audio: Audio, bpm: float, threshold: float, anchor_first: bool) -> dict:
    found = onsets(audio, threshold)
    if not found:
        return {"onsets": 0, "error": "no onset above the threshold"}
    beat = audio.rate * 60.0 / bpm
    origin = found[0] if anchor_first else 0
    offsets_ms = []
    per_beat = []
    for index in found:
        position = index - origin
        nearest = round(position / beat)
        offset = (position - nearest * beat) / audio.rate * 1000.0
        offsets_ms.append(offset)
        per_beat.append({"beat": int(nearest), "sample": index, "offsetMs": round(offset, 3)})
    absolute = [abs(o) for o in offsets_ms]
    return {
        "onsets": len(found),
        "bpm": bpm,
        "rate": audio.rate,
        "meanOffsetMs": round(sum(offsets_ms) / len(offsets_ms), 3),
        "meanAbsOffsetMs": round(sum(absolute) / len(absolute), 3),
        "maxAbsOffsetMs": round(max(absolute), 3),
        "withinTenMs": all(a <= 10.0 for a in absolute),
        "perBeat": per_beat,
    }


def monitor_delay(audio: Audio, threshold: float) -> dict:
    found = onsets(audio, threshold, hold_ms=5.0)
    if len(found) < 2:
        return {"onsets": len(found), "error": "fewer than two transients above the threshold"}
    delay = (found[1] - found[0]) / audio.rate * 1000.0
    return {"onsets": len(found), "rate": audio.rate, "delayMs": round(delay, 3), "withinThirtyMs": delay <= 30.0}


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("wav", help="the WAV to measure")
    parser.add_argument("--bpm", type=float, default=120.0, help="the take's tempo (take mode)")
    parser.add_argument("--threshold", type=float, default=0.2, help="onset gate as a fraction of the peak energy")
    parser.add_argument("--monitor", action="store_true", help="monitoring-delay mode: the delay between the first two transients")
    parser.add_argument("--no-anchor-first", dest="anchor_first", action="store_false", help="measure against sample 0 instead of the first onset")
    parser.add_argument("--json", action="store_true", help="print JSON only")
    args = parser.parse_args(argv)
    audio = read_wav(args.wav)
    result = monitor_delay(audio, args.threshold) if args.monitor else take_alignment(audio, args.bpm, args.threshold, args.anchor_first)
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        if "error" in result:
            print(f"{args.wav}: {result['error']}")
            return 1
        if args.monitor:
            print(f"{args.wav}: monitoring delay {result['delayMs']} ms over {result['onsets']} transients ({'OK' if result['withinThirtyMs'] else 'over 30 ms'})")
        else:
            print(
                f"{args.wav}: {result['onsets']} clicks at {result['bpm']} BPM, mean {result['meanOffsetMs']} ms, "
                f"mean |offset| {result['meanAbsOffsetMs']} ms, max |offset| {result['maxAbsOffsetMs']} ms "
                f"({'every click within 10 ms' if result['withinTenMs'] else 'a click beyond 10 ms'})"
            )
            for row in result["perBeat"]:
                print(f"  beat {row['beat']:4d}  sample {row['sample']:9d}  {row['offsetMs']:+8.3f} ms")
    return 0 if "error" not in result else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
