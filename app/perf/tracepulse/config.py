"""`app/tracepulse.yml` loader (specs/007-performance-pipeline/contracts/cli.md).

Every section has defaults (the trace-pulse detection parameters are unchanged);
an unknown key at any level is a `ConfigError`, which the CLI reports as exit
status 4. The metric catalogue is code (`catalogue.py`); the file may override
gate limits and screening gates and disable keys, never add one.
"""

import os
from dataclasses import dataclass, field, fields
from typing import Any, Dict, List, Mapping, Optional, Tuple

import yaml

from .catalogue import CatalogueError, Metric, apply_overrides

# PERF_DIR: the pipeline module directory (app/perf), home of data/, reports/,
# regressions/ and the history worktree. DEFAULT_CONFIG_PATH: the Gradle
# root's tracepulse.yml (app/tracepulse.yml).
PERF_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_CONFIG_PATH = os.path.join(os.path.dirname(PERF_DIR), "tracepulse.yml")

DEFAULT_JOURNEYS = ["startup", "tuner", "metronome", "timeline", "recording", "mixer", "stress", "windowing"]


class ConfigError(Exception):
    """Unknown key, invalid value or an override of a metric the catalogue lacks."""


@dataclass
class Screening:
    min_delta_pct: Dict[str, float] = field(default_factory=dict)
    min_delta_abs: Dict[str, float] = field(default_factory=dict)


@dataclass
class Detection:
    width: int = 5
    threshold: int = 15
    min_sd: float = 0.001
    min_history: int = 11
    screening_gates: Screening = field(default_factory=Screening)


@dataclass
class Confirm:
    rounds_per_side: int = 2
    alpha: float = 0.05
    q: float = 0.05
    require_direction_agreement: bool = True


@dataclass
class APK:
    min_delta_pct: float = 0.5
    persistence_rounds: int = 1


@dataclass
class AB:
    p_different: float = 0.05
    p_same: float = 0.10
    max_baseline_rounds: int = 2


@dataclass
class Quality:
    cov_max: float = 0.15
    cov_max_overrides: Dict[str, float] = field(default_factory=dict)
    thermal_flag: bool = True
    battery_temp_max_c: float = 35.0
    battery_min_level: int = 50


@dataclass
class SuiteSpike:
    min_dimensions: int = 3


@dataclass
class Collection:
    iterations: int = 20
    session_rounds: int = 5
    compilation_mode: str = "full"


@dataclass
class History:
    branch: str = "perf-history"
    remote: str = "origin"
    worktree: str = "perf/.history"


@dataclass
class Gate:
    clean_rounds: int = 5
    limits_pct: Dict[str, float] = field(default_factory=dict)
    limits_abs: Dict[str, float] = field(default_factory=dict)
    disabled: List[str] = field(default_factory=list)
    simulators: str = "inactive"


@dataclass
class Report:
    # labels of measurement experiments (noise baselines), never a feature to compare
    experiment_labels: List[str] = field(default_factory=lambda: ["noise-baseline", "release-baseline"])


@dataclass
class DeviceEntry:
    platform: str = ""
    model: str = ""
    alias: str = ""
    kind: str = "device"
    reference: bool = True
    overrides: Dict[str, Any] = field(default_factory=dict)


@dataclass
class Android:
    package: str = "com.tembraaudio"
    activity: str = "audio.tembra.android.MainActivity"
    build_type: str = "perf"
    benchmarks_module: str = ":perf-benchmarks"
    test_package: str = "audio.tembra.perf"
    adb: str = "adb"


@dataclass
class IOS:
    project: str = "iosApp/iosApp.xcodeproj"
    scheme: str = "TembraPerf"
    configuration: str = "Perf"
    bundle_id: str = "com.tembraaudio"
    test_target: str = "TembraPerfTests"
    size_variant: str = "iPhone14,7"


@dataclass
class Config:
    detection: Detection = field(default_factory=Detection)
    confirm: Confirm = field(default_factory=Confirm)
    apk: APK = field(default_factory=APK)
    ab: AB = field(default_factory=AB)
    quality: Quality = field(default_factory=Quality)
    suite_spike: SuiteSpike = field(default_factory=SuiteSpike)
    collection: Collection = field(default_factory=Collection)
    history: History = field(default_factory=History)
    gate: Gate = field(default_factory=Gate)
    devices: Dict[str, DeviceEntry] = field(default_factory=dict)
    android: Android = field(default_factory=Android)
    ios: IOS = field(default_factory=IOS)
    report: Report = field(default_factory=Report)
    journeys: List[str] = field(default_factory=lambda: list(DEFAULT_JOURNEYS))
    path: str = DEFAULT_CONFIG_PATH
    catalogue: Dict[Tuple[str, str], Metric] = field(default_factory=dict)

    @property
    def root(self) -> str:
        """The Gradle root (the directory holding tracepulse.yml)."""
        return os.path.dirname(os.path.abspath(self.path))

    def alias_to_id(self, name: str) -> Optional[str]:
        """Resolve an alias or a device id to the registry key, or None."""
        if name in self.devices:
            return name
        for device_id, entry in self.devices.items():
            if entry.alias and entry.alias == name:
                return device_id
        return None


