package audio.tembra.ui.tracksettings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.core.timeline.TimelineViewport
import audio.tembra.ui.navigation.TestViewModelFactory
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineScreenContent
import audio.tembra.ui.timeline.TimelineTestTags
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.timeline.TrackUi
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** The expanded layout's inline settings (spec FR-041; T112, US6): the chevron expands under the header, the form sits inside, one row at a time. */
@OptIn(ExperimentalTestApi::class)
class TrackSettingsInlineRowTest {

    private class Owner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    private val drums = TrackId("t1")
    private val bass = TrackId("t2")
    private val calls = mutableListOf<String>()

    private fun track(id: TrackId, name: String, colorIndex: Int) = TrackUi(
        id = id, name = name, label = InstrumentLabel.Drums, colorIndex = colorIndex, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(),
    )

    private fun state(expanded: TrackId?) = TimelineUiState(
        loaded = true, projectName = "Demo", tempo = Tempo(124), timeSignature = TimeSignature.FourFour,
        tracks = persistentListOf(track(drums, "Drums", 0), track(bass, "Bass", 1)), lengthBeats = Beats(64.0), lengthSeconds = 30.9677,
        viewport = TimelineViewport.fitted(800.0, 1.0, Beats(64.0), 4), isFirstContentReady = true, expandedTrack = expanded,
    )

    @Test
    fun theChevronOpensTheInlineRowUnderTheHeaderAndAnotherTrackReplacesIt() = runComposeUiTest {
        setContent {
            val owner = remember { Owner() }
            val factory = remember { TestViewModelFactory() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalMetroViewModelFactory provides factory) {
                TembraTheme {
                    var expanded by remember { mutableStateOf<TrackId?>(null) }
                    TimelineScreenContent(
                        state = state(expanded),
                        callbacks = TimelineCallbacks.None.copy(onExpandTrack = { calls += "expand(${it?.value})"; expanded = it }),
                        telemetry = ScriptedTransportTelemetry.stopped(0.0),
                        layout = TimelineLayout.Expanded,
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
        onNodeWithTag(TrackSettingsTestTags.INLINE).assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.chevron(0)).performClick()
        onNodeWithTag(TrackSettingsTestTags.INLINE).assertExists()
        onNodeWithTag(TrackSettingsTestTags.FORM).assertExists()
        // On a phone-sized window the open form pushes the second header below the fold; a tap must land on screen.
        onNodeWithTag(TimelineTestTags.TRACK_LIST).performScrollToNode(hasTestTag(TimelineTestTags.chevron(1)))
        onNodeWithTag(TimelineTestTags.chevron(1)).performClick()
        assertEquals(listOf("expand(t1)", "expand(t2)"), calls)
        onNodeWithTag(TrackSettingsTestTags.INLINE).assertExists()
        onNodeWithTag(TrackSettingsTestTags.CLOSE).performClick()
        assertEquals(listOf("expand(t1)", "expand(t2)", "expand(null)"), calls)
        onNodeWithTag(TrackSettingsTestTags.INLINE).assertDoesNotExist()
    }
}
