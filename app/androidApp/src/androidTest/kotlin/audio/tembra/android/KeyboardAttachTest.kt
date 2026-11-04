package audio.tembra.android

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Attaching or detaching a hardware keyboard, a mouse or a trackpad, and a convertible switching between laptop and
 * tablet mode, never recreate the activity (feature 011, spec FR-006/FR-007, research R4): the activity handles every
 * one of these configuration changes itself, as it already did window size, orientation, layout and density changes.
 * No shell command can attach a keyboard to a device, so the declaration the platform consults is what is asserted.
 */
class KeyboardAttachTest {

    @Test
    fun everyInputAndWindowChangeIsHandledByTheActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") // getActivityInfo(ComponentName, Int): the ComponentInfoFlags overload needs API 33.
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val required = mapOf(
            "keyboard" to ActivityInfo.CONFIG_KEYBOARD,
            "keyboardHidden" to ActivityInfo.CONFIG_KEYBOARD_HIDDEN,
            "navigation" to ActivityInfo.CONFIG_NAVIGATION,
            "touchscreen" to ActivityInfo.CONFIG_TOUCHSCREEN,
            "orientation" to ActivityInfo.CONFIG_ORIENTATION,
            "screenSize" to ActivityInfo.CONFIG_SCREEN_SIZE,
            "screenLayout" to ActivityInfo.CONFIG_SCREEN_LAYOUT,
            "smallestScreenSize" to ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            "uiMode" to ActivityInfo.CONFIG_UI_MODE,
            "density" to ActivityInfo.CONFIG_DENSITY,
        )
        val missing = required.filterValues { flag -> info.configChanges and flag == 0 }.keys
        assertEquals("configChanges misses $missing: a change of these recreates MainActivity", emptySet<String>(), missing)
    }
}
