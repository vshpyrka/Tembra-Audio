import logging
import math
import os

import pytest

from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.collect_android import (
    BenchmarkFile,
    BenchmarkResult,
    CollectError,
    SampledMetric,
    SingleMetric,
    apk_size,
    parse_benchmark_json,
    quality_flags,
)


FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures")
STARTUP_JSON = os.path.join(FIXTURES_DIR, "startup-benchmarkData.json")
SCROLL_JSON = os.path.join(FIXTURES_DIR, "scroll-benchmarkData.json")


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


@pytest.fixture
def startup_file():
    return parse_benchmark_json(STARTUP_JSON)


@pytest.fixture
def scroll_file():
    return parse_benchmark_json(SCROLL_JSON)


# --- parse_benchmark_json: startup fixture, every field -----------------------

def test_parse_startup_context(startup_file):
    assert startup_file.context == {
        "build": {"device": "a52q"},
        "cpuLocked": False,
        "compilationMode": "Full",
        "osCodenameAbbreviated": "U",
    }


def test_parse_startup_benchmark_shape(startup_file):
    assert len(startup_file.benchmarks) == 1
    b = startup_file.benchmarks[0]
    assert isinstance(b, BenchmarkResult)
    assert b.name == "startupCold"
    assert b.thermal_throttle_sleep_s == 0
    assert b.sampled == {}


def test_parse_startup_single_metrics_exact(startup_file):
    b = startup_file.benchmarks[0]
    assert b.single["timeToInitialDisplayMs"] == SingleMetric(
        minimum=480.1, maximum=520.7, median=501.3, cov=0.03,
        runs=[480.1, 501.3, 520.7],
    )
    assert b.single["timeToFullDisplayMs"] == SingleMetric(
        minimum=900.0, maximum=990.0, median=942.0, cov=0.04,
        runs=[900.0, 942.0, 990.0],
    )


# --- parse_benchmark_json: scroll fixture, every field -------------------------

def test_parse_scroll_benchmark_shape(scroll_file):
    assert len(scroll_file.benchmarks) == 1
    b = scroll_file.benchmarks[0]
    assert b.name == "scrollFeed"
    assert b.thermal_throttle_sleep_s == 2.5


def test_parse_scroll_single_metrics_exact(scroll_file):
    b = scroll_file.benchmarks[0]
    assert b.single["memoryRssAnonMaxKb"] == SingleMetric(
        minimum=190000, maximum=210000, median=200000, cov=0.02,
        runs=[190000, 200000, 210000],
    )
    assert b.single["memoryRssAnonLastKb"] == SingleMetric(
        minimum=50000, maximum=54000, median=52000, cov=0.025,
        runs=[50000, 52000, 54000],
    )


def test_parse_scroll_sampled_metrics_exact(scroll_file):
    b = scroll_file.benchmarks[0]
    assert b.sampled["frameDurationCpuMs"] == SampledMetric(
        p50=8.2, p90=14.9, p95=18.0, p99=26.4,
        runs=[[6, 7, 8, 9, 30], [7, 8, 8, 10, 25], [6, 8, 9, 11, 28]],
    )
    assert b.sampled["frameOverrunMs"] == SampledMetric(
        p50=-4.0, p90=1.5, p95=3.0, p99=9.9,
        runs=[[-8, -5, -4, 0, 2], [-7, -5, -3, 1, 2], [-9, -6, -4, 0, 3]],
    )


# --- parse_benchmark_json: error paths -----------------------------------------

def test_parse_benchmark_json_malformed_raises_naming_path(tmp_path):
    p = tmp_path / "broken.json"
    p.write_text("{not valid json")
    with pytest.raises(CollectError, match=r"broken\.json"):
        parse_benchmark_json(str(p))


def test_parse_benchmark_json_missing_top_level_keys_raises_naming_path(tmp_path):
    p = tmp_path / "incomplete.json"
    p.write_text('{"context": {}}')
    with pytest.raises(CollectError, match=r"incomplete\.json"):
        parse_benchmark_json(str(p))


def test_parse_benchmark_json_missing_benchmarks_key_specifically(tmp_path):
    p = tmp_path / "no_benchmarks.json"
    p.write_text('{"context": {}}')
    with pytest.raises(CollectError):
        parse_benchmark_json(str(p))


def test_parse_benchmark_json_missing_context_key_specifically(tmp_path):
    p = tmp_path / "no_context.json"
    p.write_text('{"benchmarks": []}')
    with pytest.raises(CollectError):
        parse_benchmark_json(str(p))


