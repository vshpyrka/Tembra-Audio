"""`tracepulse` command line (specs/007-performance-pipeline/contracts/cli.md).

Subcommands: devices, doctor, run, size, publish, sync, analyze, report, status,
inject, revert, and the baseline-profile tools `profile generate | compare | verify`
(feature 008, specs/008-baseline-profiles/contracts/cli.md), which share the device
registry, the device protocol and the build step and never write to the history.
Exit statuses: 0 ok (gate flags never change it); 2 refused (a device check failed
before measuring); 3 round failed (measurement started and did not complete); 4
usage or configuration error; 5 publish failed (the record is kept locally;
`tracepulse publish` retries). `--json` prints one object.
"""

import argparse
import contextlib
import json
import os
import socket
import sys
import time
import webbrowser
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional, Tuple

from . import __version__
from .config import DEFAULT_CONFIG_PATH, PERF_DIR, Config, ConfigError, load_config
from .devices import DeviceError, attached, register, resolve
from .history import History, HistoryError, PublishError
from .runner import SystemClock, SystemRunner
from .store import Store

EXIT_OK = 0
EXIT_REFUSED = 2
EXIT_FAILED = 3
EXIT_USAGE = 4
EXIT_PUBLISH = 5

Payload = Dict[str, Any]


class Refused(Exception):
    """A device check failed before measuring (exit 2)."""


class RoundFailed(Exception):
    """Measurement started and did not complete (exit 3)."""


@dataclass(frozen=True)
class Paths:
    data_dir: str
    db_path: str
    reports_dir: str
    registry_path: str
    worktree: str


def resolve_paths(cfg: Config, perf_dir: Optional[str] = None) -> Paths:
    """data/, reports/ and regressions/ live under app/perf; the history
    worktree where tracepulse.yml says (relative to the Gradle root)."""
    perf_dir = perf_dir or PERF_DIR
    data_dir = os.path.join(perf_dir, "data")
    worktree = cfg.history.worktree
    if not os.path.isabs(worktree):
        worktree = os.path.join(cfg.root, worktree)
    return Paths(
        data_dir=data_dir,
        db_path=os.path.join(data_dir, "tracepulse.db"),
        reports_dir=os.path.join(perf_dir, "reports"),
        registry_path=os.path.join(perf_dir, "regressions", "registry.yaml"),
        worktree=worktree,
    )


def make_history(cfg: Config, paths: Paths) -> History:
    repo_root = _git_root(cfg.root)
    return History(repo_root=repo_root, worktree=paths.worktree, branch=cfg.history.branch,
                   remote=cfg.history.remote, data_dir=paths.data_dir)


