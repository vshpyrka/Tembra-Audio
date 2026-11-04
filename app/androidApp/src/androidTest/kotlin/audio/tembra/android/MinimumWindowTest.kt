package audio.tembra.android

import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.android.desktop.DesktopWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The window's minimum (feature 011, spec FR-004, contracts/window-behaviour.md): the activity declares 360 × 480 dp,
 * and a mouse drag of the window's corner towards 300 × 400 dp stops no smaller. `am task resize` ignores any minimum
 * (research hands-on check "Desktop (Preview)"), so the drag is a real mouse drag; the platform's own floor may be
 * larger (Android 17: 390 dp wide, T004).
 */
class MinimumWindowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theActivityDeclares360By480Dp() {
        @Suppress("DEPRECATION") // getActivityInfo(ComponentName, Int): the ComponentInfoFlags overload needs API 33.
        val layout = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0).windowLayout
        assertNotNull("MainActivity declares no <layout>", layout)
        val density = context.resources.displayMetrics.density
        assertEquals((360 * density).roundToInt(), layout!!.minWidth)
        assertEquals((480 * density).roundToInt(), layout.minHeight)
    }

    @Test
    fun aCornerDragStopsAtTheMinimum() {
        DesktopWindow.assumeFreeform()
        DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(900, 700)
        val bounds = DesktopWindow.windowBoundsPx()
        val density = context.resources.displayMetrics.density
        DesktopWindow.dragResize(bounds.left + (300 * density).toInt(), bounds.top + (400 * density).toInt())
        val (width, height) = DesktopWindow.windowBoundsDp()
        assertTrue("the drag did not resize the window (still ${width}×$height dp)", width < 900f)
        assertTrue("the window shrank to $width dp wide", width >= 359.5f)
        assertTrue("the window shrank to $height dp tall", height >= 479.5f)
    }
}
