package audio.tembra.ui.timeline.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The square M or S toggle of a track header (spec FR-024; the pulled phone screen): [size] square with the `small`
 * radius, filled in the track's [tint] while [checked] (text `onSurface` inverse), `surfaceContainerHigh` otherwise;
 * hover raises the fill, keyboard focus draws the `primary` ring. A `Switch` named [description].
 */
@Composable
internal fun MuteSoloButton(
    letter: String,
    description: String,
    checked: Boolean,
    tint: Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TembraTheme.spacing.touchTargetMin,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
    /** The letter's colour while [checked]; the inverse `scrim` by default, `onSurface` on a dim fill (the mixer's Mute, feature 012). */
    checkedContent: Color? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val fill = when {
        checked -> tint
        hovered -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerHigh
    }
    val content = if (checked) checkedContent ?: colors.scrim else colors.onSurfaceVariant
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .size(size)
            .drawBehind {
                val radius = CornerRadius(TembraShapes.small.toPx())
                drawRoundRect(fill, cornerRadius = radius)
                if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
            }
            .pointerHoverIcon(PointerIcon.Hand).toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = TembraTheme.typography.labelLarge, color = content)
    }
}
