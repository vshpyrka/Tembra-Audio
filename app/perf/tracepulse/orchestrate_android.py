"""The Android round: doctor, measurement build, fresh install, warm-up, the
benchmark journeys through `am instrument`, pull, APK size.

De-templated from trace-pulse's orchestrator (its device protocol and its
link-flap-tolerant detached instrument are kept): the device comes from the
registry, the build is `./gradlew --console=plain -Ptembra.perf=true` on this
repository (the pipeline is user tooling, so it calls Gradle directly and keeps
the log in the round directory), and the journeys are the four Tembra benchmark
classes. Every host and device interaction goes through the injectable `Runner`.
"""

import glob
import logging
import os
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence

from .collect_android import CollectedRound, CollectError, apk_size, collect_round
from .config import Config
from .devices import Device
from .records import JourneyResult
from .runner import Clock, Completed, Runner

logger = logging.getLogger("tracepulse.orchestrate_android")


class Refused(Exception):
    """A device check failed before measuring (exit status 2)."""

    def __init__(self, checks: List["Check"]):
        self.checks = checks
        self.failing = [c for c in checks if not c.ok]
        super().__init__("; ".join(f"{c.name}: {c.detail}" for c in self.failing))


class BuildError(Exception):
    """A build or install command returned nonzero (exit status 3; nothing measured)."""


class JourneyFailed(Exception):
    """A journey could not complete after its retry; `outcome` is the failed round (exit status 3)."""

    def __init__(self, journey: str, reason: str, outcome: "RoundOutcome"):
        self.journey = journey
        self.reason = reason
        self.outcome = outcome
        super().__init__(f"journey {journey} failed: {reason}")


@dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str
    value: Any = None
    limit: Any = None

    def as_record(self) -> Dict[str, Any]:
        return {"name": self.name, "passed": self.ok, "value": self.value, "limit": self.limit}


@dataclass(frozen=True)
class RoundOptions:
    journeys: Sequence[str]
    iterations: int = 20
    no_build: bool = False
    apk: Optional[str] = None
    test_apk: Optional[str] = None
    build_type: str = "perf"
    keep_build: bool = False


@dataclass
class RoundOutcome:
    """What a round produced; `run.py` turns it into a RoundRecord."""
    status: str
    checks: List[Dict[str, Any]]
    fingerprint: Dict[str, Any]
    journeys: List[JourneyResult]
    badges: List[str]
    artifacts: List[Dict[str, str]]
    app_size_bytes: Optional[int] = None
    failure_reason: Optional[str] = None
    thermal_seconds: float = 0.0
    build_type: str = "perf"
    warnings: List[str] = field(default_factory=list)


# ============================================================
# doctor: the device protocol
# ============================================================

_STORAGE_MIN_KIB = 2 * 1024 * 1024
_LEVEL_RE = re.compile(r"level:\s*(\d+)")
_TEMP_RE = re.compile(r"temperature:\s*(-?\d+)")
_USB_POWERED_RE = re.compile(r"USB powered:\s*(true|false)", re.IGNORECASE)
_AC_POWERED_RE = re.compile(r"AC powered:\s*(true|false)", re.IGNORECASE)
_WIRELESS_POWERED_RE = re.compile(r"Wireless powered:\s*(true|false)", re.IGNORECASE)
_WAKEFULNESS_RE = re.compile(r"mWakefulness=(\w+)")
_KEYGUARD_SHOWING_RE = re.compile(r"KeyguardServiceDelegate\s*\n\s*showing=(true|false)")
DETACHED_CMD_TIMEOUT_S = 60
PULL_TIMEOUT_S = 600
BUILD_TIMEOUT_S = 3600


def _adb(cfg: Config, device: Device) -> List[str]:
    return [os.path.expanduser(cfg.android.adb), "-s", device.device_id]


def _shell(cfg: Config, device: Device, runner: Runner, *args: str, timeout: int = DETACHED_CMD_TIMEOUT_S) -> Completed:
    return runner.run(_adb(cfg, device) + ["shell", *args], timeout=timeout)


