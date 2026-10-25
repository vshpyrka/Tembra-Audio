package audio.tembra.ui.session

import kotlinx.coroutines.flow.StateFlow

/**
 * What the navigation shell asks before leaving the Timeline while a project plays (spec FR-008a,
 * contracts/project-session.md): [isPlaying] now, and [confirmLeave], which puts the stop-and-leave question in
 * [dialog], suspends until the user answers, and on "Stop and leave" waits for playback to reach Stopped before
 * returning true. The [ProjectSession] implements it; the shell renders [dialog].
 *
 * [dialog] is a `StateFlow` (renderable state with a current value, `kotlin-concurrency-and-flow`), not the
 * contract's channel: the question stays visible until answered and a late collector must still see it.
 */
interface PlaybackGuard {
    val isPlaying: Boolean

    suspend fun confirmLeave(): Boolean

    /** The question awaiting an answer, or null. */
    val dialog: StateFlow<DialogRequest?>

    /**
     * Whether the destination the shell is about to show hosts the transport (`Destination.hostsTransport`). The
     * engine's states are exclusive, so while the Timeline is not on screen the session hands the audio device over
     * (synchronously, before the tuner or the standalone metronome opens its stream) and takes it back, at the stopped
     * position, when the Timeline returns; the project stays open throughout.
     */
    fun transportHostVisible(visible: Boolean)
}
