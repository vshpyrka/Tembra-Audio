"""The audio-health series on both platforms (spec SC-010, FR-015): the counter
tracks of a Perfetto trace (six in the fixture, nine since feature 009), the nine fields
of engine-health.json, and the p99 of the I/O-proc duration from the HAL trace's export."""

import os

from tracepulse.collect_ios import engine_health
from tracepulse.traces_android import AUDIO_COUNTER_KEYS, counter_series
from tracepulse.summary import percentile
from tracepulse.traces_ios import ioproc_p99, parse_ioproc_durations

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
SCRIPTED = {"audio.callbacks": 1200.0, "audio.last_callback_us": 850.0, "audio.max_callback_us": 1900.0,
            "audio.over_budget": 0.0, "audio.xruns": 0.0, "audio.realtime": 1.0}
# Feature 009 appended the transport's three counters (ABI revision 6).
SCRIPTED_V6 = dict(SCRIPTED, **{"audio.avg_us": 410.0, "audio.starvation": 0.0, "audio.clips_active": 5.0})


def test_android_counter_tracks_yield_the_scripted_values():
    raw = counter_series(os.path.join(FIXTURES, "perfetto-audio-counters.perfetto-trace"), list(AUDIO_COUNTER_KEYS))
    series = {AUDIO_COUNTER_KEYS[name]: value for name, value in raw.items()}
    # The fixture trace predates ABI revision 6: it carries the six original tracks and none of the three new ones.
    assert series == SCRIPTED
    assert set(AUDIO_COUNTER_KEYS.values()) == set(SCRIPTED_V6)


def test_ios_engine_health_yields_the_nine_keys():
    assert engine_health(os.path.join(FIXTURES, "engine-health.json")) == SCRIPTED_V6


def test_ios_engine_health_predating_abi_6_reads_zeros_for_the_new_fields(tmp_path):
    legacy = tmp_path / "engine-health.json"
    legacy.write_text('{"schema":1,"journey":"stress","stream_open":true,"health":{"callbacks":1200,"last_us":850,"max_us":1900,"over_budget":0,"xruns":0,"realtime":1}}')
    assert engine_health(str(legacy)) == dict(SCRIPTED, **{"audio.avg_us": 0.0, "audio.starvation": 0.0, "audio.clips_active": 0.0})


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
