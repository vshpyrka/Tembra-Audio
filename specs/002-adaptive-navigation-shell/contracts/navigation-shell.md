# Contract: Navigation shell

**Module**: `:shared:ui`, package `audio.tembra.ui.navigation` | **Entry**: `App()` renders `TembraTheme { NavigationShell() }` | **Depends on**: contracts/design-system-additions.md, data-model.md §1–§3

## Types

```kotlin
// Destination.kt — the registry (data-model.md §1). Pure data; no Navigation 3 type.
enum class Destination(
    val id: String, val label: String, val title: String, val icon: TembraIcon,
    val hasSupportingPane: Boolean, val placeholderMessage: String,
) { Home, Timeline, Tuner, Metronome, Projects, Profile }

// DestinationKey.kt — the only Navigation 3 key. One class, so entryProvider has one entry.
data class DestinationKey(val destination: Destination) : NavKey

// ShellUiState.kt
data class ShellUiState(val selected: Destination = Destination.Home) {
    /** [Home] or [Home, selected]; what NavDisplay renders. */
    val backStack: List<Destination>
}

// ShellViewModel.kt — @Inject @ContributesIntoMap(AppScope::class) @ViewModelKey, obtained ABOVE NavDisplay
// (root ViewModelStoreOwner) so it outlives destination switches.
class ShellViewModel : ViewModel() {
    val uiState: StateFlow<ShellUiState>
    fun select(destination: Destination)      // no-op when already selected (FR-008)
    fun back(): Boolean                       // true and selected = Home when not on Home; false on Home (FR-010)
}

// LayoutMode.kt (data-model.md §3)
enum class LayoutMode { BottomBar, Rail, RailWithPanel;
    companion object { fun from(width: WindowWidth, isShort: Boolean): LayoutMode } }

// RememberLayoutMode.kt
/** Reads currentWindowAdaptiveInfo().windowSizeClass (adaptive 1.2.0, research R3); width buckets at 600/840 dp, isShort = height under 480 dp. */
@Composable fun rememberLayoutMode(): LayoutMode

// NavigationShell.kt — stateful wrapper
@Composable fun NavigationShell(
    viewModel: ShellViewModel = metroViewModel(),
    layoutMode: LayoutMode = rememberLayoutMode(),
    modifier: Modifier = Modifier,
)

// NavigationShellContent.kt — stateless; what UI tests drive
@Composable fun NavigationShellContent(
    state: ShellUiState,
    layoutMode: LayoutMode,
    onSelect: (Destination) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

## Composition (NavigationShellContent)

```
Column(testTag "nav.shell")                                          no top bar since 2026-10-16 (FR-007 reversed)
└── NavigationSuiteScaffoldLayout(                                   Material 3 adaptive; places bar vs rail
        layoutType = BottomBar -> NavigationSuiteType.NavigationBar, else NavigationRail,
        navigationSuite = { TembraNavigationBar(...) or TembraNavigationRail(...) })
    └── TembraNavDisplay(backStack, layoutMode, onBack)              Navigation 3 NavDisplay (below)
