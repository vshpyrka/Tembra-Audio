"""The informational gate (spec FR-011, data-model.md): per metric, the change of a
round's median against the median of the last N clean rounds of the same series;
inactive with a reason below N or for simulator series; never flags
improvements; a gate flag does not make a round unclean."""

import pytest

from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.devices import Device
from tracepulse.gate import clean_round_ids, decide, decide_round, record_all_gates
from tracepulse.records import DeviceInfo, JourneyResult, MetricResult, RoundRecord
from tracepulse.store import Store

PIXEL = Device(device_id="PIXEL", platform="android", kind="device", model="Pixel 6 Pro")
SIM = Device(device_id="SIM", platform="ios", kind="simulator", model="iPhone 17 (simulator)", reference=False)


def _rec(device, round_id, ttid, started, status="succeeded", badges=(), label="a", size=1000.0):
    journeys = [] if status == "failed" else [
        JourneyResult(journey="startup", iterations=20, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=[ttid + i * 0.1 for i in range(20)], source="macrobenchmark"),
            "app.size_bytes": MetricResult(unit="bytes", samples=[], source="apk", value=size),
        }),
        JourneyResult(journey="tuner", iterations=20, metrics={
            "memory.peak_kb": MetricResult(unit="kB", samples=[100000.0 + i for i in range(20)], source="macrobenchmark"),
        }),
    ]
    return RoundRecord(
        round_id=round_id,
        device=DeviceInfo(device_id=device.device_id, platform=device.platform, kind=device.kind, model=device.model,
                          reference=device.reference),
        label=label, build={"commit": "c", "dirty": False}, fingerprint={"os_version": "17"},
        machine={"hostname": "h", "tool_version": "0.2.0"}, checks=[], status=status, journeys=journeys,
        started_at=started, finished_at=started, badges=list(badges),
        failure_reason="boom" if status == "failed" else None,
    )


def _history(device, medians, badges_at=(), failed_at=(), label="a"):
    recs = []
    for i, m in enumerate(medians):
        recs.append(_rec(device, f"20261008T{i:02d}0000Z_{label}", m, f"2026-10-08T{i:02d}:00:00Z",
                         status="failed" if i in failed_at else "succeeded",
                         badges=["noisy"] if i in badges_at else (), label=label))
    return recs


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


def test_inactive_below_the_clean_round_count(cfg, tmp_path):
    with Store.rebuild(str(tmp_path / "db"), _history(PIXEL, [300, 301, 302, 303])) as store:
        new = _rec(PIXEL, "20261008T120000Z_b", 330, "2026-10-08T12:00:00Z", label="b")
        decisions = decide_round(store, cfg, PIXEL, new)
    ttid = next(d for d in decisions if d["metric"] == "startup/startup.ttid_ms")
    assert ttid["state"] == "inactive" and ttid["reason"] == "fewer than 5 clean rounds (4)"
    assert ttid["limit_pct"] == 15


def test_within_limit_and_above_limit_with_baseline_rounds(cfg, tmp_path):
    history = _history(PIXEL, [300, 302, 301, 303, 299, 300])
    with Store.rebuild(str(tmp_path / "db"), history) as store:
        small = _rec(PIXEL, "20261008T120000Z_b", 310, "2026-10-08T12:00:00Z", label="b")
        decisions = {d["metric"]: d for d in decide_round(store, cfg, PIXEL, small)}
        ttid = decisions["startup/startup.ttid_ms"]
        # medians are ttid + 0.95; the baseline is the median of the last five clean medians (301.95)
        assert ttid["state"] == "within_limit" and ttid["observed_pct"] == pytest.approx((310.95 - 301.95) / 301.95 * 100, abs=0.01)
        assert ttid["baseline_rounds"] == [r.round_id for r in history[-5:]], "the last 5 clean rounds"
        big = _rec(PIXEL, "20261008T130000Z_b", 360, "2026-10-08T13:00:00Z", label="b")
        decisions = {d["metric"]: d for d in decide_round(store, cfg, PIXEL, big)}
        ttid = decisions["startup/startup.ttid_ms"]
        assert ttid["state"] == "above_limit" and ttid["limit_pct"] == 15
        assert ttid["observed_pct"] == pytest.approx((360.95 - 301.95) / 301.95 * 100, abs=0.01)
        assert len(ttid["baseline_rounds"]) == 5
        # memory is unchanged, app size identical
        assert decisions["tuner/memory.peak_kb"]["state"] == "within_limit"
        assert decisions["app.size_bytes"]["state"] == "within_limit"


