package audio.tembra.ui.mixer

/**
 * What a strip shows (feature 012, research R2; spec FR-002, FR-025): [Compact] at the half height (number, Solo, Mute,
 * the fader with its meter, the name), [Full] at the full height (adds the VU dial and the pan knob), [FullNoVu] when
 * the full sheet is shorter than 480 dp (the landscape sheet): the knob stays, the dial is dropped first.
 */
enum class StripForm {
    Compact,
    Full,
    FullNoVu;

    /** The pan knob shows in both full forms (spec FR-025 drops the dial before the knob). */
    val showsPan: Boolean get() = this != Compact

    val showsVu: Boolean get() = this == Full

    companion object {
        /** Below this sheet height the full form drops its VU dial (spec FR-025). */
        const val VU_FROM_DP = 480

        /** The form for a sheet at [height] showing [sheetHeightDp] of itself. */
        fun of(height: MixerHeight, sheetHeightDp: Float): StripForm = when {
            height == MixerHeight.Half -> Compact
            sheetHeightDp < VU_FROM_DP -> FullNoVu
            else -> Full
        }
    }
}
