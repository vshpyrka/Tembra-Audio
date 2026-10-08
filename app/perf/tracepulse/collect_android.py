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

from .config import Config
from .summary import percentile


logger = logging.getLogger("tracepulse.collect")


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


@dataclass(frozen=True)
class BenchmarkFile:
    context: Dict
    benchmarks: List[BenchmarkResult]


@dataclass(frozen=True)
class MetricSample:
    trend_value: float
    iterations: List[float]


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
    )


# -- quality flags ----------------------------------------------------------------

def quality_flags(files: List[BenchmarkFile], cfg: Config) -> Tuple[List[str], float]:
    """Per-round quality badges.

    NOISY: ANY single metric's `coefficientOfVariation` exceeds its allowance --
    `cfg.quality.cov_max_overrides.get(<raw JSON key>, cfg.quality.cov_max)`.
    Phase 3 calibration (measured, A52 clean baselines): the check covers all
    single metrics, mapped or not, because an unmapped metric's instability is
    still round-quality signal (the wifi-contaminated round announced itself
    via `frameCount` cov 0.27 -- an unmapped, advisory metric); meanwhile
    `timeToFullDisplayMs` (~0.29-0.38) and `memoryHeapSizeLastKb` (~0.20) are
    honestly noisy within-round on this device while their round medians stay
    stable to +-2%, so they carry higher allowances in tracepulse.yml.
    THERMAL: any benchmark's `thermalThrottleSleepSeconds > 0`.

    Returns (flags, thermal_seconds_total) — the total summed across every benchmark
    in every file (not just those that are individually throttled).
    """
    noisy = False
    thermal_total = 0.0

    for f in files:
        for b in f.benchmarks:
            thermal_total += b.thermal_throttle_sleep_s
            for key, metric in b.single.items():
                allowance = cfg.quality.cov_max_overrides.get(key, cfg.quality.cov_max)
                if metric.cov > allowance:
                    noisy = True

    flags = []
    if noisy:
        flags.append("NOISY")
    if thermal_total > 0:
        flags.append("THERMAL")
    return flags, thermal_total


# -- apk / meminfo ------------------------------------------------------------------

def apk_size(path: str) -> int:
    """Byte size of the built APK at `path`."""
    return os.path.getsize(path)
