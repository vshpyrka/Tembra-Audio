package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import audio.tembra.ui.theme.TembraIcons

/**
 * An icon-only navigation item's glyph: the item's label is its description, so the item is announced as a named tab
 * (spec FR-014, FR-015), and hovering the item shows the label as a tooltip placed [position] of the glyph
 * (Constitution IV: every icon-only control shows a tooltip on hover), as the metronome's icon-only buttons do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationItemIcon(item: NavigationItemSpec, interactionSource: MutableInteractionSource, position: TooltipAnchorPosition) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val tooltip = rememberTooltipState(isPersistent = true)
    LaunchedEffect(hovered, tooltip) {
        if (hovered) tooltip.show() else tooltip.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(item.label) } },
        state = tooltip,
        enableUserInput = false,
    ) {
        Icon(imageVector = TembraIcons.vector(item.icon), contentDescription = item.label)
    }
}
