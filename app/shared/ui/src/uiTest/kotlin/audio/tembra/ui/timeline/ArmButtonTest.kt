package audio.tembra.ui.timeline

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.ArmButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The arm toggle (feature 010, contracts/timeline-recording-ui.md; T048): a named switch with a tooltip, 44 dp by default. */
@OptIn(ExperimentalTestApi::class)
class ArmButtonTest {

    private val reported = mutableListOf<Boolean>()

    private fun ComposeUiTest.setArm(
        armed: Boolean = false,
        source: MutableInteractionSource? = null,
        size: Dp? = null,
    ) {
        setContent {
            TembraTheme {
                if (size == null) {
                    ArmButton(armed = armed, trackTint = Color.Red, onToggle = { reported += it }, interactionSource = source, testTag = TAG)
                } else {
                    ArmButton(armed = armed, trackTint = Color.Red, onToggle = { reported += it }, size = size, interactionSource = source, testTag = TAG)
                }
            }
        }
    }

    @Test
    fun idleIsOffAsASwitchNamedArmForRecording() = runComposeUiTest {
        setArm()
        onNodeWithTag(TAG).assertIsOff().assertContentDescriptionEquals(TimelineCopy.arm)
        val role = onNodeWithTag(TAG).fetchSemanticsNode().config[SemanticsProperties.Role]
        assertEquals(Role.Switch, role)
        assertEquals("Arm for recording", TimelineCopy.arm)
    }

    @Test
    fun armedIsOn() = runComposeUiTest {
        setArm(armed = true)
        onNodeWithTag(TAG).assertIsOn()
    }

    @Test
    fun clickReportsTheNewValue() = runComposeUiTest {
        setArm(armed = false)
        onNodeWithTag(TAG).performClick()
        assertEquals(listOf(true), reported)
    }

    @Test
    fun clickWhileArmedReportsOff() = runComposeUiTest {
        setArm(armed = true)
        onNodeWithTag(TAG).performClick()
        assertEquals(listOf(false), reported)
    }

    @Test
    fun theTooltipAppearsOnHover() = runComposeUiTest {
        val source = MutableInteractionSource()
        setArm(source = source)
        onNodeWithText(TimelineCopy.arm).assertDoesNotExist()
        assertTrue(source.tryEmit(HoverInteraction.Enter()))
        waitForIdle()
        onNodeWithText(TimelineCopy.arm).assertExists()
    }

    @Test
    fun theTooltipAppearsOnKeyboardFocus() = runComposeUiTest {
        val source = MutableInteractionSource()
        setArm(source = source)
        assertTrue(source.tryEmit(FocusInteraction.Focus()))
        waitForIdle()
        onNodeWithText(TimelineCopy.arm).assertExists()
    }

    @Test
    fun theRootIs44dpByDefaultAndHonoursSize() = runComposeUiTest {
        setArm()
        onNodeWithTag(TAG).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun theRootHonoursSize() = runComposeUiTest {
        setArm(size = 36.dp)
        onNodeWithTag(TAG).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
    }

    private companion object {
        const val TAG = "arm"
    }
}
