package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme

/**
 * The Auto Listen card: the meter centred on the design's radial glow, or the denied panel (contracts/tuner-screen.md).
 * Stacked and wide layouts cap the meter's width and scroll when it is taller than the card; phone landscape sizes
 * the compact meter from the card's height so it fits without scrolling (spec FR-023, research R9). It takes only the
 * fields it renders, so the 10 Hz [reading] is the one input that recomposes it.
 */
@Composable
internal fun AutoListenPane(
    reading: MeterReading?,
    listening: Boolean,
    showDeniedState: Boolean,
    telemetry: PitchTelemetry,
    layout: TunerLayout,
    onRequestMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    TembraGradientCard(glow = TembraTheme.gradients.meterCompact, modifier = modifier, testTag = "tuner.autoListen") {
        when (layout) {
            TunerLayout.Landscape -> FittedMeter(
                reading = reading,
                listening = listening,
                showDeniedState = showDeniedState,
                telemetry = telemetry,
                onRequestMicrophone = onRequestMicrophone,
            )
            TunerLayout.Stacked, TunerLayout.Wide -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                if (showDeniedState) {
                    MicrophoneDeniedState(onRequestMicrophone = onRequestMicrophone, modifier = Modifier.fillMaxWidth())
                } else {
                    TunerMeter(
                        telemetry = telemetry,
                        reading = reading,
                        listening = listening,
                        testTag = "tuner.meter",
                        modifier = Modifier.widthIn(max = if (layout == TunerLayout.Wide) spacing.meterMaxWidthWide else spacing.meterMaxWidth).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The landscape meter: its width is the one whose compact meter is exactly as tall as the card's inner height, kept
 * between `meterMinWidth` and `meterMaxWidthWide` and centred; only when even the narrowest meter (or the denied
 * panel) is too tall does the card scroll instead.
 */
@Composable
private fun FittedMeter(
    reading: MeterReading?,
    listening: Boolean,
    showDeniedState: Boolean,
    telemetry: PitchTelemetry,
    onRequestMicrophone: () -> Unit,
) {
    val spacing = TembraTheme.spacing
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val inner = maxHeight - spacing.md * 2
        val fit = with(density) { MeterLayout.widthForHeight(inner.toPx(), density.density, density.fontScale, MeterSize.Compact).toDp() }
        val fits = fit >= spacing.meterMinWidth
        val cap = fit.coerceIn(spacing.meterMinWidth, spacing.meterMaxWidthWide)
        val scrolls = showDeniedState || !fits
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrolls) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            if (showDeniedState) {
                MicrophoneDeniedState(onRequestMicrophone = onRequestMicrophone, modifier = Modifier.fillMaxWidth())
            } else {
                TunerMeter(
                    telemetry = telemetry,
                    reading = reading,
                    listening = listening,
                    testTag = "tuner.meter",
                    modifier = Modifier.widthIn(max = cap).fillMaxWidth(),
                    size = MeterSize.Compact,
                )
            }
        }
    }
}
