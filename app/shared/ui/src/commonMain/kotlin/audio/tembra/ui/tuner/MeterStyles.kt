package audio.tembra.ui.tuner

import androidx.compose.ui.text.TextStyle

/** The text styles the meter measures with (data-model.md §8 tokens), captured once per composition. */
internal class MeterStyles(
    val note: TextStyle,
    val octave: TextStyle,
    val readout: TextStyle,
    val micro: TextStyle,
    val label: TextStyle,
    val pill: TextStyle,
    val resting: TextStyle,
)
