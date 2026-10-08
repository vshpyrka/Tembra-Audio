"""The injectable subprocess boundary (`Runner`) and wall clock (`Clock`).

Every device and host interaction in the orchestrators goes through a `Runner`
so the whole pipeline is unit-testable offline against a scripted `FakeRunner`
(tests/conftest.py); `SystemRunner` is the only piece that shells out.
"""

import os
import subprocess
import time
from dataclasses import dataclass
from typing import Dict, List, Optional, Protocol

# Cap for any single Runner.run() call that doesn't set its own timeout: generous
# enough for a cold Gradle build; its job is to stop a wedged adb link or a hung
# xcodebuild from holding a round forever.
DEFAULT_RUN_TIMEOUT_S = 1800


@dataclass(frozen=True)
class Completed:
    """Result of a Runner.run() call (the subset of subprocess.CompletedProcess
    the pipeline uses: text-mode stdout/stderr)."""
    returncode: int
    stdout: str = ""
    stderr: str = ""


class Runner(Protocol):
    """Run `cmd` with an optional environment overlay, timeout and cwd."""

    def run(self, cmd: List[str], env: Optional[Dict[str, str]] = None,
            timeout: Optional[int] = None, cwd: Optional[str] = None) -> Completed:
        ...


class SystemRunner:
    """The real Runner: subprocess.run in text mode. `env` is merged over a copy
    of the current environment so Gradle and xcodebuild keep PATH/HOME."""

    def run(self, cmd: List[str], env: Optional[Dict[str, str]] = None,
            timeout: Optional[int] = None, cwd: Optional[str] = None) -> Completed:
        full_env = {**os.environ, **env} if env is not None else None
        if timeout is None:
            timeout = DEFAULT_RUN_TIMEOUT_S
        try:
            proc = subprocess.run(
                cmd, env=full_env, timeout=timeout, cwd=cwd,
                capture_output=True, text=True,
            )
        except subprocess.TimeoutExpired as exc:
            # A failed Completed instead of an exception: callers handle a nonzero
            # rc on every path; 124 matches the shell `timeout` convention.
            partial = exc.stdout.decode() if isinstance(exc.stdout, bytes) else (exc.stdout or "")
            return Completed(124, partial, f"timed out after {timeout}s: {cmd!r}")
        return Completed(proc.returncode, proc.stdout, proc.stderr)


class Clock(Protocol):
    """Injectable wall-clock boundary; tests supply a recording fake."""

    def sleep(self, seconds: float) -> None:
        ...

    def now(self) -> int:
        ...


class SystemClock:
    """Real wall clock: `time.sleep` and integer epoch seconds."""

    def sleep(self, seconds: float) -> None:
        time.sleep(seconds)

    def now(self) -> int:
        return int(time.time())
