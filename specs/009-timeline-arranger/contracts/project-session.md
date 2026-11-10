# Contract: `ProjectSession`, edit history, engine binding, playback policy, stop-and-leave guard

**Where**: `:shared:ui`, package `audio.tembra.ui.session`; pure parts in `:shared:core`
(`ProjectEditor`, `EditHistory`, `PlaybackPolicy`). Research R12, R13; spec FR-005 to FR-008a,
FR-035, FR-046; data model §1, §5. Skill contracts: `kotlin-concurrency-and-flow` (one owner
scope, named launches, `StateFlow` for state, `Channel` for one-shot messages),
`kotlin-api-design` (edits as a sealed type parsed and applied by `ProjectEditor`).

## Responsibilities (one class, one job each; the session composes them)

| Collaborator | Job |
|---|---|
| `ProjectSession` (`@Inject @SingleIn(AppScope)`) | the open project's lifecycle: open, edit, undo/redo, save, play, pause, seek, metronome, import; exposes `state: StateFlow<SessionState>`, `messages: Flow<SessionMessage>`, `transportTelemetry` |
| `EditHistory` (`core`) | 50 immutable snapshots (data model §1) |
| `ProjectEditor` (`core`) | `apply(project, edit)` pure |
| `ArrangementMapper` | `Project` + open clip-source handles → `ArrangementSnapshot`; the solo/mute truth table → `audible`; `GainDb.toLinear()`; an asset's sources handed out first-fit over its clips by start (a source is free again a second after its clip ends; the halves of a split continue one source); `sourcesNeeded` says how many each asset wants (feature 012, 2026-11-09) |
| `ClipSourceRegistry` | asset id → its `ClipSourceHandle`s: one, plus one for every further clip of the asset that sounds at the same time (`ensure`, at most one per track; a source has one read position, and feature 012's stress project, the same loop on six tracks, starved five pops in six until 2026-11-09); opens on project open and on import, tops up after an edit that makes clips overlap, closes on project close and when an asset leaves the project **and** the history |
| `AutosaveScheduler` | contracts/project-storage.md |
| `PlaybackPolicy` (`core`) | pure transitions below |
| `OverviewTracker` | asset id → `OverviewStatus` from the repository's flows |
| `MetronomeBridge` | builds `MetronomeGrid.from(TickGrid.from(settings…), project.tempo)` from the global `MetronomePreferences` snapshot and the project's tempo/signature; pushes `setGrid` + `selectSound` on change; `setMetronome(on)` |

Launch sites (all on one `sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` provided by the graph, cancelled never — the session lives with the app):
`observeEngineEvents` (one collector of `AudioStatus.events`, FR: hot, no replay),
`observeMetronomePreferences`, `observeHostStopRequests`, `autosave`, `overviewPolling(assetId)`,
`importJob`. No `init` launches: `start()` is called once by the graph's `AppStartup` on first
composition (the shell's `LaunchedEffect(session)`). `AppLifecycleObserver` (a
`LifecycleEventObserver` installed by `App.kt` on `LocalLifecycleOwner`) calls `session.flush()`
on `ON_STOP` so backgrounding and termination save at once (FR-046); playback itself is not
touched by lifecycle events. `settingsDraft: MutableStateFlow<TrackSettingsDraft?>` carries an
open settings form between the nested screen and the inline row (FR-042).

## State

```kotlin
data class SessionState(
    val project: Project?,                 // null until the first open completes
    val assetsReady: PersistentMap<AudioAssetId, OverviewStatus>,
    val missingAssets: PersistentSet<AudioAssetId>,
    val playback: PlaybackState,           // Stopped(positionBeats) | Starting | Playing | Pausing
    val canUndo: Boolean, val canRedo: Boolean,
    val saving: Boolean,
    val metronomeOn: Boolean,
)
```

## Playback policy (pure, tested as a table)

| From | Event | To | Side effect |
|---|---|---|---|
| Stopped | Play | Starting | `keepAlive()`, `play()` |
| Starting | Started | Playing | — |
| Starting | Failed | Stopped | message "Could not start playback" |
| Playing | Pause / HostStop / Interrupted / FocusLost / RouteLost / LeaveConfirmed | Pausing | `pause()` then `release()`; position kept |
| Pausing | Paused | Stopped(position) | — |
| Playing | InterruptionEnded | Playing (no-op) | never auto-resume |
| Stopped | Interrupted / FocusLost / RouteLost | Stopped | — |
| any | Seek(beats) | same | `seek(beats)`; `following = true` in the ViewModel |
| any | SkipToStart | same | `seek(0)` |
| any | StepBar(±1) | same | `seek(barStart ± 1 bar)` |

Playback continues when the app backgrounds or the screen locks (no event). Opening another
project while playing: `pause()` first, then open (FR edge case).

## Engine binding rules

- `open(id)`: `pause` if playing → `closeTransport` → repository `open` → `openTransport` →
  open clip sources for every asset present (missing files recorded in `missingAssets`) →
  `setTempo`, `setMaster`, `setArrangement`, metronome bridge → state published → `setOpen(id)`.
- Every settled `ProjectEdit` (after `EditHistory.push`): gain/mute/solo → `setTrack` fast path
  for the affected tracks (and every track when the solo set changes); tempo/signature →
  `setTempo` then `setArrangement`; everything else → `setArrangement`. Import opens the new
  sources before the arrangement is pushed.
- Undo/redo re-push the arrangement (and `setTempo` if the tempo differs).
- Leaving the transport host (feature 010, T102): `transportOpen` clears at once and `closeTransport` is launched on the
  session scope under `transportMutex`; the engine runs it on `EngineDispatchers.blocking`, the one serial dispatcher the
  tuner's and the metronome's starts also use, so the hand-over of the device to the next screen keeps its order without
  the close blocking the main thread (before T102 the close was synchronous). A re-attach waits on the mutex.
- Asset retirement: when an asset is referenced by no snapshot in `past`, `present` or
  `future`, `closeClipSource` and `save(project, retiredAssets)`.
- `transportTelemetry.read(into)` forwards to `TimelineAudio.readTransport` once per distinct
  frame time (the metronome's `litBeatAt` pattern).

## Stop-and-leave guard (FR-008a)

```kotlin
interface PlaybackGuard { val isPlaying: Boolean; suspend fun confirmLeave(): Boolean }
```

`ProjectSession` implements it: `confirmLeave()` shows the `StopAndLeaveDialog` through a
`DialogRequest` channel the shell renders, suspends for the answer, and on "Stop and leave"
emits `LeaveConfirmed` and waits for `Stopped` before returning `true`. `ShellViewModel.select()`
and `back()` become `suspend` wrappers: if `guard.isPlaying` and the target is not Timeline (or the
track settings entry) they await `confirmLeave()` and navigate only on `true`. The dialog never
appears when stopped, between Timeline and its track settings, or on OS-level backgrounding.

## Messages (`Channel<SessionMessage>`, `receiveAsFlow`)

`ImportRefused(files: List<Pair<name, reason>>)`, `StartFailed`, `DocumentUnavailable(reason)`,
`AssetsMissing(count)`, `SaveFailed`.

## Tests (first, `commonTest`)

- `PlaybackPolicyTest`: the table above, every row.
- `ProjectSessionTest` with `FakeAudioEngine`, `FakeProjectRepository`, `FakeBackgroundPlaybackHost`,
  `FakeMetronomePreferences`: open pushes tempo, master, arrangement and grid in that order; a
  gain edit uses `setTrack`; a solo change updates every track; tempo edit re-pushes; undo re-pushes;
  import opens sources before the arrangement; retirement closes sources and passes
  `retiredAssets`; interruption pauses and never resumes; `confirmLeave` true/false paths;
  autosave timing with virtual time; opening another project pauses first.
- `ArrangementMapperTest`: solo/mute table → `audible`; clip order; bounds (65 tracks rejected
  with a message, not a crash).
