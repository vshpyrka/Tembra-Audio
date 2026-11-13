"""The overview comparison: each device's newest feature label against the one before it.

A feature is a run of rounds under one label (labels in first-appearance order;
experiment labels from `report.experiment_labels` and failed, injected, simulator and
non-reference rounds never count). Per device the median of each series' round medians
is compared before and after with the gate's own rule (`gate.decide`, one limit per
metric from the catalogue), and the result is condensed into one cell per journey plus
a list of the series that moved. Pure data: `overview_data` returns plain dicts for the
report to embed.
"""

import statistics
from typing import Any, Dict, List, Optional, Tuple

from .catalogue import CatalogueError, Metric, lookup
from .config import Config
from .gate import decide
from .store import Store, split_series_key
from .text import plain_series

JOURNEY_ORDER: List[str] = ["startup", "tuner", "metronome", "timeline", "recording", "mixer", "stress", "windowing"]

_STATE_RANK = {"worse": 0, "changed": 1, "better": 2}


def _round(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    return float(f"{value:.5g}")


def judge(before: float, after: float, entry: Metric) -> Dict[str, Any]:
    """Worse, better or same for one series; better is the mirror image of worse, so both
    directions use the gate's one rule."""
    def verdict(base: float, value: float) -> Dict[str, Any]:
        return decide([base], value, entry.gate_limit_pct, entry.gate_limit_abs, [],
                      floor_abs=entry.floor_abs, scale_pct=entry.gate_scale_pct)

    worse = verdict(before, after)
    if worse["state"] == "above_limit":
        state = "worse"
    elif verdict(before, 2 * before - after)["state"] == "above_limit":
        state = "better"
    else:
        state = "same"
    delta = after - before
    return {"state": state, "delta_abs": delta,
            "delta_pct": None if before == 0 else delta / abs(before) * 100.0,
            "limit_pct": worse.get("limit_pct"), "limit_abs": worse.get("limit_abs")}


def _journey_rank(journey: str) -> Tuple[int, str]:
    return (JOURNEY_ORDER.index(journey), "") if journey in JOURNEY_ORDER else (len(JOURNEY_ORDER), journey)


def _eligible(store: Store, cfg: Config) -> Dict[str, List[Any]]:
    """Per device id, the rounds that can be compared, judged from each round's own columns:
    succeeded, a physical reference device, not injected, not an experiment label."""
    skip = set(cfg.report.experiment_labels)
    out: Dict[str, List[Any]] = {}
    for row in store.rounds():
        if (row["status"] != "succeeded" or row["kind"] != "device" or not row["reference"]
                or not row["device_id"] or row["label"] in skip or store.injected(row["id"])):
            continue
        out.setdefault(row["device_id"], []).append(row)
    return out


def _features(rows: List[Any]) -> List[Tuple[str, List[Any]]]:
    """(label, rounds) in order of first appearance."""
    order: List[str] = []
    by_label: Dict[str, List[Any]] = {}
    for row in rows:
        if row["label"] not in by_label:
            order.append(row["label"])
            by_label[row["label"]] = []
        by_label[row["label"]].append(row)
    return [(label, by_label[label]) for label in order]


def _is_section_sum(metric: str) -> bool:
    return metric.startswith("section.") and metric.endswith(".sum_ms")


def _empty_cell() -> Dict[str, Any]:
    return {"state": "none", "worse": 0, "better": 0, "worst_pct": None,
            "sections_added": [], "sections_removed": [], "new": [], "gone": []}


def _judged(platform: str, metric: str, cfg: Config) -> bool:
    """Whether a series is compared at all: in the catalogue, enabled, repeated per journey, not a count."""
    try:
        entry = lookup(platform, metric, cfg.catalogue)
    except CatalogueError:
        return False
    return entry.enabled and not entry.single_valued and not metric.endswith(".count")


def _device(store: Store, cfg: Config, device_id: str, rows: List[Any],
            items: List[Dict[str, Any]]) -> Dict[str, Any]:
    latest = rows[-1]
    dev = {"device_id": device_id, "model": latest["model"], "platform": latest["platform"]}
    features = _features(rows)
    after_label, after_rounds = features[-1] if features else (None, [])
    before_label, before_rounds = features[-2] if len(features) > 1 else (None, [])
    flags = {"noisy": 0, "thermal": 0, "dirty": 0}
    for row in after_rounds:
        badges = store.round_badges(row["id"])
        for name in ("noisy", "thermal"):
            flags[name] += name in badges
        flags["dirty"] += bool(row["target_dirty"])
    out: Dict[str, Any] = {
        "device_id": device_id, "model": dev["model"], "platform": dev["platform"],
        "before": before_label, "after": after_label,
        "rounds_before": len(before_rounds), "rounds_after": len(after_rounds),
        "flags_after": flags, "cells": {},
    }
    if before_label is None or after_label is None:
        return out

    # series -> {side: [(round row, median)]}
    present: Dict[str, Dict[str, List[Tuple[Any, float]]]] = {}
    for side, rows in (("before", before_rounds), ("after", after_rounds)):
        for row in rows:
            for series in store.metrics(device_id):
                agg = store.aggregate(series, row["id"])
                if agg is not None:
                    present.setdefault(series, {"before": [], "after": []})[side].append((row, agg["median"]))

    sides: Dict[str, Dict[str, set]] = {}  # journey -> side -> metric keys
    judged: Dict[str, Dict[str, set]] = {}  # the same, only the series that are compared
    for series, per_side in present.items():
        journey, metric = split_series_key(series)
        if journey is None:
            continue
        for side in ("before", "after"):
            if per_side[side]:
                sides.setdefault(journey, {"before": set(), "after": set()})[side].add(metric)
                judged.setdefault(journey, {"before": set(), "after": set()})
                if _judged(dev["platform"], metric, cfg):
                    judged[journey][side].add(metric)

    cells: Dict[str, Dict[str, Any]] = {}
    changed: set = set()
    for journey, per_side in sides.items():
        cell = _empty_cell()
        cells[journey] = cell
        if not judged[journey]["before"] or not judged[journey]["after"]:
            continue
        sections = {side: {m for m in per_side[side] if _is_section_sum(m)} for side in per_side}
        cell["sections_added"] = sorted(sections["after"] - sections["before"])
        cell["sections_removed"] = sorted(sections["before"] - sections["after"])
        cell["new"] = sorted(m for m in per_side["after"] - per_side["before"] if not _is_section_sum(m)
                             and not m.startswith("section."))
        cell["gone"] = sorted(m for m in per_side["before"] - per_side["after"] if not _is_section_sum(m)
                              and not m.startswith("section."))
        cell["state"] = "same"
        # startup has no sections of its own; whatever it carries never marks it as redefined
        if journey != "startup" and (cell["sections_added"] or cell["sections_removed"]):
            changed.add(journey)
            cell["state"] = "changed"

    for series in sorted(present):
        journey, metric = split_series_key(series)
        per_side = present[series]
        if journey is None or not per_side["before"] or not per_side["after"]:
            continue
        cell = cells[journey]
        if not _judged(dev["platform"], metric, cfg):
            continue
        entry = lookup(dev["platform"], metric, cfg.catalogue)
        before = statistics.median(m for _r, m in per_side["before"])
        after = statistics.median(m for _r, m in per_side["after"])
        verdict = judge(before, after, entry)
        if verdict["state"] == "same":
            continue
        state = "changed" if journey in changed else verdict["state"]
        if state != "changed":
            cell["worse" if state == "worse" else "better"] += 1
            if state == "worse" and verdict["delta_pct"] is not None:
                cell["worst_pct"] = max(cell["worst_pct"] or 0.0, verdict["delta_pct"])
        rounds = []
        for side in ("before", "after"):
            for row, median in per_side[side]:
                rounds.append({"round_key": row["round_key"], "label": row["label"], "side": side,
                               "median": _round(median),
                               "values": [_round(v) for v in store.iterations(series, [row["id"]])]})
        items.append({
            "device_id": device_id, "model": dev["model"], "series": series, "journey": journey,
            "metric": metric, "title": plain_series(series), "unit": entry.unit, "state": state,
            "before": _round(before), "after": _round(after), "delta_abs": _round(verdict["delta_abs"]),
            "delta_pct": _round(verdict["delta_pct"]), "limit_pct": verdict["limit_pct"],
            "limit_abs": verdict["limit_abs"], "rounds": rounds,
        })

    for journey, cell in cells.items():
        if cell["state"] == "same":
            cell["state"] = "worse" if cell["worse"] else "better" if cell["better"] else "same"
        cell["worst_pct"] = _round(cell["worst_pct"])
    out["cells"] = cells
    return out


def _item_order(item: Dict[str, Any]) -> Tuple[int, int, float]:
    pct = item["delta_pct"]
    return (_STATE_RANK[item["state"]], 0 if pct is None else 1, -abs(pct or 0.0))


def overview_data(store: Store, cfg: Config) -> Dict[str, Any]:
    """Every reference device's newest feature against the one before it (see the module docstring)."""
    items: List[Dict[str, Any]] = []
    devices = [_device(store, cfg, device_id, rows, items) for device_id, rows in _eligible(store, cfg).items()]
    items.sort(key=_item_order)
    journeys = sorted({j for d in devices for j in d["cells"]}, key=_journey_rank)
    return {"journeys": journeys, "devices": devices, "items": items}
