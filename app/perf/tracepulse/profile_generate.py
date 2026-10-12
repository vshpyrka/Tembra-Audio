"""`tracepulse profile generate` (feature 008, specs/008-baseline-profiles/contracts/cli.md, D7).

The pipeline's device protocol (`doctor`) plus the API 33+ eligibility of non-rooted
profile collection, then the plugin's `:androidApp:generateBaselineProfile` task from the
Gradle root with ANDROID_SERIAL naming the device, then the host-lane coverage test; the
two committed profile files are backed up first and restored on any failure. Nothing is
written to the history, the records or the report; the Gradle logs go under
`data/profile-generate/<id>/`.
"""

import os
import re
import shutil
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

from .config import Config
from .device_state import measurement_state
from .devices import Device, DeviceError, attached, register, resolve
from .orchestrate_android import BUILD_TIMEOUT_S, _clear_media_dir, _shell, doctor
from .records import make_round_id
from .runner import Clock, Runner

EXIT_OK = 0
EXIT_REFUSED = 2
EXIT_FAILED = 3
EXIT_USAGE = 4

MIN_SDK_FOR_PROFILES = 33
PROFILE_DIR = os.path.join("androidApp", "src", "main", "generated", "baselineProfiles")
PROFILE_FILES = ("baseline-prof.txt", "startup-prof.txt")
RULES_FILE = os.path.join("androidApp", "baseline-profile-coverage.txt")
GENERATE_TASK = ":androidApp:generateBaselineProfile"
COVERAGE_TASK = ":androidApp:testDebugUnitTest"
COVERAGE_FILTER = "*BaselineProfileCoverageTest"
COMMIT_REMINDER = "commit when covered code changed; noise-only differences need not be committed"

Payload = Dict[str, Any]


# ============================================================
# the committed files: backup and restore
# ============================================================

@dataclass(frozen=True)
class ProfileBackup:
    """Copies of the committed profile files taken before generation; an absent file is recorded as absent
    so a failed generation leaves the tree exactly as it found it."""
    profile_dir: str
    backup_dir: str
    present: Tuple[str, ...]

    @classmethod
    def take(cls, profile_dir: str) -> "ProfileBackup":
        backup_dir = tempfile.mkdtemp(prefix="tracepulse-profile-")
        present = []
        for name in PROFILE_FILES:
            source = os.path.join(profile_dir, name)
            if os.path.isfile(source):
                shutil.copy2(source, os.path.join(backup_dir, name))
                present.append(name)
        return cls(profile_dir=profile_dir, backup_dir=backup_dir, present=tuple(present))

    def restore(self) -> None:
        for name in PROFILE_FILES:
            target = os.path.join(self.profile_dir, name)
            if name in self.present:
                os.makedirs(self.profile_dir, exist_ok=True)
                shutil.copy2(os.path.join(self.backup_dir, name), target)
            elif os.path.exists(target):
                os.remove(target)
        self.discard()

    def discard(self) -> None:
        shutil.rmtree(self.backup_dir, ignore_errors=True)


# ============================================================
# the summary: rule counts and the covered labels
# ============================================================

_FLAGS = re.compile(r"^[HSP]+")


def rule_lines(path: str) -> List[str]:
    if not os.path.isfile(path):
        return []
    with open(path) as fh:
        return [line.strip() for line in fh if line.strip()]


def coverage_rules(path: str) -> List[Tuple[str, str]]:
    """`<label> <descriptor>` pairs of the coverage rule file; blank lines and `#` comments skipped."""
    rules = []
    for line in rule_lines(path):
        if line.startswith("#"):
            continue
        label, descriptor = line.split(None, 1)
        rules.append((label, descriptor.strip()))
    return rules


def covered_labels(rules: List[Tuple[str, str]], profile: List[str]) -> List[str]:
    """The labels whose descriptor a profile line names, as `BaselineProfileCoverage` decides it."""
    entries = [_FLAGS.sub("", line) for line in profile]
    return [label for label, descriptor in rules
            if any(entry == descriptor or entry.startswith(descriptor + "->") for entry in entries)]


# ============================================================
# the steps
# ============================================================

def _select_device(args, cfg: Config, runner: Runner, devicectl_output: str) -> Device:
    devices = attached(runner, adb=cfg.android.adb, devicectl_output=devicectl_output)
    device = resolve(cfg, args.device, devices)
    if register(cfg, [device]):
        print(f"registered new device {device.device_id} ({device.model}) in {cfg.path}")
    device = resolve(cfg, device.device_id, devices)
    if device.platform != "android":
        raise DeviceError(f"profile generation is Android-only; {device.model} ({device.device_id}) is {device.platform}")
    return device


