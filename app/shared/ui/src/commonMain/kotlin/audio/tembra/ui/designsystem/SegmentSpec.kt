package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import audio.tembra.ui.theme.TembraIcon

/**
 * One segment of a [TembraSegmentedControl]: a stable id, its label and an optional glyph. An [interactionSource]
 * lets a caller drive or observe the segment's hover, press and focus state; null keeps one private.
 */
data class SegmentSpec(
    val id: String,
    val label: String,
    val icon: TembraIcon? = null,
    val interactionSource: MutableInteractionSource? = null,
)
