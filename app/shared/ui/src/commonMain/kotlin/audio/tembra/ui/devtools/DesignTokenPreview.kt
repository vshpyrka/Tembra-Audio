package audio.tembra.ui.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.theme.DawColors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.TembraTypography

/**
 * Renders every colour role and every text style so screenshots can be compared with theme.json.
 * Swatches carry `testTag("swatch.<role>")` and `contentDescription "<role> #RRGGBB"`; type samples carry `testTag("type.<name>")`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignTokenPreview(modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val roles = schemeRoles(TembraTheme.colors) + dawRoles(TembraTheme.daw)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Text("Colour roles", style = TembraTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            for ((name, color) in roles) {
                Swatch(name = name, color = color)
            }
        }
        Text("Type styles", style = TembraTheme.typography.headlineSmall)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            for ((name, style) in TembraTypography.styles()) {
                Text(
                    text = "Aa 0123 · $name",
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("type.$name"),
                )
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val spacing = TembraTheme.spacing
    val hex = color.toHexLabel()
    Column(
        modifier = Modifier.width(spacing.touchTargetMin * 2),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Column(
            modifier = Modifier
                .size(spacing.touchTargetMin)
                .background(color, TembraTheme.shapes.extraSmall)
                .border(spacing.seam, TembraTheme.daw.surfaceLevel2Border, TembraTheme.shapes.extraSmall)
                .testTag("swatch.$name")
                .semantics { contentDescription = "$name $hex" },
        ) {}
        Text(name, style = TembraTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(hex, style = TembraTheme.typographyExtras.tabularParam, color = TembraTheme.colors.onSurfaceVariant)
    }
}

private fun Color.toHexLabel(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

private fun schemeRoles(s: ColorScheme): List<Pair<String, Color>> = listOf(
    "background" to s.background, "on_background" to s.onBackground, "surface" to s.surface, "surface_dim" to s.surfaceDim,
    "surface_bright" to s.surfaceBright, "surface_container_lowest" to s.surfaceContainerLowest,
    "surface_container_low" to s.surfaceContainerLow, "surface_container" to s.surfaceContainer,
    "surface_container_high" to s.surfaceContainerHigh, "surface_container_highest" to s.surfaceContainerHighest,
    "surface_variant" to s.surfaceVariant, "surface_tint" to s.surfaceTint, "on_surface" to s.onSurface,
    "on_surface_variant" to s.onSurfaceVariant, "inverse_surface" to s.inverseSurface, "inverse_on_surface" to s.inverseOnSurface,
    "inverse_primary" to s.inversePrimary, "outline" to s.outline, "outline_variant" to s.outlineVariant,
    "primary" to s.primary, "on_primary" to s.onPrimary, "primary_container" to s.primaryContainer,
    "on_primary_container" to s.onPrimaryContainer, "primary_fixed" to s.primaryFixed, "primary_fixed_dim" to s.primaryFixedDim,
    "on_primary_fixed" to s.onPrimaryFixed, "on_primary_fixed_variant" to s.onPrimaryFixedVariant,
    "secondary" to s.secondary, "on_secondary" to s.onSecondary, "secondary_container" to s.secondaryContainer,
    "on_secondary_container" to s.onSecondaryContainer, "secondary_fixed" to s.secondaryFixed,
    "secondary_fixed_dim" to s.secondaryFixedDim, "on_secondary_fixed" to s.onSecondaryFixed,
    "on_secondary_fixed_variant" to s.onSecondaryFixedVariant,
    "tertiary" to s.tertiary, "on_tertiary" to s.onTertiary, "tertiary_container" to s.tertiaryContainer,
    "on_tertiary_container" to s.onTertiaryContainer, "tertiary_fixed" to s.tertiaryFixed, "tertiary_fixed_dim" to s.tertiaryFixedDim,
    "on_tertiary_fixed" to s.onTertiaryFixed, "on_tertiary_fixed_variant" to s.onTertiaryFixedVariant,
    "error" to s.error, "on_error" to s.onError, "error_container" to s.errorContainer, "on_error_container" to s.onErrorContainer,
)

private fun dawRoles(d: DawColors): List<Pair<String, Color>> = listOf(
    "canvas_level0" to d.canvasLevel0, "surface_level1" to d.surfaceLevel1, "surface_level2" to d.surfaceLevel2,
    "surface_level2_border" to d.surfaceLevel2Border, "surface_level3" to d.surfaceLevel3, "surface_level4" to d.surfaceLevel4,
    "lavender_primary" to d.lavenderPrimary, "lavender_light" to d.lavenderLight, "playhead" to d.playhead,
    "rose" to d.rose, "rose_light" to d.roseLight, "magenta" to d.magenta,
    "wine_pad" to d.winePad, "wine_pad_mid" to d.winePadMid, "wine_pad_active" to d.winePadActive,
    "grid_overlay_faint" to d.gridOverlayFaint, "grid_overlay" to d.gridOverlay, "clip_glow" to d.clipGlow,
)
