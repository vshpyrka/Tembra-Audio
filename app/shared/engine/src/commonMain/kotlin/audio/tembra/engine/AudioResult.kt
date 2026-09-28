package audio.tembra.engine

/** Outcome of an engine command; [Error.code] is the `tembra_error` value. */
sealed interface AudioResult {
    data object Ok : AudioResult

    data class Error(val code: Int) : AudioResult

    companion object {
        fun fromNative(code: Int): AudioResult = if (code == 0) Ok else Error(code)
    }
}
