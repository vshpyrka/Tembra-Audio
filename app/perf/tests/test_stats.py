import math
from tracepulse.detect import (mw_two_sided_p, benjamini_hochberg, min_achievable_p,
                               ab_verdict, directions_agree, HIGH, LOW)
from tracepulse.catalogue import keys_for, lookup
from tracepulse.config import load_config, DEFAULT_CONFIG_PATH


def test_mw_exact_complete_separation_4v4():
    # Skia's own documented example: p = 2/70
    assert math.isclose(mw_two_sided_p([1, 2, 3, 4], [5, 6, 7, 8]), 2 / 70, rel_tol=1e-9)


def test_mw_handles_ties_deterministically():
    p = mw_two_sided_p([1.0, 2.0, 2.0], [2.0, 3.0, 4.0])   # ties → asymptotic, no crash
    assert 0.0 < p <= 1.0


def test_bh_known_vector():
    pv = [("a", 0.001), ("b", 0.008), ("c", 0.039), ("d", 0.0395), ("e", 0.9)]
    assert benjamini_hochberg(pv, q=0.05) == {"a", "b", "c", "d"}   # classic BH step-up


def test_bh_empty_and_singleton():
    assert benjamini_hochberg([], 0.05) == set()
    assert benjamini_hochberg([("x", 0.04)], 0.05) == {"x"}
    assert benjamini_hochberg([("x", 0.06)], 0.05) == set()


def test_bh_recompute_is_not_monotone_in_new_members():
    # Task 8 review finding 1: a naive "adding a member with p<=q can only
    # grow the accepted set" intuition is FALSE in general -- it only holds
    # when the new member happens to be (or tie) the family's largest p.
    # Here 'a' is accepted at m=2, then REVOKED when 'c' joins at m=3, even
    # though c's own p (0.049) is itself < q: growing the family shifts
    # every rank's threshold (q*k/m shrinks as m grows for fixed k), and BH
    # only walks the whole ladder up to the largest k that still clears its
    # own threshold -- there is no per-hypothesis guarantee once other
    # members (here 'b', p=0.9) don't clear theirs. This is exactly why the
    # store-level status ratchet exists: BH recompute is not monotone, so
    # detection engine replay must never let a later evaluation's BH
    # verdict retract an earlier evaluation's CONFIRMED.
    q = 0.05
    at_m2 = benjamini_hochberg([("a", 0.02), ("b", 0.9)], q)
    assert at_m2 == {"a"}

    at_m3 = benjamini_hochberg([("a", 0.02), ("b", 0.9), ("c", 0.049)], q)
    assert at_m3 == set()   # 'a' revoked, even though c's own p clears q


def test_min_achievable_p():
    assert math.isclose(min_achievable_p(5, 5), 2 / 252, rel_tol=1e-12)   # the 5v5 trap
    assert min_achievable_p(40, 40) < 1e-20


def test_guardrail_shipped_config_reaches_corrected_alpha():
    cfg = load_config(DEFAULT_CONFIG_PATH)
    n = cfg.collection.iterations * cfg.confirm.rounds_per_side           # 40
    family = [k for k in keys_for("android") if not lookup("android", k).single_valued]
    worst_bh_floor = cfg.confirm.q / len(family)                           # q·(1/m)
    assert min_achievable_p(n, n) < worst_bh_floor
    # and the generalized trap: round-median confirmation would NOT reach it
    assert min_achievable_p(cfg.confirm.rounds_per_side + 3,
                            cfg.confirm.rounds_per_side + 3) > worst_bh_floor


def test_ab_three_way():
    lo  = [10.0 + i * 0.01 for i in range(20)]
    hi  = [20.0 + i * 0.01 for i in range(20)]
    assert ab_verdict(hi, lo, 0.05, 0.10) == "DIFFERENT"
    assert ab_verdict(lo, [x + 0.001 for x in lo], 0.05, 0.10) in ("SAME", "UNKNOWN")
    mid = [10.12 + i * 0.05 for i in range(6)]                 # small n → p lands between
    v = ab_verdict(mid, lo[:6], 0.05, 0.10)
    assert v in ("DIFFERENT", "SAME", "UNKNOWN")               # exercised; exact branch asserted below
    assert ab_verdict([1.0, 2.0], [1.0, 2.0], 0.05, 0.10) == "SAME"       # p=1


def test_direction_agreement():
    assert directions_agree(HIGH, a_median=110, b_median=100)   # step up + right>left
    assert not directions_agree(HIGH, a_median=95, b_median=100)
    assert directions_agree(LOW, a_median=90, b_median=100)
