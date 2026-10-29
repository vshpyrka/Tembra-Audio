package audio.tembra.ui.platform

import android.app.UiAutomation
import android.provider.Settings
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * In an activity that rotates in place, as MainActivity does, the window size the composition reads follows the
 * rotation. Compose's own value stayed one rotation behind on the Redmi Note 5 (API 28, 2026-10-15), so every
 * `currentWindowAdaptiveInfoV2()` reader laid out the previous orientation.
 */
class LaidOutWindowSizeTest {

    @get:Rule
    val rule = createAndroidComposeRule<InPlaceRotationActivity>()

    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var autoRotateWas = 1
    private var userRotationWas = 0

    @Before
    fun rememberRotation() {
        val resolver = rule.activity.contentResolver
        autoRotateWas = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        userRotationWas = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0)
    }

    @After
    fun restoreRotation() {
        // USER_ROTATION holds a Surface rotation, which is also the matching ROTATION_FREEZE_* value.
        automation.setRotation(if (autoRotateWas == 1) UiAutomation.ROTATION_UNFREEZE else userRotationWas)
    }

    @Test
    fun containerSizeFollowsAnInPlaceRotation() {
        var containerSize = IntSize.Zero
        rule.setContent {
            ProvideLaidOutWindowSize {
                val size = LocalWindowInfo.current.containerSize
                SideEffect { containerSize = size }
            }
        }

        for (landscape in listOf(false, true, false)) {
            automation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
            rule.waitUntil(timeoutMillis = 10_000) { windowSize().isLandscape() == landscape }
            rule.waitForIdle()
            assertEquals(landscape, containerSize.isLandscape(), "containerSize $containerSize in a window of ${windowSize()}")
        }
    }

    private fun windowSize(): IntSize = rule.runOnUiThread {
        val decor = rule.activity.window.decorView
        IntSize(decor.width, decor.height)
    }

    private fun IntSize.isLandscape() = width > height
}
