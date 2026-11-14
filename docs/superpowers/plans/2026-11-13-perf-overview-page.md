# Performance Overview Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put "what got worse since the last feature, on which device" at the top of the tracepulse report, publish a
privacy-safe build of it on GitHub Pages, and keep it current with a workflow.

**Architecture:** A new `tracepulse/overview.py` compares each device's newest feature label with the one before it
from the store, reusing the gate's `decide()` rules, and returns plain data that `report_data()` embeds. The report's
existing script renders it on `#/` above the device grid, with full-width charts of every iteration drawn when an item
opens. `tracepulse/public.py` strips host names and local paths for `report --public`, and
`.github/workflows/perf-report.yml` builds and deploys that page.

**Tech Stack:** Python 3.12 via uv (tracepulse, Jinja2, pytest), vanilla JS and inline SVG in the existing single-file
report, GitHub Actions Pages deployment.

**Spec:** `docs/superpowers/specs/2026-11-13-perf-overview-page-design.md`

All paths below are relative to the repository root; Python commands run from `app/perf/`.

## Global Constraints

- The report stays one self-contained file: no network requests, works from `file://`, no new libraries.
- Colours come from the existing `report.css` tokens; layout works at phone width with no horizontal page scroll.
- Experiment labels default to `noise-baseline` and `release-baseline` and live in configuration, not code.
- Rounds compared: succeeded, `kind = device`, `reference = 1`, `injected_json` empty; dirty, `noisy` and `thermal` rounds stay in and are counted on the row.
- Worse/better use the catalogue entry through `lookup(platform, metric, cfg.catalogue)` and `gate.decide()`; `.count` sections, single-valued metrics (`app.size_bytes`) and `enabled = False` metrics are not judged.
- A cell's state in order of precedence: `none` (no judged series on one side) → `changed` (different `section.*` sets) → `worse` / `better` / `same`. `startup` is never `changed`.
- `--public` pages contain no `/Users/`, no `/home/`, no round `machine` value and no `artifacts` paths.
- Actions are pinned by commit SHA with a `# vX.Y.Z` comment, as in `.github/workflows/ci.yml`.
- `uv run pytest` in `app/perf` stays green with no new warnings.

## Review Focus

