import json
import math
import sqlite3

import pytest

from tracepulse.store import Store


# --- fixtures ---------------------------------------------------------

@pytest.fixture
def db_path(tmp_path):
    return str(tmp_path / "tracepulse.db")


@pytest.fixture
def store(db_path):
    with Store(db_path) as s:
        yield s


# --- the one normative test (verbatim from task-5-brief.md) -----------

def test_ratchet_never_downgrades(store):
    r = store.add_round(ts=1000, label="r13", target_sha="abc", target_dirty=False,
                        injected=["R1"], device_serial="R58R61HQ7ZW",
                        device_fingerprint="A525FXXSCFYF1", quality_flags=[],
                        thermal_sleep_s=0, notes="")
    assert store.record_detection("startup.ttid_ms", r, "up", 1.2, -18.0, None,
                                  12.5, "SUSPECT", 0, False, None)
    assert store.record_detection("startup.ttid_ms", r, "up", 1.2, -18.0, 3e-6,
                                  12.5, "CONFIRMED", 0, False, True)
    assert not store.record_detection("startup.ttid_ms", r, "up", 1.2, -18.0, 0.2,
                                      12.5, "SUSPECT", 0, False, None)
    assert store.latest_status("startup.ttid_ms", r) == "CONFIRMED"


# --- schema: column names/order are the contract -----------------------

def test_rounds_table_columns_match_spec(store):
    cols = [row["name"] for row in store._conn.execute("PRAGMA table_info(rounds)")]
    assert cols == ["id", "ts", "label", "target_sha", "target_dirty", "injected_json",
                     "device_serial", "device_fingerprint", "quality_flags",
                     "thermal_sleep_s", "notes",
                     # feature 007: device-keyed history (trace-pulse columns above stay verbatim)
                     "round_key", "device_id", "platform", "kind", "model", "reference",
                     "status", "machine", "session_index", "session_size", "fingerprint_json"]


def test_detections_table_columns_match_spec(store):
    cols = [row["name"] for row in store._conn.execute("PRAGMA table_info(detections)")]
    assert cols == ["id", "computed_ts", "metric", "onset_round_id", "direction",
                     "step_size", "stepfit_score", "mwu_p", "delta_pct", "status",
                     "persistence_count", "triage_status", "env_suspect",
                     "ground_truth_match"]


def test_samples_and_aggregates_and_artifacts_columns_match_spec(store):
    assert [r["name"] for r in store._conn.execute("PRAGMA table_info(samples)")] == \
        ["round_id", "benchmark", "metric", "iteration", "value"]
    assert [r["name"] for r in store._conn.execute("PRAGMA table_info(aggregates)")] == \
        ["round_id", "metric", "n", "min", "max", "mean", "median",
         "p25", "p75", "p90", "p95", "p99", "stddev"]
    assert [r["name"] for r in store._conn.execute("PRAGMA table_info(artifacts)")] == \
        ["round_id", "kind", "path"]


# --- rounds round-trip ---------------------------------------------------

def test_add_round_round_trip_including_json_lists(store):
    r = store.add_round(ts=42, label="r1", target_sha="deadbeef", target_dirty=True,
                         injected=["R1", "R3"], device_serial="SERIAL1",
                         device_fingerprint="FP1", quality_flags=["NOISY", "THERMAL"],
                         thermal_sleep_s=2.5, notes="clean baseline")
    rows = store.rounds()
    assert len(rows) == 1
    row = rows[0]
    assert row["id"] == r
    assert row["ts"] == 42
    assert row["label"] == "r1"
    assert row["target_sha"] == "deadbeef"
    assert bool(row["target_dirty"]) is True
    assert json.loads(row["injected_json"]) == ["R1", "R3"]
    assert row["device_serial"] == "SERIAL1"
    assert row["device_fingerprint"] == "FP1"
    assert json.loads(row["quality_flags"]) == ["NOISY", "THERMAL"]
    assert row["thermal_sleep_s"] == 2.5
    assert row["notes"] == "clean baseline"