def _eligibility(cfg: Config, device: Device, runner: Runner) -> Tuple[bool, str]:
    raw = _shell(cfg, device, runner, "getprop", "ro.build.version.sdk").stdout.strip()
    try:
        sdk = int(raw)
    except ValueError:
        return False, f"could not read ro.build.version.sdk ({raw!r})"
    if sdk < MIN_SDK_FOR_PROFILES:
        return False, (f"device API {sdk} cannot generate profiles without root; "
                       f"needs API {MIN_SDK_FOR_PROFILES}+")
    return True, f"API {sdk}"


def _gradle(cfg: Config, runner: Runner, cmd: List[str], env: Optional[Dict[str, str]], log_path: str) -> int:
    res = runner.run(cmd, cwd=cfg.root, env=env, timeout=BUILD_TIMEOUT_S)
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    with open(log_path, "w") as fh:
        fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}")
        if res.stderr:
            fh.write("\n--- stderr ---\n" + res.stderr)
    return res.returncode


def main(args, cfg: Config, paths, runner: Runner, clock: Clock) -> Tuple[int, Payload]:
    """Airplane mode and the refresh-rate pin are enforced for the whole command and restored on every exit
    path, exactly as a round does; the screen is kept on."""
    device = _select_device(args, cfg, runner, os.path.join(paths.data_dir, "devicectl.json"))
    print(f"profile generate on {device.model} ({device.device_id})")
    with measurement_state(cfg, device, runner, clock):
        return _generate(args, cfg, paths, runner, device)


def _generate(args, cfg: Config, paths, runner: Runner, device: Device) -> Tuple[int, Payload]:
    payload: Payload = {"device": device.device_id}
    checks = doctor(cfg, device, runner)
    for check in checks:
        print(f"  [{'ok' if check.ok else 'FAIL'}] {check.name} — {check.detail}")
    failing = [c for c in checks if not c.ok]
    payload["checks"] = [c.as_record() for c in checks]
    if failing:
        print("refused: " + "; ".join(f"{c.name}: {c.detail}" for c in failing))
        return EXIT_REFUSED, payload
    eligible, detail = _eligibility(cfg, device, runner)
    print(f"  [{'ok' if eligible else 'FAIL'}] profile_eligibility — {detail}")
    if not eligible:
        print(f"refused: {detail}")
        return EXIT_REFUSED, payload

    run_id = make_round_id(datetime.now(timezone.utc), device.device_id)
    log_dir = os.path.join(paths.data_dir, "profile-generate", run_id)
    profile_dir = os.path.join(cfg.root, PROFILE_DIR)
    backup = ProfileBackup.take(profile_dir)
    java_home = next((c.value for c in checks if c.name == "java_home"), None)
    env = {"ANDROID_SERIAL": device.device_id}
    if java_home:
        env["JAVA_HOME"] = java_home

    # Stale outputs of an earlier run must not be collected into the merged profile (a round clears per class).
    _clear_media_dir(cfg, device, runner)
    generate_cmd = ["./gradlew", "--console=plain", "-Ptembra.perf=true", GENERATE_TASK]
    generate_log = os.path.join(log_dir, "generate.log")
    print(f"running {' '.join(generate_cmd)} (ANDROID_SERIAL={device.device_id}), log in {generate_log}")
    if _gradle(cfg, runner, generate_cmd, env, generate_log) != 0:
        backup.restore()
        print(f"failed: {GENERATE_TASK} did not complete; committed profile files restored; see {generate_log}")
        payload["failed"] = GENERATE_TASK
        return EXIT_FAILED, payload

    coverage_cmd = ["./gradlew", "--console=plain", COVERAGE_TASK, "--tests", COVERAGE_FILTER]
    coverage_log = os.path.join(log_dir, "coverage.log")
    print(f"running {' '.join(coverage_cmd)}, log in {coverage_log}")
    if _gradle(cfg, runner, coverage_cmd, {"JAVA_HOME": java_home} if java_home else None, coverage_log) != 0:
        backup.restore()
        print(f"failed: {COVERAGE_FILTER} did not pass on the generated profile; committed profile files restored; "
              f"see {coverage_log}")
        payload["failed"] = COVERAGE_FILTER
        return EXIT_FAILED, payload
    backup.discard()

    baseline = rule_lines(os.path.join(profile_dir, PROFILE_FILES[0]))
    startup = rule_lines(os.path.join(profile_dir, PROFILE_FILES[1]))
    covered = covered_labels(coverage_rules(os.path.join(cfg.root, RULES_FILE)), baseline)
    for name, lines in ((PROFILE_FILES[0], baseline), (PROFILE_FILES[1], startup)):
        path = os.path.join(profile_dir, name)
        size = os.path.getsize(path) if os.path.isfile(path) else 0
        print(f"  {name}: {len(lines)} rules, {size} bytes")
    print(f"  covered: {', '.join(covered) or '—'}")
    print(f"done: {COMMIT_REMINDER}")
    payload.update({"baseline_rules": len(baseline), "startup_rules": len(startup), "covered": covered,
                    "profile_dir": profile_dir, "logs": log_dir})
    return EXIT_OK, payload
