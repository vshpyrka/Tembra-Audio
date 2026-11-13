"""The device-grid report (contracts/report.md): rendered from the fixture history
(Pixel 6 Pro, iPhone 14, iPhone 17 simulator; two labels; one confirmed onset;
two gate flags; one OS-version change) and compared byte-for-byte to the golden;
no external requests; a `#/device/<id>` link per device; the simulator card is
marked non-reference."""

import glob
import json
import os
import re

import pytest

from tracepulse.analysis import analyze_all
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.gate import record_all_gates
from tracepulse.records import RoundRecord
from tracepulse.report import generate_report, report_data
from tracepulse.store import Store

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
HISTORY = os.path.join(FIXTURES, "history-3-devices")
GOLDEN = os.path.join(FIXTURES, "report-golden.html")
NOW_TS = 1_791_900_000  # 2026-10-13 12:40 UTC, fixed so the page is byte-stable
PIXEL = "19021FDEE001AH"
IPHONE = "00008110-001049660E3A401E"
SIM = "4CD6879C-8FBA-4CEF-B4EC-31968D0144B0"


@pytest.fixture(scope="module")
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


@pytest.fixture(scope="module")
def store(tmp_path_factory, cfg):
    records = [RoundRecord.load(p) for p in sorted(glob.glob(os.path.join(HISTORY, "records", "*", "*.json")))]
    db = str(tmp_path_factory.mktemp("history") / "tracepulse.db")
    s = Store.rebuild(db, records)
    analyze_all(s, cfg)
    record_all_gates(s, cfg)
    yield s
    s.close()


@pytest.fixture(scope="module")
def html_text(store, cfg, tmp_path_factory):
    out = str(tmp_path_factory.mktemp("report") / "report.html")
    generate_report(store, cfg, out, now_ts=NOW_TS)
    with open(out, encoding="utf-8") as fh:
        return fh.read()


@pytest.fixture(scope="module")
def data(store, cfg):
    return report_data(store, cfg)


def test_golden_byte_stable(html_text):
    if os.environ.get("UPDATE_GOLDEN") == "1":
        with open(GOLDEN, "w", encoding="utf-8") as fh:
            fh.write(html_text)
        pytest.fail("golden updated — rerun without UPDATE_GOLDEN")
    with open(GOLDEN, encoding="utf-8") as fh:
        assert html_text == fh.read()


def test_no_external_requests(html_text):
    urls = re.findall(r"https?://[^\s\"'<>]+", html_text)
    for url in urls:
        assert False, f"external URL in the report: {url}"
    assert "<link" not in html_text and "src=\"http" not in html_text and "@import" not in html_text


def test_every_device_has_a_card_and_a_hash_link(html_text, data):
    for device in data["devices"]:
        assert f"#/device/{device['device_id']}" in html_text
    assert len(data["devices"]) == 3


def test_simulator_card_is_non_reference(data):
    sim = next(d for d in data["devices"] if d["device_id"] == SIM)
    assert sim["kind"] == "simulator" and sim["reference"] is False
    assert "non-reference" in sim["badges"] and "simulator" in sim["badges"]
    assert sim["note"].startswith("Simulator numbers")
    pixel = next(d for d in data["devices"] if d["device_id"] == PIXEL)
    assert pixel["badges"] == []


def test_confirmed_onset_is_attributed_to_the_label(data):
    pixel = next(d for d in data["devices"] if d["device_id"] == PIXEL)
    startup = next(s for f in pixel["families"] for s in f["series"] if s["key"] == "startup/startup.ttid_ms")
    confirmed = [d for d in startup["detections"] if d["status"] == "CONFIRMED"]
    assert confirmed and confirmed[0]["label_at_onset"] == "007-performance-pipeline"
    assert confirmed[0]["onset_round_index"] == 8
    assert startup["sentence"].startswith("Confirmed: app start time (first picture on screen) stepped up")
    assert "in 007-performance-pipeline" in startup["sentence"]
    assert pixel["label_boundaries"] == [{"round_index": 8, "round_key": "20261009T120000Z_007-performance-pipeline",
                                         "label": "007-performance-pipeline"}]
    assert any(c["status"] == "CONFIRMED" and c["device_id"] == PIXEL for c in data["changes"])


def test_gate_flags_on_the_iphone_latest_round(data):
    iphone = next(d for d in data["devices"] if d["device_id"] == IPHONE)
    flagged = [f for f in data["flagged"] if f["device_id"] == IPHONE]
    assert {f["metric"] for f in flagged} >= {"startup/startup.ttid_ms", "tuner/memory.peak_kb"}
    assert iphone["headline"].startswith("2 metrics above their limit") or "above their limit" in iphone["headline"]
    latest = iphone["rounds"][-1]
    assert latest["gate_summary"]["above_limit"] >= 2
    changes = iphone["fingerprint_changes"]
    assert {"round_index": 6, "round_key": "20261007T140000Z_007-performance-pipeline",
            "field": "os_version", "before": "26.5", "after": "27.0.1"} in changes
    # the network state changed between rounds of one session: visible on the series, never alarmed (spec edge case)
    assert [c for c in changes if c["field"] == "network_state"] == [
        {"round_index": 9, "round_key": "20261010T140000Z_007-performance-pipeline", "field": "network_state", "before": "none", "after": "wifi"},
        {"round_index": 10, "round_key": "20261011T140000Z_007-performance-pipeline", "field": "network_state", "before": "wifi", "after": "none"},
    ]
    assert iphone["rounds"][9]["fingerprint"]["network_state"] == "wifi"


