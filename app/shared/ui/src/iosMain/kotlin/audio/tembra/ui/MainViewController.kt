package audio.tembra.ui

import androidx.compose.ui.window.ComposeUIViewController
import audio.tembra.ui.devtools.DeveloperViewRequest
import audio.tembra.ui.tuner.TunerTestSignalRequest
import dev.zacsweers.metro.createGraph
import platform.UIKit.UIViewController

private val appGraph: IosAppGraph = createGraph<IosAppGraph>()

/**
 * Entry point called from the Swift shell (`MainViewControllerKt.MainViewController(developerView:tunerTestSignal:)`);
 * [developerView] is the value of the `--tembra-developer-view=` launch argument (feature 002, spec FR-022) and
 * [tunerTestSignal] of `--tembra-tuner-test-signal=` (feature 003, spec FR-032). The Swift side reads both only in Debug.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(developerView: String? = null, tunerTestSignal: String? = null): UIViewController =
    ComposeUIViewController { App(appGraph, DeveloperViewRequest.parse(developerView), TunerTestSignalRequest.parse(tunerTestSignal)) }
