package audio.tembra.ui.transport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import audio.tembra.engine.perf.PerfSection
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.perf.traceFirstMeasure
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.shortcuts.RegisterOverlay

/**
 * The phone bar's master volume (spec FR-007, D1 2026-10-26): below 600 dp of bar width the inline slider gives way to
 * this 44 dp speaker button, a [TransportButton] whose disc fills while its pop-up is open. It reads "Master volume,
 * 80 percent" and stays enabled without a project, as the slider does. A tap opens [MasterVolumePopup] above the bar and
 * a second one closes it, as does the pop-up's own dismiss. [popupClearance] is the distance from the button's top to
 * the bar's plus the gap the card keeps above the bar.
 */
@Composable
internal fun MasterVolumeButton(
    percent: Int,
    onVolume: (Int) -> Unit,
    popupClearance: Dp,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // The pop-up anchors to the layout that calls it: this box, the button's own bounds.
    Box(modifier = modifier) {
        TransportButton(
            icon = TembraIcon.VolumeUp,
            description = TransportCopy.masterVolume,
            onClick = { open = !open },
            selected = open,
            // TransportButton names its target but carries no state: its semantics are restated here with the percentage.
            modifier = Modifier
                .testTag(MASTER_BUTTON_TAG)
                .clearAndSetSemantics {
                    contentDescription = TransportCopy.masterVolume
                    stateDescription = TransportCopy.masterVolumeState(percent)
                    role = Role.Button
                    onClick {
                        open = !open
                        true
                    }
                },
        )
        if (open) {
            MasterVolumePopup(percent = percent, onVolume = onVolume, onDismiss = { open = false }, clearance = popupClearance)
        }
    }
}

/**
 * The speaker button's pop-up (spec FR-007, D1 2026-10-26): a card on `surfaceLevel4` (DESIGN.md's popover level) with
 * the `surface_level2_border` seam and the `medium` radius, holding [MasterVolumeSlider] at 200 dp and its percentage.
 * Changes reach [onVolume] while dragging, as from the inline slider. It anchors to the layout that calls it and sits
 * [clearance] above that anchor, centred on it and kept inside the window; only a window with no room above (a bar at
 * the top) puts it below. A tap outside, Back and a hardware Escape ask [onDismiss] through the platform pop-up; the
 * card answers an Escape that reaches Compose itself, sent to its focused slider. The card is the root of a window of
 * its own, so it carries [anchorRoot] for the journeys' UiAutomator, and its first measure is the `transport.masterPopup`
 * section (contracts/perf-instrumentation.md).
 */
@Composable
internal fun MasterVolumePopup(
    percent: Int,
    onVolume: (Int) -> Unit,
    onDismiss: () -> Unit,
    clearance: Dp = 0.dp,
) {
    val daw = TembraTheme.daw
    val spacing = TembraTheme.spacing
    val density = LocalDensity.current
    val margin = spacing.sm
    val position = remember(density, clearance, margin) {
        with(density) { AboveAnchor(clearance = clearance.roundToPx(), margin = margin.roundToPx()) }
    }
    RegisterOverlay(onDismiss)
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            modifier = Modifier
                .anchorRoot()
                .traceFirstMeasure(PerfSection.TRANSPORT_MASTER_POPUP)
                .testTag(MASTER_POPUP_TAG)
                .semantics { paneTitle = TransportCopy.masterVolume }
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.Escape) return@onPreviewKeyEvent false
                    // Closed on release, as the platform pop-up does, so the release never reaches the window below.
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                },
            shape = RoundedCornerShape(TembraShapes.medium),
            color = daw.surfaceLevel4,
            contentColor = TembraTheme.colors.onSurface,
            border = BorderStroke(spacing.seam, daw.surfaceLevel2Border),
        ) {
            Row(
                // The side padding, as the gap, clears the room Material's slider node takes past its slot.
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(masterPercentGap()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MasterVolumeSlider(percent = percent, onVolume = onVolume, modifier = Modifier.width(PopupSliderWidth))
                MasterVolumePercent(percent = percent)
            }
        }
    }
}

/**
 * [clearance] px above the anchor, centred on it and at least [margin] px inside the window's sides; below the anchor by
 * the same clearance only when the window has no room above it.
 */
private class AboveAnchor(private val clearance: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centred = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val rightmost = windowSize.width - popupContentSize.width - margin
        val x = if (rightmost < margin) (windowSize.width - popupContentSize.width) / 2 else centred.coerceIn(margin, rightmost)
        val above = anchorBounds.top - clearance - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom + clearance
        return IntOffset(x, y)
    }
}

internal const val MASTER_BUTTON_TAG = PerfAnchors.TRANSPORT_MASTER_BUTTON
internal const val MASTER_POPUP_TAG = "transport.master.popup"

/** The pop-up's slider: wider than the phone bar could ever give it inline. */
private val PopupSliderWidth = 200.dp
