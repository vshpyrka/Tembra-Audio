# Implementation Plan: Adaptive Navigation Shell and Home

**Branch**: `002-adaptive-navigation-shell` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-adaptive-navigation-shell/spec.md`

## Summary

Replace the scaffold's placeholder screen with the app's persistent frame and six decoupled
destinations. Navigation 3 (`navigation3-ui` 1.1.2, multiplatform) renders a two-entry back
stack (`[Home]` or `[Home, current]`) that a `ShellViewModel` derives from one `StateFlow`; each
destination is an independent composable in its own package, reached through a single
`entryProvider` mapping. The frame adapts to the real window through Material 3 adaptive
(`currentWindowAdaptiveInfo()`, window size classes at 600 / 840 dp and 480 dp height):
`NavigationSuiteScaffoldLayout` places a 64 dp bottom bar under 600 dp and a 64 dp left rail
from 600 dp, both drawn by new design-system components that match the Stitch geometry; from
840 dp a custom Navigation 3 `SceneStrategy` lays the current entry beside a 320 dp supporting
pane when the entry declares one (only Home, with an empty "Library" state). Destination
switches fade over the design system's new 300 ms motion token and snap under reduce motion,
which Compose already reports on both platforms. Six Material Symbols glyphs are bundled as
normalised vector assets under a `TembraIcon` registry. The design-token preview survives as a
debug-only developer view opened by a launch argument; the counter screen is deleted.
Technology evidence and alternatives: [research.md](research.md) R1–R12.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform, Kotlin/Native for iOS); Swift 5 for a
one-line change in the iOS shell; Bash/Python for the glyph-fetch script.

**Primary Dependencies** (added, all in the decided stack, R11): `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.2`
(+ `androidx.navigation3:navigation3-runtime:1.1.7` transitively),
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0`,
`org.jetbrains.compose.material3.adaptive:adaptive:1.2.0`,
`org.jetbrains.compose.material3:material3-adaptive-navigation-suite:1.9.0`
(+ `androidx.window:window-core:1.4.0` transitively). Unchanged: Compose Multiplatform 1.12.1,
Material 3 1.9.0, lifecycle 2.11.0, Metro 1.4.4, compose resources. No serialization plugin,
no icon library, no icon font.

