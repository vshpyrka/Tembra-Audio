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
import shutil
import tempfile
import threading
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence, Tuple

from .collect_android import CollectError
from .collect_ios import collect_round, engine_health, parse_preflight, refusal_from_failures
from .config import Config
from .devices import Device
from .orchestrate_android import BuildError, Check, JourneyFailed, Refused, RoundOutcome
from .records import JourneyResult, MetricResult
from .runner import Clock, Completed, Runner

logger = logging.getLogger("tracepulse.orchestrate_ios")

BUILD_TIMEOUT_S = 3600
# A round of all seven journeys runs about 90 minutes on the iPhone 14 since features 010 and 011 (the stress journey
# records a take: 23 minutes; tuner, the facade self-checks, startup, metronome, windowing, timeline, recording); the
# 60-minute limit cut the first full post-merge round of 010 short in windowing (2026-11-05).
TEST_TIMEOUT_S = 7200
TOOL_TIMEOUT_S = 300
# One unmeasured stress journey (~25 s of taps) plus the runner's attach and set-up; xctrace stops by itself.
AUDIO_TRACE_SECONDS = 75
AUDIO_TRACE_TEST = "AudioTracePass"
# The cold-launch hitch driver runs only by hand under a system-wide Hitches recording; a round never runs it.
COLD_LAUNCH_HITCH_TEST = "ColdLaunchHitchTests"
# The 30-minute recording soak (feature 010, T096) runs only by hand; a round never runs it.
RECORDING_SOAK_TEST = "RecordingSoakTests"

# The XCTest class that drives each journey: a round for a subset of the journeys (`--journeys recording`) runs only
# these classes, so the classes left out cannot refuse the round (the thermal gate is per test, and a 20-minute
# recording journey leaves the phone at "serious" for the classes that would follow it).
JOURNEY_TEST_CLASSES = {
    "startup": "StartupPerfTests",
    "tuner": "TunerPerfTests",
    "metronome": "MetronomePerfTests",
    "timeline": "TimelinePerfTests",
    "recording": "RecordingPerfTests",
    "stress": "StressPerfTests",
    "windowing": "WindowingPerfTests",
}


def journey_test_classes(cfg: Config, journeys: Sequence[str]) -> Optional[List[str]]:
    """The classes to run for a strict subset of the configured journeys; None for the whole target (every
    configured journey, or a journey without a known class). Feature 011 met the same need on the iPhone 14: a
    one-journey trial ran the whole target for an hour."""
    if set(journeys) >= set(cfg.journeys):
        return None
    classes = [JOURNEY_TEST_CLASSES.get(j) for j in journeys]
    if any(c is None for c in classes):
        return None
    return [c for c in classes if c is not None]
# A "not trusted" runner launch is retried this many times, this far apart, before the round refuses.
TRUST_RETRIES = 2
TRUST_RETRY_WAIT_S = 120
# The two HAL instruments of the `Audio System Trace` template, without its kernel instruments: 35 MB for 75 s
# system-wide instead of 0.5 GB, and the only tables the pipeline reads (`traces_ios`).
AUDIO_TRACE_INSTRUMENTS = ("Audio Client", "Audio Server")


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
    checks.append(_xcode_supports_device(device, runner))
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


def _major(version: str) -> int:
    try:
        return int(version.split(".")[0])
    except ValueError:
        return 0


def _xcode_supports_device(device: Device, runner: Runner) -> Check:
    """XCUITest launches through CoreDevice fail with "process identifier could
    not be determined" (10004) when the phone's iOS is newer than the selected
    Xcode's SDK, although `devicectl` still installs and launches the app. Point
    DEVELOPER_DIR at an Xcode whose SDK major is at least the phone's iOS major."""
    res = runner.run(["xcrun", "--sdk", "iphoneos", "--show-sdk-version"], timeout=TOOL_TIMEOUT_S)
    sdk = res.stdout.strip() if res.returncode == 0 else ""
    if not sdk or not device.os_version:
        return Check("xcode_supports_device", True, "SDK or device iOS version unknown; the test launch decides")
    supported = _major(device.os_version) <= _major(sdk)
    detail = f"iOS {device.os_version} on the phone, SDK {sdk} in the selected Xcode"
    if not supported:
        detail += (": this Xcode cannot launch XCUITests on the phone; select a newer one, e.g. "
                   "DEVELOPER_DIR=/Applications/Xcode-<version>.app/Contents/Developer uv run tracepulse run …")
    return Check("xcode_supports_device", supported, detail)


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
    """`xcrun devicectl device <verb> <noun> --device <id> ...`: the device option goes before the
    positionals, because `process launch` passes everything after the bundle id to the app as its
    arguments (a trailing `--device` was swallowed and the launch refused)."""
    verb, noun, *rest = args
    return runner.run(["xcrun", "devicectl", "device", verb, noun, "--device", device.device_id, *rest], timeout=timeout)


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
    if device.kind == "simulator":
        # The tuner listens; a fresh install would ask for the microphone over the first journey.
        # A phone has no such switch: the runner answers the alert once per round (PerfJourneyTestCase).
        runner.run(["xcrun", "simctl", "privacy", device.device_id, "grant", "microphone", bundle], timeout=TOOL_TIMEOUT_S)