- A device whose newest label has only failed rounds (the iPhone's two `010-recording` rounds): the comparison falls back to the newest labels that have succeeded rounds. Test in Task 1.
- A label that reappears after another one (Pixel 6 Pro: `007-performance-pipeline`, then `noise-baseline`, then `007-performance-pipeline` again): labels are ordered by first appearance and all of a label's rounds count together. Test in Task 1.
- A zero baseline (audio xruns 0 → 3): `decide()`'s zero branch judges it, `delta_pct` is `None` and the page shows "from 0" instead of a percentage. Test in Task 1, rendering in Task 2.
- A negative series (`frames.overrun_p90_ms` sits below zero): the change is taken against `abs(before)`, so −5 → −2 is worse and −2 → −5 is better. Test in Task 1.
- A failure reason quoting a path under another user's home (records come from several Macs): `/Users/<anyone>/` and `/home/<anyone>/` become `~/`. Test in Task 3.

---

### Task 1: The comparison (`overview.py`) and its configuration

**Files:**
- Create: `app/perf/tracepulse/overview.py`
- Modify: `app/perf/tracepulse/config.py` (new `Report` dataclass, `_SECTIONS["report"]`, `Config.report`)
- Modify: `app/tracepulse.yml` (add `report: { experiment_labels: [noise-baseline, release-baseline] }` with a one-line comment)
- Test: `app/perf/tests/test_overview.py`, `app/perf/tests/test_config.py` (one case for the new key)

**Interfaces:**
- Consumes: `Store` (`rounds`, `devices`, `metrics`, `aggregate`, `iterations`), `split_series_key`, `catalogue.lookup`, `gate.decide`, `text.plain_series`.
- Produces:
  - `config.Report` with `experiment_labels: List[str] = ["noise-baseline", "release-baseline"]`; `Config.report: Report`.
  - `JOURNEY_ORDER: List[str] = ["startup", "tuner", "metronome", "timeline", "recording", "mixer", "stress", "windowing"]` (unknown journeys follow, sorted).
  - `judge(before: float, after: float, entry: catalogue.Metric) -> Dict` with keys `state` (`"worse" | "better" | "same"`), `delta_abs`, `delta_pct` (`None` when `before == 0`), `limit_pct`, `limit_abs`.
  - `overview_data(store: Store, cfg: Config) -> Dict` returning:
    - `"journeys": List[str]` (the journeys that appear in any cell, in `JOURNEY_ORDER` order)
    - `"devices": List[Dict]`, each `{"device_id", "model", "platform", "before": str|None, "after": str|None, "rounds_before": int, "rounds_after": int, "flags_after": {"noisy": int, "thermal": int, "dirty": int}, "cells": {journey: {"state": "none"|"changed"|"worse"|"better"|"same", "worse": int, "better": int, "worst_pct": float|None, "sections_added": List[str], "sections_removed": List[str], "new": List[str], "gone": List[str]}}}`
    - `"items": List[Dict]`, each `{"device_id", "model", "series", "journey", "metric", "title", "unit", "state": "worse"|"better"|"changed", "before", "after", "delta_abs", "delta_pct", "limit_pct", "limit_abs", "rounds": [{"round_key", "label", "side": "before"|"after", "median", "values": List[float]}]}`; numbers rounded to 5 significant digits like `report._round` (keep a local helper; `report.py` imports this module).

- [ ] **Step 1: Write the failing tests** in `tests/test_overview.py`, building small stores with `Store.add_round` / `add_aggregate` / `add_samples` in `tmp_path` (a helper `_round(store, label, values_by_series, **kw)` adds a round and its aggregates from per-iteration lists):
  - `test_compares_newest_feature_with_the_one_before`: labels A (3 rounds) then B (3 rounds); `devices[0]["before"] == "A"`, `["after"] == "B"`, `rounds_before == 3`, `rounds_after == 3`.
  - `test_experiment_labels_are_skipped`: A, `noise-baseline`, B → before A, after B.
  - `test_label_order_is_first_appearance`: A, N, A, B (A reappears) → before A with all A rounds counted (`rounds_before == 2` for one A round each time).
  - `test_failed_rounds_do_not_make_a_feature`: A ×2 and B ×2 succeeded, then C ×1 failed → before A, after B.
  - `test_one_feature_device`: only A → `before is None`, `after == "A"`, `cells == {}`.
  - `test_injected_simulator_and_non_reference_are_left_out`: an injected B round is not in `rounds_after`; a `kind="simulator"` device and a `reference=False` device have no row.
  - `test_dirty_and_noisy_rounds_count_and_are_reported`: after rounds with one dirty and two `noisy` → `flags_after == {"noisy": 2, "thermal": 0, "dirty": 1}`.
  - `test_worse_better_same_follow_the_catalogue_limits`: `tuner/frames.p99_ms` 10 → 13 (+30 %, limit 25) worse; 10 → 7 better; 10 → 11 same; `tuner/section.tuner.load.sum_ms` 0.03 → 0.5 same (under `floor_abs` 1 ms); 0.03 → 116 worse.
  - `test_negative_series_direction`: `tuner/frames.overrun_p90_ms` −5 → −2 worse (abs limit 2 ms), −2 → −5 better.
  - `test_zero_baseline`: `stress/audio.xruns` 0 → 3 worse with `delta_pct is None`.
  - `test_counts_single_valued_and_disabled_are_not_judged`: `tuner/section.tuner.load.count` and `app.size_bytes` produce no items and no cell counts.
  - `test_redefined_journey_is_changed_not_worse`: `stress` has sections {x} before and {x, y} after with `stress/journey.duration_ms` 4500 → 25000 → cell `state == "changed"`, `sections_added == ["section.y.sum_ms"]` (metric keys without the journey prefix), the duration item has `state == "changed"`, no `worse` item for `stress`.
  - `test_journey_missing_on_one_side_is_none`: `timeline` only in after → cell `state == "none"`, not `changed`; `startup` with different metrics never `changed`.
  - `test_items_carry_every_iteration_and_are_ordered`: two worse items (+30 %, +400 %) and one from-zero item → order from-zero first, then +400 %, then +30 %; each item's `rounds` lists before rounds then after rounds with `side` set and 20 `values` when 20 were stored.
  - `test_config.py`: an unknown key under `report` is a `ConfigError`; the default `experiment_labels` load from `tracepulse.yml`.

- [ ] **Step 2: Run them to see them fail**

Run: `uv run pytest tests/test_overview.py tests/test_config.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'tracepulse.overview'` (and the config case on the unknown section `report`).

- [ ] **Step 3: Implement `config.Report` and `overview.py`**

`judge` calls `decide([before], after, entry.gate_limit_pct, entry.gate_limit_abs, [], floor_abs=entry.floor_abs, scale_pct=entry.gate_scale_pct)` for worse and the same call with `2 * before - after` for better (the mirror image, so both directions use one rule). A feature's value is the median of its rounds' `aggregate(series, round_id)["median"]` over the rounds that have the series. A journey's section set is the set of its `section.*.sum_ms` metrics present in any round of that side.

- [ ] **Step 4: Run them to see them pass**

Run: `uv run pytest tests/test_overview.py tests/test_config.py -q`
Expected: PASS

- [ ] **Step 5: Run the whole suite**

Run: `uv run pytest -q`
Expected: PASS, no new warnings

- [ ] **Step 6: Commit**

```bash
git add app/perf/tracepulse/overview.py app/perf/tracepulse/config.py app/tracepulse.yml app/perf/tests/test_overview.py app/perf/tests/test_config.py
git commit -m "feat(perf): compare each device's newest feature with the one before it"
```

---

### Task 2: The overview on the report's front page

**Files:**
- Modify: `app/perf/tracepulse/report.py` (`report_data` adds `"overview": overview_data(store, cfg)`)
- Modify: `app/perf/tracepulse/static/report.js` (overview above the existing grid view)
- Modify: `app/perf/tracepulse/static/report.css`
- Modify: `app/perf/tracepulse/templates/report.html.j2` (static "Where" table inside `#static-grid`)
- Modify: `app/perf/tests/fixtures/report-golden.html` (regenerated)
- Test: `app/perf/tests/test_report_grid.py`

**Interfaces:**
- Consumes: `overview_data(store, cfg)` and its shapes from Task 1; the script's existing `esc`, `fmt`, `pct`, `ticks`, `device`, tooltip (`.hit` rects with `data-tip`).
- Produces: `DATA.overview` in the page; the anchors `#ov-<device_id>-<journey>` on each journey's first listed item; JS functions `overviewHtml()`, `itemChart(item)`, `sparkline(item)`.

- [ ] **Step 1: Write the failing tests** in `tests/test_report_grid.py`:
  - `test_overview_is_in_the_data`: `data["overview"]["devices"]` has a row for `PIXEL` and `IPHONE` with `before == "006-tuner-noise-gate"` and `after == "007-performance-pipeline"`, and none for `SIM`.
  - `test_static_page_prints_the_where_table`: the HTML contains `id="overview-where"` and, for each overview device, its model inside that table.
  - `test_overview_adds_no_requests`: the existing no-external-request check still passes (extend it to the new markup if it scans by pattern).

- [ ] **Step 2: Run them to see them fail**

Run: `uv run pytest tests/test_report_grid.py -q`
Expected: FAIL on the missing `overview` key and `overview-where`

- [ ] **Step 3: Implement the data, the static table and the script**

The rendered order on `#/`: summary line, "Since the last feature" heading and one-line explanation (it compares features as wholes; the gate compares the latest round with the last five clean rounds), the "Where" table, "What got worse", "What got better", "The test changed", then the existing Flagged, Changes and Devices sections unchanged. Copy and states, from the approved mockup:
  - Row header: model, then `"<before> → <after> · <n> vs <m> rounds"` plus `", <k> noisy"` / `", <k> dirty"` when non-zero; a device without two features: `"<n> rounds, one feature only"`.
  - Cells: `▲ <n> worse` with `up to <change>`; `▼ <n> better`; `✓ same`; `test changed` with the added and removed sections in its `title`; hatched `—` for `none`. Any cell's `title` also names its `new` and `gone` series. A worse cell links to `#ov-<device_id>-<journey>`.
  - Change text: `+30 %` style; `×<ratio>` (ratio `after / before`, thousands separators) past +1000 %; `from 0` when `delta_pct` is null.
  - Item row (`<details>`): device chip, `title` with the series key below in monospace, `before → after` via `fmt`, the change with `▲` or `▼`, a sparkline of round medians with the feature boundary. Body: the limit (`limit 25 %` or `limit 2 ms`), a link `#/device/<id>/<encoded series>` reading "this series in the full report", and the full-width chart.
  - Full-width chart (`itemChart`, 980 × 320, same padding and axis code as `chartSvg(..., true)`): every value as a 2.6 px dot with a deterministic horizontal jitter inside its round's slot, each round's median as a 2 px line with points, each feature's median as a dashed level labelled `median <value>`, a dashed boundary between the features with both labels and round counts above the plot, one `.hit` rect per round whose `data-tip` gives date (from the device's `rounds` entry with the same `round_key`), label, median and n. Draw it when its `<details>` opens (listen for `toggle` on `view` with capture); the first worse item starts open and drawn.
  - kB values display as MB in the overview only (`fmt(v / 1024, "MB")`); the existing charts are unchanged.

