"""The iOS round (research D6): Apple's stock tooling only.

Per session one `xcodebuild build-for-testing` (scheme TembraPerf, configuration
Perf) into the round's DerivedData; per round a fresh install through `devicectl`
(simulators: `simctl`), one unmeasured warm-up launch, `xcodebuild
test-without-building` with the result bundle under the round, then
`xcresulttool get test-results metrics` (the per-iteration values), `get
test-results summary` (failures; `PERF-REFUSED:` refuses the round), `export
attachments` (`preflight.json` → the fingerprint) and the end-of-journey
`engine-health.json` pulled from the app container. The host cannot read thermal
state or battery level, so the preflight inside the runner checks them. Every tool
call goes through the injectable `Runner`.
"""

import glob
import json
import logging
import os
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence

from .collect_android import CollectError
from .collect_ios import collect_round, engine_health, parse_preflight, refusal_from_failures
from .config import Config
from .devices import Device
from .orchestrate_android import BuildError, Check, JourneyFailed, Refused, RoundOutcome
from .records import JourneyResult, MetricResult
from .runner import Clock, Completed, Runner

logger = logging.getLogger("tracepulse.orchestrate_ios")

BUILD_TIMEOUT_S = 3600
TEST_TIMEOUT_S = 3600
TOOL_TIMEOUT_S = 300


@dataclass(frozen=True)
class IosRoundOptions:
    journeys: Sequence[str]
    iterations: int = 20
    no_build: bool = False
    xctestrun: Optional[str] = None
    configuration: str = "Perf"
    size: bool = False
    keep_build: bool = False
    audio_trace: bool = True


# ============================================================
# helpers
# ============================================================

def _xcconfig(cfg: Config) -> Dict[str, str]:
    path = os.path.join(cfg.root, "iosApp", "Configuration", "Config.xcconfig")
    values: Dict[str, str] = {}
    if os.path.isfile(path):
        with open(path) as fh:
            for line in fh:
                line = line.strip()
                if "=" in line and not line.startswith("//"):
                    key, value = line.split("=", 1)
                    values[key.strip()] = value.strip()
    return values


def team_id(cfg: Config) -> str:
    return _xcconfig(cfg).get("TEAM_ID", "")


def bundle_id_with_team(cfg: Config) -> str:
    """The device bundle identifier: `${BUNDLE_ID}${TEAM_ID}` as the Xcode project builds it."""
    return f"{cfg.ios.bundle_id}{team_id(cfg)}"


def _destination(device: Device) -> str:
    return f"platform=iOS Simulator,id={device.device_id}" if device.kind == "simulator" else f"platform=iOS,id={device.device_id}"


def _project(cfg: Config) -> str:
    return os.path.join(cfg.root, cfg.ios.project)


def _ios_dir(cfg: Config) -> str:
    return os.path.dirname(_project(cfg))


def xctestrun_path(derived_data: str) -> str:
    candidates = sorted(glob.glob(os.path.join(derived_data, "Build", "Products", "*.xctestrun")))
    if not candidates:
        raise BuildError(f"no .xctestrun under {derived_data}/Build/Products")
    return candidates[-1]


def _app_path(xctestrun: str, cfg: Config) -> str:
    """The built app next to the xctestrun (Build/Products/<Configuration>-<sdk>/<App>.app)."""
    products = os.path.dirname(xctestrun)
    apps = sorted(glob.glob(os.path.join(products, "*", "*.app")))
    apps = [a for a in apps if not a.endswith("-Runner.app") and "/PlugIns/" not in a]
    if not apps:
        raise BuildError(f"no .app under {products}")
    return apps[-1]


# ============================================================
# doctor: what the host can check
# ============================================================

