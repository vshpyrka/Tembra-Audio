package audio.tembra.ui.metronome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/** The Metronome destination; a placeholder until its feature lands (spec FR-013). */
@Composable
fun MetronomeScreen(modifier: Modifier = Modifier) = PlaceholderDestination(Destination.Metronome, modifier)
