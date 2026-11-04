"""The Android round (contracts/cli.md): doctor -> measurement build (log captured
to the round directory) -> fresh install -> warm-up -> one `am instrument` per
journey class with `additionalTestOutputDir` -> pull -> APK size; a doctor
failure refuses before any build; a journey failure records a failed round with
its reason; `--no-build --apk --test-apk` installs prebuilt artifacts; the
release build type is selectable for the SC-007 comparison."""

import json
import os
import shutil

import pytest

from conftest import Completed, FakeClock, TableRunner
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.devices import Device
from tracepulse.orchestrate_android import (
    JourneyFailed, Refused, RoundOptions, doctor, fingerprint, run_round,
)

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
SERIAL = "19021FDEE001AH"
PIXEL = Device(device_id=SERIAL, platform="android", kind="device", model="Pixel 6 Pro", alias="pixel6",
               overrides={"battery_temp_max_c": 40.0})

BATTERY_OK = "  AC powered: true\n  USB powered: false\n  level: 87\n  temperature: 291\n"
BATTERY_HOT = "  AC powered: true\n  USB powered: false\n  level: 87\n  temperature: 412\n"
BATTERY_LOW = "  AC powered: true\n  USB powered: false\n  level: 42\n  temperature: 291\n"


def _ok(text=""):
    return Completed(0, text)


def _device_rules(tmp_path, battery=BATTERY_OK, awake=True, locked=False, airplane="1", peak="60",
                  instrument_ok=True, sdk_present=True):
    """Everything a healthy Pixel answers; `out` files for the detached instrument come from `cat`."""
    sdk = tmp_path / "sdk"
    if sdk_present:
        sdk.mkdir(exist_ok=True)
    os.environ["ANDROID_HOME"] = str(sdk)
    state = {"airplane": airplane, "peak": peak, "min": peak, "awake": awake, "locked": locked}

    def settings_get(cmd):
        key = cmd[-1]
        if key == "airplane_mode_on":
            return _ok(state["airplane"] + "\n")
        if key == "peak_refresh_rate":
            return _ok(state["peak"] + "\n")
        if key == "min_refresh_rate":
            return _ok(state["min"] + "\n")
        if key == "screen_brightness_mode":
            return _ok("0\n")
        return _ok("null\n")

    def settings_put(cmd):
        if cmd[-2] == "peak_refresh_rate":
            state["peak"] = cmd[-1]
        if cmd[-2] == "min_refresh_rate":
            state["min"] = cmd[-1]
        return _ok()

    def airplane_cmd(cmd):
        state["airplane"] = "1" if cmd[-1] == "enable" else "0"
        return _ok()

    def wake(cmd):
        state["awake"] = True
        return _ok()

    def dismiss(cmd):
        if not state.get("pin"):
            state["locked"] = False
        return _ok()

    terminal = "TRACEPULSE_ATTEMPT_START\n\nOK (1 test)\n" if instrument_ok else \
        "TRACEPULSE_ATTEMPT_START\n\nFAILURES!!!\nTests run: 1,  Failures: 1\n"
    return state, [
        (["/usr/libexec/java_home", "-v", "21"], _ok("/jdk21\n")),
        (["adb", "devices", "-l"], _ok(f"List of devices attached\n{SERIAL}  device product:raven model:Pixel_6_Pro\n")),
        (["adb", "-s", SERIAL, "shell", "dumpsys", "battery"], _ok(battery)),
        (["adb", "-s", SERIAL, "shell", "settings", "get"], settings_get),
        (["adb", "-s", SERIAL, "shell", "settings", "put"], settings_put),
        (["adb", "-s", SERIAL, "shell", "cmd", "connectivity", "airplane-mode"], airplane_cmd),
        (["adb", "-s", SERIAL, "shell", "df", "-k", "/data"], _ok("Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-59 115621972 16628096 98862804 15% /data/user/0\n")),
        (["adb", "-s", SERIAL, "shell", "dumpsys", "power"],
         lambda cmd: _ok("  mWakefulness=Awake\n" if state["awake"] else "  mWakefulness=Dozing\n")),
        (["adb", "-s", SERIAL, "shell", "dumpsys", "window", "policy"],
         lambda cmd: _ok("    KeyguardServiceDelegate\n      showing=%s\n" % ("true" if state["locked"] else "false"))),
        (["adb", "-s", SERIAL, "shell", "input", "keyevent", "KEYCODE_WAKEUP"], wake),
        (["adb", "-s", SERIAL, "shell", "wm", "dismiss-keyguard"], dismiss),
        (["adb", "-s", SERIAL, "shell", "svc", "power", "stayon"], _ok()),
        (["adb", "-s", SERIAL, "shell", "getprop", "ro.build.version.release"], _ok("17\n")),
        (["adb", "-s", SERIAL, "shell", "getprop", "ro.build.version.sdk"], _ok("37\n")),
        (["adb", "-s", SERIAL, "shell", "getprop", "ro.build.id"], _ok("CP3A.260905.009\n")),
        (["adb", "-s", SERIAL, "shell", "getprop", "ro.product.model"], _ok("Pixel 6 Pro\n")),
        (["adb", "-s", SERIAL, "shell", "pm", "uninstall"], _ok()),
        (["adb", "-s", SERIAL, "install"], _ok("Success\n")),
        (["adb", "-s", SERIAL, "shell", "pm", "grant"], _ok()),
        (["adb", "-s", SERIAL, "shell", "am", "start"], _ok("Status: ok\n")),
        (["adb", "-s", SERIAL, "shell", "input", "keyevent", "KEYCODE_HOME"], _ok()),
        (["adb", "-s", SERIAL, "shell", "am", "force-stop"], _ok()),
        (["adb", "-s", SERIAL, "shell", "rm", "-rf"], _ok()),
        (["adb", "-s", SERIAL, "shell", "rm", "-f"], _ok()),
        (lambda cmd: cmd[:4] == ["adb", "-s", SERIAL, "shell"] and cmd[4].startswith("nohup sh -c"), _ok()),
        (["adb", "-s", SERIAL, "shell", "cat"], _ok(terminal)),
        (["adb", "-s", SERIAL, "shell", "pidof"], _ok("1234\n")),
        (["adb", "-s", SERIAL, "pull"], lambda cmd: _pull(cmd)),
    ]


