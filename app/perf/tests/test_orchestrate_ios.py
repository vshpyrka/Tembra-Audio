"""The iOS round (research D6): one `xcodebuild build-for-testing` per session, then
per round `devicectl` uninstall + install + warm-up launch, `xcodebuild
test-without-building` with the result bundle under the round, `xcresulttool get
test-results metrics` and `export attachments`; simulators go through `simctl`;
the bundle id carries the team suffix from Config.xcconfig."""

import json
import os
import shutil

import pytest

from conftest import Completed, FakeClock, TableRunner
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.devices import Device
from tracepulse.orchestrate_android import JourneyFailed, Refused
from tracepulse.orchestrate_ios import (
    IosRoundOptions, bundle_id_with_team, build_for_testing, doctor, find_attachment, run_round, xctestrun_path,
)

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
UDID = "00008110-001049660E3A401E"
SIM_UDID = "4CD6879C-8FBA-4CEF-B4EC-31968D0144B0"
IPHONE = Device(device_id=UDID, platform="ios", kind="device", model="iPhone 14", alias="iphone14", os_version="27.0.1", os_build="24A446")
SIM = Device(device_id=SIM_UDID, platform="ios", kind="simulator", model="iPhone 17 (simulator)", os_version="26.5", reference=False)


def _ok(text=""):
    return Completed(0, text)


@pytest.fixture
def cfg(tmp_path):
    root = tmp_path / "app"
    (root / "iosApp" / "Configuration").mkdir(parents=True)
    (root / "iosApp" / "Configuration" / "Config.xcconfig").write_text("TEAM_ID=P5QJJ5Y5V7\nBUNDLE_ID=audio.tembra.app\nAPP_NAME=Tembra Audio\n")
    shutil.copy(DEFAULT_CONFIG_PATH, root / "tracepulse.yml")
    return load_config(str(root / "tracepulse.yml"))