def _limit(cfg: Config, device: Device, key: str, default):
    return device.overrides.get(key, getattr(cfg.quality, key, default))


def _check_java_home(runner: Runner) -> Check:
    res = runner.run(["/usr/libexec/java_home", "-v", "21"])
    path = res.stdout.strip()
    if res.returncode == 0 and path:
        return Check("java_home", True, path, value=path)
    return Check("java_home", False, f"java_home -v 21 failed (rc={res.returncode}): {res.stderr.strip()}")


def _check_sdk_dir(cfg: Config) -> Check:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    source = "env"
    if not sdk:
        local = os.path.join(cfg.root, "local.properties")
        if os.path.isfile(local):
            with open(local) as fh:
                for line in fh:
                    if line.strip().startswith("sdk.dir="):
                        sdk = line.strip().split("=", 1)[1]
                        source = "local.properties"
                        break
    if not sdk:
        return Check("sdk_dir", False, "ANDROID_HOME/ANDROID_SDK_ROOT not set and no sdk.dir in app/local.properties")
    if not os.path.isdir(sdk):
        return Check("sdk_dir", False, f"SDK dir not found: {sdk}")
    return Check("sdk_dir", True, f"sdk={sdk} ({source})", value=sdk)


def _check_adb_device(cfg: Config, device: Device, runner: Runner) -> Check:
    res = runner.run([os.path.expanduser(cfg.android.adb), "devices", "-l"], timeout=60)
    for line in res.stdout.splitlines():
        if line.startswith(device.device_id) and " device" in line:
            return Check("adb_device", True, f"{device.device_id} present and authorized")
    return Check("adb_device", False, f"device {device.device_id} not found in `adb devices -l`: {res.stdout.strip()!r}")


def _battery(cfg: Config, device: Device, runner: Runner) -> Optional[Dict[str, Any]]:
    res = _shell(cfg, device, runner, "dumpsys", "battery")
    level = _LEVEL_RE.search(res.stdout)
    temp = _TEMP_RE.search(res.stdout)
    if not (level and temp):
        return None
    powered = any(m and m.group(1).lower() == "true" for m in (
        _USB_POWERED_RE.search(res.stdout), _AC_POWERED_RE.search(res.stdout), _WIRELESS_POWERED_RE.search(res.stdout)))
    return {"level": int(level.group(1)), "temp_c": int(temp.group(1)) / 10.0, "powered": powered, "raw": res.stdout}


def _check_battery(cfg: Config, device: Device, runner: Runner) -> Check:
    battery = _battery(cfg, device, runner)
    if battery is None:
        return Check("battery", False, "couldn't parse `dumpsys battery` output")
    min_level = int(_limit(cfg, device, "battery_min_level", 50))
    max_temp = float(_limit(cfg, device, "battery_temp_max_c", 35.0))
    reasons = []
    if battery["level"] < min_level:
        reasons.append(f"level {battery['level']}% < {min_level}%")
    if battery["temp_c"] > max_temp:
        reasons.append(f"temperature {battery['temp_c']:.1f} C > {max_temp:.1f} C")
    if not battery["powered"]:
        reasons.append("not externally powered (USB, AC or wireless)")
    detail = f"level={battery['level']}% temp={battery['temp_c']:.1f} C powered={battery['powered']}"
    if reasons:
        return Check("battery", False, "; ".join(reasons) + f" ({detail})",
                     value={"level": battery["level"], "temp_c": battery["temp_c"]},
                     limit={"min_level": min_level, "max_temp_c": max_temp})
    return Check("battery", True, detail, value={"level": battery["level"], "temp_c": battery["temp_c"]},
                 limit={"min_level": min_level, "max_temp_c": max_temp})


def _setting(cfg: Config, device: Device, runner: Runner, namespace: str, key: str) -> str:
    return _shell(cfg, device, runner, "settings", "get", namespace, key).stdout.strip()


