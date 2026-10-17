# Research: Adaptive Navigation Shell and Home

**Feature**: `002-adaptive-navigation-shell` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

Every decision below was checked against published artifacts (Maven Central metadata, the
sources and klibs of the exact versions), the JetBrains and Android documentation, and the
Stitch HTML in `design/stitch/screens/`. Items that could not be executed in this repository
are marked UNVERIFIED and become the first tasks of the implementation.

## R1. Navigation library and versions

**Decision**: Jetpack Navigation 3 through the JetBrains multiplatform artifact
`org.jetbrains.androidx.navigation3:navigation3-ui:1.1.2` (the version the Compose
Multiplatform 1.12.1 changelog pairs with that release; it pulls Google's multiplatform
`androidx.navigation3:navigation3-runtime:1.1.7` transitively) and
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0` (matches the catalog's
lifecycle 2.11.0). No `kotlinx-serialization` plugin is added: the back stack is owned by a
ViewModel and never saved by Navigation 3, so keys need not be `@Serializable` (spec: relaunch
starts on Home; on Timeline since 2026-10-17).

**Rationale**: the constitution and `docs/research/kmp-code-architecture.md` §4 fix Navigation 3
as the navigation library; JetBrains lists `navigation3-ui` as stable on Android and iOS since
Compose Multiplatform 1.10. The public `NavDisplay` / `Scene` / `SceneStrategy` API is identical
between 1.1.2 and the newer 1.2.0-beta01, so the pinned version carries no API risk.

**Alternatives considered**: `navigation3-ui:1.2.0-beta01` (built against Compose 1.10, not
the changelog-blessed pairing for 1.12.1); Navigation 2 `navigation-compose` (rejected by the
architecture brief: `NavHost` owns the stack, no scenes); Decompose (second navigation model).

**Known limitations recorded**: on non-JVM targets `rememberNavBackStack` needs a
`SavedStateConfiguration` with polymorphic serialisation (not used here); browser history is
not integrated (no web target); predictive back on iOS is implemented by Navigation 3 as an
edge swipe, which will pop a non-Home destination to Home (documented as the iOS mirror of
FR-010 in contracts/navigation-shell.md; since 2026-10-17 it pops to Timeline, the start
destination).

## R2. Destinations as decoupled entries; the back stack; scenes

**Decision**:
- `Destination` is a plain enum registry (id, label, title, icon, `hasSupportingPane`,
  placeholder sentence). `DestinationKey(destination)` is the single `NavKey` class, so the
  `entryProvider` has exactly one `entry<DestinationKey>` whose content is an exhaustive `when`
  in `DestinationContent.kt` mapping to one composable per feature package (`home`, `timeline`,
  `tuner`, `metronome`, `projects`, `profile`). Screens know nothing about the shell, the
  layout, or each other; the shell knows only the registry and that one mapping.
- The back stack is derived from `ShellUiState`: `[Home]` or `[Home, selected]` (since 2026-10-17
  the start destination, the first enum entry, Timeline, takes Home's place). `NavDisplay`
  accepts any `List<T>` (its entry list is `remember(backStack.toList())`), so no
  `rememberNavBackStack` is needed; `onBack` is `() -> Unit` and is wired to
  `ShellViewModel.back()`. `NavDisplay` intercepts the platform back only while the list has
  more than one entry, which gives FR-010 for free. Switching `[Home, A]` to `[Home, B]` is
  treated as a push (uses `transitionSpec`), popping to `[Home]` uses `popTransitionSpec`; both
  are the same fade (R7).
- The side panel is a Navigation 3 **scene**: `SupportingPaneSceneStrategy(enabled)` is first
  in `sceneStrategies`; when enabled (layout `RailWithPanel`) and the top entry's metadata
  carries `SupportingPaneKey`, it returns a `SupportingPaneScene` (a `data class`, because
  `NavDisplay` compares scenes by equality) whose content is `Row { main.Content() (weight 1);
  TembraSupportingPane (320 dp) }`; otherwise it returns null and the built-in single-pane
  strategy renders the entry alone. Metadata is attached per key through the
  `entry(metadata = (key) -> Map)` overload, so only Home carries the pane (since Home was
  removed on 2026-10-17 no destination carries one; the strategy stays for a later feature).
- `ShellViewModel` is obtained above `NavDisplay` (root `ViewModelStoreOwner`) so it survives
  destination switches; `rememberViewModelStoreNavEntryDecorator` scopes any future
  per-destination ViewModel to its entry and clears it when the entry is popped. Metro's
  `metroViewModel()` reads `LocalViewModelStoreOwner`, which the decorator provides, so there is
  no conflict with Metro 1.4.4.

**Rationale**: this is exactly the decoupling the user asked for: destinations are independent
entries, and the scene strategy list decides how entries are arranged for the current window
without touching navigation logic or screens. It also follows the architecture brief's rule to
keep all `NavDisplay` wiring in one file.

**Alternatives considered**: Material's `SupportingPaneSceneStrategy`
(`org.jetbrains.compose.material3.adaptive:adaptive-navigation3`, `@ExperimentalMaterial3AdaptiveApi`)
requires the main and supporting content to be two keys on the back stack, which contradicts the
two-entry stack and would make the panel a navigable destination; a sealed `NavKey` hierarchy
with one `entry<>` per destination (more files for the same decoupling; the exhaustive `when`
already makes an unmapped destination a compile error); putting the `Row` inside `HomeScreen`
(couples Home to the layout and bypasses the scene model the user asked to use).

## R3. Window size and layout mode

**Decision**: `org.jetbrains.compose.material3.adaptive:adaptive:1.2.0` for
`currentWindowAdaptiveInfo()` (`WindowAdaptiveInfo(windowSizeClass, windowPosture)`), and
`androidx.window.core.layout.WindowSizeClass` (window-core 1.4.0, transitive) with
`isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND = 600)`,
`isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND = 840)` and
`isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND = 480)`. `rememberLayoutMode()` maps
these to the existing `WindowWidth` bucket plus an `isShort` flag and then to `LayoutMode`
(data-model.md §3). The shell composable takes `LayoutMode` as a parameter so UI tests force
each layout without resizing a window.

**Rationale**: `currentWindowAdaptiveInfo()` measures the real window (`LocalWindowInfo.containerSize`),
not the composable's constraints, which is what Constitution IV asks for. On iOS, Compose
Multiplatform updates the container size from `UIWindow.bounds` on layout and interpolates it
during rotation, so rotation, iPad Split View and Stage Manager resizes recompose it (Stage
Manager on hardware is UNVERIFIED and is a quickstart check). The deprecated
`windowWidthSizeClass` / `windowHeightSizeClass` properties are not used (zero-warning gate).
`BoxWithConstraints` (feature 001's approach) is dropped for the shell because it measures the
parent, not the window.

**Alternatives considered**: `adaptive:1.3.0-rc01` (the Compose Multiplatform 1.12.1 row; based
on Jetpack Adaptive 1.3.0 stable) with `currentWindowAdaptiveInfoV2()`: works, but the project
pins Material 3 at the latest stable 1.9.0, whose navigation-suite artifact pairs with adaptive
1.2.0; mixing a release candidate into a stable set buys nothing this feature needs. Upgrade
path: when Material 3 moves past 1.9.0, move adaptive to 1.3.x and switch to
`currentWindowAdaptiveInfoV2()` in `RememberLayoutMode.kt` (one file). Material 3 moved past
1.9.0 on 2026-10-17 (R4, R11) and adaptive stayed at 1.2.0; this upgrade has not been made.

## R4. Bar versus rail placement

**Decision**: `org.jetbrains.compose.material3:material3-adaptive-navigation-suite:1.9.0` and
its `NavigationSuiteScaffoldLayout(navigationSuite, navigationSuiteType, state, content)`
overload (stable, no opt-in, verified present in the iOS klib). `navigationSuiteType` is
computed from the width bucket only: `NavigationSuiteType.NavigationBar` for `BottomBar`, else
`NavigationSuiteType.NavigationRail`; the layout then places the slot at the bottom or at the
start. The slot content is our own `TembraNavigationBar` / `TembraNavigationRail`
(contracts/design-system-additions.md), built on `Surface` + `Row` / `Column` with
`clickable`, `focusable`, hover and focus indication from an `InteractionSource`, and
`semantics { role = Tab; selected }`.

**Rationale**: the design fixes a 64 dp bar and a 64 dp rail with an active pill (bar) or
rounded square (rail) that wraps icon and label together; Material's `NavigationBar` and
`NavigationRail` enforce 80 dp minimums and draw a pill indicator behind the icon only, so
their items cannot match FR-019. `NavigationSuiteScaffoldLayout` accepts an arbitrary
composable in the slot, which keeps the "latest adaptive layout" API for placement while the
design system owns the look. Material's default heuristic
(`NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo`) would return a bar for compact
height (phone landscape); the spec (US2 scenario 5) wants the rail there, hence the width-only
type.

**Alternatives considered**: `NavigationSuiteScaffold` with `navigationSuiteItems` (item look
is fixed to Material's); the newer `ShortNavigationBarCompact` / `WideNavigationRailCollapsed`
types (Material 3 Expressive shapes, not the design); a hand-written `Column` / `Row` switch
(works, but discards the adaptive API the user asked for at no saving).

**Reversed 2026-10-17** (owner's decision; spec FR-003 reversed, FR-019 amended): the Expressive
types rejected above are now the decision. The rejection rested on the design's active pill
wrapping icon and label together; with the labels gone, Material's indicator behind the icon is
the whole marked item, and the owner took Material's sizes in place of the design's 64 dp bar and
rail. `navigationSuiteType` is `NavigationSuiteType.ShortNavigationBarCompact` for `BottomBar`
and `NavigationSuiteType.WideNavigationRailCollapsed` otherwise, still from the width bucket only.
`TembraNavigationBar` wraps `ShortNavigationBar` (container `surfaceContainerLowest`; insets: the
navigation bars plus the horizontal safe-drawing insets) with one `ShortNavigationBarItem(label =
null)` per item, coloured through `ShortNavigationBarItemDefaults.colors(selectedIconColor =
onPrimaryContainer, selectedIndicatorColor = primaryContainer, unselectedIconColor =
onSurfaceVariant)`. `TembraNavigationRail` wraps `WideNavigationRail` (container
`surfaceContainerLowest`; insets: the safe-drawing top and start plus the bottom navigation inset;
`Arrangement.Bottom` for the iPhone Duo's end rail) around a vertically scrolling `Column`, spaced
`spacing.xs`, of `WideNavigationRailItem(label = null, railExpanded = false)` with Material's rail
colours (active indicator `secondaryContainer`, icon `onSecondaryContainer`; inactive
`onSurfaceVariant`). Measured on devices: with `label = null` the collapsed rail draws its active
indicator as a 56 × 56 dp circle, while the bar's is a 56 × 32 dp pill. The label is each icon's `contentDescription`. The bar is Material's 64 dp
minimum (`defaultMinSize`) plus the navigation inset (measured 88 dp on a Pixel 6 Pro and 98 pt on
an iPhone 14); the collapsed rail is 96 dp wide below Material's 44 dp top space, and its items
scroll because five of them do not otherwise fit a 360 dp-tall phone in landscape. The indicator
moves with Material's own animation under `MotionScheme.expressive()`, which
`MaterialExpressiveTheme` gives the whole app. `MaterialExpressiveTheme` and
`MotionScheme.expressive()` are internal in Material 1.9.0 (androidx 1.4.0) and public in JetBrains
1.12.0-alpha03 and androidx 1.5.0-beta01, hence the version change in R11. Because the items are
icon-only, each icon is a `NavigationItemIcon`: Material's `TooltipBox` with a `PlainTooltip` of
the label, its persistent tooltip state shown and dismissed by the item's hover interaction (the
pattern of the metronome's `PlayPauseButton` and `TempoStepper`), above the glyph in the bar and
at its end side in the rail, which meets Constitution IV's tooltip rule. Keyboard focus uses
Material's inset focus ring: the bar and the rail provide `LocalRippleThemeConfiguration provides
InsetFocusRing`, and the ring is drawn inside the item's indicator, outer stroke `secondary`,
inner stroke `onSecondary`; hover shows Material's state layer plus the tooltip.

Found while retaking the screenshots (2026-10-17): `WideNavigationRail` places its content at its
start edge and measures it up to its 96 dp width; androidx beta01's items fill that width, but the
JetBrains port's items are only as wide as their indicator, so the scrolling column sat flush left
on the iPad and the iPhone Duo until it was given the rail's width with its items centred
(`TembraNavigationRailTest.itemsAreCentredInTheRail`). Keyboard order: the Tab cycle is unchanged
(the rail's items, then the destination's controls, then the rail again), but the first Tab from an
unfocused window now lands on the destination's first control rather than on the first rail item
(Pixel Tablet and Pixel 9 Pro Fold; with feature 002's own rail it was the first rail item).
Placing the rail before the content in a `Row`, or making the content a traversal group, did not
change that entry point, so the frame keeps `NavigationSuiteScaffoldLayout`; the controls' own
order, which features 004 and 006 require, is unchanged.

## R5. Side panel component

**Decision**: the panel is drawn by the Navigation 3 scene from R2 as a fixed 320 dp
`TembraSupportingPane` beside the main pane, hidden by the strategy unless the layout is
`RailWithPanel`. Material's `SupportingPaneScaffold` is **not** used.

**Rationale**: `SupportingPaneScaffold`, `calculatePaneScaffoldDirective`, `AnimatedPane` and
`rememberSupportingPaneScaffoldNavigator` are all `@ExperimentalMaterial3AdaptiveApi` in
adaptive 1.2.0 (and still experimental in Jetpack 1.3.0); the scaffold's directive ignores
window height (so the 480 dp rule needs a `copy(maxHorizontalPartitions = 1)` anyway); its
navigator models the panel as a destination that can open full-screen on phones, which the spec
forbids; and `NavigableSupportingPaneScaffold` is absent from the iOS klib. A fixed-width pane
inside a scene is about 40 lines, stable API only, and testable with `assertWidthIsEqualTo`.

**When to switch**: if a later feature needs the loop library to open full-screen on phones with
back navigation, animated pane bounds or a draggable divider, adopt `SupportingPaneScaffold`
(or Material's `SupportingPaneSceneStrategy` with the pane as its own key) at that point; the
scene boundary makes that a local change.

## R6. Icons: Material Symbols as shared vector assets

**Decision**: one Android `VectorDrawable` XML per glyph, copied from
`google/material-design-icons` at commit `27e9ef1dbeedc13d682fece4a58e1eda4cb0961a` (master,
2026-09-18; the repository's last tag is from 2020 and covers the classic icons only), path
`symbols/android/<glyph>/materialsymbolsoutlined/<glyph>_24px.xml` (weight 400, grade 0,
unfilled, optical size 24), normalised and stored as
`shared/ui/src/commonMain/composeResources/drawable/ic_<glyph>_24.xml`, loaded with
`vectorResource(Res.drawable.ic_<glyph>_24)` and tinted through `Icon(tint = …)`. A script
`app/tools/fetch-material-symbols.sh` performs download and normalisation and is the only way
glyphs are added.

**Evidence and normalisation**: the Compose Multiplatform resource library renders Android
vector XML on every target (`painterResource` dispatches `.xml` to `vectorResource`), but its
parser accepts only literal `#…` colours: `android:fillColor="@android:color/white"` throws
`IllegalArgumentException` at load time, and `?attr/…` values are rejected the same way; the root
`android:tint` attribute is silently ignored. The script therefore replaces
`@android:color/white` with `#FFFFFFFF`, removes `android:tint`, and asserts that no `@` or
`?attr` remains; `Icon` applies `ColorFilter.tint`, so the white fill is always recoloured.
Resource names must match `[a-z0-9_]+` and sit directly in `drawable/` (the accessor generator
does not descend into subfolders). Filled variants, if ever needed, are `<glyph>_fill1_24px.xml`.

