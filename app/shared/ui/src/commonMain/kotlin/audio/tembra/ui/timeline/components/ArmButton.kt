package audio.tembra.ui.timeline.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy

/**
 * The arm-for-recording toggle of a track header (feature 010, contracts/timeline-recording-ui.md "Components"): a
 * [size] square target (44 dp, 36 dp in the expanded header) drawing a hollow 16 dp circle in `onSurfaceVariant`
 * (`onSurface` on hover, over a `surfaceContainerHigh` disc) while idle and a filled `record` circle while [armed];
 * keyboard focus draws the `primary` ring. A `Switch` named "Arm for recording" with the same text as its hover and
 * focus tooltip. [trackTint] is the track's colour, kept so the contract's tint rules can use it without a signature
 * change; the armed fill is always the `record` token.
 */
@Composable
internal fun ArmButton(
    armed: Boolean,
    trackTint: Color,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TembraTheme.spacing.touchTargetMin,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val recordColor = TembraTheme.daw.record
    val idle = if (hovered) colors.onSurface else colors.onSurfaceVariant
    val disc = colors.surfaceContainerHigh
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    TembraTooltip(text = TimelineCopy.arm, interactionSource = source, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .drawBehind {
                    val radius = CircleRadius.toPx()
                    if (hovered) drawCircle(color = disc, radius = this.size.minDimension / 2)
                    if (armed) {
                        drawCircle(color = recordColor, radius = radius)
                    } else {
                        drawCircle(color = idle, radius = radius, style = Stroke(width = StrokeWidth.toPx()))
                    }
                    if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(this.size.minDimension / 2))
                }
                .toggleable(value = armed, interactionSource = source, indication = null, role = Role.Switch, onValueChange = onToggle)
                .semantics { contentDescription = TimelineCopy.arm }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        )
    }
}

private val CircleRadius = 8.dp
private val StrokeWidth = 2.dp
