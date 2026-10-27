# Contract: performance instrumentation for feature 010

Constitution (every feature instruments itself; feature 008's profile rule); spec "Performance
instrumentation declared by this feature" and SC-011; research R19, R20; the owner's planning
brief ("add profiling to new functionality, check the performance and compare with previous
results for regressions"). The registration list below follows feature 009's contract and
`app/perf/README.md` §"Adding a journey"; section names keep one dot (`collect_ios._SECTION_RE`
accepts exactly one).

## Catalogue (`app/shared/engine/perf-catalogue.txt`, then `./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true`)

| Enum | Family | Name | Emitted from |
|---|---|---|---|
| `TIMELINE_ARM` | timeline | `timeline.arm` | `RecordingController.arm` → the meter's first draw (`traceFirstDraw` on `InputMeter`, the `traceFirstMeasure` helper extended to the draw phase) |
| `TRANSPORT_RECORD_START` | transport | `transport.recordStart` | `RecordingController.record` (the press, or the pre-roll's end observed by the 10 Hz mirror) → `CaptureSnapshot.framesCaptured > 0` |
| `TRANSPORT_COUNT_IN` | transport | `transport.countIn` (animation) | the pre-roll's span (`PreRoll` entered → `TakeStarted`) |
| `TIMELINE_TAKE_FRAME` | timeline | `timeline.takeFrame` | `TrackLane`'s live-take draw and `InputMeter`'s draw (one section per frame, the playhead-frame pattern) |
| `TRANSPORT_RECORD_STOP` | transport | `transport.recordStop` | `RecordingController.stop` → `TakeFinalised` after `persist` |
| `TIMELINE_MONITOR_TOGGLE` | timeline | `timeline.monitorToggle` | the toggle's press → `setMonitor` returned, or the refusal snackbar shown |
| `ENGINE_TAKE_ALIGNMENT` | engine | `engine.takeAlignment` | C++ `EngineCore::determineAlignment` (control thread, at arm and reopen) |
| `AUDIO_INPUT_OVERRUNS` | counter | `audio.inputOverruns` | `AudioHealthTicker` |
| `AUDIO_RECORD_BACKLOG` | counter | `audio.recordBacklog` | `AudioHealthTicker` |
| `AUDIO_TAKE_ALIGNMENT_MS` | counter | `audio.takeAlignmentMs` | `AudioHealthTicker` (from the capture snapshot) |
| `AUDIO_MONITOR_DELAY_MS` | counter | `audio.monitorDelayMs` | `AudioHealthTicker` (input + output latency while monitoring) |

`PerfSectionCatalogueTest.familyKeyword` gains nothing (no new family). `EngineHealthJson`,
the C health struct (ABI 7), `AudioHealthTicker`, `catalogue._AUDIO` (`inputOverruns`: expected
0, flagged on any increase, like `starvation`; `recordBacklog`: 50 % gate with a 2048-frame
floor; `takeAlignmentMs` and `monitorDelayMs`: 20 % gate with a 2 ms floor, informational),
`traces_android.AUDIO_COUNTER_KEYS` (`audio.input_overruns`, `audio.record_backlog`,
`audio.take_alignment_ms`, `audio.monitor_delay_ms`), `collect_ios._HEALTH_KEYS`,
`tests/fixtures/engine-health.json` and `specs/007-performance-pipeline/contracts/metric-catalogue.md`
gain the four counters. `trace_shim_test` expects 36 names.

## Anchors (`PerfAnchors` + `PerfAnchorsCatalogueTest` 46 + the Android and iOS mirrors)

`timeline.track.arm.0`, `timeline.track.meter.0`, `timeline.track.record.0`,
`transport.record`, `timeline.toolbar.monitor`, `timeline.toolbar.overflow.monitor`,
`metronome.sheet.countIn`, `timeline.clip.take`, `snackbar.microphone.settings`,
`dialog.stopRecording.confirm` (and `.cancel`). The compact-bar journey step taps
`timeline.track.record.0` when `transport.record` is absent.

## Journey `recording` (same steps on both platforms)

1. tap `nav.item.projects`, wait `screen.projects`; 2. tap `projects.card.demo`, wait
`screen.timeline` ready; 3. tap `timeline.toolbar.addTrack` (the new track is selected and
last); 4. tap `timeline.track.arm.<last>` (the runner has granted the microphone: Android
`pm grant … RECORD_AUDIO` in `orchestrate_android`, the simulator `simctl privacy grant
microphone`, the iPhone's `grantMicrophoneOnce`), wait `timeline.track.meter.<last>`; 5. tap
`timeline.toolbar.monitor` or the overflow entry, dismiss the snackbar if shown (no headphones on
the bench) or tap again to turn it off; 6. tap `transport.record` (or `timeline.track.record.<last>`),
wait until `transport.readout` passes "Count-in" and then "00:07" (one bar of count-in plus
four bars at 124 BPM ≈ 9.7 s); 7. tap the Record control again (the take ends, playback
continues), wait `timeline.clip.take` gone and the new clip present; 8. tap `transport.play`
(pause); 9. tap `timeline.toolbar.undo` twice (the take, then the track: the document is
unchanged across iterations); 10. tap `transport.skipStart`. The new track is the last one; its
index is the demo's track count, known to the journey.

Sections measured: `timeline.arm`, `transport.recordStart`, `transport.countIn`,
`timeline.takeFrame`, `transport.recordStop`, `timeline.monitorToggle`, `engine.takeAlignment`,
plus `timeline.waveform` (the adoption's overview), `transport.play`, `timeline.playheadFrame`,
`timeline.projectSave`.

Files: Android `journeys/RecordingJourney.kt`, `RecordingJourneyBenchmark.kt`,
`PerfMetrics.sectionsOf("recording")`, `BaselineProfileGenerator.journeys()`,
`ProfileComparisonBenchmark.recording()`; iOS `Journeys/RecordingJourney.swift`,
`RecordingPerfTests.swift`, `Metrics.sections(of: "recording")`, `engineSections`, pbxproj
entries; pipeline `tracepulse.yml` journeys, `config.DEFAULT_JOURNEYS`,
`catalogue.JOURNEYS_MEASURED`, `orchestrate_android.BENCHMARK_CLASS_OF`,
`collect_android._JOURNEY_OF_CLASS/_REQUIRED/_COMPARISON_METHODS`, `collect_ios._TEST_JOURNEYS`
and `order`, `report._JOURNEY_ORDER`, the journey enums in `schema/round-record.schema.json`
and `comparison.schema.json`, the pytest fixtures and assertions, the 007 metric-catalogue
contract and the README.

## Journey `stress` (extended)

After the existing playback-and-scroll phase and before `devtools.writeHealth`: add a track,
arm it, record a take without count-in while scrolling the lane for three seconds, stop the
take, undo twice. The health dump therefore carries the recording counters. The 75 s xctrace
window is rechecked.

## Baseline profile

`baseline-profile-coverage.txt` keeps its rules (Timeline is covered); after the UI lands,
`uv run tracepulse profile generate --device pixel6` regenerates both profiles with the
`recording` journey in `BaselineProfileGenerator.journeys()`; `profile verify` and `profile
compare --journeys recording` go into the feature's evidence.

## Labelled session after merge and the regression comparison

`uv run tracepulse run --device pixel6 --label 010-recording --rounds 5` (artefacts built
through `gradle-run` with `-Ptembra.perf=true :androidApp:assemblePerf
:perf-benchmarks:assemblePerf`, passed with `--no-build --apk … --test-apk …`), the same on
`iphone14` with the default Xcode 27.1, and the Pixel 7 Pro by serial; then `sync` and
`report --open`. The report's "last session" is the `010-recording` rounds; its regression
onsets (SUSPECT/CONFIRMED detections whose onset falls in the session) and the coarse gate's
`above_limit` notices against the median of the last five clean rounds are the comparison with
the pre-010 results the owner asked for. Expected: the `recording` journey on every device;
zero `audio.inputOverruns`, `audio.starvation` and `audio.xruns` on the reference devices; no
family above its gate in the worse direction; the startup family within the gate; a duration
step in `stress` at the label boundary (documented, as for 009). The session's report, the
profile comparison and any `above_limit` notice with its explanation go into
`specs/010-timeline-recording/evidence/` and the pull request.

## Tests

`PerfSectionCatalogueTest`, `PerfAnchorsCatalogueTest` (46), `trace_shim_test` (36),
`trace_poison_test` (the new RT files), `uv run pytest` (config, catalogue, collectors, report
with the sixth journey and the four counters), the iOS `FacadeSelfCheckTests`.
