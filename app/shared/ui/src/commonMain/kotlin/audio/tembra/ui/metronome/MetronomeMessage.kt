package audio.tembra.ui.metronome

/** One-shot snackbar messages of the metronome screen (data-model.md §8); the copy lives in `MetronomeCopy`. */
sealed interface MetronomeMessage {
    /** The picked file is not a WAV file the engine can read. */
    data object NotWav : MetronomeMessage

    /** The picked file has no sound in it. */
    data object Silent : MetronomeMessage

    /** The picked file is larger than 64 MB. */
    data object TooLarge : MetronomeMessage

    /** The picked file could not be read. */
    data object ImportFailed : MetronomeMessage

    /** The stored custom sound is missing, so Classic Wood plays. */
    data object CustomSoundMissing : MetronomeMessage

    /** The platform refused the metronome audio focus: another app holds the audio (Android, `AudioResult.FOCUS_DENIED`). */
    data object FocusDenied : MetronomeMessage

    /** The audio output could not be opened, or the background host refused to keep the process alive. */
    data object DeviceUnavailable : MetronomeMessage
}