def _xcode_rules(cfg, round_dir, refused=False, simulator=False, fail_journey=False, sdk="27.1"):
    """The tool calls a round makes and the files they leave behind."""
    derived = os.path.join(round_dir, "DerivedData")

    def bft(cmd):
        products = os.path.join(derived, "Build", "Products")
        sdk = "iphonesimulator26.5" if "Simulator" in cmd[cmd.index("-destination") + 1] else "iphoneos27.0"
        os.makedirs(os.path.join(products, "Perf-" + sdk.rstrip("0123456789."), "Tembra Audio.app"), exist_ok=True)
        os.makedirs(os.path.join(products, "Perf-" + sdk.rstrip("0123456789."), "TembraPerfTests-Runner.app"), exist_ok=True)
        with open(os.path.join(products, f"TembraPerf_TembraPerf_{sdk}-arm64.xctestrun"), "w") as fh:
            fh.write("plist")
        return _ok("** TEST BUILD SUCCEEDED **\n")

    def twb(cmd):
        bundle = cmd[cmd.index("-resultBundlePath") + 1]
        os.makedirs(bundle, exist_ok=True)
        if fail_journey:
            return Completed(65, "Test Case 'TunerPerfTests.testTunerJourney()' failed\n** TEST FAILED **\n")
        return _ok("** TEST SUCCEEDED **\n") if not refused else Completed(65, "** TEST FAILED **\n")

    def metrics(cmd):
        src = "xcresult-metrics-simulator.json" if simulator else "xcresult-metrics.json"
        with open(os.path.join(FIXTURES, src)) as fh:
            return _ok(fh.read())

    def summary(cmd):
        if refused:
            return _ok(json.dumps({"testFailures": [{"testIdentifier": "TembraPerfTests/TunerPerfTests/testTunerJourney()",
                                                      "message": "PERF-REFUSED: thermal_state=serious limit=nominal"}]}))
        if fail_journey:
            return _ok(json.dumps({"testFailures": [{"testIdentifier": "TembraPerfTests/TunerPerfTests/testTunerJourney()",
                                                      "message": "XCTAssertTrue failed - perf anchor 'tuner.meter' not found within 10.0 s"}]}))
        return _ok(json.dumps({"testFailures": []}))

    def attachments(cmd):
        out = cmd[cmd.index("--output-path") + 1]
        os.makedirs(out, exist_ok=True)
        shutil.copy(os.path.join(FIXTURES, "preflight-refused.json" if refused else "preflight.json"),
                    os.path.join(out, "preflight.json"))
        return _ok()

    def copy_health(cmd):
        dest = cmd[cmd.index("--destination") + 1]
        assert dest.endswith("engine-health.json"), "devicectl copy wants the destination file, not its directory"
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copy(os.path.join(FIXTURES, "engine-health.json"), dest)
        return _ok()

    def container(cmd):
        d = os.path.join(round_dir, "container", "Documents")
        os.makedirs(d, exist_ok=True)
        shutil.copy(os.path.join(FIXTURES, "engine-health.json"), os.path.join(d, "engine-health.json"))
        return _ok(os.path.join(round_dir, "container") + "\n")

    def launch(cmd):
        # `--json-output <file>` (the audio-trace pass) gets the pid the real tool writes
        if "--json-output" in cmd:
            with open(cmd[cmd.index("--json-output") + 1], "w") as fh:
                json.dump({"result": {"process": {"processIdentifier": 4242}}}, fh)
        return _ok()

    def xctrace_record(cmd):
        assert "--attach" in cmd and "4242" in cmd and "--launch" not in cmd, "attach to the launched pid, never --launch"
        os.makedirs(cmd[cmd.index("--output") + 1], exist_ok=True)
        return _ok()

    def xctrace_export(cmd):
        with open(os.path.join(FIXTURES, "audio-ioproc.xml")) as fh:
            return _ok(fh.read())

    def devicectl_list(cmd):
        out = cmd[cmd.index("--json-output") + 1]
        with open(os.path.join(FIXTURES, "devicectl-list.json")) as fh, open(out, "w") as dst:
            dst.write(fh.read())
        return _ok()

    return [
        (["xcrun", "xcodebuild", "-version"], _ok("Xcode 26.6\nBuild version 17F60\n")),
        (["xcrun", "--sdk", "iphoneos", "--show-sdk-version"], _ok(sdk + "\n")),
        (lambda cmd: cmd[:4] == ["xcrun", "devicectl", "list", "devices"], devicectl_list),
        (["xcrun", "simctl", "list", "devices", "booted", "-j"], _ok(json.dumps({"devices": {"com.apple.CoreSimulator.SimRuntime.iOS-26-5": [{"udid": SIM_UDID, "name": "iPhone 17", "state": "Booted"}]}}))),
        (lambda cmd: cmd[:2] == ["xcodebuild", "build-for-testing"], bft),
        (lambda cmd: cmd[:2] == ["xcodebuild", "test-without-building"], twb),
        (lambda cmd: cmd[:5] == ["xcrun", "xcresulttool", "get", "test-results", "metrics"], metrics),
        (lambda cmd: cmd[:5] == ["xcrun", "xcresulttool", "get", "test-results", "summary"], summary),
        (lambda cmd: cmd[:4] == ["xcrun", "xcresulttool", "export", "attachments"], attachments),
        (lambda cmd: cmd[:5] == ["xcrun", "devicectl", "device", "copy", "from"], copy_health),
        (lambda cmd: cmd[:4] == ["xcrun", "devicectl", "device", "uninstall"], _ok()),
        (lambda cmd: cmd[:4] == ["xcrun", "devicectl", "device", "install"], _ok()),
        (lambda cmd: cmd[:5] == ["xcrun", "devicectl", "device", "process", "launch"], launch),
        (lambda cmd: cmd[:5] == ["xcrun", "devicectl", "device", "process", "terminate"], _ok()),
        (lambda cmd: cmd[:4] == ["xcrun", "devicectl", "device", "info"], _ok(json.dumps({"result": {"lockState": {"passcodeRequired": False}}}))),
        (lambda cmd: cmd[:3] == ["xcrun", "simctl", "get_app_container"], container),
        (lambda cmd: cmd[:3] == ["xcrun", "simctl", "uninstall"], _ok()),
        (lambda cmd: cmd[:3] == ["xcrun", "simctl", "install"], _ok()),
        (lambda cmd: cmd[:3] == ["xcrun", "simctl", "privacy"], _ok()),
        (lambda cmd: cmd[:3] == ["xcrun", "simctl", "launch"], _ok()),
        (lambda cmd: cmd[:3] == ["xcrun", "simctl", "terminate"], _ok()),
        (lambda cmd: cmd[:3] == ["xcrun", "xctrace", "record"], xctrace_record),
        (lambda cmd: cmd[:3] == ["xcrun", "xctrace", "export"], xctrace_export),
    ]