def test_parse_benchmark_json_file_not_found_raises_collect_error(tmp_path):
    missing = tmp_path / "does-not-exist.json"
    with pytest.raises(CollectError, match=r"does-not-exist\.json"):
        parse_benchmark_json(str(missing))


# --- quality_flags -------------------------------------------------------------

def test_quality_flags_scroll_fixture_is_thermal_not_noisy(startup_file, scroll_file, cfg):
    flags, thermal_total = quality_flags([startup_file, scroll_file], cfg)
    assert flags == ["thermal"]
    assert math.isclose(thermal_total, 2.5, rel_tol=1e-9)


def test_quality_flags_clean_files_have_no_flags(cfg):
    clean = BenchmarkFile(
        context={},
        benchmarks=[
            BenchmarkResult(
                name="clean",
                single={
                    "timeToInitialDisplayMs": SingleMetric(
                        minimum=1.0, maximum=1.0, median=1.0, cov=0.01, runs=[1.0, 1.0, 1.0]
                    )
                },
                sampled={},
                thermal_throttle_sleep_s=0,
            )
        ],
    )
    flags, thermal_total = quality_flags([clean], cfg)
    assert flags == []
    assert thermal_total == 0.0


def test_quality_flags_noisy_when_mapped_single_metric_cov_exceeds_threshold(cfg):
    noisy = BenchmarkFile(
        context={},
        benchmarks=[
            BenchmarkResult(
                name="noisyBenchmark",
                single={
                    "memoryRssAnonMaxKb": SingleMetric(
                        minimum=100.0, maximum=300.0, median=200.0, cov=0.2,
                        runs=[100.0, 200.0, 300.0],
                    )
                },
                sampled={},
                thermal_throttle_sleep_s=0,
            )
        ],
    )
    flags, _ = quality_flags([noisy], cfg)
    assert "noisy" in flags
    assert cfg.quality.cov_max == 0.15  # sanity: 0.2 > 0.15


def test_quality_flags_checks_unmapped_single_metrics_too(cfg):
    # Phase 3 calibration flip: ANY single metric's instability is round-quality
    # signal -- the wifi-contaminated live round announced itself only via the
    # unmapped, advisory `frameCount` (cov 0.72, above its then 0.6 allowance; 0.75 since the
    # tuner journey's anchor waits were measured, so 0.9 here). Unmapped keys still flag.
    unmapped = BenchmarkFile(
        context={},
        benchmarks=[
            BenchmarkResult(
                name="unrelated",
                single={
                    "frameCount": SingleMetric(
                        minimum=1.0, maximum=9.0, median=5.0, cov=0.9, runs=[1.0, 5.0, 9.0]
                    )
                },
                sampled={},
                thermal_throttle_sleep_s=0,
            )
        ],
    )
    flags, _ = quality_flags([unmapped], cfg)
    assert "noisy" in flags


def test_quality_flags_honors_per_metric_cov_overrides(cfg):
    # timeToFullDisplayMs measured at cov 0.29-0.38 on the A52 while its round
    # median stays stable -- the shipped override (0.45) keeps such rounds
    # unbadged; the same cov on a non-overridden key still flags.
    cfg.quality.cov_max_overrides = {"timeToFullDisplayMs": 0.45}
    def one(key):
        return BenchmarkFile(
            context={},
            benchmarks=[
                BenchmarkResult(
                    name="b", sampled={}, thermal_throttle_sleep_s=0,
                    single={key: SingleMetric(minimum=1.0, maximum=9.0, median=5.0,
                                               cov=0.38, runs=[1.0, 5.0, 9.0])},
                )
            ],
        )
    flags_ttfd, _ = quality_flags([one("timeToFullDisplayMs")], cfg)
    assert flags_ttfd == []          # 0.38 <= 0.45 allowance
    flags_other, _ = quality_flags([one("timeToInitialDisplayMs")], cfg)
    assert "noisy" in flags_other    # 0.38 > 0.15 default


def test_shipped_yml_carries_the_measured_overrides():
    from tracepulse.config import load_config, DEFAULT_CONFIG_PATH
    shipped = load_config(DEFAULT_CONFIG_PATH)
    # Calibrated on the Pixel 6 Pro's first rounds (tracepulse.yml comments); revisited at the noise baseline.
    assert shipped.quality.cov_max_overrides == {
        "timeToFullDisplayMs": 0.45,
        "frameCount": 0.75,
        "journey.tunerSumMs": 0.25,
    }


