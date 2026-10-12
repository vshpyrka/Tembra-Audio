"""`tracepulse profile generate` (feature 008, contracts/cli.md): the device protocol and the API 33+
eligibility refuse before any Gradle command; the plugin's task runs from the Gradle root with
ANDROID_SERIAL naming the device; the coverage test runs after it; any failure restores the committed
profile files byte for byte (or removes them when they were absent) and exits 3; success reports the
rule counts and the covered labels; nothing reads stdin."""

import argparse
import io
import os
import shutil
import sys

import pytest

from conftest import Completed, FakeClock, TableRunner
from test_orchestrate_android import PIXEL, SERIAL, _device_rules
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.profile_generate import EXIT_FAILED, EXIT_OK, EXIT_REFUSED, main

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "profile")
GENERATE_TASK = ":androidApp:generateBaselineProfile"
COVERAGE_TASK = ":androidApp:testDebugUnitTest"
OLD_BASELINE = "Laudio/tembra/ui/home/HomeScreenKt;\nHSPLaudio/tembra/android/MainActivity;->onCreate(Landroid/os/Bundle;)V\n"
OLD_STARTUP = "Laudio/tembra/android/TembraApplication;\n"
NEW_BASELINE = ("Laudio/tembra/android/TembraApplication;\nLaudio/tembra/android/MainActivity;\n"
                "Laudio/tembra/ui/navigation/NavigationShellKt;\nLaudio/tembra/ui/home/HomeScreenKt;\n"
                "HSPLaudio/tembra/ui/tuner/TunerScreenKt;->TunerScreen(Landroidx/compose/runtime/Composer;I)V\n"
                "Laudio/tembra/ui/metronome/MetronomeScreenKt;\nLandroidx/compose/runtime/Composer;\n")
NEW_STARTUP = "Laudio/tembra/android/TembraApplication;\nLaudio/tembra/android/MainActivity;\n"
RULES = ("# startup\napp.start Laudio/tembra/android/TembraApplication;\napp.activity Laudio/tembra/android/MainActivity;\n"
         "shell Laudio/tembra/ui/navigation/NavigationShellKt;\n\nhome Laudio/tembra/ui/home/HomeScreenKt;\n"
         "tuner Laudio/tembra/ui/tuner/TunerScreenKt;\nmetronome Laudio/tembra/ui/metronome/MetronomeScreenKt;\n")


def _ok(text=""):
    return Completed(0, text)


@pytest.fixture
def cfg(tmp_path):
    root = tmp_path / "app"
    root.mkdir()
    shutil.copy(DEFAULT_CONFIG_PATH, root / "tracepulse.yml")
    (root / "androidApp").mkdir()
    (root / "androidApp" / "baseline-profile-coverage.txt").write_text(RULES)
    return load_config(str(root / "tracepulse.yml"))


@pytest.fixture
def paths(tmp_path, cfg):
    from tracepulse.__main__ import resolve_paths
    return resolve_paths(cfg, perf_dir=str(tmp_path / "perf"))


def _profile_dir(cfg):
    return os.path.join(cfg.root, "androidApp", "src", "main", "generated", "baselineProfiles")


def _write_profiles(cfg, baseline, startup):
    os.makedirs(_profile_dir(cfg), exist_ok=True)
    with open(os.path.join(_profile_dir(cfg), "baseline-prof.txt"), "w") as fh:
        fh.write(baseline)
    with open(os.path.join(_profile_dir(cfg), "startup-prof.txt"), "w") as fh:
        fh.write(startup)


def _read(cfg, name):
    path = os.path.join(_profile_dir(cfg), name)
    if not os.path.exists(path):
        return None
    with open(path) as fh:
        return fh.read()


def _args(device="pixel6"):
    return argparse.Namespace(command="profile", profile_command="generate", device=device, keep_build=False)


def _rules(tmp_path, cfg, sdk="37", generate_rc=0, coverage_rc=0, **device_kwargs):
    """The healthy-Pixel rules plus the two Gradle steps: the generation task writes the new profile files
    (as the plugin does) and succeeds or fails by `generate_rc`; the coverage test answers `coverage_rc`."""
    _state, rules = _rules_with_state(tmp_path, cfg, sdk, generate_rc, coverage_rc, **device_kwargs)
    return rules


