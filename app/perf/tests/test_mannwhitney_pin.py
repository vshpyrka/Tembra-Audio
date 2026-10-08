"""Pin the exact two-sided Mann-Whitney p-values the detector relies on.

trace-pulse validated its confirmation step against scipy 1.13.1; the pin in
pyproject.toml is relaxed to `>=1.13.1,<2` so the 2015 Mac and newer Pythons can
install. This test fixes the three p-values that step produces on a 40 v 40
fixture (two rounds of 20 iterations per side, the shipped
`collection.iterations * confirm.rounds_per_side`) to the values scipy 1.13.1
computed, so a newer scipy that changes `mannwhitneyu` cannot drift silently.
"""

import json
import math
import os

from tracepulse.detect import mw_two_sided_p

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "mwu-40v40.json")


def _fixture():
    with open(FIXTURE) as fh:
        return json.load(fh)


def test_fixture_shape():
    f = _fixture()
    for case in f["cases"]:
        assert len(case["a"]) == 40 and len(case["b"]) == 40, case["name"]


def test_exact_method_without_ties_matches_scipy_1_13_1():
    case = next(c for c in _fixture()["cases"] if c["name"] == "separated-no-ties")
    assert len(set(case["a"] + case["b"])) == 80, "fixture must have no ties for the exact path"
    assert math.isclose(mw_two_sided_p(case["a"], case["b"]), case["p"], rel_tol=1e-12)


def test_exact_method_overlapping_matches_scipy_1_13_1():
    case = next(c for c in _fixture()["cases"] if c["name"] == "overlapping-no-ties")
    assert math.isclose(mw_two_sided_p(case["a"], case["b"]), case["p"], rel_tol=1e-12)


def test_asymptotic_method_with_ties_matches_scipy_1_13_1():
    case = next(c for c in _fixture()["cases"] if c["name"] == "overlapping-with-ties")
    assert len(set(case["a"] + case["b"])) < 80, "fixture must carry ties for the asymptotic path"
    assert math.isclose(mw_two_sided_p(case["a"], case["b"]), case["p"], rel_tol=1e-12)
