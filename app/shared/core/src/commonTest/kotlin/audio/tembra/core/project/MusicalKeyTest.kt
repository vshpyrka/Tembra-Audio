package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The 24 keys with the design's display names, sharps only (spec FR-003, FR-052). */
class MusicalKeyTest {

    @Test
    fun twentyFourKeysTwelveMajorThenTwelveMinor() {
        assertEquals(24, MusicalKey.entries.size)
        assertEquals(
            listOf("Cmaj", "C#maj", "Dmaj", "D#maj", "Emaj", "Fmaj", "F#maj", "Gmaj", "G#maj", "Amaj", "A#maj", "Bmaj"),
            MusicalKey.entries.take(12).map { it.displayName },
        )
        assertEquals(
            listOf("Cmin", "C#min", "Dmin", "D#min", "Emin", "Fmin", "F#min", "Gmin", "G#min", "Amin", "A#min", "Bmin"),
            MusicalKey.entries.drop(12).map { it.displayName },
        )
    }

    @Test
    fun parsesDisplayNamesAndAnswersNullForAnythingElse() {
        assertEquals(MusicalKey.DMinor, MusicalKey.fromDisplayName("Dmin"))
        assertEquals(MusicalKey.FSharpMajor, MusicalKey.fromDisplayName("F#maj"))
        assertNull(MusicalKey.fromDisplayName("Dbmin"))
        assertNull(MusicalKey.fromDisplayName(""))
    }
}
