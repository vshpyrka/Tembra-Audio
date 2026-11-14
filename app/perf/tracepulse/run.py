"""The `run` command shared by both platforms: a session of one or more rounds on
one device, each a full round, stopping at the first refusal or failure; every
completed round becomes a record that is published, then the history is synced,
the cache rebuilt, detection and the gate recomputed and the report rendered.
A per-device lock file guards the whole invocation.
"""

import json
import os
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Tuple

from . import __version__
from .config import Config
from .devices import Device, DeviceError, attached, register, resolve
from .history import PublishError
from .orchestrate_android import BuildError, JourneyFailed, Refused, RoundOptions, RoundOutcome
from .records import DeviceInfo, JourneyResult, MetricResult, RoundRecord, instrumented_build, make_round_id
from .runner import Clock, Runner
from .scrub import default_scrub, machine_id
from .store import Store, split_series_key
from .text import gate_limit, gate_observed

EXIT_OK = 0
EXIT_REFUSED = 2
EXIT_FAILED = 3
EXIT_USAGE = 4
EXIT_PUBLISH = 5


# ============================================================
# the per-device lock
# ============================================================

class DeviceLock:
    def __init__(self, path: str):
        self.path = path

    def release(self) -> None:
        if os.path.exists(self.path):
            os.remove(self.path)


def _pid_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def acquire_lock(data_dir: str, device_id: str, label: str = "") -> DeviceLock:
    """Create `data/locks/<device_id>`; a live holder is a usage error naming its
    pid, label and start time; a dead holder's lock is taken over."""
    locks = os.path.join(data_dir, "locks")
    os.makedirs(locks, exist_ok=True)
    path = os.path.join(locks, device_id)
    if os.path.exists(path):
        try:
            with open(path) as fh:
                holder = json.load(fh)
        except (OSError, ValueError):
            holder = {}
        pid = int(holder.get("pid", 0) or 0)
        if pid and _pid_alive(pid):
            raise DeviceError(f"device {device_id} is busy: pid {pid} (label {holder.get('label', '?')!r}) "
                              f"started {holder.get('started_at', '?')}; wait for it or remove {path}")
    with open(path, "w") as fh:
        json.dump({"pid": os.getpid(), "label": label, "started_at": _now_iso()}, fh)
    return DeviceLock(path)


# ============================================================
# helpers
# ============================================================

