"""Sessions (spec FR-018a, US6): `run --rounds N` builds once, repeats install ->
warm-up -> journeys -> collect -> record -> publish N times with session index and
size, stops at the first refusal (exit 2) or failure (exit 3) after publishing the
completed rounds, needs a label, and lists the gate flags across the session."""

import os

import pytest

from conftest import FakeClock, TableRunner, Completed
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.devices import Device
from tracepulse.orchestrate_android import Check, JourneyFailed, Refused, RoundOptions, RoundOutcome
from tracepulse.records import JourneyResult, MetricResult
from tracepulse.run import EXIT_FAILED, EXIT_OK, EXIT_REFUSED, SessionDeps, run_session
from tracepulse.__main__ import Paths, main

PIXEL = Device(device_id="PIXEL", platform="android", kind="device", model="Pixel 6 Pro", alias="pixel6")


class FakeHistory:
    def __init__(self):
        self.branch = []
        self.published = []

    def publish(self, record, _parked=False):
        self.published.append(record)
        self.branch.append(record)
        return f"records/{record.device.device_id}/{record.round_id}.json"

    def sync(self):
        return list(self.branch)

    def records(self):
        return list(self.branch)

    def pending(self):
        return []


def _outcome(ttid=300.0):
    return RoundOutcome(status="succeeded", checks=[Check("battery", True, "ok").as_record()],
                        fingerprint={"os_version": "17", "network_state": "none"},
                        journeys=[JourneyResult(journey="startup", iterations=20, metrics={
                            "startup.ttid_ms": MetricResult(unit="ms", samples=[ttid + i * 0.1 for i in range(20)], source="macrobenchmark")})],
                        badges=[], artifacts=[], app_size_bytes=100)


def _git_runner():
    def git(cmd):
        if "--abbrev-ref" in cmd:
            return Completed(0, "007\n")
        if "--porcelain" in cmd:
            return Completed(0, "")
        return Completed(0, "c0ffee\n")
    return TableRunner([(["git", "-C"], git)])


@pytest.fixture
def paths(tmp_path):
    data = tmp_path / "data"
    return Paths(data_dir=str(data), db_path=str(data / "tracepulse.db"), reports_dir=str(tmp_path / "reports"),
                 registry_path=str(tmp_path / "regressions" / "registry.yaml"), worktree=str(tmp_path / ".history"))


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


def _deps(round_fn, prepare_calls, history=None):
    history = history or FakeHistory()

    def prepare_fn(cfg, device, runner, session_dir, options):
        prepare_calls.append(session_dir)
        return RoundOptions(journeys=options.journeys, iterations=options.iterations, no_build=True,
                            apk="/built/app.apk", test_apk="/built/bench.apk")

    def report_fn(store, cfg, out, now_ts):
        os.makedirs(os.path.dirname(out), exist_ok=True)
        open(out, "w").write("<html></html>")
        return out

    def gate_fn(store, cfg_, device, record):
        from tracepulse.gate import decide_round
        return decide_round(store, cfg_, device, record)

    return SessionDeps(round_fn=round_fn, history=history, report_fn=report_fn,
                       detect_fn=lambda s, c: {"lines": []}, gate_fn=gate_fn, prepare_fn=prepare_fn), history


def test_session_builds_once_and_publishes_every_round(cfg, paths):
    seen = []
    prepare_calls = []

    def round_fn(cfg_, device, runner, clock, round_dir, options):
        seen.append(options)
        return _outcome()
    deps, history = _deps(round_fn, prepare_calls)
    result = run_session(cfg, paths, PIXEL, "007-performance-pipeline", 5, RoundOptions(journeys=cfg.journeys),
                         _git_runner(), FakeClock(), deps)
    assert result.exit_status == EXIT_OK
    assert len(prepare_calls) == 1, "one build per session"
    assert len(seen) == 5 and all(o.no_build and o.apk == "/built/app.apk" for o in seen)
    assert [r.session for r in history.published] == [{"index": i, "size": 5} for i in range(1, 6)]
    assert {r.label for r in history.published} == {"007-performance-pipeline"}
    assert len({r.round_id for r in history.published}) == 5, "distinct round ids"


def test_session_stops_at_the_first_refusal_after_publishing_completed_rounds(cfg, paths):
    calls = {"n": 0}

    def round_fn(*a, **k):
        calls["n"] += 1
        if calls["n"] == 3:
            raise Refused([Check("battery", False, "level 42% < 50%")])
        return _outcome()
    deps, history = _deps(round_fn, [])
    result = run_session(cfg, paths, PIXEL, "x", 5, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps)
    assert result.exit_status == EXIT_REFUSED and calls["n"] == 3
    assert len(history.published) == 2 and "battery" in result.refused
    assert result.report_path and os.path.isfile(result.report_path), "the completed rounds are reported"


def test_session_stops_at_the_first_failure_and_publishes_the_failed_record(cfg, paths):
    calls = {"n": 0}

    def round_fn(*a, **k):
        calls["n"] += 1
        if calls["n"] == 2:
            failed = RoundOutcome(status="failed", checks=[], fingerprint={"os_version": "17"}, journeys=[], badges=[],
                                  artifacts=[], failure_reason="TunerJourneyBenchmark failed twice")
            raise JourneyFailed("tuner", "TunerJourneyBenchmark failed twice", failed)
        return _outcome()
    deps, history = _deps(round_fn, [])
    result = run_session(cfg, paths, PIXEL, "x", 5, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps)
    assert result.exit_status == EXIT_FAILED and calls["n"] == 2
    assert [r.status for r in history.published] == ["succeeded", "failed"]


def test_session_lists_gate_flags_across_rounds_and_still_succeeds(cfg, paths):
    history = FakeHistory()
    values = iter([300.0] * 6 + [380.0, 300.0])

    def round_fn(*a, **k):
        return _outcome(next(values))
    deps, history = _deps(round_fn, [], history)
    result = run_session(cfg, paths, PIXEL, "x", 8, RoundOptions(journeys=cfg.journeys), _git_runner(), FakeClock(), deps)
    assert result.exit_status == EXIT_OK
    flagged = [f for f in result.flagged if f["metric"] == "startup/startup.ttid_ms"]
    assert len(flagged) == 1 and flagged[0]["round_id"] == history.published[6].round_id
    assert history.published[6].gate and any(g["state"] == "above_limit" for g in history.published[6].gate)


def test_run_requires_a_label(capsys):
    assert main(["run", "--device", "pixel6", "--rounds", "5"]) == 4
    assert "--label" in capsys.readouterr().err