def _git_root(start: str) -> str:
    """The repository root above `start` (the directory holding `.git`)."""
    path = os.path.abspath(start)
    while True:
        if os.path.exists(os.path.join(path, ".git")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            return os.path.abspath(start)
        path = parent


# ============================================================
# Subcommand handlers: each returns (exit status, payload)
# ============================================================

def _handle_devices(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    runner = SystemRunner()
    devices = attached(runner, adb=cfg.android.adb, devicectl_output=os.path.join(paths.data_dir, "devicectl.json"))
    rows = []
    for dev in devices:
        known = dev.device_id in cfg.devices
        entry = cfg.devices.get(dev.device_id)
        rows.append({
            "device_id": dev.device_id, "platform": dev.platform, "kind": dev.kind,
            "model": entry.model if entry and entry.model else dev.model,
            "alias": entry.alias if entry else "", "reference": entry.reference if entry else dev.kind == "device",
            "registered": known, "os_version": dev.os_version,
        })
    attached_ids = {d.device_id for d in devices}
    for device_id, entry in cfg.devices.items():
        if device_id not in attached_ids:
            rows.append({"device_id": device_id, "platform": entry.platform, "kind": entry.kind,
                         "model": entry.model, "alias": entry.alias, "reference": entry.reference,
                         "registered": True, "os_version": "", "attached": False})
    for row in rows:
        row.setdefault("attached", True)
        state = "attached" if row["attached"] else "not attached"
        mark = "" if row["registered"] else "  (new: added to the registry on first run)"
        alias = f" alias={row['alias']}" if row["alias"] else ""
        print(f"{row['device_id']:28} {row['platform']:8} {row['kind']:10} {row['model']:28} {state}{alias}{mark}")
    if not rows:
        print("no devices attached and none registered")
    return EXIT_OK, {"devices": rows}


def _select_device(args, cfg: Config, paths: Paths):
    runner = SystemRunner()
    devices = attached(runner, adb=cfg.android.adb, devicectl_output=os.path.join(paths.data_dir, "devicectl.json"))
    device = resolve(cfg, args.device, devices)
    added = register(cfg, [device])
    if added:
        print(f"registered new device {device.device_id} ({device.model}) in {cfg.path}")
    return resolve(cfg, device.device_id, devices)


def _handle_doctor(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    device = _select_device(args, cfg, paths)
    if device.platform == "android":
        from .orchestrate_android import doctor
        checks = doctor(cfg, device, SystemRunner())
    else:
        from .orchestrate_ios import doctor
        checks = doctor(cfg, device, SystemRunner())
    for check in checks:
        print(f"[{'ok' if check.ok else 'FAIL'}] {check.name} — {check.detail}")
    failing = [c for c in checks if not c.ok]
    payload = {"device": device.device_id, "checks": [c.__dict__ for c in checks]}
    return (EXIT_REFUSED if failing else EXIT_OK), payload


def _handle_run(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    if not args.label:
        raise DeviceError("run needs --label <feature> (the label every record of this session carries)")
    if not args.device and not args.simulators:
        raise DeviceError("run needs --device <id|alias> or --simulators")
    if args.device and args.simulators:
        raise DeviceError("--device and --simulators are exclusive")
    from .run import run_command
    return run_command(args, cfg, paths, SystemRunner(), SystemClock())


def _handle_size(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    device = _select_device(args, cfg, paths)
    from .run import size_command
    return size_command(args, cfg, paths, device, SystemRunner())


def _handle_publish(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    history = make_history(cfg, paths)
    pending = history.pending()
    if args.device:
        wanted = cfg.alias_to_id(args.device) or args.device
        pending = [r for r in pending if r.device.device_id == wanted]
    if not pending:
        print("nothing to publish")
        return EXIT_OK, {"published": []}
    published = []
    for record in pending:
        rel = history.publish(record, _parked=True)
        published.append(rel)
        print(f"published {cfg.history.branch}/{rel}")
    return EXIT_OK, {"published": published}


def _rebuild(cfg: Config, paths: Paths) -> Tuple[Store, int]:
    history = make_history(cfg, paths)
    records = history.sync()
    os.makedirs(paths.data_dir, exist_ok=True)
    store = Store.rebuild(paths.db_path, records)
    return store, len(records)


def _handle_sync(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    store, count = _rebuild(cfg, paths)
    with store:
        devices = [dict(d) for d in store.devices()]
    print(f"synced {count} records from {cfg.history.remote}/{cfg.history.branch} into {paths.db_path}")
    for d in devices:
        print(f"  {d['device_id']:28} {d['platform']:8} {d['kind']:10} {d['model']:28} rounds={d['rounds']}")
    return EXIT_OK, {"records": count, "devices": devices, "db": paths.db_path}


def _handle_analyze(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    from .analysis import analyze_all
    from .gate import record_all_gates
    from .inject import applied_injections, expected_lookup_factory, load_registry
    store, _count = _rebuild(cfg, paths)
    with store:
        expected = None
        if os.path.isfile(paths.registry_path):
            registry = load_registry(paths.registry_path)
            expected = expected_lookup_factory(registry, lambda round_id: store.injected(round_id))
        summary = analyze_all(store, cfg, paths, expected_lookup=expected)
        summary["gate_decisions"] = record_all_gates(store, cfg)
    for line in summary["lines"]:
        print(line)
    print(f"gate decisions recorded: {summary['gate_decisions']}; injections applied now: {applied_injections(paths.data_dir) or '—'}")
    return EXIT_OK, summary


def _handle_report(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    from .report import generate_report
    os.makedirs(paths.data_dir, exist_ok=True)
    os.makedirs(paths.reports_dir, exist_ok=True)
    out = args.out or os.path.join(paths.reports_dir, "report.html")
    with Store(paths.db_path) as store:
        written = generate_report(store, cfg, out, now_ts=int(time.time()))
    print(written)
    if args.open:
        webbrowser.open(f"file://{os.path.abspath(written)}")
    return EXIT_OK, {"report": written}


def _handle_status(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    os.makedirs(paths.data_dir, exist_ok=True)
    with Store(paths.db_path) as store:
        rounds = [dict(r) for r in store.rounds()]
        devices = [dict(d) for d in store.devices()]
    history = make_history(cfg, paths)
    unpublished = [r.round_id for r in history.pending()]
    branch = {"branch": cfg.history.branch, "worktree": paths.worktree,
              "present": os.path.isdir(paths.worktree)}
    payload = {"rounds": rounds, "devices": devices, "unpublished": unpublished, "history": branch,
               "db": paths.db_path, "machine": socket.gethostname()}
    print(f"history: {branch['branch']} at {branch['worktree']} ({'present' if branch['present'] else 'absent'})")
    print(f"rounds in the cache: {len(rounds)} across {len(devices)} devices")
    for d in devices:
        print(f"  {d['device_id']:28} {d['model']:28} rounds={d['rounds']}")
    print(f"unpublished records: {len(unpublished)}")
    for round_id in unpublished:
        print(f"  {round_id}")
    if getattr(args, "series", False):
        from .analysis import series_variation
        with Store(paths.db_path) as store:
            variation = series_variation(store, getattr(args, "label", None), getattr(args, "last", None))
        payload["series"] = variation
        for row in variation:
            print(f"  {row['device_id']:28} {row['metric']:40} rounds={row['rounds']} cov_pct={row['cov_pct']:.2f}")
    return EXIT_OK, payload


def _handle_inject(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    from .inject import inject
    inject(cfg, SystemRunner(), args.regression, paths.data_dir, paths.registry_path)
    print(f"injected {args.regression}")
    return EXIT_OK, {"injected": args.regression}


def _handle_revert(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    from .inject import revert
    target = "--all" if args.all else args.target
    revert(cfg, SystemRunner(), target, paths.data_dir, paths.registry_path)
    print(f"reverted {target}")
    return EXIT_OK, {"reverted": target}


_PROFILE_BUILD_TYPES = ("perf", "release")


def _handle_profile(args, cfg: Config, paths: Paths) -> Tuple[int, Payload]:
    """`profile generate | compare | verify` (feature 008): argument checks that need only the
    configuration happen here, as `run` does for `--label`; the commands own the rest."""
    if args.profile_command == "compare" and args.journeys:
        journeys = [j.strip() for j in args.journeys.split(",")]
        unknown = [j for j in journeys if j not in cfg.journeys]
        if unknown:
            raise DeviceError(f"unknown journey {unknown[0]!r} (configured: {', '.join(cfg.journeys)})")
    if args.profile_command == "verify" and args.build_type not in _PROFILE_BUILD_TYPES:
        raise DeviceError(f"profile verify inspects a release-like build: --build-type must be one of "
                          f"{', '.join(_PROFILE_BUILD_TYPES)}, not {args.build_type!r}")
    runner, clock = SystemRunner(), SystemClock()
    if args.profile_command == "generate":
        from .profile_generate import main as generate
        return generate(args, cfg, paths, runner, clock)
    if args.profile_command == "compare":
        from .profile_compare import main as compare
        return compare(args, cfg, paths, runner, clock)
    from .profile_verify import main as verify
    return verify(args, cfg, paths, runner, clock)


HANDLERS: Dict[str, Callable[[Any, Config, Paths], Tuple[int, Payload]]] = {
    "devices": _handle_devices, "doctor": _handle_doctor, "run": _handle_run, "size": _handle_size,
    "publish": _handle_publish, "sync": _handle_sync, "analyze": _handle_analyze,
    "report": _handle_report, "status": _handle_status, "inject": _handle_inject,
    "revert": _handle_revert, "profile": _handle_profile,
}


# ============================================================
# Parser and entry point
# ============================================================

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="tracepulse",
                                     description="Tembra Audio performance pipeline (feature 007)")
    parser.add_argument("-c", "--config", default=DEFAULT_CONFIG_PATH,
                        help=f"configuration file (default: {DEFAULT_CONFIG_PATH})")
    parser.add_argument("--json", action="store_true", help="print one JSON object instead of text")
    parser.add_argument("--version", action="version", version=f"tracepulse {__version__}")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("devices", help="attached devices (adb, devicectl, booted simulators) and the registry")

    doctor = sub.add_parser("doctor", help="device protocol checks only; exit 0 or 2")
    doctor.add_argument("--device", help="serial, UDID or registry alias")

    run = sub.add_parser("run", help="one complete round, or a session with --rounds")
    run.add_argument("--device", help="serial, UDID or registry alias")
    run.add_argument("--simulators", action="store_true", help="every booted iOS simulator, sequentially")
    run.add_argument("--label", help="feature label carried by every record (required)")
    run.add_argument("--rounds", type=int, default=1, help="rounds in this session (default 1)")
    run.add_argument("--journeys", help="comma-separated subset of the configured journeys")
    run.add_argument("--iterations", type=int, help="iterations per journey (default from tracepulse.yml)")
    run.add_argument("--no-publish", action="store_true", help="skip the push to the history branch")
    run.add_argument("--keep-build", action="store_true", help="keep the build directory after the round")
    run.add_argument("--size", action="store_true", help="also measure app size (iOS: archive + thinning report)")
    run.add_argument("--no-build", action="store_true", help="reuse artifacts: --apk/--test-apk or --xctestrun")
    run.add_argument("--apk", help="Android app APK built elsewhere (with --no-build)")
    run.add_argument("--test-apk", help="Android benchmarks APK built elsewhere (with --no-build)")
    run.add_argument("--xctestrun", help="iOS build-for-testing output (with --no-build)")
    run.add_argument("--build-type", help="Android build type to measure (default perf; release for SC-007)")
    run.add_argument("--configuration", help="iOS configuration to measure (default Perf; Release for SC-007)")

    size = sub.add_parser("size", help="app size only (Android APK bytes; iOS archive + thinning report)")
    size.add_argument("--device", help="serial, UDID or registry alias")

    publish = sub.add_parser("publish", help="push unpublished local records to the history branch")
    publish.add_argument("--device", help="only this device's records")

    sub.add_parser("sync", help="pull the history branch and rebuild data/tracepulse.db")
    sub.add_parser("analyze", help="recompute detections and gate decisions over the rebuilt history")

    report = sub.add_parser("report", help="render reports/report.html")
    report.add_argument("--open", action="store_true", help="open the report in the default browser")
    report.add_argument("--out", help="output path (default reports/report.html)")

    status = sub.add_parser("status", help="local rounds, unpublished records, history branch state")
    status.add_argument("--series", action="store_true",
                        help="also print the coefficient of variation of round medians per series")
    status.add_argument("--label", help="with --series: only rounds of this label (one session)")
    status.add_argument("--last", type=int, help="with --series: only the most recent N rounds per device")

    inject = sub.add_parser("inject", help="apply a regression-injection patch (self-test)")
    inject.add_argument("regression", help="regression id from regressions/registry.yaml (R1..R4)")

    revert = sub.add_parser("revert", help="reverse one injection or all of them")
    group = revert.add_mutually_exclusive_group(required=True)
    group.add_argument("target", nargs="?", help="regression id")
    group.add_argument("--all", action="store_true", help="revert every applied injection")

    profile = sub.add_parser("profile", help="baseline profiles: generate, compare, verify (never a round)")
    profile_sub = profile.add_subparsers(dest="profile_command", required=True,
                                         metavar="{generate,compare,verify}")
    generate = profile_sub.add_parser("generate", help="generate baseline-prof.txt and startup-prof.txt on a device")
    generate.add_argument("--device", help="serial or registry alias of an Android device (API 33+)")
    generate.add_argument("--keep-build", action="store_true", help="keep the generation build directory")
    compare = profile_sub.add_parser("compare", help="measure one build with no profile vs the shipped profile")
    compare.add_argument("--device", help="serial or registry alias of an Android device")
    compare.add_argument("--with-full", action="store_true", help="also measure the fully optimised state")
    compare.add_argument("--iterations", type=int, help="iterations per journey and state (default from tracepulse.yml)")
    compare.add_argument("--journeys", help="comma-separated subset of the configured journeys")
    compare.add_argument("--no-build", action="store_true", help="reuse artifacts: --apk and --test-apk")
    compare.add_argument("--apk", help="Android app APK built elsewhere (with --no-build)")
    compare.add_argument("--test-apk", help="Android benchmarks APK built elsewhere (with --no-build)")
    compare.add_argument("--keep-build", action="store_true", help="keep the build directory after the comparison")
    verify = profile_sub.add_parser("verify", help="check an installed build recognises and compiles its profile")
    verify.add_argument("--device", help="serial or registry alias of an Android device")
    verify.add_argument("--apk", help="app APK to verify instead of building one")
    verify.add_argument("--build-type", default="perf", help="perf (default, installable) or release (APK inspection only)")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    # Rounds take minutes; a log file or an SSH pipe should show each line as it happens.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(line_buffering=True)
    try:
        cfg = load_config(args.config)
    except (ConfigError, FileNotFoundError, OSError) as exc:
        print(f"configuration error in {args.config}: {exc}", file=sys.stderr)
        return EXIT_USAGE
    paths = resolve_paths(cfg)
    handler = HANDLERS[args.command]
    payload: Payload = {"command": args.command}
    # With --json the human-readable progress lines go to stderr so stdout is one object.
    human_out = sys.stderr if args.json else sys.stdout
    try:
        with contextlib.redirect_stdout(human_out):
            code, result = handler(args, cfg, paths)
        payload.update(result)
    except (DeviceError, ConfigError) as exc:
        code = EXIT_USAGE
        payload["error"] = str(exc)
        print(f"error: {exc}", file=sys.stderr)
    except Refused as exc:
        code = EXIT_REFUSED
        payload["error"] = str(exc)
        print(f"refused: {exc}", file=sys.stderr)
    except RoundFailed as exc:
        code = EXIT_FAILED
        payload["error"] = str(exc)
        print(f"round failed: {exc}", file=sys.stderr)
    except (PublishError, HistoryError) as exc:
        code = EXIT_PUBLISH
        payload["error"] = str(exc)
        print(f"publish failed: {exc}", file=sys.stderr)
    except ImportError as exc:
        code = EXIT_USAGE
        payload["error"] = f"{args.command} is not available in this build: {exc}"
        print(payload["error"], file=sys.stderr)
    payload["exit_status"] = code
    if args.json:
        print(json.dumps(payload, default=str, sort_keys=True))
    return code


if __name__ == "__main__":
    sys.exit(main())