def test_bundle_id_gains_the_team_suffix(cfg):
    assert bundle_id_with_team(cfg) == "audio.tembra.appP5QJJ5Y5V7"


def test_build_for_testing_once_per_session_and_the_xctestrun_is_found(cfg, tmp_path):
    round_dir = str(tmp_path / "r1")
    runner = TableRunner(_xcode_rules(cfg, round_dir))
    path = build_for_testing(cfg, IPHONE, runner, os.path.join(round_dir, "DerivedData"), "Perf")
    cmd = runner.calls[0]["cmd"]
    assert cmd[:2] == ["xcodebuild", "build-for-testing"]
    assert "-project" in cmd and cmd[cmd.index("-project") + 1].endswith("iosApp/iosApp.xcodeproj")
    assert cmd[cmd.index("-scheme") + 1] == "TembraPerf" and cmd[cmd.index("-configuration") + 1] == "Perf"
    assert cmd[cmd.index("-destination") + 1] == f"platform=iOS,id={UDID}"
    assert "-allowProvisioningUpdates" in cmd and cmd[cmd.index("-derivedDataPath") + 1] == os.path.join(round_dir, "DerivedData")
    assert runner.calls[0]["cwd"] == os.path.join(cfg.root, "iosApp")
    assert path.endswith(".xctestrun") and xctestrun_path(os.path.join(round_dir, "DerivedData")) == path


