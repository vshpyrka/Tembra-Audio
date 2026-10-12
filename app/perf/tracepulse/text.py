"""Plain-language text for the report (contracts/report.md "Human text").

Generated, never free-form: every sentence comes from detection and gate state.
trace-pulse's `PLAIN_*` dictionaries extended for both platforms, sections,
labels (the feature at onset) and the gate.
"""

from typing import Any, Dict, List, Optional, Sequence

from .store import split_series_key

PLAIN_METRIC: Dict[str, str] = {
    "startup.ttid_ms": "App start time (first picture on screen)",
    "startup.ttfd_ms": "App start time (ready to use)",
    "frames.p50_ms": "Typical frame drawing time (smoothness)",
    "frames.p90_ms": "Slow frames (smoothness)",
    "frames.p99_ms": "Worst frames (occasional stutter)",
    "frames.overrun_p90_ms": "Frames missing their deadline",
    "frames.hitch_ratio_ms_per_s": "Stutter per second of animation (hitch time ratio)",
    "frames.hitch_count": "Number of stutters (hitches)",
    "frames.fps_deficit": "Frame rate short of 60 fps",
    "memory.peak_kb": "Peak memory use",
    "memory.end_kb": "Memory kept at the end of the journey",
    "cpu.time_ms": "Processor time used by the journey",
    "cpu.instructions_k": "Processor instructions used by the journey",
    "journey.duration_ms": "Journey duration",
    "app.size_bytes": "App download size",
    "app.size_uncompressed_bytes": "App installed size",
    "audio.callbacks": "Audio callbacks rendered",
    "audio.last_callback_us": "Last audio callback duration",
    "audio.max_callback_us": "Longest audio callback",
    "audio.over_budget": "Audio callbacks over their time budget",
    "audio.xruns": "Audio dropouts (underruns and overruns)",
    "audio.realtime": "Audio thread has real-time priority",
    "audio.ioproc_p99_us": "Audio I/O cycle, slowest 1 %",
}

PLAIN_STATUS: Dict[str, str] = {
    "CONFIRMED": "Confirmed — proven worse",
    "SUSPECT": "Suspect — looks worse, not proven yet",
    "PRELIMINARY": "First hint — might just be noise",
    "IMPROVED": "Improved — got better, not worse",
    "ENV_SUSPECT": "Environment-suspect — several families moved at once",
}

_LADDER = {"CONFIRMED": "Confirmed", "SUSPECT": "Suspect", "PRELIMINARY": "First hint",
           "IMPROVED": "Improved", "ENV_SUSPECT": "Environment-suspect"}
_RANK = {"CONFIRMED": 3, "SUSPECT": 2, "ENV_SUSPECT": 1, "PRELIMINARY": 1, "IMPROVED": 0}

SIMULATOR_NOTE = "Simulator numbers show gross changes only; they are not a performance reference."


def plain_metric(key: str) -> str:
    """The everyday name of a catalogue key; sections by their signpost name; unknown keys as they are."""
    if key in PLAIN_METRIC:
        return PLAIN_METRIC[key]
    parts = key.split(".")
    if parts[0] == "section" and len(parts) >= 3:
        name = ".".join(parts[1:-1])
        if parts[-1] == "sum_ms":
            return f"Time inside {name}"
        if parts[-1] == "count":
            return f"How often {name} ran"
    return key


def plain_series(series: str) -> str:
    """A store series key (`<journey>/<metric>`) in words."""
    journey, metric = split_series_key(series)
    name = plain_metric(metric)
    if journey and journey != "startup":
        return f"{name} during the {journey} journey"
    return name


def status_ladder(status: Optional[str]) -> str:
    return _LADDER.get(status or "", "Steady")


def _pct(value: Optional[float]) -> str:
    return f"{abs(value or 0):.0f} %"


def _abs(value: Optional[float], unit: str) -> str:
    text = f"{value or 0:+.1f}" if abs(value or 0) < 100 else f"{value or 0:+.0f}"
    return f"{text} {unit}".rstrip()


def gate_observed(flag: Dict[str, Any], unit: str = "") -> str:
    """A flag's change in the terms of its limit: "+18 %" for a percentage limit,
    "+2.5 ms" for an absolute one."""
    if flag.get("limit_pct") is not None:
        return f"{flag.get('observed_pct') or 0:+.0f} %"
    return _abs(flag.get("observed_abs"), unit or flag.get("unit", ""))


def gate_limit(flag: Dict[str, Any], unit: str = "") -> str:
    """A flag's limit: "15 %" or "2 ms"."""
    if flag.get("limit_pct") is not None:
        return f"{flag['limit_pct']:.0f} %"
    return f"{flag.get('limit_abs') or 0:g} {unit or flag.get('unit', '')}".rstrip()


def device_headline(rounds: int, min_history: int, detections: Sequence[Dict[str, Any]],
                    flagged: Sequence[Dict[str, Any]], window: int) -> str:
    """The one-line state of a device: preliminary-only, the worst current onset,
    the latest round's gate flags, or "nothing changed"."""
    if rounds < min_history:
        return f"Preliminary only: {rounds} of {min_history} rounds needed for a verdict."
    if flagged:
        items = ", ".join(f"{f['metric']} ({gate_observed(f)}, limit {gate_limit(f)})" for f in flagged)
        noun = "metric above its" if len(flagged) == 1 else "metrics above their"
        return f"{len(flagged)} {noun} limit in the latest round: {items}."
    # the highest status, and among equals the largest step (a side effect never leads the regression)
    worst = max((d for d in detections if d.get("status") in _RANK and d.get("status") != "IMPROVED"),
                key=lambda d: (_RANK[d["status"]], abs(d.get("delta_pct") or 0.0)), default=None)
    if worst is not None:
        name = plain_metric(split_series_key(worst["metric"])[1])
        where = f" in {worst['label_at_onset']}" if worst.get("label_at_onset") else ""
        status = worst["status"]
        if status == "CONFIRMED":
            tail = "confirmed"
        elif status == "SUSPECT":
            more = worst.get("persistence_rounds_to_confirm")
            tail = f"suspect; confirmed after {more} more rounds" if more else "suspect"
        elif status == "ENV_SUSPECT":
            tail = "environment-suspect"
        else:
            tail = "first hint"
        return f"{name} got {_pct(worst.get('delta_pct'))} slower{where} ({tail})."
    return f"Nothing changed in the last {window} rounds."


def chart_sentence(detection: Optional[Dict[str, Any]], series: str) -> str:
    """One sentence under a chart: the status ladder, the step and the label at onset."""
    if detection is None:
        return "Steady: no step in this series."
    name = plain_series(series)
    name = name[0].lower() + name[1:]
    status = detection.get("status")
    direction = "down" if status == "IMPROVED" or (detection.get("delta_pct") or 0) < 0 else "up"
    where = f" in {detection['label_at_onset']}" if detection.get("label_at_onset") else ""
    onset = f" (round {detection['onset_round_key']})" if detection.get("onset_round_key") else ""
    return f"{status_ladder(status)}: {name} stepped {direction} {_pct(detection.get('delta_pct'))}{where}{onset}."


def flagged_summary(flagged: Sequence[Dict[str, Any]]) -> List[str]:
    """One line per gate flag for the report's "Flagged" list."""
    return [f"{f.get('device_model', f.get('device_id', ''))}: {plain_series(f['metric'])} "
            f"{gate_observed(f)} against the limit of {gate_limit(f)} "
            f"in round {f.get('round_key', '')}" for f in flagged]
