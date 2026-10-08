"""The `perf-history` branch: one record per file, published through a git
worktree and synchronised back; two machines publishing different devices never
conflict; a failed push parks the record locally for `publish` to retry."""

import json
import os
import subprocess

import pytest

from tracepulse.history import History, PublishError
from tracepulse.records import DeviceInfo, JourneyResult, MetricResult, RoundRecord

BRANCH = "perf-history"


def _git(*args, cwd):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout


def _repo(tmp_path, name, origin):
    """A clone of `origin` with a main-branch commit, as one machine's checkout."""
    path = tmp_path / name
    _git("clone", "-q", str(origin), str(path), cwd=tmp_path)
    _git("config", "user.email", "t@example.com", cwd=path)
    _git("config", "user.name", "tester", cwd=path)
    return path


def _record(device_id, platform, round_id, label="007"):
    return RoundRecord(
        round_id=round_id,
        device=DeviceInfo(device_id=device_id, platform=platform, kind="device", model="M", reference=True),
        label=label, build={"commit": "c", "dirty": False}, fingerprint={"os_version": "1"},
        machine={"hostname": "h", "tool_version": "0.2.0"}, checks=[], status="succeeded",
        journeys=[JourneyResult(journey="startup", iterations=2, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=[1.0, 2.0], source="macrobenchmark")})],
        started_at="2026-10-08T10:00:00Z", finished_at="2026-10-08T10:01:00Z",
    )


@pytest.fixture
def origin(tmp_path):
    bare = tmp_path / "origin.git"
    _git("init", "-q", "--bare", "-b", "main", str(bare), cwd=tmp_path)
    seed = tmp_path / "seed"
    _git("clone", "-q", str(bare), str(seed), cwd=tmp_path)
    _git("config", "user.email", "t@example.com", cwd=seed)
    _git("config", "user.name", "tester", cwd=seed)
    (seed / "README.md").write_text("seed\n")
    _git("add", ".", cwd=seed)
    _git("commit", "-q", "-m", "init", cwd=seed)
    _git("push", "-q", "origin", "main", cwd=seed)
    return bare


def _history(repo, data_dir):
    return History(repo_root=str(repo), worktree=".history", branch=BRANCH, remote="origin",
                   data_dir=str(data_dir))


def test_publish_creates_orphan_branch_commits_and_pushes(tmp_path, origin):
    repo = _repo(tmp_path, "mac1", origin)
    h = _history(repo, tmp_path / "data1")
    rec = _record("PIXEL", "android", "20261008T100000Z_007")
    path = h.publish(rec)
    assert path == "records/PIXEL/20261008T100000Z_007.json"
    wt = repo / ".history"
    assert (wt / path).is_file()
    assert json.loads((wt / path).read_text())["round_id"] == rec.round_id
    log = _git("log", "--format=%s", BRANCH, cwd=wt)
    assert log.splitlines()[0] == "perf: PIXEL 20261008T100000Z_007"
    # orphan: the branch history does not contain main's commit
    parents = _git("rev-list", "--max-parents=0", BRANCH, cwd=wt).split()
    main_root = _git("rev-list", "--max-parents=0", "main", cwd=repo).split()
    assert parents and parents != main_root
    # pushed
    remote_heads = _git("ls-remote", "--heads", str(origin), BRANCH, cwd=repo)
    assert BRANCH in remote_heads
    # main's working tree is untouched
    assert not (repo / "records").exists()


def test_sync_pulls_records_published_from_another_machine(tmp_path, origin):
    mac1 = _repo(tmp_path, "mac1", origin)
    mac2 = _repo(tmp_path, "mac2", origin)
    h1 = _history(mac1, tmp_path / "d1")
    h2 = _history(mac2, tmp_path / "d2")
    h1.publish(_record("PIXEL", "android", "20261008T100000Z_007"))
    records = h2.sync()
    assert [r.round_id for r in records] == ["20261008T100000Z_007"]
    h2.publish(_record("IPHONE", "ios", "20261008T110000Z_007"))
    records = h1.sync()
    assert sorted(r.device.device_id for r in records) == ["IPHONE", "PIXEL"]


def test_two_machines_publishing_different_devices_do_not_conflict(tmp_path, origin):
    mac1 = _repo(tmp_path, "mac1", origin)
    mac2 = _repo(tmp_path, "mac2", origin)
    h1 = _history(mac1, tmp_path / "d1")
    h2 = _history(mac2, tmp_path / "d2")
    h1.publish(_record("PIXEL", "android", "20261008T100000Z_007"))
    h2.sync()
    # both publish without syncing in between: the second push is rejected,
    # rebased on the distinct file and pushed again
    h1.publish(_record("PIXEL", "android", "20261008T120000Z_007"))
    h2.publish(_record("IPHONE", "ios", "20261008T110000Z_007"))
    for h in (h1, h2):
        ids = sorted(r.round_id for r in h.sync())
        assert ids == ["20261008T100000Z_007", "20261008T110000Z_007", "20261008T120000Z_007"]


def test_failed_push_parks_the_record_and_publish_pending_retries(tmp_path, origin):
    repo = _repo(tmp_path, "mac1", origin)
    data = tmp_path / "data"
    h = _history(repo, data)
    h.publish(_record("PIXEL", "android", "20261008T100000Z_007"))
    _git("remote", "set-url", "origin", str(tmp_path / "nowhere.git"), cwd=repo)
    rec = _record("PIXEL", "android", "20261008T130000Z_007")
    with pytest.raises(PublishError):
        h.publish(rec)
    parked = data / "unpublished" / "PIXEL" / "20261008T130000Z_007.json"
    assert parked.is_file()
    assert [r.round_id for r in h.pending()] == ["20261008T130000Z_007"]
    _git("remote", "set-url", "origin", str(origin), cwd=repo)
    published = h.publish_pending()
    assert published == ["records/PIXEL/20261008T130000Z_007.json"]
    assert not parked.exists() and h.pending() == []
    assert sorted(r.round_id for r in h.sync()) == ["20261008T100000Z_007", "20261008T130000Z_007"]


def test_sync_on_a_repository_without_the_branch_returns_nothing(tmp_path, origin):
    repo = _repo(tmp_path, "mac1", origin)
    h = _history(repo, tmp_path / "data")
    assert h.sync() == []


def test_publish_refuses_to_overwrite_an_existing_record(tmp_path, origin):
    repo = _repo(tmp_path, "mac1", origin)
    h = _history(repo, tmp_path / "data")
    rec = _record("PIXEL", "android", "20261008T100000Z_007")
    h.publish(rec)
    with pytest.raises(PublishError, match="already"):
        h.publish(rec)
