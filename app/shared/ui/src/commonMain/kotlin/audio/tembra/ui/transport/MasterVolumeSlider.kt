package audio.tembra.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.TembraSlider
import audio.tembra.ui.designsystem.TembraSliderDefaults
import audio.tembra.ui.designsystem.TembraSliderThumb
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The master volume (spec FR-007): a Material slider over 0–100 % in whole steps on the design system's 6 dp track and
 * 16 dp `primary` handle ([TembraSliderThumb], as the tempo slider and the noise gate). Arrows move it 1 %, the page
 * keys 10 %; it reads "Master volume, 80 percent" and keeps Material's range semantics for `setProgress`. Changes
 * reach [onVolume] while dragging, so the engine's 10 ms ramp follows the finger (SC-006). The wide bar shows it inline
 * ([MasterVolumeInline]); the phone bar inside the speaker button's pop-up ([MasterVolumePopup]).
 */
@Composable
internal fun MasterVolumeSlider(
    percent: Int,
    onVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    testTag: String = TAG,
) {
    val colors = TembraTheme.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val current by rememberUpdatedState(percent)
    val currentOnVolume by rememberUpdatedState(onVolume)

    fun offer(next: Int) {
        val clamped = next.coerceIn(0, 100)
        if (clamped != current) currentOnVolume(clamped)
    }

    TembraSlider(
        value = percent.toFloat(),
        onValueChange = { offer(it.roundToInt()) },
        modifier = modifier
            .testTag(testTag)
            .semantics {
                contentDescription = TransportCopy.masterVolume
                stateDescription = TransportCopy.masterVolumeState(percent)
            }
            .onPreviewKeyEvent { event ->
                val delta = when {
                    event.type != KeyEventType.KeyDown -> 0
                    event.key == Key.PageUp -> PAGE_STEP
                    event.key == Key.PageDown -> -PAGE_STEP
                    else -> 0
                }
                if (delta != 0) offer(current + delta)
                delta != 0
            },
        valueRange = 0f..100f,
        steps = 99,
        interactionSource = source,
        thumb = { TembraSliderThumb(source) },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TembraSliderDefaults.TrackHeight)
                    .background(colors.surfaceContainerHighest, CircleShape),
            )
        },
    )
}

/**
 * The wide bar's master volume (spec FR-004, FR-007): the slider, from [MasterSliderMinWidth] up to
 * [MasterSliderMaxWidth] of the room the bar gives it, and its percentage after it. The bar reserves
 * [rememberMasterInlineMinWidth] for it.
 */
@Composable
internal fun MasterVolumeInline(
    percent: Int,
    onVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(masterPercentGap()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MasterVolumeSlider(
            percent = percent,
            onVolume = onVolume,
            modifier = Modifier
                .widthIn(min = MasterSliderMinWidth, max = MasterSliderMaxWidth)
                .weight(1f, fill = false),
        )
        MasterVolumePercent(percent = percent)
    }
}

/**
 * The master volume as text ("80%", spec FR-007) in `tabularParam` and `onSurfaceVariant`, beside the inline slider and
 * in the pop-up. It is as wide as "100%" at every value, so the slider beside it never shifts, and screen readers skip
 * it: the slider's own state already reads "80 percent".
 */
@Composable
internal fun MasterVolumePercent(percent: Int, modifier: Modifier = Modifier) {
    Text(
        text = TransportCopy.masterVolumePercent(percent),
        modifier = modifier
            .width(rememberMasterPercentWidth())
            .testTag(MASTER_PERCENT_TAG)
            .semantics { hideFromAccessibility() },
        color = TembraTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.End,
        softWrap = false,
        maxLines = 1,
        style = TembraTheme.typographyExtras.tabularParam,
    )
}

/** The least the inline master volume takes in the wide bar: the slider's floor, the gap and the widest percentage. */
@Composable
internal fun rememberMasterInlineMinWidth(): Dp = MasterSliderMinWidth + masterPercentGap() + rememberMasterPercentWidth()

/**
 * Between the slider and its percentage: Material's slider lays its node out about 10 dp past its slot on each side
 * (the room of its inset focus ring), so the gap is wider than that and neither the ring nor the slider's accessibility
 * bounds cover the label.
 */
@Composable
@ReadOnlyComposable
internal fun masterPercentGap(): Dp = TembraTheme.spacing.md

/** "100%" measured in the label's style at the current density and font scale. */
@Composable
private fun rememberMasterPercentWidth(): Dp {
    val style = TembraTheme.typographyExtras.tabularParam
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(style, density, measurer) {
        with(density) { measurer.measure(TransportCopy.masterVolumePercent(100), style).size.width.toDp() }
    }
}

internal const val TAG = "transport.master"
internal const val MASTER_PERCENT_TAG = "transport.master.percent"

/** The page keys move the volume by ten, the arrows by one. */
private const val PAGE_STEP = 10

/** The inline slider's floor and cap in the wide bar. */
private val MasterSliderMinWidth = 72.dp
private val MasterSliderMaxWidth = 160.dp
