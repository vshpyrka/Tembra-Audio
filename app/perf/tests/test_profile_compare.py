"""`tracepulse profile compare` (feature 008, contracts/cli.md, D6): the states run in order on a fresh
install each, one `am instrument` per state names the comparison class, the state and the iterations; a
state that fails or misses a requested journey exits 3 naming it and keeps the earlier states' files; the
statistics (medians, signed change, direction, two-sided Mann–Whitney at alpha 0.01, the gap closed with
`full`) match values computed independently from the fixtures; `comparison.json` validates against the
schema and `comparison.md` carries one row per metric and the startup headline; nothing enters the
history, the records or the store; a refused doctor exits 2 before any build."""

import argparse
import json
import os
import shutil
import statistics

import jsonschema
import pytest

from conftest import Completed, FakeClock, TableRunner
from test_orchestrate_android import PIXEL, SERIAL, _device_rules
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.detect import mw_two_sided_p
from tracepulse.profile_compare import EXIT_FAILED, EXIT_OK, EXIT_REFUSED, main

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "profile")
SCHEMA = os.path.join(os.path.dirname(os.path.dirname(__file__)), "tracepulse", "schema", "comparison.schema.json")
CLASS = "audio.tembra.perf.ProfileComparisonBenchmark"
STATE_ARG = "tembra.compare.state"


def _ok(text=""):
    return Completed(0, text)


@pytest.fixture
def cfg(tmp_path):
    root = tmp_path / "app"
    root.mkdir()
    shutil.copy(DEFAULT_CONFIG_PATH, root / "tracepulse.yml")
    return load_config(str(root / "tracepulse.yml"))


@pytest.fixture
def paths(tmp_path, cfg):
    from tracepulse.__main__ import resolve_paths
    return resolve_paths(cfg, perf_dir=str(tmp_path / "perf"))


def _args(**overrides):
    base = dict(command="profile", profile_command="compare", device="pixel6", with_full=False, iterations=None,
                journeys=None, no_build=False, apk=None, test_apk=None, keep_build=False)
    base.update(overrides)
    return argparse.Namespace(**base)


def _apks(cfg):
    return (os.path.join(cfg.root, "androidApp", "build", "outputs", "apk", "perf", "androidApp-perf.apk"),
            os.path.join(cfg.root, "perf-benchmarks", "build", "outputs", "apk", "perf", "perf-benchmarks-perf.apk"))


def _fixture_json(state, drop_method=None):
    with open(os.path.join(FIXTURES, f"benchmarkData-{state}.json")) as fh:
        raw = json.load(fh)
    if drop_method:
        raw["benchmarks"] = [b for b in raw["benchmarks"] if b["name"] != drop_method]
    return raw


class _Device:
    """The fake Pixel of a comparison: remembers the state of the last `am instrument` so the pull serves that
    state's benchmarkData.json, and can fail one state or hide one journey of it."""

    def __init__(self, cfg, tmp_path, failing_state=None, missing=None, **device_kwargs):
        self.cfg = cfg
        self.state = None
        self.failing_state = failing_state
        self.missing = missing or {}
        self.device_state, rules = _device_rules(tmp_path, **device_kwargs)
        app_apk, test_apk = _apks(cfg)

        def build(cmd):
            for p in (app_apk, test_apk):
                os.makedirs(os.path.dirname(p), exist_ok=True)
                with open(p, "wb") as fh:
                    fh.write(b"\x00" * 1234)
            return _ok("BUILD SUCCESSFUL\n")

        def nohup(cmd):
            device_cmd = cmd[4]
            marker = f"-e {STATE_ARG} "
            self.state = device_cmd.split(marker, 1)[1].split()[0]
            return _ok()

        def cat(cmd):
            if self.state == self.failing_state:
                return _ok("TRACEPULSE_ATTEMPT_START\n\nFAILURES!!!\nTests run: 4,  Failures: 1\n")
            return _ok("TRACEPULSE_ATTEMPT_START\n\nOK (4 tests)\n")

        def pull(cmd):
            dest = cmd[-1]
            os.makedirs(dest, exist_ok=True)
            with open(os.path.join(dest, "audio.tembra.perf-benchmarkData.json"), "w") as fh:
                json.dump(_fixture_json(self.state, self.missing.get(self.state)), fh)
            return _ok()

        self.rules = [
            (["xcrun"], Completed(1, "", "no xcrun in this test")),
            (["git", "-C", _repo_root(cfg), "rev-parse", "HEAD"], _ok("0123456789abcdef\n")),
            (["git", "-C", _repo_root(cfg), "rev-parse", "--abbrev-ref", "HEAD"], _ok("008-baseline-profiles\n")),
            (["git", "-C", _repo_root(cfg), "status", "--porcelain"], _ok("")),
            (["./gradlew"], build),
            (lambda cmd: cmd[:4] == ["adb", "-s", SERIAL, "shell"] and cmd[4].startswith("nohup sh -c"), nohup),
            (["adb", "-s", SERIAL, "shell", "cat"], cat),
            (["adb", "-s", SERIAL, "pull"], pull),
        ] + rules
        self.runner = TableRunner(self.rules)


