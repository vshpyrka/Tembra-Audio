package audio.tembra.ui.timeline.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import audio.tembra.core.project.GainDb
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The gain readout of an expanded header and the settings form (spec FR-023; contracts/timeline-screen.md):
 * "−2.4dB" in `tabularParam` on `surfaceContainerHigh`; a vertical drag sets it ([DRAG_DB_PER_DP] per dp), the
 * arrow keys move it [ARROW_STEP_DB], the page keys [PAGE_STEP_DB], a double tap returns to 0 dB; below
 * [SILENCE_DB] it reads "−∞". Range semantics (`progressBarRangeInfo`, `setProgress`) over [SILENCE_DB]..+6 so
 * screen readers can adjust it. Named "Gain" with its readout as the state.
 */
@Composable
internal fun GainReadout(
    gain: GainDb,
    onGain: (GainDb) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val current by rememberUpdatedState(gain)
    val currentOnGain by rememberUpdatedState(onGain)
    val fill = if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val ring = spacing.focusRingWidth
    val ringColor = colors.primary
    val readout = gain.readout

    fun offer(valueDb: Float) {
        val next = if (valueDb <= SILENCE_DB) GainDb.Silence else GainDb.clamped(valueDb)
        if (next != current) currentOnGain(next)
    }

    fun numeric(value: GainDb): Float = if (value == GainDb.Silence) SILENCE_DB else value.value

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .widthIn(min = MinWidthDp.dp)
            .height(spacing.touchTargetMin)
            .hoverable(source)
            .focusable(interactionSource = source)
            .drawBehind {
                val radius = CornerRadius(TembraShapes.small.toPx())
                drawRoundRect(fill, cornerRadius = radius)
                if (focused) drawFocusRing(ringColor, ring.toPx(), radius)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    offer(numeric(current) - dragAmount / density * DRAG_DB_PER_DP)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { offer(0f) })
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionUp, Key.DirectionRight -> ARROW_STEP_DB
                    Key.DirectionDown, Key.DirectionLeft -> -ARROW_STEP_DB
                    Key.PageUp -> PAGE_STEP_DB
                    Key.PageDown -> -PAGE_STEP_DB
                    else -> 0f
                }
                if (delta == 0f) return@onKeyEvent false
                offer(numeric(current) + delta)
                true
            }
            .semantics {
                contentDescription = GAIN_DESCRIPTION
                stateDescription = readout
                progressBarRangeInfo = ProgressBarRangeInfo(numeric(gain), SILENCE_DB..GainDb.MAX, 0)
                setProgress { value ->
                    offer((value * 2).roundToInt() / 2f)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(readout, style = TembraTheme.typographyExtras.tabularParam, color = colors.onSurface, maxLines = 1)
    }
}

internal const val GAIN_DESCRIPTION = "Gain"

/** Below this the readout reads "−∞" and the strip is silent (spec FR-023). */
internal const val SILENCE_DB = -60f
internal const val ARROW_STEP_DB = 0.5f
internal const val PAGE_STEP_DB = 6f
private const val DRAG_DB_PER_DP = 0.25f
private const val MinWidthDp = 64
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
