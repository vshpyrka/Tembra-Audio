"""The regression-injection self-test (spec SC-004, FR-019): `inject R#` applies a
registry patch (or generates R4's asset) to a clean repository and records it in
data/injections.json; `revert R#` reverses it; `revert --all` lands on a clean
tree; the registry validates its metric keys; the shipped patches apply to this
repository."""

import json
import os
import subprocess

import pytest

from tracepulse.config import PERF_DIR
from tracepulse.inject import (
    InjectError, applied_injections, expected_lookup_factory, inject, load_registry, revert,
)
from tracepulse.runner import SystemRunner

REAL_REGISTRY = os.path.join(PERF_DIR, "regressions", "registry.yaml")
REPO = os.path.dirname(os.path.dirname(PERF_DIR))


def _git(*args, cwd):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout


@pytest.fixture
def repo(tmp_path):
    """A tiny repository with a file the fake registry's patch changes."""
    root = tmp_path / "repo"
    root.mkdir()
    _git("init", "-q", "-b", "main", cwd=root)
    _git("config", "user.email", "t@example.com", cwd=root)
    _git("config", "user.name", "t", cwd=root)
    (root / "app").mkdir()
    (root / "app" / "Main.kt").write_text("fun main() {\n    println(\"hi\")\n}\n")
    _git("add", ".", cwd=root)
    _git("commit", "-q", "-m", "init", cwd=root)
    regressions = tmp_path / "regressions"
    regressions.mkdir()
    (regressions / "r_test.patch").write_text(
        "diff --git a/app/Main.kt b/app/Main.kt\n--- a/app/Main.kt\n+++ b/app/Main.kt\n@@ -1,3 +1,4 @@\n fun main() {\n+    Thread.sleep(300)\n     println(\"hi\")\n }\n")
    (regressions / "gen_x.py").write_text(
        "import os, sys\np = os.path.join(sys.argv[1], 'app', 'ballast.bin')\nopen(p, 'wb').write(b'\\0' * 10)\nprint(p)\n")
    (regressions / "registry.yaml").write_text(
        "R1:\n  title: t\n  patch_file: r_test.patch\n  expected_metrics:\n    - [startup/startup.ttid_ms, up]\n"
        "R4:\n  title: size\n  generate: gen_x.py\n  generated_paths: [app/ballast.bin]\n  expected_metrics:\n    - [app.size_bytes, up]\n")
    return root, str(regressions / "registry.yaml")


class Cfg:
    def __init__(self, root):
        self.root = os.path.join(str(root), "app")


def test_registry_validates_metric_keys_and_directions(tmp_path):
    bad = tmp_path / "registry.yaml"
    bad.write_text("R9:\n  title: x\n  patch_file: x.patch\n  expected_metrics:\n    - [startup/made.up, up]\n")
    with pytest.raises(ValueError, match="made.up"):
        load_registry(str(bad))
    bad.write_text("R9:\n  title: x\n  patch_file: x.patch\n  expected_metrics:\n    - [startup/startup.ttid_ms, sideways]\n")
    with pytest.raises(ValueError, match="sideways"):
        load_registry(str(bad))


def test_shipped_registry_has_the_four_regressions_and_valid_keys():
    registry = load_registry(REAL_REGISTRY)
    assert sorted(registry) == ["R1", "R2", "R3", "R4"]
    assert registry["R1"].patch_file == "r1-startup.patch" and registry["R4"].generate == "gen_ballast.py"
    for r in ("R1", "R2", "R3"):
        assert os.path.isfile(os.path.join(PERF_DIR, "regressions", registry[r].patch_file))


def test_shipped_patches_apply_to_this_repository():
    for name in ("r1-startup.patch", "r2-frames.patch", "r3-memory.patch"):
        proc = subprocess.run(["git", "apply", "--check", os.path.join(PERF_DIR, "regressions", name)],
                              cwd=REPO, capture_output=True, text=True)
        assert proc.returncode == 0, f"{name}: {proc.stderr}"


def test_inject_and_revert_one_patch(repo):
    root, registry = repo
    data = str(root.parent / "data")
    inject(Cfg(root), SystemRunner(), "R1", data, registry)
    assert "Thread.sleep(300)" in (root / "app" / "Main.kt").read_text()
    assert applied_injections(data) == ["R1"]
    with pytest.raises(InjectError, match="already applied"):
        inject(Cfg(root), SystemRunner(), "R1", data, registry)
    revert(Cfg(root), SystemRunner(), "R1", data, registry)
    assert "Thread.sleep" not in (root / "app" / "Main.kt").read_text()
    assert applied_injections(data) == []
    assert _git("status", "--porcelain", cwd=root) == ""


def test_generated_regression_and_revert_all(repo):
    root, registry = repo
    data = str(root.parent / "data")
    inject(Cfg(root), SystemRunner(), "R4", data, registry)
    assert (root / "app" / "ballast.bin").is_file()
    inject(Cfg(root), SystemRunner(), "R1", data, registry)
    assert applied_injections(data) == ["R4", "R1"]
    revert(Cfg(root), SystemRunner(), "--all", data, registry)
    assert not (root / "app" / "ballast.bin").exists()
    assert _git("status", "--porcelain", cwd=root) == "" and applied_injections(data) == []


def test_inject_refuses_a_dirty_tree_and_unknown_ids(repo):
    root, registry = repo
    data = str(root.parent / "data")
    (root / "app" / "Stray.kt").write_text("x")
    with pytest.raises(InjectError, match="stray"):
        inject(Cfg(root), SystemRunner(), "R1", data, registry)
    (root / "app" / "Stray.kt").unlink()
    with pytest.raises(InjectError, match="unknown regression"):
        inject(Cfg(root), SystemRunner(), "R7", data, registry)


def test_expected_lookup_uses_the_registry_and_the_round_injections():
    registry = load_registry(REAL_REGISTRY)
    lookup = expected_lookup_factory(registry, lambda round_id: ["R1"] if round_id >= 5 else [])
    assert lookup(5, "startup/startup.ttid_ms", "up") is True
    assert lookup(5, "tuner/frames.p90_ms", "up") is False
    assert lookup(2, "startup/startup.ttid_ms", "up") is None
    # a tolerated side effect of an applied injection is neither a hit nor a false alarm
    assert lookup(5, "tuner/cpu.instructions_k", "up") is None
    assert lookup(5, "app.size_bytes", "up") is None


def test_registry_rejects_an_unknown_tolerated_side_effect(tmp_path):
    bad = tmp_path / "registry.yaml"
    bad.write_text("R9:\n  patch_file: r9.patch\n  expected_metrics: [[startup/startup.ttid_ms, up]]\n"
                   "  tolerated_side_effects: [tuner/no.such_metric]\n")
    with pytest.raises(ValueError, match="tolerated_side_effects"):
        load_registry(str(bad))
