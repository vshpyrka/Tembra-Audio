package audio.tembra.ui.tuner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** The theme colours the meter draws with, captured once per composition so the draw lambda reads no locals. */
internal class MeterPalette(
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val secondaryContainer: Color,
    val needleGlow: Color,
    val needleGlowRadius: Dp,
)
