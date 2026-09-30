package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The style row's pill (contracts/design-system-additions.md, research R10): colours, semantics and targets. */
@OptIn(ExperimentalTestApi::class)
class TembraPillTest {

    private var pixelDensity = 1f
    private var secondaryContainer: Color = Color.Unspecified
    private var onSecondaryContainer: Color = Color.Unspecified
    private var surfaceContainerHigh: Color = Color.Unspecified
    private var onSurfaceVariant: Color = Color.Unspecified
    private var onSurface: Color = Color.Unspecified

    private fun ComposeUiTest.setPill(selected: Boolean, interactionSource: MutableInteractionSource? = null, onClick: () -> Unit = {}) {
        setContent {
            TembraTheme {
                pixelDensity = LocalDensity.current.density
                secondaryContainer = TembraTheme.colors.secondaryContainer
                onSecondaryContainer = TembraTheme.colors.onSecondaryContainer
                surfaceContainerHigh = TembraTheme.colors.surfaceContainerHigh
                onSurfaceVariant = TembraTheme.colors.onSurfaceVariant
                onSurface = TembraTheme.colors.onSurface
                TembraPill("Standard", selected = selected, onClick = onClick, interactionSource = interactionSource, testTag = "pill")
            }
        }
        mainClock.autoAdvance = false
    }

    /** The fill beside the label: on the vertical centre, 5 dp in from the start edge (inside the cap, before the 10 dp padding ends). */
    private fun ComposeUiTest.fillColor(): Color {
        val image = onNodeWithTag("pill.surface", useUnmergedTree = true).captureToImage()
        return image.averageColorAt((FILL_PROBE_DP * pixelDensity).roundToInt(), image.height / 2)
    }

    private fun ComposeUiTest.label() = onNode(hasText("Standard") and hasAnyAncestor(hasTestTag("pill")), useUnmergedTree = true)

    @Test
    fun isASelectedTabWithA28dpDrawingInsideA44dpTarget() = runComposeUiTest {
        setPill(selected = true)
        onNodeWithTag("pill")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
            .assertHeightIsAtLeast(44.dp)
            .assertWidthIsAtLeast(44.dp)
        onNodeWithTag("pill.surface", useUnmergedTree = true).assertHeightIsEqualTo(28.dp)
    }

    @Test
    fun selectedPillIsSecondaryContainerWithOnSecondaryContainerText() = runComposeUiTest {
        setPill(selected = true)
        assertTrue(fillColor().approximately(secondaryContainer), "selected fill ${fillColor()} is not secondaryContainer")
        assertTrue(label().captureToImage().containsColor(onSecondaryContainer), "selected text is not onSecondaryContainer")
    }

    @Test
    fun unselectedPillIsSurfaceContainerHighWithOnSurfaceVariantText() = runComposeUiTest {
        setPill(selected = false)
        onNodeWithTag("pill").assertIsNotSelected()
        assertTrue(fillColor().approximately(surfaceContainerHigh), "unselected fill ${fillColor()} is not surfaceContainerHigh")
        assertTrue(label().captureToImage().containsColor(onSurfaceVariant), "unselected text is not onSurfaceVariant")
    }

    @Test
    fun clickInvokesOnClick() = runComposeUiTest {
        var clicks = 0
        setPill(selected = false, onClick = { clicks++ })
        mainClock.autoAdvance = true
        onNodeWithTag("pill").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun hoverRaisesTheUnselectedTextToOnSurface() = runComposeUiTest {
        val hover = MutableInteractionSource()
        setPill(selected = false, interactionSource = hover)
        assertFalse(label().captureToImage().containsColor(onSurface), "the resting text already shows onSurface")
        assertTrue(hover.tryEmit(HoverInteraction.Enter()))
        mainClock.advanceTimeBy(FRAMES_MS)
        assertTrue(label().captureToImage().containsColor(onSurface), "the hovered text is not onSurface")
    }

    private companion object {
        const val FILL_PROBE_DP = 5
        const val FRAMES_MS = 100L
    }
}
