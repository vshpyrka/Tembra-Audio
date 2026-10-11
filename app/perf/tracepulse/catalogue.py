"""The metric catalogue (specs/007-performance-pipeline/contracts/metric-catalogue.md).

Keys are code: one `Metric` per (platform, key). `tracepulse.yml` may override a
gate limit or disable a key through `apply_overrides`; it can never add one.
All metrics are higher-is-worse. `section.<name>.sum_ms` / `.count` resolve by
pattern because the section names come from `app/shared/engine/perf-catalogue.txt`.
"""

from dataclasses import dataclass, replace
from typing import Dict, Iterable, List, Mapping, Optional, Tuple

PLATFORMS = ("android", "ios")
FAMILIES = ("startup", "frames", "memory", "cpu", "journey", "section", "app", "audio")
JOURNEYS_MEASURED = ("tuner", "metronome", "stress")


class CatalogueError(Exception):
    """An unknown key, platform or family, or an override that would add a key."""


@dataclass(frozen=True)
class Metric:
    key: str
    platform: str
    family: str
    source: str
    unit: str
    journeys: Tuple[str, ...]
    comparable: bool = False
    gate_limit_pct: Optional[float] = None
    gate_limit_abs: Optional[float] = None
    gate_floor_abs: Optional[float] = None  # a percentage limit flags only a change of at least this much
    gate_scale_pct: Optional[float] = None  # an absolute limit grows to this share of a large baseline
    screening_pct: Optional[float] = None
    screening_abs: Optional[float] = None
    single_valued: bool = False
    enabled: bool = True
    note: str = ""
    since: str = ""  # ISO instant from which the source applies; earlier rounds' values are not loaded

    @property
    def higher_is_worse(self) -> bool:
        return True


def _m(platform: str, key: str, family: str, source: str, unit: str, journeys, **kw) -> Metric:
    return Metric(key=key, platform=platform, family=family, source=source, unit=unit,
                  journeys=tuple(journeys), **kw)


_ANDROID: List[Metric] = [
    _m("android", "startup.ttid_ms", "startup", "macrobenchmark StartupTimingMetric.timeToInitialDisplayMs",
       "ms", ("startup",), gate_limit_pct=15, screening_pct=3),
    _m("android", "startup.ttfd_ms", "startup", "macrobenchmark StartupTimingMetric.timeToFullDisplayMs",
       "ms", ("startup",), gate_limit_pct=15, screening_pct=3,
       note="app-declared through ReportDrawnWhen on Android; OS-defined on iOS"),
    _m("android", "frames.p50_ms", "frames", "macrobenchmark FrameTimingMetric.frameDurationCpuMs P50",
       "ms", JOURNEYS_MEASURED, gate_limit_pct=25, screening_pct=3),
    _m("android", "frames.p90_ms", "frames", "macrobenchmark FrameTimingMetric.frameDurationCpuMs P90",
       "ms", JOURNEYS_MEASURED, gate_limit_pct=25, screening_pct=5),
    _m("android", "frames.p99_ms", "frames", "macrobenchmark FrameTimingMetric.frameDurationCpuMs P99",
       "ms", JOURNEYS_MEASURED, gate_limit_pct=25, screening_pct=8),
    _m("android", "frames.overrun_p90_ms", "frames", "macrobenchmark FrameTimingMetric.frameOverrunMs P90",
       "ms", JOURNEYS_MEASURED, gate_limit_abs=2.0, screening_abs=2.0, gate_scale_pct=25,
       note="per-frame percentiles on Android; hitch-based on iOS"),
    _m("android", "memory.peak_kb", "memory", "macrobenchmark MemoryUsageMetric(Max, RssAnon)",
       "kB", JOURNEYS_MEASURED, gate_limit_pct=15, screening_pct=3),
    _m("android", "memory.end_kb", "memory", "macrobenchmark MemoryUsageMetric(Last, RssAnon)",
       "kB", JOURNEYS_MEASURED, gate_limit_pct=15, screening_pct=5, since="2026-10-11T20:10:00Z",
       note="resident anonymous memory at the end of the journey on Android (heap size before 2026-10-11, "
            "which varied 4-16 % between rounds); physical-memory delta on iOS"),
    _m("android", "journey.duration_ms", "journey", "macrobenchmark TraceSectionMetric around the journey",
       "ms", JOURNEYS_MEASURED, gate_limit_pct=20, screening_pct=5),
    _m("android", "app.size_bytes", "app", "apk", "bytes", (),
       gate_limit_pct=2, single_valued=True, note="perf APK bytes on Android; thinned IPA variant on iOS"),
]

