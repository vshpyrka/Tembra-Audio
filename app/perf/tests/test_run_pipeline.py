"""The `run` orchestration shared by both platforms (run.py): a RoundRecord with the
build identity from git, the machine, checks and badges; publish -> sync ->
rebuild -> detect -> report; a per-device lock held for the invocation."""

import json
import os

import pytest

from conftest import Completed, FakeClock, TableRunner
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.devices import Device, DeviceError
from tracepulse.orchestrate_android import JourneyFailed, Refused, Check, RoundOptions, RoundOutcome
from tracepulse.records import JourneyResult, MetricResult, RoundRecord
from tracepulse.run import (
    EXIT_FAILED, EXIT_OK, EXIT_REFUSED, SessionDeps, acquire_lock, git_info, run_session,
)
from tracepulse.__main__ import Paths

PIXEL = Device(device_id="PIXEL", platform="android", kind="device", model="Pixel 6 Pro", alias="pixel6")


class FakeHistory:
    def __init__(self):
        self.published = []
        self.synced = 0
        self.branch = []

    def publish(self, record, _parked=False):
        self.published.append(record)
        self.branch.append(record)
        return f"records/{record.device.device_id}/{record.round_id}.json"

    def sync(self):
        self.synced += 1
        return list(self.branch)

    def pending(self):
        return []

    def records(self):
        return list(self.branch)


def _outcome(status="succeeded", badges=()):
    journeys = [] if status == "failed" else [
        JourneyResult(journey="startup", iterations=20, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=[300.0 + i for i in range(20)], source="macrobenchmark")}),
        JourneyResult(journey="tuner", iterations=20, metrics={
            "frames.p90_ms": MetricResult(unit="ms", samples=[8.0] * 20, source="macrobenchmark")}),
    ]
    return RoundOutcome(status=status, checks=[Check("battery", True, "ok", 87, 50).as_record()],
                        fingerprint={"os_version": "17", "sdk": 37, "network_state": "none"},
                        journeys=journeys, badges=list(badges),
                        artifacts=[{"kind": "trace", "path": "/tmp/x.perfetto-trace"}], app_size_bytes=4321,
                        failure_reason="boom" if status == "failed" else None)


def _git_runner(dirty=False):
    def git(cmd):
        if "--abbrev-ref" in cmd:
            return Completed(0, "007-performance-pipeline\n")
        if "--porcelain" in cmd:
            return Completed(0, " M app/x.kt\n" if dirty else "")
        return Completed(0, "c0ffee1234\n")
    return TableRunner([(["git", "-C"], git)])


@pytest.fixture
def paths(tmp_path):
    data = tmp_path / "data"
    return Paths(data_dir=str(data), db_path=str(data / "tracepulse.db"), reports_dir=str(tmp_path / "reports"),
                 registry_path=str(tmp_path / "regressions" / "registry.yaml"), worktree=str(tmp_path / ".history"))


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


def _deps(round_fn, history=None, calls=None):
    calls = calls if calls is not None else []
    history = history or FakeHistory()

    def report_fn(store, cfg, out, now_ts):
        calls.append("report")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w") as fh:
            fh.write("<html></html>")
        return out

    def detect_fn(store, cfg):
        calls.append("detect")
        return {"lines": []}

    def wrapped_round(*args, **kwargs):
        calls.append("round")
        return round_fn(*args, **kwargs)

    return SessionDeps(round_fn=wrapped_round, history=history, report_fn=report_fn, detect_fn=detect_fn), history, calls


def test_git_info_reads_commit_branch_and_dirty_state():
    assert git_info("/repo", _git_runner()) == {"commit": "c0ffee1234", "branch": "007-performance-pipeline", "dirty": False}
    assert git_info("/repo", _git_runner(dirty=True))["dirty"] is True


