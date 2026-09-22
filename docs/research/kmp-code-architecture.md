# Kotlin-side source code architecture for Tembra Audio

**Status:** decided 2026-09-17. This brief fixes how the Kotlin Multiplatform side of the app is organised: Gradle module graph, presentation pattern, navigation, dependency injection, persistence, concurrency, native-interop layering and testing. It does not revisit the engine decisions in `kmp-architecture-tools.md` and `daw-internals.md`; it builds on them. Spec-kit plans must follow this brief or amend it here first.

**How it was decided.** A NotebookLM deep web-research run plus hand-added JetBrains and Google documentation and library docs (92 sources, notebook `6c22014f-6f99-4f7d-a0d7-aaab57fb6d29`) answered nine comparison questions (`notebooklm-kmp-architecture-qa.md`) and produced a briefing report (`notebooklm-kmp-architecture-report-en.md`). Four official reference repositories were cloned and inspected directly (`kmp-reference-repos.md`): JetBrains' production `kotlinconf-app`, Google's `Fruitties` sample and the two JetBrains app templates. Where the NotebookLM synthesis and the shipped reference code disagree, this brief says so and picks.

---

## 0. Decision table

| Concern | Decision | Runner-up | Why the runner-up lost |
|---|---|---|---|
| Module graph | Four shared Gradle modules (`:shared:core`, `:shared:engine`, `:shared:data`, `:shared:ui`) + C++ engine + thin apps | Now-in-Android style `:core:*` / `:feature:*` fan-out with a separate umbrella module | Every reference app JetBrains and Google ship uses one to two shared modules; a per-feature fan-out is premature for one team and slows Kotlin/Native builds with more klibs than it saves. The umbrella is unnecessary because `:shared:ui` is already the only module iOS links. |
| Build plumbing | Version catalog + `build-logic` convention plugins; new `com.android.kotlin.multiplatform.library` plugin for shared modules | `buildSrc`, `allprojects {}` blocks | Convention plugins keep six modules consistent and preserve Gradle configuration cache; the Android KMP library plugin gives single-variant shared modules. |
| Presentation | MVVM with multiplatform `androidx.lifecycle.ViewModel` + `StateFlow`, unidirectional data flow; document edits via a pure `ProjectEditor` in `:shared:core`; 60–120 Hz telemetry read in the Compose draw phase | Decompose components; Orbit MVI / MVIKotlin; Circuit; Molecule | Decompose duplicates what Navigation 3 and the multiplatform ViewModel now give officially and is the fallback only if SwiftUI screens ever appear. MVI libraries add ceremony the small domain does not need; the reducer discipline lives in `ProjectEditor` instead. Circuit and Molecule tie state production to the Compose runtime, which is exactly wrong for 120 Hz telemetry. |
| Navigation | Jetpack Navigation 3 multiplatform (`org.jetbrains.androidx.navigation3:navigation3-ui` 1.1.1) + Material 3 adaptive Navigation 3 + `lifecycle-viewmodel-navigation3` | Navigation 2 multiplatform (`navigation-compose` 2.9.2); Decompose | Nav3's app-owned back stack is what a persistent transport bar and tablet dual panes need; Nav2 hides the stack inside `NavHost`. Both JetBrains (`kotlinconf-app`) and Google (`Fruitties`) already ship Nav3. |
| Dependency injection | Metro (`dev.zacsweers.metro` 1.4.x), compile-time IR plugin, `metrox-viewmodel-compose` | Koin 4.2.x (runtime); manual `AppContainer` (Fruitties style) | Compile-time graph validation and zero runtime lookup, no KSP. JetBrains switched `kotlinconf-app` to Metro in shared code. Koin remains acceptable if Metro ever blocks a Kotlin upgrade; the graph is small enough that manual DI is the last-resort fallback. |
| Library / index DB | Room KMP 2.8.x with `BundledSQLiteDriver` | SQLDelight 2.x | Google-maintained, used by `Fruitties`, relational mapping without hand-written mappers. SQLDelight is the pick only if web targets ever matter. |
| Preferences | DataStore multiplatform (preferences) | `multiplatform-settings` | Audio-driver settings must be reactive `Flow`s with transactional writes; `multiplatform-settings` is synchronous. |
| Project file | kotlinx.serialization **JSON**, versioned schema, all musical time in beats, one folder per project with `project.json` + `audio/` + `peaks/` | CBOR or protobuf | Human-readable and diff-friendly matters for an open-source DAW and for collaboration later; switch the document body to CBOR only if measured parse time on low-end Android says so. |
| File I/O | Okio 3.x | kotlinx-io | DataStore already pulls Okio in transitively and both reference apps use it; kotlinx-io is still 0.x. Revisit when kotlinx-io reaches 1.0. |
| Undo/redo | Snapshot history of an immutable `ProjectState` using `kotlinx.collections.immutable` (structural sharing) | Command pattern with inverse operations | Snapshots need no inverse logic per edit and persistent collections make them cheap; a command log can be added later for collaboration without changing the editor API. |
| Concurrency | kotlinx.coroutines; `Dispatchers.Main` for UI, `Default` for peak/beat math, `IO` for files and DB; frame-synchronised telemetry via `withFrameNanos` | Fixed-interval polling with `delay()` | Fixed intervals drift against 60/120 Hz displays and burn CPU while paused. |
| Native interop | Plain C ABI header; `expect class AudioEngine` in `:shared:engine`; JNI via a `com.android.library` module built with CMake; cinterop `.def` on iOS against a static library; static iOS framework with symbol stripping; no SKIE | SKIE / Swift export; C++ headers into cinterop | The Swift shell is a few lines calling `MainViewController()`, so SKIE buys nothing today. cinterop cannot parse C++, hence the C ABI (already settled). |
| Testing | `kotlin.test` + `kotlinx-coroutines-test` + Turbine in `commonTest`, run on JVM and `iosSimulatorArm64`; `runComposeUiTest` for shared UI; Roborazzi screenshots on JVM; fake `AudioEngine`; Catch2 offline-render tests for the C++ engine | Kotest; linking the real engine into Kotlin tests | Kotest adds a second test DSL without a need; the real engine in Kotlin tests makes CI non-deterministic and needs audio hardware. |

