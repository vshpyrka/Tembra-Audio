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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.unit.lerp
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
 * - [MetronomeLayout.Landscape]: a settings column (240–344 dp, as wide as a 180 dp dial in the pane allows; the compact
 *   bar-shape card as in the Stacked form; it scrolls only when the card does not fit) beside a
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

/**
 * The phone-landscape form (T150, T152): a 2:3 split. The compact settings card in a column 2⁄5 of the width (at
 * least 240 dp, scaled by the font scale, but never so wide that the pane loses room for a 160 dp ring; it scrolls only
 * when the card is taller than the screen), and the dial pane,
 * the remaining 3⁄5: the dial card across the whole pane with its ring sized from the height (see [landscapeBlock]),
 * and the tap and play row under it across the pane too.
 */
@Composable
private fun LandscapeMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val chrome = landscapeDialChrome()
    BoxWithConstraints(modifier = modifier) {
        // The column never takes the room a 160 dp ring needs in the pane (large fonts on a narrow phone).
        val columnMax = maxWidth - spacing.md - (MetronomeDefaults.DialMin + spacing.sm * 2)
        val columnMin = MetronomeDefaults.LandscapeSettingsMinWidth * fontScale
        val column = min(max(maxWidth * MetronomeDefaults.LANDSCAPE_SETTINGS_FRACTION, columnMin), columnMax)
        val paneWidth = maxWidth - column - spacing.md
        val block = landscapeBlock(height = maxHeight, chrome = chrome, ringWidth = paneWidth - spacing.sm * 2)
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            MetronomeSettingsPanel(
                state = state,
                callbacks = callbacks,
                telemetry = telemetry,
                layout = MetronomeLayout.Landscape,
                modifier = Modifier
                    .widthFraction(MetronomeDefaults.LANDSCAPE_SETTINGS_FRACTION, min = columnMin, max = columnMax)
                    .fillMaxHeight()
                    .testTag(SETTINGS_TAG)
                    .verticalScroll(rememberScrollState()),
            )
            LandscapeDialPane(state = state, callbacks = callbacks, block = block, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** The landscape dial block's sizes: the ring, the tap and play row's height, and whether the block must scroll. */
private data class LandscapeBlock(val ring: Dp, val row: Dp, val scrolls: Boolean)

/**
 * The dial pane's sizes for a pane [height] (T152): the tap and play row is 48 dp up to a 300 dp pane and grows
 * linearly to 64 dp at 420 dp; the ring takes the rest of the height ([chrome] being the card's padding, the slider and
 * the gaps), within 160–300 dp and never wider than [ringWidth]. Only when even the 48 dp row leaves the ring under
 * 160 dp does the pane scroll, with the ring at 160 dp.
 */
private fun landscapeBlock(height: Dp, chrome: Dp, ringWidth: Dp): LandscapeBlock {
    val low = MetronomeDefaults.LandscapeRowScaleFrom
    val high = MetronomeDefaults.LandscapeRowScaleTo
    val share = ((height - low) / (high - low)).coerceIn(0f, 1f)
    val row = lerp(MetronomeDefaults.LandscapeRowMin, MetronomeDefaults.LandscapeRowMax, share)
    val ring = height - chrome - row
    val ringAtMinRow = height - chrome - MetronomeDefaults.LandscapeRowMin
    return when {
        // Never under DialMin: the column cap keeps the pane wide enough for it.
        ring >= MetronomeDefaults.DialMin -> LandscapeBlock(
            ring = min(min(ring, MetronomeDefaults.CompactDialMax), ringWidth).coerceAtLeast(MetronomeDefaults.DialMin),
            row = row,
            scrolls = false,
        )
        ringAtMinRow >= MetronomeDefaults.DialMin -> LandscapeBlock(
            ring = min(ringAtMinRow, ringWidth).coerceAtLeast(MetronomeDefaults.DialMin),
            row = MetronomeDefaults.LandscapeRowMin,
            scrolls = false,
        )
        else -> LandscapeBlock(ring = MetronomeDefaults.DialMin, row = MetronomeDefaults.LandscapeRowMin, scrolls = true)
    }
}

/** The landscape dial card's height around its ring (padding `sm`, the gap, the 48 dp slider) plus the gap to the row. */
@Composable
private fun landscapeDialChrome(): Dp {
    val spacing = TembraTheme.spacing
    val slider = max(spacing.touchTargetMin, LocalMinimumInteractiveComponentSize.current)
    return spacing.sm * 2 + spacing.xs + slider + spacing.sm
}

/**
 * The landscape dial pane (T152, contracts/metronome-screen.md): the dial card across the whole pane with the ring
 * centred in it and, under it, tap tempo (taking the rest of the row) and play in a row of [LandscapeBlock.row] across
 * the pane. Tab reads the dial, the slider, tap, then play.
 */
@Composable
private fun LandscapeDialPane(state: MetronomeUiState, callbacks: MetronomeCallbacks, block: LandscapeBlock, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    val scroll = if (block.scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier.then(scroll),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        DialCard(
            state = state,
            callbacks = callbacks,
            discs = CardDiscs.None,
            modifier = if (block.scrolls) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth(),
            padding = spacing.sm,
        ) {
            val room = if (block.scrolls) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
            Box(modifier = room, contentAlignment = Alignment.Center) {
                TempoDialFor(state, callbacks, Modifier.size(block.ring))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(block.row),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapTempoFor(state, callbacks, Modifier.weight(1f).fillMaxHeight(), minHeight = block.row)
            PlayPauseButton(running = state.running, onToggle = callbacks.onTogglePlayback, diameter = block.row)
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
    padding: Dp = TembraTheme.spacing.md,
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
            modifier = Modifier.fillMaxSize().padding(padding),
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
private fun TapTempoFor(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier, minHeight: Dp = TembraTheme.spacing.tapTempoHeight) {
    TapTempoButton(
        caption = MetronomeCopy.caption(state.tapCaption),
        onTap = callbacks.onTap,
        modifier = modifier,
        minHeight = minHeight,
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
        // Lower bound first, then the upper one wins: a cap under the floor (large fonts, a narrow window) still holds.
        (available * fraction).roundToInt()
            .coerceAtLeast(lower)
            .coerceAtMost(upper)
            .coerceAtMost((available * maxFraction).roundToInt())
            .coerceAtMost(available)
    } else {
        lower
    }
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private const val SETTINGS_TAG = "metronome.settings"
