"""Records carry no host name and no home-directory path.

Round records are pushed to `perf-history`, which is public. `scrub_record` cleans a record before it is
written anywhere: a path under the repository becomes repository-relative, any other home-directory path
becomes `~/...`, and the host name (and its first DNS label) becomes `machine_id()`, a stable hash of the
machine's hardware id that names no one."""

import functools
import hashlib
import os
import re
import subprocess
import sys
from typing import Any, Dict, Optional

HOME_PATH = re.compile(r"/(?:Users|home)/[^/\s\"']+(/|(?=[\s\"']|$))")


def shorten_home(text: str) -> str:
    """`/Users/<name>/x` and `/home/<name>/x` become `~/x`; a bare `/Users/<name>` becomes `~`."""
    return HOME_PATH.sub(lambda m: "~/" if m.group(1) else "~", text)


def _hardware_id() -> Optional[str]:
    """The Mac's IOPlatformUUID or Linux's /etc/machine-id; None when neither can be read."""
    try:
        if sys.platform == "darwin":
            out = subprocess.run(["ioreg", "-rd1", "-c", "IOPlatformExpertDevice"],
                                 capture_output=True, text=True, timeout=10).stdout
            found = re.search(r'"IOPlatformUUID"\s*=\s*"([^"]+)"', out)
            return found.group(1) if found else None
        with open("/etc/machine-id") as fh:
            return fh.read().strip() or None
    except (OSError, subprocess.SubprocessError):
        return None


@functools.lru_cache(maxsize=None)
def machine_id() -> str:
    """`machine-` and 8 hex digits of a hash of the hardware id: the same on every round of one machine."""
    hardware = _hardware_id()
    if not hardware:
        return "machine-unknown"
    return "machine-" + hashlib.sha256(f"tracepulse:{hardware}".encode()).hexdigest()[:8]


def scrub_text(text: str, repo_root: str, home: str, host: str, machine: str) -> str:
    """One string with the repository prefix removed, home paths shortened and the host name replaced."""
    if repo_root:
        text = text.replace(repo_root.rstrip("/") + "/", "")
    for term in sorted({host, host.split(".")[0]} - {""}, key=len, reverse=True):
        text = re.sub(re.escape(term), machine, text, flags=re.IGNORECASE)
    if home and home != "/":
        home = home.rstrip("/")
        text = re.sub(re.escape(home) + r"(?=/|$|[\s\"'])", "~", text)
    return shorten_home(text)


def scrub_record(data: Dict[str, Any], repo_root: str, home: str, host: str, machine: str) -> Dict[str, Any]:
    """A cleaned copy of a record dict: every string value goes through `scrub_text`; the input is unchanged."""

    def clean(value: Any) -> Any:
        if isinstance(value, str):
            return scrub_text(value, repo_root, home, host, machine)
        if isinstance(value, list):
            return [clean(v) for v in value]
        if isinstance(value, dict):
            return {k: clean(v) for k, v in value.items()}
        return value

    return clean(data)


def default_scrub(data: Dict[str, Any], repo_root: str) -> Dict[str, Any]:
    """`scrub_record` with this machine's home, host name and `machine_id()`."""
    import socket
    return scrub_record(data, repo_root, os.path.expanduser("~"), socket.gethostname(), machine_id())