All runtime dependencies above are Apache-2.0 (AndroidX, JetBrains, Metro, Okio, Turbine, kotlinx.*). Catch2 is BSL-1.0 and test-only. Nothing here changes rule 4 of the constitution.

---

## 1. Constraints this architecture serves

These come from CLAUDE.md and the engine briefs and are not renegotiated here:

1. Nothing Kotlin runs on the audio callback; Kotlin produces commands, C++ consumes them, through lock-free SPSC FIFOs and atomics.
2. One shared C++ engine behind `expect class AudioEngine`, JNI on Android, cinterop on iOS, variable frame count at 48 kHz.
3. Everything musical is stored in beats and converted through the tempo map at render time.
4. Permissive licenses only in the shipped binary.
5. Five primary destinations (Timeline, Tuner, Effects, Pads, Projects): bottom bar on phones, left rail on tablets, transport bar on every DAW screen (`design/stitch/README.md`).
6. Build order: audio spine first, then transport/timeline, mixer, metronome, effects + automation, recording + loops + speed, tuner, collaboration (`DAW-App-Research-Report.md` §6).

---

## 2. Module graph and build setup

```
app/                                   Gradle root (settings.gradle.kts, gradle/libs.versions.toml)
├── build-logic/                       convention plugins: tembra.kmp.library, tembra.kmp.compose, tembra.android.app
├── engine/                            C++ engine, CMake project, C ABI in include/tembra_engine.h, Catch2 tests
│   └── android/                       :engine:android  com.android.library + externalNativeBuild (CMake)
│                                        → libtembra_engine.so + JNI glue (tembra_jni.cpp)
├── shared/
│   ├── core/                          :shared:core     pure Kotlin domain (no Compose, no platform code)
│   ├── engine/                        :shared:engine   expect/actual AudioEngine, commands, telemetry, EngineSync
│   ├── data/                          :shared:data     Room KMP, DataStore, project files (Okio, kotlinx.serialization)
│   └── ui/                            :shared:ui       Compose Multiplatform screens, ViewModels, Navigation 3,
│                                                       Metro graph, iOS framework "TembraShared" (MainViewController)
├── androidApp/                        :androidApp      Activity + Application, nothing else
└── iosApp/                            Xcode project: Swift launcher embedding TembraShared.framework
```

