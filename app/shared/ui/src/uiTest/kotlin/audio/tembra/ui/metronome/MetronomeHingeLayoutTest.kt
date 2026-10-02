package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import audio.tembra.ui.posture.VerticalFold
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The wide metronome on a foldable half-opened like a book (design iteration D1, research R8): the settings column
 * ends a gutter before the hinge and the dial pane starts a gutter after it. The host sits at the window's origin, so
 * a fold 426 dp into the window is 426 dp into the root.
 */
@OptIn(ExperimentalTestApi::class)
class MetronomeHingeLayoutTest {

    private fun ComposeUiTest.setScreen(foldAtDp: Float?) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                val px = LocalDensity.current.density * (foldAtDp ?: 0f)
                Box(modifier = Modifier.requiredSize(DpSize(852.dp, 760.dp))) {
                    MetronomeScreenContent(
                        state = MetronomeUiState(loaded = true),
                        callbacks = MetronomeCallbacks.None,
                        telemetry = ScriptedBeatTelemetry(),
                        layout = MetronomeLayout.Wide,
                        snackbarHostState = SnackbarHostState(),
                        verticalFold = foldAtDp?.let { VerticalFold(left = px, right = px) },
                    )
                }
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theHingeSplitsTheSettingsFromTheDialPane() = runComposeUiTest {
        setScreen(foldAtDp = 426f)
        val settings = bounds("metronome.settings")
        val dialCard = bounds("metronome.dialCard")
        assertTrue(settings.right <= 414.dp + 1.dp, "the settings $settings reach the hinge's gutter at 414 dp")
        assertTrue(dialCard.left >= 438.dp - 1.dp, "the dial card $dialCard starts inside the hinge's gutter at 438 dp")
        assertTrue(bounds("metronome.tap").left >= 438.dp - 1.dp, "tap tempo starts inside the hinge's gutter")
    }

    @Test
    fun withoutAHingeTheSettingsTakeAThird() = runComposeUiTest {
        setScreen(foldAtDp = null)
        val settings = bounds("metronome.settings")
        // A third of the 820 dp content width is under the 320 dp minimum, so the column is 320 dp.
        assertTrue(settings.right - settings.left <= 321.dp, "the settings column is ${settings.right - settings.left} wide")
    }
}
