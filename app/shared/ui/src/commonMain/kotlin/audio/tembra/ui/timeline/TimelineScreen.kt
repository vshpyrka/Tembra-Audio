package audio.tembra.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.perf.ReportFirstContentDrawn

/**
 * The Timeline destination; a placeholder until its feature lands (spec FR-013). It is the start destination now that
 * Home is gone, and its content is static, so it is fully drawn at once (feature 007).
 */
@Composable
fun TimelineScreen(modifier: Modifier = Modifier) {
    ReportFirstContentDrawn(ready = true)
    PlaceholderDestination(Destination.Timeline, modifier)
}