Dependency direction (arrows mean "depends on"):

```
:androidApp ──► :shared:ui ──► :shared:engine ──► :shared:core
   iosApp  ──►      │      ──► :shared:data   ──► :shared:core
                    └──────────────────────────► :shared:core
:shared:engine (androidMain) ──► :engine:android (AAR with libtembra_engine.so)
:shared:engine (iosMain)     ──► cinterop def ──► libtembra_engine.a (built from app/engine by CMake)
```

**What lives where.**

- `:shared:core` holds the project model (`Project`, `Track`, `Clip`, `TempoMap`, `AutomationLane`, `EffectChain`, `Bus`), beat/sample math, the `ProjectEditor` (pure functions from `(ProjectState, Edit) -> ProjectState`), `UndoHistory`, the serialisable document schema and its migrations. Dependencies: kotlinx.serialization, kotlinx.collections.immutable, kotlinx.coroutines, kotlinx-datetime. No Compose, no platform APIs, no `expect` declarations. This is the module that runs thousands of fast tests on the JVM.
- `:shared:engine` holds `expect class AudioEngine` with `pushCommand`, telemetry readers, the `EngineCommand` and `Telemetry` types, and `EngineSync`, which turns two successive `ProjectState`s into the minimal command batch (add/remove node, set param at beat, swap graph, set tempo map). `androidMain` implements the actual over JNI; `iosMain` over cinterop. The FIFOs live in C++; Kotlin only calls thin `push`/`poll` functions from non-real-time threads. `commonMain` also ships `FakeAudioEngine` under a `testFixtures`-style source set so every other module can test without native code.
- `:shared:data` holds `ProjectRepository` (project folder read/write with Okio, autosave, recovery file), `LibraryDatabase` (Room KMP: projects index, loop library metadata, recent files) and `Preferences` (DataStore: audio device settings, metronome defaults, UI flags). Room's KSP processor is confined to this module so it never slows `:shared:core` or `:shared:ui` builds.
- `:shared:ui` holds the Compose theme built from `design/stitch/theme.json`, the design-system components, all screens, their ViewModels, the Navigation 3 graph, the Metro `AppGraph`, and the iOS entry point `fun MainViewController(): UIViewController = ComposeUIViewController { App() }`. It is the umbrella: its iOS framework is the single binary the Xcode project links, so there is no separate umbrella module.
- `:engine:android` exists only because the `com.android.kotlin.multiplatform.library` plugin has no `externalNativeBuild`; Google's documented recommendation is a standalone `com.android.library` module with CMake, consumed from `androidMain`. It builds `app/engine` for `arm64-v8a`, `armeabi-v7a` and `x86_64` and contains the JNI glue.
- `app/engine` is a plain CMake project with its own Catch2 test target. For iOS a Gradle task in `:shared:engine` invokes CMake for `ios-arm64` and `ios-arm64-simulator`, producing static libraries that the cinterop `.def` links via `linkerOpts`. The Kotlin framework is built with `isStatic = true` and `-Xbinary=strip=true`.

**Build plumbing.** One version catalog; three convention plugins in `build-logic` (a composite build, not `buildSrc`): `tembra.kmp.library` (targets `androidTarget`, `iosArm64`, `iosSimulatorArm64`, JVM toolchain, `kotlin.test`), `tembra.kmp.compose` (adds Compose Multiplatform and the compiler plugin), `tembra.android.app`. Kotlin Power-Assert is enabled for tests as in `kotlinconf-app`. Intermediate source sets are added only when needed (`nonAndroidMain` in `kotlinconf-app` is the pattern).

**When to split `:shared:ui`.** Split out `:feature:<destination>` modules only when one of these is true: the module's Kotlin/Native incremental compile exceeds about two minutes on the CI Mac, more than one team works in it concurrently, or a feature needs a dependency the rest of the UI must not see. Until then, package-per-destination inside `:shared:ui` (`ui/timeline`, `ui/tuner`, `ui/effects`, `ui/pads`, `ui/projects`, `ui/transport`) with `internal` visibility keeps the boundaries without the build cost.

---

## 3. Presentation architecture

**Pattern.** MVVM with unidirectional data flow. Each destination has one `ViewModel` (multiplatform `androidx.lifecycle.ViewModel`, `viewModelScope`) exposing a single immutable `UiState` through `StateFlow` and accepting intents through plain functions. ViewModels never touch the engine directly; they call the `ProjectEditor` and the `ProjectSession`.

