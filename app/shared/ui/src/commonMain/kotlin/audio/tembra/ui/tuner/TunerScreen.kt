package audio.tembra.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/** The Tuner destination; a placeholder until its feature lands (spec FR-013). */
@Composable
fun TunerScreen(modifier: Modifier = Modifier) = PlaceholderDestination(Destination.Tuner, modifier)
