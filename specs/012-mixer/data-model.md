# Data model: Mixer (feature 012)

Phase 1 of the plan. Types by layer; every type is one file (constitution Principle VII).
Research references: [research.md](research.md).

## 1. Document (`:shared:core`, `audio.tembra.core.project`)

| Type | Fields / shape | Rules |
|---|---|---|
| `Track` (extended) | `pan: Pan = Pan.Centre` appended after `clips` | wire order (009 contract rule 8); a document without the key reads as centred (FR-006); `encodeDefaults` writes `"pan": 0` (R7) |
| `Pan` (value class) | `percent: Int` in −100…100; `Centre = Pan(0)`; `readout: String` ("C", "L 35", "R 20"); `clamped(Int)`; `snapToCentre(within: Int = 5)`; `toLinear(): Float` (−1…1 for the engine) | serialised as the bare integer; `Pan.MAX = 100` |
| `SetPan : ProjectEdit` | `id: TrackId`, `pan: Pan` | applied by `ProjectEditor.setPan`; `UnknownTrack` rejection; the validator refuses a percent outside the range |
| `FaderTaper` (object) | `dbOf(throw: Float): GainDb`, `throwOf(gain: GainDb): Float`, `ticks: List<Pair<Float, String>>` | piecewise-linear in dB: 1.0 → +6, 0.7 → 0, 0.5 → −12, 0.2 → −40, 0.02 → −60, below → silence; inverse exact at the knots (R3) |
| `ProjectDerived` (unchanged) | `audibleTracks` | the solo/mute truth table stays in Kotlin; the engine sees `audible` |

## 2. Engine seam (`:shared:engine`, `audio.tembra.engine`)

| Type | Fields | Notes |
|---|---|---|
| `TimelineAudio` (extended) | `fun setPan(index: Int, pan: Float)`, `fun setTrack(index, gainLinear, audible)` (unchanged signature, lock-free now), `fun setMaster(gainLinear)` (lock-free now), `fun setMetering(enabled: Boolean)`, `fun readMix(into: MixSnapshot)` ("lock-free; safe every frame; main thread only, one scratch per engine"), `fun resetMixClip(index: Int)` (64 = master) | contracts/engine-c-abi-v8.md "Kotlin seam"; none suspend (R4, R12) |
| `MixSnapshot` (mutable, one per reader) | `trackCount: Int`, `peak: FloatArray(128)` (track × 2 sides), `rms: FloatArray(128)`, `masterPeak: FloatArray(2)`, `masterRms: FloatArray(2)`, `clipMask: Long`, `masterClipped: Boolean`, `block: Long`, `metering: Boolean`; `copyFrom(other)`; `peakDb(track, side)`, `rmsDb(track, side)` helpers | filled by `readMix` from the seqlock; read in draw lambdas and by the 10 Hz mirror (R6); zeros when metering is off or the transport is closed |
| `AudioHealth` (extended) | `masterClips: Long` | the clamp-engaged block count |
| `FakeAudioEngine` (extended) | `pans: FloatArray(64)`, `metering: Boolean`, `scriptedMix: (block: Long, into: MixSnapshot) -> Unit`, `calls` entries `setPan/setMetering/readMix/resetMixClip` | the JVM tests script levels per block |
| `PerfSection` (extended) | `TIMELINE_MIXER_OPEN`, `TIMELINE_MIXER_CLOSE`, `TIMELINE_MIXER_FRAME`, `TIMELINE_FADER_DRAG`, `TIMELINE_PAN_TURN`, `TRANSPORT_MASTER_DRAG`, `ENGINE_PAN_APPLY` | generated from `perf-catalogue.txt` (§8) |
| `PerfCounter` (extended) | `AUDIO_MASTER_CLIPS`, `AUDIO_METER_FRAMES_DROPPED` | |

## 3. C++ structures (`engine/src/mixer`, `engine/src/transport`, `engine/src/core`)

