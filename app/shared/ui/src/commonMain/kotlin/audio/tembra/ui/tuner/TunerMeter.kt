package audio.tembra.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import audio.tembra.core.tuner.PitchDirection
import audio.tembra.engine.PitchSnapshot
import audio.tembra.engine.perf.PerfSection
import audio.tembra.engine.perf.PerfTrace
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.roundToInt

/**
 * The chromatic meter as one Canvas (research R8, spec FR-008 to FR-013): the status pill, the note and octave,
 * the arc gauge with its marks and needle, the live frequency, the reference chip and the Flat / Sharp row are all
 * drawn in the draw phase from the latest snapshot, every display frame while [listening]; the frame loop invalidates
 * drawing alone. The composable itself recomposes only with [reading], the ViewModel's 10 Hz sample, which the node
 * announces as the heard note and offset (or the resting state). It fills the width its caller allows and derives its
 * height from it, so a width cap is the caller's `Modifier.widthIn`; [size] picks the regular or the compact rows
 * (`MeterLayout.widthForHeight` gives the width that fits a height).
 */
@Composable
fun TunerMeter(
    telemetry: PitchTelemetry,
    reading: MeterReading?,
    listening: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    size: MeterSize = MeterSize.Regular,
) {
    val colors = TembraTheme.colors
    val extras = TembraTheme.typographyExtras
    val typography = TembraTheme.typography
    val spacing = TembraTheme.spacing
    val gradients = TembraTheme.gradients
    val reduceMotion = TembraTheme.motion.reduceMotion
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tick = rememberFrameTick(listening)
    val snapshot = remember { PitchSnapshot() }
    val needle = remember { NeedleSmoother() }
    val arrowLeft = rememberVectorPainter(TembraIcons.vector(TembraIcon.ArrowLeft))
    val arrowRight = rememberVectorPainter(TembraIcons.vector(TembraIcon.ArrowRight))
    val palette = remember(colors, gradients) {
        MeterPalette(
            onSurface = colors.onSurface,
            onSurfaceVariant = colors.onSurfaceVariant,
            primary = colors.primary,
            secondary = colors.secondary,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            surfaceContainerHigh = colors.surfaceContainerHigh,
            surfaceContainerHighest = colors.surfaceContainerHighest,
            secondaryContainer = colors.secondaryContainer,
            needleGlow = gradients.needleGlow.color,
            needleGlowRadius = gradients.needleGlow.radius,
        )
    }
    val styles = remember(extras, typography, size) {
        MeterStyles(
            note = when (size) {
                MeterSize.Regular -> extras.tunerNote
                MeterSize.Compact -> extras.tunerCardNote
            },
            octave = when (size) {
                MeterSize.Regular -> extras.tunerOctave
                MeterSize.Compact -> extras.tunerCardOctave
            },
            readout = extras.tunerReadout,
            micro = extras.microLabel,
            label = typography.labelMedium,
            pill = typography.labelSmall,
            resting = typography.headlineSmall,
        )
    }
    val fontScale = density.fontScale
    val pixelDensity = density.density
    val arrowSize = spacing.meterArrowSize
    val description = MeterReading.describe(reading)

    Canvas(
        modifier = modifier
            .layout { measurable, constraints ->
                val width = (if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth).toFloat()
                val height = MeterLayout(width, pixelDensity, fontScale, size).height
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width.roundToInt(), maxWidth = width.roundToInt(), minHeight = height.roundToInt(), maxHeight = height.roundToInt()),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .testTag(testTag)
            .semantics {
                contentDescription = TunerCopy.tunerMeter
                stateDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        val frameNanos = tick.longValue
        // `size` is the meter size; the canvas size is the draw scope's.
        val layout = MeterLayout(this.size.width, pixelDensity, fontScale, size)
        // The per-frame pitch-to-needle computation is the tuner.frame section of the performance catalogue
        // (feature 007): a draw-lambda computation, never composition.
        val frame = PerfTrace.section(PerfSection.TUNER_FRAME) {
            val shown: MeterReading? = if (listening) {
                telemetry.read(snapshot)
                if (snapshot.hasPitch && snapshot.frequencyHz > 0f) MeterReading.from(snapshot) else null
            } else {
                reading
            }
            val targetAngle = layout.geometry.needleAngleDegrees(shown?.cents ?: 0.0)
            val angle = if (reduceMotion || !listening) targetAngle else needle.advance(targetAngle, frameNanos)
            MeterFrame(shown, angle)
        }
        drawMeter(layout, frame.reading, frame.angle, palette, styles, textMeasurer, arrowLeft, arrowRight, arrowSize.toPx(), listening && !reduceMotion, frameNanos)
    }
}

private fun DrawScope.drawMeter(
    layout: MeterLayout,
    reading: MeterReading?,
    needleAngle: Float,
    palette: MeterPalette,
    styles: MeterStyles,
    measurer: TextMeasurer,
    arrowLeft: Painter,
    arrowRight: Painter,
    arrowSize: Float,
    pulse: Boolean,
    frameNanos: Long,
) {
    val inTune = reading?.isInTune == true
    drawStatusPill(layout, inTune, palette, styles, measurer, pulse, frameNanos)
    drawNote(layout, reading, palette, styles, measurer)
    drawGauge(layout, inTune, needleAngle, palette)
    drawReadouts(layout, reading, palette, styles, measurer)
    drawEndLabels(layout, reading, palette, styles, measurer, arrowLeft, arrowRight, arrowSize)
}

private fun DrawScope.drawStatusPill(
    layout: MeterLayout,
    lit: Boolean,
    palette: MeterPalette,
    styles: MeterStyles,
    measurer: TextMeasurer,
    pulse: Boolean,
    frameNanos: Long,
) {
    val text = measurer.measure(TunerCopy.inTune.uppercase(), styles.pill)
    val (_, centerY) = layout.pillCenter
    val top = centerY - layout.pillHeight / 2
    val radius = CornerRadius(layout.pillHeight / 2)
    drawRoundRect(
        color = palette.surfaceContainerHigh.copy(alpha = 0.8f),
        topLeft = Offset(layout.pillLeft, top),
        size = Size(layout.pillWidth, layout.pillHeight),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = if (lit) palette.primary.copy(alpha = 0.2f) else palette.outlineVariant,
        topLeft = Offset(layout.pillLeft, top),
        size = Size(layout.pillWidth, layout.pillHeight),
        cornerRadius = radius,
        style = Stroke(width = 1f),
    )
    // Tailwind's animate-pulse: opacity between 1 and 0.5 once a second.
    val dotAlpha = if (lit && pulse) 0.75f + 0.25f * kotlin.math.sin(frameNanos / 1_000_000_000.0 * 2 * kotlin.math.PI).toFloat() else 1f
    val dotColor = if (lit) palette.primary.copy(alpha = dotAlpha) else palette.onSurfaceVariant
    val (dotX, dotY) = layout.pillDotCenter
    drawCircle(color = dotColor, radius = layout.pillDotRadius, center = Offset(dotX, dotY))
    drawText(
        textLayoutResult = text,
        color = if (lit) palette.primary else palette.onSurfaceVariant,
        topLeft = Offset(layout.pillTextLeft, centerY - text.size.height / 2f),
    )
}

private fun DrawScope.drawNote(layout: MeterLayout, reading: MeterReading?, palette: MeterPalette, styles: MeterStyles, measurer: TextMeasurer) {
    if (reading == null) {
        val text = measurer.measure(TunerCopy.listening, styles.resting)
        drawText(text, color = palette.onSurfaceVariant, topLeft = Offset(layout.width / 2 - text.size.width / 2f, layout.noteBaseline - text.size.height))
        return
    }
    val name = measurer.measure(reading.note.pitchClass.label, styles.note)
    val octave = measurer.measure(reading.note.octave.toString(), styles.octave)
    val gap = layout.px(2f)
    val total = name.size.width + gap + octave.size.width
    val left = layout.width / 2 - total / 2
    val baseline = layout.noteBaseline
    drawText(name, color = palette.onSurface, topLeft = Offset(left, baseline - name.size.height))
    drawText(octave, color = palette.primary, topLeft = Offset(left + name.size.width + gap, baseline - octave.size.height))
}

private fun DrawScope.drawGauge(layout: MeterLayout, inTune: Boolean, needleAngle: Float, palette: MeterPalette) {
    val g = layout.geometry
    val pivot = Offset(layout.geometryLeft + g.pivotX, layout.geometryTop + g.pivotY)
    fun arcRect(radius: Float) = Rect(pivot.x - radius, pivot.y - radius, pivot.x + radius, pivot.y + radius)
    fun line(from: Pair<Float, Float>, to: Pair<Float, Float>, color: Color, width: Float) {
        drawLine(
            color = color,
            start = Offset(layout.geometryLeft + from.first, layout.geometryTop + from.second),
            end = Offset(layout.geometryLeft + to.first, layout.geometryTop + to.second),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
    // Outer arc from −90° (left) to +90° (right); Compose angles start at 3 o'clock, so the arc spans 180°..360°.
    drawArc(palette.surfaceContainerHighest, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = arcRect(g.outerRadius).topLeft, size = arcRect(g.outerRadius).size, style = Stroke(g.outerStroke))
    line(g.endMarkStart(-90f), g.endMarkEnd(-90f), palette.outline, g.endMarkStroke)
    line(g.endMarkStart(90f), g.endMarkEnd(90f), palette.outline, g.endMarkStroke)
    for (angle in listOf(-45f, 45f)) {
        val (from, to) = g.quarterMark(angle)
        line(from, to, palette.outlineVariant, g.quarterMarkStroke)
    }
    val (topFrom, topTo) = g.topMark()
    line(topFrom, topTo, palette.secondary, g.topMarkStroke)
    // The in-tune zone glows brighter while the pitch sits inside it (spec FR-008).
    drawArc(
        palette.primary.copy(alpha = if (inTune) 0.85f else 0.35f),
        startAngle = 270f - g.inTuneHalfAngle,
        sweepAngle = g.inTuneHalfAngle * 2,
        useCenter = false,
        topLeft = arcRect(g.outerRadius).topLeft,
        size = arcRect(g.outerRadius).size,
        style = Stroke(g.zoneStroke, cap = StrokeCap.Round),
    )
    drawCircle(palette.outlineVariant.copy(alpha = 0.3f), radius = g.dashedCircleRadius, center = pivot, style = Stroke(g.dashedStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))))
    drawArc(palette.surfaceContainerHigh.copy(alpha = 0.9f), startAngle = 270f - g.innerArcHalfAngle, sweepAngle = g.innerArcHalfAngle * 2, useCenter = false, topLeft = arcRect(g.innerArcRadius).topLeft, size = arcRect(g.innerArcRadius).size, style = Stroke(g.innerArcStroke))
    drawArc(palette.secondaryContainer.copy(alpha = 0.6f), startAngle = 270f - g.dashedInnerHalfAngle, sweepAngle = g.dashedInnerHalfAngle * 2, useCenter = false, topLeft = arcRect(g.innerArcRadius).topLeft, size = arcRect(g.innerArcRadius).size, style = Stroke(g.dashedInnerStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
    // Needle with its glow: a wider translucent stroke under the needle, never a shadow.
    val tip = g.pointAt(needleAngle, g.needleLength)
    val back = g.pointAt(needleAngle + 180f, g.needleBackLength)
    line(back, tip, palette.needleGlow.copy(alpha = palette.needleGlow.alpha * 0.5f), g.needleStroke + palette.needleGlowRadius.toPx())
    line(back, tip, palette.secondary, g.needleStroke)
    drawCircle(palette.secondary, radius = g.pivotRingRadius, center = pivot, style = Stroke(g.pivotRingStroke))
    drawCircle(palette.primary, radius = g.pivotDotRadius, center = pivot)
}

private fun DrawScope.drawReadouts(layout: MeterLayout, reading: MeterReading?, palette: MeterPalette, styles: MeterStyles, measurer: TextMeasurer) {
    if (reading == null) return
    val hz = measurer.measure("${formatHz(reading.frequencyHz)} ${TunerCopy.hertz}", styles.readout)
    val bullet = measurer.measure("•", styles.readout)
    val chipText = measurer.measure("${TunerCopy.reference} ${formatHz(reading.referenceHz)} ${TunerCopy.hertz}".uppercase(), styles.micro)
    val dotRadius = layout.pillDotRadius
    val chipPaddingX = layout.pillPaddingX
    val chipGap = layout.pillGap
    val chipHeight = chipText.size.height + layout.px(8f)
    val chipWidth = chipPaddingX * 2 + dotRadius * 2 + chipGap + chipText.size.width
    val gap = layout.px(12f)
    val total = hz.size.width + gap + bullet.size.width + gap + chipWidth
    var x = layout.width / 2 - total / 2
    val centerY = layout.readoutCenterY
    drawText(hz, color = palette.onSurface, topLeft = Offset(x, centerY - hz.size.height / 2f))
    x += hz.size.width + gap
    drawText(bullet, color = palette.outlineVariant, topLeft = Offset(x, centerY - bullet.size.height / 2f))
    x += bullet.size.width + gap
    drawRoundRect(palette.surfaceContainerHighest.copy(alpha = 0.6f), topLeft = Offset(x, centerY - chipHeight / 2), size = Size(chipWidth, chipHeight), cornerRadius = CornerRadius(chipHeight / 2))
    drawRoundRect(palette.outlineVariant.copy(alpha = 0.3f), topLeft = Offset(x, centerY - chipHeight / 2), size = Size(chipWidth, chipHeight), cornerRadius = CornerRadius(chipHeight / 2), style = Stroke(1f))
    drawCircle(palette.secondary, radius = dotRadius, center = Offset(x + chipPaddingX + dotRadius, centerY))
    drawText(chipText, color = palette.secondary, topLeft = Offset(x + chipPaddingX + dotRadius * 2 + chipGap, centerY - chipText.size.height / 2f))
}

private fun DrawScope.drawEndLabels(
    layout: MeterLayout,
    reading: MeterReading?,
    palette: MeterPalette,
    styles: MeterStyles,
    measurer: TextMeasurer,
    arrowLeft: Painter,
    arrowRight: Painter,
    arrowSize: Float,
) {
    drawLine(palette.outlineVariant.copy(alpha = 0.2f), Offset(0f, layout.labelRowTop), Offset(layout.width, layout.labelRowTop), strokeWidth = 1f)
    val flatLit = reading != null && reading.direction == PitchDirection.Flat
    val sharpLit = reading != null && reading.direction == PitchDirection.Sharp
    val flat = measurer.measure(TunerCopy.flat, styles.label)
    val sharp = measurer.measure(TunerCopy.sharp, styles.label)
    val (flatX, y) = layout.flatLabelCenter
    val (sharpX, _) = layout.sharpLabelCenter
    val gap = layout.px(4f)
    val flatColor = if (flatLit) palette.secondary else palette.onSurfaceVariant
    val sharpColor = if (sharpLit) palette.secondary else palette.onSurfaceVariant
    val flatArrowColor = if (flatLit) palette.secondary else palette.outline
    val sharpArrowColor = if (sharpLit) palette.secondary else palette.outline
    // Flat: arrow then label, anchored at the row's start; Sharp: label then arrow, anchored at the end.
    val flatLeft = flatX - (arrowSize + gap + flat.size.width) / 2
    translate(flatLeft, y - arrowSize / 2) { with(arrowLeft) { draw(Size(arrowSize, arrowSize), colorFilter = ColorFilter.tint(flatArrowColor)) } }
    drawText(flat, color = flatColor, topLeft = Offset(flatLeft + arrowSize + gap, y - flat.size.height / 2f))
    val sharpLeft = sharpX - (arrowSize + gap + sharp.size.width) / 2
    drawText(sharp, color = sharpColor, topLeft = Offset(sharpLeft, y - sharp.size.height / 2f))
    translate(sharpLeft + sharp.size.width + gap, y - arrowSize / 2) { with(arrowRight) { draw(Size(arrowSize, arrowSize), colorFilter = ColorFilter.tint(sharpArrowColor)) } }
}

/** One decimal, as the design shows ("82.4"). */
internal fun formatHz(hz: Double): String {
    val tenths = (hz * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