**Storage**: N/A (navigation state is in-memory; relaunch starts on Home).

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`; `runComposeUiTest` in
`uiTest` (iOS simulator and Android device) with `mainClock` control and a `MotionDurationScale`
effect context for motion cases; JVM `androidHostTest` for repository-file checks (icon assets,
theme parity); one Espresso `pressBack()` test in `androidDeviceTest`; unchanged engine lane.
Details: R10 and the contracts.

**Target Platform**: Android 8.0+ (API 26) phones, foldables, tablets and desktop-mode windows;
iOS 16+ iPhone and iPad including Split View / Stage Manager resizes.

**Project Type**: mobile app, shared Compose Multiplatform UI in `:shared:ui`.

**Performance Goals**: a destination switch starts within 100 ms of the tap and completes in
300 ms; no blank frame; layout changes on live resize recompose within one frame; the six
vector icons parse once and are remembered.

**Constraints**: nothing touches the audio engine; all visual values from `TembraTheme`
(chrome metrics, motion and icons become tokens); one type per file; zero compiler and lint
warnings (hence adaptive 1.2.0's non-deprecated `currentWindowAdaptiveInfo()`); stable Material
3 pairing (no `@ExperimentalMaterial3AdaptiveApi` opt-in); developer view unreachable from any
control and gated to debug builds; tests independent of animation timing.

**Scale/Scope**: about 35 new Kotlin files, 6 vector assets, 1 licence text, 1 script,
about 20 test classes, 2 platform shell edits, 4 catalog entries, README and licence inventory
rows; 6 files deleted.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | No destination, ViewModel or component calls `AudioEngine`; `App` only forwards the graph. No audio thread exists. | PASS (not exercised) |
| II. Shared Kotlin Control Plane, Compose UI | Everything new is in `:shared:ui` `commonMain`; module graph unchanged; platform code limited to reading a launch argument in `MainActivity` / `ContentView`. Navigation state follows MVVM: `ShellViewModel` exposes one immutable `ShellUiState` via `StateFlow`, intents are `select` / `back`; screens never touch the engine. Placeholder destinations have no state and therefore no ViewModel (Complexity Tracking #1); the entry-scoped ViewModel decorator is wired so later destinations get one each. No `expect`/`actual` added (reduce motion comes from Compose's own `MotionDurationScale`, R7). No high-frequency telemetry. | PASS |
| III. Test-First | Every contract lists its tests before code: registry, state, layout mapping, motion, chrome metrics, developer-view parsing on JVM and iOS; UI tests for the three layouts, selection, layout switching, keyboard, touch targets, transitions and reduce motion; Android back on device; icon asset hygiene on the JVM. Kotlin tests use `FakeAudioEngine` only where a graph is needed (none in this feature). | PASS |
| IV. Adaptive, Input-Agnostic UI | Layouts keyed on window size classes measured from the real window (R3), not device type; bottom bar under 600 dp, rail from 600 dp, rail and pane from 840 dp, pane hidden under 480 dp height. Hover, focus, keyboard activation, 44 dp targets and `role = Tab` / `selected` semantics on every item; no icon-only control (labels are always visible). All values from tokens (new chrome metrics and motion tokens). Screenshots at compact, medium and expanded widths on both platforms plus a large-font capture, reviewed against the Stitch bar and rail references (quickstart §9). | PASS |
| V. Document Model in Beats | No document or musical time. | PASS (not exercised) |
| VI. Simplicity | Four dependencies, all named in `docs/research/kmp-code-architecture.md`, permissive licences (R11). No `SupportingPaneScaffold` (experimental, navigator state the spec forbids, R5), no Material scene strategy (needs two keys, R2), no serialization plugin, no icon library; the custom scene strategy is about 40 lines. Placeholder counter deleted. Custom bar/rail composables are required by the design geometry (Complexity Tracking #3). | PASS |
| VII. Code Organisation | One type per file, listed in Project Structure; packages `navigation`, `designsystem`, `devtools`, `home`, `timeline`, `tuner`, `metronome`, `projects`, `profile`; `internal` by default except the contracts' public surface. | PASS |
| Technology & Architecture Constraints | Navigation 3 + Material 3 adaptive + multiplatform ViewModel + Metro, as decided; design tokens from `design/stitch/theme.json` plus screen-derived chrome metrics with evidence (R8); fonts unchanged; product name "Tembra Audio". The constitution's primary-destination list stays as written: this feature ships Home, Timeline, Tuner, Metronome, Projects and Profile per the clarified spec, and Effects and Pads join with their features. | PASS |
| Development Workflow & Quality Gates | This feature sits between the scaffold and the audio spine in the build order; it implements no later feature's functionality (every destination except Home is a placeholder and there is no transport bar), so nothing is pulled forward. Definition of done maps to the four lanes plus screenshots (quickstart §2, §9). | PASS |

**Post-design re-check (after Phase 1)**: unchanged. The three interpretations that a reviewer
could read as deviations are recorded in Complexity Tracking with the simpler alternative and
why it was rejected.

## Project Structure

### Documentation (this feature)

```text
specs/002-adaptive-navigation-shell/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R12 with evidence
├── data-model.md        # Phase 1: destination registry, navigation state, layout mode, icon set, motion, chrome metrics, developer view
├── quickstart.md        # Phase 1: build/run/test, the three layouts, panel, input, icons, reduce motion, developer view, screenshots
├── contracts/
│   ├── navigation-shell.md          # Destination, DestinationKey, ShellUiState/ViewModel, LayoutMode, NavigationShell, NavDisplay wiring, scene strategy, feature packages, semantics, tests
│   ├── design-system-additions.md   # TembraIcon/TembraIcons, TembraMotion, chrome metrics, TembraTopBar/NavigationBar/NavigationRail/SupportingPane, EmptyState, fetch script, licence
│   └── developer-view.md            # DeveloperView, DeveloperViewRequest, AppContent, platform launch arguments
├── checklists/requirements.md
├── screenshots/         # created during implementation: <platform>-<compact|medium|expanded>-<destination>.png (+ large-font)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── gradle/libs.versions.toml                       + androidx-navigation3-ui 1.1.2, androidx-lifecycle-viewmodel-navigation3 2.11.0,
│                                                     compose-material3-adaptive 1.2.0, compose-material3-adaptive-navigation-suite 1.9.0
├── THIRD_PARTY_LICENSES.md                         + one row per R11 entry; Material Symbols row with the pinned commit
├── README.md                                       module map + "where does X go" rows for a destination, an icon, a chrome component, a dev view
├── tools/fetch-material-symbols.sh                 new: download + normalise glyphs (R6), pinned commit, idempotent
├── androidApp/
│   ├── build.gradle.kts                            buildFeatures.buildConfig = true
│   └── src/main/kotlin/audio/tembra/android/MainActivity.kt      reads "tembra.developer.view" inside if (BuildConfig.DEBUG)
├── iosApp/iosApp/ContentView.swift                 reads --tembra-developer-view inside #if DEBUG, calls MainViewController(developerView:)
└── shared/ui/
    ├── build.gradle.kts                            + the four dependencies in commonMain
    ├── src/commonMain/composeResources/
    │   ├── drawable/ic_{home,graphic_eq,music_note,av_timer,folder,person}_24.xml     normalised Material Symbols Outlined glyphs
    │   └── files/LICENSE-MaterialSymbols.txt
    ├── src/commonMain/kotlin/audio/tembra/ui/
    │   ├── App.kt                                  App(appGraph, developerView = DeveloperViewRequest(null)); AppContent.kt (graph-free branch)
    │   ├── theme/
    │   │   ├── TembraIcon.kt, TembraIcons.kt       icon registry + vector accessor (contracts/design-system-additions.md)
    │   │   ├── TembraMotion.kt, LocalTembraMotion.kt, ReduceMotion.kt   motion tokens; rememberReduceMotion()
    │   │   ├── TembraSpacing.kt                    + chrome metrics (data-model.md §6)
    │   │   ├── TembraTheme.kt                      + motion accessor, provides LocalTembraMotion
    │   │   └── WindowWidth.kt                      + from(WindowSizeClass)
    │   ├── designsystem/
    │   │   ├── NavigationItemSpec.kt, TembraTopBar.kt, TembraNavigationBar.kt, TembraNavigationRail.kt,
    │   │   ├── TembraSupportingPane.kt, EmptyState.kt, PlaceholderDestination.kt
    │   ├── navigation/
    │   │   ├── Destination.kt, DestinationKey.kt, ShellUiState.kt, ShellViewModel.kt
    │   │   ├── LayoutMode.kt, RememberLayoutMode.kt
    │   │   ├── NavigationShell.kt, NavigationShellContent.kt, TembraNavDisplay.kt, DestinationContent.kt
    │   │   └── SupportingPane.kt, SupportingPaneKey.kt, SupportingPaneScene.kt, SupportingPaneSceneStrategy.kt, SupportingPaneContent.kt
    │   ├── home/HomeScreen.kt, home/LibraryPane.kt
    │   ├── timeline/TimelineScreen.kt, tuner/TunerScreen.kt, metronome/MetronomeScreen.kt, projects/ProjectsScreen.kt, profile/ProfileScreen.kt
    │   ├── devtools/DeveloperView.kt, DeveloperViewRequest.kt, DeveloperViewScreen.kt, DesignTokenPreview.kt (moved from theme/)
    │   └── placeholder/                            DELETED (PlaceholderScreen, PlaceholderScreenContent, PlaceholderViewModel, PlaceholderUiState)
    ├── src/iosMain/kotlin/audio/tembra/ui/MainViewController.kt   MainViewController(developerView: String? = null)
    ├── src/commonTest/kotlin/audio/tembra/ui/
    │   ├── navigation/{DestinationTest,ShellUiStateTest,ShellViewModelTest,LayoutModeTest}.kt
    │   ├── theme/{TembraMotionTest,ChromeMetricsTest}.kt
    │   └── devtools/DeveloperViewRequestTest.kt
    ├── src/androidHostTest/kotlin/audio/tembra/ui/theme/IconAssetsTest.kt          (+ ThemeJsonParityTest unchanged)
    ├── src/uiTest/kotlin/audio/tembra/ui/
    │   ├── navigation/NavigationShellTest.kt
    │   ├── designsystem/{TembraNavigationBarTest,TembraNavigationRailTest,TembraTopBarTest,TembraSupportingPaneTest}.kt
    │   ├── theme/ReduceMotionTest.kt
    │   ├── AppContentTest.kt
    │   └── devtools/DesignTokenPreviewTest.kt      (moved)
    │   └── placeholder/PlaceholderScreenTest.kt    DELETED; commonTest/placeholder/PlaceholderViewModelTest.kt DELETED
    └── src/androidDeviceTest/kotlin/audio/tembra/ui/navigation/AndroidBackTest.kt
