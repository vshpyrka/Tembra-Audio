package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/** The By Ear card: preset picker and loop toggle above the string cards (spec FR-017, contracts/tuner-screen.md). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ByEarPane(state: TunerUiState, callbacks: TunerCallbacks, windowWidth: WindowWidth, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    val expanded = windowWidth == WindowWidth.Expanded
    TembraGradientCard(glow = if (expanded) gradients.byEarWide else gradients.byEarCompact, testTag = "tuner.byEar", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The toggle wraps below the picker when large fonts leave the picker no room (spec FR-023).
            FlowRow(
                modifier = Modifier.widthIn(max = if (expanded) spacing.byEarGridMaxWidthWide else spacing.byEarGridMaxWidth).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                TuningPresetPicker(
                    current = state.preset,
                    presets = state.presets,
                    onPreset = callbacks.onPreset,
                    modifier = Modifier.weight(1f).widthIn(min = spacing.presetPickerMinWidth),
                )
                LoopToneToggle(checked = state.loopTone, onCheckedChange = callbacks.onLoopTone)
            }
            StringCardGrid(
                strings = state.strings,
                playingIndex = state.audition?.stringIndex,
                windowWidth = windowWidth,
                onAudition = callbacks.onAudition,
                onStop = callbacks.onStopAudition,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
