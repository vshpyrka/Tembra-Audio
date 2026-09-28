package audio.tembra.ui.tuner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.tuner.Note
import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.AudioState
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.isCloserTo
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The sounding card and its button (spec FR-018, FR-025; User Story 3). */
@OptIn(ExperimentalTestApi::class)
class TunerScreenPlayingTest {

    private val telemetry = ScriptedTelemetry()
    private var callbacks = TunerCallbacks.None
    private var secondary = Color.Unspecified
    private var outlineVariant = Color.Unspecified

    private val byEar = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, mode = TunerMode.ByEar)
    private val playing = byEar.copy(audition = AuditionState(0, true, ReferenceSound.Synth(Note.parse("E2").frequencyHz())), audio = AudioState.Tone)

    private fun ComposeUiTest.setScreen(state: TunerUiState) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                outlineVariant = TembraTheme.colors.outlineVariant
                TunerScreenContent(state = state, callbacks = callbacks, telemetry = telemetry, layout = TunerLayout.Stacked)
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    @Test
    fun auditionButtonCallsBack() = runComposeUiTest {
        var index: Int? = null
        callbacks = TunerCallbacks.None.copy(onAudition = { index = it })
        setScreen(byEar)
        onNodeWithTag("tuner.card.VI.audition").assertContentDescriptionEquals("Audition E2").performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        assertEquals(0, index)
    }

    @Test
    fun playingCardState() = runComposeUiTest {
        var stopped = false
        callbacks = TunerCallbacks.None.copy(onStopAudition = { stopped = true })
        setScreen(playing)
        onNodeWithTag("tuner.card.VI").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
        onNodeWithTag("tuner.card.VI.audition").assert(hasText("Playing")).assertContentDescriptionEquals("Stop E2")
        onNodeWithTag("tuner.card.V.audition").assert(hasText("Audition"))
        onNodeWithTag("tuner.pill").assertContentDescriptionEquals("Tone out")
        onNodeWithTag("tuner.card.VI.audition").performClick()
        repeat(2) { mainClock.advanceTimeByFrame() }
        assertTrue(stopped)
    }

    @Test
    fun playingCardIsOutlined() = runComposeUiTest {
        setScreen(playing)
        val image = onNodeWithTag("tuner.card.VI").captureToImage()
        val border = image.averageColorAt(image.width / 2, 0, radius = 0)
        assertTrue(border.isCloserTo(secondary, outlineVariant), "playing card border $border is not secondary")
        val idle = onNodeWithTag("tuner.card.V").captureToImage()
        val idleBorder = idle.averageColorAt(idle.width / 2, 0, radius = 0)
        assertTrue(idleBorder.isCloserTo(outlineVariant, secondary) || idleBorder.alpha < 1f, "idle card border $idleBorder looks highlighted")
    }
}