_SECTIONS = {
    "detection": Detection, "confirm": Confirm, "apk": APK, "ab": AB, "quality": Quality,
    "suite_spike": SuiteSpike, "collection": Collection, "history": History, "gate": Gate,
    "android": Android, "ios": IOS, "report": Report,
}
_FREE_MAPS = {("quality", "cov_max_overrides"), ("gate", "limits_pct"), ("gate", "limits_abs"),
              ("detection", "screening_gates")}


def _build(cls, raw: Mapping[str, Any], where: str):
    """Instantiate dataclass `cls` from `raw`, rejecting keys it has no field for."""
    if raw is None:
        raw = {}
    if not isinstance(raw, Mapping):
        raise ConfigError(f"{where} must be a mapping, got {type(raw).__name__}")
    allowed = {f.name for f in fields(cls)}
    unknown = sorted(set(raw) - allowed)
    if unknown:
        raise ConfigError(f"unknown key {where}.{unknown[0]} (known keys: {', '.join(sorted(allowed))})")
    kwargs = {}
    for f in fields(cls):
        if f.name not in raw:
            continue
        value = raw[f.name]
        if f.name == "screening_gates":
            value = _build(Screening, value, f"{where}.screening_gates")
        elif f.name == "overrides" and value is None:
            value = {}
        kwargs[f.name] = value
    return cls(**kwargs)


def _devices(raw: Any) -> Dict[str, DeviceEntry]:
    if raw is None:
        return {}
    if not isinstance(raw, Mapping):
        raise ConfigError("devices must be a mapping keyed by device id")
    out: Dict[str, DeviceEntry] = {}
    for device_id, entry in raw.items():
        out[str(device_id)] = _build(DeviceEntry, entry, f"devices.{device_id}")
    return out


def load_config(path: str) -> Config:
    """Load `path`; defaults for every missing section; `ConfigError` on any
    unknown key, invalid detection parameter or unknown metric override."""
    with open(path, "r") as fh:
        raw = yaml.safe_load(fh) or {}
    if not isinstance(raw, Mapping):
        raise ConfigError(f"{path}: top level must be a mapping")

    known_top = set(_SECTIONS) | {"devices", "journeys"}
    unknown = sorted(set(raw) - known_top)
    if unknown:
        raise ConfigError(f"unknown top-level key {unknown[0]!r} in {path} "
                          f"(known keys: {', '.join(sorted(known_top))})")

    sections = {name: _build(cls, raw.get(name), name) for name, cls in _SECTIONS.items()}
    detection: Detection = sections["detection"]
    if detection.width < 3:
        raise ConfigError(f"detection.width must be >= 3, got {detection.width}")
    expected_history = 2 * detection.width + 1
    if detection.min_history != expected_history:
        raise ConfigError(f"detection.min_history must equal 2*width+1 (expected {expected_history}), "
                          f"got {detection.min_history}")
    gate: Gate = sections["gate"]
    if gate.simulators not in ("inactive", "active"):
        raise ConfigError(f"gate.simulators must be 'inactive' or 'active', got {gate.simulators!r}")

    journeys = raw.get("journeys")
    if journeys is None:
        journeys = list(DEFAULT_JOURNEYS)
    if not isinstance(journeys, list) or not all(isinstance(j, str) for j in journeys):
        raise ConfigError("journeys must be a list of journey names")
    unknown_journeys = [j for j in journeys if j not in DEFAULT_JOURNEYS]
    if unknown_journeys:
        raise ConfigError(f"unknown journey {unknown_journeys[0]!r} (known: {', '.join(DEFAULT_JOURNEYS)})")

    try:
        catalogue = apply_overrides(
            limits_pct=gate.limits_pct, limits_abs=gate.limits_abs, disabled=gate.disabled,
            screening_pct=detection.screening_gates.min_delta_pct,
            screening_abs=detection.screening_gates.min_delta_abs,
        )
    except CatalogueError as exc:
        raise ConfigError(str(exc)) from exc

    return Config(
        devices=_devices(raw.get("devices")),
        journeys=journeys,
        path=os.path.abspath(path),
        catalogue=catalogue,
        **sections,
    )