def test_add_round_empty_lists_round_trip(store):
    r = store.add_round(ts=1, label="r0", target_sha="abc", target_dirty=False,
                         injected=[], device_serial="S", device_fingerprint="F",
                         quality_flags=[], thermal_sleep_s=0, notes="")
    row = store.rounds()[0]
    assert json.loads(row["injected_json"]) == []
    assert json.loads(row["quality_flags"]) == []
    assert bool(row["target_dirty"]) is False


def test_add_round_ids_increment_in_insertion_order(store):
    r1 = store.add_round(ts=1, label="a", target_sha="s", target_dirty=False, injected=[],
                          device_serial="d", device_fingerprint="f", quality_flags=[],
                          thermal_sleep_s=0, notes="")
    r2 = store.add_round(ts=2, label="b", target_sha="s", target_dirty=False, injected=[],
                          device_serial="d", device_fingerprint="f", quality_flags=[],
                          thermal_sleep_s=0, notes="")
    r3 = store.add_round(ts=3, label="c", target_sha="s", target_dirty=False, injected=[],
                          device_serial="d", device_fingerprint="f", quality_flags=[],
                          thermal_sleep_s=0, notes="")
    assert r1 < r2 < r3
    assert [row["id"] for row in store.rounds()] == [r1, r2, r3]
    assert [row["label"] for row in store.rounds()] == ["a", "b", "c"]


# --- samples / iterations -------------------------------------------------

def _mkround(store, label="r"):
    return store.add_round(ts=1, label=label, target_sha="s", target_dirty=False,
                            injected=[], device_serial="d", device_fingerprint="f",
                            quality_flags=[], thermal_sleep_s=0, notes="")


def test_add_samples_round_trip_iteration_index_is_list_position(store):
    r = _mkround(store)
    store.add_samples(r, "startupCold", "startup.ttid_ms", [480.1, 501.3, 520.7])
    assert store.iterations("startup.ttid_ms", [r]) == [480.1, 501.3, 520.7]


def test_iterations_orders_by_round_insertion_then_iteration_index_not_arg_order(store):
    r1 = _mkround(store, "r1")
    store.add_samples(r1, "startupCold", "startup.ttid_ms", [10.0, 20.0, 30.0])
    r2 = _mkround(store, "r2")
    store.add_samples(r2, "startupCold", "startup.ttid_ms", [40.0, 50.0])
    # decoy metric — must never leak into the result below
    store.add_samples(r1, "startupCold", "startup.ttfd_ms", [999.0])

    # round_ids passed newest-first: ordering must still follow round *insertion* order
    result = store.iterations("startup.ttid_ms", [r2, r1])
    assert result == [10.0, 20.0, 30.0, 40.0, 50.0]


def test_iterations_empty_round_ids_returns_empty_list(store):
    assert store.iterations("startup.ttid_ms", []) == []


# --- aggregates / percentile convention -----------------------------------
# (the percentile function itself is collect.percentile — its unit tests live
# in test_collect.py; Store's use of it is pinned via add_aggregate below)


def test_add_aggregate_computes_all_stats(store):
    r = _mkround(store)
    values = [10.0, 20.0, 30.0, 40.0, 50.0]
    store.add_aggregate(r, "startup.ttid_ms", values)
    row = store._conn.execute(
        "SELECT * FROM aggregates WHERE round_id = ? AND metric = ?", (r, "startup.ttid_ms")
    ).fetchone()
    assert row["n"] == 5
    assert row["min"] == 10.0
    assert row["max"] == 50.0
    assert math.isclose(row["mean"], 30.0)
    assert math.isclose(row["median"], 30.0)
    assert math.isclose(row["p25"], 20.0)
    assert math.isclose(row["p75"], 40.0)
    assert math.isclose(row["p90"], 46.0)
    assert math.isclose(row["p95"], 48.0)
    assert math.isclose(row["p99"], 49.6)
    assert math.isclose(row["stddev"], math.sqrt(200.0))  # population sd, /N


def test_series_orders_by_round_insertion_order(store):
    r1 = _mkround(store, "r1")
    store.add_aggregate(r1, "startup.ttid_ms", [100.0, 100.0, 100.0])
    r2 = _mkround(store, "r2")
    store.add_aggregate(r2, "startup.ttid_ms", [115.0, 115.0, 115.0])
    r3 = _mkround(store, "r3")
    store.add_aggregate(r3, "startup.ttid_ms", [110.0, 110.0, 110.0])
    assert store.series("startup.ttid_ms") == [(r1, 100.0), (r2, 115.0), (r3, 110.0)]


