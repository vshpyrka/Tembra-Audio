package audio.tembra.ui.metronome.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Subdivision
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The compact layout's subdivisions (design iteration D1, contracts/design-system-additions.md): four 44 dp square
 * tiles in one row, 4 dp apart, icon only, each the note figure of its subdivision on the beat [unit]
 * ([SubdivisionGlyphs]). The [selected] tile fills with `primaryContainer` as a selected chip; the others sit on
 * `surfaceContainerHigh` with an `onSurfaceVariant` glyph that hover raises to `onSurface`; keyboard focus draws the
 * `primary` ring. Each tile is a tab with `selected`, named by its value ("1/4", "1/16", "3let") to accessibility and in
 * a tooltip on hover (spec FR-029). Choosing another tile calls [onSelect]; the selected one selects nothing again.
 * The row carries [testTag], each tile `<testTag>.<id>` (`metronome.subdivision.4`) and its glyph
 * `<testTag>.<id>.icon.<glyph>`.
 */
@Composable
internal fun SubdivisionTiles(
    selected: Subdivision,
    unit: BeatUnit,
    onSelect: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.subdivision",
) {
    Row(
        modifier = modifier.testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(TembraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (subdivision in Subdivision.entries) {
            val isSelected = subdivision == selected
            SubdivisionTile(
                icon = SubdivisionGlyphs.of(subdivision, unit),
                label = subdivision.label(unit),
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(subdivision) },
                testTag = "$testTag.${subdivision.id}",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubdivisionTile(
    icon: TembraIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val motion = TembraTheme.motion
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val fill by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "tileFill",
    )
    val tint = when {
        selected -> colors.onPrimaryContainer
        hovered || pressed -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    val tooltip = rememberTooltipState(isPersistent = true)
    LaunchedEffect(hovered, tooltip) {
        if (hovered) tooltip.show() else tooltip.dismiss()
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
        enableUserInput = false,
    ) {
        Box(
            modifier = Modifier
                .testTag(testTag)
                .size(spacing.touchTargetMin)
                .drawBehind {
                    val radius = CornerRadius(TembraShapes.default.toPx())
                    drawRoundRect(color = fill, cornerRadius = radius)
                    if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                }
                .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TembraIcons.vector(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(MetronomeDefaults.SubdivisionGlyphSize).testTag("$testTag.icon.${icon.name}"),
            )
        }
    }
}
