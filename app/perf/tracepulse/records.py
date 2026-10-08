"""Round records: the portable, trace-free form of a round.

One file per round on the `perf-history` branch at `records/<device_id>/<round_id>.json`,
append-only, validated against `schema/round-record.schema.json`
(specs/007-performance-pipeline/contracts/round-record.schema.json). The history
cache (`store.rebuild`) and the report are derived from these files.
"""

import json
import os
import re
from dataclasses import asdict, dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional

import jsonschema

from .summary import summarize

SCHEMA_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema", "round-record.schema.json")
SCHEMA_VERSION = 1

_LABEL_SAFE = re.compile(r"[^A-Za-z0-9._-]+")


class RecordError(Exception):
    """A record that violates the schema."""


@dataclass(frozen=True)
class DeviceInfo:
    device_id: str
    platform: str
    kind: str
    model: str
    reference: bool


@dataclass(frozen=True)
class MetricResult:
    """One metric of one journey: per-iteration `samples` and the derived
    `summary`. A single-valued metric (app size) has no samples and passes its
    value as `value`."""
    unit: str
    samples: List[float]
    source: str
    value: Optional[float] = None
    summary: Dict[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.summary:
            if self.samples:
                computed = summarize(self.samples)
            elif self.value is not None:
                computed = summarize([self.value])
            else:
                raise RecordError("a MetricResult needs samples or a value")
            object.__setattr__(self, "summary", computed)
        object.__setattr__(self, "samples", [float(v) for v in self.samples])

    def to_dict(self) -> Dict[str, Any]:
        return {"unit": self.unit, "samples": list(self.samples), "summary": dict(self.summary),
                "source": self.source}

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "MetricResult":
        summary = dict(data["summary"])
        samples = list(data.get("samples", []))
        value = None if samples else summary.get("median")
        return cls(unit=data["unit"], samples=samples, source=data["source"], value=value,
                   summary=summary)


@dataclass(frozen=True)
class JourneyResult:
    journey: str
    iterations: int
    metrics: Dict[str, MetricResult]

    def to_dict(self) -> Dict[str, Any]:
        return {"journey": self.journey, "iterations": self.iterations,
                "metrics": {key: m.to_dict() for key, m in self.metrics.items()}}

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "JourneyResult":
        return cls(journey=data["journey"], iterations=data["iterations"],
                   metrics={key: MetricResult.from_dict(m) for key, m in data["metrics"].items()})


@dataclass
class RoundRecord:
    round_id: str
    device: DeviceInfo
    label: str
    build: Dict[str, Any]
    fingerprint: Dict[str, Any]
    machine: Dict[str, Any]
    checks: List[Dict[str, Any]]
    status: str
    journeys: List[JourneyResult]
    started_at: str
    finished_at: str
    schema_version: int = SCHEMA_VERSION
    session: Optional[Dict[str, int]] = None
    failure_reason: Optional[str] = None
    badges: List[str] = field(default_factory=list)
    gate: List[Dict[str, Any]] = field(default_factory=list)
    artifacts: List[Dict[str, str]] = field(default_factory=list)

    # -- serialisation ------------------------------------------------------

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "schema_version": self.schema_version,
            "round_id": self.round_id,
            "device": asdict(self.device),
            "label": self.label,
            "build": dict(self.build),
            "fingerprint": dict(self.fingerprint),
            "machine": dict(self.machine),
            "checks": [dict(c) for c in self.checks],
            "status": self.status,
            "badges": list(self.badges),
            "journeys": [j.to_dict() for j in self.journeys],
            "gate": [dict(g) for g in self.gate],
            "artifacts": [dict(a) for a in self.artifacts],
            "started_at": self.started_at,
            "finished_at": self.finished_at,
        }
        if self.session is not None:
            data["session"] = dict(self.session)
        if self.failure_reason is not None:
            data["failure_reason"] = self.failure_reason
        return data

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), indent=1, sort_keys=True) + "\n"

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "RoundRecord":
        return cls(
            round_id=data["round_id"],
            device=DeviceInfo(**data["device"]),
            label=data["label"],
            build=dict(data["build"]),
            fingerprint=dict(data["fingerprint"]),
            machine=dict(data["machine"]),
            checks=[dict(c) for c in data.get("checks", [])],
            status=data["status"],
            journeys=[JourneyResult.from_dict(j) for j in data.get("journeys", [])],
            started_at=data["started_at"],
            finished_at=data["finished_at"],
            schema_version=data.get("schema_version", SCHEMA_VERSION),
            session=dict(data["session"]) if data.get("session") is not None else None,
            failure_reason=data.get("failure_reason"),
            badges=list(data.get("badges", [])),
            gate=[dict(g) for g in data.get("gate", [])],
            artifacts=[dict(a) for a in data.get("artifacts", [])],
        )

    @classmethod
    def from_json(cls, text: str) -> "RoundRecord":
        return cls.from_dict(json.loads(text))

    @classmethod
    def load(cls, path: str) -> "RoundRecord":
        with open(path, "r") as fh:
            data = json.load(fh)
        cls.validate(data)
        return cls.from_dict(data)

    # -- validation ---------------------------------------------------------

    @staticmethod
    def validate(data: Dict[str, Any]) -> None:
        """Raise `RecordError` naming the offending field when `data` violates the schema."""
        try:
            jsonschema.validate(data, _schema(), cls=jsonschema.Draft202012Validator)
        except jsonschema.ValidationError as exc:
            where = ".".join(str(p) for p in exc.absolute_path) or "record"
            raise RecordError(f"{where}: {exc.message}") from exc


_SCHEMA_CACHE: Optional[Dict[str, Any]] = None


def _schema() -> Dict[str, Any]:
    global _SCHEMA_CACHE
    if _SCHEMA_CACHE is None:
        with open(SCHEMA_PATH, "r") as fh:
            _SCHEMA_CACHE = json.load(fh)
    return _SCHEMA_CACHE


def make_round_id(started_at: datetime, label: str) -> str:
    """`<utc-timestamp>_<label>` with the label reduced to the schema's safe characters."""
    stamp = started_at.strftime("%Y%m%dT%H%M%SZ")
    safe = _LABEL_SAFE.sub("-", label).strip("-") or "round"
    return f"{stamp}_{safe}"


def record_path(device_id: str, round_id: str) -> str:
    """The record's path inside the history branch: `records/<device_id>/<round_id>.json`."""
    return os.path.join("records", device_id, f"{round_id}.json")