def test_improvements_are_never_flagged(cfg, tmp_path):
    with Store.rebuild(str(tmp_path / "db"), _history(PIXEL, [300] * 6)) as store:
        faster = _rec(PIXEL, "20261008T120000Z_b", 200, "2026-10-08T12:00:00Z", label="b")
        ttid = next(d for d in decide_round(store, cfg, PIXEL, faster) if d["metric"] == "startup/startup.ttid_ms")
    assert ttid["state"] == "within_limit" and ttid["observed_pct"] < 0


def test_clean_rounds_exclude_failed_noisy_thermal_and_env_suspect(cfg, tmp_path):
    history = _history(PIXEL, [300] * 8, badges_at=(2,), failed_at=(5,))
    with Store.rebuild(str(tmp_path / "db"), history) as store:
        clean = clean_round_ids(store, cfg, "PIXEL")
        keys = {r["id"]: r["round_key"] for r in store.rounds("PIXEL")}
        names = [keys[i] for i in clean]
        assert history[2].round_id not in names and history[5].round_id not in names
        assert len(names) == 6
        # a gate flag on a round does not make it unclean
        store.add_gate_decision(store.rounds("PIXEL")[0]["id"], "startup/startup.ttid_ms", "above_limit", "", 15, 20, [])
        assert len(clean_round_ids(store, cfg, "PIXEL")) == 6
        # an environment-suspect onset makes that round unclean
        store.record_detection("startup/startup.ttid_ms", store.rounds("PIXEL")[7]["id"], "up", 1, 1, None, 5,
                               "ENV_SUSPECT", 0, True, None)
        assert len(clean_round_ids(store, cfg, "PIXEL")) == 5


def test_simulator_series_are_inactive_unless_configured_active(cfg, tmp_path):
    with Store.rebuild(str(tmp_path / "db"), _history(SIM, [300] * 6)) as store:
        new = _rec(SIM, "20261008T120000Z_b", 400, "2026-10-08T12:00:00Z", label="b")
        ttid = next(d for d in decide_round(store, cfg, SIM, new) if d["metric"] == "startup/startup.ttid_ms")
        assert ttid["state"] == "inactive" and ttid["reason"] == "simulator series"
        cfg.gate.simulators = "active"
        ttid = next(d for d in decide_round(store, cfg, SIM, new) if d["metric"] == "startup/startup.ttid_ms")
        assert ttid["state"] == "above_limit"


def test_absolute_limits_and_disabled_metrics(cfg, tmp_path):
    cfg.gate.disabled = ["memory.peak_kb"]
    from tracepulse.catalogue import apply_overrides
    cfg.catalogue = apply_overrides(limits_pct=cfg.gate.limits_pct, disabled=cfg.gate.disabled)
    with Store.rebuild(str(tmp_path / "db"), _history(PIXEL, [300] * 6)) as store:
        new = _rec(PIXEL, "20261008T120000Z_b", 300, "2026-10-08T12:00:00Z", label="b")
        decisions = {d["metric"]: d for d in decide_round(store, cfg, PIXEL, new)}
    assert decisions["tuner/memory.peak_kb"]["state"] == "inactive" and decisions["tuner/memory.peak_kb"]["reason"] == "disabled"
    # an absolute-limit metric reports the observed absolute change
    assert decide([1.0, 1.0, 1.0, 1.0, 1.0], 4.0, limit_pct=None, limit_abs=2.0, ids=["a"] * 5)["state"] == "above_limit"
    assert decide([1.0] * 5, 2.5, limit_pct=None, limit_abs=2.0, ids=["a"] * 5)["state"] == "within_limit"


