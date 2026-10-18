package audio.tembra.ui.transport

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * The transport's metronome toggle (spec FR-036, FR-040): a 44 dp round `Switch` whose disc fills in
 * `secondaryContainer` while [on]; a tap toggles ([onToggle]), a long press opens the metronome settings sheet
 * ([onSettings]). Its name says what a tap does ("Turn metronome on/off"); its state reads "On"/"Off".
 */
@Composable
internal fun MetronomeToggle(
    on: Boolean,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val fill = when {
        on -> colors.secondaryContainer
        enabled && hovered -> colors.surfaceContainerHigh
        else -> null
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = DisabledAlpha)
        on -> colors.onSecondaryContainer
        hovered -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val description = if (on) TransportCopy.metronomeOff else TransportCopy.metronomeOn
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    TembraTooltip(text = description, interactionSource = source, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(spacing.touchTargetMin)
                .drawBehind {
                    val radius = size.minDimension / 2
                    if (fill != null) drawCircle(color = fill, radius = radius)
                    if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(radius))
                }
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onLongClick = onSettings,
                    onClick = onToggle,
                )
                .semantics {
                    contentDescription = description
                    stateDescription = if (on) TransportCopy.stateOn else TransportCopy.stateOff
                }
                .testTag(PerfAnchors.TRANSPORT_METRONOME),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = TembraIcons.vector(TembraIcon.Metronome), contentDescription = null, tint = content, modifier = Modifier.size(IconSize))
        }
    }
}

private val IconSize = androidx.compose.ui.unit.Dp(24f)
private const val DisabledAlpha = 0.38f
