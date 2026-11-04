"""The iOS collector (contracts/metric-catalogue.md, research D6): `xcresulttool get
test-results metrics` JSON → journeys keyed by the catalogue (20 samples per
metric); the preflight attachment → fingerprint; `PERF-REFUSED:` → refusal and
no record; simulators carry the `simulator` flag and no frames family."""

import json
import os

import pytest

from tracepulse.collect_ios import (
    CollectError, Refusal, collect_round, engine_health, journey_of_test, parse_preflight, refusal_from_failures,
)
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
METRICS = os.path.join(FIXTURES, "xcresult-metrics.json")
METRICS_SIM = os.path.join(FIXTURES, "xcresult-metrics-simulator.json")


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


def test_journey_of_test_identifier():
    assert journey_of_test("TembraPerfTests/StartupPerfTests/testColdLaunchToFirstFrame()") == ("startup", "ttid")
    assert journey_of_test("TembraPerfTests/StartupPerfTests/testColdLaunchToResponsive()") == ("startup", "ttfd")
    assert journey_of_test("TembraPerfTests/TunerPerfTests/testTunerJourney()") == ("tuner", None)
    assert journey_of_test("TembraPerfTests/MetronomePerfTests/testMetronomeJourney()") == ("metronome", None)
    assert journey_of_test("TembraPerfTests/TimelinePerfTests/testTimelineJourney()") == ("timeline", None)
    assert journey_of_test("TembraPerfTests/StressPerfTests/testStressJourney()") == ("stress", None)
    assert journey_of_test("TembraPerfTests/WindowingPerfTests/testWindowingJourney()") == ("windowing", None)
    assert journey_of_test("TembraPerfTests/FacadeSelfCheckTests/testModeSwitchSectionIsMeasuredByName()") is None


def test_metrics_map_to_catalogue_keys_with_twenty_samples(cfg):
    result = collect_round(METRICS, cfg, simulator=False)
    journeys = {j.journey: j for j in result.journeys}
    assert set(journeys) == {"startup", "tuner", "metronome", "timeline", "stress"}
    startup = journeys["startup"].metrics
    assert set(startup) == {"startup.ttid_ms", "startup.ttfd_ms"}
    assert len(startup["startup.ttid_ms"].samples) == 20
    assert 500 < startup["startup.ttid_ms"].summary["median"] < 600, "seconds become milliseconds"
    assert startup["startup.ttid_ms"].source == "xcresult" and startup["startup.ttid_ms"].unit == "ms"
    tuner = journeys["tuner"].metrics
    assert set(tuner) == {
        "journey.duration_ms", "cpu.time_ms", "cpu.instructions_k", "memory.peak_kb", "memory.end_kb",
        "section.tuner.load.sum_ms", "section.tuner.modeSwitch.sum_ms", "section.tuner.frame.sum_ms",
    }
    assert 4000 < tuner["journey.duration_ms"].summary["median"] < 5000
    assert 800 < tuner["cpu.time_ms"].summary["median"] < 1100
    assert tuner["cpu.instructions_k"].unit == "kI" and tuner["memory.peak_kb"].unit == "kB"
    assert 10 < tuner["section.tuner.modeSwitch.sum_ms"].summary["median"] < 20
    assert all(j.iterations == 20 for j in result.journeys)


def test_animation_signpost_yields_the_frames_family(cfg):
    result = collect_round(METRICS, cfg, simulator=False)
    metronome = {j.journey: j for j in result.journeys}["metronome"].metrics
    assert "frames.hitch_ratio_ms_per_s" in metronome and "frames.hitch_count" in metronome and "frames.fps_deficit" in metronome
    assert metronome["frames.hitch_ratio_ms_per_s"].unit == "ms/s"
    assert 0 <= metronome["frames.fps_deficit"].summary["median"] <= 3, "max(0, 60 - fps)"
    assert metronome["frames.hitch_count"].samples == [float(i % 3) for i in range(20)]
    assert "section.metronome.tempoChange.sum_ms" in metronome


def test_simulator_rounds_carry_the_flag_and_no_frames_family(cfg):
    result = collect_round(METRICS_SIM, cfg, simulator=True)
    assert result.badges == ["simulator"]
    for j in result.journeys:
        assert not any(k.startswith("frames.") for k in j.metrics), j.journey


