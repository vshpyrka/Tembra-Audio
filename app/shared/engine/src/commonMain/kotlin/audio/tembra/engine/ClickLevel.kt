package audio.tembra.engine

/** How loud one tick sounds; maps 1:1 to `tembra_click_level` (data-model.md §4). */
enum class ClickLevel {
    Sub,
    Click,
    Accent;

    /** The `tembra_click_level` value. */
    val native: Int
        get() = when (this) {
            Sub -> 0
            Click -> 1
            Accent -> 2
        }

    companion object {
        /** Maps the C ABI value; anything unknown reads as [Click]. */
        fun fromNative(value: Int): ClickLevel = when (value) {
            0 -> Sub
            2 -> Accent
            else -> Click
        }
    }
}
