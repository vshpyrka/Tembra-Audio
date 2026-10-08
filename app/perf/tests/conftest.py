# Pytest configuration - currently minimal, extended by later tasks

"""Shared fixtures/helpers for tracepulse's test suite.

`Completed`/`FakeRunner`/`FakeClock` (Task 9) let `orchestrate.py`'s doctor()
and run_round() be driven fully offline: FakeRunner scripts a deterministic
command sequence in place of real subprocess/adb/gradle calls, and FakeClock
records sleep() durations instead of actually blocking. Both live here (not
in test_doctor.py/test_round.py) per the task brief: "tests use them from
conftest.py" -- pytest auto-injects `clock` as a fixture, and test modules in
this directory import FakeRunner/Completed directly (pytest's default
"prepend" import mode puts this directory on sys.path once conftest.py is
collected, so `from conftest import ...` resolves).
"""

from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional, Sequence, Tuple, Union

import pytest


@dataclass(frozen=True)
class Completed:
    """Result of a Runner.run() call -- mirrors the subset of
    subprocess.CompletedProcess's fields tracepulse actually uses (text-mode
    stdout/stderr)."""
    returncode: int
    stdout: str = ""
    stderr: str = ""


# A matcher is either a predicate over the argv list, or a prefix of it
# (matched via `cmd[:len(prefix)] == list(prefix)` -- an exact-length prefix
# degenerates to an exact-equality check, which is how most call sites below
# use it).
Matcher = Union[Callable[[List[str]], bool], Sequence[str]]


def _matches(matcher: Matcher, cmd: List[str]) -> bool:
    if callable(matcher):
        return bool(matcher(cmd))
    prefix = list(matcher)
    return cmd[: len(prefix)] == prefix


class FakeRunner:
    """Scripted stand-in for orchestrate.Runner.

    `script` is consumed strictly in order: each `run()` call pops the next
    (matcher, Completed) pair and raises AssertionError -- naming the actual
    command -- if the matcher rejects it, or if the script is already
    exhausted (more calls than scripted). Every call is recorded in `.calls`
    (as a dict of cmd/env/timeout/cwd) regardless of match outcome, so
    sequence-asserting tests can inspect the full history afterward instead
    of relying solely on matchers.
    """

    def __init__(self, script: List[Tuple[Matcher, Completed]]):
        self.script: List[Tuple[Matcher, Completed]] = list(script)
        self.calls: List[Dict] = []

    def run(self, cmd: List[str], env: Optional[Dict[str, str]] = None,
            timeout: Optional[int] = None, cwd: Optional[str] = None) -> Completed:
        self.calls.append({"cmd": cmd, "env": env, "timeout": timeout, "cwd": cwd})
        if not self.script:
            raise AssertionError(
                f"FakeRunner: unscripted command (script already exhausted): {cmd!r}"
            )
        matcher, completed = self.script.pop(0)
        if not _matches(matcher, cmd):
            raise AssertionError(
                "FakeRunner: command did not match the next scripted entry.\n"
                f"  expected (matcher): {matcher!r}\n"
                f"  actual command:     {cmd!r}"
            )
        return completed

    def assert_exhausted(self) -> None:
        """Fail loudly if scripted commands remain unconsumed -- catches a
        round/doctor path that stopped short of the full expected sequence."""
        assert not self.script, (
            f"FakeRunner: {len(self.script)} scripted command(s) never ran: {self.script!r}"
        )


class FakeClock:
    """Recording fake for orchestrate.Clock: sleep() logs the requested
    duration instead of blocking; now() hands out a fake epoch that only
    advances via sleep() (never on its own), so repeated now() reads within
    one round are stable unless the code under test actually slept."""

    def __init__(self, start: int = 1_700_000_000):
        self._now = start
        self.sleeps: List[float] = []

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self._now += int(seconds)

    def now(self) -> int:
        return self._now


@pytest.fixture
def clock() -> FakeClock:
    return FakeClock()


class TableRunner:
    """A lenient stand-in for the Runner: every call is answered by the first rule
    whose matcher accepts it (rules are reusable, so polling loops need one rule),
    recorded in `.calls`, and an unmatched command raises. A rule's response may
    be a `Completed` or a callable `(cmd) -> Completed`."""

    def __init__(self, rules: List[Tuple[Matcher, Union[Completed, Callable[[List[str]], Completed]]]]):
        self.rules = list(rules)
        self.calls: List[Dict] = []

    def run(self, cmd: List[str], env: Optional[Dict[str, str]] = None,
            timeout: Optional[int] = None, cwd: Optional[str] = None) -> Completed:
        self.calls.append({"cmd": cmd, "env": env, "timeout": timeout, "cwd": cwd})
        for matcher, response in self.rules:
            if _matches(matcher, cmd):
                return response(cmd) if callable(response) else response
        raise AssertionError(f"TableRunner: no rule for command {cmd!r}")

    def commands(self) -> List[List[str]]:
        return [c["cmd"] for c in self.calls]

    def index_of(self, *needles: str) -> int:
        """Position of the first call whose argv contains every needle, or -1."""
        for i, c in enumerate(self.calls):
            joined = " ".join(c["cmd"])
            if all(n in joined for n in needles):
                return i
        return -1

    def count(self, *needles: str) -> int:
        return sum(1 for c in self.calls if all(n in " ".join(c["cmd"]) for n in needles))
