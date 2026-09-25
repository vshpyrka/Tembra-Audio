package audio.tembra.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/** The Profile destination; a placeholder until its feature lands (spec FR-013). */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) = PlaceholderDestination(Destination.Profile, modifier)
