package audio.tembra.ui.designsystem

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The audio-state pill: capitals drawn, sentence case announced, a dot that pulses only with motion (spec FR-006, FR-027). */
@OptIn(ExperimentalTestApi::class)
class TembraStatusPillTest {

    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    private var primary: Color = Color.Unspecified

    @Test
    fun drawsCapitalsAndAnnouncesSentenceCase() = runComposeUiTest {
        // A still dot: the pulse is not what this test proves, and a frozen state keeps the colour probe exact.
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                TembraStatusPill(text = "Mic active", color = TembraTheme.colors.primary, pulsing = false, testTag = "pill")
            }
        }
        onNodeWithTag("pill").assertContentDescriptionEquals("Mic active")
        onNode(hasText("MIC ACTIVE") and hasAnyAncestor(hasTestTag("pill")), useUnmergedTree = true).assertExists()
        onNodeWithTag("pill.dot", useUnmergedTree = true).assertExists()
        val dot = onNodeWithTag("pill.dot", useUnmergedTree = true).captureToImage()
        assertTrue(dot.containsColor(primary), "the dot is not drawn in the pill colour")
    }

    @Test
    fun theDotDoesNotPulseUnderReduceMotion() = runComposeUiTest(effectContext = zeroScale) {
        setContent {
            TembraTheme {
                TembraStatusPill(text = "Mic active", color = TembraTheme.colors.primary, pulsing = true, testTag = "pill")
            }
        }
        mainClock.autoAdvance = false
        val dot = onNodeWithTag("pill.dot", useUnmergedTree = true)
        val image = dot.captureToImage()
        val before = image.averageColorAt(image.width / 2, image.height / 2)
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        val after = dot.captureToImage().averageColorAt(image.width / 2, image.height / 2)
        assertTrue(before.approximately(after, 0.01f) && kotlin.math.abs(before.alpha - after.alpha) < 0.01f, "dot changed under reduce motion: $before -> $after")
    }

    @Test
    fun anIconIsShownWhenGiven() = runComposeUiTest {
        setContent {
            TembraTheme {
                TembraStatusPill(text = "Tone out", color = TembraTheme.colors.secondary, icon = TembraIcon.VolumeUp, testTag = "pill")
            }
        }
        onNodeWithTag("pill.icon", useUnmergedTree = true).assertExists()
        onNodeWithTag("pill").assertContentDescriptionEquals("Tone out")
        onNode(hasText("TONE OUT") and hasAnyAncestor(hasTestTag("pill")), useUnmergedTree = true).assertExists()
    }
}
