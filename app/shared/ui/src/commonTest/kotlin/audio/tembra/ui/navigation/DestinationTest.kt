package audio.tembra.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** The registry of data-model.md §1 (spec FR-002, FR-011, FR-013); Home was removed on 2026-10-17. */
class DestinationTest {

    @Test
    fun containsExactlyFiveDestinationsInDisplayOrder() {
        assertEquals(
            listOf(Destination.Timeline, Destination.Tuner, Destination.Metronome, Destination.Projects, Destination.Profile),
            Destination.entries.toList(),
        )
    }

    @Test
    fun idsAreStableLowercaseIdentifiers() {
        assertEquals(listOf("timeline", "tuner", "metronome", "projects", "profile"), Destination.entries.map { it.id })
    }

    @Test
    fun labelAndTitleEqualTheName() {
        for (destination in Destination.entries) {
            assertEquals(destination.name, destination.label, "${destination.name} label")
            assertEquals(destination.name, destination.title, "${destination.name} title")
        }
    }

    @Test
    fun idsLabelsAndTitlesAreUnique() {
        assertEquals(5, Destination.entries.map { it.id }.distinct().size)
        assertEquals(5, Destination.entries.map { it.label }.distinct().size)
        assertEquals(5, Destination.entries.map { it.title }.distinct().size)
    }

    @Test
    fun noDestinationHasSupportingContentSinceHomeWasRemoved() {
        assertEquals(emptyList(), Destination.entries.filter { it.hasSupportingPane })
    }

    @Test
    fun placeholderMessagesAreTheAgreedSentences() {
        val expected = mapOf(
            Destination.Timeline to "The timeline arranger arrives with the transport and timeline feature.",
            Destination.Tuner to "The instrument tuner arrives with the tuner feature.",
            Destination.Metronome to "The metronome arrives with the metronome feature.",
            Destination.Projects to "Your projects and library arrive with the projects feature.",
            Destination.Profile to "Your profile and sign-in arrive with the collaboration feature.",
        )
        assertEquals(expected, Destination.entries.associateWith { it.placeholderMessage })
    }
}
