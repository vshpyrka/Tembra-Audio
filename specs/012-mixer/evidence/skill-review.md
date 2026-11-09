# Feature 012 — skill review (contracts/skills-and-audit.md §B)

One review per implementation phase: the phase's files routed through `using-chrisbanes-skills`, walked against the
owning skill of each package (table A of the contract), every finding recorded with its fix or the reason it stays.
The final review (T122) covers the whole diff against `main` and the compose compiler report.

## Phase 2 — Foundational (T040)

Files: `engine/src/mixer/{pan_law.h, mix_params.h, recent_peak.h, mix_record.h, mix_meter.h, mix_meter.cpp,
mixer_abi.cpp, track_strip.*, master_bus.*}`, `engine/src/transport/transport_renderer.*`, `engine/src/core/{engine_core.*,
audio_health.*}`, `engine/include/tembra_engine.h`; `core.project/{Pan, SetPan, FaderTaper, FaderTick, Track,
ProjectEditor, EditRejection}`; `data.projects/{ProjectValidator, ValidationFailure}`; `engine/{TimelineAudio,
MixSnapshot, FakeAudioEngine, perf/AudioHealth, perf/AudioHealthPublisher}`, both `NativeAudioEngine` actuals and the
JNI shim; `ui.session/{ProjectSession, MixTelemetry}`; `ui.timeline/{TrackUi, TimelineViewModel (mapState), TimelineCopy}`.

