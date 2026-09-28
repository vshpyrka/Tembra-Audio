package audio.tembra.ui.tuner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.tuner.Instrument
import audio.tembra.core.tuner.TunerMode
import audio.tembra.core.tuner.TuningPresets
import audio.tembra.ui.designsystem.assertDisplayedWithin
import audio.tembra.ui.designsystem.largeFonts
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Selectors, presets and the By Ear cards (spec FR-002 to FR-005, FR-017; User Story 2) and the loop toggle (User Story 4). */
@OptIn(ExperimentalTestApi::class)
class TunerScreenByEarTest {

    private val telemetry = ScriptedTelemetry()
    private var callbacks = TunerCallbacks.None

    private val byEar = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, mode = TunerMode.ByEar)
    private val sevenStrings = byEar.copy(
        guitarStrings = audio.tembra.core.tuner.GuitarStringCount.Seven,
        preset = TuningPresets.byId("guitar7-standard")!!,
        presets = TuningPresets.forInstrument(Instrument.Guitar, audio.tembra.core.tuner.GuitarStringCount.Seven),
    )

    private fun ComposeUiTest.setScreen(initial: TunerUiState, layout: TunerLayout = TunerLayout.Stacked, fontScale: Float? = null): (TunerUiState) -> Unit {
        var state by mutableStateOf(initial)
        setContent {
            TembraTheme {
                val content: @androidx.compose.runtime.Composable () -> Unit = {
                    TunerScreenContent(state = state, callbacks = callbacks, telemetry = telemetry, layout = layout)
                }
                if (fontScale == null) content() else CompositionLocalProvider(LocalDensity provides largeFonts(fontScale)) { content() }
            }
        }
        return { next -> state = next; waitForIdle() }
    }

    @Test
    fun selectorsReflectState() = runComposeUiTest {
        setScreen(byEar.copy(mode = TunerMode.AutoListen, audio = audio.tembra.engine.AudioState.Idle))
        onNodeWithTag("tuner.instrument.guitar").assertIsSelected()
        onNodeWithTag("tuner.strings.6").assertIsSelected()
        onNodeWithTag("tuner.mode.autoListen").assertIsSelected()
    }

    @Test
    fun celloHidesStringCount() = runComposeUiTest {
        setScreen(byEar.copy(instrument = Instrument.Cello, preset = TuningPresets.byId("cello-standard")!!, presets = TuningPresets.forInstrument(Instrument.Cello, null)))
        onNodeWithTag("tuner.strings").assertDoesNotExist()
        onNodeWithTag("tuner.instrument.cello").assertIsSelected()
        for (numeral in listOf("IV", "III", "II", "I")) onNodeWithTag("tuner.card.$numeral").assertExists()
    }

    @Test
    fun byEarListsTheStrings() = runComposeUiTest {
        setScreen(sevenStrings)
        val expected = listOf(
            Triple("VII", "B", "61.7 Hz"), Triple("VI", "E", "82.4 Hz"), Triple("V", "A", "110.0 Hz"), Triple("IV", "D", "146.8 Hz"),
            Triple("III", "G", "196.0 Hz"), Triple("II", "B", "246.9 Hz"), Triple("I", "E", "329.6 Hz"),
        )
        var lastTop = -1f
        for ((numeral, note, hz) in expected) {
            val card = onNodeWithTag("tuner.card.$numeral").assertExists()
            onNode(hasText(numeral) and hasAnyAncestor(hasTestTag("tuner.card.$numeral")), useUnmergedTree = true).assertExists()
            onNode(hasText(note) and hasAnyAncestor(hasTestTag("tuner.card.$numeral")), useUnmergedTree = true).assertExists()
            onNode(hasText(hz) and hasAnyAncestor(hasTestTag("tuner.card.$numeral")), useUnmergedTree = true).assertExists()
            onNodeWithTag("tuner.card.$numeral.audition").assert(hasText("Audition"))
            val top = card.getUnclippedBoundsInRoot().top.value
            assertTrue(top >= lastTop, "$numeral is above the previous card")
            lastTop = top
        }
        onNode(hasText("1") and hasAnyAncestor(hasTestTag("tuner.card.VII")), useUnmergedTree = true).assertExists()
        onNode(hasText("4") and hasAnyAncestor(hasTestTag("tuner.card.I")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun presetPickerOpensAndSelects() = runComposeUiTest {
        var selected: String? = null
        callbacks = TunerCallbacks.None.copy(onPreset = { selected = it })
        setScreen(byEar)
        onNodeWithTag("tuner.preset.picker").assert(hasText("Standard (E2 A2 D3 G3 B3 E4)"))
        onNodeWithTag("tuner.preset.picker").performClick()
        for (preset in TuningPresets.forInstrument(Instrument.Guitar, audio.tembra.core.tuner.GuitarStringCount.Six)) {
            onNodeWithTag("tuner.preset.item.${preset.id}").assertIsDisplayed().assert(hasText(preset.name)).assert(hasText(preset.summary))
        }
        onNodeWithTag("tuner.preset.item.guitar6-drop-d").performClick()
        assertEquals("guitar6-drop-d", selected)
    }

    @Test
    fun twoColumnsAtCompact() = runComposeUiTest {
        setScreen(sevenStrings)
        val vii = onNodeWithTag("tuner.card.VII").getUnclippedBoundsInRoot()
        val vi = onNodeWithTag("tuner.card.VI").getUnclippedBoundsInRoot()
        val v = onNodeWithTag("tuner.card.V").getUnclippedBoundsInRoot()
        val i = onNodeWithTag("tuner.card.I").getUnclippedBoundsInRoot()
        assertEquals(vii.top, vi.top)
        assertTrue(v.top > vii.bottom, "V does not start the second row")
        assertTrue(i.top > onNodeWithTag("tuner.card.II").getUnclippedBoundsInRoot().bottom, "I is not alone on the last row")
    }

    @Test
    fun cardsFitAtLargeFont() = runComposeUiTest {
        setScreen(byEar, fontScale = 2f)
        for (string in byEar.strings) {
            val tag = "tuner.card.${string.numeral}"
            // The By Ear card scrolls; a lower row may start below the fold on a phone-sized window.
            onNodeWithTag(tag).performScrollTo()
            waitForIdle()
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            onNode(hasText(string.numeral) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertDisplayedWithin(bounds, "$tag numeral")
            onNode(hasText(string.note.pitchClass.label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertDisplayedWithin(bounds, "$tag note")
            onNodeWithTag("$tag.audition").assertDisplayedWithin(bounds, "$tag button")
        }
    }

    @Test
    fun loopToggleReflectsAndCallsBack() = runComposeUiTest {
        var loop: Boolean? = null
        callbacks = TunerCallbacks.None.copy(onLoopTone = { loop = it })
        val update = setScreen(byEar)
        onNodeWithTag("tuner.loop").assertIsOn()
        onNode(hasText("LOOP TONE") and hasAnyAncestor(hasTestTag("tuner.byEar")), useUnmergedTree = true).assertExists()
        onNode(hasText("Active") and hasAnyAncestor(hasTestTag("tuner.byEar")), useUnmergedTree = true).assertExists()
        onNodeWithTag("tuner.loop").performClick()
        assertEquals(false, loop)
        update(byEar.copy(loopTone = false))
        onNodeWithTag("tuner.loop").assertIsOff()
        onNode(hasText("Off") and hasAnyAncestor(hasTestTag("tuner.byEar")), useUnmergedTree = true).assertExists()
    }
}
