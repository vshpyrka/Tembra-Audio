package audio.tembra.ui.platform

/** Why a picked sound file could not be copied (contracts/click-sounds.md). */
enum class SoundPickFailure {
    /** Larger than 64 MB. */
    TooLarge,

    /** The platform could not open or read the file. */
    Unreadable,
}