Skills loaded: `kotlin-api-design` (the core types, the seam), `kotlin-concurrency-and-flow` (the session),
`kotlin-control-flow` (the `when`s over `ProjectEdit`, `EditRejection`, `ValidationFailure`).

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 2 | `core/project/Pan.kt` | kotlin-api-design | A single-field domain concept: `@JvmInline value class Pan(val percent: Int)`, serialised as the bare integer, owns its readout, clamp, snap and the engine mapping (`toLinear`). The range is not a constructor rule (the `Track.name` precedent): a document with a bad pan is reported by the validator, not thrown at decode. `inRange` exposes the rule to the editor and the validator. | by construction |
| 2 | `core/project/FaderTaper.kt`, `FaderTick.kt` | kotlin-api-design | The taper is an `object` that owns the curve in both directions; the scale marks are a small typed value (`FaderTick`) rather than `Pair<Float, String>` (primitive obsession), one type per file (Principle VII). | by construction |
| 2 | `core/project/ProjectEditor.kt:26,54` | kotlin-control-flow | `SetPan` joins the exhaustive `when` over `ProjectEdit` (no `else`, so a missing branch fails to compile); the out-of-range refusal is a new `EditRejection.PanOutOfRange` and `TimelineCopy.rejected` names it (the controls clamp first, so no message). | by construction |
| 2 | `ui/session/ProjectSession.kt` `coalesces` | kotlin-control-flow | The existing `else -> false` over the sealed `ProjectEdit` hid every non-coalescing case; replaced by the full case list so a future edit type must choose. | fixed in the phase |
| 2 | `ui/session/ProjectSession.kt` `setMetering`, `pushPan`, `resetTrackClip` | kotlin-concurrency-and-flow | No new launch site: the mixer's engine calls are synchronous and bounded (atomic stores, a seqlock copy), called from the edit path and the ViewModel's intents; the only coroutine the feature adds later is the sheet's 10 Hz mirror (`LaunchedEffect`). | by construction |
| 2 | `ui/session/ProjectSession.kt` `pushPans` | kotlin-api-design / spec | **Deviation from contracts/mixer-session.md**: the contract says "the session pushes every track's pan once after `setArrangement`". Pushing 64 `setPan` calls after every arrangement would change the call order every existing session test asserts and send 60-odd no-op stores per edit. The session instead remembers what it pushed per index (`pushedPans`, reset when the transport opens because the engine's open resets to the centre) and pushes only the indices whose pan differs. Same observable result for the engine; `pansArePushedAfterAnArrangementWhenTheIndicesMove` proves the index shift case. | recorded; the contract text is corrected in the final docs task |
| 2 | `ui/session/ProjectSession.kt` `setMetering` | spec | **Deviation from contracts/mixer-session.md**: the contract resets the holder count "when the transport closes or the project changes". A sheet that stays open across a Tuner detour or a project switch would then show dark meters until closed and reopened. The count stays with the UI that holds it and `pushEngine` re-pushes `setMetering(true)` on every rebind while a holder is open (`meteringIsOnWhileAnyHolderIsOpenAndIsPushedAgainWhenTheTransportRebinds`). | recorded; contract corrected in the docs task |
| 2 | `engine/include/tembra_engine.h` `tembra_audio_health` | contract arithmetic | **Correction to contracts/engine-c-abi-v8.md and data-model.md §3**: the contract says `sizeof == 60`, `offsetof(master_clips) == 56`. Revision 7's fields end at byte 52 and the struct is padded to 56 for the `uint64_t`; `master_clips` fills that padding, so `offsetof == 52` and `sizeof` stays 56 (a 60-byte size is impossible under 8-byte alignment). The tests assert 56/52; no reader depends on the raw layout (JNI arrays, cinterop fields). | fixed in the phase |
| 2 | `shared/engine/perf-catalogue.txt`, `trace_shim_test.cpp`, `PerfSectionCatalogueTest` | contract arithmetic | The plan's "49 names" miscounted: 43 existing + 7 sections + 2 counters = 52 (37 sections, 15 counters). | fixed in phase 1 |
| 2 | `engine/src/mixer/mix_meter.*`, `transport_renderer.*` | Principle I | The master clamp count moved from the meter to the renderer (`masterClips_`, beside `starvation_`) so the health counter counts whether or not the sheet is open; the meter keeps only the master's latch. `mix_meter.cpp` joins `TEMBRA_RT_SOURCES`; the meter allocates nothing after construction, never traces, and does no work while metering is off (`meterMeasureCalls` stays put in the renderer test). | by construction |
| 2 | `engine/src/core/engine_core.cpp` `setTransportTrack` | ABI compatibility | Revision 7's `tembra_transport_set_track` rejected an index past the arrangement's track count; the lock-free path keeps that rule through an atomic `mixTrackCount_` written under `commands_` by the arrangement, read without it by the setter, so `engine_core_state_test`'s "one track only" assertion holds unchanged. The revision-8 `tembra_mixer_set_*` accept any index under 64, as their contract says. | by construction |
| 2 | `NativeAudioEngine.ios.kt` `audioHealth` | pre-existing gap | The iOS actual never mapped revision 7's `input_overruns`, `record_backlog` and `input_xruns` into `AudioHealth` (they read 0 on iOS since feature 010). Mapped together with `master_clips`. | fixed in the phase (one line each) |
| 2 | `engine/src/mixer/track_strip.*` | Principle I / FR-031 | The strip re-targets its two ramps when the source kind changes between blocks (mono ↔ stereo), so a clip boundary never jumps; the centre of the mono law yields `1.0f` exactly, so a centred mono track renders bit-exact to revision 7 (`track_strip_test`). | by construction |
| 2 | `NativeAudioEngineMixerCostTest` (both lanes) | contract §C part 3 | The simulator run read a median of 0.08 µs and one 1.08 ms outlier among 3,000 calls: a preemption of the test thread, not an engine wait (a wait behind play's prime or record-stop's drain is tens of milliseconds). The bound became: median < 50 µs, 99th percentile < 1 ms, worst call < 10 ms, and a run whose worst call passes 1 ms is measured once more. | fixed in the phase; recorded for the audit file |
| 2 | `NativeAudioEngine.ios.kt` `readMix` | cinterop | The C ABI's `float peak[64][2]` arrives without an indexer on the nested array; the 128 floats are read flat through `reinterpret<FloatVar>()` in the snapshot's own order (track × 2 + side). | fixed in the phase |

Lanes at the end of the phase: `engineHostTest` 291 Catch2 cases green, zero warnings (2026-11-05); `checkAndroidHost`
recorded in `../screenshots/NOTES.md` once green.

## Phase 3 — User Story 1, the sheet and the faders (T056)

Files: `ui.mixer/{MixerHeight, StripForm, MeterSource, MixerAnchors, MixerUi, StripUi, MasterUi, MixerSheetValue,
MixerSheet, MixerHandle, MixerStripRow, MixerStrip, MasterStrip, MixerStripDefaults, MixerSheetDefaults, MixerCopy}`,
`ui.designsystem/{Fader, FaderScale, FaderDefaults}`, `ui.timeline/{MixerViewState, LanesBox, TimelineViewState,
TimelineUiState, TimelineCallbacks, TimelineViewModel, TimelineScreen, TimelineScreenContent, TimelineKeyHandling,
components/TimelineToolbar}`, `ui.shortcuts/{ShortcutAction, ShortcutCatalogue, ShortcutCopy, ShortcutDispatcher,
TimelineShortcuts}`; tests `FaderTest`, `MixerStripTest`, `MixerSheetTest`, `MixerAnchorsTest`, and the additions to
`TimelineViewModelTest`, `TimelineScreenTest`, `TimelineToolbarTest`, `TimelineKeyboardTest`, `RecordingTargets`.

Skills loaded through `using-chrisbanes-skills`: `compose-component-design`, `compose-performance`,
`compose-state-and-effects`, `compose-animations`, `compose-focus-navigation`, `compose-ui-testing-patterns`; plus the
project's `navigation-event` skill for the Back route.

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 3 | `designsystem/Fader.kt` | compose-component-design | `modifier` is the first optional parameter and lands on the root `Box`; the value domain is a typed `FaderScale` (`Gain`, `Percent`) rather than Boolean shape flags or a bag of step/range/unity primitives; the strip and the master pass the semantics strings in. | by construction |
| 3 | `designsystem/Fader.kt` `semantics` | contract text | **Deviation from contracts/mixer-ui.md**: the contract names `Role.Slider`; Compose has no such role (Button, Checkbox, Switch, RadioButton, Tab, Image, DropdownList, ValuePicker, Carousel). The fader carries `progressBarRangeInfo` + `setProgress` in the value domain, `contentDescription` and `stateDescription`, as Material's slider and feature 009's `GainReadout` do, and no role. | recorded; the contract text is corrected in the docs task |
| 3 | `designsystem/Fader.kt` drag | compose-performance / R3 | The drag reads nothing from composition per frame: the gesture coroutine computes the throw from the pointer's delta and the start value and calls `onValue`; the drawn geometry comes from `value` in `drawBehind`; the scale marks are measured once per scale and text style (`remember`). The handle's hover and focus colours are plain reads of the interaction source. | by construction |
| 3 | `designsystem/Fader.kt` double tap | compose-animations | An `Animatable` drives the return to unity (an imperative, interruptible motion), `tween(motion.standardDuration, motion.standardEasing)`; reduce motion skips the animation and jumps, which `FaderTest.underReduceMotionTheDoubleTapJumps` proves. | by construction |
| 3 | `mixer/MixerSheet.kt` | compose-state-and-effects | The sheet is a plain content composable over `MixerUi` and callbacks; the `AnchoredDraggableState` is `remember`ed and synchronised from `MixerUi.height` through `LaunchedEffect(target, anchors, reduceMotion)`; a settled drag writes back through `snapshotFlow { settledValue }` (the first emission, the initial value, dropped). `rememberUpdatedState` keeps the collector on the latest `ui` and callbacks. | by construction |
| 3 | `mixer/MixerSheet.kt` `layout { }` | compose-performance | The drag offset is read in the layout phase only: the sheet's visible height follows it so the strips fit what shows, and nothing recomposes during a drag. Composition reads `currentValue` and `isAnimationRunning` only to decide whether the sheet is composed at all (three recompositions per open or close, none per frame). | by construction |
| 3 | `mixer/MixerSheet.kt` anchors | compose-state-and-effects | A new `DraggableAnchors` (a rotation, a resized window) is applied in a `LaunchedEffect` keyed on it, never in composition (`updateAnchors` writes the state's offset); the first anchors go into the constructor so the first frame has a position. | by construction |
| 3 | `mixer/MixerSheet.kt` Back | navigation-event / zero warnings | Compose UI 1.12.2 deprecates `androidx.compose.ui.backhandler.BackHandler` ("Use NavigationEventHandler instead"), a warning the gate forbids; the sheet registers a `NavigationBackHandler` with its own `rememberNavigationEventState(NavigationEventInfo.None)` while open. It composes inside the Timeline entry, after the shell's `NavDisplay` registered its own, so it takes Back first. `navigationevent-compose` is an API dependency of `navigation3-ui` on every target, so no catalogue entry was needed (the `ui-backhandler` entry tried first was reverted). | fixed in the phase |
| 3 | `mixer/MixerAnchors.kt` `MinHalf` | spec arithmetic | **Deviation from spec FR-018 / research R2's budget**: R2 sized the half sheet at 240 dp as the strip alone (20 + 36 + 20 + 24 → a 140 dp fader) and left out the 32 dp handle row; with it the compact strip's fader would fall to 112 dp, under FR-018's 120 dp floor. The half height's floor is 256 dp ("at least 240 dp" holds), which gives the fader 128 dp; `MixerStripTest.theTargetsHaveTheirSizes` measures it at 256 − 32 dp. | fixed in the phase; recorded for the docs task |
| 3 | `mixer/MixerStrip.kt` Solo/Mute | constitution Principle IV | The toggles are 36 dp as research R2 and the design (`tablet-split-v2`) size them: two side by side fill the 72 dp strip, and a 44 dp pair would need 88 dp of width or 88 dp of height, which the half sheet's budget does not have. The 44 dp rule holds for the fader (44 dp wide, the whole rail is the target), the handle row and the toolbar button. The number and the name are 20 dp rows (the strip's own budget; R2). | recorded as a deliberate R2 choice; the review audit (T121) decides whether the owner wants the 44 dp pair and a taller half sheet |
| 3 | `mixer/MixerStrip.kt` `focusGroup` | compose-focus-navigation | Composition order gives Solo → Mute → fader without `focusProperties`; `MixerStripTest.tabWalksSoloMuteThenTheFader` drives it with Tab on the lane that declares keyboard mode. Focus restoration across a layout rebuild follows the fader's `focusIdentity(testTag)` (feature 011's pattern). | by construction |
| 3 | `mixer/MixerHandle.kt` | compose-focus-navigation | The handle is a focusable `clickable` (Enter and Space toggle the height) rather than a key handler of its own; disabled (unfocusable) where the sheet has one anchor. | by construction |
| 3 | `timeline/TimelineViewModel.kt` `toggleMixer` | kotlin-concurrency-and-flow | Opening and closing are synchronous view-state updates plus one `setMetering` store; the metering count follows the sheet (released in `onCleared`, carried across a project switch), so no holder leaks when the ViewModel dies with the sheet open. | by construction |
| 3 | `timeline/TimelineViewModel.kt` `chrome` | kotlin-concurrency-and-flow | The lanes box joins the chrome `Triple` so `uiState` still combines five flows (the plain `combine` overload's limit). | by construction |
| 3 | `uiTest/*` | compose-ui-testing-patterns | Every test drives plain content composables with held state and callbacks; the sheet tests use real swipes (slow ones where a fling's velocity would decide the anchor), the Back route is proven through the overlay stack (the shell's Escape/⌘. route) because no lane can raise a platform Back event in `runComposeUiTest`; hover and focus go through an injected `MutableInteractionSource` and `requestFocus`, with one pixel probe each. | by construction |
| 3 | `engine/src/transport/transport_renderer.cpp` `silence()` | Principle I / emulator lane | The Pixel 9 Pro Fold lane (the first Android run of the Phase 2 device tests) caught the renderer keeping each strip's last gains across transport sessions: a new session's first arrangement ramped from the old mix over 10 ms instead of snapping to the stored pan, and the ramp's first samples sat in the 64 ms peak window and the 300 ms rms window for seconds. `silence()` now clears `stripSet_` and the monitor's track, so the next session snaps; `transport_renderer_test` has the case. The device tests pan before the arrangement lands, the session's own order. | fixed in the phase (292 Catch2 cases) |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`.

## Phase 4 — User Story 2, the meters (T066)

Files: `ui.designsystem/{LevelMeter, LevelMeterDefaults, LevelMeterCopy, MeterReading, MeterSample, MeterLevel, VuDial,
VuScale}`, `ui.mixer/{MixerLevelMirror, MeterFeed, MixerSheet, MixerStrip, MixerStripRow, MasterStrip, MixerCopy}`,
`ui.perf/{MeterFrameCounter, AudioHealthTicker, AudioHealthWriteButton, EngineHealthJson}`, `ui.timeline/{TimelineViewModel,
TimelineScreen, TimelineScreenContent}`; tests `LevelMeterTest`, `VuDialTest`, `MeterFrameCounterTest`,
`ScriptedMixTelemetry`, and the additions to `MixerSheetTest`, `MixerStripTest`, `TimelineViewModelTest`,
`EngineHealthJsonTest`, `AudioHealthWriteButtonTest`.

Skills: `compose-performance` (the draw path and the mirror), `compose-component-design`, `compose-ui-testing-patterns`.

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 4 | `mixer/MixerSheet.kt` `drawWithContent` | compose-performance / R6 | One telemetry read per frame at the sheet root, inside `timeline.mixerFrame`, into one shared `MixSnapshot`; every meter and dial reads that snapshot in its own `drawBehind` through the frame tick on a layer of its own. `MixerSheetTest.theRootReadsTheTelemetryOncePerFrameForAnyNumberOfStrips` counts the reads with eight strips. No snapshot state is written in draw; the per-frame path recomposes nothing. | by construction |
| 4 | `designsystem/LevelMeter.kt`, `VuDial.kt` | compose-component-design | **Deviation from contracts/mixer-ui.md**: the contract's `LevelMeter(telemetry, index, mirror, …)` would make every meter a telemetry reader; R6 and T059 want one read per frame, so the meter takes a `MeterReading` (a strip's or the master's slice of the shared snapshot, or the armed track's capture) and the dial the shared snapshot and an index. `MeterLevel` replaces the data model's `StripLevel` name: the design-system meter owns the semantic value, the mixer's mirror produces it. | recorded; contract and data model corrected in the docs task |
| 4 | `mixer/MixerLevelMirror.kt` | compose-state-and-effects | One `LaunchedEffect` per sheet, keyed on open and the armed index, 10 Hz, copying from the shared snapshot (no engine read of its own); one `MutableState<MeterLevel>` per strip, written only when the value changes, read by the meter alone, so a level change recomposes one leaf at most ten times a second and the strip never (FR-015). | by construction |
| 4 | `mixer/MeterFeed.kt` | compose-component-design | The four draw-phase handles the strips need (snapshot, tick, mirror, capture telemetry) travel as one object the sheet owns, not four parameters repeated through the row and the strip. | by construction |
| 4 | `perf/MeterFrameCounter.kt` | perf contract | "A frame later than twice the display period" is measured against the shortest interval seen in the run (the display period learnt, floored at 4 ms so a repeated draw of one frame is not a period); a repeated draw of the same frame time is ignored; the sheet's close pauses the run. Published by the ticker and written into `engine-health.json` beside `master_clips`. The write button and its test take their own counter, since the shared one carries the frames the frozen-clock sheet tests drop. | by construction |
| 4 | `uiTest/mixer/MixerSheetTest.kt` | compose-ui-testing-patterns | T059's "`TIMELINE_MIXER_FRAME` wraps the read" cannot be asserted in a lane: `PerfBuild.PERF_ENABLED` is generated false outside `-Ptembra.perf=true`, so `PerfTrace.section` is inert and no sink sees it. The wrap is by construction (one call site); the perf build's traces (T109) show the section. | recorded |
| 4 | `designsystem/VuDial.kt` | spec FR-013 | The needle follows the engine's 300 ms rms, eased by the 30 ms smoother between frames; the "no twitch on single peaks" rule is the rms window's, proven at the engine (`mix_meter_test`), and the dial's test proves the easing (two frames after a jump the needle is in flight). | by construction |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`.