def test_session_builds_the_record_and_runs_publish_sync_rebuild_detect_report(cfg, paths, tmp_path):
    deps, history, calls = _deps(lambda *a, **k: _outcome(badges=["noisy"]))
    result = run_session(cfg, paths, PIXEL, "007-performance-pipeline", 1, RoundOptions(journeys=cfg.journeys),
                         _git_runner(dirty=True), FakeClock(), deps)
    assert result.exit_status == EXIT_OK
    assert calls == ["round", "detect", "report"]
    assert history.synced == 1 and len(history.published) == 1
    rec: RoundRecord = history.published[0]
    RoundRecord.validate(rec.to_dict())
    assert rec.device.device_id == "PIXEL" and rec.label == "007-performance-pipeline"
    assert rec.build["commit"] == "c0ffee1234" and rec.build["dirty"] is True and rec.build["branch"] == "007-performance-pipeline"
    assert rec.build["app_size_bytes"] == 4321 and rec.build["type"] == "perf"
    assert rec.machine["hostname"] and rec.machine["tool_version"]
    assert rec.checks == [{"name": "battery", "passed": True, "value": 87, "limit": 50}]
    assert rec.badges == ["noisy"] and rec.status == "succeeded"
    assert rec.round_id.endswith("_007-performance-pipeline") and rec.session == {"index": 1, "size": 1}
    assert [j.journey for j in rec.journeys] == ["startup", "tuner"]
    # the APK size rides the record as a round-level metric too
    assert any("app.size_bytes" in j.metrics for j in rec.journeys)
    assert os.path.isfile(paths.db_path) and os.path.isfile(result.report_path)
    assert result.records[0].round_id == rec.round_id
    assert not os.path.exists(os.path.join(paths.data_dir, "locks", "PIXEL")), "the lock is released"


def test_a_release_round_records_its_type_and_no_section(cfg, paths):
    def outcome(*_a, **_k):
        out = _outcome()
        out.journeys[1].metrics["section.tuner.load.sum_ms"] = MetricResult(unit="ms", samples=[0.0] * 20,
                                                                            source="macrobenchmark")
        out.build_type = "release"
        return out
    deps, history, _calls = _deps(outcome)
    result = run_session(cfg, paths, PIXEL, "release-baseline", 1, RoundOptions(journeys=cfg.journeys, build_type="release"),
                         _git_runner(dirty=False), FakeClock(), deps)
    assert result.exit_status == EXIT_OK
    rec: RoundRecord = history.published[0]
    RoundRecord.validate(rec.to_dict())
    assert rec.build["type"] == "release"
    assert not any(k.startswith("section.") for j in rec.journeys for k in j.metrics), "the facade is compiled out"
    assert "frames.p90_ms" in rec.journeys[1].metrics


def test_refusal_exits_2_and_publishes_nothing(cfg, paths):
    def refuse(*a, **k):
        raise Refused([Check("battery", False, "temperature 41.2 C > 40.0 C")])
    deps, history, calls = _deps(refuse)
    result = run_session(cfg, paths, PIXEL, "x", 1, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps)
    assert result.exit_status == EXIT_REFUSED and "battery" in result.refused
    assert history.published == [] and "report" not in calls


def test_journey_failure_publishes_a_failed_record_and_exits_3(cfg, paths):
    def fail(*a, **k):
        raise JourneyFailed("tuner", "TunerJourneyBenchmark failed twice", _outcome(status="failed"))
    deps, history, calls = _deps(fail)
    result = run_session(cfg, paths, PIXEL, "x", 1, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps)
    assert result.exit_status == EXIT_FAILED
    rec = history.published[0]
    assert rec.status == "failed" and rec.failure_reason == "boom" and rec.journeys == []
    RoundRecord.validate(rec.to_dict())
    assert "report" in calls, "the report is regenerated so the failed round is visible"


def test_no_publish_keeps_the_record_local(cfg, paths):
    deps, history, calls = _deps(lambda *a, **k: _outcome())
    result = run_session(cfg, paths, PIXEL, "x", 1, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps,
                         publish=False)
    assert result.exit_status == EXIT_OK and history.published == []
    local = os.path.join(paths.data_dir, "rounds", result.records[0].round_id, "record.json")
    assert os.path.isfile(local)
    assert json.load(open(local))["round_id"] == result.records[0].round_id


def test_second_invocation_on_the_same_device_is_refused_naming_the_holder(cfg, paths):
    lock = acquire_lock(paths.data_dir, "PIXEL", label="first")
    try:
        with pytest.raises(DeviceError) as exc:
            acquire_lock(paths.data_dir, "PIXEL", label="second")
        message = str(exc.value)
        assert str(os.getpid()) in message and "first" in message and "started" in message
    finally:
        lock.release()
    # a stale lock (dead pid) is taken over
    stale = os.path.join(paths.data_dir, "locks", "PIXEL")
    with open(stale, "w") as fh:
        json.dump({"pid": 999999999, "started_at": "2026-10-08T00:00:00Z", "label": "ghost"}, fh)
    acquire_lock(paths.data_dir, "PIXEL", label="third").release()


def test_session_holds_the_lock_while_rounds_run(cfg, paths):
    seen = {}

    def round_fn(*a, **k):
        seen["held"] = os.path.exists(os.path.join(paths.data_dir, "locks", "PIXEL"))
        return _outcome()
    deps, _history, _calls = _deps(round_fn)
    run_session(cfg, paths, PIXEL, "x", 1, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps)
    assert seen["held"] is True
