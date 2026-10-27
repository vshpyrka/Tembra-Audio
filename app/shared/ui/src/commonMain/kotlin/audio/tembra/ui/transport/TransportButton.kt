package audio.tembra.ui.transport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.designsystem.TembraTooltip
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme

/**
 * An icon-only transport control (research R15; Constitution IV): a 44 dp round target drawing [icon] in
 * `onSurfaceVariant`, raised to `onSurface` on hover and `surfaceContainerHigh` under it, the `primary` focus ring,
 * and [description] as its accessible name and its hover tooltip. [selected] (the metronome toggle) fills the disc
 * in `secondaryContainer`. A disabled button draws at the disabled alpha and swallows clicks.
 */
@Composable
internal fun TransportButton(
    icon: TembraIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val fill = when {
        selected -> colors.secondaryContainer
        enabled && (hovered || pressed) -> colors.surfaceContainerHigh
        else -> null
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = DisabledAlpha)
        selected -> colors.onSecondaryContainer
        hovered || pressed -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
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
                .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    if (!enabled) disabled()
                }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = TembraIcons.vector(icon), contentDescription = null, tint = content, modifier = Modifier.size(IconSize))
        }
    }
}

private val IconSize = androidx.compose.ui.unit.Dp(24f)
private const val DisabledAlpha = 0.38f