def _rules_with_state(tmp_path, cfg, sdk="37", generate_rc=0, coverage_rc=0, **device_kwargs):
    state, rules = _device_rules(tmp_path, **device_kwargs)

    def gradle(cmd):
        if GENERATE_TASK in cmd:
            _write_profiles(cfg, NEW_BASELINE, NEW_STARTUP)
            return Completed(generate_rc, "BUILD SUCCESSFUL\n" if generate_rc == 0 else "FAILURE: journey tuner failed\n")
        if COVERAGE_TASK in cmd:
            return Completed(coverage_rc, "BUILD SUCCESSFUL\n" if coverage_rc == 0 else "BaselineProfileCoverageTest FAILED\n")
        raise AssertionError(f"unexpected gradle invocation {cmd!r}")

    return state, [
        (["adb", "-s", SERIAL, "shell", "getprop", "ro.build.version.sdk"], _ok(sdk + "\n")),
        (["xcrun"], Completed(1, "", "no xcrun in this test")),
        (["./gradlew"], gradle),
    ] + rules


def _run(cfg, paths, runner, device="pixel6"):
    stdin = sys.stdin
    sys.stdin = _NoStdin()
    try:
        return main(_args(device), cfg, paths, runner, FakeClock())
    finally:
        sys.stdin = stdin


class _NoStdin(io.TextIOBase):
    def read(self, *_):
        raise AssertionError("profile generate must not read stdin")

    readline = read


def _gradle_calls(runner):
    return [c for c in runner.calls if c["cmd"][:1] == ["./gradlew"]]


# --- refusals ------------------------------------------------------------------------------------

def test_a_failed_doctor_check_refuses_before_any_gradle_command(cfg, paths, tmp_path, capsys):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg, awake=False, locked=True))
    runner.rules.insert(0, (["adb", "-s", SERIAL, "shell", "wm", "dismiss-keyguard"], _ok()))  # a PIN: stays locked
    code, payload = _run(cfg, paths, runner)
    assert code == EXIT_REFUSED
    assert not _gradle_calls(runner)
    assert "unlocked" in capsys.readouterr().out
    assert _read(cfg, "baseline-prof.txt") == OLD_BASELINE


def test_a_device_below_api_33_is_refused_naming_the_requirement(cfg, paths, tmp_path, capsys):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    with open(os.path.join(FIXTURES, "getprop-sdk-31.txt")) as fh:
        sdk = fh.read().strip()
    runner = TableRunner(_rules(tmp_path, cfg, sdk=sdk))
    code, _payload = _run(cfg, paths, runner)
    assert code == EXIT_REFUSED
    assert not _gradle_calls(runner)
    out = capsys.readouterr().out
    assert "API 31" in out and "needs API 33+" in out


# --- the Gradle steps ----------------------------------------------------------------------------

def test_generation_runs_the_plugin_task_from_the_gradle_root_with_android_serial(cfg, paths, tmp_path):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg))
    code, _payload = _run(cfg, paths, runner)
    assert code == EXIT_OK
    generate = _gradle_calls(runner)[0]
    assert generate["cmd"] == ["./gradlew", "--console=plain", "-Ptembra.perf=true", GENERATE_TASK]
    assert generate["cwd"] == cfg.root
    assert generate["env"]["ANDROID_SERIAL"] == SERIAL


def test_the_coverage_test_runs_after_a_successful_generation(cfg, paths, tmp_path):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg))
    _run(cfg, paths, runner)
    calls = _gradle_calls(runner)
    assert len(calls) == 2
    assert calls[1]["cmd"] == ["./gradlew", "--console=plain", COVERAGE_TASK, "--tests", "*BaselineProfileCoverageTest"]
    assert calls[1]["cwd"] == cfg.root


# --- restore on failure --------------------------------------------------------------------------

