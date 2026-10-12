"""The audio-health series on both platforms (spec SC-010, FR-015): the six
counter tracks of a Perfetto trace, the six fields of engine-health.json, and the
p99 of the I/O-proc duration from the HAL trace's export."""

import os

from tracepulse.collect_ios import engine_health
from tracepulse.traces_android import AUDIO_COUNTER_KEYS, counter_series
from tracepulse.summary import percentile
from tracepulse.traces_ios import ioproc_p99, parse_ioproc_durations

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
SCRIPTED = {"audio.callbacks": 1200.0, "audio.last_callback_us": 850.0, "audio.max_callback_us": 1900.0,
            "audio.over_budget": 0.0, "audio.xruns": 0.0, "audio.realtime": 1.0}


def test_android_counter_tracks_yield_the_scripted_values():
    raw = counter_series(os.path.join(FIXTURES, "perfetto-audio-counters.perfetto-trace"), list(AUDIO_COUNTER_KEYS))
    series = {AUDIO_COUNTER_KEYS[name]: value for name, value in raw.items()}
    assert series == SCRIPTED


def test_ios_engine_health_yields_the_same_keys():
    assert engine_health(os.path.join(FIXTURES, "engine-health.json")) == SCRIPTED


def test_ioproc_durations_and_p99_from_the_spike_shaped_export():
    with open(os.path.join(FIXTURES, "audio-ioproc.xml")) as fh:
        xml = fh.read()
    durations = parse_ioproc_durations(xml)
    assert len(durations) == 20 and durations[0] == 1180.0 and durations[4] == 1900.0, "nanoseconds become microseconds"
    p99 = ioproc_p99(xml)
    assert 2000 < p99 <= 2100, "the slowest cycle dominates the 99th percentile"


def test_ioproc_p99_of_an_empty_table_is_none():
    assert ioproc_p99('<trace-query-result><node><schema name="audio-hal-client-io-cycle-ioproc"></schema></node></trace-query-result>') is None
    with open(os.path.join(FIXTURES, "audio-ioproc-empty.xml")) as fh:
        assert ioproc_p99(fh.read()) is None, "the phone's client-side table: a schema and no rows"


def test_server_side_io_cycle_rows_follow_shared_duration_cells():
    """`audio-hal-io-cycle-client` as the iPhone 14 exported it on 2026-10-12: the duration sits in column 1 and
    xctrace writes an equal value once, later rows pointing back to it by `ref`."""
    with open(os.path.join(FIXTURES, "audio-io-cycle-client.xml")) as fh:
        xml = fh.read()
    durations = parse_ioproc_durations(xml)
    assert len(durations) == 68, "every row counts, the one with a `ref` duration included"
    assert durations[0] == 73.625 and durations[1] == 1590.875, "nanoseconds become microseconds"
    assert durations[65] == durations[60] == 117.375, "the shared cell resolves to the value it points at"
    assert ioproc_p99(xml) == percentile(durations, 99)
