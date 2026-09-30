package audio.tembra.data.metronome

/**
 * The app's own copy of the imported click sound: bytes are written to [stagingPath], then [commit] moves them into
 * place so a failed import never damages the file in use.
 */
interface CustomClickSoundStore {
    /** `<documents>/metronome/custom.wav`. */
    val path: String

    /** `<documents>/metronome/custom.wav.tmp`. */
    val stagingPath: String

    /** Replaces [path] with the staged file; false when nothing is staged. */
    suspend fun commit(): Boolean

    suspend fun exists(): Boolean

    /** Removes the file; a missing file is not an error. */
    suspend fun delete()
}
