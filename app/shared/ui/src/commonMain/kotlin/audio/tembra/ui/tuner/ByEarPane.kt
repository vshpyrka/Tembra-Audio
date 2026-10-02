package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.TargetString
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme

/**
 * The By Ear card (spec FR-017, contracts/tuner-screen.md): in the stacked and wide layouts the string count (guitar
 * only, [guitarStrings] null hides it), the preset picker and the loop toggle sit above the string cards; in phone
 * landscape those live in the settings column and the cards fill the card in two rows (research R9); above a tabletop
 * fold (feature 005, FR-004) the cards fill the card the same way and the settings sit below the fold. It takes only
 * the fields it renders, so a reading-only state change leaves it untouched.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        TunerLayout.Stacked, TunerLayout.Wide -> {
            val wide = layout == TunerLayout.Wide
            TembraGradientCard(glow = if (wide) gradients.byEarWide else gradients.byEarCompact, modifier = modifier, testTag = "tuner.byEar") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The string count leads the row; the picker and the toggle wrap onto further lines when the width
                    // (compact) or large fonts leave them no room (spec FR-023).
                    FlowRow(
                        modifier = Modifier.widthIn(max = if (wide) spacing.byEarGridMaxWidthWide else spacing.byEarGridMaxWidth).fillMaxWidth(),
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
        }
    }
}

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
                .verticalScroll(rememberScrollState())
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
