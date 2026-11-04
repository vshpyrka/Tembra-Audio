package audio.tembra.ui.session

import audio.tembra.core.project.Beats
import audio.tembra.core.project.Project
import audio.tembra.core.project.RecordTake
import audio.tembra.core.project.tempoMap

/** A scripted session for the controller tests: the open project, the position, and a log of what the controller asked for. */
class FakeRecordingHost(override var project: Project?, override var isPlaying: Boolean = false) : RecordingHost {
    var position: Beats = Beats.Zero
    val appliedTakes = mutableListOf<Triple<RecordTake, String, String>>()
    val messages = mutableListOf<SessionMessage>()
    var playbackStarted: Int = 0
        private set
    var playbackStoppedByCancel: Int = 0
        private set

    override fun positionBeats(): Beats = position

    override fun framesToBeats(frames: Long, sampleRate: Int): Beats = project?.tempoMap?.framesToBeats(frames, sampleRate) ?: Beats.Zero

    override suspend fun applyTake(edit: RecordTake, audioPath: String, overviewPath: String) {
        appliedTakes += Triple(edit, audioPath, overviewPath)
    }

    override fun playbackStartedByTake() {
        playbackStarted++
        isPlaying = true
    }

    override fun playbackStoppedByCancel() {
        playbackStoppedByCancel++
    }

    override fun notify(message: SessionMessage) {
        messages += message
    }
}
