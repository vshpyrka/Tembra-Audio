package audio.tembra.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.navigation.NavigationShell
import audio.tembra.ui.theme.TembraTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/** Root composable hosted by both platform shells; [developerView] is set only by debug builds (spec FR-022). */
@Composable
fun App(appGraph: AppGraph, developerView: DeveloperViewRequest = DeveloperViewRequest(null)) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
        TembraTheme {
            AppContent(developerView) { NavigationShell() }
        }
    }
}
