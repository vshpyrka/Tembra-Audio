package audio.tembra.engine

/** Which audio focus [AndroidAudioFocus.request] asks for. */
enum class FocusType {
    /** `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`: reference tones and the preview click; other apps duck. */
    TransientMayDuck,

    /** `AUDIOFOCUS_GAIN`: the running metronome; other media pause. */
    Gain,
}
