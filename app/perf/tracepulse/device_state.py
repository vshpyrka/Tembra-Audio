"""The measurement state a round enforces on an Android device, as a context manager for the
baseline-profile commands (feature 008): airplane mode on, the refresh rate pinned, the screen
kept on, every change restored on exit. The logic is `orchestrate_android.run_round`'s, repeated
here rather than refactored out of it because the round command is not modified by feature 008
(spec FR-010)."""

import contextlib
from typing import Iterator

from .config import Config
from .devices import Device
from .orchestrate_android import _enforce_airplane, _limit, _pin_refresh, _setting, _shell
from .runner import Clock, Runner


@contextlib.contextmanager
def measurement_state(cfg: Config, device: Device, runner: Runner, clock: Clock) -> Iterator[None]:
    airplane_was = _setting(cfg, device, runner, "global", "airplane_mode_on")
    peak_was = _setting(cfg, device, runner, "system", "peak_refresh_rate")
    min_was = _setting(cfg, device, runner, "system", "min_refresh_rate")
    wanted = f"{float(_limit(cfg, device, 'refresh_rate_hz', 60.0)):g}"
    if airplane_was != "1":
        _enforce_airplane(cfg, device, runner, True)
        clock.sleep(2)
    if peak_was != wanted or min_was != wanted:
        _pin_refresh(cfg, device, runner, wanted, wanted)
    _shell(cfg, device, runner, "svc", "power", "stayon", "true")
    try:
        yield
    finally:
        if airplane_was != "1":
            _enforce_airplane(cfg, device, runner, False)
        if peak_was != wanted or min_was != wanted:
            _pin_refresh(cfg, device, runner, peak_was or "null", min_was or "null")
