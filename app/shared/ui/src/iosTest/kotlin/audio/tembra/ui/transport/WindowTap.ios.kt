package audio.tembra.ui.transport

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick

/** The scene dispatches test input to its top pop-up layer first, as it does a finger's. */
@OptIn(ExperimentalTestApi::class)
internal actual fun ComposeUiTest.tapThroughTheWindow(node: SemanticsNodeInteraction) {
    node.performClick()
    waitForIdle()
}
