package audio.tembra.core.project

import audio.tembra.core.metronome.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Grid steps in beats of the signature's unit (spec FR-018). */
class GridSizeTest {

    @Test
    fun stepsInFourFour() {
        assertEquals(Beats(4.0), GridSize.Bar.beatsOf(TimeSignature.FourFour))
        assertEquals(Beats(1.0), GridSize.Quarter.beatsOf(TimeSignature.FourFour))
        assertEquals(Beats(0.5), GridSize.Eighth.beatsOf(TimeSignature.FourFour))
        assertEquals(Beats(0.25), GridSize.Sixteenth.beatsOf(TimeSignature.FourFour))
        assertNull(GridSize.Off.beatsOf(TimeSignature.FourFour))
    }

    @Test
    fun stepsInSixEightCountEighthNoteBeats() {
        assertEquals(Beats(6.0), GridSize.Bar.beatsOf(TimeSignature.SixEight))
        assertEquals(Beats(2.0), GridSize.Quarter.beatsOf(TimeSignature.SixEight))
        assertEquals(Beats(1.0), GridSize.Eighth.beatsOf(TimeSignature.SixEight))
        assertEquals(Beats(0.5), GridSize.Sixteenth.beatsOf(TimeSignature.SixEight))
    }

    @Test
    fun labelsAndDefault() {
        assertEquals(listOf("Bar", "1/4", "1/8", "1/16", "Off"), GridSize.entries.map { it.label })
        assertEquals(GridSize.Sixteenth, GridSize.Default)
    }
}
