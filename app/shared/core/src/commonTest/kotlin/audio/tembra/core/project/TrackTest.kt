package audio.tembra.core.project

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Track defaults, name rules and "Track N" naming (data-model.md section 1; spec FR-019, FR-020, FR-022). */
class TrackTest {

    @Test
    fun aNewTrackHasNoLabelUnityGainAndIsNeitherMutedNorSoloed() {
        val track = Track.new(TrackId("t"), name = "Track 1", colorIndex = 0)
        assertNull(track.label)
        assertEquals(GainDb.Unity, track.gainDb)
        assertFalse(track.muted)
        assertFalse(track.soloed)
        assertTrue(track.clips.isEmpty())
        assertEquals(0, track.colorIndex)
    }

    @Test
    fun colourIndexMustBeZeroToFive() {
        Track.new(TrackId("t"), "Track 1", colorIndex = 5)
        assertFailsWith<IllegalArgumentException> { Track.new(TrackId("t"), "Track 1", colorIndex = 6) }
        assertFailsWith<IllegalArgumentException> { Track.new(TrackId("t"), "Track 1", colorIndex = -1) }
    }

    @Test
    fun nextNameIsOneMoreThanTheHighestExistingTrackNumber() {
        assertEquals("Track 1", Track.nextName(emptyList()))
        assertEquals("Track 2", Track.nextName(listOf("Track 1")))
        assertEquals("Track 8", Track.nextName(listOf("Track 7", "Lead", "Track 3")))
        assertEquals("Track 1", Track.nextName(listOf("Lead", "Track", "Track x", "Track 2b")))
        assertEquals("Track 11", Track.nextName(listOf("Track 10")))
    }

    @Test
    fun aTrackNameIsValidWhenItsTrimmedLengthIsOneToForty() {
        assertTrue(ProjectNames.isValidTrackName("A"))
        assertTrue(ProjectNames.isValidTrackName("x".repeat(40)))
        assertFalse(ProjectNames.isValidTrackName("x".repeat(41)))
        assertFalse(ProjectNames.isValidTrackName(""))
        assertFalse(ProjectNames.isValidTrackName("  "))
    }

    @Test
    fun emptyNameRevertsToTrackN() {
        assertEquals("Track 3", ProjectNames.trackNameOrDefault("   ", listOf("Track 2", "Lead")))
        assertEquals("Lead", ProjectNames.trackNameOrDefault("  Lead ", listOf("Track 2")))
    }

    @Test
    fun clipsAreKeptInTheOrderGiven() {
        val track = Track(TrackId("t"), "Track 1", null, 0, GainDb.Unity, false, false, persistentListOf(ProjectFixtures.clip(1, 4.0), ProjectFixtures.clip(2, 0.0)))
        assertEquals(listOf(ProjectFixtures.clipId(1), ProjectFixtures.clipId(2)), track.clips.map { it.id })
    }
}
