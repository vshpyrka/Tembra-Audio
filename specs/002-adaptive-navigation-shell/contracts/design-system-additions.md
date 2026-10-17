# Contract: Design-system additions (icons, motion, chrome)

**Module**: `:shared:ui` | **Packages**: `audio.tembra.ui.theme` (tokens, icons) and `audio.tembra.ui.designsystem` (chrome components) | **Consumers**: the navigation shell now; every later screen

## Icons (FR-016)

```kotlin
// TembraIcon.kt
/** Bundled Material Symbols Outlined glyphs (24 dp, weight 400, unfilled). Add a value and a normalised XML file to extend. */
enum class TembraIcon(internal val resource: DrawableResource) {
    Home(Res.drawable.ic_home_24),              // removed with ic_home_24.xml on 2026-10-17, when Home left the navigation
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
subfolders); the accessor is `Res.drawable.ic_home_24` (that glyph was deleted on 2026-10-17; the
pattern holds for the others, for example `Res.drawable.ic_graphic_eq_24`).

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

`TembraSpacing` gains: `topBarHeight = 56.dp` (the `TembraTopBar` component's; the shell shows no top bar since 2026-10-16), `bottomBarHeight = 64.dp`, `railWidth = 64.dp`,
`supportingPaneWidth = 320.dp`, `navItemMinWidth = 56.dp`, `railItemWidth = 48.dp`,
`navBarIconSize = 20.dp`, `navRailIconSize = 22.dp`, `navItemGap = 4.dp`.
Values and their design sources: data-model.md §6. `ThemeJsonParityTest` is unaffected (these
are layout constants from the screens, not `theme.json` tokens); `ChromeMetricsTest`
(`commonTest`) pins them so an accidental edit fails a test.

Amended 2026-10-17: `bottomBarHeight`, `railWidth`, `navItemMinWidth`, `railItemWidth`,
`navBarIconSize`, `navRailIconSize` and `navItemGap` are removed, because the bar and the rail
take their sizes from Material 3 Expressive's component tokens (below). `topBarHeight`,
`supportingPaneWidth` and `focusRingWidth` stay, and `ChromeMetricsTest` pins those three.

## Chrome components (FR-018)

All in `audio.tembra.ui.designsystem`, one file each, stateless, every value from `TembraTheme`.

```kotlin
// NavigationItemSpec.kt — what a bar or rail item needs to know; no dependency on Destination
data class NavigationItemSpec(val id: String, val label: String, val icon: TembraIcon)

// TembraTopBar.kt (not composed by the shell since 2026-10-16; kept for a later screen that needs a title bar)
@Composable fun TembraTopBar(title: String, modifier: Modifier = Modifier)
// 56 dp tall + status inset; overline "TEMBRA AUDIO" (labelSmall, primary, uppercase, letter-spaced),
// title in headlineSmall on surfaceContainerLowest; nothing else. testTag "chrome.topBar";
// title node testTag "chrome.topBar.title".

// TembraNavigationBar.kt
@Composable fun TembraNavigationBar(
    items: List<NavigationItemSpec>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)
// 64 dp + bottom inset, items spaced evenly inside the horizontal safe-drawing insets too (2026-10-03: the
// iPhone Duo's closed display keeps a status column at the end edge, and the content already cleared it); each item: icon (20 dp) over label (labelSmall),
// min 56 dp wide, 44 dp tall touch target, pill shape. Active: primaryContainer fill,
// onPrimaryContainer content, clip glow; the fill animates between items over
// TembraTheme.motion.effectiveDuration. Inactive: onSurfaceVariant; hover -> onSurface.
// testTag "chrome.bottomBar"; items "nav.item.<id>".
// Since 2026-10-17: Material 3 Expressive's ShortNavigationBar on surfaceContainerLowest, windowInsets = the
// navigation bars plus the horizontal safe-drawing insets; one ShortNavigationBarItem(label = null) per item, its
// Icon's contentDescription = label, colours ShortNavigationBarItemDefaults.colors(selectedIconColor =
// onPrimaryContainer, selectedIndicatorColor = primaryContainer, unselectedIconColor = onSurfaceVariant), the item's
// interactionSource from NavigationItemSpec. Height is Material's: at least 64 dp (defaultMinSize) plus the inset;
// the pill indicator moves with Material's own animation under the theme's expressive motion scheme. No glow, no
// label, no size token of the app. Same test tags.

// TembraNavigationRail.kt
@Composable fun TembraNavigationRail(
    items: List<NavigationItemSpec>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    itemsAlignment: Alignment.Vertical = Alignment.Top)