```

The content box pads the safe-drawing top inset (status bar, a desktop window's caption, a top cutout) as well as
its other sides, and the rail's surface reaches the top edge while its items clear the same inset
(`NavigationShellTest.noLayoutHasATopBarAndTheContentStartsAtTheTopInset`). The iPhone Duo's end-rail frame pads
the top too.

The navigation items are `Destination.entries.map { NavigationItemSpec(it.id, it.label, it.icon) }`;
`selectedId = state.selected.id`; `onSelect` maps id back to the enum. The chrome never sees
`Destination` directly (design-system components stay reusable).

## TembraNavDisplay (internal, one file: `TembraNavDisplay.kt`)

```kotlin
NavDisplay(
    backStack = state.backStack.map(::DestinationKey),           // plain List, derived from the ViewModel
    onBack = onBack,                                             // ViewModel.back(); with [Home] alone NavDisplay does not intercept, so the platform exits (FR-010)
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    sceneStrategies = listOf(SupportingPaneSceneStrategy(enabled = layoutMode == LayoutMode.RailWithPanel)),  // then the built-in single-pane fallback
    transitionSpec = fade, popTransitionSpec = fade, predictivePopTransitionSpec = { fade() },
    entryProvider = entryProvider {
        entry<DestinationKey>(metadata = { key -> if (key.destination.hasSupportingPane) supportingPane(SupportingPane.Library) else emptyMap() }) { key ->
            DestinationContent(key.destination)                 // the single mapping to feature packages
        }
    },
)
// fade = if (TembraTheme.motion.reduceMotion) ContentTransform(EnterTransition.None, ExitTransition.None)
//        else ContentTransform(fadeIn(tween(standardDuration, easing = standardEasing)), fadeOut(tween(standardDuration, easing = standardEasing)))
```

```kotlin
// DestinationContent.kt — exhaustive `when`; adding a destination fails compilation until mapped
@Composable fun DestinationContent(destination: Destination) = when (destination) {
    Home -> HomeScreen(); Timeline -> TimelineScreen(); Tuner -> TunerScreen();
    Metronome -> MetronomeScreen(); Projects -> ProjectsScreen(); Profile -> ProfileScreen() }

