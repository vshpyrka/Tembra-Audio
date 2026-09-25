---

description: "Task list for feature 002: adaptive navigation shell and Home"
---

# Tasks: Adaptive Navigation Shell and Home

**Input**: Design documents from `/specs/002-adaptive-navigation-shell/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R12), data-model.md, contracts/ (navigation-shell.md, design-system-additions.md, developer-view.md), quickstart.md

**Tests**: Required. Constitution III (test-first, non-negotiable) and spec FR-020: every implementation task is preceded by the test that fails without it. Run a lane after each group: `cd app && ./gradlew checkAndroidHost` (JVM), `./gradlew checkIos` (simulator), `./gradlew checkAndroidDevice` (device), always with `--warning-mode all`.

**Organization**: Tasks are grouped by user story so each story is an independently testable increment. Setup and foundational phases hold the shared tokens every story needs.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on unfinished tasks)
- **[Story]**: US1–US5 from spec.md
- Paths are repository-relative. Gradle root is `app/`; shared UI code lives under `app/shared/ui/src/<sourceSet>/kotlin/audio/tembra/ui/`; compose resources under `app/shared/ui/src/commonMain/composeResources/`.

## Path Conventions

- `commonMain` = `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/`
- `commonTest` = `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/` (runs on the JVM host and the iOS simulator)
- `uiTest` = `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/` (`runComposeUiTest`; iOS simulator and Android device)
- `androidHostTest` = `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/` (JVM only, may read repository files through the `tembra.repoRoot` system property)
- `androidDeviceTest` = `app/shared/ui/src/androidDeviceTest/kotlin/audio/tembra/ui/`
- Screenshots: `specs/002-adaptive-navigation-shell/screenshots/<platform>-<compact|medium|expanded|short>-<destination>.png`; outcomes of manual checks go to `specs/002-adaptive-navigation-shell/screenshots/NOTES.md`

---

## Phase 1: Setup (dependencies and assets)

**Purpose**: add the four libraries from research R11, the glyph pipeline from R6, and prove the dependency set resolves before any code depends on it.

- [X] T001 Add to `app/gradle/libs.versions.toml`: versions `androidx-navigation3 = "1.1.2"` and `compose-material3-adaptive = "1.2.0"`; libraries `androidx-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "androidx-navigation3" }`, `androidx-lifecycle-viewmodel-navigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "androidx-lifecycle" }`, `compose-material3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "compose-material3-adaptive" }`, `compose-material3-adaptive-navigation-suite = { module = "org.jetbrains.compose.material3:material3-adaptive-navigation-suite", version.ref = "compose-material3" }`, each with a comment pointing at research R11
- [X] T002 Add `implementation(libs.androidx.navigation3.ui)`, `implementation(libs.androidx.lifecycle.viewmodel.navigation3)`, `implementation(libs.compose.material3.adaptive)` and `implementation(libs.compose.material3.adaptive.navigation.suite)` to the `commonMain.dependencies` block in `app/shared/ui/build.gradle.kts`
- [X] T003 [P] Enable `buildFeatures { buildConfig = true }` inside the `android { }` block of `app/androidApp/build.gradle.kts` (needed by the `BuildConfig.DEBUG` gate in Phase 8)
- [X] T004 Prove resolution (research R11 UNVERIFIED): run `cd app && ./gradlew :shared:ui:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug --warning-mode all`; both must succeed with zero new warnings; append the outcome (and any exclusion needed) to research R11 in `specs/002-adaptive-navigation-shell/research.md`
- [X] T005 [P] Create `app/tools/fetch-material-symbols.sh` (executable): usage `fetch-material-symbols.sh <glyph> [<glyph> ...]`; constant `COMMIT=27e9ef1dbeedc13d682fece4a58e1eda4cb0961a`; for each glyph download `https://raw.githubusercontent.com/google/material-design-icons/$COMMIT/symbols/android/<glyph>/materialsymbolsoutlined/<glyph>_24px.xml`, remove the `android:tint="..."` attribute, replace `android:fillColor="@android:color/white"` with `android:fillColor="#FFFFFFFF"`, fail if any `@` or `?attr` remains, prepend the header `<!-- Material Symbols Outlined "<glyph>" from google/material-design-icons@<COMMIT> (Apache-2.0); fetched by tools/fetch-material-symbols.sh, do not edit -->`, and write `app/shared/ui/src/commonMain/composeResources/drawable/ic_<glyph>_24.xml`; re-running overwrites identically (idempotent)
- [X] T006 [P] Download `https://raw.githubusercontent.com/google/material-design-icons/27e9ef1dbeedc13d682fece4a58e1eda4cb0961a/LICENSE` to `app/shared/ui/src/commonMain/composeResources/files/LICENSE-MaterialSymbols.txt`

---

## Phase 2: Foundational (design tokens every story needs)

**Purpose**: icons, motion, chrome metrics, window buckets and layout mode as tokens and pure functions, each guarded by a test. No screen yet.

**⚠️ CRITICAL**: no user story work starts until T021 is green.

### Tests (write first, watch them fail)