**Rationale**: the design's HTML uses Material Symbols Outlined, so the glyphs match the
reference exactly; each file is 0.5 to 1.3 KB; no library or icon font is added; the same bytes
render on both platforms (FR-016, SC-006).

**Alternatives considered**: `androidx.compose.material:material-icons-extended` (classic
Material Icons set, frozen since 2022, several megabytes before shrinking, glyphs differ from
Material Symbols); the Material Symbols variable font (about 8 MB, glyphs as text with ligatures
and axis settings, awkward with `Icon`); SVG files (`.svg` is not supported on Android by the
resource library).

## R7. Motion and reduce motion

**Decision**: `TembraMotion(standardDuration = 300 ms, standardEasing = cubic-bezier(0.2, 0, 0, 1))`
in the theme, from Material 3's motion tokens (`duration.medium2`, `easing.standard`) as the
"standard transition duration" the spec asks for. `NavDisplay`'s `transitionSpec`,
`popTransitionSpec` and `predictivePopTransitionSpec` all return
`fadeIn(tween(300, easing)) togetherWith fadeOut(tween(300, easing))`, replacing Navigation 3's
platform defaults (a 700 ms fade on Android, a 500 ms slide on iOS) so both platforms behave the
same. The active marker in the bar and rail animates its fill over the same token (until
2026-10-17; since then the marker is Material's indicator, animated by the theme's expressive
motion scheme, R4).

