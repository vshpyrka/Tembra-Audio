package audio.tembra.core.timeline

import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import kotlin.test.Test
import kotlin.test.assertEquals

/** The readout and chip formats: hundredths truncated, never rounded (spec FR-009, FR-010). */
class TimeFormatTest {

    @Test
    fun clockIsMinutesSecondsHundredthsTruncated() {
        assertEquals("00:42.20", TimeFormat.clock(42.2))
        assertEquals("00:30.96", TimeFormat.clock(30.9677))
        assertEquals("00:00.00", TimeFormat.clock(0.0))
        assertEquals("00:00.99", TimeFormat.clock(0.999))
        assertEquals("61:01.99", TimeFormat.clock(3661.999))
        assertEquals("00:00.00", TimeFormat.clock(-1.0))
        assertEquals("00:05.00", TimeFormat.clock(5.0))
        assertEquals("00:04.99", TimeFormat.clock(4.9999))
    }

    @Test
    fun coarserClocksForTheRulerRows() {
        assertEquals("00:05", TimeFormat.clock(5.25, decimals = 0))
        assertEquals("00:05.2", TimeFormat.clock(5.25, decimals = 1))
        assertEquals("00:05.25", TimeFormat.clock(5.25, decimals = 2))
        assertEquals("02:00", TimeFormat.clock(120.0, decimals = 0))
        assertEquals("00:09", TimeFormat.clock(9.99, decimals = 0))
    }

    @Test
    fun barBeatIsOneBasedInTheSignature() {
        assertEquals("1.1", TimeFormat.barBeat(Beats(0.0), TimeSignature.FourFour))
        assertEquals("5.1", TimeFormat.barBeat(Beats(16.0), TimeSignature.FourFour))
        assertEquals("5.2", TimeFormat.barBeat(Beats(17.9), TimeSignature.FourFour))
        assertEquals("2.1", TimeFormat.barBeat(Beats(7.0), TimeSignature.SevenEight))
        assertEquals("1.6", TimeFormat.barBeat(Beats(5.5), TimeSignature.SixEight))
    }

    @Test
    fun readoutJoinsPositionAndLength() {
        assertEquals("00:00.00 / 00:42.20", TimeFormat.readout(positionSeconds = 0.0, lengthSeconds = 42.2))
    }
}
