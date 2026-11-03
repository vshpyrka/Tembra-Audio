package audio.tembra.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import audio.tembra.engine.TestSignal
import audio.tembra.engine.perf.PerfBuild
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.di.prebuiltForScreens
import audio.tembra.ui.metronome.LocalMetronomeDebug
import audio.tembra.ui.metronome.MetronomeDebugRequest
import audio.tembra.ui.navigation.DestinationsDrawnAhead
import audio.tembra.ui.navigation.NavigationShell
import audio.tembra.ui.perf.AudioHealthTicker
import audio.tembra.ui.perf.AudioHealthWriteButton
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.platform.LocalTrackpadPinchSource
import audio.tembra.ui.projects.ProjectsSeedRequest
import audio.tembra.ui.session.AppLifecycleObserver
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WarmUpText
import audio.tembra.ui.tuner.LocalTunerNoiseGate
import audio.tembra.ui.tuner.LocalTunerTestSignal
import audio.tembra.ui.tuner.TunerNoiseGateRequest
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Root composable hosted by both platform shells. [developerView] (feature 002, spec FR-022), [tunerTestSignal]
 * (feature 003, spec FR-032), [tunerNoiseGate] (feature 006, spec FR-023) and [metronomeDebug] (feature 004, spec
 * FR-037) and [projectsSeed] (feature 009, T121) are set only by debug builds from their launch arguments. The root carries [anchorRoot] so the benchmark
 * journeys can reach the perf anchors (feature 007). The first composition starts the project session once
 * ([AppStartup]) and installs the lifecycle observer that saves on backgrounding (feature 009, spec FR-046).
 */
@Composable
fun App(
    appGraph: AppGraph,
    developerView: DeveloperViewRequest = DeveloperViewRequest(null),
    tunerTestSignal: TestSignal? = null,
    tunerNoiseGate: TunerNoiseGateRequest? = null,
    metronomeDebug: MetronomeDebugRequest? = null,
    projectsSeed: ProjectsSeedRequest? = null,
) {
    LaunchedEffect(appGraph) { appGraph.appStartup.start() }
    // A debug seed (T121) fills the library once; it runs on the session scope, so a recreated activity never repeats it.
    LaunchedEffect(appGraph, projectsSeed) { if (projectsSeed != null) appGraph.projectsSeeder.seed(projectsSeed) }
    AppLifecycleObserver(appGraph.projectSession)
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        LocalTunerTestSignal provides tunerTestSignal,
        LocalTunerNoiseGate provides tunerNoiseGate?.gate,
        LocalMetronomeDebug provides metronomeDebug,
        LocalTrackpadPinchSource provides appGraph.trackpadPinchSource,
    ) {
        // The objects the screens' ViewModels are built from, built off the main thread once the first frame is up, so
        // the frame that opens a screen does not build them (AppGraph.prebuiltForScreens).
        LaunchedEffect(appGraph) {
            withFrameNanos { }
            withContext(Dispatchers.IO) { appGraph.prebuiltForScreens() }
        }
        TembraTheme {
            Box(Modifier.anchorRoot()) {
                // After launch, first the text is laid out and then the Tuner and the Metronome are drawn once ahead
                // (DestinationsDrawnAhead), while the start screen is idle; beneath everything, so nothing of it is seen
                // or touched.
                var textWarm by remember { mutableStateOf(false) }
                if (textWarm) DestinationsDrawnAhead()
                AppContent(developerView) { NavigationShell() }
                WarmUpText(onWarm = { textWarm = true })
                // Measurement builds only (feature 007): the one-second audio-health publisher and the stress journey's
                // "write engine health" control; PERF_ENABLED is a compile-time constant, so release builds carry neither.
                if (PerfBuild.PERF_ENABLED) {
                    AudioHealthTicker(appGraph.audioEngine)
                    AudioHealthWriteButton(
                        engine = appGraph.audioEngine,
                        modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }
}