def test_series_ignores_other_metrics(store):
    r1 = _mkround(store)
    store.add_aggregate(r1, "startup.ttid_ms", [1.0, 2.0, 3.0])
    store.add_aggregate(r1, "startup.ttfd_ms", [900.0, 901.0, 902.0])
    assert store.series("startup.ttid_ms") == [(r1, 2.0)]


# --- artifacts --------------------------------------------------------

def test_add_artifact_round_trip(store):
    r = _mkround(store)
    store.add_artifact(r, "trace", "data/rounds/1/scrollFeed.perfetto-trace")
    store.add_artifact(r, "json", "data/rounds/1/startupCold-benchmarkData.json")
    row = store._conn.execute(
        "SELECT * FROM artifacts WHERE round_id = ? AND kind = ?", (r, "trace")
    ).fetchone()
    assert row["path"] == "data/rounds/1/scrollFeed.perfetto-trace"
    all_rows = store._conn.execute("SELECT * FROM artifacts ORDER BY rowid").fetchall()
    assert len(all_rows) == 2


# --- artifacts() getter (Task 12 additive) ---------------------------------

def test_artifacts_round_trip_and_scoped_by_round(store):
    r1 = _mkround(store, "r1")
    r2 = _mkround(store, "r2")
    store.add_artifact(r1, "trace", "data/rounds/1/scrollFeed.perfetto-trace")
    store.add_artifact(r1, "slice_delta", "data/rounds/1/slice_delta.json")
    store.add_artifact(r2, "trace", "data/rounds/2/scrollFeed.perfetto-trace")

    r1_rows = store.artifacts(r1)
    assert [(row["kind"], row["path"]) for row in r1_rows] == [
        ("trace", "data/rounds/1/scrollFeed.perfetto-trace"),
        ("slice_delta", "data/rounds/1/slice_delta.json"),
    ]
    assert [row["kind"] for row in store.artifacts(r2)] == ["trace"]
    assert store.artifacts(999999) == []


# --- detections: ratchet semantics beyond the normative test --------------

def test_record_detection_first_event_always_recorded(store):
    r = _mkround(store)
    assert store.record_detection("frames.p90_ms", r, "up", 2.0, 20.0, 0.001,
                                   6.0, "PRELIMINARY", 0, False, None)
    assert store.latest_status("frames.p90_ms", r) == "PRELIMINARY"


def test_record_detection_preliminary_upgrades_to_suspect(store):
    r = _mkround(store)
    assert store.record_detection("frames.p90_ms", r, "up", 2.0, 20.0, None,
                                   6.0, "PRELIMINARY", 0, False, None)
    assert store.record_detection("frames.p90_ms", r, "up", 2.0, 20.0, 0.01,
                                   6.0, "SUSPECT", 0, False, None)
    assert store.latest_status("frames.p90_ms", r) == "SUSPECT"


def test_record_detection_same_rank_suppressed_across_different_statuses(store):
    # SUSPECT, IMPROVED and ENV_SUSPECT all share rank 1 — a lateral move must
    # be suppressed exactly like a same-status replay, and must not clobber
    # the row that is already there.
    r = _mkround(store)
    assert store.record_detection("frames.p90_ms", r, "up", 2.0, 20.0, 0.01,
                                   6.0, "SUSPECT", 0, False, None)
    assert not store.record_detection("frames.p90_ms", r, "up", 2.0, 20.0, 0.01,
                                       6.0, "ENV_SUSPECT", 0, True, None)
    assert not store.record_detection("frames.p90_ms", r, "up", 2.0, 20.0, 0.01,
                                       6.0, "IMPROVED", 0, False, None)
    assert store.latest_status("frames.p90_ms", r) == "SUSPECT"
    assert len(store.all_detections()) == 1