def test_signpost_identifier_form_from_xcresulttool(cfg, tmp_path):
    """The real form seen on the simulator: identifier `XCTMetric_OSSignpost-tuner.modeSwitch.duration`,
    display name `Duration (tuner.modeSwitch)`, unit s, 20 measurements."""
    entry = {"testIdentifier": "TembraPerfTests/TunerPerfTests/testTunerJourney()", "testRuns": [{"device": {}, "metrics": [
        {"displayName": "Duration (tuner.modeSwitch)", "identifier": "com.apple.dt.XCTMetric_OSSignpost-tuner.modeSwitch.duration",
         "unitOfMeasurement": "s", "measurements": [0.145, 0.143] * 10},
        {"displayName": "Clock Monotonic Time", "identifier": "com.apple.dt.XCTMetric_Clock.time.monotonic",
         "unitOfMeasurement": "s", "measurements": [4.3] * 20},
        {"displayName": "CPU Time", "identifier": "com.apple.dt.XCTMetric_CPU.time", "unitOfMeasurement": "s", "measurements": [0.9] * 20},
        {"displayName": "CPU Instructions Retired", "identifier": "com.apple.dt.XCTMetric_CPU.instructions_retired", "unitOfMeasurement": "kI", "measurements": [2.0] * 20},
        {"displayName": "Memory Peak Physical", "identifier": "com.apple.dt.XCTMetric_Memory.physical_peak", "unitOfMeasurement": "kB", "measurements": [9.0] * 20},
        {"displayName": "Memory Physical", "identifier": "com.apple.dt.XCTMetric_Memory.physical", "unitOfMeasurement": "kB", "measurements": [1.0] * 20},
    ]}]}
    path = tmp_path / "m.json"
    path.write_text(json.dumps([entry]))
    tuner = collect_round(str(path), cfg, simulator=True).journeys[0].metrics
    assert tuner["section.tuner.modeSwitch.sum_ms"].summary["median"] == pytest.approx(144.0)


def test_cpu_cycles_and_unknown_metrics_are_skipped_not_fatal(cfg, caplog):
    with caplog.at_level("INFO", logger="tracepulse.collect_ios"):
        result = collect_round(METRICS, cfg, simulator=False)
    tuner = {j.journey: j for j in result.journeys}["tuner"].metrics
    assert "cpu.cycles" not in tuner


def test_missing_measurement_count_is_an_error(cfg, tmp_path):
    with open(METRICS) as fh:
        raw = json.load(fh)
    raw[2]["testRuns"][0]["metrics"][0]["measurements"] = raw[2]["testRuns"][0]["metrics"][0]["measurements"][:5]
    path = tmp_path / "short.json"
    path.write_text(json.dumps(raw))
    with pytest.raises(CollectError, match="5 measurements"):
        collect_round(str(path), cfg, simulator=False, iterations=20)


def test_preflight_attachment_becomes_the_fingerprint():
    fp = parse_preflight(os.path.join(FIXTURES, "preflight.json"))
    assert fp == {"thermal_state": "nominal", "battery_level": 0.83, "network_state": "none"}
    refused = parse_preflight(os.path.join(FIXTURES, "preflight-refused.json"))
    assert refused["thermal_state"] == "serious" and refused["network_state"] == "wifi"


def test_perf_refused_failure_maps_to_a_refusal():
    failures = [{"testIdentifier": "TembraPerfTests/TunerPerfTests/testTunerJourney()",
                 "message": "PERF-REFUSED: thermal_state=serious limit=nominal"}]
    refusal = refusal_from_failures(failures)
    assert isinstance(refusal, Refusal) and refusal.check == "thermal_state" and "serious" in refusal.message
    assert refusal_from_failures([{"testIdentifier": "x", "message": "XCTAssertTrue failed - perf anchor 'tuner.meter' not found"}]) is None


def test_engine_health_json_yields_the_nine_audio_series():
    series = engine_health(os.path.join(FIXTURES, "engine-health.json"))
    assert series == {"audio.callbacks": 1200.0, "audio.last_callback_us": 850.0, "audio.max_callback_us": 1900.0,
                      "audio.over_budget": 0.0, "audio.xruns": 0.0, "audio.realtime": 1.0,
                      "audio.avg_us": 410.0, "audio.starvation": 0.0, "audio.clips_active": 5.0}