- [ ] **Step 4: Regenerate the golden page and run the tests**

Run: `UPDATE_GOLDEN=1 uv run pytest tests/test_report_grid.py -q && uv run pytest tests/test_report_grid.py -q`
Expected: PASS twice (the second run against the new golden)

- [ ] **Step 5: Look at it**

Run: `uv run tracepulse report --out /tmp/overview-check.html` against a local store (or render the fixture store to a temp file), open it in Chrome at 1200 px and at 390 px wide, and check: no overlapping labels in the full-width chart, the table scrolls inside its own box at 390 px instead of widening the page, the first worse item is open, every link opens its series.

- [ ] **Step 6: Commit**

```bash
git add app/perf/tracepulse/report.py app/perf/tracepulse/static/report.js app/perf/tracepulse/static/report.css app/perf/tracepulse/templates/report.html.j2 app/perf/tests/test_report_grid.py app/perf/tests/fixtures/report-golden.html
git commit -m "feat(perf): show what got worse since the last feature on the report's front page"
```

---

### Task 3: The public build (`--public`)

**Files:**
- Create: `app/perf/tracepulse/public.py`
- Modify: `app/perf/tracepulse/report.py` (`report_data(store, cfg, public: bool = False)`, `generate_report(store, cfg, out_path, now_ts, public: bool = False)`)
- Modify: `app/perf/tracepulse/__main__.py` (`report --public`, passed through `_handle_report`)
- Test: `app/perf/tests/test_report_public.py`

