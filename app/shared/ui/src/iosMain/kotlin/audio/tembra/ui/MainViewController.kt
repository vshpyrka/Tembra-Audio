package audio.tembra.ui

import androidx.compose.ui.window.ComposeUIViewController
import dev.zacsweers.metro.createGraph
import platform.UIKit.UIViewController

private val appGraph: IosAppGraph = createGraph<IosAppGraph>()

/** Entry point called from the Swift shell (`MainViewControllerKt.MainViewController()`). */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App(appGraph) }
