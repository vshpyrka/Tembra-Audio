package audio.tembra.ui.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The only test that exercises the window-measuring composable (Constitution III); the mapping itself is LayoutModeTest. */
@OptIn(ExperimentalTestApi::class)
class RememberLayoutModeTest {

    @Test
    fun matchesTheWindowSizeClassMapping() = runComposeUiTest {
        var actual: LayoutMode? = null
        var expected: LayoutMode? = null
        setContent {
            TembraTheme {
                actual = rememberLayoutMode()
                expected = LayoutMode.from(currentWindowAdaptiveInfoV2().windowSizeClass)
            }
        }
        assertEquals(expected, actual)
        assertTrue(actual in LayoutMode.entries, "layout mode $actual")
    }
}
