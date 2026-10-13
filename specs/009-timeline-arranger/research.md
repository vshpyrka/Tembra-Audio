# Research: Timeline Arranger (feature 009)

**Date**: 2026-10-13 | **Spec**: [spec.md](spec.md) (clarified the same day) | **Plan**: [plan.md](plan.md)

Inputs: the spec and its five clarifications; the user's planning brief (record, add, split and
move tracks; metronome, mixer, track settings, effects placeholder; abstractions and data models
that save to a server "for instance using FlatBuffers"; designs for tracks and waveforms; tests
and performance with the pipeline; the app's design schema; adaptive UI on every device; calls
and other apps taking the audio stop playback; a confirmation when leaving the tab; playback
with the screen off; separate components and separation of concerns; own UI controls; the best
way to work with audio and whether to copy files; metronome reuse; effects applicable later);
the constitution v1.3.1; three codebase surveys (engine, Kotlin modules, performance pipeline,
2026-10-13); the DAW internals brief; NotebookLM answers from the deep-research notebook
(`9f3644d4…`, 84 sources) and the KMP architecture notebook (`6c22014f…`, 92 sources); the
FlatBuffers and miniaudio documentation fetched through the docs tool.

The brief asks for recording and the mixer. The spec (clarification 1) and the constitution's
amended build order deliver them as features 011 and 010; this plan builds the seams they plug
into (R1) and nothing of their user-facing surface.

---

## R1. Scope cut and the seams for 010 (mixer), 011 (recording) and effects

**Decision**: Feature 009 builds the audio spine (multi-clip playback through one engine), the
transport, the timeline, tracks with gain, mute and solo, clip editing with undo, the metronome
locked to the project, the track settings screen with the effects placeholder, WAV/FLAC/MP3
import, the demo project and the full local Projects library. The engine's render path is shaped
so the next features add to it without reshaping it:

- **Mixer (010)**: every track already renders through a `TrackStrip` (gain, audibility) into a
  `MasterBus` (master gain, clamp). Pan and the peak/VU meters are the strip's next two members;
  the mixer sheet reads meters through the same seqlock telemetry the playhead uses.
- **Recording (011)**: the `AudioDevice` seam already opens an input stream; the transport's
  frame clock is the timebase a recorder aligns to; a recorded take becomes an `AudioAsset` in the
  project folder like an import.
- **Effects (later)**: the strip's `process(block)` is the one place an insert chain is inserted;
  the document's `Track` gains an `inserts` list under a new format version, which the
  forward-compatible reader of R8 already tolerates.

**Rationale**: the user asked that effects, the mixer and recording be possible without rework;
the constitution forbids speculative abstractions (VI). A strip with two members and a bus is the
minimum that plays gain, mute and solo at all, and is the same shape every DAW grows (brief §1.3,
§2). No empty fields, interfaces or flags are added for the later features.

**Alternatives considered**: a flat "sum all clips" renderer (no strips) — would be rewritten
by 010; a full node graph now — speculative.

## R2. Engine layout: transport, timeline and mixer modules on the existing core

**Decision**: three new folders under `app/engine/src/`, following the metronome's pattern
(survey §"patterns"): `transport/` (`TransportClock`, `TempoMap`, `TransportRenderer`,
`transport_abi.cpp`), `timeline/` (`ClipSource`, `ArrangementSnapshot`, `DiskStreamer`,
`SpscFifo`, `AudioDecoder`, `OverviewBuilder`, `timeline_abi.cpp`) and `mixer/` (`TrackStrip`,
`MasterBus`, `GainRamp`). `EngineCore` gains the `TRANSPORT` state, owns the renderer like it
owns the metronome renderer (created at the stream rate in `onOutputOpened`, reached by the
callback through an atomic pointer, retired on close), and its ad-hoc summing in `render()` is
replaced by the `MasterBus` for the transport state while the tone, sample and standalone
metronome paths stay as they are. Commands reach the callback through `TripleBuffer` snapshots
and atomic command words; nothing new is invented for host-to-callback traffic. The new
real-time sources join `TEMBRA_RT_SOURCES` so the poison build keeps tracing and allocation out
of them.

**Rationale**: Principle I; the survey shows the exact mechanisms (`TripleBuffer<T>`,
`Seqlock<T>`, `callbackX_` atomics, `retireLocked`) and tests (`FakeAudioDevice`, block sizes
96/256/1024) the metronome proved on both platforms.

