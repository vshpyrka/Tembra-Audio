package audio.tembra.ui.transport

import audio.tembra.core.project.Beats
import audio.tembra.core.project.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The transport bar's derived labels and flags (contracts/timeline-screen.md). */
class TransportUiStateTest {

    @Test
    fun theReadoutTruncatesHundredths() {
        val state = TransportUiState(positionSeconds = 0.0, lengthSeconds = 30.9677, hasProject = true)
        assertEquals("00:00.00 / 00:30.96", state.readoutText)
        assertEquals("00:42.20 / 00:30.96", state.copy(positionSeconds = 42.209).readoutText)
    }

    @Test
    fun playLabelFollowsThePlaybackState() {
        val stopped = TransportUiState(playback = PlaybackState.Stopped(Beats.Zero), hasProject = true)
        assertEquals(TransportCopy.play, stopped.playLabel)
        assertFalse(stopped.isPlaying)
        assertEquals(TransportCopy.pause, stopped.copy(playback = PlaybackState.Playing).playLabel)
        assertTrue(stopped.copy(playback = PlaybackState.Playing).isPlaying)
        assertTrue(stopped.copy(playback = PlaybackState.Starting(Beats.Zero)).isPlaying)
        assertFalse(stopped.copy(playback = PlaybackState.Pausing).isPlaying)
    }

    @Test
    fun metronomeLabelSaysWhatATapDoes() {
        assertEquals(TransportCopy.metronomeOn, TransportUiState(metronomeOn = false).metronomeLabel)
        assertEquals(TransportCopy.metronomeOff, TransportUiState(metronomeOn = true).metronomeLabel)
    }

    @Test
    fun transportControlsAreEnabledOnlyWithAProject() {
        assertFalse(TransportUiState().transportEnabled)
        assertTrue(TransportUiState(hasProject = true).transportEnabled)
        assertEquals(80, TransportUiState().masterVolume)
    }
}
