"""Per-metric summary statistics shared by the collectors, the store and the records.

`percentile` is the project-wide convention (sort; rank = (n-1)*q/100; linear
interpolation between floor and ceil ranks), identical to trace-pulse's.
"""

import math
from typing import Dict, Sequence


def percentile(values: Sequence[float], q: float) -> float:
    """Linear-interpolated percentile: sort; rank=(n-1)*q/100; interpolate floor/ceil."""
    s = sorted(values)
    n = len(s)
    rank = (n - 1) * q / 100
    lo = math.floor(rank)
    hi = math.ceil(rank)
    if lo == hi:
        return s[int(lo)]
    frac = rank - lo
    return s[int(lo)] + (s[int(hi)] - s[int(lo)]) * frac


def summarize(values: Sequence[float]) -> Dict[str, float]:
    """The record's `summary` block: median, min, max, mean, p90, p99 and the
    coefficient of variation (population standard deviation over the mean;
    0 when the mean is 0)."""
    if not values:
        raise ValueError("summarize needs at least one value")
    n = len(values)
    mean = sum(values) / n
    variance = sum((v - mean) ** 2 for v in values) / n
    cov = (math.sqrt(variance) / mean) if mean else 0.0
    return {
        "median": percentile(values, 50), "min": min(values), "max": max(values), "mean": mean,
        "p90": percentile(values, 90), "p99": percentile(values, 99), "cov": cov,
    }
