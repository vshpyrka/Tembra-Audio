#!/usr/bin/env python3
"""Measure the pan law, the opposite side's leakage and a sweep's smoothness on loopback recordings (feature 012,
quickstart §8; SC-002, SC-004). The alignment script's sibling (measure-take-alignment.py).

Power mode (default): for each two-channel WAV prints the RMS level of each channel in dBFS and the power sum
L² + R² in dB (the quantity a constant-power pan law keeps); with several files, the spread of the power sums.
SC-004 asks the centred, hard-left and hard-right recordings to agree within 0.5 dB (``--within``) and the opposite
channel of a hard-panned recording to sit below −60 dBFS (``--floor``): a file whose channels differ by more than
20 dB reports its quieter channel as the leakage.

Sweep mode (``--sweep``): for one recording of a sine while a fader or knob is swept, prints the largest per-sample
step on each channel, the step a 10 ms linear ramp of the sine's peak would add, the sine's own largest step, the
number of clicks (runs of samples whose step exceeds both) and the time of the first level change from the start of
the file (SC-002: no step larger than the ramp's, click count 0, the first change within 20 ms of the gesture).

Standard library only; numpy is used when present (faster on long files) but not required.

    tools/measure-pan-law.py centre.wav left.wav right.wav [--within 0.5] [--floor -60] [--skip 0.1] [--json]
    tools/measure-pan-law.py --sweep sweep.wav [--hz 440] [--json]
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

FLOOR_DB = -120.0
RAMP_SECONDS = 0.010
ENVELOPE_WINDOW_SECONDS = 0.005
LEVEL_CHANGE_DB = 1.0
LEAKAGE_DIFFERENCE_DB = 20.0


@dataclass
class Stereo:
    rate: int
    channels: int
    left: list[float]   # −1…1
    right: list[float]  # the second channel; a copy of the first for a mono file


def read_wav(path: str) -> Stereo:
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
    left = [ints[i] / scale for i in range(0, len(ints), channels)]
    right = [ints[i + 1] / scale for i in range(0, len(ints), channels)] if channels > 1 else list(left)
    return Stereo(rate, channels, left, right)


def db(value: float) -> float:
    """20·log10 of an amplitude, floored at −120 dB."""
    if value <= 0.0:
        return FLOOR_DB
    return max(FLOOR_DB, 20.0 * math.log10(value))


def power_db(mean_square: float) -> float:
    """10·log10 of a power, floored at −120 dB."""
    if mean_square <= 0.0:
        return FLOOR_DB
    return max(FLOOR_DB, 10.0 * math.log10(mean_square))


def mean_square(samples: list[float]) -> float:
    if not samples:
        return 0.0
    if _np is not None:
        arr = _np.asarray(samples, dtype=_np.float64)
        return float(_np.mean(arr * arr))
    return sum(s * s for s in samples) / len(samples)


def power_report(path: str, audio: Stereo, skip_seconds: float) -> dict:
    """The per-channel RMS levels and the power sum L² + R² over the file after [skip_seconds]."""
    start = min(len(audio.left), int(audio.rate * skip_seconds))
    left_ms = mean_square(audio.left[start:])
    right_ms = mean_square(audio.right[start:])
    left_db = power_db(left_ms)
    right_db = power_db(right_ms)
    report = {
        "file": path,
        "rate": audio.rate,
        "channels": audio.channels,
        "leftDb": round(left_db, 3),
        "rightDb": round(right_db, 3),
        "powerDb": round(power_db(left_ms + right_ms), 3),
    }
    if abs(left_db - right_db) > LEAKAGE_DIFFERENCE_DB:
        report["side"] = "left" if left_db > right_db else "right"
        report["leakageDb"] = round(min(left_db, right_db), 3)
    return report


def power_summary(reports: list[dict], within_db: float, floor_db: float) -> dict:
    powers = [r["powerDb"] for r in reports]
    spread = max(powers) - min(powers) if len(powers) > 1 else 0.0
    leakages = [r["leakageDb"] for r in reports if "leakageDb" in r]
    return {
        "mode": "power",
        "recordings": reports,
        "spreadDb": round(spread, 3),
        "withinDb": spread <= within_db,
        "leakageBelowFloor": all(l <= floor_db for l in leakages) if leakages else None,
    }


def largest_step(samples: list[float]) -> float:
    if len(samples) < 2:
        return 0.0
    if _np is not None:
        arr = _np.asarray(samples, dtype=_np.float64)
        return float(_np.max(_np.abs(_np.diff(arr))))
    return max(abs(samples[i] - samples[i - 1]) for i in range(1, len(samples)))


def click_runs(samples: list[float], allowed: float) -> int:
    """Runs of consecutive samples whose step exceeds [allowed]; each run is one click."""
    runs = 0
    inside = False
    for i in range(1, len(samples)):
        if abs(samples[i] - samples[i - 1]) > allowed:
            if not inside:
                runs += 1
                inside = True
        else:
            inside = False
    return runs


def first_level_change_ms(samples: list[float], rate: int) -> float | None:
    """The start of the first 5 ms window whose RMS differs from the first window's by more than 1 dB; None when none does."""
    window = max(1, int(rate * ENVELOPE_WINDOW_SECONDS))
    if len(samples) < 2 * window:
        return None
    reference = power_db(mean_square(samples[:window]))
    for start in range(window, len(samples) - window + 1, window):
        level = power_db(mean_square(samples[start:start + window]))
        if abs(level - reference) > LEVEL_CHANGE_DB:
            return round(start / rate * 1000.0, 3)
    return None