def _check_refresh_rate(cfg: Config, device: Device, runner: Runner) -> Check:
    wanted = float(_limit(cfg, device, "refresh_rate_hz", 60.0))
    peak = _setting(cfg, device, runner, "system", "peak_refresh_rate")
    minimum = _setting(cfg, device, runner, "system", "min_refresh_rate")
    try:
        if float(peak) == wanted and float(minimum) == wanted:
            return Check("refresh_rate", True, f"pinned at {wanted:g} Hz", value=wanted, limit=wanted)
    except ValueError:
        pass
    return Check("refresh_rate", False, f"peak_refresh_rate={peak!r} min_refresh_rate={minimum!r} (expected {wanted:g})",
                 value={"peak": peak, "min": minimum}, limit=wanted)


def _check_airplane(cfg: Config, device: Device, runner: Runner) -> Check:
    value = _setting(cfg, device, runner, "global", "airplane_mode_on")
    if value == "1":
        return Check("airplane", True, "airplane_mode_on=1", value=True, limit=True)
    return Check("airplane", False, f"airplane_mode_on={value!r} (expected '1')", value=False, limit=True)


def _check_brightness_manual(cfg: Config, device: Device, runner: Runner) -> Check:
    value = _setting(cfg, device, runner, "system", "screen_brightness_mode")
    if value == "0":
        return Check("brightness_manual", True, "screen_brightness_mode=0")
    return Check("brightness_manual", False, f"screen_brightness_mode={value!r} (expected '0', manual)")


def _check_storage(cfg: Config, device: Device, runner: Runner) -> Check:
    res = _shell(cfg, device, runner, "df", "-k", "/data")
    available = None
    for line in res.stdout.splitlines():
        parts = line.split()
        if len(parts) >= 4 and parts[-1].startswith("/data"):
            try:
                available = int(parts[3])
                break
            except ValueError:
                continue
    if available is None:
        return Check("storage", False, f"couldn't parse `df -k /data`: {res.stdout.strip()!r}")
    if available < _STORAGE_MIN_KIB:
        return Check("storage", False, f"available {available} KiB < {_STORAGE_MIN_KIB} KiB", value=available, limit=_STORAGE_MIN_KIB)
    return Check("storage", True, f"available={available} KiB", value=available, limit=_STORAGE_MIN_KIB)


def _check_awake(cfg: Config, device: Device, runner: Runner) -> Check:
    def wakefulness() -> str:
        m = _WAKEFULNESS_RE.search(_shell(cfg, device, runner, "dumpsys", "power").stdout)
        return m.group(1) if m else "unknown"
    state = wakefulness()
    if state != "Awake":
        _shell(cfg, device, runner, "input", "keyevent", "KEYCODE_WAKEUP")
        state = wakefulness()
    if state == "Awake":
        return Check("awake", True, "mWakefulness=Awake")
    return Check("awake", False, f"screen is {state}; wake the phone")


def _check_unlocked(cfg: Config, device: Device, runner: Runner) -> Check:
    def showing() -> Optional[bool]:
        m = _KEYGUARD_SHOWING_RE.search(_shell(cfg, device, runner, "dumpsys", "window", "policy").stdout)
        return None if m is None else m.group(1) == "true"
    locked = showing()
    if locked:
        _shell(cfg, device, runner, "wm", "dismiss-keyguard")
        locked = showing()
    if locked is None:
        return Check("unlocked", False, "couldn't read the keyguard state from `dumpsys window policy`")
    if locked:
        return Check("unlocked", False, "phone is locked (keyguard showing); unlock it and keep the screen on")
    return Check("unlocked", True, "keyguard not showing")


def doctor(cfg: Config, device: Device, runner: Runner) -> List[Check]:
    """Every check, never short-circuited, so one failure doesn't hide the rest.
    The screen is woken and the keyguard dismissed (when it has no credential)
    before their checks; airplane mode and the refresh rate are enforced by
    `run_round` before this runs."""
    return [
        _check_java_home(runner),
        _check_sdk_dir(cfg),
        _check_adb_device(cfg, device, runner),
        _check_battery(cfg, device, runner),
        _check_refresh_rate(cfg, device, runner),
        _check_airplane(cfg, device, runner),
        _check_brightness_manual(cfg, device, runner),
        _check_storage(cfg, device, runner),
        _check_awake(cfg, device, runner),
        _check_unlocked(cfg, device, runner),
    ]


