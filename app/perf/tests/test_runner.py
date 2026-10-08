"""SystemRunner timeout behavior (Phase 3 parked housekeeping).

The rest of the suite never exercises SystemRunner (no real subprocesses,
per the task ground rules); these tests cover exactly the two timeout
gaps: hung commands must come back as a failed Completed instead of an
uncaught TimeoutExpired, and calls that pass no timeout must still get
the default cap instead of hanging forever on a wedged adb link.
"""
import subprocess

from tracepulse.runner import DEFAULT_RUN_TIMEOUT_S, Completed, SystemRunner


def test_timeout_returns_failed_completed_not_exception():
    res = SystemRunner().run(["sleep", "5"], timeout=1)
    assert isinstance(res, Completed)
    assert res.returncode == 124   # shell `timeout` convention
    assert "timed out" in res.stderr


def test_default_timeout_applied_when_none_given(monkeypatch):
    seen = {}

    def fake_run(cmd, **kwargs):
        seen["timeout"] = kwargs.get("timeout")
        return subprocess.CompletedProcess(cmd, 0, stdout="", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)
    SystemRunner().run(["true"])
    assert seen["timeout"] == DEFAULT_RUN_TIMEOUT_S
