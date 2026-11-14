# Performance overview page on GitHub Pages

Date: 2026-11-13. Status: approved in conversation with the owner, written up for the build.

## Why

The owner felt a stutter when opening the Tuner and the Metronome on a phone and wanted to know whether it was new,
how big it was and on which devices. The answer was already in the data: the tracepulse report
(`app/perf/reports/report.html`, feature 007) holds 93 rounds from six devices, and its Pixel 6 Pro series
`metronome/section.metronome.load.sum_ms` went from 0.03 ms (baseline-profiles) to 116 ms (010-closeout), about seven
frames at 60 Hz. Nobody saw it, because the report's front page is a grid of device cards and the change sits on one
small chart among more than a hundred.

The owner's question for the page, in their words: "whether it got worse since the last feature, and on which
device". After seeing a real-data mockup they settled the shape: an overview of where things got worse, by device,
"and then I can go to more details with the existing report".

## Goals

- The front page answers "what got worse since the last feature, on which device" without scrolling through charts.
- Every item links to its existing detail view (`#/device/<id>/<series>`), which stays as it is.
- Larger charts with the collected numbers: an expanded item shows every iteration of every round of the two
  features being compared, not only the per-round median and interquartile band.
- The page is public at `https://vshpyrka.github.io/Tembra-Audio/` and updates itself after rounds are published,
  without anyone rebuilding or pushing it by hand.

## Non-goals

- No change to measuring, detection, the gate or the records. The overview is a view over data the store already has.
- No raw traces on the site. Perfetto traces and xcresult bundles (about 110 MB per Android round) stay on the
  machine that produced them.
- No new charting library and no network requests: the report stays one self-contained file that works from `file://`.

## Anti-goals

- A list that cries wolf. If the overview flags changes that come from a test being redefined, the owner learns to
  ignore it. The stress journey shows why: in 010 it started recording a take and grew from about 4.5 s to about
  25 s, which puts 14 stress numbers on the "worse" list for the Pixels while the app did not get slower.
- Publishing private details. The current report embeds the build Mac's host name (it contains the owner's first
  name) on every round and absolute local paths (`/Users/<user>/…`) on every artifact. The public page must carry
  neither.

## What the page shows

The mockup the owner approved (real data, generated from the store on 2026-11-13) has three parts, top to bottom.
The overview goes on the report's `#/` view, above the existing device grid.

1. **Where.** A grid with one row per reference device and one column per journey, in the report's journey order
   (startup, tuner, metronome, timeline, recording, mixer, stress, windowing). The row header names the device and
   its comparison, for example "baseline-profiles → 010-closeout · 6 vs 5 rounds". A cell shows `▲ n worse, up to
   +x %`, `▼ n better`, `✓ same`, "test changed" or a hatched "no rounds to compare". A worse cell links to its
   first item in the list below. Simulators are left out: the report already says their numbers are not a
   performance reference (`text.SIMULATOR_NOTE`).
2. **What got worse.** One row per series that got worse, ordered by the size of the change: device, the plain series
   title (`text.plain_series`) with the key underneath, before → after in readable units (MB for kB, µs, counts with
   thousands separators), the change (a percentage, or `×N` past +1000 %), and a sparkline of the round medians with
   the boundary between the two features. Opening a row shows the limit it broke, a link to the series in the full
   report and a full-width chart: every iteration as a dot, each round's median as a line, each feature's median as a
   dashed level, the two features separated by a dashed boundary.
3. **What got better.** The same rows for improvements, collapsed.

## The comparison

- **Which rounds.** Succeeded rounds of reference devices (`kind = device`, `reference = 1`). Rounds with an injected
  regression (`injected_json` not empty, the self-test) are left out: they are slow on purpose. Rounds built from a
  dirty tree and rounds with a `noisy` or `thermal` badge stay in, and the row says how many there are ("5 rounds,
  5 noisy"). Builder's call, from the data: all five Pixel 7 Pro baseline-profiles rounds are dirty and all five
  Pixel 6 Pro 010-closeout rounds are noisy, so leaving either kind out would remove the comparisons the page exists for.
- **Which labels.** For each device, take those rounds in time order and list the labels in order of
  first appearance, skipping experiment labels. The newest label is "after", the one before it is "before". The
  experiment labels today are `noise-baseline` and `release-baseline`; they live in configuration with that default,
  not in code. A device with fewer than two feature labels gets a row that says so and no cells.
- **The value per feature.** The median of the rounds' medians (`aggregates.median`) of that feature.
- **Worse, better or the same.** The catalogue entry of the metric decides, with the same rules the gate uses: a
  percentage limit (`gate_limit_pct`) counts only past `floor_abs`; an absolute limit (`gate_limit_abs`) grows to
  `gate_scale_pct` of a large baseline. Beyond the limit and higher is worse; beyond it and lower is better; anything
  else is the same. Every catalogue metric is higher-is-worse today (`Metric.higher_is_worse`), and every one has a
  limit (checked on 2026-11-13). A metric without a limit, or with `enabled = False`, is not judged and not listed.
  `Metric.comparable` says whether a series compares across platforms and plays no part here.
- **What is left out.** Section `.count` series (they count how often a section ran, not how long it took) and
  `app.size_bytes`, which has no journey and is already flagged by the gate.
- **A metric on one side only** is "new" or "gone", named in the journey's cell tooltip, never as worse.
- **A cell's state, in order of precedence** (builder's call): no judged series on one side or the other, because the
  journey did not run or everything in it was left out → "no rounds to compare"; otherwise a different set of
  `section.*` series → "test changed"; otherwise the counts of worse and better series. `startup` has no sections,
  so it is never "test changed".
