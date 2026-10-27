"""Deterministic Macrobenchmark `benchmarkData.json` fixtures for `tracepulse profile compare` (feature 008).

One file per optimisation state (`none`, `profile`, `full`) in the shape Macrobenchmark 1.5 writes for
`audio.tembra.perf.ProfileComparisonBenchmark` (methods `startup`, `tuner`, `metronome`, `timeline`, `stress`, 20
iterations each; the key set mirrors `tests/fixtures/benchmarkData-1.5.json`). Medians are chosen so the
comparison tests can assert hand-computed outcomes:

- `timeToInitialDisplayMs`: `profile` 20 % below `none`, `full` a further 10 % below (SC-001's shape);
- `timeToFullDisplayMs`, `journey.*SumMs`, `tuner.loadSumMs`, `metronome.loadSumMs`: lower with the profile;
- `memoryRssAnonLastKb` of the tuner journey: 4 % *higher* with the profile (a confirmed `worse` direction);
- `memoryRssAnonMaxKb` of the stress journey: identical medians in `none` and `full` (gap undefined → null).

Values come from a fixed LCG, never the clock, so the files are byte-stable. Regenerate with:
    cd app/perf && uv run python tests/fixtures/profile/gen_profile_fixtures.py
"""

import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
CLASS = "audio.tembra.perf.ProfileComparisonBenchmark"
ITERATIONS = 20
FRAMES_PER_ITERATION = 30


def _lcg(seed):
    x = seed
    while True:
        x = (x * 1103515245 + 12345) % (2 ** 31)
        yield x


def _samples(gen, base, spread, n=ITERATIONS, nd=2):
    """`n` values in [base - spread, base + spread], symmetric around `base` so the median stays near it."""
    out = []
    for _ in range(n):
        u = (next(gen) % 10_000) / 10_000.0
        out.append(round(base + (2 * u - 1) * spread, nd))
    return out


def _single(runs):
    ordered = sorted(runs)
    mid = len(ordered) // 2
    median = ordered[mid] if len(ordered) % 2 else (ordered[mid - 1] + ordered[mid]) / 2
    mean = sum(runs) / len(runs)
    var = sum((v - mean) ** 2 for v in runs) / len(runs)
    cov = (var ** 0.5) / mean if mean else 0.0
    return {"minimum": min(runs), "maximum": max(runs), "median": round(median, 3),
            "coefficientOfVariation": round(cov, 4), "runs": runs}


def _sampled(gen, base, spread):
    runs = [_samples(gen, base, spread, n=FRAMES_PER_ITERATION) for _ in range(ITERATIONS)]
    flat = sorted(v for run in runs for v in run)

    def pct(p):
        return round(flat[min(len(flat) - 1, int(round(p / 100 * (len(flat) - 1))))], 3)
    return {"P50": pct(50), "P90": pct(90), "P95": pct(95), "P99": pct(99), "runs": runs}


def _benchmark(name, metrics, sampled):
    return {
        "name": name, "params": {}, "className": CLASS, "totalRunTimeNs": 120_000_000_000,
        "metrics": metrics, "sampledMetrics": sampled,
        "warmupIterations": 0, "repeatIterations": ITERATIONS, "thermalThrottleSleepSeconds": 0.0,
    }


def _context(state):
    mode = {"none": "None", "profile": "Partial(baselineProfile=Require,warmupIterations=0)", "full": "Full"}[state]
    return {
        "build": {"brand": "google", "device": "raven", "model": "Pixel 6 Pro",
                  "fingerprint": "google/raven/raven:17/BP1A.251005.004/x:user/release-keys", "version": {"sdk": 37}},
        "cpuCoreCount": 8, "cpuLocked": False, "cpuMaxFreqHz": 2802000000, "memTotalBytes": 12000000000,
        "sustainedPerformanceModeEnabled": False, "artMainlineVersion": 123, "osCodenameAbbreviated": "REL",
        "compilationMode": mode,
    }


# Per-state medians. Keys: (state) -> value. Spreads are tight so the differences are confirmed at alpha 0.01.
TTID = {"none": 620.0, "profile": 496.0, "full": 446.4}
TTFD = {"none": 1180.0, "profile": 980.0, "full": 900.0}
TUNER_JOURNEY = {"none": 4600.0, "profile": 4350.0, "full": 4300.0}
TUNER_LOAD = {"none": 62.0, "profile": 40.0, "full": 36.0}
TUNER_END_KB = {"none": 49000.0, "profile": 50960.0, "full": 49100.0}      # worse with the profile, by design
METRONOME_JOURNEY = {"none": 6400.0, "profile": 6250.0, "full": 6230.0}
METRONOME_LOAD = {"none": 58.0, "profile": 41.0, "full": 38.0}
STRESS_JOURNEY = {"none": 9500.0, "profile": 9260.0, "full": 9240.0}
# Feature 009's timeline journey draws from its own generator so the metronome's and stress's samples stay as they were.
TIMELINE_JOURNEY = {"none": 24100.0, "profile": 23700.0, "full": 23650.0}
TIMELINE_OPEN = {"none": 210.0, "profile": 150.0, "full": 142.0}
TIMELINE_SEED = {"none": 41, "profile": 53, "full": 67}
STRESS_PEAK_KB = {"none": 207500.0, "profile": 206000.0, "full": 207500.0}  # none == full → gap undefined
# `none` and `full` draw the stress peak from the same seed so their medians are identical, not merely close.
STRESS_PEAK_SEED = {"none": 99, "profile": 23, "full": 99}