**Reduce motion**: no platform code is needed. Compose injects a `MotionDurationScale` into the
composition's coroutine context on both platforms: Android mirrors
`Settings.Global.ANIMATOR_DURATION_SCALE` (the "Remove animations" toggle sets it to 0 and a
`ContentObserver` keeps it live), and Compose Multiplatform for iOS (since 1.8.0) sets the
scale to 0 when `UIAccessibilityIsReduceMotionEnabled()` is true, refreshed when the scene becomes
active. With scale 0 every animation, including `NavDisplay`'s, completes on the next frame.
`rememberReduceMotion()` reads `rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f`
so the transition specs can return `ContentTransform(EnterTransition.None, ExitTransition.None)`
explicitly (spec FR-008) and so the behaviour is unit-testable through
`runComposeUiTest(effectContext = <MotionDurationScale with scaleFactor 0f>)`.

**Alternatives considered**: `expect`/`actual` readers of the raw OS flags (only needed for
motion outside the animation clock, none here); iOS
`UIAccessibilityReduceMotionStatusDidChangeNotification` observer for instant refresh (Compose
refreshes on scene activation, which is when the user returns from Settings; sufficient).

## R8. Chrome geometry from the design (evidence for FR-019 and data-model.md §6)

From `design/stitch/screens/instrument-tuner/phone/index.html` (bottom bar and top bar) and
`design/stitch/screens/export-mastering/tablet/index.html` (rail), which agree with every other
screen of their kind. Since 2026-10-17 the bar and rail rows below are history: the app draws
Material's components with their own metrics (R4) and keeps from this table only the
`surfaceContainerLowest` containers, the bar's `primaryContainer` / `onPrimaryContainer` active
colours and the `onSurfaceVariant` inactive colour; the top bar and panel rows still apply.

