package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import audio.tembra.ui.metronome.components.AccentCells
import audio.tembra.ui.metronome.components.ClickSoundPicker
import audio.tembra.ui.metronome.components.ClickSoundPickerLayout
import audio.tembra.ui.metronome.components.MetronomeDefaults
import audio.tembra.ui.metronome.components.SectionLabel
import audio.tembra.ui.metronome.components.StyleRow
import audio.tembra.ui.metronome.components.SubdivisionChips
import audio.tembra.ui.metronome.components.TimeSignatureChips
import audio.tembra.ui.theme.TembraShapes
import audio.tembra.ui.theme.TembraTheme

/**
 * The metronome's settings as section cards in the design's order (contracts/metronome-screen.md, research R10): the
 * time signature with its "Common & Complex" caption, the subdivision, the beat cells with the style row, and the click
 * sound picker. Every layout stacks the same panel; [signatureColumns] is six on a phone and three in the landscape
 * column, and [pickerLayout] is the centred pill ([ClickSoundPickerLayout.Dropdown]) everywhere but the expanded
 * layout's tiles, which sit in their own "Click timbre" card as in the tablet design.
 */
@Composable
internal fun MetronomeSettingsPanel(
    state: MetronomeUiState,
    callbacks: MetronomeCallbacks,
    telemetry: BeatTelemetry,
    pickerLayout: ClickSoundPickerLayout,
    signatureColumns: Int,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TembraTheme.spacing.md)) {
        SectionCard {
            SectionHeader(label = MetronomeCopy.timeSignature, caption = MetronomeCopy.commonAndComplex)
            TimeSignatureChips(
                selected = settings.signature,
                onSelect = callbacks.onSignature,
                modifier = Modifier.fillMaxWidth(),
                columns = signatureColumns,
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
            AccentCells(
                pattern = settings.pattern,
                running = state.running,
                currentBeat = state.currentBeat,
                telemetry = telemetry,
                onCycle = callbacks.onCycleBeat,
            )
            StyleRow(selected = settings.style, onSelect = callbacks.onStyle, modifier = Modifier.fillMaxWidth())
        }
        when (pickerLayout) {
            ClickSoundPickerLayout.Dropdown -> SoundPicker(state, callbacks, pickerLayout, Modifier.align(Alignment.CenterHorizontally))
            ClickSoundPickerLayout.Tiles -> SectionCard {
                SectionHeader(label = MetronomeCopy.clickTimbre)
                SoundPicker(state, callbacks, pickerLayout, Modifier.fillMaxWidth())
            }
        }
    }
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
private fun SectionCard(gap: Dp = MetronomeDefaults.SectionGap, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
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
