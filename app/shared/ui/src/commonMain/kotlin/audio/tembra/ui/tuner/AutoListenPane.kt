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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme

/**
 * The Auto Listen card: the meter centred on the design's radial glow, or the denied panel (contracts/tuner-screen.md).
 * Stacked and wide layouts cap the meter's width and scroll when it is taller than the card; phone landscape sizes
 * the compact meter from the card's height so it fits without scrolling (spec FR-023, research R9), as does the region
 * above the fold of a tabletop foldable (feature 005, FR-003). It takes only the
 * fields it renders, so the 10 Hz [reading] is the one input that recomposes it. In the stacked and wide layouts an
 * optional [footer] (the noise gate control, feature 006 FR-016) keeps the foot of the card and the meter is fitted
 * to the height above it ([MeterOverFooter]); it is left out with the denied panel. A [microphoneNotice] (the
 * permission refused, or no microphone at all: feature 011) takes the meter's place.
 */
@Composable
internal fun AutoListenPane(
    reading: MeterReading?,
    listening: Boolean,
    microphoneNotice: MicrophoneNotice?,
    telemetry: PitchTelemetry,
    layout: TunerLayout,
    onRequestMicrophone: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val spacing = TembraTheme.spacing
    TembraGradientCard(glow = TembraTheme.gradients.meterCompact, modifier = modifier, testTag = "tuner.autoListen") {
        when {
            layout == TunerLayout.Landscape || layout == TunerLayout.Tabletop -> FittedMeter(
                reading = reading,
                listening = listening,
                microphoneNotice = microphoneNotice,
                telemetry = telemetry,
                onRequestMicrophone = onRequestMicrophone,
            )
            footer != null && microphoneNotice == null -> MeterOverFooter(
                reading = reading,
                listening = listening,
                telemetry = telemetry,
                wide = layout == TunerLayout.Wide,
                footer = footer,
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                if (microphoneNotice != null) {
                    MicrophoneNoticePanel(notice = microphoneNotice, onRequestMicrophone = onRequestMicrophone, modifier = Modifier.fillMaxWidth())
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
 * The meter over a [footer] (feature 006, spec FR-016, research R11). The footer is measured first and keeps the foot
 * of the card, across the card on a phone and no wider than the wide meter otherwise; the meter gets the height that
 * is left and sits in its middle. The regular meter is used when it fits that height at its full width. Otherwise the
 * meter takes its compact rows, as wide as the height allows; and only when even that would be narrower than its
 * least width does the content scroll: the compact meter at that width, then the footer. The least width is
 * `meterMinWidth` times the font scale, at most the full width: at large fonts a narrower meter would draw "Flat"
 * over "Sharp".
 *
 * The form is chosen while measuring, in a subcomposition, so nothing is measured twice or written back. The reading
 * reaches the meter through State read inside its slot: a new reading recomposes the meter and measures nothing.
 */
@Composable
private fun MeterOverFooter(
    reading: MeterReading?,
    listening: Boolean,
    telemetry: PitchTelemetry,
    wide: Boolean,
    footer: @Composable () -> Unit,
) {
    val spacing = TembraTheme.spacing
    val currentReading by rememberUpdatedState(reading)
    val currentListening by rememberUpdatedState(listening)
    val currentFooter by rememberUpdatedState(footer)
    // A layer of its own: each reading measures and places the card again, which re-records only this layer.
    SubcomposeLayout(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).graphicsLayer()) { constraints ->
        val width = constraints.maxWidth
        // A scroll container hands its own height down as the minimum height: the viewport this content should fill.
        val viewport = constraints.minHeight
        val margin = spacing.md.roundToPx()
        val footerPlaceable = subcompose(MeterSlot.Footer) {
            Box(
                modifier = Modifier.padding(start = spacing.md, end = spacing.md, bottom = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = if (wide) Modifier.widthIn(max = spacing.meterMaxWidthWide) else Modifier) { currentFooter() }
            }
        }.first().measure(Constraints.fixedWidth(width))

        // The height left for the meter, a margin above it and a margin between it and the footer.
        val room = viewport - footerPlaceable.height - margin * 2
        val widest = minOf((if (wide) spacing.meterMaxWidthWide else spacing.meterMaxWidth).roundToPx(), width - margin * 2).coerceAtLeast(0)
        // The meter's rows grow with the font, so the least width that keeps its labels apart grows with it.
        val narrowest = minOf((spacing.meterMinWidth * fontScale.coerceAtLeast(1f)).roundToPx(), widest)
        val regularHeight = MeterLayout.textRowsHeight(density, fontScale) + widest / MeterLayout.ARC_ASPECT
        val compactWidth = minOf(MeterLayout.widthForHeight(room.toFloat(), density, fontScale, MeterSize.Compact).toInt(), widest)
        val scrolls = regularHeight > room && compactWidth < narrowest
        val size = if (regularHeight <= room) MeterSize.Regular else MeterSize.Compact
        val meterWidth = when {
            size == MeterSize.Regular -> widest
            scrolls -> narrowest
            else -> compactWidth
        }
        val meterPlaceable = subcompose(MeterSlot.Meter) {
            TunerMeter(telemetry = telemetry, reading = currentReading, listening = currentListening, testTag = "tuner.meter", size = size)
        }.first().measure(Constraints.fixedWidth(meterWidth))

        // The meter is placed first: the order of placing is the order a screen reader meets the two in (FR-018).
        val meterX = (width - meterPlaceable.width) / 2
        if (scrolls) {
            val footerY = margin + meterPlaceable.height + margin
            layout(width, maxOf(viewport, footerY + footerPlaceable.height)) {
                meterPlaceable.placeRelative(meterX, margin)
                footerPlaceable.placeRelative(0, footerY)
            }
        } else {
            val footerY = viewport - footerPlaceable.height
            layout(width, viewport) {
                meterPlaceable.placeRelative(meterX, (footerY - meterPlaceable.height) / 2)
                footerPlaceable.placeRelative(0, footerY)
            }
        }
    }
}

private enum class MeterSlot { Footer, Meter }

/**
 * The landscape meter: its width is the one whose compact meter is exactly as tall as the card's inner height, kept
 * between `meterMinWidth` and `meterMaxWidthWide` and centred; only when even the narrowest meter (or a microphone
 * notice) is too tall does the card scroll instead.
 */
@Composable
private fun FittedMeter(
    reading: MeterReading?,
    listening: Boolean,
    microphoneNotice: MicrophoneNotice?,
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
        val scrolls = microphoneNotice != null || !fits
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrolls) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            if (microphoneNotice != null) {
                MicrophoneNoticePanel(notice = microphoneNotice, onRequestMicrophone = onRequestMicrophone, modifier = Modifier.fillMaxWidth())
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

/** The panel for a [notice]: the denied state asks again; the no-microphone message has nothing to ask for. */
@Composable
private fun MicrophoneNoticePanel(notice: MicrophoneNotice, onRequestMicrophone: () -> Unit, modifier: Modifier) {
    when (notice) {
        MicrophoneNotice.Denied -> MicrophoneDeniedState(onRequestMicrophone = onRequestMicrophone, modifier = modifier)
        MicrophoneNotice.Unavailable -> MicrophoneUnavailableState(modifier = modifier)
    }
}
