package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The plain tooltip every icon-only control shows on hover and keyboard focus (Constitution IV), promoted from the
 * tuner's and metronome's ad-hoc uses (contracts/design-system-additions.md): [text] appears above [content] while
 * [interactionSource] reports hover or focus — the caller passes the same source to its control, so a test drives
 * the tooltip by emitting interactions, never by moving a pointer. The tooltip text node carries `<testTag>` when
 * one is given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TembraTooltip(
    text: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val tooltip = rememberTooltipState(isPersistent = true)
    val visible = hovered || focused
    LaunchedEffect(visible, tooltip) {
        if (visible) tooltip.show() else tooltip.dismiss()
    }
    // The caller's modifier sits on this component's own root: the tooltip box places its modifier on an inner anchor.
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(text, modifier = Modifier.testTagIfSet(testTag)) } },
            state = tooltip,
            enableUserInput = false,
            content = content,
        )
    }
}
