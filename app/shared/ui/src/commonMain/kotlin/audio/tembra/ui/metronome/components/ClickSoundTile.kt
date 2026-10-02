package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.designsystem.testTagIfSet
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * One sound in the expanded layout's "Click Timbre" grid (contracts/design-system-additions.md, research R10): a 44 dp
 * tile with a 28 dp glyph box and the sound's name, one choice among the sounds (`selected` semantics).
 *
 * Selected: `secondaryContainer` at 20 % with a `secondary` border, glyph and name. Otherwise `surfaceContainerHigh`
 * with a faint `outlineVariant` border that hover raises to `outline`, and hover turns the glyph and name `primary`.
 * [dashed] is the design system's state of the empty Custom tile: a dashed border (hover `primary`) around a `primary`
 * "+". [muted] is the state of a Custom tile whose sound could not be loaded: the name and glyph in `onSurfaceVariant`,
 * still clickable. A tile that is not [enabled] (the Custom tile while a file is imported) ignores clicks and draws its
 * name and glyph at the disabled alpha. [trailing] holds an action beside the name, such as the filled Custom tile's
 * Replace button. Keyboard focus draws a `primary` ring.
 */
@Composable
internal fun ClickSoundTile(
    label: String,
    icon: TembraIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    enabled: Boolean = true,
    muted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val raised = enabled && (hovered || pressed)
    val disabled = colors.onSurface.copy(alpha = MetronomeDefaults.DISABLED_ALPHA)

    val fill = if (selected) colors.secondaryContainer.copy(alpha = MetronomeDefaults.SOUND_TINT_ALPHA) else colors.surfaceContainerHigh
    val border = when {
        selected -> colors.secondary
        dashed && raised -> colors.primary
        dashed -> colors.outlineVariant.copy(alpha = MetronomeDefaults.DASHED_BORDER_ALPHA)
        raised -> colors.outline
        else -> colors.outlineVariant.copy(alpha = MetronomeDefaults.SOUND_TINT_ALPHA)
    }
    val glyphBox = when {
        selected -> colors.secondaryContainer
        dashed -> colors.primaryContainer.copy(alpha = MetronomeDefaults.SOUND_TINT_ALPHA)
        else -> colors.surfaceContainerHighest
    }
    val glyph = when {
        !enabled -> disabled
        selected -> colors.secondary
        dashed || raised -> colors.primary
        else -> colors.onSurfaceVariant
    }
    val text = when {
        !enabled -> disabled
        selected -> colors.secondary
        dashed || raised -> colors.primary
        muted -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val seam = spacing.seam
    val dash = MetronomeDefaults.DashLength
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary

    Row(
        modifier = modifier
            .testTagIfSet(testTag)
            .height(spacing.soundTileHeight)
            .selectable(selected = selected, interactionSource = source, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .drawBehind {
                val radius = CornerRadius(TembraShapes.default.toPx())
                drawRoundRect(color = fill, cornerRadius = radius)
                val width = seam.toPx()
                val dashPx = dash.toPx()
                drawRoundRect(
                    color = border,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(radius.x - width / 2),
                    style = Stroke(width = width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)) else null),
                )
                if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
            }
            .padding(horizontal = MetronomeDefaults.SoundTilePadding),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.soundTileGlyphBox)
                .background(glyphBox, RoundedCornerShape(TembraShapes.small)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TembraIcons.vector(icon),
                contentDescription = null,
                tint = glyph,
                modifier = Modifier.size(MetronomeDefaults.SoundGlyphSize),
            )
        }
        Text(
            text = label,
            style = TembraTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) trailing()
    }
}
