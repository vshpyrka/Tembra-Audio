"""Deterministic fixture history for the report golden (feature 007, US3).

Three devices: Pixel 6 Pro (14 rounds; labels 006-tuner-noise-gate then
007-performance-pipeline; a +30 % startup step at the first 007 round that the
detector confirms), iPhone 14 (12 rounds; an OS-version change at round 6; the
latest round +20 % on memory.peak_kb and +18 % on startup.ttid_ms, so two gate
flags), iPhone 17 simulator (6 rounds, non-reference). Values come from a fixed
LCG, never the clock, so the records (and the rendered report) are byte-stable.

Regenerate with:
    cd app/perf && uv run python tests/fixtures/gen_history.py
"""

import os
import shutil

from tracepulse.records import DeviceInfo, JourneyResult, MetricResult, RoundRecord, record_path

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "history-3-devices")


def _lcg(seed):
    x = seed
    while True:
        x = (x * 1103515245 + 12345) % (2 ** 31)
        yield x


def _samples(gen, base, spread, n=20, nd=2):
    return [round(base + (next(gen) % 1000) / 1000.0 * spread - spread / 2, nd) for _ in range(n)]


def _android_round(i, label, device, gen, startup=300.0, memory=180000.0, os_version="17"):
    journeys = [
        JourneyResult(journey="startup", iterations=20, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=_samples(gen, startup, 12.0), source="macrobenchmark"),
            "startup.ttfd_ms": MetricResult(unit="ms", samples=_samples(gen, startup + 150, 60.0), source="macrobenchmark"),
            "app.size_bytes": MetricResult(unit="bytes", samples=[], source="apk", value=14_900_000.0 + i * 16),
        }),
    ]
    for journey in ("tuner", "metronome", "stress"):
        metrics = {
            "frames.p50_ms": MetricResult(unit="ms", samples=_samples(gen, 5.0, 0.6), source="macrobenchmark"),
            "frames.p90_ms": MetricResult(unit="ms", samples=_samples(gen, 8.0, 1.0), source="macrobenchmark"),
            "frames.p99_ms": MetricResult(unit="ms", samples=_samples(gen, 12.0, 2.0), source="macrobenchmark"),
            "frames.overrun_p90_ms": MetricResult(unit="ms", samples=_samples(gen, -7.0, 1.5), source="macrobenchmark"),
            "memory.peak_kb": MetricResult(unit="kB", samples=_samples(gen, memory, 3000.0, nd=0), source="macrobenchmark"),
            "memory.end_kb": MetricResult(unit="kB", samples=_samples(gen, 48000.0, 1500.0, nd=0), source="macrobenchmark"),
            "journey.duration_ms": MetricResult(unit="ms", samples=_samples(gen, 4200.0, 120.0), source="macrobenchmark"),
        }
        if journey == "tuner":
            metrics["section.tuner.modeSwitch.sum_ms"] = MetricResult(unit="ms", samples=_samples(gen, 12.0, 2.0), source="macrobenchmark")
            metrics["section.tuner.modeSwitch.count"] = MetricResult(unit="count", samples=[2.0] * 20, source="macrobenchmark")
        if journey == "stress":
            for key, value in (("audio.callbacks", 0.0), ("audio.last_callback_us", 0.0), ("audio.max_callback_us", 0.0),
                               ("audio.over_budget", 0.0), ("audio.xruns", 0.0), ("audio.realtime", 0.0)):
                metrics[key] = MetricResult(unit="count", samples=[value] * 20, source="perfetto")
        journeys.append(JourneyResult(journey=journey, iterations=20, metrics=metrics))
    return RoundRecord(
        round_id=f"202610{i + 1:02d}T120000Z_{label}", device=device, label=label,
        build={"commit": f"{(0x3a0 + i * 7):07x}", "branch": label, "dirty": False, "app_size_bytes": 14_900_000 + i * 16},
        fingerprint={"os_version": os_version, "os_build": "CP3A.260905.009", "sdk": 37, "refresh_rate_hz": 60.0,
                     "network_state": "none", "battery_temp_c": 27.0 + (i % 3) * 0.4, "battery_level": 100 - i},
        machine={"hostname": "mbp-m4", "os": "Darwin 25.6.0", "tool_version": "0.2.0"},
        checks=[{"name": "battery", "passed": True, "value": {"level": 100 - i, "temp_c": 27.0}, "limit": {"min_level": 50, "max_temp_c": 40.0}}],
        status="succeeded", journeys=journeys, badges=[],
        started_at=f"2026-10-{i + 1:02d}T12:00:00Z", finished_at=f"2026-10-{i + 1:02d}T12:18:00Z",
        session={"index": 1, "size": 1},
        artifacts=[{"kind": "trace", "path": f"/Users/owner/app/perf/data/rounds/r{i}/StartupBenchmark/iter000.perfetto-trace"}],
    )


