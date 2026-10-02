package audio.tembra.engine

/** Outcome of an engine command; [Error.code] is the `tembra_error` value. */
sealed interface AudioResult {
    data object Ok : AudioResult

    data class Error(val code: Int) : AudioResult

    companion object {
        /** Android only: audio focus was refused, so the metronome did not start (not a `tembra_error`). */
        const val FOCUS_DENIED: Int = -100

        fun fromNative(code: Int): AudioResult = if (code == 0) Ok else Error(code)
    }
}
