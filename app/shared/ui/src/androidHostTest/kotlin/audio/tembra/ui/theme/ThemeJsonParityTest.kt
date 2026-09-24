package audio.tembra.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every visual token in Kotlin must equal design/stitch/theme.json (spec FR-004, SC-004). Runs on the JVM host
 * because it reads the repository file; fails in both directions (file key without token, or value drift).
 */
class ThemeJsonParityTest {

    private val theme = Json.parseToJsonElement(
        File(System.getProperty("tembra.repoRoot"), "design/stitch/theme.json").readText(),
    ).jsonObject

    private fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)
    private fun alpha(color: Color): Int = (color.toArgb() ushr 24) and 0xFF

    @Test
    fun everyNamedColorMatchesTheColorScheme() {
        val scheme = TembraColorScheme.dark
        val kotlin: Map<String, Color> = mapOf(
            "background" to scheme.background, "on_background" to scheme.onBackground,
            "surface" to scheme.surface, "surface_dim" to scheme.surfaceDim, "surface_bright" to scheme.surfaceBright,
            "surface_container_lowest" to scheme.surfaceContainerLowest, "surface_container_low" to scheme.surfaceContainerLow,
            "surface_container" to scheme.surfaceContainer, "surface_container_high" to scheme.surfaceContainerHigh,
            "surface_container_highest" to scheme.surfaceContainerHighest, "surface_variant" to scheme.surfaceVariant,
            "surface_tint" to scheme.surfaceTint, "on_surface" to scheme.onSurface, "on_surface_variant" to scheme.onSurfaceVariant,
            "inverse_surface" to scheme.inverseSurface, "inverse_on_surface" to scheme.inverseOnSurface,
            "inverse_primary" to scheme.inversePrimary, "outline" to scheme.outline, "outline_variant" to scheme.outlineVariant,
            "primary" to scheme.primary, "on_primary" to scheme.onPrimary, "primary_container" to scheme.primaryContainer,
            "on_primary_container" to scheme.onPrimaryContainer, "primary_fixed" to scheme.primaryFixed,
            "primary_fixed_dim" to scheme.primaryFixedDim, "on_primary_fixed" to scheme.onPrimaryFixed,
            "on_primary_fixed_variant" to scheme.onPrimaryFixedVariant,
            "secondary" to scheme.secondary, "on_secondary" to scheme.onSecondary, "secondary_container" to scheme.secondaryContainer,
            "on_secondary_container" to scheme.onSecondaryContainer, "secondary_fixed" to scheme.secondaryFixed,
            "secondary_fixed_dim" to scheme.secondaryFixedDim, "on_secondary_fixed" to scheme.onSecondaryFixed,
            "on_secondary_fixed_variant" to scheme.onSecondaryFixedVariant,
            "tertiary" to scheme.tertiary, "on_tertiary" to scheme.onTertiary, "tertiary_container" to scheme.tertiaryContainer,
            "on_tertiary_container" to scheme.onTertiaryContainer, "tertiary_fixed" to scheme.tertiaryFixed,
            "tertiary_fixed_dim" to scheme.tertiaryFixedDim, "on_tertiary_fixed" to scheme.onTertiaryFixed,
            "on_tertiary_fixed_variant" to scheme.onTertiaryFixedVariant,
            "error" to scheme.error, "on_error" to scheme.onError, "error_container" to scheme.errorContainer,
            "on_error_container" to scheme.onErrorContainer,
        )
        val file = theme.getValue("namedColors").jsonObject
        assertEquals(47, file.size, "namedColors count in theme.json")
        for ((key, value) in file) {
            val color = assertNotNull(kotlin[key], "theme.json namedColors.$key has no TembraColorScheme counterpart")
            assertEquals(value.jsonPrimitive.content.uppercase(), hex(color), "namedColors.$key")
        }
        assertEquals(file.keys, kotlin.keys, "TembraColorScheme roles must match theme.json exactly")
    }

    @Test
    fun everyDawSemanticColorMatchesDawColors() {
        val daw = DawColors.Default
        val kotlin: Map<String, Color> = mapOf(
            "canvas_level0" to daw.canvasLevel0, "surface_level1" to daw.surfaceLevel1, "surface_level2" to daw.surfaceLevel2,
            "surface_level2_border" to daw.surfaceLevel2Border, "surface_level3" to daw.surfaceLevel3, "surface_level4" to daw.surfaceLevel4,
            "lavender_primary" to daw.lavenderPrimary, "lavender_light" to daw.lavenderLight, "playhead" to daw.playhead,
            "rose" to daw.rose, "rose_light" to daw.roseLight, "magenta" to daw.magenta,
            "wine_pad" to daw.winePad, "wine_pad_mid" to daw.winePadMid, "wine_pad_active" to daw.winePadActive,
            "grid_overlay_faint" to daw.gridOverlayFaint, "grid_overlay" to daw.gridOverlay,
        )
        val file = theme.getValue("dawSemanticColors").jsonObject.filterKeys { it != "_note" && it != "clip_glow" }
        for ((key, value) in file) {
            val color = assertNotNull(kotlin[key], "theme.json dawSemanticColors.$key has no DawColors counterpart")
            val raw = value.jsonPrimitive.content.uppercase() // #RRGGBB or #RRGGBBAA
            assertEquals(raw.substring(0, 7), hex(color), "dawSemanticColors.$key")
            val expectedAlpha = if (raw.length == 9) raw.substring(7).toInt(16) else 0xFF
            assertEquals(expectedAlpha, alpha(color), "dawSemanticColors.$key alpha")
        }
        assertEquals(file.keys, kotlin.keys, "DawColors must match theme.json exactly")
        // clip_glow = "0 0 12px rgba(167, 139, 250, 0.25)"
        assertEquals("#A78BFA", hex(daw.clipGlow)); assertEquals(64, alpha(daw.clipGlow)); assertEquals(12f, daw.clipGlowBlur.value)
    }

    @Test
    fun everyTypographyEntryMatchesTheTypeScale() {
        val file = theme.getValue("typography").jsonObject
        assertEquals(12, file.size)
        for ((key, entry) in file) {
            val spec = assertNotNull(TembraTypeScale.byThemeName[key], "theme.json typography.$key has no TembraTypeScale counterpart")
            val obj = entry.jsonObject
            val family = when (obj.getValue("fontFamily").jsonPrimitive.content) { "Space Grotesk" -> TembraFont.SpaceGrotesk; "Inter" -> TembraFont.Inter; else -> error(key) }
            assertEquals(family, spec.family, "$key family")
            assertEquals(obj.getValue("fontSize").jsonPrimitive.content.removeSuffix("px").toFloat(), spec.sizeSp, "$key size")
            assertEquals(obj.getValue("lineHeight").jsonPrimitive.content.removeSuffix("px").toFloat(), spec.lineHeightSp, "$key lineHeight")
            assertEquals(obj.getValue("fontWeight").jsonPrimitive.content.toInt(), spec.weight, "$key weight")
            val tracking = obj["letterSpacing"]?.jsonPrimitive?.content?.removeSuffix("em")?.toFloat() ?: 0f
            assertEquals(tracking, spec.letterSpacingEm, "$key letterSpacing")
            assertEquals(obj.containsKey("fontFeatureSettings"), spec.tabularFigures, "$key tnum")
        }
        assertEquals(file.keys, TembraTypeScale.byThemeName.keys)
    }

    @Test
    fun shapesSpacingAndTouchTargetMatch() {
        val rounded = theme.getValue("rounded").jsonObject
        fun rem(key: String) = rounded.getValue(key).jsonPrimitive.content.removeSuffix("rem").toFloat() * 16f
        assertEquals(rem("sm"), TembraShapes.small.value); assertEquals(rem("DEFAULT"), TembraShapes.default.value)
        assertEquals(rem("md"), TembraShapes.medium.value); assertEquals(rem("lg"), TembraShapes.large.value)
        assertEquals(rem("xl"), TembraShapes.extraLarge.value)
        val spacing = theme.getValue("spacing").jsonObject
        fun sp(key: String) = spacing.getValue(key).jsonPrimitive.content.removeSuffix("rem").toFloat() * 16f
        val s = TembraSpacing()
        assertEquals(sp("space-xs"), s.xs.value); assertEquals(sp("space-sm"), s.sm.value); assertEquals(sp("space-md"), s.md.value)
        assertEquals(sp("space-lg"), s.lg.value); assertEquals(sp("space-xl"), s.xl.value)
        assertEquals(sp("gutter"), s.gutter.value); assertEquals(sp("margin"), s.pageMargin.value)
        assertEquals(theme.getValue("touchTargetMin").jsonPrimitive.content.removeSuffix("px").toFloat(), s.touchTargetMin.value)
        assertTrue(s.touchTargetMin.value >= 44f)
    }
}
