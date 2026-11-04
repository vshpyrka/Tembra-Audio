package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

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
    // Above the control, unless that is under what the system draws over the window's top edge, then below (feature 011:
    // the T125 self-check found the toolbar row's tooltips hidden under a desktop window's caption bar). Popups are placed
    // in window coordinates; the app's content starts where the window's decor ends (below the caption, which the decor
    // keeps for itself) and then keeps the safe-drawing inset (the status bar of an edge-to-edge phone) clear. Both are
    // read when the popup is placed: the content's offset from layout, into a plain holder, so nothing recomposes.
    val above = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)
    val below = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below)
    val safeTopPx by rememberUpdatedState(WindowInsets.safeDrawing.getTop(LocalDensity.current))
    val contentTop = remember { ContentTop() }
    val position = remember(above, below) { InsetAwareTooltipPosition(above, below) { contentTop.px + safeTopPx } }
    // The caller's modifier sits on this component's own root: the tooltip box places its modifier on an inner anchor.
    Box(modifier = modifier.onGloballyPositioned { contentTop.px = it.findRootCoordinates().positionInWindow().y.roundToInt() }) {
        TooltipBox(
            positionProvider = position,
            tooltip = { PlainTooltip { Text(text, modifier = Modifier.testTagIfSet(testTag)) } },
            state = tooltip,
            enableUserInput = false,
            content = content,
        )
    }
}

/** Where the app's content begins in its window, in pixels: below a desktop window's caption bar, 0 elsewhere. */
private class ContentTop {
    var px = 0
}

/**
 * A tooltip's place: where [above] puts it, unless that starts above [topLimitPx] (window pixels, read at placement),
 * where the system draws over the window (a desktop window's caption bar, the status bar); then where [below] puts it.
 */
internal class InsetAwareTooltipPosition(
    private val above: PopupPositionProvider,
    private val below: PopupPositionProvider,
    private val topLimitPx: () -> Int,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val placed = above.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
        return if (placed.y < topLimitPx()) below.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize) else placed
    }
}
