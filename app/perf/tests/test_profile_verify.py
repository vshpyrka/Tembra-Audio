"""`tracepulse profile verify` (feature 008, contracts/cli.md, D5): the APK must carry
assets/dexopt/baseline.prof (its share of the APK reported with one decimal); the steps run in the
documented order; the installer's broadcast result 1 or 2 passes and 6 fails; `dumpsys package dexopt`
must show speed-profile; only the awake/unlocked/adb checks run; a release APK without a signing
config is inspected and not installed."""

import argparse
import os
import shutil

import pytest

from conftest import Completed, FakeClock, TableRunner
from test_orchestrate_android import SERIAL, _device_rules
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.profile_verify import EXIT_FAILED, EXIT_OK, main

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "profile")
PACKAGE = "audio.tembra.app"
RECEIVER = f"{PACKAGE}/androidx.profileinstaller.ProfileInstallReceiver"


def _fixture(name):
    with open(os.path.join(FIXTURES, name)) as fh:
        return fh.read()


def _ok(text=""):
    return Completed(0, text)


@pytest.fixture
def cfg(tmp_path):
    root = tmp_path / "app"
    root.mkdir()
    shutil.copy(DEFAULT_CONFIG_PATH, root / "tracepulse.yml")
    return load_config(str(root / "tracepulse.yml"))


@pytest.fixture
def paths(tmp_path, cfg):
    from tracepulse.__main__ import resolve_paths
    return resolve_paths(cfg, perf_dir=str(tmp_path / "perf"))


def _apk(tmp_path, size=8_000_000):
    path = tmp_path / "androidApp-perf.apk"
    path.write_bytes(b"\x00" * size)
    return str(path)


def _args(apk=None, build_type="perf"):
    return argparse.Namespace(command="profile", profile_command="verify", device="pixel6", apk=apk,
                              build_type=build_type)


def _rules(tmp_path, cfg, listing="unzip-l-with-prof.txt", broadcast="broadcast-result-1.txt",
           dexopt="dumpsys-dexopt-speed-profile.txt", release_signed=False):
    _state, rules = _device_rules(tmp_path)

    def gradle(cmd):
        build_type = "perf" if ":androidApp:assemblePerf" in cmd else "release"
        out = os.path.join(cfg.root, "androidApp", "build", "outputs", "apk", build_type)
        os.makedirs(out, exist_ok=True)
        name = f"androidApp-{build_type}.apk" if (build_type == "perf" or release_signed) else "androidApp-release-unsigned.apk"
        with open(os.path.join(out, name), "wb") as fh:
            fh.write(b"\x00" * 8_000_000)
        return _ok("BUILD SUCCESSFUL\n")

    return [
        (["xcrun"], Completed(1, "", "no xcrun in this test")),
        (["./gradlew"], gradle),
        (["unzip", "-l"], _ok(_fixture(listing))),
        (["adb", "-s", SERIAL, "shell", "am", "broadcast"], _ok(_fixture(broadcast))),
        (["adb", "-s", SERIAL, "shell", "cmd", "package", "compile"], _ok("Success\n")),
        (["adb", "-s", SERIAL, "shell", "dumpsys", "package", "dexopt"], _ok(_fixture(dexopt))),
    ] + rules


def _run(cfg, paths, runner, args=None, clock=None):
    return main(args or _args(), cfg, paths, runner, clock or FakeClock())


# --- the APK -------------------------------------------------------------------------------------

def test_an_apk_without_the_profile_asset_fails_the_present_step(cfg, paths, tmp_path, capsys):
    runner = TableRunner(_rules(tmp_path, cfg, listing="unzip-l-without-prof.txt"))
    code, _payload = _run(cfg, paths, runner, _args(apk=_apk(tmp_path)))
    assert code == EXIT_FAILED
    assert "assets/dexopt/baseline.prof" in capsys.readouterr().out
    assert runner.index_of("adb", "-s", SERIAL, "install") == -1, "nothing is installed without a profile"


def test_the_profile_share_is_computed_against_the_apk_size_with_one_decimal(cfg, paths, tmp_path, capsys):
    runner = TableRunner(_rules(tmp_path, cfg))
    code, payload = _run(cfg, paths, runner, _args(apk=_apk(tmp_path, size=8_000_000)))
    assert code == EXIT_OK
    assert payload["profile_bytes"] == 183512 and payload["apk_bytes"] == 8_000_000
    assert payload["profile_share_pct"] == 2.3
    assert "profile present (183512 bytes, 2.3 %)" in capsys.readouterr().out


# --- the device steps ----------------------------------------------------------------------------

