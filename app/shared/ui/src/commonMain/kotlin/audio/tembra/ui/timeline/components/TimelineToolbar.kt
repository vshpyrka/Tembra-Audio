package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
 * The 48 dp toolbar above the ruler (contracts/timeline-screen.md "Layouts"; spec FR-036, FR-055, D1). Every width
 * shows "+ Track", the scissors, undo, redo, the tempo pill ([onTempo] opens the tempo dialog) and the overflow
 * button (tagged `timeline.toolbar.overflow.button`). Compact and landscape windows keep "Add audio…", "Add audio as
 * new track", "Snap: <value>" ([onSnapSheet] opens the snap sheet), "Overview strip" and "Metronome settings" behind
 * the overflow; there the targets sit edge to edge and the tempo pill takes the room the buttons leave, right-aligned,
 * so it stays whole and the overflow button keeps its 44 dp at twice the font size on a 360 dp phone (FR-055). Medium
 * and expanded windows show the full row: "Add audio…" and "Add audio as new track" after "+ Track" and, at the right
 * end, the "Snap" pill, the tempo pill and the metronome settings button, with only "Overview strip" in the overflow
 * (the device preference lives nowhere else). "Overview strip" is checked while the strip shows and a tap flips it
 * through [onOverviewStripShown] (FR-015; T151). The scissors are enabled only while a split is possible (spec
 * FR-032), undo and redo while the history allows (FR-035).
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
    onTempo: () -> Unit,
    onGrid: (GridSize) -> Unit,
    onSnapSheet: () -> Unit,
    onOverviewStripShown: (shown: Boolean) -> Unit,
    onMetronomeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val full = layout == TimelineLayout.Expanded || layout == TimelineLayout.Medium
    var overflowOpen by remember { mutableStateOf(false) }
    val overviewStrip = MenuItemSpec(OVERVIEW_STRIP, TimelineCopy.overviewStrip, TembraIcon.SwapHoriz, selected = state.overviewStripShown)
    val overflowItems = if (full) {
        listOf(overviewStrip)
    } else {
        listOf(
            MenuItemSpec(ADD_AUDIO, TimelineCopy.addAudio, TembraIcon.AudioFile, enabled = state.canImport),
            MenuItemSpec(ADD_AUDIO_NEW, TimelineCopy.addAudioAsNewTrack, TembraIcon.PlaylistAdd, enabled = state.canImport),
            MenuItemSpec(SNAP, TimelineCopy.snapEntry(state.grid.label), TembraIcon.GridOn, enabled = state.loaded),
            overviewStrip,
            MenuItemSpec(METRONOME, TimelineCopy.metronomeSettings, TembraIcon.Metronome, enabled = state.loaded),
        )
    }
    Row(
        modifier = modifier.fillMaxWidth().height(spacing.toolbarHeight).padding(horizontal = spacing.xs).testTag(TimelineTestTags.TOOLBAR),
        // The compact row sets its 44 dp targets edge to edge (their glyphs stay 20 dp apart): the room is the tempo
        // pill's, which needs 125 dp at twice the font size on a 360 dp phone.
        horizontalArrangement = Arrangement.spacedBy(if (full) spacing.xs else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(icon = TembraIcon.Add, description = TimelineCopy.addTrack, onClick = onAddTrack, enabled = state.canAddTrack, testTag = TimelineTestTags.ADD_TRACK)
        if (full) {
            TransportButton(icon = TembraIcon.AudioFile, description = TimelineCopy.addAudio, onClick = onAddAudio, enabled = state.canImport, testTag = TimelineTestTags.ADD_AUDIO)
            TransportButton(icon = TembraIcon.PlaylistAdd, description = TimelineCopy.addAudioAsNewTrack, onClick = onAddAudioAsNewTrack, enabled = state.canImport, testTag = TimelineTestTags.ADD_AUDIO_NEW_TRACK)
        }
        TransportButton(icon = TembraIcon.ContentCut, description = TimelineCopy.split, onClick = onSplit, enabled = state.canSplit, testTag = TimelineTestTags.SPLIT)
        TransportButton(icon = TembraIcon.Undo, description = TimelineCopy.undo, onClick = onUndo, enabled = state.canUndo, testTag = TimelineTestTags.UNDO)
        TransportButton(icon = TembraIcon.Redo, description = TimelineCopy.redo, onClick = onRedo, enabled = state.canRedo, testTag = TimelineTestTags.REDO)
        if (full) {
            Spacer(Modifier.weight(1f))
            GridPicker(grid = state.grid, onGrid = onGrid)
            TempoPill(tempo = state.tempo, onClick = onTempo, enabled = state.canEditTempo)
            TransportButton(icon = TembraIcon.Metronome, description = TimelineCopy.metronomeSettings, onClick = onMetronomeSettings, enabled = state.loaded, testTag = TimelineTestTags.METRONOME_SETTINGS)
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TempoPill(tempo = state.tempo, onClick = onTempo, enabled = state.canEditTempo)
            }
        }
        TembraMenu(
            expanded = overflowOpen,
            onDismiss = { overflowOpen = false },
            items = overflowItems,
            onSelect = { id ->
                when (id) {
                    ADD_AUDIO -> onAddAudio()
                    ADD_AUDIO_NEW -> onAddAudioAsNewTrack()
                    SNAP -> onSnapSheet()
                    OVERVIEW_STRIP -> onOverviewStripShown(!state.overviewStripShown)
                    METRONOME -> onMetronomeSettings()
                }
            },
            testTag = TimelineTestTags.OVERFLOW,
        ) {
            TransportButton(icon = TembraIcon.MoreVert, description = MORE, onClick = { overflowOpen = true }, testTag = TimelineTestTags.OVERFLOW_BUTTON)
        }
    }
}

private const val MORE = "More"
private const val ADD_AUDIO = "addAudio"
private const val ADD_AUDIO_NEW = "addAudioNewTrack"
private const val SNAP = "snap"
private const val METRONOME = "metronomeSettings"
/** The row id: tagged [TimelineTestTags.OVERFLOW_OVERVIEW_STRIP]. */
private const val OVERVIEW_STRIP = "overviewStrip"
