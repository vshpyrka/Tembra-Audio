package audio.tembra.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import audio.tembra.engine.TestSignal
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.navigation.NavigationShell
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tuner.LocalTunerTestSignal
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/**
 * Root composable hosted by both platform shells. [developerView] (feature 002, spec FR-022) and [tunerTestSignal]
 * (feature 003, spec FR-032) are set only by debug builds from their launch arguments.
 */
@Composable
fun App(appGraph: AppGraph, developerView: DeveloperViewRequest = DeveloperViewRequest(null), tunerTestSignal: TestSignal? = null) {
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalTunerTestSignal provides tunerTestSignal,
    ) {
        TembraTheme {
            AppContent(developerView) { NavigationShell() }
        }
    }
}
