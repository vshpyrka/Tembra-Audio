"""The public build (`report --public`): no host names, no home-directory paths."""

import copy
import glob
import os
from pathlib import Path

import pytest

from tracepulse.__main__ import main
from tracepulse.analysis import analyze_all
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.gate import record_all_gates
from tracepulse.public import publicise
from tracepulse.records import RoundRecord
from tracepulse.report import generate_report, report_data
from tracepulse.store import Store

HISTORY = os.path.join(os.path.dirname(__file__), "fixtures", "history-3-devices")
NOW_TS = 1_791_900_000
FAILURE = "Failed to launch at /Users/someone/Workspace/Dev/Tembra Audio/app/perf/data/rounds/x and /home/runner/y"


@pytest.fixture(scope="module")
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


@pytest.fixture(scope="module")
def store(tmp_path_factory, cfg):
    records = [RoundRecord.load(p) for p in sorted(glob.glob(os.path.join(HISTORY, "records", "*", "*.json")))]
    base = next(r for r in records if r.device.device_id.startswith("00008110"))
    failed = RoundRecord.from_dict(copy.deepcopy(base.to_dict()))
    failed.round_id = "20261020T140000Z_failed"
    failed.status = "failed"
    failed.failure_reason = FAILURE
    failed.machine = {"hostname": "Someones-MacBook.local", "os": "Darwin", "tool_version": "0.2.0"}
    failed.fingerprint = {**failed.fingerprint, "build_dir": "/Users/someone"}
    failed.journeys = []
    failed.gate = []
    failed.started_at, failed.finished_at = "2026-10-20T14:00:00Z", "2026-10-20T14:01:00Z"
    db = str(tmp_path_factory.mktemp("history") / "tracepulse.db")
    s = Store.rebuild(db, records + [failed])
    analyze_all(s, cfg)
    record_all_gates(s, cfg)
    yield s
    s.close()


def _rounds(data):
    return [r for d in data["devices"] for r in d["rounds"]]


def test_public_page_has_no_host_names_or_home_paths(store, cfg, tmp_path):
    out = str(tmp_path / "public.html")
    generate_report(store, cfg, out, now_ts=NOW_TS, public=True)
    html = Path(out).read_text(encoding="utf-8")
    for forbidden in ("/Users/", "/home/", "mbp-m4", "Someones-MacBook.local"):
        assert forbidden not in html


def test_public_page_keeps_failure_reasons_readable(store, cfg):
    reasons = [r["failure_reason"] for r in _rounds(report_data(store, cfg, public=True)) if r["failure_reason"]]
    assert "Failed to launch at ~/Workspace/Dev/Tembra Audio/app/perf/data/rounds/x and ~/y" in reasons


def test_public_rounds_have_no_machine_or_artifacts(store, cfg):
    rounds = _rounds(report_data(store, cfg, public=True))
    assert rounds
    for r in rounds:
        assert "machine" not in r
        assert r["artifacts"] == []


def test_private_page_is_unchanged(store, cfg):
    data = report_data(store, cfg)
    assert any(r["machine"] == "mbp-m4" for r in _rounds(data))
    assert any(r["artifacts"] for r in _rounds(data))


def test_publicise_does_not_mutate_its_input(store, cfg):
    data = report_data(store, cfg)
    before = copy.deepcopy(data)
    publicise(data)
    assert data == before


def test_cli_report_public_flag(tmp_path, monkeypatch):
    records = [RoundRecord.load(p) for p in sorted(glob.glob(os.path.join(HISTORY, "records", "*", "*.json")))]
    os.makedirs(tmp_path / "data")
    Store.rebuild(str(tmp_path / "data" / "tracepulse.db"), records).close()
    monkeypatch.setattr("tracepulse.__main__.PERF_DIR", str(tmp_path))
    out = tmp_path / "public.html"
    assert main(["report", "--public", "--out", str(out)]) == 0
    html = out.read_text(encoding="utf-8")
    assert "mbp-m4" not in html
    assert "<html" in html


def test_publicise_shortens_paths_without_a_trailing_slash():
    data = {"devices": [], "a": "cd '/Users/someone' failed", "b": "home is /home/runner", "c": "/Users/x/y"}
    out = publicise(data)
    assert (out["a"], out["b"], out["c"]) == ("cd '~' failed", "home is ~", "~/y")


def test_the_seeded_fingerprint_path_reaches_the_private_page(store, cfg):
    assert any(r["fingerprint"].get("build_dir") == "/Users/someone" for r in _rounds(report_data(store, cfg)))
