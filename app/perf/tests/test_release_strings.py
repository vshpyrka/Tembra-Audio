"""SC-007: the release binaries carry no catalogue signpost name. Builds both
platforms, so it runs only with TRACEPULSE_FULL=1 (the `device` lane)."""

import os
import subprocess

import pytest

TOOL = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools", "check-release-strings.sh")


@pytest.mark.device
@pytest.mark.skipif(os.environ.get("TRACEPULSE_FULL") != "1", reason="builds the release app on both platforms; set TRACEPULSE_FULL=1")
def test_release_binaries_carry_no_catalogue_name():
    proc = subprocess.run([TOOL], capture_output=True, text=True, timeout=3600)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "OK: no catalogue signpost name" in proc.stdout


def test_tool_is_executable_and_reads_the_catalogue():
    assert os.access(TOOL, os.X_OK)
    with open(TOOL) as fh:
        text = fh.read()
    assert "perf-catalogue.txt" in text and "strings -a" in text
