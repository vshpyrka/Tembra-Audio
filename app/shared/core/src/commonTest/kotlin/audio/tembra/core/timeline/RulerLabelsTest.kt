package audio.tembra.core.timeline

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TempoMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The label rules of spec FR-014 swept across the zoom range (data-model.md section 2). */
class RulerLabelsTest {

    private val barWidth = 36.0
    private val timeWidth = 64.0
    private val gap = 8.0

    private fun viewport(pixelsPerBeat: Double, beatsPerBar: Int = 4, scroll: Double = 0.0): TimelineViewport =
        TimelineViewport(pixelsPerBeat, Beats(scroll), widthPx = 1_000.0, densityScale = 1.0, projectLengthBeats = Beats(256.0), beatsPerBar = beatsPerBar)

    private fun labels(pixelsPerBeat: Double, signature: TimeSignature = TimeSignature.FourFour, tempo: Tempo = Tempo(120), scroll: Double = 0.0): RulerLabels =
        RulerLabels.compute(viewport(pixelsPerBeat, signature.beats, scroll), TempoMap(tempo, signature), barWidth, timeWidth, gap)

    /** Forty levels from fit (1000 px over 256 beats) to closest (one beat across the width). */
    private val sweep: List<Double> = (0 until 40).map { i ->
        val min = 1_000.0 / 256.0
        val max = 1_000.0
        min * (max / min).pow(i / 39.0)
    }

    private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    @Test
    fun noTwoLabelsInARowAreCloserThanTheirWidthPlusTheGap() {
        for (ppb in sweep) {
            val result = labels(ppb)
            result.bars.labels.zipWithNext { a, b -> assertTrue(b.x - a.x >= barWidth + gap - 1e-6, "bars at $ppb px/beat: ${a.text}@${a.x} ${b.text}@${b.x}") }
            result.time.labels.zipWithNext { a, b -> assertTrue(b.x - a.x >= timeWidth + gap - 1e-6, "time at $ppb px/beat: ${a.text}@${a.x} ${b.text}@${b.x}") }
            assertTrue(result.bars.labels.isNotEmpty(), "bar labels at $ppb")
            assertTrue(result.time.labels.isNotEmpty(), "time labels at $ppb")
            for (label in result.bars.labels + result.time.labels) assertTrue(label.x >= -1e-6 && label.x <= 1_000.0 + 1e-6, "${label.text} at ${label.x}")
        }
    }

    @Test
    fun theDensestLevelThatFitsIsChosen() {
        for (ppb in sweep) {
            val result = labels(ppb)
            assertTrue(result.bars.step * ppb >= barWidth + gap - 1e-6, "bar step ${result.bars.step} at $ppb")
            val finerBar = result.bars.finerStep
            if (finerBar != null) assertTrue(finerBar * ppb < barWidth + gap, "a finer bar level (${finerBar}) would fit at $ppb")
            val secondsPerPx = 0.5 / ppb
            assertTrue(result.time.step / secondsPerPx >= timeWidth + gap - 1e-6, "time step ${result.time.step} at $ppb")
            val finerTime = result.time.finerStep
            if (finerTime != null) assertTrue(finerTime / secondsPerPx < timeWidth + gap, "a finer time level ($finerTime) would fit at $ppb")
        }
    }

    @Test
    fun barLabelsGoFromEveryPowerOfTwoBarsToBarsToBarBeatToSixteenths() {
        var previousStep = Double.MAX_VALUE
        var sawPowerOfTwo = false
        var sawBars = false
        var sawBeats = false
        var sawSixteenths = false
        for (ppb in sweep) {
            val row = labels(ppb).bars
            assertTrue(row.step <= previousStep, "step grew from $previousStep to ${row.step} at $ppb")
            previousStep = row.step
            when {
                row.step > 4.0 -> {
                    val bars = (row.step / 4.0).roundToInt()
                    assertTrue(isPowerOfTwo(bars) && bars >= 2, "every $bars bars at $ppb")
                    row.labels.forEach { assertTrue(it.text.toIntOrNull() != null, it.text) }
                    sawPowerOfTwo = true
                }
                row.step == 4.0 -> {
                    assertEquals(listOf("1", "2", "3", "4"), row.labels.take(4).map { it.text })
                    sawBars = true
                }
                row.step == 1.0 -> {
                    assertEquals(listOf("1.1", "1.2", "1.3", "1.4", "2.1"), row.labels.take(5).map { it.text })
                    sawBeats = true
                }
                row.step == 0.25 -> {
                    assertEquals(listOf("1.1.1", "1.1.2", "1.1.3", "1.1.4", "1.2.1"), row.labels.take(5).map { it.text })
                    sawSixteenths = true
                }
                row.step == 0.5 -> assertEquals(listOf("1.1.1", "1.1.3", "1.2.1"), row.labels.take(3).map { it.text })
                else -> throw AssertionError("unexpected bar step ${row.step}")
            }
        }
        assertTrue(sawPowerOfTwo && sawBars && sawBeats && sawSixteenths, "sweep covered $sawPowerOfTwo $sawBars $sawBeats $sawSixteenths")
    }

