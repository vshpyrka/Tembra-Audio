package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The six signatures in display order (spec FR-006). */
class TimeSignatureTest {

    @Test
    fun entriesAreInDisplayOrder() {
        assertEquals(
            listOf(
                TimeSignature.FourFour,
                TimeSignature.ThreeFour,
                TimeSignature.SixEight,
                TimeSignature.TwoFour,
                TimeSignature.FiveFour,
                TimeSignature.SevenEight,
            ),
            TimeSignature.entries,
        )
    }

    @Test
    fun idsLabelsAndBeats() {
        assertEquals(listOf("4-4", "3-4", "6-8", "2-4", "5-4", "7-8"), TimeSignature.entries.map { it.id })
        assertEquals(listOf("4/4", "3/4", "6/8", "2/4", "5/4", "7/8"), TimeSignature.entries.map { it.label })
        assertEquals(listOf(4, 3, 6, 2, 5, 7), TimeSignature.entries.map { it.beats })
    }

    @Test
    fun onlyTheEighthSignaturesCountEighths() {
        for (signature in TimeSignature.entries) {
            val expected = if (signature == TimeSignature.SixEight || signature == TimeSignature.SevenEight) {
                BeatUnit.Eighth
            } else {
                BeatUnit.Quarter
            }
            assertEquals(expected, signature.unit, signature.id)
        }
    }

    @Test
    fun byIdParsesKnownIdsOnly() {
        assertEquals(TimeSignature.SixEight, TimeSignature.byId("6-8"))
        assertNull(TimeSignature.byId("9-8"))
        assertEquals(TimeSignature.FourFour, TimeSignature.Default)
    }
}