- [X] T007 [P] Create `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/IconAssetsTest.kt`: resolve `<tembra.repoRoot>/app/shared/ui/src/commonMain/composeResources/drawable/`; assert the six files `ic_home_24.xml`, `ic_graphic_eq_24.xml`, `ic_music_note_24.xml`, `ic_av_timer_24.xml`, `ic_folder_24.xml`, `ic_person_24.xml` exist; each contains `android:width="24dp"`, contains the commit `27e9ef1dbeedc13d682fece4a58e1eda4cb0961a` in its header, and contains neither `@` nor `?attr`; assert `files/LICENSE-MaterialSymbols.txt` starts with the Apache License header
- [X] T008 Run `app/tools/fetch-material-symbols.sh home graphic_eq music_note av_timer folder person` (depends on T005; must precede the icon code in this phase); confirm the six files appear under `app/shared/ui/src/commonMain/composeResources/drawable/` and that `Res.drawable.ic_home_24` is generated by `cd app && ./gradlew :shared:ui:generateComposeResClass`
- [X] T009 [P] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/theme/TembraMotionTest.kt`: `TembraMotion()` has `standardDuration == 300`, `standardEasing` transforms 0f→0f and 1f→1f, `reduceMotion == false`, `effectiveDuration == 300`; `TembraMotion(reduceMotion = true).effectiveDuration == 0`
- [X] T010 [P] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/theme/ChromeMetricsTest.kt`: `TembraSpacing()` has `topBarHeight == 56.dp`, `bottomBarHeight == 64.dp`, `railWidth == 64.dp`, `supportingPaneWidth == 320.dp`, `navItemMinWidth == 56.dp`, `railItemWidth == 48.dp`, `navBarIconSize == 20.dp`, `navRailIconSize == 22.dp`, `navItemGap == 4.dp`
- [X] T011 [P] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/LayoutModeTest.kt`: `LayoutMode.from(width, isShort)` returns `BottomBar` for (`Compact`, false) and (`Compact`, true); `Rail` for (`Medium`, false), (`Medium`, true) and (`Expanded`, true); `RailWithPanel` for (`Expanded`, false). `LayoutMode.from(WindowSizeClass(minWidthDp, minHeightDp))` returns `BottomBar` for (599, 800), `Rail` for (600, 800), (839, 800) and (1024, 479), `RailWithPanel` for (840, 480) and (1024, 800); `WindowWidth.from(WindowSizeClass)` returns `Compact` for 599, `Medium` for 600 and 839, `Expanded` for 840
- [X] T012 [P] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/theme/ReduceMotionTest.kt`: `runComposeUiTest { setContent { TembraTheme { reduce = TembraTheme.motion.reduceMotion } } }` gives `false`; `runComposeUiTest(effectContext = object : MotionDurationScale { override val scaleFactor = 0f }) { ... }` gives `true`; and `rememberReduceMotion()` reports the same values

### Implementation

