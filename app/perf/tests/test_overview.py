"""The overview comparison: each device's newest feature label against the one before it."""

import itertools

import pytest

from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.overview import JOURNEY_ORDER, judge, overview_data
from tracepulse.catalogue import lookup
from tracepulse.store import Store

_ts = itertools.count(1)


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


@pytest.fixture
def store(tmp_path):
    with Store(str(tmp_path / "db")) as s:
        yield s


def _round(store, label, values_by_series=None, device_id="D", platform="android", kind="device",
           reference=True, status="succeeded", injected=(), flags=(), dirty=False, model="Model"):
    """Add a round and one aggregate (with its samples) per series from per-iteration lists."""
    n = next(_ts)
    rid = store.add_round(n, label, "sha", dirty, list(injected), device_id, "fp", list(flags), 0.0, "",
                          round_key=f"{label}-{n}", device_id=device_id, platform=platform, kind=kind,
                          model=model, reference=reference, status=status)
    for series, values in (values_by_series or {}).items():
        if not isinstance(values, list):
            values = [values] * 3
        store.add_samples(rid, series.split("/")[0], series, values)
        store.add_aggregate(rid, series, values)
    return rid


def _cell(data, journey, device=0):
    return data["devices"][device]["cells"][journey]


P99 = "tuner/frames.p99_ms"


def _pair(store, before_values, after_values, **kw):
    _round(store, "A", before_values, **kw)
    _round(store, "B", after_values, **kw)


def test_compares_newest_feature_with_the_one_before(store, cfg):
    for _ in range(3):
        _round(store, "A", {P99: 10.0})
    for _ in range(3):
        _round(store, "B", {P99: 10.0})
    row = overview_data(store, cfg)["devices"][0]
    assert (row["before"], row["after"]) == ("A", "B")
    assert (row["rounds_before"], row["rounds_after"]) == (3, 3)


def test_experiment_labels_are_skipped(store, cfg):
    _round(store, "A", {P99: 10.0})
    _round(store, "noise-baseline", {P99: 10.0})
    _round(store, "B", {P99: 10.0})
    row = overview_data(store, cfg)["devices"][0]
    assert (row["before"], row["after"]) == ("A", "B")


def test_label_order_is_first_appearance(store, cfg):
    _round(store, "A", {P99: 10.0})
    _round(store, "noise-baseline", {P99: 10.0})
    _round(store, "A", {P99: 10.0})
    _round(store, "B", {P99: 10.0})
    row = overview_data(store, cfg)["devices"][0]
    assert (row["before"], row["after"]) == ("A", "B")
    assert row["rounds_before"] == 2 and row["rounds_after"] == 1


def test_failed_rounds_do_not_make_a_feature(store, cfg):
    for label in ("A", "A", "B", "B"):
        _round(store, label, {P99: 10.0})
    _round(store, "C", status="failed")
    row = overview_data(store, cfg)["devices"][0]
    assert (row["before"], row["after"]) == ("A", "B")


def test_one_feature_device(store, cfg):
    _round(store, "A", {P99: 10.0})
    row = overview_data(store, cfg)["devices"][0]
    assert row["before"] is None and row["after"] == "A" and row["cells"] == {}


def test_injected_simulator_and_non_reference_are_left_out(store, cfg):
    _round(store, "A", {P99: 10.0})
    _round(store, "B", {P99: 10.0})
    _round(store, "B", {P99: 10.0}, injected=["tuner/frames.p99_ms"])
    _round(store, "A", {P99: 10.0}, device_id="SIM", kind="simulator", reference=False)
    _round(store, "A", {P99: 10.0}, device_id="SPARE", reference=False)
    data = overview_data(store, cfg)
    assert [d["device_id"] for d in data["devices"]] == ["D"]
    assert data["devices"][0]["rounds_after"] == 1


def test_dirty_and_noisy_rounds_count_and_are_reported(store, cfg):
    _round(store, "A", {P99: 10.0})
    _round(store, "B", {P99: 10.0}, flags=["noisy"])
    _round(store, "B", {P99: 10.0}, flags=["noisy"], dirty=True)
    _round(store, "B", {P99: 10.0})
    row = overview_data(store, cfg)["devices"][0]
    assert row["rounds_after"] == 3
    assert row["flags_after"] == {"noisy": 2, "thermal": 0, "dirty": 1}


def _items(data):
    return {i["series"]: i for i in data["items"]}


def test_worse_better_same_follow_the_catalogue_limits(store, cfg):
    load = "tuner/section.tuner.load.sum_ms"
    _pair(store, {P99: 10.0, load: 0.03}, {P99: 13.0, load: 116.0})
    data = overview_data(store, cfg)
    assert _items(data)[P99]["state"] == "worse"
    assert _items(data)[load]["state"] == "worse"
    assert _cell(data, "tuner")["state"] == "worse" and _cell(data, "tuner")["worse"] == 2