**Three kinds of state, three paths.**

| Kind | Examples | Owner | Path to the screen |
|---|---|---|---|
| Document state | tracks, clips, tempo map, automation, mixer settings, effect params | `ProjectSession` (one per open project) holding `StateFlow<ProjectState>` + `UndoHistory` | ViewModel maps `ProjectState` to `UiState`; Compose collects with `collectAsStateWithLifecycle` |
| Transient UI state | selection, zoom, scroll, open sheet, drag in progress | the ViewModel (or `rememberSaveable` when purely visual) | `StateFlow` or Compose state |
| Telemetry | playhead position, per-track RMS/peak, input level, CPU load, xrun count | the C++ engine, read through `AudioEngine` atomics and pre-allocated arrays | **never** `StateFlow`; read in the draw phase, see below |

**Document editing.** `ProjectEditor.apply(state, edit): ProjectState` is a pure function in `:shared:core`; `Edit` is a sealed hierarchy (`MoveClip`, `SetGain`, `AddAutomationPoint`, ...). `UndoHistory` keeps `ArrayDeque<ProjectState>` snapshots; because `ProjectState` uses `PersistentList`/`PersistentMap`, snapshots share unchanged subtrees and a few hundred history entries cost little. Continuous gestures (fader drag) coalesce into one history entry on gesture end. After every apply, `EngineSync` computes the command batch between the previous and next state and pushes it; the engine is a derived view of the document, never the source of truth. This is the Tracktion "document model compiled to a graph" idea from `daw-internals.md` §6, expressed in Kotlin.

**Telemetry without recomposition storms.** Publishing 120 Hz values through `StateFlow` or `mutableStateOf` at the screen level recomposes the whole tree. The pattern is: a frame loop that runs only while the transport is playing or metering is armed, and reads that happen only inside draw lambdas.

```kotlin
@Composable
fun Playhead(engine: AudioEngine, tempoMap: TempoMap, pxPerBeat: () -> Float, modifier: Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine.isRunning) {
        while (engine.isRunning) withFrameNanos { tick++ }   // one invalidation per display frame
    }
    Spacer(modifier.fillMaxHeight().width(2.dp).drawBehind {
        tick                                                   // read here: only the draw phase re-runs
        val beats = tempoMap.samplesToBeats(engine.playheadSamples())   // atomic read, no allocation
        translate(left = beats * pxPerBeat()) { drawRect(playheadColor) }
    })
}
```

Meters use the same loop, reading into a pre-allocated `FloatArray` via `usePinned`/`addressOf` on iOS and a direct `FloatArray` JNI call on Android, then drawing bars in `drawBehind`. Threshold events that must reach composition (clip indicator, transport stopped) go through `derivedStateOf` or the ViewModel, not through the frame loop. Waveforms are drawn from pre-computed peak mip levels held in `:shared:data`, cached with `drawWithCache`, clipped to the viewport; the playhead is a separate layer so waveform bitmaps are not redrawn every frame. Static grid and clip fills live behind `graphicsLayer` so pan/zoom becomes a transform, not a redraw.

**Why not the alternatives.** Decompose is a good library, but it brings its own navigation and lifecycle model; with Navigation 3 and the multiplatform ViewModel both official and shipped by JetBrains in production, adopting Decompose would mean two overlapping models. Reconsider only if native SwiftUI screens are ever required. Orbit and MVIKotlin formalise the reducer, but this app already has exactly one reducer that matters (`ProjectEditor`) and it belongs in the domain, not the presentation layer. Circuit and Molecule produce state from `@Composable` functions, which couples state production to the Compose runtime; for telemetry that is the failure mode we are avoiding.

---

## 4. Navigation

**Library.** `org.jetbrains.androidx.navigation3:navigation3-ui` (1.1.1 as of 2026-09-17; Compose Multiplatform 1.10+ supports Navigation 3 on Android, iOS, desktop and web), `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` (ViewModel scoped to a `NavEntry`), and `org.jetbrains.compose.material3.adaptive:adaptive-navigation3` (`ListDetailSceneStrategy`).

**Shape.**

