package audio.tembra.perf.journeys

import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import audio.tembra.perf.journeys.Anchors.anchor
import audio.tembra.perf.journeys.Anchors.tap

/**
 * The windowing journey (feature 011, contracts/perf-instrumentation.md): open Metronome and start it; change the window
 * across the 600 and 840 dp width classes and back three times (a free-form window is resized with `am task resize`, a
 * phone rotates); stop; open the demo project, play, change the window once more, right-click the first clip and dismiss
 * its menu, pause, press Ctrl+2 then Ctrl+1. The pause comes before the destination chords (the contract's order would
 * leave a playing Timeline, which asks the stop-and-leave question). The window returns to where it began.
 */
class WindowingJourney {
    fun run(device: UiDevice) {
        val window = Window.of(device)
        try {
            device.tap(Anchors.NAV_METRONOME)
            device.anchor(Anchors.METRONOME_ROOT)
            device.tap(Anchors.METRONOME_START_STOP)
            repeat(3) {
                window.change(device, WidthClass.Compact)
                window.change(device, WidthClass.Medium)
                window.change(device, WidthClass.Expanded)
            }
            device.tap(Anchors.METRONOME_START_STOP)

            device.tap(Anchors.NAV_PROJECTS)
            device.anchor(Anchors.PROJECTS_ROOT)
            device.tap(Anchors.PROJECTS_CARD_DEMO)
            device.anchor(Anchors.TIMELINE_ROOT)
            device.tap(Anchors.TRANSPORT_PLAY)
            window.change(device, WidthClass.Medium)
            window.change(device, WidthClass.Expanded)

            val clip = device.anchor(Anchors.TIMELINE_CLIP_FIRST).visibleBounds
            secondaryClick(clip.centerX().toFloat(), clip.centerY().toFloat())
            device.anchor(Anchors.TIMELINE_CLIP_MENU_FIRST)
            device.pressBack()
            check(device.wait(Until.gone(By.res(Anchors.TIMELINE_CLIP_MENU_FIRST)), CLOSE_TIMEOUT_MS)) { "the clip menu did not close" }
            device.tap(Anchors.TRANSPORT_PLAY)

            device.pressKeyCode(KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            device.anchor(Anchors.TUNER_ROOT)
            device.pressKeyCode(KeyEvent.KEYCODE_1, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            device.anchor(Anchors.TIMELINE_ROOT)
        } finally {
            window.restore(device)
        }
    }

    private enum class WidthClass(val widthDp: Int) { Compact(480), Medium(720), Expanded(1000) }

    /** How this device changes the window: a free-form task resized in place, or the phone's rotation. */
    private sealed interface Window {
        fun change(device: UiDevice, to: WidthClass)

        fun restore(device: UiDevice)

        companion object {
            fun of(device: UiDevice): Window {
                val context = InstrumentationRegistry.getInstrumentation().context
                val freeform = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                val task = if (freeform) Task.find(device) else null
                return if (task != null) Freeform(task) else Rotation
            }
        }
    }

    /** `am task resize` keeps the window's top-left corner; the sizes are not remembered by the platform. */
    private class Freeform(private val start: Task) : Window {
        override fun change(device: UiDevice, to: WidthClass) {
            val density = InstrumentationRegistry.getInstrumentation().context.resources.displayMetrics.density
            val width = (to.widthDp * density).toInt()
            val height = (HEIGHT_DP * density).toInt()
            device.executeShellCommand("am task resize ${start.id} ${start.left} ${start.top} ${start.left + width} ${start.top + height}")
            settle(device)
        }

        override fun restore(device: UiDevice) {
            device.executeShellCommand("am task resize ${start.id} ${start.left} ${start.top} ${start.right} ${start.bottom}")
            settle(device)
        }
    }

    /** A phone has one window: quarter turns cross the width classes. */
    private object Rotation : Window {
        override fun change(device: UiDevice, to: WidthClass) {
            if (to == WidthClass.Compact) device.setOrientationNatural() else device.setOrientationLeft()
            settle(device)
        }

        override fun restore(device: UiDevice) {
            device.setOrientationNatural()
            device.unfreezeRotation()
            settle(device)
        }
    }

    /** The app's task and its bounds, from `am stack list`. */
    private class Task(val id: Int, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        companion object {
            private val line = Regex("""taskId=(\d+): ${Regex.escape(PACKAGE)}/\S+ bounds=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

            fun find(device: UiDevice): Task? {
                val match = line.find(device.executeShellCommand("am stack list")) ?: return null
                val (id, left, top, right, bottom) = match.destructured
                return Task(id.toInt(), left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.tembraaudio"
        const val HEIGHT_DP = 720
        const val CLOSE_TIMEOUT_MS = 5_000L
        /** Long enough for the relayout and its first frames, short against the journey. */
        const val SETTLE_MS = 600L

        fun settle(device: UiDevice) {
            device.waitForIdle()
            SystemClock.sleep(SETTLE_MS)
        }

        /** A mouse secondary click, as a right button press and release at ([x], [y]). */
        fun secondaryClick(x: Float, y: Float) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, buttons: Int, actionButton: Int, time: Long) {
                val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
                val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
                val event = MotionEvent.obtain(down, time, action, 1, properties, coords, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
                // `MotionEvent.setActionButton` is hidden; BUTTON_PRESS and BUTTON_RELEASE need it for Compose to see the button.
                if (actionButton != 0) MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType).invoke(event, actionButton)
                check(automation.injectInputEvent(event, true)) { "the platform refused the injected $event" }
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY, 0, down)
            send(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_SECONDARY, down)
            SystemClock.sleep(40)
            val up = SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_BUTTON_RELEASE, 0, MotionEvent.BUTTON_SECONDARY, up)
            send(MotionEvent.ACTION_UP, 0, 0, up)
        }
    }
}
