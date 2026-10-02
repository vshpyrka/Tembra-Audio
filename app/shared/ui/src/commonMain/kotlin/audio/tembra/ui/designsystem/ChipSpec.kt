package audio.tembra.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import audio.tembra.ui.theme.TembraIcon

/**
 * One chip of a [TembraChipRow]: a stable id, its label and an optional leading glyph (the subdivision row's note).
 * An [interactionSource] lets a caller drive or observe the chip's hover, press and focus state; null keeps one private.
 */
data class ChipSpec(
    val id: String,
    val label: String,
    val icon: TembraIcon? = null,
    val interactionSource: MutableInteractionSource? = null,
)
