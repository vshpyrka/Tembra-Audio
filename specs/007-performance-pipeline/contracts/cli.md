# Contract: `tracepulse` command line (app/perf)

All commands run from `app/perf/` (or via `uv run --project app/perf tracepulse …` from anywhere), are non-interactive, read `app/tracepulse.yml`, and write under `app/perf/data/` (git-ignored) and `app/perf/.history/` (the history-branch worktree, git-ignored).

## Exit statuses (FR-003)

| Code | Meaning |
|---|---|
| 0 | success (gate flags do not change this) |
| 2 | refused: a device check failed before measuring; nothing stored or published |
| 3 | round failed: measurement started and did not complete; a `failed` record is published without samples |
| 4 | usage or configuration error (unknown device, two devices and none named, missing tool) |
| 5 | publish failed (round stored locally; `tracepulse publish` retries) |

## Commands

```
tracepulse devices                       # attached (adb, devicectl, simctl booted) + registry; marks unknown devices
tracepulse doctor --device <id>          # device protocol checks only; exit 0/2
tracepulse run --device <id> --label <text> [--rounds N] [--journeys a,b] [--iterations 20] [--no-publish] [--keep-build]
tracepulse run --simulators --label <text> [--rounds N]      # every booted simulator, sequentially
tracepulse publish [--device <id>]       # push any unpublished local records to the history branch
tracepulse sync                          # pull the history branch, rebuild data/tracepulse.db from records
tracepulse analyze                       # recompute detections + gate decisions over the rebuilt history
tracepulse report [--open] [--out <file>]  # render reports/report.html (self-contained)
tracepulse status                        # local rounds, unpublished records, history branch state
tracepulse inject <R#> | revert [<R#>|--all]   # regression-injection self-test (ported patches)
```

`run` performs, in order: `doctor` → build (Android: `:androidApp:assemblePerf` + `:perf-benchmarks:assemblePerf` through the `gradle-run` wrapper with `-Ptembra.perf=true`; iOS: `xcodebuild build-for-testing … -configuration Perf`) → fresh install → one unmeasured warm-up launch → journeys (Android: `am instrument -w` per benchmark class with `additionalTestOutputDir`; iOS: `xcodebuild test-without-building -xctestrun … -resultBundlePath …`) → collect → app size (APK bytes; iOS thinning report, cached per build) → store record → `publish` → `sync` → `analyze` → `report`. With `--rounds N` the build happens once and steps from fresh install onward repeat N times; the session stops at the first refusal or failure.

`--device` accepts an Android serial, an iOS UDID, or a registry alias (`pixel6`, `iphone14`). With exactly one attached device the flag may be omitted; with several it is required (exit 4).

## Output

Human-readable progress on stdout, one line per step; a final block:

```
round 2026-10-08T14:02:11Z_007-performance-pipeline on iPhone 14 (00008110-…): succeeded
  journeys: startup ✓ tuner ✓ metronome ✓ stress ✓   iterations: 20
  badges: —   network: none   thermal: nominal   battery: 83%
  gate: 2 above limit — startup.ttid_ms +18.4% (limit 15%, vs 5 clean rounds), memory.peak_kb +16.1% (limit 15%)
  published: perf-history/records/00008110-…/2026-10-08T140211Z_007-performance-pipeline.json
  report: app/perf/reports/report.html
```

`--json` on any command prints the same information as one JSON object on stdout (for a future CI job).

## Configuration: `app/tracepulse.yml`

```yaml
detection: { width: 5, threshold: 15, min_sd: 0.001, min_history: 11 }     # unchanged trace-pulse defaults
confirm:   { rounds_per_side: 2, q: 0.05 }
collection: { iterations: 20, session_rounds: 5 }
history:   { branch: perf-history, remote: origin, worktree: perf/.history }
gate:
  clean_rounds: 5
  limits_pct: { startup.ttid_ms: 15, memory.peak_kb: 15, app.size_bytes: 2 }   # overrides of catalogue defaults
  simulators: inactive
devices:
  19021FDEE001AH: { platform: android, model: Pixel 6 Pro, alias: pixel6, overrides: { battery_temp_max_c: 40.0 } }
  00008110-001049660E3A401E: { platform: ios, model: iPhone 14, alias: iphone14 }
android:
  package: audio.tembra.app
  activity: audio.tembra.android.MainActivity
  build_type: perf
  benchmarks_module: ":perf-benchmarks"
ios:
  project: iosApp/iosApp.xcodeproj
  scheme: TembraPerf
  configuration: Perf
  bundle_id: audio.tembra.app          # team suffix appended from Config.xcconfig at run time
  test_target: TembraPerfTests
  size_variant: iPhone14,7
journeys: [startup, tuner, metronome, stress]
```

Unknown keys are an error (exit 4). Device entries are added automatically on first run.