_IOS: List[Metric] = [
    _m("ios", "startup.ttid_ms", "startup", "xcresult XCTApplicationLaunchMetric(waitUntilResponsive: false)",
       "ms", ("startup",), gate_limit_pct=15, screening_pct=3),
    _m("ios", "startup.ttfd_ms", "startup", "xcresult XCTApplicationLaunchMetric(waitUntilResponsive: true)",
       "ms", ("startup",), gate_limit_pct=15, screening_pct=3,
       note="OS-defined responsiveness on iOS; app-declared on Android"),
    _m("ios", "frames.hitch_ratio_ms_per_s", "frames", "xcresult animation signpost hitch time ratio",
       "ms/s", JOURNEYS_MEASURED, gate_limit_abs=5, screening_abs=2,
       note="hitch-based on iOS; per-frame percentiles on Android"),
    _m("ios", "frames.hitch_count", "frames", "xcresult animation signpost hitch count",
       "count", JOURNEYS_MEASURED, gate_limit_pct=25, screening_pct=25),
    _m("ios", "frames.fps_deficit", "frames", "xcresult animation signpost frame rate, stored as max(0, 60 - fps)",
       "fps", JOURNEYS_MEASURED, gate_limit_abs=5, screening_abs=2),
    _m("ios", "memory.peak_kb", "memory", "xcresult XCTMemoryMetric Memory Peak Physical",
       "kB", JOURNEYS_MEASURED, gate_limit_pct=15, screening_pct=3),
    _m("ios", "memory.end_kb", "memory", "xcresult XCTMemoryMetric Memory Physical (delta)",
       "kB", JOURNEYS_MEASURED, gate_limit_pct=15, screening_pct=5,
       note="physical-memory delta on iOS; resident anonymous memory at the end of the journey on Android"),
    _m("ios", "cpu.time_ms", "cpu", "xcresult XCTCPUMetric CPU Time",
       "ms", JOURNEYS_MEASURED, gate_limit_pct=20, screening_pct=5),
    _m("ios", "cpu.instructions_k", "cpu", "xcresult XCTCPUMetric CPU Instructions Retired",
       "kI", JOURNEYS_MEASURED, gate_limit_pct=20, screening_pct=5),
    _m("ios", "journey.duration_ms", "journey", "xcresult XCTClockMetric",
       "ms", JOURNEYS_MEASURED, gate_limit_pct=20, screening_pct=5),
    _m("ios", "app.size_bytes", "app", "thinning-report compressed size of the iPhone14,7 variant",
       "bytes", (), gate_limit_pct=2, single_valued=True,
       note="thinned IPA variant on iOS; perf APK bytes on Android"),
    _m("ios", "app.size_uncompressed_bytes", "app", "thinning-report uncompressed size (advisory)",
       "bytes", (), gate_limit_pct=2, single_valued=True),
    _m("ios", "audio.ioproc_p99_us", "audio", "xctrace Audio System Trace audio-hal-client-io-cycle-ioproc p99 duration",
       "us", ("stress",), gate_limit_pct=50, screening_pct=10, note="advisory; iOS only"),
]

_AUDIO = (
    ("audio.callbacks", "count", dict(gate_limit_pct=50, screening_pct=10)),
    ("audio.last_callback_us", "us", dict(gate_limit_pct=50, screening_pct=10)),
    ("audio.max_callback_us", "us", dict(gate_limit_pct=50, screening_pct=10)),
    ("audio.over_budget", "count", dict(gate_limit_pct=50, screening_pct=10)),
    ("audio.xruns", "count", dict(gate_limit_pct=50, screening_pct=10)),
    ("audio.realtime", "flag", dict(gate_limit_abs=0.5, screening_abs=0.5)),
)
for _key, _unit, _gates in _AUDIO:
    _ANDROID.append(_m("android", _key, "audio", "perfetto counter track (last value)", _unit, ("stress",),
                       comparable=True, **_gates))
    _IOS.append(_m("ios", _key, "audio", "engine-health engine-health.json", _unit, ("stress",),
                   comparable=True, **_gates))

