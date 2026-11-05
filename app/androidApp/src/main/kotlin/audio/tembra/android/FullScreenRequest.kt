package audio.tembra.android

/**
 * One request for full screen or back (feature 011, FR-010a, T141), over the system call [send]: the Desktop (Preview)
 * image refuses a request sent while the menu that asked for it is still closing ("Fullscreen request denied by system
 * policy", release build, window animations on) and accepts the same request a moment later. A refusal while the
 * window is still in the mode it is leaving ([windowed] for an enter request, not windowed for an exit) is therefore
 * sent once more [RETRY_DELAY_MS] [later]; a second refusal, or one after the window already changed (the shell can
 * carry a request out and still call it refused), goes to the caller.
 */
internal class FullScreenRequest(
    private val send: (enter: Boolean, onApproved: () -> Unit, onRefused: (Throwable) -> Unit) -> Unit,
    private val later: (delayMs: Long, action: () -> Unit) -> Unit,
    private val windowed: () -> Boolean,
) {
    fun request(enter: Boolean, onApproved: () -> Unit, onRefused: (Throwable) -> Unit) {
        send(enter, onApproved) { error ->
            if (windowed() == enter) later(RETRY_DELAY_MS) { send(enter, onApproved, onRefused) } else onRefused(error)
        }
    }
}

/** How long after a refusal the request is sent again: past the closing menu's window (300 ms worked, T141). */
internal const val RETRY_DELAY_MS = 300L