**Alternatives considered**: a second engine singleton for the transport — breaks the
one-stream rule; growing `MetronomeRenderer` into the transport — mixes two features' state.

## R3. The transport clock and the tempo map (beats to frames at render time)

**Decision**: `TransportClock` holds an `int64_t` frame position advanced by each block's frame
count, a `playing` flag and a seek request; `TempoMap` holds the project's one tempo (BPM of the
signature's beat unit, as the metronome defines it) and signature, and converts beats ↔ frames
(`framesPerBeat = 60 / bpm · rate`). The arrangement snapshot carries clip starts in **beats**
(double) and clip offsets and lengths in **source frames**; each block the renderer converts the
clip starts that fall inside `[pos, pos + n)` to frames and splits the block at those frames so
a clip begins on its exact sample (brief §4.3). A tempo change is a new snapshot; positions
re-derive on the next block; nothing is re-timed in the audio.

**Rationale**: Principle V (store in beats, convert at render) and the brief's dual-domain
transport (§4.1–4.2, NotebookLM: "store in beats, convert to samples"); one constant tempo makes
the map a multiplication, which the metronome's `framesPerBeat` already does.

**Alternatives considered**: converting beats to frames in Kotlin and sending frames — would
make the engine the authority on time and break the tempo-change rule.

## R4. The metronome in the transport: anchor the existing scheduler

