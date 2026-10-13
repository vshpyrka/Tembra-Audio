package audio.tembra.ui.platform

/** Why a pick produced no usable file. */
enum class AudioPickFailure {
    /** No activity or window could present the picker, or the chosen document could not be opened. */
    Unreadable,
}
