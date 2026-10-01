package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import audio.tembra.ui.designsystem.CardDiscs
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.metronome.components.MetronomeDefaults
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.metronome.components.TapTempoButton
import audio.tembra.ui.metronome.components.TempoDial
import audio.tembra.ui.metronome.components.TempoSlider
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The stateless metronome screen the UI tests drive (contracts/metronome-screen.md, spec FR-028). Three forms:
 * - [MetronomeLayout.Stacked]: the compact [MetronomeSettingsPanel] over the dial card, which takes the remaining
 *   height, and the tap and play row pinned below them; the column scrolls only when the dial would drop under its
 *   minimum (see [StackedMetronome]);
 * - [MetronomeLayout.Wide]: the settings (with the sound picker as tiles in a "Click timbre" card) in a left column a
 *   third of the width, the dial card filling the rest and the tap and play card under it;
 * - [MetronomeLayout.Landscape]: a narrow scrolling settings column (the signatures in two rows of three) beside a
 *   pane whose dial is sized from its height, so the dial, the slider, play and tap fit without scrolling (see
 *   [LandscapePane] for where they go).
 *
 * The settings column is tagged `metronome.settings` in every form; the root keeps the shell's `screen.metronome`,
 * and snackbars show over it. The column comes before the dial in every form, so keyboard focus reads the settings
 * first, then the dial, the slider, tap and play.
 */
@Composable
fun MetronomeScreenContent(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    telemetry: BeatTelemetry,
    layout: MetronomeLayout,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    Box(modifier = modifier.fillMaxSize()) {
        // The tag sits inside the page margin, where the content starts, as the tuner's does.
        val margin = when (layout) {
            MetronomeLayout.Wide -> Modifier.padding(spacing.pageMargin)
            MetronomeLayout.Stacked, MetronomeLayout.Landscape -> Modifier.padding(horizontal = spacing.pageMargin, vertical = spacing.sm)
        }
        val root = Modifier.fillMaxSize().then(margin).testTag("screen.metronome")
        when (layout) {
            MetronomeLayout.Stacked -> StackedMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = root)
            MetronomeLayout.Wide -> WideMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = root)
            MetronomeLayout.Landscape -> LandscapeMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = root)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The compact form (design iteration D1): the settings ([MetronomeSettingsPanel]'s one bar-shape card and the sound
 * pill) over the dial card, which takes the height left above the pinned tap and play row, so on a phone the dial, its
 * slider, tap and play are all in view. The ring grows with that height between [MetronomeDefaults.DialMin] and
 * [MetronomeDefaults.CompactDialMax]; only when it would drop under the minimum (a small phone, large fonts) does the
 * column get taller than its viewport and scroll, the card at its minimum. The sizes are worked out in layout from the
 * viewport and the measured settings: nothing measured is written back to composition.
 */
@Composable
private fun StackedMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SettingsOverDial(
                viewportHeight = maxHeight,
                gap = spacing.md,
                minDialCardHeight = compactDialMin() + dialCardChrome(),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                settings = {
                    MetronomeSettingsPanel(
                        state = state,
                        callbacks = callbacks,
                        telemetry = telemetry,
                        layout = MetronomeLayout.Stacked,
                        modifier = Modifier.fillMaxWidth().testTag(SETTINGS_TAG),
                    )
                },
                dialCard = {
                    DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.fillMaxSize()) {
                        DialInCard(state, callbacks, cap = MetronomeDefaults.CompactDialMax)
                    }
                },
            )
        }
        TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * [settings] at their own height, then [dialCard] [gap] under them filling the rest of [viewportHeight], but never
 * shorter than [minDialCardHeight]: past that the layout is taller than the viewport and its scrolling parent scrolls.
 */
