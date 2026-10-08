"""Detection engine tests (Task 8): run_detection, analyze, and the seed()
synthetic-store helper. Test numbering follows task-8-brief.md's 7 normative
tests verbatim (test 1..7); three supplementary tests cover binding
semantics (expected_lookup/ground_truth_match wiring, BH-family LOW ->
IMPROVED) the brief's resolutions require but none of the 7 pinned tests
exercise directly.
"""

import random
from typing import Dict, List

import pytest

from tracepulse.config import load_config, DEFAULT_CONFIG_PATH
from tracepulse.detect import run_detection, analyze, Event, DetectionReport, AbResult
from tracepulse.store import Store


# --- fixtures ---------------------------------------------------------

@pytest.fixture
def db_path(tmp_path):
    return str(tmp_path / "tracepulse.db")


@pytest.fixture
def store(db_path):
    with Store(db_path) as s:
        yield s


@pytest.fixture
def cfg():
    return load_config(DEFAULT_CONFIG_PATH)


# --- seed helper --------------------------------------------------------

def seed(store: Store, metric_values: Dict[str, List[List[float]]]) -> List[int]:
    """Seed a Store with synthetic rounds from per-metric per-round iteration
    lists.

    `metric_values`: metric -> list of rounds, each round a list of raw
    iteration values (APK-style n=1 series just use single-element round
    lists). Every metric's list must be the same length (n_rounds); one
    round is added per index, in lockstep across every metric passed, with
    sequential timestamps. `benchmark` is derived from the metric's prefix
    (`"startup.ttid_ms"` -> `"startup"`). Returns the round_ids created, in
    insertion order (== [1..n_rounds] for a fresh store).
    """
    n_rounds = len(next(iter(metric_values.values())))
    for metric, rounds in metric_values.items():
        assert len(rounds) == n_rounds, (
            f"{metric} has {len(rounds)} rounds, expected {n_rounds} (seed() "
            "requires every metric in one call to share the same round count)"
        )

    round_ids = []
    for i in range(n_rounds):
        r = store.add_round(ts=1000 + i, label=f"r{i + 1}", target_sha="sha",
                             target_dirty=False, injected=[], device_serial="SERIAL",
                             device_fingerprint="FP", quality_flags=[],
                             thermal_sleep_s=0.0, notes="")
        round_ids.append(r)
        for metric, rounds_for_metric in metric_values.items():
            values = rounds_for_metric[i]
            benchmark = metric.split(".", 1)[0]
            store.add_samples(r, benchmark, metric, values)
            store.add_aggregate(r, metric, values)
    return round_ids


# --- test 1: clean step confirmed at onset+5 -----------------------------

def test_clean_step_confirmed_at_onset_plus_5(store, cfg):
    rng = random.Random(42)
    baseline = [[rng.gauss(100, 0.5) for _ in range(20)] for _ in range(12)]
    rng2 = random.Random(43)
    stepped = [[rng2.gauss(115, 0.5) for _ in range(20)] for _ in range(6)]
    seed(store, {"startup.ttid_ms": baseline + stepped})

    # A/B mode catches the transition provisionally, long before the
    # step-fit window is scorable: round 13 (first stepped round, and the
    # newest round at upto_round=13) vs its 2 pooled predecessors (11, 12 -
    # still baseline) is a massive jump.
    report13 = run_detection(store, cfg, upto_round=13)
    assert isinstance(report13, DetectionReport)
    prelim = next(e for e in report13.new_events
                  if e.onset_round == 13 and e.status == "PRELIMINARY")
    assert isinstance(prelim, Event)
    assert prelim.metric == "startup.ttid_ms"
    assert prelim.direction == "up"
    assert store.latest_status("startup.ttid_ms", 13) == "PRELIMINARY"

    # width=5 needs 5 successor rounds beyond onset -> round 13 isn't
    # scorable as a step-fit candidate until round 18 exists; at round 17
    # (onset+4) nothing new fires -- newest-round A/B settles to UNKNOWN
    # (round 17 vs its already-stepped predecessors 15,16), so it only
    # shows up as a provisional AbResult, never stored.
    report17 = run_detection(store, cfg, upto_round=17)
    assert report17.new_events == []
    assert store.latest_status("startup.ttid_ms", 13) == "PRELIMINARY"
    assert len(report17.provisional) == 1
    ab = report17.provisional[0]
    assert isinstance(ab, AbResult)
    assert ab.metric == "startup.ttid_ms"
    assert ab.verdict in ("SAME", "UNKNOWN")

    # onset+5 = 18: SUSPECT and CONFIRMED both land in the very same evaluation.
    report18 = run_detection(store, cfg, upto_round=18)
    onset_events = [e for e in report18.new_events if e.onset_round == 13]
    assert {e.status for e in onset_events} == {"SUSPECT", "CONFIRMED"}
    confirmed = next(e for e in onset_events if e.status == "CONFIRMED")
    assert confirmed.metric == "startup.ttid_ms"
    assert confirmed.direction == "up"
    assert confirmed.mwu_p < 1e-10
    assert store.latest_status("startup.ttid_ms", 13) == "CONFIRMED"


