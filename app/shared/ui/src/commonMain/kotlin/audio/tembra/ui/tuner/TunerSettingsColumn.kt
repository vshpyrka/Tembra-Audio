package audio.tembra.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPreset
import audio.tembra.ui.designsystem.SegmentWidths
import audio.tembra.ui.designsystem.TembraGradientCard
import audio.tembra.ui.designsystem.verticalScrollWithScrollbar
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The phone-landscape settings column (spec FR-023, research R9 landscape table, the Stitch "Mobile Landscape"
 * screens): a card with a "Tuner controls" header carrying the audio-state pill, then the instrument and mode
 * selectors sharing the column's width, and in By Ear the string count (guitar only), the tuning preset picker and
 * the loop toggle. The design's overline above each control is left out: with 44 dp targets the By Ear column is 271 dp tall
 * and a phone's landscape window had about 295 dp under the former top bar (351 dp now), so the overlines would push the picker and the
 * toggle out of view (research R9). It is 36 % of the content width within the design's 224–280 dp, scaled by the
 * font scale up to half the width, and scrolls vertically when large fonts make it taller than the window. It takes
 * only the fields it renders, so the 10 Hz reading never recomposes it. [autoListenControls] (the noise gate control,
 * feature 006 FR-016) goes after the mode selector at the column's width; null leaves no space.
 */
@Composable
internal fun TunerSettingsColumn(
    instrument: Instrument,
    guitarStrings: GuitarStringCount,
    mode: TunerMode,
    pill: AudioStatePill?,
    preset: TuningPreset,
    presets: List<TuningPreset>,
    loopTone: Boolean,
    callbacks: TunerCallbacks,
    modifier: Modifier = Modifier,
    autoListenControls: (@Composable () -> Unit)? = null,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val fontScale = LocalDensity.current.fontScale
    TembraGradientCard(
        glow = TembraTheme.gradients.byEarCompact,
        modifier = modifier.settingsWidth(spacing.landscapeSettingsMinWidth, spacing.landscapeSettingsMaxWidth, fontScale),
        testTag = "tuner.settings",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollWithScrollbar(rememberScrollState())
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
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
            InstrumentControl(instrument = instrument, onInstrument = callbacks.onInstrument, modifier = Modifier.fillMaxWidth(), widths = SegmentWidths.Equal)
            ModeControl(mode = mode, onMode = callbacks.onMode, modifier = Modifier.fillMaxWidth(), widths = SegmentWidths.Equal)
            autoListenControls?.invoke()
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

/**
 * [SETTINGS_FRACTION] of the available width, clamped to [min]..[max] scaled by [fontScale] (never below 1), and never
 * more than [SETTINGS_MAX_FRACTION] of the width so the content card keeps most of a narrow window.
 */
private fun Modifier.settingsWidth(min: Dp, max: Dp, fontScale: Float): Modifier = layout { measurable, constraints ->
    val scale = fontScale.coerceAtLeast(1f)
    val lower = (min.toPx() * scale).roundToInt()
    val upper = (max.toPx() * scale).roundToInt()
    val width = if (constraints.hasBoundedWidth) {
        (constraints.maxWidth * SETTINGS_FRACTION).roundToInt()
            .coerceIn(lower, upper)
            .coerceAtMost((constraints.maxWidth * SETTINGS_MAX_FRACTION).roundToInt())
    } else {
        upper
    }
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private const val SETTINGS_FRACTION = 0.36f
private const val SETTINGS_MAX_FRACTION = 0.5f
private const val SEAM_ALPHA = 0.2f
