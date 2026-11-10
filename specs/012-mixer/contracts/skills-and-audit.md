# Contract: the Chris Banes skill review and the main-thread audit

The owner's brief for this plan: "make sure nothing is running on the main thread" and
"check if the implementation works according to the Chris Banes best practices skill". Both
are deliverables with evidence files, not reading notes. Research R12, R13; constitution
Principles I, II, VIII.

## A. Skill contracts by package

| Package / files | Owning skill | Rules the implementation is held to |
|---|---|---|
| `core.project`: `Pan`, `FaderTaper`, `SetPan`, `Track`, `ProjectEditor` | `kotlin-api-design` | closed value and object types own their arithmetic and formatting; one edit type; member functions on the types they concern; no helper object for what a type can do itself |
| `engine`: `TimelineAudio`, `MixSnapshot`, both actuals, `FakeAudioEngine` | `kotlin-api-design`, `kotlin-concurrency-and-flow` | the seam grows member functions; none suspend because each is bounded (R4); `MixSnapshot` is a mutable scratch owned by one reader; the actuals allocate nothing per call |
| `ui.session`: `ProjectSession` (pan, metering, `mixTelemetry`) | `kotlin-concurrency-and-flow`, `kotlin-control-flow` | no new launch site; `setMetering` synchronous; the `when` over `ProjectEdit` exhaustive with the `SetPan` branches; cancellation untouched |
| `ui.timeline`: `TimelineViewModel`, `MixerUi`, `StripUi`, `MasterUi`, `TimelineCallbacks`, `TimelineKeyHandling` | `compose-state-and-effects`, `kotlin-control-flow` | screen state in the ViewModel's single `UiState`; immutable UI types; callbacks built once; exhaustive `when` over `MixerHeight`, `StripForm`, `MeterSource` |
| `ui.mixer`: `MixerSheet`, `MixerHandle`, `MixerStripRow`, `MixerStrip`, `MasterStrip`, `MixerLevelMirror` | `compose-component-design`, `compose-performance`, `compose-animations`, `compose-focus-navigation` | `modifier` first and on the root; primitives and callbacks; the per-frame read in `drawWithContent` only; offsets through `offset { }`; `key(id)` items; `animateTo` between anchors; `focusGroup` per strip |
| `ui.designsystem`: `Fader`, `PanKnob`, `LevelMeter`, `VuDial` | `compose-component-design`, `compose-performance`, `compose-focus-navigation`, `compose-animations` | reusable controls with semantics (`contentDescription`, `stateDescription`, `progressBarRangeInfo`, `setProgress`, `testTag`); draw-phase reads; keys through `onKeyEvent`; the double-tap return through an `Animatable`, none under reduce motion |
| `ui.tracksettings`, `ui.timeline.components` (`PanSlider`, `PanReadout`, `TrackHeader`, `TimelineToolbar`) | `compose-component-design` | the same component rules; the toolbar button through `TransportButton` |
| `ui.platform`: `WindowCapabilities` + bindings | `kotlin-api-design` | an interface with platform bindings (010's `AudioRouteMonitor` pattern), not expect/actual |
| `uiTest`, `commonTest` | `compose-ui-testing-patterns` | controlled state; scripted telemetry; injected interaction sources; frozen clock; semantics before pixels; keyboard tests declared per lane |
| every Gradle invocation | `gradle-run` | the wrapper, `--warning-mode all`, the ledger |

## B. The review procedure (one task per implementation phase)

1. List the files the phase changed (`git diff --name-only <phase-start>..HEAD`).
2. Route them through `using-chrisbanes-skills`: one owning skill per concern from table A;
   add a second skill only where it owns an independent decision in the same file.
3. Read each loaded skill's procedure and the focused reference it points to for the
   observed signal; walk the files against them.
4. Record every finding in `evidence/skill-review.md` as `| phase | file:line | skill | finding
   | fix (commit) or why not |`; fix each finding test-first in the same phase unless it is
   a spec-level choice, which is recorded for the owner.
5. The feature's last review covers the whole diff against `main` and the compose compiler
   report (`-PcomposeReports=true`: every new composable skippable; `MixerUi`, `StripUi`,
   `MasterUi` stable).

## C. The main-thread audit (research R12)

| Part | Deliverable | Pass condition |
|---|---|---|
| 1. Construction | `evidence/main-thread-audit.md` table: every engine call the mixer makes, its thread, its bound (atomic store / seqlock copy / none), and the calls it never makes on the main thread (`openTransport`, `closeTransport`, `arm`, `disarm`, `play`, `seek`, `recordStart`, `recordStop`, `inspect`, `buildOverview`, saves) | every row bounded; no new `withContext(blocking)` needed, none added |
| 2. Engine tests | `mixer_abi_test.cpp` (the `commands_` hold: `set_pan` returns within 1 ms), `mix_meter_test.cpp` (no allocation: the poison build) | green in `engineHostTest` |
| 3. Facade tests | `NativeAudioEngineMixerCostTest` (androidDeviceTest and iosTest): 1,000 `setPan`, `setTrack`, `readMix` on the main thread, median < 50 µs, max < 1 ms; `NativeAudioEngineBlockingDispatchTest` extended: the mixer setters do not dispatch | green on every Android target and simulator (quickstart §3) |
| 4. Debug guard | `StrictMode.ThreadPolicy` `detectAll().penaltyLog()` in `:androidApp` debug builds; logcat read for `StrictMode` during the device lane and a manual mixer session on the Pixel 6 Pro | zero `StrictMode` lines caused by the mixer; pre-existing ones reported in the evidence |
| 5. Traces | the Perfetto query of the labelled session (perf contract) | no main-thread slice > 8 ms from the mixer's sections; others attributed |

iOS: parts 1 and 3, plus the `mixer` journey's hitch metric (`XCTHitchMetric`, which read 0 for
the cold-launch work of 2026-10-21); `xctrace` cannot attach on iOS 27 (feature 007's record),
so no Instruments trace is promised.

## D. What "nothing on the main thread" means here

The main thread composes, lays out, draws and handles input; every mixer call it makes is
a bounded atomic or a 1.2 KB seqlock copy. Anything that can block (the engine's open, close,
arm, disarm, play, seek, record start/stop, inspect, overview, the file system) stays on
`EngineDispatchers.blocking` or the autosave's path as features 009 and 010 left it. A
finding that an older call still blocks the main thread is reported in the audit file and
fixed in this feature only if the mixer exercises it; otherwise it is handed to the owner.
