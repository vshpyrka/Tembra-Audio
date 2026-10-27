package audio.tembra.core.project

/**
 * What reaches the playback policy: user intents (Play, Pause, Seek, SkipToStart, StepBar, LeaveConfirmed), the
 * engine's answers (Started, Failed, Paused) and the platform's signals (Interrupted, FocusLost, RouteLost, HostStop,
 * InterruptionEnded). [StepBar] carries the current position and the bar length because the policy is pure and,
 * while playing, the position lives in the engine.
 */
sealed interface PlaybackEvent {
    data object Play : PlaybackEvent

    data object Started : PlaybackEvent

    data object Failed : PlaybackEvent

    data object Pause : PlaybackEvent

    data class Paused(val positionBeats: Beats) : PlaybackEvent

    data object Interrupted : PlaybackEvent

    data object FocusLost : PlaybackEvent

    data object RouteLost : PlaybackEvent

    data object HostStop : PlaybackEvent

    data object LeaveConfirmed : PlaybackEvent

    data object InterruptionEnded : PlaybackEvent

    data class Seek(val beats: Beats) : PlaybackEvent

    data object SkipToStart : PlaybackEvent

    data class StepBar(val delta: Int, val fromBeats: Beats, val beatsPerBar: Int) : PlaybackEvent
}
