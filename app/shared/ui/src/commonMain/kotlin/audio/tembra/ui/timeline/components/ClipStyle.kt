package audio.tembra.ui.timeline.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** The theme values [drawClip] needs, resolved once per lane composition (contracts/timeline-screen.md "Design conformance"). */
internal class ClipStyle(
    /** The six track tints; a clip takes its track's. */
    val tints: List<Color>,
    /** `clip_caption_band`: the name band over the tint. */
    val captionBand: Color,
    /** `selection_outline`, 2 dp around the selected clip. */
    val selectionOutline: Color,
    /** `unavailable_clip`: the fill of a clip whose audio is missing. */
    val unavailable: Color,
    /** `shapes.clip` in pixels. */
    val cornerRadiusPx: Float,
    val outlineWidthPx: Float,
    val captionStyle: TextStyle,
    val captionColor: Color,
    val placeholderColor: Color,
) {
    fun tintOf(colorIndex: Int): Color = tints[colorIndex % tints.size]
}
