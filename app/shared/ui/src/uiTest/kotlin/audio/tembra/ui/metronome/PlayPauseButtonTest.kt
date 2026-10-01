package audio.tembra.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.metronome.components.PlayPauseButton
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The play glow's reach (design iteration D1, contracts/design-system-additions.md `playGlow` 8 dp): the halo stays
 * within 8 dp of the button's edge, so it no longer spills over the Tap Tempo card beside it, and it is still there.
 */
@OptIn(ExperimentalTestApi::class)
class PlayPauseButtonTest {

    @Test
    fun theGlowStaysWithin8dpOfTheButton() = runComposeUiTest {
        var surface = Color.Unspecified
        var density = 1f
        var buttonDp = 0f
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                surface = TembraTheme.colors.surface
                density = LocalDensity.current.density
                buttonDp = TembraTheme.spacing.playButton.value
                Box(Modifier.size(160.dp).background(TembraTheme.colors.surface).testTag("stage"), contentAlignment = Alignment.Center) {
                    PlayPauseButton(running = false, onToggle = {})
                }
            }
        }
        repeat(2) { mainClock.advanceTimeByFrame() }
        val pixels = onNodeWithTag("stage").captureToImage().toPixelMap()
        val cx = pixels.width / 2f
        val cy = pixels.height / 2f
        val edge = buttonDp / 2 * density
        var beyond = 0
        var near = 0
        var nearTinted = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val reach = (sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy)) - edge) / density
            val tinted = pixels[x, y].distanceTo(surface) > 0.02f
            // One dp of tolerance for antialiasing at the gradient's transparent end.
            if (reach > 9f && tinted) beyond++
            if (reach in 1f..3f) {
                near++
                if (tinted) nearTinted++
            }
        }
        assertTrue(beyond == 0, "$beyond glow-coloured pixels lie more than 8 dp from the play button's edge")
        assertTrue(nearTinted > near / 2, "no glow within 3 dp of the button ($nearTinted of $near pixels tinted)")
    }
}
