# Tembra Audio Constitution

Tembra Audio is a free, open-source mobile digital audio workstation for Android and iOS,
built as a Kotlin Multiplatform control plane and Compose Multiplatform UI on top of one shared
C++ real-time audio engine. This constitution states the rules every specification, plan, task
and pull request MUST satisfy. The research behind each rule lives in `docs/research/`; when a
rule and a research brief disagree, this document wins until it is amended.

## Core Principles

### I. Real-Time Engine Isolation (NON-NEGOTIABLE)

- Nothing Kotlin (JVM or Native) MUST ever execute on the audio callback thread. The C++ engine
  owns that thread; Kotlin only produces commands and reads telemetry.
- The two sides MUST communicate exclusively through lock-free single-producer/single-consumer
  FIFOs and atomics exposed by a plain C ABI. No callbacks from C++ into Kotlin.
- Inside the audio callback there MUST be no allocation, locks, blocking I/O, logging or
  unbounded work. The engine MUST accept a variable frame count per callback at 48 kHz.
- There is exactly one engine, shared by both platforms (Oboe on Android, AVAudioEngine or
  RemoteIO on iOS). Per-platform DSP is forbidden.

Rationale: Kotlin is garbage collected; a single GC pause on the render thread is an audible
dropout. This rule is the reason the engine is C++ at all.

### II. Shared Kotlin Control Plane, Compose Multiplatform UI

- The app MUST be built with Kotlin Multiplatform for Android and iOS, and every screen MUST be
  implemented once in Compose Multiplatform in `commonMain`. Platform code is limited to a thin
  I/O shell, the engine bridge actuals and OS entry points.
- The module graph is fixed as `:shared:core` (pure domain, no Compose, no platform APIs),
  `:shared:engine` (the `expect class AudioEngine` facade and its JNI / cinterop actuals),
  `:shared:data` (persistence), `:shared:ui` (Compose, ViewModels, navigation, DI graph), plus
  thin `:androidApp` and `iosApp`. Dependencies MUST point inward toward `:shared:core`; the
  domain module MUST never depend on engine, data or UI.
- Native build-helper modules (for example a `com.android.library` module that compiles the C++
  engine) MAY sit below `:shared:engine`; they contain no Kotlin business logic and nothing
  depends on them except `:shared:engine`.
- Presentation MUST follow MVVM with unidirectional data flow: one `ViewModel` per destination
  that holds state, exposing a single immutable `UiState` via `StateFlow`; a destination whose
  content is static (a placeholder) has none. Intents are plain functions. Screens MUST NOT
  talk to the engine directly.
- High-frequency telemetry (playhead, meters, at 60–120 Hz) MUST be read inside Compose draw or
  layout lambdas and MUST NOT flow through `StateFlow`, `mutableStateOf` at screen level, or any
  reducer.
- `expect`/`actual` is reserved for platform SDK access (`AudioEngine`, file paths). Business
  logic MUST use interfaces and dependency injection.

Rationale: one UI and one domain keep the two platforms identical and testable; the telemetry
rule prevents recomposition storms that would defeat the engine's low latency.

### III. Test-First (NON-NEGOTIABLE)

- Every change MUST follow red-green-refactor: write the failing test, make it pass, then
  refactor. Production code without a preceding failing test MUST NOT be merged.
- Every functionality MUST be covered by an automated test at the level where it can fail: pure
  logic by unit tests in `commonTest` run on the JVM and on the iOS simulator; engine DSP and
  FIFOs by C++ unit and offline-render tests; screens by Compose UI tests; behaviour that depends
  on real audio hardware, window resizing or pointer input by a test on a real device or
  emulator.
- Kotlin tests MUST NOT link the real engine; they use the fake `AudioEngine`. The engine has its
  own test target that runs without an audio device.
- A pull request that lowers coverage of a touched module, or adds a public function with no
  test exercising it, MUST NOT be merged.

Rationale: a DAW has no tolerance for regressions in timing, mixing or state; tests written
first are the only proof that a behaviour was specified before it was coded.

### IV. Adaptive, Input-Agnostic UI

- Every screen MUST adapt to window size classes, not device types: compact width uses the
  bottom navigation bar, expanded width uses the left rail and may show two panes. Layouts
  MUST be driven by the available window, so the same build works on phones, tablets,
  foldables and Android desktop mode (free-form resizable windows, external displays).
- Pointer and keyboard input MUST be first-class: every interactive control MUST show a hover
  state, every icon-only control MUST show a tooltip on hover, scroll wheel and trackpad
  gestures MUST scroll and zoom the timeline, and focus MUST be visible and traversable with a
  keyboard. Touch targets MUST be at least 44 dp.
- Nothing MUST overlap or misalign at any supported width: no clipped text, no controls hidden
  behind the transport bar, no content under system bars. Spacing, radii, typography and colour
  MUST come from the design tokens in `design/stitch/DESIGN.md` and `theme.json`; hard-coded
  values are defects.
- Layout correctness MUST be verified visually, not assumed: rendered screenshots of the
  composables at compact and expanded widths, captured on an emulator or device with the
  `android` CLI (`android screen`, `android layout`) or by host-side screenshot tests, MUST be
  compared against the design reference before a UI change is accepted.
- Every custom control MUST expose semantics (`contentDescription`, `stateDescription`,
  `testTag`) so screen readers and UI tests can reach it on both platforms.

Rationale: users run the same app on a phone in one hand and on a desk with a mouse; a DAW that
only works with fingers on a 6-inch screen is half a product.

### V. Document Model in Beats

- The Kotlin project document is the single source of truth. The engine holds a derived
  real-time view and MUST never be the authority for what the project contains.
