package audio.tembra.ui.metronome

/** The Custom entry of the sound picker (data-model.md §8, contracts/click-sounds.md). */
sealed interface CustomSoundState {
    /** Nothing imported. */
    data object None : CustomSoundState

    /** A file is being picked, copied or prepared. */
    data object Importing : CustomSoundState

    /** [displayName] is loaded in the engine. */
    data class Available(val displayName: String) : CustomSoundState

    /** [displayName] was imported but its copy could not be loaded. */
    data class Unavailable(val displayName: String) : CustomSoundState
}
