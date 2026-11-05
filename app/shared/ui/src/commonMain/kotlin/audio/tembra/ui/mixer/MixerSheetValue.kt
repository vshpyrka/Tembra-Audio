package audio.tembra.ui.mixer

/** The sheet's drag anchors (feature 012, research R1): closed below the lanes box, or open at one of the two heights. */
internal enum class MixerSheetValue {
    Hidden,
    Half,
    Full;

    companion object {
        fun of(height: MixerHeight): MixerSheetValue = when (height) {
            MixerHeight.Half -> Half
            MixerHeight.Full -> Full
        }
    }
}
