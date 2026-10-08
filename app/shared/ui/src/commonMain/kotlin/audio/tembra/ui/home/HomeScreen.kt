package audio.tembra.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.perf.ReportFirstContentDrawn

/** The Home destination; a placeholder until its feature lands (spec FR-011). Its content is static, so it is fully drawn at once (feature 007). */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    ReportFirstContentDrawn(ready = true)
    PlaceholderDestination(Destination.Home, modifier)
}
