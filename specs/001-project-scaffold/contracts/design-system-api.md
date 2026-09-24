# Contract: Design system API

**Module**: `:shared:ui`, package `audio.tembra.ui.theme` | **Consumers**: every screen; the placeholder screen in this feature

```kotlin
// TembraTheme.kt
@Composable
fun TembraTheme(content: @Composable () -> Unit)
// Applies MaterialTheme(colorScheme = TembraColorScheme.dark, typography = TembraTypography.material,
// shapes = TembraShapes.material) and provides LocalDawColors, LocalTembraSpacing,
// LocalTembraTypographyExtras. Always dark (Constitution constraint; design defines dark only).

object TembraTheme {                       // accessors, all @Composable @ReadOnlyComposable
    val colors: ColorScheme                // MaterialTheme.colorScheme
    val daw: DawColors                     // LocalDawColors.current
    val typography: Typography             // MaterialTheme.typography
    val typographyExtras: TembraTypographyExtras  // headlineLgMobile, tabularTimecode, tabularParam
    val shapes: Shapes                     // MaterialTheme.shapes
    val spacing: TembraSpacing             // LocalTembraSpacing.current
}

// One file each (Constitution VII): TembraColorScheme.kt, DawColors.kt, TembraTypography.kt,
// TembraTypographyExtras.kt, TembraShapes.kt, TembraSpacing.kt, TembraFonts.kt (font families
// from bundled resources), LocalDawColors.kt, LocalTembraSpacing.kt, LocalTembraTypographyExtras.kt.

// DesignTokenPreview.kt — the palette/type preview used by the placeholder screen.
@Composable
fun DesignTokenPreview(modifier: Modifier = Modifier)
// Renders every ColorScheme role and every DawColors value as a labelled swatch
// (testTag "swatch.<roleName>", contentDescription "<roleName> <hex>") and one sample line per
// text style (testTag "type.<styleName>"). Wraps into a grid on wide windows, a single column on
// compact windows.
```

**Rules**
- Values in these files are the only place hex colours, sp/dp sizes and font families appear in
  `:shared:ui`.
- `tabularTimecode` and `tabularParam` set `FontFeatureSettings("tnum")`.
- Tooltips use Material 3 `TooltipBox` with plain tooltips; hover state comes from Material
  interaction sources; nothing custom in this feature.

**Tests**
- `androidHostTest` (JVM): `ThemeJsonParityTest` reads `design/stitch/theme.json` (path resolved
  from the Gradle project directory via a system property set by the build) and asserts every
  `namedColors` and `dawSemanticColors` entry equals the Kotlin token, and every typography
  entry's family/size/lineHeight/weight equals `TembraTypography`. Fails on drift in either
  direction (a token missing from the file is also a failure).
- `commonTest` (`runComposeUiTest`, runs on iOS simulator and Android device):
  `DesignTokenPreviewTest` asserts a swatch exists for every role and that `swatch.primary`
  has the expected content description.
