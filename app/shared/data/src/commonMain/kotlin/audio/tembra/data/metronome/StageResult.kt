package audio.tembra.data.metronome

/** What [CustomClickSoundStore.stage] did with a picked file. */
enum class StageResult {
    /** The whole file is at [CustomClickSoundStore.stagingPath]. */
    Staged,

    /** Larger than 64 MB; whatever was copied is removed. */
    TooLarge,

    /** The file could not be opened or read; whatever was copied is removed. */
    Unreadable,
}
