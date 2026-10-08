"""Detections exposed per device for the report carry the label of their onset
round (`label_at_onset`), the onset round key, and the fingerprint changes
along the series; gate decisions ride along per round."""

from tracepulse.analysis import device_overview, fingerprint_changes
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.records import DeviceInfo, JourneyResult, MetricResult, RoundRecord
from tracepulse.store import Store


def _rec(i, ttid, label, os_version="17", device_id="PIXEL", platform="android"):
    return RoundRecord(
        round_id=f"20261008T{i:02d}0000Z_{label}",
        device=DeviceInfo(device_id=device_id, platform=platform, kind="device", model="Pixel 6 Pro", reference=True),
        label=label, build={"commit": f"c{i}", "dirty": False}, fingerprint={"os_version": os_version},
        machine={"hostname": "h", "tool_version": "0.2.0"}, checks=[], status="succeeded",
        journeys=[JourneyResult(journey="startup", iterations=20, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=[ttid + (k % 5) * 0.5 for k in range(20)],
                                            source="macrobenchmark")})],
        started_at=f"2026-10-08T{i:02d}:00:00Z", finished_at=f"2026-10-08T{i:02d}:00:00Z",
    )


def test_overview_carries_label_at_onset_and_round_keys(tmp_path):
    cfg = load_config(DEFAULT_CONFIG_PATH)
    # 8 rounds under "006", a step of +30 % at round 9 under "007", 6 more rounds
    records = [_rec(i, 300.0, "006-noise-gate") for i in range(8)] + \
              [_rec(i, 390.0, "007-performance-pipeline") for i in range(8, 15)]
    with Store.rebuild(str(tmp_path / "db"), records) as store:
        from tracepulse.analysis import analyze_all
        analyze_all(store, cfg)
        overview = device_overview(store, cfg, "PIXEL")
    detections = overview["detections"]
    assert detections, "the step is detected"
    onset = next(d for d in detections if d["metric"] == "startup/startup.ttid_ms" and d["status"] in ("CONFIRMED", "SUSPECT"))
    assert onset["label_at_onset"] == "007-performance-pipeline"
    assert onset["onset_round_key"] == "20261008T080000Z_007-performance-pipeline"
    assert onset["delta_pct"] > 25
    assert overview["labels"] == ["006-noise-gate", "007-performance-pipeline"]
    boundaries = overview["label_boundaries"]
    assert boundaries == [{"round_index": 8, "round_key": "20261008T080000Z_007-performance-pipeline",
                           "label": "007-performance-pipeline"}]


def test_fingerprint_changes_are_marked_on_the_series(tmp_path):
    records = [_rec(i, 300.0, "a", os_version="16" if i < 3 else "17") for i in range(6)]
    with Store.rebuild(str(tmp_path / "db"), records) as store:
        changes = fingerprint_changes(store, "PIXEL")
    assert changes == [{"round_index": 3, "round_key": "20261008T030000Z_a", "field": "os_version",
                        "before": "16", "after": "17"}]