| Type | Shape | Notes |
|---|---|---|
| `MixParams` | per track `std::atomic<float> gain`, `std::atomic<float> pan`, `std::atomic<uint32_t> audible` (64 of each); `std::atomic<uint64_t> version`; `std::atomic<uint32_t> metering` | written by the ABI setters on any host thread without a lock; read by the callback once per block when `version` changed (R4) |
| `PanLaw` (header-only) | `static void gains(float pan, bool stereo, float& left, float& right)` | mono: `√2·cos θ`, `√2·sin θ`; stereo: `min(1, …)` on each side (R5) |
| `TrackStrip` (extended) | `GainRamp left_`, `GainRamp right_`; `setTarget(gain, pan, audible, stereo)`; `process(in, channels, out, frames)` applies the two ramps | mono duplicated then shaped by the side gains; the monitor strip is unchanged (010's) |
| `MixMeter` | per track `float peakHistory[2][16]` (64 ms), `float ms[2]` (mean square, 300 ms exponential), `bool clipped`; master the same plus `clampEngaged`; `measureStrip(index, out, frames)`, `measureMaster(out, frames)`, `publish(Seqlock<MixRecord>&, block)`; `reset(index)` | runs only while `metering` is set; no allocation, no lock (the poison build covers `mix_meter.cpp`) (R6) |
| `MixRecord` | `uint64_t block; uint32_t count; float peak[64][2]; float rms[64][2]; float masterPeak[2]; float masterRms[2]; uint64_t clipMask; uint32_t masterClipped;` | about 1.2 KB; one `Seqlock<MixRecord>` on the renderer |
| `tembra_mix_snapshot` (C ABI) | the same fields in C layout | `tembra_mixer_read` copies the record out |
| `tembra_audio_health` (extended) | `uint32_t master_clips` appended | `sizeof == 60`; `offsetof(master_clips) == 56` |
| `EngineCore` (extended) | `setMixTrack`, `setMixPan`, `setMixMaster`, `setMetering`, `readMix`, `resetMixClip` | none takes `commands_`; `setTransportArrangement` seeds `MixParams` under `commands_` |

## 4. Session and UI state (`:shared:ui`)

| Type | Fields |
|---|---|
| `MixTelemetry` (fun interface, `ui.session`) | `read(frameNanos: Long, into: MixSnapshot)` once per distinct frame time (the `TransportTelemetry` pattern) |
| `ProjectSession` (extended) | `mixTelemetry: MixTelemetry`; `setMetering(on: Boolean)` (a refcount: the sheet and a window each hold one); `coalesces` gains `SetPan`; `applyToEngine` gains `is SetPan -> audio.setPan(index, pan.toLinear())`; `pushTrack` unchanged; `resetTrackClip(id)`, `resetMasterClip()` |
| `MixerViewState` (`TimelineViewState.mixer`) | `open: Boolean = false`, `height: MixerHeight = Half` |
| `MixerHeight` (enum) | `Half`, `Full` |
| `StripForm` (enum) | `Compact` (meter beside the fader, Solo, Mute, name), `Full` (adds pan and VU), `FullNoVu` (sheets shorter than 480 dp) |
| `MeterSource` (enum) | `Playback`, `Input` (the armed track, FR-014) |
| `MixerUi` (`@Immutable`, `TimelineUiState.mixer`) | `open`, `height`, `form: StripForm` (from the layout and the height), `anchors: MixerAnchors` (half and full heights in dp for the layout), `strips: ImmutableList<StripUi>`, `master: MasterUi`, `windowAvailable: Boolean` (FR-021a), `selectedIndex: Int?` |
| `StripUi` (`@Immutable`) | `id: TrackId`, `index: Int`, `number: Int` (1-based), `name`, `colorIndex`, `gainDb: GainDb`, `pan: Pan`, `muted`, `soloed`, `audible`, `meterSource: MeterSource` |
| `MasterUi` (`@Immutable`) | `percent: Int` |
| `TrackUi` (extended) | `pan: Pan` |
| `TimelineCallbacks` (extended) | `onMixerToggle()`, `onMixerHeight(MixerHeight)`, `onPan(TrackId, Pan)`, `onGainSettled()`, `onPanSettled()`, `onMasterSettled()`, `onResetTrackClip(TrackId)`, `onResetMasterClip()`, `onStripSelect(TrackId)`, `onOpenMixerWindow()` |
| `TrackSettingsUiState` / `TrackSettingsCallbacks` (extended) | `pan: Pan`; `onPan(Pan)`, `onPanSettled()` |
| `MixerLevelMirror` (class, one per sheet) | `levels: SnapshotStateList<StripLevel>` refreshed at 10 Hz by a `LaunchedEffect`; `StripLevel(peakDb: Float, clipped: Boolean)`; `master: StripLevel` | semantics only (`stateDescription`, "clipped" once) (R6) |
| `WindowCapabilities` (interface, `ui.platform`) | `val multipleWindows: Boolean`; `fun openMixerWindow()` | Android binding in `:androidApp`; iOS binding returns false (R10) |
| `MixerWindowViewModel` (optional, R10) | `uiState: StateFlow<MixerWindowUi>` over the session | the second host's state holder |

## 5. Layout rules (`TimelineLayout` → `MixerAnchors`)

| Layout | Half | Full | Form at Half | Form at Full |
|---|---|---|---|---|
| Compact (< 600 dp) | max(240 dp, 45 % of the lanes box) | the lanes box | Compact | Full (FullNoVu below 480 dp of sheet height) |
| Medium (600–839 dp) | the same | the same | Compact | Full |
| Landscape (≥ 600 wide, < 480 tall) | none (one anchor) | the lanes box | — | FullNoVu |
| Expanded (≥ 840 dp, ≥ 480 dp tall) | max(240 dp, lanes box − 2 × 96 dp) | the lanes box | Compact | Full |

The lanes box excludes the toolbar, the ruler, the overview strip and the transport bar
(FR-016, FR-021); the strip width is 72 dp, the master strip is pinned at the right edge
outside the scrolling row (FR-001).

## 6. Tokens and icons (`theme.json`, `DawColors`, `TembraIcon`)

No new colour (R16). Glyph `instant_mix` (fetched) for the Mixer button, `TembraIcon.Tune`
as the fallback; the fader handle, the knob and the VU needle are drawn. Copy in
`MixerCopy`: "Mixer", "Open mixer", "Close mixer", "Gain", "Pan", "Solo", "Mute", "Master
volume", "Level", "Clipped, tap to reset", "VU", "Open in a window".

## 7. Preferences and per-project view state

None added: the sheet's open state and height live in `MixerViewState` for the session only
(FR-022 keeps them across a layout rebuild, not across launches); the spec dropped the
remembered-panel preference with the clarification of 2026-11-03.

## 8. Performance names (`perf-catalogue.txt`; one dot per section name)

Sections: `timeline.mixerOpen`, `timeline.mixerClose`, `timeline.mixerFrame`,
`timeline.faderDrag` (animation), `timeline.panTurn` (animation), `transport.masterDrag`
(animation), `engine.panApply`. Counters: `audio.masterClips`, `audio.meterFramesDropped`.
Anchors: `timeline.toolbar.mixer`, `timeline.toolbar.overflow.mixer`, `mixer.sheet`,
`mixer.handle`, `mixer.strips`, `mixer.strip.fader.<i>`, `mixer.strip.pan.<i>`,
`mixer.strip.solo.<i>`, `mixer.strip.mute.<i>`, `mixer.strip.meter.<i>`, `mixer.strip.clip.<i>`,
`mixer.strip.name.<i>`, `mixer.master.fader`, `mixer.master.meter`, `mixer.master.clip`,
`mixer.window.open`, `trackSettings.pan`. Journey: `mixer` (new); `stress` extended.

## 9. State transitions

- Sheet: `Closed —toggle/X→ Open(Half)` (landscape: `Open(Full)`); `Open(Half) —drag up→
  Open(Full)`; `Open(Full) —drag down→ Open(Half)`; `Open(Half) —drag down / Back / toggle /
  X→ Closed`; a layout-class change keeps `open` and maps `height` (landscape forces `Full`).
- Metering: `off —sheet opens or window opens→ on (count 1…n)`; `—last closes→ off`; the
  transport closing resets the count.
- Clip latch (per strip and master): `clear —peak ≥ full scale (master: clamp engaged)→ lit`;
  `lit —tap / play from stop→ clear`; the armed strip shows 010's input latch instead.
- Edit group: `fader or knob press → steps coalesce into one snapshot → release closes the group`.
