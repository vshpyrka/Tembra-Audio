# Research: Mixer (feature 012)

Phase 0 of the plan. Every decision cites the code as it is on `012-mixer` (built on the 010
tip, ee63fa0) and on `011-large-screens-desktop` where the mixer depends on it. Paths are
relative to the repository root; `UI` = `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui`,
`ENGINE` = `app/engine`, `KENGINE` = `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine`.

The owner's planning brief: make a plan for the mixer; make sure nothing runs on the main
thread; check the implementation against the Chris Banes best-practice skills; test everything
on all available devices and emulators. R12, R13 and R14 answer those three points.

## R1. The mixer is a sheet of its own inside the Timeline screen, not a Material modal sheet

**Decision**: `MixerSheet` is a non-modal, two-anchor sheet composed inside
`TimelineScreenContent`'s lanes box (the `Box(weight 1f)` that holds `TrackList`,
`TimelineScreenContent.kt:143-157`), aligned to its bottom, built on Compose Foundation's
`anchoredDraggable` with anchors `Hidden`, `Half` and `Full`; its offset is read in
`offset { }` (deferred read). The toolbar, the ruler, the overview strip and the transport bar
stay where they are and stay usable; the sheet covers only the lanes. `TimelineViewState`
gains `mixer: MixerViewState(open, height)`; the composable owns the drag, the ViewModel owns
open/closed and the settled height. Back closes it (the multiplatform `BackHandler`); after
011 merges the sheet also calls `RegisterOverlay(onDismiss)` so Escape and ⌘. close it like
every other overlay.

**Rationale**: spec FR-016, FR-018, FR-021 (the transport bar usable, the toolbar visible at
full height, the lanes visible at half height, two heights the handle drags between). Every
existing sheet is Material 3 `ModalBottomSheet` (`MetronomeSettingsSheet.kt:70`,
`SnapSheet.kt:50`), which covers the whole window, scrims it, lives in its own window (the
iOS screenshot matrix relaunches the app to dismiss one, `ScreenshotMatrix.swift:50-54`) and
has no second resting height the spec can name. `TimelineSheet` (`TimelineSheet.kt:7`) stays
the "one modal overlay at a time" enum; the mixer is not one of them and may be open while
the metronome sheet or a menu is shown.

**Alternatives**: `ModalBottomSheet` with `skipPartiallyExpanded = false` (covers the transport
bar; no control over the half height; modal); a nested Navigation 3 entry like the track
settings (`TembraNavDisplay.kt:92`; it would replace the timeline rather than overlay it,
which FR-018 forbids on phones); a `Popup` (no drag, no anchors).

## R2. Strip layout, forms and sizes

**Decision**: one `MixerStrip` composable with two forms: **compact** (number, Solo, Mute, the
fader with the two-bar meter running beside its throw as `tablet-split-v2` shows, the name)
and **full** (number, VU dial, pan knob, Solo, Mute, fader with the meter beside it, name).
The half height shows the compact form, the full height and the landscape sheet the full
form minus the VU dial when the sheet is shorter than 480 dp (FR-025's drop order: VU, then
the knob). Strips are 72 dp wide on touch screens, the fader handle 44 × 28 dp, the Solo and
Mute toggles 36 dp like the expanded header's (`TrackHeader.kt:128-139`), the meter 6 dp per
bar. The master strip is the same frame with a percentage fader, the summed meter and no
knob or toggles. The row is a `LazyRow` keyed by track id with the master strip pinned
outside it at the right edge; after 011 merges the row gets `tembraScrollbar`.

