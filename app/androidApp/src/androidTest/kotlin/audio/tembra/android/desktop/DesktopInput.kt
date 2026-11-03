package audio.tembra.android.desktop

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Real mouse and keyboard input on the desktop emulator (feature 011, research R14): mouse events with
 * `SOURCE_MOUSE` and the mouse tool type injected through [android.app.UiAutomation.injectInputEvent] (screen
 * coordinates in pixels), key combinations through `input keycombination`. These reach the window the way a USB or
 * Bluetooth mouse does, so hover, the secondary button and the wheel go through the platform's own dispatch.
 */
object DesktopInput {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /**
     * Moves the hovering pointer to ([x], [y]) with no button pressed, in two steps so the window sees movement. The
     * platform refuses injected hover enter and exit events: it derives them from the moves itself.
     */
    fun hover(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        inject(mouseEvent(now, now, MotionEvent.ACTION_HOVER_MOVE, x - 1f, y - 1f, buttons = 0))
        inject(mouseEvent(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_HOVER_MOVE, x, y, buttons = 0))
        instrumentation.waitForIdleSync()
    }

    /** Presses and releases the secondary (right) button at ([x], [y]). */
    fun secondaryClick(x: Float, y: Float) {
        click(x, y, MotionEvent.BUTTON_SECONDARY)
    }

    /** Presses and releases the primary (left) button at ([x], [y]). */
    fun primaryClick(x: Float, y: Float) {
        click(x, y, MotionEvent.BUTTON_PRIMARY)
    }

    /**
     * Presses the primary button at ([fromX], [fromY]), moves to ([toX], [toY]) in [steps] moves and, unless [release]
     * is false, releases there. With `release = false` the button stays down so a test can inspect the dragging state;
     * finish it with [releasePrimary].
     */
    fun primaryDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int = 12, release: Boolean = true) {
        val down = SystemClock.uptimeMillis()
        inject(mouseEvent(down, down, MotionEvent.ACTION_DOWN, fromX, fromY, MotionEvent.BUTTON_PRIMARY))
        inject(mouseEvent(down, down, MotionEvent.ACTION_BUTTON_PRESS, fromX, fromY, MotionEvent.BUTTON_PRIMARY, actionButton = MotionEvent.BUTTON_PRIMARY))
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            val x = fromX + (toX - fromX) * fraction
            val y = fromY + (toY - fromY) * fraction
            SystemClock.sleep(16)
            inject(mouseEvent(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, MotionEvent.BUTTON_PRIMARY))
        }
        dragDownTime = down
        if (release) releasePrimary(toX, toY)
        instrumentation.waitForIdleSync()
    }

    private var dragDownTime = 0L

    /** Releases the primary button held by a [primaryDrag] started with `release = false`. */
    fun releasePrimary(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        inject(mouseEvent(dragDownTime, now, MotionEvent.ACTION_BUTTON_RELEASE, x, y, buttons = 0, actionButton = MotionEvent.BUTTON_PRIMARY))
        inject(mouseEvent(dragDownTime, now, MotionEvent.ACTION_UP, x, y, buttons = 0))
        instrumentation.waitForIdleSync()
    }

    /**
     * Turns the wheel at ([x], [y]): [vertical] notches (positive = away from the user, scrolling up) and [horizontal]
     * notches (positive = right), as `AXIS_VSCROLL` / `AXIS_HSCROLL` of one `ACTION_SCROLL`.
     */
    fun scroll(x: Float, y: Float, vertical: Float = 0f, horizontal: Float = 0f) {
        val now = SystemClock.uptimeMillis()
        val event = mouseEvent(now, now, MotionEvent.ACTION_SCROLL, x, y, buttons = 0, vscroll = vertical, hscroll = horizontal)
        inject(event)
        instrumentation.waitForIdleSync()
    }

    /** Presses a key combination, e.g. `keyCombination("KEYCODE_CTRL_LEFT", "KEYCODE_2")`. */
    fun keyCombination(vararg keyCodes: String) {
        DesktopWindow.shell("input keycombination ${keyCodes.joinToString(" ")}")
        instrumentation.waitForIdleSync()
    }

    /** Presses one key, e.g. `key("KEYCODE_ESCAPE")`. */
    fun key(keyCode: String) {
        DesktopWindow.shell("input keyevent $keyCode")
        instrumentation.waitForIdleSync()
    }

    /**
     * The pointer icon the window shows at the last hover position: Compose sets it on its root view, which the
     * platform's `View.onResolvePointerIcon` returns (research R7).
     */
    fun resolvedPointerIcon(activity: Activity, x: Float, y: Float): PointerIcon? {
        var icon: PointerIcon? = null
        instrumentation.runOnMainSync {
            val root = composeRoot(activity.window.decorView) ?: activity.window.decorView
            val location = IntArray(2).also(root::getLocationOnScreen)
            val now = SystemClock.uptimeMillis()
            val event = mouseEvent(now, now, MotionEvent.ACTION_HOVER_MOVE, x - location[0], y - location[1], buttons = 0)
            icon = root.onResolvePointerIcon(event, 0)
            event.recycle()
        }
        return icon
    }

    /** The system pointer icon of [type] (`PointerIcon.TYPE_*`), for comparisons with [resolvedPointerIcon]. */
    fun systemIcon(activity: Activity, type: Int): PointerIcon = PointerIcon.getSystemIcon(activity, type)

    private fun composeRoot(view: View): View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) composeRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun click(x: Float, y: Float, button: Int) {
        val down = SystemClock.uptimeMillis()
        inject(mouseEvent(down, down, MotionEvent.ACTION_DOWN, x, y, button))
        inject(mouseEvent(down, down, MotionEvent.ACTION_BUTTON_PRESS, x, y, button, actionButton = button))
        SystemClock.sleep(40)
        val up = SystemClock.uptimeMillis()
        inject(mouseEvent(down, up, MotionEvent.ACTION_BUTTON_RELEASE, x, y, buttons = 0, actionButton = button))
        inject(mouseEvent(down, up, MotionEvent.ACTION_UP, x, y, buttons = 0))
        instrumentation.waitForIdleSync()
    }

    private fun inject(event: MotionEvent) {
        check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "the platform refused the injected $event" }
        event.recycle()
    }

    private fun mouseEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        buttons: Int,
        actionButton: Int = 0,
        vscroll: Float = 0f,
        hscroll: Float = 0f,
    ): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = if (buttons != 0) 1f else 0f
            size = 1f
            setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll)
            setAxisValue(MotionEvent.AXIS_HSCROLL, hscroll)
        }
        val event = MotionEvent.obtain(
            downTime, eventTime, action, 1, arrayOf(properties), arrayOf(coords),
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        if (actionButton != 0) setActionButton(event, actionButton)
        return event
    }

    /** `MotionEvent.setActionButton` is hidden; BUTTON_PRESS / BUTTON_RELEASE need it for Compose to see the button. */
    private fun setActionButton(event: MotionEvent, button: Int) {
        MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType).invoke(event, button)
    }
}
