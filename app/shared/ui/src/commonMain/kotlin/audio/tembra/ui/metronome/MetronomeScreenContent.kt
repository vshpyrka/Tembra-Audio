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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.designsystem.CardDiscs
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.metronome.components.ClickSoundPickerLayout
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
 * - [MetronomeLayout.Stacked]: a scrolling column with the [MetronomeSettingsPanel] and the dial card, and the tap and
 *   play row pinned below it so the transport stays reachable;
 * - [MetronomeLayout.Wide]: the settings (with the sound picker as tiles in a "Click timbre" card) in a left column a
 *   third of the width, the dial card filling the rest and the tap and play card under it;
 * - [MetronomeLayout.Landscape]: a narrow scrolling settings column (the signatures in two rows of three) beside a
 *   pane whose dial is sized from its height, so the dial, the slider, tap and play fit without scrolling (see
 *   [LandscapePane] for where tap and play go).
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

@Composable
private fun StackedMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            MetronomeSettingsPanel(
                state = state,
                callbacks = callbacks,
                telemetry = telemetry,
                pickerLayout = ClickSoundPickerLayout.Dropdown,
                signatureColumns = TimeSignature.entries.size,
                modifier = Modifier.testTag(SETTINGS_TAG),
            )
            DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.fillMaxWidth()) {
                TempoDialFor(state, callbacks, Modifier.widthIn(max = spacing.tempoDialMaxWidth).fillMaxWidth())
            }
        }
        TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WideMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        MetronomeSettingsPanel(
            state = state,
            callbacks = callbacks,
            telemetry = telemetry,
            pickerLayout = ClickSoundPickerLayout.Tiles,
            signatureColumns = TimeSignature.entries.size,
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
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cap = spacing.tempoDialMaxWidthWide
                    TempoDialFor(state, callbacks, Modifier.sizeIn(maxWidth = cap, maxHeight = cap))
                }
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
            pickerLayout = ClickSoundPickerLayout.Dropdown,
            signatureColumns = LANDSCAPE_SIGNATURE_COLUMNS,
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
 * order) or, when that leaves the dial smaller, the dial card beside a column holding tap over play; a phone held
 * sideways has under 300 dp of height, where the stacked order would shrink the dial until the readout no longer fits
 * between the steppers. Either way the dial, the slider, tap and play fit without scrolling; only when even the larger
 * of the two would put the dial under [MetronomeDefaults.LandscapeDialMin] does the pane scroll, stacked, with the dial
 * at that size.
 */
@Composable
private fun LandscapePane(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    BoxWithConstraints(modifier = modifier) {
        // Around the dial inside its card: the padding on both sides, the gap and the slider (a 44 dp target).
        val cardAroundDial = spacing.md * 2 + spacing.xs + spacing.touchTargetMin
        val cardSides = spacing.md * 2
        val stackedDial = minOf(maxHeight - cardAroundDial - spacing.md - spacing.tapTempoHeight, maxWidth - cardSides)
        val besideDial = minOf(maxHeight - cardAroundDial, maxWidth - MetronomeDefaults.LandscapeTransportWidth - spacing.md - cardSides)
        val cap = spacing.tempoDialMaxWidthWide
        val dialInCard: @Composable ColumnScope.() -> Unit = {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                TempoDialFor(state, callbacks, Modifier.sizeIn(maxWidth = cap, maxHeight = cap))
            }
        }
        when {
            maxOf(stackedDial, besideDial) < MetronomeDefaults.LandscapeDialMin -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.fillMaxWidth()) {
                    TempoDialFor(state, callbacks, Modifier.size(MetronomeDefaults.LandscapeDialMin))
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
                    TapTempoButton(
                        caption = MetronomeCopy.caption(state.tapCaption),
                        onTap = callbacks.onTap,
                        modifier = Modifier.fillMaxWidth(),
                        stateDescription = MetronomeCopy.tapState(state.tapCaption, state.settings.tempo),
                    )
                    PlayPauseButton(running = state.running, onToggle = callbacks.onTogglePlayback)
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
        TapTempoButton(
            caption = MetronomeCopy.caption(state.tapCaption),
            onTap = callbacks.onTap,
            modifier = Modifier.weight(1f),
            stateDescription = MetronomeCopy.tapState(state.tapCaption, state.settings.tempo),
        )
        PlayPauseButton(running = state.running, onToggle = callbacks.onTogglePlayback)
    }
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
private const val LANDSCAPE_SIGNATURE_COLUMNS = 3