- Everything musical MUST be stored in beats (clip starts, lengths, automation points, loop
  positions, markers) and converted to samples through the tempo map at render time.
- Document edits MUST be pure functions from state to state; undo/redo MUST be a history of
  immutable snapshots. The project file format MUST be versioned and forward-compatible
  (unknown keys ignored, explicit migrations).

Rationale: tempo changes, time-stretch and collaboration all break if positions are stored in
samples; a pure editor makes undo, autosave and sync mechanical.

### VI. Simplicity

- Build only what the current specification requires. Speculative abstractions, unused
  parameters, feature flags without a consumer, and "future-proofing" layers MUST NOT be added.
- A new third-party dependency MUST be justified in the plan against the decided stack in
  `docs/research/kmp-code-architecture.md` and MUST carry a permissive licence (MIT, Apache-2.0,
  BSD, ISC, Zlib, public domain). GPL, AGPL and commercial libraries MUST NOT be linked into the
  shipped binary; LGPL only if unavoidable and dynamically linked.
- Prefer the platform and the decided libraries over custom implementations; prefer deleting
  code over adding configuration.

Rationale: an open-source project with a small team survives on a small surface area and clean
licensing.

### VII. Code Organisation

- Every class, interface, object and enum MUST live in its own file named after it. Nested or
  inner classes are permitted only for private implementation details of the enclosing type
  (for example a private `State` holder); public or internal types MUST NOT be nested.
- Sealed hierarchies MAY keep their subtypes in one file only when each subtype is a small data
  holder with no behaviour; otherwise each subtype gets its own file.
- Files are grouped by feature package (`timeline`, `tuner`, `effects`, `pads`, `projects`,
  `transport`) inside the owning module, with `internal` visibility by default.
- No commented-out code, no TODO without an issue reference, no wildcard imports.

Rationale: one type per file keeps diffs reviewable, tests discoverable and navigation
predictable for humans and agents.

## Technology & Architecture Constraints

- Targets: Android (phones, tablets, desktop mode) and iOS (iPhone, iPad). Desktop and web are
  out of scope.
- Stack (decided 2026-09-17, details and versions in `docs/research/kmp-code-architecture.md`):
  Kotlin Multiplatform, Compose Multiplatform, multiplatform `androidx.lifecycle.ViewModel`,
  Jetpack Navigation 3 multiplatform with Material 3 adaptive, Metro for dependency injection,
  Room KMP for the library index, DataStore for preferences, `kotlinx.serialization` JSON
  project files, Okio for file I/O, `kotlinx.collections.immutable` for the document model.
- Engine stack (details in `docs/research/kmp-architecture-tools.md`): C++ with a C ABI, Oboe
  (Android), AVAudioEngine / RemoteIO (iOS), miniaudio and dr_libs for files, Signalsmith
  Stretch for time-stretch, KissFFT or pffft for spectral work, hand-written DSP with STK and
  Maximilian as references. JUCE, Tracktion, Rubber Band, KFR, Superpowered and élastique are
  forbidden.
- Design source of truth: `design/stitch/DESIGN.md` and `theme.json`. Primary DAW destinations
  are Timeline, Tuner, Effects, Pads and Projects; the navigation also carries Home (the start
  destination), Metronome and Profile. The transport bar is present on every DAW screen. Fonts
  are Space Grotesk and Inter, bundled; live numbers use tabular figures.
- Product name is "Tembra Audio".

## Development Workflow & Quality Gates

- All feature work flows through spec-kit: `/speckit-specify` (what, user-facing),
  `/speckit-plan` (how, technology), `/speckit-tasks`, `/speckit-implement`. Specifications
  MUST NOT contain technology choices; plans MUST cite this constitution's principles in their
  Constitution Check and justify any deviation.
- Build order is fixed: project scaffold, navigation shell, tuner, audio spine, transport and
  timeline, mixer, metronome, effects and automation, recording and loops and speed,
  collaboration. A plan MUST NOT pull later work forward without amending this order here. The
  tuner precedes the audio spine (amended 2026-09-26): it is the first feature to capture audio
  and produce sound, and its input and output paths MUST obey Principle I so the audio spine can
  grow from them.
- Definition of done for any task: failing test written first; tests green on the JVM and, for
  shared code, on the iOS simulator; engine changes green in the C++ test target; UI changes
  screenshot-verified at compact and expanded widths on Android and on the iOS simulator; no
  new lint or compiler warnings; one type per file; no forbidden dependency.
- Pull requests MUST state which principle each non-obvious decision serves and MUST include
  the screenshots used for UI verification.
- Continuous integration MUST run: JVM unit tests and Compose UI tests on every push; iOS
  simulator tests and C++ tests on every pull request; a device smoke test (launch, open the
  demo project, press play, playhead moves) on release branches.

## Governance

- This constitution supersedes CLAUDE.md, research briefs and any other practice document. When
  they conflict, this file wins and the other document is corrected.
- Amendments are made with `/speckit-constitution`, recorded in the Sync Impact Report, and
  versioned semantically: MAJOR for removing or redefining a principle, MINOR for adding a
  principle or materially expanding guidance, PATCH for clarifications. An amendment that
  changes an existing rule MUST name the specs and plans it invalidates.
- Every plan's Constitution Check and every pull request review MUST verify compliance with
  Principles I–VII. Violations are blocking, not advisory; complexity MUST be justified in the
  plan's Complexity Tracking table or removed.
- `CLAUDE.md` is the runtime guidance file for agents working in this repository; it MUST point
  at this constitution and MUST NOT restate rules in a way that drifts from it.

**Version**: 1.0.3 | **Ratified**: 2026-09-19 | **Last Amended**: 2026-09-26
