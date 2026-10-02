package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.theme.TembraTheme

/**
 * The selector bar above both modes (spec FR-002, FR-023): instrument, audio-state pill and mode. The string count
 * is not here: it only matters to By Ear, so it sits beside the tuning preset in that card (clarified 2026-09-28).
 * [TunerLayout.Wide] puts the three on one row (the tabletop form uses [TunerTabletopSettings] instead and takes the
 * same one-row branch should it ever be passed); the stacked layout uses two rows (instrument + pill; mode). Phone
 * landscape does not use this bar: [TunerSettingsColumn] holds the same controls in a column. It takes only the
 * fields it renders, so the 10 Hz reading never recomposes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerSelectors(
    instrument: Instrument,
    mode: TunerMode,
    pill: AudioStatePill?,
    callbacks: TunerCallbacks,
    layout: TunerLayout,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    when (layout) {
        TunerLayout.Wide, TunerLayout.Tabletop -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentControl(instrument = instrument, onInstrument = callbacks.onInstrument)
            if (pill != null) AudioStatePillView(pill)
            Spacer(modifier = Modifier.weight(1f))
            ModeControl(mode = mode, onMode = callbacks.onMode)
        }
        TunerLayout.Stacked, TunerLayout.Landscape -> Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            // The first row wraps onto a further line when large fonts make its controls wider than the window.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentControl(instrument = instrument, onInstrument = callbacks.onInstrument)
                if (pill != null) AudioStatePillView(pill)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ModeControl(mode = mode, onMode = callbacks.onMode)
            }
        }
    }
}
