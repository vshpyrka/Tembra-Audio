package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.ui.designsystem.SegmentWidths
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme

/**
 * The tuner's settings below a tabletop fold (feature 005, spec FR-003, FR-004): one card tagged `tuner.settings`, the
 * landscape settings column laid out across the width. A "Tuner controls" header carries the audio-state pill; under a
 * seam the instrument and mode selectors share the width with equal segments; in By Ear a further row holds the string
 * count (guitar only), the tuning preset picker and the loop toggle. Each row wraps when large fonts leave it no room,
 * and the card scrolls when its rows are taller than the region (FR-007); otherwise the rows sit in its vertical
 * middle, within reach of hands resting on the table. It takes only the fields it renders, so the 10 Hz reading never
 * recomposes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerTabletopSettings(
    instrument: Instrument,
    guitarStrings: GuitarStringCount,
    mode: TunerMode,
    pill: AudioStatePill?,
    preset: TuningPreset,
    presets: List<TuningPreset>,
    loopTone: Boolean,
    callbacks: TunerCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    // A selector keeps the landscape column's minimum width, grown with the font scale, before its row wraps.
    val selectorMin = spacing.landscapeSettingsMinWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)
    TembraGradientCard(glow = TembraTheme.gradients.byEarCompact, modifier = modifier, testTag = "tuner.settings") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = TunerCopy.tunerControls.uppercase(),
                    style = TembraTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = spacing.sm),
                )
                if (pill != null) AudioStatePillView(pill)
            }
            Box(modifier = Modifier.fillMaxWidth().height(spacing.seam).background(colors.outlineVariant.copy(alpha = SEAM_ALPHA)))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentControl(
                    instrument = instrument,
                    onInstrument = callbacks.onInstrument,
                    modifier = Modifier.weight(1f).widthIn(min = selectorMin),
                    widths = SegmentWidths.Equal,
                )
                ModeControl(mode = mode, onMode = callbacks.onMode, modifier = Modifier.weight(1f).widthIn(min = selectorMin), widths = SegmentWidths.Equal)
            }
            if (mode == TunerMode.ByEar) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (instrument.hasStringCount) StringCountControl(guitarStrings = guitarStrings, onGuitarStrings = callbacks.onGuitarStrings)
                    TuningPresetPicker(
                        current = preset,
                        presets = presets,
                        onPreset = callbacks.onPreset,
                        modifier = Modifier.weight(1f).widthIn(min = spacing.presetPickerMinWidth),
                    )
                    LoopToneToggle(checked = loopTone, onCheckedChange = callbacks.onLoopTone)
                }
            }
        }
    }
}

private const val SEAM_ALPHA = 0.2f
