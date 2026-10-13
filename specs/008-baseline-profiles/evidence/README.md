# Feature 008 — baseline profiles: pull-request evidence

Device for every number: Pixel 6 Pro `19021FDEE001AH` (API 37, Android 17), Mac host, 2026-10-12. Raw artefacts
(benchmark JSON, instrumentation logs, Gradle logs) stay under `app/perf/data/` on the producing machine, as the
rounds' do; nothing of this feature entered the `perf-history` branch. Full numbers and the device-step outcomes:
[`../baselines.md`](../baselines.md).

## 1. Does the profile help? (SC-001, SC-002) — `uv run tracepulse profile compare --device pixel6`

Same shrunk `perf` build (`7ba60be`), fresh install per state, 20 iterations per journey, two-sided Mann–Whitney at
p < 0.01. Two runs of the default two states and one with the fully optimised reference:

| Run | File | Wall | `startup.ttid_ms` none → profile |
|---|---|---|---|
| 1 | [`comparison-pixel6-run1.md`](comparison-pixel6-run1.md) | 13 min 10 s | 182.7 → 155.9 ms, **−14.7 %, confirmed** |
| 2 | [`comparison-pixel6.md`](comparison-pixel6.md) | 14 min 32 s | 184.5 → 151.3 ms, **−18.0 %, confirmed** |
| 3 (`--with-full`) | [`comparison-pixel6-with-full.md`](comparison-pixel6-with-full.md) | 19 min 53 s | 182.0 → 153.5 ms, **−15.7 %, confirmed** |

Run 1 predates the direction fix (`232f0b6`: "frames.overrun_p90_ms −1.4 → −3.6 ms" was labelled *worse +157 %*
because its baseline median is negative); its medians and p-values are valid, only the `direction` of the
negative-baseline rows is wrong. Run 2 is the evidence for the criteria.

### 1.1 SC-001 — cold-start first frame at least 15 % lower, confirmed

Met in run 2 (−18.0 %, p < 0.01); run 1 landed just under the floor (−14.7 %, confirmed). The profile's gain on this
app is about 15–18 % of a first frame that shrinking had already brought from 500 ms to 180 ms (§3).

### 1.2 SC-002 — TTFD and the first-open sections lower and confirmed; nothing worse and confirmed

| Criterion | Run 2 | Verdict |
|---|---|---|
| `startup.ttfd_ms` lower, confirmed | 184.5 → 151.3 ms, −18.0 %, confirmed | **met** (TTFD equals TTID on this app: first content is reported with the first frame) |
| `tuner/section.tuner.load.sum_ms` lower, confirmed | 70.7 → 72.6 ms, +2.8 %, not confirmed | **not met**: the tuner's first-open section is dominated by the engine's microphone start, not by Kotlin code the profile compiles |
| `metronome/section.metronome.load.sum_ms` lower, confirmed | 0.1 → 0.1 ms (+14 %, confirmed worse on a 0.01 ms difference) | **not met**: a sub-millisecond section; the change is real in the test's sense and irrelevant in the product's |
| no metric worse and confirmed | three rows: the two `metronome.load` sections above (≈ 0.01 ms) and `stress/section.tuner.frame.sum_ms` 0.5 → 0.8 ms (+59 %; 30 frames of a 0.02 ms draw helper) | **not met by the letter**, on sub-millisecond sections; no journey duration, frame percentile, memory or startup series is worse and confirmed |

What the profile does improve, confirmed in both runs: the frame-time tails of every journey — tuner p90 −27 %
and p99 −25 %, metronome p90 −9 % and p99 −28 %, stress p99 −13 %, metronome frame overrun −1.7 → −3.4 ms.
The journey durations and the p50 frame times move within ±5 % and are not confirmed either way.

### 1.3 The gap to full optimisation (`--with-full`)

Run 3 (none, profile, full; 19 min 53 s; SC-004 ≤ 40 min holds for every run) confirms SC-001 a third time
(−15.7 %). The fully optimised state is **not** a better cold start than no profile on this app (182.4 vs 182.0 ms):
`speed` compiles everything and there is more to load at start, while `speed-profile` compiles the startup path
only — so "gap closed" is undefined for startup (the two reference medians are 0.4 ms apart) and the profile is
simply the best of the three states for the first frame. For the frame-time tails the profile closes most of the
none → full gap: tuner p99 92 %, metronome p99 87 %, stress p99 83 %. `tuner.load` stays at ≈ 70 ms in all three
states, which confirms it is not compilation-bound.

After the three runs `uv run tracepulse status` lists the same 67 rounds and `git -C app/perf/.history status`
is clean: no comparison entered the history (FR-009).

## 2. The profile ships and takes effect (SC-005, FR-006, FR-007) — `uv run tracepulse profile verify --device pixel6`

```
profile present (8443 bytes, 0.1 %) in …/androidApp-perf.apk
recognised (result 1): installed
optimised (speed-profile)
```

Exit 0, 10.1 s wall for the whole command including the build (SC-005 ≤ 2 min). `dumpsys package dexopt`:
`arm64: [status=speed-profile] [reason=cmdline]`. The receiver's `INSTALL_PROFILE` path forces the write, so the
broadcast answers 1 on a repeat as well; 2 can only come from the app's own first-launch write.

## 3. The shrunk build (FR-005a, SC-007, SC-010)

- `androidApp-perf.apk` **6 966 412 bytes** (14 922 467 before: −53 %); `assets/dexopt/baseline.prof` 8 443 bytes =
  **0.12 %** (budget 3 %). Zero compiler and R8 warnings; `proguard-rules.pro` holds only its policy header — no keep
  rule was needed.
