"""Detection over the rebuilt history: one device at a time, through the
detector's append-only ratchet, with the summary lines the CLI prints."""

import json
import math
from typing import Any, Dict, List, Optional

from .config import Config
from .detect import analyze
from .store import Store, split_series_key


def analyze_all(store: Store, cfg: Config, paths=None,
                expected_lookup=None) -> Dict[str, Any]:
    """Replay detection for every device in the store (series never mix devices
    or platforms). Returns the counts per device and printable lines."""
    lines: List[str] = []
    per_device: List[Dict[str, Any]] = []
    for device in store.devices():
        reports = analyze(store, cfg, expected_lookup=expected_lookup, platform=device["platform"],
                          device_id=device["device_id"])
        events = sum(len(r.new_events) for r in reports)
        provisional = sum(len(r.provisional) for r in reports)
        env = sorted({o for r in reports for o in r.env_suspect_rounds})
        per_device.append({"device_id": device["device_id"], "platform": device["platform"], "rounds": len(reports),
                           "events": events, "provisional": provisional, "env_suspect_rounds": env})
        lines.append(f"{device['device_id']} ({device['model']}): {len(reports)} rounds evaluated, "
                     f"{events} events recorded, {provisional} provisional, "
                     f"{len(env)} environment-suspect rounds")
    if not per_device:
        lines.append("no rounds in the history")
    return {"devices": per_device, "lines": lines}


def series_variation(store: Store, label: Optional[str] = None) -> List[Dict[str, Any]]:
    """Coefficient of variation of the round medians per (device, series), for
    the noise baseline (SC-003): startup.* and memory.* at or below 3 %. With
    `label`, only that label's rounds count (one session of an unchanged build)."""
    rows: List[Dict[str, Any]] = []
    for device in store.devices():
        wanted = None
        if label is not None:
            wanted = {row["id"] for row in store.rounds(device["device_id"]) if row["label"] == label}
        for metric in store.metrics(device["device_id"]):
            medians = [v for rid, v in store.series(metric, device["device_id"]) if wanted is None or rid in wanted]
            if len(medians) < 2:
                continue
            mean = sum(medians) / len(medians)
            sd = math.sqrt(sum((v - mean) ** 2 for v in medians) / len(medians))
            cov = (sd / mean * 100.0) if mean else 0.0
            rows.append({"device_id": device["device_id"], "metric": metric, "rounds": len(medians),
                         "median_of_medians": sorted(medians)[len(medians) // 2], "cov_pct": cov})
    return rows


def family_of(series: str) -> Optional[str]:
    _journey, metric = split_series_key(series)
    return metric.split(".", 1)[0]


_FINGERPRINT_FIELDS = ("os_version", "os_build", "sdk", "refresh_rate_hz", "network_state")


def _rounds_index(store: Store, device_id: str) -> Dict[int, Dict[str, Any]]:
    """round id -> {index, key, label, fingerprint} for one device, in order."""
    out: Dict[int, Dict[str, Any]] = {}
    for index, row in enumerate(store.rounds(device_id)):
        out[row["id"]] = {"index": index, "key": row["round_key"], "label": row["label"],
                          "fingerprint": json.loads(row["fingerprint_json"] or "{}")}
    return out


def detections_for_device(store: Store, device_id: str) -> List[Dict[str, Any]]:
    """The latest status per (series, onset) on one device, with the onset round's
    label and key (`label_at_onset`) for the report's headlines."""
    rounds = _rounds_index(store, device_id)
    latest: Dict[tuple, Dict[str, Any]] = {}
    for row in store.all_detections():
        if row["onset_round_id"] not in rounds:
            continue
        key = (row["metric"], row["onset_round_id"])
        rank = Store.STATUS_RANK[row["status"]]
        if key in latest and Store.STATUS_RANK[latest[key]["status"]] >= rank:
            continue
        onset = rounds[row["onset_round_id"]]
        latest[key] = {
            "metric": row["metric"], "status": row["status"], "direction": row["direction"],
            "delta_pct": row["delta_pct"], "step_size": row["step_size"], "stepfit_score": row["stepfit_score"],
            "mwu_p": row["mwu_p"], "env_suspect": bool(row["env_suspect"]),
            "onset_round_id": row["onset_round_id"], "onset_round_index": onset["index"],
            "onset_round_key": onset["key"], "label_at_onset": onset["label"],
            "ground_truth_match": row["ground_truth_match"],
        }
    return sorted(latest.values(), key=lambda d: (d["onset_round_index"], d["metric"]))


def label_boundaries(store: Store, device_id: str) -> List[Dict[str, Any]]:
    """Every round at which the label changes (the feature boundaries on a series)."""
    out: List[Dict[str, Any]] = []
    previous: Optional[str] = None
    for index, row in enumerate(store.rounds(device_id)):
        if previous is not None and row["label"] != previous:
            out.append({"round_index": index, "round_key": row["round_key"], "label": row["label"]})
        previous = row["label"]
    return out


def fingerprint_changes(store: Store, device_id: str) -> List[Dict[str, Any]]:
    """Every round at which a fingerprint field (OS version, build, SDK, refresh rate,
    network state) differs from the previous round's."""
    out: List[Dict[str, Any]] = []
    previous: Optional[Dict[str, Any]] = None
    for index, row in enumerate(store.rounds(device_id)):
        fp = json.loads(row["fingerprint_json"] or "{}")
        if previous is not None:
            for field in _FINGERPRINT_FIELDS:
                if field in fp and field in previous and fp[field] != previous[field]:
                    out.append({"round_index": index, "round_key": row["round_key"], "field": field,
                                "before": str(previous[field]), "after": str(fp[field])})
        previous = fp
    return out


def device_overview(store: Store, cfg: Config, device_id: str) -> Dict[str, Any]:
    """Detections, label boundaries, fingerprint changes and labels of one device."""
    labels: List[str] = []
    for row in store.rounds(device_id):
        if row["label"] not in labels:
            labels.append(row["label"])
    return {
        "detections": detections_for_device(store, device_id),
        "labels": labels,
        "label_boundaries": label_boundaries(store, device_id),
        "fingerprint_changes": fingerprint_changes(store, device_id),
    }
