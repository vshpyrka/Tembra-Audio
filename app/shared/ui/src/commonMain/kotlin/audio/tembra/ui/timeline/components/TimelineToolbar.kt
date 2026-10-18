package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.core.project.GridSize
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.transport.TransportButton

/**
 * The 48 dp toolbar above the ruler (contracts/timeline-screen.md "Layouts"): "+ Track", "Add audio…", "Add audio as
 * new track", the scissors, undo and redo, the grid picker and the zoom readout. Compact and landscape windows keep
 * "+ Track", the scissors, undo and redo in the row and put the rest behind the overflow menu (tagged
 * `timeline.toolbar.overflow`); wider windows show everything. The scissors are enabled only while a split is possible
 * (spec FR-032), undo and redo while the history allows (FR-035).
 */
@Composable
internal fun TimelineToolbar(
    state: TimelineUiState,
    layout: TimelineLayout,
    onAddTrack: () -> Unit,
    onAddAudio: () -> Unit,
    onAddAudioAsNewTrack: () -> Unit,
    onSplit: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onGrid: (GridSize) -> Unit,
    onFitProject: () -> Unit,
    onZoomStep: (Int) -> Unit,
    onMetronomeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val full = layout == TimelineLayout.Expanded || layout == TimelineLayout.Medium
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().height(spacing.toolbarHeight).padding(horizontal = spacing.xs).testTag(TimelineTestTags.TOOLBAR),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(icon = TembraIcon.Add, description = TimelineCopy.addTrack, onClick = onAddTrack, enabled = state.loaded, testTag = TimelineTestTags.ADD_TRACK)
        if (full) {
            TransportButton(icon = TembraIcon.AudioFile, description = TimelineCopy.addAudio, onClick = onAddAudio, enabled = state.loaded, testTag = TimelineTestTags.ADD_AUDIO)
            TransportButton(icon = TembraIcon.PlaylistAdd, description = TimelineCopy.addAudioAsNewTrack, onClick = onAddAudioAsNewTrack, enabled = state.loaded, testTag = TimelineTestTags.ADD_AUDIO_NEW_TRACK)
        }
        TransportButton(icon = TembraIcon.ContentCut, description = TimelineCopy.split, onClick = onSplit, enabled = state.canSplit, testTag = TimelineTestTags.SPLIT)
        TransportButton(icon = TembraIcon.Undo, description = TimelineCopy.undo, onClick = onUndo, enabled = state.canUndo, testTag = TimelineTestTags.UNDO)
        TransportButton(icon = TembraIcon.Redo, description = TimelineCopy.redo, onClick = onRedo, enabled = state.canRedo, testTag = TimelineTestTags.REDO)
        Spacer(Modifier.weight(1f))
        if (full) {
            GridPicker(grid = state.grid, onGrid = onGrid)
            Spacer(Modifier.width(spacing.xs))
            ZoomReadout(zoomFactor = zoomFactorOf(state), onFitProject = onFitProject, onZoomStep = onZoomStep)
            TransportButton(icon = TembraIcon.Metronome, description = TimelineCopy.metronomeSettings, onClick = onMetronomeSettings, enabled = state.loaded, testTag = METRONOME_SETTINGS_TAG)
        } else {
            TembraMenu(
                expanded = overflowOpen,
                onDismiss = { overflowOpen = false },
                items = listOf(
                    MenuItemSpec(ADD_AUDIO, TimelineCopy.addAudio, TembraIcon.AudioFile, enabled = state.loaded),
                    MenuItemSpec(ADD_AUDIO_NEW, TimelineCopy.addAudioAsNewTrack, TembraIcon.PlaylistAdd, enabled = state.loaded),
                    MenuItemSpec(FIT, TimelineCopy.fitProject, TembraIcon.ZoomOut, enabled = state.loaded),
                    MenuItemSpec(ZOOM_IN, "Zoom in", TembraIcon.ZoomIn, enabled = state.loaded),
                    MenuItemSpec(ZOOM_OUT, "Zoom out", TembraIcon.ZoomOut, enabled = state.loaded),
                    MenuItemSpec(METRONOME, TimelineCopy.metronomeSettings, TembraIcon.Metronome, enabled = state.loaded),
                ) + GridSize.entries.map { MenuItemSpec("$GRID_PREFIX${it.name.lowercase()}", "${TimelineCopy.grid} ${it.label}", TembraIcon.GridOn, enabled = state.loaded) },
                onSelect = { id ->
                    when {
                        id == ADD_AUDIO -> onAddAudio()
                        id == ADD_AUDIO_NEW -> onAddAudioAsNewTrack()
                        id == FIT -> onFitProject()
                        id == ZOOM_IN -> onZoomStep(1)
                        id == ZOOM_OUT -> onZoomStep(-1)
                        id == METRONOME -> onMetronomeSettings()
                        id.startsWith(GRID_PREFIX) -> GridSize.entries.firstOrNull { it.name.lowercase() == id.removePrefix(GRID_PREFIX) }?.let(onGrid)
                    }
                },
                testTag = TimelineTestTags.OVERFLOW,
            ) {
                TransportButton(icon = TembraIcon.MoreVert, description = MORE, onClick = { overflowOpen = true }, testTag = "${TimelineTestTags.OVERFLOW}.button")
            }
        }
    }
}

/** The zoom relative to the widest level: 1× at fit, larger closer in. */
internal fun zoomFactorOf(state: TimelineUiState): Double {
    val port = state.viewport
    if (port.widthPx <= 0.0) return 1.0
    return (port.pixelsPerBeat / port.fitPixelsPerBeat).coerceAtLeast(1.0)
}

internal const val METRONOME_SETTINGS_TAG = "timeline.toolbar.metronomeSettings"
private const val MORE = "More"
private const val ADD_AUDIO = "addAudio"
private const val ADD_AUDIO_NEW = "addAudioNewTrack"
private const val FIT = "fit"
private const val ZOOM_IN = "zoomIn"
private const val ZOOM_OUT = "zoomOut"
private const val METRONOME = "metronomeSettings"
private const val GRID_PREFIX = "grid."