**Interfaces:**
- Consumes: `report_data`'s output shape (`devices[].rounds[]` with `machine`, `artifacts`, `failure_reason`).
- Produces: `publicise(data: Dict[str, Any]) -> Dict[str, Any]` (returns a new structure; the input is unchanged).

- [ ] **Step 1: Write the failing tests** in `tests/test_report_public.py`, on the fixture history from `test_report_grid.py` plus one failed round added with `notes="Failed to launch at /Users/someone/Workspace/Dev/Tembra Audio/app/perf/data/rounds/x and /home/runner/y"` and `machine="Someones-MacBook.local"`:
  - `test_public_page_has_no_host_names_or_home_paths`: render with `public=True`; the HTML contains none of `/Users/`, `/home/`, `mbp-m4`, `Someones-MacBook.local`.
  - `test_public_page_keeps_failure_reasons_readable`: the failed round's `failure_reason` in the public data is `"Failed to launch at ~/Workspace/Dev/Tembra Audio/app/perf/data/rounds/x and ~/y"`.
  - `test_public_rounds_have_no_machine_or_artifacts`: every public round lacks `machine` and has `artifacts == []`.
  - `test_private_page_is_unchanged`: `report_data(store, cfg)` without the flag still carries `machine == "mbp-m4"`.
  - `test_cli_report_public_flag`: `tracepulse report --public --out <tmp>` exits 0 and writes a page without `mbp-m4` (follow the CLI test pattern in `tests/test_cli.py`).

- [ ] **Step 2: Run them to see them fail**

Run: `uv run pytest tests/test_report_public.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'tracepulse.public'`

- [ ] **Step 3: Implement `publicise` and the flag**

