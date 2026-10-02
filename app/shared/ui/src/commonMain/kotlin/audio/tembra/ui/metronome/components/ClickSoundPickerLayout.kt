package audio.tembra.ui.metronome.components

/** How [ClickSoundPicker] presents the sounds (spec FR-021). */
internal enum class ClickSoundPickerLayout {
    /** A pill naming the current sound that opens a menu: compact, medium and phone landscape windows. */
    Dropdown,

    /** The two-column grid of [ClickSoundTile]s: expanded windows. */
    Tiles,
}
