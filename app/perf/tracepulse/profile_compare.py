"""`tracepulse profile compare` (feature 008, specs/008-baseline-profiles/contracts/cli.md)."""

from typing import Any, Dict, Tuple

EXIT_USAGE = 4


def main(*_args, **_kwargs) -> Tuple[int, Dict[str, Any]]:
    return EXIT_USAGE, {"error": "profile compare is not implemented yet"}