def test_steps_run_in_the_documented_order(cfg, paths, tmp_path):
    runner = TableRunner(_rules(tmp_path, cfg))
    clock = FakeClock()
    code, _payload = _run(cfg, paths, runner, _args(apk=_apk(tmp_path)), clock)
    assert code == EXIT_OK
    order = [runner.index_of("adb", "-s", SERIAL, "install"),
             runner.index_of("am", "start", "-n", f"{PACKAGE}/audio.tembra.android.MainActivity"),
             runner.index_of("am", "broadcast", "-a", "androidx.profileinstaller.action.INSTALL_PROFILE", "-n", RECEIVER),
             runner.index_of("cmd", "package", "compile", "-r", "bg-dexopt", PACKAGE),
             runner.index_of("dumpsys", "package", "dexopt"),
             runner.index_of("am", "force-stop", PACKAGE)]
    assert all(i >= 0 for i in order), order
    assert order == sorted(order)
    assert 5 in clock.sleeps, "the installer writes a few seconds after the first frame"


def test_broadcast_result_2_passes_as_already_installed(cfg, paths, tmp_path, capsys):
    runner = TableRunner(_rules(tmp_path, cfg, broadcast="broadcast-result-1.txt"))
    runner.rules.insert(0, (["adb", "-s", SERIAL, "shell", "am", "broadcast"], _ok("Broadcast completed: result=2\n")))
    code, payload = _run(cfg, paths, runner, _args(apk=_apk(tmp_path)))
    assert code == EXIT_OK and payload["broadcast_result"] == 2
    assert "recognised (result 2)" in capsys.readouterr().out


def test_broadcast_result_6_fails_naming_the_missing_profile(cfg, paths, tmp_path, capsys):
    runner = TableRunner(_rules(tmp_path, cfg, broadcast="broadcast-result-6.txt"))
    code, _payload = _run(cfg, paths, runner, _args(apk=_apk(tmp_path)))
    assert code == EXIT_FAILED
    assert "no profile in the APK" in capsys.readouterr().out
    assert runner.index_of("am", "force-stop", PACKAGE) >= 0, "the app is stopped on every exit path"


def test_dexopt_status_other_than_speed_profile_fails_naming_it(cfg, paths, tmp_path, capsys):
    runner = TableRunner(_rules(tmp_path, cfg, dexopt="dumpsys-dexopt-verify.txt"))
    code, _payload = _run(cfg, paths, runner, _args(apk=_apk(tmp_path)))
    assert code == EXIT_FAILED
    out = capsys.readouterr().out
    assert "status=verify" in out or "optimised (verify)" in out


def test_only_the_awake_unlocked_and_adb_checks_run(cfg, paths, tmp_path):
    runner = TableRunner(_rules(tmp_path, cfg))
    _run(cfg, paths, runner, _args(apk=_apk(tmp_path)))
    assert runner.index_of("adb", "devices", "-l") >= 0
    assert runner.index_of("dumpsys", "power") >= 0 and runner.index_of("dumpsys", "window", "policy") >= 0
    assert runner.index_of("dumpsys", "battery") == -1, "this is not a measurement: no thermal or battery check"
    assert runner.index_of("airplane-mode") == -1


# --- building the APK ----------------------------------------------------------------------------

def test_without_apk_the_perf_build_is_produced_through_the_pipeline_gradle_step(cfg, paths, tmp_path):
    runner = TableRunner(_rules(tmp_path, cfg))
    code, payload = _run(cfg, paths, runner)
    assert code == EXIT_OK
    gradle = next(c for c in runner.calls if c["cmd"][:1] == ["./gradlew"])
    assert gradle["cmd"] == ["./gradlew", "--console=plain", "-Ptembra.perf=true", ":androidApp:assemblePerf"]
    assert gradle["cwd"] == cfg.root
    assert payload["apk"].endswith(os.path.join("apk", "perf", "androidApp-perf.apk"))


def test_an_unsigned_release_apk_is_inspected_only(cfg, paths, tmp_path, capsys):
    runner = TableRunner(_rules(tmp_path, cfg))
    code, payload = _run(cfg, paths, runner, _args(build_type="release"))
    assert code == EXIT_OK
    gradle = next(c for c in runner.calls if c["cmd"][:1] == ["./gradlew"])
    assert gradle["cmd"] == ["./gradlew", "--console=plain", ":androidApp:assembleRelease"]
    assert payload["apk"].endswith("androidApp-release-unsigned.apk")
    out = capsys.readouterr().out
    assert "profile present" in out and "unsigned release APK cannot be installed" in out
    for step in (("adb", "-s", SERIAL, "install"), ("am", "start"), ("am", "broadcast")):
        assert runner.index_of(*step) == -1, step
