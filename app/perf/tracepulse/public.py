"""The public build of the report: no build-machine host names, no local paths.

The page is published on GitHub Pages, so `publicise` drops each round's
`machine`, empties `artifacts`, replaces any machine value (and its first DNS label) quoted in a string
with "build machine" and shortens home-directory paths to `~/`."""

import copy
import re
from typing import Any, Dict, List, Set

_HOME_PATH = re.compile(r"/(?:Users|home)/[^/\s\"']+(/|(?=[\s\"']|$))")
_MACHINE_TEXT = "build machine"


def _machines(data: Dict[str, Any]) -> Set[str]:
    return {r["machine"] for d in data.get("devices", []) for r in d.get("rounds", []) if r.get("machine")}


def host_terms(data: Dict[str, Any]) -> List[str]:
    """Every machine value in the data and its first DNS label, longest first (`x.local` and `x` both)."""
    terms: Set[str] = set()
    for machine in _machines(data):
        terms.add(machine)
        label = machine.split(".")[0]
        if label:
            terms.add(label)
    return sorted(terms, key=len, reverse=True)


def leaks(text: str, terms: List[str]) -> List[str]:
    """What a page still carries that must not be public: names the kinds of leak, never the surrounding text."""
    found = []
    lowered = text.lower()
    for marker in ("/Users/", "/home/"):
        if marker.lower() in lowered:
            found.append(f"home path {marker}")
    for term in terms:
        if term.lower() in lowered:
            found.append(f"host name {term!r}")
    return found


def publicise(data: Dict[str, Any]) -> Dict[str, Any]:
    """A cleaned copy of `report_data`'s output; the input is unchanged."""
    machines = [re.compile(re.escape(t), re.IGNORECASE) for t in host_terms(data)]

    def clean(value: Any) -> Any:
        if isinstance(value, str):
            for machine in machines:
                value = machine.sub(_MACHINE_TEXT, value)
            return _HOME_PATH.sub(lambda m: "~/" if m.group(1) else "~", value)
        if isinstance(value, list):
            return [clean(v) for v in value]
        if isinstance(value, dict):
            return {k: ([] if k == "artifacts" else clean(v)) for k, v in value.items() if k != "machine"}
        return value

    return clean(copy.deepcopy(data))