def test_round_runs_install_warm_up_test_and_collects(cfg, tmp_path):
    round_dir = str(tmp_path / "r1")
    runner = TableRunner(_xcode_rules(cfg, round_dir))
    xctestrun = build_for_testing(cfg, IPHONE, runner, os.path.join(round_dir, "DerivedData"), "Perf")
    outcome = run_round(cfg, IPHONE, runner, FakeClock(), round_dir, IosRoundOptions(journeys=cfg.journeys, xctestrun=xctestrun))
    cmds = runner.commands()
    uninstall = runner.index_of("devicectl", "device", "uninstall", "app", "audio.tembra.appP5QJJ5Y5V7")
    install = runner.index_of("devicectl", "device install", "app")
    launch = runner.index_of("devicectl", "device", "process", "launch", "--terminate-existing", "audio.tembra.appP5QJJ5Y5V7")
    test = runner.index_of("xcodebuild", "test-without-building")
    assert 0 <= uninstall < install < launch < test
    twb = cmds[test]
    assert twb[twb.index("-xctestrun") + 1] == xctestrun
    assert twb[twb.index("-destination") + 1] == f"platform=iOS,id={UDID}"
    assert twb[twb.index("-resultBundlePath") + 1] == os.path.join(round_dir, "perf.xcresult")
    assert "-only-testing:TembraPerfTests" in twb
    assert runner.index_of("xcresulttool", "get", "test-results", "metrics", "--compact") > test
    assert runner.index_of("xcresulttool", "export", "attachments") > test
    assert [j.journey for j in outcome.journeys] == ["startup", "tuner", "metronome", "stress"]
    assert outcome.fingerprint["thermal_state"] == "nominal" and outcome.fingerprint["network_state"] == "none"
    assert outcome.fingerprint["os_version"] == "27.0.1" and outcome.fingerprint["battery_level"] == 0.83
    assert outcome.checks and all(c["passed"] for c in outcome.checks)
    assert {a["kind"] for a in outcome.artifacts} >= {"xcresult", "engine_health", "audio_trace"}
    stress = next(j for j in outcome.journeys if j.journey == "stress")
    assert stress.metrics["audio.callbacks"].summary["median"] == 1200.0 and stress.metrics["audio.callbacks"].source == "engine-health"
    assert outcome.app_size_bytes is None, "size is opt-in on iOS"
    # the audio-trace pass: the measured run skips AudioTracePass, the pass runs it alone while xctrace is attached
    assert any("-skip-testing:TembraPerfTests/AudioTracePass" in c for c in cmds if c[:2] == ["xcodebuild", "test-without-building"])
    audio_pass = runner.index_of("xcodebuild", "test-without-building", "-only-testing:TembraPerfTests/AudioTracePass")
    record = runner.index_of("xctrace", "record", "Audio System Trace", f"--device {UDID}")
    launch_for_trace = runner.index_of("devicectl", "device", "process", "launch", "--json-output")
    assert test < launch_for_trace < audio_pass and record > launch_for_trace
    assert cmds[audio_pass][cmds[audio_pass].index("-resultBundlePath") + 1] == os.path.join(round_dir, "audio-pass.xcresult")
    assert stress.metrics["audio.ioproc_p99_us"].source == "xctrace" and stress.metrics["audio.ioproc_p99_us"].summary["median"] > 0


def test_refusal_inside_the_runner_refuses_the_round(cfg, tmp_path):
    round_dir = str(tmp_path / "r1")
    runner = TableRunner(_xcode_rules(cfg, round_dir, refused=True))
    with pytest.raises(Refused) as exc:
        run_round(cfg, IPHONE, runner, FakeClock(), round_dir, IosRoundOptions(journeys=cfg.journeys, xctestrun="x.xctestrun"))
    assert "thermal_state" in str(exc.value)


def test_untrusted_runner_profile_refuses_the_round(cfg, tmp_path):
    round_dir = str(tmp_path / "r1")
    rules = _xcode_rules(cfg, round_dir)
    rules.insert(0, (lambda cmd: cmd[:2] == ["xcodebuild", "test-without-building"], Completed(
        65, "Failed to install or launch the test runner. (Underlying Error: The application could not be launched because "
            "the Developer App Certificate is not trusted ... its profile has not been explicitly trusted by the user.)")))
    with pytest.raises(Refused) as exc:
        run_round(cfg, IPHONE, TableRunner(rules), FakeClock(), round_dir, IosRoundOptions(journeys=cfg.journeys, xctestrun="x.xctestrun"))
    assert "runner_trusted" in str(exc.value) and "VPN & Device Management" in str(exc.value)


def test_anchor_failure_is_a_failed_round(cfg, tmp_path):
    round_dir = str(tmp_path / "r1")
    runner = TableRunner(_xcode_rules(cfg, round_dir, fail_journey=True))
    with pytest.raises(JourneyFailed) as exc:
        run_round(cfg, IPHONE, runner, FakeClock(), round_dir, IosRoundOptions(journeys=cfg.journeys, xctestrun="x.xctestrun"))
    assert "tuner.meter" in exc.value.reason and exc.value.outcome.status == "failed"


