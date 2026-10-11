"""The SQLite store is a derived cache keyed by device: `rounds` carries the
round key, device identity, label, kind, reference flag, status and machine;
`gate_decisions` holds the gate; `Store.rebuild` recreates the file from records
deterministically; series are keyed (device_id, journey/metric) and never mix
devices or platforms."""

import os
import sqlite3

import pytest

from tracepulse.records import DeviceInfo, JourneyResult, MetricResult, RoundRecord
from tracepulse.store import Store, series_key, split_series_key


def _rec(device_id, platform, round_id, label, started, ttid, kind="device", model="Pixel 6 Pro",
         status="succeeded", gate=(), badges=(), size=1000):
    journeys = [] if status == "failed" else [
        JourneyResult(journey="startup", iterations=3, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=[ttid, ttid + 1, ttid + 2],
                                            source="macrobenchmark"),
        }),
        JourneyResult(journey="tuner", iterations=3, metrics={
            "frames.p90_ms": MetricResult(unit="ms", samples=[8.0, 9.0, 10.0], source="macrobenchmark"),
            "section.tuner.modeSwitch.sum_ms": MetricResult(unit="ms", samples=[1.0, 2.0, 3.0],
                                                            source="macrobenchmark"),
        }),
        JourneyResult(journey="metronome", iterations=3, metrics={
            "frames.p90_ms": MetricResult(unit="ms", samples=[20.0, 21.0, 22.0], source="macrobenchmark"),
        }),
        JourneyResult(journey="startup", iterations=1, metrics={
            "app.size_bytes": MetricResult(unit="bytes", samples=[], source="apk", value=float(size)),
        }),
    ]
    return RoundRecord(
        round_id=round_id,
        device=DeviceInfo(device_id=device_id, platform=platform, kind=kind, model=model,
                          reference=(kind == "device")),
        label=label,
        build={"commit": "c0ffee", "dirty": False, "injected": ["R1"] if label == "inject" else []},
        fingerprint={"os_version": "17"},
        machine={"hostname": "mbp", "tool_version": "0.2.0"},
        checks=[], status=status, journeys=journeys,
        started_at=started, finished_at=started,
        failure_reason="boom" if status == "failed" else None,
        badges=list(badges), gate=list(gate),
    )


@pytest.fixture
def records():
    return [
        _rec("PIXEL", "android", "20261008T100000Z_a", "a", "2026-10-08T10:00:00Z", 300.0),
        _rec("IPHONE", "ios", "20261008T100500Z_a", "a", "2026-10-08T10:05:00Z", 500.0, model="iPhone 14"),
        _rec("PIXEL", "android", "20261008T110000Z_b", "b", "2026-10-08T11:00:00Z", 310.0,
             gate=[{"metric": "startup/startup.ttid_ms", "state": "above_limit", "limit_pct": 1.0,
                    "observed_pct": 3.3, "baseline_rounds": ["20261008T100000Z_a"]}],
             badges=["noisy"]),
        _rec("SIM-1", "ios", "20261008T120000Z_b", "b", "2026-10-08T12:00:00Z", 900.0,
             kind="simulator", model="iPhone 17 (simulator)"),
        _rec("PIXEL", "android", "20261008T130000Z_b", "b", "2026-10-08T13:00:00Z", 0.0, status="failed"),
    ]


def test_series_key_helpers():
    assert series_key("tuner", "frames.p90_ms") == "tuner/frames.p90_ms"
    assert series_key(None, "app.size_bytes") == "app.size_bytes"
    assert split_series_key("tuner/frames.p90_ms") == ("tuner", "frames.p90_ms")
    assert split_series_key("startup.ttid_ms") == (None, "startup.ttid_ms")


def test_rounds_carry_device_label_kind_reference_status_and_machine(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, records) as store:
        rows = store.rounds()
        assert [r["round_key"] for r in rows] == [
            "20261008T100000Z_a", "20261008T100500Z_a", "20261008T110000Z_b",
            "20261008T120000Z_b", "20261008T130000Z_b"]
        pixel = rows[0]
        assert pixel["device_id"] == "PIXEL" and pixel["platform"] == "android"
        assert pixel["label"] == "a" and pixel["kind"] == "device" and pixel["reference"] == 1
        assert pixel["machine"] == "mbp" and pixel["status"] == "succeeded"
        assert pixel["model"] == "Pixel 6 Pro"
        sim = rows[3]
        assert sim["kind"] == "simulator" and sim["reference"] == 0
        failed = rows[4]
        assert failed["status"] == "failed" and "boom" in failed["notes"]
        assert store.rounds("PIXEL") and all(r["device_id"] == "PIXEL" for r in store.rounds("PIXEL"))
        assert [r["round_key"] for r in store.rounds("IPHONE")] == ["20261008T100500Z_a"]


