"""Records carry no host name and no home-directory path (scrub.py): the `perf-history` branch is public."""

import re

from tracepulse import scrub
from tracepulse.scrub import machine_id, scrub_record, scrub_text

REPO = "/Users/alice/Work/Tembra Audio"


def _record():
    return {
        "machine": {"hostname": "machine-0123abcd", "os": "Darwin 27.0.0", "tool_version": "0.2.0"},
        "checks": [{"name": "java_home", "passed": True, "value": "/Users/alice/Library/Java/temurin-21.jdk/Contents/Home"},
                   {"name": "battery", "passed": True, "value": 87, "limit": 50}],
        "artifacts": [{"kind": "trace", "path": f"{REPO}/app/perf/data/rounds/r1/startup.perfetto-trace"},
                      {"kind": "log", "path": "/Volumes/External/perf/data/rounds/r1/gradle.log"}],
        "failure_reason": f"7: launch failed at {REPO}/app/perf/data/r1 on Alices-MacBook-Pro.local (alices-macbook-pro)",
        "session": {"index": 1, "size": 5},
    }


def test_machine_id_is_a_stable_hash_of_the_hardware_id(monkeypatch):
    monkeypatch.setattr(scrub, "_hardware_id", lambda: "6F1C2D3E-0000-4000-8000-1234567890AB")
    scrub.machine_id.cache_clear()
    first = machine_id()
    scrub.machine_id.cache_clear()
    assert re.fullmatch(r"machine-[0-9a-f]{8}", first)
    assert machine_id() == first
    assert "6f1c2d3e" not in first


def test_machine_id_without_a_hardware_id_names_no_one(monkeypatch):
    monkeypatch.setattr(scrub, "_hardware_id", lambda: None)
    scrub.machine_id.cache_clear()
    try:
        assert machine_id() == "machine-unknown"
    finally:
        scrub.machine_id.cache_clear()


def test_scrub_text_makes_repo_paths_relative_and_shortens_home_paths():
    kw = dict(repo_root=REPO, home="/Users/alice", host="Alices-MacBook-Pro.local", machine="machine-0123abcd")
    assert scrub_text(f"{REPO}/app/perf/data/x.json", **kw) == "app/perf/data/x.json"
    assert scrub_text("/Users/alice/Library/Java", **kw) == "~/Library/Java"
    assert scrub_text("/Users/alice", **kw) == "~"
    assert scrub_text("cache in /home/bob/.gradle and /Users/carol", **kw) == "cache in ~/.gradle and ~"
    assert scrub_text("on ALICES-MACBOOK-PRO", **kw) == "on machine-0123abcd"
    assert scrub_text("/Volumes/External/perf/x", **kw) == "/Volumes/External/perf/x"


def test_scrub_record_leaves_no_host_name_or_home_path_and_keeps_the_input():
    record = _record()
    before = repr(record)
    out = scrub_record(record, repo_root=REPO, home="/Users/alice", host="Alices-MacBook-Pro.local",
                       machine="machine-0123abcd")
    assert repr(record) == before, "the input is not changed"
    text = repr(out)
    assert "/Users/" not in text and "alice" not in text.lower()
    assert out["artifacts"][0]["path"] == "app/perf/data/rounds/r1/startup.perfetto-trace"
    assert out["artifacts"][1]["path"] == "/Volumes/External/perf/data/rounds/r1/gradle.log"
    assert out["checks"][0]["value"] == "~/Library/Java/temurin-21.jdk/Contents/Home"
    assert out["checks"][1] == {"name": "battery", "passed": True, "value": 87, "limit": 50}
    assert out["failure_reason"] == "7: launch failed at app/perf/data/r1 on machine-0123abcd (machine-0123abcd)"
    assert out["session"] == {"index": 1, "size": 5}