def _repo_root(cfg):
    from tracepulse.run import _repo_root
    return _repo_root(cfg)


def _run(cfg, paths, device, args=None):
    return main(args or _args(), cfg, paths, device.runner, FakeClock())


def _instruments(runner):
    return [c["cmd"][4] for c in runner.calls if c["cmd"][:4] == ["adb", "-s", SERIAL, "shell"]
            and c["cmd"][4].startswith("nohup sh -c")]


def _state_of(device_cmd):
    return device_cmd.split(f"-e {STATE_ARG} ", 1)[1].split()[0]


def _comparison(payload):
    with open(payload["comparison_json"]) as fh:
        return json.load(fh)


# --- the states ----------------------------------------------------------------------------------

def test_states_run_in_order_and_full_only_on_request(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path)
    code, payload = _run(cfg, paths, device)
    assert code == EXIT_OK
    assert [_state_of(c) for c in _instruments(device.runner)] == ["none", "profile"]
    assert _comparison(payload)["states"] == ["none", "profile"]

    device = _Device(cfg, tmp_path)
    code, payload = _run(cfg, paths, device, _args(with_full=True))
    assert code == EXIT_OK
    assert [_state_of(c) for c in _instruments(device.runner)] == ["none", "profile", "full"]


def test_each_state_starts_from_a_fresh_install_of_both_apks(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path)
    _run(cfg, paths, device)
    runner = device.runner
    uninstalls = [i for i, c in enumerate(runner.calls) if c["cmd"][3:6] == ["shell", "pm", "uninstall"]]
    installs = [i for i, c in enumerate(runner.calls) if c["cmd"][3:4] == ["install"]]
    instruments = [i for i, c in enumerate(runner.calls)
                   if c["cmd"][:4] == ["adb", "-s", SERIAL, "shell"] and c["cmd"][4].startswith("nohup sh -c")]
    assert len(installs) == 4 and len(uninstalls) >= 4, "two APKs installed per state after an uninstall"
    assert uninstalls[0] < installs[0] < installs[1] < instruments[0] < uninstalls[2] < installs[2] < installs[3] < instruments[1]


def test_one_instrument_per_state_names_the_class_the_state_and_the_iterations(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path)
    _run(cfg, paths, device, _args(iterations=7))
    cmds = _instruments(device.runner)
    assert len(cmds) == 2
    for cmd, state in zip(cmds, ("none", "profile")):
        assert f"-e class {CLASS} " in cmd
        assert f"-e {STATE_ARG} {state} " in cmd
        # Macrobenchmark 1.5 does not honour androidx.benchmark.iterations (checked on the Pixel 6 Pro, T039);
        # the comparison class reads its own argument, passed beside the pipeline's for parity with a round.
        assert "-e androidx.benchmark.iterations 7 " in cmd
        assert "-e tembra.compare.iterations 7 " in cmd
        assert "-e additionalTestOutputDir /storage/emulated/0/Android/media/audio.tembra.perf/ " in cmd


def test_the_default_iterations_come_from_the_configuration(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path)
    _run(cfg, paths, device)
    assert f"-e tembra.compare.iterations {cfg.collection.iterations} " in _instruments(device.runner)[0]