def _pull(cmd):
    """A real pull lands one benchmarkData.json under the class directory: only the class just run."""
    dest = cmd[-1]
    os.makedirs(dest, exist_ok=True)
    with open(os.path.join(FIXTURES, "benchmarkData-1.5.json")) as fh:
        raw = json.load(fh)
    wanted = os.path.basename(dest)
    matching = [b for b in raw["benchmarks"] if b["className"].endswith("." + wanted)]
    if not matching and wanted in ("TimelineJourneyBenchmark", "RecordingJourneyBenchmark"):
        # The 1.5 fixture predates feature 009's journey: stand in with the metronome's shape under the new class name.
        stem = wanted[:-len("JourneyBenchmark")].lower()
        # Sections carry a catalogue family (feature 011 checks it): the recording journey's are timeline and transport ones.
        family = "timeline" if stem == "recording" else stem
        for b in raw["benchmarks"]:
            if b["className"].endswith(".MetronomeJourneyBenchmark"):
                clone = json.loads(
                    json.dumps(b).replace("MetronomeJourneyBenchmark", wanted).replace("metronomeJourney", stem + "Journey")
                        .replace("journey.metronome", "journey." + stem).replace("metronome.", family + "."),
                )
                matching.append(clone)
    raw["benchmarks"] = matching
    with open(os.path.join(dest, "audio.tembra.perf-benchmarkData.json"), "w") as fh:
        json.dump(raw, fh)
    with open(os.path.join(dest, "iter000.perfetto-trace"), "wb") as fh:
        fh.write(b"\x0a\x00")
    return _ok()


def _gradle_rule(cfg, app_apk, test_apk):
    def build(cmd):
        for p in (app_apk, test_apk):
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "wb") as fh:
                fh.write(b"\x00" * 1234)
        return _ok("BUILD SUCCESSFUL in 4m 12s\n" + "x" * 5000)
    return (["./gradlew", "--console=plain"], build)


@pytest.fixture
def cfg(tmp_path):
    # the Gradle root for this test is tmp_path/app so the build writes APKs under it
    root = tmp_path / "app"
    root.mkdir()
    shutil.copy(DEFAULT_CONFIG_PATH, root / "tracepulse.yml")
    return load_config(str(root / "tracepulse.yml"))


def _apks(cfg, build_type="perf"):
    return (os.path.join(cfg.root, "androidApp", "build", "outputs", "apk", build_type, f"androidApp-{build_type}.apk"),
            os.path.join(cfg.root, "perf-benchmarks", "build", "outputs", "apk", build_type, f"perf-benchmarks-{build_type}.apk"))


def test_doctor_passes_on_a_healthy_pixel_and_uses_the_device_override(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path, battery=BATTERY_OK.replace("291", "385"))
    runner = TableRunner(rules)
    checks = doctor(cfg, PIXEL, runner)
    assert [c.name for c in checks] == ["java_home", "sdk_dir", "adb_device", "battery", "refresh_rate",
                                        "airplane", "brightness_manual", "storage", "awake", "unlocked"]
    assert all(c.ok for c in checks), [c for c in checks if not c.ok]
    assert "38.5" in next(c for c in checks if c.name == "battery").detail


