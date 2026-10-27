package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The project's defaults and name rules (data-model.md section 1; spec FR-003). */
class ProjectTest {

    private val project = Project(
        id = ProjectId("p"),
        name = "Song",
        tempo = Tempo(120),
        timeSignature = TimeSignature.FourFour,
        tracks = persistentListOf(),
        assets = persistentMapOf(),
    )

    @Test
    fun defaultsAreNoDescriptionNoKeyEightyPercentMasterAndMetronomeOff() {
        assertEquals("", project.description)
        assertNull(project.key)
        assertEquals(Percent(80), project.masterVolume)
        assertFalse(project.metronomeOn)
    }

    @Test
    fun aNameIsValidWhenItsTrimmedLengthIsOneToSixty() {
        assertTrue(ProjectNames.isValidProjectName("A"))
        assertTrue(ProjectNames.isValidProjectName("  A  "))
        assertTrue(ProjectNames.isValidProjectName("x".repeat(60)))
        assertFalse(ProjectNames.isValidProjectName(""))
        assertFalse(ProjectNames.isValidProjectName("   "))
        assertFalse(ProjectNames.isValidProjectName("x".repeat(61)))
        assertTrue(ProjectNames.isValidProjectName("  " + "x".repeat(60) + "  "))
    }

    @Test
    fun aDescriptionIsValidUpToOneHundredAndTwentyCharacters() {
        assertTrue(ProjectNames.isValidDescription(""))
        assertTrue(ProjectNames.isValidDescription("x".repeat(120)))
        assertFalse(ProjectNames.isValidDescription("x".repeat(121)))
    }

    @Test
    fun newProjectStartsAtOneHundredAndTwentyInFourFourWithNoTracks() {
        val fresh = Project.new(ProjectId("n"), "Project 3")
        assertEquals(Tempo(120), fresh.tempo)
        assertEquals(TimeSignature.FourFour, fresh.timeSignature)
        assertTrue(fresh.tracks.isEmpty())
        assertTrue(fresh.assets.isEmpty())
        assertEquals("", fresh.description)
        assertNull(fresh.key)
    }

    @Test
    fun tempoMapIsBuiltFromTempoAndSignature() {
        assertEquals(TempoMap(Tempo(120), TimeSignature.FourFour), project.tempoMap)
    }
}
