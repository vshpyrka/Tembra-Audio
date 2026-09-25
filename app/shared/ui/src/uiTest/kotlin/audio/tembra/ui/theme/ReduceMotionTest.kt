package audio.tembra.ui.theme

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reduce motion is read from Compose's own MotionDurationScale on both platforms (research R7, spec FR-008). */
@OptIn(ExperimentalTestApi::class)
class ReduceMotionTest {

    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    @Test
    fun fullMotionByDefault() = runComposeUiTest {
        var reduce: Boolean? = null
        var remembered: Boolean? = null
        setContent {
            TembraTheme {
                reduce = TembraTheme.motion.reduceMotion
                remembered = rememberReduceMotion()
            }
        }
        assertEquals(false, reduce)
        assertEquals(false, remembered)
    }

    @Test
    fun reduceMotionWhenTheDurationScaleIsZero() = runComposeUiTest(effectContext = zeroScale) {
        var reduce: Boolean? = null
        var remembered: Boolean? = null
        setContent {
            TembraTheme {
                reduce = TembraTheme.motion.reduceMotion
                remembered = rememberReduceMotion()
            }
        }
        assertEquals(true, reduce)
        assertEquals(true, remembered)
    }
}