@Composable
private fun SettingsOverDial(
    viewportHeight: Dp,
    gap: Dp,
    minDialCardHeight: Dp,
    modifier: Modifier,
    settings: @Composable () -> Unit,
    dialCard: @Composable () -> Unit,
) {
    Layout(contents = listOf(settings, dialCard), modifier = modifier) { (settingsMeasurables, dialMeasurables), constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val settingsPlaceables = settingsMeasurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        val settingsHeight = settingsPlaceables.sumOf { it.height }
        val cardHeight = maxOf(viewportHeight.roundToPx() - settingsHeight - gapPx, minDialCardHeight.roundToPx())
        val dialPlaceables = dialMeasurables.map { it.measure(Constraints.fixed(width, cardHeight)) }
        layout(width, settingsHeight + gapPx + cardHeight) {
            var y = 0
            settingsPlaceables.forEach { it.place(0, y); y += it.height }
            dialPlaceables.forEach { it.place(0, y + gapPx) }
        }
    }
}

/**
 * The dial card's height around its dial: the padding above and below, the gap and the slider, whose Material track
 * keeps the platform's minimum interactive size (48 dp) around the 44 dp thumb target.
 */
@Composable
private fun dialCardChrome(): Dp {
    val spacing = TembraTheme.spacing
    val slider = max(spacing.touchTargetMin, LocalMinimumInteractiveComponentSize.current)
    return spacing.md * 2 + spacing.xs + slider
}

/**
 * The compact dial's minimum: [MetronomeDefaults.DialMin] grown with the font scale (the readout's tempo name must stay
 * whole between the steppers at the largest fonts, where the column scrolls anyway), within the compact cap.
 */
@Composable
private fun compactDialMin(): Dp {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    return min(MetronomeDefaults.DialMin * fontScale, MetronomeDefaults.CompactDialMax)
}

