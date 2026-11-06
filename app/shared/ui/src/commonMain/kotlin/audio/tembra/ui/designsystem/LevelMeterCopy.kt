package audio.tembra.ui.designsystem

/** The level meter's and the VU dial's accessible names (feature 012, spec FR-030); `MixerCopy` repeats them for the mixer's tests. */
internal object LevelMeterCopy {
    const val level = "Level"
    const val vu = "VU"

    /** The clip square's name while the latch is off (feature 010's wording). */
    const val clipIndicator = "Clip indicator"

    /** The clip square's name while the latch is lit: a tap clears it. */
    const val clipped = "Clipped, tap to reset"
}