def test_better_and_same(tmp_path, cfg):
    load = "tuner/section.tuner.load.sum_ms"
    with Store(str(tmp_path / "b")) as s:
        _pair(s, {P99: 10.0}, {P99: 7.0})
        data = overview_data(s, cfg)
        assert _items(data)[P99]["state"] == "better"
        assert _cell(data, "tuner")["state"] == "better" and _cell(data, "tuner")["better"] == 1
    with Store(str(tmp_path / "s")) as s:
        _pair(s, {P99: 10.0, load: 0.03}, {P99: 11.0, load: 0.5})
        data = overview_data(s, cfg)
        assert data["items"] == []
        assert _cell(data, "tuner")["state"] == "same"


def test_negative_series_direction(tmp_path, cfg):
    series = "tuner/frames.overrun_p90_ms"
    with Store(str(tmp_path / "w")) as s:
        _pair(s, {series: -5.0}, {series: -2.0})
        assert _items(overview_data(s, cfg))[series]["state"] == "worse"
    with Store(str(tmp_path / "b")) as s:
        _pair(s, {series: -2.0}, {series: -5.0})
        assert _items(overview_data(s, cfg))[series]["state"] == "better"


def test_zero_baseline(store, cfg):
    series = "stress/audio.xruns"
    _pair(store, {series: 0.0}, {series: 3.0})
    item = _items(overview_data(store, cfg))[series]
    assert item["state"] == "worse" and item["delta_pct"] is None and item["delta_abs"] == 3.0


def test_counts_single_valued_and_disabled_are_not_judged(store, cfg):
    count = "tuner/section.tuner.load.count"
    _pair(store, {count: 10.0, "app.size_bytes": 1000.0, P99: 10.0},
          {count: 400.0, "app.size_bytes": 9000.0, P99: 10.0})
    data = overview_data(store, cfg)
    assert data["items"] == []
    assert _cell(data, "tuner")["worse"] == 0 and _cell(data, "tuner")["state"] == "same"


def test_redefined_journey_is_changed_not_worse(store, cfg):
    duration = "stress/journey.duration_ms"
    _round(store, "A", {duration: 4500.0, "stress/section.shell.x.sum_ms": 5.0})
    _round(store, "B", {duration: 25000.0, "stress/section.shell.x.sum_ms": 5.0,
                        "stress/section.y.sum_ms": 5.0})
    data = overview_data(store, cfg)
    cell = _cell(data, "stress")
    assert cell["state"] == "changed" and cell["sections_added"] == ["section.y.sum_ms"]
    assert cell["sections_removed"] == []
    assert _items(data)[duration]["state"] == "changed"
    assert not [i for i in data["items"] if i["journey"] == "stress" and i["state"] == "worse"]


def test_journey_missing_on_one_side_is_none(store, cfg):
    _round(store, "A", {P99: 10.0, "startup/startup.ttid_ms": 300.0})
    _round(store, "B", {P99: 10.0, "startup/startup.ttfd_ms": 400.0, "timeline/frames.p99_ms": 10.0})
    data = overview_data(store, cfg)
    assert _cell(data, "timeline")["state"] == "none"
    assert _cell(data, "startup")["state"] != "changed"
    assert _cell(data, "startup")["new"] == ["startup.ttfd_ms"]
    assert _cell(data, "startup")["gone"] == ["startup.ttid_ms"]
    assert data["journeys"] == [j for j in JOURNEY_ORDER if j in ("startup", "tuner", "timeline")]


def test_items_carry_every_iteration_and_are_ordered(store, cfg):
    p50, p90, xruns = "tuner/frames.p50_ms", "tuner/frames.p90_ms", "stress/audio.xruns"
    twenty = lambda v: [v + i * 0.001 for i in range(20)]  # noqa: E731
    _round(store, "A", {p50: twenty(10.0), p90: twenty(10.0), xruns: twenty(0.0)})
    _round(store, "B", {p50: twenty(13.0), p90: twenty(50.0), xruns: twenty(5.0)})
    items = overview_data(store, cfg)["items"]
    assert [i["series"] for i in items] == [xruns, p90, p50]
    rounds = items[0]["rounds"]
    assert [r["side"] for r in rounds] == ["before", "after"]
    assert [r["label"] for r in rounds] == ["A", "B"]
    assert all(len(r["values"]) == 20 for r in rounds)
    assert rounds[0]["round_key"].startswith("A-") and rounds[0]["median"] == pytest.approx(0.0095, abs=1e-4)


def test_judge_returns_the_limits_used():
    entry = lookup("android", "frames.p99_ms")
    verdict = judge(10.0, 13.0, entry)
    assert verdict["state"] == "worse" and verdict["limit_pct"] == 25
    assert verdict["delta_abs"] == pytest.approx(3.0) and verdict["delta_pct"] == pytest.approx(30.0)
