package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.TargetString
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.designsystem.verticalScrollWithScrollbar
import audio.tembra.ui.theme.TembraTheme

/**
 * The By Ear card (spec FR-017, contracts/tuner-screen.md): in the stacked and wide layouts the string count (guitar
 * only, [guitarStrings] null hides it), the preset picker and the loop toggle sit above the string cards; in phone
 * landscape those live in the settings column and the cards fill the card in two rows (research R9); above a tabletop
 * fold (feature 005, FR-004) the cards fill the card the same way and the settings sit below the fold. Wide, the
 * square cards shrink to fit the card's height ([FittedByEarBlock], feature 005) and the block sits in its middle; the
 * stacked (phone portrait) column scrolls instead. It takes only
 * the fields it renders, so a reading-only state change leaves it untouched.
 */
@Composable
internal fun ByEarPane(
    guitarStrings: GuitarStringCount?,
    preset: TuningPreset,
    presets: List<TuningPreset>,
    loopTone: Boolean,
    playingIndex: Int?,
    callbacks: TunerCallbacks,
    layout: TunerLayout,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    val strings = remember(preset) { preset.strings }
    when (layout) {
        TunerLayout.Landscape, TunerLayout.Tabletop -> TembraGradientCard(glow = gradients.byEarWide, modifier = modifier, testTag = "tuner.byEar") {
            FilledStringCards(strings = strings, playingIndex = playingIndex, layout = layout, onAudition = callbacks.onAudition, onStop = callbacks.onStopAudition)
        }
        TunerLayout.Stacked, TunerLayout.Pocket -> TembraGradientCard(glow = gradients.byEarCompact, modifier = modifier, testTag = "tuner.byEar") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollWithScrollbar(rememberScrollState())
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ByEarControls(guitarStrings, preset, presets, loopTone, callbacks, maxWidth = spacing.byEarGridMaxWidth)
                StringCardGrid(
                    strings = strings,
                    playingIndex = playingIndex,
                    layout = layout,
                    onAudition = callbacks.onAudition,
                    onStop = callbacks.onStopAudition,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        TunerLayout.Wide -> WideByEarCard(
            strings = strings,
            playingIndex = playingIndex,
            callbacks = callbacks,
            modifier = modifier,
            controls = { ByEarControls(guitarStrings, preset, presets, loopTone, callbacks, maxWidth = spacing.byEarGridMaxWidthWide) },
        )
    }
}

/**
 * The string cards alone in the wide By Ear card, for the pane beside a book-posture hinge (design iteration D3): the
 * string count, the preset and the loop tone are in the tuner's controls card on the other side of the hinge.
 */
@Composable
internal fun ByEarCardsPane(preset: TuningPreset, playingIndex: Int?, callbacks: TunerCallbacks, modifier: Modifier = Modifier) {
    val strings = remember(preset) { preset.strings }
    WideByEarCard(strings = strings, playingIndex = playingIndex, callbacks = callbacks, modifier = modifier, controls = {})
}

/** The wide By Ear card: [controls] over the fitted grid of square cards ([FittedByEarBlock]). */
@Composable
private fun WideByEarCard(
    strings: List<TargetString>,
    playingIndex: Int?,
    callbacks: TunerCallbacks,
    modifier: Modifier,
    controls: @Composable () -> Unit,
) {
    val spacing = TembraTheme.spacing
    TembraGradientCard(glow = TembraTheme.gradients.byEarWide, modifier = modifier, testTag = "tuner.byEar") {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewport = maxHeight - spacing.md * 2
            Box(modifier = Modifier.fillMaxSize().verticalScrollWithScrollbar(rememberScrollState()).padding(spacing.md)) {
                FittedByEarBlock(
                    viewportHeight = viewport,
                    gap = spacing.md,
                    controls = controls,
                    grid = { width, height ->
                        StringCardGrid(
                            strings = strings,
                            playingIndex = playingIndex,
                            layout = TunerLayout.Wide,
                            onAudition = callbacks.onAudition,
                            onStop = callbacks.onStopAudition,
                            modifier = Modifier.fillMaxWidth(),
                            heightBudget = height,
                            widthBudget = width,
                        )
                    },
                )
            }
        }
    }
}

