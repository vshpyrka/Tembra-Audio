"""The regression-injection self-test (spec SC-004, FR-019; trace-pulse's inject/revert
ported to this repository).

`inject R#` applies the registry entry's patch with `git apply` (or runs its
generator) to a repository whose only dirt is already-applied injections, and
records the touched paths in `data/injections.json`; `revert R#` reverses one
patch; `revert --all` restores every tracked path from HEAD, removes generated
files and must land on a clean tree. `expected_lookup_factory` gives the detector
its ground truth: a detection at a round whose injections expect that series and
direction is a true positive; one on a series the entry lists as a tolerated side
effect gets no verdict (the injection does move it, the self-test does not score it).
"""

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from typing import Callable, Dict, List, Optional, Set, Tuple

import yaml

from .catalogue import CatalogueError, lookup
from .runner import Runner
from .store import split_series_key

_VALID_DIRECTIONS = ("up", "down")


class InjectError(Exception):
    """A dirty tree, an unknown or already-applied id, or a git failure."""


@dataclass(frozen=True)
class Regression:
    id: str
    title: str
    patch_file: str
    generate: str
    generated_paths: List[str]
    expected_metrics: List[Tuple[str, str]]
    tolerated_side_effects: List[str]
    notes: str


def _validate_series(key: str, r_id: str, field: str = "expected_metrics") -> None:
    _journey, metric = split_series_key(key)
    for platform in ("android", "ios"):
        try:
            lookup(platform, metric)
            return
        except CatalogueError:
            continue
    raise ValueError(f"{r_id}: unknown metric key {key!r} in {field}")


def load_registry(path: str) -> Dict[str, Regression]:
    with open(path) as fh:
        raw = yaml.safe_load(fh) or {}
    registry: Dict[str, Regression] = {}
    for r_id, entry in raw.items():
        expected: List[Tuple[str, str]] = []
        for metric, direction in entry.get("expected_metrics", []):
            _validate_series(metric, r_id)
            if direction not in _VALID_DIRECTIONS:
                raise ValueError(f"{r_id}: invalid direction {direction!r} for {metric!r} (expected one of {_VALID_DIRECTIONS})")
            expected.append((metric, direction))
        tolerated = list(entry.get("tolerated_side_effects", []))
        for metric in tolerated:
            _validate_series(metric, r_id, "tolerated_side_effects")
        registry[r_id] = Regression(
            id=r_id, title=entry.get("title", ""), patch_file=entry.get("patch_file", ""),
            generate=entry.get("generate", ""), generated_paths=list(entry.get("generated_paths", [])),
            expected_metrics=expected, tolerated_side_effects=tolerated,
            notes=entry.get("notes", ""))
    return registry


# -- state -------------------------------------------------------------------

def _state_path(data_dir: str) -> str:
    return os.path.join(data_dir, "injections.json")


def _read_state(data_dir: str) -> List[Dict]:
    path = _state_path(data_dir)
    if not os.path.isfile(path):
        return []
    with open(path) as fh:
        return json.load(fh).get("applied", [])


def _write_state(data_dir: str, applied: List[Dict]) -> None:
    os.makedirs(data_dir, exist_ok=True)
    with open(_state_path(data_dir), "w") as fh:
        json.dump({"applied": applied}, fh, indent=2)
        fh.write("\n")


def applied_injections(data_dir: str) -> List[str]:
    return [entry["id"] for entry in _read_state(data_dir)]


def _owned_paths(applied: List[Dict]) -> Set[str]:
    return {p for entry in applied for p in entry.get("paths", [])}


# -- git ----------------------------------------------------------------------

