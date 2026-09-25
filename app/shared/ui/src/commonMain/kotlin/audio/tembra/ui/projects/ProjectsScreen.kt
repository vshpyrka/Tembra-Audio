package audio.tembra.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/** The Projects destination; a placeholder until its feature lands (spec FR-013). */
@Composable
fun ProjectsScreen(modifier: Modifier = Modifier) = PlaceholderDestination(Destination.Projects, modifier)
