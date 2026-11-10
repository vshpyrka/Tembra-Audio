"""Synthetic-file tests of tools/measure-pan-law.py (feature 012, T008): run with
`python3 -m unittest discover -s app/tools/tests` from the repository root (or `-s tools/tests` from app/)."""
from __future__ import annotations

import importlib.util
import io
import json
import math
import os
import struct
import sys
import tempfile
import unittest
import wave
from contextlib import redirect_stdout
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "measure-pan-law.py"
RATE = 48000


def load_script():
    spec = importlib.util.spec_from_file_location("measure_pan_law", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module  # dataclasses resolve their annotations through sys.modules
    spec.loader.exec_module(module)
    return module


def write_stereo(path: str, left: list[float], right: list[float]) -> None:
    frames = b"".join(
        struct.pack("<hh", int(max(-1.0, min(1.0, l)) * 32767), int(max(-1.0, min(1.0, r)) * 32767)) for l, r in zip(left, right)
    )
    with wave.open(path, "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(frames)


def sine(seconds: float, hz: float, amplitude) -> list[float]:
    count = int(RATE * seconds)
    if callable(amplitude):
        return [amplitude(i) * math.sin(2 * math.pi * hz * i / RATE) for i in range(count)]
    return [amplitude * math.sin(2 * math.pi * hz * i / RATE) for i in range(count)]


class MeasurePanLawTest(unittest.TestCase):
    def setUp(self):
        self.module = load_script()
        self.dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.dir.cleanup()

    def path(self, name: str) -> str:
        return os.path.join(self.dir.name, name)

    def run_main(self, args: list[str]) -> tuple[int, dict]:
        out = io.StringIO()
        with redirect_stdout(out):
            code = self.module.main(args + ["--json"])
        return code, json.loads(out.getvalue())

    def test_constant_power_recordings_agree_and_the_opposite_side_is_silent(self):
        tone = sine(1.0, 440.0, 1.0)
        centre, left, right = self.path("centre.wav"), self.path("left.wav"), self.path("right.wav")
        write_stereo(centre, [0.5 * s for s in tone], [0.5 * s for s in tone])
        write_stereo(left, [0.5 * math.sqrt(2.0) * s for s in tone], [0.0] * len(tone))
        write_stereo(right, [0.0] * len(tone), [0.5 * math.sqrt(2.0) * s for s in tone])
        code, result = self.run_main([centre, left, right])
        self.assertEqual(0, code)
        self.assertEqual("power", result["mode"])
        self.assertLess(result["spreadDb"], 0.05)
        self.assertTrue(result["withinDb"])
        self.assertTrue(result["leakageBelowFloor"])
        by_file = {Path(r["file"]).name: r for r in result["recordings"]}
        self.assertNotIn("leakageDb", by_file["centre.wav"])
        self.assertEqual("left", by_file["left.wav"]["side"])
        self.assertLessEqual(by_file["left.wav"]["leakageDb"], -60.0)
        self.assertEqual("right", by_file["right.wav"]["side"])
        # A −3 dB law's power sum: two channels at −9 dBFS RMS, or one at −6 dBFS, both −6.02 dB.
        self.assertAlmostEqual(-6.02, by_file["centre.wav"]["powerDb"], delta=0.05)

    def test_a_three_decibel_dip_fails_the_spread(self):
        tone = sine(1.0, 440.0, 1.0)
        centre, left = self.path("centre.wav"), self.path("left.wav")
        write_stereo(centre, [0.5 * s for s in tone], [0.5 * s for s in tone])
        write_stereo(left, [0.5 * s for s in tone], [0.0] * len(tone))  # a −3 dB dip at the extreme (no compensation)
        code, result = self.run_main([centre, left])
        self.assertEqual(1, code)
        self.assertFalse(result["withinDb"])
        self.assertAlmostEqual(3.01, result["spreadDb"], delta=0.05)

    def test_leakage_above_the_floor_fails(self):
        tone = sine(1.0, 440.0, 1.0)
        left = self.path("left.wav")
        write_stereo(left, [0.7 * s for s in tone], [0.01 * s for s in tone])  # −43 dBFS on the right
        code, result = self.run_main([left])
        self.assertEqual(1, code)
        self.assertFalse(result["leakageBelowFloor"])
        self.assertAlmostEqual(-43.0, result["recordings"][0]["leakageDb"], delta=0.5)

    def test_a_ramped_sweep_is_smooth_and_a_step_is_a_click(self):
        count = int(RATE * 2.0)
        smooth = sine(2.0, 440.0, lambda i: i / count)  # silence to full scale over two seconds
        sweep = self.path("sweep.wav")
        write_stereo(sweep, smooth, smooth)
        code, result = self.run_main(["--sweep", sweep, "--hz", "440"])
        self.assertEqual(0, code)
        self.assertEqual("sweep", result["mode"])
        self.assertEqual(0, result["clicks"])
        self.assertTrue(result["smooth"])
        self.assertLessEqual(result["channels"]["left"]["largestStep"], result["allowedStep"])

        clicked = list(smooth)
        clicked[RATE] += 0.5  # one sample jumps by half the scale at one second
        clicked_path = self.path("clicked.wav")
        write_stereo(clicked_path, clicked, smooth)
        code, result = self.run_main(["--sweep", clicked_path, "--hz", "440"])
        self.assertEqual(1, code)
        self.assertEqual(1, result["channels"]["left"]["clicks"])
        self.assertEqual(0, result["channels"]["right"]["clicks"])
        self.assertGreater(result["channels"]["left"]["largestStep"], 0.4)
        self.assertFalse(result["smooth"])

    def test_the_first_level_change_is_timed(self):
        half = int(RATE * 0.5)
        tone = sine(1.0, 440.0, lambda i: 0.5 if i < half else 0.25)  # −6 dB at 500 ms
        stepped = self.path("stepped.wav")
        write_stereo(stepped, tone, tone)
        _, result = self.run_main(["--sweep", stepped, "--hz", "440"])
        self.assertAlmostEqual(500.0, result["channels"]["left"]["firstChangeMs"], delta=5.0)

    def test_a_mono_file_reads_both_channels_as_one(self):
        tone = sine(0.5, 440.0, 0.5)
        mono = self.path("mono.wav")
        with wave.open(mono, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in tone))
        _, result = self.run_main([mono])
        row = result["recordings"][0]
        self.assertEqual(1, row["channels"])
        self.assertEqual(row["leftDb"], row["rightDb"])


if __name__ == "__main__":
    unittest.main()
