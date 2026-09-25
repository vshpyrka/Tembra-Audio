package audio.tembra.ui

import androidx.compose.ui.window.ComposeUIViewController
import audio.tembra.ui.devtools.DeveloperViewRequest
import dev.zacsweers.metro.createGraph
import platform.UIKit.UIViewController

private val appGraph: IosAppGraph = createGraph<IosAppGraph>()

/**
 * Entry point called from the Swift shell (`MainViewControllerKt.MainViewController(developerView:)`); [developerView]
 * is the value of the `--tembra-developer-view=` launch argument, which the Swift side reads only in Debug (spec FR-022).
 */
@Suppress("FunctionName", "unused")
fun MainViewController(developerView: String? = null): UIViewController =
    ComposeUIViewController { App(appGraph, DeveloperViewRequest.parse(developerView)) }
