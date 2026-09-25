package audio.tembra.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/** The Timeline destination; a placeholder until its feature lands (spec FR-013). */
@Composable
fun TimelineScreen(modifier: Modifier = Modifier) = PlaceholderDestination(Destination.Timeline, modifier)
