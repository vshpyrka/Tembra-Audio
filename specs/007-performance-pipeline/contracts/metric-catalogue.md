# Contract: metric catalogue

Code in `app/perf/tracepulse/catalogue.py`; `tracepulse.yml` may override `gate_limit_pct` and disable a key, never add one. All metrics are higher-is-worse. "Comparable" = may be shown on one axis across platforms (only the `audio.*` family and `app.size_bytes`-style per-platform series are never overlaid; the report keeps every series per device regardless).

| Key | Platform | Source | Unit | Journey | Gate default % | Screening gate (detector) | Comparable |
|---|---|---|---|---|---|---|---|
| `startup.ttid_ms` | android | Macrobenchmark `StartupTimingMetric.timeToInitialDisplayMs` | ms | startup | 15 | 3 % | no (per platform) |
| `startup.ttfd_ms` | android | `timeToFullDisplayMs` (needs `ReportDrawnWhen`) | ms | startup | 15 | 3 % | no |
| `startup.ttid_ms` | ios | `XCTApplicationLaunchMetric(waitUntilResponsive: false)` | ms | startup | 15 | 3 % | no |
| `startup.ttfd_ms` | ios | `XCTApplicationLaunchMetric(waitUntilResponsive: true)` | ms | startup | 15 | 3 % | no |
| `frames.p50_ms`, `frames.p90_ms`, `frames.p99_ms` | android | `FrameTimingMetric.frameDurationCpuMs` P50/P90/P99 | ms | tuner, metronome, stress | 25 | 3 / 5 / 8 % | no |
| `frames.overrun_p90_ms` | android | `frameOverrunMs` P90 (API 31+) | ms | same | abs 2.0 ms | abs 2.0 ms | no |
| `frames.hitch_ratio_ms_per_s` | ios | animation `XCTOSSignpostMetric` (or `XCTHitchMetric`) hitch time ratio | ms/s | tuner, metronome, stress | abs 5 ms/s | abs 2 ms/s | no |
| `frames.hitch_count` | ios | same | count | same | 25 | 25 % | no |
| `frames.fps` | ios | same, frame rate (lower is worse → stored as `frames.fps_deficit` = max(0, 60 − fps)) | fps | same | abs 5 | abs 2 | no |
| `memory.peak_kb` | android | `MemoryUsageMetric(Max, RssAnon)` | kB | tuner, metronome, stress | 15 | 3 % | no |
| `memory.end_kb` | android | `MemoryUsageMetric(Last, RssAnon)` (heap size until 2026-10-11; its round medians varied 4–16 %) | kB | same | 15 | 5 % | no |
| `memory.peak_kb` | ios | `XCTMemoryMetric` Memory Peak Physical | kB | same | 15 | 3 % | no |
| `memory.end_kb` | ios | `XCTMemoryMetric` Memory Physical (delta) | kB | same | 15 | 5 % | no |
| `cpu.time_ms` | ios | `XCTCPUMetric` CPU Time | ms | tuner, metronome, stress | 20 | 5 % | no |
| `cpu.instructions_k` | ios | CPU Instructions Retired | kI | same | 20 | 5 % | no |
| `journey.duration_ms` | both | Android: trace section around the journey (`TraceSectionMetric`); iOS: `XCTClockMetric` | ms | tuner, metronome, stress | 20 | 5 % | no |
| `section.<name>.sum_ms` | both | Android `TraceSectionMetric(name, Sum)`; iOS `XCTOSSignpostMetric(subsystem: audio.tembra, category: ui|engine, name:)` duration | ms | journey that exercises it | 20 | 5 % | **yes** (same instrumentation, same name) |
| `section.<name>.count` | both | same | count | same | 20 | 10 % | yes |
| `app.size_bytes` | android | perf APK bytes (`os.path.getsize`) | bytes | — (n=1) | 2 (threshold detector, 0.5 %) | — | no |
| `app.size_bytes` | ios | thinning report, compressed size of `iPhone14,7` variant | bytes | — (n=1) | 2 | — | no |
| `app.size_uncompressed_bytes` | ios | thinning report, uncompressed (advisory) | bytes | — | — | — | no |
| `audio.callbacks`, `audio.last_callback_us`, `audio.max_callback_us`, `audio.over_budget`, `audio.xruns`, `audio.realtime` | both | Android counter tracks; iOS `engine-health.json` | various | stress | 50 (realtime: any change) | 10 % | **yes** |
| `audio.ioproc_p99_us` | ios | `xctrace` Audio System Trace `audio-hal-client-io-cycle-ioproc` | µs | stress | 50 | 10 % | no (advisory) |

Simulator series: same keys, `kind: simulator`; gates inactive by default; `frames.*` and `audio.ioproc_*` absent (not produced on simulators); report marks the series as non-reference.

A catalogue entry may carry `since` (an ISO instant): the first round its current source applies to. Values of that key from earlier rounds stay in their records but are not loaded into the cache, so a source change never shows as a step in the series.

Known mismatches recorded for the report's per-platform notes (spec FR-008): TTFD is app-declared on Android and OS-defined on iOS; frames are percentiles on Android and hitch-based on iOS; `memory.end_kb` is resident anonymous memory at the end of the journey on Android and a physical delta on iOS; app size is an APK on Android and a thinned IPA variant on iOS.
