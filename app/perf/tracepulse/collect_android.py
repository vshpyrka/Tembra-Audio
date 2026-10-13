"""Android collection: benchmarkData.json parsing, catalogue mapping, quality badges, APK size.

Pure-stdlib, no device I/O: pulling benchmarkData.json and the APK off the device is
orchestrate_android.py's job; this module only parses files already on disk.
"""

import json
import logging
import os
import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence, Tuple

from .catalogue import lookup
from .config import Config
from .records import JourneyResult, MetricResult
from .summary import percentile


logger = logging.getLogger("tracepulse.collect_android")


class CollectError(Exception):
    """Raised when benchmark data can't be parsed, or required metrics are missing."""


# -- benchmarkData.json schema (SPEC S3.2 / androidx BenchmarkData.kt) ----------

@dataclass(frozen=True)
class SingleMetric:
    minimum: float
    maximum: float
    median: float
    cov: float
    runs: List[float]


@dataclass(frozen=True)
class SampledMetric:
    p50: float
    p90: float
    p95: float
    p99: float
    runs: List[List[float]]


@dataclass(frozen=True)
class BenchmarkResult:
    name: str
    single: Dict[str, SingleMetric]
    sampled: Dict[str, SampledMetric]
    thermal_throttle_sleep_s: float
    class_name: str = ""


@dataclass(frozen=True)
class BenchmarkFile:
    context: Dict
    benchmarks: List[BenchmarkResult]


