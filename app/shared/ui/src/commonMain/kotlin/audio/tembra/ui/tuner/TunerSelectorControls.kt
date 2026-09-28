package audio.tembra.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.tuner.GuitarStringCount
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.designsystem.SegmentAccent
import audio.tembra.ui.designsystem.SegmentSpec
import audio.tembra.ui.designsystem.SegmentWidths
import audio.tembra.ui.designsystem.SegmentedStyle
import audio.tembra.ui.designsystem.TembraSegmentedControl
import audio.tembra.ui.designsystem.TembraStatusPill
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme

// The three selectors' segments depend only on the enums, so they are built once rather than on every recomposition.
private val instrumentItems = Instrument.entries.map { SegmentSpec(it.id, it.label, if (it == Instrument.Guitar) TembraIcon.Guitar else TembraIcon.Cello) }
private val stringItems = GuitarStringCount.entries.map { SegmentSpec(it.count.toString(), it.label) }
private val modeItems = TunerMode.entries.map { SegmentSpec(it.id, it.label, if (it == TunerMode.AutoListen) TembraIcon.Mic else TembraIcon.Hearing) }

/** The instrument selector (spec FR-002): the large style, `tuner.instrument`. */
@Composable
internal fun InstrumentControl(
    instrument: Instrument,
    onInstrument: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
    widths: SegmentWidths = SegmentWidths.Content,
) {
    TembraSegmentedControl(
        items = instrumentItems,
        selectedId = instrument.id,
        onSelect = { id -> Instrument.entries.firstOrNull { it.id == id }?.let(onInstrument) },
        style = SegmentedStyle.Large,
        modifier = modifier,
        widths = widths,
        testTag = "tuner.instrument",
    )
}

/** The string-count selector (spec FR-003): small, subtle accent, `tuner.strings`; callers show it for guitars only. */
@Composable
internal fun StringCountControl(
    guitarStrings: GuitarStringCount,
    onGuitarStrings: (GuitarStringCount) -> Unit,
    modifier: Modifier = Modifier,
    widths: SegmentWidths = SegmentWidths.Content,
) {
    TembraSegmentedControl(
        items = stringItems,
        selectedId = guitarStrings.count.toString(),
        onSelect = { id -> GuitarStringCount.entries.firstOrNull { it.count.toString() == id }?.let(onGuitarStrings) },
        style = SegmentedStyle.Small,
        modifier = modifier,
        accent = SegmentAccent.Subtle,
        widths = widths,
        testTag = "tuner.strings",
    )
}

/** The mode selector (spec FR-001): small, primary accent, `tuner.mode`. */
@Composable
internal fun ModeControl(
    mode: TunerMode,
    onMode: (TunerMode) -> Unit,
    modifier: Modifier = Modifier,
    widths: SegmentWidths = SegmentWidths.Content,
) {
    TembraSegmentedControl(
        items = modeItems,
        selectedId = mode.id,
        onSelect = { id -> TunerMode.entries.firstOrNull { it.id == id }?.let(onMode) },
        style = SegmentedStyle.Small,
        modifier = modifier,
        widths = widths,
        testTag = "tuner.mode",
    )
}

/** The audio-state pill in its three states: colour, glyph and pulse per contracts/tuner-screen.md; `tuner.pill`. */
@Composable
internal fun AudioStatePillView(pill: AudioStatePill, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    when (pill) {
        AudioStatePill.MicActive -> TembraStatusPill(text = pill.label, color = colors.primary, modifier = modifier, icon = TembraIcon.Mic, pulsing = true, testTag = "tuner.pill")
        AudioStatePill.ToneOut -> TembraStatusPill(text = pill.label, color = colors.secondary, modifier = modifier, icon = TembraIcon.VolumeUp, testTag = "tuner.pill")
        AudioStatePill.TestSignal -> TembraStatusPill(text = pill.label, color = colors.tertiary, modifier = modifier, icon = TembraIcon.Timeline, testTag = "tuner.pill")
    }
}