def fingerprint(cfg: Config, device: Device, runner: Runner) -> Dict[str, Any]:
    """The round's device fingerprint (record `fingerprint` block)."""
    prop = lambda name: _shell(cfg, device, runner, "getprop", name).stdout.strip()  # noqa: E731
    battery = _battery(cfg, device, runner) or {}
    peak = _setting(cfg, device, runner, "system", "peak_refresh_rate")
    try:
        refresh = float(peak)
    except ValueError:
        refresh = None
    airplane = _setting(cfg, device, runner, "global", "airplane_mode_on") == "1"
    fp: Dict[str, Any] = {
        "os_version": prop("ro.build.version.release"),
        "os_build": prop("ro.build.id"),
        "sdk": int(prop("ro.build.version.sdk") or 0),
        "network_state": "none" if airplane else "unknown",
        "model": prop("ro.product.model"),
    }
    if refresh is not None:
        fp["refresh_rate_hz"] = refresh
    if battery:
        fp["battery_temp_c"] = battery["temp_c"]
        fp["battery_level"] = battery["level"]
    return fp


# ============================================================
# build, install, warm-up
# ============================================================

def gradle_tasks(cfg: Config, build_type: str) -> List[str]:
    suffix = build_type[:1].upper() + build_type[1:]
    return [f":androidApp:assemble{suffix}", f"{cfg.android.benchmarks_module}:assemble{suffix}"]


def apk_paths(cfg: Config, build_type: str) -> "tuple[str, str]":
    module = cfg.android.benchmarks_module.lstrip(":")
    app = os.path.join(cfg.root, "androidApp", "build", "outputs", "apk", build_type, f"androidApp-{build_type}.apk")
    test = os.path.join(cfg.root, module, "build", "outputs", "apk", build_type, f"{module}-{build_type}.apk")
    return app, test


def build(cfg: Config, runner: Runner, build_type: str, log_path: str, java_home: Optional[str]) -> "tuple[str, str]":
    """`./gradlew --console=plain -Ptembra.perf=true <assemble tasks>` from the Gradle
    root; stdout and stderr go to `log_path`, never to the console in full."""
    cmd = ["./gradlew", "--console=plain", "-Ptembra.perf=true", *gradle_tasks(cfg, build_type)]
    env = {"JAVA_HOME": java_home} if java_home else None
    res = runner.run(cmd, cwd=cfg.root, env=env, timeout=BUILD_TIMEOUT_S)
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    with open(log_path, "w") as fh:
        fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}")
        if res.stderr:
            fh.write("\n--- stderr ---\n" + res.stderr)
    if res.returncode != 0:
        raise BuildError(f"{' '.join(cmd)} failed (rc={res.returncode}); see {log_path}")
    app, test = apk_paths(cfg, build_type)
    for path in (app, test):
        if not os.path.isfile(path):
            raise BuildError(f"build succeeded but {path} is missing; see {log_path}")
    return app, test


def install(cfg: Config, device: Device, runner: Runner, app_apk: str, test_apk: str) -> None:
    adb = _adb(cfg, device)
    runner.run(adb + ["shell", "pm", "uninstall", cfg.android.package], timeout=120)  # nothing to remove on a fresh phone
    runner.run(adb + ["shell", "pm", "uninstall", cfg.android.test_package], timeout=120)
    for apk in (app_apk, test_apk):
        res = runner.run(adb + ["install", "-r", "-t", apk], timeout=600)
        if res.returncode != 0 or "Success" not in res.stdout:
            raise BuildError(f"adb install {apk} failed (rc={res.returncode}): {res.stderr.strip() or res.stdout.strip()}")
    res = runner.run(adb + ["shell", "pm", "grant", cfg.android.package, "android.permission.RECORD_AUDIO"], timeout=60)
    if res.returncode != 0:
        logger.warning("pm grant RECORD_AUDIO failed (rc=%s): %s", res.returncode, res.stderr.strip())