@Composable
private fun WideMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        MetronomeSettingsPanel(
            state = state,
            callbacks = callbacks,
            telemetry = telemetry,
            layout = MetronomeLayout.Wide,
            modifier = Modifier
                .widthFraction(MetronomeDefaults.WIDE_SETTINGS_FRACTION, min = spacing.wideSettingsMinWidth)
                .fillMaxHeight()
                .testTag(SETTINGS_TAG)
                .verticalScroll(rememberScrollState()),
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            DialCard(
                state = state,
                callbacks = callbacks,
                discs = CardDiscs.TopEndBottomStart,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                sliderMaxWidth = spacing.tempoDialMaxWidthWide,
            ) {
                DialInCard(state, callbacks, cap = spacing.tempoDialMaxWidthWide)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TembraTheme.colors.surfaceContainerLow, RoundedCornerShape(TembraShapes.medium))
                    .padding(spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                TransportRow(state = state, callbacks = callbacks, modifier = Modifier.widthIn(max = MetronomeDefaults.TransportMaxWidth).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LandscapeMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        MetronomeSettingsPanel(
            state = state,
            callbacks = callbacks,
            telemetry = telemetry,
            layout = MetronomeLayout.Landscape,
            modifier = Modifier
                .widthFraction(
                    fraction = MetronomeDefaults.LANDSCAPE_SETTINGS_FRACTION,
                    min = spacing.landscapeSettingsMinWidth * fontScale,
                    max = spacing.landscapeSettingsMaxWidth * fontScale,
                    maxFraction = MetronomeDefaults.LANDSCAPE_SETTINGS_MAX_FRACTION,
                )
                .fillMaxHeight()
                .testTag(SETTINGS_TAG)
                .verticalScroll(rememberScrollState()),
        )
        LandscapePane(state = state, callbacks = callbacks, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * The landscape pane. The dial is sized from the pane: either the dial card over the tap and play row (the design's
 * order) or, when that leaves the dial smaller, the dial card beside a column holding play over tap; a phone held
 * sideways has under 300 dp of height, where the stacked order would shrink the dial until the readout no longer fits
 * between the steppers. Either way the dial, the slider, tap and play fit without scrolling; only when even the larger
 * of the two would put the dial under [MetronomeDefaults.DialMin] does the pane scroll, stacked, with the dial
 * at that size.
 */
@Composable
private fun LandscapePane(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    BoxWithConstraints(modifier = modifier) {
        val cardAroundDial = dialCardChrome()
        val cardSides = spacing.md * 2
        val stackedDial = minOf(maxHeight - cardAroundDial - spacing.md - spacing.tapTempoHeight, maxWidth - cardSides)
        val besideDial = minOf(maxHeight - cardAroundDial, maxWidth - MetronomeDefaults.LandscapeTransportWidth - spacing.md - cardSides)
        val dialInCard: @Composable ColumnScope.() -> Unit = { DialInCard(state, callbacks, cap = spacing.tempoDialMaxWidthWide) }
        when {
            maxOf(stackedDial, besideDial) < MetronomeDefaults.DialMin -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.fillMaxWidth()) {
                    TempoDialFor(state, callbacks, Modifier.size(MetronomeDefaults.DialMin))
                }
                TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
            }
            stackedDial >= besideDial -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.weight(1f).fillMaxWidth(), dial = dialInCard)
                TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
            }
            else -> Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.weight(1f).fillMaxHeight(), dial = dialInCard)
                Column(
                    modifier = Modifier.width(MetronomeDefaults.LandscapeTransportWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Play on top, where the thumb rests on a phone held sideways (revised 2026-10-01).
                    PlayPauseButton(running = state.running, onToggle = callbacks.onTogglePlayback)
                    TapTempoFor(state, callbacks, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The dial card: the `tempoDialCard` glow with the `tempoDialOverlay` tint and the corner [discs], holding [dial] and
 * the slider (capped at [sliderMaxWidth] when given) centred under it.
 */
@Composable
private fun DialCard(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    discs: CardDiscs,
    modifier: Modifier,
    sliderMaxWidth: Dp = Dp.Unspecified,
    dial: @Composable ColumnScope.() -> Unit,
) {
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    TembraGradientCard(
        glow = gradients.tempoDialCard,
        modifier = modifier,
        discs = discs,
        overlay = gradients.tempoDialOverlay,
        testTag = "metronome.dialCard",
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            dial()
            TempoSlider(tempo = state.settings.tempo, onTempo = callbacks.onTempo, modifier = Modifier.widthIn(max = sliderMaxWidth).fillMaxWidth())
        }
    }
}

/** The dial in the card's remaining height, centred, as large as the card allows up to [cap]. */
@Composable
private fun ColumnScope.DialInCard(state: MetronomeUiState, callbacks: MetronomeCallbacks, cap: Dp) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        TempoDialFor(state, callbacks, Modifier.sizeIn(maxWidth = cap, maxHeight = cap))
    }
}

@Composable
private fun TempoDialFor(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    TempoDial(
        tempo = state.settings.tempo,
        marking = state.marking,
        canStepDown = state.canStepDown,
        canStepUp = state.canStepUp,
        onTempo = callbacks.onTempo,
        onStep = callbacks.onStep,
        modifier = modifier,
    )
}

/** Tap tempo (taking the rest of the width) and play, side by side. */
@Composable
private fun TransportRow(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TembraTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTempoFor(state, callbacks, Modifier.weight(1f))
        PlayPauseButton(running = state.running, onToggle = callbacks.onTogglePlayback)
    }
}

/** The tap tempo button bound to the screen state: its caption and the tempo it announces (the row and the landscape column). */
@Composable
private fun TapTempoFor(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    TapTempoButton(
        caption = MetronomeCopy.caption(state.tapCaption),
        onTap = callbacks.onTap,
        modifier = modifier,
        stateDescription = MetronomeCopy.tapState(state.tapCaption, state.settings.tempo),
    )
}

/**
 * [fraction] of the available width, at least [min] and at most [max], and never more than [maxFraction] of the width
 * nor more than the width itself.
 */
private fun Modifier.widthFraction(
    fraction: Float,
    min: Dp,
    max: Dp = Dp.Infinity,
    maxFraction: Float = 1f,
): Modifier = layout { measurable, constraints ->
    val lower = min.roundToPx()
    val upper = if (max == Dp.Infinity) Int.MAX_VALUE else max.roundToPx()
    val width = if (constraints.hasBoundedWidth) {
        val available = constraints.maxWidth
        (available * fraction).roundToInt()
            .coerceIn(lower, upper)
            .coerceAtMost((available * maxFraction).roundToInt())
            .coerceAtMost(available)
    } else {
        lower
    }
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private const val SETTINGS_TAG = "metronome.settings"