/**
 * The string count (guitar only, [guitarStrings] null hides it), the preset picker and the loop toggle in one row no
 * wider than [maxWidth]; the picker and the toggle wrap onto further lines when the width or large fonts leave them no
 * room (spec FR-023).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ByEarControls(
    guitarStrings: GuitarStringCount?,
    preset: TuningPreset,
    presets: List<TuningPreset>,
    loopTone: Boolean,
    callbacks: TunerCallbacks,
    maxWidth: Dp,
) {
    val spacing = TembraTheme.spacing
    FlowRow(
        modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (guitarStrings != null) StringCountControl(guitarStrings = guitarStrings, onGuitarStrings = callbacks.onGuitarStrings)
        TuningPresetPicker(
            current = preset,
            presets = presets,
            onPreset = callbacks.onPreset,
            modifier = Modifier.weight(1f).widthIn(min = spacing.presetPickerMinWidth),
        )
        LoopToneToggle(checked = loopTone, onCheckedChange = callbacks.onLoopTone)
    }
}

/**
 * The wide By Ear block (feature 005): [controls] at their own height, then [grid] [gap] under them, handed the width
 * and the height left of [viewportHeight] as its budgets so the cards fit rather than scroll. Both are centred horizontally,
 * and the block sits in the middle of the viewport when it is shorter than it; only when the grid cannot shrink enough
 * (its cards at their minimum) is the block taller than the viewport, and its scrolling parent scrolls. The budget is
 * worked out in layout from the measured controls; nothing measured is written back to composition.
 */
@Composable
private fun FittedByEarBlock(
    viewportHeight: Dp,
    gap: Dp,
    controls: @Composable () -> Unit,
    grid: @Composable (width: Dp, height: Dp) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val controlPlaceables = subcompose(ByEarSlot.Controls, controls).map { it.measure(loose) }
        val controlsHeight = controlPlaceables.maxOfOrNull { it.height } ?: 0
        // No controls (the pane beside a hinge): no gap above the grid either.
        val gapPx = if (controlsHeight > 0) gap.roundToPx() else 0
        val budget = (viewportHeight.roundToPx() - controlsHeight - gapPx).coerceAtLeast(0).toDp()
        val gridPlaceables = subcompose(ByEarSlot.Grid) { grid(width.toDp(), budget) }.map { it.measure(loose) }
        val gridHeight = gridPlaceables.maxOfOrNull { it.height } ?: 0
        val blockHeight = controlsHeight + gapPx + gridHeight
        val height = maxOf(blockHeight, viewportHeight.roundToPx())
        layout(width, height) {
            val top = (height - blockHeight) / 2
            controlPlaceables.forEach { it.place((width - it.width) / 2, top) }
            gridPlaceables.forEach { it.place((width - it.width) / 2, top + controlsHeight + gapPx) }
        }
    }
}

/** The two slots of [FittedByEarBlock]. */
private enum class ByEarSlot { Controls, Grid }

/**
 * The landscape grid: two rows that share the card's inner height (never under `stringCardHeightLandscape`), scrolling
 * only when large fonts make the cards taller than that.
 */
@Composable
private fun FilledStringCards(strings: List<TargetString>, playingIndex: Int?, layout: TunerLayout, onAudition: (Int) -> Unit, onStop: () -> Unit) {
    val spacing = TembraTheme.spacing
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rowMinHeight = maxOf(spacing.stringCardHeightLandscape, (maxHeight - spacing.md * 2 - spacing.sm) / 2)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollWithScrollbar(rememberScrollState())
                .padding(spacing.md),
        ) {
            StringCardGrid(
                strings = strings,
                playingIndex = playingIndex,
                layout = layout,
                onAudition = onAudition,
                onStop = onStop,
                rowMinHeight = rowMinHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
