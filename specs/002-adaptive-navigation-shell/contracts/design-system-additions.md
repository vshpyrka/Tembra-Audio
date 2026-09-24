# Contract: Design-system additions (icons, motion, chrome)

**Module**: `:shared:ui` | **Packages**: `audio.tembra.ui.theme` (tokens, icons) and `audio.tembra.ui.designsystem` (chrome components) | **Consumers**: the navigation shell now; every later screen

## Icons (FR-016)

```kotlin
// TembraIcon.kt
/** Bundled Material Symbols Outlined glyphs (24 dp, weight 400, unfilled). Add a value and a normalised XML file to extend. */
enum class TembraIcon(internal val resource: DrawableResource) {
    Home(Res.drawable.ic_home_24),
    Timeline(Res.drawable.ic_graphic_eq_24),
    Tuner(Res.drawable.ic_music_note_24),
    Metronome(Res.drawable.ic_av_timer_24),
    Projects(Res.drawable.ic_folder_24),
    Profile(Res.drawable.ic_person_24),
}

// TembraIcons.kt
object TembraIcons {
    /** The glyph as an ImageVector, tintable through Icon(tint = ...). */
    @Composable fun vector(icon: TembraIcon): ImageVector
}
```

Files: `shared/ui/src/commonMain/composeResources/drawable/ic_<glyph>_24.xml`, produced by
`app/tools/fetch-material-symbols.sh <glyph> [<glyph> ...]`, which downloads
`symbols/android/<glyph>/materialsymbolsoutlined/<glyph>_24px.xml` from
`google/material-design-icons` at the commit pinned in the script, then normalises the file
(research R6): replaces `@android:color/white` with `#FFFFFFFF`, removes the `android:tint`
attribute, and writes a header comment with the source path, commit and licence. The script is
idempotent and is the only sanctioned way to add a glyph.

Licence inventory: one row "Material Symbols Outlined glyphs (`composeResources/drawable/ic_*_24.xml`)",
version = the pinned commit `27e9ef1dbeedc13d682fece4a58e1eda4cb0961a` (master, 2026-09-18; the
repository has no usable release tag), Apache-2.0, source
`https://github.com/google/material-design-icons`; the licence text is bundled as
`composeResources/files/LICENSE-MaterialSymbols.txt`. Resource file names must match
`[a-z0-9_]+` and sit directly in `drawable/` (the resource generator does not descend into
subfolders); the accessor is `Res.drawable.ic_home_24`.

## Motion (FR-008)

```kotlin
// TembraMotion.kt
@Immutable data class TembraMotion(
    val standardDuration: Int = 300,                    // ms, Material 3 duration.medium2
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),  // Material 3 easing.standard
    val reduceMotion: Boolean = false,                  // platform preference, provided at the root
) {
    /** 0 when reduceMotion, else standardDuration; every animation in the app reads this. */
    val effectiveDuration: Int
}
// LocalTembraMotion.kt: CompositionLocal, default TembraMotion(); TembraTheme provides it.
// TembraTheme.motion: TembraMotion accessor (same pattern as spacing).
// ReduceMotion.kt (commonMain, no expect/actual needed — research R7):
@Composable fun rememberReduceMotion(): Boolean =
    rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
```

Compose already injects a `MotionDurationScale` on both platforms: Android mirrors the
"Remove animations" setting (`ANIMATOR_DURATION_SCALE`), Compose Multiplatform on iOS mirrors
`UIAccessibilityIsReduceMotionEnabled()` (refreshed when the scene becomes active). Ordinary
animations therefore already snap; `TembraMotion.reduceMotion` exists so the Nav3 transition
specs can choose `snap()` / `EnterTransition.None` explicitly and so tests can force both cases
with `runComposeUiTest(effectContext = <MotionDurationScale with scaleFactor 0f>)`.
`TembraTheme` reads `rememberReduceMotion()` itself and exposes the result through
`TembraTheme.motion`; it takes no new parameter.

## Chrome metrics (FR-019)

