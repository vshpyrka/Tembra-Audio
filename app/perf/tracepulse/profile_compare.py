"""`tracepulse profile compare` (feature 008, specs/008-baseline-profiles/contracts/cli.md, D6).

One build measured on one device in the optimisation states `none` and `profile` (and `full`
with --with-full), each entered from a fresh install: the device protocol (`doctor`), the
pipeline's build step, then per state an uninstall, an install of both APKs and one detached
`am instrument` of `ProfileComparisonBenchmark` with the state as an instrumentation argument,
polled as a round is; the pulled benchmarkData.json files are collected with the round's
catalogue mapping; per metric the medians, the signed change `profile` vs `none`, its
direction, the detector's two-sided Mann–Whitney p-value (confirmed at alpha 0.01) and, with
`full`, the share of the none→full gap the profile closes. Written to
`data/comparisons/<id>/comparison.json` (validated against schema/comparison.schema.json) and
`comparison.md`. Informational: exit 0 whatever the numbers say; nothing enters the history,
the records or the store.
"""

import json
import os
import socket
import statistics
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Sequence, Tuple

import jsonschema

from .collect_android import COMPARISON_CLASS, CollectError, collect_round
from .config import Config
from .detect import mw_two_sided_p
from .device_state import measurement_state
from .devices import Device, DeviceError, attached, register, resolve
from .orchestrate_android import (
    BuildError, Refused, _clear_media_dir, _discover, _instrument_cmd, _instrument_ok, _pull,
    _run_instrument_detached, _shell, apk_paths, build, doctor, fingerprint, install, media_dir,
)
from .records import JourneyResult, make_round_id
from .run import _repo_root, git_info
from .runner import Clock, Runner

EXIT_OK = 0
EXIT_REFUSED = 2
EXIT_FAILED = 3
EXIT_USAGE = 4

STATE_ARGUMENT = "tembra.compare.state"
# Macrobenchmark 1.5 does not honour `androidx.benchmark.iterations` (seen on the Pixel 6 Pro: 11+ iterations
# with it set to 1), so the comparison class reads this argument; the pipeline's is still passed for parity.
ITERATIONS_ARGUMENT = "tembra.compare.iterations"
DEFAULT_STATES = ("none", "profile")
FULL_STATE = "full"
ALPHA = 0.01
BUILD_TYPE = "perf"
SCHEMA_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema", "comparison.schema.json")
HEADLINE_KEY = "startup/startup.ttid_ms"

Payload = Dict[str, Any]


class StateFailed(Exception):
    """A state's instrumentation did not complete or lacks a requested journey (exit 3)."""


# ============================================================
# the states on the device
# ============================================================

def instrument_cmd(cfg: Config, device: Device, state: str, iterations: int, journeys: Sequence[str]) -> List[str]:
    """The round's `am instrument` command for the comparison class, with the state argument; a journey subset
    names the class's test methods (`Class#method,Class#method`) so only those run."""
    fqcn = f"{cfg.android.test_package}.{COMPARISON_CLASS}"
    target = fqcn if list(journeys) == list(cfg.journeys) else ",".join(f"{fqcn}#{j}" for j in journeys)
    cmd = _instrument_cmd(cfg, device, target, iterations)
    cmd[-1:-1] = ["-e", STATE_ARGUMENT, state, "-e", ITERATIONS_ARGUMENT, str(iterations)]
    return cmd


def _measure_state(cfg: Config, device: Device, runner: Runner, clock: Clock, state: str, state_dir: str,
                   app_apk: str, test_apk: str, iterations: int, journeys: Sequence[str]) -> Dict[str, JourneyResult]:
    print(f"state {state}: fresh install, {len(journeys)} journeys x {iterations} iterations")
    install(cfg, device, runner, app_apk, test_apk)
    _clear_media_dir(cfg, device, runner)
    cmd = instrument_cmd(cfg, device, state, iterations, journeys)
    fqcn = f"{cfg.android.test_package}.{COMPARISON_CLASS}"
    _shell(cfg, device, runner, "am", "force-stop", cfg.android.package)
    res = _run_instrument_detached(cfg, device, runner, clock, cmd, fqcn)
    os.makedirs(state_dir, exist_ok=True)
    log_path = os.path.join(state_dir, "instrument.log")
    with open(log_path, "w") as fh:
        fh.write(f"# {' '.join(cmd)}\n# rc={res.returncode}\n\n{res.stdout}")
        if res.stderr:
            fh.write("\n--- stderr ---\n" + res.stderr)
    _pull(cfg, device, runner, state_dir)
    if not _instrument_ok(res):
        raise StateFailed(f"state {state}: instrumentation did not complete (rc={res.returncode}); see {log_path}")
    json_paths, _traces = _discover(state_dir)
    try:
        collected = collect_round(json_paths, cfg)
    except CollectError as exc:
        raise StateFailed(f"state {state}: collection failed: {exc}") from exc
    by_name = {j.journey: j for j in collected.journeys}
    missing = [j for j in journeys if j not in by_name]
    if missing:
        raise StateFailed(f"state {state}: no result for journey {missing[0]} (see {state_dir})")
    return by_name


