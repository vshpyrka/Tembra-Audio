package audio.tembra.ui.tracksettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import audio.tembra.ui.timeline.components.DELETE_CONFIRM_TAG
import audio.tembra.ui.timeline.components.DELETE_DIALOG_TAG
import audio.tembra.ui.transport.TransportCallbacks
import audio.tembra.ui.transport.TransportUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The nested track-settings screen: the title, the form's fields, delete behind its dialog, back, the transport bar (T092, US2). */
@OptIn(ExperimentalTestApi::class)
class TrackSettingsScreenTest {

    private val calls = mutableListOf<String>()
    private val state = TrackSettingsUiState(
        trackId = TrackId("t2"), name = "Guitar", label = InstrumentLabel.Guitar, colorIndex = 1, gainDb = GainDb(-2.4f),
        muted = true, soloed = false, transport = TransportUiState(lengthSeconds = 30.9677, hasProject = true),
    )

    private fun ComposeUiTest.setScreen(state: TrackSettingsUiState = this@TrackSettingsScreenTest.state) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                // The name field is controlled: the typed text comes back through the state, or the device's IME resets it.
                var current by remember { mutableStateOf(state) }
                TrackSettingsScreenContent(
                    state = current,
                    callbacks = TrackSettingsCallbacks(
                        onName = { calls += "name($it)"; current = current.copy(name = it) }, onNameCommit = { calls += "commit" }, onLabel = { calls += "label($it)" },
                        onGain = { calls += "gain(${it.readout})" }, onMute = { calls += "mute($it)" }, onSolo = { calls += "solo($it)" },
                        onDelete = { calls += "delete" }, onConfirmDelete = { calls += "confirm" }, onDismissDelete = { calls += "dismiss" },
                    ),
                    transportCallbacks = TransportCallbacks.None.copy(onPlayPause = { calls += "play" }),
                    telemetry = ScriptedTransportTelemetry.stopped(0.0),
                    onBack = { calls += "back" },
                )
            }
        }
    }

    @Test
    fun theScreenShowsTheTrackAndItsFields() = runComposeUiTest {
        setScreen()
        onNodeWithTag(TrackSettingsTestTags.ROOT).assertExists()
        onNodeWithTag(TrackSettingsTestTags.TITLE).assertTextEquals("Guitar")
        onNodeWithTag(TrackSettingsTestTags.NAME).assertTextEquals("Name", "Guitar")
        onNodeWithTag(TrackSettingsTestTags.LABEL).assertContentDescriptionEquals("Instrument, Guitar")
        onNodeWithTag(TrackSettingsTestTags.GAIN).assertExists()
        onNodeWithTag(TrackSettingsTestTags.GAIN_SLIDER).assertExists()
        onNodeWithTag(TrackSettingsTestTags.MUTE).assertIsOn()
        onNodeWithTag(TrackSettingsTestTags.SOLO).assertIsOff()
        onNodeWithTag("transport.bar").assertExists()
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:00.00 of 00:30.00")
    }

    @Test
    fun typingEditsTheNameAndTheTogglesAreIntents() = runComposeUiTest {
        setScreen()
        onNodeWithTag(TrackSettingsTestTags.NAME).performTextInput("s")
        onNodeWithTag(TrackSettingsTestTags.MUTE).performClick()
        onNodeWithTag(TrackSettingsTestTags.SOLO).performClick()
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).performClick()
        assertEquals(listOf("name(sGuitar)", "mute(false)", "solo(true)", "play"), calls)
    }

    @Test
    fun theLabelMenuOffersEveryInstrumentAndNone() = runComposeUiTest {
        setScreen()
        mainClock.autoAdvance = true // the menu is a popup that needs frames to appear
        onNodeWithTag(TrackSettingsTestTags.LABEL).performClick()
        onNodeWithText("None").performClick()
        assertEquals(listOf("label(null)"), calls)
    }

    @Test
    fun deleteAsksBeforeConfirming() = runComposeUiTest {
        setScreen()
        onNodeWithTag(TrackSettingsTestTags.DELETE).performClick()
        assertEquals(listOf("delete"), calls)
    }

    @Test
    fun thePendingDeleteShowsTheDialogAndConfirms() = runComposeUiTest {
        setScreen(state.copy(confirmDelete = true))
        mainClock.autoAdvance = true // the dialog is a popup that needs frames to appear
        onNodeWithTag(DELETE_DIALOG_TAG).assertExists()
        onNodeWithTag(DELETE_CONFIRM_TAG).performClick()
        assertEquals(listOf("confirm"), calls)
    }

    /** The Effects section is a heading and exactly one sentence, no controls (spec FR-048; T112, US6). */
    @Test
    fun theEffectsSectionIsAHeadingAndOneSentence() = runComposeUiTest {
        setScreen()
        onNodeWithTag(TrackSettingsTestTags.EFFECTS).assertExists()
        onNodeWithTag(TrackSettingsTestTags.EFFECTS_TEXT).assertTextEquals("Effects for this track arrive in a later version.")
        onNodeWithText("Effects").assertExists()
        onNode(hasAnyAncestor(hasTestTag(TrackSettingsTestTags.EFFECTS)) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun backLeavesTheScreen() = runComposeUiTest {
        setScreen()
        onNodeWithTag(TrackSettingsTestTags.CLOSE).assertContentDescriptionEquals("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
