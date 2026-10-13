"""`tracepulse profile verify` (feature 008, specs/008-baseline-profiles/contracts/cli.md, D5).

Does an installed build carry its baseline profile, does the device recognise it at first
launch, and is the app compiled from it after the optimisation pass? The APK is inspected
(`assets/dexopt/baseline.prof` and its share of the APK), installed with `adb install -r`,
launched once, asked through the profile installer's INSTALL_PROFILE broadcast (result 1
installed, 2 already installed, 6 no profile in the APK), compiled with the device's
background pass (`cmd package compile -r bg-dexopt`), and read back through `dumpsys package
dexopt` (expected `speed-profile`). Not a measurement: only the awake, unlocked and adb checks
run. Exit 0 only when every step passed; 3 names the first failing step.
"""

import os
import re
from typing import Any, Dict, List, Optional, Tuple

from .config import Config
from .devices import Device, DeviceError, attached, register, resolve
from .orchestrate_android import (
    BUILD_TIMEOUT_S, _adb, _check_adb_device, _check_awake, _check_unlocked, _shell, perf_property,
)
from .runner import Clock, Runner

EXIT_OK = 0
EXIT_REFUSED = 2
EXIT_FAILED = 3
EXIT_USAGE = 4

PROFILE_ASSET = "assets/dexopt/baseline.prof"
INSTALL_PROFILE_ACTION = "androidx.profileinstaller.action.INSTALL_PROFILE"
RECEIVER = "androidx.profileinstaller.ProfileInstallReceiver"
FIRST_LAUNCH_WAIT_S = 5
EXPECTED_STATUS = "speed-profile"
# Result codes of ProfileInstallReceiver (androidx.profileinstaller.ProfileInstaller.RESULT_*).
BROADCAST_RESULTS = {
    0: "RESULT_NOT_WRITTEN (profile verifier not run yet)", 1: "installed", 2: "already installed",
    3: "unsupported ART version", 4: "baseline profile not found in the APK", 5: "ART profile unavailable",
    6: "no profile in the APK", 7: "I/O error", 8: "parse error", 9: "meta file unavailable",
    10: "desired version mismatch", 11: "baseline profile not found", 12: "install skipped (benchmark operation)",
}
_RESULT_RE = re.compile(r"result=(\d+)")
_LISTING_RE = re.compile(r"^\s*(\d+)\s+\S+\s+\S+\s+(\S.*?)\s*$")
_STATUS_RE = re.compile(r"status=([A-Za-z0-9_-]+)")

Payload = Dict[str, Any]


class StepFailed(Exception):
    """A verification step did not pass; the message names the step and its finding."""


# ============================================================
# the APK
# ============================================================

def profile_asset_size(listing: str) -> Optional[int]:
    """The byte size of assets/dexopt/baseline.prof in an `unzip -l` listing, or None when absent."""
    for line in listing.splitlines():
        m = _LISTING_RE.match(line)
        if m and m.group(2) == PROFILE_ASSET:
            return int(m.group(1))
    return None


def inspect_apk(runner: Runner, apk: str) -> Tuple[int, int, float]:
    listing = runner.run(["unzip", "-l", apk], timeout=120)
    if listing.returncode != 0:
        raise StepFailed(f"present: `unzip -l {apk}` failed (rc={listing.returncode})")
    size = profile_asset_size(listing.stdout)
    if size is None:
        raise StepFailed(f"present: {PROFILE_ASSET} is not in {apk}")
    apk_bytes = os.path.getsize(apk)
    share = round(size / apk_bytes * 100, 1) if apk_bytes else 0.0
    return size, apk_bytes, share


def _build(cfg: Config, runner: Runner, build_type: str, java_home: Optional[str]) -> Tuple[str, bool]:
    """Build `assemble<BuildType>` through the pipeline's Gradle step. The perf build is debug-signed and
    installable; a release build without a signing config comes out as `-unsigned.apk` and can only be inspected.
    Returns the APK path and whether it is installable."""
    suffix = build_type[:1].upper() + build_type[1:]
    cmd = ["./gradlew", "--console=plain"]
    if build_type == "perf":
        cmd.append(perf_property(build_type))
    cmd.append(f":androidApp:assemble{suffix}")
    env = {"JAVA_HOME": java_home} if java_home else None
    res = runner.run(cmd, cwd=cfg.root, env=env, timeout=BUILD_TIMEOUT_S)
    if res.returncode != 0:
        raise StepFailed(f"build: {' '.join(cmd)} failed (rc={res.returncode})")
    # Not `orchestrate_android.apk_paths`: there `release` names the round's uninstrumented perf-type baseline;
    # here it is the real release build type.
    out_dir = os.path.join(cfg.root, "androidApp", "build", "outputs", "apk", build_type)
    signed = os.path.join(out_dir, f"androidApp-{build_type}.apk")
    if os.path.isfile(signed):
        return signed, True
    unsigned = os.path.join(out_dir, f"androidApp-{build_type}-unsigned.apk")
    if os.path.isfile(unsigned):
        return unsigned, False
    raise StepFailed(f"build: neither {signed} nor {unsigned} was produced")


# ============================================================
# the device steps
# ============================================================