    @Test
    fun timeLabelsGoFromMinutesToSecondsToTenthsToHundredths() {
        val steps = sweep.map { labels(it).time.step }
        assertTrue(steps.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(steps.first() >= 10.0, "widest time step ${steps.first()}")
        assertTrue(steps.last() <= 0.05, "closest time step ${steps.last()}")
        val minutes = labels(1.0).time
        assertEquals(60.0, minutes.step)
        assertEquals(listOf("00:00", "01:00", "02:00"), minutes.labels.take(3).map { it.text })
        val twoMinutes = labels(0.5).time
        assertEquals(120.0, twoMinutes.step)
        assertEquals(listOf("00:00", "02:00", "04:00"), twoMinutes.labels.take(3).map { it.text })
        val seconds = labels(40.0).time
        assertEquals(1.0, seconds.step)
        assertEquals(listOf("00:00", "00:01", "00:02"), seconds.labels.take(3).map { it.text })
        val tenths = labels(400.0).time
        assertEquals(0.1, tenths.step)
        assertEquals(listOf("00:00.0", "00:00.1", "00:00.2"), tenths.labels.take(3).map { it.text })
        val hundredths = labels(1_000.0).time
        assertTrue(hundredths.step <= 0.05)
        assertEquals("00:00.00", hundredths.labels.first().text)
        assertTrue(hundredths.labels[1].text.length == 8, hundredths.labels[1].text)
    }

    @Test
    fun ticksAreOneLevelBelowTheLabels() {
        for (ppb in sweep) {
            val result = labels(ppb)
            for (row in listOf(result.bars, result.time)) {
                val finer = row.finerStep
                if (finer == null) {
                    assertTrue(row.ticks.isEmpty())
                } else {
                    assertTrue(row.ticks.size >= row.labels.size, "ticks ${row.ticks.size} labels ${row.labels.size} at $ppb")
                    val spacing = row.ticks.zipWithNext { a, b -> b - a }
                    spacing.forEach { assertTrue(abs(it - spacing.first()) < 1e-6, "uneven ticks at $ppb") }
                }
            }
        }
        val bars = labels(12.0).bars
        assertEquals(4.0, bars.step)
        assertEquals(1.0, bars.finerStep)
        assertEquals(listOf(0.0, 12.0, 24.0, 36.0), bars.ticks.take(4))
    }

    @Test
    fun labelsStartAtTheFirstLineInsideTheScrolledWindow() {
        val scrolled = labels(12.0, scroll = 6.0).bars
        assertEquals("3", scrolled.labels.first().text)
        assertEquals(24.0, scrolled.labels.first().x)
        val time = labels(40.0, scroll = 2.5).time
        assertEquals("00:02", time.labels.first().text)
        assertEquals(60.0, time.labels.first().x)
    }

    @Test
    fun sixEightCountsSixBeatsPerBarAndSubdividesToSixteenths() {
        val bars = labels(10.0, TimeSignature.SixEight).bars
        assertEquals(6.0, bars.step)
        assertEquals(listOf("1", "2"), bars.labels.take(2).map { it.text })
        val fine = labels(1_000.0, TimeSignature.SixEight).bars
        assertEquals(0.5, fine.step)
        assertEquals(listOf("1.1.1", "1.1.2", "1.2.1"), fine.labels.take(3).map { it.text })
        assertTrue(fine.finerStep == null)
    }

    @Test
    fun theTimeRowFollowsTheTempo() {
        val slow = labels(40.0, tempo = Tempo(60)).time
        assertEquals(2.0, slow.step)
        assertEquals(80.0, slow.labels[1].x)
        val fast = labels(40.0, tempo = Tempo(240)).time
        assertEquals(0.5, fast.step)
        assertEquals(80.0, fast.labels[1].x)
    }

    @Test
    fun theSweepCoversTheWholeRange() {
        assertTrue(abs(ln(sweep.last() / sweep.first()) - ln(256.0)) < 1e-9)
    }
}
