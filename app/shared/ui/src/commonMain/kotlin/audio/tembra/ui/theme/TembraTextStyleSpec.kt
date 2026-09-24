package audio.tembra.ui.theme

/** Numeric description of one text style, kept free of Compose types so it can be compared with theme.json on the JVM. */
data class TembraTextStyleSpec(
    val family: TembraFont,
    val sizeSp: Float,
    val lineHeightSp: Float,
    val weight: Int,
    val letterSpacingEm: Float = 0f,
    val tabularFigures: Boolean = false,
)
