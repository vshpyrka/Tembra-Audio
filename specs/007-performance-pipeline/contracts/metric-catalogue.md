# Contract: metric catalogue

Code in `app/perf/tracepulse/catalogue.py`; `tracepulse.yml` may override `gate_limit_pct` and disable a key, never add one. All metrics are higher-is-worse. "Comparable" = may be shown on one axis across platforms (only the `audio.*` family and `app.size_bytes`-style per-platform series are never overlaid; the report keeps every series per device regardless).

| Key | Platform | Source | Unit | Journey | Gate default % | Screening gate (detector) | Comparable |
|---|---|---|---|---|---|---|---|
| `startup.ttid_ms` | android | Macrobenchmark `StartupTimingMetric.timeToInitialDisplayMs` | ms | startup | 15 | 3 % | no (per platform) |
| `startup.ttfd_ms` | android | `timeToFullDisplayMs` (needs `ReportDrawnWhen`) | ms | startup | 15 | 3 % | no |
| `startup.ttid_ms` | ios | `XCTApplicationLaunchMetric(waitUntilResponsive: false)` | ms | startup | 15 | 3 % | no |
| `startup.ttfd_ms` | ios | `XCTApplicationLaunchMetric(waitUntilResponsive: true)` | ms | startup | 15 | 3 % | no |
| `frames.p50_ms`, `frames.p90_ms`, `frames.p99_ms` | android | `FrameTimingMetric.frameDurationCpuMs` P50/P90/P99 | ms | tuner, metronome, timeline, recording, stress | 25 | 3 / 5 / 8 % | no |
| `frames.overrun_p90_ms` | android | `frameOverrunMs` P90 (API 31+) | ms | same | abs 2.0 ms, or 25 % of the baseline when that is larger | abs 2.0 ms | no |
| `frames.hitch_ratio_ms_per_s` | ios | animation `XCTOSSignpostMetric` (or `XCTHitchMetric`) hitch time ratio | ms/s | tuner, metronome, timeline, recording, stress | abs 5 ms/s | abs 2 ms/s | no |
| `frames.hitch_count` | ios | same | count | same | 25 | 25 % | no |
| `frames.fps` | ios | same, frame rate (lower is worse → stored as `frames.fps_deficit` = max(0, 60 − fps)) | fps | same | abs 5 | abs 2 | no |
| `memory.peak_kb` | android | `MemoryUsageMetric(Max, RssAnon)` | kB | tuner, metronome, timeline, recording, stress | 15 | 3 % | no |
| `memory.end_kb` | android | `MemoryUsageMetric(Last, RssAnon)` (heap size until 2026-10-11; its round medians varied 4–16 %) | kB | same | 15 | 5 % | no |
| `memory.peak_kb` | ios | `XCTMemoryMetric` Memory Peak Physical | kB | same | 15 | 3 % | no |
| `memory.end_kb` | ios | `XCTMemoryMetric` Memory Physical (delta) | kB | same | 15 | 5 % | no |
| `cpu.time_ms` | ios | `XCTCPUMetric` CPU Time | ms | tuner, metronome, timeline, recording, stress | 20 | 5 % | no |
| `cpu.instructions_k` | ios | CPU Instructions Retired | kI | same | 20 | 5 % | no |
| `journey.duration_ms` | both | Android: trace section around the journey (`TraceSectionMetric`); iOS: `XCTClockMetric` | ms | tuner, metronome, timeline, recording, stress | 20 | 5 % | no |
| `section.<name>.sum_ms` | both | Android `TraceSectionMetric(name, Sum)`; iOS `XCTOSSignpostMetric(subsystem: audio.tembra, category: ui|engine, name:)` duration | ms | journey that exercises it | 20, and at least 1 ms | 5 % | **yes** (same instrumentation, same name) |
| `section.<name>.count` | both | same | count | same | 20 | 10 % | yes |
| `app.size_bytes` | android | perf APK bytes (`os.path.getsize`) | bytes | — (n=1) | 2 (threshold detector, 0.5 %) | — | no |
| `app.size_bytes` | ios | thinning report, compressed size of `iPhone14,7` variant | bytes | — (n=1) | 2 | — | no |
| `app.size_uncompressed_bytes` | ios | thinning report, uncompressed (advisory) | bytes | — | — | — | no |
| `audio.callbacks`, `audio.last_callback_us`, `audio.max_callback_us`, `audio.over_budget`, `audio.xruns`, `audio.realtime` | both | Android counter tracks; iOS `engine-health.json` | various | stress | 50 (callback durations: and at least 100 µs; realtime: any change) | 10 % | **yes** |
| `audio.avg_us`, `audio.starvation`, `audio.clips_active` (feature 009, ABI revision 6) | both | Android counter tracks (the peak over the trace: the engine restarts its counters per stream session and the stress journey hands the device over); iOS `engine-health.json`, written while the transport still plays | µs, count, count | stress | 50 (`avg_us`: and at least 100 µs; `starvation`: any increase from 0, absolute limit 1) | 10 % (`starvation`: 1) | **yes** |
| `audio.input_overruns`, `audio.record_backlog`, `audio.take_alignment_ms`, `audio.monitor_delay_ms` (feature 010, ABI revision 7) | both | Android counter tracks (`audio.inputOverruns`, `audio.recordBacklog`, `audio.takeAlignmentMs`, `audio.monitorDelayMs`, the peak over the trace); iOS `engine-health.json` (`input_overruns`, `record_backlog`, `take_alignment_ms`, `monitor_delay_ms`, after `clips_active`; a file from a revision-6 build reads zeros) | count, frames, ms, ms | stress | `input_overruns`: any increase from 0, absolute limit 1; `record_backlog`: 50, and at least 2048 frames; `take_alignment_ms`, `monitor_delay_ms`: 20, and at least 2 ms (informational) | 10 % (`input_overruns`: 1; the ms rows 5 %) | **yes** |
| `audio.master_clips`, `audio.meter_frames_dropped` (feature 012, ABI revision 8) | both | Android counter tracks (`audio.masterClips`, `audio.meterFramesDropped`, the peak over the trace); iOS `engine-health.json` (`master_clips`, `meter_frames_dropped`, after `monitor_delay_ms`; a file from a revision-7 build reads zeros) | count, count | stress | `master_clips`: any increase from 0, absolute limit 1 (informational: the journeys never drive the sum over full scale); `meter_frames_dropped`: 20, and at least 2 (expected 0 on the reference devices) | 1; 5 % | **yes** |
| `audio.ioproc_p99_us` | ios | `xctrace` Audio Client + Audio Server instruments, system-wide: `audio-hal-client-io-cycle-ioproc` when it has rows, else the HAL server's `audio-hal-io-cycle-client` (the only table iOS 27.0.1 fills; the phone's only HAL client is the app) | µs | stress | 50 | 10 % | no (advisory) |

