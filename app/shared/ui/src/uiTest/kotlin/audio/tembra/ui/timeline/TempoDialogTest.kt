package audio.tembra.ui.timeline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.metronome.Tempo
import audio.tembra.ui.metronome.TapCaption
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.components.TEMPO_DIAL_TAG
import audio.tembra.ui.timeline.components.TEMPO_DIALOG_TAG
import audio.tembra.ui.timeline.components.TEMPO_DONE_TAG
import audio.tembra.ui.timeline.components.TEMPO_DOWN_TAG
import audio.tembra.ui.timeline.components.TEMPO_SLIDER_TAG
import audio.tembra.ui.timeline.components.TEMPO_TAP_TAG
import audio.tembra.ui.timeline.components.TEMPO_UP_TAG
import audio.tembra.ui.timeline.components.TempoDialog
import kotlin.test.Test
import kotlin.test.assertEquals

/** The project tempo dialog (spec FR-035; T108, US5): the metronome's controls under timeline tags, steppers live, Done commits. */
@OptIn(ExperimentalTestApi::class)
class TempoDialogTest {

    @Test
    fun theControlsCarryTimelineTagsAndTheSteppersChangeTheTempoLive() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            TembraTheme {
                TempoDialog(
                    tempo = Tempo(124), tapCaption = TapCaption.Rest,
                    onTempoDragging = { calls += "drag(${it.bpm})" }, onTempoCommit = { calls += "commit(${it.bpm})" },
                    onTapTempo = { calls += "tap" }, onDismiss = { calls += "dismiss" },
                )
            }
        }
        onNodeWithTag(TEMPO_DIALOG_TAG).assertExists()
        onNodeWithTag(TEMPO_DIAL_TAG).assertExists()
        onNodeWithTag(TEMPO_SLIDER_TAG).assertExists()
        onNodeWithTag(TEMPO_UP_TAG).assertContentDescriptionEquals("Tempo up").performClick()
        onNodeWithTag(TEMPO_DOWN_TAG).assertContentDescriptionEquals("Tempo down").performClick()
        onNodeWithTag(TEMPO_TAP_TAG).performClick()
        onNodeWithTag(TEMPO_DONE_TAG).performClick()
        assertEquals(listOf("drag(125)", "drag(123)", "tap", "commit(124)", "dismiss"), calls)
    }
}
