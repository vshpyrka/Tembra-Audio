"""The report: one self-contained HTML file rendered from the rebuilt history
(specs/007-performance-pipeline/contracts/report.md).

`report_data` builds the embedded JSON (devices, rounds, series as compact
`[median, p25, p75]` points, detections with the label at onset, gate decisions,
label boundaries, fingerprint changes, plain-language text, catalogue notes);
`generate_report` renders `templates/report.html.j2` with the CSS and JS inlined.
The page has three views by URL hash (`#/`, `#/device/<id>`,
`#/device/<id>/<series>`), draws its charts as inline SVG on demand, makes no
network request, and reads in light and dark. Identical history, identical bytes.
"""

import json
import os
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import jinja2

from .analysis import device_overview
from .catalogue import CATALOGUE, CatalogueError, FAMILIES, lookup
from .config import Config
from .store import Store, split_series_key
from .text import (
    PLAIN_STATUS, SIMULATOR_NOTE, chart_sentence, device_headline, plain_metric, plain_series, status_ladder,
)

_HERE = os.path.dirname(os.path.abspath(__file__))
_TEMPLATE_DIR = os.path.join(_HERE, "templates")
_STATIC_DIR = os.path.join(_HERE, "static")
_JOURNEY_ORDER = {"startup": 0, "tuner": 1, "metronome": 2, "timeline": 3, "recording": 4, "mixer": 5, "stress": 6, "windowing": 7}
_PLATFORM_ORDER = {"android": 0, "ios": 1}
_FAMILY_TITLES = {"startup": "Startup", "frames": "Frames", "memory": "Memory", "cpu": "CPU",
                  "journey": "Journeys", "section": "Sections", "app": "App size", "audio": "Audio health"}


