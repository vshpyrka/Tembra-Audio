package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraGradients
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rounded gradient card behind the meter and the By Ear grid (research R9). */
@OptIn(ExperimentalTestApi::class)
class TembraGradientCardTest {

    private var lowest: Color = Color.Unspecified
    private var pixelDensity = 1f

    @Test
    fun rendersContentWithTheGlowAndAnOpaqueBody() = runComposeUiTest {
        setContent {
            TembraTheme {
                lowest = TembraTheme.colors.surfaceContainerLowest
                pixelDensity = LocalDensity.current.density
                TembraGradientCard(glow = TembraGradients.Default.meterCompact, testTag = "card", modifier = Modifier.size(320.dp, 240.dp)) {
                    Text("inside")
                }
            }
        }
        onNodeWithText("inside").assertIsDisplayed()
        onNodeWithTag("card").assertIsDisplayed()
        val image = onNodeWithTag("card").captureToImage()
        val centre = image.averageColorAt(image.width * 6 / 10, image.height * 45 / 100)
        assertFalse(centre.approximately(lowest), "the glow is not drawn: centre $centre equals surfaceContainerLowest $lowest")
        val inset = (24 * pixelDensity).roundToInt()
        val corner = image.averageColorAt(inset, inset)
        assertTrue(corner.alpha > 0.99f, "the body near the corner is transparent: $corner")
    }
}
