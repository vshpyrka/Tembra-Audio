package audio.tembra.ui.metronome.components

import audio.tembra.core.metronome.Tempo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Angle ↔ tempo on the dial's 275° track, and the ring's proportions (data-model.md §9, research R8). */
class TempoDialGeometryTest {

    private val epsilon = 0.001f

    @Test
    fun sweepGrowsLinearlyOverTheTrack() {
        assertEquals(0f, TempoDialGeometry.sweepFor(Tempo(40)), epsilon)
        assertEquals(275f, TempoDialGeometry.sweepFor(Tempo(240)), epsilon)
        assertEquals(110f, TempoDialGeometry.sweepFor(Tempo(120)), epsilon)
    }

    @Test
    fun tempoAtAnAngleRoundsToWholeBeats() {
        assertEquals(Tempo(40), TempoDialGeometry.tempoAt(0f))
        assertEquals(Tempo(240), TempoDialGeometry.tempoAt(275f))
        assertEquals(Tempo(140), TempoDialGeometry.tempoAt(137.5f))
    }

    @Test
    fun anglesOutsideTheTrackClamp() {
        assertEquals(Tempo(240), TempoDialGeometry.tempoAt(300f))
        assertEquals(Tempo(40), TempoDialGeometry.tempoAt(-10f))
    }

    @Test
    fun pointerAnglesAreMeasuredClockwiseFromTheTop() {
        assertEquals(0f, TempoDialGeometry.angleFromTop(dx = 0f, dy = -1f), epsilon)
        assertEquals(90f, TempoDialGeometry.angleFromTop(dx = 1f, dy = 0f), epsilon)
        assertEquals(180f, TempoDialGeometry.angleFromTop(dx = 0f, dy = 1f), epsilon)
        assertEquals(270f, TempoDialGeometry.angleFromTop(dx = -1f, dy = 0f), epsilon)
        // Just left of the top lies in the gap nearer the start, so it reads as a negative angle (clamped to 40).
        assertEquals(-10f, TempoDialGeometry.angleFromTop(dx = -0.17364818f, dy = -0.9848077f), 0.01f)
    }

    @Test
    fun ringAndTickProportions() {
        assertEquals(0.4f, TempoDialGeometry.RING_RADIUS, epsilon)
        assertEquals(listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f), TempoDialGeometry.tickAngles)
        assertEquals(0.408f, TempoDialGeometry.TICK_INNER, epsilon)
        assertEquals(0.442f, TempoDialGeometry.TICK_OUTER, epsilon)
    }
}
