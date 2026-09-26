package audio.tembra.ui.designsystem

import audio.tembra.ui.theme.TembraIcon

/** One segment of a [TembraSegmentedControl]: a stable id, its label and an optional glyph. */
data class SegmentSpec(val id: String, val label: String, val icon: TembraIcon? = null)
