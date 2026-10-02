package audio.tembra.engine

/** Outcome of [MetronomeAudio.loadCustomSound] (data-model.md §4). */
sealed interface CustomSoundResult {
    data class Loaded(val info: CustomSoundInfo) : CustomSoundResult

    /** `TEMBRA_ERR_DECODE`: not a PCM WAV. */
    data object NotWav : CustomSoundResult

    /** `TEMBRA_ERR_SILENT`: it decodes but holds no sound above -60 dBFS. */
    data object Silent : CustomSoundResult

    /** `TEMBRA_ERR_ARGUMENT`: the path cannot be opened. */
    data object Missing : CustomSoundResult

    companion object {
        /**
         * Maps a failed `tembra_metronome_load_custom_sound` code. The code is an open native value: anything the
         * contract does not name reads as [Missing], the file the engine could not use.
         */
        internal fun fromNativeError(code: Int): CustomSoundResult = when (code) {
            TEMBRA_ERR_DECODE -> NotWav
            TEMBRA_ERR_SILENT -> Silent
            TEMBRA_ERR_ARGUMENT -> Missing
            else -> Missing
        }

        private const val TEMBRA_ERR_ARGUMENT = -3
        private const val TEMBRA_ERR_DECODE = -4
        private const val TEMBRA_ERR_SILENT = -5
    }
}
