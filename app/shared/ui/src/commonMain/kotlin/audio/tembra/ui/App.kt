package audio.tembra.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.placeholder.PlaceholderScreen
import audio.tembra.ui.theme.TembraTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/** Root composable hosted by both platform shells. */
@Composable
fun App(appGraph: AppGraph) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
        TembraTheme {
            PlaceholderScreen()
        }
    }
}
