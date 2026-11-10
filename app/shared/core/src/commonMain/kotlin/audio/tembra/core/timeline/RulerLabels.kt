package audio.tembra.core.timeline

import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.TempoMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The two ruler rows of spec FR-013 and FR-014 for a viewport: the bar row labels every Nth bar (N a power of two),
 * then every bar, then every beat as "bar.beat", then subdivisions to sixteenths as "bar.beat.sixteenth"; the time row
 * labels minutes, then seconds, then tenths, then hundredths. Each row takes the densest level whose labels, [labelWidth]
 * plus a gap apart, do not overlap, and draws ticks for the level below. Pure: the label width is a parameter, so the
 * UI measures its font once.
 */
data class RulerLabels(val bars: RulerRow, val time: RulerRow) {

    companion object {
        /** The time-row candidate steps in seconds, coarse to fine. */
        private val TIME_STEPS: List<Double> = listOf(3600.0, 1800.0, 600.0, 300.0, 120.0, 60.0, 30.0, 10.0, 5.0, 2.0, 1.0, 0.5, 0.2, 0.1, 0.05, 0.02, 0.01)

        private const val EPSILON = 1e-9

        fun compute(viewport: TimelineViewport, tempoMap: TempoMap, barLabelWidthPx: Double, timeLabelWidthPx: Double, gapPx: Double): RulerLabels {
            val pixelsPerBeat = viewport.devicePixelsPerBeat
            val barSteps = barSteps(tempoMap, viewport)
            val barIndex = densestThatFits(barSteps, pixelsPerBeat, barLabelWidthPx + gapPx)
            val barStep = barSteps[barIndex]
            val finerBar = barSteps.getOrNull(barIndex + 1)
            val bars = RulerRow(
                step = barStep,
                finerStep = finerBar,
                labels = beatPositions(viewport, barStep).map { RulerLabel(viewport.xOf(Beats(it)), barLabel(it, barStep, tempoMap)) },
                ticks = finerBar?.let { step -> beatPositions(viewport, step).map { viewport.xOf(Beats(it)) } } ?: emptyList(),
            )

            val pixelsPerSecond = pixelsPerBeat / tempoMap.secondsPerBeat
            val timeIndex = densestThatFits(TIME_STEPS, pixelsPerSecond, timeLabelWidthPx + gapPx)
            val timeStep = TIME_STEPS[timeIndex]
            val finerTime = TIME_STEPS.getOrNull(timeIndex + 1)
            val decimals = when {
                timeStep >= 1.0 -> 0
                timeStep >= 0.1 -> 1
                else -> 2
            }
            val time = RulerRow(
                step = timeStep,
                finerStep = finerTime,
                labels = secondPositions(viewport, tempoMap, timeStep).map { RulerLabel(viewport.xOf(tempoMap.secondsToBeats(it)), TimeFormat.clock(it, decimals)) },
                ticks = finerTime?.let { step -> secondPositions(viewport, tempoMap, step).map { viewport.xOf(tempoMap.secondsToBeats(it)) } } ?: emptyList(),
            )
            return RulerLabels(bars, time)
        }

        /** Bar-row candidate steps in beats, coarse to fine: 2^k bars down to two, one bar, one beat, then the subdivisions to a sixteenth. */
        private fun barSteps(tempoMap: TempoMap, viewport: TimelineViewport): List<Double> {
            val beatsPerBar = tempoMap.beatsPerBar.toDouble()
            val steps = ArrayList<Double>()
            var bars = 2.0
            while (bars * beatsPerBar * viewport.devicePixelsPerBeat < viewport.widthPx * 4 && bars < 1 shl 20) bars *= 2
            while (bars >= 2.0) {
                steps += bars * beatsPerBar
                bars /= 2
            }
            steps += beatsPerBar
            steps += 1.0
            when (tempoMap.timeSignature.unit) {
                BeatUnit.Quarter -> {
                    steps += 0.5
                    steps += 0.25
                }
                BeatUnit.Eighth -> steps += 0.5
            }
            return steps
        }

        /** The index of the finest step whose spacing in pixels reaches [needed]; the coarsest when none does. */
        private fun densestThatFits(steps: List<Double>, pixelsPerUnit: Double, needed: Double): Int {
            val index = steps.indexOfLast { it * pixelsPerUnit >= needed - EPSILON }
            return if (index < 0) 0 else index
        }

        private fun beatPositions(viewport: TimelineViewport, step: Double): List<Double> {
            // The lead-in shows the left edge at a negative beat: the rows begin at the project's start all the same.
            val first = ceil((viewport.scrollBeats.coerceAtLeastZero().value - EPSILON) / step).toLong()
            val last = floor((viewport.endBeats.value + EPSILON) / step).toLong()
            return (first..last).map { it * step }
        }

        private fun secondPositions(viewport: TimelineViewport, tempoMap: TempoMap, step: Double): List<Double> {
            val startSeconds = tempoMap.beatsToSeconds(viewport.scrollBeats.coerceAtLeastZero())
            val endSeconds = tempoMap.beatsToSeconds(viewport.endBeats)
            val first = ceil((startSeconds - EPSILON) / step).toLong()
            val last = floor((endSeconds + EPSILON) / step).toLong()
            return (first..last).map { it * step }
        }

        private fun barLabel(beats: Double, step: Double, tempoMap: TempoMap): String {
            val position = Beats(beats)
            val bar = tempoMap.barOf(position)
            if (step >= tempoMap.beatsPerBar) return "$bar"
            val beat = tempoMap.beatInBar(position)
            if (step >= 1.0) return "$bar.$beat"
            val sixteenth = GridSize.Sixteenth.beatsOf(tempoMap.timeSignature)?.value ?: 0.25
            val beatStart = (bar - 1) * tempoMap.beatsPerBar + (beat - 1)
            val sub = ((beats - beatStart) / sixteenth).roundToInt() + 1
            return "$bar.$beat.$sub"
        }
    }
}
