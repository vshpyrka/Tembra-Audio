"""Perfetto trace extraction: top main-thread slices and before/after deltas.

Each call to `top_main_thread_slices` spawns exactly one `TraceProcessor`
against the given trace file and closes it before returning — TraceProcessor
spawn is ~1-2s, so v1 deliberately does not pool instances across calls (see
task-7 brief).
"""

from dataclasses import dataclass
from typing import List

from perfetto.trace_processor import TraceProcessor


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