def _round(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    return float(f"{value:.5g}")


def _unit_of(platform: str, series: str, cfg: Config) -> str:
    try:
        return lookup(platform, split_series_key(series)[1], cfg.catalogue).unit
    except CatalogueError:
        return ""


def _date(ts: int) -> str:
    return datetime.fromtimestamp(ts, tz=timezone.utc).strftime("%Y-%m-%d %H:%M")


def _catalogue_notes() -> Dict[str, Any]:
    notes: Dict[str, Any] = {}
    for (platform, key), metric in CATALOGUE.items():
        entry = notes.setdefault(key, {"name": plain_metric(key), "family": metric.family, "note": ""})
        entry[platform] = {"unit": metric.unit, "source": metric.source,
                           "gate_limit_pct": metric.gate_limit_pct, "gate_limit_abs": metric.gate_limit_abs,
                           "comparable": metric.comparable}
        if metric.note and not entry["note"]:
            entry["note"] = metric.note
    notes["section.*"] = {"name": "Named sections", "family": "section",
                          "note": "The same instrumentation and name on both platforms (android: TraceSectionMetric; ios: XCTOSSignpostMetric).",
                          "android": {"unit": "ms / count", "source": "macrobenchmark TraceSectionMetric(name, Mode.Sum)"},
                          "ios": {"unit": "ms / count", "source": "xcresult XCTOSSignpostMetric(subsystem: audio.tembra, name:)"}}
    return notes


def _device_entry(store: Store, cfg: Config, row) -> Dict[str, Any]:
    device_id = row["device_id"]
    platform = row["platform"]
    kind = row["kind"]
    overview = device_overview(store, cfg, device_id)
    detections = overview["detections"]
    rounds_rows = store.rounds(device_id)
    index_of = {r["id"]: i for i, r in enumerate(rounds_rows)}

    rounds: List[Dict[str, Any]] = []
    for i, r in enumerate(rounds_rows):
        decisions = store.gate_decisions(r["id"])
        summary = {"above_limit": 0, "within_limit": 0, "inactive": 0}
        for d in decisions:
            summary[d["state"]] = summary.get(d["state"], 0) + 1
        rounds.append({
            "index": i, "round_key": r["round_key"], "ts": r["ts"], "date": _date(r["ts"]), "label": r["label"],
            "status": r["status"], "badges": json.loads(r["quality_flags"] or "[]"),
            "commit": (r["target_sha"] or "")[:10], "dirty": bool(r["target_dirty"]), "machine": r["machine"],
            "fingerprint": json.loads(r["fingerprint_json"] or "{}"),
            "session": {"index": r["session_index"], "size": r["session_size"]} if r["session_index"] else None,
            "failure_reason": r["notes"] if r["status"] != "succeeded" else "",
            "gate_summary": summary,
            "artifacts": [{"kind": a["kind"], "path": a["path"]} for a in store.artifacts(r["id"])],
        })

    detections_by_series: Dict[str, List[Dict[str, Any]]] = {}
    for d in detections:
        detections_by_series.setdefault(d["metric"], []).append(d)

    families: Dict[str, List[Dict[str, Any]]] = {}
    for series in store.metrics(device_id):
        journey, metric = split_series_key(series)
        try:
            entry = lookup(platform, metric, cfg.catalogue)
        except CatalogueError:
            continue
        points: List[Optional[List[Optional[float]]]] = [None] * len(rounds_rows)
        for r in rounds_rows:
            agg = store.aggregate(series, r["id"])
            if agg is not None:
                points[index_of[r["id"]]] = [_round(agg["median"]), _round(agg["p25"]), _round(agg["p75"])]
        gate: List[Dict[str, Any]] = []
        for r in rounds_rows:
            for d in store.gate_decisions(r["id"]):
                if d["metric"] != series:
                    continue
                baseline = json.loads(d["baseline_rounds"] or "[]")
                gate.append({"round_index": index_of[r["id"]], "state": d["state"], "reason": d["reason"],
                             "limit_pct": d["limit_pct"], "observed_pct": _round(d["observed_pct"]),
                             "limit_abs": d["limit_abs"], "observed_abs": _round(d["observed_abs"]),
                             "baseline_round_keys": baseline})
        series_detections = sorted(detections_by_series.get(series, []), key=lambda d: d["onset_round_index"])
        worst = max((d for d in series_detections), key=lambda d: Store.STATUS_RANK[d["status"]], default=None)
        families.setdefault(entry.family, []).append({
            "key": series, "journey": journey, "metric": metric, "title": plain_series(series), "unit": entry.unit,
            "family": entry.family, "comparable": entry.comparable, "single_valued": entry.single_valued,
            "gate_limit_pct": entry.gate_limit_pct, "gate_limit_abs": entry.gate_limit_abs,
            "points": points, "gate": gate,
            "detections": [{k: (_round(v) if isinstance(v, float) else v) for k, v in d.items()} for d in series_detections],
            "sentence": chart_sentence(worst, series),
            "status": worst["status"] if worst else None,
        })
    family_list = []
    for family in FAMILIES:
        if family in families:
            series_list = sorted(families[family], key=lambda s: (_JOURNEY_ORDER.get(s["journey"] or "startup", 9), s["metric"]))
            family_list.append({"family": family, "title": _FAMILY_TITLES[family], "series": series_list})

    latest_flags = []
    if rounds:
        latest = rounds[-1]
        for d in store.gate_decisions(rounds_rows[-1]["id"]):
            if d["state"] == "above_limit":
                latest_flags.append({"metric": d["metric"], "observed_pct": d["observed_pct"], "limit_pct": d["limit_pct"],
                                     "observed_abs": d["observed_abs"], "limit_abs": d["limit_abs"],
                                     "unit": _unit_of(platform, d["metric"], cfg),
                                     "round_key": latest["round_key"]})
    succeeded = sum(1 for r in rounds if r["status"] == "succeeded")
    headline = device_headline(rounds=succeeded, min_history=cfg.detection.min_history, detections=detections,
                               flagged=latest_flags, window=cfg.gate.clean_rounds)
    badges = []
    if kind == "simulator":
        badges += ["simulator", "non-reference"]
    elif not row["reference"]:
        badges.append("non-reference")
    last_label = rounds[-1]["label"] if rounds else ""
    last_session = [r for r in rounds if r["label"] == last_label]
    session_indexes = {r["index"] for r in last_session}
    recent_changes = [d for d in detections if d["status"] in ("SUSPECT", "CONFIRMED") and d["onset_round_index"] in session_indexes]
    return {
        "device_id": device_id, "platform": platform, "kind": kind, "model": row["model"],
        "reference": bool(row["reference"]), "badges": badges,
        "alias": next((e.alias for did, e in cfg.devices.items() if did == device_id and e.alias), ""),
        "overrides": next((e.overrides for did, e in cfg.devices.items() if did == device_id), {}),
        "headline": headline, "note": SIMULATOR_NOTE if kind == "simulator" else "",
        "rounds": rounds, "labels": overview["labels"], "label_boundaries": overview["label_boundaries"],
        "fingerprint_changes": overview["fingerprint_changes"],
        "families": family_list,
        "counts": {"rounds": len(rounds), "open_flags": len(latest_flags), "recent_changes": len(recent_changes),
                   "worst_status": max((d["status"] for d in detections), key=lambda s: Store.STATUS_RANK[s], default=None)},
        "last_round": {"date": rounds[-1]["date"], "label": last_label} if rounds else None,
        "detections": detections,
    }


def report_data(store: Store, cfg: Config) -> Dict[str, Any]:
    devices = [_device_entry(store, cfg, row) for row in store.devices()]
    devices.sort(key=lambda d: (d["kind"] != "device", _PLATFORM_ORDER.get(d["platform"], 9), d["model"]))
    flagged: List[Dict[str, Any]] = []
    changes: List[Dict[str, Any]] = []
    for d in devices:
        latest = d["rounds"][-1] if d["rounds"] else None
        if latest:
            for f in d["families"]:
                for s in f["series"]:
                    for g in s["gate"]:
                        if g["round_index"] == latest["index"] and g["state"] == "above_limit":
                            flagged.append({"device_id": d["device_id"], "device_model": d["model"], "metric": s["key"],
                                            "title": s["title"], "observed_pct": g["observed_pct"],
                                            "limit_pct": g["limit_pct"], "observed_abs": g["observed_abs"],
                                            "limit_abs": g["limit_abs"], "unit": s["unit"],
                                            "round_key": latest["round_key"]})
        for det in d["detections"]:
            if det["status"] in ("SUSPECT", "CONFIRMED"):
                changes.append({"device_id": d["device_id"], "device_model": d["model"], "metric": det["metric"],
                                "title": plain_series(det["metric"]), "status": det["status"],
                                "status_text": status_ladder(det["status"]), "delta_pct": _round(det["delta_pct"]),
                                "label_at_onset": det["label_at_onset"], "onset_round_key": det["onset_round_key"],
                                "onset_round_index": det["onset_round_index"],
                                "sentence": chart_sentence(det, det["metric"])})
    changes.sort(key=lambda c: (-c["onset_round_index"], c["device_model"], c["metric"]))
    total_rounds = sum(d["counts"]["rounds"] for d in devices)
    last = max((r for d in devices for r in d["rounds"]), key=lambda r: r["ts"], default=None)
    last_device = next((d["model"] for d in devices for r in d["rounds"] if last and r["round_key"] == last["round_key"]), "")
    confirmed = sum(1 for c in changes if c["status"] == "CONFIRMED")
    summary_parts = [f"{len(devices)} device{'s' if len(devices) != 1 else ''}, {total_rounds} round{'s' if total_rounds != 1 else ''}"]
    if last:
        summary_parts.append(f"last session {last['label']} on {last['date'][:10]} ({last_device})")
    if flagged:
        by_device: Dict[str, int] = {}
        for f in flagged:
            by_device[f["device_model"]] = by_device.get(f["device_model"], 0) + 1
        summary_parts.append("; ".join(f"{n} metric{'s' if n != 1 else ''} above their limit on {m}" for m, n in by_device.items()))
    summary_parts.append(f"{confirmed} confirmed" if confirmed else "nothing confirmed")
    return {
        "summary": {"devices": len(devices), "rounds": total_rounds, "text": "; ".join(summary_parts) + ".",
                    "flagged": len(flagged), "confirmed": confirmed},
        "flagged": flagged,
        "changes": changes,
        "devices": devices,
        "catalogue": _catalogue_notes(),
        "status_text": PLAIN_STATUS,
        "config": {"min_history": cfg.detection.min_history, "clean_rounds": cfg.gate.clean_rounds,
                   "iterations": cfg.collection.iterations},
    }


def _read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def generate_report(store: Store, cfg: Config, out_path: str, now_ts: int) -> str:
    data = report_data(store, cfg)
    env = jinja2.Environment(loader=jinja2.FileSystemLoader(_TEMPLATE_DIR), autoescape=True,
                             trim_blocks=True, lstrip_blocks=True)
    template = env.get_template("report.html.j2")
    html = template.render(
        data=data,
        data_json=json.dumps(data, sort_keys=True, separators=(",", ":")).replace("</", "<\\/"),
        css=_read(os.path.join(_STATIC_DIR, "report.css")),
        js=_read(os.path.join(_STATIC_DIR, "report.js")),
        generated=datetime.fromtimestamp(now_ts, tz=timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
    )
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write(html)
    return out_path
