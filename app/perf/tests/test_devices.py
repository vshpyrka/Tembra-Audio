"""Device discovery and the registry in tracepulse.yml: attached devices from
`adb devices -l`, `xcrun devicectl list devices --json-output` and
`xcrun simctl list devices booted -j`; unknown devices join the registry with
`reference = (kind == device)`; aliases resolve; two attached devices and no
`--device` is a usage error."""

import os

import pytest
import yaml

from conftest import Completed, FakeRunner
from tracepulse.config import DEFAULT_CONFIG_PATH, load_config
from tracepulse.devices import (
    Device, DeviceError, attached, parse_adb_devices, parse_devicectl, parse_simctl_booted,
    register, resolve,
)

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")


def _read(name):
    with open(os.path.join(FIXTURES, name)) as fh:
        return fh.read()


def test_parse_adb_devices_keeps_authorized_devices_and_marks_emulators():
    devices = parse_adb_devices(_read("adb-devices.txt"))
    assert [d.device_id for d in devices] == ["19021FDEE001AH", "2B091FDH3004QL", "127.0.0.1:5556"]
    pixel = devices[0]
    assert pixel == Device(device_id="19021FDEE001AH", platform="android", kind="device", model="Pixel 6 Pro")
    assert devices[1].model == "Pixel 7 Pro"
    emulator = devices[2]
    assert emulator.kind == "simulator" and emulator.model == "sdk_gphone64_arm64"


def test_parse_devicectl_keeps_wired_paired_physical_devices_only():
    devices = parse_devicectl(_read("devicectl-list.json"))
    assert devices == [Device(device_id="00008110-001049660E3A401E", platform="ios", kind="device",
                              model="iPhone 14", os_version="27.0.1", os_build="24A446",
                              product_type="iPhone14,7")]


def test_parse_simctl_booted_yields_simulators():
    devices = parse_simctl_booted(_read("simctl-booted.json"))
    ids = {d.device_id: d for d in devices}
    assert set(ids) == {"EB59DDD2-6D13-4503-A77E-BDFB69BF8CCD", "4CD6879C-8FBA-4CEF-B4EC-31968D0144B0",
                        "C9A67FF1-62FC-4BE6-89F9-685DC39FBD46"}
    iphone17 = ids["4CD6879C-8FBA-4CEF-B4EC-31968D0144B0"]
    assert iphone17.platform == "ios" and iphone17.kind == "simulator"
    assert iphone17.model == "iPhone 17 (simulator)" and iphone17.os_version == "26.5"
    assert [d.model for d in devices] == sorted(d.model for d in devices), "sorted by model for stable output"


def test_attached_runs_the_three_tools_and_merges(tmp_path):
    runner = FakeRunner([
        (["adb", "devices", "-l"], Completed(0, _read("adb-devices.txt"))),
        (lambda cmd: cmd[:4] == ["xcrun", "devicectl", "list", "devices"], Completed(0, "")),
        (["xcrun", "simctl", "list", "devices", "booted", "-j"], Completed(0, _read("simctl-booted.json"))),
    ])
    out = str(tmp_path / "devicectl.json")
    with open(out, "w") as fh:  # what the real `--json-output` call writes
        fh.write(_read("devicectl-list.json"))
    devices = attached(runner, adb="adb", devicectl_output=out)
    ids = [d.device_id for d in devices]
    assert ids[:3] == ["19021FDEE001AH", "2B091FDH3004QL", "127.0.0.1:5556"]
    assert "00008110-001049660E3A401E" in ids and "4CD6879C-8FBA-4CEF-B4EC-31968D0144B0" in ids
    runner.assert_exhausted()


def test_attached_tolerates_a_missing_tool():
    runner = FakeRunner([
        (["adb", "devices", "-l"], Completed(0, _read("adb-devices.txt"))),
        (lambda cmd: cmd[:2] == ["xcrun", "devicectl"], Completed(69, "", "xcrun: error: unable to find utility")),
        (["xcrun", "simctl", "list", "devices", "booted", "-j"], Completed(69, "", "no simctl")),
    ])
    devices = attached(runner, adb="adb", devicectl_output="/nonexistent/devicectl.json")
    assert [d.platform for d in devices] == ["android", "android", "android"]


def test_register_adds_unknown_devices_with_reference_from_kind(tmp_path):
    cfg = load_config(DEFAULT_CONFIG_PATH)
    path = str(tmp_path / "tracepulse.yml")
    with open(DEFAULT_CONFIG_PATH) as src, open(path, "w") as dst:
        dst.write(src.read())
    cfg = load_config(path)
    new = [Device(device_id="2B091FDH3004QL", platform="android", kind="device", model="Pixel 7 Pro"),
           Device(device_id="4CD6879C-8FBA-4CEF-B4EC-31968D0144B0", platform="ios", kind="simulator",
                  model="iPhone 17 (simulator)"),
           Device(device_id="19021FDEE001AH", platform="android", kind="device", model="Pixel 6 Pro")]
    added = register(cfg, new)
    assert added == ["2B091FDH3004QL", "4CD6879C-8FBA-4CEF-B4EC-31968D0144B0"]
    with open(path) as fh:
        raw = yaml.safe_load(fh)
    assert raw["devices"]["2B091FDH3004QL"] == {"platform": "android", "model": "Pixel 7 Pro", "kind": "device",
                                                "reference": True}
    sim = raw["devices"]["4CD6879C-8FBA-4CEF-B4EC-31968D0144B0"]
    assert sim["kind"] == "simulator" and sim["reference"] is False
    # the known Pixel entry keeps its alias and overrides untouched
    assert raw["devices"]["19021FDEE001AH"]["alias"] == "pixel6"
    assert raw["devices"]["19021FDEE001AH"]["overrides"] == {"battery_temp_max_c": 40.0}
    reloaded = load_config(path)
    assert reloaded.devices["2B091FDH3004QL"].reference is True
    assert reloaded.devices["4CD6879C-8FBA-4CEF-B4EC-31968D0144B0"].reference is False
    assert register(reloaded, new) == []


def test_resolve_alias_id_and_single_attached():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    pixel = Device(device_id="19021FDEE001AH", platform="android", kind="device", model="Pixel 6 Pro")
    iphone = Device(device_id="00008110-001049660E3A401E", platform="ios", kind="device", model="iPhone 14")
    assert resolve(cfg, "pixel6", [pixel, iphone]).device_id == "19021FDEE001AH"
    assert resolve(cfg, "00008110-001049660E3A401E", [pixel, iphone]).device_id == "00008110-001049660E3A401E"
    assert resolve(cfg, None, [pixel]).device_id == "19021FDEE001AH"
    with pytest.raises(DeviceError, match="--device"):
        resolve(cfg, None, [pixel, iphone])
    with pytest.raises(DeviceError, match="no device attached"):
        resolve(cfg, None, [])
    with pytest.raises(DeviceError, match="not attached"):
        resolve(cfg, "pixel6", [iphone])
    with pytest.raises(DeviceError, match="unknown device"):
        resolve(cfg, "nope", [pixel, iphone])


def test_resolve_merges_registry_overrides_and_alias():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    pixel = Device(device_id="19021FDEE001AH", platform="android", kind="device", model="Pixel_6_Pro")
    resolved = resolve(cfg, "pixel6", [pixel])
    assert resolved.model == "Pixel 6 Pro", "the registry's model wins over the tool's spelling"
    assert resolved.alias == "pixel6" and resolved.reference is True
    assert resolved.overrides == {"battery_temp_max_c": 40.0}