# --- test 2: screening gate blocks a small step --------------------------

def test_screening_gate_blocks_small_step(store, cfg):
    # +1.5% step on startup.ttid_ms (gate 3%), tiny variance so step-fit
    # itself fires easily (|R| >> 15) -- only the practical-significance
    # gate should be what blocks this one. This also exercises the A/B
    # path's own gate (task review finding 2): round 13 -- the onset round
    # itself, the first time it's "newest" -- is the very same 1.5% move
    # relative to its baseline predecessors, and must be gated too, or A/B
    # would store a PRELIMINARY for a step step-fit itself would reject.
    rng = random.Random(101)
    baseline = [[1000 + rng.gauss(0, 0.05) for _ in range(20)] for _ in range(12)]
    rng2 = random.Random(102)
    stepped = [[1015 + rng2.gauss(0, 0.05) for _ in range(20)] for _ in range(6)]
    seed(store, {"startup.ttid_ms": baseline + stepped})

    report13 = run_detection(store, cfg, upto_round=13)
    assert report13.new_events == []
    assert len(store.all_detections()) == 0
    assert store.latest_status("startup.ttid_ms", 13) is None
    # the gate-failing DIFFERENT verdict still surfaces, just report-only.
    assert len(report13.provisional) == 1
    assert report13.provisional[0].verdict == "DIFFERENT"

    report = run_detection(store, cfg, upto_round=18)
    assert all(e.onset_round != 13 for e in report.new_events)
    assert store.latest_status("startup.ttid_ms", 13) is None
    assert len(store.all_detections()) == 0


# --- test 3: APK threshold rule -------------------------------------------

def test_apk_threshold_rule(cfg):
    # +1.02% sustained step at round 7; persistence_rounds=1 -> confirmable
    # (onset+1) as soon as round 7 itself is in the prefix.
    with Store(":memory:") as s:
        seed(s, {"app.size_bytes": [[4_900_000]] * 6 + [[4_950_000]] * 4})
        report = run_detection(s, cfg, upto_round=7)
        confirmed = next(e for e in report.new_events if e.metric == "app.size_bytes")
        assert confirmed.onset_round == 7
        assert confirmed.status == "CONFIRMED"
        assert confirmed.direction == "up"
        assert s.latest_status("app.size_bytes", 7) == "CONFIRMED"

    # +0.3% step: below the 0.5% gate -> no event, ever.
    with Store(":memory:") as s:
        base = 4_900_000
        small_step = round(base * 1.003)
        seed(s, {"app.size_bytes": [[base]] * 6 + [[small_step]] * 4})
        report = run_detection(s, cfg, upto_round=10)
        assert report.new_events == []
        assert len(s.all_detections()) == 0

    # -1% step: sustained shrink -> IMPROVED, never alerted.
    with Store(":memory:") as s:
        base = 4_900_000
        smaller = round(base * 0.99)
        seed(s, {"app.size_bytes": [[base]] * 6 + [[smaller]] * 4})
        report = run_detection(s, cfg, upto_round=7)
        improved = next(e for e in report.new_events if e.metric == "app.size_bytes")
        assert improved.onset_round == 7
        assert improved.status == "IMPROVED"
        assert improved.direction == "down"
        assert s.latest_status("app.size_bytes", 7) == "IMPROVED"


# --- test 4: A/B mode, short history ---------------------------------------

