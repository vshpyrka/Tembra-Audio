"""Step-fit detection core - Skia-exact implementation - and the detection
engine (screening -> confirmation -> BH -> env guard -> APK rule -> A/B
provisional -> ratchet-recorded replay) built on top of it."""

import math
from dataclasses import dataclass
from typing import Callable, Dict, List, Sequence, Tuple, Optional, Set

import scipy.stats

from .catalogue import CatalogueError, Metric, lookup
from .config import Config, Detection
from .store import Store, split_series_key


# Module constants
LOW = "LOW"
HIGH = "HIGH"
UNINTERESTING = "UNINTERESTING"


@dataclass(frozen=True)
class StepFit:
    """Result of fitting a step to a window of data."""
    status: str
    r: float
    step_size: float
    lse: float


@dataclass(frozen=True)
class Candidate:
    """Candidate regression detection at an onset round."""
    onset_round: int
    status: str
    r: float
    delta_raw: float
    delta_pct: Optional[float]
    window_start_round: int


def _median(values: Sequence[float]) -> float:
    """Compute median: sort, return middle or mean of two middles."""
    sorted_vals = sorted(values)
    n = len(sorted_vals)
    if n % 2 == 1:
        return sorted_vals[n // 2]
    else:
        return (sorted_vals[n // 2 - 1] + sorted_vals[n // 2]) / 2.0


def step_fit_original(window: Sequence[float], min_sd: float, threshold: float) -> StepFit:
    """
    Fit a step to a window using Skia's exact algorithm.

    Normalizes by population standard deviation, splits at midpoint,
    computes R-statistic with the LSE quirk (sqrt(SSE)/N).
    """
    n = len(window)
    if n < 3:
        return StepFit(UNINTERESTING, 0.0, 0.0, 0.0)

    # Compute mean and population standard deviation
    m = sum(window) / n
    sd = math.sqrt(sum((x - m) ** 2 for x in window) / n)

    # Normalize: if sd > min_sd use normalized values, else use centered values
    if sd > min_sd:
        t = [(x - m) / sd for x in window]
    else:
        t = [x - m for x in window]

    # Split at midpoint
    i = n // 2
    left, right = t[:i], t[i:]

    # Compute half-means
    y0 = sum(left) / len(left)
    y1 = sum(right) / len(right)

    # Check for equal halves
    if y0 == y1:
        return StepFit(UNINTERESTING, 0.0, 0.0, float("inf"))

    # Compute SSE and LSE (with Skia quirk: sqrt(SSE)/N not sqrt(SSE/N))
    sse = sum((x - y0) ** 2 for x in left) + sum((x - y1) ** 2 for x in right)
    lse = math.sqrt(sse) / n

    # Compute R-statistic with min_sd floor
    r = (y0 - y1) / max(lse, min_sd)

    # Determine status based on R-statistic
    # Negative R = step up (regression) = HIGH
    # Positive R = step down (improvement) = LOW
    if r >= threshold:
        status = LOW
    elif r <= -threshold:
        status = HIGH
    else:
        status = UNINTERESTING

    return StepFit(status, r, y0 - y1, lse)


def scan_series(medians: List[Tuple[int, float]], det: Detection) -> List[Candidate]:
    """
    Scan a series of medians for step detections using a centered window.

    Args:
        medians: List of (round_id, median_value) tuples in chronological order.
        det: Detection configuration with width, threshold, min_sd.

    Returns:
        List of Candidate detections, deduplicated by onset (keeping max |r|).
    """
    w = det.width
    out = []

    # Scan with centered window of radius w (total 2w+1 points)
    for c in range(w, len(medians) - w):
        # Extract window around center c
        window = [v for _, v in medians[c - w : c + w + 1]]

        # Fit step to window
        fit = step_fit_original(window, det.min_sd, det.threshold)

        # Skip uninteresting fits
        if fit.status == UNINTERESTING:
            continue

        # Extract left and right halves (around center, excluding center)
        left_raw = window[:w]
        right_raw = window[w:]

        # Compute delta and percentage change
        left_median = _median(left_raw)
        right_median = _median(right_raw)
        delta = right_median - left_median
        pct = (delta / left_median * 100.0) if left_median else None

        # Record candidate with the center point's round id as onset
        out.append(Candidate(
            onset_round=medians[c][0],
            status=fit.status,
            r=fit.r,
            delta_raw=delta,
            delta_pct=pct,
            window_start_round=medians[c - w][0]
        ))

    # Deduplicate by onset: keep max |r| for each onset_round
    best = {}
    for cand in out:
        if cand.onset_round not in best or abs(cand.r) > abs(best[cand.onset_round].r):
            best[cand.onset_round] = cand

    # Return in sorted order by onset round
    return [best[k] for k in sorted(best)]


def mw_two_sided_p(a: Sequence[float], b: Sequence[float]) -> float:
    """
    Compute the two-sided Mann-Whitney U test p-value.

    Uses scipy.stats.mannwhitneyu with method="exact" if there are no ties
    across the pooled samples, otherwise uses method="asymptotic".

    Args:
        a: First sample (sequence of floats).
        b: Second sample (sequence of floats).

    Returns:
        Two-sided p-value.
    """
    # Pool all values to check for ties
    pooled = list(a) + list(b)
    unique_count = len(set(pooled))
    total_count = len(pooled)

    # Use exact method if no ties, otherwise asymptotic
    if unique_count == total_count:
        method = "exact"
    else:
        method = "asymptotic"

    result = scipy.stats.mannwhitneyu(a, b, alternative="two-sided", method=method)
    return result.pvalue


def benjamini_hochberg(pvals: List[Tuple[str, float]], q: float) -> Set[str]:
    """
    Benjamini-Hochberg FDR correction procedure (step-up).

    Sorts p-values ascending, finds the largest k with p_k <= q*k/m,
    and accepts ranks 1..k.

    Args:
        pvals: List of (label, p-value) tuples.
        q: FDR control parameter (typically 0.05).

    Returns:
        Set of labels corresponding to rejected hypotheses.
    """
    if not pvals:
        return set()

    m = len(pvals)

    # Sort by p-value ascending (1-indexed)
    sorted_pvals = sorted(pvals, key=lambda x: x[1])

    # Find largest k where p_k <= q * k / m
    # k is 1-indexed
    largest_k = 0
    for k in range(1, m + 1):
        p_k = sorted_pvals[k - 1][1]  # k-1 for 0-indexed array
        threshold = q * k / m
        if p_k <= threshold:
            largest_k = k

    # Accept ranks 1..largest_k (0..largest_k-1 in 0-indexed array)
    result = {sorted_pvals[i][0] for i in range(largest_k)}
    return result


def min_achievable_p(n1: int, n2: int) -> float:
    """
    Compute the minimum achievable p-value for a Mann-Whitney U test.

    This is the p-value under the most extreme ordering (complete separation).
    Formula: 2.0 / C(n1 + n2, n1), where C is the binomial coefficient.

    Args:
        n1: Size of first sample.
        n2: Size of second sample.

    Returns:
        Minimum achievable p-value (always > 0, often << 0.05).
    """
    return 2.0 / math.comb(n1 + n2, n1)


def ab_verdict(new: Sequence[float], baseline: Sequence[float],
               p_different: float, p_same: float) -> str:
    """
    Three-way A/B test verdict based on Mann-Whitney U p-value.

    Args:
        new: New/treatment sample.
        baseline: Baseline/control sample.
        p_different: Threshold for "DIFFERENT" verdict (typically 0.05).
        p_same: Threshold for "SAME" verdict (typically 0.10).

    Returns:
        "DIFFERENT" if p <= p_different,
        "SAME" if p > p_same,
        "UNKNOWN" otherwise.
    """
    p = mw_two_sided_p(new, baseline)

    if p <= p_different:
        return "DIFFERENT"
    elif p > p_same:
        return "SAME"
    else:
        return "UNKNOWN"


def directions_agree(step_status: str, a_median: float, b_median: float) -> bool:
    """
    Check if the direction of change agrees with the step status.

    Args:
        step_status: Either HIGH (regression/step-up) or LOW (improvement/step-down).
        a_median: Median of right/new side.
        b_median: Median of left/baseline side.

    Returns:
        True if direction agrees with status:
        - HIGH requires a_median > b_median
        - LOW requires a_median < b_median
    """
    if step_status == HIGH:
        return a_median > b_median
    elif step_status == LOW:
        return a_median < b_median
    else:
        return False


# ============================================================
# Part 3: detection engine + replay (SPEC S9)
# ============================================================

APK_METRIC = "app.size_bytes"


@dataclass(frozen=True)
class Event:
    """One ratchet-accepted detection write (store.record_detection returned
    True) produced by a single run_detection evaluation."""
    metric: str
    onset_round: int
    status: str
    direction: str
    r: Optional[float]
    mwu_p: Optional[float]
    delta_pct: Optional[float]
    ground_truth_match: Optional[bool]


@dataclass(frozen=True)
class AbResult:
    """A SAME/UNKNOWN A/B verdict on the newest round: surfaced, never stored."""
    metric: str
    verdict: str
    p: float


@dataclass(frozen=True)
class DetectionReport:
    """Result of one run_detection evaluation."""
    new_events: List[Event]
    provisional: List[AbResult]
    env_suspect_rounds: List[int]


def _prefix(series: List[Tuple[int, float]], upto_round: Optional[int]) -> List[Tuple[int, float]]:
    """Series entries with round_id <= upto_round (None = the whole series).

    `store.series()` already returns round_id-ascending order, and round_ids
    are assigned in strict insertion order (sqlite AUTOINCREMENT), so this
    filter is always a true prefix of the full series.
    """
    if upto_round is None:
        return series
    return [(rid, v) for rid, v in series if rid <= upto_round]


def _passes_gate(metric: str, delta_pct: Optional[float], delta_raw: float, cfg: Config,
                  platform: str, require_positive_pct: bool = False) -> bool:
    """SPEC S9 screening gate: exactly one of pct/abs applies per metric, taken
    from the catalogue (overridable in tracepulse.yml).

    Shared by step-fit candidates (HIGH/LOW) and the A/B path (task review
    finding 2) -- both must clear the SAME per-metric gate before anything
    gets stored.

    pct gate: `require_positive_pct=True` (HIGH candidates specifically)
    needs delta_pct >= gate; everything else (LOW candidates, and A/B's
    undirected delta) needs abs(delta_pct) >= gate.
    abs gate (e.g. frames.overrun_p90_ms): abs(delta_raw) >= gate, either direction.
    """
    entry = catalogue_entry(platform, metric, cfg)
    if entry.screening_pct is not None:
        gate = entry.screening_pct
        if delta_pct is None:
            return False
        return delta_pct >= gate if require_positive_pct else abs(delta_pct) >= gate
    gate = entry.screening_abs
    return abs(delta_raw) >= gate


def catalogue_entry(platform: str, series: str, cfg: Config) -> Metric:
    """The catalogue entry behind a store series key (`<journey>/<metric>` or bare)."""
    _journey, metric = split_series_key(series)
    return lookup(platform, metric, cfg.catalogue)


def _family_metrics(store: Store, cfg: Config, platform: str,
                    device_id: Optional[str]) -> List[str]:
    """Every series on this device whose metric the catalogue knows and that is
    not single-valued (app size has its own threshold rule)."""
    keys = []
    for metric in store.metrics(device_id):
        try:
            entry = catalogue_entry(platform, metric, cfg)
        except CatalogueError:
            continue
        if entry.single_valued or not entry.enabled:
            continue
        keys.append(metric)
    return keys


def _has_nearby_detection(store: Store, metric: str, onset: int) -> bool:
    """Adjacent-onset guard for A/B PRELIMINARY (task review finding 3):
    True iff `metric` already has ANY stored detection (any status) at
    onset-1, onset, or onset+1 -- SPEC's own +-1 onset-localization slack.

    A/B's newest-round check can straddle a true step-fit onset (its
    pooled baseline mixes pre- and post-step rounds one round after the
    true onset), which used to let it permanently attribute a second,
    false PRELIMINARY to the adjacent round even after the true onset was
    already flagged or later CONFIRMED. Blocking on ANY existing nearby
    detection (not just a higher-ranked one) stops that duplicate from
    ever being written, since the true onset's own record -- whatever its
    current status -- already covers this regression.
    """
    return any(store.latest_status(metric, o) is not None for o in (onset - 1, onset, onset + 1))


def _apk_moves(prefix: List[Tuple[int, float]], apk_cfg) -> List[Tuple[int, str, float, float]]:
    """APK size: threshold detector on consecutive round pairs (outside the
    BH family -- n=1/round, no step-fit, no MW).

    Returns (onset_round_id, direction, delta_pct, raw_delta) for every
    consecutive pair whose |pct change| clears apk.min_delta_pct. With
    persistence_rounds=1, a move is confirmable the moment its onset round
    (the later round of the pair) is itself in the prefix -- no separate
    persistence bookkeeping is needed.
    """
    moves = []
    for k in range(1, len(prefix)):
        _prev_round, prev_v = prefix[k - 1]
        cur_round, cur_v = prefix[k]
        if prev_v == 0:
            continue
        pct = (cur_v - prev_v) / prev_v * 100.0
        if pct >= apk_cfg.min_delta_pct:
            moves.append((cur_round, "up", pct, cur_v - prev_v))
        elif pct <= -apk_cfg.min_delta_pct:
            moves.append((cur_round, "down", pct, cur_v - prev_v))
    return moves


def run_detection(store: Store, cfg: Config, upto_round: Optional[int] = None,
                   expected_lookup: Optional[Callable[[int, str, str], Optional[bool]]] = None,
                   platform: str = "android", device_id: Optional[str] = None,
                   ) -> DetectionReport:
    """Evaluate one device's history <= upto_round (None = all) and record
    events through the store's append-only ratchet.

    `expected_lookup(onset_round_id, metric, direction) -> Optional[bool]`
    is the ground-truth registry indirection (the injection registry
    supplies the real one against `injected_json`); None-safe when omitted.
    `platform` selects the catalogue column; `device_id` selects the series
    (None = every round in the store: single-device stores and tests).
    """
    def gt(onset: int, metric: str, direction: str) -> Optional[bool]:
        return None if expected_lookup is None else expected_lookup(onset, metric, direction)

    events: List[Event] = []
    provisional: List[AbResult] = []

    # --- gate-passed step-fit candidates per catalogue metric ---------------
    family = _family_metrics(store, cfg, platform, device_id)
    metric_prefix: Dict[str, List[Tuple[int, float]]] = {}
    metric_candidates: Dict[str, List[Candidate]] = {}
    for metric in family:
        prefix = _prefix(store.series(metric, device_id), upto_round)
        metric_prefix[metric] = prefix
        cands = scan_series(prefix, cfg.detection)
        metric_candidates[metric] = [
            c for c in cands
            if _passes_gate(metric, c.delta_pct, c.delta_raw, cfg, platform,
                             require_positive_pct=(c.status == HIGH))
        ]

    # --- APK rule: outside the BH family, its own threshold detector -------
    apk_prefix = _prefix(store.series(APK_METRIC, device_id), upto_round)
    apk_moves = _apk_moves(apk_prefix, cfg.apk)

    # --- env guard: group gate-passed candidates by onset, count distinct
    # catalogue families (never series within one family). App size
    # participates via its own rule firing at the same onset.
    dims_by_onset: Dict[int, Set[str]] = {}
    for metric, cands in metric_candidates.items():
        dim = catalogue_entry(platform, metric, cfg).family
        for c in cands:
            dims_by_onset.setdefault(c.onset_round, set()).add(dim)
    for onset, _direction, _pct, _raw in apk_moves:
        dims_by_onset.setdefault(onset, set()).add("app")

    env_suspect_rounds: List[int] = sorted(
        onset for onset, dims in dims_by_onset.items()
        if len(dims) >= cfg.suite_spike.min_dimensions
    )
    env_set: Set[int] = set(env_suspect_rounds)

    # --- env-suspect recording (BH-family): preempts MW confirmation and
    # IMPROVED alike for every gate-passed candidate grouped at this onset.
    for metric, cands in metric_candidates.items():
        for c in cands:
            if c.onset_round not in env_set:
                continue
            direction = "up" if c.status == HIGH else "down"
            g = gt(c.onset_round, metric, direction)
            if store.record_detection(metric, c.onset_round, direction, c.delta_raw, c.r,
                                       None, c.delta_pct, "ENV_SUSPECT", 0, True, g):
                events.append(Event(metric, c.onset_round, "ENV_SUSPECT", direction, c.r,
                                     None, c.delta_pct, g))

    # --- env-suspect recording (APK) ----------------------------------------
    for onset, direction, pct, raw in apk_moves:
        if onset not in env_set:
            continue
        g = gt(onset, APK_METRIC, direction)
        if store.record_detection(APK_METRIC, onset, direction, raw, None, None, pct,
                                   "ENV_SUSPECT", 1, True, g):
            events.append(Event(APK_METRIC, onset, "ENV_SUSPECT", direction, None, None, pct, g))

    # --- LOW candidates (gate-passed, non-env-suspect): IMPROVED, no MW,
    # never alerted.
    for metric, cands in metric_candidates.items():
        for c in cands:
            if c.status != LOW or c.onset_round in env_set:
                continue
            g = gt(c.onset_round, metric, "down")
            if store.record_detection(metric, c.onset_round, "down", c.delta_raw, c.r,
                                       None, c.delta_pct, "IMPROVED", 0, False, g):
                events.append(Event(metric, c.onset_round, "IMPROVED", "down", c.r,
                                     None, c.delta_pct, g))

    # --- HIGH confirmation pipeline: MW p + direction agreement, then BH ----
    pending = []   # (metric, candidate, p, agree)
    for metric, cands in metric_candidates.items():
        prefix = metric_prefix[metric]
        pos = {rid: i for i, (rid, _) in enumerate(prefix)}
        k = cfg.confirm.rounds_per_side
        for c in cands:
            if c.status != HIGH or c.onset_round in env_set:
                continue
            idx = pos[c.onset_round]
            left_ids = [rid for rid, _ in prefix[idx - k: idx]]
            right_ids = [rid for rid, _ in prefix[idx: idx + k]]
            left_iters = store.iterations(metric, left_ids)
            right_iters = store.iterations(metric, right_ids)
            p = mw_two_sided_p(right_iters, left_iters)
            agree = directions_agree(HIGH, _median(right_iters), _median(left_iters))
            pending.append((metric, c, p, agree))

    bh_pairs = [((metric, c.onset_round), p) for metric, c, p, _agree in pending]
    accepted = benjamini_hochberg(bh_pairs, cfg.confirm.q)

    for metric, c, p, agree in pending:
        # Same (onset, metric, direction) triple for both writes below -> one
        # expected_lookup call covers both (it's a pure function of those args).
        g = gt(c.onset_round, metric, "up")
        if store.record_detection(metric, c.onset_round, "up", c.delta_raw, c.r, p,
                                   c.delta_pct, "SUSPECT", 0, False, g):
            events.append(Event(metric, c.onset_round, "SUSPECT", "up", c.r, p, c.delta_pct, g))
        if (metric, c.onset_round) in accepted and agree:
            if store.record_detection(metric, c.onset_round, "up", c.delta_raw, c.r, p,
                                       c.delta_pct, "CONFIRMED", 0, False, g):
                events.append(Event(metric, c.onset_round, "CONFIRMED", "up", c.r, p,
                                     c.delta_pct, g))

    # --- APK rule recording: up -> CONFIRMED (onset+1), down -> IMPROVED ----
    for onset, direction, pct, raw in apk_moves:
        if onset in env_set:
            continue
        status = "CONFIRMED" if direction == "up" else "IMPROVED"
        g = gt(onset, APK_METRIC, direction)
        if store.record_detection(APK_METRIC, onset, direction, raw, None, None, pct,
                                   status, 1, False, g):
            events.append(Event(APK_METRIC, onset, status, direction, None, None, pct, g))

    # --- A/B provisional: the newest round vs pooled immediately-prior rounds
    for metric in family:
        prefix = metric_prefix[metric]
        if len(prefix) < 2:
            continue
        newest_round = prefix[-1][0]
        base_lo = max(0, len(prefix) - 1 - cfg.ab.max_baseline_rounds)
        baseline_ids = [rid for rid, _ in prefix[base_lo: len(prefix) - 1]]
        new_iters = store.iterations(metric, [newest_round])
        baseline_iters = store.iterations(metric, baseline_ids)
        if not new_iters or not baseline_iters:
            continue
        verdict = ab_verdict(new_iters, baseline_iters, cfg.ab.p_different, cfg.ab.p_same)
        p = mw_two_sided_p(new_iters, baseline_iters)
        if verdict != "DIFFERENT":
            provisional.append(AbResult(metric, verdict, p))
            continue

        new_med = _median(new_iters)
        base_med = _median(baseline_iters)
        direction = "up" if new_med > base_med else "down"
        raw_delta = new_med - base_med
        delta_pct = (raw_delta / base_med * 100.0) if base_med else None

        # Task review findings 2 & 3: a DIFFERENT verdict alone isn't enough
        # to store -- it must also clear the metric's own screening gate
        # (finding 2: an A/B-detected sub-gate move must stay report-only,
        # exactly like a gate-failing step-fit candidate), and there must be
        # no existing detection (any status) at an adjacent onset (finding
        # 3: a mixed-baseline newest-round check one round after a true
        # onset must not permanently attribute a second, false PRELIMINARY
        # next to it). Either failure -> report-only provisional, never stored.
        if not _passes_gate(metric, delta_pct, raw_delta, cfg, platform) or \
                _has_nearby_detection(store, metric, newest_round):
            provisional.append(AbResult(metric, verdict, p))
            continue

        g = gt(newest_round, metric, direction)
        if store.record_detection(metric, newest_round, direction, raw_delta,
                                   None, p, delta_pct, "PRELIMINARY", 0, False, g):
            events.append(Event(metric, newest_round, "PRELIMINARY", direction, None, p,
                                 delta_pct, g))

    return DetectionReport(new_events=events, provisional=provisional,
                            env_suspect_rounds=env_suspect_rounds)


def analyze(store: Store, cfg: Config,
            expected_lookup: Optional[Callable[[int, str, str], Optional[bool]]] = None,
            platform: str = "android", device_id: Optional[str] = None,
            ) -> List[DetectionReport]:
    """Replay run_detection(upto_round=r) for every round of one device, in
    insertion order. The store's ratchet makes this idempotent: re-running
    analyze over unchanged history never changes any latest_status."""
    return [run_detection(store, cfg, upto_round=row["id"], expected_lookup=expected_lookup,
                          platform=platform, device_id=device_id)
            for row in store.rounds(device_id)]