def warm_up(cfg: Config, device: Device, runner: Runner, clock: Clock) -> None:
    """One unmeasured launch, then HOME before the force-stop so ActivityManager
    does not resurrect the task (trace-pulse's first live-round lesson)."""
    component = f"{cfg.android.package}/{cfg.android.activity}"
    _shell(cfg, device, runner, "am", "start", "-W", "-n", component, timeout=120)
    clock.sleep(8)
    _shell(cfg, device, runner, "input", "keyevent", "KEYCODE_HOME")
    clock.sleep(1)
    _shell(cfg, device, runner, "am", "force-stop", cfg.android.package)


# ============================================================
# journeys: one detached `am instrument` per benchmark class
# ============================================================

BENCHMARK_CLASS_OF = {
    "startup": "StartupBenchmark",
    "tuner": "TunerJourneyBenchmark",
    "metronome": "MetronomeJourneyBenchmark",
    "stress": "StressJourneyBenchmark",
}
_INSTRUMENT_FAILURE_MARKERS = ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=")
_INSTRUMENT_TERMINAL_MARKERS = ("OK (", "FAILURES!!!", "INSTRUMENTATION_CODE:")
_DETACHED_BEACON = "TRACEPULSE_ATTEMPT_START"
_DETACHED_POLL_INTERVAL_S = 10
_DETACHED_TIMEOUT_S = 1500
_DETACHED_WALL_CAP_S = 3 * 3600
COOLDOWN_BETWEEN_CLASSES_S = 120


def media_dir(cfg: Config) -> str:
    return f"/storage/emulated/0/Android/media/{cfg.android.test_package}/"


def _instrument_cmd(cfg: Config, device: Device, fqcn: str, iterations: int) -> List[str]:
    component = f"{cfg.android.test_package}/androidx.test.runner.AndroidJUnitRunner"
    return _adb(cfg, device) + ["shell", "am", "instrument", "-w", "-e", "class", fqcn,
                                "-e", "additionalTestOutputDir", media_dir(cfg),
                                "-e", "androidx.benchmark.iterations", str(iterations), component]


def _instrument_ok(res: Completed) -> bool:
    return res.returncode == 0 and not any(m in res.stdout for m in _INSTRUMENT_FAILURE_MARKERS)


def _run_instrument_detached(cfg: Config, device: Device, runner: Runner, clock: Clock, cmd: List[str],
                             fqcn: str) -> Completed:
    """Start `am instrument` under `nohup` on the device and poll its output file:
    the USB link may flap for minutes while the measurement keeps running; only
    reachable polls consume the budget (trace-pulse's Pixel 6 Pro lesson)."""
    adb = _adb(cfg, device)
    out = f"/data/local/tmp/tracepulse-{fqcn.rsplit('.', 1)[-1]}.out"
    runner.run(adb + ["shell", "am", "force-stop", cfg.android.test_package], timeout=DETACHED_CMD_TIMEOUT_S)
    device_cmd = " ".join(cmd[cmd.index("shell") + 1:])

    def start() -> None:
        runner.run(adb + ["shell", "rm", "-f", out], timeout=DETACHED_CMD_TIMEOUT_S)
        runner.run(adb + ["shell", f"nohup sh -c 'echo {_DETACHED_BEACON}; {device_cmd}' > {out} 2>&1 < /dev/null &"],
                   timeout=DETACHED_CMD_TIMEOUT_S)

    start()
    reachable_waited = wall_waited = beaconless = dead = 0
    content = ""
    while reachable_waited < _DETACHED_TIMEOUT_S and wall_waited < _DETACHED_WALL_CAP_S:
        clock.sleep(_DETACHED_POLL_INTERVAL_S)
        wall_waited += _DETACHED_POLL_INTERVAL_S
        res = runner.run(adb + ["shell", "cat", out], timeout=DETACHED_CMD_TIMEOUT_S)
        if res.returncode != 0:
            continue
        reachable_waited += _DETACHED_POLL_INTERVAL_S
        content = res.stdout
        if _DETACHED_BEACON not in content:
            beaconless += 1
            if beaconless >= 3:
                logger.warning("detached start for %s never took; re-issuing", fqcn)
                start()
                beaconless = 0
            continue
        if any(m in content for m in _INSTRUMENT_TERMINAL_MARKERS):
            return Completed(0, content, "")
        alive = runner.run(adb + ["shell", "pidof", cfg.android.test_package], timeout=DETACHED_CMD_TIMEOUT_S)
        if alive.returncode == 124:
            continue
        if alive.returncode == 0 and alive.stdout.strip():
            dead = 0
        else:
            dead += 1
            if dead >= 3:
                return Completed(1, content, "detached instrument died without a terminal marker")
    return Completed(124, content, f"detached instrument poll timed out (reachable {reachable_waited}s, wall {wall_waited}s)")