def test_a_failing_generation_restores_the_committed_files_byte_for_byte(cfg, paths, tmp_path, capsys):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg, generate_rc=1))
    code, _payload = _run(cfg, paths, runner)
    assert code == EXIT_FAILED
    assert _read(cfg, "baseline-prof.txt") == OLD_BASELINE
    assert _read(cfg, "startup-prof.txt") == OLD_STARTUP
    assert len(_gradle_calls(runner)) == 1, "the coverage test does not run after a failed generation"
    out = capsys.readouterr().out
    assert "generateBaselineProfile" in out and ".log" in out


def test_a_failing_coverage_test_restores_the_committed_files(cfg, paths, tmp_path, capsys):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg, coverage_rc=1))
    code, _payload = _run(cfg, paths, runner)
    assert code == EXIT_FAILED
    assert _read(cfg, "baseline-prof.txt") == OLD_BASELINE
    assert _read(cfg, "startup-prof.txt") == OLD_STARTUP
    assert "BaselineProfileCoverageTest" in capsys.readouterr().out


def test_files_absent_before_a_failed_generation_are_removed_again(cfg, paths, tmp_path):
    runner = TableRunner(_rules(tmp_path, cfg, generate_rc=1))
    code, _payload = _run(cfg, paths, runner)
    assert code == EXIT_FAILED
    assert _read(cfg, "baseline-prof.txt") is None and _read(cfg, "startup-prof.txt") is None


# --- the summary ---------------------------------------------------------------------------------

def test_success_reports_rule_counts_and_covered_labels(cfg, paths, tmp_path, capsys):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg))
    code, payload = _run(cfg, paths, runner)
    assert code == EXIT_OK
    assert _read(cfg, "baseline-prof.txt") == NEW_BASELINE
    out = capsys.readouterr().out
    assert "baseline-prof.txt" in out and "7 rules" in out
    assert "startup-prof.txt" in out and "2 rules" in out
    for label in ("app.start", "app.activity", "shell", "home", "tuner", "metronome"):
        assert label in out
    assert "commit when covered code changed" in out
    assert payload["baseline_rules"] == 7 and payload["startup_rules"] == 2
    assert payload["covered"] == ["app.start", "app.activity", "shell", "home", "tuner", "metronome"]


def test_two_android_devices_and_no_name_is_a_usage_error(cfg, paths, tmp_path):
    from tracepulse.devices import DeviceError
    rules = _rules(tmp_path, cfg)
    rules.insert(0, (["adb", "devices", "-l"], _ok(
        f"List of devices attached\n{SERIAL}  device product:raven model:Pixel_6_Pro\n"
        "2B281FDH2003VY  device product:cheetah model:Pixel_7_Pro\n")))
    with pytest.raises(DeviceError) as exc:
        _run(cfg, paths, TableRunner(rules), device=None)
    assert "--device" in str(exc.value)



def test_generation_enforces_and_restores_airplane_mode_and_refresh_rate_like_a_round(cfg, paths, tmp_path):
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    state, rules = _rules_with_state(tmp_path, cfg, airplane="0", peak="Infinity")
    runner = TableRunner(rules)
    code, _payload = _run(cfg, paths, runner)
    assert code == EXIT_OK
    enable = runner.index_of("airplane-mode", "enable")
    disable = runner.index_of("airplane-mode", "disable")
    assert 0 <= enable < runner.index_of("./gradlew") < disable
    assert runner.index_of("settings", "put", "system", "peak_refresh_rate", "60") >= 0
    assert state["peak"] == "Infinity" and state["airplane"] == "0", "the device is left as found"


def test_the_device_test_output_directory_is_cleared_before_the_generation_task(cfg, paths, tmp_path):
    """Stale files of an earlier run must not be collected into the merged profile (a round clears it per class)."""
    _write_profiles(cfg, OLD_BASELINE, OLD_STARTUP)
    runner = TableRunner(_rules(tmp_path, cfg))
    _run(cfg, paths, runner)
    clear = runner.index_of("rm", "-rf", "/storage/emulated/0/Android/media/audio.tembra.perf/")
    assert 0 <= clear < runner.index_of("./gradlew")
