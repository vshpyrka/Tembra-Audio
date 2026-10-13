package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The card surface: level 1 at rest, level 2 when elevated, the caller's content inside. */
@OptIn(ExperimentalTestApi::class)
class TembraCardTest {

    private var level1: Color = Color.Unspecified
    private var level2: Color = Color.Unspecified

    private fun ComposeUiTest.setCard(elevated: Boolean) {
        setContent {
            TembraTheme {
                level1 = TembraTheme.daw.surfaceLevel1
                level2 = TembraTheme.daw.surfaceLevel2
                TembraCard(elevated = elevated, testTag = "card") {
                    Box(Modifier.size(120.dp)) { Text("Demo project") }
                }
            }
        }
        mainClock.autoAdvance = false
    }

    private fun ComposeUiTest.centreColor(): Color {
        val image = onNodeWithTag("card").captureToImage()
        return image.averageColorAt(image.width * 3 / 4, image.height * 3 / 4)
    }

    @Test
    fun restingCardIsSurfaceLevel1WithItsContent() = runComposeUiTest {
        setCard(elevated = false)
        onNodeWithText("Demo project").assertExists()
        assertTrue(centreColor().approximately(level1), "resting fill ${centreColor()} is not surfaceLevel1")
    }

    @Test
    fun elevatedCardIsSurfaceLevel2() = runComposeUiTest {
        setCard(elevated = true)
        assertTrue(centreColor().approximately(level2), "elevated fill ${centreColor()} is not surfaceLevel2")
    }
}
