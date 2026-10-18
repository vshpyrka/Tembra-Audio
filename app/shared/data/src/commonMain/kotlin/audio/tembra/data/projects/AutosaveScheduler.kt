package audio.tembra.data.projects

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Autosave timing (contracts/project-storage.md, Autosave; spec FR-046): [onEdit] schedules a save [debounceMs]
 * later, a further edit moves it, and a save is forced [capMs] after the first unsaved edit; [flush] saves at once
 * when anything is pending. Saves serialise on one mutex; an edit during a save yields exactly one more save after
 * it. Concurrency contract (`kotlin-concurrency-and-flow`): the owner passes the scope whose lifetime bounds the
 * work (the session's) and starts the one named launch site with [start]; the constructor launches nothing;
 * [onEdit] is called on the owner's thread; [saving] is renderable state with a synchronous `.value`; the edit
 * signal is a conflated channel because only the fact that something changed matters.
 */
class AutosaveScheduler(
    private val scope: CoroutineScope,
    private val save: suspend () -> Unit,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val capMs: Long = DEFAULT_CAP_MS,
) {
    private val edits = Channel<Unit>(Channel.CONFLATED)
    private val saveMutex = Mutex()
    private val _saving = MutableStateFlow(false)
    private var dirty = false
    private var job: Job? = null

    /** True while [save] runs. */
    val saving: StateFlow<Boolean> = _saving

    /** Starts the scheduling loop on the owner's scope; call once. */
    fun start(): Job {
        check(job == null) { "AutosaveScheduler is already started" }
        return scope.launch(CoroutineName("autosave")) { run() }.also { job = it }
    }

    /** Marks the project dirty and (re)schedules the save. */
    fun onEdit() {
        dirty = true
        edits.trySend(Unit)
    }

    /** Saves now when an edit is pending, after any save in flight; returns when the save is done. */
    suspend fun flush() = performSave()

    private suspend fun run() {
        for (firstEdit in edits) {
            withTimeoutOrNull(capMs) { debounce() }
            performSave()
        }
    }

    /** Returns when [debounceMs] pass without a further edit. */
    private suspend fun debounce() {
        while (true) {
            withTimeoutOrNull(debounceMs) { edits.receive() } ?: return
        }
    }

    private suspend fun performSave() {
        saveMutex.withLock {
            if (!dirty) return
            dirty = false
            _saving.value = true
            try {
                save()
            } finally {
                _saving.value = false
            }
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 500L
        const val DEFAULT_CAP_MS = 2_000L
    }
}
