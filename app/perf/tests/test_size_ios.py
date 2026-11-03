"""iOS app size (spike 6): an archive exported with an options plist of method
`development` and `thinning` `<thin-for-all-variants>` writes `App Thinning Size
Report.txt`; the compressed size of the configured variant is `app.size_bytes`,
the uncompressed size `app.size_uncompressed_bytes`; cached per build commit."""

import json
import os
import shutil

import pytest

from conftest import Completed, TableRunner
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.size_ios import SizeError, export_options_plist, parse_thinning_report, measure_size

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
REPORT = os.path.join(FIXTURES, "App Thinning Size Report.txt")


@pytest.fixture
def cfg(tmp_path):
    root = tmp_path / "app"
    (root / "iosApp" / "Configuration").mkdir(parents=True)
    (root / "iosApp" / "Configuration" / "Config.xcconfig").write_text("TEAM_ID=P5QJJ5Y5V7\nBUNDLE_ID=com.tembraaudio\n")
    shutil.copy(DEFAULT_CONFIG_PATH, root / "tracepulse.yml")
    return load_config(str(root / "tracepulse.yml"))


def test_parse_thinning_report_picks_the_variant():
    with open(REPORT) as fh:
        text = fh.read()
    sizes = parse_thinning_report(text, "iPhone14,7")
    assert sizes == {"app_size_bytes": 21_300_000, "app_size_uncompressed_bytes": 58_900_000}
    universal = parse_thinning_report(text, "Universal")
    assert universal["app_size_bytes"] == 23_000_000
    with pytest.raises(SizeError, match="iPhone99,9"):
        parse_thinning_report(text, "iPhone99,9")


def test_export_options_plist_has_development_method_and_thinning(tmp_path):
    path = export_options_plist(str(tmp_path / "export.plist"), team_id="P5QJJ5Y5V7")
    with open(path) as fh:
        text = fh.read()
    assert "<key>method</key>" in text and "<string>development</string>" in text
    assert "<key>thinning</key>" in text and "<string>&lt;thin-for-all-variants&gt;</string>" in text
    assert "<key>teamID</key>" in text and "P5QJJ5Y5V7" in text


def test_measure_runs_archive_and_export_once_and_caches_by_commit(cfg, tmp_path):
    data_dir = str(tmp_path / "data")

    def archive(cmd):
        archive_path = cmd[cmd.index("-archivePath") + 1]
        os.makedirs(archive_path, exist_ok=True)
        return Completed(0, "** ARCHIVE SUCCEEDED **\n")

    def export(cmd):
        out = cmd[cmd.index("-exportPath") + 1]
        os.makedirs(out, exist_ok=True)
        shutil.copy(REPORT, os.path.join(out, "App Thinning Size Report.txt"))
        return Completed(0, "** EXPORT SUCCEEDED **\n")

    runner = TableRunner([
        (lambda cmd: cmd[:2] == ["xcodebuild", "archive"], archive),
        (lambda cmd: cmd[:2] == ["xcodebuild", "-exportArchive"], export),
    ])
    first = measure_size(cfg, data_dir, runner, commit="c0ffee")
    assert first["app_size_bytes"] == 21_300_000 and first["app_size_uncompressed_bytes"] == 58_900_000
    archive_cmd = runner.commands()[0]
    assert archive_cmd[archive_cmd.index("-configuration") + 1] == "Perf" and archive_cmd[archive_cmd.index("-scheme") + 1] == "TembraPerf"
    assert archive_cmd[archive_cmd.index("-destination") + 1] == "generic/platform=iOS"
    export_cmd = runner.commands()[1]
    assert "-exportOptionsPlist" in export_cmd and export_cmd[export_cmd.index("-exportArchive") + 1] == "-archivePath"
    assert runner.count("xcodebuild") == 2
    second = measure_size(cfg, data_dir, runner, commit="c0ffee")
    assert second == first and runner.count("xcodebuild") == 2, "cached per commit"
    cache = json.load(open(os.path.join(data_dir, "size-cache", "ios-c0ffee.json")))
    assert cache["app_size_bytes"] == 21_300_000
    measure_size(cfg, data_dir, runner, commit="deadbeef")
    assert runner.count("xcodebuild") == 4