- The root `Scaffold` in `:shared:ui` owns the chrome: top bar, the `TransportBar`, and a `NavigationSuiteScaffold`-style switch between a bottom `NavigationBar` (compact width) and a `NavigationRail` (expanded width) driven by `WindowSizeClass` from `material3.adaptive`. `NavDisplay` sits inside; switching destinations mutates the back stack list and never recreates the transport bar.
- Keys are a `@Serializable sealed interface TembraKey : NavKey` with `data object Timeline`, `Tuner`, `Effects`, `Pads`, `Projects` and `data class EffectDetail(trackId, slot)`, `ProjectSettings`, `Export`, `TrackInspector(trackId)`. On non-JVM targets Navigation 3 cannot use reflection, so the back stack is created with the `rememberNavBackStack` overload that takes a `SavedStateConfiguration` with polymorphic serialisation for `TembraKey` (documented on the JetBrains Navigation 3 page). This also gives process-death and iOS background-termination restoration for free.
- Primary destinations are top-level keys (replace-top semantics); secondary screens push. On tablets `ListDetailSceneStrategy` renders Timeline as the list pane and `TrackInspector`/`EffectDetail` as the detail pane without changing navigation logic.
- Deep links (open a project file from Files, share sheet) map a URI to a key with `UriDeepLinkMatcher`; back gestures and predictive back go through `NavigationBackHandler`.

**Runner-ups.** Navigation 2 multiplatform (`navigation-compose` 2.9.2) is what the JetBrains templates still use and is stable, but `NavHost` owns the stack and dual-pane scenes are bolted on. Decompose's `ChildStack`/`ChildPanels` would also work and has the best state-restoration story, but see §3.

---

## 5. Dependency injection

**Library.** Metro, applied as a Gradle plugin (`dev.zacsweers.metro`), with `dev.zacsweers.metro:metrox-viewmodel-compose` for `metroViewModel<T>()` in Compose. Compile-time graph validation, no KSP, generated factories that work identically on Android and Kotlin/Native. `kotlinconf-app` uses exactly this stack in `commonMain` (`@DependencyGraph`, `@SingleIn(AppScope::class)`, `@GraphExtension`, `LocalMetroViewModelFactory`).

**Scopes.**

```
AppScope (process lifetime)            AudioEngine (native singleton), LibraryDatabase, Preferences,
                                       ProjectRepository, PeakCache, Clock
   └─ ProjectSessionScope (@GraphExtension, one per open project)
                                       ProjectSession (StateFlow<ProjectState> + UndoHistory),
                                       EngineSync, Autosave, LoopLoader
         └─ ViewModel scope (per NavEntry via lifecycle-viewmodel-navigation3)
                                       TimelineViewModel, MixerViewModel, EffectDetailViewModel, ...
```

The engine facade is created once at process start (before the first Compose frame, off the main thread, see §10) and is never recreated. Opening a project creates a `ProjectSessionGraph`; closing it disposes the graph, which stops autosave, clears undo history and sends `SwapGraph(empty)` to the engine. ViewModels receive the session through constructor injection, so a ViewModel can never outlive its project.

**Runner-ups.** Koin 4.2.x is what both JetBrains templates use and integrates with Navigation 3 (`koin-compose-navigation3`); it is the documented fallback if Metro's compiler plugin ever lags a Kotlin release the project needs. Manual DI (Google's `Fruitties` uses an `expect class Factory` and an `AppContainer`) is viable for a graph this size and is the last resort; the scoping above is written so either fallback is a mechanical change.

---

## 6. Persistence

**Project file.** A project is a folder (zipped for export/share as `.tembra`):

```
My Song.tembra/
├── project.json        kotlinx.serialization JSON: schemaVersion, tempo map, tracks, clips (beats), automation (beats),
│                       mixer, effect chains, markers, loop settings. Pretty-printed, ignoreUnknownKeys on read.
├── audio/              recorded and imported audio as WAV (written by the C++ engine via miniaudio/dr_libs)
└── peaks/              regenerable waveform mip levels (never backed up, can be deleted)
```

Reasons for JSON over CBOR/protobuf: readable diffs for an open-source project, trivially inspectable when a user reports a broken project, and no schema-tag discipline to maintain. `schemaVersion` plus explicit migration functions in `:shared:core` handle evolution. If parsing a large automation-heavy project is ever measured to be slow on a low-end Android device, the document body can switch to `kotlinx.serialization` CBOR with the same classes; that is a one-line format swap, not a redesign.

