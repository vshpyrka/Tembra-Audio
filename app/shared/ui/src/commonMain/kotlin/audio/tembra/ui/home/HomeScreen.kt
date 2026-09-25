package audio.tembra.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination

/** The Home destination; a placeholder until its feature lands (spec FR-011). */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) = PlaceholderDestination(Destination.Home, modifier)
