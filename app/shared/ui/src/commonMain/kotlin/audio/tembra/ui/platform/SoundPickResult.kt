package audio.tembra.ui.platform

/** What [SoundFilePicker.pickWav] produced. */
sealed interface SoundPickResult {
    /** The file was copied to the destination; [displayName] is its name without the extension. */
    data class Picked(val displayName: String) : SoundPickResult

    /** The user dismissed the picker; nothing was written. */
    data object Cancelled : SoundPickResult

    /** The file could not be copied; nothing usable was written. */
    data class Failed(val reason: SoundPickFailure) : SoundPickResult
}