- Startup profile consumed: `mergePerfStartupProfile` executed (14 609 rules), `classes.dex` holds `MainActivity`
  and 1 559 classes, `classes2.dex` 1 577 classes without it.
- One full round on the shrunk build: all four journeys, 20 iterations, no badge, 13 min 35 s. Cold start **500 →
  177 ms (−65 %)** under full compilation — shrinking alone; the one gate flag (`stress/section.tuner.modeSwitch`
  +64 %) is a bimodal section whose two modes (≈275 / ≈478 ms) are unchanged; the slow mode happened to win the
  median in this round (11/20 vs 6/20 iterations). To watch in the labelled session.
- Screenshots: [`../screenshots/`](../screenshots/) — 14 pairs `<width>-<destination>-{debug,perf}.png`, compact
  (portrait, bottom bar) and expanded (landscape ≈ 891 dp, rail). Home, Timeline, Tuner (Auto Listen; By Ear with
  string VI auditioned), Metronome (running, settings open), Projects, Profile — Effects and Pads are not
  destinations yet. Pixel difference per pair ≤ 0.13 % except the two live Auto Listen meters (0.8 / 1.0 %).

## 4. Before / after (SC-006, SC-007)

| Measure | Before | After |
|---|---|---|
| `checkAndroidHost --rerun-tasks`, steady state (feature 007's lane protocol) | 4.35 s | 4.46 s (+2.4 %) |
| `:androidApp:assembleDebug`, up to date, same daemon | 0.447 s | 0.448–0.458 s (+0.2 … +2.5 %) |
| `assembleRelease` (R8, not budgeted) | — | 15.6 s, zero warnings |
| Pixel round duration | 14.4 min | 13.6 min |
| `app.size_bytes` | 14 922 467 | 6 966 412 |

## 5. Generation (SC-003) — `uv run tracepulse profile generate --device pixel6`

3 min 00 s; `baseline-prof.txt` 21 244 rules (2 781 own, 13 196 Compose), `startup-prof.txt` 14 610 rules; all six
coverage labels found. A regeneration on the unchanged build differs in 0.2 % of the baseline lines and 0.75 % of
the startup lines (timing-reached rules), never in the six descriptors; it was not committed. Negative paths: no
`--device` with three devices → 4; an iOS device → 4; a broken anchor → 3 with the committed files untouched.

## 6. Decisions and the principle each serves

| Decision | Principle / requirement |
|---|---|
| R8 shrinking and obfuscation of `release` and `perf` | spec FR-005a (the owner's planning input of 2026-10-12): the startup profile's DEX layout is R8-only; the app halves in size |
| `androidx.profileinstaller` as the one shipped dependency (Apache-2.0) | Principle VI with justification: direct installs (FR-006) and the comparison's `Partial(Require)` state need it |
| no new module — generator and comparison beside the benchmarks, profile files and the coverage check in `:androidApp`, commands in `tracepulse` | Principle II (fixed module graph) |
| the Baseline Profile plugin applied from `build-logic` by the two convention plugins | the plugin compiles against AGP, which lives in that classloader; incidentally ends the second AGP instance `:perf-benchmarks` had loaded |
| one `collect` per test in the generator; the generator grants the microphone | found on the device (baselines.md T019): the plugin's per-test message file and its own install |
| `tembra.compare.iterations` | Macrobenchmark 1.5 ignores `androidx.benchmark.iterations` for `measureRepeated` |
| the coverage test declares its two text files as inputs | otherwise Gradle served it from the cache after a rule changed and the lane stayed green |
| `CompareState.parse` owns the state argument; exhaustive `when` for the compilation mode | Principle VIII (`kotlin-api-design`, `kotlin-control-flow`) |
| JVM coverage test kept (no Gradle-task fallback) | the lane cost is +2.4 % under the project's protocol (SC-006) |

## 7. After the merge (SC-008, SC-009) — the labelled session `baseline-profiles`

Run on 2026-10-13 against `main` at the merge commit `bda88af` (tasks.md T049; the full record is
[`../baselines.md` § T049](../baselines.md)). Five rounds on the Pixel 6 Pro, all succeeded, no badge, no
interactive step, published to `perf-history`; 13.57 min per round against 14.21 min for the 007 session (−4.6 %,
within SC-008's 5 %).

| SC-008 criterion | Outcome |
|---|---|
| no family flagged in the worse direction | one flag, round 3: `stress/section.tuner.modeSwitch.sum_ms` +35 % — the bimodal section of §3, with 10/20 iterations in its slow mode against 2–9 elsewhere and both modes unchanged; the other four rounds are within limits on every series |
| every onset at the label boundary is an improvement | ten onsets, all improvements: `app.size_bytes` −53 % (IMPROVED), cold start `ttid`/`ttfd` −64 %, frame p99 −13 % (tuner), −16 % (metronome), −15 % (stress), `tuner/frames.p50` −8 %, `metronome/memory.end` −8 %; nothing in the worse direction |
| `app.size_bytes` lower than the previous label | 14 922 467 → 6 966 412 bytes |

Cold start over the five rounds: median 178.3 ms (500.8 ms in the 007 session). Journey durations, `tuner.load`
and the memory peaks are unchanged within noise. The report (`app/perf/reports/report.html`, rebuilt by
`uv run tracepulse report` from the synced history) is the artefact for SC-009 together with §1–§3 above.

**Still owed**: the same five rounds on the Pixel 7 Pro, which was not attached on 2026-10-13 (wake it, then
`uv run tracepulse run --device pixel7 --label baseline-profiles --rounds 5`). The iPhone 14 is not part of this
feature: the iOS app is untouched (FR-014).
