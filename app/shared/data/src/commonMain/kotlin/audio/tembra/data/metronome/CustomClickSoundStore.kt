package audio.tembra.data.metronome

/**
 * The app's own copy of the imported click sound: bytes are written to [stagingPath] and, once the engine has accepted
 * them, [commit] moves them into place; a refused file is removed with [deleteStaging], so a failed import never
 * damages the file in use.
 */
interface CustomClickSoundStore {
    /** `<documents>/metronome/custom.wav`. */
    val path: String

    /** `<documents>/metronome/custom.wav.tmp`. */
    val stagingPath: String

    /**
     * Copies the picked file [open] yields to [stagingPath] with the 64 MB cap (feature 009 moved the copy here from
     * the platform pickers, which now hand over content handles). A refused or failed copy leaves nothing staged.
     */
    suspend fun stage(open: suspend () -> okio.Source): StageResult

    /** Replaces [path] with the staged file; false when nothing is staged. */
    suspend fun commit(): Boolean

    suspend fun exists(): Boolean

    /** Removes the file; a missing file is not an error. */
    suspend fun delete()

    /** Removes the staged file (a refused or partial copy) and leaves [path] untouched; a missing file is not an error. */
    suspend fun deleteStaging()
}
