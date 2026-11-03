package audio.tembra.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The style row's fully rounded pill (research R10, contracts/design-system-additions.md): drawn 28 dp tall (taller only
 * when large fonts need it) inside a
 * 44 dp target (the project's `touchTargetMin`, not Material's 48 dp), a tab with `selected` semantics. Selected: `secondaryContainer` with
 * `onSecondaryContainer` text; otherwise `surfaceContainerHigh` with `onSurfaceVariant` text that hover raises to
 * `onSurface`; keyboard focus draws a `primary` ring. [onClick] is called on every click; whether reselecting means
 * anything is the caller's decision. With a [testTag], the drawn surface carries `<tag>.surface`.
 */
@Composable
fun TembraPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val motion = TembraTheme.motion
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val fill by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(motion.effectiveDuration, easing = motion.standardEasing),
        label = "pillFill",
    )
    val content = when {
        selected -> colors.onSecondaryContainer
        hovered || pressed -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides spacing.touchTargetMin) {
        Box(
            modifier = modifier
                .testTagIfSet(testTag)
                .focusIdentity(testTag)
                .hoverArea(hovered, TembraShapes.extraLarge)
                .pointerHoverIcon(PointerIcon.Hand).selectable(
                    selected = selected,
                    interactionSource = source,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .testTagIfSet(testTag?.let { "$it.surface" })
                    .heightIn(min = spacing.pillHeight)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(color = fill, cornerRadius = radius)
                        if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                    }
                    .padding(horizontal = spacing.sm + spacing.xs / 2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = TembraTheme.typographyExtras.microLabel,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
