package audio.tembra.ui.timeline.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import audio.tembra.core.recording.MonitorState
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * The input monitoring toggle (feature 010, spec FR-016, FR-017; contracts/timeline-recording-ui.md "MonitorToggle"):
 * a 44 dp target with the headphones glyph, a `Role.Switch` named "Monitor input". [MonitorState.On] fills the disc
 * like the metronome toggle; [MonitorState.SuspendedSpeaker] (remembered on, but the output is the built-in speaker)
 * draws the outline with a slash and reads "needs headphones", and a tap there asks the owner to explain through
 * [onToggle] (the snackbar). The toolbar shows it after the scissors from 600 dp; narrower windows keep it in the
 * overflow menu. The accessible name is also the hover tooltip.
 */
@Composable
internal fun MonitorToggle(
    state: MonitorState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = TimelineTestTags.MONITOR,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val on = state == MonitorState.On
    val fill = when {
        on -> colors.secondaryContainer
        enabled && (hovered || pressed) -> colors.surfaceContainerHigh
        else -> null
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = DisabledAlpha)
        on -> colors.onSecondaryContainer
        hovered || pressed -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    val suspended = state == MonitorState.SuspendedSpeaker
    TembraTooltip(text = TimelineCopy.monitorInput, interactionSource = source, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(spacing.touchTargetMin)
                .drawBehind {
                    val radius = size.minDimension / 2
                    if (fill != null) drawCircle(color = fill, radius = radius)
                    if (suspended) drawCircle(color = content, radius = radius - OutlineInset.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(OutlineWidth.toPx()))
                    if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(radius))
                }
                .drawWithContent {
                    drawContent()
                    // The slash over the glyph: monitoring is remembered but silent on the speaker (spec FR-017).
                    if (suspended) {
                        val inset = size.minDimension / 2 - IconSize.toPx() / 2
                        drawLine(content, Offset(inset, size.height - inset), Offset(size.width - inset, inset), strokeWidth = SlashWidth.toPx(), cap = StrokeCap.Round)
                    }
                }
                .toggleable(value = on, interactionSource = source, indication = null, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
                .semantics {
                    contentDescription = TimelineCopy.monitorInput
                    if (suspended) stateDescription = NEEDS_HEADPHONES
                    if (!enabled) disabled()
                }
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = TembraIcons.vector(TembraIcon.Headphones), contentDescription = null, tint = content, modifier = Modifier.size(IconSize))
        }
    }
}

/** The suspended state as a screen reader hears it (contracts/timeline-recording-ui.md). */
internal const val NEEDS_HEADPHONES = "needs headphones"
private val IconSize = 24.dp
private val OutlineInset = 2.dp
private val OutlineWidth = 1.5.dp
private val SlashWidth = 2.dp
private const val DisabledAlpha = 0.38f