def test_series_never_mix_devices_or_journeys(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, records) as store:
        pixel = store.series("startup/startup.ttid_ms", "PIXEL")
        assert [v for _, v in pixel] == [301.0, 311.0], "two succeeded Pixel rounds; the failed one has no samples"
        iphone = store.series("startup/startup.ttid_ms", "IPHONE")
        assert [v for _, v in iphone] == [501.0]
        assert store.series("startup/startup.ttid_ms", "SIM-1") == [(4, 901.0)]
        assert [v for _, v in store.series("tuner/frames.p90_ms", "PIXEL")] == [9.0, 9.0]
        assert [v for _, v in store.series("metronome/frames.p90_ms", "PIXEL")] == [21.0, 21.0]
        assert store.series("app.size_bytes", "PIXEL") == [(1, 1000.0), (3, 1000.0)]
        # the bare metric name is not a series: journeys are part of the key
        assert store.series("frames.p90_ms", "PIXEL") == []


def test_metrics_lists_series_keys_per_device(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, records) as store:
        assert store.metrics("PIXEL") == [
            "app.size_bytes", "metronome/frames.p90_ms", "startup/startup.ttid_ms",
            "tuner/frames.p90_ms", "tuner/section.tuner.modeSwitch.sum_ms",
        ]
        assert "startup/startup.ttid_ms" in store.metrics(None)


def test_samples_keep_the_journey_in_the_benchmark_column(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, records) as store:
        assert store.iterations("tuner/frames.p90_ms", [1]) == [8.0, 9.0, 10.0]
        rows = store._conn.execute(
            "SELECT benchmark FROM samples WHERE metric = ? AND round_id = 1",
            ("tuner/frames.p90_ms",)).fetchall()
        assert {r["benchmark"] for r in rows} == {"tuner"}


def test_gate_decisions_table_and_badges(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, records) as store:
        cols = [r["name"] for r in store._conn.execute("PRAGMA table_info(gate_decisions)")]
        assert cols == ["round_id", "metric", "state", "reason", "limit_pct", "observed_pct",
                        "baseline_rounds", "limit_abs", "observed_abs"]
        decisions = store.gate_decisions(3)
        assert len(decisions) == 1
        d = decisions[0]
        assert d["metric"] == "startup/startup.ttid_ms" and d["state"] == "above_limit"
        assert d["limit_pct"] == 1.0 and d["observed_pct"] == 3.3
        assert d["baseline_rounds"] == '["20261008T100000Z_a"]'
        assert store.gate_decisions(1) == []
        assert store.round_badges(3) == ["noisy"] and store.round_badges(1) == []
        assert store.injected(3) == [] and store.injected(1) == []


def test_devices_are_derived_from_the_rounds(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, records) as store:
        devices = store.devices()
        assert [(d["device_id"], d["platform"], d["kind"], d["model"], d["reference"]) for d in devices] == [
            ("PIXEL", "android", "device", "Pixel 6 Pro", 1),
            ("IPHONE", "ios", "device", "iPhone 14", 1),
            ("SIM-1", "ios", "simulator", "iPhone 17 (simulator)", 0),
        ]


def test_rebuild_is_deterministic_and_replaces_the_file(tmp_path, records):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, list(reversed(records))) as store:
        first = store.rounds()
        first_keys = [r["round_key"] for r in first]
    with Store.rebuild(db, records) as store:
        assert [r["round_key"] for r in store.rounds()] == first_keys
        assert store.rounds()[0]["id"] == 1, "a rebuild starts from an empty file, ids restart at 1"
    with Store.rebuild(db, records) as store:
        assert store._conn.execute("SELECT COUNT(*) AS n FROM rounds").fetchone()["n"] == 5
    assert not os.path.exists(db + "-wal") or os.path.getsize(db + "-wal") == 0


def test_rebuild_with_no_records_yields_an_empty_store(tmp_path):
    db = str(tmp_path / "tracepulse.db")
    with Store.rebuild(db, []) as store:
        assert store.rounds() == [] and store.devices() == []


def test_add_round_defaults_keep_the_old_single_device_call_shape(tmp_path):
    with Store(str(tmp_path / "t.db")) as store:
        r = store.add_round(ts=1, label="a", target_sha="s", target_dirty=False, injected=[],
                            device_serial="SERIAL", device_fingerprint="FP", quality_flags=[],
                            thermal_sleep_s=0.0, notes="")
        row = store.rounds()[0]
        assert r == 1 and row["device_id"] == "SERIAL" and row["platform"] == "android"
        assert row["kind"] == "device" and row["reference"] == 1 and row["status"] == "succeeded"
        assert store.rounds("SERIAL")[0]["id"] == 1 and store.rounds("OTHER") == []
        assert isinstance(row, sqlite3.Row)
