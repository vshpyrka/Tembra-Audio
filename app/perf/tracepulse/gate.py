"""The informational gate (spec FR-011, research D8).

Per metric of a round, the change of the round's median against the median of
the last N clean rounds' medians on the same series (N = `gate.clean_rounds`,
default 5): `above_limit` when the change exceeds the metric's limit in the
worse direction (every metric is higher-is-worse), `within_limit` otherwise,
`inactive` with a reason below N clean rounds, for simulator series (unless
`gate.simulators: active`) and for disabled metrics. Decisions ride the round
record (`gate[]`) and the cache (`gate_decisions`); they never change an exit
status. A clean round succeeded, carries no `noisy`/`thermal` badge and is not in
an environment-suspect group; a gate flag does not make a round unclean.
"""

import json
from typing import Any, Dict, List, Optional, Sequence

from .catalogue import CatalogueError, lookup
from .config import Config
from .devices import Device
from .records import RoundRecord
from .store import Store, series_key, split_series_key
from .summary import percentile

_UNCLEAN_BADGES = {"noisy", "thermal"}


def _median(values: Sequence[float]) -> float:
    return percentile(list(values), 50)


def decide(baseline: Sequence[float], value: float, limit_pct: Optional[float], limit_abs: Optional[float],
           ids: Sequence[str]) -> Dict[str, Any]:
    """One decision from a clean baseline (medians of the N clean rounds) and the round's median."""
    base = _median(baseline)
    if limit_pct is not None:
        observed = ((value - base) / base * 100.0) if base else 0.0
        state = "above_limit" if observed > limit_pct else "within_limit"
        return {"state": state, "limit_pct": float(limit_pct), "observed_pct": observed, "baseline_rounds": list(ids)}
    observed_abs = value - base
    state = "above_limit" if observed_abs > float(limit_abs or 0.0) else "within_limit"
    observed_pct = ((value - base) / base * 100.0) if base else 0.0
    return {"state": state, "limit_abs": float(limit_abs or 0.0), "observed_abs": observed_abs,
            "observed_pct": observed_pct, "baseline_rounds": list(ids)}


def clean_round_ids(store: Store, cfg: Config, device_id: str, before_round_id: Optional[int] = None) -> List[int]:
    """Ids of the clean rounds of one device in insertion order (optionally only
    those before `before_round_id`)."""
    env_suspect = {row["onset_round_id"] for row in store.all_detections() if row["status"] == "ENV_SUSPECT"}
    clean: List[int] = []
    for row in store.rounds(device_id):
        if before_round_id is not None and row["id"] >= before_round_id:
            break
        if row["status"] != "succeeded":
            continue
        badges = set(json.loads(row["quality_flags"] or "[]"))
        if badges & _UNCLEAN_BADGES or row["id"] in env_suspect:
            continue
        clean.append(row["id"])
    return clean


def _round_series_values(record: RoundRecord) -> Dict[str, float]:
    """The round's median per series key, from its journeys."""
    values: Dict[str, float] = {}
    for journey in record.journeys:
        for metric, result in journey.metrics.items():
            try:
                single = lookup(record.device.platform, metric).single_valued
            except CatalogueError:
                continue
            values[series_key(None if single else journey.journey, metric)] = result.summary["median"]
    return values


def _decide_series(store: Store, cfg: Config, device: Device, series: str, value: float,
                   clean_ids: List[int]) -> Dict[str, Any]:
    _journey, metric = split_series_key(series)
    entry = lookup(device.platform, metric, cfg.catalogue)
    base: Dict[str, Any] = {"metric": series}
    if not entry.enabled:
        return dict(base, state="inactive", reason="disabled")
    if device.kind == "simulator" and cfg.gate.simulators != "active":
        return dict(base, state="inactive", reason="simulator series")
    n = cfg.gate.clean_rounds
    points = dict(store.series(series, device.device_id))
    baseline_ids = [rid for rid in clean_ids if rid in points][-n:]
    limit_field = {"limit_pct": entry.gate_limit_pct} if entry.gate_limit_pct is not None else {"limit_abs": entry.gate_limit_abs}
    if len(baseline_ids) < n:
        return dict(base, state="inactive", reason=f"fewer than {n} clean rounds ({len(baseline_ids)})", **limit_field)
    keys = {row["id"]: row["round_key"] for row in store.rounds(device.device_id)}
    decision = decide([points[rid] for rid in baseline_ids], value, entry.gate_limit_pct, entry.gate_limit_abs,
                      [keys[rid] for rid in baseline_ids])
    return dict(base, **decision)


def decide_round(store: Store, cfg: Config, device: Device, record: RoundRecord) -> List[Dict[str, Any]]:
    """Decisions for a round that is not yet in the store, against the store's clean rounds."""
    if record.status != "succeeded":
        return []
    clean = clean_round_ids(store, cfg, device.device_id)
    return [_decide_series(store, cfg, device, series, value, clean)
            for series, value in sorted(_round_series_values(record).items())]


def record_all_gates(store: Store, cfg: Config) -> int:
    """Recompute and store the decisions of every succeeded round of every device,
    each against the clean rounds before it (`tracepulse analyze`). Idempotent."""
    written = 0
    for dev in store.devices():
        device = Device(device_id=dev["device_id"], platform=dev["platform"], kind=dev["kind"], model=dev["model"],
                        reference=bool(dev["reference"]))
        for row in store.rounds(device.device_id):
            if row["status"] != "succeeded":
                continue
            clean = clean_round_ids(store, cfg, device.device_id, before_round_id=row["id"])
            store.clear_gate_decisions(row["id"])
            for series in store.metrics(device.device_id):
                points = dict(store.series(series, device.device_id))
                if row["id"] not in points:
                    continue
                try:
                    decision = _decide_series(store, cfg, device, series, points[row["id"]], clean)
                except CatalogueError:
                    continue
                store.add_gate_decision(row["id"], series, decision["state"], decision.get("reason", ""),
                                        decision.get("limit_pct", decision.get("limit_abs")),
                                        decision.get("observed_pct"), decision.get("baseline_rounds", []))
                written += 1
    return written