- [X] T013 [P] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraIcon.kt`: `enum class TembraIcon(internal val resource: DrawableResource) { Home(Res.drawable.ic_home_24), Timeline(Res.drawable.ic_graphic_eq_24), Tuner(Res.drawable.ic_music_note_24), Metronome(Res.drawable.ic_av_timer_24), Projects(Res.drawable.ic_folder_24), Profile(Res.drawable.ic_person_24) }` with a KDoc naming the source commit and the fetch script
- [X] T014 [P] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraIcons.kt`: `object TembraIcons { @Composable fun vector(icon: TembraIcon): ImageVector = vectorResource(icon.resource) }`
- [X] T015 [P] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraMotion.kt` (`@Immutable data class TembraMotion(val standardDuration: Int = 300, val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f), val reduceMotion: Boolean = false) { val effectiveDuration: Int get() = if (reduceMotion) 0 else standardDuration }`, KDoc citing Material 3 `duration.medium2` / `easing.standard`), `LocalTembraMotion.kt` (`compositionLocalOf { TembraMotion() }`) and `ReduceMotion.kt` (`@Composable fun rememberReduceMotion(): Boolean = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f`)
- [X] T016 Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraTheme.kt`: provide `LocalTembraMotion provides TembraMotion(reduceMotion = rememberReduceMotion())` in `TembraTheme` and add `val motion: TembraMotion @Composable @ReadOnlyComposable get() = LocalTembraMotion.current` to `object TembraTheme` (depends on T015)
- [X] T017 [P] Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraSpacing.kt`: add `topBarHeight = 56.dp`, `bottomBarHeight = 64.dp`, `railWidth = 64.dp`, `supportingPaneWidth = 320.dp`, `navItemMinWidth = 56.dp`, `railItemWidth = 48.dp`, `navBarIconSize = 20.dp`, `navRailIconSize = 22.dp`, `navItemGap = 4.dp`, each with its design source from data-model.md §6 in the KDoc
- [X] T018 [P] Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/WindowWidth.kt`: add `fun from(windowSizeClass: WindowSizeClass): WindowWidth` = `Compact` unless `isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)`, `Medium` unless `isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)`, else `Expanded`; keep `fromWidth(Dp)`
- [X] T019 [P] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/LayoutMode.kt`: `enum class LayoutMode { BottomBar, Rail, RailWithPanel }` with `companion object { fun from(width: WindowWidth, isShort: Boolean): LayoutMode` (Compact → BottomBar; Medium → Rail; Expanded → if isShort Rail else RailWithPanel) `; fun from(windowSizeClass: WindowSizeClass): LayoutMode = from(WindowWidth.from(windowSizeClass), isShort = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) }`
- [X] T020 Run `cd app && ./gradlew checkAndroidHost --warning-mode all`: T007 and T009–T011 green, zero warnings
- [X] T021 Run `cd app && ./gradlew checkIos --warning-mode all`: T009–T012 green on the simulator, zero warnings

**Checkpoint**: tokens, icons, motion and layout mode exist and are tested; user stories can begin.

---

## Phase 3: User Story 1 - Start on Home and move between the six destinations (Priority: P1) 🎯 MVP

**Goal**: the app opens on Home; the top bar names the destination; the six items in the bottom bar (or rail on wide windows) switch destinations with a 300 ms fade; re-selecting does nothing; Android back returns to Home.

**Independent Test**: launch on a phone and a simulator, confirm Home, tap all six items, confirm title and active marker, press back on Android (spec US1 scenarios 1–4).

### Tests for User Story 1 (write first, watch them fail)

- [X] T022 [P] [US1] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/DestinationTest.kt`: `Destination.entries` is exactly `[Home, Timeline, Tuner, Metronome, Projects, Profile]`; ids are `home, timeline, tuner, metronome, projects, profile`; `label` and `title` equal the enum name for every value; ids, labels and titles are unique; only `Home.hasSupportingPane` is true; `placeholderMessage` values are exactly: Home "This is your starting point in Tembra Audio.", Timeline "The timeline arranger arrives with the transport and timeline feature.", Tuner "The instrument tuner arrives with the tuner feature.", Metronome "The metronome arrives with the metronome feature.", Projects "Your projects and library arrive with the projects feature.", Profile "Your profile and sign-in arrive with the collaboration feature."
- [X] T023 [P] [US1] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/ShellUiStateTest.kt`: `ShellUiState().selected == Home`; `ShellUiState().backStack == listOf(Home)`; `ShellUiState(selected = Tuner).backStack == listOf(Home, Tuner)`
- [X] T024 [P] [US1] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/ShellViewModelTest.kt` (Dispatchers.setMain pattern from feature 001): starts with `selected == Home`; `select(Tuner)` → `Tuner`; `select(Tuner)` again leaves `uiState.value` the same instance (no emission); `back()` from `Tuner` returns `true` and selects `Home`; `back()` on `Home` returns `false` and leaves the state unchanged; `select(Tuner)` then `select(Profile)` leaves `Profile` selected (last call wins)
- [X] T025 [P] [US1] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraTopBarTest.kt`: `TembraTopBar(title = "Tuner")` shows text "TEMBRA AUDIO" and a node `chrome.topBar.title` with text "Tuner"; root `chrome.topBar` has height at least 56 dp; `showsNothingElse`: exactly two text nodes under `chrome.topBar` (`onAllNodes(hasParent(hasTestTag("chrome.topBar")) and hasText(...))` count 2) and no node with a click action (`onAllNodes(hasClickAction()).assertCountEquals(0)`)
- [X] T026 [P] [US1] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraNavigationBarTest.kt`: render `TembraNavigationBar(items = six NavigationItemSpec, selectedId = "home", onSelect)`; root `chrome.bottomBar` exists; nodes `nav.item.<id>` exist for all six with their label text; `nav.item.home` `assertIsSelected()` and the other five `assertIsNotSelected()`; `performClick()` on `nav.item.tuner` calls `onSelect("tuner")`; `fitsSixLabelledItemsAt320dp`: render the bar inside `Box(Modifier.width(320.dp))`; all six `nav.item.*` nodes and their label text nodes `assertIsDisplayed()`; repeat under `CompositionLocalProvider(LocalDensity provides Density(density = 2.75f, fontScale = 2f))`
- [X] T027 [P] [US1] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraNavigationRailTest.kt`: the same assertions for `TembraNavigationRail` with root `chrome.rail`
- [X] T028 [P] [US1] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/navigation/NavigationShellTest.kt` driving `NavigationShellContent(state, layoutMode, onSelect, onBack)` with `state` in a `mutableStateOf`: `bottomBarLayout` (BottomBar → `chrome.bottomBar` exists, `chrome.rail` and `chrome.supportingPane` do not, six `nav.item.*`, `screen.home` displayed, `chrome.topBar.title` text "Home"); `selectingAnItemCallsOnSelectAndMarksIt` (click `nav.item.tuner` → callback receives `Tuner`; after `state = ShellUiState(Tuner)`: `nav.item.tuner` selected, title "Tuner", `screen.tuner` displayed); `reselectingTheActiveItemIsANoOp` (click `nav.item.home` on Home → callback not invoked); `transitionFinishesWithinTheStandardDuration` (`mainClock.autoAdvance = false`; set Tuner; `mainClock.advanceTimeBy(300)` plus one frame; `screen.tuner` displayed and `screen.home` does not exist); `reduceMotionSwitchesInstantly` (effect context with `MotionDurationScale` 0f; set Tuner; advance one frame; `screen.tuner` displayed); all cases set content through a shared helper `withShellLocals { ... }` defined in the same file that provides `LocalViewModelStoreOwner` (an object owning a fresh `ViewModelStore`) and, if `LocalLifecycleOwner` is missing on iOS, a `LifecycleRegistry`-backed owner, because `TembraNavDisplay` installs `rememberViewModelStoreNavEntryDecorator()`
- [X] T029 [P] [US1] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/navigation/RememberLayoutModeTest.kt`: inside `TembraTheme`, assert `rememberLayoutMode() == LayoutMode.from(currentWindowAdaptiveInfo().windowSizeClass)` and that the value is one of the three `LayoutMode` entries; the only test that exercises the public composable (Constitution III)
- [X] T030 [P] [US1] Create `app/shared/ui/src/androidDeviceTest/kotlin/audio/tembra/ui/navigation/AndroidBackTest.kt` with `createAndroidComposeRule<ComponentActivity>()`: set content `TembraTheme { NavigationShell(viewModel = ShellViewModel(), layoutMode = LayoutMode.BottomBar) }`; click `nav.item.tuner`; `Espresso.pressBack()` → `screen.home` displayed; `Espresso.pressBack()` again → `NoActivityResumedException` (activity finished)

### Implementation for User Story 1

- [X] T031 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/Destination.kt`: `enum class Destination(val id: String, val label: String, val title: String, val icon: TembraIcon, val hasSupportingPane: Boolean, val placeholderMessage: String)` with the six values and strings from T022, declaration order Home, Timeline, Tuner, Metronome, Projects, Profile
- [X] T032 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/DestinationKey.kt`: `data class DestinationKey(val destination: Destination) : NavKey`
- [X] T033 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/ShellUiState.kt`: `data class ShellUiState(val selected: Destination = Destination.Home) { val backStack: List<Destination> get() = if (selected == Destination.Home) listOf(Destination.Home) else listOf(Destination.Home, selected) }`
- [X] T034 [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/ShellViewModel.kt`: `@Inject @ContributesIntoMap(AppScope::class) @ViewModelKey class ShellViewModel : ViewModel()` with `MutableStateFlow(ShellUiState())`, `fun select(destination)` (no-op when equal), `fun back(): Boolean` (depends on T031, T033)
- [X] T035 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/NavigationItemSpec.kt`: `data class NavigationItemSpec(val id: String, val label: String, val icon: TembraIcon)`
- [X] T036 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraTopBar.kt`: `Surface(surfaceContainerLowest)` with `WindowInsets.statusBars.union(WindowInsets.displayCutout)` padding, content row of height `spacing.topBarHeight`, page-margin padding; overline "TEMBRA AUDIO" in `labelSmall`, colour `primary`, `letterSpacing` from the style; title in `headlineSmall`; testTags `chrome.topBar`, `chrome.topBar.title`; nothing else (FR-007)
- [X] T037 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraNavigationBar.kt`: `Surface(surfaceContainerLowest)` + `WindowInsets.navigationBars` padding + `Row` of height `spacing.bottomBarHeight`, horizontal padding `spacing.gutter`, `Arrangement.SpaceAround`; each item a `Column` (icon `navBarIconSize` above `labelSmall` label, `navItemGap`) with `sizeIn(minWidth = navItemMinWidth, minHeight = touchTargetMin)`, `CircleShape` pill; active item: background `primaryContainer`, content `onPrimaryContainer`, glow from `TembraTheme.daw.clipGlow` drawn with `drawBehind`, fill colour animated with `animateColorAsState(tween(TembraTheme.motion.effectiveDuration, easing = standardEasing))`; inactive `onSurfaceVariant`; `clickable { onSelect(id) }`; testTags `chrome.bottomBar` and `nav.item.<id>`; `semantics { selected = (id == selectedId) }` (role, description, hover and focus arrive in US4)
- [X] T038 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraNavigationRail.kt`: `Surface(surfaceContainerLowest)` of width `spacing.railWidth` filling height, padded by `WindowInsets.displayCutout.only(WindowInsetsSides.Start)` so a landscape cutout never overlaps the rail, `Column` with `spacing.sm` horizontal padding and 6 dp vertical gaps; items `railItemWidth` wide, icon `navRailIconSize` above `labelSmall`, shape `TembraTheme.shapes.large` (16 dp); active: background `secondaryContainer`, content `onSecondaryContainer`, bold label, colour animated as in T037; inactive `onSurfaceVariant`; testTags `chrome.rail`, `nav.item.<id>`; `semantics { selected }`
- [X] T039 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/PlaceholderDestination.kt`: `@Composable fun PlaceholderDestination(destination: Destination, modifier)` = `Column` with page margins, `testTag("screen.${destination.id}")`, title in `headlineLarge` (or `headlineLgMobile` when `WindowWidth` is Compact, read from a parameter defaulting to `Compact`), and `Text(destination.placeholderMessage, bodyLarge, testTag "screen.${id}.message")`; nothing else (FR-011, FR-013)
- [X] T040 [P] [US1] Create the six screen files, each a one-line composable calling `PlaceholderDestination`: `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/home/HomeScreen.kt`, `.../timeline/TimelineScreen.kt`, `.../tuner/TunerScreen.kt`, `.../metronome/MetronomeScreen.kt`, `.../projects/ProjectsScreen.kt`, `.../profile/ProfileScreen.kt` (packages `audio.tembra.ui.home` … `audio.tembra.ui.profile`)
- [X] T041 [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/DestinationContent.kt`: `@Composable fun DestinationContent(destination: Destination)` exhaustive `when` → `HomeScreen()`, `TimelineScreen()`, `TunerScreen()`, `MetronomeScreen()`, `ProjectsScreen()`, `ProfileScreen()` (depends on T040)
- [X] T042 [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/TembraNavDisplay.kt`: `@Composable internal fun TembraNavDisplay(backStack: List<Destination>, layoutMode: LayoutMode, onBack: () -> Unit, modifier)` calling `NavDisplay(backStack = backStack.map(::DestinationKey), onBack = onBack, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()), transitionSpec = fade, popTransitionSpec = fade, predictivePopTransitionSpec = { fade() }, entryProvider = entryProvider { entry<DestinationKey> { key -> DestinationContent(key.destination) } })` where `fade` = `ContentTransform(EnterTransition.None, ExitTransition.None)` when `TembraTheme.motion.reduceMotion`, else `fadeIn(tween(effectiveDuration, easing = standardEasing)) togetherWith fadeOut(tween(effectiveDuration, easing = standardEasing))`; `layoutMode` is accepted now and used by the scene strategy in US3 (depends on T032, T041)
- [X] T043 [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/NavigationShellContent.kt`: `Column(testTag "nav.shell")` → `TembraTopBar(title = state.selected.title)` then `NavigationSuiteScaffoldLayout(navigationSuiteType = if (layoutMode == BottomBar) NavigationSuiteType.NavigationBar else NavigationSuiteType.NavigationRail, state = rememberNavigationSuiteScaffoldState(), navigationSuite = { bar or rail built from Destination.entries.map { NavigationItemSpec(it.id, it.label, it.icon) }, selectedId = state.selected.id, onSelect = { id -> onSelect(Destination.entries.first { it.id == id }) } }) { TembraNavDisplay(state.backStack, layoutMode, onBack) }` (depends on T036–T038, T042)
- [X] T044 [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/RememberLayoutMode.kt`: `@Composable fun rememberLayoutMode(): LayoutMode = LayoutMode.from(currentWindowAdaptiveInfo().windowSizeClass)` (recomputes on every window change because `currentWindowAdaptiveInfo()` is state-backed; research R3)
- [X] T045 [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/NavigationShell.kt`: `@Composable fun NavigationShell(viewModel: ShellViewModel = metroViewModel(), layoutMode: LayoutMode = rememberLayoutMode(), modifier)`: `collectAsStateWithLifecycle()` then `NavigationShellContent(state, layoutMode, onSelect = viewModel::select, onBack = { viewModel.back() })` (depends on T034, T043, T044)
- [X] T046 [US1] Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/App.kt`: render `TembraTheme { NavigationShell() }` instead of `PlaceholderScreen()` (the placeholder files stay compiled until Phase 8 removes them)
- [X] T047 [US1] Run `cd app && ./gradlew checkAndroidHost checkIos checkAndroidDevice --warning-mode all`: T022–T030 green, zero warnings
- [X] T048 [US1] Launch on Android and iOS per quickstart §1; confirm Home, six items, title changes, fade, no-op reselect, Android back; capture `specs/002-adaptive-navigation-shell/screenshots/android-compact-home.png` and `ios-compact-home.png`; start `specs/002-adaptive-navigation-shell/screenshots/NOTES.md` with device names, OS versions and the outcome

**Checkpoint**: MVP. Navigation works on both platforms in the compact layout; wide windows already show the rail because the layout mode is measured, but its behaviour is verified in US2.

---

## Phase 4: User Story 2 - The frame adapts to the window (Priority: P1)

**Goal**: bottom bar under 600 dp, rail from 600 dp, rail in short wide windows, selection preserved across live resizes (spec US2 scenarios 1, 2, 4, 5; scenario 3's panel arrives in US3).

**Independent Test**: render at 360 dp, 700 dp and 1024 dp on both platforms; resize across 600 dp and 840 dp while on Tuner and confirm Tuner stays selected.

### Tests for User Story 2 (write first, watch them fail)

- [X] T049 [P] [US2] Extend `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/navigation/NavigationShellTest.kt`: `railLayout` (Rail → `chrome.rail` exists, `chrome.bottomBar` and `chrome.supportingPane` do not, six items); `railWithPanelWithoutSupportingContentShowsNoPane` (RailWithPanel on Tuner → rail, no pane); `layoutSwitchKeepsTheSelection` (state Tuner; `layoutMode` in a `mutableStateOf` cycled BottomBar → Rail → RailWithPanel → BottomBar; after each change `nav.item.tuner` selected and `screen.tuner` displayed) (set content through the `withShellLocals` helper from T028)
- [X] T050 [P] [US2] Extend `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/LayoutModeTest.kt` with the round trip `from(from(wsc) inputs)` for the boundary pairs (599/600, 839/840, 479/480) in both directions to satisfy SC-004's "both directions"

### Implementation for User Story 2

- [X] T051 [US2] Make T049 pass in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/NavigationShellContent.kt`: confirm the rail branch renders `TembraNavigationRail` at the start edge below the top bar and the bar branch at the bottom; key the `NavigationSuiteScaffoldLayout` content so switching `navigationSuiteType` does not recreate `TembraNavDisplay` (selection and content state survive)
- [X] T052 [US2] Manual live-resize checks per quickstart §3 on a foldable AVD (fold/unfold on Tuner), a desktop-mode/freeform window dragged across 600 dp and 840 dp, and an iPad in Split View / Stage Manager; record devices and outcomes in `specs/002-adaptive-navigation-shell/screenshots/NOTES.md`
- [X] T053 [US2] Capture `specs/002-adaptive-navigation-shell/screenshots/android-medium-home.png` (tablet or foldable portrait, 600–839 dp), `android-short-home.png` (phone landscape: rail, no pane), `ios-medium-home.png` (iPad mini portrait) and `ios-short-home.png` (iPhone 17 landscape); confirm no clipped or overlapping elements

**Checkpoint**: US1 and US2 together give the full adaptive navigation without the side panel.

---

## Phase 5: User Story 3 - The Home page and its side panel (Priority: P2)

**Goal**: Home shows only its title and sentence; on 840 dp+ windows taller than 480 dp a 320 dp "Library" panel with an empty state sits beside Home and nowhere else, never collapsible (spec US3, FR-011, FR-012).

**Independent Test**: open Home at expanded width → panel; open Tuner → no panel; rotate a phone to landscape on Home → no panel.

### Tests for User Story 3 (write first, watch them fail)

- [X] T054 [P] [US3] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/SupportingPaneSceneStrategyTest.kt`: build `NavEntry<DestinationKey>` instances by hand; with `enabled = false` `calculateScene` returns null; with `enabled = true` and a top entry without `SupportingPaneKey` metadata returns null; with `enabled = true` and metadata `SupportingPane.Library` returns a `SupportingPaneScene` whose `key == top.contentKey`, `entries == listOf(top)`, `previousEntries == entries.dropLast(1)` and `pane == Library`; two calls with equal inputs produce equal scenes (data class)
- [X] T055 [P] [US3] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraSupportingPaneTest.kt`: `TembraSupportingPane(title = "Library") { Text("body") }` shows "Library" and "body", root `chrome.supportingPane` has width equal to 320 dp (`assertWidthIsEqualTo(320.dp)`)
- [X] T056 [P] [US3] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/EmptyStateTest.kt`: `EmptyState("Loops and sounds will appear here.")` shows the text under `chrome.emptyState`
- [X] T057 [P] [US3] Extend `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/navigation/NavigationShellTest.kt`: `railWithPanelShowsThePaneOnHomeOnly` (RailWithPanel + Home → `chrome.supportingPane` displayed containing text "Library" and "Loops and sounds will appear here."; RailWithPanel + Tuner → no pane; Rail + Home → no pane); `homeShowsOnlyTitleAndSentence` (`screen.home` contains text "Home" and `screen.home.message` text "This is your starting point in Tembra Audio." and no other text node); `paneHasNoControls` (no node with click action inside `chrome.supportingPane`) (set content through the `withShellLocals` helper from T028)

### Implementation for User Story 3

- [X] T058 [P] [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/SupportingPane.kt`: `enum class SupportingPane { Library }`
- [X] T059 [P] [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/SupportingPaneKey.kt`: `object SupportingPaneKey : NavMetadataKey<SupportingPane>` and `fun supportingPane(pane: SupportingPane): Map<String, Any> = metadata { put(SupportingPaneKey, pane) }`
- [X] T060 [P] [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraSupportingPane.kt`: `@Composable fun TembraSupportingPane(title: String, modifier, content: @Composable ColumnScope.() -> Unit)`: width `spacing.supportingPaneWidth`, fills height, `surfaceContainerLow`, shape `TembraTheme.shapes.medium` (12 dp), padding `spacing.md`, title `headlineSmall`, testTag `chrome.supportingPane`
- [X] T061 [P] [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/EmptyState.kt`: centred `bodyMedium` text in `onSurfaceVariant`, testTag `chrome.emptyState`
- [X] T062 [P] [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/home/LibraryPane.kt`: `TembraSupportingPane(title = "Library") { EmptyState("Loops and sounds will appear here.") }`
- [X] T063 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/SupportingPaneContent.kt`: `@Composable fun SupportingPaneContent(pane: SupportingPane) = when (pane) { Library -> LibraryPane() }` (depends on T062)
- [X] T064 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/SupportingPaneScene.kt`: `data class SupportingPaneScene<T : Any>(override val key: Any, val main: NavEntry<T>, val pane: SupportingPane, override val previousEntries: List<NavEntry<T>>) : Scene<T>` with `entries = listOf(main)` and `content = { Row(Modifier.fillMaxSize()) { Box(Modifier.weight(1f).fillMaxHeight()) { main.Content() }; SupportingPaneContent(pane) } }` (depends on T058, T063)
- [X] T065 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/SupportingPaneSceneStrategy.kt`: `class SupportingPaneSceneStrategy<T : Any>(private val enabled: Boolean) : SceneStrategy<T>` whose `SceneStrategyScope<T>.calculateScene(entries)` returns null unless enabled and `entries.last().metadata[SupportingPaneKey]` is non-null, else `SupportingPaneScene(top.contentKey, top, pane, entries.dropLast(1))` (depends on T059, T064)
- [X] T066 [US3] Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/navigation/TembraNavDisplay.kt`: pass `sceneStrategies = listOf(SupportingPaneSceneStrategy(enabled = layoutMode == LayoutMode.RailWithPanel))` (single-pane fallback is implicit) and change the entry to `entry<DestinationKey>(metadata = { key -> if (key.destination.hasSupportingPane) supportingPane(SupportingPane.Library) else emptyMap() }) { … }` (depends on T065)
- [X] T067 [US3] Run `cd app && ./gradlew checkAndroidHost checkIos checkAndroidDevice --warning-mode all`: T054–T057 green, zero warnings
- [X] T068 [US3] Capture `specs/002-adaptive-navigation-shell/screenshots/android-expanded-home.png` (Pixel Tablet landscape or a freeform window ≥ 840 × 480 dp: rail + Library panel), `android-expanded-tuner.png` (no panel), `ios-expanded-home.png` (iPad Pro 13-inch landscape) and `ios-expanded-tuner.png`; compare the rail against `design/stitch/screens/export-mastering/tablet/screenshot.jpg` and the panel against `design/stitch/screens/timeline-arranger/tablet/screenshot.jpg`; note results in `screenshots/NOTES.md`

**Checkpoint**: all three layouts complete, including the panel; spec US2 scenario 3 now holds.

---

## Phase 6: User Story 4 - Pointer, keyboard and screen reader (Priority: P2)

**Goal**: every navigation item shows hover and focus states, is keyboard-activatable, exposes label and selected state to accessibility services, and measures at least 44 dp (spec US4, FR-014, FR-015).

**Independent Test**: hover with a mouse, tab through the items with a keyboard and press Enter, read the items with TalkBack / VoiceOver, measure targets.

### Tests for User Story 4 (write first, watch them fail)

- [X] T069 [P] [US4] Extend `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraNavigationBarTest.kt`: every `nav.item.<id>` has `Role.Tab`, `contentDescription` equal to its label, `assertWidthIsAtLeast(44.dp)` and `assertHeightIsAtLeast(44.dp)`; `keyboardActivatesAnItem` (request `InputMode.Keyboard` with feature 001's guard, `requestFocus()` on `nav.item.metronome`, `assertIsFocused()`, `performKeyInput { pressKey(Key.Enter) }` → `onSelect("metronome")`); `focusedItemShowsAnIndicator` (after `requestFocus`, `captureToImage()` of the item has a pixel of the `primary` colour on its 2 dp outer edge)
- [X] T070 [P] [US4] Extend `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraNavigationRailTest.kt`: the same role, description, size and keyboard assertions; `hoverFillsTheItem` (`performMouseInput { enter(center) }` on an inactive item, then `captureToImage()` shows the `surfaceContainerHigh` colour at a corner pixel inside the item's rounded square, and the bar test's counterpart asserts the label colour changed from `onSurfaceVariant`)
- [X] T071 [P] [US4] Extend `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/navigation/NavigationShellTest.kt`: `keyboardActivatesAnItemThroughTheShell` (focus `nav.item.projects`, Enter → `onSelect(Projects)`) and `everyItemMeetsTheTouchTargetInBothLayouts` (BottomBar and Rail: all six items ≥ 44 dp both ways)

### Implementation for User Story 4

- [X] T072 [US4] Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraNavigationBar.kt`: per item a `remember { MutableInteractionSource() }`; `clickable(interactionSource, indication = null, role = Role.Tab)`; `collectIsHoveredAsState()` raises content to `onSurface` for inactive items; `collectIsFocusedAsState()` draws a 2 dp `primary` border in the item shape; `semantics { contentDescription = label; selected = … }` (role comes from `clickable`); `sizeIn(minWidth = navItemMinWidth, minHeight = touchTargetMin)` already present; `focusable` implied by `clickable`
- [X] T073 [US4] Edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraNavigationRail.kt`: the same interaction source pattern; hover fills the item with `surfaceContainerHigh` and raises content to `onSurface`; focus border as in T072; `sizeIn(minWidth = touchTargetMin, minHeight = touchTargetMin)` on the 48 dp item (it grows to 48 × ≥ 44)
- [X] T074 [US4] Run `cd app && ./gradlew checkIos checkAndroidDevice --warning-mode all`: T069–T071 green, zero warnings (the keyboard cases may print the touch-mode skip line on Android exactly as feature 001's test does; they must pass on iOS)
- [ ] T075 [US4] Manual checks per quickstart §5: mouse hover in Android desktop mode or on an iPad with trackpad, hardware-keyboard traversal and Enter/Space on both platforms, TalkBack and VoiceOver announcements ("<label>, selected" on the active item); record in `specs/002-adaptive-navigation-shell/screenshots/NOTES.md` and capture `android-expanded-home-hover.png`

**Checkpoint**: input-agnostic navigation on both platforms.

---

## Phase 7: User Story 5 - The shared design system gains the icons and the frame (Priority: P3)

**Goal**: contributors find icons, chrome components and type styles by name, the licence inventory is complete, and the glyphs render identically on both platforms (spec US5, FR-016, FR-018, SC-006, SC-008).

**Independent Test**: compare the six glyphs across platforms; read the licence inventory; answer "which icon, component and label style for a new destination" from the README in under five minutes.

- [X] T076 [P] [US5] Edit `app/THIRD_PARTY_LICENSES.md`: add rows for `navigation3-ui` 1.1.2 (JetBrains, Apache-2.0), `androidx.navigation3:navigation3-runtime` 1.1.7, `lifecycle-viewmodel-navigation3` 2.11.0, `material3.adaptive:adaptive` 1.2.0, `material3-adaptive-navigation-suite` 1.9.0, `androidx.window:window-core` 1.4.0, and "Material Symbols Outlined glyphs (`shared/ui/src/commonMain/composeResources/drawable/ic_*_24.xml`)" with version `google/material-design-icons@27e9ef1dbeedc13d682fece4a58e1eda4cb0961a`, Apache-2.0, licence file `composeResources/files/LICENSE-MaterialSymbols.txt`; update "Last reviewed" to 2026-09-24 (feature 002)
- [X] T077 [P] [US5] Edit `app/README.md`: module map entries for `audio.tembra.ui.navigation` (shell, Navigation 3 wiring, scene strategy), `audio.tembra.ui.designsystem` (chrome components), `audio.tembra.ui.devtools`, and the six feature packages; "Where does X go?" rows: a new destination (enum value + screen composable + `DestinationContent` branch), an icon (`tools/fetch-material-symbols.sh <glyph>` + `TembraIcon` value), a chrome component (`designsystem`), a developer view (`devtools`); test-lane table rows for the new `uiTest` and `androidDeviceTest` classes; screenshot section gains the medium band and the `NOTES.md` convention
- [X] T078 [P] [US5] Edit `CLAUDE.md`: in the `:shared:ui` module description add "navigation shell (Navigation 3), design-system chrome components, debug-only devtools"; add the fetch script to the commands list
- [X] T079 [US5] Glyph parity: crop the bottom bar from `screenshots/android-compact-home.png` and `ios-compact-home.png` and compare the six glyph shapes side by side (they come from the same vector files; only platform anti-aliasing may differ); record in `specs/002-adaptive-navigation-shell/screenshots/NOTES.md`
- [X] T080 [US5] SC-008 dry run: using only `app/README.md`, write down the icon enum, chrome component and label style a seventh destination would use; must take under 5 minutes; record the answers and time in `specs/002-adaptive-navigation-shell/screenshots/NOTES.md`

**Checkpoint**: documentation and licensing complete.

---

## Phase 8: Polish & Cross-Cutting Concerns (developer view, cleanup, final verification)

**Purpose**: FR-022 (debug-only token preview, counter screen removed), zero-warning full lanes, remaining screenshot matrix, quickstart validation.

### Tests (write first, watch them fail)

- [X] T081 [P] Create `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/devtools/DeveloperViewRequestTest.kt`: `DeveloperViewRequest.parse("token-preview").view == DeveloperView.TokenPreview`; `parse(null).view == null`; `parse("nonsense").view == null`
- [X] T082 [P] Create `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/AppContentTest.kt`: `AppContent(DeveloperViewRequest(DeveloperView.TokenPreview)) { Text("shell") }` shows `devtools.tokenPreview` and no text "shell"; `AppContent(DeveloperViewRequest(null)) { Text("shell") }` shows "shell" and no `devtools.tokenPreview`

### Implementation

- [X] T083 [P] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/devtools/DeveloperView.kt`: `enum class DeveloperView(val argument: String) { TokenPreview("token-preview") }`
- [X] T084 [P] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/devtools/DeveloperViewRequest.kt`: `data class DeveloperViewRequest(val view: DeveloperView?) { companion object { fun parse(value: String?): DeveloperViewRequest = DeveloperViewRequest(DeveloperView.entries.firstOrNull { it.argument == value }) } }`
- [X] T085 `git mv app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/DesignTokenPreview.kt app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/devtools/DesignTokenPreview.kt` and `git mv app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/theme/DesignTokenPreviewTest.kt app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/devtools/DesignTokenPreviewTest.kt`; change both packages to `audio.tembra.ui.devtools`, fix imports; behaviour and assertions unchanged
- [X] T086 Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/devtools/DeveloperViewScreen.kt`: `@Composable fun DeveloperViewScreen(view: DeveloperView, modifier)`: `Scaffold` with page margins and vertical scroll; `when (view) { TokenPreview -> DesignTokenPreview(Modifier.testTag("devtools.tokenPreview")) }` (depends on T085)
- [X] T087 Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/AppContent.kt` (`@Composable fun AppContent(developerView: DeveloperViewRequest, shell: @Composable () -> Unit)` = `DeveloperViewScreen(view)` when `developerView.view != null`, else `shell()`) and edit `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/App.kt` to `fun App(appGraph: AppGraph, developerView: DeveloperViewRequest = DeveloperViewRequest(null))` rendering `TembraTheme { AppContent(developerView) { NavigationShell() } }` (depends on T084, T086)
- [X] T088 Edit `app/androidApp/src/main/kotlin/audio/tembra/android/MainActivity.kt`: `val request = if (BuildConfig.DEBUG) DeveloperViewRequest.parse(intent.getStringExtra("tembra.developer.view")) else DeveloperViewRequest(null)`; `setContent { App(appGraph, request) }`
- [X] T089 Edit `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/MainViewController.kt`: `fun MainViewController(developerView: String? = null): UIViewController = ComposeUIViewController { App(appGraph, DeveloperViewRequest.parse(developerView)) }`
- [X] T090 Edit `app/iosApp/iosApp/ContentView.swift`: inside `#if DEBUG` read `CommandLine.arguments.first { $0.hasPrefix("--tembra-developer-view=") }` and pass its value after `=` to `MainViewControllerKt.MainViewController(developerView:)`; outside DEBUG call `MainViewController(developerView: nil)`
- [X] T091 Delete the scaffold's counter screen: `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/placeholder/PlaceholderScreen.kt`, `PlaceholderScreenContent.kt`, `PlaceholderViewModel.kt`, `PlaceholderUiState.kt`, `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/placeholder/PlaceholderScreenTest.kt`, `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/placeholder/PlaceholderViewModelTest.kt`; remove the empty `placeholder` directories; keep `WindowWidth.kt` and `WindowWidthTest.kt` (depends on T087)
- [X] T092 Run all lanes: `cd app && ./gradlew checkAndroidHost checkIos checkAndroidDevice engineHostTest --warning-mode all` → every test green, zero compiler and lint warnings (SC-007); fix anything found before continuing
- [X] T093 Verify the developer view per quickstart §8 on Android (`adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.developer.view token-preview`) and iOS (`xcrun simctl launch "iPhone 17" audio.tembra.app --tembra-developer-view=token-preview`); confirm a release Android build (`./gradlew :androidApp:assembleRelease`) ignores the extra; record in `specs/002-adaptive-navigation-shell/screenshots/NOTES.md`
- [ ] T094 [P] Verify reduce motion per quickstart §7 on both platforms (Android `animator_duration_scale 0`, iOS Reduce Motion): switches are instant; restore the settings; record in `NOTES.md`
- [X] T095 [P] Capture the large-font screenshots `specs/002-adaptive-navigation-shell/screenshots/android-compact-home-largefont.png` (`font_scale 2.0`) and `ios-compact-home-largefont.png` (`content_size accessibility-extra-extra-extra-large`); labels unclipped, six items still fit; restore `1.0` / `medium`
- [ ] T096 Confirm the screenshot matrix is complete under `specs/002-adaptive-navigation-shell/screenshots/`: `{android,ios}-compact-home`, `{android,ios}-medium-home`, `{android,ios}-short-home`, `{android,ios}-expanded-home`, `{android,ios}-expanded-tuner`, `android-expanded-home-hover`, `{android,ios}-compact-home-largefont`, plus `NOTES.md`; review each against the Stitch references named in quickstart §9
- [X] T097 Walk through `specs/002-adaptive-navigation-shell/quickstart.md` top to bottom; correct any command or expectation that no longer matches the implementation
- [X] T098 Grep the new packages for literal visual values (`grep -rnE '\b[0-9]+\.(dp|sp)\b|Color\(0x' app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/{navigation,designsystem,home,timeline,tuner,metronome,projects,profile,devtools}`), for `TODO` without an issue reference, and for wildcard imports; must all return nothing (FR-017, Constitution VII); and `grep -rn 'Res.drawable.ic_' app/shared/ui/src/commonMain/kotlin/audio/tembra/ui --include=*.kt | grep -v '/theme/TembraIcon.kt'` returns nothing (FR-016)
- [X] T099 Final commit through the hook: write a conventional commit message and run `.specify/extensions/git/scripts/bash/auto-commit.sh after_implement --message-file <path>`; then `git log -1` shows the commit with the Co-Authored-By trailer

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: starts immediately; T004 must succeed before Phase 2 (dependency resolution is the biggest unknown, research R11)
- **Foundational (Phase 2)**: depends on Phase 1; blocks every user story (icons, motion, metrics, layout mode)
- **US1 (Phase 3)**: depends on Phase 2. The MVP.
- **US2 (Phase 4)**: depends on US1 (extends `NavigationShellContent` and its test)
- **US3 (Phase 5)**: depends on US1 (edits `TembraNavDisplay`); independent of US2's manual checks
- **US4 (Phase 6)**: depends on US1 (edits the bar and rail); independent of US2 and US3
- **US5 (Phase 7)**: depends on US1 screenshots (T048) for the parity check; T076–T078 can start after Phase 2
- **Polish (Phase 8)**: depends on all stories; T091 (deletion) must follow T087 (App no longer references the placeholder)

### Within Each User Story

- Tests are written first and must fail (Constitution III); implementation follows; the lane run closes the story
- Registry and state types before components; components before the shell; the shell before `App`

### Parallel Opportunities

- Phase 1: T003, T005, T006 in parallel with T001–T002
- Phase 2: the five tests T007 and T009–T012 in parallel (T008 runs the glyph script as soon as T007 fails); then T013, T014, T015, T017, T018, T019 in parallel (T016 after T015)
- US1: tests T022–T030 in parallel; then T031, T032, T033, T035, T036, T037, T038, T039, T040 in parallel; T034 after T031/T033; T041 after T040; T042 → T043 → T045 sequential; T044 in parallel with T042
- US3: T054–T056 in parallel; T058–T062 in parallel; then T063 → T064 → T065 → T066
- US4: T069–T071 in parallel; T072 and T073 in parallel
- US5: T076, T077, T078 in parallel
- Phase 8: T081–T084 in parallel; T094 and T095 in parallel with T093

---

## Parallel Example: User Story 1

```bash
# Tests first, all in parallel (different files):
Task: "DestinationTest in app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/DestinationTest.kt"
Task: "ShellUiStateTest in app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/ShellUiStateTest.kt"
Task: "ShellViewModelTest in app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/navigation/ShellViewModelTest.kt"
Task: "TembraTopBarTest in app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraTopBarTest.kt"
Task: "TembraNavigationBarTest in app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraNavigationBarTest.kt"
Task: "TembraNavigationRailTest in app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraNavigationRailTest.kt"
Task: "NavigationShellTest in app/shared/ui/src/uiTest/kotlin/audio/tembra/ui/navigation/NavigationShellTest.kt"
Task: "AndroidBackTest in app/shared/ui/src/androidDeviceTest/kotlin/audio/tembra/ui/navigation/AndroidBackTest.kt"

# Then the independent types and components in parallel:
Task: "Destination.kt", "DestinationKey.kt", "ShellUiState.kt", "NavigationItemSpec.kt",
      "TembraTopBar.kt", "TembraNavigationBar.kt", "TembraNavigationRail.kt",
      "PlaceholderDestination.kt", the six *Screen.kt files
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 (T001–T006): dependencies resolve, glyph pipeline exists
2. Phase 2 (T007–T021): tokens tested on the JVM and the simulator
3. Phase 3 (T022–T048): navigation works on both platforms
4. **STOP and VALIDATE**: quickstart §1 on a phone and a simulator; screenshots captured
5. Demo: the app opens on Home and moves between six destinations

### Incremental Delivery

1. US2 (T049–T053): rail on wide windows, live resize keeps the selection
2. US3 (T054–T068): Library panel beside Home on large windows
3. US4 (T069–T075): hover, keyboard, screen reader
4. US5 (T076–T080): docs and licences
5. Phase 8 (T081–T099): developer view, counter screen removed, full verification, commit

### Parallel Team Strategy

After Phase 2, one person can take US1; once US1's shell exists, US2, US3 and US4 touch different files (`NavigationShellContent`/tests, the scene files, the item components) and can proceed in parallel, with US5 documentation alongside.

---

## Notes

- [P] tasks = different files, no dependencies on unfinished work
- Every implementation task quotes the exact values from data-model.md so nothing is left to discretion
- Verify tests fail before implementing; run the named lane with `--warning-mode all` after each group
- Commit after each phase through the git hook (`auto-commit.sh <event> --message-file <path>`)
- Stop at any checkpoint to validate the story on a device and a simulator
