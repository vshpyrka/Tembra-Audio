package audio.tembra.ui.platform

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The app is dark whatever the system theme, so its system bars draw light icons even in the light system theme, where
 * the default edge-to-edge style drew dark icons on the dark app (OPPO CPH2239, Redmi Note 5, 2026-10-15).
 */
class EdgeToEdgeTest {

    @get:Rule
    val rule = createAndroidComposeRule<LightSystemThemeActivity>()

    @Test
    fun theBarsDrawLightIconsInTheLightSystemTheme() {
        rule.runOnUiThread {
            val activity = rule.activity
            val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            assertEquals(Configuration.UI_MODE_NIGHT_NO, night, "the host does not see the light system theme")
            activity.enableEdgeToEdgeOverDarkContent()
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertFalse(bars.isAppearanceLightStatusBars, "the status bar draws dark icons")
            assertFalse(bars.isAppearanceLightNavigationBars, "the navigation bar draws dark icons")
        }
    }
}
