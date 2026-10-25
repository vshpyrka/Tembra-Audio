"""Perfetto trace extraction (Android): top main-thread slices for the report's
drill-down, before/after deltas, and the counter tracks that carry the engine's
audio-health values (feature 007, US5).

Each call spawns exactly one `TraceProcessor` against the given trace file and
closes it before returning (spawn is ~1-2 s; no pooling across calls).
"""

from dataclasses import dataclass
from typing import Dict, List, Sequence

from perfetto.trace_processor import TraceProcessor

# Signpost (counter-track) name -> catalogue key; the names are the PerfCounter
# entries of app/shared/engine/perf-catalogue.txt.
AUDIO_COUNTER_KEYS: Dict[str, str] = {
    "audio.callbacks": "audio.callbacks",
    "audio.lastUs": "audio.last_callback_us",
    "audio.maxUs": "audio.max_callback_us",
    "audio.overBudget": "audio.over_budget",
    "audio.xruns": "audio.xruns",
    "audio.realtime": "audio.realtime",
    "audio.avgUs": "audio.avg_us",
    "audio.starvation": "audio.starvation",
    "audio.clipsActive": "audio.clips_active",
}


@dataclass(frozen=True)
class SliceStat:
    """Aggregate stats for one slice name on the main thread."""
    name: str
    total_dur_ms: float
    count: int


@dataclass(frozen=True)
class SliceDelta:
    """Before/after comparison of a slice name's total duration."""
    name: str
    before_ms: float
    after_ms: float
    delta_ms: float


def _escape_glob(glob: str) -> str:
    """Escape a string for embedding as a single-quoted SQL literal.

    The TraceProcessor python API's `query()` has no bind-parameter support
    (see api.py), so `process_glob` is formatted directly into the SQL text.
    Doubling embedded single quotes is the standard SQL escape and prevents a
    quote in the glob from terminating the literal early.
    """
    return glob.replace("'", "''")


def top_main_thread_slices(trace_path: str, process_glob: str, top_n: int) -> List[SliceStat]:
    """Top-N main-thread slices by total duration, for processes matching `process_glob`.

    Opens a fresh TraceProcessor over `trace_path`, runs the perfetto-sql
    convention join (slice -> thread_track -> thread -> process), restricted
    to the main thread (`is_main_thread = 1`) and finished slices (`dur > 0`,
    which excludes the `dur = -1` sentinel for unterminated slices), grouped
    by slice name and ordered by total duration descending.

    Args:
        trace_path: path to a .perfetto-trace file.
        process_glob: SQL GLOB pattern matched against `process.name`.
        top_n: max number of rows to return.

    Returns:
        SliceStat list, sorted by total_dur_ms descending, length <= top_n.
    """
    query = f"""
    SELECT s.name AS name, SUM(s.dur) AS total_dur, COUNT(*) AS cnt
    FROM slice s
    JOIN thread_track tt ON s.track_id = tt.id
    JOIN thread t USING(utid)
    JOIN process p USING(upid)
    WHERE t.is_main_thread = 1 AND p.name GLOB '{_escape_glob(process_glob)}' AND s.dur > 0
    GROUP BY s.name ORDER BY total_dur DESC LIMIT {int(top_n)}
    """
    with TraceProcessor(trace=trace_path) as tp:
        return [
            SliceStat(name=row.name, total_dur_ms=row.total_dur / 1e6, count=row.cnt)
            for row in tp.query(query)
        ]


def slice_deltas(before: List[SliceStat], after: List[SliceStat], top_n: int) -> List[SliceDelta]:
    """Compare before/after slice stats, sorted by regression (delta_ms descending).

    Takes the union of slice names across both sides; a name missing from
    one side contributes 0.0 for that side's ms value.

    Args:
        before: SliceStat list from the "before" trace.
        after: SliceStat list from the "after" trace.
        top_n: max number of rows to return.

    Returns:
        SliceDelta list sorted by delta_ms descending (ties broken by name
        ascending, so ordering is stable across process invocations even
        though `names` below is built from a set — str hashes, and thus set
        iteration order, are randomized per-process by default), truncated
        to top_n.
    """
    before_ms = {s.name: s.total_dur_ms for s in before}
    after_ms = {s.name: s.total_dur_ms for s in after}
    names = set(before_ms) | set(after_ms)

    deltas = []
    for name in names:
        b = before_ms.get(name, 0.0)
        a = after_ms.get(name, 0.0)
        deltas.append(SliceDelta(name=name, before_ms=b, after_ms=a, delta_ms=a - b))

    deltas.sort(key=lambda d: (-d.delta_ms, d.name))
    return deltas[:top_n]


# The engine restarts its health counters with every stream session, and a journey that hands the audio device over
# (feature 009's stress: the transport plays, then the metronome and the tuner take the device) ends on another
# session. The cumulative and peak counters therefore keep their maximum over the trace, the heaviest session's
# numbers; the two that describe a moment (the last callback, the real-time flag) keep their last value.
_LAST_VALUE_COUNTERS = frozenset({"audio.lastUs", "audio.realtime"})


def counter_series(trace_path: str, names: Sequence[str]) -> Dict[str, float]:
    """The value of every counter track in `names` present in the trace (`counter`
    joined to `counter_track` by name): the maximum over the trace, except the
    last value for `_LAST_VALUE_COUNTERS`. Absent tracks are absent from the
    result: a round without audio counters yields no audio series."""
    if not names:
        return {}
    quoted = ", ".join("'" + _escape_glob(n) + "'" for n in names)
    query = f"""
    SELECT t.name AS name, c.value AS value, c.ts AS ts
    FROM counter c JOIN counter_track t ON c.track_id = t.id
    WHERE t.name IN ({quoted})
    ORDER BY c.ts ASC
    """
    series: Dict[str, float] = {}
    with TraceProcessor(trace=trace_path) as tp:
        for row in tp.query(query):
            value = float(row.value)
            series[row.name] = value if row.name in _LAST_VALUE_COUNTERS else max(series.get(row.name, value), value)
    return series