def build(state):
    gen = _lcg({"none": 11, "profile": 23, "full": 37}[state])
    startup = _benchmark("startup", {
        "timeToInitialDisplayMs": _single(_samples(gen, TTID[state], 12.0)),
        "timeToFullDisplayMs": _single(_samples(gen, TTFD[state], 30.0)),
    }, {})
    tuner = _benchmark("tuner", {
        "memoryRssAnonMaxKb": _single(_samples(gen, 182800.0, 1500.0, nd=0)),
        "memoryRssAnonLastKb": _single(_samples(gen, TUNER_END_KB[state], 400.0, nd=0)),
        "journey.tunerSumMs": _single(_samples(gen, TUNER_JOURNEY[state], 40.0)),
        "journey.tunerCount": _single([1.0] * ITERATIONS),
        "tuner.loadSumMs": _single(_samples(gen, TUNER_LOAD[state], 3.0)),
        "tuner.loadCount": _single([1.0] * ITERATIONS),
        "tuner.modeSwitchSumMs": _single(_samples(gen, 14.5, 1.0)),
        "tuner.modeSwitchCount": _single([1.0] * ITERATIONS),
        "frameCount": _single([float(FRAMES_PER_ITERATION)] * ITERATIONS),
    }, {
        "frameDurationCpuMs": _sampled(gen, 7.1, 2.0),
        "frameOverrunMs": _sampled(gen, -4.9, 2.0),
    })
    metronome = _benchmark("metronome", {
        "memoryRssAnonMaxKb": _single(_samples(gen, 178800.0, 1500.0, nd=0)),
        "memoryRssAnonLastKb": _single(_samples(gen, 49500.0, 400.0, nd=0)),
        "journey.metronomeSumMs": _single(_samples(gen, METRONOME_JOURNEY[state], 40.0)),
        "journey.metronomeCount": _single([1.0] * ITERATIONS),
        "metronome.loadSumMs": _single(_samples(gen, METRONOME_LOAD[state], 3.0)),
        "metronome.loadCount": _single([1.0] * ITERATIONS),
        "metronome.tempoChangeSumMs": _single(_samples(gen, 34.0, 2.0)),
        "metronome.tempoChangeCount": _single([10.0] * ITERATIONS),
        "frameCount": _single([float(FRAMES_PER_ITERATION)] * ITERATIONS),
    }, {
        "frameDurationCpuMs": _sampled(gen, 8.0, 2.0),
        "frameOverrunMs": _sampled(gen, -4.0, 2.0),
    })
    timeline_gen = _lcg(TIMELINE_SEED[state])
    timeline = _benchmark("timeline", {
        "memoryRssAnonMaxKb": _single(_samples(timeline_gen, 196400.0, 1500.0, nd=0)),
        "memoryRssAnonLastKb": _single(_samples(timeline_gen, 61200.0, 400.0, nd=0)),
        "journey.timelineSumMs": _single(_samples(timeline_gen, TIMELINE_JOURNEY[state], 60.0)),
        "journey.timelineCount": _single([1.0] * ITERATIONS),
        "timeline.projectOpenSumMs": _single(_samples(timeline_gen, TIMELINE_OPEN[state], 6.0)),
        "timeline.projectOpenCount": _single([1.0] * ITERATIONS),
        "transport.playSumMs": _single(_samples(timeline_gen, 38.0, 3.0)),
        "transport.playCount": _single([2.0] * ITERATIONS),
        "timeline.playheadFrameSumMs": _single(_samples(timeline_gen, 310.0, 12.0)),
        "timeline.playheadFrameCount": _single([900.0] * ITERATIONS),
        "frameCount": _single([float(FRAMES_PER_ITERATION)] * ITERATIONS),
    }, {
        "frameDurationCpuMs": _sampled(timeline_gen, 8.6, 2.0),
        "frameOverrunMs": _sampled(timeline_gen, -3.4, 2.0),
    })
    stress = _benchmark("stress", {
        "memoryRssAnonMaxKb": _single(_samples(_lcg(STRESS_PEAK_SEED[state]), STRESS_PEAK_KB[state], 300.0, nd=0)),
        "memoryRssAnonLastKb": _single(_samples(gen, 54200.0, 400.0, nd=0)),
        "journey.stressSumMs": _single(_samples(gen, STRESS_JOURNEY[state], 40.0)),
        "journey.stressCount": _single([1.0] * ITERATIONS),
        "frameCount": _single([float(FRAMES_PER_ITERATION)] * ITERATIONS),
    }, {
        "frameDurationCpuMs": _sampled(gen, 9.5, 2.0),
        "frameOverrunMs": _sampled(gen, -2.1, 2.0),
    })
    return {"context": _context(state), "benchmarks": [startup, tuner, metronome, timeline, stress]}


def main():
    for state in ("none", "profile", "full"):
        path = os.path.join(HERE, f"benchmarkData-{state}.json")
        with open(path, "w") as fh:
            json.dump(build(state), fh, indent=1)
            fh.write("\n")
        print(path)


if __name__ == "__main__":
    main()