## Phase 5 — User Story 3, pan (T075)

Files: `ui.designsystem/{PanKnob, PanKnobDefaults}`, `ui.tracksettings/{PanSlider, TrackSettingsUiState, TrackSettingsCallbacks,
TrackSettingsViewModel, TrackSettingsScreen, TrackSettingsForm, TrackSettingsCopy}`, `ui.timeline/{TimelineViewModel,
TimelineScreen, components/GainReadout, components/TrackHeader}`, `ui.mixer/MixerStrip`; tests `PanKnobTest` and the
additions to `TrackSettingsScreenTest`, `TrackHeaderTest`, `MixerStripTest`, `TimelineViewModelTest`,
`TrackSettingsViewModelTest`.

Skills: `compose-component-design`, `compose-focus-navigation`, `kotlin-control-flow`.

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 5 | `designsystem/PanKnob.kt` | compose-component-design | The fader's shape: `modifier` first among the optionals on the root, primitives and callbacks, the readout from `Pan.readout` (the core type owns it), the semantics in percent. The drag is the fader's (`awaitFirstDown(requireUnconsumed = false)` under the tap detector, the vertical slop, `drag`), the release snaps within 5 % through `Pan.snapToCentre`, every gesture settles once; `PerfTrace.animation(TIMELINE_PAN_TURN)` spans the drag. | by construction |
| 5 | `designsystem/PanKnob.kt` keys | compose-focus-navigation | Arrows 1 %, Page 10 %, Home the centre, consumed only when handled; the knob composes between the dial and the toggles, so the strip's focus group walks knob → Solo → Mute → fader by composition order in the full forms and Solo → Mute → fader in the compact one. **Deviation from contracts/mixer-ui.md** "Solo → Mute → pan → fader": the knob sits above the toggles as spec FR-002 orders the strip (number, meter, VU, pan, Solo, Mute, fader, name), and the focus order follows the eye. | recorded; contract corrected in the docs task |
| 5 | `timeline/components/TrackHeader.kt`, `GainReadout.kt` | spec FR-007 / feature 010 regression | **Deviation from contracts/mixer-ui.md and data-model.md**: the contract's `PanReadout` after the `GainReadout` in the expanded header's second row cannot exist. `TrackHeaderTest` measured the row: since feature 010 put the arm toggle there, the gain readout has 16 dp of the 184 (three 36 dp toggles, the 44 dp chevron and the gaps take 168), so its text was already clipped on every expanded window, and a second readout has no room at all. The fix keeps every control at its size: the arm toggle moves to the first row in the instrument chip's place (the label stays in the track's description and the settings form), which gives the readout its 56 dp, and the pan reads under the gain inside that readout (`GainReadout(caption = pan.readout)`, "−2.4dB" over "L 35", announced as "−2.4dB, L 35"). Pan is edited by the strip's knob and the settings slider; the header shows it, as FR-007 requires. `PanReadout` was not shipped. | fixed in the phase; recorded for the docs task and the owner's review (T124: the chip's departure from the expanded header) |
| 5 | `tracksettings/PanSlider.kt` | compose-component-design | A `TembraSlider` over −100 … 100 with 199 steps (whole percent, Material's arrow step 1 %), the readout text after it; `onValueChangeFinished` is the settle. The row mirrors the gain row of feature 009. | by construction |
| 5 | `tracksettings/TrackSettingsCallbacks.kt` | kotlin-api-design | `onPan` and `onPanSettled` default to no-ops so the two existing construction sites (a test fixture and the scrollbar test) keep compiling; the production wiring names them. | by construction |
| 5 | `timeline/TimelineViewModel.kt` `setPan` | kotlin-control-flow | One line through `edit(SetPan)`; the session's `coalesces` (Phase 2) folds a run on one track into one undo step and `gestureSettled` closes it; `TimelineViewModelTest.aKnobTurnIsOneUndoStep` proves both ends. | by construction |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`.

## Phase 6 — User Story 4, mute, solo and the master (T081)

Files: `ui.mixer/{MixerStrip, MasterStrip, MixerStripRow}`, `ui.designsystem/Fader`, `ui.timeline/components/{MuteSoloButton,
TrackList}`; tests `MasterStripTest` and the additions to `MixerStripTest` and `TimelineViewModelTest`.

Skills: `compose-state-and-effects`, `kotlin-control-flow`.

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 6 | `mixer/MixerStrip.kt` Solo / Mute | compose-state-and-effects | No state of its own: the toggles render `StripUi.soloed` / `muted`, the same document fields the headers render, and fire the same `onToggleSolo` / `onToggleMute` (FR-010); `TimelineViewModelTest.stripAndHeaderStatesNeverDisagree` walks 200 random toggles and compares every `TrackUi` with its `StripUi`, audibility included. | by construction |
| 6 | `mixer/MixerStrip.kt` `onKeyEvent` | compose-focus-navigation / kotlin-control-flow | "M" and "O" are handled on the strip's focus group for its own track, unmodified keys only, consumed when handled; the root's catalogue handler still serves the selected track when focus is elsewhere. A `when (event.key)` with an `else` that returns false: an open key space, so the fallback stays. | by construction |
| 6 | `timeline/components/MuteSoloButton.kt` | compose-component-design | The mixer's "Solo lit in lavender, Mute dim" needs a readable letter on a dim fill: one optional `checkedContent` colour, `scrim` by default, keeps feature 009's headers unchanged. The dim fill is the lavender at a third. | by construction |
| 6 | `designsystem/Fader.kt` `section` | perf contract | The master's drag is `transport.masterDrag`, a strip's `timeline.faderDrag`: the section is a parameter with the strip's as its default, so the master passes its own. | by construction |
| 6 | `timeline/components/TrackList.kt` | compose-state-and-effects / FR-023 | A track selected while its row is out of view (a strip's number or name) scrolls the lane into view through one `LaunchedEffect(selectedTrack)` keyed on the selection, animated; a header tap selects a visible row and moves nothing. No request state was added: the selection already is the request. The strip row's own first-composition scroll (Phase 3) became an animated one. | by construction |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`.

## Phase 7 — User Story 5, every window, pointer, keyboard and screen reader (T094)

Files: `ui.mixer/{MixerStrip, MasterStrip, MixerStripRow, MixerSheet, MixerHandle, MixerUi, StripForm, MixerKeyHandling,
StripKeyTargets, MasterKeyTargets}`, `ui.designsystem/{Fader, PanKnob}`, `ui.shortcuts/{ShortcutScope, ShortcutAction,
ShortcutCatalogue, ShortcutCopy, ShortcutDispatcher, ShortcutTargets, MixerShortcuts}`, `ui.timeline/TimelineViewModel`;
tests `MixerSemanticsTest` and the additions to `MixerSheetTest`, `MixerStripTest`, `FaderTest`, `PanKnobTest`,
`MixerAnchorsTest`, `RecordingTargets`.

Skills: `compose-focus-navigation`, `compose-component-design`, `compose-ui-testing-patterns`, `kotlin-api-design`,
`kotlin-control-flow`.

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 7 | `shortcuts/ShortcutCatalogue.kt` Mixer scope, `mixer/MixerKeyHandling.kt` | compose-focus-navigation / FR-028, FR-029 | Phase 6's ad-hoc `when (event.key)` on the strip became nine catalogue entries in a `ShortcutScope.Mixer` (feature 011's rule: every key the app answers is one catalogue entry, so the shortcut sheet lists it). The strip's focus group matches the catalogue and dispatches to `StripKeyTargets` (the master to `MasterKeyTargets`); the fader and the knob consume their own arrows, Page and Home first, so the strip's handler serves the keys that reach it from Solo, Mute or the name. Consumes exactly the matched events. | by construction |
| 7 | `shortcuts/ShortcutCatalogue.kt` | kotlin-api-design / `ShortcutCatalogueTest.noChordIsListedTwiceWhereItCouldFire` | The Mixer scope's `M`, `O`, Left, Right and Home are Timeline chords too. The catalogue's invariant pairs Everywhere with one scope at a time, so it holds; at run time the strip's handler is nearer the focused control than the Timeline root's and consumes first, so neither fires twice. Recorded because the shortcut sheet shows `M` under both groups: that is the design (the Timeline's `M` mutes the selected track from anywhere, the Mixer's the focused strip). | by construction |
| 7 | `mixer/MixerStrip.kt` `StripLabel` | compose-focus-navigation / contracts/mixer-ui.md "Keyboard" | The number and the name are `selectable` (a tap or a screen reader selects the track), which made them Tab stops: two per strip the contract does not list. `focusProperties { canFocus = false }` before the `selectable` keeps the click and the semantics and takes them out of traversal; `MixerSemanticsTest.tabWalksTheHandleEachStripsControlsThenTheMaster` walks handle → knob, Solo, Mute, fader per strip → master. | by construction |
| 7 | `mixer/MixerStrip.kt` Tab order in the full form | compose-focus-navigation | The contract reads "Solo, Mute, pan, fader"; the composition's order is knob, Solo, Mute, fader, because research R2 draws the knob above the toggles. Default traversal follows the layout top to bottom; encoding the contract's order with `focusProperties { next }` would make Tab jump upward over the knob. The layout order is kept; the contract line is corrected with the docs task (T120). | deviation recorded, by design |
| 7 | `designsystem/Fader.kt`, `designsystem/PanKnob.kt` | compose-component-design | `TembraTooltip` wraps each control with the control's own `interactionSource`, as `TransportButton` does, so hover and keyboard focus show the description; `hoverArea` sits before the drawing modifiers, so the layer covers the whole hit area (the rail's empty ends, the arc's gap), rounded like the control. The caller's modifier stays on the control's own Box (the tooltip's box only wraps it): the strip's `fillMaxHeight()` reaches the fader, not the wrapper. `pointerHoverIcon(Hand)` and the wheel were there since Phases 3 and 5; the handle gained the hand. | by construction |
| 7 | `mixer/MixerStrip.kt` root | compose-component-design / feature 011 FR-012 | The secondary click reuses 011's `onSecondaryClick` + `PointerMenuAnchor` + `TrackMenu`: the strip is the menu item and a zero-size box at its top-left corner is the anchor the pointer offset is measured from (the strip has no ⋮ button). The root became a `Box` holding the controls' `Column` (`StripBody`) and the menu, so the anchor takes no room in the column's spacing. `MixerStripTest.aSecondaryClickOpensTheTrackMenuAtThePointer` checks a menu corner within 24 dp of the click; `MixerUi.canReorder` (false during a take) disables move and delete as the headers' menu does. | by construction |
| 7 | `mixer/StripForm.kt` `of()` | kotlin-control-flow / FR-025 | A subjectless `when` over two thresholds: the dial from 480 dp, the knob from 292 dp (handle 32, padding 8, number 20, knob 40, toggles 36, name 20, four gaps 16 and the 120 dp throw), compact below. The 440 dp and 280 dp cases of T082 pin the order. | by construction |
| 7 | `mixer/MixerStrip.kt`, `mixer/MasterStrip.kt` labels | compose-component-design / FR-026 | The number, the name and the master's two labels are `heightIn(min = 20.dp)`, so a larger font grows them instead of clipping; the fader row keeps `weight(1f)` and shrinks. The strip's width stays 72 dp at every font (a `widthIn(min)` was tried and dropped: inside a `LazyRow` item the name would widen the strip to its full single-line width instead of ellipsising). `MixerSheetTest.theLargestFontClipsNothingAndEllipsisesTheName` checks every control inside the strip and the name's layout truncated. | by construction |
| 7 | `uiTest/mixer/MixerSheetTest.kt` `relayout` | compose-ui-testing-patterns | The window change is driven through state (`shownLayout`, `shownWidth`, `shownLanes`) while the sheet stays composed, as a rotation or a resized window does in the app; the assertions read semantics and bounds (the sheet's top, the first visible strip), never internals. Skia's text layout on iOS reports no `isLineEllipsized`: the ellipsis check reads `hasVisualOverflow` with the layout's width inside the strip. | by construction |
| 7 | `uiTest/mixer/MixerSemanticsTest.kt` Escape | compose-ui-testing-patterns | Escape is proven through the user's input model: the sheet under a `shellKeyHandling` root whose `ShellShortcuts.closeOverlay()` is the overlay stack's `closeTop()`, a focused fader, `pressKey(Key.Escape)`; calling the stack directly is `MixerSheetTest.theShellsCloseOverlayActionClosesTheSheet`'s job. | by construction |
| 7 | `shortcuts/MixerShortcuts.kt`, `mixer/StripKeyTargets.kt` | kotlin-api-design | One interface per scope, as 011's `TimelineShortcuts`; the strip's implementation is built from `StripUi` and `TimelineCallbacks` and remembered per strip, so the dispatcher stays a pure `when` over actions and the strip owns nothing the ViewModel does not already hold. Each call ends with the matching `…Settled`, one undo step per key (FR-005). | by construction |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`. Open after this phase: T088's emulator lane, the
T089/T090 captures, the T091 design comparison, the T092 desktop-emulator pass, T093 TalkBack and VoiceOver (owner).

## Phase 8 — User Story 6, many tracks stay smooth (T099)

Files: `ui.mixer/{MixerStripRow, MixerStrip, MeterFeed}`, `ui.projects/{StressProjectRequest, StressProject,
StressProjectSeeder}`, `ui/App`, `ui.di/{AppGraph, AppBindings}`, `data.projects/{ProjectRepository, OkioProjectRepository,
FakeProjectRepository}`, `ui.perf/PerfAnchors`, the Android and iOS entry points and journeys
(`StressJourney.kt`/`.swift`, `StressJourneyBenchmark`, `StressPerfTests`); tests `MixerStripRowTest`,
`StressProjectRequestTest`, the additions to `MixerSheetTest`.

Skills: `compose-performance` (the compiler report as evidence), `kotlin-api-design`, `kotlin-concurrency-and-flow`.

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 8 | `mixer/MixerStripRow.kt` `items(...)` | compose-performance | The row was keyed by track id since Phase 3; `contentType` added so a scrolled-out strip's slot is reused for the next one. The strips' own lambdas (`onSelect`, the toggles', the fader's) capture a `@Immutable StripUi` and the callbacks data class, both stable, so the strong-skipping compiler memoises them: no `remember` wrapping was needed, and the report below shows every `ui.mixer` composable skippable. | by construction; report in `compose-report/` |
| 8 | `mixer/MixerStrip.kt` meters | compose-performance / research R6 | Each strip's meter reads its own slice of the shared snapshot (`MeterReading.playback(levels, index)`) in draw; the sheet's root reads the telemetry once per frame. `MixerSheetTest.tenOpenCloseCyclesSettleWithinThreeHundredMillisecondsEach` keeps the read at one per frame with 24 strips across ten cycles. A per-strip recomposition counter was not added: it needs a hook in production code; the compiler report's skippability is the evidence instead. | deviation recorded |
| 8 | `projects/StressProjectSeeder.kt` | kotlin-api-design / kotlin-concurrency-and-flow | The 24-track project is built by a launch argument the journey passes (`tembra.stress.tracks=24`), not by the UI automation: a seeder on the session's scope, once per process, after the startup (`beforeStart`), that creates the project under one fixed id (`projects.card.stressTracks` is the journey's anchor) and hands the demo's bundled loops to the session's import path as picked files, so the tracks are made exactly as a user's imports are. The import is a lambda seam (`importInto`), so the test drives the seeder with the fake repository alone. `ProjectRepository.create` gained an optional id with a default, so every other caller is unchanged. | by construction |
| 8 | `MainActivity.kt` | kotlin-control-flow | The stress argument is read under `BuildConfig.DEBUG || PerfBuild.PERF_ENABLED`: the perf build type inherits release, so `DEBUG` alone (the other debug arguments' gate) would have left the measured journey without its project. | by construction |
| 8 | `StressJourney.kt`/`.swift` | perf contract | The stress journey opens the stress card when the launch built it and the demo otherwise (a release baseline has no argument), records on the added track (index 24 or 4), and adds the mixer phase before the health dump; the fader drag is undone so the document is the same across iterations. | by construction |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`.

## Phase 9 — User Story 7, the mixer in its own window (T106)

Files: `ui.platform/WindowCapabilities`, `ui.platform/IosWindowCapabilities` (iOS), `android/{AndroidWindowCapabilities,
MixerWindowActivity}`, `ui.mixer/{MixerWindowViewModel, MixerWindowUiState, MixerWindowContent, MixerWindow, MixerHandle,
MeterFeed, StripUi}`, `ui.timeline/{TimelineViewModel, TimelineScreen}`, the two graphs, the manifest; tests
`FakeWindowCapabilities`, `MixerWindowViewModelTest`, `AndroidWindowCapabilitiesTest`, the additions to
`TimelineViewModelTest` and `MixerSheetTest`.

Skills: `kotlin-api-design` (the capabilities seam), `compose-state-and-effects` (the second host).

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 9 | `platform/WindowCapabilities.kt` | kotlin-api-design | One interface in `ui.platform` with three members: whether a second window exists, whether the mixer's is open, and the one action. Android binds it to free-form window management and a second activity; iOS returns false (research R10: Android first). The Timeline's ViewModel reads it for the handle's button and for the Mixer button's meaning while the window is open. | by construction |
| 9 | `mixer/MixerWindowViewModel.kt` | compose-state-and-effects | The window is a second host over the same `ProjectSession`: its state is a map of the session's state (no copy of the document), its edits are the session's edits (one undo history, one coalesced run per settled drag), it holds a metering count from `init` to `onCleared`. The selection stays in the timeline's window (the strips' labels select nothing here): a window shows the console, it is not a second timeline. | by construction |
| 9 | `mixer/MixerWindow.kt` `LaunchedEffect(state.open)` | compose-state-and-effects | The window closes when the project is gone: the effect keyed on `open` waits 300 ms before reporting, so the empty state a project switch passes through cancels it. Kept in the composable rather than the activity, which keeps `:androidApp` free of the core document types. | by construction |
| 9 | `mixer/StripUi.kt` `forProject` | kotlin-api-design | The strip mapping both ViewModels need moved from the Timeline's `mapState` into a companion factory on `StripUi`, with the audibility rule read from `Project.audibleTracks` as before. | by construction |
| 9 | `mixer/MeterFeed.kt` `rememberMeterFeed` | compose-state-and-effects | The sheet's snapshot, frame tick and 10 Hz mirror became one remembered feed both surfaces build the same way; each surface's root still does the one read per frame. | by construction |
| 9 | `android/AndroidWindowCapabilities.kt`, the manifest | kotlin-api-design | `MixerWindowActivity` is `singleTask` with its own `taskAffinity`, so one window exists and a second open brings it forward; the first open adds `FLAG_ACTIVITY_MULTIPLE_TASK` and launch bounds (720 × 480 dp centred), as research R10 asked. The activity's minimum is 360 × 320 dp: the full strip form fits 320 dp with the dial dropped (`StripForm.of`). The timeline window's close finishes the mixer window's task (`MainActivity.onDestroy` while finishing → `closeMixerWindow`), because the session never has no project: the user's "project close" is that window (quickstart §12 by hand, `screenshots/android/desktop-api37/`). The window's root carries the perf anchor root, so its tags reach the journeys and adb. | by construction |
| 9 | `android/AndroidWindowCapabilities.kt` `multipleWindows` | kotlin-api-design / spec FR-021a | The capability is the platform's free-form feature flag, as research R10 and feature 011's desktop tests read it. The Pixel Tablet reports the flag while in touch mode too (its desktop mode is a display away), so its handle shows the button and the window opens as a full-screen task there (`android/pixel-tablet/1024dp-mixer-full.png`). The spec's "where the platform offers a second window" is met by the flag; reading the live windowing mode instead would need an attached activity (011's `AndroidWindowControls` pattern) and is left for the owner's T124 call. | deviation recorded |
| 9 | the T100 spike | — | Run with the real activity on the Android 17 desktop emulator rather than a throwaway: the second free-form window opened in its own task and closed back to one (`window-spike.md`). Four of feature 011's full-screen tests timed out in the same lane and passed once the other windows other tests leave open were closed and animations were off: an emulator state, not the second window. | go |
| 9 | `mixer/MixerWindowContent.kt` | compose-component-design | The window's strips show the track menu of the sheet, whose settings, add-audio, move and delete actions are the Timeline's: in the window they are no-ops. Recorded for the docs task; a window that drives those actions would need the timeline's navigation, outside this story. | deviation recorded |

Lanes at the end of the phase: recorded in `../screenshots/NOTES.md`.

## Phase 10 — the polish round and the final review of the whole diff (T122)

Files reviewed in this round beyond the phase sections above: `ui.mixer/MixerSheet` (the focus-on-open),
`ui.projects/{StressProjectRequest, StressProject, StressProjectSeeder}`, the perf journeys on both platforms, the
pipeline's registries, the capture and audit evidence. The compose compiler report of 2026-11-08
(`compose-report/`) is the stability evidence: every `ui.mixer` composable and the four design-system controls are
skippable, `MixerUi`/`StripUi`/`MasterUi`/`MixerWindowUiState` stable, `MeterFeed`/`MixerLevelMirror` unstable by
design (read in the draw phase only).

| Phase | File:line | Skill | Finding | Fix (commit) or why not |
|---|---|---|---|---|
| 10 | `mixer/MixerSheet.kt` `handleFocus` | compose-focus-navigation | The desktop pass (T092) found that X opened the sheet while the focus stayed in the toolbar, so Tab walked the toolbar and every track header before the first strip (35 stops on the demo, 150 on 24 tracks). The sheet now requests the handle's focus from a `LaunchedEffect(ui.open)` when the input mode is the keyboard: the request runs after the handle is attached, touch devices are untouched (their input mode stays touch), and the existing Tab order from the handle holds. `MixerSemanticsTest.openingTheSheetUnderAKeyboardFocusesTheHandle`. | fixed (the T092 commit) |
| 10 | `mixer/MixerSheet.kt` `remember { FocusRequester() }` after `if (!shown) return` | compose-state-and-effects | A `remember` and an effect below an early return: the slot table drops and recreates them with the sheet's subtree, which is the intended lifetime (a new open is a new request). | no change |
| 10 | `projects/StressProjectSeeder.kt` | kotlin-concurrency-and-flow | The seeder runs once on the application scope, awaits the repository's creation and imports through the session's own import path, so the 24 tracks arrive like a user's import (decode, overview, autosave) and no second write path exists. Debug and perf builds only (`BuildConfig.DEBUG || PerfBuild.PERF_ENABLED`). | no change |
| 10 | `perf-benchmarks/.../MixerJourney.kt`, `IOSPERF/Journeys/MixerJourney.swift` | perf contract | The 14 steps match contracts/perf-instrumentation.md; the drag runs 200 steps on Android and a pressed drag on iOS so the fader's `onGainSettled` fires once per gesture and `timeline.faderDrag` brackets the whole move. | no change |
| 10 | `perf-benchmarks/.../MixerJourney.kt`, `IOSPERF/Journeys/MixerJourney.swift` step 6 | perf contract | The trial round's mixer journey failed twice on the Pixel 6 Pro: `mixer.strip.pan.0` never appeared, because a phone opens the sheet at its half height where the knob is not shown (data model §5). The journey now taps the handle for the full form when the knob is absent and taps it back before the close, so every iteration starts the same way; the contract's steps 6 and 11 say so. | fixed (the T109 commit) |
| 10 | `perf-benchmarks/.../MixerJourney.kt` `run` | perf contract | The second trial round's mixer journey ran but at 75 s an iteration (the timeline's takes 44 s): the sheet's meters update their semantics ten times a second, so UiAutomator never saw the UI idle and every click acknowledgment and `waitForIdle` ran to the default 10 s; twenty iterations overran the pipeline's 25-minute instrument window at 18. The journey sets `Configurator`'s idle and acknowledgment waits to 500 ms for its run and restores them; the contract says so. | fixed (the T109 commit) |
| 10 | `perf-benchmarks/.../StressJourney.kt` `recordWhileScrolling`, `mixerPhase`; `IdleWaits.kt` | perf contract / kotlin-api-design | The mixer-and-stress round's stress journey failed both attempts with `StaleObjectException` at the recording step: the lane handle was taken before the 25th track was added to the 24-track project, and the list's recomposition replaced the node. The lane is now scrolled through `scrollFirstLane`, which finds it again on a stale handle, and the mixer phase runs under the short idle waits; the idle-wait wrapper moved from `MixerJourney` into `withShortIdleWaits` (one owner for the Configurator dance, both journeys call it). | fixed (the T109 commit) |
| 10 | `tracepulse/catalogue.py` `audio.meter_frames_dropped` | perf contract | The 20 % gate with a floor of 2 keeps a single dropped meter frame in a 25-frame window from failing a round, as the contract's note asks; `audio.master_clips` is informational. | no change |
| 10 | `ScreenshotMatrix.swift` landscape | compose-ui-testing-patterns | The matrix relaunches the app after rotating, since Compose keeps the portrait accessibility frames after a rotation; the iPhone's landscape still has no mixer capture because the timeline toolbar sits above the window there (pre-existing, recorded for the owner in `../screenshots/NOTES.md`). | recorded |
| 10 | `StripForm.of` at the half anchor | compose-component-design / spec | Half means the compact form whatever the sheet's height (data model §5); on the iPad in portrait the half sheet is about 1,050 dp tall with 900 dp faders. Spec-conformant; a design-iteration candidate for the owner (T124). | recorded |

### Summary of the review (T122)

81 rows across Phases 2–10: 54 findings the code already met when the skill was applied
("by construction": the row records the check and the evidence), 11 fixed in the phase that raised them
(each names its commit or the phase), 16 recorded as decisions with their reason (the
deviations list in `../screenshots/NOTES.md`; the T100 spike's "go" counts here). By primary skill:

| Skill | Findings | Already met (by construction) | Fixed | Recorded as a decision |
|---|---|---|---|---|
| kotlin-api-design | 11 | 9 | 0 | 2 |
| compose-component-design | 11 | 8 | 0 | 3 |
| compose-state-and-effects | 9 | 9 | 0 | 0 |
| compose-focus-navigation | 8 | 5 | 1 | 2 |
| kotlin-control-flow | 5 | 3 | 1 | 1 |
| compose-performance | 5 | 4 | 0 | 1 |
| kotlin-concurrency-and-flow | 4 | 3 | 0 | 1 |
| compose-ui-testing-patterns | 5 | 3 | 0 | 2 |
| compose-animations | 1 | 1 | 0 | 0 |
| navigation-event | 1 | 0 | 1 | 0 |
| spec, contracts, constitution and perf-contract rows | 21 | 9 | 8 | 4 |

Every fixed row names its commit or phase; every recorded row names the spec clause or the research decision it rests
on. The constitution's Principle I (nothing Kotlin on the audio callback) is the subject of the main-thread audit
(`main-thread-audit.md`): every mixer call from the main thread is an atomic store or one seqlock copy, and the
StrictMode reads found zero mixer lines.
