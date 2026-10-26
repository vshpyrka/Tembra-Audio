package audio.tembra.ui.transport

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.platform.app.InstrumentationRegistry

/** A down and an up at the node's centre in screen coordinates, injected like a finger's (the test thread, not the main one). */
@OptIn(ExperimentalTestApi::class)
internal actual fun ComposeUiTest.tapThroughTheWindow(node: SemanticsNodeInteraction) {
    val semantics = node.fetchSemanticsNode()
    val x = semantics.positionOnScreen.x + semantics.size.width / 2f
    val y = semantics.positionOnScreen.y + semantics.size.height / 2f
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val downTime = SystemClock.uptimeMillis()
    for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.sendPointerSync(event)
    }
    waitForIdle()
}