def doctor(cfg: Config, device: Device, runner: Runner, scratch_dir: Optional[str] = None) -> List[Check]:
    checks: List[Check] = []
    res = runner.run(["xcrun", "xcodebuild", "-version"], timeout=TOOL_TIMEOUT_S)
    checks.append(Check("xcode", res.returncode == 0, res.stdout.strip().replace("\n", " ") or res.stderr.strip()))
    if device.kind == "simulator":
        res = runner.run(["xcrun", "simctl", "list", "devices", "booted", "-j"], timeout=TOOL_TIMEOUT_S)
        booted = device.device_id in res.stdout
        checks.append(Check("simulator_booted", booted, f"{device.device_id} {'booted' if booted else 'not booted'}"))
        return checks
    out = os.path.join(scratch_dir or os.path.expanduser("~"), ".tracepulse-devicectl.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    res = runner.run(["xcrun", "devicectl", "list", "devices", "--json-output", out, "--quiet"], timeout=TOOL_TIMEOUT_S)
    attached = res.returncode == 0 and os.path.isfile(out) and device.device_id in open(out).read()
    checks.append(Check("device_attached", attached, f"{device.device_id} {'paired and attached' if attached else 'not found by devicectl'}"))
    res = runner.run(["xcrun", "devicectl", "device", "info", "lockState", "--device", device.device_id, "--json-output", "-"], timeout=TOOL_TIMEOUT_S)
    locked = None
    try:
        data = json.loads(res.stdout or "{}")
        locked = bool(data.get("result", {}).get("lockState", {}).get("passcodeRequired", False))
    except ValueError:
        locked = None
    if locked is None:
        checks.append(Check("unlocked", res.returncode == 0, "lock state unreadable; the runner's preflight decides"))
    else:
        checks.append(Check("unlocked", not locked, "unlocked" if not locked else "locked: unlock the iPhone and keep it awake"))
    return checks


# ============================================================
# build, install, warm-up, test
# ============================================================

def build_for_testing(cfg: Config, device: Device, runner: Runner, derived_data: str, configuration: str,
                      log_path: Optional[str] = None) -> str:
    cmd = ["xcodebuild", "build-for-testing", "-project", _project(cfg), "-scheme", cfg.ios.scheme,
           "-configuration", configuration, "-destination", _destination(device),
           "-derivedDataPath", derived_data, "-allowProvisioningUpdates"]
    res = runner.run(cmd, cwd=_ios_dir(cfg), timeout=BUILD_TIMEOUT_S)
    if log_path:
        os.makedirs(os.path.dirname(log_path), exist_ok=True)
        with open(log_path, "w") as fh:
            fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}\n--- stderr ---\n{res.stderr}")
    if res.returncode != 0:
        raise BuildError(f"xcodebuild build-for-testing failed (rc={res.returncode}); see {log_path or 'the output'}")
    return xctestrun_path(derived_data)


def _devicectl(cfg: Config, device: Device, runner: Runner, *args: str, timeout: int = TOOL_TIMEOUT_S) -> Completed:
    return runner.run(["xcrun", "devicectl", "device", *args, "--device", device.device_id], timeout=timeout)


def install(cfg: Config, device: Device, runner: Runner, app_path: str) -> None:
    bundle = bundle_id_with_team(cfg)
    if device.kind == "simulator":
        runner.run(["xcrun", "simctl", "uninstall", device.device_id, bundle], timeout=TOOL_TIMEOUT_S)
        res = runner.run(["xcrun", "simctl", "install", device.device_id, app_path], timeout=TOOL_TIMEOUT_S)
    else:
        _devicectl(cfg, device, runner, "uninstall", "app", bundle)
        res = _devicectl(cfg, device, runner, "install", "app", app_path, timeout=600)
    if res.returncode != 0:
        raise BuildError(f"install of {app_path} failed (rc={res.returncode}): {res.stderr.strip() or res.stdout.strip()}")


def warm_up(cfg: Config, device: Device, runner: Runner, clock: Clock) -> None:
    bundle = bundle_id_with_team(cfg)
    if device.kind == "simulator":
        runner.run(["xcrun", "simctl", "launch", device.device_id, bundle], timeout=TOOL_TIMEOUT_S)
        clock.sleep(8)
        runner.run(["xcrun", "simctl", "terminate", device.device_id, bundle], timeout=TOOL_TIMEOUT_S)
    else:
        _devicectl(cfg, device, runner, "process", "launch", "--terminate-existing", bundle)
        clock.sleep(8)
        _devicectl(cfg, device, runner, "process", "terminate", "--pid-or-name", bundle)


def test_without_building(cfg: Config, device: Device, runner: Runner, xctestrun: str, result_bundle: str,
                          log_path: str) -> Completed:
    cmd = ["xcodebuild", "test-without-building", "-xctestrun", xctestrun, "-destination", _destination(device),
           "-resultBundlePath", result_bundle, f"-only-testing:{cfg.ios.test_target}"]
    res = runner.run(cmd, cwd=_ios_dir(cfg), timeout=TEST_TIMEOUT_S)
    with open(log_path, "w") as fh:
        fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}\n--- stderr ---\n{res.stderr}")
    return res


