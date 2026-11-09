package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import audio.tembra.ui.designsystem.CardDiscs
import audio.tembra.ui.designsystem.FocusMemory
import audio.tembra.ui.designsystem.FoldSplit
import audio.tembra.ui.designsystem.FoldSplitRow
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.designsystem.focusRestoringRoot
import audio.tembra.ui.designsystem.verticalScrollWithScrollbar
import audio.tembra.ui.metronome.components.AccentCells
import audio.tembra.ui.metronome.components.AccentCellsForm
import audio.tembra.ui.metronome.components.ClickSoundPicker
import audio.tembra.ui.metronome.components.ClickSoundPickerLayout
import audio.tembra.ui.metronome.components.MetronomeDefaults
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.metronome.components.StyleRow
import audio.tembra.ui.metronome.components.SubdivisionTiles
import audio.tembra.ui.metronome.components.TapTempoButton
import audio.tembra.ui.metronome.components.TempoDial
import audio.tembra.ui.metronome.components.TempoRow
import audio.tembra.ui.metronome.components.TempoSlider
import audio.tembra.ui.metronome.components.TimeSignatureMenu
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.posture.VerticalFold
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt
import audio.tembra.ui.shortcuts.RegisterOverlay

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
 * - [MetronomeLayout.Tabletop] (feature 005): the screen split at the tabletop [fold] (see [TabletopMetronome]); a null
 *   [fold] splits it in half, as the UI tests do.
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
    fold: TabletopFold? = null,
    verticalFold: VerticalFold? = null,
) {
    val spacing = TembraTheme.spacing
    // Keyboard focus starts on the content root once the settings are in (feature 011, spec FR-024).
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(state.loaded) {
        if (state.loaded) rootFocus.requestFocus()
    }
    // Above the layout branches: focus returns to the same control in the new form (feature 011, FR-007).
    val focusMemory = remember { FocusMemory() }
    Box(modifier = modifier.fillMaxSize()) {
        // The tag sits inside the page margin, where the content starts, as the tuner's does.
        val margin = when (layout) {
            MetronomeLayout.Wide -> Modifier.padding(spacing.pageMargin)
            MetronomeLayout.Stacked, MetronomeLayout.Landscape, MetronomeLayout.Tabletop, MetronomeLayout.Pocket -> Modifier.padding(horizontal = spacing.pageMargin, vertical = spacing.sm)
        }
        // A very wide window keeps the expanded width, centred (feature 011, spec FR-008).
        val root = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = spacing.screenMaxWidth).fillMaxWidth().then(margin).testTag("screen.metronome").focusRestoringRoot(focusMemory).focusRequester(rootFocus).focusable()
            // Space, ↑/↓ (Shift for 10) and T (feature 011, spec FR-018), after the focused control.
            .metronomeKeyHandling(callbacks)
        when (layout) {
            MetronomeLayout.Stacked -> StackedMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = root)
            MetronomeLayout.Wide -> WideMetronome(state = state, callbacks = callbacks, telemetry = telemetry, verticalFold = verticalFold, modifier = root)
            MetronomeLayout.Landscape -> LandscapeMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = root)
            MetronomeLayout.Tabletop -> TabletopMetronome(state = state, callbacks = callbacks, telemetry = telemetry, fold = fold, modifier = root)
            MetronomeLayout.Pocket -> PocketMetronome(state = state, callbacks = callbacks, telemetry = telemetry, modifier = root)
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
        // The column's weighted height fixes the scroll container, which passes it on to SettingsOverDial as the
        // minimum height: the viewport arrives without a BoxWithConstraints, so this composes in the composition and
        // can be prepared ahead (PreparedLayout), not in a measure pass.
        SettingsOverDial(
            gap = spacing.md,
            minDialCardHeight = compactDialMin() + dialCardChrome(),
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScrollWithScrollbar(rememberScrollState()),
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
                DialCard(state = state, callbacks = callbacks, discs = CardDiscs.TopStartBottomEnd, modifier = Modifier.fillMaxSize()) {
                    DialInCard(state, callbacks, cap = MetronomeDefaults.CompactDialMax)
                }
            },
        )
        TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The Pocket form: the accent cells as bar boxes without labels (the phone-landscape form) in a card on top, the dial
 * card filling the height under them with a settings button in its top end corner, and the tap and play row pinned at
 * the foot. The button opens the other settings (signature, subdivision, style and sound) in a modal bottom sheet;
 * whether it is open is this form's own state. Born as the iPhone Duo experiment; since 2026-10-16 also the form of any
 * screen too short for [MetronomeLayout.Stacked] (a short desktop window, a split-screen half), so it steps down with
 * the height ([pocketTier]): when the ring would be under its 160 dp the [TempoRow] stands in for it with the settings
 * button at its end, then the slider goes, then the cells move into the sheet. Tap, play and the tempo stay in view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PocketMetronome(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val heights = pocketHeights()
    BoxWithConstraints(modifier = modifier) {
        // A constraint read while measuring (BoxWithConstraints), so the sheet below knows where the cells are.
        val tier = pocketTier(height = maxHeight, cellsCard = heights.cellsCard(maxWidth, state.settings.signature.beats), heights = heights)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (tier.cells) {
                Box(modifier = Modifier.fillMaxWidth().tabletopCard(TembraTheme.colors.surfaceContainerLow).padding(spacing.sm)) {
                    AccentCells(
                        pattern = state.settings.pattern,
                        running = state.running,
                        currentBeat = state.currentBeat,
                        telemetry = telemetry,
                        onCycle = callbacks.onCycleBeat,
                        modifier = Modifier.fillMaxWidth(),
                        gap = spacing.xs,
                        form = AccentCellsForm.BarOnly,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (tier.ring) {
                    DialCard(state = state, callbacks = callbacks, discs = CardDiscs.None, modifier = Modifier.fillMaxSize()) {
                        DialInCard(state, callbacks, cap = MetronomeDefaults.CompactDialMax)
                    }
                    SettingsButton(onClick = { settingsOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(spacing.xs))
                } else {
                    TempoCard(state = state, callbacks = callbacks, slider = tier.slider, onSettings = { settingsOpen = true }, modifier = Modifier.fillMaxSize())
                }
            }
            TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
        }
        if (settingsOpen) {
            RegisterOverlay { settingsOpen = false }
            ModalBottomSheet(
                onDismissRequest = { settingsOpen = false },
                containerColor = TembraTheme.colors.surfaceContainerLowest,
            ) {
                MetronomeSettingsPanel(
                    state = state,
                    callbacks = callbacks,
                    telemetry = telemetry,
                    layout = MetronomeLayout.Pocket,
                    pocketCells = !tier.cells,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScrollWithScrollbar(rememberScrollState())
                        .padding(start = spacing.pageMargin, end = spacing.pageMargin, bottom = spacing.lg)
                        .testTag(SETTINGS_TAG),
                )
            }
        }
    }
}

/**
 * The Pocket form's dial card when the ring does not fit: the [TempoRow] (with the settings button at its end) and,
 * when [slider], the tempo slider under it, centred in the card's height.
 */
@Composable
private fun TempoCard(state: MetronomeUiState, callbacks: MetronomeCallbacks, slider: Boolean, onSettings: () -> Unit, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    TembraGradientCard(
        glow = gradients.meterCompact,
        modifier = modifier,
        discs = CardDiscs.TopStartBottomEnd,
        overlay = null,
        testTag = "metronome.dialCard",
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterVertically),
        ) {
            TempoRow(
                tempo = state.settings.tempo,
                marking = state.marking,
                canStepDown = state.canStepDown,
                canStepUp = state.canStepUp,
                onTempo = callbacks.onTempo,
                onStep = callbacks.onStep,
                modifier = Modifier.fillMaxWidth(),
                trailing = { SettingsButton(onClick = onSettings, modifier = Modifier) },
            )
            if (slider) TempoSlider(tempo = state.settings.tempo, onTempo = callbacks.onTempo, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** What a Pocket screen of a given height shows: the cells over the card, the ring (else the tempo row), the slider. */
private data class PocketTier(val cells: Boolean, val ring: Boolean, val slider: Boolean)

/** The heights the Pocket tiers are weighed with, from the same tokens the parts are drawn with. */
private class PocketHeights(
    val transport: Dp,
    val gap: Dp,
    val ringCard: Dp,
    val tempoCard: Dp,
    private val cell: Dp,
    private val cellGap: Dp,
    private val cellsPadding: Dp,
) {
    /** The bar-box cells card for [beats] cells across [width]: 44 dp cells, wrapping into as many rows as needed. */
    fun cellsCard(width: Dp, beats: Int): Dp {
        val inner = width - cellsPadding * 2
        val perRow = ((inner + cellGap) / (cell + cellGap)).toInt().coerceAtLeast(1)
        val rows = (beats + perRow - 1) / perRow
        return cellsPadding * 2 + cell * rows + cellGap * (rows - 1).coerceAtLeast(0)
    }
}

@Composable
private fun pocketHeights(): PocketHeights {
    val spacing = TembraTheme.spacing
    val slider = max(spacing.touchTargetMin, LocalMinimumInteractiveComponentSize.current)
    // The tempo row is one number line tall (the readout's 64 sp line, which grows with the font), or the steppers.
    val tempoRow = max(spacing.tempoStepper, with(LocalDensity.current) { TembraTheme.typographyExtras.tempoNumber.lineHeight.toDp() })
    return PocketHeights(
        transport = max(spacing.tapTempoHeight, spacing.playButton),
        gap = spacing.sm,
        ringCard = MetronomeDefaults.DialMin + dialCardChrome(),
        tempoCard = spacing.md * 2 + tempoRow + spacing.xs + slider,
        cell = spacing.touchTargetMin,
        cellGap = spacing.xs,
        cellsPadding = spacing.sm,
    )
}

/**
 * The Pocket tier for a screen [height]: the cells and the ring while the ring keeps its 160 dp under the cells; the
 * cells and the tempo row with the slider; the tempo row with the slider alone (the cells in the sheet); and, shorter
 * still, the tempo row alone. Tap and play keep their row at the foot in every tier.
 */
private fun pocketTier(height: Dp, cellsCard: Dp, heights: PocketHeights): PocketTier {
    val card = height - heights.transport - heights.gap
    val cardUnderCells = card - cellsCard - heights.gap
    return when {
        cardUnderCells >= heights.ringCard -> PocketTier(cells = true, ring = true, slider = true)
        cardUnderCells >= heights.tempoCard -> PocketTier(cells = true, ring = false, slider = true)
        card >= heights.tempoCard -> PocketTier(cells = false, ring = false, slider = true)
        else -> PocketTier(cells = false, ring = false, slider = false)
    }
}

/** The Pocket form's settings button: the settings glyph in a 44 dp round `surfaceContainerHigh` button. */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier) {
    val colors = TembraTheme.colors
    Box(
        modifier = modifier
            .size(TembraTheme.spacing.touchTargetMin)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClickLabel = MetronomeCopy.metronomeSettings, onClick = onClick)
            .semantics { contentDescription = MetronomeCopy.metronomeSettings }
            .testTag(POCKET_SETTINGS_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = TembraIcons.vector(TembraIcon.Settings), contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/**
 * [settings] at their own height, then [dialCard] [gap] under them filling the rest of the viewport, but never
 * shorter than [minDialCardHeight]: past that the layout is taller than the viewport and its scrolling parent scrolls.
 * The viewport is the minimum height a vertical scroll passes on (it lifts only the maximum), or the maximum height
 * when nothing scrolls.
 */
@Composable
private fun SettingsOverDial(
    gap: Dp,
    minDialCardHeight: Dp,
    modifier: Modifier,
    settings: @Composable () -> Unit,
    dialCard: @Composable () -> Unit,
) {
    Layout(contents = listOf(settings, dialCard), modifier = modifier) { (settingsMeasurables, dialMeasurables), constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val settingsPlaceables = settingsMeasurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        val settingsHeight = settingsPlaceables.sumOf { it.height }
        val cardHeight = maxOf(viewport - settingsHeight - gapPx, minDialCardHeight.roundToPx())
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
private fun WideMetronome(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    telemetry: BeatTelemetry,
    verticalFold: VerticalFold?,
    modifier: Modifier,
) {
    val spacing = TembraTheme.spacing
    val settings: @Composable (Modifier) -> Unit = { size ->
        MetronomeSettingsPanel(
            state = state,
            callbacks = callbacks,
            telemetry = telemetry,
            layout = MetronomeLayout.Wide,
            modifier = size
                .fillMaxHeight()
                .testTag(SETTINGS_TAG)
                .verticalScrollWithScrollbar(rememberScrollState()),
        )
    }
    val dialPane: @Composable (Modifier) -> Unit = { size ->
        Column(modifier = size.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
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
    if (verticalFold != null) {
        // A book-posture foldable splits at its hinge (design iteration D1, FR-011): the settings before it and the
        // dial pane after it, each a gutter clear of the fold.
        FoldSplitRow(
            fold = verticalFold,
            modifier = modifier,
            start = { settings(Modifier.fillMaxWidth()) },
            end = { dialPane(Modifier.fillMaxWidth()) },
        )
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            settings(Modifier.widthFraction(MetronomeDefaults.WIDE_SETTINGS_FRACTION, min = spacing.wideSettingsMinWidth))
            dialPane(Modifier.weight(1f))
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
                    .verticalScrollWithScrollbar(rememberScrollState()),
            )
            LandscapeDialPane(state = state, callbacks = callbacks, block = block, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * The tabletop form (feature 005, spec FR-005): above the fold only what is watched, the dial card holding the ring
 * alone (readout, tempo name, − / + and drag) and the accent cells, which grow tall enough to read from a music stand;
 * below it one controls card ([TabletopControlDeck], design iteration D1). From
 * [MetronomeDefaults.TabletopSideBySideMinWidth] (a book-style foldable) the dial and the cells sit side by side;
 * narrower (a flip-style foldable) they stack, the dial taking the height the cells leave. The regions are tagged `metronome.tabletop.upper` and `.lower`; each scrolls its own content when large fonts
 * make it taller than the region.
 */
@Composable
private fun TabletopMetronome(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    telemetry: BeatTelemetry,
    fold: TabletopFold?,
    modifier: Modifier,
) {
    val spacing = TembraTheme.spacing
    val card = TembraTheme.colors.surfaceContainerLow
    BoxWithConstraints(modifier = modifier) {
        val sideBySide = maxWidth >= MetronomeDefaults.TabletopSideBySideMinWidth
        FoldSplit(
            fold = fold,
            modifier = Modifier.fillMaxSize(),
            upper = {
                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxSize().testTag(TABLETOP_UPPER_TAG),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        TabletopDialCard(state = state, callbacks = callbacks, modifier = Modifier.weight(1f).fillMaxHeight())
                        FilledAccentCard(state = state, callbacks = callbacks, telemetry = telemetry, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().testTag(TABLETOP_UPPER_TAG),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        TabletopDialCard(state = state, callbacks = callbacks, modifier = Modifier.weight(1f).fillMaxWidth())
                        AccentCard(
                            state = state,
                            callbacks = callbacks,
                            telemetry = telemetry,
                            barHeight = spacing.accentCellBarHeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            lower = {
                Box(modifier = Modifier.fillMaxSize().testTag(TABLETOP_LOWER_TAG)) {
                    TabletopControlDeck(state = state, callbacks = callbacks, modifier = Modifier.fillMaxSize())
                }
            },
        )
    }
}

/**
 * Every control below a tabletop fold in one card (feature 005, design iteration D1), tagged `metronome.settings`: a
 * centred "Metronome controls" heading over a seam, the signature menu and the subdivision tiles with the sound pill at
 * the end of their line, the style row, the slider, then tap tempo and play. The rows wrap and the card scrolls when
 * they do not fit (FR-007).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletopControlDeck(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    val settings = state.settings
    Column(
        modifier = modifier
            .tabletopCard(TembraTheme.colors.surfaceContainerLow)
            .verticalScrollWithScrollbar(rememberScrollState())
            .padding(spacing.md)
            .testTag(SETTINGS_TAG),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
    ) {
        // The deck's heading, centred like the tuner's "Tuner controls", over a seam.
        Text(
            text = MetronomeCopy.metronomeControls.uppercase(),
            style = TembraTheme.typography.labelSmall,
            color = TembraTheme.colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.seam)
                .background(TembraTheme.colors.outlineVariant.copy(alpha = DECK_SEAM_ALPHA)),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TimeSignatureMenu(selected = settings.signature, onSelect = callbacks.onSignature)
            SubdivisionTiles(selected = settings.subdivision, unit = settings.signature.unit, onSelect = callbacks.onSubdivision)
            Spacer(Modifier.weight(1f))
            ClickSoundPicker(
                sound = settings.sound,
                customSound = state.customSound,
                layout = ClickSoundPickerLayout.Dropdown,
                onSelect = callbacks.onSound,
                onImportCustom = callbacks.onImportCustom,
                onReplaceCustom = callbacks.onReplaceCustom,
            )
        }
        StyleRow(selected = settings.style, onSelect = callbacks.onStyle, modifier = Modifier.fillMaxWidth())
        TempoSlider(tempo = settings.tempo, onTempo = callbacks.onTempo, modifier = Modifier.fillMaxWidth())
        TransportRow(state = state, callbacks = callbacks, modifier = Modifier.fillMaxWidth())
    }
}

/** A tabletop card: [color] (`surfaceContainerLow`) with the medium corners. */
private fun Modifier.tabletopCard(color: Color): Modifier = background(color, RoundedCornerShape(TembraShapes.medium))

/** The tabletop dial card: the `tempoDialCard` glow holding only the ring, as large as the card allows. */
@Composable
private fun TabletopDialCard(state: MetronomeUiState, callbacks: MetronomeCallbacks, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    TembraGradientCard(
        glow = gradients.meterCompact,
        modifier = modifier,
        discs = CardDiscs.TopStartBottomEnd,
        overlay = null,
        testTag = "metronome.dialCard",
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(spacing.sm), contentAlignment = Alignment.Center) {
            val ring = min(min(maxWidth, maxHeight), spacing.tempoDialMaxWidthWide)
            val scroll = if (ring < MetronomeDefaults.DialMin) Modifier.verticalScrollWithScrollbar(rememberScrollState()) else Modifier
            Box(modifier = Modifier.fillMaxSize().then(scroll), contentAlignment = Alignment.Center) {
                TempoDialFor(state, callbacks, Modifier.size(max(ring, MetronomeDefaults.DialMin)))
            }
        }
    }
}

/**
 * The side-by-side accent card filling its half: the bar box as tall as the card leaves after the level label
 * ([MetronomeDefaults.TabletopCellLabelRoom]), within `accentCellBarHeight` and [MetronomeDefaults.TabletopCellBarMax];
 * it scrolls only when not even the 64 dp bar box fits.
 */
@Composable
private fun FilledAccentCard(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    BoxWithConstraints(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val room = maxHeight - spacing.sm * 2 - MetronomeDefaults.CellPadding * 2 - MetronomeDefaults.TabletopCellLabelRoom * fontScale
        val bar = room.coerceIn(spacing.accentCellBarHeight, MetronomeDefaults.TabletopCellBarMax)
        val scroll = if (room < spacing.accentCellBarHeight) Modifier.verticalScrollWithScrollbar(rememberScrollState()) else Modifier
        AccentCard(state = state, callbacks = callbacks, telemetry = telemetry, barHeight = bar, modifier = Modifier.fillMaxSize().then(scroll))
    }
}

/** The bar's cells centred in a tabletop card, their bar box [barHeight] tall; they light and cycle as in every form. */
@Composable
private fun AccentCard(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, barHeight: Dp, modifier: Modifier) {
    Box(modifier = modifier.tabletopCard(TembraTheme.colors.surfaceContainerLow).padding(TembraTheme.spacing.sm), contentAlignment = Alignment.Center) {
        AccentCells(
            pattern = state.settings.pattern,
            running = state.running,
            currentBeat = state.currentBeat,
            telemetry = telemetry,
            onCycle = callbacks.onCycleBeat,
            modifier = Modifier.fillMaxWidth(),
            barHeight = barHeight,
        )
    }
}

/** The landscape dial block's sizes: the ring, the tap and play row's height, and whether the block must scroll. */
private data class LandscapeBlock(val ring: Dp, val row: Dp, val scrolls: Boolean)

/**
 * The dial pane's sizes for a pane [height] (T152): the tap and play row is 48 dp up to a 300 dp pane and grows
 * linearly to 64 dp at 420 dp; the ring takes the rest of the height ([chrome] being the card's padding, the slider and
 * the gaps), within 160–300 dp and never wider than [ringWidth]. Only when even the 48 dp row leaves the ring under
 * 160 dp does the dial card scroll, with the ring at 160 dp; the row stays in view under it.
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
 * the pane. In a pane too short for both ([LandscapeBlock.scrolls]: a short desktop window) the card scrolls in the
 * height the row leaves, so the row is never pushed out of view. Tab reads the dial, the slider, tap, then play.
 */
@Composable
private fun LandscapeDialPane(state: MetronomeUiState, callbacks: MetronomeCallbacks, block: LandscapeBlock, modifier: Modifier) {
    val spacing = TembraTheme.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        DialCard(
            state = state,
            callbacks = callbacks,
            discs = CardDiscs.None,
            modifier = if (block.scrolls) {
                Modifier.weight(1f).fillMaxWidth().verticalScrollWithScrollbar(rememberScrollState())
            } else {
                Modifier.weight(1f).fillMaxWidth()
            },
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
        glow = gradients.meterCompact,
        modifier = modifier,
        discs = discs,
        overlay = null,
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
private const val DECK_SEAM_ALPHA = 0.2f
private const val TABLETOP_UPPER_TAG = "metronome.tabletop.upper"
private const val TABLETOP_LOWER_TAG = "metronome.tabletop.lower"
private const val POCKET_SETTINGS_TAG = "metronome.pocket.settings"