def test_simulator_round_uses_simctl_and_the_simulator_destination(cfg, tmp_path):
    round_dir = str(tmp_path / "r1")
    runner = TableRunner(_xcode_rules(cfg, round_dir, simulator=True))
    xctestrun = build_for_testing(cfg, SIM, runner, os.path.join(round_dir, "DerivedData"), "Perf")
    assert runner.calls[0]["cmd"][runner.calls[0]["cmd"].index("-destination") + 1] == f"platform=iOS Simulator,id={SIM_UDID}"
    outcome = run_round(cfg, SIM, runner, FakeClock(), round_dir, IosRoundOptions(journeys=cfg.journeys, xctestrun=xctestrun))
    assert runner.index_of("simctl", "install", SIM_UDID) >= 0 and runner.index_of("simctl", "launch", SIM_UDID) >= 0
    assert runner.index_of("simctl", "privacy", SIM_UDID, "grant", "microphone", "audio.tembra.appP5QJJ5Y5V7") > \
        runner.index_of("simctl", "install", SIM_UDID), "a fresh install is granted the microphone before the run"
    assert runner.index_of("devicectl", "device", "install") == -1
    assert runner.index_of("simctl", "get_app_container", SIM_UDID, "audio.tembra.appP5QJJ5Y5V7", "data") >= 0
    assert outcome.badges == ["simulator"]
    assert not any(k.startswith("frames.") for j in outcome.journeys for k in j.metrics)
    assert runner.index_of("xctrace", "record") == -1, "a simulator has no HAL to trace"


def test_doctor_checks_pairing_xcode_support_and_lock_state_only(cfg, tmp_path):
    runner = TableRunner(_xcode_rules(cfg, str(tmp_path / "r")))
    checks = doctor(cfg, IPHONE, runner)
    names = [c.name for c in checks]
    assert names == ["xcode", "device_attached", "xcode_supports_device", "unlocked"]
    assert all(c.ok for c in checks)
    sim_checks = doctor(cfg, SIM, TableRunner(_xcode_rules(cfg, str(tmp_path / "s"))))
    assert [c.name for c in sim_checks] == ["xcode", "simulator_booted"]


def test_find_attachment_resolves_through_the_export_manifest(tmp_path):
    """The real export layout: UUID file names, the human name only in manifest.json."""
    shutil.copy(os.path.join(FIXTURES, "attachments-manifest.json"), tmp_path / "manifest.json")
    with open(os.path.join(FIXTURES, "attachments-manifest.json")) as fh:
        entries = json.load(fh)
    newest = max((a for e in entries for a in e["attachments"]), key=lambda a: a["timestamp"])
    for e in entries:
        for a in e["attachments"]:
            (tmp_path / a["exportedFileName"]).write_text('{"schema":1,"thermal_state":"nominal","battery_level":1,"network_state":"wifi","simulator":true,"refusal":null}')
    found = find_attachment(str(tmp_path), "preflight.json")
    assert found == str(tmp_path / newest["exportedFileName"])
    assert find_attachment(str(tmp_path), "engine-health.json") is None
    assert find_attachment(str(tmp_path / "missing"), "preflight.json") is None


def test_doctor_refuses_an_xcode_older_than_the_phones_ios(cfg, tmp_path):
    """iOS 27.0.1 on the phone with a 26.5 SDK: XCUITest launches die with CoreDevice
    10004 although devicectl works, so the check names the DEVELOPER_DIR fix."""
    checks = doctor(cfg, IPHONE, TableRunner(_xcode_rules(cfg, str(tmp_path / "r"), sdk="26.5")), scratch_dir=str(tmp_path))
    support = next(c for c in checks if c.name == "xcode_supports_device")
    assert not support.ok and "DEVELOPER_DIR" in support.detail and "27.0.1" in support.detail and "26.5" in support.detail
    ok = doctor(cfg, IPHONE, TableRunner(_xcode_rules(cfg, str(tmp_path / "r2"), sdk="27.1")), scratch_dir=str(tmp_path))
    assert next(c for c in ok if c.name == "xcode_supports_device").ok
    with pytest.raises(Refused):
        run_round(cfg, IPHONE, TableRunner(_xcode_rules(cfg, str(tmp_path / "r3"), sdk="26.5")), FakeClock(),
                  str(tmp_path / "r3"), IosRoundOptions(journeys=cfg.journeys, xctestrun="x.xctestrun"))
