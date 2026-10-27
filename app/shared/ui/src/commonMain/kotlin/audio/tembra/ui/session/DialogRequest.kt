package audio.tembra.ui.session

/** A question the session needs the shell to put to the user (contracts/project-session.md). */
sealed interface DialogRequest {
    /**
     * "Stop playback?": [answer] with true for "Stop and leave", false for "Keep playing". With [recording] (feature
     * 010, FR-022) the copy reads "Stop recording?" with "Stop recording and leave" / "Keep recording".
     */
    class StopAndLeave(val recording: Boolean = false, val answer: (Boolean) -> Unit) : DialogRequest
}
