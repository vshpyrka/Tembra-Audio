"""Audio HAL trace (iOS, spike 3): `xctrace record --instrument 'Audio Client'
--instrument 'Audio Server' --all-processes` during the stress journey (never
`--launch`, which hung; never `--attach`, see `orchestrate_ios._audio_trace`), then
`xctrace export --xpath '/trace-toc/run[@number="1"]/data/table[@schema=…]'` of the
client I/O-proc duration per cycle — the callback duration as the HAL sees it.
`audio.ioproc_p99_us` is its 99th percentile.

Two tables carry that interval: `audio-hal-client-io-cycle-ioproc`, written inside
the client process, and `audio-hal-io-cycle-client`, the HAL server's (audiomxd's)
view of the same interval. On iOS 27.0.1 the client table stays empty whether or
not xctrace is attached to the app, so the server table is what feeds the series;
its client-process column reads "Unknown" there, and the pass trusts that the app is
the phone's only HAL client (the phone is otherwise idle during a round).
"""

import os
import re
from typing import Dict, List, Optional
from xml.etree import ElementTree

from .runner import Runner
from .summary import percentile

IOPROC_XPATH = '/trace-toc/run[@number="1"]/data/table[@schema="audio-hal-client-io-cycle-ioproc"]'
IO_CYCLE_CLIENT_XPATH = '/trace-toc/run[@number="1"]/data/table[@schema="audio-hal-io-cycle-client"]'
# in reading order: the client's own rows when the trace has them, else the server's view of the same interval
IOPROC_TABLES = ((IOPROC_XPATH, "audio-ioproc.xml"), (IO_CYCLE_CLIENT_XPATH, "audio-io-cycle-client.xml"))
_DURATION_TAG = re.compile(r"duration", re.IGNORECASE)


def parse_ioproc_durations(xml: str) -> List[float]:
    """Every row's `duration` column in microseconds. A cell may reference an earlier
    cell by `ref` (xctrace writes an equal value once and points back to it: 1 315 of
    7 490 server rows on 2026-10-12), so references are followed; a row without a
    readable duration is skipped."""
    root = ElementTree.fromstring(xml)
    schema = root.find(".//schema")
    columns = [col.findtext("mnemonic") for col in schema.findall("col")] if schema is not None else []
    try:
        index = columns.index("duration")
    except ValueError:
        index = None
    by_id: Dict[str, ElementTree.Element] = {el.get("id"): el for el in root.iter() if el.get("id")}
    durations: List[float] = []
    for row in root.iter("row"):
        cells = list(row)
        cell = None
        if index is not None and index < len(cells) and _DURATION_TAG.search(cells[index].tag):
            cell = cells[index]
        else:
            cell = next((c for c in cells if _DURATION_TAG.search(c.tag)), None)
        if cell is not None and cell.get("ref"):
            cell = by_id.get(cell.get("ref"))
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


def export_table(runner: Runner, trace_path: str, xpath: str, out_xml: str) -> Optional[str]:
    res = runner.run(["xcrun", "xctrace", "export", "--input", trace_path, "--xpath", xpath], timeout=600)
    if res.returncode != 0 or not res.stdout.strip():
        return None
    with open(out_xml, "w") as fh:
        fh.write(res.stdout)
    return out_xml


def export_ioproc(runner: Runner, trace_path: str, out_xml: str) -> Optional[str]:
    return export_table(runner, trace_path, IOPROC_XPATH, out_xml)


def ioproc_p99_from_trace(runner: Runner, trace_path: str, round_dir: str) -> Optional[float]:
    """The p99 of the first table in `IOPROC_TABLES` that has rows; every exported table
    stays under the round (an empty `audio-ioproc.xml` beside a full `audio-io-cycle-client.xml`
    says which view fed the series)."""
    for xpath, name in IOPROC_TABLES:
        xml_path = export_table(runner, trace_path, xpath, os.path.join(round_dir, name))
        if xml_path is None:
            continue
        with open(xml_path) as fh:
            p99 = ioproc_p99(fh.read())
        if p99 is not None:
            return p99
    return None
