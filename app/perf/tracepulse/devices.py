"""Device discovery and the registry in `tracepulse.yml`.

Attached devices come from `adb devices -l` (Android; emulators are listed as
simulators and never reference), `xcrun devicectl list devices --json-output`
(physical iPhones: wired and paired) and `xcrun simctl list devices booted -j`
(booted simulators). An attached device the registry has not seen is added with
`reference = (kind == "device")`; aliases resolve through the registry.
"""

import json
import os
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import yaml

from .config import Config, DeviceEntry
from .runner import Runner


class DeviceError(Exception):
    """Usage error around device selection (exit status 4)."""


@dataclass(frozen=True)
class Device:
    device_id: str
    platform: str
    kind: str
    model: str
    os_version: str = ""
    os_build: str = ""
    product_type: str = ""
    alias: str = ""
    reference: bool = True
    overrides: Dict[str, Any] = field(default_factory=dict)


_ADB_LINE = re.compile(r"^(?P<serial>\S+)\s+(?P<state>\S+)(?P<rest>.*)$")
_EMULATOR_SERIAL = re.compile(r"^(emulator-\d+|127\.0\.0\.1:\d+|localhost:\d+)$")


def parse_adb_devices(text: str) -> List[Device]:
    devices: List[Device] = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue
        m = _ADB_LINE.match(line)
        if not m or m.group("state") != "device":
            continue
        props = dict(p.split(":", 1) for p in m.group("rest").split() if ":" in p)
        serial = m.group("serial")
        model = props.get("model", serial).replace("_", " ")
        emulator = bool(_EMULATOR_SERIAL.match(serial)) or props.get("product", "").startswith("sdk_")
        devices.append(Device(device_id=serial, platform="android",
                              kind="simulator" if emulator else "device",
                              model=props.get("model", serial) if emulator else model))
    return devices


def parse_devicectl(text: str) -> List[Device]:
    if not text.strip():
        return []
    data = json.loads(text)
    devices: List[Device] = []
    for dev in data.get("result", {}).get("devices", []):
        conn = dev.get("connectionProperties", {})
        if conn.get("transportType") == "sameMachine" or conn.get("pairingState") != "paired":
            continue
        hw = dev.get("hardwareProperties", {})
        props = dev.get("deviceProperties", {})
        udid = hw.get("udid") or dev.get("identifier")
        devices.append(Device(device_id=udid, platform="ios", kind="device",
                              model=hw.get("marketingName") or props.get("name") or udid,
                              os_version=props.get("osVersionNumber", ""),
                              os_build=props.get("osBuildUpdate", ""),
                              product_type=hw.get("productType", "")))
    return devices


def parse_simctl_booted(text: str) -> List[Device]:
    if not text.strip():
        return []
    data = json.loads(text)
    devices: List[Device] = []
    for runtime, entries in data.get("devices", {}).items():
        version = runtime.rsplit(".", 1)[-1].replace("iOS-", "").replace("-", ".")
        for dev in entries:
            if dev.get("state") != "Booted":
                continue
            devices.append(Device(device_id=dev["udid"], platform="ios", kind="simulator",
                                  model=f"{dev['name']} (simulator)", os_version=version,
                                  product_type=dev.get("deviceTypeIdentifier", "").rsplit(".", 1)[-1],
                                  reference=False))
    return sorted(devices, key=lambda d: d.model)


def attached(runner: Runner, adb: str = "adb", devicectl_output: str = "") -> List[Device]:
    """Every attached device in a stable order: Android (adb order), iPhones, simulators.
    A missing tool contributes nothing."""
    devices: List[Device] = []
    res = runner.run([adb, "devices", "-l"], timeout=60)
    if res.returncode == 0:
        devices.extend(parse_adb_devices(res.stdout))
    out = devicectl_output or os.path.join(os.path.expanduser("~"), ".tracepulse-devicectl.json")
    res = runner.run(["xcrun", "devicectl", "list", "devices", "--json-output", out, "--quiet"], timeout=120)
    if res.returncode == 0 and os.path.isfile(out):
        with open(out) as fh:
            devices.extend(parse_devicectl(fh.read()))
    res = runner.run(["xcrun", "simctl", "list", "devices", "booted", "-j"], timeout=60)
    if res.returncode == 0:
        devices.extend(parse_simctl_booted(res.stdout))
    return devices


def register(cfg: Config, devices: List[Device]) -> List[str]:
    """Add every unknown device to `cfg.path`'s `devices:` map (model from the
    tool, `reference = kind == device`), keeping known entries byte-for-byte in
    meaning. Returns the ids added."""
    added: List[str] = []
    with open(cfg.path) as fh:
        raw = yaml.safe_load(fh) or {}
    registry = raw.setdefault("devices", {}) or {}
    raw["devices"] = registry
    for dev in devices:
        if dev.device_id in registry:
            continue
        registry[dev.device_id] = {"platform": dev.platform, "model": dev.model, "kind": dev.kind,
                                   "reference": dev.kind == "device"}
        cfg.devices[dev.device_id] = DeviceEntry(platform=dev.platform, model=dev.model, kind=dev.kind,
                                                 reference=dev.kind == "device")
        added.append(dev.device_id)
    if added:
        with open(cfg.path, "w") as fh:
            yaml.safe_dump(raw, fh, sort_keys=False, allow_unicode=True)
    return added


def resolve(cfg: Config, name: Optional[str], attached_devices: List[Device]) -> Device:
    """The one device a command runs on: `name` is an alias or id (must be
    attached); None picks the single attached device, two or more need `--device`."""
    if name is None:
        if not attached_devices:
            raise DeviceError("no device attached (adb, devicectl or a booted simulator)")
        if len(attached_devices) > 1:
            listing = ", ".join(f"{d.model} ({d.device_id})" for d in attached_devices)
            raise DeviceError(f"{len(attached_devices)} devices attached; name one with --device: {listing}")
        device = attached_devices[0]
    else:
        device_id = cfg.alias_to_id(name) or name
        matches = [d for d in attached_devices if d.device_id == device_id]
        if not matches:
            if device_id in cfg.devices or any(d.device_id == device_id for d in attached_devices):
                raise DeviceError(f"device {name!r} ({device_id}) is not attached")
            raise DeviceError(f"unknown device {name!r}: not in the registry and not attached")
        device = matches[0]
    entry = cfg.devices.get(device.device_id)
    if entry is None:
        return device
    return Device(device_id=device.device_id, platform=entry.platform or device.platform,
                  kind=entry.kind or device.kind, model=entry.model or device.model,
                  os_version=device.os_version, os_build=device.os_build,
                  product_type=device.product_type, alias=entry.alias, reference=entry.reference,
                  overrides=dict(entry.overrides))