- **Test changed.** When the set of `section.*` series of a journey differs between the two features, the journey
  was redefined. Its cell reads "test changed" and names the sections that appeared or disappeared, and its series
  are listed in their own collapsed group instead of under "worse" or "better". This is a heuristic: a journey can
  change its steps without adding a section. On the Pixel 6 Pro it catches the stress journey's redefinition in 010,
  which added `timeline.playheadFrame`, `timeline.scroll` and `transport.play`.

The overview is not the gate. The gate compares the latest round with the last five clean rounds; the overview
compares two features as wholes. Both stay on the page and say which is which.

## Data in the page

The report embeds per-round aggregates only; the full-width chart needs the iterations. The overview embeds the
iteration values (`samples`) of the series it lists as worse, better or changed, for the rounds of the two features
being compared, rounded like the rest of the data. On today's history that is 37 series of about 11 rounds of
20 iterations, about 8,000 numbers, well under 100 kB on a 2.6 MB page.

## Privacy

`tracepulse report --public` leaves out each round's `machine` and the `artifacts` list (local paths of traces that
are not published anyway), and rewrites absolute paths inside every other string of the embedded data: failure
reasons quote xcodebuild messages such as `… at /Users/<user>/Workspace/Dev/Tembra Audio/app/perf/data/…`. A path
under the repository becomes repository-relative; any other home-directory path becomes `~/…`. The round fingerprint
holds OS version and build, battery, thermal and network state, and stays. A test renders the fixture history with
`--public` and fails if the page contains `/Users/`, `/home/` or any round's `machine` value (builder's call: the
reviewer pointed out that a leak is the one mistake publishing cannot take back). The published page is always built with `--public`; a local `report` without the flag
keeps both for the owner. The records on `perf-history` already contain both and are public; changing what a round
records is out of scope here and noted as a follow-up.

## Publishing

- A workflow `.github/workflows/perf-report.yml` on `main` builds the page and deploys it with GitHub Actions' Pages
  deployment (upload the artifact, deploy it). Actions are pinned by commit SHA with a version comment, as in `ci.yml`,
  so Renovate keeps them current.
- Triggers: `workflow_dispatch`; `push` to `main` touching `app/perf/**` or the workflow itself; and a `schedule`
  every hour. A push to `perf-history` cannot trigger it, because that branch holds records only and GitHub reads
  workflows from the pushed branch. The scheduled run first compares the head of `perf-history`
  (`git ls-remote`) with the commit recorded in the deployed site (`history-commit.txt` next to `index.html`) and
  stops if they match, so an idle hour costs a few seconds. It reads that file from the public URL with `curl`; a
  missing or unreachable file means "build". Push and manual runs always build, so a change to the report code
  redeploys without new rounds.
- Steps: check out `main`; install uv and Python as `app/perf/README.md` specifies; `uv sync`; `uv run tracepulse sync`
  (it fetches `perf-history` into its worktree); `uv run tracepulse report --public --out <site>/index.html`; write
  `history-commit.txt`; upload and deploy.
- The site switches from the `gh-pages` branch (the first manual publish on 2026-11-13) to the workflow as its source
  with `gh api -X PUT repos/vshpyrka/Tembra-Audio/pages -f build_type=workflow`, run once the workflow is on `main`.
  The `gh-pages` branch is deleted after the first deployment from the workflow succeeds. Both are outward-facing
  steps and get the owner's yes at that moment.

## Constraints

- The report's existing rules hold: one file, no network requests, works from `file://`, dark and light from the
  existing `report.css` tokens (`design/stitch/DESIGN.md`), usable at phone width.
- `uv run pytest` in `app/perf` stays green; the overview's comparison logic gets its own tests on a fixture store,
  including a redefined journey and a device with one feature.
- `app/perf/README.md` § "Reading the report" describes the overview and § "Where things live" the public page.
- The CI warning gate does not cover Python; no new warnings in the pytest run.

## Left to the builder

- Where the comparison code lives (a new module beside `report.py` is the obvious place) and its function names.
- The exact chart geometry, jitter and tooltip text, within the mockup's look.
- Whether the static Jinja grid also prints the overview for readers without JavaScript (it should at least print
  the "Where" grid).
- The concurrency and permission blocks of the workflow, within what Pages deployment requires.

## Follow-ups, not in this build

- Stop recording the build machine's host name and absolute paths in round records.
- An iPhone round of all seven journeys: on the physical iPhone 14 every XCUITest step waits about 60 s for
  quiescence while the metronome or playback animates (feature 011's T143), so a full round takes about 2.5 hours and
  the 010 rounds were cut. Until then the iPhone runs the startup, tuner and metronome journeys only.
