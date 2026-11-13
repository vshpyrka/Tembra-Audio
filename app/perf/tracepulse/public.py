"""The public build of the report: no build-machine host names, no local paths.

The page is published on GitHub Pages, so `publicise` drops each round's
`machine`, empties `artifacts`, replaces any machine value quoted in a string
with "build machine" and shortens home-directory paths to `~/`."""

import copy
import re
from typing import Any, Dict, Set

_HOME_PATH = re.compile(r"/(?:Users|home)/[^/\s\"']+(/|(?=[\s\"']|$))")
_MACHINE_TEXT = "build machine"


def _machines(data: Dict[str, Any]) -> Set[str]:
    return {r["machine"] for d in data.get("devices", []) for r in d.get("rounds", []) if r.get("machine")}


def publicise(data: Dict[str, Any]) -> Dict[str, Any]:
    """A cleaned copy of `report_data`'s output; the input is unchanged."""
    machines = sorted(_machines(data), key=len, reverse=True)

    def clean(value: Any) -> Any:
        if isinstance(value, str):
            for machine in machines:
                value = value.replace(machine, _MACHINE_TEXT)
            return _HOME_PATH.sub(lambda m: "~/" if m.group(1) else "~", value)
        if isinstance(value, list):
            return [clean(v) for v in value]
        if isinstance(value, dict):
            return {k: ([] if k == "artifacts" else clean(v)) for k, v in value.items() if k != "machine"}
        return value

    return clean(copy.deepcopy(data))
