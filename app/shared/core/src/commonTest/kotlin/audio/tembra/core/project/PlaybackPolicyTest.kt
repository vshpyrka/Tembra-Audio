package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** Every row of the playback policy table in contracts/project-session.md (spec FR-005, FR-006, FR-008, FR-008a). */
class PlaybackPolicyTest {

    private val stopped = PlaybackState.Stopped(Beats(16.0))

    private fun step(state: PlaybackState, event: PlaybackEvent): PlaybackOutcome = PlaybackPolicy.onEvent(state, event)

    @Test
    fun stoppedPlusPlayStarts() {
        assertEquals(PlaybackOutcome(PlaybackState.Starting(Beats(16.0)), PlaybackTransition.Start), step(stopped, PlaybackEvent.Play))
    }

    @Test
    fun startingPlusStartedIsPlaying() {
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.BecomePlaying), step(PlaybackState.Starting(Beats(16.0)), PlaybackEvent.Started))
    }

    @Test
    fun startingPlusFailedStopsWithAMessage() {
        assertEquals(PlaybackOutcome(stopped, PlaybackTransition.StopWithMessage), step(PlaybackState.Starting(Beats(16.0)), PlaybackEvent.Failed))
    }

    @Test
    fun playingPlusAnyStopSignalPauses() {
        val signals = listOf(
            PlaybackEvent.Pause, PlaybackEvent.HostStop, PlaybackEvent.Interrupted,
            PlaybackEvent.FocusLost, PlaybackEvent.RouteLost, PlaybackEvent.LeaveConfirmed,
        )
        for (signal in signals) {
            assertEquals(PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.Pause), step(PlaybackState.Playing, signal), "$signal")
        }
    }

    @Test
    fun pausingPlusPausedIsStoppedAtThePausePosition() {
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats(20.5)), PlaybackTransition.None), step(PlaybackState.Pausing, PlaybackEvent.Paused(Beats(20.5))))
    }

    @Test
    fun interruptionEndedNeverResumes() {
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.None), step(PlaybackState.Playing, PlaybackEvent.InterruptionEnded))
        assertEquals(PlaybackOutcome(stopped, PlaybackTransition.None), step(stopped, PlaybackEvent.InterruptionEnded))
        assertEquals(PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.None), step(PlaybackState.Pausing, PlaybackEvent.InterruptionEnded))
    }

    @Test
    fun stoppedPlusInterruptionSignalsStaysStopped() {
        for (signal in listOf(PlaybackEvent.Interrupted, PlaybackEvent.FocusLost, PlaybackEvent.RouteLost, PlaybackEvent.HostStop, PlaybackEvent.Pause, PlaybackEvent.LeaveConfirmed)) {
            assertEquals(PlaybackOutcome(stopped, PlaybackTransition.None), step(stopped, signal), "$signal")
        }
    }

    @Test
    fun seekKeepsTheStateAndMovesTheStoppedPosition() {
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats(3.0)), PlaybackTransition.Seek(Beats(3.0))), step(stopped, PlaybackEvent.Seek(Beats(3.0))))
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.Seek(Beats(3.0))), step(PlaybackState.Playing, PlaybackEvent.Seek(Beats(3.0))))
        assertEquals(PlaybackOutcome(PlaybackState.Starting(Beats(3.0)), PlaybackTransition.Seek(Beats(3.0))), step(PlaybackState.Starting(Beats(16.0)), PlaybackEvent.Seek(Beats(3.0))))
        assertEquals(PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.Seek(Beats(3.0))), step(PlaybackState.Pausing, PlaybackEvent.Seek(Beats(3.0))))
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats.Zero), PlaybackTransition.Seek(Beats.Zero)), step(stopped, PlaybackEvent.Seek(Beats(-2.0))))
    }

    @Test
    fun skipToStartSeeksToZeroInAnyState() {
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats.Zero), PlaybackTransition.Seek(Beats.Zero)), step(stopped, PlaybackEvent.SkipToStart))
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.Seek(Beats.Zero)), step(PlaybackState.Playing, PlaybackEvent.SkipToStart))
    }

    @Test
    fun stepBarSeeksToTheNextOrPreviousBarLine() {
        val forward = PlaybackEvent.StepBar(delta = 1, fromBeats = Beats(17.5), beatsPerBar = 4)
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats(20.0)), PlaybackTransition.Seek(Beats(20.0))), step(stopped, forward))
        val backMidBar = PlaybackEvent.StepBar(delta = -1, fromBeats = Beats(17.5), beatsPerBar = 4)
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats(16.0)), PlaybackTransition.Seek(Beats(16.0))), step(stopped, backMidBar))
        val backOnBar = PlaybackEvent.StepBar(delta = -1, fromBeats = Beats(16.0), beatsPerBar = 4)
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats(12.0)), PlaybackTransition.Seek(Beats(12.0))), step(stopped, backOnBar))
        val beforeStart = PlaybackEvent.StepBar(delta = -1, fromBeats = Beats(2.0), beatsPerBar = 4)
        assertEquals(PlaybackOutcome(PlaybackState.Stopped(Beats.Zero), PlaybackTransition.Seek(Beats.Zero)), step(stopped, beforeStart))
        val whilePlaying = PlaybackEvent.StepBar(delta = 1, fromBeats = Beats(8.0), beatsPerBar = 6)
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.Seek(Beats(12.0))), step(PlaybackState.Playing, whilePlaying))
    }

    @Test
    fun outOfOrderEventsAreIgnored() {
        assertEquals(PlaybackOutcome(stopped, PlaybackTransition.None), step(stopped, PlaybackEvent.Started))
        assertEquals(PlaybackOutcome(stopped, PlaybackTransition.None), step(stopped, PlaybackEvent.Paused(Beats(1.0))))
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.None), step(PlaybackState.Playing, PlaybackEvent.Play))
        assertEquals(PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.None), step(PlaybackState.Playing, PlaybackEvent.Started))
        assertEquals(PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.None), step(PlaybackState.Pausing, PlaybackEvent.Play))
        assertEquals(PlaybackOutcome(PlaybackState.Starting(Beats(16.0)), PlaybackTransition.None), step(PlaybackState.Starting(Beats(16.0)), PlaybackEvent.Play))
    }

    @Test
    fun aStopSignalWhileStartingPauses() {
        assertEquals(PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.Pause), step(PlaybackState.Starting(Beats(16.0)), PlaybackEvent.Interrupted))
        assertEquals(PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.Pause), step(PlaybackState.Starting(Beats(16.0)), PlaybackEvent.Pause))
    }
}