def test_ab_mode_short_history(store, cfg):
    rng = random.Random(51)
    baseline = [[100 + rng.gauss(0, 0.5) for _ in range(20)] for _ in range(5)]
    rng2 = random.Random(52)
    stepped = [[130 + rng2.gauss(0, 0.5) for _ in range(20)]]
    seed(store, {"startup.ttid_ms": baseline + stepped})   # 6 rounds total

    report = run_detection(store, cfg, upto_round=6)
    # far below min_history (11) -> no step-fit candidate is even scorable.
    assert not any(e.status in ("SUSPECT", "CONFIRMED", "IMPROVED") for e in report.new_events)
    prelim = next(e for e in report.new_events if e.status == "PRELIMINARY")
    assert prelim.metric == "startup.ttid_ms"
    assert prelim.onset_round == 6
    assert prelim.direction == "up"
    assert store.latest_status("startup.ttid_ms", 6) == "PRELIMINARY"


# --- test 5: environment guard groups 3 dimensions -------------------------

def _flat(n, mean, sd, seed_):
    r = random.Random(seed_)
    return [[mean + r.gauss(0, sd) for _ in range(20)] for _ in range(n)]


def test_env_guard_groups_3_dimensions(store, cfg):
    metric_values = {
        # round 13: 3 DIFFERENT dimensions step simultaneously -> env guard.
        "startup.ttid_ms":        _flat(12, 100.0, 0.5, 1) + _flat(18, 120.0, 0.5, 2),
        "memory.peak_kb": _flat(12, 100.0, 0.5, 3) + _flat(18, 120.0, 0.5, 4),
        # frames.p90_ms steps TWICE: once with the round-13 trio (env guard),
        # once again at round 25 alongside its own frames siblings (below) --
        # a single metric reused across both scenarios pins that env-guard
        # grouping keys off (onset, dimension), not "has this metric ever
        # been grouped before".
        "frames.p90_ms": (_flat(12, 100.0, 0.5, 5) + _flat(12, 120.0, 0.5, 6)
                           + _flat(6, 144.0, 0.5, 7)),
        # round 25: R2-style multi-series-ONE-dimension (all 4 frames
        # metrics) -> must NOT trigger env guard (pins critique C1).
        "frames.p50_ms":         _flat(24, 100.0, 0.5, 8) + _flat(6, 120.0, 0.5, 9),
        "frames.p99_ms":         _flat(24, 100.0, 0.5, 10) + _flat(6, 120.0, 0.5, 11),
        "frames.overrun_p90_ms": _flat(24, 5.0, 0.2, 12) + _flat(6, 8.0, 0.2, 13),
    }
    seed(store, metric_values)

    report = run_detection(store, cfg, upto_round=30)

    # round 13: startup + frames + memory = 3 distinct dimensions -> single
    # grouped ENV_SUSPECT; nobody CONFIRMED at round 13.
    assert report.env_suspect_rounds == [13]
    onset13 = [e for e in report.new_events if e.onset_round == 13]
    assert {e.metric for e in onset13} == {
        "startup.ttid_ms", "memory.peak_kb", "frames.p90_ms",
    }
    assert all(e.status == "ENV_SUSPECT" for e in onset13)
    for metric in ("startup.ttid_ms", "memory.peak_kb", "frames.p90_ms"):
        assert store.latest_status(metric, 13) == "ENV_SUSPECT"

    # round 25: all 4 frames metrics step together -- one dimension only ->
    # normal CONFIRMED, not grouped.
    onset25 = [e for e in report.new_events if e.onset_round == 25]
    confirmed25 = {e.metric for e in onset25 if e.status == "CONFIRMED"}
    assert confirmed25 == {
        "frames.p50_ms", "frames.p90_ms", "frames.p99_ms", "frames.overrun_p90_ms",
    }
    for metric in confirmed25:
        assert store.latest_status(metric, 25) == "CONFIRMED"


# --- test 6: replay ratchet never downgrades --------------------------------