def _ios_round(i, label, device, gen, startup=520.0, memory=96000.0, os_version="27.0.1", kind="device",
               network="none", simulator=False):
    journeys = [
        JourneyResult(journey="startup", iterations=20, metrics={
            "startup.ttid_ms": MetricResult(unit="ms", samples=_samples(gen, startup, 20.0), source="xcresult"),
            "startup.ttfd_ms": MetricResult(unit="ms", samples=_samples(gen, startup + 40, 30.0), source="xcresult"),
        }),
    ]
    for journey in ("tuner", "metronome", "stress"):
        metrics = {
            "memory.peak_kb": MetricResult(unit="kB", samples=_samples(gen, memory, 2000.0, nd=0), source="xcresult"),
            "memory.end_kb": MetricResult(unit="kB", samples=_samples(gen, 1200.0, 300.0, nd=0), source="xcresult"),
            "cpu.time_ms": MetricResult(unit="ms", samples=_samples(gen, 900.0, 60.0), source="xcresult"),
            "cpu.instructions_k": MetricResult(unit="kI", samples=_samples(gen, 2_400_000.0, 90_000.0, nd=0), source="xcresult"),
            "journey.duration_ms": MetricResult(unit="ms", samples=_samples(gen, 4300.0, 150.0), source="xcresult"),
        }
        if not simulator:
            metrics["frames.hitch_ratio_ms_per_s"] = MetricResult(unit="ms/s", samples=_samples(gen, 2.0, 1.0), source="xcresult")
            metrics["frames.hitch_count"] = MetricResult(unit="count", samples=[float(k % 3) for k in range(20)], source="xcresult")
            metrics["frames.fps_deficit"] = MetricResult(unit="fps", samples=_samples(gen, 0.5, 0.5), source="xcresult")
        if journey == "tuner":
            metrics["section.tuner.modeSwitch.sum_ms"] = MetricResult(unit="ms", samples=_samples(gen, 11.0, 2.0), source="xcresult")
            metrics["section.tuner.modeSwitch.count"] = MetricResult(unit="count", samples=[2.0] * 20, source="xcresult")
        if journey == "stress":
            for key in ("audio.callbacks", "audio.last_callback_us", "audio.max_callback_us", "audio.over_budget",
                        "audio.xruns", "audio.realtime"):
                metrics[key] = MetricResult(unit="count", samples=[0.0] * 20, source="engine-health")
        journeys.append(JourneyResult(journey=journey, iterations=20, metrics=metrics))
    badges = ["simulator"] if simulator else []
    return RoundRecord(
        round_id=f"202610{i + 1:02d}T140000Z_{label}", device=device, label=label,
        build={"commit": f"{(0x3a0 + i * 7):07x}", "branch": label, "dirty": False},
        fingerprint={"os_version": os_version, "os_build": "24A446", "network_state": network, "thermal_state": "nominal",
                     "battery_level": 0.9 - i * 0.01},
        machine={"hostname": "mbp-m4", "os": "Darwin 25.6.0", "tool_version": "0.2.0"},
        checks=[{"name": "thermal", "passed": True, "value": "nominal", "limit": "nominal"}],
        status="succeeded", journeys=journeys, badges=badges,
        started_at=f"2026-10-{i + 1:02d}T14:00:00Z", finished_at=f"2026-10-{i + 1:02d}T14:16:00Z",
        session={"index": 1, "size": 1},
        artifacts=[{"kind": "xcresult", "path": f"/Users/owner/app/perf/data/rounds/i{i}/perf.xcresult"}],
    )


def build() -> list:
    pixel = DeviceInfo(device_id="19021FDEE001AH", platform="android", kind="device", model="Pixel 6 Pro", reference=True)
    iphone = DeviceInfo(device_id="00008110-001049660E3A401E", platform="ios", kind="device", model="iPhone 14", reference=True)
    sim = DeviceInfo(device_id="4CD6879C-8FBA-4CEF-B4EC-31968D0144B0", platform="ios", kind="simulator",
                     model="iPhone 17 (simulator)", reference=False)
    gen = _lcg(7)
    records = []
    for i in range(14):
        label = "006-tuner-noise-gate" if i < 8 else "007-performance-pipeline"
        startup = 300.0 if i < 8 else 390.0
        records.append(_android_round(i, label, pixel, gen, startup=startup))
    for i in range(12):
        label = "006-tuner-noise-gate" if i < 6 else "007-performance-pipeline"
        os_version = "26.5" if i < 6 else "27.0.1"
        startup = 520.0 if i < 11 else 614.0
        memory = 96000.0 if i < 11 else 115200.0
        network = "none" if i != 9 else "wifi"
        records.append(_ios_round(i, label, iphone, gen, startup=startup, memory=memory, os_version=os_version, network=network))
    for i in range(6):
        records.append(_ios_round(i, "007-performance-pipeline", sim, gen, startup=410.0, memory=70000.0,
                                  os_version="26.5", kind="simulator", simulator=True))
    return records


def main() -> None:
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    for record in build():
        RoundRecord.validate(record.to_dict())
        path = os.path.join(ROOT, record_path(record.device.device_id, record.round_id))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as fh:
            fh.write(record.to_json())
    print(f"wrote {sum(len(f) for _, _, f in os.walk(ROOT))} records under {ROOT}")


if __name__ == "__main__":
    main()
