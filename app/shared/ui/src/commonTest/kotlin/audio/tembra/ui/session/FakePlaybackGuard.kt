package audio.tembra.ui.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A guard tests script: whether a project plays and the answer to the next stop-and-leave question. */
class FakePlaybackGuard(override var isPlaying: Boolean = false) : PlaybackGuard {
    private val _dialog = MutableStateFlow<DialogRequest?>(null)
    override val dialog: StateFlow<DialogRequest?> = _dialog

    /** How many times [confirmLeave] was asked. */
    var asked: Int = 0
        private set

    private var pending: CompletableDeferred<Boolean>? = null

    /** True while a take runs (feature 010, FR-022): the question then carries the recording variant. */
    var recording: Boolean = false

    /** Every [transportHostVisible] call, in order. */
    val hostVisibility = mutableListOf<Boolean>()

    override fun transportHostVisible(visible: Boolean) {
        hostVisibility += visible
    }

    override suspend fun confirmLeave(): Boolean {
        asked++
        val answer = CompletableDeferred<Boolean>()
        pending = answer
        _dialog.value = DialogRequest.StopAndLeave(recording = recording) { result -> answerLeave(result) }
        return answer.await()
    }

    /** Answers the pending question as the user would: true stops playback and leaves, false keeps playing. */
    fun answerLeave(result: Boolean) {
        val answer = pending ?: return
        pending = null
        _dialog.value = null
        if (result) isPlaying = false
        answer.complete(result)
    }
}