Simulator series: same keys, `kind: simulator`; gates inactive by default; `frames.*` and `audio.ioproc_*` absent (not produced on simulators); report marks the series as non-reference.

A gate limit is one of a percentage or an absolute value per key (`tracepulse.yml` chooses which). A percentage limit may carry a floor (`floor_abs`, which the detector's screening gate applies too): a change under the floor never flags and never screens in, however large the percentage of a tiny baseline (sections: 1 ms; audio callback durations: 100 µs). An absolute limit may scale (`gate_scale_pct`): it grows to that share of the baseline's magnitude when the baseline is large (frame overrun: 25 %, so ±5 ms of noise on the tuner journey's ~50 ms mode-switch frame is not a flag while +2 ms on a quiet series is); the record's `limit_abs` is the limit that applied. A percentage of a zero baseline is no limit: from 0 (xruns, over-budget callbacks) any rise past the floor flags, decided and recorded in absolute terms (`limit_abs` = the floor or 0).

A round's `build.type` names the build measured (`perf`/`release` on Android, `Perf`/`Release` on iOS). Only the measurement build compiles the tracing facade in, so a round of any other build publishes no `section.*` series (macrobenchmark reports an absent section as 0, which is not a value); the cache also ignores the sections of a record from before `build.type` existed in which not one section was hit.

A catalogue entry may carry `since` (an ISO instant): the first round its current source applies to. Values of that key from earlier rounds stay in their records but are not loaded into the cache, so a source change never shows as a step in the series.

Known mismatches recorded for the report's per-platform notes (spec FR-008): TTFD is app-declared on Android and OS-defined on iOS; frames are percentiles on Android and hitch-based on iOS; `memory.end_kb` is resident anonymous memory at the end of the journey on Android and a physical delta on iOS; app size is an APK on Android and a thinned IPA variant on iOS.