def sweep_report(path: str, audio: Stereo, hz: float) -> dict:
    """The smoothness of a sweep: the largest step against the ramp's and the sine's, the click count, the first change."""
    channels = {"left": audio.left, "right": audio.right}
    peak = max(max((abs(s) for s in audio.left), default=0.0), max((abs(s) for s in audio.right), default=0.0))
    sine_step = 2.0 * math.pi * hz * peak / audio.rate
    ramp_step = peak / (RAMP_SECONDS * audio.rate)
    allowed = sine_step + ramp_step + 1e-4
    per_channel = {}
    for name, samples in channels.items():
        per_channel[name] = {
            "largestStep": round(largest_step(samples), 6),
            "clicks": click_runs(samples, allowed),
            "firstChangeMs": first_level_change_ms(samples, audio.rate),
        }
    return {
        "mode": "sweep",
        "file": path,
        "rate": audio.rate,
        "hz": hz,
        "peak": round(peak, 6),
        "sineStep": round(sine_step, 6),
        "rampStep": round(ramp_step, 6),
        "allowedStep": round(allowed, 6),
        "channels": per_channel,
        "clicks": sum(c["clicks"] for c in per_channel.values()),
        "smooth": all(c["largestStep"] <= allowed for c in per_channel.values()),
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("wav", nargs="+", help="the WAV recordings to measure (one in --sweep mode)")
    parser.add_argument("--within", type=float, default=0.5, help="the largest spread of the power sums that passes, dB")
    parser.add_argument("--floor", type=float, default=-60.0, help="the level the opposite channel must stay below, dBFS")
    parser.add_argument("--skip", type=float, default=0.1, help="seconds skipped at the start of each recording (power mode)")
    parser.add_argument("--sweep", action="store_true", help="sweep mode: steps, clicks and the first level change of one recording")
    parser.add_argument("--hz", type=float, default=440.0, help="the sine's frequency during the sweep")
    parser.add_argument("--json", action="store_true", help="print JSON only")
    args = parser.parse_args(argv)
    if args.sweep:
        if len(args.wav) != 1:
            parser.error("--sweep takes exactly one recording")
        result = sweep_report(args.wav[0], read_wav(args.wav[0]), args.hz)
        passed = result["smooth"] and result["clicks"] == 0
    else:
        reports = [power_report(path, read_wav(path), args.skip) for path in args.wav]
        result = power_summary(reports, args.within, args.floor)
        passed = result["withinDb"] and result["leakageBelowFloor"] is not False
    if args.json:
        print(json.dumps(result, indent=2))
        return 0 if passed else 1
    if args.sweep:
        print(
            f"{result['file']}: peak {result['peak']}, allowed step {result['allowedStep']} "
            f"(sine {result['sineStep']} + ramp {result['rampStep']}), {result['clicks']} click(s)"
        )
        for name, channel in result["channels"].items():
            change = f"{channel['firstChangeMs']} ms" if channel["firstChangeMs"] is not None else "none"
            print(f"  {name:5s} largest step {channel['largestStep']}  clicks {channel['clicks']}  first level change {change}")
        print("  " + ("smooth: no step larger than the ramp's" if passed else "a step larger than the ramp's, or a click"))
    else:
        for row in result["recordings"]:
            leakage = f"  leakage {row['leakageDb']} dBFS on the {'right' if row['side'] == 'left' else 'left'}" if "leakageDb" in row else ""
            print(f"{row['file']}: L {row['leftDb']} dBFS  R {row['rightDb']} dBFS  L²+R² {row['powerDb']} dB{leakage}")
        verdict = "within" if result["withinDb"] else "beyond"
        print(f"  power sums {verdict} {args.within} dB (spread {result['spreadDb']} dB)")
        if result["leakageBelowFloor"] is not None:
            print(f"  opposite channel {'below' if result['leakageBelowFloor'] else 'above'} {args.floor} dBFS")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