**Rationale**: FR-002, FR-018 (at least three strips and the master at 360 dp: 3 × 72 + 72 +
gaps = 312 dp), FR-025 (72 dp strips, 44 dp handle), the design table ("Dual vertical LED
meter per strip" beside the fader in `tablet-split-v2`). The fader's throw at the half height
(256 dp, the implementation's floor: this sum forgot the 32 dp handle row and the 8 dp of padding)
is 256 − 32 (handle) − 8 (padding) − 20 (number) − 36 (toggles) − 20 (name) − 16 (gaps) = 124 dp ≥ 120 dp
(FR-018). `laneHeightCompact = 88` and `laneHeightExpanded = 96` (`TembraSpacing.kt`) give
the "two lanes visible" rule of FR-021 its number: Half = lanes box − 2 × 96 dp on expanded
windows, never under 256 dp.

**Alternatives**: a `Row` with horizontal scroll (no item recycling at 64 tracks); the design's
meter above the knob (a third row of height the half form cannot afford).

## R3. The fader is a new vertical control with a console taper; the knob a new rotary control

**Decision**: `Fader(gainDb, onGain, onGainSettled, modifier, …)` is a custom composable (no
`TembraSlider`, which is horizontal and Material-backed, `TembraSlider.kt:23`): a
`pointerInput` drag along the throw that follows the pointer 1:1 in throw space and keeps
following when the finger strays sideways (`awaitEachGesture` + `drag`, like `TempoDial`'s
ring drag, `TempoDial.kt`), a double tap to unity, the wheel in 0.5 dB steps, arrows 0.5 dB,
Page 6 dB, Home to 0 dB, `progressBarRangeInfo` + `setProgress` semantics (the
`GainReadout` contract, `GainReadout.kt:109-116`), a `stateDescription` of the readout. The
mapping between the throw and dB is `FaderTaper` in `:shared:core`, a pure piecewise-linear
curve in dB: throw 1.0 → +6 dB, 0.7 → 0 dB, 0.5 → −12 dB, 0.2 → −40 dB, 0.02 → −60 dB, below
that silence (`GainDb` carries `-Infinity` for silence, `GainDb.kt`). `PanKnob(pan, onPan,
onPanSettled, …)` is a rotary control: a vertical drag of 2 dp per 1 %, a double tap to the
centre, a release within 5 % snapping to the centre, the wheel, arrows, Page and Home, and
the same semantics shape with the "L 35 / C / R 20" readout. Two faders under two fingers
work without special code: Compose dispatches each pointer to the node it hit, and each
strip's `pointerInput` owns its own gesture.

**Rationale**: FR-003, FR-004, FR-007, FR-008, FR-027, FR-028. `TempoDial` is the project's
own gesture-and-keys precedent (`TempoDial.kt:130-142, 203`); the taper numbers follow the
spec ("0 dB at about 70 % of the throw, −12 dB near the middle, the lowest fifth covering
−40 dB to silence"). A pure taper in core is unit-tested in both directions and shared by
the semantics (`setProgress` works in dB, not throw).

**Alternatives**: a rotated `TembraSlider` (Material's slider cannot be rotated without
breaking its semantics and the hit area); a fine-control mode on sideways drag (no spec
requirement; deferred).

## R4. The mixer's hot parameters travel over atomics, never through the command mutex

**Decision**: the engine gains a lock-free parameter block: per track `std::atomic<float>
gainTarget`, `std::atomic<float> panTarget`, `std::atomic<uint32_t> audible`, plus a
`std::atomic<uint64_t> mixVersion` bumped on every write; the master keeps its
`std::atomic<float> masterTarget_` (`transport_renderer.h:165-176`). The new ABI functions
`tembra_mixer_set_track(index, gain_linear, pan, audible)`, `tembra_mixer_set_pan(index, pan)`,
`tembra_mixer_set_master(gain_linear)` and `tembra_mixer_set_metering(enabled)` validate
their arguments, store and return; they never take `EngineCore::commands_`.
`tembra_transport_set_track` and `tembra_transport_set_master` keep their signatures and
forward to the new path. The callback's `applyTracks` (`transport_renderer.cpp:85-106`)
compares `mixVersion` once per block and, when it changed, reads the 64 × 3 atomics and sets
the strips' ramp targets. `setArrangement` (under `commands_`) initialises the atomics for the
new track table so a reopened project starts from the document's values.

**Rationale**: the owner's "nothing on the main thread" and the 010 audit's classification of
`setTrack` as "cheap by construction" (`specs/010-timeline-recording/screenshots/NOTES.md:71-91`)
are only true if the call is bounded. Today `setTransportTrack` takes `commands_`
(`engine_core.cpp:721-730`), the same mutex `transportPlay` holds through `prime` (< 50 ms,
`:755-770`) and `recordStop`/`recordCancel` hold while draining (up to 1 s, `:1038-1082`). A
fader step sent from the session on the main-immediate scope (`ProjectSession.kt:610-630`)
can therefore stall the main thread for up to a second. Atomics make every mixer setter a
few hundred nanoseconds on any thread, so the session keeps calling them synchronously from
the edit path and no coroutine hop or queue is needed (Principle I: "Kotlin only produces
commands"; the `TrackTable` TripleBuffer stays for the arrangement's structure). The
constitution's "lock-free SPSC FIFOs and atomics" is satisfied by atomics; a FIFO would add
ordering the parameters do not need (the latest target is the only one that matters, and a
10 ms ramp towards it is the smoothing FR-031 asks for).

**Alternatives**: hopping every setter to `EngineDispatchers.blocking` (the main thread never
waits, but a drag's sound lags behind a one-second `recordStop`, and 64 conflated sends per
drag add machinery); a second mutex for the mixer table (a second writer to the
`TrackTable` TripleBuffer breaks its single-writer rule, `triple_buffer.h:16-46`).

## R5. Pan law: constant power with the centre at unity; stereo sources use balance

**Decision**: for a mono source, `L = √2·cos θ`, `R = √2·sin θ`, `θ = (pan + 1)·π/4` with pan
in −1…+1: centre gives 1.0 on both sides, full left gives √2 (+3 dB) on the left and silence
on the right, and `L² + R² = 2` everywhere (constant power). For a stereo source the same
curve attenuates the opposite side and never boosts: `bL = min(1, √2·cos θ)`, `bR = min(1,
√2·sin θ)` (balance). `TrackStrip` gets two `GainRamp`s (left and right targets = fader gain
× side gain × audible) so pan moves are smoothed over the same 10 ms as gain
(`gain_ramp.h`). The law lives in `ENGINE/src/mixer/pan_law.h` with Catch2 tests; Kotlin
never computes side gains.

**Rationale**: FR-009 (constant power, no click), FR-032 (a track at 0 dB, centred and alone
meters at the level of its own audio): `TrackStrip` today sends a mono sample to both sides at
unity (`track_strip.cpp:5-20`), so the standard −3 dB law (0.707 at centre) would make every
existing project 3 dB quieter the day the feature lands and would break FR-032. The
centre-compensated form (Logic Pro's "−3 dB compensated") keeps old projects identical and
puts the +3 dB at the extremes, where the master clamp and the strip's clip indicator show it.
Balance for stereo is what most DAWs do for stereo tracks and avoids collapsing a stereo
image into one side at +3 dB. The DAW internals brief (`docs/research/daw-internals.md`
§3) names the −3 dB law and the research report §6 "per-track gain/pan (−3 dB law)".

**Spec note for `/speckit-analyze`**: SC-004 says "summed to mono"; the quantity that stays
constant under a constant-power law is the power sum `L² + R²`, which is how the quickstart
measures it (§8). The wording should say "power-summed" and is flagged, not changed here.

**Alternatives**: standard −3 dB (breaks FR-032 and old projects' level); −6 dB linear
(mono-compatible but a −6 dB dip in stereo listening, the brief's §3 second option); a
per-project pan-law setting (no spec requirement).

## R6. Meters are measured on the callback, published through one seqlock, drawn per frame

**Decision**: when metering is enabled (an atomic the sheet turns on while it is open and off
when it closes, FR-015's "a meter MUST NOT make the strip recompose"), `MixMeter` on the
callback measures each strip's post-fader stereo output per block (peak per side, mean square
per side with a 300 ms exponential window updated per block) and the master's output after
the clamp (peak, mean square, and a sticky clip latch set when the clamp engaged), keeps a
64 ms recent-peak history per side (the capture meter's `kRecentPeakSeconds` and history ring
generalised, `capture_source.h:23-29`), and publishes one `Seqlock<MixRecord>` per block
(`seqlock.h:16-47`; about 1.2 KB: 64 tracks × 2 sides × (peak, rms) + master + a 64-bit clip
mask + the block index). The host reads it through `tembra_mixer_read(tembra_mix_snapshot*)`
exactly as `tembra_transport_read` and `tembra_capture_read` are read: lock-free, allocation-
free (`SetFloatArrayRegion` in the JNI shim, a pre-allocated struct in the iOS actual),
once per frame through a `MixTelemetry` fun interface memoised on the frame time
(`ProjectSession.readFrame`'s pattern, `ProjectSession.kt:754-760`). The sheet root performs
the one read per frame inside `drawWithContent` wrapped in the `timeline.mixerFrame` section;
each strip's `LevelMeter` and `VuDial` read the shared `MixSnapshot` inside `drawBehind`
(`InputMeter.kt:96-118` is the pattern), reusing `LevelHold` (1.5 s hold, 20 dB/s fall,
−60 dB floor, `LevelHold.kt`) and `LevelSmoother` (30 ms); the VU needle maps the published
RMS to −20…+3 VU with 0 VU at −18 dBFS. Clip latches are sticky on the engine side
(`tembra_mixer_reset_clip(index)`, index 64 = master) so a latch is never lost between
frames, like the capture latch (`capture_source.cpp:65-66`). Semantics (`stateDescription`,
"clipped" once) come from one 10 Hz mirror per sheet (`InputMeter`'s `MIRROR_MILLIS` pattern,
`InputMeter.kt:78-87`), not per strip.

**Rationale**: FR-012 to FR-015, SC-003; constitution Principle II (telemetry at 60–120 Hz
read in draw, never through `StateFlow`); the capture meter is the proven path (010 R8).
Measuring per block on the callback is bounded (a compare and a multiply-add per sample per
track, already paid by the mixing); the 64 ms history guarantees a 60 Hz reader never misses
a transient; the 300 ms mean-square window on the engine keeps the host's VU a mapping, not an
integrator that has to know the block rate. A single record for every strip costs one
memcpy per block and one read per frame, independent of the track count.

**Alternatives**: per-strip reads (64 seqlock reads per frame); computing RMS on the host from
per-frame peaks (wrong: a VU is an average of the signal, not of peaks); measuring in a
host thread from a FIFO of blocks (adds a FIFO's worth of latency to the meter, 010 R8).

## R7. The document gains `pan` after `clips`; `SetPan` is one coalescing edit

**Decision**: `Track` gains `pan: Pan = Pan.Centre` appended after `clips` (`Track.kt:15-24`;
the project's rule "a field added by a later feature is appended with a default",
`Project.kt:16-18`); `Pan` is a `@JvmInline @Serializable value class Pan(val percent: Int)`
in −100…100 (0 = centre) serialised as the bare integer, with `readout` ("L 35", "C", "R 20")
and `snapToCentre(within = 5)`; `SetPan(id, pan) : ProjectEdit` joins `ProjectEditor` and the
validator (range), and `ProjectSession.coalesces` gains `is SetPan -> last is SetPan && last.id
== edit.id` (`ProjectSession.kt:685-690`) so a knob turn is one undo step. `encodeDefaults =
true` (`ProjectDocumentCodec.kt:21-28`) means every track now writes `"pan": 0`: the golden
(`DemoProjectGolden.kt`) and `specs/009-timeline-arranger/fixtures/demo-project.json` are
updated; the format stays version 1 (unknown keys are dropped on read, so a document from
before this feature reads as centred, FR-006). The 009 document contract gets one appended
row for the field.

**Rationale**: FR-005, FR-006, FR-008; the wire-order rule of the 009 contract (appending keeps
the future `@ProtoNumber` of `clips`). An integer percent matches the readout, the master's
percentage and the 1 % keyboard step, and avoids float formatting.

**Alternatives**: a float −1…1 (formatting and equality noise); storing the two side gains
(the law would leak into the document).

## R8. State ownership: the Timeline ViewModel, one `MixerUi`, callbacks like the rest

**Decision**: no new ViewModel for the sheet (the constitution's "one ViewModel per
destination"; the mixer is part of Timeline, FR-016). `TimelineViewState` gains
`mixer: MixerViewState(open, height: MixerHeight)`; `TimelineUiState` gains `mixer: MixerUi`
(`open`, `height`, `form` derived from the layout, `strips: ImmutableList<StripUi>` derived
from `tracks`, `master: MasterUi`) and `TrackUi` gains `pan: Pan`; `TimelineCallbacks` gains
`onMixerToggle`, `onMixerHeight`, `onPan`, `onGainSettled`, `onPanSettled`, `onResetTrackClip`,
`onResetMasterClip`, `onStripSelect`. The ViewModel's intents call `session.edit(SetPan(…))`
and `session.closeEditGroup()` at gesture end (so two separate drags are two undo steps,
FR-005), `session.setMetering(true/false)` when the sheet opens or closes (a refcount in the
session so a future second window keeps it on), and `selectTrack` + a viewport scroll request
for `onStripSelect`. Keyboard "X" joins `handleTimelineKey` (`TimelineKeyHandling.kt:28-55`)
and, after 011 merges, the `ShortcutCatalogue` under a Mixer group with the fader and knob
keys and "M"/"O".

**Rationale**: `compose-state-and-effects` (screen-level state in the ViewModel, content
composables take immutable state and callbacks); the 009/010 pattern (`TimelineScreen.kt:74-135`
builds the callbacks once); FR-023, FR-028, FR-029. The 10 Hz level mirror and the per-frame
reads are not state (R6).

**Alternatives**: a `MixerViewModel` (the research's example, `kmp-code-architecture.md:152`;
it would duplicate the session's track list and break the one-VM rule); state in the sheet
composable only (lost on a layout-class rebuild, which FR-022 forbids).

## R9. Where pan appears outside the sheet

**Decision**: a `PanSlider` row in `TrackSettingsForm` after the gain row
(`TrackSettingsForm.kt:88-92`, a `TembraSlider` −100…100 with the readout, `stateDescription`
the readout), a `PanReadout` in the expanded `TrackHeader`'s second row after `GainReadout`
(`TrackHeader.kt:128-139`; the 200 dp column holds it at `tabularParam` size), nothing in the
compact header (FR-007, 010 US6 scenario 1: the 360 dp row is full).

**Rationale**: FR-007; feature 009's "the header's place for it is noted there" resolved by
the spec's own rule.

## R10. The separate window: Android only, delivered last, cuttable

**Decision**: FR-021a is delivered as the last phase of the feature on Android only: a
`MixerWindowActivity` in `:androidApp` launched with `FLAG_ACTIVITY_NEW_TASK |
FLAG_ACTIVITY_MULTIPLE_TASK` and `ActivityOptions.setLaunchBounds` on platforms whose
`WindowCapabilities.multipleWindows` is true (an interface in `ui.platform` with an Android
binding that checks free-form window management and desktop windowing, and an iOS binding
that returns false; 010's `AudioRouteMonitor` pattern), hosting `MixerWindowContent` from
`:shared:ui` with a `MixerWindowViewModel` (a window is a destination of its own) over the
same `ProjectSession` from the application graph; it finishes when the project closes and
is brought to the front by the Mixer button and "X" while open. The plan declares it
**cuttable**: the implement run delivers the sheet and all other requirements first; if the
window's spike (tasks phase "window") shows the platform refuses a second window of the app
in the desktop emulator, or the owner stops the run before that phase, the feature is
complete without it and the window becomes a follow-up feature. The spec allows exactly
this ("the plan MAY deliver it last or defer it to a follow-up feature, and MUST say which").

**Rationale**: the owner asked for it "as optional"; feature 011 explicitly left "several
windows (for example, the tuner beside the timeline)" out of scope (011 spec line 547) and
booted the desktop emulator with `-no-audio`; the app has one activity and one Navigation 3
back stack, so a second window is a second host, not a new destination inside the stack.
iPad windows need a `UIScene` configuration and a second SwiftUI host, which the iOS shell
does not have; "Android first; iPad later" is the spec's own order.

**Alternatives**: a picture-in-picture-style floating mixer (not a window); Android
activity embedding (one window split in two, not a second window).

## R11. Performance instrumentation names and placement

**Decision**: sections `timeline.mixerOpen` (the toolbar press → the first draw of the first
strip's meter, `traceFirstDraw`), `timeline.mixerClose` (the press → the sheet gone),
`timeline.mixerFrame` (the sheet root's per-frame read-and-draw pass), `timeline.faderDrag`,
`timeline.panTurn` and `transport.masterDrag` (animation variants over a gesture, the
`TIMELINE_SCROLL` pattern in `ViewportGestures.kt:55-84`), `engine.panApply` (C++
`TEMBRA_TRACE_SCOPE` in the non-RT `mixer_abi.cpp`, the `capture_abi.cpp:16` pattern);
counters `audio.masterClips` (a new health field written by the callback when the clamp
engaged, read by `AudioHealthTicker`) and `audio.meterFramesDropped` (a UI counter: frames the
sheet's frame loop saw arrive later than twice the display period while metering was on);
anchors as the UI contract lists; journey `mixer` and the `stress` extension as the spec
states; the baseline profile regenerated. Section names keep one dot
(`collect_ios._SECTION_RE`); the `trace_shim_test` count becomes 49.

**Rationale**: the spec's "Performance instrumentation declared by this feature"; the
catalogue format (`perf-catalogue.txt`; `PerfSectionCatalogueTest`); the registration list of
`app/perf/README.md` "Adding a journey, a section, a counter".

## R12. The main-thread audit: bounded by construction, proven by tests, measured in traces

**Decision**: the audit has five parts, all deliverables of the feature.
1. **Construction**: every call the mixer makes on the main thread is lock-free and bounded:
   the four setters and `setMetering` (atomics, R4), `readMix` (one seqlock copy of about
   1.2 KB), `resetMixClip` (one atomic), the 10 Hz mirror (one `readMix`), and the sheet's
   composition. The only suspending calls remain the ones 010 already moved to
   `EngineDispatchers.blocking` (`openTransport`, `closeTransport`, `arm`, `disarm`, …,
   `NativeAudioEngine.android.kt:33-37`); the mixer adds no new call to them. Project saves stay
   on the autosave's path (`AutosaveScheduler`), unchanged.
2. **Engine tests** (Catch2): `mixer_abi_test` holds `commands_` from a second thread through
   a test seam and asserts `tembra_mixer_set_pan` returns within 1 ms; `mix_meter_test`
   asserts the measurement allocates nothing (the poison build covers `mix_meter.cpp`,
   `tests/CMakeLists.txt:67-85`).
3. **Facade tests** (device and simulator): `NativeAudioEngineMixerCostTest` times 1,000
   `setPan`, `setTrack` and `readMix` calls on the main thread and requires a median under
   50 µs and a maximum under 1 ms; the 010 pattern `NativeAudioEngineBlockingDispatchTest`
   (`CountingDispatcher`) is extended to assert the mixer's setters do **not** dispatch (they
   are synchronous by design, and the test documents it).
4. **Debug guard**: `:androidApp` debug builds install `StrictMode.ThreadPolicy`
   (`detectAll().penaltyLog()`) in `TembraApplication.onCreate`; the device lane and the
   mixer journey are read for `StrictMode` lines in logcat (zero expected; an existing
   violation found this way is reported, not silently fixed in this feature).
5. **Traces**: after the labelled session, the Pixel 6 Pro round's Perfetto trace is queried
   for main-thread slices longer than 8 ms inside `journey.mixer` (the `perfetto-sql` skill;
   the query and its result go into `evidence/main-thread-audit.md` beside the table of parts
   1–4, the form of 010's audit table in `screenshots/NOTES.md:71-91`). On iOS 27 `xctrace`
   cannot attach (feature 007's record), so the iPhone 14 is covered by part 3 and the
   journey's hitch metric.

**Rationale**: the owner's brief; the 010 audit found three calls on the main thread by code
reading alone and fixed them test-first (commits 55f6c16, 957da4b); this feature makes the
check mechanical. `kotlin-concurrency-and-flow`: no stored scope, no `init` launch, no
`runBlocking`, suspend only where the engine blocks; the UI state holder is the only launch
site (the 10 Hz mirror as a `LaunchedEffect` keyed by the sheet's open state).

## R13. The Chris Banes skill review is a contract per package and a review task per phase

**Decision**: `contracts/skills-and-audit.md` names, for every new or extended package, the
skill that owns its decisions and the rules the implementation is held to; every
implementation phase ends with a review task that routes the changed files through
`using-chrisbanes-skills` and records each finding and its fix in `evidence/skill-review.md`
(the form of feature 003's 29-finding review). The rules the plan already commits to:
`compose-component-design` (`modifier` first and on the root; primitives and callbacks; no
Boolean shape flags; `Fader`, `PanKnob`, `LevelMeter`, `VuDial`, `MixerStrip`, `MasterStrip`,
`MixerSheet`, `MixerHandle`, `PanSlider`, `PanReadout` are one type per file),
`compose-state-and-effects` (R8), `compose-performance` (frame-rate reads only in
`drawBehind`/`drawWithContent`/`offset { }`; `MixerUi`, `StripUi`, `MasterUi` immutable; the
dB scale ticks `remember`ed; `key(track.id)` items; the compose compiler report shows every
new composable skippable), `compose-animations` (`anchoredDraggable.animateTo` for the sheet;
an `Animatable` for the double-tap return; reduce motion honoured as `InputMeter` does),
`compose-focus-navigation` (a `focusGroup` per strip, Solo → Mute → pan → fader order by
composition, keys on the focused control through `onKeyEvent`, the sheet handle focusable),
`compose-ui-testing-patterns` (controlled state, `ScriptedMixTelemetry`, injected interaction
sources, frozen clock, semantics before pixels, keyboard tests declared per lane),
`kotlin-concurrency-and-flow` (R12), `kotlin-api-design` (`Pan` and `FaderTaper` as closed
core types; `SetPan` an edit; `TimelineAudio` grows member functions; `WindowCapabilities` an
interface with bindings, no new expect/actual), `kotlin-control-flow` (exhaustive `when` over
`MixerHeight`, `StripForm`, `MeterSource` and the new `ProjectEdit` case; no `else`),
`gradle-run` for every Gradle command.

**Rationale**: constitution Principle VIII and the owner's brief; 010's plan cited the skills
in its Constitution Check and contracts, this feature adds the per-phase review so the check
is done, not assumed.

## R14. Verification on every available device, emulator and simulator

**Decision**: the quickstart's §2 lists every target the repository knows with the lane each
runs: the Pixel 6 Pro (`19021FDEE001AH`), Pixel 7 Pro (`33241FDH3000NZ`), Pixel Tablet
(`3621105H803UFX`), OPPO CPH2239 (`O7PBYLZP4HMBW8S4`), Redmi Note 5 (`e9446ac2`), the
Pixel 9 Pro Fold emulator (`emulator-5554`), the desktop emulators of feature 011
(`Tembra_Desktop_API37` on `emulator-5582`, `Tembra_Desktop_API34` on `emulator-5580`), the
iPhone 18 Pro, iPad Pro 13-inch (M5) and iPhone Duo simulators, and the iPhone 14
(`00008110-001049660E3A401E`; release build, screenshots and perf, not the Kotlin lane). Each
Android target runs `checkAndroidDevice` through `tools/gradlew-device.sh` after
`tools/apply-device-run-conditions.sh` (one target per sub-agent, serialised by the device
lock, `app/build.gradle.kts:20-33`); each simulator runs `checkIos -Ptembra.iosSimulator`;
every target gets the release build (`tools/release-android.sh`, `tools/release-ios.sh`) and
the screenshot set; the labelled sessions run on `pixel6`, `iphone14`, the Pixel 7 Pro, the
Pixel Tablet by serial and the desktop emulator. Any target skipped is named with the reason
(the OPPO refuses brightness and the runtime microphone grant; the Redmi has no Perfetto and
refuses do-not-disturb; the fold emulator's full lane takes about 2.5 h and has PixelCopy
flakes; the iPhone 14 needs its passcode after an update), as the owner's rule requires.

**Rationale**: the owner's brief ("test everything on all available devices and emulators")
and the standing rule to name every skipped target; the device facts from
`specs/009-timeline-arranger/screenshots/NOTES.md:128-141`, `app/tracepulse.yml:33-40`, 010's
T092/T104 records and 011's quickstart §1.

## R15. Branch order, the application id and what 011 brings

**Decision**: implementation starts after `010-timeline-recording` and
`011-large-screens-desktop` have merged to `main` and `main` has been merged into `012-mixer`
(the first implementation task). Until then this branch carries the old application id
(`audio.tembra.app`, `app/androidApp/build.gradle.kts:9`); the quickstart writes commands with
`com.tembraaudio` (`main` since a850586) and says so. From 011 the mixer uses: the
`ShortcutCatalogue` and `ShortcutSheet` (the Mixer group), `OverlayStack.RegisterOverlay`
(Escape closes the sheet), `tembraScrollbar` (the strip row), the wheel handling on
`TembraSlider` (the pan slider), `pointerHoverIcon` and `HoverArea`, `focusRestoringRoot` (the
sheet keeps focus across a layout-class rebuild, FR-022), the desktop emulators and the
`windowing` journey's device setup; from `main` the CI gates (`-Ptembra.warningsAsErrors=true
--warning-mode fail`).

**Rationale**: the two branches are unmerged today (`main` is not an ancestor of 012; 011 does
not contain 010); the mixer must not re-implement what 011 ships.

## R16. Tokens, glyphs and copy

**Decision**: no new colour token: the fader rail and handle use `surfaceLevel3`/`surfaceLevel4`
and `onSurface`, the lit fader track and the Solo fill the lavender active colour, the meter
the three meter tokens, the strip tint `trackTints[i]`, the VU arc `meterWarning` above 0 VU;
if the design review in implementation needs a token it is added with its evidence to
`theme.json` first (`ThemeJsonParityTest`). The Mixer button's glyph is Material Symbols
`instant_mix` fetched with `tools/fetch-material-symbols.sh instant_mix` (falling back to the
existing `TembraIcon.Tune` if the pinned commit lacks it). Copy in `TimelineCopy`/`MixerCopy`:
"Mixer", "Open mixer", "Close mixer", "Gain", "Pan", "Solo", "Mute", "Master volume",
"Level", "Clipped, tap to reset", "VU", "Open in a window".

**Rationale**: Principle IV (tokens only); the design table (lavender active toggles, the
three meter colours sampled from `phone-drawer`); the icon workflow in `TembraIcon.kt:69`.

## R17. Tests first, per layer

**Decision**: Catch2: `pan_law_test` (centre unity, extremes √2/0, constant power, stereo
balance never boosts), `mix_meter_test` (peak, RMS window, 64 ms history, latches, metering
off = no work), `mixer_abi_test` (atomics, argument checks, the `commands_` hold), `track_strip_test`
(two ramps, pan ramp 10 ms, stereo pass-through with balance), `transport_renderer_test` (a
stereo render helper beside the left-only `render`, `transport_renderer_test.cpp:61-210`),
`audio_health_test` (the new field, sizeof 60), `engine_info_test` (ABI 8). `commonTest`:
`PanTest`, `FaderTaperTest`, `ProjectEditorSetPanTest`, `ProjectDocumentCodecTest` (golden with
pan; an old document reads centred), `ProjectSessionTest` (pan coalescing, `closeEditGroup`
at gesture end, metering refcount), `TimelineViewModelTest` (mixer open/close, X, strips
derived, metering calls synchronous), `TimelineUiStateTest` (forms by layout and height),
`PerfAnchorsCatalogueTest`, `PerfSectionCatalogueTest`, `TimelineTokensTest`. `uiTest`:
`FaderTest`, `PanKnobTest`, `LevelMeterTest`, `VuDialTest`, `MixerStripTest`,
`MasterStripTest`, `MixerSheetTest` (anchors, drag, back, both forms at 360/600/840/1024 dp
and landscape, every target ≥ 44 dp, nothing clipped, largest font), `TimelineScreenTest`
(toolbar button, the sheet over the lanes with the transport bar reachable),
`TimelineKeyHandlingTest` (X, M/O on the focused strip), `TrackSettingsFormTest` (pan row),
`TrackHeaderTest` (pan readout). Device/simulator: `NativeAudioEngineMixerTest` (pan audible
through the engine on both platforms with a loopback-free assertion through `readMix`),
`NativeAudioEngineMixerCostTest` (R12). Python: `uv run pytest` with the seventh journey and
two counters.

**Rationale**: constitution Principle III and the definition of done; the 010 contracts'
"Tests (first)" sections.

## R18. Dependencies

**Decision**: none added. `anchoredDraggable` is in Compose Foundation (present), the
multiplatform `BackHandler` in Compose UI 1.12.1 (present), atomics in C++17, the seqlock and
the ramp in the engine. The licence inventory gains no row.