def test_a_failing_state_exits_3_naming_it_and_keeps_the_earlier_state_files(cfg, paths, tmp_path, capsys):
    device = _Device(cfg, tmp_path, failing_state="profile")
    code, payload = _run(cfg, paths, device)
    assert code == EXIT_FAILED
    assert "profile" in capsys.readouterr().out
    none_dir = os.path.join(payths_dir(paths), payload["comparison_id"], "none")
    assert os.path.isfile(os.path.join(none_dir, "audio.tembra.perf-benchmarkData.json"))
    assert not os.path.exists(os.path.join(payths_dir(paths), payload["comparison_id"], "comparison.json"))


def payths_dir(paths):
    return os.path.join(paths.data_dir, "comparisons")


def test_a_state_missing_a_requested_journey_exits_3_naming_it(cfg, paths, tmp_path, capsys):
    device = _Device(cfg, tmp_path, missing={"profile": "tuner"})
    code, _payload = _run(cfg, paths, device)
    assert code == EXIT_FAILED
    out = capsys.readouterr().out
    assert "tuner" in out and "profile" in out


# --- the statistics ------------------------------------------------------------------------------

def _samples(state, method, key):
    b = next(b for b in _fixture_json(state)["benchmarks"] if b["name"] == method)
    return b["metrics"][key]["runs"]


def test_medians_change_direction_and_confirmation_match_independent_values(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path)
    code, payload = _run(cfg, paths, device)
    assert code == EXIT_OK
    metrics = _comparison(payload)["metrics"]

    none = _samples("none", "startup", "timeToInitialDisplayMs")
    profile = _samples("profile", "startup", "timeToInitialDisplayMs")
    ttid = metrics["startup/startup.ttid_ms"]
    assert ttid["journey"] == "startup" and ttid["unit"] == "ms" and ttid["higher_is_worse"] is True
    assert ttid["by_state"]["none"]["median"] == pytest.approx(statistics.median(none))
    assert ttid["by_state"]["profile"]["median"] == pytest.approx(statistics.median(profile))
    assert ttid["by_state"]["none"]["samples"] == none
    expected_change = (statistics.median(profile) - statistics.median(none)) / statistics.median(none) * 100
    assert ttid["change_pct"] == pytest.approx(expected_change, abs=0.01)
    assert expected_change < -15
    assert ttid["direction"] == "better"
    assert ttid["p_value"] == pytest.approx(mw_two_sided_p(none, profile))
    assert ttid["confirmed"] is True
    assert "gap_closed_pct" not in ttid

    # the tuner journey's end-of-journey memory is 4 % higher with the profile by construction of the fixture
    end_kb = metrics["tuner/memory.end_kb"]
    assert end_kb["change_pct"] > 0 and end_kb["direction"] == "worse" and end_kb["confirmed"] is True

    # the tuner's first-open section is lower and confirmed
    load = metrics["tuner/section.tuner.load.sum_ms"]
    assert load["direction"] == "better" and load["confirmed"] is True


def test_gap_closed_is_present_only_with_full_and_null_when_none_equals_full(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path)
    _code, payload = _run(cfg, paths, device, _args(with_full=True))
    metrics = _comparison(payload)["metrics"]
    ttid = metrics["startup/startup.ttid_ms"]
    none, profile, full = (ttid["by_state"][s]["median"] for s in ("none", "profile", "full"))
    assert ttid["gap_closed_pct"] == pytest.approx((none - profile) / (none - full) * 100, abs=0.01)
    peak = metrics["stress/memory.peak_kb"]
    assert peak["by_state"]["none"]["median"] == peak["by_state"]["full"]["median"]
    assert peak["gap_closed_pct"] is None


# --- the files -----------------------------------------------------------------------------------

def test_comparison_json_validates_and_markdown_has_a_row_per_metric_and_the_headline(cfg, paths, tmp_path, capsys):
    device = _Device(cfg, tmp_path)
    code, payload = _run(cfg, paths, device, _args(with_full=True))
    assert code == EXIT_OK
    data = _comparison(payload)
    with open(SCHEMA) as fh:
        jsonschema.validate(data, json.load(fh), cls=jsonschema.Draft202012Validator)
    assert data["build"]["commit"] == "0123456789abcdef" and data["build"]["dirty"] is False
    assert data["device"]["device_id"] == SERIAL and data["device"]["fingerprint"]["sdk"] == 37
    assert data["journeys"] == ["startup", "tuner", "metronome", "timeline", "recording", "stress"]
    assert set(data["artifacts"]) == {"none", "profile", "full"}
    md_path = os.path.join(os.path.dirname(payload["comparison_json"]), "comparison.md")
    with open(md_path) as fh:
        md = fh.read()
    rows = [line for line in md.splitlines() if line.startswith("| ") and "/" in line.split("|")[1]]
    assert len(rows) == len(data["metrics"])
    assert "startup.ttid_ms" in md and "gap closed" in md.lower()
    out = capsys.readouterr().out
    assert "startup.ttid_ms:" in out and "confirmed" in out