// SupportingPane.kt: enum class SupportingPane { Library }
// SupportingPaneKey.kt: object SupportingPaneKey : NavMetadataKey<SupportingPane>; fun supportingPane(p) = metadata { put(SupportingPaneKey, p) }
// SupportingPaneScene.kt: data class SupportingPaneScene<T : Any>(override val key: Any, val main: NavEntry<T>, val pane: SupportingPane,
//     override val previousEntries: List<NavEntry<T>>) : Scene<T>   — content = Row { Box(weight 1f) { main.Content() }; SupportingPaneContent(pane) }
// SupportingPaneSceneStrategy.kt: class SupportingPaneSceneStrategy<T : Any>(private val enabled: Boolean) : SceneStrategy<T>
//     calculateScene: if (!enabled) null else entries.last().metadata[SupportingPaneKey]?.let { SupportingPaneScene(top.contentKey, top, it, entries.dropLast(1)) }
// SupportingPaneContent.kt: when (pane) { Library -> LibraryPane() }
```

Why a custom strategy: Material's `SupportingPaneSceneStrategy` (adaptive-navigation3) needs the
main and supporting content as two keys on the back stack, which contradicts the two-entry
stack; ours is about 40 lines and keeps the pane as metadata on one entry (research R2).

## Feature packages (each independent of the shell and of each other)

| Package | Composable | Content in this feature | testTag |
|---|---|---|---|
| `audio.tembra.ui.home` | `HomeScreen()` | `PlaceholderDestination(Destination.Home)` | `screen.home` |
| `audio.tembra.ui.home` | `LibraryPane()` | `TembraSupportingPane(title = "Library") { EmptyState("Loops and sounds will appear here.") }` | `chrome.supportingPane` |
| `audio.tembra.ui.timeline` | `TimelineScreen()` | `PlaceholderDestination(Destination.Timeline)` | `screen.timeline` |
| `audio.tembra.ui.tuner` | `TunerScreen()` | placeholder | `screen.tuner` |
| `audio.tembra.ui.metronome` | `MetronomeScreen()` | placeholder | `screen.metronome` |
| `audio.tembra.ui.projects` | `ProjectsScreen()` | placeholder | `screen.projects` |
| `audio.tembra.ui.profile` | `ProfileScreen()` | placeholder | `screen.profile` |
| `audio.tembra.ui.designsystem` | `PlaceholderDestination(destination)` | title (headlineLarge / headlineLgMobile by width) and `placeholderMessage` (bodyLarge), page margins, nothing else (FR-011, FR-013); message node `testTag "screen.<id>.message"` | `screen.<id>` |

A later feature replaces one composable in this table and touches nothing else.

## Semantics and test tags (FR-015)

| Element | testTag | Semantics |
|---|---|---|
| shell root | `nav.shell` | — |
| top bar / title | `chrome.topBar` / `chrome.topBar.title` | absent from the shell since 2026-10-16; the component keeps the tags |
| bottom bar / rail | `chrome.bottomBar` / `chrome.rail` | exactly one of them exists per layout |
| item | `nav.item.<id>` | role Tab, `selected`, contentDescription = label, focusable, ≥ 44 dp |
| supporting pane | `chrome.supportingPane` | exists only for `RailWithPanel` + `hasSupportingPane` |
| destination root | `screen.<id>` | exactly one exists once the transition finished |

## Back

Android: `NavDisplay` intercepts the system back only while the back stack has two entries,
then calls `onBack` = `ShellViewModel.back()`; with `[Home]` alone the activity finishes
(FR-010). iOS: the same path is driven by the edge-swipe predictive back that Navigation 3
implements on iOS, so a swipe from the left edge on a non-Home destination returns to Home;
on Home nothing happens. This is the iOS mirror of FR-010 and is documented in quickstart §1.

## Tests

`commonTest` (JVM + iOS simulator): `DestinationTest` (six values, order, unique ids/labels/titles,
only Home has a pane); `ShellUiStateTest` (back stack derivation); `ShellViewModelTest` (starts
on Home; `select` changes and re-select is a no-op with no emission; `back()` from Tuner → Home
returns true; `back()` on Home returns false and leaves state); `LayoutModeTest` (every row of
data-model §3, both directions).

`uiTest` (`runComposeUiTest`, iOS simulator + Android device) — `NavigationShellTest` drives
`NavigationShellContent` with a `mutableStateOf` state and layout mode:
- `bottomBarLayout`: `BottomBar` → `chrome.bottomBar` exists, `chrome.rail` and `chrome.supportingPane` do not; six `nav.item.*` nodes.
- `railLayout`: `Rail` → rail exists, no bar, no pane.
- `railWithPanelShowsThePaneOnHomeOnly`: `RailWithPanel` + Home → pane exists, width 320 dp; + Tuner → no pane.
- `selectingAnItemCallsOnSelectAndMarksIt`: click `nav.item.tuner` → `onSelect(Tuner)`; with state updated, `nav.item.tuner` `assertIsSelected()`, others not, title "Tuner", `screen.tuner` exists.
- `reselectingTheActiveItemIsANoOp`: click `nav.item.home` while on Home → `onSelect` not called.
- `layoutSwitchKeepsTheSelection`: on Tuner, flip layout mode BottomBar → Rail → RailWithPanel → BottomBar; `nav.item.tuner` stays selected and `screen.tuner` stays.
- `keyboardActivatesAnItem`: request keyboard input mode (guard as in feature 001), focus `nav.item.metronome`, press Enter → `onSelect(Metronome)`.
- `everyItemMeetsTheTouchTarget`: `assertWidthIsAtLeast(44.dp)` and `assertHeightIsAtLeast(44.dp)` on all six in both bar and rail.
- `transitionFinishesWithinTheStandardDuration`: `mainClock.autoAdvance = false`; select Tuner; advance 300 ms + one frame; `screen.tuner` displayed and `screen.home` gone.
- `reduceMotionSwitchesInstantly`: effect context with `MotionDurationScale` 0f; select Tuner; after one frame `screen.tuner` is displayed.
- Start on Home is covered by `ShellViewModelTest` (initial state) plus `NavigationShellTest`
  rendering the initial state; `App` itself needs the Metro graph and is exercised by the
  launch checks in quickstart §1 (a test graph would have to bind the real engine).

`androidDeviceTest` only: `AndroidBackTest` — launch `MainActivity`, tap Tuner, `Espresso.pressBack()` → Home shown; `pressBack()` again → activity finishing.

Manual (quickstart §3–§5): live resize on foldable/desktop-mode, iPad multitasking, hover
colour, screen reader announcements, screenshots.
