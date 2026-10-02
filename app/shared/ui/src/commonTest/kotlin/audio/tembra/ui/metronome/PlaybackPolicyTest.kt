package audio.tembra.ui.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Every row of data-model.md §7 and contracts/background-playback.md: nothing ever restarts by itself (spec FR-027). */
class PlaybackPolicyTest {

    private val stopEvents = listOf(
        PlaybackEvent.PausePressed,
        PlaybackEvent.ScreenHidden,
        PlaybackEvent.AudioInterrupted,
        PlaybackEvent.OutputRouteLost,
        PlaybackEvent.DeviceLost,
        PlaybackEvent.StopRequested,
    )
    private val allEvents: List<PlaybackEvent> = listOf(PlaybackEvent.PlayPressed) + stopEvents +
        StartFailure.entries.map { PlaybackEvent.StartFailed(it) }

    @Test
    fun playFromStoppedStarts() {
        assertEquals(PlaybackTransition.Start, PlaybackPolicy.onEvent(PlaybackState.Stopped, PlaybackEvent.PlayPressed))
    }

    @Test
    fun everyStopEventStopsARunningMetronomeWithoutAMessage() {
        for (event in stopEvents) {
            assertEquals(PlaybackTransition.Stop(message = null), PlaybackPolicy.onEvent(PlaybackState.Running, event), "Running + $event")
        }
    }

    @Test
    fun everyStopEventCancelsAStartWithoutAMessage() {
        for (event in stopEvents) {
            assertEquals(PlaybackTransition.Stop(message = null), PlaybackPolicy.onEvent(PlaybackState.Starting, event), "Starting + $event")
        }
    }

    @Test
    fun playWhileStartingIsIgnored() {
        assertEquals(PlaybackTransition.Ignore, PlaybackPolicy.onEvent(PlaybackState.Starting, PlaybackEvent.PlayPressed))
    }

    @Test
    fun aFocusDenialStopsTheStartWithFocusDenied() {
        assertEquals(
            PlaybackTransition.Stop(MetronomeMessage.FocusDenied),
            PlaybackPolicy.onEvent(PlaybackState.Starting, PlaybackEvent.StartFailed(StartFailure.FocusDenied)),
        )
    }

    /** The foreground service was refused: not another app holding the audio, so the output is reported unavailable. */
    @Test
    fun aRefusedHostStopsTheStartWithDeviceUnavailable() {
        assertEquals(
            PlaybackTransition.Stop(MetronomeMessage.DeviceUnavailable),
            PlaybackPolicy.onEvent(PlaybackState.Starting, PlaybackEvent.StartFailed(StartFailure.HostRefused)),
        )
    }

    @Test
    fun anEngineErrorStopsTheStartWithDeviceUnavailable() {
        assertEquals(
            PlaybackTransition.Stop(MetronomeMessage.DeviceUnavailable),
            PlaybackPolicy.onEvent(PlaybackState.Starting, PlaybackEvent.StartFailed(StartFailure.EngineError)),
        )
    }

    @Test
    fun anInterruptionWhileStoppedIsIgnored() {
        assertEquals(PlaybackTransition.Ignore, PlaybackPolicy.onEvent(PlaybackState.Stopped, PlaybackEvent.AudioInterrupted))
    }

    @Test
    fun playWhileRunningIsIgnored() {
        assertEquals(PlaybackTransition.Ignore, PlaybackPolicy.onEvent(PlaybackState.Running, PlaybackEvent.PlayPressed))
    }

    @Test
    fun onlyPlayPressedStartsFromStopped() {
        for (event in allEvents - PlaybackEvent.PlayPressed) {
            assertNotEquals(PlaybackTransition.Start, PlaybackPolicy.onEvent(PlaybackState.Stopped, event), "Stopped + $event")
        }
    }

    @Test
    fun nothingButPlayPressedFromStoppedEverStarts() {
        for (state in PlaybackState.entries) {
            for (event in allEvents) {
                val transition = PlaybackPolicy.onEvent(state, event)
                if (transition == PlaybackTransition.Start) {
                    assertEquals(PlaybackState.Stopped to PlaybackEvent.PlayPressed, state to event, "$state + $event restarts playback")
                }
            }
        }
    }
}