def _install_and_launch(cfg: Config, device: Device, runner: Runner, clock: Clock, apk: str) -> None:
    res = runner.run(_adb(cfg, device) + ["install", "-r", "-t", apk], timeout=600)
    if res.returncode != 0 or "Success" not in res.stdout:
        raise StepFailed(f"install: adb install {apk} failed (rc={res.returncode}): "
                         f"{res.stderr.strip() or res.stdout.strip()}")
    component = f"{cfg.android.package}/{cfg.android.activity}"
    _shell(cfg, device, runner, "am", "start", "-W", "-n", component, timeout=120)
    clock.sleep(FIRST_LAUNCH_WAIT_S)


def _broadcast(cfg: Config, device: Device, runner: Runner) -> int:
    res = _shell(cfg, device, runner, "am", "broadcast", "-a", INSTALL_PROFILE_ACTION,
                 "-n", f"{cfg.android.package}/{RECEIVER}")
    m = _RESULT_RE.search(res.stdout)
    if m is None:
        raise StepFailed(f"recognised: no result in the broadcast output: {res.stdout.strip()!r}")
    result = int(m.group(1))
    if result not in (1, 2):
        raise StepFailed(f"recognised: broadcast result {result} — {BROADCAST_RESULTS.get(result, 'unknown code')}")
    return result


def _compile_and_status(cfg: Config, device: Device, runner: Runner) -> str:
    _shell(cfg, device, runner, "cmd", "package", "compile", "-r", "bg-dexopt", cfg.android.package, timeout=600)
    res = _shell(cfg, device, runner, "dumpsys", "package", "dexopt", timeout=120)
    status = dexopt_status(res.stdout, cfg.android.package)
    if status is None:
        raise StepFailed(f"optimised: {cfg.android.package} has no entry in `dumpsys package dexopt`")
    if status != EXPECTED_STATUS:
        raise StepFailed(f"optimised: status={status} (expected {EXPECTED_STATUS})")
    return status


def dexopt_status(dump: str, package: str) -> Optional[str]:
    """The first `status=` under the package's block of `dumpsys package dexopt`."""
    inside = False
    for line in dump.splitlines():
        stripped = line.strip()
        if stripped.startswith("[") and stripped.endswith("]"):
            inside = stripped == f"[{package}]"
            continue
        if inside:
            m = _STATUS_RE.search(stripped)
            if m:
                return m.group(1)
    return None


# ============================================================
# the command
# ============================================================

def _select_device(args, cfg: Config, runner: Runner, devicectl_output: str) -> Device:
    devices = attached(runner, adb=cfg.android.adb, devicectl_output=devicectl_output)
    device = resolve(cfg, args.device, devices)
    if register(cfg, [device]):
        print(f"registered new device {device.device_id} ({device.model}) in {cfg.path}")
    device = resolve(cfg, device.device_id, devices)
    if device.platform != "android":
        raise DeviceError(f"profile verify is Android-only; {device.model} ({device.device_id}) is {device.platform}")
    return device


def main(args, cfg: Config, paths, runner: Runner, clock: Clock) -> Tuple[int, Payload]:
    device = _select_device(args, cfg, runner, os.path.join(paths.data_dir, "devicectl.json"))
    payload: Payload = {"device": device.device_id, "build_type": args.build_type}
    print(f"profile verify on {device.model} ({device.device_id})")
    checks = [_check_adb_device(cfg, device, runner), _check_awake(cfg, device, runner),
              _check_unlocked(cfg, device, runner)]
    for check in checks:
        print(f"  [{'ok' if check.ok else 'FAIL'}] {check.name} — {check.detail}")
    failing = [c for c in checks if not c.ok]
    if failing:
        print("refused: " + "; ".join(f"{c.name}: {c.detail}" for c in failing))
        return EXIT_REFUSED, payload

    lines: List[str] = []
    try:
        if args.apk:
            apk, installable = os.path.abspath(args.apk), True
            if not os.path.isfile(apk):
                raise DeviceError(f"--apk {args.apk}: file not found")
        else:
            apk, installable = _build(cfg, runner, args.build_type, java_home=None)
        payload["apk"] = apk
        size, apk_bytes, share = inspect_apk(runner, apk)
        lines.append(f"profile present ({size} bytes, {share} %)")
        print(f"  [ok] {lines[-1]} in {apk}")
        payload.update({"profile_bytes": size, "apk_bytes": apk_bytes, "profile_share_pct": share})
        if not installable:
            print("  an unsigned release APK cannot be installed: the APK inspection is the whole check "
                  "(use --build-type perf, or --apk with a signed build, for the device steps)")
            print("; ".join(lines))
            return EXIT_OK, payload
        try:
            _install_and_launch(cfg, device, runner, clock, apk)
            result = _broadcast(cfg, device, runner)
            lines.append(f"recognised (result {result})")
            print(f"  [ok] {lines[-1]}: {BROADCAST_RESULTS[result]}")
            payload["broadcast_result"] = result
            status = _compile_and_status(cfg, device, runner)
            lines.append(f"optimised ({status})")
            print(f"  [ok] {lines[-1]}")
            payload["dexopt_status"] = status
        finally:
            _shell(cfg, device, runner, "am", "force-stop", cfg.android.package)
    except StepFailed as exc:
        print(f"  [FAIL] {exc}")
        print("failed: " + "; ".join(lines + [str(exc)]))
        payload["failed"] = str(exc)
        return EXIT_FAILED, payload
    print("; ".join(lines))
    return EXIT_OK, payload
