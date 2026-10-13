package audio.tembra.ui.session

/** A question the session needs the shell to put to the user (contracts/project-session.md). */
sealed interface DialogRequest {
    /** "Stop playback?": [answer] with true for "Stop and leave", false for "Keep playing". */
    class StopAndLeave(val answer: (Boolean) -> Unit) : DialogRequest
}
