package audio.tembra.core.project

/**
 * The transport's state as the session publishes it (contracts/project-session.md), the metronome's shape: the
 * stopped position is known here, the playing position only through the engine's telemetry. [Starting] keeps the
 * position so a failed start returns to it.
 */
sealed interface PlaybackState {
    data class Stopped(val positionBeats: Beats) : PlaybackState

    data class Starting(val positionBeats: Beats) : PlaybackState

    data object Playing : PlaybackState

    data object Pausing : PlaybackState
}
