package audio.tembra.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Beat levels and the accent pattern (spec FR-008, FR-010). */
class AccentPatternTest {

    private val accentThenClicks = AccentPattern(
        listOf(BeatLevel.Accent, BeatLevel.Click, BeatLevel.Click, BeatLevel.Click),
    )

    @Test
    fun levelsCycleAccentClickSub() {
        assertEquals(BeatLevel.Click, BeatLevel.Accent.next())
        assertEquals(BeatLevel.Sub, BeatLevel.Click.next())
        assertEquals(BeatLevel.Accent, BeatLevel.Sub.next())
    }

    @Test
    fun byIdParsesKnownIdsOnly() {
        assertEquals(BeatLevel.Sub, BeatLevel.byId('S'))
        assertNull(BeatLevel.byId('X'))
    }

    @Test
    fun cycleChangesOnlyTheTappedBeat() {
        val cycled = accentThenClicks.cycle(1)
        assertEquals(BeatLevel.Sub, cycled.levels[1])
        assertEquals(BeatLevel.Accent, cycled.levels[0])
        assertEquals(BeatLevel.Click, cycled.levels[2])
    }

    @Test
    fun resizedToKeepsExistingBeatsAndAppendsClicks() {
        assertEquals(
            listOf(
                BeatLevel.Accent, BeatLevel.Click, BeatLevel.Click, BeatLevel.Click,
                BeatLevel.Click, BeatLevel.Click,
            ),
            accentThenClicks.resizedTo(6).levels,
        )
        assertEquals(listOf(BeatLevel.Accent, BeatLevel.Click), accentThenClicks.resizedTo(2).levels)
    }

    @Test
    fun encodesAsOneCharacterPerBeat() {
        assertEquals("ACCC", accentThenClicks.encode())
    }

    @Test
    fun decodeRefitsALengthMismatch() {
        assertEquals("ACSC", AccentPattern.decode("ACS", 4)!!.encode())
        assertEquals("AC", AccentPattern.decode("ACS", 2)!!.encode())
        assertEquals("ACS", AccentPattern.decode("ACS", 3)!!.encode())
    }

    @Test
    fun decodeRejectsAnUnknownCharacter() {
        assertNull(AccentPattern.decode("AXC", 3))
    }
}
