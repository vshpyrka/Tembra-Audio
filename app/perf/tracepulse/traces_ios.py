"""Audio System Trace (iOS, spike 3): `xctrace record --template 'Audio System
Trace' --attach <pid>` during the stress journey (never `--launch`, which hung),
then `xctrace export --xpath '/trace-toc/run[@number="1"]/data/table[@schema=
"audio-hal-client-io-cycle-ioproc"]'`: the client I/O-proc duration per cycle, the
callback duration as the HAL sees it. `audio.ioproc_p99_us` is its 99th percentile.
"""

import os
import re
from typing import List, Optional
from xml.etree import ElementTree

from .runner import Runner
from .summary import percentile

IOPROC_XPATH = '/trace-toc/run[@number="1"]/data/table[@schema="audio-hal-client-io-cycle-ioproc"]'
_DURATION_TAG = re.compile(r"duration", re.IGNORECASE)


def parse_ioproc_durations(xml: str) -> List[float]:
    """Every row's `duration` column in microseconds. Rows may reference an earlier
    cell by `ref`; durations are never shared, so each row carries its own value."""
    root = ElementTree.fromstring(xml)
    schema = root.find(".//schema")
    columns = [col.findtext("mnemonic") for col in schema.findall("col")] if schema is not None else []
    try:
        index = columns.index("duration")
    except ValueError:
        index = None
    durations: List[float] = []
    for row in root.iter("row"):
        cells = list(row)
        cell = None
        if index is not None and index < len(cells) and _DURATION_TAG.search(cells[index].tag):
            cell = cells[index]
        else:
            cell = next((c for c in cells if _DURATION_TAG.search(c.tag)), None)
        if cell is None or cell.text is None:
            continue
        try:
            durations.append(float(cell.text.strip()) / 1000.0)
        except ValueError:
            continue
    return durations


def ioproc_p99(xml: str) -> Optional[float]:
    durations = parse_ioproc_durations(xml)
    return percentile(durations, 99) if durations else None


def export_ioproc(runner: Runner, trace_path: str, out_xml: str) -> Optional[str]:
    res = runner.run(["xcrun", "xctrace", "export", "--input", trace_path, "--xpath", IOPROC_XPATH], timeout=600)
    if res.returncode != 0 or not res.stdout.strip():
        return None
    with open(out_xml, "w") as fh:
        fh.write(res.stdout)
    return out_xml


def ioproc_p99_from_trace(runner: Runner, trace_path: str, round_dir: str) -> Optional[float]:
    xml_path = export_ioproc(runner, trace_path, os.path.join(round_dir, "audio-ioproc.xml"))
    if xml_path is None:
        return None
    with open(xml_path) as fh:
        return ioproc_p99(fh.read())