def _run_class_with_retry(cfg: Config, device: Device, runner: Runner, clock: Clock, fqcn: str, iterations: int,
                          log_dir: str) -> "tuple[bool, str]":
    cmd = _instrument_cmd(cfg, device, fqcn, iterations)
    simple = fqcn.rsplit(".", 1)[-1]
    last = ""
    for attempt in (1, 2):
        _shell(cfg, device, runner, "am", "force-stop", cfg.android.package)
        res = _run_instrument_detached(cfg, device, runner, clock, cmd, fqcn)
        os.makedirs(log_dir, exist_ok=True)
        with open(os.path.join(log_dir, f"{simple}-attempt{attempt}.log"), "w") as fh:
            fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}")
            if res.stderr:
                fh.write("\n--- stderr ---\n" + res.stderr)
        if _instrument_ok(res):
            return True, ""
        last = (res.stderr.strip() or _tail(res.stdout))
        logger.warning("instrument failed for %s (attempt %d/2): rc=%s", fqcn, attempt, res.returncode)
    return False, last


def _tail(text: str, n: int = 5) -> str:
    return " | ".join(line for line in text.splitlines()[-n:] if line.strip())


def _pull(cfg: Config, device: Device, runner: Runner, dest: str) -> None:
    os.makedirs(dest, exist_ok=True)
    runner.run(_adb(cfg, device) + ["pull", media_dir(cfg), dest], timeout=PULL_TIMEOUT_S)


def _clear_media_dir(cfg: Config, device: Device, runner: Runner) -> None:
    _shell(cfg, device, runner, "rm", "-rf", media_dir(cfg))


def _discover(local_dest: str) -> "tuple[List[str], List[str]]":
    jsons = sorted(glob.glob(os.path.join(local_dest, "**", "*-benchmarkData.json"), recursive=True))
    traces = sorted(glob.glob(os.path.join(local_dest, "**", "*.perfetto-trace"), recursive=True))
    return jsons, traces


# ============================================================
# the round
# ============================================================

def _enforce_airplane(cfg: Config, device: Device, runner: Runner, enable: bool) -> None:
    _shell(cfg, device, runner, "cmd", "connectivity", "airplane-mode", "enable" if enable else "disable")


def _pin_refresh(cfg: Config, device: Device, runner: Runner, peak: str, minimum: str) -> None:
    _shell(cfg, device, runner, "settings", "put", "system", "peak_refresh_rate", peak)
    _shell(cfg, device, runner, "settings", "put", "system", "min_refresh_rate", minimum)


