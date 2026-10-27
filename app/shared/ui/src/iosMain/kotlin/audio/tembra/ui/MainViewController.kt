package audio.tembra.ui

import androidx.compose.ui.util.enableTraceOSLog
import androidx.compose.ui.window.ComposeUIViewController
import audio.tembra.engine.perf.PerfBuild
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.metronome.MetronomeDebugRequest
import audio.tembra.ui.projects.ProjectsSeedRequest
import audio.tembra.ui.tuner.TunerNoiseGateRequest
import audio.tembra.ui.tuner.TunerTestSignalRequest
import dev.zacsweers.metro.createGraph
import platform.UIKit.UIViewController

private val appGraph: IosAppGraph = createGraph<IosAppGraph>()

/**
 * Entry point called from the Swift shell
 * (`MainViewControllerKt.MainViewController(developerView:tunerTestSignal:tunerNoiseGate:metronomeSyncopation:)`);
 * [developerView] is the value of the `--tembra-developer-view=` launch argument (feature 002, spec FR-022),
 * [tunerTestSignal] of `--tembra-tuner-test-signal=` (feature 003, spec FR-032), [tunerNoiseGate] of
 * `--tembra-tuner-noise-gate=` (feature 006, spec FR-023) and [metronomeSyncopation] of
 * `--tembra-metronome-syncopation=` (feature 004, spec FR-037) and [projectsSeed] of `--tembra-projects-seed=`
 * (feature 009, T121). The Swift side reads all five only in Debug.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(
    developerView: String? = null,
    tunerTestSignal: String? = null,
    tunerNoiseGate: String? = null,
    metronomeSyncopation: String? = null,
    projectsSeed: String? = null,
): UIViewController =
    ComposeUIViewController(configure = {
        // Measurement builds (feature 007, FR-016): Compose's own frame phases become os_signpost intervals.
        if (PerfBuild.PERF_ENABLED) enableTraceOSLog()
    }) {
        App(
            appGraph = appGraph,
            developerView = DeveloperViewRequest.parse(developerView),
            tunerTestSignal = TunerTestSignalRequest.parse(tunerTestSignal),
            tunerNoiseGate = TunerNoiseGateRequest.parse(tunerNoiseGate),
            metronomeDebug = MetronomeDebugRequest.parse(metronomeSyncopation),
            projectsSeed = ProjectsSeedRequest.parse(projectsSeed),
        )
    }
