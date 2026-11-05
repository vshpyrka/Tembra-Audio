package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.designsystem.FaderDefaults
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One mixer strip in its compact form (feature 012, contracts/mixer-ui.md "MixerStrip"; research R2; T045): the
 * number, Solo, Mute, the fader and the name top to bottom, the sizes of the targets, the callbacks each control
 * reports, the selection on the number and the name, and the focus order Solo → Mute → fader. The meter arrives in
 * User Story 2 and the knob in User Story 3 (their tests extend this file).
 */
@OptIn(ExperimentalTestApi::class)
class MixerStripTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null
    private val strip = StripUi(
        id = TrackId("t1"), index = 0, number = 1, name = "Drums", colorIndex = 0, gainDb = GainDb(-3f), pan = Pan.Centre,
        muted = false, soloed = false, audible = true,
    )
    private val callbacks = TimelineCallbacks.None.copy(
        onToggleSolo = { calls += "solo(${it.value})" },
        onToggleMute = { calls += "mute(${it.value})" },
        onGain = { id, gain -> calls += "gain(${id.value},${gain.readout})" },
        onGainSettled = { calls += "settled" },
        onStripSelect = { calls += "select(${it.value})" },
    )

    private fun ComposeUiTest.setStrip(strip: StripUi = this@MixerStripTest.strip, form: StripForm = StripForm.Compact, selected: Boolean = false, height: Dp = 400.dp) {
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalTembraMotion provides TembraMotion(reduceMotion = true)) {
                    manager = LocalInputModeManager.current
                    Box(Modifier.height(height)) {
                        MixerStrip(strip = strip, form = form, selected = selected, callbacks = callbacks)
                    }
                }
            }
        }
    }

    @Test
    fun theCompactFormStacksNumberTogglesFaderAndName() = runComposeUiTest {
        setStrip()
        val frame = onNodeWithTag(MixerTestTags.strip(0)).getUnclippedBoundsInRoot()
        val number = onNodeWithTag(MixerTestTags.number(0)).assertTextEquals("1").getUnclippedBoundsInRoot()
        val solo = onNodeWithTag(MixerTestTags.solo(0)).getUnclippedBoundsInRoot()
        val mute = onNodeWithTag(MixerTestTags.mute(0)).getUnclippedBoundsInRoot()
        val fader = onNodeWithTag(MixerTestTags.fader(0)).getUnclippedBoundsInRoot()
        val name = onNodeWithTag(MixerTestTags.name(0)).assertTextEquals("Drums").getUnclippedBoundsInRoot()
        assertEquals(MixerStripDefaults.Width, frame.width, "the strip is ${frame.width} wide")
        assertTrue(number.bottom <= solo.top, "the number $number is not above Solo $solo")
        assertTrue(solo.right <= mute.left && solo.top == mute.top, "Solo $solo and Mute $mute are not side by side")
        assertTrue(fader.top >= solo.bottom, "the fader $fader is not under the toggles")
        assertTrue(name.top >= fader.bottom, "the name $name is not under the fader")
        onNodeWithTag(MixerTestTags.pan(0)).assertDoesNotExist()
        onNodeWithTag(MixerTestTags.vu(0)).assertDoesNotExist()
    }

    @Test
    fun theTargetsHaveTheirSizes() = runComposeUiTest {
        // The strip's room at the least half height: the sheet less its handle row.
        setStrip(height = MixerAnchors.MinHalf - MixerSheetDefaults.HandleHeight)
        // Research R2: the Solo and Mute toggles are the 36 dp squares the strip's 72 dp hold side by side; the fader's
        // handle is 44 dp wide and its throw at the half height stays 120 dp or more (spec FR-018, FR-025).
        val solo = onNodeWithTag(MixerTestTags.solo(0)).getUnclippedBoundsInRoot()
        val mute = onNodeWithTag(MixerTestTags.mute(0)).getUnclippedBoundsInRoot()
        val fader = onNodeWithTag(MixerTestTags.fader(0)).getUnclippedBoundsInRoot()
        assertTrue(solo.width >= 36.dp && solo.height >= 36.dp, "Solo is $solo")
        assertTrue(mute.width >= 36.dp && mute.height >= 36.dp, "Mute is $mute")
        assertTrue(fader.width >= FaderDefaults.HandleWidth, "the fader is ${fader.width} wide")
        // The throw is the fader's height, the handle included, as research R2 counts it (140 dp in its budget).
        assertTrue(fader.height >= FaderDefaults.MinThrow, "the fader's throw is ${fader.height}, under ${FaderDefaults.MinThrow}")
    }

    @Test
    fun theControlsReportTheTrack() = runComposeUiTest {
        setStrip(strip.copy(muted = true))
        onNodeWithTag(MixerTestTags.solo(0)).assertIsOff().assertContentDescriptionEquals(MixerCopy.solo).performClick()
        onNodeWithTag(MixerTestTags.mute(0)).assertIsOn().assertContentDescriptionEquals(MixerCopy.mute).performClick()
        onNodeWithTag(MixerTestTags.fader(0))
            .assertContentDescriptionEquals(MixerCopy.gainDescription("Drums"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−3.0dB"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-6f) }
        onNodeWithTag(MixerTestTags.number(0)).performClick()
        onNodeWithTag(MixerTestTags.name(0)).performClick()
        assertEquals(listOf("solo(t1)", "mute(t1)", "gain(t1,−6.0dB)", "settled", "select(t1)", "select(t1)"), calls)
    }

    @Test
    fun theSelectedStripMarksItsNumberAndName() = runComposeUiTest {
        setStrip(selected = true)
        onNodeWithTag(MixerTestTags.number(0)).assertIsSelected()
        onNodeWithTag(MixerTestTags.name(0)).assertIsSelected()
    }

    @Test
    fun anUnselectedStripIsNot() = runComposeUiTest {
        setStrip(selected = false)
        onNodeWithTag(MixerTestTags.number(0)).assertIsNotSelected()
    }

    /** The strip is one focus group: Tab walks Solo, Mute, then the fader (contracts/mixer-ui.md "Keyboard"); declared per lane. */
    @Test
    fun tabWalksSoloMuteThenTheFader() = runComposeUiTest {
        setStrip()
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        onNodeWithTag(MixerTestTags.solo(0)).requestFocus()
        onNodeWithTag(MixerTestTags.solo(0)).assertIsFocused()
        onNodeWithTag(MixerTestTags.solo(0)).performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag(MixerTestTags.mute(0)).assertIsFocused()
        onNodeWithTag(MixerTestTags.mute(0)).performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag(MixerTestTags.fader(0)).assertIsFocused()
        // The fader's Up arrow reaches the strip's gain (0.5 dB) and settles the step.
        onNodeWithTag(MixerTestTags.fader(0)).performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(listOf("gain(t1,−2.5dB)", "settled"), calls)
    }
}
