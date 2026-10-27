# Contract: performance instrumentation for feature 009

Constitution (every feature instruments itself; feature 008's profile rule); spec "Performance
instrumentation declared by this feature"; research R16; the pipeline survey of 2026-10-13
(`app/perf/README.md` §"Adding a journey" is incomplete — this contract is the full list).

## Catalogue (`app/shared/engine/perf-catalogue.txt`, then regenerate `tembra_trace_names.h`)

| Enum | Family | Name | Emitted from |
|---|---|---|---|
| `TIMELINE_PROJECT_OPEN` | timeline | `timeline.projectOpen` | session `open()` (card tap → arrangement pushed) |
| `TIMELINE_WAVEFORM` | timeline | `timeline.waveform` | repository overview build per asset |
| `TIMELINE_PLAYHEAD_FRAME` | timeline | `timeline.playheadFrame` | `PlayheadOverlay` draw |
| `TIMELINE_CLIP_SPLIT` | timeline | `timeline.clipSplit` | `TimelineViewModel.split` |
| `TIMELINE_PROJECT_SAVE` | timeline | `timeline.projectSave` | repository `save` |
| `TIMELINE_LIBRARY_OPEN` | timeline | `timeline.libraryOpen` | `ProjectsViewModel` first index → first content |
| `TRANSPORT_PLAY` | transport (new `PerfFamily.TRANSPORT`) | `transport.play` | session play → `Playing` |
| `TRANSPORT_SEEK` | transport | `transport.seek` | session seek → engine returned |
| `ENGINE_PRIME` | engine | `engine.prime` | C++ `DiskStreamer::prime` (control thread) |
| `TRANSPORT_MASTER_POPUP` (D1, T146) | transport | `transport.masterPopup` | `MasterVolumePopup`: the card's first measure (`Modifier.traceFirstMeasure`, layout phase: the slider and the percentage laid out as the pop-up opens; composition and the platform window stay outside, as for every section) |
| existing | | `timeline.scroll`, `timeline.zoom` (animation), `engine.loadClip` (clip source open) | |
| `AUDIO_AVG_US` | counter | `audio.avgUs` | `AudioHealthTicker` |
| `AUDIO_STARVATION` | counter | `audio.starvation` | `AudioHealthTicker` |
| `AUDIO_CLIPS_ACTIVE` | counter | `audio.clipsActive` | `AudioHealthTicker` |

`PerfSectionCatalogueTest.familyKeyword` gains `TRANSPORT`. `EngineHealthJson`, the C health
struct (ABI v6), `AudioHealthTicker`, `catalogue._AUDIO` (gate 50 % / screening 10 %, `floor_abs`
100 µs for `avgUs`; absolute 0 expected for `starvation`, flagged on any increase),
`traces_android.AUDIO_COUNTER_KEYS`, `collect_ios._HEALTH_KEYS`, `tests/fixtures/engine-health.json`
and `specs/007-performance-pipeline/contracts/metric-catalogue.md` gain the three counters.
`Metrics.swift` gets `section(name, category:)` and the engine names use `"engine"`.

## Anchors (`PerfAnchors` + `PerfAnchorsCatalogueTest` count + the Android and iOS mirrors)

`NAV_PROJECTS = "nav.item.projects"`, `PROJECTS_ROOT = "screen.projects"`,
`PROJECTS_SEARCH = "projects.search"`, `PROJECTS_CARD_DEMO = "projects.card.demo"`,
`NAV_TIMELINE = "nav.item.timeline"`, `TIMELINE_ROOT = "screen.timeline"`,
`TIMELINE_ADD_TRACK = "timeline.toolbar.addTrack"`, `TIMELINE_SPLIT = "timeline.toolbar.split"`,
`TIMELINE_UNDO = "timeline.toolbar.undo"`, `TIMELINE_RULER = "timeline.ruler"`,
`TIMELINE_LANE_FIRST = "timeline.lane.0"`, `TIMELINE_CLIP_FIRST = "timeline.clip.first"`,
`TIMELINE_TRACK_MENU_FIRST = "timeline.track.menu.0"`, `TIMELINE_TRACK_SETTINGS = "timeline.track.menu.settings"`,
`TIMELINE_SETTINGS_CLOSE = "timeline.settings.close"`, `TRANSPORT_PLAY = "transport.play"`,
`TRANSPORT_SKIP_START = "transport.skipStart"`, `TRANSPORT_METRONOME = "transport.metronome"`,
`TRANSPORT_READOUT = "transport.readout"`, `DIALOG_STOP_AND_LEAVE_CONFIRM = "dialog.stopAndLeave.confirm"`;
design iteration D1 (T146) adds `TRANSPORT_MASTER_BUTTON = "transport.master.button"`,
`TRANSPORT_MASTER = "transport.master"` (the pop-up's slider on a phone, the inline slider from 600 dp),
`TIMELINE_OVERVIEW = "timeline.overview"` and `TIMELINE_OVERVIEW_WINDOW = "timeline.overview.window"` (36 anchors).
The pop-up is a window of its own on Android, so its card carries `anchorRoot()` like the dialogs and menus;
without it UiAutomator would not see the slider.
Waveform readiness is not an anchor (a zero-size node could be pruned from the accessibility
tree): the `screen.timeline` node carries `stateDescription = "ready"` once every visible clip's
overview is `Ready` (and `"loading"` before), which both UiAutomator (`By.res` + `UiObject2.text`/
content description) and XCUITest (`value`) can wait on.

## Journey `timeline` (same steps on both platforms)

1. tap `nav.item.projects`, wait `screen.projects`; 2. tap `projects.card.demo`, wait
`screen.timeline` with `stateDescription == "ready"` (10 s budget); 3. tap `transport.play`, wait
until `transport.readout` text passes "00:15" (eight bars at 124 BPM = 15.5 s); 4. tap
`timeline.ruler` at 25 % width (seek); 5. pinch in then out on `timeline.lane.0` (Android
`UiObject2.pinchOpen/pinchClose`, iOS `pinch(withScale:velocity:)`); 6. tap
`timeline.clip.first`, tap `timeline.toolbar.split`; 7. tap `timeline.track.menu.0`, tap
`timeline.track.menu.settings`, wait the settings form, tap `timeline.settings.close` (or back);
8. tap `transport.metronome` twice; 8a. (D1, phones: only when `transport.master.button` is present;
from 600 dp of bar width the slider is inline and the step is skipped) read the bounds of
`transport.readout`, tap `transport.master.button`, wait `transport.master` (the pop-up's
slider), drag it from its centre an eighth of its width to the right (Android `UiDevice.swipe`,
iOS `press(forDuration:thenDragTo:)`), tap the readout's centre as a real tap outside the pop-up
(Android `UiDevice.click`, iOS a coordinate from the app's origin), wait until `transport.master`
is gone, tap `timeline.toolbar.undo`: the drag is one undo step, so the volume returns to its
value before the step and the document is unchanged across iterations; 8b. (D1, only when
`timeline.overview` is present: the strip shows unless the device hid it from the overflow
menu's "Overview strip" entry, T151, and a hidden strip skips the step silently) drag
`timeline.overview.window` from its centre
by a quarter of its width to the right, then back from its new centre by the distance it moved
(it stops at the project's end; the scroll position is not stored); 9. tap `transport.play`
(pause); 10. tap `transport.skipStart`.

Sections measured: `timeline.projectOpen`, `timeline.waveform`, `transport.play`,
`transport.seek`, `timeline.zoom`, `timeline.scroll`, `timeline.clipSplit`,
`timeline.playheadFrame`, `engine.prime`, `engine.loadClip`, `transport.masterPopup` (D1; 0 on
Android and no interval on iOS where the bar shows the inline slider). The strip's window drag
runs inside the existing `timeline.scroll`.

Files: Android `journeys/TimelineJourney.kt`, `TimelineJourneyBenchmark.kt`,
`PerfMetrics.sectionsOf("timeline")`, `BaselineProfileGenerator.journeys()`,
`ProfileComparisonBenchmark.timeline()`; iOS `Journeys/TimelineJourney.swift`,
`TimelinePerfTests.swift`, `Metrics.sections(of: "timeline")`, pbxproj entries; pipeline
`config.DEFAULT_JOURNEYS`, `tracepulse.yml`, `catalogue.JOURNEYS_MEASURED`,
`orchestrate_android.BENCHMARK_CLASS_OF`, `collect_android._JOURNEY_OF_CLASS/_REQUIRED/_COMPARISON_METHODS`,
`collect_ios._TEST_JOURNEYS` and `order`, `report._JOURNEY_ORDER`, the pytest assertions, the
007 metric-catalogue contract and the README.

## Journey `stress` (extended)

Before the existing steps: open Projects → demo → Timeline, play with `transport.metronome` on,
scroll the lane for five seconds (animation), then the existing tuner/metronome cycle (which
pauses playback through the dialog: the journey taps "Stop and leave" by tag
`dialog.stopAndLeave.confirm`); the timeline journey undoes its split at once (the document persists between iterations) and restarts playback before it so the playhead is inside the first clip whatever the saved zoom; `devtools.writeHealth` is tapped right after the playback phase, while the transport still owns the device (a later write would carry the metronome's counters). The 75 s xctrace window
is rechecked against the new length and widened if the taps exceed 60 s.

## Baseline profile

`app/androidApp/baseline-profile-coverage.txt` gains `timeline Laudio/tembra/ui/timeline/TimelineScreenKt;`
and `projects Laudio/tembra/ui/projects/ProjectsScreenKt;` first (the host lane fails until the
profile is regenerated), then `uv run tracepulse profile generate --device pixel6` and both profile
files are committed; `profile verify` and `profile compare --journeys timeline` results go into the
feature's evidence.

## Labelled session after merge

`uv run tracepulse run --device pixel6 --label 009-timeline --rounds 5` (through `gradle-run`
artefacts with `--no-build --apk … --test-apk …`), the same on `iphone14` with
the default Xcode 27.1, and the Pixel 7 Pro by serial from the second Mac;
`sync`, `report`. Expected: a duration step in `stress` at the label boundary (documented), the
`timeline` journey present on every device, zero `audio.starvation` and `audio.xruns` on the
reference devices, no family above its gate in the worse direction.

## Tests

`PerfSectionCatalogueTest`, `PerfAnchorsCatalogueTest` (new count; 36 since D1), `trace_shim_test`
(the header's name count; 29 since D1), `trace_poison_test` (new RT
files), `uv run pytest` (config, catalogue, collectors, report with the fifth journey), the iOS
`FacadeSelfCheckTests` for the engine-category metric.