def test_decide_against_a_negative_baseline_keeps_the_sign_of_the_change():
    # frame overrun P90 sits below zero on a quiet device; a rise to +2 ms is a
    # +2.5 ms change over the 2 ms limit, reported as a positive percentage
    d = decide([-0.5] * 5, 2.04, limit_pct=None, limit_abs=2.0, ids=["a"] * 5)
    assert d["state"] == "above_limit" and d["limit_abs"] == 2.0
    assert d["observed_abs"] == pytest.approx(2.54) and d["observed_pct"] == pytest.approx(508.0)
    assert "limit_pct" not in d


def test_a_percentage_limit_with_a_floor_ignores_changes_under_the_floor():
    # a 0.03 ms section swings by tens of percent; sections flag only a change of at least 1 ms
    tiny = decide([0.03] * 5, 0.037, limit_pct=20, limit_abs=None, ids=["a"] * 5, floor_abs=1.0)
    assert tiny["state"] == "within_limit" and tiny["observed_pct"] == pytest.approx(23.33, abs=0.01)
    real = decide([70.0] * 5, 90.0, limit_pct=20, limit_abs=None, ids=["a"] * 5, floor_abs=1.0)
    assert real["state"] == "above_limit"


def test_an_absolute_limit_scales_with_a_large_baseline():
    # frame overrun P90 is ~51 ms in the tuner journey (the mode-switch frame) with ±5 ms of noise:
    # the 2 ms limit grows to 25 % of the baseline there and stays 2 ms on a quiet series
    noisy = decide([51.0] * 5, 56.0, limit_pct=None, limit_abs=2.0, ids=["a"] * 5, scale_pct=25)
    assert noisy["state"] == "within_limit" and noisy["limit_abs"] == pytest.approx(12.75)
    worse = decide([51.0] * 5, 64.0, limit_pct=None, limit_abs=2.0, ids=["a"] * 5, scale_pct=25)
    assert worse["state"] == "above_limit" and worse["observed_abs"] == pytest.approx(13.0)
    quiet = decide([-0.5] * 5, 2.04, limit_pct=None, limit_abs=2.0, ids=["a"] * 5, scale_pct=25)
    assert quiet["state"] == "above_limit" and quiet["limit_abs"] == 2.0


def test_the_catalogue_floor_and_scale_reach_the_round_decisions(cfg, tmp_path):
    def with_series(round_id, started, overrun, section):
        rec = _rec(PIXEL, round_id, 300, started)
        tuner = rec.journeys[1]
        tuner.metrics["frames.overrun_p90_ms"] = MetricResult(unit="ms", samples=[overrun] * 3, source="macrobenchmark")
        tuner.metrics["section.tuner.load.sum_ms"] = MetricResult(unit="ms", samples=[section] * 3, source="macrobenchmark")
        return rec
    history = [with_series(f"20261008T1{i}0000Z_a", f"2026-10-08T1{i}:00:00Z", 51.0, 0.03) for i in range(6)]
    with Store.rebuild(str(tmp_path / "db"), history) as store:
        new = with_series("20261008T200000Z_b", "2026-10-08T20:00:00Z", 56.0, 0.037)
        decisions = {d["metric"]: d for d in decide_round(store, cfg, PIXEL, new)}
    assert decisions["tuner/frames.overrun_p90_ms"]["state"] == "within_limit"
    assert decisions["tuner/frames.overrun_p90_ms"]["limit_abs"] == pytest.approx(12.75)
    assert decisions["tuner/section.tuner.load.sum_ms"]["state"] == "within_limit"


def test_record_all_gates_writes_decisions_for_every_round_in_order(cfg, tmp_path):
    history = _history(PIXEL, [300, 300, 300, 300, 300, 300, 360, 300])
    with Store.rebuild(str(tmp_path / "db"), history) as store:
        record_all_gates(store, cfg)
        rounds = store.rounds("PIXEL")
        assert store.gate_decisions(rounds[0]["id"])[0]["state"] == "inactive"
        flagged = {d["metric"]: d for d in store.gate_decisions(rounds[6]["id"])}
        assert flagged["startup/startup.ttid_ms"]["state"] == "above_limit"
        # the flagged round stays clean for the next baseline; the next round is within limit
        assert {d["metric"]: d for d in store.gate_decisions(rounds[7]["id"])}["startup/startup.ttid_ms"]["state"] == "within_limit"
        record_all_gates(store, cfg)
        assert len(store.gate_decisions(rounds[6]["id"])) == len(flagged), "idempotent"
