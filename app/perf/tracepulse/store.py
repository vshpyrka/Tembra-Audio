"""SQLite history cache with append-only ratcheting detections.

The round records on the `perf-history` branch are the source of truth; this
file is rebuilt from them by `Store.rebuild` (`tracepulse sync`). The trace-pulse
schema is kept verbatim (column names and order are relied on by the detector
and its tests) and extended with the device identity, label, kind, reference
flag, status and machine on `rounds`, the journey on `aggregates`, and a
`gate_decisions` table. All timestamps are caller-supplied ints: this module
never reads the wall clock, so results stay deterministic for golden tests.

Series are keyed `(device_id, series key)` where the series key is
`<journey>/<metric>` for journey metrics and the bare metric key for
round-level metrics (`app.size_bytes`); see `series_key`.
"""

import json
import math
import os
import sqlite3
from datetime import datetime, timezone
from typing import Iterable, List, Optional, Tuple

from .catalogue import CatalogueError, lookup
from .records import RoundRecord, sections_unmeasured
from .summary import percentile


_SCHEMA = """
CREATE TABLE IF NOT EXISTS rounds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    label TEXT NOT NULL,
    target_sha TEXT NOT NULL,
    target_dirty INTEGER NOT NULL,
    injected_json TEXT NOT NULL,
    device_serial TEXT NOT NULL,
    device_fingerprint TEXT NOT NULL,
    quality_flags TEXT NOT NULL,
    thermal_sleep_s REAL NOT NULL,
    notes TEXT NOT NULL,
    round_key TEXT NOT NULL DEFAULT '',
    device_id TEXT NOT NULL DEFAULT '',
    platform TEXT NOT NULL DEFAULT 'android',
    kind TEXT NOT NULL DEFAULT 'device',
    model TEXT NOT NULL DEFAULT '',
    reference INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'succeeded',
    machine TEXT NOT NULL DEFAULT '',
    session_index INTEGER,
    session_size INTEGER,
    fingerprint_json TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_rounds_device ON rounds(device_id, id);

CREATE TABLE IF NOT EXISTS samples (
    round_id INTEGER NOT NULL REFERENCES rounds(id),
    benchmark TEXT NOT NULL,
    metric TEXT NOT NULL,
    iteration INTEGER NOT NULL,
    value REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_samples_metric_round ON samples(metric, round_id);

CREATE TABLE IF NOT EXISTS aggregates (
    round_id INTEGER NOT NULL REFERENCES rounds(id),
    metric TEXT NOT NULL,
    n INTEGER NOT NULL,
    min REAL NOT NULL,
    max REAL NOT NULL,
    mean REAL NOT NULL,
    median REAL NOT NULL,
    p25 REAL NOT NULL,
    p75 REAL NOT NULL,
    p90 REAL NOT NULL,
    p95 REAL NOT NULL,
    p99 REAL NOT NULL,
    stddev REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_aggregates_metric_round ON aggregates(metric, round_id);

CREATE TABLE IF NOT EXISTS detections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    computed_ts INTEGER,
    metric TEXT NOT NULL,
    onset_round_id INTEGER NOT NULL REFERENCES rounds(id),
    direction TEXT NOT NULL,
    step_size REAL,
    stepfit_score REAL,
    mwu_p REAL,
    delta_pct REAL,
    status TEXT NOT NULL,
    persistence_count INTEGER NOT NULL,
    triage_status TEXT NOT NULL DEFAULT 'NEW',
    env_suspect INTEGER NOT NULL,
    ground_truth_match INTEGER
);
CREATE INDEX IF NOT EXISTS idx_detections_metric_onset ON detections(metric, onset_round_id);

CREATE TABLE IF NOT EXISTS artifacts (
    round_id INTEGER NOT NULL REFERENCES rounds(id),
    kind TEXT NOT NULL,
    path TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_artifacts_round ON artifacts(round_id);

CREATE TABLE IF NOT EXISTS gate_decisions (
    round_id INTEGER NOT NULL REFERENCES rounds(id),
    metric TEXT NOT NULL,
    state TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    limit_pct REAL,
    observed_pct REAL,
    baseline_rounds TEXT NOT NULL DEFAULT '[]',
    limit_abs REAL,
    observed_abs REAL
);
CREATE INDEX IF NOT EXISTS idx_gate_round ON gate_decisions(round_id);
"""

# columns added after the first caches were written; an older cache gets them on open
_LATER_COLUMNS = {"gate_decisions": ("limit_abs REAL", "observed_abs REAL")}


def series_key(journey: Optional[str], metric: str) -> str:
    """`<journey>/<metric>` for journey metrics; the bare key for round-level ones."""
    return f"{journey}/{metric}" if journey else metric