**Autosave and recovery.** `Autosave` in the session scope observes `ProjectState` with a two-second debounce, serialises on `Dispatchers.IO` to `project.json.tmp`, then atomically renames. On open, a leftover `.tmp` newer than `project.json` is offered as recovery.

**Library index.** Room KMP (`androidx.room` Gradle plugin, KSP, `BundledSQLiteDriver` from `androidx.sqlite:sqlite-bundled` so both platforms run the same SQLite build): tables for projects (path, name, tempo, duration, modified, thumbnail), loops/samples (path, tags, key, tempo, duration) and recents. Room's schema JSON is exported and committed as in `Fruitties`.

**Preferences.** DataStore multiplatform (`datastore-preferences-core`, Okio-backed) for audio device and buffer preferences, metronome defaults, last project, theme flags. Read as `Flow`, written transactionally, so the engine settings screen and the engine bootstrap observe the same source.

**File locations.** Android: app-specific external files for projects, cache dir for peaks. iOS: compute `NSDocumentDirectory` at runtime (container UUIDs change on reinstall), keep projects there so Files.app can see them, put `peaks/` under `Library/Caches`, and set `isExcludedFromBackup` on `audio/` when a project is large so iCloud backup quota is not blown. Never hard-code paths; both platforms expose them through a `Paths` object provided by the app graph.

---

## 7. Concurrency

- `Dispatchers.Main`: UI, collecting `StateFlow`s, the frame-synchronised telemetry loop (`withFrameNanos` is only available in a Compose frame clock context, so the loop lives in the UI layer, never in a ViewModel).
- `Dispatchers.Default`: waveform peak generation, beat/sample conversions for the timeline grid, project diffing in `EngineSync`.
- `Dispatchers.IO`: project folder I/O, Room, DataStore, loop decoding hand-off.
- Loader work that must feed the engine (decoding a loop to PCM, building a new graph node) is done by the C++ loader thread; Kotlin only asks for it via a command and learns completion via telemetry, per `daw-internals.md` §8.2.
- Kotlin/Native uses the shared-heap memory model with a concurrent GC, so no freezing rules apply, but GC pauses are exactly why no Kotlin runs on the audio thread. GC logging (`-Xruntime-logs=gc=info`) and safepoint signposts (`kotlin.native.binary.enableSafepointSignposts=true`) are on in debug builds so pauses show up in Xcode Instruments.
- ViewModels are cleared by Navigation 3 entry decorators on both platforms because the UI is Compose everywhere; the "call `clear()` from Swift `deinit`" pitfall only applies to native-UI apps.

---

## 8. Native interop layering

```
:shared:ui  ──► :shared:engine  (Kotlin API: AudioEngine, EngineCommand, Telemetry, EngineSync)
                     │ androidMain: JNI actual  ──► :engine:android (libtembra_engine.so + tembra_jni.cpp, CMake)
                     │ iosMain:     cinterop actual ──► tembra_engine.def ──► libtembra_engine.a (CMake, per arch)
                     ▼
                app/engine  C++ core: RT graph, DSP, FIFOs, platform I/O (Oboe / AVAudioEngine)
                            include/tembra_engine.h is the only header Kotlin ever sees (extern "C")
```

- **C ABI** (`tembra_engine.h`): `tembra_engine_create/destroy`, `tembra_engine_start/stop`, `tembra_engine_push_command(const uint8_t*, size_t)` (commands are serialised into a fixed binary layout by Kotlin, decoded by C++ off the RT thread), `tembra_engine_playhead_samples()` (atomic load), `tembra_engine_read_meters(float*, size_t)` (copies the latest snapshot), `tembra_engine_stats(...)` (xruns, CPU load). No callbacks into Kotlin, ever.
- **iOS**: `src/nativeInterop/cinterop/tembra_engine.def` with `headers = tembra_engine.h`, `package = audio.tembra.engine.native`, `linkerOpts` pointing at the CMake-built static library for the current target. The `actual class AudioEngine` pins Kotlin arrays with `usePinned { addressOf(0) }` for meter reads.
- **Android**: `:engine:android` builds the C++ core and a JNI shim; `actual class AudioEngine` declares `external fun` methods and loads `tembra_engine` in a companion `init`. Meter reads pass a pre-allocated `FloatArray` (JNI copies into the array; no `ByteBuffer` needed at 60 Hz).
- **Testing seam**: `AudioEngine` is behind an interface in `commonMain`; `FakeAudioEngine` records commands and plays back scripted telemetry, so ViewModel and `EngineSync` tests never load native code.
- **Not used**: SKIE and Swift export, because the Swift side only calls `MainViewController()`. Add SKIE the day a SwiftUI view needs a Kotlin `Flow`.

