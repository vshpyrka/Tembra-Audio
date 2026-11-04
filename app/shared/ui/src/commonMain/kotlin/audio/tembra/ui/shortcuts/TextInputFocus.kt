package audio.tembra.ui.shortcuts

/**
 * Whether a text field has keyboard focus (feature 011, spec FR-023): the shell's chords wait while one does, so its
 * characters are typed. The app's text fields report through [LocalTextInputFocus]. Read only in key handlers, never
 * in composition, so it is plain state.
 */
class TextInputFocus {
    var focused: Boolean = false
        private set

    fun report(focused: Boolean) {
        this.focused = focused
    }
}
