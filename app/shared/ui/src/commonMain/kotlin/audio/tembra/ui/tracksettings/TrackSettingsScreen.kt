package audio.tembra.ui.tracksettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.core.project.TrackId
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy

/** The nested track-settings entry (contracts/timeline-screen.md); its form, title and transport bar land with User Story 2. */
@Composable
fun TrackSettingsScreen(trackId: TrackId, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().testTag(ROOT_TAG), contentAlignment = Alignment.Center) {
        Text(text = "${TimelineCopy.trackSettings} ${trackId.value}", style = TembraTheme.typography.bodyMedium, color = TembraTheme.colors.onSurfaceVariant)
    }
}

internal const val ROOT_TAG = "screen.trackSettings"