| Element | Markup | Value used |
|---|---|---|
| bottom bar | `nav.h-16.px-gutter.justify-around` inside `footer.pb-safe.bg-surface-container-lowest/95` | 64 dp + bottom inset, `surfaceContainerLowest` |
| bar item | `flex-col gap-1 px-space-sm py-1.5 rounded-full min-w-[56px]`, icon `text-[20px]`, label `text-label-sm` | pill, min width 56 dp, icon 20 dp, gap 4 dp, `labelSmall` |
| bar item active | `bg-primary-container text-on-primary-container font-semibold shadow-[0_0_12px_rgba(167,139,250,0.3)]` | `primaryContainer` fill, `onPrimaryContainer` content, `clip_glow` |
| bar item inactive / hover | `text-on-surface-variant hover:text-on-surface` | `onSurfaceVariant` → `onSurface` |
| rail | `aside.w-16.top-14.bg-surface-container-lowest`, `nav.px-2.gap-1.5` | 64 dp wide below a 56 dp top bar (full height since the bar was removed on 2026-10-16), items 48 dp wide, 6 dp apart |
| rail item | `py-2 px-1 rounded-lg`, icon `text-[22px]`, label `text-label-sm` | rounded 16 dp (design `rounded.lg`), icon 22 dp |
| rail item active / hover | `bg-secondary-container text-on-secondary-container font-bold` / `hover:bg-surface-container-high hover:text-on-surface` | `secondaryContainer` fill; hover `surfaceContainerHigh` |
| top bar | overline `font-label-sm text-primary tracking-wider uppercase` "Tembra Audio", title `font-headline-sm text-on-surface` | overline `labelSmall` in `primary`, title `headlineSmall`; height 56 dp per spec (phone screens use 64 dp, wide screens 56 dp; the two-line block fits 56 dp). Removed from the shell on 2026-10-16 (spec FR-007 reversed); the component stays |
| panel | `w-80 bg-surface-container-low rounded-xl p-space-md` (timeline tablet) | 320 dp, `surfaceContainerLow`, 12 dp radius and padding |

