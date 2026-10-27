package audio.tembra.core.timeline

import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.Beats
import audio.tembra.core.project.GridSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** Snapping to the nearest grid line for every grid size (spec FR-018). */
class SnapTest {

    @Test
    fun snapsToTheNearestLineOfEachGridInFourFour() {
        assertEquals(Beats(4.0), Snap.snap(Beats(5.9), GridSize.Bar, TimeSignature.FourFour))
        assertEquals(Beats(8.0), Snap.snap(Beats(6.1), GridSize.Bar, TimeSignature.FourFour))
        assertEquals(Beats(4.0), Snap.snap(Beats(4.3), GridSize.Quarter, TimeSignature.FourFour))
        assertEquals(Beats(5.0), Snap.snap(Beats(4.6), GridSize.Quarter, TimeSignature.FourFour))
        assertEquals(Beats(4.5), Snap.snap(Beats(4.3), GridSize.Eighth, TimeSignature.FourFour))
        assertEquals(Beats(4.25), Snap.snap(Beats(4.3), GridSize.Sixteenth, TimeSignature.FourFour))
    }

    @Test
    fun offIsTheIdentity() {
        assertEquals(Beats(4.3), Snap.snap(Beats(4.3), GridSize.Off, TimeSignature.FourFour))
    }

    @Test
    fun followsTheSignatureUnitAndNeverGoesBelowZero() {
        assertEquals(Beats(6.0), Snap.snap(Beats(5.9), GridSize.Bar, TimeSignature.SixEight))
        assertEquals(Beats(2.0), Snap.snap(Beats(2.9), GridSize.Quarter, TimeSignature.SixEight))
        assertEquals(Beats(0.5), Snap.snap(Beats(0.6), GridSize.Sixteenth, TimeSignature.SixEight))
        assertEquals(Beats.Zero, Snap.snap(Beats(-0.4), GridSize.Quarter, TimeSignature.FourFour))
        assertEquals(Beats.Zero, Snap.snap(Beats(-3.0), GridSize.Off, TimeSignature.FourFour))
    }
}