def test_record_detection_idempotent_replay_same_status_suppressed(store):
    # brief's explicit checklist case: "PRELIMINARY -> PRELIMINARY False —
    # idempotent replays don't grow the log" (re-running `tracepulse analyze`
    # over unchanged history must not duplicate events).
    r = _mkround(store)
    assert store.record_detection("startup.ttid_ms", r, "up", 1.0, 12.0, None,
                                   4.0, "PRELIMINARY", 0, False, None)
    assert not store.record_detection("startup.ttid_ms", r, "up", 1.0, 12.0, None,
                                       4.0, "PRELIMINARY", 0, False, None)
    assert store.latest_status("startup.ttid_ms", r) == "PRELIMINARY"
    assert len(store.all_detections()) == 1


def test_record_detection_distinguishes_by_metric_and_onset(store):
    r1 = _mkround(store, "r1")
    r2 = _mkround(store, "r2")
    store.record_detection("startup.ttid_ms", r1, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None)
    store.record_detection("startup.ttid_ms", r2, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None)
    store.record_detection("frames.p90_ms", r1, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None)
    assert len(store.all_detections()) == 3
    assert store.latest_status("startup.ttid_ms", r1) == "SUSPECT"
    assert store.latest_status("startup.ttid_ms", r2) == "SUSPECT"


def test_latest_status_none_when_no_detections(store):
    r = _mkround(store)
    assert store.latest_status("startup.ttid_ms", r) is None


def test_all_detections_is_insertion_order_and_excludes_suppressed(store):
    r = _mkround(store)
    store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None)
    store.record_detection("frames.p90_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                            "PRELIMINARY", 0, False, None)
    suppressed = store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                                         "SUSPECT", 0, False, None)
    store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 3e-6, 5.0,
                            "CONFIRMED", 0, False, True)
    assert suppressed is False
    events = store.all_detections()
    assert [(e["metric"], e["status"]) for e in events] == [
        ("startup.ttid_ms", "SUSPECT"),
        ("frames.p90_ms", "PRELIMINARY"),
        ("startup.ttid_ms", "CONFIRMED"),
    ]


def test_detections_triage_status_defaults_to_new(store):
    r = _mkround(store)
    store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None)
    row = store.all_detections()[0]
    assert row["triage_status"] == "NEW"


def test_record_detection_rejects_unknown_status(store):
    r = _mkround(store)
    with pytest.raises(ValueError):
        store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                                "BOGUS", 0, False, None)


# --- computed_ts: caller-supplied, never a wall-clock read in store.py ----
# NOTE: the brief's normative Interfaces line for record_detection omits
# computed_ts even though SPEC S8 lists it as a real detections column and
# the brief's prose separately requires "ts/computed_ts are caller-supplied
# ints (no wall-clock reads inside store.py)". Resolved as an *additive*
# trailing optional kwarg (default None -> stored NULL) so the normative
# 11-positional-arg call in test_ratchet_never_downgrades is unaffected and
# store.py still never touches the wall clock. See task-5-report.md.

def test_record_detection_computed_ts_is_caller_supplied_when_given(store):
    r = _mkround(store)
    store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None, computed_ts=7777)
    assert store.all_detections()[0]["computed_ts"] == 7777


def test_record_detection_computed_ts_defaults_to_null_not_wallclock(store):
    r = _mkround(store)
    store.record_detection("startup.ttid_ms", r, "up", 1.0, 20.0, 0.01, 5.0,
                            "SUSPECT", 0, False, None)
    assert store.all_detections()[0]["computed_ts"] is None


# --- storage-level guarantees requested by the brief -----------------------

def test_foreign_keys_are_enforced(store):
    with pytest.raises(sqlite3.IntegrityError):
        store.add_artifact(999999, "trace", "nope.perfetto-trace")


def test_wal_mode_is_enabled(store):
    mode = store._conn.execute("PRAGMA journal_mode").fetchone()[0]
    assert mode.lower() == "wal"


def test_context_manager_closes_connection_on_exit(db_path):
    with Store(db_path) as s:
        s.add_round(ts=1, label="a", target_sha="s", target_dirty=False, injected=[],
                    device_serial="d", device_fingerprint="f", quality_flags=[],
                    thermal_sleep_s=0, notes="")
    with pytest.raises(sqlite3.ProgrammingError):
        s._conn.execute("SELECT 1")