# ============================================================
# the statistics
# ============================================================

@dataclass(frozen=True)
class MetricComparison:
    key: str
    journey: str
    unit: str
    higher_is_worse: bool
    by_state: Dict[str, Dict[str, Any]]
    change_pct: float
    direction: str
    p_value: float
    confirmed: bool
    gap_closed_pct: Optional[float]
    with_full: bool

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "journey": self.journey, "unit": self.unit, "higher_is_worse": self.higher_is_worse,
            "by_state": self.by_state, "change_pct": self.change_pct, "direction": self.direction,
            "p_value": self.p_value, "confirmed": self.confirmed,
        }
        if self.with_full:
            data["gap_closed_pct"] = self.gap_closed_pct
        return data


def _direction(change_pct: float, higher_is_worse: bool) -> str:
    if change_pct == 0:
        return "none"
    worse = change_pct > 0 if higher_is_worse else change_pct < 0
    return "worse" if worse else "better"


def compare_metrics(results: Dict[str, Dict[str, JourneyResult]], journeys: Sequence[str],
                    states: Sequence[str], cfg: Config) -> Dict[str, MetricComparison]:
    """Per series key (`<journey>/<metric>`, the store's convention) present in both `none` and `profile`."""
    from .catalogue import lookup
    with_full = FULL_STATE in states
    out: Dict[str, MetricComparison] = {}
    for journey in journeys:
        none = results["none"][journey].metrics
        profile = results["profile"][journey].metrics
        for metric in none:
            if metric not in profile:
                continue
            samples = {s: list(results[s][journey].metrics[metric].samples) for s in states
                       if metric in results[s][journey].metrics}
            medians = {s: float(statistics.median(v)) for s, v in samples.items()}
            higher_is_worse = lookup("android", metric, cfg.catalogue).higher_is_worse
            base = medians["none"]
            change = (medians["profile"] - base) / base * 100 if base else 0.0
            p = mw_two_sided_p(samples["none"], samples["profile"])
            gap: Optional[float] = None
            if with_full and FULL_STATE in medians:
                denominator = base - medians[FULL_STATE]
                gap = (base - medians["profile"]) / denominator * 100 if denominator else None
            out[f"{journey}/{metric}"] = MetricComparison(
                key=f"{journey}/{metric}", journey=journey, unit=none[metric].unit,
                higher_is_worse=higher_is_worse,
                by_state={s: {"samples": samples[s], "median": medians[s]} for s in samples},
                change_pct=round(change, 3), direction=_direction(change, higher_is_worse),
                p_value=float(p), confirmed=bool(p < ALPHA), gap_closed_pct=None if gap is None else round(gap, 3),
                with_full=with_full,
            )
    return out


# ============================================================
# the files
# ============================================================

