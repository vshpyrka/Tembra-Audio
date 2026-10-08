import math
import random
from tracepulse.detect import step_fit_original, scan_series, HIGH, LOW, UNINTERESTING
from tracepulse.config import load_config, DEFAULT_CONFIG_PATH


# --- Skia test vectors, verbatim (research detection.md; floor 0.1 as in Skia's tests) ---
def test_skia_vector_step():
    fit = step_fit_original([0, 0, 1, 1, 1], min_sd=0.1, threshold=20)
    assert math.isclose(fit.r, -20.412415, abs_tol=1e-5)
    assert fit.status == HIGH                     # negative R = step up


def test_skia_vector_three_points():
    fit = step_fit_original([608100, 672970, 653180], min_sd=0.1, threshold=5)
    assert math.isclose(fit.r, -11.785709, abs_tol=1e-5)


def test_flat_window_uninteresting():
    assert step_fit_original([5.0] * 11, 0.001, 15).status == UNINTERESTING


def test_equal_halves_uninteresting():
    assert step_fit_original([1, 2, 3, 3, 2, 1], 0.001, 15).status == UNINTERESTING  # y0 == y1


def test_population_sd_and_lse_quirk():
    # hand-computed: window [0,0,0,0,0,1,1,1,1,1,1] (11 pts, step at center)
    fit = step_fit_original([0]*5 + [1]*6, min_sd=0.001, threshold=15)
    m = 6/11; sd = math.sqrt(sum((x - m)**2 for x in [0]*5 + [1]*6) / 11)
    t0 = (0 - m)/sd; t1 = (1 - m)/sd
    y0, y1 = t0, t1                                # halves are constant → means are the values
    assert math.isclose(fit.step_size, y0 - y1, rel_tol=1e-12)
    assert math.isclose(fit.r, (y0 - y1) / 0.001, rel_tol=1e-9)  # SSE=0 → lse=0 → min_sd floor
    assert fit.status == HIGH


def test_scan_finds_onset_at_step():
    det = load_config(DEFAULT_CONFIG_PATH).detection
    rng = random.Random(7)
    base = [100 + rng.gauss(0, 0.5) for _ in range(12)]
    stepped = [115 + rng.gauss(0, 0.5) for _ in range(12)]
    medians = list(enumerate(base + stepped, start=1))   # onset at round 13
    hits = scan_series(medians, det)
    assert [c.onset_round for c in hits if c.status == HIGH] == [13]
    c = hits[0]
    assert c.delta_raw > 10 and 10 < c.delta_pct < 20    # ≈ +15%


def test_scan_pure_noise_never_fires():
    det = load_config(DEFAULT_CONFIG_PATH).detection
    rng = random.Random(11)
    fired = 0
    for _ in range(300):
        medians = list(enumerate([100 + rng.gauss(0, 3) for _ in range(24)], 1))
        fired += sum(1 for c in scan_series(medians, det) if c.status in (HIGH, LOW))
    assert fired == 0                                    # |R|≥15 ≈ 1e-4/window; 300×14 windows ⇒ 0


def test_scan_needs_width_successors():
    det = load_config(DEFAULT_CONFIG_PATH).detection
    medians = list(enumerate([100.0]*8 + [150.0]*4, 1))  # step at 9, only 4 successors
    assert scan_series(medians, det) == []               # not scorable yet


def test_unstable_benchmark_shape_no_fire():
    # SPEC §15 "unstable-benchmark shape": bimodal alternation is variance, not a step.
    det = load_config(DEFAULT_CONFIG_PATH).detection
    series = [100.0 if i % 2 == 0 else 130.0 for i in range(24)]
    assert [c for c in scan_series(list(enumerate(series, 1)), det)
            if c.status in (HIGH, LOW)] == []


def test_adjacent_steps_within_6_rounds_documented_interference():
    # two +15% steps 3 rounds apart: the FIRST onset may be suppressed (shared windows);
    # the SECOND must be found — this pins the known limitation from SPEC §9 notes.
    det = load_config(DEFAULT_CONFIG_PATH).detection
    series = [100.0]*8 + [115.0]*3 + [132.0]*10
    hits = scan_series(list(enumerate(series, 1)), det)
    assert 12 in [c.onset_round for c in hits]
