"""iOS collection (research D6): the result bundle's metrics JSON
(`xcrun xcresulttool get test-results metrics --compact`) → journeys keyed by the
catalogue; the `preflight.json` attachment → fingerprint; a test failure whose
message starts `PERF-REFUSED:` → refusal (exit 2, no record); `engine-health.json`
→ the `audio.*` series. Pure parsing; the orchestrator runs the tools.
"""

import json
import logging
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence, Tuple

from .catalogue import lookup
from .collect_android import CollectedRound, CollectError
from .config import Config
from .records import JourneyResult, MetricResult

logger = logging.getLogger("tracepulse.collect_ios")

_TEST_JOURNEYS = {
    "TunerPerfTests": "tuner",
    "MetronomePerfTests": "metronome",
    "StressPerfTests": "stress",
}
_STARTUP_TESTS = {"testColdLaunchToFirstFrame": "ttid", "testColdLaunchToResponsive": "ttfd"}
# `XCTMetric_OSSignpost-<family>.<camelCase>.<suffix>` is how xcresulttool names a signpost metric
# (verified on the iPhone 17 simulator, FacadeSelfCheckTests, 2026-10-08); the display name is
# "<Column> (<name>)".
_SECTION_RE = re.compile(r"XCTMetric_OSSignpost-(?P<name>[a-z]+\.[A-Za-z0-9]+)(?:\.|$)")
_HEALTH_KEYS = {
    "callbacks": "audio.callbacks", "last_us": "audio.last_callback_us", "max_us": "audio.max_callback_us",
    "over_budget": "audio.over_budget", "xruns": "audio.xruns", "realtime": "audio.realtime",
}


@dataclass(frozen=True)
class Refusal:
    check: str
    message: str


def journey_of_test(identifier: str) -> Optional[Tuple[str, Optional[str]]]:
    """(journey, startup variant) for a test identifier `Bundle/Class/testName()`; None for other tests."""
    parts = identifier.split("/")
    if len(parts) < 2:
        return None
    cls, name = parts[-2], parts[-1].rstrip("()")
    if cls == "StartupPerfTests" and name in _STARTUP_TESTS:
        return "startup", _STARTUP_TESTS[name]
    journey = _TEST_JOURNEYS.get(cls)
    return (journey, None) if journey else None


def _to_ms(values: Sequence[float], unit: str) -> List[float]:
    unit = unit.lower()
    if unit in ("s", "sec", "seconds"):
        return [float(v) * 1000.0 for v in values]
    if unit in ("ms", "milliseconds"):
        return [float(v) for v in values]
    if unit in ("us", "µs", "microseconds"):
        return [float(v) / 1000.0 for v in values]
    return [float(v) for v in values]


def _classify(identifier: str, display: str, unit: str, startup_variant: Optional[str],
              simulator: bool) -> Optional[Tuple[str, str, Optional[str]]]:
    """(catalogue key, unit, conversion) for one xcresult metric, or None to skip it."""
    ident = identifier.lower()
    disp = display.lower()
    if "applicationlaunch" in ident or ("launch" in ident and "duration" in disp):
        if startup_variant is None:
            return None
        return (f"startup.{startup_variant}_ms", "ms", "ms")
    if "clock.time.monotonic" in ident:
        return ("journey.duration_ms", "ms", "ms")
    if "cpu.time" in ident:
        return ("cpu.time_ms", "ms", "ms")
    if "cpu.instructions_retired" in ident:
        return ("cpu.instructions_k", "kI", None)
    if "memory.physical_peak" in ident:
        return ("memory.peak_kb", "kB", None)
    if "memory.physical" in ident:
        return ("memory.end_kb", "kB", None)
    section = _SECTION_RE.search(identifier)
    if section:
        name = section.group("name")
        if "hitch time ratio" in disp:
            return (None, None, None) if simulator else ("frames.hitch_ratio_ms_per_s", "ms/s", None)
        if "hitch count" in disp or disp.startswith("hitches") and "count" in disp:
            return (None, None, None) if simulator else ("frames.hitch_count", "count", None)
        if "frame rate" in disp:
            return (None, None, None) if simulator else ("frames.fps_deficit", "fps", "fps_deficit")
        if "duration" in disp and "hitches" not in disp:
            return (f"section.{name}.sum_ms", "ms", "ms")
        return None
    return None


