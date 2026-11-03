"""`app/tracepulse.yml` loader (contracts/cli.md): the shipped file loads with the
trace-pulse detection defaults, the device registry, the platform sections and
the gate; an unknown key anywhere is a `ConfigError` (exit status 4)."""

import pytest
import yaml

from tracepulse.config import (
    DEFAULT_CONFIG_PATH, PERF_DIR, ConfigError, DeviceEntry, load_config,
)


def _raw():
    with open(DEFAULT_CONFIG_PATH) as fh:
        return yaml.safe_load(fh)


def _write(tmp_path, raw):
    p = tmp_path / "tracepulse.yml"
    p.write_text(yaml.safe_dump(raw))
    return str(p)


def test_shipped_yml_loads_trace_pulse_detection_defaults():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    assert cfg.detection.width == 5 and cfg.detection.threshold == 15
    assert cfg.detection.min_sd == 0.001 and cfg.detection.min_history == 11
    assert cfg.confirm.rounds_per_side == 2 and cfg.confirm.q == 0.05
    assert cfg.apk.min_delta_pct == 0.5
    assert cfg.ab.p_different == 0.05 and cfg.ab.p_same == 0.10 and cfg.ab.max_baseline_rounds == 2
    assert cfg.quality.cov_max == 0.15 and cfg.quality.battery_temp_max_c == 35.0
    assert cfg.quality.battery_min_level == 50
    assert cfg.suite_spike.min_dimensions == 3
    assert cfg.collection.iterations == 20 and cfg.collection.session_rounds == 5


def test_shipped_yml_history_gate_and_platforms():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    assert cfg.history.branch == "perf-history" and cfg.history.remote == "origin"
    assert cfg.history.worktree == "perf/.history"
    assert cfg.gate.clean_rounds == 5 and cfg.gate.simulators == "inactive"
    assert cfg.gate.limits_pct["app.size_bytes"] == 2
    assert cfg.android.package == "com.tembraaudio"
    assert cfg.android.activity == "audio.tembra.android.MainActivity"
    assert cfg.android.build_type == "perf" and cfg.android.benchmarks_module == ":perf-benchmarks"
    assert cfg.android.test_package == "audio.tembra.perf"
    assert cfg.ios.project == "iosApp/iosApp.xcodeproj" and cfg.ios.scheme == "TembraPerf"
    assert cfg.ios.configuration == "Perf" and cfg.ios.bundle_id == "com.tembraaudio"
    assert cfg.ios.test_target == "TembraPerfTests" and cfg.ios.size_variant == "iPhone14,7"
    assert cfg.journeys == ["startup", "tuner", "metronome", "timeline", "stress"]


def test_shipped_yml_device_registry():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    pixel = cfg.devices["19021FDEE001AH"]
    assert isinstance(pixel, DeviceEntry)
    assert pixel.platform == "android" and pixel.model == "Pixel 6 Pro" and pixel.alias == "pixel6"
    assert pixel.kind == "device" and pixel.reference is True
    assert pixel.overrides["battery_temp_max_c"] == 40.0
    iphone = cfg.devices["00008110-001049660E3A401E"]
    assert iphone.platform == "ios" and iphone.model == "iPhone 14" and iphone.alias == "iphone14"
    assert cfg.alias_to_id("pixel6") == "19021FDEE001AH"
    assert cfg.alias_to_id("19021FDEE001AH") == "19021FDEE001AH"
    assert cfg.alias_to_id("nope") is None


def test_config_path_and_root():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    assert cfg.path == DEFAULT_CONFIG_PATH
    assert cfg.root.endswith("/app") and PERF_DIR.endswith("/app/perf")


def test_unknown_top_level_key_is_a_config_error(tmp_path):
    raw = _raw()
    raw["ab_mode"] = {}
    with pytest.raises(ConfigError, match="ab_mode"):
        load_config(_write(tmp_path, raw))


def test_unknown_nested_key_is_a_config_error(tmp_path):
    raw = _raw()
    raw["gate"]["limit_pct"] = {}
    with pytest.raises(ConfigError, match="gate.limit_pct"):
        load_config(_write(tmp_path, raw))
    raw = _raw()
    raw["devices"]["19021FDEE001AH"]["serial"] = "x"
    with pytest.raises(ConfigError, match="devices.19021FDEE001AH.serial"):
        load_config(_write(tmp_path, raw))


def test_gate_limit_for_unknown_metric_is_a_config_error(tmp_path):
    raw = _raw()
    raw["gate"]["limits_pct"]["startup.made_up"] = 1
    with pytest.raises(ConfigError, match="startup.made_up"):
        load_config(_write(tmp_path, raw))


def test_detection_invariants_still_enforced(tmp_path):
    raw = _raw()
    raw["detection"]["min_history"] = 7
    with pytest.raises(ConfigError, match="min_history"):
        load_config(_write(tmp_path, raw))


def test_missing_sections_fall_back_to_defaults(tmp_path):
    minimal = {"android": {"package": "a.b", "activity": "a.b.Main"}}
    cfg = load_config(_write(tmp_path, minimal))
    assert cfg.detection.width == 5 and cfg.gate.clean_rounds == 5
    assert cfg.devices == {} and cfg.journeys == ["startup", "tuner", "metronome", "timeline", "stress"]


def test_catalogue_table_reflects_gate_overrides_and_disabled(tmp_path):
    raw = _raw()
    raw["gate"]["limits_pct"]["startup.ttid_ms"] = 1
    raw["gate"]["disabled"] = ["cpu.instructions_k"]
    cfg = load_config(_write(tmp_path, raw))
    assert cfg.catalogue[("android", "startup.ttid_ms")].gate_limit_pct == 1
    assert cfg.catalogue[("ios", "cpu.instructions_k")].enabled is False


def test_screening_gate_override_lands_in_catalogue(tmp_path):
    raw = _raw()
    raw["detection"]["screening_gates"] = {"min_delta_pct": {"startup.ttid_ms": 7}}
    cfg = load_config(_write(tmp_path, raw))
    assert cfg.catalogue[("android", "startup.ttid_ms")].screening_pct == 7
    assert cfg.catalogue[("android", "startup.ttfd_ms")].screening_pct == 3
