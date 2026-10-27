package audio.tembra.ui.session

import audio.tembra.core.project.Beats
import audio.tembra.core.project.Project
import audio.tembra.core.project.RecordTake

/**
 * What the [RecordingController] needs from the project session (feature 010, contracts/recording-session.md): the
 * open document and the transport's position, the edit a finished take becomes, the pause a cancelled count-in
 * needs, the switch to Playing a started take needs, and the message channel. The session implements it and attaches
 * itself in its `start()`.
 */
interface RecordingHost {
    val project: Project?
    val isPlaying: Boolean

    /** The transport position now, in beats. */
    fun positionBeats(): Beats

    /** The engine's transport frame through the project's tempo map. */
    fun framesToBeats(frames: Long, sampleRate: Int): Beats

    /** Applies the finished take as one undo step: the clip source at [audioPath] is opened first and its overview at [overviewPath] tracked. */
    fun applyTake(edit: RecordTake, audioPath: String, overviewPath: String)

    /** The take began capturing: playback is running now (the pre-roll ended, or Record was pressed while stopped). */
    fun playbackStartedByTake()

    /** The count-in was cancelled: the transport is stopped at the restored position. */
    fun playbackStoppedByCancel()

    fun notify(message: SessionMessage)
}