def _measurements(metric: Dict[str, Any], iterations: Optional[int], test_id: str) -> List[float]:
    values = [float(v) for v in metric.get("measurements", [])]
    if iterations is not None and len(values) != iterations:
        raise CollectError(f"{test_id}: {metric.get('displayName')} has {len(values)} measurements, expected {iterations}")
    if not values:
        raise CollectError(f"{test_id}: {metric.get('displayName')} has no measurements")
    return values


def collect_round(metrics_json_path: str, cfg: Config, simulator: bool,
                  iterations: Optional[int] = None) -> CollectedRound:
    """The journeys of one round from the metrics JSON. `iterations` (default from
    tracepulse.yml) must match every metric's measurement count."""
    with open(metrics_json_path) as fh:
        data = json.load(fh)
    if isinstance(data, dict):
        data = data.get("tests") or data.get("testWithMetrics") or data.get("metrics") or []
    iterations = cfg.collection.iterations if iterations is None else iterations
    journeys: Dict[str, Dict[str, MetricResult]] = {}
    for entry in data:
        identifier = entry.get("testIdentifier") or entry.get("identifier") or ""
        target = journey_of_test(identifier)
        if target is None:
            logger.info("skipping non-journey test %s", identifier)
            continue
        journey, startup_variant = target
        metrics = journeys.setdefault(journey, {})
        for run in entry.get("testRuns", []):
            for metric in run.get("metrics", []):
                classified = _classify(metric.get("identifier", ""), metric.get("displayName", ""),
                                       metric.get("unitOfMeasurement", ""), startup_variant, simulator)
                if classified is None:
                    logger.info("%s: metric %r (%s) is not a catalogue metric; not recorded", identifier,
                                metric.get("displayName"), metric.get("identifier"))
                    continue
                key, unit, conversion = classified
                if key is None:
                    continue
                values = _measurements(metric, iterations, identifier)
                if conversion == "ms":
                    values = _to_ms(values, metric.get("unitOfMeasurement", ""))
                elif conversion == "fps_deficit":
                    values = [max(0.0, 60.0 - v) for v in values]
                entry_meta = lookup("ios", key, cfg.catalogue)
                metrics[key] = MetricResult(unit=entry_meta.unit, samples=values, source="xcresult")
    if not journeys:
        raise CollectError(f"no journey test with metrics in {metrics_json_path}")
    results = [JourneyResult(journey=j, iterations=max(len(m.samples) for m in ms.values()), metrics=ms)
               for j, ms in journeys.items()]
    order = {"startup": 0, "tuner": 1, "metronome": 2, "stress": 3}
    results.sort(key=lambda r: order.get(r.journey, 9))
    return CollectedRound(journeys=results, badges=["simulator"] if simulator else [], thermal_seconds=0.0)


def parse_preflight(path: str) -> Dict[str, Any]:
    """The `preflight.json` attachment: thermal state, battery level and network state for the fingerprint."""
    with open(path) as fh:
        data = json.load(fh)
    out: Dict[str, Any] = {}
    if "thermal_state" in data:
        out["thermal_state"] = data["thermal_state"]
    if data.get("battery_level") is not None:
        out["battery_level"] = float(data["battery_level"])
    if "network_state" in data:
        out["network_state"] = data["network_state"] if data["network_state"] in ("none", "wifi", "cellular", "unknown") else "unknown"
    return out


def refusal_from_failures(failures: Sequence[Dict[str, Any]]) -> Optional[Refusal]:
    """A `PERF-REFUSED: <check>=<value> limit=<limit>` failure message, if any."""
    for failure in failures:
        message = failure.get("message") or ""
        if "PERF-REFUSED:" in message:
            detail = message.split("PERF-REFUSED:", 1)[1].strip()
            check = detail.split("=", 1)[0].strip()
            return Refusal(check=check, message=detail)
    return None


def engine_health(path: str) -> Dict[str, float]:
    """`engine-health.json` → the six `audio.*` values (zeros when no stream was open)."""
    with open(path) as fh:
        data = json.load(fh)
    health = data.get("health", {})
    return {series: float(health.get(field, 0)) for field, series in _HEALTH_KEYS.items()}
