package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.designsystem.SegmentAccent
import audio.tembra.ui.designsystem.SegmentSpec
import audio.tembra.ui.designsystem.SegmentedStyle
import audio.tembra.ui.designsystem.TembraSegmentedControl
import audio.tembra.ui.designsystem.TembraStatusPill
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/**
 * The selector bar above both modes (spec FR-002, FR-023): instrument, audio-state pill, string count (guitar only)
 * and mode. Compact and medium windows stack two rows (instrument + pill; strings + mode); expanded windows use one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerSelectors(state: TunerUiState, callbacks: TunerCallbacks, windowWidth: WindowWidth, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val instrumentItems = Instrument.entries.map { SegmentSpec(it.id, it.label, if (it == Instrument.Guitar) TembraIcon.Guitar else TembraIcon.Cello) }
    val stringItems = GuitarStringCount.entries.map { SegmentSpec(it.count.toString(), it.label) }
    val modeItems = TunerMode.entries.map { SegmentSpec(it.id, it.label, if (it == TunerMode.AutoListen) TembraIcon.Mic else TembraIcon.Hearing) }

    val instrumentControl: @Composable () -> Unit = {
        TembraSegmentedControl(
            items = instrumentItems,
            selectedId = state.instrument.id,
            onSelect = { id -> Instrument.entries.firstOrNull { it.id == id }?.let(callbacks.onInstrument) },
            style = SegmentedStyle.Large,
            testTag = "tuner.instrument",
        )
    }
    val pill: @Composable () -> Unit = {
        val current = state.pill
        if (current != null) AudioStatePillView(current)
    }
    val stringsControl: @Composable () -> Unit = {
        if (state.instrument.hasStringCount) {
            TembraSegmentedControl(
                items = stringItems,
                selectedId = state.guitarStrings.count.toString(),
                onSelect = { id -> GuitarStringCount.entries.firstOrNull { it.count.toString() == id }?.let(callbacks.onGuitarStrings) },
                style = SegmentedStyle.Small,
                accent = SegmentAccent.Subtle,
                testTag = "tuner.strings",
            )
        }
    }
    val modeControl: @Composable () -> Unit = {
        TembraSegmentedControl(
            items = modeItems,
            selectedId = state.mode.id,
            onSelect = { id -> TunerMode.entries.firstOrNull { it.id == id }?.let(callbacks.onMode) },
            style = SegmentedStyle.Small,
            testTag = "tuner.mode",
        )
    }

    if (windowWidth == WindowWidth.Expanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            instrumentControl()
            pill()
            Spacer(modifier = Modifier.weight(1f))
            stringsControl()
            modeControl()
        }
    } else {
        // Two rows; each wraps onto a further line when large fonts make its controls wider than the window.
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                instrumentControl()
                pill()
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (state.instrument.hasStringCount) Arrangement.SpaceBetween else Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                stringsControl()
                modeControl()
            }
        }
    }
}

/** The pill in its three states: colour, glyph and pulse per contracts/tuner-screen.md. */
@Composable
private fun AudioStatePillView(pill: AudioStatePill) {
    val colors = TembraTheme.colors
    when (pill) {
        AudioStatePill.MicActive -> TembraStatusPill(text = pill.label, color = colors.primary, icon = TembraIcon.Mic, pulsing = true, testTag = "tuner.pill")
        AudioStatePill.ToneOut -> TembraStatusPill(text = pill.label, color = colors.secondary, icon = TembraIcon.VolumeUp, testTag = "tuner.pill")
        AudioStatePill.TestSignal -> TembraStatusPill(text = pill.label, color = colors.tertiary, icon = TembraIcon.Timeline, testTag = "tuner.pill")
    }
}
