package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.metronome.BeatTelemetry
import audio.tembra.ui.metronome.CustomSoundState
import audio.tembra.ui.metronome.components.AccentCells
import audio.tembra.ui.metronome.components.ClickSoundPicker
import audio.tembra.ui.metronome.components.ClickSoundPickerLayout
import audio.tembra.ui.metronome.components.StyleMenu
import audio.tembra.ui.metronome.components.SubdivisionTiles
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy

/**
 * The transport's metronome settings (spec FR-038, FR-039): the global metronome's sound, subdivision, accent cells
 * and style, edited here and stored with the Metronome's own preferences; the tempo and signature rows are the
 * project's and read-only. The accent pattern is the global one laid over the project's signature, as the engine
 * plays it. Tagged `timeline.metronomeSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetronomeSettingsSheet(
    settings: MetronomeSettings,
    tempo: Tempo,
    signature: TimeSignature,
    onClickSound: (ClickSound) -> Unit,
    onImportCustomSound: () -> Unit,
    onSubdivision: (Subdivision) -> Unit,
    onCycleBeat: (Int) -> Unit,
    onStyle: (MetronomeStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val pattern = settings.style?.pattern(signature) ?: settings.pattern.resizedTo(signature.beats)
    val customSound = settings.customSound?.let { CustomSoundState.Available(it.displayName) } ?: CustomSoundState.None
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(METRONOME_SHEET_TAG),
        containerColor = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.pageMargin).padding(bottom = spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(TimelineCopy.metronomeSettings, style = TembraTheme.typography.headlineSmall, color = colors.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth().testTag(METRONOME_SHEET_TEMPO_TAG).semantics { contentDescription = "${tempo.bpm} BPM, ${signature.label}" },
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${tempo.bpm} BPM", style = TembraTheme.typographyExtras.tabularParam, color = colors.onSurfaceVariant)
                Text(signature.label, style = TembraTheme.typographyExtras.tabularParam, color = colors.onSurfaceVariant)
                Text(TimelineCopy.fromTheProject, style = TembraTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            ClickSoundPicker(
                sound = settings.sound,
                customSound = customSound,
                layout = ClickSoundPickerLayout.Dropdown,
                onSelect = onClickSound,
                onImportCustom = onImportCustomSound,
                onReplaceCustom = onImportCustomSound,
                testTag = METRONOME_SHEET_SOUND_TAG,
            )
            SubdivisionTiles(selected = settings.subdivision, unit = signature.unit, onSelect = onSubdivision, testTag = METRONOME_SHEET_SUBDIVISION_TAG)
            AccentCells(
                pattern = pattern,
                running = false,
                currentBeat = -1,
                telemetry = BeatTelemetry { -1 },
                onCycle = onCycleBeat,
                modifier = Modifier.fillMaxWidth(),
                testTag = METRONOME_SHEET_CELLS_TAG,
            )
            StyleMenu(selected = settings.style, onSelect = onStyle, testTag = METRONOME_SHEET_STYLE_TAG)
        }
    }
}

internal const val METRONOME_SHEET_TAG = "timeline.metronomeSheet"
internal const val METRONOME_SHEET_TEMPO_TAG = "timeline.metronomeSheet.tempo"
internal const val METRONOME_SHEET_SOUND_TAG = "timeline.metronomeSheet.sound"
internal const val METRONOME_SHEET_SUBDIVISION_TAG = "timeline.metronomeSheet.subdivision"
internal const val METRONOME_SHEET_CELLS_TAG = "timeline.metronomeSheet.cells"
internal const val METRONOME_SHEET_STYLE_TAG = "timeline.metronomeSheet.style"
