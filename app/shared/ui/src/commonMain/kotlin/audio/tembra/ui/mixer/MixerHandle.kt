package audio.tembra.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.designsystem.hoverArea
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The sheet's grabber row (feature 012, spec FR-018; contracts/mixer-ui.md "MixerHandle"): a 32 dp row with the pill
 * and the "Mixer" label, the surface a finger drags between the two heights. Focusable; a tap, Enter or Space asks
 * for the other height ([onToggleHeight]; nothing where the sheet has one anchor). Its state reads the height. The
 * "Open in a window" button (spec FR-021a) joins the row with User Story 7.
 */
@Composable
internal fun MixerHandle(
    height: MixerHeight,
    onToggleHeight: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    val state = when (height) {
        MixerHeight.Half -> MixerCopy.halfHeight
        MixerHeight.Full -> MixerCopy.fullHeight
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MixerSheetDefaults.HandleHeight)
            .testTag(MixerTestTags.HANDLE)
            .drawBehind { if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(TembraShapes.large.toPx())) }
            .hoverArea(hovered, TembraShapes.large)
            .clickable(interactionSource = source, indication = null, enabled = onToggleHeight != null, role = Role.Button, onClick = { onToggleHeight?.invoke() })
            .semantics { stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(MixerSheetDefaults.GrabberWidth, MixerSheetDefaults.GrabberHeight).background(colors.onSurfaceVariant, CircleShape))
            Text(MixerCopy.mixer, style = TembraTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1)
        }
    }
}
