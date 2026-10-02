package audio.tembra.ui.metronome.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.ui.designsystem.drawFocusRing
import audio.tembra.ui.designsystem.testTagIfSet
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The dial's "−" and "+" (research R8): a 44 dp round `surfaceContainerHigh` button (hover `surfaceContainerHighest`)
 * that steps once per click and, held down, repeats after 400 ms every 80 ms until release. The repeat is a
 * user-event coroutine in [rememberCoroutineScope] (`compose-state-and-effects`): release, leaving the stepper's
 * bounds, disabling or leaving the composition cancels it, and a press that repeated does not also click. Hovering
 * shows [contentDescription] as a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TempoStepper(
    icon: TembraIcon,
    enabled: Boolean,
    contentDescription: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String? = null,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val currentOnStep by rememberUpdatedState(onStep)
    val currentEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    val hold = remember { HoldRepeat() }
    DisposableEffect(hold) {
        onDispose { hold.cancel() }
    }
    val tooltip = rememberTooltipState(isPersistent = true)
    LaunchedEffect(hovered, tooltip) {
        if (hovered) tooltip.show() else tooltip.dismiss()
    }
    val fill = if (hovered && enabled) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    val tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = MetronomeDefaults.DISABLED_ALPHA)
    val ringColor = colors.primary
    val ring = spacing.focusRingWidth

    // The caller's modifier sits on this component's own root: the tooltip box places its modifier on an inner anchor,
    // where a parent's alignment would be lost.
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(contentDescription) } },
            state = tooltip,
            enableUserInput = false,
        ) {
            Box(
                modifier = Modifier
                    .testTagIfSet(testTag)
                    .size(spacing.tempoStepper)
                    .clip(CircleShape)
                    .background(fill)
                    .drawWithContent {
                        drawContent()
                        if (focused) drawFocusRing(ringColor, ring.toPx(), CornerRadius(size.minDimension / 2))
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        awaitEachGesture {
                            // The initial pass sees the press before the click handler below does.
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            hold.start(scope) {
                                delay(MetronomeDefaults.HOLD_DELAY_MILLIS)
                                while (currentEnabled) {
                                    hold.repeated = true
                                    currentOnStep()
                                    delay(MetronomeDefaults.HOLD_INTERVAL_MILLIS)
                                }
                            }
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val held = event.changes.any { it.pressed && !it.isOutOfBounds(size, extendedTouchPadding) }
                            } while (held)
                            hold.cancel()
                        }
                    }
                    .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                        if (!hold.consumeRepeat()) currentOnStep()
                    }
                    .semantics { this.contentDescription = contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TembraIcons.vector(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(MetronomeDefaults.StepperIconSize),
                )
            }
        }
    }
}

/** The press-and-hold job of one stepper and whether it has stepped, so the release does not step again. */
private class HoldRepeat {
    private var job: Job? = null
    var repeated: Boolean = false

    fun start(scope: CoroutineScope, block: suspend () -> Unit) {
        cancel()
        repeated = false
        job = scope.launch { block() }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** True once after a press that repeated; the click that ends it is then swallowed. */
    fun consumeRepeat(): Boolean {
        val had = repeated
        repeated = false
        return had
    }
}