## R9. Developer-only token preview

**Decision**: a launch argument, never a control. Android `MainActivity` reads the intent extra
`tembra.developer.view` inside `if (BuildConfig.DEBUG)` (`buildFeatures.buildConfig = true` is
enabled for `:androidApp`; R8's shrinker removes the branch from release builds); the iOS Swift
shell reads `--tembra-developer-view=<value>` from `CommandLine.arguments` inside `#if DEBUG` and
passes it to `MainViewController(developerView:)`. `App` renders `DeveloperViewScreen` instead
of the shell when a request is present. `DesignTokenPreview` and its test move to
`audio.tembra.ui.devtools`; the scaffold's counter screen, ViewModel, state and tests are deleted.

**Rationale**: satisfies FR-022's "never reachable from the navigation control or the top bar"
and "debug builds only" with the smallest mechanism, and quickstart §8 documents the commands.
Limitation recorded in plan.md Complexity Tracking: on iOS the composable remains in the
framework binary; only its entry point is compiled out.

**Alternatives considered**: a hidden long-press on the title (reachable from the top bar,
forbidden); a separate `:shared:ui-devtools` module linked only into debug variants (an iOS
framework per configuration; disproportionate for one preview).

## R10. Test strategy and ViewModel scoping

**Decision** (lanes unchanged from feature 001):

| Lane | New tests |
|---|---|
| `commonTest` (JVM + iOS simulator) | `DestinationTest`, `ShellUiStateTest`, `ShellViewModelTest`, `LayoutModeTest`, `TembraMotionTest`, `ChromeMetricsTest`, `DeveloperViewRequestTest` |
| `androidHostTest` (JVM, repository files) | `IconAssetsTest` (every `TembraIcon` file exists, contains no `@` or `?attr` references, header names the pinned commit); `ThemeJsonParityTest` unchanged |
| `uiTest` (iOS simulator + Android device) | `NavigationShellTest` (three layouts, pane only on Home (on no destination since 2026-10-17), select / reselect, layout switch keeps selection, keyboard, 44 dp, transition timing, reduce motion), `TembraNavigationBarTest`, `TembraNavigationRailTest`, `TembraTopBarTest`, `TembraSupportingPaneTest`, `ReduceMotionTest`, `AppContentTest` (developer view replaces the shell), `DesignTokenPreviewTest` (moved) |
| `androidDeviceTest` | `AndroidBackTest` (`Espresso.pressBack()` returns to Home, then finishes the activity; to Timeline since 2026-10-17) |

Transition tests drive `mainClock` (`autoAdvance = false`, advance 300 ms) rather than waiting
on wall time, so they never depend on animation timing (FR-020). Layout tests pass `LayoutMode`
explicitly; the width-to-mode mapping is covered by `LayoutModeTest` on both platforms, and the
real-window path by the screenshot matrix in quickstart §9. `ShellViewModel` is created above
`NavDisplay`; the entry-scoped decorators are wired now so later per-destination ViewModels get
the documented scoping without changes to the shell.

## R11. Dependencies added (Constitution VI) and licence inventory

| Catalog alias | Coordinates | Version | Licence | Why |
|---|---|---|---|---|
| `androidx-navigation3-ui` | `org.jetbrains.androidx.navigation3:navigation3-ui` | 1.1.2 | Apache-2.0 | R1 |
| (transitive) | `androidx.navigation3:navigation3-runtime` | 1.1.7 | Apache-2.0 | R1 |
| `androidx-lifecycle-viewmodel-navigation3` | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 | Apache-2.0 | R2 |
| `compose-material3-adaptive` | `org.jetbrains.compose.material3.adaptive:adaptive` | 1.2.0 | Apache-2.0 | R3 |
| `compose-material3-adaptive-navigation-suite` | `org.jetbrains.compose.material3:material3-adaptive-navigation-suite` | 1.9.0 | Apache-2.0 | R4 |
| (transitive) | `org.jetbrains.androidx.window:window-core` → `androidx.window:window-core` | 1.4.0 | Apache-2.0 | R3 |
| asset | Material Symbols Outlined glyphs, `google/material-design-icons` | commit `27e9ef1d` | Apache-2.0 | R6 |
| `compose-material3` (changed 2026-10-17) | `org.jetbrains.compose.material3:material3` and `material3-adaptive-navigation-suite` | 1.9.0 → 1.12.0-alpha03 | Apache-2.0 | R4 (Expressive components and theme); ports androidx material3 1.5.0-alpha22; common code and iOS |
| `androidx-compose-material3` (added 2026-10-17, `androidMain`) | `androidx.compose.material3:material3` and `material3-adaptive-navigation-suite` | 1.5.0-beta01 | Apache-2.0 | R4; raises the androidx Material 3 under the JetBrains artifacts on Android |

All are in the stack decided in `docs/research/kmp-code-architecture.md` (Navigation 3,
Material 3 adaptive, multiplatform lifecycle). `THIRD_PARTY_LICENSES.md` gains one row each.
Verified 2026-09-24 (task T004): with the four catalog entries added to `:shared:ui` `commonMain`,
`./gradlew :shared:ui:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug --warning-mode all`
succeeded in 33 s with zero warnings and no duplicate-class or klib-name conflict alongside Metro's
runtime; no exclusion was needed. `androidx.window.core.layout.WindowSizeClass` is visible to
`commonTest` on both the JVM and the iOS simulator (LayoutModeTest), and the six normalised glyphs
generate `Res.drawable.ic_<glyph>_24` accessors (feature 001's benign KLIB name warning remains
confined to the metadata compilation).

Amended 2026-10-17: the two Material 3 rows added to the table that day are pre-releases, taken
for the Expressive bar, rail and theme (R4). Android resolves androidx material3 1.5.0-beta01
while iOS runs the JetBrains port of 1.5.0-alpha22. Beta01 removed the value-based
`Slider(value, …, thumb, track)`, and the two releases share no slider signature that takes a
custom thumb and track, so the design system gains `internal expect fun TembraSlider(...)` with
an Android actual (the state-based slider) and an iOS actual (the value-based one); the tuner's
`NoiseGateSlider` and the metronome's `TempoSlider` call it. The same split names Material's
inset focus-ring preset differently (`RippleDefaults.InsetFocusRingThemeConfiguration` in
androidx 1.5.0-beta01, `RippleDefaults.InsetFocusRingRippleThemeConfiguration` in the JetBrains
1.12.0-alpha03 port), so `InsetFocusRing` (R4) is an internal `expect val` with an Android and an
iOS actual. The navigation glyphs are five since `ic_home_24.xml` was deleted on 2026-10-17.

## R12. What is removed or kept from feature 001

- Removed: `PlaceholderScreen`, `PlaceholderScreenContent`, `PlaceholderViewModel`,
  `PlaceholderUiState`, `PlaceholderScreenTest`, `PlaceholderViewModelTest` and the
  `placeholder` package (FR-022; contracts/placeholder-screen.md of feature 001 is superseded).
- Moved: `DesignTokenPreview` and `DesignTokenPreviewTest` to `audio.tembra.ui.devtools`.
- Kept: `WindowWidth` (now fed from `WindowSizeClass`, still the width bucket; `fromWidth(Dp)`
  stays for tests), the whole theme package, `App(appGraph)` signature extended with a defaulted
  parameter so both platform shells keep compiling until they are updated.
- `App` still needs the engine graph only for `AppGraph`; no destination touches the engine in
  this feature (Constitution II).