def test_journeys_subset_restricts_the_instrumentation_and_the_required_journeys(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path, missing={"profile": "tuner"})
    code, payload = _run(cfg, paths, device, _args(journeys="startup"))
    assert code == EXIT_OK, "a missing tuner does not matter when only startup is requested"
    cmd = _instruments(device.runner)[0]
    assert f"-e class {CLASS}#startup " in cmd
    data = _comparison(payload)
    assert data["journeys"] == ["startup"]
    assert all(m["journey"] == "startup" for m in data["metrics"].values())


def test_nothing_enters_the_history_the_records_or_the_store(cfg, paths, tmp_path):
    os.makedirs(paths.data_dir, exist_ok=True)
    with open(paths.db_path, "wb") as fh:
        fh.write(b"untouched")
    device = _Device(cfg, tmp_path)
    code, _payload = _run(cfg, paths, device)
    assert code == EXIT_OK
    with open(paths.db_path, "rb") as fh:
        assert fh.read() == b"untouched"
    assert not os.path.exists(os.path.join(paths.data_dir, "records"))
    assert not os.path.exists(os.path.join(paths.data_dir, "unpublished"))
    assert not os.path.exists(os.path.join(paths.data_dir, "rounds"))
    assert not any(c["cmd"][:1] == ["git"] and c["cmd"][3:4] not in (["rev-parse"], ["status"]) for c in device.runner.calls)


def test_a_refused_doctor_exits_2_before_any_build(cfg, paths, tmp_path):
    device = _Device(cfg, tmp_path, battery="  AC powered: true\n  USB powered: false\n  level: 20\n  temperature: 291\n")
    code, _payload = _run(cfg, paths, device)
    assert code == EXIT_REFUSED
    assert not any(c["cmd"][:1] == ["./gradlew"] for c in device.runner.calls)
    assert not _instruments(device.runner)


def test_no_build_uses_the_given_artifacts(cfg, paths, tmp_path):
    app = tmp_path / "x.apk"
    test = tmp_path / "y.apk"
    app.write_bytes(b"\x00" * 10)
    test.write_bytes(b"\x00" * 10)
    device = _Device(cfg, tmp_path)
    code, payload = _run(cfg, paths, device, _args(no_build=True, apk=str(app), test_apk=str(test)))
    assert code == EXIT_OK
    assert not any(c["cmd"][:1] == ["./gradlew"] for c in device.runner.calls)
    assert _comparison(payload)["build"]["apk"] == str(app)
    installed = [c["cmd"][-1] for c in device.runner.calls if c["cmd"][3:4] == ["install"]]
    assert installed[:2] == [str(app), str(test)]


def test_direction_and_change_follow_the_raw_difference_when_the_baseline_median_is_negative(cfg):
    """frames.overrun_p90_ms is negative when frames finish before the deadline: −1.4 → −3.6 ms is less overrun,
    so it is `better` with a negative change, not `worse` with +157 % (the first real comparison, 2026-10-12)."""
    from tracepulse.profile_compare import compare_metrics
    from tracepulse.records import JourneyResult, MetricResult
    def journey(values):
        return {"tuner": JourneyResult(journey="tuner", iterations=len(values), metrics={
            "frames.overrun_p90_ms": MetricResult(unit="ms", samples=values, source="macrobenchmark")})}
    results = {"none": journey([-1.0, -1.4, -1.6, -1.2, -1.5, -1.3, -1.4, -1.1]),
               "profile": journey([-3.4, -3.6, -3.8, -3.5, -3.7, -3.6, -3.9, -3.5])}
    m = compare_metrics(results, ["tuner"], ["none", "profile"], cfg)["tuner/frames.overrun_p90_ms"]
    assert m.direction == "better"
    assert m.change_pct < 0
    assert m.change_pct == pytest.approx((-3.6 - -1.35) / abs(-1.35) * 100, abs=0.5)
    assert m.confirmed is True