def test_quality_flags_thermal_seconds_total_sums_across_benchmarks(cfg):
    f = BenchmarkFile(
        context={},
        benchmarks=[
            BenchmarkResult(name="a", single={}, sampled={}, thermal_throttle_sleep_s=1.5),
            BenchmarkResult(name="b", single={}, sampled={}, thermal_throttle_sleep_s=2.0),
        ],
    )
    flags, thermal_total = quality_flags([f], cfg)
    assert "thermal" in flags
    assert math.isclose(thermal_total, 3.5, rel_tol=1e-9)


# --- apk_size -------------------------------------------------------------------

def test_apk_size_returns_byte_count(tmp_path):
    p = tmp_path / "app-demo-release.apk"
    p.write_bytes(b"x" * 12345)
    assert apk_size(str(p)) == 12345


def test_apk_size_zero_byte_file(tmp_path):
    p = tmp_path / "empty.apk"
    p.write_bytes(b"")
    assert apk_size(str(p)) == 0



def test_quality_flags_ignore_section_keys_but_not_the_journey_section(cfg):
    """Sub-millisecond sections jitter by their own size within a round; the journey
    section is the journey's duration and keeps its allowance."""
    def single(cov):
        return SingleMetric(minimum=1.0, maximum=1.0, median=1.0, cov=cov, runs=[1.0, 1.0, 1.0])

    def benchmark(single_metrics):
        return BenchmarkResult(name="tunerJourney", single=single_metrics, sampled={}, thermal_throttle_sleep_s=0)
    sections = {"tuner.frameSumMs": single(0.98), "tuner.frameCount": single(0.76), "metronome.loadSumMs": single(0.56),
                "tuner.modeSwitchSumMs": single(0.3), "tuner.modeSwitchCount": single(0.0),
                "timeToInitialDisplayMs": single(0.05)}
    flags, _ = quality_flags([BenchmarkFile(context={}, benchmarks=[benchmark(sections)])], cfg)
    assert flags == []
    journey = {"journey.tunerSumMs": single(0.3), "timeToInitialDisplayMs": single(0.05)}
    flags, _ = quality_flags([BenchmarkFile(context={}, benchmarks=[benchmark(journey)])], cfg)
    assert flags == ["noisy"], "the journey section (0.3 > its 0.25 allowance) still counts"


# ---------------------------------------------------------------------------
# the comparison class (feature 008): keyed by method, same catalogue keys as a round
# ---------------------------------------------------------------------------

PROFILE_FIXTURES = os.path.join(FIXTURES_DIR, "profile")
COMPARISON_CLASS = "audio.tembra.perf.ProfileComparisonBenchmark"


@pytest.mark.parametrize("method", ["startup", "tuner", "metronome", "timeline", "recording", "mixer", "stress"])
def test_journey_of_method_maps_the_comparison_class_by_method(method):
    from tracepulse.collect_android import journey_of_method
    assert journey_of_method(COMPARISON_CLASS, method) == method


def test_journey_of_method_rejects_an_unknown_method():
    from tracepulse.collect_android import journey_of_method
    with pytest.raises(CollectError) as exc:
        journey_of_method(COMPARISON_CLASS, "scroll")
    assert "scroll" in str(exc.value)


def test_journey_of_method_rejects_a_class_that_is_not_the_comparison_class():
    from tracepulse.collect_android import journey_of_method
    with pytest.raises(CollectError):
        journey_of_method("audio.tembra.perf.StartupBenchmark", "startup")


def test_a_comparison_fixture_parses_into_per_journey_results_with_the_round_catalogue_keys(cfg):
    from tracepulse.collect_android import collect_round
    collected = collect_round([os.path.join(PROFILE_FIXTURES, "benchmarkData-none.json")], cfg)
    by_name = {j.journey: j for j in collected.journeys}
    assert sorted(by_name) == ["metronome", "mixer", "recording", "startup", "stress", "timeline", "tuner"]
    assert set(by_name["startup"].metrics) == {"startup.ttid_ms", "startup.ttfd_ms"}
    assert by_name["startup"].iterations == 20
    for journey in ("tuner", "metronome", "timeline", "recording", "mixer", "stress"):
        keys = set(by_name[journey].metrics)
        assert {"frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
                "memory.peak_kb", "memory.end_kb", "journey.duration_ms"} <= keys, journey
    assert "section.tuner.load.sum_ms" in by_name["tuner"].metrics
    assert "section.metronome.load.sum_ms" in by_name["metronome"].metrics
    assert len(by_name["startup"].metrics["startup.ttid_ms"].samples) == 20


def test_the_round_mapping_by_class_is_untouched():
    from tracepulse.collect_android import journey_of
    assert journey_of("audio.tembra.perf.StartupBenchmark") == "startup"
    with pytest.raises(CollectError):
        journey_of(COMPARISON_CLASS)