CATALOGUE: Dict[Tuple[str, str], Metric] = {
    (m.platform, m.key): m for m in _ANDROID + _IOS
}

_SECTION_SUFFIXES = {
    # a section of a few hundredths of a millisecond swings by tens of percent; under a millisecond is not a change
    "sum_ms": ("ms", dict(gate_limit_pct=20, screening_pct=5, gate_floor_abs=1.0)),
    "count": ("count", dict(gate_limit_pct=20, screening_pct=10)),
}
_SECTION_SOURCE = {
    "android": "macrobenchmark TraceSectionMetric(name, Mode.Sum)",
    "ios": "xcresult XCTOSSignpostMetric(subsystem: audio.tembra, name:)",
}


def _section_metric(platform: str, key: str) -> Optional[Metric]:
    parts = key.split(".")
    if len(parts) < 3 or parts[0] != "section":
        return None
    suffix = parts[-1]
    if suffix not in _SECTION_SUFFIXES:
        return None
    unit, gates = _SECTION_SUFFIXES[suffix]
    return _m(platform, key, "section", _SECTION_SOURCE[platform], unit, JOURNEYS_MEASURED,
              comparable=True, **gates)


def lookup(platform: str, key: str,
           table: Optional[Mapping[Tuple[str, str], Metric]] = None) -> Metric:
    """The `Metric` for (platform, key); section keys resolve by pattern."""
    if platform not in PLATFORMS:
        raise CatalogueError(f"unknown platform {platform!r} (expected one of {PLATFORMS})")
    table = CATALOGUE if table is None else table
    metric = table.get((platform, key))
    if metric is not None:
        return metric
    section = _section_metric(platform, key)
    if section is not None:
        return section
    raise CatalogueError(f"metric {key!r} is not in the catalogue for {platform}")


def keys_for(platform: str) -> List[str]:
    """Concrete (non-section) keys of one platform, in catalogue order."""
    if platform not in PLATFORMS:
        raise CatalogueError(f"unknown platform {platform!r} (expected one of {PLATFORMS})")
    return [key for (p, key) in CATALOGUE if p == platform]


def metrics_for(platform: str,
                table: Optional[Mapping[Tuple[str, str], Metric]] = None) -> List[Metric]:
    """Enabled concrete metrics of one platform from `table` (default: the base catalogue)."""
    table = CATALOGUE if table is None else table
    return [m for (p, _), m in table.items() if p == platform and m.enabled]


def apply_overrides(limits_pct: Optional[Mapping[str, float]] = None,
                    limits_abs: Optional[Mapping[str, float]] = None,
                    disabled: Iterable[str] = (),
                    screening_pct: Optional[Mapping[str, float]] = None,
                    screening_abs: Optional[Mapping[str, float]] = None,
                    ) -> Dict[Tuple[str, str], Metric]:
    """A copy of the catalogue with per-key overrides applied on every platform
    that has the key. A key no platform has raises `CatalogueError`: the
    configuration may not add metrics."""
    table = dict(CATALOGUE)
    known = {key for (_, key) in CATALOGUE}

    def targets(key: str) -> List[Tuple[str, str]]:
        if key not in known:
            raise CatalogueError(f"metric {key!r} is not in the catalogue; tracepulse.yml cannot add keys")
        return [(p, k) for (p, k) in table if k == key]

    for key, pct in (limits_pct or {}).items():
        for pk in targets(key):
            table[pk] = replace(table[pk], gate_limit_pct=float(pct), gate_limit_abs=None)
    for key, value in (limits_abs or {}).items():
        for pk in targets(key):
            table[pk] = replace(table[pk], gate_limit_abs=float(value), gate_limit_pct=None)
    for key, pct in (screening_pct or {}).items():
        for pk in targets(key):
            table[pk] = replace(table[pk], screening_pct=float(pct), screening_abs=None)
    for key, value in (screening_abs or {}).items():
        for pk in targets(key):
            table[pk] = replace(table[pk], screening_abs=float(value), screening_pct=None)
    for key in disabled:
        for pk in targets(key):
            table[pk] = replace(table[pk], enabled=False)
    return table