def warm_up(cfg: Config, device: Device, runner: Runner, clock: Clock) -> None:
    bundle = bundle_id_with_team(cfg)
    if device.kind == "simulator":
        runner.run(["xcrun", "simctl", "launch", device.device_id, bundle], timeout=TOOL_TIMEOUT_S)
        clock.sleep(8)
        runner.run(["xcrun", "simctl", "terminate", device.device_id, bundle], timeout=TOOL_TIMEOUT_S)
    else:
        with tempfile.TemporaryDirectory() as tmp:
            _res, pid = _launch(cfg, device, runner, os.path.join(tmp, "launch.json"))
        clock.sleep(8)
        if pid is not None:  # devicectl terminates by pid only
            _devicectl(cfg, device, runner, "process", "terminate", "--pid", str(pid))


def synthesized_inputs(device: Device) -> Tuple[bool, bool]:
    """Whether XCUITest synthesizes a pointer and ⌘ keys on [device]: both on an iPad; ⌘ keys on any simulator; neither on
    a physical iPhone ("Pointer events are not supported for this device"; typed ⌘2 opened nothing on the iPhone 14,
    where the iPhone simulator opens the Tuner, 2026-11-04)."""
    pad = "iPad" in device.model
    return pad, pad or device.kind == "simulator"


def test_without_building(cfg: Config, device: Device, runner: Runner, xctestrun: str, result_bundle: str,
                          log_path: str, only: Optional[str] = None,
                          classes: Optional[Sequence[str]] = None) -> Completed:
    """The measured run (`only=None`: the whole target minus the audio-trace pass and the cold-launch hitch driver,
    or just `classes`, the journeys' test classes of a subset round) or one named test."""
    cmd = ["xcodebuild", "test-without-building", "-xctestrun", xctestrun, "-destination", _destination(device),
           "-resultBundlePath", result_bundle]
    if only is not None:
        cmd += [f"-only-testing:{only}"]
    elif classes:
        cmd += [f"-only-testing:{cfg.ios.test_target}/{cls}" for cls in classes]
    else:
        cmd += [f"-only-testing:{cfg.ios.test_target}", f"-skip-testing:{cfg.ios.test_target}/{AUDIO_TRACE_TEST}",
                f"-skip-testing:{cfg.ios.test_target}/{COLD_LAUNCH_HITCH_TEST}",
                f"-skip-testing:{cfg.ios.test_target}/{RECORDING_SOAK_TEST}"]
    res = runner.run(cmd, cwd=_ios_dir(cfg), timeout=TEST_TIMEOUT_S)
    with open(log_path, "w") as fh:
        fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}\n--- stderr ---\n{res.stderr}")
    return res


_TRUST_MARKERS = ("not been explicitly trusted", "Developer App Certificate is not trusted",
                  "Developer App certificate is not trusted")


def _untrusted(res: Completed) -> bool:
    return res.returncode != 0 and any(m in res.stdout or m in res.stderr for m in _TRUST_MARKERS)


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
    # `--destination` names the file to write; a directory fails with "Is a directory" (verified on iOS 27.0.1).
    res = _devicectl(cfg, device, runner, "copy", "from", "--source", "Documents/engine-health.json",
                     "--domain-type", "appDataContainer", "--domain-identifier", bundle, "--destination", dest)
    return dest if res.returncode == 0 and os.path.isfile(dest) else None


def _launched_pid(launch_json: str) -> Optional[int]:
    try:
        with open(launch_json) as fh:
            return int(json.load(fh)["result"]["process"]["processIdentifier"])
    except (OSError, ValueError, KeyError, TypeError):
        return None


def _launch(cfg: Config, device: Device, runner: Runner, launch_json: str) -> "Tuple[Completed, Optional[int]]":
    """Launch the app on a phone (terminating a running copy) and learn its pid from devicectl's JSON."""
    bundle = bundle_id_with_team(cfg)
    res = _devicectl(cfg, device, runner, "process", "launch", "--terminate-existing", "--json-output", launch_json, bundle)
    return res, (_launched_pid(launch_json) if res.returncode == 0 else None)