```

**Structure Decision**: all new code lives in `:shared:ui`, as the module graph and
`app/README.md` prescribe for screens, ViewModels, navigation and the design system. Three new
packages hold the reusable parts (`designsystem` for chrome components, `navigation` for the
shell and Navigation 3 wiring in one place, `devtools` for debug-only views) and one package
per destination holds a screen that is independent of the shell. No new Gradle module, no
change to `:shared:core`, `:shared:engine` or `:shared:data`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| #1 Placeholder destinations have no ViewModel (Constitution II reads "one ViewModel per destination") | Five of the six destinations render a title and one sentence from the registry; they have no state, no intents and no dependencies. The navigation state itself does have a ViewModel (`ShellViewModel`). The entry-scoped decorator is already wired so each later feature adds its ViewModel to its entry without touching the shell. | Six empty ViewModels would be code without a consumer (Constitution VI) and would be deleted by the very features that replace the placeholders. |
| #2 On iOS the developer view's composable remains in the release framework (spec FR-022 says "absent from release builds") | Kotlin Multiplatform common code has no per-configuration source set; the entry point is compiled out in the Swift shell (`#if DEBUG`), so nothing can open it, and on Android the `BuildConfig.DEBUG` branch and the composable it references are removed by the release shrinker. Recorded in quickstart §8. | A separate `:shared:ui-devtools` module linked only into Debug would need a second iOS framework flavour and Xcode configuration wiring for one preview composable. |
| #3 Custom `TembraNavigationBar` / `TembraNavigationRail` instead of Material's `NavigationBar` / `NavigationRail` (Constitution VI "prefer the platform") | The design and spec FR-019 fix a 64 dp bar and rail whose active item is a pill or rounded square wrapping icon and label; Material's components enforce 80 dp minimums and draw an icon-only indicator. Placement still uses Material's `NavigationSuiteScaffoldLayout`; hover, focus and semantics use standard Compose modifiers. | Styling Material's items reaches neither the height nor the indicator shape; overriding their internals is not possible. |
