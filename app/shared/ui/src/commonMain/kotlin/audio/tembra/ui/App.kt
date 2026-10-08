package audio.tembra.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import audio.tembra.engine.TestSignal
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.metronome.LocalMetronomeDebug
import audio.tembra.ui.metronome.MetronomeDebugRequest
import audio.tembra.ui.navigation.NavigationShell
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tuner.LocalTunerNoiseGate
import audio.tembra.ui.tuner.LocalTunerTestSignal
import audio.tembra.ui.tuner.TunerNoiseGateRequest
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

/**
 * Root composable hosted by both platform shells. [developerView] (feature 002, spec FR-022), [tunerTestSignal]
 * (feature 003, spec FR-032), [tunerNoiseGate] (feature 006, spec FR-023) and [metronomeDebug] (feature 004, spec
 * FR-037) are set only by debug builds from their launch arguments. The root carries [anchorRoot] so the benchmark
 * journeys can reach the perf anchors (feature 007).
 */
@Composable
fun App(
    appGraph: AppGraph,
    developerView: DeveloperViewRequest = DeveloperViewRequest(null),
    tunerTestSignal: TestSignal? = null,
    tunerNoiseGate: TunerNoiseGateRequest? = null,
    metronomeDebug: MetronomeDebugRequest? = null,
) {
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalTunerTestSignal provides tunerTestSignal,
        LocalTunerNoiseGate provides tunerNoiseGate?.gate,
        LocalMetronomeDebug provides metronomeDebug,
    ) {
        TembraTheme {
            Box(Modifier.anchorRoot()) {
                AppContent(developerView) { NavigationShell() }
            }
        }
    }
}
