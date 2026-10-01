package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.metronome.components.AccentCells
import audio.tembra.ui.metronome.components.ClickSoundPicker
import audio.tembra.ui.metronome.components.ClickSoundPickerLayout
import audio.tembra.ui.metronome.components.MetronomeDefaults
import audio.tembra.ui.metronome.components.SectionLabel
import audio.tembra.ui.metronome.components.StyleRow
import audio.tembra.ui.metronome.components.SubdivisionChips
import audio.tembra.ui.metronome.components.SubdivisionTiles
import audio.tembra.ui.metronome.components.TimeSignatureChips
import audio.tembra.ui.metronome.components.TimeSignatureMenu
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The metronome's settings in the design's order (contracts/metronome-screen.md, research R10), shaped by [layout]:
 * - [MetronomeLayout.Stacked] and [MetronomeLayout.Landscape] (design iteration D1, T150): one compact "bar shape"
 *   card, tagged `metronome.bar`, holding the signature menu and the subdivision tiles on one line, the accent cells in
 *   one row and the style row, then the click sound pill centred under it; the card is small enough that the dial
 *   keeps its size under it on a phone held upright, and that it stands whole beside the dial pane held sideways;
 * - [MetronomeLayout.Wide]: section cards for the time signature (with its "Common & Complex" caption, six chips in a
 *   row), the subdivision chips, the cells with the style row, and the sound tile grid in its own "Click timbre" card,
 *   as in the tablet design.
 */
@Composable
internal fun MetronomeSettingsPanel(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    telemetry: BeatTelemetry,
    layout: MetronomeLayout,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TembraTheme.spacing.md)) {
        when (layout) {
            MetronomeLayout.Stacked, MetronomeLayout.Landscape -> {
                // The landscape column (2⁄5 of a phone held sideways) is narrower than a phone held upright: 2 dp between
                // the cells keeps 7/8 in one row of 44 dp cells there; the portrait card keeps 4 dp.
                val cellGap = if (layout == MetronomeLayout.Landscape) TembraTheme.spacing.xs / 2 else TembraTheme.spacing.xs
                BarShapeCard(state = state, callbacks = callbacks, telemetry = telemetry, cellGap = cellGap)
                SoundPicker(state, callbacks, ClickSoundPickerLayout.Dropdown, Modifier.align(Alignment.CenterHorizontally))
            }
            MetronomeLayout.Wide -> {
                SectionCards(state = state, callbacks = callbacks, telemetry = telemetry)
                SectionCard {
                    SectionHeader(label = MetronomeCopy.clickTimbre)
                    SoundPicker(state, callbacks, ClickSoundPickerLayout.Tiles, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The compact layout's one card (design iteration D1, the Stitch "live performance" variant): the signature menu at the
 * start and the subdivision tiles at the end of its first line (the tiles move under the menu when large fonts leave
 * no room), the accent cells in one row 4 dp apart, and the style row.
 */
@Composable
private fun BarShapeCard(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, cellGap: Dp) {
    val settings = state.settings
    val spacing = TembraTheme.spacing
    SectionCard(gap = spacing.sm, modifier = barShapeSemantics()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TimeSignatureMenu(selected = settings.signature, onSelect = callbacks.onSignature)
            Spacer(Modifier.weight(1f))
            SubdivisionTiles(selected = settings.subdivision, unit = settings.signature.unit, onSelect = callbacks.onSubdivision)
        }
        Cells(state, callbacks, telemetry, gap = cellGap)
        StyleRow(selected = settings.style, onSelect = callbacks.onStyle, modifier = Modifier.fillMaxWidth())
    }
}

/** The bar-shape card has no drawn heading (the Stitch screens have none): its semantics name it and group its controls. */
private fun barShapeSemantics(): Modifier = Modifier.testTag(BAR_TAG).semantics {
    contentDescription = MetronomeCopy.barShape
    isTraversalGroup = true
}

/** The expanded settings: a card per section, as the tablet design. */
@Composable
private fun SectionCards(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry) {
    val settings = state.settings
    SectionCard {
        SectionHeader(label = MetronomeCopy.timeSignature, caption = MetronomeCopy.commonAndComplex)
        TimeSignatureChips(
            selected = settings.signature,
            onSelect = callbacks.onSignature,
            modifier = Modifier.fillMaxWidth(),
            columns = TimeSignature.entries.size,
        )
    }
    SectionCard {
        SectionHeader(label = MetronomeCopy.rhythmSubdivision)
        SubdivisionChips(
            selected = settings.subdivision,
            unit = settings.signature.unit,
            onSelect = callbacks.onSubdivision,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SectionCard(gap = MetronomeDefaults.CellsStyleGap) {
        Cells(state, callbacks, telemetry, gap = MetronomeDefaults.CellGap)
        StyleRow(selected = settings.style, onSelect = callbacks.onStyle, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Cells(state: MetronomeUiState, callbacks: MetronomeCallbacks, telemetry: BeatTelemetry, gap: Dp) {
    AccentCells(
        pattern = state.settings.pattern,
        running = state.running,
        currentBeat = state.currentBeat,
        telemetry = telemetry,
        onCycle = callbacks.onCycleBeat,
        gap = gap,
    )
}

@Composable
private fun SoundPicker(state: MetronomeUiState, callbacks: MetronomeCallbacks, layout: ClickSoundPickerLayout, modifier: Modifier) {
    ClickSoundPicker(
        sound = state.settings.sound,
        customSound = state.customSound,
        layout = layout,
        onSelect = callbacks.onSound,
        onImportCustom = callbacks.onImportCustom,
        onReplaceCustom = callbacks.onReplaceCustom,
        modifier = modifier,
    )
}

/** One section: `surfaceContainerLow`, 12 dp corners, 8 dp padding, its parts [gap] apart. */
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    gap: Dp = MetronomeDefaults.SectionGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TembraTheme.colors.surfaceContainerLow, RoundedCornerShape(TembraShapes.medium))
            .padding(TembraTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * A section's label and optional caption on one line, inset by 4 dp as the design's `px-1`; when large fonts leave no
 * room for both, the caption wraps under the label rather than cutting it short.
 */
@Composable
private fun SectionHeader(label: String, caption: String? = null) {
    val spacing = TembraTheme.spacing
    val extras = TembraTheme.typographyExtras
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text = label, modifier = Modifier.padding(end = spacing.sm))
        if (caption != null) {
            Text(
                text = caption,
                // The design's caption is the tabular parameter face at the section label's 10 sp size.
                style = extras.tabularParam.copy(fontSize = extras.sectionLabel.fontSize),
                color = TembraTheme.colors.outline,
            )
        }
    }
}

private const val BAR_TAG = "metronome.bar"
