package audio.tembra.ui.tracksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import audio.tembra.core.project.TrackId
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TransportButton
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The expanded layout's inline settings under a track's row (spec FR-041): the same form as the nested screen, with a
 * collapse control at the end; a deleted track collapses it. Tagged `timeline.settings.inline`.
 */
@Composable
internal fun TrackSettingsInlineRow(
    trackId: TrackId,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackSettingsViewModel = metroViewModel(),
) {
    DisposableEffect(viewModel, trackId) {
        viewModel.open(trackId)
        viewModel.screenVisible(true)
        onDispose { viewModel.screenVisible(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) {
        if (state.closed) onCollapse()
    }
    val callbacks = remember(viewModel) { trackSettingsCallbacks(viewModel) }
    Column(modifier = modifier.fillMaxWidth().background(TembraTheme.daw.surfaceLevel1).testTag(TrackSettingsTestTags.INLINE)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = TembraTheme.spacing.xs), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TransportButton(icon = TembraIcon.ExpandLess, description = TrackSettingsCopy.collapse, onClick = onCollapse, testTag = TrackSettingsTestTags.CLOSE)
        }
        TrackSettingsForm(state = state, callbacks = callbacks)
    }
}
