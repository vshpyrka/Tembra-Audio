# Data Model: Performance Pipeline (feature 007)

Entities come from the spec's Key Entities; storage is the per-round record on the history branch (source of truth) and a derived SQLite cache (`app/perf/data/tracepulse.db`, rebuilt by `sync`). Field names are the ones the record schema and the CLI use.

## Device

The unit a series belongs to. Keyed by `device_id`.

| Field | Type | Notes |
|---|---|---|
| `device_id` | string | Android serial (`adb devices`) or iOS UDID (`devicectl`/`simctl`); the series key |
| `platform` | `android` \| `ios` | |
| `kind` | `device` \| `simulator` | simulators come from the plan input; never mixed with devices |
| `model` | string | `Pixel 6 Pro`, `iPhone 14`, `iPhone 17 (simulator)`; read from the device on first sight |
| `reference` | bool | `true` for physical devices by default; `false` for simulators; gates are inactive when false unless overridden |
| `overrides` | map | per-device protocol overrides (`battery_temp_max_c`, `refresh_rate_hz`, …) |

Validation: `device_id` unique; a record whose `device_id` is unknown creates the device from the record's fingerprint.

## Round

One measurement of one build on one device. Identity: `(device_id, started_at)`.

| Field | Type | Notes |
|---|---|---|
| `round_id` | string | `<utc-timestamp>_<label>` (the record file name without extension) |
| `device_id` | string | → Device |
| `label` | string | feature name by convention (`007-performance-pipeline`); required |
| `session_index` / `session_size` | int / int | position inside a `--rounds N` session |
| `build` | object | `commit`, `branch`, `dirty` (bool), `app_version`, `app_size_bytes` (and iOS `app_size_uncompressed_bytes`) |
| `fingerprint` | object | `os_version`, `os_build`, `model`, `sdk` (Android), `refresh_rate_hz`, `network_state` (`none` \| `wifi` \| `cellular` \| `unknown`), `thermal_state` (iOS: `nominal`…`critical`; Android: battery temperature °C), `battery_level` |
| `machine` | object | `hostname`, `os`, `tool_version` — metadata only, never a key |
| `checks` | list | each `{ name, passed, value, limit }` from the device protocol |
| `status` | `succeeded` \| `refused` \| `failed` | refused rounds are **not** published; failed rounds are published with `failure_reason` and no samples |
| `badges` | list of `noisy` \| `thermal` \| `simulator` \| `fingerprint_changed` | |
| `journeys` | list of Journey results | |
| `gate` | list of Gate decisions | filled by `detect`/`gate` on rebuild, also written at publish time |
| `artifacts` | list | `{ kind, path_on_machine }`; paths are informational (traces never leave the machine) |
| `started_at`, `finished_at` | ISO-8601 UTC | |

State transitions: `planned → running → succeeded | refused | failed`; `succeeded` and `failed` are terminal and published; `refused` is terminal and local only.

## Journey result

| Field | Type | Notes |
|---|---|---|
| `journey` | `startup` \| `tuner` \| `metronome` \| `stress` | same names on both platforms |
| `iterations` | int | 20 by default |
| `metrics` | map metric key → Metric result | |

## Metric result (Samples)

| Field | Type | Notes |
|---|---|---|
| `key` | string | from the catalogue, e.g. `startup.ttid_ms`, `frames.hitch_ratio_ms_per_s`, `section.tuner.frame.sum_ms`, `audio.max_callback_us` |
| `unit` | string | |
| `direction` | `higher_is_worse` (all) | improvements are reported, never alarmed |
| `samples` | number[] | one value per iteration (the `samples` table rows); empty for `n=1` metrics such as app size |
| `summary` | object | `median, min, max, mean, p90, p99, cov` computed at collection |
| `source` | string | provenance: `macrobenchmark`, `perfetto`, `xcresult`, `xctrace`, `engine-health`, `apk`, `thinning-report` |

## Series

Derived: all rounds of one `(device_id, metric key)` ordered by `started_at`. Not stored; built on `sync`. Round medians feed the detector; samples feed confirmation.

## Detection (append-only, derived but persisted in the cache and in the report)

Unchanged from trace-pulse: `(metric, onset_round) → status ∈ {preliminary, suspect, confirmed, improved, environment_suspect}`, `step_size`, `stepfit_score`, `mwu_p`, `delta_pct`, `persistence_count`, `triage_status`; statuses ratchet on recompute. New field: `label_at_onset` (the round's label) for the report headline.

## Gate decision

| Field | Type | Notes |
|---|---|---|
| `metric` | string | |
| `state` | `inactive` \| `within_limit` \| `above_limit` | informational only |
| `reason` | string | for `inactive` (`fewer than N clean rounds`, `simulator series`, `disabled`) |
| `limit_pct` | number | from the catalogue or config |
| `observed_pct` | number | median change against the baseline |
| `baseline_rounds` | string[] | the N clean round ids compared against |

Clean round (spec US3 scenario 3): `status == succeeded` and no `noisy`/`thermal` badge and not in an environment-suspect group; a gate flag does not make a round unclean.

## Section catalogue (code, not data)

`PerfSection` enum in `:shared:engine` (families `tuner`, `metronome`, `timeline`, `engine`; entries carry no strings) and `PerfCounter` enum (`AUDIO_CALLBACKS`, `AUDIO_LAST_US`, `AUDIO_MAX_US`, `AUDIO_OVER_BUDGET`, `AUDIO_XRUNS`, `AUDIO_REALTIME`); the signpost names live in `app/shared/engine/perf-catalogue.txt`, from which `PerfNames.kt` (measurement builds only) and the C header `tembra_trace_names.h` are generated; a unit test keeps enum, catalogue and header identical. Startup has no sections: it is measured by the platforms' launch metrics.

## Metric catalogue (config + code)

`app/perf/tracepulse/catalogue.py` holds the per-platform table in `contracts/metric-catalogue.md`: key, platform, source, unit, comparable-across-platforms flag, default gate limit, screening gate for the detector. `tracepulse.yml` may override limits and disable metrics; it cannot add keys (keys are code).

## Audio health (engine telemetry)

`tembra_audio_health { callbacks, last_us, max_us, over_budget, xruns, realtime }` read through `tembra_engine_audio_health()`; Kotlin `AudioHealth` value type in `:shared:engine`; `FakeAudioEngine.scriptAudioHealth(...)` for tests.

## Report model (derived)

Grid of Device cards (platform, model, kind badge, reference badge, last round date and label, round count, open gate flags, worst current verdict) → Device page (headline text, per-family charts with label boundaries, onset markers, gate marks; rounds table; flagged list) → Metric page (per-iteration distributions per round, detection details). Built entirely from the rebuilt cache; no network access at view time.