Collect every round's `machine` value first, then walk the whole structure: drop `machine`, empty `artifacts`, replace each collected machine value with `build machine`, and apply `re.sub(r"/(?:Users|home)/[^/\s\"']+/", "~/", s)` to every string. `report_data(..., public=True)` returns `publicise(data)`.

- [ ] **Step 4: Run them to see them pass, then the suite**

Run: `uv run pytest tests/test_report_public.py -q && uv run pytest -q`
Expected: PASS, no new warnings

- [ ] **Step 5: Commit**

```bash
git add app/perf/tracepulse/public.py app/perf/tracepulse/report.py app/perf/tracepulse/__main__.py app/perf/tests/test_report_public.py
git commit -m "feat(perf): build a public report without host names or local paths"
```

---

### Task 4: The Pages workflow and the documentation

**Files:**
- Create: `.github/workflows/perf-report.yml`
- Modify: `app/perf/README.md` (§ "Commands": `report --public`; § "Reading the report": the overview; § "Where things live": the public page, its workflow and `history-commit.txt`)

**Interfaces:**
- Consumes: `uv run tracepulse analyze` (rebuilds the store from `perf-history` and recomputes detections and gate decisions) and `uv run tracepulse report --public --out <file>` from Task 3.
- Produces: the site at `https://vshpyrka.github.io/Tembra-Audio/` with `index.html` and `history-commit.txt`.

- [ ] **Step 1: Write the workflow**

  - `name: Performance report`; triggers `workflow_dispatch`, `push` to `main` with `paths: [app/perf/**, app/tracepulse.yml, .github/workflows/perf-report.yml]`, `schedule: - cron: "17 * * * *"`.
  - `permissions: { contents: read, pages: write, id-token: write }`; `concurrency: { group: perf-report, cancel-in-progress: false }`.
  - Check each action's inputs in its README at the pinned version (for `setup-uv`, whether it takes `working-directory` or needs `python-version-file: app/perf/.python-version`).
  - Job `build` on `ubuntu-24.04`: `actions/checkout` (SHA as pinned in `ci.yml`); a step `check` that reads `HEAD=$(git ls-remote origin refs/heads/perf-history | cut -f1)` and, only for `schedule` events, `DEPLOYED=$(curl -fsS https://vshpyrka.github.io/Tembra-Audio/history-commit.txt || true)`, and sets output `skip=true` when they are equal and non-empty; the remaining steps run only when `skip != 'true'`: `astral-sh/setup-uv` (pinned SHA, latest release) with `working-directory: app/perf`, `uv sync --locked`, `uv run tracepulse analyze`, `uv run tracepulse report --public --out "$GITHUB_WORKSPACE/site/index.html"`, `echo "$HEAD" > "$GITHUB_WORKSPACE/site/history-commit.txt"`, `actions/configure-pages`, `actions/upload-pages-artifact` with `path: site` (all pinned by SHA).
  - Job `deploy` needs `build`, runs when `needs.build.outputs.skip != 'true'`, `environment: { name: github-pages, url: ${{ steps.deployment.outputs.page_url }} }`, step `actions/deploy-pages` (pinned SHA) with `id: deployment`.

- [ ] **Step 2: Check it**

Run: `actionlint .github/workflows/perf-report.yml` when installed (`brew install actionlint` is acceptable on the owner's Mac), else `python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/perf-report.yml`
Expected: no findings

- [ ] **Step 3: Run the workflow's build steps locally**

From a clean clone of the branch in a temp dir: `cd app/perf && uv sync --locked && uv run tracepulse analyze && uv run tracepulse report --public --out /tmp/site/index.html`, then `grep -c -E "/Users/|/home/" /tmp/site/index.html`.
Expected: the commands exit 0 and the count is `0`.

- [ ] **Step 4: Update `app/perf/README.md`** with the three sections above, in the README's existing voice (plain sentences, measured facts, dates).

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/perf-report.yml app/perf/README.md
git commit -m "ci(perf): publish the performance report on GitHub Pages"
```

---

## After the tasks (owner's yes at each step, not part of an implementer's task)

1. Pull request from `perf-overview-page` to `main`; CI green; merge.
2. `gh api -X PUT repos/vshpyrka/Tembra-Audio/pages -f build_type=workflow`, then `gh workflow run perf-report.yml`, and check the live page.
3. Delete the `gh-pages` branch once the workflow's deployment is live.
