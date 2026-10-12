# Contract: the report (`app/perf/reports/report.html`)

One self-contained HTML file (inline CSS, inline SVG, vanilla JS, data embedded as JSON; zero external requests; readable in light and dark). Navigation by URL hash so a device page can be bookmarked. Rendered by `tracepulse report` from the rebuilt history; identical output for identical history (golden test).

## Views

### `#/` — device grid (the landing view)

A responsive grid of device cards, one per device in the history, sorted platform then model, simulators after devices. Each card shows: platform icon and model; `kind` badge (`simulator`) and `non-reference` badge when applicable; last round date and label; number of rounds; a one-line status headline (see "Human text"); counts: open gate flags, suspect/confirmed onsets in the last session. Clicking a card opens `#/device/<device_id>`. Above the grid: a one-paragraph summary across devices ("3 devices, 47 rounds, last session 007-performance-pipeline on 2026-10-08; 2 metrics above their limit on iPhone 14, nothing confirmed"), then a "Flagged" list (every `above_limit` decision in the latest round of each device) and a "Changes" list (every suspect/confirmed onset, newest first), each item linking to its metric page.

### `#/device/<device_id>` — device page

Header: model, platform, kind, device id, OS version (with "changed at round …" when the fingerprint changed), network-state note for iOS, protocol overrides in use. Headline paragraph in plain language. Then one section per metric family (startup, frames, memory, cpu, sections, app size, audio) with a chart per metric: x = round index with dates and **label boundaries** (vertical markers named by label, i.e. the feature), y = round median with the interquartile band of iterations; onset markers by status (confirmed solid, suspect hollow, preliminary dashed, improved with a downward arrow, environment-suspect grouped band); gate flags as a mark on the flagged round with the limit line drawn for the comparison window; quality badges on hover. Under each chart one sentence of human text. Then the rounds table (date, label, status, badges, build commit, OS, thermal/battery/network, gate summary, link to the raw-artifact path on the producing machine) and a "What changed" list for this device. Back link to the grid.

### `#/device/<device_id>/<metric>` — metric page

The chart enlarged, per-round iteration distributions (strip or box per round), the detection record (score, p-value, onset label), the gate history, and the catalogue note for that metric (unit, source, per-platform mismatch text).

## Human text

Generated, never free-form, from detection and gate state with trace-pulse's existing plain-language dictionaries extended for labels and gates:

- Device headline: "Nothing changed in the last 5 rounds." / "Startup got 18 % slower in 007-performance-pipeline (suspect; confirmed after 3 more rounds)." / "2 metrics above their limit in the latest round: startup.ttid_ms (+18 %, limit 15 %), memory.peak_kb (+16 %, limit 15 %)." / "Preliminary only: 4 of 11 rounds needed for a verdict."
- Per-chart sentence: status ladder "Confirmed / Suspect / First hint / Improved / Steady", with the label at onset and the size of the step.
- Simulator pages carry the fixed note: "Simulator numbers show gross changes only; they are not a performance reference."

## Non-functional

- Opens from the file system; no fonts or scripts fetched; under 5 MB with 50 devices × 200 rounds (data embedded compactly, charts drawn on demand).
- Keyboard: cards and links focusable; hash navigation works with browser back.
- Charts follow the `dataviz` skill (one palette, consistent marks, legible in both themes); the grid and typography follow `design/stitch/DESIGN.md` tokens so the report reads as a Tembra artefact.
- Golden test renders a fixture history (3 devices incl. one simulator, labels, one confirmed onset, two gate flags, one fingerprint change) and compares the HTML; a second test asserts zero external URLs.
