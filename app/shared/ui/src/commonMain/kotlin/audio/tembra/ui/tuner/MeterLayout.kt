package audio.tembra.ui.tuner

/**
 * Where the meter's rows sit for a given width, display density and font scale: the status pill, the note, the arc
 * (a [MeterGeometry] fitted into a 1.8 : 1 box), the readout row and the Flat/Sharp row. Design pixels of the text
 * rows scale with density and font scale so large fonts grow the meter instead of clipping it (spec FR-023); the pill
 * geometry is fixed in dp so the meter test can probe its dot.
 */
class MeterLayout(val width: Float, val density: Float = 1f, val fontScale: Float = 1f) {
    /** Design pixels of text rows to screen pixels. */
    val textScale: Float = density * fontScale

    val pillRowHeight: Float = PILL_ROW * textScale
    val noteRowHeight: Float = NOTE_ROW * textScale
    val readoutRowHeight: Float = READOUT_ROW * textScale
    val labelRowHeight: Float = LABEL_ROW * textScale
    val textRowsHeight: Float = pillRowHeight + noteRowHeight + readoutRowHeight + labelRowHeight

    val arcHeight: Float = width / ARC_ASPECT
    val height: Float = arcHeight + textRowsHeight
    val arcTop: Float = pillRowHeight + noteRowHeight

    /** The 400 × 230 gauge fitted into the arc box like an SVG with `meet`, centred horizontally. */
    val geometry: MeterGeometry = MeterGeometry(minOf(width, arcHeight * MeterGeometry.DESIGN_WIDTH / MeterGeometry.DESIGN_HEIGHT))
    val geometryLeft: Float = (width - geometry.width) / 2f
    val geometryTop: Float = arcTop + (arcHeight - geometry.height) / 2f

    /** The "In tune" pill: centred in its row, fixed dp size, dot then text. */
    val pillWidth: Float = PILL_WIDTH_DP * textScale
    val pillHeight: Float = PILL_HEIGHT_DP * textScale
    val pillPaddingX: Float = PILL_PADDING_DP * density
    val pillDotRadius: Float = PILL_DOT_DP * density / 2f
    val pillGap: Float = PILL_GAP_DP * density
    val pillCenter: Pair<Float, Float> = Pair(width / 2f, pillRowHeight / 2f)
    val pillLeft: Float = width / 2f - pillWidth / 2f
    val pillDotCenter: Pair<Float, Float> = Pair(pillLeft + pillPaddingX + pillDotRadius, pillRowHeight / 2f)
    val pillTextLeft: Float = pillLeft + pillPaddingX + pillDotRadius * 2f + pillGap

    val noteBaseline: Float = pillRowHeight + noteRowHeight - NOTE_DESCENT * textScale
    val readoutCenterY: Float = arcTop + arcHeight + readoutRowHeight / 2f
    val labelRowTop: Float = arcTop + arcHeight + readoutRowHeight
    val labelCenterY: Float = labelRowTop + labelRowHeight / 2f + LABEL_SEAM_GAP * textScale / 2f
    val flatLabelCenter: Pair<Float, Float> = Pair(LABEL_INSET * textScale, labelCenterY)
    val sharpLabelCenter: Pair<Float, Float> = Pair(width - LABEL_INSET * textScale, labelCenterY)
    val labelProbeHalfWidth: Float = LABEL_PROBE_HALF_WIDTH * textScale
    val labelProbeHalfHeight: Float = LABEL_PROBE_HALF_HEIGHT * textScale

    /** Screen pixels for a design-pixel spacing in the text rows. */
    fun px(designPx: Float): Float = designPx * textScale

    /** Converts a geometry-local point to meter coordinates. */
    fun toMeter(point: Pair<Float, Float>): Pair<Float, Float> = Pair(point.first + geometryLeft, point.second + geometryTop)

    companion object {
        const val ARC_ASPECT = 1.8f
        private const val PILL_ROW = 36f
        private const val PILL_WIDTH_DP = 108f
        private const val PILL_HEIGHT_DP = 24f
        private const val PILL_PADDING_DP = 12f
        private const val PILL_DOT_DP = 6f
        private const val PILL_GAP_DP = 6f
        private const val NOTE_ROW = 76f
        private const val NOTE_DESCENT = 14f
        private const val READOUT_ROW = 40f
        private const val LABEL_ROW = 36f
        private const val LABEL_SEAM_GAP = 8f
        private const val LABEL_INSET = 44f
        private const val LABEL_PROBE_HALF_WIDTH = 34f
        private const val LABEL_PROBE_HALF_HEIGHT = 9f
    }
}
