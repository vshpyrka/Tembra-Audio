package audio.tembra.ui.tracksettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy

/** The "Effects" section of the track settings (spec FR-048; User Story 6): a heading and the one sentence, no controls. */
@Composable
internal fun EffectsPlaceholder(modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    Column(modifier = modifier.fillMaxWidth().testTag(TrackSettingsTestTags.EFFECTS)) {
        Text(TimelineCopy.effects, style = TembraTheme.typography.titleMedium, color = TembraTheme.colors.onSurface)
        Text(
            TimelineCopy.effectsLater,
            style = TembraTheme.typography.bodyMedium,
            color = TembraTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs).testTag(TrackSettingsTestTags.EFFECTS_TEXT),
        )
    }
}