def test_doctor_refuses_hot_or_low_battery_naming_the_threshold(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path, battery=BATTERY_HOT)
    battery = next(c for c in doctor(cfg, PIXEL, TableRunner(rules)) if c.name == "battery")
    assert not battery.ok and "41.2" in battery.detail and "40.0" in battery.detail
    _state, rules = _device_rules(tmp_path, battery=BATTERY_LOW)
    battery = next(c for c in doctor(cfg, PIXEL, TableRunner(rules)) if c.name == "battery")
    assert not battery.ok and "42" in battery.detail and "50" in battery.detail


def test_doctor_refuses_a_dozing_or_locked_phone(cfg, tmp_path):
    state, rules = _device_rules(tmp_path, awake=False, locked=True)
    state["pin"] = True  # dismiss-keyguard cannot unlock a PIN-protected phone
    checks = {c.name: c for c in doctor(cfg, PIXEL, TableRunner(rules))}
    assert checks["awake"].ok, "doctor wakes the screen itself"
    assert not checks["unlocked"].ok and "locked" in checks["unlocked"].detail


def test_fingerprint_reads_os_sdk_refresh_network_and_battery(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path)
    fp = fingerprint(cfg, PIXEL, TableRunner(rules))
    assert fp == {"os_version": "17", "os_build": "CP3A.260905.009", "sdk": 37, "refresh_rate_hz": 60.0,
                  "network_state": "none", "battery_temp_c": 29.1, "battery_level": 87, "model": "Pixel 6 Pro"}


def test_round_runs_every_step_in_order(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path)
    app_apk, test_apk = _apks(cfg)
    runner = TableRunner([_gradle_rule(cfg, app_apk, test_apk)] + rules)
    clock = FakeClock()
    round_dir = str(tmp_path / "rounds" / "r1")
    outcome = run_round(cfg, PIXEL, runner, clock, round_dir, RoundOptions(journeys=cfg.journeys))

    gradle = runner.index_of("./gradlew", "--console=plain", "-Ptembra.perf=true",
                             ":androidApp:assemblePerf", ":perf-benchmarks:assemblePerf")
    assert gradle >= 0 and runner.calls[gradle]["cwd"] == cfg.root
    assert runner.index_of("dumpsys", "battery") < gradle, "doctor runs before the build"
    assert os.path.isfile(os.path.join(round_dir, "gradle.log")) and os.path.getsize(os.path.join(round_dir, "gradle.log")) > 5000
    uninstall = runner.index_of("pm", "uninstall", "com.tembraaudio")
    install_app = runner.index_of("install", app_apk)
    install_test = runner.index_of("install", test_apk)
    warm = runner.index_of("am", "start", "-W", "com.tembraaudio/audio.tembra.android.MainActivity")
    stop = runner.index_of("am", "force-stop", "com.tembraaudio")
    assert gradle < uninstall < install_app < install_test < warm < stop
    instruments = [c for c in runner.commands() if any("am instrument" in part for part in c)]
    assert len(instruments) == 7
    for journey_class in ("StartupBenchmark", "TunerJourneyBenchmark", "MetronomeJourneyBenchmark", "TimelineJourneyBenchmark", "RecordingJourneyBenchmark",
                          "StressJourneyBenchmark", "WindowingJourneyBenchmark"):
        assert any(journey_class in " ".join(c) and "additionalTestOutputDir" in " ".join(c) for c in instruments), journey_class
    assert warm < runner.index_of("am instrument")
    assert runner.count("adb", "-s", SERIAL, "pull") == 7
    # The fixture's benchmark output predates feature 011's windowing journey: seven classes run, six report.
    assert [j.journey for j in outcome.journeys] == ["startup", "tuner", "metronome", "timeline", "recording", "stress"]
    assert outcome.app_size_bytes == 1234
    assert outcome.badges == [] and outcome.checks and all(c["passed"] for c in outcome.checks)
    assert outcome.fingerprint["os_version"] == "17"
    assert {a["kind"] for a in outcome.artifacts} >= {"benchmark_json", "trace", "gradle_log"}
    assert clock.sleeps.count(120) == 6, "a cooldown between classes, never after the last one"