def run_round(cfg: Config, device: Device, runner: Runner, clock: Clock, round_dir: str,
              options: RoundOptions) -> RoundOutcome:
    """One hermetic round on `device`. Airplane mode and the refresh-rate pin are
    enforced for the round and restored on every exit path; the screen is kept on.
    Raises `Refused` (nothing built), `BuildError`, or `JourneyFailed` (whose
    `outcome` is the failed round with its salvaged artifacts)."""
    airplane_was = _setting(cfg, device, runner, "global", "airplane_mode_on")
    peak_was = _setting(cfg, device, runner, "system", "peak_refresh_rate")
    min_was = _setting(cfg, device, runner, "system", "min_refresh_rate")
    wanted = f"{float(_limit(cfg, device, 'refresh_rate_hz', 60.0)):g}"
    if airplane_was != "1":
        _enforce_airplane(cfg, device, runner, True)
        clock.sleep(2)
    if peak_was != wanted or min_was != wanted:
        _pin_refresh(cfg, device, runner, wanted, wanted)
    _shell(cfg, device, runner, "svc", "power", "stayon", "true")
    try:
        return _run_round_inner(cfg, device, runner, clock, round_dir, options)
    finally:
        if airplane_was != "1":
            _enforce_airplane(cfg, device, runner, False)
        if peak_was != wanted or min_was != wanted:
            _pin_refresh(cfg, device, runner, peak_was or "null", min_was or "null")


def _run_round_inner(cfg: Config, device: Device, runner: Runner, clock: Clock, round_dir: str,
                     options: RoundOptions) -> RoundOutcome:
    checks = doctor(cfg, device, runner)
    if any(not c.ok for c in checks):
        raise Refused(checks)
    java_home = next((c.value for c in checks if c.name == "java_home"), None)
    fp = fingerprint(cfg, device, runner)
    os.makedirs(round_dir, exist_ok=True)
    artifacts: List[Dict[str, str]] = []

    if options.no_build:
        if not (options.apk and options.test_apk):
            raise BuildError("--no-build needs --apk <app.apk> and --test-apk <benchmarks.apk>")
        app_apk, test_apk = options.apk, options.test_apk
    else:
        log_path = os.path.join(round_dir, "gradle.log")
        app_apk, test_apk = build(cfg, runner, options.build_type, log_path, java_home)
        artifacts.append({"kind": "gradle_log", "path": log_path})
    install(cfg, device, runner, app_apk, test_apk)
    warm_up(cfg, device, runner, clock)

    journeys = list(options.journeys)
    failed: Optional["tuple[str, str]"] = None
    for i, journey in enumerate(journeys):
        fqcn = f"{cfg.android.test_package}.{BENCHMARK_CLASS_OF[journey]}"
        _clear_media_dir(cfg, device, runner)
        class_dir = os.path.join(round_dir, BENCHMARK_CLASS_OF[journey])
        ok, reason = _run_class_with_retry(cfg, device, runner, clock, fqcn, options.iterations, round_dir)
        _pull(cfg, device, runner, class_dir)
        if not ok:
            failed = (journey, f"{BENCHMARK_CLASS_OF[journey]} failed twice: {reason or 'see the attempt logs'}")
            break
        if i < len(journeys) - 1:
            clock.sleep(COOLDOWN_BETWEEN_CLASSES_S)

    json_paths, trace_paths = _discover(round_dir)
    artifacts += [{"kind": "benchmark_json", "path": p} for p in json_paths]
    artifacts += [{"kind": "trace", "path": p} for p in trace_paths]
    check_records = [c.as_record() for c in checks]

    if failed is not None:
        outcome = RoundOutcome(status="failed", checks=check_records, fingerprint=fp, journeys=[], badges=[],
                               artifacts=artifacts, failure_reason=failed[1], build_type=options.build_type)
        raise JourneyFailed(failed[0], failed[1], outcome)

    try:
        collected: CollectedRound = collect_round(json_paths, cfg)
    except CollectError as exc:
        outcome = RoundOutcome(status="failed", checks=check_records, fingerprint=fp, journeys=[], badges=[],
                               artifacts=artifacts, failure_reason=f"collection: {exc}", build_type=options.build_type)
        raise JourneyFailed("collection", str(exc), outcome) from exc
    by_name = {j.journey: j for j in collected.journeys}
    wanted_journeys = [by_name[j] for j in journeys if j in by_name]
    return RoundOutcome(status="succeeded", checks=check_records, fingerprint=fp, journeys=wanted_journeys,
                        badges=collected.badges, artifacts=artifacts, app_size_bytes=apk_size(app_apk),
                        thermal_seconds=collected.thermal_seconds, build_type=options.build_type)