def test_series_never_overlay_platforms_and_frames_families_differ(data):
    pixel = next(d for d in data["devices"] if d["device_id"] == PIXEL)
    iphone = next(d for d in data["devices"] if d["device_id"] == IPHONE)
    pixel_frames = {s["metric"] for f in pixel["families"] if f["family"] == "frames" for s in f["series"]}
    iphone_frames = {s["metric"] for f in iphone["families"] if f["family"] == "frames" for s in f["series"]}
    assert "frames.p90_ms" in pixel_frames and "frames.hitch_count" in iphone_frames
    assert not pixel_frames & iphone_frames
    sim = next(d for d in data["devices"] if d["device_id"] == SIM)
    assert not any(f["family"] == "frames" for f in sim["families"]), "simulators produce no frames family"


def test_points_are_compact_and_catalogue_notes_present(data, html_text):
    pixel = next(d for d in data["devices"] if d["device_id"] == PIXEL)
    series = pixel["families"][0]["series"][0]
    assert all(len(p) == 3 for p in series["points"]), "[median, p25, p75] per round"
    assert data["catalogue"]["startup.ttfd_ms"]["note"]
    assert data["catalogue"]["startup.ttid_ms"]["android"]["unit"] == "ms"
    assert len(html_text.encode("utf-8")) < 2_000_000


def test_report_data_is_json_serialisable_and_sorted(data):
    text = json.dumps(data, sort_keys=True)
    assert "19021FDEE001AH" in text


# --- labels (User Story 6): boundaries on every series, onsets attributed to the label --------------------

def test_label_boundaries_are_drawn_once_per_label_change(data, html_text):
    pixel = next(d for d in data["devices"] if d["device_id"] == PIXEL)
    iphone = next(d for d in data["devices"] if d["device_id"] == IPHONE)
    sim = next(d for d in data["devices"] if d["device_id"] == SIM)
    assert [b["label"] for b in pixel["label_boundaries"]] == ["007-performance-pipeline"]
    assert [b["label"] for b in iphone["label_boundaries"]] == ["007-performance-pipeline"]
    assert sim["label_boundaries"] == [], "one label, no boundary"
    assert pixel["labels"] == ["006-tuner-noise-gate", "007-performance-pipeline"]
    # the chart code draws one dashed marker and one label per boundary of the device, on every chart
    assert "dev.label_boundaries.forEach" in html_text and 'class="boundary"' in html_text and 'class="boundary-label"' in html_text


def test_onset_with_a_label_is_described_in_that_label_on_the_device_and_in_changes(data):
    pixel = next(d for d in data["devices"] if d["device_id"] == PIXEL)
    startup = next(s for f in pixel["families"] for s in f["series"] if s["key"] == "startup/startup.ttid_ms")
    assert "in 007-performance-pipeline" in startup["sentence"]
    assert "in 007-performance-pipeline" in pixel["headline"]
    change = next(c for c in data["changes"] if c["device_id"] == PIXEL and c["metric"] == "startup/startup.ttid_ms")
    assert change["label_at_onset"] == "007-performance-pipeline" and "in 007-performance-pipeline" in change["sentence"]
    assert data["summary"]["text"].startswith("3 devices, 32 rounds")


# --- the overview on the front page ----------------------------------------------------------------------

def test_overview_is_in_the_data(data):
    rows = {d["device_id"]: d for d in data["overview"]["devices"]}
    assert set(rows) == {PIXEL, IPHONE}, "the simulator has no row"
    for device_id in (PIXEL, IPHONE):
        assert rows[device_id]["before"] == "006-tuner-noise-gate"
        assert rows[device_id]["after"] == "007-performance-pipeline"


def test_static_page_prints_the_where_table(html_text, data):
    assert 'id="overview-where"' in html_text
    table = html_text[html_text.index('id="overview-where"'):]
    table = table[:table.index("</table>")]
    for device in data["overview"]["devices"]:
        assert device["model"] in table
    assert "006-tuner-noise-gate" in table and "007-performance-pipeline" in table


def test_overview_adds_no_requests(html_text):
    overview = html_text[html_text.index("function overviewHtml"):]
    assert not re.search(r"https?://|fetch\(|XMLHttpRequest|@import", overview[:20000])
    assert "<link" not in html_text
