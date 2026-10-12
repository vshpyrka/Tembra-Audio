"""Round records: the portable, trace-free form of a round that travels on the
perf-history branch (contracts/round-record.schema.json)."""

import json
import os
from datetime import datetime, timezone

import pytest

from tracepulse.records import (
    SCHEMA_PATH, DeviceInfo, JourneyResult, MetricResult, RecordError, RoundRecord,
    make_round_id, record_path,
)


def _record(**overrides) -> RoundRecord:
    base = dict(
        round_id="20261008T140211Z_007-performance-pipeline",
        device=DeviceInfo(device_id="19021FDEE001AH", platform="android", kind="device",
                          model="Pixel 6 Pro", reference=True),
        label="007-performance-pipeline",
        build={"commit": "abc1234", "branch": "007-performance-pipeline", "dirty": False,
               "app_size_bytes": 12345678},
        fingerprint={"os_version": "17", "os_build": "BP1A.251005.004", "sdk": 37,
                     "refresh_rate_hz": 60.0, "network_state": "none", "battery_temp_c": 29.1,
                     "battery_level": 87},
        machine={"hostname": "mbp", "os": "macOS 26.1", "tool_version": "0.2.0"},
        checks=[{"name": "battery", "passed": True, "value": 87, "limit": 50}],
        status="succeeded",
        journeys=[
            JourneyResult(journey="startup", iterations=20, metrics={
                "startup.ttid_ms": MetricResult(unit="ms", samples=[300.0 + i for i in range(20)],
                                                source="macrobenchmark"),
            }),
            JourneyResult(journey="tuner", iterations=20, metrics={
                "frames.p90_ms": MetricResult(unit="ms", samples=[8.0] * 20, source="macrobenchmark"),
            }),
        ],
        started_at="2026-10-08T14:02:11Z",
        finished_at="2026-10-08T14:19:40Z",
    )
    base.update(overrides)
    return RoundRecord(**base)


def test_schema_is_shipped_with_the_package():
    assert os.path.isfile(SCHEMA_PATH)
    with open(SCHEMA_PATH) as fh:
        schema = json.load(fh)
    assert schema["$id"] == "tembra/perf/round-record/1"


def test_round_trip_through_json_and_schema():
    rec = _record()
    text = rec.to_json()
    data = json.loads(text)
    assert data["schema_version"] == 1
    assert data["journeys"][0]["metrics"]["startup.ttid_ms"]["summary"]["median"] == 309.5
    assert data["journeys"][0]["metrics"]["startup.ttid_ms"]["summary"]["cov"] > 0
    RoundRecord.validate(data)  # raises on a schema violation
    back = RoundRecord.from_json(text)
    assert back == rec
    assert back.journeys[0].metrics["startup.ttid_ms"].summary["median"] == 309.5


def test_summary_is_derived_from_samples_at_construction():
    m = MetricResult(unit="ms", samples=[3.0, 1.0, 2.0], source="xcresult")
    assert m.summary["median"] == 2.0 and m.summary["min"] == 1.0 and m.summary["max"] == 3.0


def test_single_valued_metric_keeps_its_value_as_the_median():
    m = MetricResult(unit="bytes", samples=[], source="apk", value=1234.0)
    assert m.summary["median"] == 1234.0 and m.samples == []
    data = _record(journeys=[JourneyResult(journey="startup", iterations=1,
                                           metrics={"app.size_bytes": m})]).to_dict()
    RoundRecord.validate(data)


def test_round_id_pattern_is_enforced():
    with pytest.raises(RecordError, match="round_id"):
        RoundRecord.validate(_record(round_id="2026-10-08_label").to_dict())


def test_status_refused_is_never_a_record():
    with pytest.raises(RecordError, match="status"):
        RoundRecord.validate(_record(status="refused").to_dict())
    RoundRecord.validate(_record(status="failed", failure_reason="journey tuner: anchor missing",
                                 journeys=[]).to_dict())


def test_journey_names_are_the_four_known_ones():
    bad = _record(journeys=[JourneyResult(journey="scroll", iterations=20, metrics={})])
    with pytest.raises(RecordError, match="journey"):
        RoundRecord.validate(bad.to_dict())


def test_label_must_be_non_empty():
    with pytest.raises(RecordError, match="label"):
        RoundRecord.validate(_record(label="").to_dict())


def test_machine_is_metadata_only_and_required():
    data = _record().to_dict()
    del data["machine"]
    with pytest.raises(RecordError, match="machine"):
        RoundRecord.validate(data)
    rec = _record(machine={"hostname": "other-mac", "os": "macOS 12", "tool_version": "0.2.0"})
    assert rec.device.device_id == "19021FDEE001AH", "the series key is the device, never the machine"


def test_unknown_top_level_field_is_rejected():
    data = _record().to_dict()
    data["traces"] = ["/tmp/x.perfetto-trace"]
    with pytest.raises(RecordError):
        RoundRecord.validate(data)


def test_record_path_and_round_id():
    assert record_path("19021FDEE001AH", "20261008T140211Z_007-performance-pipeline") == \
        os.path.join("records", "19021FDEE001AH", "20261008T140211Z_007-performance-pipeline.json")
    started = datetime(2026, 10, 8, 14, 2, 11, tzinfo=timezone.utc)
    assert make_round_id(started, "007-performance-pipeline") == "20261008T140211Z_007-performance-pipeline"
    assert make_round_id(started, "feature with spaces/slash") == "20261008T140211Z_feature-with-spaces-slash"


def test_optional_blocks_round_trip():
    rec = _record(session={"index": 2, "size": 5}, badges=["noisy"],
                  gate=[{"metric": "startup.ttid_ms", "state": "above_limit", "limit_pct": 15,
                         "observed_pct": 18.4, "baseline_rounds": ["a", "b"]}],
                  artifacts=[{"kind": "trace", "path": "/Users/x/rounds/r/0.perfetto-trace"}])
    data = rec.to_dict()
    RoundRecord.validate(data)
    assert RoundRecord.from_dict(data) == rec
