"""Plain-language text for the report (contracts/report.md "Human text"): device
headlines for the four states, per-chart sentences per detection status naming
the label at onset, and the metric names in everyday words."""

from tracepulse.text import (
    PLAIN_METRIC, PLAIN_STATUS, chart_sentence, device_headline, flagged_summary, gate_limit, gate_observed,
    plain_metric, plain_series, status_ladder,
)


def test_plain_metric_covers_both_platforms_and_sections():
    assert plain_metric("startup.ttid_ms") == "App start time (first picture on screen)"
    assert plain_metric("frames.hitch_ratio_ms_per_s").startswith("Stutter")
    assert plain_metric("cpu.time_ms") == "Processor time used by the journey"
    assert plain_metric("audio.xruns") == "Audio dropouts (underruns and overruns)"
    assert plain_metric("section.tuner.modeSwitch.sum_ms") == "Time inside tuner.modeSwitch"
    assert plain_metric("section.tuner.modeSwitch.count") == "How often tuner.modeSwitch ran"
    assert plain_metric("made.up") == "made.up"
    assert plain_series("tuner/frames.p90_ms") == "Slow frames (smoothness) during the tuner journey"
    assert plain_series("app.size_bytes") == "App download size"
    for key in PLAIN_METRIC:
        assert PLAIN_METRIC[key]


def test_status_ladder_words():
    assert PLAIN_STATUS["CONFIRMED"].startswith("Confirmed")
    assert status_ladder("CONFIRMED") == "Confirmed"
    assert status_ladder("SUSPECT") == "Suspect"
    assert status_ladder("PRELIMINARY") == "First hint"
    assert status_ladder("IMPROVED") == "Improved"
    assert status_ladder("ENV_SUSPECT") == "Environment-suspect"
    assert status_ladder(None) == "Steady"


def test_device_headline_nothing_changed():
    text = device_headline(rounds=12, min_history=11, detections=[], flagged=[], window=5)
    assert text == "Nothing changed in the last 5 rounds."


def test_device_headline_preliminary_only_below_min_history():
    text = device_headline(rounds=4, min_history=11, detections=[], flagged=[], window=5)
    assert text == "Preliminary only: 4 of 11 rounds needed for a verdict."


def test_device_headline_names_the_worst_onset_and_its_label():
    detections = [
        {"metric": "startup/startup.ttid_ms", "status": "SUSPECT", "delta_pct": 18.4, "label_at_onset": "007-performance-pipeline",
         "persistence_rounds_to_confirm": 3},
        {"metric": "tuner/memory.peak_kb", "status": "PRELIMINARY", "delta_pct": 4.0, "label_at_onset": "007-performance-pipeline"},
    ]
    text = device_headline(rounds=12, min_history=11, detections=detections, flagged=[], window=5)
    assert text == "App start time (first picture on screen) got 18 % slower in 007-performance-pipeline (suspect; confirmed after 3 more rounds)."
    confirmed = [dict(detections[0], status="CONFIRMED")]
    text = device_headline(rounds=12, min_history=11, detections=confirmed, flagged=[], window=5)
    assert text == "App start time (first picture on screen) got 18 % slower in 007-performance-pipeline (confirmed)."
    # among equal statuses the largest step leads, whatever the listing order (a side effect confirmed
    # alongside the injected regression on the iPhone, 2026-10-12)
    side_effect = {"metric": "tuner/cpu.instructions_k", "status": "CONFIRMED", "delta_pct": 27.5,
                   "label_at_onset": "007-performance-pipeline"}
    startup = dict(confirmed[0], delta_pct=44.3)
    text = device_headline(rounds=12, min_history=11, detections=[side_effect, startup], flagged=[], window=5)
    assert text.startswith("App start time (first picture on screen) got 44 % slower")


def test_device_headline_lists_gate_flags_of_the_latest_round():
    flagged = [{"metric": "startup/startup.ttid_ms", "observed_pct": 18.4, "limit_pct": 15},
               {"metric": "tuner/memory.peak_kb", "observed_pct": 16.1, "limit_pct": 15}]
    text = device_headline(rounds=12, min_history=11, detections=[], flagged=flagged, window=5)
    assert text == ("2 metrics above their limit in the latest round: startup/startup.ttid_ms (+18 %, limit 15 %), "
                    "tuner/memory.peak_kb (+16 %, limit 15 %).")


def test_gate_texts_use_the_limit_s_own_terms():
    pct = {"metric": "startup/startup.ttid_ms", "observed_pct": 18.4, "limit_pct": 15}
    abs_ = {"metric": "stress/frames.overrun_p90_ms", "observed_pct": -527.7, "observed_abs": 2.52, "limit_abs": 2.0,
            "unit": "ms", "device_model": "Pixel 6 Pro", "round_key": "r8"}
    assert gate_observed(pct) == "+18 %" and gate_limit(pct) == "15 %"
    assert gate_observed(abs_) == "+2.5 ms" and gate_limit(abs_) == "2 ms"
    assert gate_observed(abs_, "") == "+2.5 ms", "the flag's own unit is the fallback"
    text = device_headline(rounds=12, min_history=11, detections=[], flagged=[abs_], window=5)
    assert text == "1 metric above its limit in the latest round: stress/frames.overrun_p90_ms (+2.5 ms, limit 2 ms)."
    assert flagged_summary([abs_]) == ["Pixel 6 Pro: Frames missing their deadline during the stress journey "
                                       "+2.5 ms against the limit of 2 ms in round r8"]


def test_chart_sentence_per_status():
    assert chart_sentence(None, "startup/startup.ttid_ms") == "Steady: no step in this series."
    det = {"status": "CONFIRMED", "delta_pct": 21.3, "label_at_onset": "007-performance-pipeline", "onset_round_key": "r9"}
    assert chart_sentence(det, "startup/startup.ttid_ms") == \
        "Confirmed: app start time (first picture on screen) stepped up 21 % in 007-performance-pipeline (round r9)."
    det = dict(det, status="SUSPECT")
    assert chart_sentence(det, "startup/startup.ttid_ms").startswith("Suspect: ")
    det = dict(det, status="PRELIMINARY")
    assert chart_sentence(det, "startup/startup.ttid_ms").startswith("First hint: ")
    det = dict(det, status="IMPROVED", delta_pct=-12.0)
    assert chart_sentence(det, "startup/startup.ttid_ms") == \
        "Improved: app start time (first picture on screen) stepped down 12 % in 007-performance-pipeline (round r9)."
    det = dict(det, status="ENV_SUSPECT", delta_pct=30.0)
    assert chart_sentence(det, "startup/startup.ttid_ms").startswith("Environment-suspect: ")
    det = dict(det, status="CONFIRMED", label_at_onset=None)
    assert "in " not in chart_sentence(det, "startup/startup.ttid_ms").split("stepped up 30 %")[1].split("(round")[0]
