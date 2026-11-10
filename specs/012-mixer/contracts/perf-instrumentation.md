# Contract: performance instrumentation for feature 012

Constitution (every feature instruments itself; feature 008's profile rule); spec
"Performance instrumentation declared by this feature", SC-003, SC-005; research R11, R12,
R14. The registration list follows feature 010's contract and `app/perf/README.md`
§"Adding a journey, a section, a counter"; section names keep one dot.

## Catalogue (`app/shared/engine/perf-catalogue.txt`, then `./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true`)

| Enum | Family | Name | Emitted from |
|---|---|---|---|
| `TIMELINE_MIXER_OPEN` | timeline | `timeline.mixerOpen` | the toolbar press (`toggleMixer`) → `traceFirstDraw` on the first strip's `LevelMeter` |
| `TIMELINE_MIXER_CLOSE` | timeline | `timeline.mixerClose` | the press or swipe → the sheet's `Hidden` anchor settled |
| `TIMELINE_MIXER_FRAME` | timeline | `timeline.mixerFrame` | `MixerSheet`'s `drawWithContent`: the one `MixTelemetry.read` and the strips' draws (one section per frame, the playhead-frame pattern) |
| `TIMELINE_FADER_DRAG` | timeline | `timeline.faderDrag` (animation) | `Fader`'s gesture span (press → release), the `TIMELINE_SCROLL` pattern |
| `TIMELINE_PAN_TURN` | timeline | `timeline.panTurn` (animation) | `PanKnob`'s gesture span |
| `TRANSPORT_MASTER_DRAG` | transport | `transport.masterDrag` (animation) | the master fader's gesture span |
| `ENGINE_PAN_APPLY` | engine | `engine.panApply` | C++ `tembra_mixer_set_pan` in `mixer_abi.cpp` (non-RT) |
| `AUDIO_MASTER_CLIPS` | counter | `audio.masterClips` | `AudioHealthTicker` from the health struct's `master_clips` |
| `AUDIO_METER_FRAMES_DROPPED` | counter | `audio.meterFramesDropped` | `AudioHealthTicker` from `MeterFrameCounter` (the sheet's frame loop: a frame later than twice the display period while metering is on) |

`PerfSectionCatalogueTest.familyKeyword` gains nothing. `EngineHealthJson`, the C health
struct (ABI 8), `AudioHealthTicker`, `catalogue._AUDIO` (`masterClips`: informational, flagged
on any increase in `stress` like `starvation`; `meterFramesDropped`: expected 0 on the
reference devices, 20 % gate with a floor of 2), `traces_android.AUDIO_COUNTER_KEYS`
(`audio.master_clips`, `audio.meter_frames_dropped`), `collect_ios._HEALTH_KEYS`,
`tests/fixtures/engine-health.json` and `specs/007-performance-pipeline/contracts/metric-catalogue.md`
gain the two counters. `trace_shim_test` expects 52 names.

## Anchors (`PerfAnchors` + `PerfAnchorsCatalogueTest` + the Android and iOS mirrors)

`timeline.toolbar.mixer`, `timeline.toolbar.overflow.mixer`, `mixer.sheet`, `mixer.handle`,
`mixer.strips`, `mixer.strip.fader.0`, `mixer.strip.pan.0`, `mixer.strip.solo.0`,
`mixer.strip.mute.0`, `mixer.strip.meter.0`, `mixer.strip.clip.0`, `mixer.strip.name.0`,
`mixer.master.fader`, `mixer.master.meter`, `mixer.master.clip`, `mixer.window.open`,
`trackSettings.pan`. The journey addresses strip *i* by replacing the trailing index.

## Journey `mixer` (same steps on both platforms)

1. tap `nav.item.projects`, wait `screen.projects`; 2. tap `projects.card.demo`, wait
`screen.timeline` ready; 3. tap `transport.play`; 4. tap `timeline.toolbar.mixer`, wait
`mixer.strip.meter.0`; 5. drag `mixer.strip.fader.0` down by a third of its height over one
second and back up over one second (the readout returns to its start); 6. where
`mixer.strip.pan.0` is not shown (a phone opens the sheet at its half height), tap `mixer.handle`
for the full form; drag `mixer.strip.pan.0` up by 60 dp over half a second and back; 7. tap `mixer.strip.solo.1`,
tap it again; 8. tap `mixer.strip.mute.2`, tap it again; 9. swipe `mixer.strips` to its end
and back; 10. drag `mixer.master.fader` down by a quarter and back; 11. tap `mixer.handle`
back to the half height where step 6 raised it, then tap `timeline.toolbar.mixer` (the sheet
closes), wait `mixer.sheet` gone; 12. tap
`timeline.toolbar.undo` until it disables (the document is unchanged across iterations;
the fader and knob returns are two steps each, the toggles four); 13. tap `transport.play`
(pause); 14. tap `transport.skipStart`. UiAutomator's idle waits (each click's acknowledgment and
`waitForIdle`) are shortened to 500 ms for this journey: the sheet's meters update their semantics ten
times a second while it is open, so the UI never reads idle and the default 10 s waits took an iteration to
75 s (the 2026-11-09 trial round timed out at 18 of 20). On iOS the same meters keep XCUITest's quiescence
waits long: on the iPhone 18 Pro simulator (the Mac loaded by two emulator lanes) an iteration took 96 s and the
Perf test plan's 30-minute per-test allowance stopped the journey at its 20th iteration with no step failing, so
`TembraPerf.xctestplan` now allows an hour per test (the pipeline's outer window is two hours).

Sections measured: `timeline.mixerOpen`, `timeline.mixerClose`, `timeline.mixerFrame`,
`timeline.faderDrag`, `timeline.panTurn`, `transport.masterDrag`, `engine.panApply`, plus
`transport.play`, `timeline.playheadFrame`, `timeline.projectSave`.

Files: Android `journeys/MixerJourney.kt`, `MixerJourneyBenchmark.kt`
(`PerfMetrics.journey("mixer")`, 20 iterations, `CompilationMode.Full`, `StartupMode.WARM`),
`PerfMetrics.sectionsOf("mixer")`, `BaselineProfileGenerator.journeys()`,
`ProfileComparisonBenchmark.mixer()`, `journeys/Anchors.kt`; iOS `Journeys/MixerJourney.swift`,
`MixerPerfTests.swift`, `Metrics.sections(of: "mixer")`, `engineSections`, `Anchors.swift`,
pbxproj entries; pipeline `tracepulse.yml` journeys, `config.DEFAULT_JOURNEYS`,
`catalogue.JOURNEYS_MEASURED`, `orchestrate_android.BENCHMARK_CLASS_OF`,
`collect_android._JOURNEY_OF_CLASS/_REQUIRED/_COMPARISON_METHODS`, `collect_ios._TEST_JOURNEYS`
and `order`, `report._JOURNEY_ORDER`, the journey enums in `schema/round-record.schema.json`
and `comparison.schema.json`, the pytest fixtures and assertions, the 007 metric-catalogue
contract and the README.

## Journey `stress` (extended)

After the existing playback-and-scroll phase and the recording phase, before
`devtools.writeHealth`: open the mixer, keep it open with the meters live for five seconds
while the lane scrolls and the metronome runs, drag the first fader once, close it. The
health dump then carries `audio.masterClips` and `audio.meterFramesDropped`. The 75 s
xctrace window is rechecked. On the 24-track project the list scrolls the added 25th track into view
and the first lane leaves the lazy list's composition (a `UiObject2` held from before the add throws
`StaleObjectException`, a fresh lookup finds nothing: the 2026-11-09 rounds), so the recording phase
scrolls the new track's lane on both platforms, and the mixer phase scrolls the list back to its top
before the sheet opens so the first lane stays visible above the half sheet; on Android the lane
scrolls through a stale-safe helper and the mixer phase runs under the same 500 ms idle waits as the
mixer journey (`withShortIdleWaits`).

## Baseline profile

Timeline gains main interactions: after the UI lands, `uv run tracepulse profile generate
--device pixel6` with the `mixer` journey in `BaselineProfileGenerator.journeys()`; both
profiles committed; `profile verify` and `profile compare --journeys mixer` into
`evidence/profile.md`.

## Labelled sessions after merge and the regression comparison

`uv run tracepulse run --device pixel6 --label 012-mixer --rounds 5` (artefacts built through
`gradle-run` with `-Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf`,
passed with `--no-build --apk … --test-apk …`), the same on `iphone14`, the Pixel 7 Pro and
the Pixel Tablet by serial, and the desktop emulator `emulator-5582` (feature 011's device
entry); then `sync` and `report --open`. Expected: the `mixer` journey on every device;
`timeline.mixerFrame` within one frame at 24 strips on the reference devices (the stress
phase's 24-track project); zero `audio.meterFramesDropped`, `audio.starvation` and
`audio.xruns` there; `audio.masterClips` zero in `mixer` (the journey never drives the sum
over full scale); the startup family within the gate. Every SUSPECT/CONFIRMED onset and
`above_limit` notice goes with its cause or fix into `evidence/perf-session.md`.

## Main-thread trace query (research R12 part 5)

From the Pixel 6 Pro round's Perfetto trace, the `perfetto-sql` query for slices on the main
thread longer than 8 ms whose span lies inside `journey.mixer`; the query text and the result
table go into `evidence/main-thread-audit.md`. Expected: none from the mixer's own sections;
any slice found is attributed (an existing cause is reported, a mixer cause is fixed).

## Tests

`PerfSectionCatalogueTest`, `PerfAnchorsCatalogueTest`, `trace_shim_test` (49),
`trace_poison_test` (the new RT files), `uv run pytest` (config, catalogue, collectors,
report with the seventh journey and the two counters), the iOS `FacadeSelfCheckTests`.
