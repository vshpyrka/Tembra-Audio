package audio.tembra.ui.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraTheme

/**
 * The tempo pill's shape (research R15; the pill sat on the ruler until D1 moved it into the toolbar, spec FR-036): a
 * 32 dp `secondaryContainer` pill with [text] in `tabularParam`, raised to `secondary` on hover and press, the
 * `primary` focus ring, a button named [description] inside a 44 dp target. Read-only when not [enabled].
 */
@Composable
internal fun RulerPill(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
    val fill = if (raised) colors.secondary else colors.secondaryContainer
    val content = if (raised) colors.onSecondary else colors.onSecondaryContainer
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides spacing.touchTargetMin) {
        Box(
            modifier = modifier
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    if (!enabled) disabled()
                }
                .minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = spacing.chipHeight)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(color = fill, cornerRadius = radius)
                        if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
                    }
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                // The pill's description already names the value: the drawn text stays out of the merged node.
                Text(text = text, style = TembraTheme.typographyExtras.tabularParam, color = content, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
