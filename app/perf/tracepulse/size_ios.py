"""iOS app size (spike 6): `xcodebuild archive` + `-exportArchive` with a
development export thinned for all variants; `App Thinning Size Report.txt` gives
the compressed (download) and uncompressed (installed) size of the configured
variant. Minutes of work, so it is opt-in (`run --size`, `tracepulse size`) and
cached per build commit under `data/size-cache/`.
"""

import json
import os
import re
from typing import Any, Dict, Optional

from .config import Config
from .runner import Runner

ARCHIVE_TIMEOUT_S = 3600


class SizeError(Exception):
    """The report lacks the variant or a tool failed."""


_VARIANT_RE = re.compile(r"^Variant:\s*(?P<file>.+?)\s*$", re.MULTILINE)
_APP_SIZE_RE = re.compile(r"^App size:\s*(?P<c>[0-9.,]+)\s*(?P<cu>[KMG]B|bytes?)\s*compressed,\s*(?P<u>[0-9.,]+)\s*(?P<uu>[KMG]B|bytes?)\s*uncompressed",
                          re.MULTILINE)
_UNITS = {"bytes": 1, "byte": 1, "KB": 1000, "MB": 1_000_000, "GB": 1_000_000_000}


def _bytes(value: str, unit: str) -> int:
    return int(round(float(value.replace(",", "")) * _UNITS[unit]))


def parse_thinning_report(text: str, variant: str) -> Dict[str, int]:
    """`app_size_bytes` (compressed) and `app_size_uncompressed_bytes` of the `Variant:` whose file name carries `variant`."""
    blocks = text.split("\nVariant:")
    for block in blocks[1:]:
        block = "Variant:" + block
        head = _VARIANT_RE.search(block)
        if head is None or variant not in head.group("file"):
            continue
        size = _APP_SIZE_RE.search(block)
        if size is None:
            continue
        return {"app_size_bytes": _bytes(size.group("c"), size.group("cu")),
                "app_size_uncompressed_bytes": _bytes(size.group("u"), size.group("uu"))}
    raise SizeError(f"variant {variant!r} not found in the App Thinning Size Report")


def export_options_plist(path: str, team_id: str) -> str:
    text = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
\t<key>method</key>
\t<string>development</string>
\t<key>thinning</key>
\t<string>&lt;thin-for-all-variants&gt;</string>
\t<key>teamID</key>
\t<string>{team_id}</string>
\t<key>signingStyle</key>
\t<string>automatic</string>
\t<key>compileBitcode</key>
\t<false/>
</dict>
</plist>
"""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(text)
    return path


def measure_size(cfg: Config, data_dir: str, runner: Runner, commit: str,
                 configuration: Optional[str] = None) -> Dict[str, Any]:
    """Archive, export thinned, parse the report; the result is cached under `data/size-cache/ios-<commit>.json`."""
    cache_dir = os.path.join(data_dir, "size-cache")
    cache = os.path.join(cache_dir, f"ios-{commit}.json")
    if os.path.isfile(cache):
        with open(cache) as fh:
            return json.load(fh)
    from .orchestrate_ios import team_id
    work = os.path.join(data_dir, "size", commit)
    os.makedirs(work, exist_ok=True)
    archive_path = os.path.join(work, "Tembra.xcarchive")
    export_path = os.path.join(work, "export")
    ios_dir = os.path.join(cfg.root, "iosApp")
    archive = runner.run(["xcodebuild", "archive", "-project", os.path.join(cfg.root, cfg.ios.project), "-scheme", cfg.ios.scheme,
                          "-configuration", configuration or cfg.ios.configuration, "-destination", "generic/platform=iOS",
                          "-archivePath", archive_path, "-allowProvisioningUpdates"], cwd=ios_dir, timeout=ARCHIVE_TIMEOUT_S)
    if archive.returncode != 0:
        raise SizeError(f"xcodebuild archive failed (rc={archive.returncode}): {archive.stderr.strip()[-500:]}")
    plist = export_options_plist(os.path.join(work, "ExportOptions.plist"), team_id(cfg))
    export = runner.run(["xcodebuild", "-exportArchive", "-archivePath", archive_path, "-exportPath", export_path,
                         "-exportOptionsPlist", plist, "-allowProvisioningUpdates"], cwd=ios_dir, timeout=ARCHIVE_TIMEOUT_S)
    if export.returncode != 0:
        raise SizeError(f"xcodebuild -exportArchive failed (rc={export.returncode}): {export.stderr.strip()[-500:]}")
    report = os.path.join(export_path, "App Thinning Size Report.txt")
    if not os.path.isfile(report):
        raise SizeError(f"{report} was not written by the export")
    with open(report) as fh:
        sizes = parse_thinning_report(fh.read(), cfg.ios.size_variant)
    result = dict(sizes, commit=commit, variant=cfg.ios.size_variant, report=report)
    os.makedirs(cache_dir, exist_ok=True)
    with open(cache, "w") as fh:
        json.dump(result, fh, indent=1)
    return result
