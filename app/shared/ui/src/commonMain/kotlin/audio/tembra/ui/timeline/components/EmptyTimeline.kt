package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/** The invitation of an empty project (contracts/timeline-screen.md): "No tracks yet", one sentence, and the two ways to begin. */
@Composable
internal fun EmptyTimeline(onAddTrack: () -> Unit, onAddAudio: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TembraTheme.colors
    val spacing = TembraTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.pageMargin).testTag(TimelineTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(TimelineCopy.noTracksYet, style = TembraTheme.typography.headlineSmall, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(
            TimelineCopy.addToBegin,
            style = TembraTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )
        Row(modifier = Modifier.padding(top = spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TextButton(onClick = onAddTrack, modifier = Modifier.testTag("${TimelineTestTags.EMPTY}.addTrack")) { Text(TimelineCopy.addTrack, color = colors.primary) }
            TextButton(onClick = onAddAudio, modifier = Modifier.testTag("${TimelineTestTags.EMPTY}.addAudio")) { Text(TimelineCopy.addAudio, color = colors.primary) }
        }
    }
}