def test_replay_ratchet_never_downgrades(store, cfg):
    A = _flat(12, 100.0, 0.5, 61) + _flat(8, 115.0, 0.5, 62)   # 20 rounds, onset=13
    B = _flat(12, 100.0, 0.5, 63) + _flat(8, 115.0, 0.5, 64)   # 20 rounds, onset=13
    C = _flat(14, 100.0, 0.5, 65) + _flat(6, 110.0, 0.5, 66)   # 20 rounds, onset=15
    seed(store, {
        "startup.ttid_ms": A,
        "memory.peak_kb": B,
        "frames.p50_ms": C,
    })

    # Establish CONFIRMED for A and B directly, at upto=18 (their onset+5).
    # C's own onset+5 is 20, so it can't fire yet -- m=2 at this evaluation.
    report18 = run_detection(store, cfg, upto_round=18)
    confirmed18 = {e.metric for e in report18.new_events if e.status == "CONFIRMED"}
    assert confirmed18 == {"startup.ttid_ms", "memory.peak_kb"}
    assert store.latest_status("startup.ttid_ms", 13) == "CONFIRMED"
    assert store.latest_status("memory.peak_kb", 13) == "CONFIRMED"

    # Full replay of the whole history: by r=20, C's onset (15) also becomes
    # scorable and joins the BH-family p-value set for that evaluation (BH's
    # m grows from 2 to 3). A/B's already-CONFIRMED rows for onset 13 must
    # never be rewritten -- the store-level ratchet suppresses every replay
    # attempt at that (metric, onset) pair, so no such event should ever
    # reappear in any of analyze()'s reports.
    reports = analyze(store, cfg)
    assert len(reports) == 20
    for r in reports:
        for e in r.new_events:
            if e.onset_round == 13 and e.metric in ("startup.ttid_ms", "memory.peak_kb"):
                pytest.fail(f"replay re-emitted an event for an already-CONFIRMED onset: {e}")

    assert store.latest_status("startup.ttid_ms", 13) == "CONFIRMED"
    assert store.latest_status("memory.peak_kb", 13) == "CONFIRMED"
    # C reaches its own CONFIRMED once scorable, undisturbed by A/B.
    assert store.latest_status("frames.p50_ms", 15) == "CONFIRMED"

    # Idempotent replay: running analyze() again over unchanged history must
    # not change any latest_status, or add/alter a single detections row.
    before = [dict(row) for row in store.all_detections()]
    analyze(store, cfg)
    after = [dict(row) for row in store.all_detections()]
    assert before == after


# --- task review finding 3: A/B must not attribute PRELIMINARY to a false,
# adjacent onset next to a step-fit candidate's true onset -------------------

def test_ab_does_not_survive_at_adjacent_false_onset(store, cfg):
    # Same series as test 1 (onset=13). Replaying via analyze() genuinely
    # sweeps through upto_round=14 -- unlike test 1's hand-picked checkpoints
    # (13, 17, 18), which never touch 14 at all and so can't exercise this.
    # At upto=14, the newest-round A/B check compares r14 against pooled
    # [r12, r13] -- a MIXED baseline straddling the true onset -- which also
    # reads DIFFERENT. Before the fix this permanently stored a second, false
    # PRELIMINARY at onset 14 that coexisted forever (append-only, separate
    # (metric, onset) ratchet key) with the later CONFIRMED at the true
    # onset 13. Exactly one of {13, 14} may ever carry a stored detection.
    rng = random.Random(42)
    baseline = [[rng.gauss(100, 0.5) for _ in range(20)] for _ in range(12)]
    rng2 = random.Random(43)
    stepped = [[rng2.gauss(115, 0.5) for _ in range(20)] for _ in range(6)]
    seed(store, {"startup.ttid_ms": baseline + stepped})

    analyze(store, cfg)

    onsets_with_detections = [
        o for o in (13, 14) if store.latest_status("startup.ttid_ms", o) is not None
    ]
    assert onsets_with_detections == [13]
    assert store.latest_status("startup.ttid_ms", 13) == "CONFIRMED"


# --- test 7: direction agreement vetoes -------------------------------------