// itemsAlignment (2026-10-04): Alignment.Bottom for the iPhone Duo's end-edge rail (LayoutMode.EndRail), whose
// column also holds the status bar and camera at the top; the items scroll when they do not fit either way.
// 64 dp wide, fills the window height (the shell has no top bar since 2026-10-16), its items clear the
// safe-drawing top inset; items 48 dp wide, icon (22 dp) over label,
// 16 dp rounded square; active: secondaryContainer fill, onSecondaryContainer content, bold label;
// hover: surfaceContainerHigh fill, onSurface content. testTag "chrome.rail"; items "nav.item.<id>".
// Since 2026-10-17: Material 3 Expressive's WideNavigationRail, collapsed, on surfaceContainerLowest, windowInsets =
// the safe-drawing top and start plus the bottom navigation inset, arrangement Top or Bottom from itemsAlignment;
// inside, a vertically scrolling Column spaced spacing.xs of WideNavigationRailItem(label = null, railExpanded =
// false), the Icon's contentDescription = label, Material's rail colours (active indicator secondaryContainer with
// an onSecondaryContainer icon, inactive onSurfaceVariant); with label = null the active indicator is a 56 × 56 dp
// circle (measured on devices), where the bar's is a 56 × 32 dp pill. Width is Material's 96 dp, below Material's
// 44 dp top space; the end-edge rail of LayoutMode.EndRail is at least as wide as the end safe-drawing inset. Same
// test tags.
// Both (2026-10-17): each item's icon is NavigationItemIcon(item, interactionSource, position), which shows the label
// as a PlainTooltip in a TooltipBox whose persistent tooltip state follows the item's hover interaction (as the
// metronome's PlayPauseButton and TempoStepper do), placed TooltipAnchorPosition.Above in the bar and .End in the
// rail (Constitution IV). Both provide LocalRippleThemeConfiguration provides InsetFocusRing, so keyboard focus draws
// Material's inset focus ring inside the indicator: outer stroke secondary, inner stroke onSecondary.

// NavigationItemIcon.kt (2026-10-17)
@Composable internal fun NavigationItemIcon(item: NavigationItemSpec, interactionSource: MutableInteractionSource, position: TooltipAnchorPosition)

// InsetFocusRing.kt (2026-10-17), with InsetFocusRing.android.kt / InsetFocusRing.ios.kt
internal expect val InsetFocusRing: RippleThemeConfiguration
// Android: RippleDefaults.InsetFocusRingThemeConfiguration (androidx 1.5.0-beta01);
// iOS: RippleDefaults.InsetFocusRingRippleThemeConfiguration (JetBrains 1.12.0-alpha03). Same preset, two names.

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
Since 2026-10-17 Material's items provide the tab role, the selected state, focus and the touch
size themselves; hover shows Material's state layer plus the label as a tooltip
(`NavigationItemIcon`), keyboard focus shows Material's inset focus ring (`InsetFocusRing`); the
label reaches accessibility services as the icon's `contentDescription`, and
the test tags are unchanged.

**Theme** (2026-10-17): `TembraTheme` wraps its content in `MaterialExpressiveTheme(colorScheme =
TembraColorScheme.dark, motionScheme = MotionScheme.expressive(), typography, shapes)` instead of
`MaterialTheme`, so Material's components, the bar's and rail's indicators among them, animate
with the expressive motion scheme. The destination fade still reads `TembraTheme.motion`.

**Slider** (2026-10-17): `internal expect fun TembraSlider(value, onValueChange, modifier,
valueRange, steps, onValueChangeFinished, interactionSource, thumb, track)` in
`audio.tembra.ui.designsystem`: Material's slider with the design system's own thumb and track.
The Android actual uses the state-based slider of androidx material3 1.5.0-beta01, which removed
the value-based form with a custom thumb and track; the iOS actual uses the value-based form of
the JetBrains port of 1.5.0-alpha22. The tuner's `NoiseGateSlider` and the metronome's
`TempoSlider` call it (research R11).

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
- Amended 2026-10-17: `TembraNavigationBarTest` and `TembraNavigationRailTest` assert five
  icon-only items with no text label and Timeline selected (`rendersFiveIconOnlyItemsAndMarksTheSelectedOne`);
  a click calls `onSelect` with the id; the items fit (the bar at 320 dp) also at font scale 2,
  and the bar's height does not change at font scale 2 (`largeFontsLeaveTheBarHeightUnchanged`);
  every item is a Tab with its label as content description and a 44 dp target; Enter activates
  a focused item; a focused item shows the `secondary`-coloured focus ring
  (`focusedItemShowsAnIndicator`); hover visibly changes the item (`hoverIsVisible`) and shows the
  label as a tooltip (`hoverShowsTheLabelAsATooltip`); and a 240 dp tall rail scrolls to its last
  item (`aShortRailScrollsToItsLastItem`). `ChromeMetricsTest` pins `topBarHeight`,
  `supportingPaneWidth` and `focusRingWidth` only; `IconAssetsTest` no longer lists
  `ic_home_24.xml`.