def test_a_fresh_install_is_granted_the_microphone_and_notifications_before_the_warm_up(cfg, tmp_path):
    """Android 13+ asks for notifications on the first Play (the metronome's keep-alive service, 2026-10-16):
    ungranted, the dialog covers the app and the metronome journey never finds its anchors."""
    _state, rules = _device_rules(tmp_path)
    app_apk, test_apk = _apks(cfg)
    runner = TableRunner([_gradle_rule(cfg, app_apk, test_apk)] + rules)
    run_round(cfg, PIXEL, runner, FakeClock(), str(tmp_path / "r"), RoundOptions(journeys=["startup"]))
    install_test = runner.index_of("install", test_apk)
    warm = runner.index_of("am", "start", "-W", "com.tembraaudio/audio.tembra.android.MainActivity")
    for permission in ("android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS"):
        grant = runner.index_of("pm", "grant", "com.tembraaudio", permission)
        assert install_test < grant < warm, permission


def test_doctor_failure_refuses_before_any_build(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path, battery=BATTERY_HOT)
    app_apk, test_apk = _apks(cfg)
    runner = TableRunner([_gradle_rule(cfg, app_apk, test_apk)] + rules)
    with pytest.raises(Refused) as exc:
        run_round(cfg, PIXEL, runner, FakeClock(), str(tmp_path / "r"), RoundOptions(journeys=cfg.journeys))
    assert "battery" in str(exc.value)
    assert runner.index_of("./gradlew") == -1 and runner.index_of("install") == -1


def test_journey_failure_records_the_reason_and_keeps_artifacts(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path, instrument_ok=False)
    app_apk, test_apk = _apks(cfg)
    runner = TableRunner([_gradle_rule(cfg, app_apk, test_apk)] + rules)
    round_dir = str(tmp_path / "r")
    with pytest.raises(JourneyFailed) as exc:
        run_round(cfg, PIXEL, runner, FakeClock(), round_dir, RoundOptions(journeys=cfg.journeys))
    failed = exc.value
    assert failed.journey == "startup" and "StartupBenchmark" in failed.reason
    assert failed.outcome.status == "failed" and failed.outcome.journeys == []
    assert any(a["kind"] == "trace" for a in failed.outcome.artifacts), "salvaged artifacts are kept"
    assert runner.count("am instrument") == 2, "one retry, then the remaining classes are skipped"


def test_no_build_installs_the_given_artifacts(cfg, tmp_path):
    _state, rules = _device_rules(tmp_path)
    app = tmp_path / "elsewhere" / "app.apk"
    test = tmp_path / "elsewhere" / "bench.apk"
    app.parent.mkdir()
    app.write_bytes(b"\x00" * 10)
    test.write_bytes(b"\x00" * 20)
    runner = TableRunner(rules)
    outcome = run_round(cfg, PIXEL, runner, FakeClock(), str(tmp_path / "r"),
                        RoundOptions(journeys=["startup"], no_build=True, apk=str(app), test_apk=str(test)))
    assert runner.index_of("./gradlew") == -1
    assert runner.index_of("install", str(app)) >= 0 and runner.index_of("install", str(test)) >= 0
    assert outcome.app_size_bytes == 10 and [j.journey for j in outcome.journeys] == ["startup"]


def test_release_build_type_for_the_sc007_comparison(cfg, tmp_path):
    """The baseline is the perf build type (installable, profileable) with the instrumentation
    compiled out through `-Ptembra.perf=release`; the plain release APK is unsigned and
    Macrobenchmark refuses a non-profileable target."""
    _state, rules = _device_rules(tmp_path)
    app_apk, test_apk = _apks(cfg, "perf")
    runner = TableRunner([_gradle_rule(cfg, app_apk, test_apk)] + rules)
    outcome = run_round(cfg, PIXEL, runner, FakeClock(), str(tmp_path / "r"),
                        RoundOptions(journeys=["startup"], build_type="release"))
    assert runner.index_of("./gradlew", "-Ptembra.perf=release", ":androidApp:assemblePerf", ":perf-benchmarks:assemblePerf") >= 0
    assert runner.index_of("-Ptembra.perf=true") == -1
    assert runner.index_of("install", app_apk) >= 0
    assert outcome.build_type == "release"


def test_round_enforces_and_restores_airplane_mode_and_refresh_rate(cfg, tmp_path):
    state, rules = _device_rules(tmp_path, airplane="0", peak="Infinity")
    app_apk, test_apk = _apks(cfg)
    runner = TableRunner([_gradle_rule(cfg, app_apk, test_apk)] + rules)
    run_round(cfg, PIXEL, runner, FakeClock(), str(tmp_path / "r"), RoundOptions(journeys=["startup"]))
    enable = runner.index_of("airplane-mode", "enable")
    disable = runner.index_of("airplane-mode", "disable")
    assert 0 <= enable < runner.index_of("./gradlew") and disable > runner.index_of("adb", "-s", SERIAL, "pull")
    assert runner.index_of("settings", "put", "system", "peak_refresh_rate", "60") >= 0
    assert state["peak"] == "Infinity" and state["airplane"] == "0", "the device is left as found"