def _xcresulttool(runner: Runner, *args: str) -> Completed:
    return runner.run(["xcrun", "xcresulttool", *args], timeout=TOOL_TIMEOUT_S)


def _failures(runner: Runner, result_bundle: str) -> List[Dict[str, Any]]:
    res = _xcresulttool(runner, "get", "test-results", "summary", "--path", result_bundle, "--compact")
    try:
        data = json.loads(res.stdout or "{}")
    except ValueError:
        return []
    failures = data.get("testFailures") or []
    return [{"testIdentifier": f.get("testIdentifier") or f.get("testName", ""), "message": f.get("failureText") or f.get("message", "")}
            for f in failures]


def _pull_engine_health(cfg: Config, device: Device, runner: Runner, round_dir: str) -> Optional[str]:
    bundle = bundle_id_with_team(cfg)
    dest = os.path.join(round_dir, "engine-health.json")
    if device.kind == "simulator":
        res = runner.run(["xcrun", "simctl", "get_app_container", device.device_id, bundle, "data"], timeout=TOOL_TIMEOUT_S)
        container = res.stdout.strip()
        src = os.path.join(container, "Documents", "engine-health.json")
        if res.returncode == 0 and os.path.isfile(src):
            with open(src) as fh, open(dest, "w") as out:
                out.write(fh.read())
            return dest
        return None
    res = _devicectl(cfg, device, runner, "copy", "from", "--source", "Documents/engine-health.json",
                     "--domain-type", "appDataContainer", "--domain-identifier", bundle, "--destination", round_dir)
    return dest if res.returncode == 0 and os.path.isfile(dest) else None


def _audio_trace(cfg: Config, device: Device, runner: Runner, round_dir: str) -> Optional[str]:
    """`xctrace record --template 'Audio System Trace' --attach` for the stress journey is started by the
    orchestrator around the test run on physical devices; the p99 of the I/O-proc duration is read by traces_ios."""
    if device.kind == "simulator":
        return None
    path = os.path.join(round_dir, "audio.trace")
    return path if os.path.isdir(path) else None


# ============================================================
# the round
# ============================================================