def _now_iso(ts: Optional[int] = None) -> str:
    dt = datetime.fromtimestamp(ts, tz=timezone.utc) if ts is not None else datetime.now(timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def git_info(repo_root: str, runner: Runner) -> Dict[str, Any]:
    """`build.commit`, `build.branch`, `build.dirty` of the repository the round measures."""
    commit = runner.run(["git", "-C", repo_root, "rev-parse", "HEAD"], timeout=60).stdout.strip()
    branch = runner.run(["git", "-C", repo_root, "rev-parse", "--abbrev-ref", "HEAD"], timeout=60).stdout.strip()
    porcelain = runner.run(["git", "-C", repo_root, "status", "--porcelain"], timeout=60).stdout
    return {"commit": commit, "branch": branch, "dirty": bool(porcelain.strip())}


def _machine() -> Dict[str, str]:
    """`hostname` holds `machine_id()`: records are public, so they never name the machine."""
    return {"hostname": machine_id(), "os": _os_name(), "tool_version": __version__}


def _os_name() -> str:
    try:
        import platform
        return f"{platform.system()} {platform.release()}"
    except Exception:  # pragma: no cover - never fatal
        return "unknown"


def assemble_record(cfg: Config, device: Device, outcome: RoundOutcome, label: str, round_id: str,
                    started_at: str, finished_at: str, session: Dict[str, int], build: Dict[str, Any],
                    injected: Optional[List[str]] = None) -> RoundRecord:
    journeys = list(outcome.journeys)
    build = dict(build, type=outcome.build_type)
    if not instrumented_build(outcome.build_type):
        # the facade is compiled out: macrobenchmark would report every section as 0, which is not a value
        journeys = [JourneyResult(journey=j.journey, iterations=j.iterations,
                                  metrics={k: v for k, v in j.metrics.items() if not k.startswith("section.")})
                    for j in journeys]
    if outcome.app_size_bytes is not None and outcome.status == "succeeded":
        build["app_size_bytes"] = outcome.app_size_bytes
        size = MetricResult(unit="bytes", samples=[], source="apk" if device.platform == "android" else "thinning-report",
                            value=float(outcome.app_size_bytes))
        # A round-level metric: it rides the startup journey entry (the store keys it per device, not per journey).
        startup = next((i for i, j in enumerate(journeys) if j.journey == "startup"), None)
        if startup is None:
            journeys.append(JourneyResult(journey="startup", iterations=1, metrics={"app.size_bytes": size}))
        else:
            merged = dict(journeys[startup].metrics)
            merged["app.size_bytes"] = size
            journeys[startup] = JourneyResult(journey="startup", iterations=journeys[startup].iterations, metrics=merged)
    if outcome.__dict__.get("app_size_uncompressed_bytes") is not None:
        build["app_size_uncompressed_bytes"] = outcome.app_size_uncompressed_bytes
    if injected:
        build["injected"] = list(injected)
    badges = list(outcome.badges)
    if device.kind == "simulator" and "simulator" not in badges:
        badges.append("simulator")
    fingerprint = {k: v for k, v in outcome.fingerprint.items() if k != "model"}
    record = RoundRecord(
        round_id=round_id,
        device=DeviceInfo(device_id=device.device_id, platform=device.platform, kind=device.kind,
                          model=device.model or outcome.fingerprint.get("model", device.device_id),
                          reference=device.reference),
        label=label, build=build, fingerprint=fingerprint, machine=_machine(), checks=list(outcome.checks),
        status=outcome.status, journeys=journeys, started_at=started_at, finished_at=finished_at,
        session=dict(session), failure_reason=outcome.failure_reason, badges=badges,
        gate=[], artifacts=list(outcome.artifacts),
    )
    # perf-history is public: no host name and no home-directory path leave this function
    return RoundRecord.from_dict(default_scrub(record.to_dict(), _repo_root(cfg)))


# ============================================================
# the session
# ============================================================

RoundFn = Callable[[Config, Device, Runner, Clock, str, RoundOptions], RoundOutcome]


PrepareFn = Callable[[Config, Device, Runner, str, Any], Any]


@dataclass
class SessionDeps:
    """The seams `run_command` wires to the real platform round, history and report.
    `prepare_fn` builds once per session and returns the per-round options that reuse the build."""
    round_fn: RoundFn
    history: Any
    report_fn: Callable[[Store, Config, str, int], str]
    detect_fn: Callable[[Store, Config], Dict[str, Any]]
    gate_fn: Optional[Callable[[Store, Config, Device, RoundRecord], List[Dict[str, Any]]]] = None
    injected_fn: Optional[Callable[[], List[str]]] = None
    prepare_fn: Optional[PrepareFn] = None


@dataclass
class SessionResult:
    exit_status: int = EXIT_OK
    records: List[RoundRecord] = field(default_factory=list)
    published: List[str] = field(default_factory=list)
    report_path: Optional[str] = None
    refused: Optional[str] = None
    failed: Optional[str] = None
    publish_error: Optional[str] = None
    flagged: List[Dict[str, Any]] = field(default_factory=list)
    lines: List[str] = field(default_factory=list)


def _save_local(data_dir: str, record: RoundRecord) -> str:
    path = os.path.join(data_dir, "rounds", record.round_id, "record.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(record.to_json())
    return path


def run_session(cfg: Config, paths, device: Device, label: str, rounds: int, options: RoundOptions,
                runner: Runner, clock: Clock, deps: SessionDeps, publish: bool = True,
                report_out: Optional[str] = None) -> SessionResult:
    result = SessionResult()
    lock = acquire_lock(paths.data_dir, device.device_id, label)
    try:
        os.makedirs(paths.data_dir, exist_ok=True)
        # The gate baseline comes from the records already on this machine; the pull happens after publishing.
        records = deps.history.records()
        store = Store.rebuild(paths.db_path, records)
        build = git_info(_repo_root(cfg), runner)
        injected = deps.injected_fn() if deps.injected_fn else []
        if deps.prepare_fn is not None:
            session_dir = os.path.join(paths.data_dir, "sessions", make_round_id(datetime.fromtimestamp(clock.now(), tz=timezone.utc), label))
            try:
                options = deps.prepare_fn(cfg, device, runner, session_dir, options)
            except Refused as exc:
                result.refused = str(exc)
                result.exit_status = EXIT_REFUSED
                print(f"  refused: {exc}")
                return result
            except BuildError as exc:
                result.failed = str(exc)
                result.exit_status = EXIT_FAILED
                print(f"  build failed: {exc}")
                return result
        last_started: Optional[int] = None
        for index in range(1, rounds + 1):
            started_ts = clock.now()
            if last_started is not None and started_ts <= last_started:
                clock.sleep(1)  # round ids carry the start second; never reuse one
                started_ts = clock.now()
            last_started = started_ts
            started_at = _now_iso(started_ts)
            round_id = make_round_id(datetime.fromtimestamp(started_ts, tz=timezone.utc), label)
            round_dir = os.path.join(paths.data_dir, "rounds", round_id)
            session = {"index": index, "size": rounds}
            print(f"round {index}/{rounds} {round_id} on {device.model} ({device.device_id})")
            try:
                outcome = deps.round_fn(cfg, device, runner, clock, round_dir, options)
                exit_status = EXIT_OK
            except Refused as exc:
                result.refused = str(exc)
                result.exit_status = EXIT_REFUSED
                print(f"  refused: {exc}")
                break
            except JourneyFailed as exc:
                outcome = exc.outcome
                result.failed = exc.reason
                exit_status = EXIT_FAILED
                print(f"  failed: {exc.reason}")
            except BuildError as exc:
                result.failed = str(exc)
                result.exit_status = EXIT_FAILED
                print(f"  build failed: {exc}")
                break
            record = assemble_record(cfg, device, outcome, label, round_id, started_at, _now_iso(clock.now()),
                                     session, build, injected)
            if deps.gate_fn is not None and record.status == "succeeded":
                record.gate = deps.gate_fn(store, cfg, device, record)
                result.flagged += [dict(g, round_id=round_id, unit=_unit(device.platform, g["metric"]))
                                   for g in record.gate if g.get("state") == "above_limit"]
            _save_local(paths.data_dir, record)
            if publish:
                try:
                    rel = deps.history.publish(record)
                    result.published.append(rel)
                    print(f"  published: {cfg.history.branch}/{rel}")
                except PublishError as exc:
                    result.publish_error = str(exc)
                    print(f"  publish failed (record kept locally): {exc}")
            result.records.append(record)
            store.add_record(record)
            _print_round(record, result)
            if exit_status != EXIT_OK:
                result.exit_status = exit_status
                break
        if result.records or result.refused is None:
            store.close()
            records = deps.history.sync() if publish else records + result.records
            if not publish:
                records = list(records)
            store = Store.rebuild(paths.db_path, records if publish else _merge(records, result.records))
            detection = deps.detect_fn(store, cfg)
            result.lines += detection.get("lines", [])
            out = report_out or os.path.join(paths.reports_dir, "report.html")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            result.report_path = deps.report_fn(store, cfg, out, int(time.time()))
            print(f"  report: {result.report_path}")
        store.close()
        if result.publish_error and result.exit_status == EXIT_OK:
            result.exit_status = EXIT_PUBLISH
        return result
    finally:
        lock.release()


def _merge(records: List[RoundRecord], local: List[RoundRecord]) -> List[RoundRecord]:
    seen = {r.round_id for r in records}
    return list(records) + [r for r in local if r.round_id not in seen]


def _repo_root(cfg: Config) -> str:
    path = os.path.abspath(cfg.root)
    while True:
        if os.path.exists(os.path.join(path, ".git")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            return os.path.abspath(cfg.root)
        path = parent


def _unit(platform: str, series: str) -> str:
    from .catalogue import CatalogueError, lookup
    try:
        return lookup(platform, split_series_key(series)[1]).unit
    except CatalogueError:
        return ""


def _flag_text(flag: Dict[str, Any], unit: str) -> str:
    return f"{flag['metric']} {gate_observed(flag, unit)} (limit {gate_limit(flag, unit)})"


def _print_round(record: RoundRecord, result: SessionResult) -> None:
    journeys = " ".join(f"{j.journey} ✓" for j in record.journeys if j.journey != "startup" or "startup.ttid_ms" in j.metrics)
    iterations = max((j.iterations for j in record.journeys), default=0)
    size = record.build.get("app_size_bytes")
    size_text = f"{size} bytes" if size is not None else "skipped (use --size)"
    fp = record.fingerprint
    print(f"  status: {record.status}" + (f" ({record.failure_reason})" if record.failure_reason else ""))
    print(f"  journeys: {journeys or '—'}   iterations: {iterations}   size: {size_text}")
    print(f"  badges: {', '.join(record.badges) or '—'}   network: {fp.get('network_state', '?')}   "
          f"thermal: {fp.get('thermal_state', fp.get('battery_temp_c', '?'))}   battery: {fp.get('battery_level', '?')}")
    flagged = [g for g in record.gate if g.get("state") == "above_limit"]
    if flagged:
        print("  gate: %d above limit — %s" % (len(flagged), ", ".join(
            _flag_text(g, _unit(record.device.platform, g["metric"])) for g in flagged)))
    else:
        inactive = [g for g in record.gate if g.get("state") == "inactive"]
        if record.gate:
            print(f"  gate: within limits ({len(inactive)} inactive)")


# ============================================================
# the CLI entry
# ============================================================

def run_command(args, cfg: Config, paths, runner: Runner, clock: Clock) -> Tuple[int, Dict[str, Any]]:
    from .__main__ import make_history
    from .analysis import analyze_all
    from .report import generate_report

    history = make_history(cfg, paths)
    devices = attached(runner, adb=cfg.android.adb, devicectl_output=os.path.join(paths.data_dir, "devicectl.json"))
    if args.simulators:
        targets = [d for d in devices if d.platform == "ios" and d.kind == "simulator"]
        if not targets:
            raise DeviceError("--simulators: no booted iOS simulator (xcrun simctl list devices booted)")
    else:
        targets = [resolve(cfg, args.device, devices)]
    added = register(cfg, targets)
    if added:
        print(f"registered new device(s) {', '.join(added)} in {cfg.path}")
    targets = [resolve(cfg, d.device_id, devices) for d in targets]

    journeys = [j.strip() for j in args.journeys.split(",")] if args.journeys else list(cfg.journeys)
    unknown = [j for j in journeys if j not in cfg.journeys]
    if unknown:
        raise DeviceError(f"unknown journey {unknown[0]!r} (configured: {', '.join(cfg.journeys)})")
    rounds = args.rounds if args.rounds and args.rounds > 0 else 1
    iterations = args.iterations or cfg.collection.iterations

    def gate_fn(store, cfg_, device, record):
        from .gate import decide_round
        return decide_round(store, cfg_, device, record)

    def detect_fn(store, cfg_):
        from .analysis import analyze_all as _analyze
        from .gate import record_all_gates
        summary = _analyze(store, cfg_)
        record_all_gates(store, cfg_)
        return summary

    def injected_fn():
        from .inject import applied_injections
        return applied_injections(paths.data_dir)

    payload: Dict[str, Any] = {"sessions": []}
    worst = EXIT_OK
    for device in targets:
        options = round_options(args, cfg, device.platform, journeys, iterations)
        if device.platform == "android":
            from .orchestrate_android import run_round as round_fn
            prepare_fn = _prepare_android
        else:
            from .orchestrate_ios import run_round as round_fn
            prepare_fn = _prepare_ios
        deps = SessionDeps(round_fn=round_fn, history=history, report_fn=generate_report, detect_fn=detect_fn,
                           gate_fn=_safe(gate_fn), injected_fn=_safe_list(injected_fn), prepare_fn=prepare_fn)
        result = run_session(cfg, paths, device, args.label, rounds, options, runner, clock, deps,
                             publish=not args.no_publish)
        payload["sessions"].append({
            "device": device.device_id, "exit_status": result.exit_status,
            "records": [r.round_id for r in result.records], "published": result.published,
            "refused": result.refused, "failed": result.failed, "flagged": result.flagged,
            "report": result.report_path,
        })
        if result.flagged:
            print(f"gate: {len(result.flagged)} above limit across the session — " + ", ".join(
                f"{g['round_id']} {_flag_text(g, g.get('unit', ''))}" for g in result.flagged))
        worst = max(worst, result.exit_status) if result.exit_status != EXIT_OK else worst
        if result.exit_status in (EXIT_REFUSED, EXIT_FAILED) and not args.simulators:
            break
    return worst, payload


def _safe(fn):
    """A gate seam that tolerates the gate module not being built yet (no decisions)."""
    def wrapped(*a, **k):
        try:
            return fn(*a, **k)
        except ImportError:
            return []
    return wrapped


def _safe_list(fn):
    def wrapped():
        try:
            return fn()
        except ImportError:
            return []
    return wrapped


def size_command(args, cfg: Config, paths, device: Device, runner: Runner) -> Tuple[int, Dict[str, Any]]:
    if device.platform == "android":
        from .orchestrate_android import apk_paths
        app, _test = apk_paths(cfg, cfg.android.build_type)
        if not os.path.isfile(app):
            raise DeviceError(f"{app} not found; build it first (`./gradlew -Ptembra.perf=true :androidApp:assemblePerf`)")
        size = os.path.getsize(app)
        print(f"app.size_bytes: {size} ({app})")
        return EXIT_OK, {"device": device.device_id, "app_size_bytes": size, "path": app}
    from .size_ios import measure_size
    result = measure_size(cfg, paths, device, runner)
    print(f"app.size_bytes: {result['app_size_bytes']}   app.size_uncompressed_bytes: {result['app_size_uncompressed_bytes']}")
    return EXIT_OK, dict(result, device=device.device_id)


def _absolute(path: Optional[str]) -> Optional[str]:
    return os.path.abspath(path) if path else path


def round_options(args, cfg: Config, platform: str, journeys: List[str], iterations: int):
    """The round options for one platform from the `run` arguments. Prebuilt
    artifacts (`--apk`, `--test-apk`, `--xctestrun`) are given relative to the
    caller's cwd while the tools run from the app directories, so they are made
    absolute here."""
    if platform == "android":
        return RoundOptions(journeys=journeys, iterations=iterations, no_build=args.no_build,
                            apk=_absolute(args.apk), test_apk=_absolute(args.test_apk),
                            build_type=args.build_type or cfg.android.build_type, keep_build=args.keep_build)
    from .orchestrate_ios import IosRoundOptions
    return IosRoundOptions(journeys=journeys, iterations=iterations, no_build=args.no_build,
                           xctestrun=_absolute(args.xctestrun),
                           configuration=args.configuration or cfg.ios.configuration,
                           size=args.size, keep_build=args.keep_build)


def _prepare_android(cfg: Config, device: Device, runner: Runner, session_dir: str, options: RoundOptions) -> RoundOptions:
    """One measurement build per session; every round then installs the same artifacts."""
    if options.no_build:
        return options
    from .orchestrate_android import build, perf_property
    os.makedirs(session_dir, exist_ok=True)
    log = os.path.join(session_dir, "gradle.log")
    print(f"building {options.build_type} artifacts (./gradlew --console=plain {perf_property(options.build_type)}), "
          f"log in {log}")
    app_apk, test_apk = build(cfg, runner, options.build_type, log, java_home=None)
    return RoundOptions(journeys=options.journeys, iterations=options.iterations, no_build=True, apk=app_apk,
                        test_apk=test_apk, build_type=options.build_type, keep_build=options.keep_build)


def _prepare_ios(cfg: Config, device: Device, runner: Runner, session_dir: str, options):
    """One `xcodebuild build-for-testing` per session; every round reuses its xctestrun."""
    if options.no_build or options.xctestrun:
        return options
    from .orchestrate_ios import IosRoundOptions, build_for_testing, doctor
    os.makedirs(session_dir, exist_ok=True)
    checks = doctor(cfg, device, runner, scratch_dir=session_dir)
    if any(not c.ok for c in checks):
        raise Refused(checks)  # a device precondition is cheaper to learn before the build
    derived = os.path.join(session_dir, "DerivedData")
    log = os.path.join(session_dir, "xcodebuild-build.log")
    print(f"building for testing (xcodebuild build-for-testing -configuration {options.configuration}), log in {log}")
    xctestrun = build_for_testing(cfg, device, runner, derived, options.configuration, log)
    return IosRoundOptions(journeys=options.journeys, iterations=options.iterations, no_build=True, xctestrun=xctestrun,
                           configuration=options.configuration, size=options.size, keep_build=options.keep_build,
                           audio_trace=options.audio_trace)