def render_markdown(data: Dict[str, Any]) -> str:
    states = data["states"]
    with_full = FULL_STATE in states
    head = ["metric", "median none", "median profile"] + (["median full"] if with_full else []) + \
        ["change %", "direction", "confirmed"] + (["gap closed %"] if with_full else [])
    lines = [f"# Baseline profile comparison {data['comparison_id']}", "",
             f"Device {data['device']['model']} ({data['device']['device_id']}), build {data['build']['commit'][:12]}"
             f"{' (dirty)' if data['build']['dirty'] else ''}, states {', '.join(states)}, "
             f"{data['iterations']} iterations per journey, journeys {', '.join(data['journeys'])}. "
             f"Change is `profile` vs `none`; confirmed means two-sided Mann–Whitney p < {ALPHA}.", "",
             "| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
    for key, m in data["metrics"].items():
        row = [key, _fmt(m["by_state"]["none"]["median"]), _fmt(m["by_state"]["profile"]["median"])]
        if with_full:
            row.append(_fmt(m["by_state"].get(FULL_STATE, {}).get("median")))
        row += [f"{m['change_pct']:+.1f}", m["direction"], "yes" if m["confirmed"] else "no"]
        if with_full:
            row.append("—" if m.get("gap_closed_pct") is None else f"{m['gap_closed_pct']:.0f}")
        lines.append("| " + " | ".join(row) + " |")
    lines += ["", headline(data), ""]
    return "\n".join(lines)


def _fmt(value: Optional[float]) -> str:
    if value is None:
        return "—"
    return f"{value:.1f}" if abs(value) < 10_000 else f"{value:.0f}"


def headline(data: Dict[str, Any]) -> str:
    m = data["metrics"].get(HEADLINE_KEY)
    if m is None:
        return "startup.ttid_ms: not measured"
    none, profile = m["by_state"]["none"]["median"], m["by_state"]["profile"]["median"]
    verdict = "confirmed" if m["confirmed"] else "not confirmed"
    return f"startup.ttid_ms: {none:.1f} → {profile:.1f} ms ({m['change_pct']:+.1f} %, {verdict})"


def validate(data: Dict[str, Any]) -> None:
    with open(SCHEMA_PATH) as fh:
        jsonschema.validate(data, json.load(fh), cls=jsonschema.Draft202012Validator)


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
        raise DeviceError(f"profile compare is Android-only; {device.model} ({device.device_id}) is {device.platform}")
    return device


def main(args, cfg: Config, paths, runner: Runner, clock: Clock) -> Tuple[int, Payload]:
    device = _select_device(args, cfg, runner, os.path.join(paths.data_dir, "devicectl.json"))
    print(f"profile compare on {device.model} ({device.device_id})")
    journeys = [j.strip() for j in args.journeys.split(",")] if args.journeys else list(cfg.journeys)
    unknown = [j for j in journeys if j not in cfg.journeys]
    if unknown:
        raise DeviceError(f"unknown journey {unknown[0]!r} (configured: {', '.join(cfg.journeys)})")
    iterations = args.iterations or cfg.collection.iterations
    states = list(DEFAULT_STATES) + ([FULL_STATE] if args.with_full else [])
    with measurement_state(cfg, device, runner, clock):
        return _compare(args, cfg, paths, runner, clock, device, journeys, iterations, states)


def _compare(args, cfg: Config, paths, runner: Runner, clock: Clock, device: Device, journeys: List[str],
             iterations: int, states: List[str]) -> Tuple[int, Payload]:
    payload: Payload = {"device": device.device_id, "states": states, "journeys": journeys, "iterations": iterations}
    checks = doctor(cfg, device, runner)
    for check in checks:
        print(f"  [{'ok' if check.ok else 'FAIL'}] {check.name} — {check.detail}")
    if any(not c.ok for c in checks):
        refused = Refused(checks)
        print(f"refused: {refused}")
        payload["refused"] = str(refused)
        return EXIT_REFUSED, payload
    java_home = next((c.value for c in checks if c.name == "java_home"), None)

    started = datetime.now(timezone.utc)
    comparison_id = make_round_id(started, device.device_id)
    comparison_dir = os.path.join(paths.data_dir, "comparisons", comparison_id)
    os.makedirs(comparison_dir, exist_ok=True)
    payload["comparison_id"] = comparison_id
    repo_root = _repo_root(cfg)
    build_info = git_info(repo_root, runner)
    try:
        if args.no_build:
            if not (args.apk and args.test_apk):
                raise BuildError("--no-build needs --apk <app.apk> and --test-apk <benchmarks.apk>")
            app_apk, test_apk = os.path.abspath(args.apk), os.path.abspath(args.test_apk)
        else:
            log_path = os.path.join(comparison_dir, "gradle.log")
            print(f"building {BUILD_TYPE} artifacts, log in {log_path}")
            app_apk, test_apk = build(cfg, runner, BUILD_TYPE, log_path, java_home)
    except BuildError as exc:
        print(f"build failed: {exc}")
        payload["failed"] = str(exc)
        return EXIT_FAILED, payload
    fp = fingerprint(cfg, device, runner)

    results: Dict[str, Dict[str, JourneyResult]] = {}
    artifacts: Dict[str, Dict[str, str]] = {}
    for state in states:
        state_dir = os.path.join(comparison_dir, state)
        try:
            results[state] = _measure_state(cfg, device, runner, clock, state, state_dir, app_apk, test_apk,
                                            iterations, journeys)
        except (StateFailed, BuildError) as exc:
            print(f"failed: {exc}")
            payload["failed"] = str(exc)
            return EXIT_FAILED, payload
        json_paths, _traces = _discover(state_dir)
        artifacts[state] = {"benchmark_json": json_paths[0] if json_paths else "",
                            "instrument_log": os.path.join(state_dir, "instrument.log")}
        print(f"  {state}: " + ", ".join(f"{j} ✓" for j in journeys))

    metrics = compare_metrics(results, journeys, states, cfg)
    data: Dict[str, Any] = {
        "schema_version": 1, "comparison_id": comparison_id,
        "started_at": started.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "build": {"commit": build_info["commit"], "dirty": build_info["dirty"], "build_type": BUILD_TYPE,
                  "apk": app_apk, "test_apk": test_apk},
        "device": {"device_id": device.device_id, "model": device.model or fp.get("model", device.device_id),
                   "fingerprint": fp},
        "driver": socket.gethostname(), "states": states, "iterations": iterations, "journeys": journeys,
        "metrics": {key: m.to_dict() for key, m in metrics.items()}, "artifacts": artifacts,
    }
    validate(data)
    json_path = os.path.join(comparison_dir, "comparison.json")
    with open(json_path, "w") as fh:
        json.dump(data, fh, indent=1, sort_keys=True)
        fh.write("\n")
    markdown = render_markdown(data)
    md_path = os.path.join(comparison_dir, "comparison.md")
    with open(md_path, "w") as fh:
        fh.write(markdown)
    print(markdown)
    print(f"written: {json_path} and {md_path}")
    payload.update({"comparison_json": json_path, "comparison_md": md_path, "headline": headline(data)})
    return EXIT_OK, payload
