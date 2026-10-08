"""`summary.percentile` is the project-wide convention; `summarize` is the record's block."""

import math

from tracepulse.summary import percentile, summarize


# --- percentile: public project-wide convention --------------------------------

def test_percentile_hand_computed_vector():
    # rank = (5-1)*99/100 = 3.96 -> s[3]=9, s[4]=30 -> 9 + 0.96*21 = 29.16
    assert math.isclose(percentile([6, 7, 8, 9, 30], 99), 29.16, rel_tol=1e-9)


def test_percentile_edges():
    assert percentile([1.0, 2.0, 3.0, 4.0, 5.0], 0) == 1.0
    assert percentile([1.0, 2.0, 3.0, 4.0, 5.0], 100) == 5.0
    assert percentile([42.0], 0) == 42.0
    assert percentile([42.0], 50) == 42.0
    assert percentile([42.0], 100) == 42.0


def test_percentile_matches_store_private_copy_convention():
    # Same convention as store.py's private _percentile (task-5-report.md);
    # this is the project's public copy per the task-6 brief.
    assert math.isclose(percentile([10.0, 20.0, 30.0, 40.0, 50.0], 90), 46.0, rel_tol=1e-9)


def test_summarize_fields():
    s = summarize([1.0, 2.0, 3.0, 4.0])
    assert s["median"] == 2.5 and s["min"] == 1.0 and s["max"] == 4.0 and s["mean"] == 2.5
    assert math.isclose(s["p90"], 3.7) and math.isclose(s["p99"], 3.97)
    assert math.isclose(s["cov"], math.sqrt(1.25) / 2.5)


def test_summarize_single_value_has_zero_cov():
    s = summarize([5.0])
    assert s["median"] == 5.0 and s["cov"] == 0.0


def test_summarize_zero_mean_cov_is_zero():
    assert summarize([0.0, 0.0])["cov"] == 0.0
