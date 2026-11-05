package audio.tembra.ui.timeline

import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.transport.RecordButtonState
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The recording halves of [TimelineUiState] (feature 010, contracts/recording-session.md "Availability"; spec FR-007,
 * FR-018, FR-026): the availability map while a take runs, Record's state by the bar's width and the monitor toggle's
 * placement.
 */
class TimelineUiStateTest {

    private val track = TrackUi(
        id = TrackId("t1"), name = "Track 1", label = null, colorIndex = 0, gainDb = GainDb.Unity,
        muted = false, soloed = false, audible = true, clips = persistentListOf(), armed = true,
    )

    /** Feature 012 (contracts/mixer-session.md): a track carries its pan and the description names it when it is off centre. */
    @Test
    fun theDescriptionNamesThePanWhenItIsNotCentred() {
        assertEquals(Pan.Centre, track.pan)
        assertEquals("Track 1, +0.0dB, armed", track.description)
        assertEquals("Track 1, +0.0dB, L 35, armed", track.copy(pan = Pan(-35)).description)
        assertEquals("Track 1, +0.0dB, R 100, muted, armed", track.copy(pan = Pan(100), muted = true).description)
    }

    @Test
    fun theStructuralEditsAreAvailableWhileNoTakeRunsAndUnavailableDuringOne() {
        val idle = TimelineUiState(loaded = true, tracks = persistentListOf(track), canUndo = true, canRedo = true)
        assertTrue(idle.canAddTrack)
        assertTrue(idle.canImport)
        assertTrue(idle.canEditTempo)
        assertTrue(idle.canEditSignature)
        assertTrue(idle.canEditCountIn)
        assertTrue(idle.canArm)
        assertTrue(idle.canMoveOrDeleteTrack)
        assertTrue(idle.transport.seekEnabled)

        val recording = idle.copy(takeRunning = true, recordPhase = RecordButtonState.Recording)
        assertFalse(recording.canAddTrack)
        assertFalse(recording.canImport)
        assertFalse(recording.canEditTempo)
        assertFalse(recording.canEditSignature)
        assertFalse(recording.canEditCountIn)
        assertFalse(recording.canArm)
        assertFalse(recording.canMoveOrDeleteTrack)
        assertFalse(recording.transport.seekEnabled)
    }

    @Test
    fun nothingIsAvailableWithoutAProject() {
        val none = TimelineUiState()
        assertFalse(none.canAddTrack)
        assertFalse(none.canArm)
        assertFalse(none.canEditCountIn)
    }

    @Test
    fun recordIsHiddenBelow432dpAndShowsThePhaseFromThere() {
        val idle = TimelineUiState(loaded = true, recordPhase = RecordButtonState.Idle)
        assertEquals(RecordButtonState.Hidden, idle.copy(barWidth = 360.dp).recordButton)
        assertEquals(RecordButtonState.Hidden, idle.copy(barWidth = 431.dp).recordButton)
        assertEquals(RecordButtonState.Idle, idle.copy(barWidth = 432.dp).recordButton)
        assertEquals(RecordButtonState.CountIn, idle.copy(barWidth = 432.dp, recordPhase = RecordButtonState.CountIn, countInBeatsLeft = 3).recordButton)
        assertEquals(RecordButtonState.Recording, idle.copy(barWidth = 1024.dp, recordPhase = RecordButtonState.Recording).recordButton)
        // Hidden until the bar is measured: nothing is drawn twice when the first frame places it.
        assertEquals(RecordButtonState.Hidden, idle.recordButton)
    }

    @Test
    fun theTransportCarriesRecordTheCountInAndTheSeekAvailability() {
        val state = TimelineUiState(loaded = true, barWidth = 600.dp, recordPhase = RecordButtonState.CountIn, countInBeatsLeft = 2, takeRunning = true)
        val transport = state.transport
        assertEquals(RecordButtonState.CountIn, transport.record)
        assertEquals(2, transport.countInBeatsLeft)
        assertTrue(transport.isCountingIn)
        assertFalse(transport.seekEnabled)
        assertEquals("Count-in, 2 beats left", transport.recordLabel)
        assertEquals("Count-in 2", transport.positionText)
    }

    @Test
    fun theMonitorToggleMovesToTheToolbarFrom600dp() {
        val state = TimelineUiState(loaded = true)
        assertFalse(state.copy(barWidth = 599.dp).monitorInToolbar)
        assertTrue(state.copy(barWidth = 600.dp).monitorInToolbar)
    }

    @Test
    fun theArmedTrackIsTheOneLit() {
        val state = TimelineUiState(loaded = true, tracks = persistentListOf(track.copy(armed = false), track.copy(id = TrackId("t2"), armed = true)))
        assertEquals(TrackId("t2"), state.armedTrack)
        assertEquals(null, state.copy(tracks = persistentListOf(track.copy(armed = false))).armedTrack)
    }
}
