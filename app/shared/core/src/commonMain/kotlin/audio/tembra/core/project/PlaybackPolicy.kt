package audio.tembra.core.project

import kotlin.math.abs
import kotlin.math.floor

/**
 * The playback policy table of contracts/project-session.md as a pure, exhaustive function (spec FR-005, FR-006,
 * FR-008, FR-008a): every event is classified by a subject `when` with no `else` (`kotlin-control-flow`). An
 * interruption never resumes by itself; a stop signal while stopped is ignored; seeks keep the state and move the
 * stopped position; an out-of-order engine answer is ignored.
 */
object PlaybackPolicy {

    fun onEvent(state: PlaybackState, event: PlaybackEvent): PlaybackOutcome = when (event) {
        PlaybackEvent.Play -> when (state) {
            is PlaybackState.Stopped -> PlaybackOutcome(PlaybackState.Starting(state.positionBeats), PlaybackTransition.Start)
            is PlaybackState.Starting, PlaybackState.Playing, PlaybackState.Pausing -> stay(state)
        }
        PlaybackEvent.Started -> when (state) {
            is PlaybackState.Starting -> PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.BecomePlaying)
            is PlaybackState.Stopped, PlaybackState.Playing, PlaybackState.Pausing -> stay(state)
        }
        PlaybackEvent.Failed -> when (state) {
            is PlaybackState.Starting -> PlaybackOutcome(PlaybackState.Stopped(state.positionBeats), PlaybackTransition.StopWithMessage)
            is PlaybackState.Stopped, PlaybackState.Playing, PlaybackState.Pausing -> stay(state)
        }
        PlaybackEvent.Pause,
        PlaybackEvent.HostStop,
        PlaybackEvent.Interrupted,
        PlaybackEvent.FocusLost,
        PlaybackEvent.RouteLost,
        PlaybackEvent.LeaveConfirmed,
        -> when (state) {
            PlaybackState.Playing, is PlaybackState.Starting -> PlaybackOutcome(PlaybackState.Pausing, PlaybackTransition.Pause)
            is PlaybackState.Stopped, PlaybackState.Pausing -> stay(state)
        }
        is PlaybackEvent.Paused -> when (state) {
            PlaybackState.Pausing -> PlaybackOutcome(PlaybackState.Stopped(event.positionBeats), PlaybackTransition.None)
            is PlaybackState.Stopped, is PlaybackState.Starting, PlaybackState.Playing -> stay(state)
        }
        PlaybackEvent.InterruptionEnded -> stay(state)
        PlaybackEvent.RecordStarted -> when (state) {
            is PlaybackState.Stopped, is PlaybackState.Starting -> PlaybackOutcome(PlaybackState.Playing, PlaybackTransition.BecomePlaying)
            PlaybackState.Playing, PlaybackState.Pausing -> stay(state)
        }
        is PlaybackEvent.Seek -> seekTo(state, event.beats.coerceAtLeastZero())
        PlaybackEvent.SkipToStart -> seekTo(state, Beats.Zero)
        is PlaybackEvent.StepBar -> seekTo(state, barLineFrom(event))
    }

    private fun stay(state: PlaybackState): PlaybackOutcome = PlaybackOutcome(state, PlaybackTransition.None)

    private fun seekTo(state: PlaybackState, target: Beats): PlaybackOutcome {
        val next = when (state) {
            is PlaybackState.Stopped -> PlaybackState.Stopped(target)
            is PlaybackState.Starting -> PlaybackState.Starting(target)
            PlaybackState.Playing -> PlaybackState.Playing
            PlaybackState.Pausing -> PlaybackState.Pausing
        }
        return PlaybackOutcome(next, PlaybackTransition.Seek(target))
    }

    /**
     * The next bar line for a positive step; for a negative one the previous bar line, which is the current bar's start
     * when the position is inside the bar and the bar before when it sits on the line (spec FR-006). Never before zero.
     */
    private fun barLineFrom(event: PlaybackEvent.StepBar): Beats {
        val beatsPerBar = event.beatsPerBar.toDouble()
        val from = event.fromBeats.value
        val bar = floor(from / beatsPerBar + EPSILON)
        val onLine = abs(from - bar * beatsPerBar) < EPSILON
        val targetBar = if (event.delta >= 0 || onLine) bar + event.delta else bar + event.delta + 1
        return Beats(targetBar * beatsPerBar).coerceAtLeastZero()
    }

    private const val EPSILON = 1e-9
}
