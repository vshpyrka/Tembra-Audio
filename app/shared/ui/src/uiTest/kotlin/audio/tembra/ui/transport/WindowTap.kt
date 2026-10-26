package audio.tembra.ui.transport

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction

/**
 * A tap at the centre of [node] delivered the way a finger's is, through the window system, so that an open focusable
 * pop-up above [node] receives it as a tap outside itself. Compose's own test input on Android goes straight to the
 * node's compose view and never passes the pop-up's window, so the Android actual injects the tap through the window
 * manager; on iOS the test input already enters the scene above its pop-up layers.
 */
@OptIn(ExperimentalTestApi::class)
internal expect fun ComposeUiTest.tapThroughTheWindow(node: SemanticsNodeInteraction)