**Decision**: the transport drives the existing `MetronomeRenderer` (constitution: reuse, never
duplicate) through `MetronomeRenderer::anchor(frame, beatPosition)` — the hook feature 004 left
"not in the C ABI yet" — on play, on every seek and whenever the arrangement's tempo or
signature changes; the grid itself is the metronome's `tembra_metronome_grid` built in Kotlin by
`MetronomeGrid.from(TickGrid, projectTempo)` from the global metronome preferences (sound,
subdivision, accent pattern, style) and the project's tempo and signature. The transport
renderer calls the metronome renderer's `render` into scratch and sums it after the master bus
(the click is never attenuated by the master volume; it keeps the metronome's own level rules).
Switching the click on during playback publishes the grid and an anchor at the next beat; off
stops the voice with the existing 5 ms release.

**Rationale**: the constitution's metronome paragraph; the survey shows `anchor` tested at
`metronome_renderer_test.cpp:260/404` with the grid rules (tempo change recomputes from
`lastTickFrame_`). The tick-spacing invariant (62.5 ms ≥ 3 ms ramp + 5 ms release) still holds
because the grid range is unchanged; an anchor burst cannot shorten the spacing.

**Alternatives considered**: a second click scheduler inside the transport — duplicates what
the constitution forbids duplicating.

## R5. Audio files: keep the original, decode on a reader thread, no decoded cache

**Decision**: the project keeps the imported file byte for byte (spec clarification 2). At
project open, a worker thread opens one miniaudio `ma_decoder` per audio asset on its copy
(WAV, FLAC and MP3 through miniaudio's bundled dr_wav, dr_flac and dr_mp3 backends; output
32-bit float, source channel count, converted to the stream rate by miniaudio's resampler when
the rates differ; MP3 decoders are created with a seek table so seeks are not linear scans).
Playback streams from the decoders: a single `DiskStreamer` thread per engine refills one
`SpscFifo` of 16 384 frames per channel per clip source ahead of the playhead, primes the fifos
of every clip within the next two seconds before `play` and after a seek, and sleeps 10 ms when
every fifo is full; the callback only pops frames. A fifo that runs dry renders silence for that
clip and increments the `starvation` health counter; nothing waits. There is no decoded PCM
cache: the only derived file is the waveform overview (R10).

**Rationale**: the user chose originals; a decoded cache at 48 kHz float would cost 23 MB per
stereo minute (690 MB for the 30-minute limit) for no audible gain, while decoding on the reader
thread costs a few percent of one core. The architecture is the one every surveyed DAW uses
(NotebookLM: background reader, per-clip SPSC ring buffers of ~16 384 samples, prime on start and
seek, silence on underrun). miniaudio is already vendored (0.11.25, resampler only) and the
decoders are enabled by dropping `MA_NO_DECODING`, `MA_NO_WAV/FLAC/MP3` from the implementation
unit; licence public domain / MIT-0, already in the inventory.

**Verification during implementation**: `ma_decoder_config.seekPointCount` (MP3 seek table) is
confirmed in the vendored header before the decoder contract is frozen; if MP3 seeking proves
slower than 50 ms on the Pixel 6 for a 30-minute file, the fallback is a per-asset decoded PCM
cache for MP3 only, kept under `AppDirectories.cache` and rebuildable (the contract reserves the
path).

**Alternatives considered**: fully in-memory decoded clips (691 MB per 30-minute stereo file —
impossible on phones); platform decoders (`MediaCodec`, `AVAudioFile`) — two code paths that
behave differently, against Principle I's one engine; a decoded cache (rejected above); the
existing `SpscRingBuffer<T>` (overwrite-oldest, newest wins — the wrong semantics for a stream,
hence a plain `SpscFifo`).

## R6. Mixing: gain ramps, solo logic, master clamp

**Decision**: each `TrackStrip` applies a linear gain ramp over 10 ms (block-rate increments,
NotebookLM "5–20 ms") toward its target `gain × audible`; mute and solo become an `audible`
flag per track computed in Kotlin from the truth table (soloed set non-empty ⇒ only soloed and
not muted tracks are audible) and sent with the gain, so the engine knows no mixer semantics.
Mono clips feed both outputs at unity, stereo clips pass through; the `MasterBus` sums in float,
applies the smoothed master gain, and clamps to ±1.0 before the device. Peak metering, pan and
a limiter are feature 010.

**Rationale**: click-free changes within the spec's 20 ms (FR-024, SC-006); float summing has
headroom (brief §2.1–2.2); the clamp is the smallest protection that keeps the device from
wrapping.

**Alternatives considered**: solo logic in the engine — duplicates domain logic; a soft clipper
or look-ahead limiter now — audible colouring without a meter to see it; deferred to 010.

## R7. Telemetry: one seqlock snapshot for the playhead

**Decision**: the transport renderer publishes a `Seqlock<TransportSnapshot>` per block (frame
position at block start, playing flag, block time in nanoseconds, frames per beat) and the C ABI
exposes `tembra_transport_read(out)` which, like the metronome tick, computes the audible
position at call time by adding the elapsed time since the block and subtracting the output
latency. Kotlin reads it once per display frame inside draw lambdas (`rememberFrameTick`, the
tuner meter's pattern) for the playhead line, the ruler marker and the readout; semantics and
tests use a 10 Hz poll into `UiState` (the metronome's `currentBeat` pattern).

**Rationale**: Principle II's telemetry rule and the spec's 30 ms playhead budget; the pattern
exists twice already.

## R8. The project document: kotlinx.serialization JSON, schema-first, wire-ready

**Decision**: the document is a `@Serializable` tree in `:shared:core` (`project` package):
`Project` → `Track` → `Clip`, plus `AudioAsset`, with every entity carrying a stable `id`
(UUID v4 as a string), positions in beats, audio referenced by the asset's content identifier
(SHA-256 of the file), explicit `@SerialName` names, a top-level `formatVersion`, and nothing
device-specific (paths, caches, last-opened, selection live outside it). It is written as JSON
with `ignoreUnknownKeys = true`, `explicitNulls = false`, `encodeDefaults = true`, read through
a `ProjectDocumentMigrations` step keyed on `formatVersion`. The classes use
`kotlinx.collections.immutable` persistent collections so undo snapshots share structure.

**On FlatBuffers**: FlatBuffers' Kotlin code generation targets the Java runtime only; Kotlin/
Native (iOS) is not supported (FlatBuffers docs, `languages/kotlin.md`), so it cannot serialise
a `commonMain` document. The server path, when the collaboration feature arrives, is the same
`@Serializable` classes through `kotlinx-serialization-protobuf` or `-cbor` (both support
Kotlin/Native; the NotebookLM architecture answer) or JSON over HTTP — a format choice, not a
model change. The document follows FlatBuffers' own evolution rules anyway (never change a
field's type or meaning, only add fields with defaults, never reuse a name), which is what makes
it forward-compatible on either format.

**Rationale**: the constitution's stack (kotlinx.serialization JSON project files,
`kotlinx.collections.immutable`), spec FR-046 and FR-049, and the user's request for
abstractions that save to a server. JSON is inspectable in a pull request, which a binary
format is not.

**Alternatives considered**: FlatBuffers (JVM-only Kotlin); Protobuf now (binary files are not
reviewable; the classes can adopt `@ProtoNumber` later without a model change); a hand-written
format (no).

## R9. Storage: project folders, a JSON library index, autosave, no Room yet

**Decision**: `AppDirectories.documents/projects/<projectId>/` holds `project.json`, the audio
copies under `audio/<sha256>.<ext>` and nothing else; `AppDirectories.cache/projects/<projectId>/`
holds the overviews (`<sha256>.peaks`) and anything rebuildable. A single
`documents/projects/library.json` indexes the cards (id, name, description, key, tempo, length,
track count, size, last edited, origin, the card overview) and is rewritten on every save; it is
rebuilt from the folders when missing or stale. Writes stage to a temporary file and rename
(`OkioCustomClickSoundStore`'s pattern). Autosave debounces 500 ms after the last edit and never
waits longer than 2 s; the session also saves on leaving Timeline, on backgrounding and when the
host asks. Room KMP is not introduced in this feature (Complexity Tracking #1): two hundred
index entries are read in one go within SC-010's second, and the library needs no query engine
until sync state arrives.

**Rationale**: FR-046 to FR-054; Okio and `AppDirectories` exist; the `documents` directory is
documented as "backed-up storage for project folders" and `cache` as "waveform peak caches" — the
scaffold already intended this split.

**Alternatives considered**: Room for the index (KSP toolchain and schema migrations for a
≤200-row list; deferred to the feature that needs queries); one big file per project with the
audio inside (breaks the byte-for-byte originals and the content identifiers).

## R10. Waveform overviews: a peak-mip file per asset, built at import

**Decision**: the engine's offline `tembra_audio_overview_build(path, out_path)` decodes the
asset once on the calling (IO) thread and writes a little-endian file: a header (magic,
version, channels, frames, base bucket size 256, level count, buckets completed), the base level
(min/max int8 pairs per channel per 256 frames) and pyramid levels at 1024, 4096 and 16 384
frames per bucket; the base level is flushed every ten seconds of audio with the header's
"completed" count updated so the UI can draw a partial overview while a long file builds. Kotlin
loads the file into a `WaveformOverview` (one `ByteArray` per level) and the clip drawing picks
the level whose bucket is closest to one device pixel at the current zoom, then draws min/max
columns. The card overview of FR-054 is derived from the asset overviews, gains and mutes at
save time into the library index (48 spans, peak and loudest-track colour).

**Rationale**: FR-029, SC-007; the brief and NotebookLM (peak-mip pyramid at 256:1, 1024:1,
4096:1, computed by a background worker at import); int8 min/max keeps a 30-minute stereo file's
base level at 1.4 MB.

**Alternatives considered**: computing overviews in Kotlin from decoded chunks (an ABI chatter
of buffers); float buckets (4× the size for no visible gain).

## R11. Import: platform pickers, copy through Okio, inspect through the engine

**Decision**: `SoundFilePicker` (feature 004) is generalised into an `AudioFilePicker` seam
with a `pickOne()` and a `pickMany()` request over the MIME types `audio/wav`, `audio/x-wav`,
`audio/flac`, `audio/mpeg` (Android `OpenDocument`/`OpenMultipleDocuments`, iOS
`UIDocumentPickerViewController` with `allowsMultipleSelection`), returning content handles the
repository copies with Okio into a staging file while hashing (`HashingSource`, SHA-256). The
engine's offline `tembra_audio_inspect(path, out)` reports format, channels, rate and frames;
the repository refuses more than two channels, more than 30 minutes or an undecodable file, then
renames the staging file to `audio/<sha256>.<ext>` (a duplicate hash reuses the existing copy)
and builds the overview. A multiple pick runs the pipeline per file, collects refusals into one
message and commits one edit (one undo step).

**Rationale**: FR-044; Android's SAF and iOS security-scoped URLs are transient grants, which is
why the file must be copied at pick time (NotebookLM: copy into app storage); the metronome's
picker and store already do this for one WAV.

## R12. Background playback, calls, other apps, the stop-and-leave dialog

**Decision**: reuse the feature 004 machinery with names generalised: `BackgroundPlaybackHost`
stays the seam; `MetronomeForegroundService` becomes `PlaybackForegroundService`
(`mediaPlayback` type, notification "Playing <project>" / "Metronome", Stop action), the
Android focus request is `AUDIOFOCUS_GAIN`, iOS keeps `.playback` with the `audio` background
mode. The transport's `PlaybackPolicy` (a copy of the metronome's policy shape, in the session)
maps `AudioEvent.Interrupted`, `FocusLost`, `OutputRouteLost` and the host's stop request to
**pause, never resume** — this is what an incoming call, YouTube taking the stream, or unplugged
headphones produce on each platform. The user's "ask when navigating to another tab" is
FR-008a: a `PlaybackGuard` in the shell asks the `ProjectSession` whether playback is running
before `ShellViewModel.select()` and the system back action proceed; the dialog's "Stop and
leave" pauses then navigates.

**Rationale**: FR-008, FR-008a; the platform guidance was fetched and recorded for feature 004
(research R12 there) and the implementation exists; the metronome's names are the only thing to
generalise.

## R13. Session, ViewModels and separation of concerns

**Decision**: a `ProjectSession` (`@SingleIn(AppScope)`, package `audio.tembra.ui.session`)
owns the open project: the document and its `EditHistory` (immutable snapshots, 50 steps), the
`ProjectEditor` (pure edit functions in `:shared:core`), autosave through the
`ProjectRepository`, the engine binding (`TimelineAudio` seam: arrangement snapshots, transport
commands, metronome grid) and the `PlaybackPolicy`. `TimelineViewModel`, `TrackSettingsViewModel`
and `ProjectsViewModel` are thin: they map the session's `StateFlow<ProjectState>` and their own
view state (selection, zoom, sheets) into one immutable `UiState` each, and forward intents as
plain functions. Screens are the three seams of `compose-state-and-effects` (wiring, content,
components). No composable is nested inside another's file: each component is its own file and
type (Principle VII), and composition happens by slots and parameters.

**Rationale**: the user asked for separate components and separation of concerns; a
destination-scoped ViewModel dies when the user opens the track settings screen or the library,
so the document must outlive it; one session is also where 010 and 011 attach.

## R14. Timeline rendering and gestures

**Decision**: the track area is a `LazyColumn` of `TrackLane` rows (each one `Canvas` drawing
its clips' blocks, captions and waveform columns from the overviews) under a pinned
`TimelineRuler` `Canvas`, with one `PlayheadOverlay` `Canvas` on top that reads the transport
snapshot in its draw phase; all share a `TimelineViewport` (pure: pixels per beat, scroll in
beats, label density rules of FR-014, snapping of FR-018; tested in `commonTest`). Gestures:
`detectTransformGestures` for pinch zoom around the centroid, horizontal drag for scroll,
`onPointerEvent(Scroll)` for the wheel (modifier ⇒ zoom), tap and long press on lanes resolved
by the viewport to a clip or an empty spot (clarification 3: plain drag scrolls, long press then
drag moves). Scroll and zoom are wrapped in `PerfTrace.animation(TIMELINE_SCROLL/ZOOM)`; the
playhead draw in `PerfTrace.section(TIMELINE_PLAYHEAD_FRAME)`.

**Rationale**: the tuner meter and the tempo dial establish the Canvas-plus-pure-geometry
pattern; a lazy column keeps 32 tracks cheap; one overlay keeps the playhead a single draw-phase
read per frame.

**Alternatives considered**: one giant Canvas for all lanes (recomposes everything on any
change, no lazy recycling); Material components for clips (there are none).

## R15. Components: built in-house, from the design tokens

**Decision**: the user asked for own controls. New reusable components (each its own file,
`modifier` first optional, slots for caller content, `testTag` overridable): `TransportBar`,
`TransportButton`, `TimeReadout` (`tabularTimecode`), `MasterVolumeSlider` (on the existing
`TembraSliderDefaults`/`TembraSliderThumb`), `TimelineToolbar`, `GridPicker` (on `MenuPill`),
`ZoomReadout`, `TimelineRuler`, `TrackLane`, `TrackHeader`, `TrackHeaderCompact`, `ClipBlock`
(drawn, not composed), `GainReadout` (drag-to-set with keyboard), `InstrumentChip`,
`InstrumentLabelMenu`, `StopAndLeaveDialog`, `TrackSettingsForm`, `EffectsPlaceholder`,
`ProjectCard`, `ProjectOverviewChart`, `LibrarySearchField`, `LibraryFilterChips` (on
`TembraChipRow`), `SortMenu`. Reused from the metronome: `TempoDial`, `TempoStepper`,
`TempoSlider`, `TapTempoButton`, `TimeSignatureMenu`, `SubdivisionTiles`, `AccentCells`,
`StyleMenu`, `ClickSoundPicker`, `PlayPauseButton`, `MenuPill`. Colours: the six track tints come
from `DawColors` (lavender, rose and the clip tints already there; the plan adds the missing
tints as tokens with evidence from the pulled screens); icons through
`tools/fetch-material-symbols.sh`.

**Rationale**: the design's clip blocks, ruler and headers have no Material equivalent; the
metronome's components were built for reuse (constitution).

## R16. Perf instrumentation: journey, sections, counters, baseline profile

**Decision** (survey §"files to touch"): one new journey `timeline` on both platforms (open
Projects, open the demo card, wait for overviews, play eight bars, seek, pinch zoom in and out
as an animation interval, split, open and close track settings, toggle the metronome, pause,
skip to start); `stress` extended to play the demo project with the click on while scrolling;
new catalogue names `timeline.projectOpen`, `timeline.waveform`, `timeline.playheadFrame`,
`timeline.clipSplit`, `timeline.projectSave`, `timeline.libraryOpen`, `transport.play`,
`transport.seek` (new family `transport`), `engine.prime` (plus the existing `timeline.scroll`,
`timeline.zoom`, `engine.loadClip`); counters `audio.avgUs`, `audio.starvation`,
`audio.clipsActive` added to the health struct, the ticker, the JSON and the three pipeline
tables; the iOS `Metrics.swift` gains a category-aware section (engine names are emitted from
C++ under category `engine`). Anchors for every control the journey touches. The baseline
profile coverage file gains `timeline` and `projects`, the generator and comparison register the
journey, and the profile is regenerated and committed in the feature.

**Rationale**: constitution (every feature instruments itself; feature 008's rule); the
pipeline's extension points are enumerated in the survey.

## R17. Tests

**Decision**: Catch2 on the host: `TempoMap` conversions, `TransportClock` seek and advance,
`SpscFifo`, `DiskStreamer` against an in-memory decoder fake, `ClipSource` scheduling rendered
offline (clip starts within one frame at blocks of 96/256/1024, mid-clip start, the join after a
split, overlap precedence, the click anchored to the audio), `TrackStrip` ramps, `MasterBus`
clamp, `AudioDecoder` acceptance matrix on generated WAV fixtures plus committed tiny FLAC and
MP3 fixtures (synthesised tones, our own content), `OverviewBuilder` output, the engine state
rules with `FakeAudioDevice`. `commonTest` (JVM and iOS): the document (`ProjectEditor` edits,
history, solo/mute table, project length, snapping, ruler labels, beat/time conversions, JSON
round trip, unknown field, migration, portability — no path strings), the repository on
`okio-fakefilesystem` (folders, index rebuild, dedup, refusals, delete), the library search/
filter/sort, the ViewModels with `FakeAudioEngine` (extended), `FakeProjectRepository`,
`FakeAudioFilePicker`, `FakeBackgroundPlaybackHost`, the viewport geometry, the layouts, the
tokens. `uiTest` (simulator and device): the three screens and every component with scripted
telemetry, the dialog, the menus, keyboard and hover through injected interaction sources.
Device/simulator tests: the engine round trip (open transport, load demo assets, play, read
position, seek, pause), DataStore and file round trips, the foreground service host. Manual:
lock screen, calls, another app, headphones, the playhead against the audible position
(quickstart).

## R18. Demo project

**Decision**: a script `app/tools/make-demo-audio.py` (committed) synthesises four 16-bar loops
at 124 BPM (drums, bass, keys, pad; 31 s) as 48 kHz 16-bit WAV into
`app/shared/ui/src/commonMain/composeResources/files/demo/`; on first launch the repository
installs "Demo project" (124 BPM, 4/4, key "Dmin", description "Four loops to play with",
five clips across four tracks with labels) from those resources; "Restore demo project"
reinstalls it. Generated content needs no licence row; the script is the provenance.

**Alternatives considered**: bundling recordings (licence review for every file).

## R19. Dependencies added

| Dependency | Version | Licence | Why |
|---|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-serialization-json` + the Gradle plugin | 1.11.0 (already in the catalogue for a test) | Apache-2.0 | the project document (R8) |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.4.0 | Apache-2.0 | persistent collections for the document and history (R8) |
| miniaudio decoders (already vendored 0.11.25) | — | public domain / MIT-0 | WAV, FLAC, MP3 decoding and resampling (R5); the implementation unit's `MA_NO_DECODING`, `MA_NO_WAV`, `MA_NO_FLAC`, `MA_NO_MP3` defines are removed |

No forbidden library; every version lives in `libs.versions.toml`; rows go to
`THIRD_PARTY_LICENSES.md`. Turbine stays out (the existing `backgroundScope.collect` pattern
suffices).