`TembraSpacing` gains: `topBarHeight = 56.dp`, `bottomBarHeight = 64.dp`, `railWidth = 64.dp`,
`supportingPaneWidth = 320.dp`, `navItemMinWidth = 56.dp`, `railItemWidth = 48.dp`,
`navBarIconSize = 20.dp`, `navRailIconSize = 22.dp`, `navItemGap = 4.dp`, `shortWindowMaxHeight = 480.dp`.
Values and their design sources: data-model.md §6. `ThemeJsonParityTest` is unaffected (these
are layout constants from the screens, not `theme.json` tokens); `ChromeMetricsTest`
(`commonTest`) pins them so an accidental edit fails a test.

## Chrome components (FR-018)

All in `audio.tembra.ui.designsystem`, one file each, stateless, every value from `TembraTheme`.

```kotlin
// NavigationItemSpec.kt — what a bar or rail item needs to know; no dependency on Destination
data class NavigationItemSpec(val id: String, val label: String, val icon: TembraIcon)

// TembraTopBar.kt
@Composable fun TembraTopBar(title: String, modifier: Modifier = Modifier)
// 56 dp tall + status inset; overline "TEMBRA AUDIO" (labelSmall, primary, uppercase, letter-spaced),
// title in headlineSmall on surfaceContainerLowest; nothing else. testTag "chrome.topBar";
// title node testTag "chrome.topBar.title".

// TembraNavigationBar.kt
@Composable fun TembraNavigationBar(
    items: List<NavigationItemSpec>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)
// 64 dp + bottom inset, items spaced evenly; each item: icon (20 dp) over label (labelSmall),
// min 56 dp wide, 44 dp tall touch target, pill shape. Active: primaryContainer fill,
// onPrimaryContainer content, clip glow; the fill animates between items over
// TembraTheme.motion.effectiveDuration. Inactive: onSurfaceVariant; hover -> onSurface.
// testTag "chrome.bottomBar"; items "nav.item.<id>".

// TembraNavigationRail.kt
@Composable fun TembraNavigationRail(
    items: List<NavigationItemSpec>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)
// 64 dp wide, fills height below the top bar; items 48 dp wide, icon (22 dp) over label,
// 16 dp rounded square; active: secondaryContainer fill, onSecondaryContainer content, bold label;
// hover: surfaceContainerHigh fill, onSurface content. testTag "chrome.rail"; items "nav.item.<id>".

// TembraSupportingPane.kt
@Composable fun TembraSupportingPane(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
// 320 dp wide panel on surfaceContainerLow with 12 dp rounded corners, headline "title", 12 dp padding.
// testTag "chrome.supportingPane".

// EmptyState.kt
@Composable fun EmptyState(message: String, modifier: Modifier = Modifier)
// centred bodyMedium in onSurfaceVariant. testTag "chrome.emptyState".
```

**Semantics** (FR-015): every item has `Modifier.semantics { role = Role.Tab; selected = (id == selectedId); contentDescription = label }`
and `testTag("nav.item.<id>")`; the containers carry their `testTag`. Items are focusable,
show Material hover and focus indication through their `InteractionSource`, and satisfy 44 dp
in both dimensions (`sizeIn(minWidth = touchTargetMin, minHeight = touchTargetMin)`).

**Tests**
- `androidHostTest` (JVM, reads repository files): `IconAssetsTest` — every `TembraIcon` has a
  file, no file contains `?attr/` or `@android:`, the header names the pinned commit.
- `commonTest`: `TembraMotionTest` (effective duration 0 under reduce motion, 300 otherwise);
  `uiTest`: `ReduceMotionTest` — `rememberReduceMotion()` is true under an effect context whose
  `MotionDurationScale.scaleFactor` is 0f and false by default;
  `ChromeMetricsTest`; `DeveloperViewRequestTest` (see developer-view.md).
- `uiTest` (`runComposeUiTest`): `TembraNavigationBarTest` and `TembraNavigationRailTest` —
  six items rendered with labels, selected item has `selected = true` and only it, click calls
  `onSelect` with the id, each item ≥ 44 dp both ways, keyboard focus + Enter selects
  (same input-mode guard as feature 001), hover changes the label colour (asserted through a
  `stateDescription`-free approach: the item exposes `testTag` and the test checks the hovered
  interaction by `performMouseInput { enter }` and then `assertIsDisplayed` on the item;
  colour itself is verified by screenshot); `TembraTopBarTest` — overline and title text;
  `TembraSupportingPaneTest` — title and content rendered, width 320 dp
  (`assertWidthIsEqualTo`).