def _repo_root(cfg) -> str:
    path = os.path.abspath(cfg.root)
    while True:
        if os.path.exists(os.path.join(path, ".git")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            return os.path.abspath(cfg.root)
        path = parent


def _porcelain_paths(text: str) -> Set[str]:
    return {line[3:] for line in text.splitlines() if line.strip()}


_DIFF_GIT_RE = re.compile(r"^diff --git a/(.+) b/(.+)$")
_NEW_FILE_RE = re.compile(r"^new file mode \d+$")


def _patch_paths(patch_path: str) -> Tuple[List[str], List[str]]:
    """(every path a patch touches, the paths it adds)."""
    touched: List[str] = []
    added: List[str] = []
    current: Optional[str] = None
    with open(patch_path) as fh:
        for line in fh:
            m = _DIFF_GIT_RE.match(line.rstrip("\n"))
            if m:
                current = m.group(2)
                touched.append(current)
            elif current and _NEW_FILE_RE.match(line.rstrip("\n")):
                added.append(current)
    return touched, added


def _git_status(runner: Runner, root: str) -> Set[str]:
    res = runner.run(["git", "-C", root, "status", "--porcelain", "--untracked-files=all"], timeout=60)
    return _porcelain_paths(res.stdout)


def inject(cfg, runner: Runner, r_id: str, data_dir: str, registry_path: str) -> None:
    root = _repo_root(cfg)
    applied = _read_state(data_dir)
    if r_id in {e["id"] for e in applied}:
        raise InjectError(f"{r_id} is already applied -- revert it before injecting again")
    registry = load_registry(registry_path)
    if r_id not in registry:
        raise InjectError(f"unknown regression id {r_id!r} (not in {registry_path})")
    stray = _git_status(runner, root) - _owned_paths(applied) - _ignored_state_paths(root, data_dir)
    if stray:
        raise InjectError(f"repository has stray dirty path(s) beyond applied injections, refusing to inject {r_id}: {sorted(stray)}")
    entry = registry[r_id]
    regressions_dir = os.path.dirname(os.path.abspath(registry_path))
    if entry.generate:
        script = os.path.join(regressions_dir, entry.generate)
        proc = subprocess.run([sys.executable, script, root], capture_output=True, text=True)
        if proc.returncode != 0:
            raise InjectError(f"{r_id}'s generator failed: {proc.stderr.strip()}")
        paths = list(entry.generated_paths)
    else:
        patch = os.path.join(regressions_dir, entry.patch_file)
        check = runner.run(["git", "apply", "--check", patch], cwd=root, timeout=60)
        if check.returncode != 0:
            raise InjectError(f"{r_id}'s patch ({entry.patch_file}) does not apply cleanly: {check.stderr.strip()}")
        res = runner.run(["git", "apply", patch], cwd=root, timeout=60)
        if res.returncode != 0:
            raise InjectError(f"git apply failed for {r_id}: {res.stderr.strip()}")
        paths, _added = _patch_paths(patch)
    applied.append({"id": r_id, "paths": paths})
    _write_state(data_dir, applied)


def _ignored_state_paths(root: str, data_dir: str) -> Set[str]:
    """data/ is git-ignored under app/perf; a data_dir elsewhere (tests) shows up in status and is ours."""
    rel = os.path.relpath(os.path.abspath(data_dir), root)
    return {p for p in {rel + "/", rel + "/injections.json"}}


def revert(cfg, runner: Runner, target: str, data_dir: str, registry_path: str) -> None:
    root = _repo_root(cfg)
    applied = _read_state(data_dir)
    registry = load_registry(registry_path)
    regressions_dir = os.path.dirname(os.path.abspath(registry_path))
    if target == "--all":
        generated: List[str] = []
        tracked: List[str] = []
        for entry in applied:
            reg = registry.get(entry["id"])
            if reg is not None and reg.generate:
                generated += reg.generated_paths
            else:
                tracked += entry.get("paths", [])
                if reg is not None and reg.patch_file:
                    _touched, added = _patch_paths(os.path.join(regressions_dir, reg.patch_file))
                    generated += added
        if tracked:
            res = runner.run(["git", "-C", root, "checkout", "HEAD", "--", *sorted(set(tracked))], timeout=60)
            if res.returncode != 0:
                raise InjectError(f"git checkout failed: {res.stderr.strip()}")
        for path in generated:
            full = os.path.join(root, path)
            if os.path.exists(full):
                os.remove(full)
        remaining = _git_status(runner, root) - _ignored_state_paths(root, data_dir)
        remaining = {p for p in remaining if not p.startswith(os.path.relpath(os.path.abspath(data_dir), root))}
        if remaining:
            raise InjectError(f"revert --all did not land on a clean tree, still dirty: {sorted(remaining)}")
        _write_state(data_dir, [])
        return
    if target not in {e["id"] for e in applied}:
        raise InjectError(f"{target} is not currently applied -- nothing to revert")
    if target not in registry:
        raise InjectError(f"unknown regression id {target!r} (not in {registry_path})")
    entry = registry[target]
    if entry.generate:
        for path in entry.generated_paths:
            full = os.path.join(root, path)
            if os.path.exists(full):
                os.remove(full)
    else:
        patch = os.path.join(regressions_dir, entry.patch_file)
        check = runner.run(["git", "apply", "-R", "--check", patch], cwd=root, timeout=60)
        if check.returncode != 0:
            raise InjectError(f"{target}'s patch does not reverse-apply cleanly: {check.stderr.strip()}")
        res = runner.run(["git", "apply", "-R", patch], cwd=root, timeout=60)
        if res.returncode != 0:
            raise InjectError(f"git apply -R failed for {target}: {res.stderr.strip()}")
    _write_state(data_dir, [e for e in applied if e["id"] != target])


def expected_lookup_factory(registry: Dict[str, Regression],
                            applied_at_round: Callable[[int], List[str]]) -> Callable[[int, str, str], Optional[bool]]:
    """True when the round's injections expect (series, direction); None for a round
    without injections or when one of them lists the series as a tolerated side
    effect; False when the round has injections and none of them accounts for it."""
    def lookup_fn(onset_round_id: int, metric: str, direction: str) -> Optional[bool]:
        ids = applied_at_round(onset_round_id)
        if not ids:
            return None
        entries = [registry[r] for r in ids if r in registry]
        if any((metric, direction) in e.expected_metrics for e in entries):
            return True
        if any(metric in e.tolerated_side_effects for e in entries):
            return None
        return False
    return lookup_fn