---

## 9. Testing

| Layer | Tooling | Where it runs |
|---|---|---|
| `:shared:core` domain, `ProjectEditor`, `UndoHistory`, schema migrations | `kotlin.test`, Power-Assert, property-style tests for beat/sample math | JVM on every PR; `iosSimulatorArm64Test` nightly and before release |
| ViewModels, `EngineSync`, repositories | `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `Dispatchers.setMain`), Turbine for `StateFlow`, `FakeAudioEngine`, Room in-memory with `BundledSQLiteDriver` | JVM and iOS simulator |
| Shared Compose UI | `runComposeUiTest` in `commonTest` with `testTag`s (which also map to iOS `accessibilityIdentifier`) | JVM (desktop) on PRs; iOS simulator nightly |
| Visual regression | Roborazzi on the JVM against golden images of the design-system components and each screen in phone and tablet widths | JVM |
| C++ engine | Catch2: unit tests for FIFOs, smoothing, pan law, tempo map; offline render tests that process a scripted command list and compare output WAV to goldens within tolerance | Linux and macOS CI, no audio device |
| Device smoke | One Android instrumented test and one XCUITest that launch, open a demo project and press play, asserting the playhead moves | Emulator / simulator on release branches |

---

## 10. Production pitfalls the scaffold must handle from day one

- iOS `Info.plist` needs `CADisableMinimumFrameDurationOnPhone = true` or ProMotion stays at 60 Hz.
- Build the iOS framework static and stripped; expect Compose Multiplatform to add roughly 9 MB to the iOS binary.
- Warm up Skiko and create the engine off the main thread during the launch screen; do not open the audio stream until the first screen is composed.
- Kotlin/Native crash reporting: route unhandled Kotlin exceptions through `NSExceptionKt` or Touchlab CrashKiOS so traces symbolicate.
- Keep `expect`/`actual` for platform SDK access only (`AudioEngine`, `Paths`); business logic uses interfaces and DI.
- Embedding native views through `UIKitView` costs a 150 ms gesture disambiguation delay; the design needs none, so do not embed any.
- Compose semantics map to VoiceOver automatically; every custom control (fader, knob, pad) must set `contentDescription`, `stateDescription` and a `testTag`.
- Bundle Space Grotesk and Inter; use `FontFeatureSettings("tnum")` on every live number (`design/stitch/README.md`).

---

## 11. Versions to start from (as shipped on 2026-09-17)

| Component | Version | Evidence |
|---|---|---|
| Kotlin | 2.4.10 | `kotlinconf-app` catalog |
| Compose Multiplatform | 1.11.1 | `kotlinconf-app` catalog |
| Navigation 3 multiplatform (`navigation3-ui`) | 1.1.1 | JetBrains docs, `kotlinconf-app` |
| Lifecycle multiplatform (ViewModel, `lifecycle-viewmodel-navigation3`) | 2.10.0 | JetBrains Navigation 3 page |
| Material 3 adaptive (`adaptive`, `adaptive-navigation3`) | 1.3.0-beta02 | JetBrains adaptive layouts page |
| Metro | 1.4.0 | `kotlinconf-app` catalog |
| Room KMP | 2.8.3 | `Fruitties` catalog |
| DataStore | 1.1.7 | `Fruitties` catalog |
| Koin (fallback only) | 4.2.2 | both JetBrains templates |
| Android Gradle Plugin | current stable with `com.android.kotlin.multiplatform.library` | Android KMP plugin docs |

The NotebookLM report quotes older versions (Kotlin 2.1.0, Navigation 3 1.0.0-alpha01, Room 2.7.0-alpha01); trust this table and the reference repos, not the report.

---

## 12. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Metro's compiler plugin lags a Kotlin release the project needs | Pin Kotlin to versions Metro supports; the scoping in §5 is designed so a swap to Koin is mechanical |
| Navigation 3 multiplatform API churn before 2.0 | Keep all `NavDisplay` wiring in one file (`ui/navigation/TembraNavHost.kt`); ViewModels never see navigation types |
| Telemetry loop accidentally routed through composition by a contributor | Lint rule / code review checklist: no `StateFlow<Float>` for meters, no `mutableStateOf` written from the frame loop except the tick counter; a Compose compiler metrics check in CI |
| CMake for iOS integrated into Gradle is fiddly | Own it in one Gradle task in `:shared:engine` with a cached output; document in `app/README.md`; run it on the macOS CI lane only |
| Kotlin/Native compile time grows with `:shared:ui` | Measure per PR; apply the split trigger in §2 |
| iCloud backup quota and Files.app expectations on iOS | Follow §6 file locations; test with a 2 GB project on a real device |

---

## 13. Open questions the research could not settle

Settle each with a spike inside the audio-spine feature, not by more reading:

1. **Command encoding across the FIFO.** Fixed binary layout serialised by Kotlin vs. a small set of typed C functions. Measure allocation and call overhead for 1,000 `SetParam` commands per second on a mid-range Android device.
2. **Project document size.** Generate a synthetic project with 50,000 automation points; measure JSON vs. CBOR load time on a low-end Android device and an iPhone SE. Keep JSON unless load exceeds one second.
3. **Waveform rendering strategy at zoom.** Cached bitmaps via `drawWithCache` vs. redrawing peak mips per frame; measure p99 frame time during pinch-zoom on both platforms.
4. **Android CMake in `:engine:android` vs. Kotlin/Native `androidNative*` targets.** Google documents both; start with CMake because Oboe's own build is CMake, revisit if the AGP path proves awkward.

---

## 14. Alignment with the build order

- **Project scaffold (first spec-kit feature)** creates the module graph in §2 with empty modules, the convention plugins, the version catalog, the Metro `AppGraph`, the Navigation 3 shell with five empty destinations and the transport bar, `FakeAudioEngine`, and CI lanes for JVM, iOS simulator and Catch2.
- **Audio spine** fills `app/engine`, `:engine:android` and `:shared:engine`, proving one clip plays through Oboe and AVAudioEngine and that telemetry reaches a `Playhead` drawn per §3.
- **Transport + timeline** brings `ProjectState`, `ProjectEditor`, `UndoHistory`, `EngineSync` and the timeline screen.
- Everything after that adds `Edit` cases, engine commands and screens without touching this architecture.

---

## Sources

- NotebookLM notebook `6c22014f-6f99-4f7d-a0d7-aaab57fb6d29` (92 sources) and its outputs: `notebooklm-kmp-architecture-qa.md`, `notebooklm-kmp-architecture-report-en.md`.
- Reference repositories inspected from source: `kmp-reference-repos.md` (JetBrains `kotlinconf-app`, Google `kotlin-multiplatform-samples/Fruitties`, `Kotlin/KMP-App-Template`, `Kotlin/KMP-App-Template-Native`).
- JetBrains: Navigation 3 in Compose Multiplatform (`kotlinlang.org/docs/multiplatform/compose-navigation-3.html`), Adaptive layouts, Multiplatform ViewModel, Multiplatform project configuration, Build final native binaries, C interop and definition files, Compose Multiplatform testing, iOS accessibility and touch interop, Kotlin/Native memory manager.
- Google: Kotlin Multiplatform overview and Android KMP library plugin (`developer.android.com/kotlin/multiplatform/plugin`), Room for KMP, DataStore for KMP, Navigation 3 guide and `nav3-recipes`, Compose performance best practices and phases, Modularization patterns, Now in Android learning journeys.
- Libraries: Metro (`zacsweers.github.io/metro`), Koin, kotlin-inject, Decompose, MVIKotlin, Orbit, Circuit, Molecule, Voyager, SQLDelight, kotlinx.serialization, kotlinx-io, Okio, Turbine, SKIE, KaMPKit, Confetti.
- Existing Tembra briefs this builds on: `kmp-architecture-tools.md`, `daw-internals.md` §8, `design/stitch/README.md`, `DAW-App-Research-Report.md` §6.
