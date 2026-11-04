"""The metric catalogue (contracts/metric-catalogue.md) is code: every key exists
per platform with unit, source, direction, comparability, a default gate limit and
a screening gate; tracepulse.yml may override limits and disable keys, never add one.
"""

import pytest

from pathlib import Path

from tracepulse.catalogue import (
    CATALOGUE, SECTION_FAMILIES, CatalogueError, Metric, apply_overrides, keys_for, lookup, metrics_for,
)

ENGINE_CATALOGUE = Path(__file__).resolve().parents[2] / "shared" / "engine" / "perf-catalogue.txt"


ANDROID_KEYS = {
    "startup.ttid_ms", "startup.ttfd_ms",
    "frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
    "memory.peak_kb", "memory.end_kb",
    "journey.duration_ms",
    "app.size_bytes",
    "audio.callbacks", "audio.last_callback_us", "audio.max_callback_us",
    "audio.over_budget", "audio.xruns", "audio.realtime",
    "audio.avg_us", "audio.starvation", "audio.clips_active",
    "audio.input_overruns", "audio.record_backlog", "audio.take_alignment_ms", "audio.monitor_delay_ms",
}

IOS_KEYS = {
    "startup.ttid_ms", "startup.ttfd_ms",
    "frames.hitch_ratio_ms_per_s", "frames.hitch_count", "frames.fps_deficit",
    "memory.peak_kb", "memory.end_kb",
    "cpu.time_ms", "cpu.instructions_k",
    "journey.duration_ms",
    "app.size_bytes", "app.size_uncompressed_bytes",
    "audio.callbacks", "audio.last_callback_us", "audio.max_callback_us",
    "audio.over_budget", "audio.xruns", "audio.realtime",
    "audio.avg_us", "audio.starvation", "audio.clips_active",
    "audio.input_overruns", "audio.record_backlog", "audio.take_alignment_ms", "audio.monitor_delay_ms",
    "audio.ioproc_p99_us",
}


def test_every_contract_key_exists_per_platform():
    assert set(keys_for("android")) == ANDROID_KEYS
    assert set(keys_for("ios")) == IOS_KEYS


def test_every_entry_is_fully_described():
    for (platform, key), metric in CATALOGUE.items():
        assert isinstance(metric, Metric)
        assert metric.platform == platform and metric.key == key
        assert metric.unit, key
        assert metric.source, key
        assert metric.family in {"startup", "frames", "memory", "cpu", "journey", "section", "app", "audio"}, key
        assert metric.higher_is_worse is True, key
        assert isinstance(metric.comparable, bool), key
        # exactly one gate limit kind and, unless single-valued, exactly one screening gate kind
        assert (metric.gate_limit_pct is None) != (metric.gate_limit_abs is None), key
        if metric.single_valued:
            assert metric.screening_pct is None and metric.screening_abs is None, key
        else:
            assert (metric.screening_pct is None) != (metric.screening_abs is None), key


def test_contract_values_spot_checks():
    ttid = lookup("android", "startup.ttid_ms")
    assert ttid.unit == "ms" and ttid.gate_limit_pct == 15 and ttid.screening_pct == 3
    assert ttid.source.startswith("macrobenchmark") and ttid.comparable is False
    assert lookup("ios", "startup.ttid_ms").source.startswith("xcresult")
    assert lookup("android", "frames.overrun_p90_ms").gate_limit_abs == 2.0
    assert lookup("android", "frames.overrun_p90_ms").screening_abs == 2.0
    hitch = lookup("ios", "frames.hitch_ratio_ms_per_s")
    assert hitch.gate_limit_abs == 5 and hitch.screening_abs == 2 and hitch.unit == "ms/s"
    assert lookup("ios", "frames.fps_deficit").gate_limit_abs == 5
    assert lookup("ios", "memory.end_kb").screening_pct == 5
    assert lookup("ios", "cpu.time_ms").gate_limit_pct == 20
    size = lookup("android", "app.size_bytes")
    assert size.single_valued and size.gate_limit_pct == 2 and size.unit == "bytes"
    assert lookup("ios", "app.size_uncompressed_bytes").gate_limit_pct == 2
    assert lookup("android", "audio.xruns").comparable is True
    assert lookup("android", "audio.xruns").gate_limit_pct == 50
    assert lookup("ios", "audio.ioproc_p99_us").comparable is False
    assert lookup("ios", "audio.realtime").gate_limit_abs == 0.5  # any 0/1 flip flags