def _audio_trace(cfg: Config, device: Device, runner: Runner, clock: Clock, round_dir: str,
                 xctestrun: str) -> Optional[str]:
    """The audio-trace pass (research spike 3, US5), after the measured runs on a phone: launch the app with
    `devicectl` so its engine is already running, record the HAL system-wide with xctrace's two audio instruments
    for a fixed time limit and drive one unmeasured stress journey through `AudioTracePass`, which attaches to the
    running process (and launches it if the `devicectl` launch failed). System-wide rather than `--attach <pid>`:
    the Xcode 27.1 beta's xctrace finds no process on an iOS 27.0.1 phone by pid or name ("Cannot find process
    for provided pid", every round of 2026-10-12), an attached recording with Xcode 26.6's xctrace reaches only the
    first 10 s of the HAL's stream, and neither fills the client-side table (`traces_ios`); the phone is otherwise
    idle, so the HAL's only client is the app. Never `--launch`, which hung in spike 3. The trace lands under the
    round as `audio.trace`; `traces_ios` reads the I/O-proc p99 from it. Simulators have no HAL: nothing to trace.
    A pass that fails costs the round nothing but the series."""
    if device.kind == "simulator":
        return None
    res, pid = _launch(cfg, device, runner, os.path.join(round_dir, "audio-trace-launch.json"))
    if pid is None:
        logger.warning("audio-trace pass: the app did not launch (%s); AudioTracePass launches it", res.stderr.strip())
    path = os.path.join(round_dir, "audio.trace")
    record = ["xcrun", "xctrace", "record"]
    for instrument in AUDIO_TRACE_INSTRUMENTS:
        record += ["--instrument", instrument]
    record += ["--device", device.device_id, "--all-processes", "--time-limit", f"{AUDIO_TRACE_SECONDS}s",
               "--no-prompt", "--output", path]
    outcome: Dict[str, Completed] = {}
    recorder = threading.Thread(target=lambda: outcome.update(record=runner.run(record, timeout=AUDIO_TRACE_SECONDS + TOOL_TIMEOUT_S)))
    recorder.start()
    clock.sleep(5)  # xctrace is recording before the journey's first tap
    log = os.path.join(round_dir, "xcodebuild-audio-pass.log")
    test_without_building(cfg, device, runner, xctestrun, os.path.join(round_dir, "audio-pass.xcresult"), log,
                          only=f"{cfg.ios.test_target}/{AUDIO_TRACE_TEST}")
    recorder.join()
    recorded = outcome.get("record")
    if recorded is None or recorded.returncode != 0 or not os.path.isdir(path):
        logger.warning("audio-trace pass: xctrace did not record (%s); no audio.trace this round",
                       (recorded.stderr.strip() if recorded else "no result"))
        return None
    return path


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
    classes = journey_test_classes(cfg, options.journeys)
    res = test_without_building(cfg, device, runner, xctestrun, result_bundle, log, classes=classes)
    # SpringBoard denies a runner launch as "not trusted" in two shapes: for good, until the developer profile is
    # trusted in Settings, and for a few minutes at a time on an otherwise trusted phone (2026-10-12: the same runner
    # products were denied at 11:37 and launched at 11:41, denied at 12:14 and 12:15 and launched at 12:21). A round
    # waits that out before it refuses; the attempts' logs and bundles stay under the round.
    for attempt in range(1, TRUST_RETRIES + 1):
        if not _untrusted(res):
            break
        logger.warning("the phone denied the runner as not trusted (attempt %d of %d); retrying in %d s",
                       attempt, TRUST_RETRIES + 1, TRUST_RETRY_WAIT_S)
        os.replace(log, os.path.join(round_dir, f"xcodebuild-test-attempt{attempt}.log"))
        shutil.rmtree(result_bundle, ignore_errors=True)
        clock.sleep(TRUST_RETRY_WAIT_S)
        res = test_without_building(cfg, device, runner, xctestrun, result_bundle, log, classes=classes)
    artifacts.append({"kind": "xcresult", "path": result_bundle})
    artifacts.append({"kind": "xcodebuild_log", "path": log})

    # A runner the phone will not launch is a device precondition, not a measurement: refuse, publish nothing.
    if _untrusted(res):
        raise Refused(checks + [Check("runner_trusted", False,
                                      "the TembraPerfTests runner's profile is not trusted on the phone: Settings → General → "
                                      "VPN & Device Management → trust the developer profile, then rerun")])
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
        pointer, keyboard = synthesized_inputs(device)
        collected = collect_round(metrics_json, cfg, simulator=simulator, iterations=options.iterations,
                                  pointer=pointer, keyboard=keyboard)
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
    trace = _audio_trace(cfg, device, runner, clock, round_dir, xctestrun) if options.audio_trace else None
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
