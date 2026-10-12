import importlib.util
import math
import os

import pytest

from tracepulse.traces_android import (
    AUDIO_COUNTER_KEYS, SliceStat, SliceDelta, counter_series, top_main_thread_slices, slice_deltas,
)


FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures")
FIXTURE = os.path.join(FIXTURES_DIR, "fixture.perfetto-trace")
PROCESS_GLOB = "audio.tembra.app*"
COUNTERS_FIXTURE = os.path.join(FIXTURES_DIR, "perfetto-audio-counters.perfetto-trace")


def _load_gen_trace():
    """Load tests/fixtures/gen_trace.py by path (it's a fixture script, not a package)."""
    path = os.path.join(FIXTURES_DIR, "gen_trace.py")
    spec = importlib.util.spec_from_file_location("gen_trace", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# --- top_main_thread_slices: one TraceProcessor spawn shared across assertions ---

@pytest.fixture(scope="module")
def stats():
    return top_main_thread_slices(FIXTURE, PROCESS_GLOB, 10)


def test_top_main_thread_slices(stats):
    byname = {s.name: s for s in stats}
    assert byname["Choreographer#doFrame"].count == 3
    assert math.isclose(byname["Choreographer#doFrame"].total_dur_ms, 21.0, rel_tol=1e-6)
    assert stats[0].name == "Choreographer#doFrame"          # sorted by total desc


def test_top_main_thread_slices_includes_inflate(stats):
    byname = {s.name: s for s in stats}
    assert byname["inflate"].count == 1
    assert math.isclose(byname["inflate"].total_dur_ms, 2.0, rel_tol=1e-6)
    assert len(stats) == 2  # only two distinct slice names in the fixture


def test_top_main_thread_slices_no_match_returns_empty():
    assert top_main_thread_slices(FIXTURE, "com.example.doesnotexist*", 10) == []


def test_top_main_thread_slices_escapes_single_quote_in_glob():
    # A glob containing a single quote must not break the inlined SQL string
    # literal (no bind params in the TraceProcessor python API - see traces.py).
    assert top_main_thread_slices(FIXTURE, "no such process'; --*", 10) == []


# --- slice_deltas: pure function, no TraceProcessor needed ---------------------

def test_slice_deltas_orders_by_regression():
    before = [SliceStat("a", 10.0, 3), SliceStat("b", 5.0, 1)]
    after = [SliceStat("a", 30.0, 3), SliceStat("c", 4.0, 2)]
    d = slice_deltas(before, after, 5)
    assert isinstance(d[0], SliceDelta)
    assert d[0].name == "a" and math.isclose(d[0].delta_ms, 20.0)
    assert any(x.name == "c" and x.before_ms == 0.0 for x in d)


def test_slice_deltas_truncates_to_top_n():
    before = [SliceStat("a", 10.0, 1)]
    after = [SliceStat("a", 10.0, 1), SliceStat("b", 50.0, 1), SliceStat("c", 5.0, 1)]
    d = slice_deltas(before, after, 2)
    assert [x.name for x in d] == ["b", "c"]  # delta_ms desc: 50, 5, 0 -> top 2


def test_slice_deltas_tie_break_is_deterministic_by_name():
    # Three names with an identical delta_ms: the union `before`/`after` names are
    # combined via a set, whose iteration order is randomized per-process (str hash
    # randomization) - the sort key must break ties by name so the result is the
    # same regardless of that iteration order (see fix in traces.py: sort key is
    # (-delta_ms, name), not delta_ms alone).
    before = [SliceStat("zeta", 0.0, 1), SliceStat("alpha", 0.0, 1), SliceStat("mid", 0.0, 1)]
    after = [SliceStat("zeta", 10.0, 1), SliceStat("alpha", 10.0, 1), SliceStat("mid", 10.0, 1)]
    d = slice_deltas(before, after, 5)
    assert [x.name for x in d] == ["alpha", "mid", "zeta"]


# --- fixture determinism ---------------------------------------------------

def test_gen_trace_is_deterministic(tmp_path):
    gen_trace = _load_gen_trace()
    out1 = tmp_path / "one.perfetto-trace"
    out2 = tmp_path / "two.perfetto-trace"
    gen_trace.generate(str(out1))
    gen_trace.generate(str(out2))

    bytes1 = out1.read_bytes()
    bytes2 = out2.read_bytes()
    assert bytes1 == bytes2

    with open(FIXTURE, "rb") as f:
        committed = f.read()
    assert bytes1 == committed


# --- counter tracks: the audio-health series (feature 007, US5) -----------------

def test_counter_series_reads_the_last_value_of_every_audio_counter():
    series = counter_series(COUNTERS_FIXTURE, list(AUDIO_COUNTER_KEYS))
    assert series == {
        "audio.callbacks": 1200.0, "audio.lastUs": 850.0, "audio.maxUs": 1900.0,
        "audio.overBudget": 0.0, "audio.xruns": 0.0, "audio.realtime": 1.0,
    }


def test_counter_series_is_empty_without_counter_tracks():
    assert counter_series(FIXTURE, list(AUDIO_COUNTER_KEYS)) == {}


def test_audio_counter_keys_map_signpost_names_to_catalogue_keys():
    assert AUDIO_COUNTER_KEYS == {
        "audio.callbacks": "audio.callbacks", "audio.lastUs": "audio.last_callback_us",
        "audio.maxUs": "audio.max_callback_us", "audio.overBudget": "audio.over_budget",
        "audio.xruns": "audio.xruns", "audio.realtime": "audio.realtime",
    }


def test_counters_fixture_is_deterministic():
    gen = _load_gen_trace()
    with open(COUNTERS_FIXTURE, "rb") as fh:
        assert fh.read() == gen.build_trace(with_counters=True)