def test_section_keys_resolve_by_pattern_on_both_platforms():
    for platform in ("android", "ios"):
        s = lookup(platform, "section.tuner.modeSwitch.sum_ms")
        assert s.family == "section" and s.unit == "ms" and s.comparable is True
        assert s.gate_limit_pct == 20 and s.screening_pct == 5
        c = lookup(platform, "section.tuner.modeSwitch.count")
        assert c.unit == "count" and c.screening_pct == 10 and c.comparable is True
    with pytest.raises(CatalogueError):
        lookup("android", "section.tuner.modeSwitch.p99_ms")


def test_shell_family_sections_resolve_and_unknown_families_raise():
    # Feature 011 (contracts/perf-instrumentation.md): window changes and shortcut navigation are the shell family.
    assert "shell" in SECTION_FAMILIES
    for platform in ("android", "ios"):
        for name in ("shell.windowRelayout", "shell.shortcutNavigate", "timeline.contextMenu"):
            s = lookup(platform, f"section.{name}.sum_ms")
            assert s.family == "section" and s.unit == "ms" and s.comparable is True, name
    with pytest.raises(CatalogueError, match="section family"):
        lookup("android", "section.madeUp.windowRelayout.sum_ms")


def test_section_families_match_the_engine_catalogue():
    families = set()
    for line in ENGINE_CATALOGUE.read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        _, family, signpost = line.split()
        if family != "counter":
            families.add(family)
            assert signpost.split(".")[0] == family, signpost
    assert families == set(SECTION_FAMILIES)


def test_unknown_key_or_platform_raises():
    with pytest.raises(CatalogueError):
        lookup("android", "cpu.time_ms")
    with pytest.raises(CatalogueError):
        lookup("macos", "startup.ttid_ms")


def test_overrides_change_limits_and_disable_but_never_add():
    table = apply_overrides(limits_pct={"startup.ttid_ms": 1}, disabled=["memory.end_kb"])
    assert table[("android", "startup.ttid_ms")].gate_limit_pct == 1
    assert table[("ios", "startup.ttid_ms")].gate_limit_pct == 1
    assert table[("android", "memory.end_kb")].enabled is False
    assert table[("ios", "memory.end_kb")].enabled is False
    assert CATALOGUE[("android", "startup.ttid_ms")].gate_limit_pct == 15, "the base table is immutable"
    with pytest.raises(CatalogueError, match="not in the catalogue"):
        apply_overrides(limits_pct={"startup.made_up_ms": 1})
    with pytest.raises(CatalogueError, match="not in the catalogue"):
        apply_overrides(disabled=["frames.made_up"])


def test_metrics_for_platform_respects_disabled():
    table = apply_overrides(disabled=["cpu.instructions_k"])
    keys = {m.key for m in metrics_for("ios", table)}
    assert "cpu.instructions_k" not in keys and "cpu.time_ms" in keys


def test_recording_counters_gates_and_journeys():
    """Feature 010: input overruns flag any increase, the backlog has a 2048-frame floor, the rest a 2 ms floor."""
    for platform in ("android", "ios"):
        overruns = CATALOGUE[(platform, "audio.input_overruns")]
        assert overruns.gate_limit_abs == 1.0 and overruns.screening_abs == 1.0 and overruns.journeys == ("stress",)
        backlog = CATALOGUE[(platform, "audio.record_backlog")]
        assert backlog.gate_limit_pct == 50 and backlog.floor_abs == 2048.0 and backlog.unit == "frames"
        for key in ("audio.take_alignment_ms", "audio.monitor_delay_ms"):
            metric = CATALOGUE[(platform, key)]
            assert metric.gate_limit_pct == 20 and metric.floor_abs == 2.0 and metric.unit == "ms"
    assert "recording" in CATALOGUE[("android", "frames.p50_ms")].journeys
    assert "recording" in CATALOGUE[("ios", "journey.duration_ms")].journeys


def test_report_orders_the_recording_journey_between_timeline_and_stress():
    from tracepulse.report import _JOURNEY_ORDER
    assert sorted(_JOURNEY_ORDER, key=_JOURNEY_ORDER.get) == ["startup", "tuner", "metronome", "timeline", "recording", "stress"]
