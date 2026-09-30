package audio.tembra.ui.metronome

/** What the tap-tempo button says under its title (research R9); the copy lives in `MetronomeCopy`. */
sealed interface TapCaption {
    /** No tap in the last two seconds: "Sync finger rhythm". */
    data object Rest : TapCaption

    /** One tap so far: "Tap again". */
    data object Again : TapCaption

    /** [taps] taps in the current sequence: "n taps". */
    data class Counting(val taps: Int) : TapCaption
}
