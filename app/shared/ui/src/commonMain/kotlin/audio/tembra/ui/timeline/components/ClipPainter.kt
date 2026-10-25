package audio.tembra.ui.timeline.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.timeline.ClipUi
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one clip into a lane (contracts/timeline-screen.md "Design conformance"; spec FR-016, FR-029, FR-030): the
 * rounded block in the track tint, the caption band with the name (pinned to the visible left edge), the waveform
 * columns from the overview level nearest the frames-per-pixel, a flat placeholder line while the overview builds,
 * the 2 dp selection outline, and the `outline` fill with the name only when the audio is unavailable. [left] and
 * [right] are the clip's edges in lane pixels; columns outside the lane are skipped.
 */
internal fun DrawScope.drawClip(
    clip: ClipUi,
    left: Float,
    right: Float,
    tint: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    style: ClipStyle,
    measurer: TextMeasurer,
    hovered: Boolean = false,
) {
    val width = right - left
    if (width <= 0f) return
    val visibleLeft = max(left, 0f)
    val visibleRight = min(right, size.width)
    if (visibleRight <= visibleLeft) return
    val top = CLIP_INSET
    val height = size.height - CLIP_INSET * 2
    val radius = CornerRadius(style.cornerRadiusPx)
    val blockSize = Size(width, height)
    val fill = if (clip.available) tint.copy(alpha = if (hovered) HOVER_ALPHA else BLOCK_ALPHA) else style.unavailable
    drawRoundRect(fill, topLeft = Offset(left, top), size = blockSize, cornerRadius = radius)

    // The caption band: the rounded block clipped to the band's height keeps the top corners.
    val caption = measurer.measure(
        text = clip.name,
        style = style.captionStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = max(0f, visibleRight - visibleLeft - CAPTION_PADDING * 2).toInt()),
    )
    val bandHeight = caption.size.height + CAPTION_PADDING
    if (clip.available) {
        clipRect(left, top, right, top + bandHeight) {
            drawRoundRect(style.captionBand, topLeft = Offset(left, top), size = blockSize, cornerRadius = radius)
        }
    }
    drawText(caption, color = style.captionColor, topLeft = Offset(visibleLeft + CAPTION_PADDING, top + CAPTION_PADDING / 2))

    if (clip.available) {
        val bodyTop = top + bandHeight
        val bodyBottom = top + height
        val centre = (bodyTop + bodyBottom) / 2
        val halfBody = (bodyBottom - bodyTop) / 2 - WAVE_INSET
        val overview = clip.overview
        if (overview != null && halfBody > 0f) {
            val framesPerPixel = clip.lengthFrames.toDouble() / width
            val level = overview.levelFor(framesPerPixel)
            var x = visibleLeft + COLUMN_STRIDE / 2
            while (x < visibleRight) {
                val frame = clip.offsetFrames + ((x - left) * framesPerPixel).toLong()
                val bucket = (frame / level.framesPerBucket).toInt()
                val peak = level.peak(bucket) / PEAK_FULL_SCALE
                val half = max(peak * halfBody, MIN_COLUMN_HALF)
                drawLine(tint, Offset(x, centre - half), Offset(x, centre + half), strokeWidth = COLUMN_WIDTH)
                x += COLUMN_STRIDE
            }
        } else if (clip.overviewStatus is OverviewStatus.Building) {
            drawLine(style.placeholderColor, Offset(visibleLeft + CAPTION_PADDING, centre), Offset(visibleRight - CAPTION_PADDING, centre), strokeWidth = COLUMN_WIDTH)
        }
    }

    if (selected) {
        val inset = style.outlineWidthPx / 2
        drawRoundRect(
            style.selectionOutline,
            topLeft = Offset(left + inset, top + inset),
            size = Size(width - style.outlineWidthPx, height - style.outlineWidthPx),
            cornerRadius = radius,
            style = Stroke(style.outlineWidthPx),
        )
    }
}

internal const val CLIP_INSET = 4f
private const val BLOCK_ALPHA = 0.35f
/** A pointer over the clip lifts the block's tint (Constitution IV hover states). */
private const val HOVER_ALPHA = 0.5f
private const val CAPTION_PADDING = 6f
private const val WAVE_INSET = 3f
private const val COLUMN_WIDTH = 2f
private const val COLUMN_STRIDE = 3f
private const val MIN_COLUMN_HALF = 0.5f
private const val PEAK_FULL_SCALE = 127f
