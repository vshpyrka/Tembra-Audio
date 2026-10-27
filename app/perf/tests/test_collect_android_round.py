"""The Android collector turns Macrobenchmark 1.5.0 `benchmarkData.json` files into
journey results keyed by the catalogue (contracts/metric-catalogue.md): one journey
per benchmark class, 20 samples per metric, sections by name, and the `noisy` /
`thermal` badges from the JSON's own statistics."""

import json
import os

import pytest

from tracepulse.collect_android import CollectError, collect_round, journey_of, map_benchmark
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "benchmarkData-1.5.json")


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


def test_journey_of_benchmark_class():
    assert journey_of("audio.tembra.perf.StartupBenchmark") == "startup"
    assert journey_of("audio.tembra.perf.TunerJourneyBenchmark") == "tuner"
    assert journey_of("audio.tembra.perf.MetronomeJourneyBenchmark") == "metronome"
    assert journey_of("audio.tembra.perf.TimelineJourneyBenchmark") == "timeline"
    assert journey_of("audio.tembra.perf.StressJourneyBenchmark") == "stress"
    with pytest.raises(CollectError):
        journey_of("audio.tembra.perf.ScrollBenchmark")


def test_collect_round_yields_one_journey_per_class_with_twenty_samples(cfg):
    result = collect_round([FIXTURE], cfg)
    journeys = {j.journey: j for j in result.journeys}
    assert set(journeys) == {"startup", "tuner", "metronome", "stress"}
    for j in result.journeys:
        assert j.iterations == 20
        for key, m in j.metrics.items():
            assert len(m.samples) == 20, (j.journey, key)
            assert m.source == "macrobenchmark"
    startup = journeys["startup"].metrics
    assert set(startup) == {"startup.ttid_ms", "startup.ttfd_ms"}
    assert startup["startup.ttid_ms"].unit == "ms"
    assert startup["startup.ttid_ms"].summary["median"] == pytest.approx(
        sorted(startup["startup.ttid_ms"].samples)[9:11][0] * 0.5 + sorted(startup["startup.ttid_ms"].samples)[9:11][1] * 0.5)


def test_frames_memory_journey_and_sections_map_to_catalogue_keys(cfg):
    result = collect_round([FIXTURE], cfg)
    tuner = {j.journey: j for j in result.journeys}["tuner"].metrics
    assert set(tuner) == {
        "frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
        "memory.peak_kb", "memory.end_kb", "journey.duration_ms",
        "section.tuner.modeSwitch.sum_ms", "section.tuner.modeSwitch.count",
        "section.tuner.load.sum_ms", "section.tuner.load.count",
    }
    assert tuner["memory.peak_kb"].unit == "kB" and tuner["frames.overrun_p90_ms"].unit == "ms"
    assert tuner["section.tuner.modeSwitch.count"].unit == "count"
    assert tuner["section.tuner.modeSwitch.count"].samples == [1.0] * 20
    # per-iteration percentiles of each iteration's own frames, not the pooled P-values
    with open(FIXTURE) as fh:
        raw = json.load(fh)
    frames = next(b for b in raw["benchmarks"] if b["name"] == "tunerJourney")["sampledMetrics"]["frameDurationCpuMs"]
    first = sorted(frames["runs"][0])
    assert tuner["frames.p50_ms"].samples[0] == pytest.approx(first[29] + (first[30] - first[29]) * 0.5)
    assert tuner["frames.p99_ms"].samples[0] >= tuner["frames.p90_ms"].samples[0] >= tuner["frames.p50_ms"].samples[0]
    metronome = {j.journey: j for j in result.journeys}["metronome"].metrics
    assert metronome["section.metronome.tempoChange.count"].samples == [10.0] * 20
    assert "journey.duration_ms" in metronome


def test_unknown_single_metric_keys_are_reported_not_silently_dropped(cfg, caplog):
    with open(FIXTURE) as fh:
        raw = json.load(fh)
    ttid = raw["benchmarks"][0]["metrics"]["timeToInitialDisplayMs"]
    raw["benchmarks"][0]["metrics"]["somethingNewMs"] = ttid
    raw["benchmarks"][0]["metrics"]["frameCount"] = ttid  # advisory: read by the quality checks, never a series
    with caplog.at_level("WARNING", logger="tracepulse.collect_android"):
        mapped = map_benchmark(_benchmark(raw["benchmarks"][0]), cfg)
    assert "startup.ttid_ms" in mapped.metrics and "somethingNewMs" not in mapped.metrics
    assert "frameCount" not in mapped.metrics
    assert any("somethingNewMs" in r.message for r in caplog.records)
    assert not any("frameCount" in r.message for r in caplog.records), "an advisory key is not noise in the session log"


def test_noisy_badge_above_cov_max_with_raw_key_overrides(cfg, tmp_path):
    with open(FIXTURE) as fh:
        raw = json.load(fh)
    # ttfd is honestly noisy (fixture cov ~0.2) but carries an override of 0.45 in tracepulse.yml: clean
    clean = collect_round([FIXTURE], cfg)
    assert clean.badges == [] and clean.thermal_seconds == 0.0
    raw["benchmarks"][0]["metrics"]["timeToInitialDisplayMs"]["coefficientOfVariation"] = 0.2
    noisy_path = tmp_path / "noisy-benchmarkData.json"
    noisy_path.write_text(json.dumps(raw))
    noisy = collect_round([str(noisy_path)], cfg)
    assert noisy.badges == ["noisy"]


def test_thermal_badge_from_throttle_sleep(cfg, tmp_path):
    with open(FIXTURE) as fh:
        raw = json.load(fh)
    raw["benchmarks"][2]["thermalThrottleSleepSeconds"] = 3.5
    path = tmp_path / "thermal-benchmarkData.json"
    path.write_text(json.dumps(raw))
    result = collect_round([str(path)], cfg)
    assert result.badges == ["thermal"] and result.thermal_seconds == 3.5


def test_missing_required_family_is_an_error(cfg, tmp_path):
    with open(FIXTURE) as fh:
        raw = json.load(fh)
    del raw["benchmarks"][1]["metrics"]["memoryRssAnonMaxKb"]
    path = tmp_path / "partial-benchmarkData.json"
    path.write_text(json.dumps(raw))
    with pytest.raises(CollectError, match="memory.peak_kb"):
        collect_round([str(path)], cfg)


def _benchmark(raw_benchmark):
    from tracepulse.collect_android import _parse_benchmark
    return _parse_benchmark(raw_benchmark)
