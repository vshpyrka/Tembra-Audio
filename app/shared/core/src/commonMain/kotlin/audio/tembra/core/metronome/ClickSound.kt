package audio.tembra.core.metronome

/** What sounds on each tick: one of the built-in sounds or the imported custom file. */
sealed interface ClickSound {
    data class BuiltIn(val sound: BuiltInClickSound) : ClickSound

    /** [displayName] is the imported file's name without its extension. */
    data class Custom(val displayName: String) : ClickSound
}