def test_phone_identifier_forms_and_a_frameless_animation_interval(cfg, caplog):
    """The first iPhone 14 round (iOS 27.0.1, Xcode 27.1): memory and CPU carry the bundle id
    (`XCTMetric_Memory-com.tembraaudioP5QJJ5Y5V7.physical_peak`), `physical_absolute` and
    `cycles` are skipped, and the tempo change's animation interval held no frame in any
    iteration, so the hitch columns are dropped instead of publishing a 60 fps deficit."""
    with caplog.at_level("WARNING", logger="tracepulse.collect_ios"):
        result = collect_round(os.path.join(FIXTURES, "xcresult-metrics-iphone14.json"), cfg, simulator=False)
    journeys = {j.journey: j.metrics for j in result.journeys}
    tuner, metronome = journeys["tuner"], journeys["metronome"]
    assert 120_000 < tuner["memory.peak_kb"].summary["median"] < 140_000
    assert 110_000 < tuner["memory.end_kb"].summary["median"] < 140_000
    assert 2_000 < tuner["cpu.time_ms"].summary["median"] < 4_000 and "cpu.instructions_k" in tuner
    assert not any(k.startswith("frames.") for k in metronome), "no frames in the interval, no frames family"
    assert "frames._count" not in metronome
    assert 0.1 < metronome["section.metronome.tempoChange.sum_ms"].summary["median"] < 0.2
    assert any("contained no frames" in r.message for r in caplog.records)


def _windowing_entry(sections):
    metrics = [{"displayName": f"Duration ({name})", "identifier": f"com.apple.dt.XCTMetric_OSSignpost-{name}.duration",
                "unitOfMeasurement": "s", "measurements": [0.01] * 20} for name in sections]
    metrics.append({"displayName": "Clock Monotonic Time", "identifier": "com.apple.dt.XCTMetric_Clock.time.monotonic",
                    "unitOfMeasurement": "s", "measurements": [40.0] * 20})
    return {"testIdentifier": "TembraPerfTests/WindowingPerfTests/testWindowingJourney()", "testRuns": [{"device": {}, "metrics": metrics}]}


def test_a_windowing_round_without_its_three_sections_is_refused(cfg, tmp_path):
    # As on Android (feature 011, T129): a window change, the right-click menu or a destination chord that left no
    # section fails the round; the iPhone 14 trial's journey once tapped navigation items and recorded no chord.
    path = tmp_path / "w.json"
    path.write_text(json.dumps([_windowing_entry(["shell.windowRelayout", "timeline.contextMenu"])]))
    with pytest.raises(CollectError, match="shortcutNavigate"):
        collect_round(str(path), cfg, simulator=False)
    path.write_text(json.dumps([_windowing_entry(["shell.windowRelayout", "shell.shortcutNavigate", "timeline.contextMenu"])]))
    windowing = collect_round(str(path), cfg, simulator=False).journeys[0]
    assert {"section.shell.windowRelayout.sum_ms", "section.shell.shortcutNavigate.sum_ms",
            "section.timeline.contextMenu.sum_ms"} <= set(windowing.metrics)


def test_an_iphone_windowing_round_needs_no_menu_section(cfg, tmp_path):
    # An iPhone has no pointer to right-click with (XCUITest: "Pointer events are not supported for this device"), so its
    # windowing round records the window change and the chords only.
    path = tmp_path / "w.json"
    path.write_text(json.dumps([_windowing_entry(["shell.windowRelayout", "shell.shortcutNavigate"])]))
    windowing = collect_round(str(path), cfg, simulator=False, pointer=False).journeys[0]
    assert "section.shell.shortcutNavigate.sum_ms" in windowing.metrics
    path.write_text(json.dumps([_windowing_entry(["shell.windowRelayout"])]))
    with pytest.raises(CollectError, match="shortcutNavigate"):
        collect_round(str(path), cfg, simulator=False, pointer=False)


def test_a_physical_iphone_windowing_round_needs_the_relayout_section_only(cfg, tmp_path):
    # A physical iPhone takes neither a synthesized pointer nor synthesized ⌘ keys (the iPhone 14 trial, 2026-11-04: ⌘2
    # was typed and nothing opened; the iPhone simulator opens the Tuner): its round records the window change only.
    path = tmp_path / "w.json"
    path.write_text(json.dumps([_windowing_entry(["shell.windowRelayout"])]))
    windowing = collect_round(str(path), cfg, simulator=False, pointer=False, keyboard=False).journeys[0]
    assert "section.shell.windowRelayout.sum_ms" in windowing.metrics
    path.write_text(json.dumps([_windowing_entry(["timeline.contextMenu"])]))
    with pytest.raises(CollectError, match="windowRelayout"):
        collect_round(str(path), cfg, simulator=False, pointer=False, keyboard=False)
