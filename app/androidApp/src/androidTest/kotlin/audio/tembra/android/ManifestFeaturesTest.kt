package audio.tembra.android

import android.content.ComponentName
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Reach (feature 011, spec FR-001/FR-002, research R1, R3): the installed package declares the touchscreen and the
 * microphone as not required, so Google Play offers it to Chromebooks and desktops without them, and the activity
 * carries ChromeOS's first-window size hint. The window minimum is asserted by `MinimumWindowTest`.
 */
class ManifestFeaturesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    private fun declaredFeature(name: String): FeatureInfo? {
        @Suppress("DEPRECATION") // getPackageInfo(String, Int): the PackageInfoFlags overload needs API 33; minSdk is 26.
        val info = packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
        return info.reqFeatures?.firstOrNull { it.name == name }
    }

    @Test
    fun theTouchscreenIsDeclaredNotRequired() {
        val feature = declaredFeature(PackageManager.FEATURE_TOUCHSCREEN)
        assertNotNull("android.hardware.touchscreen is not declared: Play implies it is required", feature)
        assertEquals(0, feature!!.flags and FeatureInfo.FLAG_REQUIRED)
    }

    @Test
    fun theMicrophoneIsDeclaredNotRequired() {
        val feature = declaredFeature(PackageManager.FEATURE_MICROPHONE)
        assertNotNull("android.hardware.microphone is not declared: RECORD_AUDIO implies it is required", feature)
        assertEquals(0, feature!!.flags and FeatureInfo.FLAG_REQUIRED)
    }

    @Test
    fun chromeOsOpensTheFirstWindowAtTabletSize() {
        @Suppress("DEPRECATION") // getActivityInfo(ComponentName, Int): the ComponentInfoFlags overload needs API 33.
        val activity = packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), PackageManager.GET_META_DATA)
        assertEquals("tablet", activity.metaData?.getString("WindowManagerPreference:FreeformWindowSize"))
    }
}
