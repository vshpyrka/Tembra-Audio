package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.ui.designsystem.SegmentWidths
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.theme.TembraTheme

/**
 * The tuner's settings below a tabletop fold (feature 005, spec FR-003, FR-004; design iteration D1): one card tagged
 * `tuner.settings`. A centred "Tuner controls" heading with the audio-state pill at the end of its line, a seam, then
 * one centred column no wider than the wide string grid: the instrument selector, the mode selector and, in By Ear,
 * the string count (guitar only), the tuning preset picker and the loop toggle, each at the column's full width. The
 * card scrolls when the column is taller than the region (FR-007); otherwise it sits in the card's vertical middle,
 * within reach of hands resting on the table. It takes only the fields it renders, so the 10 Hz reading never
 * recomposes it.
 */
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
    TembraGradientCard(glow = TembraTheme.gradients.byEarCompact, modifier = modifier, testTag = "tuner.settings") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        ) {
            // The heading centred over the card, the audio-state pill at the end of its line (design iteration D1).
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = TunerCopy.tunerControls.uppercase(),
                    style = TembraTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pill != null) AudioStatePillView(pill, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Box(modifier = Modifier.fillMaxWidth().height(spacing.seam).background(colors.outlineVariant.copy(alpha = SEAM_ALPHA)))
            // Every control stacked full width in one centred column, no wider than the wide string grid (D1).
            Column(
                modifier = Modifier.widthIn(max = spacing.byEarGridMaxWidthWide).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                InstrumentControl(instrument = instrument, onInstrument = callbacks.onInstrument, modifier = Modifier.fillMaxWidth(), widths = SegmentWidths.Equal)
                ModeControl(mode = mode, onMode = callbacks.onMode, modifier = Modifier.fillMaxWidth(), widths = SegmentWidths.Equal)
                if (mode == TunerMode.ByEar) {
                    if (instrument.hasStringCount) {
                        StringCountControl(guitarStrings = guitarStrings, onGuitarStrings = callbacks.onGuitarStrings, modifier = Modifier.fillMaxWidth(), widths = SegmentWidths.Equal)
                    }
                    TuningPresetPicker(current = preset, presets = presets, onPreset = callbacks.onPreset, modifier = Modifier.fillMaxWidth())
                    LoopToneToggle(checked = loopTone, onCheckedChange = callbacks.onLoopTone, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private const val SEAM_ALPHA = 0.2f