def test_direction_agreement_vetoes(cfg):
    # Step-fit's R statistic is a mean-based split of the FULL two halves of
    # an (2*width+1)-round window; MW confirmation only looks at the 2
    # innermost rounds on each side of onset (rounds_per_side=2). Widening
    # the step-fit window to 20 makes that innermost pair a small enough
    # fraction of each half that a local reversal there barely dents the
    # halves' means (R stays comfortably HIGH), while the reversal is still
    # trivially MW-significant given the tiny per-round sd -- the only way
    # to construct "trend says up, iterations say down" as two genuinely
    # different estimators over overlapping-but-not-identical data.
    cfg.detection.width = 20
    cfg.detection.min_history = 41

    outer_left = _flat(18, 90.0, 0.3, 71)     # rounds 1..18   (dominant LOW)
    inner_left = _flat(2, 91.0, 0.3, 72)      # rounds 19,20   <- confirmation LEFT
    inner_right = _flat(2, 89.0, 0.3, 73)     # rounds 21,22   <- confirmation RIGHT (inverted: lower than LEFT)
    outer_right = _flat(19, 140.0, 0.3, 74)   # rounds 23..41  (dominant HIGH)

    with Store(":memory:") as s:
        seed(s, {"startup.ttid_ms": outer_left + inner_left + inner_right + outer_right})
        report = run_detection(s, cfg, upto_round=41)

        onset_events = [e for e in report.new_events if e.onset_round == 21]
        assert onset_events, "expected step-fit to still find onset=21 as HIGH"
        assert {e.status for e in onset_events} == {"SUSPECT"}
        assert onset_events[0].metric == "startup.ttid_ms"
        assert s.latest_status("startup.ttid_ms", 21) == "SUSPECT"


# --- supplementary (beyond the brief's 7 pinned tests): binding semantics
# that none of the 7 numbered tests exercise directly, but that the task's
# resolved semantics require verbatim -- see task-8-report.md.

def test_ground_truth_match_flows_through_when_expected_lookup_given(store, cfg):
    rng = random.Random(42)
    baseline = [[rng.gauss(100, 0.5) for _ in range(20)] for _ in range(12)]
    rng2 = random.Random(43)
    stepped = [[rng2.gauss(115, 0.5) for _ in range(20)] for _ in range(6)]
    seed(store, {"startup.ttid_ms": baseline + stepped})

    calls = []

    def expected_lookup(onset_round_id, metric, direction):
        calls.append((onset_round_id, metric, direction))
        return (onset_round_id, metric, direction) == (13, "startup.ttid_ms", "up")

    report = run_detection(store, cfg, upto_round=18, expected_lookup=expected_lookup)
    confirmed = next(e for e in report.new_events if e.status == "CONFIRMED")
    assert confirmed.ground_truth_match is True
    assert calls, "expected_lookup was never invoked"
    row = next(r for r in store.all_detections() if r["status"] == "CONFIRMED")
    assert bool(row["ground_truth_match"]) is True


def test_ground_truth_match_is_none_when_expected_lookup_omitted(store, cfg):
    rng = random.Random(42)
    baseline = [[rng.gauss(100, 0.5) for _ in range(20)] for _ in range(12)]
    rng2 = random.Random(43)
    stepped = [[rng2.gauss(115, 0.5) for _ in range(20)] for _ in range(6)]
    seed(store, {"startup.ttid_ms": baseline + stepped})

    report = run_detection(store, cfg, upto_round=18)
    confirmed = next(e for e in report.new_events if e.status == "CONFIRMED")
    assert confirmed.ground_truth_match is None


def test_bh_family_low_candidate_recorded_as_improved(store, cfg):
    # A clean downward step (all metrics are higher-is-worse) must record
    # IMPROVED with no MW confirmation and no path to CONFIRMED/alerting.
    rng = random.Random(42)
    baseline = [[rng.gauss(115, 0.5) for _ in range(20)] for _ in range(12)]
    rng2 = random.Random(43)
    stepped = [[rng2.gauss(100, 0.5) for _ in range(20)] for _ in range(6)]
    seed(store, {"startup.ttid_ms": baseline + stepped})

    report = run_detection(store, cfg, upto_round=18)
    onset_events = [e for e in report.new_events if e.onset_round == 13]
    improved = next(e for e in onset_events if e.status == "IMPROVED")
    assert improved.metric == "startup.ttid_ms"
    assert improved.direction == "down"
    assert improved.mwu_p is None
    assert not any(e.status in ("SUSPECT", "CONFIRMED") for e in onset_events)
    assert store.latest_status("startup.ttid_ms", 13) == "IMPROVED"
