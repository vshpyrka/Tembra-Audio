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
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.theme.TembraTheme

/**
 * The selector bar above both modes (spec FR-002, FR-023): instrument, audio-state pill, string count (guitar only)
 * and mode. [TunerLayout.Wide] puts them on one row; the stacked layout uses two rows (instrument + pill; strings +
 * mode). Phone landscape does not use this bar: [TunerSettingsColumn] holds the same controls in a column. It takes
 * only the fields it renders, so the 10 Hz reading never recomposes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerSelectors(
    instrument: Instrument,
    guitarStrings: GuitarStringCount,
    mode: TunerMode,
    pill: AudioStatePill?,
    callbacks: TunerCallbacks,
    layout: TunerLayout,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    when (layout) {
        TunerLayout.Wide -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentControl(instrument = instrument, onInstrument = callbacks.onInstrument)
            if (pill != null) AudioStatePillView(pill)
            Spacer(modifier = Modifier.weight(1f))
            if (instrument.hasStringCount) StringCountControl(guitarStrings = guitarStrings, onGuitarStrings = callbacks.onGuitarStrings)
            ModeControl(mode = mode, onMode = callbacks.onMode)
        }
        TunerLayout.Stacked, TunerLayout.Landscape -> Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            // Two rows; each wraps onto a further line when large fonts make its controls wider than the window.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentControl(instrument = instrument, onInstrument = callbacks.onInstrument)
                if (pill != null) AudioStatePillView(pill)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (instrument.hasStringCount) Arrangement.SpaceBetween else Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (instrument.hasStringCount) StringCountControl(guitarStrings = guitarStrings, onGuitarStrings = callbacks.onGuitarStrings)
                ModeControl(mode = mode, onMode = callbacks.onMode)
            }
        }
    }
}