def parse_benchmark_json(path: str) -> BenchmarkFile:
    """Parse a `<benchmark>-benchmarkData.json` file into a BenchmarkFile.

    Raises CollectError (naming `path`) when the file is missing, the JSON is
    malformed, the top-level `context`/`benchmarks` keys are absent, or a benchmark
    entry doesn't match the expected shape.
    """
    try:
        with open(path, "r") as fh:
            raw = json.load(fh)
    except FileNotFoundError as exc:
        raise CollectError(f"benchmark JSON not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise CollectError(f"malformed JSON in {path}: {exc}") from exc

    if not isinstance(raw, dict) or "context" not in raw or "benchmarks" not in raw:
        raise CollectError(
            f"missing top-level 'context'/'benchmarks' keys in {path}"
        )

    try:
        benchmarks = [_parse_benchmark(b) for b in raw["benchmarks"]]
    except (KeyError, TypeError) as exc:
        raise CollectError(f"malformed benchmark entry in {path}: {exc}") from exc

    return BenchmarkFile(context=raw["context"], benchmarks=benchmarks)


def _parse_benchmark(b: Dict) -> BenchmarkResult:
    single = {
        key: SingleMetric(
            minimum=m["minimum"],
            maximum=m["maximum"],
            median=m["median"],
            cov=m["coefficientOfVariation"],
            runs=list(m["runs"]),
        )
        for key, m in b.get("metrics", {}).items()
    }
    sampled = {
        key: SampledMetric(
            p50=m["P50"],
            p90=m["P90"],
            p95=m["P95"],
            p99=m["P99"],
            runs=[list(r) for r in m["runs"]],
        )
        for key, m in b.get("sampledMetrics", {}).items()
    }
    return BenchmarkResult(
        name=b["name"],
        single=single,
        sampled=sampled,
        thermal_throttle_sleep_s=b.get("thermalThrottleSleepSeconds", 0.0),
        class_name=b.get("className", ""),
    )


# -- quality flags ----------------------------------------------------------------

def quality_flags(files: List[BenchmarkFile], cfg: Config) -> Tuple[List[str], float]:
    """Per-round quality badges.

    `noisy`: ANY single metric's `coefficientOfVariation` exceeds its allowance --
    `cfg.quality.cov_max_overrides.get(<raw JSON key>, cfg.quality.cov_max)`.
    Phase 3 calibration (measured, A52 clean baselines): the check covers all
    single metrics, mapped or not, because an unmapped metric's instability is
    still round-quality signal (the wifi-contaminated round announced itself
    via `frameCount` cov 0.27 -- an unmapped, advisory metric); meanwhile
    `timeToFullDisplayMs` (~0.29-0.38) is honestly noisy within-round on this
    device while its round median stays stable to +-2%, so it carries a higher
    allowance in tracepulse.yml. (`memoryHeapSizeLastKb` carried one too until
    the noise baseline showed its round medians swing 4-16 %: the heap size at
    the end of a journey is whatever GC left, so the end-of-journey memory is
    `memoryRssAnonLastKb` since 2026-10-11.)
    `thermal`: any benchmark's `thermalThrottleSleepSeconds > 0`.

    Section keys (`tuner.frameSumMs`, `metronome.loadCount`, …; feature 007) are
    left out: a 0.03 ms loader or a 17-frame draw sum varies by 30-100 % within a
    round on a clean Pixel 6 Pro (seven rounds, 2026-10-08/11) because scheduling
    jitter is the size of the work itself, which says nothing about the round. The
    journey section (`journey.<x>SumMs`) is the journey's duration and stays in.

    Returns (flags, thermal_seconds_total) — the total summed across every benchmark
    in every file (not just those that are individually throttled).
    """
    noisy = False
    thermal_total = 0.0

    for f in files:
        for b in f.benchmarks:
            thermal_total += b.thermal_throttle_sleep_s
            for key, metric in b.single.items():
                if _is_section_key(key) and not key.startswith("journey."):
                    continue
                allowance = cfg.quality.cov_max_overrides.get(key, cfg.quality.cov_max)
                if metric.cov > allowance:
                    noisy = True

    flags = []
    if noisy:
        flags.append("noisy")
    if thermal_total > 0:
        flags.append("thermal")
    return flags, thermal_total


# -- apk / meminfo ------------------------------------------------------------------

def apk_size(path: str) -> int:
    """Byte size of the built APK at `path`."""
    return os.path.getsize(path)


# -- catalogue mapping ----------------------------------------------------------------

_JOURNEY_OF_CLASS = {
    "StartupBenchmark": "startup",
    "TunerJourneyBenchmark": "tuner",
    "MetronomeJourneyBenchmark": "metronome",
    "StressJourneyBenchmark": "stress",
}
# Families every journey class must deliver (per the catalogue's journey column).
_REQUIRED = {
    "startup": ("startup.ttid_ms", "startup.ttfd_ms"),
    "tuner": ("frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
              "memory.peak_kb", "memory.end_kb", "journey.duration_ms"),
    "metronome": ("frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
                  "memory.peak_kb", "memory.end_kb", "journey.duration_ms"),
    "stress": ("frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
               "memory.peak_kb", "memory.end_kb", "journey.duration_ms"),
}
_RSS_ANON_MAX_RE = re.compile(r"^memoryRssAnonMaxKb$", re.IGNORECASE)
_RSS_ANON_LAST_RE = re.compile(r"^memoryRssAnonLastKb$", re.IGNORECASE)
# Catalogue section names are dotted (`tuner.modeSwitch`), which keeps Macrobenchmark's own
# un-dotted keys (`frameCount`) out of the section family.
# Macrobenchmark keys the quality checks read (`cov_max_overrides`) that are no series of their own.
_ADVISORY_KEYS = frozenset({"frameCount"})
_SECTION_SUM_RE = re.compile(r"^(?P<name>[a-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]+?)SumMs$")
_SECTION_COUNT_RE = re.compile(r"^(?P<name>[a-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]+?)Count$")


@dataclass(frozen=True)
class CollectedRound:
    """What one round's benchmarkData.json files amount to: one journey per benchmark class."""
    journeys: List[JourneyResult]
    badges: List[str]
    thermal_seconds: float


def journey_of(class_name: str) -> str:
    """The journey a benchmark class measures, from its simple name."""
    simple = class_name.rsplit(".", 1)[-1]
    try:
        return _JOURNEY_OF_CLASS[simple]
    except KeyError:
        raise CollectError(f"benchmark class {class_name!r} is not one of {sorted(_JOURNEY_OF_CLASS)}") from None


# The comparison class of `tracepulse profile compare` (feature 008) holds one test per journey, named after it.
COMPARISON_CLASS = "ProfileComparisonBenchmark"
_COMPARISON_METHODS = ("startup", "tuner", "metronome", "stress")


def journey_of_method(class_name: str, method: str) -> str:
    """The journey a test of the comparison class measures: its method name. Rounds never go through here."""
    simple = class_name.rsplit(".", 1)[-1]
    if simple != COMPARISON_CLASS:
        raise CollectError(f"benchmark class {class_name!r} is not {COMPARISON_CLASS}; rounds map by class")
    if method not in _COMPARISON_METHODS:
        raise CollectError(f"{COMPARISON_CLASS} method {method!r} is not one of {list(_COMPARISON_METHODS)}")
    return method


def _journey_of_benchmark(benchmark: BenchmarkResult) -> str:
    class_name = benchmark.class_name or benchmark.name
    if class_name.rsplit(".", 1)[-1] == COMPARISON_CLASS:
        return journey_of_method(class_name, benchmark.name)
    return journey_of(class_name)


def _is_section_key(key: str) -> bool:
    return bool(_SECTION_SUM_RE.match(key) or _SECTION_COUNT_RE.match(key))


def _classify_single_key(key: str, journey: str) -> Optional[str]:
    """A single-metric JSON key to its catalogue key (None when not a catalogue metric)."""
    if key == "timeToInitialDisplayMs":
        return "startup.ttid_ms"
    if key == "timeToFullDisplayMs":
        return "startup.ttfd_ms"
    if _RSS_ANON_MAX_RE.match(key):
        return "memory.peak_kb"
    if _RSS_ANON_LAST_RE.match(key):
        return "memory.end_kb"
    m = _SECTION_SUM_RE.match(key)
    if m:
        name = m.group("name")
        return "journey.duration_ms" if name == f"journey.{journey}" else f"section.{name}.sum_ms"
    m = _SECTION_COUNT_RE.match(key)
    if m:
        name = m.group("name")
        return None if name == f"journey.{journey}" else f"section.{name}.count"
    return None


def map_benchmark(benchmark: BenchmarkResult, cfg: Config) -> JourneyResult:
    """One benchmark class (or one test of the comparison class) -> one JourneyResult with catalogue keys and
    per-iteration samples."""
    journey = _journey_of_benchmark(benchmark)
    metrics: Dict[str, MetricResult] = {}
    for key, metric in benchmark.single.items():
        if key == f"journey.{journey}Count" or key in _ADVISORY_KEYS:
            continue  # the journey section runs once per iteration by construction; advisory keys feed quality only
        target = _classify_single_key(key, journey)
        if target is None:
            logger.warning("benchmark %s: single metric %r is not a catalogue metric; not recorded",
                           benchmark.name, key)
            continue
        entry = lookup("android", target, cfg.catalogue)
        metrics[target] = MetricResult(unit=entry.unit, samples=list(metric.runs), source="macrobenchmark")
    for key, sampled_metric in benchmark.sampled.items():
        if key == "frameDurationCpuMs":
            for pct, target in ((50, "frames.p50_ms"), (90, "frames.p90_ms"), (99, "frames.p99_ms")):
                metrics[target] = MetricResult(unit="ms", samples=[percentile(run, pct) for run in sampled_metric.runs],
                                               source="macrobenchmark")
        elif key == "frameOverrunMs":
            metrics["frames.overrun_p90_ms"] = MetricResult(
                unit="ms", samples=[percentile(run, 90) for run in sampled_metric.runs], source="macrobenchmark")
        else:
            logger.warning("benchmark %s: sampled metric %r is not a catalogue metric; not recorded",
                           benchmark.name, key)
    missing = [k for k in _REQUIRED[journey] if k not in metrics]
    if missing:
        raise CollectError(f"journey {journey} ({benchmark.name}) is missing catalogue metrics: {missing}")
    iterations = max((len(m.samples) for m in metrics.values()), default=0)
    return JourneyResult(journey=journey, iterations=iterations, metrics=metrics)


def collect_round(json_paths: Sequence[str], cfg: Config) -> CollectedRound:
    """Parse every benchmarkData.json of a round into journeys, badges and thermal seconds."""
    files = [parse_benchmark_json(p) for p in json_paths]
    journeys: List[JourneyResult] = []
    for f in files:
        for b in f.benchmarks:
            journeys.append(map_benchmark(b, cfg))
    seen = [j.journey for j in journeys]
    duplicates = sorted({j for j in seen if seen.count(j) > 1})
    if duplicates:
        raise CollectError(f"more than one benchmark class per journey: {duplicates}")
    badges, thermal = quality_flags(files, cfg)
    return CollectedRound(journeys=journeys, badges=badges, thermal_seconds=thermal)
