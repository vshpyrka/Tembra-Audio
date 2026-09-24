package audio.tembra.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

/** Text styles without a Material slot. */
@Immutable
data class TembraTypographyExtras(
    val headlineLgMobile: TextStyle,
    val tabularTimecode: TextStyle,
    val tabularParam: TextStyle,
)
