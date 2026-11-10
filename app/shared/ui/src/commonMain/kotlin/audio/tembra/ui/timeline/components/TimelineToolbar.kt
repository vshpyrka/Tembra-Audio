package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GridSize
import audio.tembra.core.recording.MonitorState
import audio.tembra.ui.designsystem.MenuItemSpec
import audio.tembra.ui.designsystem.TembraMenu
import audio.tembra.ui.mixer.MixerCopy
import audio.tembra.ui.mixer.MixerTestTags
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.FullScreenItem
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
 * (the device preference lives nowhere else). Since feature 010 the input monitoring toggle follows the scissors in the
 * full row and is a checked "Monitor input" row in the compact overflow (spec FR-016). "Overview strip" is checked while the strip shows and a tap flips it
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
    onFullScreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Input monitoring (feature 010, spec FR-016): the toggle after the scissors from 600 dp, a checked overflow row below. */
    onMonitorToggle: () -> Unit = {},
    /** The mixer (feature 012, spec FR-017): a toggle after redo at every width, mirrored by a checked row in the compact overflow. */
    onMixerToggle: () -> Unit = {},
    /** The mixer sheet hands the keyboard focus back to the Mixer button when it closes (feature 012, T092). */
    mixerFocusRequester: FocusRequester? = null,
) {
    val spacing = TembraTheme.spacing
    val full = layout == TimelineLayout.Expanded || layout == TimelineLayout.Medium
    var overflowOpen by remember { mutableStateOf(false) }
    val overviewStrip = MenuItemSpec(OVERVIEW_STRIP, TimelineCopy.overviewStrip, TembraIcon.SwapHoriz, selected = state.overviewStripShown)
    // Feature 011 (spec FR-010a): last in the menu, only where the platform offers app-requested full screen.
    val fullScreen = when (state.fullScreenItem) {
        FullScreenItem.Hidden -> null
        FullScreenItem.Enter -> MenuItemSpec(FULL_SCREEN, TimelineCopy.fullScreen, TembraIcon.Fullscreen)
        FullScreenItem.Exit -> MenuItemSpec(FULL_SCREEN, TimelineCopy.exitFullScreen, TembraIcon.FullscreenExit)
    }
    val overflowItems = if (full) {
        listOfNotNull(overviewStrip, fullScreen)
    } else {
        listOfNotNull(
            MenuItemSpec(ADD_AUDIO, TimelineCopy.addAudio, TembraIcon.AudioFile, enabled = state.canImport),
            MenuItemSpec(ADD_AUDIO_NEW, TimelineCopy.addAudioAsNewTrack, TembraIcon.PlaylistAdd, enabled = state.canImport),
            MenuItemSpec(SNAP, TimelineCopy.snapEntry(state.grid.label), TembraIcon.GridOn, enabled = state.loaded),
            MenuItemSpec(MONITOR, TimelineCopy.monitorInput, TembraIcon.Headphones, enabled = state.loaded, selected = state.monitor != MonitorState.Off),
            MenuItemSpec(MIXER, MixerCopy.mixer, TembraIcon.InstantMix, enabled = state.loaded, selected = state.mixer.open),
            overviewStrip,
            MenuItemSpec(METRONOME, TimelineCopy.metronomeSettings, TembraIcon.Metronome, enabled = state.loaded),
            fullScreen,
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
        if (full) MonitorToggle(state = state.monitor, onToggle = onMonitorToggle, enabled = state.loaded)
        TransportButton(icon = TembraIcon.Undo, description = TimelineCopy.undo, onClick = onUndo, enabled = state.canUndo, testTag = TimelineTestTags.UNDO)
        TransportButton(icon = TembraIcon.Redo, description = TimelineCopy.redo, onClick = onRedo, enabled = state.canRedo, testTag = TimelineTestTags.REDO)
        // The mixer's toggle (feature 012, spec FR-017): after redo at every width; the compact tail lets it yield to the pill.
        val mixerButton: @Composable () -> Unit = {
            TransportButton(
                icon = TembraIcon.InstantMix,
                description = MixerCopy.mixer,
                onClick = onMixerToggle,
                modifier = if (mixerFocusRequester != null) Modifier.focusRequester(mixerFocusRequester) else Modifier,
                enabled = state.loaded,
                selected = state.mixer.open,
                testTag = MixerTestTags.TOOLBAR_BUTTON,
            )
        }
        val overflowMenu: @Composable () -> Unit = {
            TembraMenu(
                expanded = overflowOpen,
                onDismiss = { overflowOpen = false },
                items = overflowItems,
                onSelect = { id ->
                    when (id) {
                        ADD_AUDIO -> onAddAudio()
                        ADD_AUDIO_NEW -> onAddAudioAsNewTrack()
                        SNAP -> onSnapSheet()
                        MONITOR -> onMonitorToggle()
                        MIXER -> onMixerToggle()
                        OVERVIEW_STRIP -> onOverviewStripShown(!state.overviewStripShown)
                        METRONOME -> onMetronomeSettings()
                        FULL_SCREEN -> onFullScreen(state.fullScreenItem == FullScreenItem.Enter)
                    }
                },
                testTag = TimelineTestTags.OVERFLOW,
            ) {
                TransportButton(icon = TembraIcon.MoreVert, description = MORE, onClick = { overflowOpen = true }, testTag = TimelineTestTags.OVERFLOW_BUTTON)
            }
        }
        if (full) {
            mixerButton()
            Spacer(Modifier.weight(1f))
            GridPicker(grid = state.grid, onGrid = onGrid)
            TempoPill(tempo = state.tempo, onClick = onTempo, enabled = state.canEditTempo)
            TransportButton(icon = TembraIcon.Metronome, description = TimelineCopy.metronomeSettings, onClick = onMetronomeSettings, enabled = state.loaded, testTag = TimelineTestTags.METRONOME_SETTINGS)
            overflowMenu()
        } else {
            CompactTail(
                mixer = mixerButton,
                pill = { TempoPill(tempo = state.tempo, onClick = onTempo, enabled = state.canEditTempo) },
                overflow = overflowMenu,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The compact row's tail: the Mixer button, the tempo pill and the overflow button (feature 012, spec FR-017, FR-026).
 * The pill takes its own width (125 dp at twice the font size on a 360 dp phone, feature 011's rule) and the Mixer
 * button yields to it: when the three do not fit the room, the button stays out of the row and the overflow's Mixer row
 * serves. Decided in the layout phase from the pill's intrinsic width, so no width or font-size threshold is hard-coded.
 */
@Composable
private fun CompactTail(
    mixer: @Composable () -> Unit,
    pill: @Composable () -> Unit,
    overflow: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(mixer, pill, overflow), modifier = modifier) { (mixerMeasurables, pillMeasurables, overflowMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val overflowPlaceable = overflowMeasurables.first().measure(loose)
        val pillWidth = pillMeasurables.first().maxIntrinsicWidth(constraints.maxHeight)
        val mixerWidth = mixerMeasurables.first().maxIntrinsicWidth(constraints.maxHeight)
        val room = constraints.maxWidth - overflowPlaceable.width
        val mixerPlaceable = if (room - pillWidth >= mixerWidth) mixerMeasurables.first().measure(loose) else null
        val pillPlaceable = pillMeasurables.first().measure(loose.copy(maxWidth = (room - (mixerPlaceable?.width ?: 0)).coerceAtLeast(0)))
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else maxOf(overflowPlaceable.height, pillPlaceable.height, mixerPlaceable?.height ?: 0)
        layout(constraints.maxWidth, height) {
            mixerPlaceable?.placeRelative(0, (height - mixerPlaceable.height) / 2)
            pillPlaceable.placeRelative(constraints.maxWidth - overflowPlaceable.width - pillPlaceable.width, (height - pillPlaceable.height) / 2)
            overflowPlaceable.placeRelative(constraints.maxWidth - overflowPlaceable.width, (height - overflowPlaceable.height) / 2)
        }
    }
}

private const val MORE = "More"
private const val ADD_AUDIO = "addAudio"
private const val ADD_AUDIO_NEW = "addAudioNewTrack"
private const val SNAP = "snap"
/** The row id: tagged [TimelineTestTags.OVERFLOW_MONITOR]. */
private const val MONITOR = "monitor"
/** The row id: tagged [MixerTestTags.OVERFLOW_BUTTON] (feature 012). */
private const val MIXER = "mixer"
private const val METRONOME = "metronomeSettings"
/** The row id: tagged [TimelineTestTags.OVERFLOW_OVERVIEW_STRIP]. */
private const val OVERVIEW_STRIP = "overviewStrip"
/** The row id: tagged [TimelineTestTags.OVERFLOW_FULL_SCREEN]. */
private const val FULL_SCREEN = "fullScreen"