def split_series_key(key: str) -> Tuple[Optional[str], str]:
    """Inverse of `series_key`."""
    journey, sep, metric = key.partition("/")
    return (journey, metric) if sep else (None, key)


def _round_level(platform: str, metric: str) -> bool:
    """Single-valued catalogue metrics (app size) are one series per device, not per journey."""
    try:
        return lookup(platform, metric).single_valued
    except CatalogueError:
        return False


def _before_source(platform: str, metric: str, started_at: str) -> bool:
    """True when the round predates the catalogue entry's current source (`since`):
    its values measured something else and stay out of the cache. The record keeps them."""
    try:
        since = lookup(platform, metric).since
    except CatalogueError:
        return False
    return bool(since) and _epoch(started_at) < _epoch(since)


def _epoch(iso: str) -> int:
    """ISO-8601 UTC (`...Z` or with offset) to epoch seconds."""
    text = iso.replace("Z", "+00:00")
    dt = datetime.fromisoformat(text)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return int(dt.timestamp())


class Store:
    """Append-only SQLite history store for rounds, samples, detections and gate decisions.

    Usable as a context manager: `with Store(db_path) as s: ...`.
    """

    STATUS_RANK = {
        "PRELIMINARY": 0,
        "SUSPECT": 1,
        "IMPROVED": 1,
        "ENV_SUSPECT": 1,
        "CONFIRMED": 2,
    }

    def __init__(self, db_path):
        self._conn = sqlite3.connect(db_path)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA foreign_keys=ON")
        self._conn.executescript(_SCHEMA)
        for table, columns in _LATER_COLUMNS.items():
            present = {row["name"] for row in self._conn.execute(f"PRAGMA table_info({table})")}
            for column in columns:
                if column.split()[0] not in present:
                    self._conn.execute(f"ALTER TABLE {table} ADD COLUMN {column}")
        self._conn.commit()

    def __enter__(self) -> "Store":
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()

    def close(self) -> None:
        self._conn.close()

    # -- rebuild from records ---------------------------------------------

    @classmethod
    def rebuild(cls, db_path: str, records: Iterable[RoundRecord]) -> "Store":
        """Recreate `db_path` from `records` (any order; inserted by started_at,
        then device id, then round id, so the result is deterministic)."""
        for suffix in ("", "-wal", "-shm"):
            path = db_path + suffix
            if os.path.exists(path):
                os.remove(path)
        parent = os.path.dirname(os.path.abspath(db_path))
        os.makedirs(parent, exist_ok=True)
        store = cls(db_path)
        ordered = sorted(records, key=lambda r: (_epoch(r.started_at), r.device.device_id, r.round_id))
        for record in ordered:
            store.add_record(record)
        store._conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        return store

    def add_record(self, record: RoundRecord) -> int:
        """Insert one record: its round row, samples, aggregates, gate decisions and artifacts."""
        failed = record.status != "succeeded"
        notes = record.failure_reason or ""
        round_id = self.add_round(
            ts=_epoch(record.started_at), label=record.label,
            target_sha=record.build.get("commit", ""), target_dirty=bool(record.build.get("dirty", False)),
            injected=list(record.build.get("injected", [])),
            device_serial=record.device.device_id,
            device_fingerprint=record.fingerprint.get("os_version", ""),
            quality_flags=list(record.badges), thermal_sleep_s=0.0, notes=notes,
            round_key=record.round_id, device_id=record.device.device_id,
            platform=record.device.platform, kind=record.device.kind, model=record.device.model,
            reference=record.device.reference, status=record.status,
            machine=record.machine.get("hostname", ""),
            session=record.session, fingerprint=record.fingerprint,
        )
        if not failed:
            unmeasured = sections_unmeasured(record)
            for journey in record.journeys:
                for metric, result in journey.metrics.items():
                    if _before_source(record.device.platform, metric, record.started_at):
                        continue
                    if unmeasured and metric.startswith("section."):
                        continue  # a build without the facade (the release baseline) measured no section
                    key = series_key(None if _round_level(record.device.platform, metric) else journey.journey,
                                     metric)
                    values = result.samples or [result.summary["median"]]
                    self.add_samples(round_id, journey.journey, key, values)
                    self.add_aggregate(round_id, key, values)
        for decision in record.gate:
            self.add_gate_decision(round_id, decision["metric"], decision["state"],
                                   decision.get("reason", ""), decision.get("limit_pct"),
                                   decision.get("observed_pct"), decision.get("baseline_rounds", []),
                                   limit_abs=decision.get("limit_abs"), observed_abs=decision.get("observed_abs"))
        for artifact in record.artifacts:
            self.add_artifact(round_id, artifact["kind"], artifact["path"])
        return round_id

    # -- rounds ---------------------------------------------------------

    def add_round(self, ts, label, target_sha, target_dirty, injected: List[str],
                  device_serial, device_fingerprint, quality_flags: List[str],
                  thermal_sleep_s, notes, round_key: str = "", device_id: Optional[str] = None,
                  platform: str = "android", kind: str = "device", model: str = "",
                  reference: bool = True, status: str = "succeeded", machine: str = "",
                  session: Optional[dict] = None, fingerprint: Optional[dict] = None) -> int:
        device_id = device_serial if device_id is None else device_id
        session = session or {}
        cur = self._conn.execute(
            "INSERT INTO rounds (ts, label, target_sha, target_dirty, injected_json, "
            "device_serial, device_fingerprint, quality_flags, thermal_sleep_s, notes, "
            "round_key, device_id, platform, kind, model, reference, status, machine, "
            "session_index, session_size, fingerprint_json) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (ts, label, target_sha, int(bool(target_dirty)), json.dumps(injected),
             device_serial, device_fingerprint, json.dumps(quality_flags),
             thermal_sleep_s, notes, round_key, device_id, platform, kind, model,
             int(bool(reference)), status, machine, session.get("index"), session.get("size"),
             json.dumps(fingerprint or {}, sort_keys=True)),
        )
        self._conn.commit()
        return cur.lastrowid

    def rounds(self, device_id: Optional[str] = None) -> List[sqlite3.Row]:
        if device_id is None:
            return self._conn.execute("SELECT * FROM rounds ORDER BY id ASC").fetchall()
        return self._conn.execute(
            "SELECT * FROM rounds WHERE device_id = ? ORDER BY id ASC", (device_id,)
        ).fetchall()

    def round_by_key(self, round_key: str) -> Optional[sqlite3.Row]:
        return self._conn.execute("SELECT * FROM rounds WHERE round_key = ?", (round_key,)).fetchone()

    def devices(self) -> List[sqlite3.Row]:
        """One row per device, in order of first appearance."""
        return self._conn.execute(
            "SELECT device_id, platform, kind, model, reference, MIN(id) AS first_round, "
            "MAX(id) AS last_round, COUNT(*) AS rounds FROM rounds "
            "WHERE device_id != '' GROUP BY device_id ORDER BY first_round ASC"
        ).fetchall()

    def round_badges(self, round_id: int) -> List[str]:
        row = self._conn.execute("SELECT quality_flags FROM rounds WHERE id = ?", (round_id,)).fetchone()
        return json.loads(row["quality_flags"]) if row and row["quality_flags"] else []

    def injected(self, round_id: int) -> List[str]:
        row = self._conn.execute("SELECT injected_json FROM rounds WHERE id = ?", (round_id,)).fetchone()
        return json.loads(row["injected_json"]) if row and row["injected_json"] else []

    # -- samples / aggregates --------------------------------------------

    def add_samples(self, round_id: int, benchmark: str, metric: str,
                    values: List[float]) -> None:
        rows = [(round_id, benchmark, metric, i, v) for i, v in enumerate(values)]
        self._conn.executemany(
            "INSERT INTO samples (round_id, benchmark, metric, iteration, value) "
            "VALUES (?, ?, ?, ?, ?)",
            rows,
        )
        self._conn.commit()

    def add_aggregate(self, round_id: int, metric: str, values: List[float]) -> None:
        n = len(values)
        mean = sum(values) / n
        variance = sum((v - mean) ** 2 for v in values) / n
        self._conn.execute(
            "INSERT INTO aggregates (round_id, metric, n, min, max, mean, median, "
            "p25, p75, p90, p95, p99, stddev) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (round_id, metric, n, min(values), max(values), mean,
             percentile(values, 50), percentile(values, 25), percentile(values, 75),
             percentile(values, 90), percentile(values, 95), percentile(values, 99),
             math.sqrt(variance)),
        )
        self._conn.commit()

    def series(self, metric: str, device_id: Optional[str] = None) -> List[Tuple[int, float]]:
        """(round_id, median) per round of one device for one series key, in
        round-insertion order (round_id is the parent's autoincrement id)."""
        if device_id is None:
            rows = self._conn.execute(
                "SELECT round_id, median FROM aggregates WHERE metric = ? ORDER BY round_id ASC",
                (metric,),
            ).fetchall()
        else:
            rows = self._conn.execute(
                "SELECT a.round_id AS round_id, a.median AS median FROM aggregates a "
                "JOIN rounds r ON r.id = a.round_id "
                "WHERE a.metric = ? AND r.device_id = ? ORDER BY a.round_id ASC",
                (metric, device_id),
            ).fetchall()
        return [(row["round_id"], row["median"]) for row in rows]

    def metrics(self, device_id: Optional[str] = None) -> List[str]:
        """Distinct series keys with aggregates on one device (or anywhere), sorted."""
        if device_id is None:
            rows = self._conn.execute("SELECT DISTINCT metric FROM aggregates ORDER BY metric").fetchall()
        else:
            rows = self._conn.execute(
                "SELECT DISTINCT a.metric AS metric FROM aggregates a JOIN rounds r ON r.id = a.round_id "
                "WHERE r.device_id = ? ORDER BY a.metric", (device_id,),
            ).fetchall()
        return [row["metric"] for row in rows]

    def iterations(self, metric: str, round_ids: List[int]) -> List[float]:
        if not round_ids:
            return []
        placeholders = ",".join("?" * len(round_ids))
        rows = self._conn.execute(
            f"SELECT value FROM samples WHERE metric = ? AND round_id IN ({placeholders}) "
            "ORDER BY round_id ASC, iteration ASC",
            (metric, *round_ids),
        ).fetchall()
        return [row["value"] for row in rows]

    def aggregate(self, metric: str, round_id: int) -> Optional[sqlite3.Row]:
        return self._conn.execute(
            "SELECT * FROM aggregates WHERE metric = ? AND round_id = ?", (metric, round_id)
        ).fetchone()

    # -- detections: append-only ratchet ---------------------------------

    def record_detection(self, metric, onset_round_id, direction, step_size, stepfit_score,
                         mwu_p, delta_pct, status, persistence_count, env_suspect,
                         ground_truth_match, computed_ts: Optional[int] = None) -> bool:
        """Insert a detection event iff STATUS_RANK strictly increases (or first event).

        `computed_ts` is stored verbatim when given, NULL otherwise (never the
        wall clock).
        """
        if status not in self.STATUS_RANK:
            raise ValueError(f"unknown detection status: {status!r}")
        current = self.latest_status(metric, onset_round_id)
        if current is not None and self.STATUS_RANK[status] <= self.STATUS_RANK[current]:
            return False
        self._conn.execute(
            "INSERT INTO detections (computed_ts, metric, onset_round_id, direction, "
            "step_size, stepfit_score, mwu_p, delta_pct, status, persistence_count, "
            "env_suspect, ground_truth_match) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (computed_ts, metric, onset_round_id, direction, step_size, stepfit_score,
             mwu_p, delta_pct, status, persistence_count, env_suspect, ground_truth_match),
        )
        self._conn.commit()
        return True

    def latest_status(self, metric: str, onset_round_id: int) -> Optional[str]:
        rows = self._conn.execute(
            "SELECT status FROM detections WHERE metric = ? AND onset_round_id = ?",
            (metric, onset_round_id),
        ).fetchall()
        if not rows:
            return None
        return max((row["status"] for row in rows), key=lambda s: self.STATUS_RANK[s])

    def all_detections(self) -> List[sqlite3.Row]:
        return self._conn.execute("SELECT * FROM detections ORDER BY id ASC").fetchall()

    # -- gate decisions -----------------------------------------------------

    def add_gate_decision(self, round_id: int, metric: str, state: str, reason: str = "",
                          limit_pct: Optional[float] = None, observed_pct: Optional[float] = None,
                          baseline_rounds: Optional[List[str]] = None, limit_abs: Optional[float] = None,
                          observed_abs: Optional[float] = None) -> None:
        self._conn.execute(
            "INSERT INTO gate_decisions (round_id, metric, state, reason, limit_pct, observed_pct, "
            "baseline_rounds, limit_abs, observed_abs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (round_id, metric, state, reason or "", limit_pct, observed_pct,
             json.dumps(list(baseline_rounds or [])), limit_abs, observed_abs),
        )
        self._conn.commit()

    def gate_decisions(self, round_id: int) -> List[sqlite3.Row]:
        return self._conn.execute(
            "SELECT * FROM gate_decisions WHERE round_id = ? ORDER BY rowid ASC", (round_id,)
        ).fetchall()

    def clear_gate_decisions(self, round_id: int) -> None:
        self._conn.execute("DELETE FROM gate_decisions WHERE round_id = ?", (round_id,))
        self._conn.commit()

    # -- artifacts --------------------------------------------------------

    def add_artifact(self, round_id: int, kind: str, path: str) -> None:
        self._conn.execute(
            "INSERT INTO artifacts (round_id, kind, path) VALUES (?, ?, ?)",
            (round_id, kind, path),
        )
        self._conn.commit()

    def artifacts(self, round_id: int) -> List[sqlite3.Row]:
        """Every artifact row for one round, in insertion order (`rowid`)."""
        return self._conn.execute(
            "SELECT * FROM artifacts WHERE round_id = ? ORDER BY rowid ASC",
            (round_id,),
        ).fetchall()
