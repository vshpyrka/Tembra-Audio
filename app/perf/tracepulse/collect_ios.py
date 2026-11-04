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
    "TimelinePerfTests": "timeline",
    "RecordingPerfTests": "recording",
    "StressPerfTests": "stress",
    "WindowingPerfTests": "windowing",
}
_STARTUP_TESTS = {"testColdLaunchToFirstFrame": "ttid", "testColdLaunchToResponsive": "ttfd"}
# Feature 011, as on Android: a windowing round fails when a window change, the right-click menu or a destination chord
# left no section.
_REQUIRED_SECTIONS = {
    "windowing": ("section.shell.windowRelayout.sum_ms", "section.shell.shortcutNavigate.sum_ms",
                  "section.timeline.contextMenu.sum_ms"),
}
# Sections only a pointer reaches: an iPhone has none (XCUITest: "Pointer events are not supported for this device").
_POINTER_SECTIONS = {"section.timeline.contextMenu.sum_ms"}
# Sections only ⌘ keys reach: a physical iPhone drops XCUITest's synthesized keys (the simulator takes them).
_KEYBOARD_SECTIONS = {"section.shell.shortcutNavigate.sum_ms"}
# `XCTMetric_OSSignpost-<family>.<camelCase>.<suffix>` is how xcresulttool names a signpost metric
# (verified on the iPhone 17 simulator, FacadeSelfCheckTests, 2026-10-08); the display name is
# "<Column> (<name>)". An animation interval adds `.animation.frame.rate`, `.animation.frame.count`,
# `.animation.hitch.number`, `.animation.hitch.time.ratio` and `.animation.hitches.total.duration`
# (iPhone 14, iOS 27.0.1, Xcode 27.1, 2026-10-11). Memory and CPU carry the bundle id on a phone
# (`XCTMetric_Memory-com.tembraaudioP5QJJ5Y5V7.physical_peak`) and not on the simulator
# (`XCTMetric_Memory.physical_peak`), so they are told by their last component.
_SECTION_RE = re.compile(r"XCTMetric_OSSignpost-(?P<name>[a-z]+\.[A-Za-z0-9]+)(?P<rest>(?:\.[A-Za-z0-9_]+)*)$")
_ANIMATION_COLUMNS = {
    ".animation.hitch.time.ratio": ("frames.hitch_ratio_ms_per_s", "ms/s", None),
    ".animation.hitch.number": ("frames.hitch_count", "count", None),
    ".animation.frame.rate": ("frames.fps_deficit", "fps", "fps_deficit"),
}
FRAME_COUNT = "frames._count"  # advisory: an interval without frames has no frames family
_HEALTH_KEYS = {
    "callbacks": "audio.callbacks", "last_us": "audio.last_callback_us", "max_us": "audio.max_callback_us",
    "over_budget": "audio.over_budget", "xruns": "audio.xruns", "realtime": "audio.realtime",
    "avg_us": "audio.avg_us", "starvation": "audio.starvation", "clips_active": "audio.clips_active",
    "input_overruns": "audio.input_overruns", "record_backlog": "audio.record_backlog",
    "take_alignment_ms": "audio.take_alignment_ms", "monitor_delay_ms": "audio.monitor_delay_ms",
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
    last = ident.rsplit(".", 1)[-1]
    if "xctmetric_cpu" in ident:
        if last == "time":
            return ("cpu.time_ms", "ms", "ms")
        if last == "instructions_retired":
            return ("cpu.instructions_k", "kI", None)
        return None
    if "xctmetric_memory" in ident:
        if last == "physical_peak":
            return ("memory.peak_kb", "kB", None)
        if last == "physical":
            return ("memory.end_kb", "kB", None)
        return (None, None, None) if last == "physical_absolute" else None
    section = _SECTION_RE.search(identifier)
    if section:
        name, rest = section.group("name"), section.group("rest")
        if rest == ".duration":
            return (f"section.{name}.sum_ms", "ms", "ms")
        if rest == ".animation.frame.count":
            return (None, None, None) if simulator else (FRAME_COUNT, "frames", None)
        if rest in _ANIMATION_COLUMNS:
            return (None, None, None) if simulator else _ANIMATION_COLUMNS[rest]
        if rest == ".animation.hitches.total.duration":
            return (None, None, None)
        return None
    _ = disp
    return None


def _kept_iterations(run: Dict[str, Any], iterations: Optional[int], test_id: str) -> Optional[int]:
    """XCTest drops an iteration whose set of signpost metrics differs from the first iteration's ("Received
    unexpected number of metrics: 20 in iteration with index 2. Got 21 in iteration with index 0"), every metric
    of it, so a test whose metrics all carry the same shortfall measured fewer iterations than asked and is
    recorded with that count; metrics that disagree with each other stay an error."""
    counts = {len(m.get("measurements", [])) for m in run.get("metrics", []) if m.get("measurements")}
    if iterations is None or len(counts) != 1:
        return iterations
    kept = counts.pop()
    if kept < iterations:
        logger.warning("%s: XCTest kept %d of %d iterations (an iteration whose signpost set differs from the "
                       "first is dropped with every metric of it); recorded with %d", test_id, kept, iterations, kept)
        return kept
    return iterations


def _measurements(metric: Dict[str, Any], iterations: Optional[int], test_id: str) -> List[float]:
    values = [float(v) for v in metric.get("measurements", [])]
    if iterations is not None and len(values) != iterations:
        raise CollectError(f"{test_id}: {metric.get('displayName')} has {len(values)} measurements, expected {iterations}")
    if not values:
        raise CollectError(f"{test_id}: {metric.get('displayName')} has no measurements")
    return values


def collect_round(metrics_json_path: str, cfg: Config, simulator: bool,
                  iterations: Optional[int] = None, pointer: bool = True, keyboard: bool = True) -> CollectedRound:
    """The journeys of one round from the metrics JSON. `iterations` (default from
    tracepulse.yml) must match every metric's measurement count. `pointer` and `keyboard` say
    whether XCUITest synthesizes a pointer (an iPad) and ⌘ keys (an iPad, any simulator) on the
    device: the windowing journey's menu and chord sections need them."""
    with open(metrics_json_path) as fh:
        data = json.load(fh)
    if isinstance(data, dict):
        data = data.get("tests") or data.get("testWithMetrics") or data.get("metrics") or []
    iterations = cfg.collection.iterations if iterations is None else iterations
    journeys: Dict[str, Dict[str, MetricResult]] = {}
    short = False
    for entry in data:
        identifier = entry.get("testIdentifier") or entry.get("identifier") or ""
        target = journey_of_test(identifier)
        if target is None:
            logger.info("skipping non-journey test %s", identifier)
            continue
        journey, startup_variant = target
        metrics = journeys.setdefault(journey, {})
        for run in entry.get("testRuns", []):
            kept = _kept_iterations(run, iterations, identifier)
            if kept is not None and iterations is not None and kept < iterations:
                short = True
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
                values = _measurements(metric, kept, identifier)
                if conversion == "ms":
                    values = _to_ms(values, metric.get("unitOfMeasurement", ""))
                elif conversion == "fps_deficit":
                    values = [max(0.0, 60.0 - v) for v in values]
                if key == FRAME_COUNT:
                    metrics[key] = MetricResult(unit="frames", samples=values, source="xcresult")
                    continue
                entry_meta = lookup("ios", key, cfg.catalogue)
                metrics[key] = MetricResult(unit=entry_meta.unit, samples=values, source="xcresult")
    for journey, metrics in journeys.items():
        _drop_frames_without_frames(journey, metrics)
        required = [key for key in _REQUIRED_SECTIONS.get(journey, ())
                    if (pointer or key not in _POINTER_SECTIONS) and (keyboard or key not in _KEYBOARD_SECTIONS)]
        missing = [key for key in required if key not in metrics]
        if missing:
            raise CollectError(f"journey {journey} is missing its sections: {missing}")
    if not journeys:
        raise CollectError(f"no journey test with metrics in {metrics_json_path}")
    results = [JourneyResult(journey=j, iterations=max(len(m.samples) for m in ms.values()), metrics=ms)
               for j, ms in journeys.items()]
    order = {"startup": 0, "tuner": 1, "metronome": 2, "timeline": 3, "recording": 4, "stress": 5, "windowing": 6}
    results.sort(key=lambda r: order.get(r.journey, 9))
    badges = (["simulator"] if simulator else []) + (["short-iterations"] if short else [])
    return CollectedRound(journeys=results, badges=badges, thermal_seconds=0.0)


def _drop_frames_without_frames(journey: str, metrics: Dict[str, MetricResult]) -> None:
    """The hitch columns describe the frames inside the animation interval; an interval
    that contained no frame in any iteration (the metronome's tempo change is a state
    update of ~0.1 ms, not a drawn animation) would report a 60 fps "deficit" and no
    hitches, which is not a measurement. Drop the family and say so."""
    count = metrics.pop(FRAME_COUNT, None)
    frames = [k for k in metrics if k.startswith("frames.")]
    if not frames:
        return
    if count is None or all(v == 0 for v in count.samples):
        for key in frames:
            del metrics[key]
        logger.warning("%s: the animation interval contained no frames in any iteration; the frames family "
                       "is not recorded (give the interval a drawn animation to measure hitches)", journey)


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
    """`engine-health.json` → the nine `audio.*` values (zeros when no stream was open or the file predates ABI 6)."""
    with open(path) as fh:
        data = json.load(fh)
    health = data.get("health", {})
    return {series: float(health.get(field, 0)) for field, series in _HEALTH_KEYS.items()}
