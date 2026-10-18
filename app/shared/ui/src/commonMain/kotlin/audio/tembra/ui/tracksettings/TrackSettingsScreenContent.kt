package audio.tembra.ui.tracksettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import audio.tembra.ui.session.TransportTelemetry
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.transport.TransportBar
import audio.tembra.ui.transport.TransportButton
import audio.tembra.ui.transport.TransportCallbacks

/**
 * The nested track-settings entry on compact windows (contracts/timeline-screen.md): a header with the back control
 * and the track's name as the title, the form, and the transport bar at the bottom so playback stays in reach
 * (spec FR-041). Root tag `screen.trackSettings`.
 */
@Composable
internal fun TrackSettingsScreenContent(
    state: TrackSettingsUiState,
    callbacks: TrackSettingsCallbacks,
    transportCallbacks: TransportCallbacks,
    telemetry: TransportTelemetry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val daw = TembraTheme.daw
    Column(modifier = modifier.fillMaxSize().background(daw.canvasLevel0).testTag(TrackSettingsTestTags.ROOT)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(spacing.toolbarHeight).padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(icon = TembraIcon.ArrowLeft, description = TrackSettingsCopy.back, onClick = onBack, testTag = TrackSettingsTestTags.CLOSE)
            Text(
                text = state.name,
                style = TembraTheme.typography.titleLarge,
                color = TembraTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = spacing.sm).testTag(TrackSettingsTestTags.TITLE),
            )
        }
        TrackSettingsForm(
            state = state,
            callbacks = callbacks,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
        TransportBar(state = state.transport, callbacks = transportCallbacks, telemetry = telemetry)
    }
}