def run_round(cfg: Config, device: Device, runner: Runner, clock: Clock, round_dir: str,
              options: IosRoundOptions) -> RoundOutcome:
    os.makedirs(round_dir, exist_ok=True)
    checks = doctor(cfg, device, runner, scratch_dir=round_dir)
    if any(not c.ok for c in checks):
        raise Refused(checks)
    artifacts: List[Dict[str, str]] = []
    derived = os.path.join(round_dir, "DerivedData")
    if options.xctestrun:
        xctestrun = options.xctestrun
    elif options.no_build:
        raise BuildError("--no-build needs --xctestrun <path> on iOS")
    else:
        log = os.path.join(round_dir, "xcodebuild-build.log")
        xctestrun = build_for_testing(cfg, device, runner, derived, options.configuration, log)
        artifacts.append({"kind": "xcodebuild_log", "path": log})
    app_path = _app_path(xctestrun, cfg) if os.path.exists(xctestrun) else xctestrun.replace(".xctestrun", ".app")
    try:
        install(cfg, device, runner, app_path)
    except BuildError:
        raise
    warm_up(cfg, device, runner, clock)

    result_bundle = os.path.join(round_dir, "perf.xcresult")
    log = os.path.join(round_dir, "xcodebuild-test.log")
    res = test_without_building(cfg, device, runner, xctestrun, result_bundle, log)
    artifacts.append({"kind": "xcresult", "path": result_bundle})
    artifacts.append({"kind": "xcodebuild_log", "path": log})

    failures = _failures(runner, result_bundle)
    attachments_dir = os.path.join(round_dir, "attachments")
    _xcresulttool(runner, "export", "attachments", "--path", result_bundle, "--output-path", attachments_dir)
    fingerprint: Dict[str, Any] = {"os_version": device.os_version or "unknown"}
    if device.os_build:
        fingerprint["os_build"] = device.os_build
    preflight = find_attachment(attachments_dir, "preflight.json")
    if preflight:
        fingerprint.update(parse_preflight(preflight))
    check_records = [c.as_record() for c in checks]
    for name in ("thermal_state", "battery_level"):
        if name in fingerprint:
            check_records.append({"name": name, "passed": True, "value": fingerprint[name],
                                  "limit": "nominal" if name == "thermal_state" else 0.5})

    refusal = refusal_from_failures(failures)
    if refusal is not None:
        failing = Check(refusal.check, False, refusal.message)
        raise Refused(checks + [failing])
    if res.returncode != 0 or failures:
        reason = failures[0]["message"] if failures else f"xcodebuild test-without-building rc={res.returncode}"
        reason = f"{failures[0]['testIdentifier']}: {reason}" if failures else reason
        outcome = RoundOutcome(status="failed", checks=check_records, fingerprint=fingerprint, journeys=[],
                               badges=[], artifacts=artifacts, failure_reason=reason, build_type=options.configuration)
        raise JourneyFailed("journey", reason, outcome)

    metrics_json = os.path.join(round_dir, "metrics.json")
    res = _xcresulttool(runner, "get", "test-results", "metrics", "--path", result_bundle, "--compact")
    with open(metrics_json, "w") as fh:
        fh.write(res.stdout)
    artifacts.append({"kind": "metrics_json", "path": metrics_json})
    simulator = device.kind == "simulator"
    try:
        collected = collect_round(metrics_json, cfg, simulator=simulator, iterations=options.iterations)
    except CollectError as exc:
        outcome = RoundOutcome(status="failed", checks=check_records, fingerprint=fingerprint, journeys=[],
                               badges=[], artifacts=artifacts, failure_reason=f"collection: {exc}",
                               build_type=options.configuration)
        raise JourneyFailed("collection", str(exc), outcome) from exc

    journeys = [j for j in collected.journeys if j.journey in options.journeys]
    health_path = _pull_engine_health(cfg, device, runner, round_dir)
    if health_path:
        artifacts.append({"kind": "engine_health", "path": health_path})
        stress = next((j for j in journeys if j.journey == "stress"), None)
        if stress is not None:
            values = engine_health(health_path)
            merged = dict(stress.metrics)
            for key, value in values.items():
                merged[key] = MetricResult(unit=_unit_of(key), samples=[value] * stress.iterations, source="engine-health")
            journeys[journeys.index(stress)] = JourneyResult(journey="stress", iterations=stress.iterations, metrics=merged)
    trace = _audio_trace(cfg, device, runner, round_dir)
    if trace:
        artifacts.append({"kind": "audio_trace", "path": trace})
        from .traces_ios import ioproc_p99_from_trace
        p99 = ioproc_p99_from_trace(runner, trace, round_dir)
        stress = next((j for j in journeys if j.journey == "stress"), None)
        if p99 is not None and stress is not None:
            merged = dict(stress.metrics)
            merged["audio.ioproc_p99_us"] = MetricResult(unit="us", samples=[p99] * stress.iterations, source="xctrace")
            journeys[journeys.index(stress)] = JourneyResult(journey="stress", iterations=stress.iterations, metrics=merged)

    return RoundOutcome(status="succeeded", checks=check_records, fingerprint=fingerprint, journeys=journeys,
                        badges=collected.badges, artifacts=artifacts, app_size_bytes=None,
                        build_type=options.configuration)


def _unit_of(key: str) -> str:
    from .catalogue import lookup
    return lookup("ios", key).unit


def find_attachment(directory: str, name: str) -> Optional[str]:
    """`xcresulttool export attachments` writes `<uuid>.<ext>` files and a `manifest.json`
    whose `suggestedHumanReadableName` is `<name>_<index>_<uuid>.<ext>` (verified on the
    iPhone 17 simulator, round 20261008T212602Z): resolve through the manifest, newest
    attachment first; fall back to a file literally named `name`."""
    if not os.path.isdir(directory):
        return None
    stem, _ext = os.path.splitext(name)
    manifest = os.path.join(directory, "manifest.json")
    if os.path.isfile(manifest):
        try:
            with open(manifest) as fh:
                entries = json.load(fh)
        except ValueError:
            entries = []
        candidates = []
        for entry in entries:
            for att in entry.get("attachments", []):
                human = att.get("suggestedHumanReadableName", "")
                if human == name or human.startswith(stem + "_") or human.startswith(stem + "."):
                    candidates.append((att.get("timestamp", 0), att.get("exportedFileName", "")))
        for _ts, exported in sorted(candidates, reverse=True):
            path = os.path.join(directory, exported)
            if os.path.isfile(path):
                return path
    direct = os.path.join(directory, name)
    return direct if os.path.isfile(direct) else None
