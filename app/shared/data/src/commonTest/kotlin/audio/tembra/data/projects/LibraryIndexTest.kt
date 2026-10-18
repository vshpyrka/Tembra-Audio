package audio.tembra.data.projects

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.project.MusicalKey
import audio.tembra.core.project.ProjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The library index's JSON shape and its helpers (contracts/project-storage.md, `LibraryIndex`). */
class LibraryIndexTest {

    private fun entry(id: String, name: String, origin: ProjectOrigin = ProjectOrigin.User): LibraryEntry = LibraryEntry(
        id = ProjectId(id), name = name, description = "", key = MusicalKey.DMinor, tempo = Tempo(124), lengthSeconds = 30.96,
        trackCount = 4, byteSize = 11_891_616, lastEdited = 1_791_331_200_000, origin = origin, overview = CardOverview.Empty,
    )

    @Test
    fun nextProjectNameIsOnePastTheHighestNumber() {
        assertEquals("Project 1", LibraryIndex(entries = emptyList()).nextProjectName())
        assertEquals("Project 4", LibraryIndex(entries = listOf(entry("a", "Project 1"), entry("b", "Song"), entry("c", "Project 3"))).nextProjectName())
    }

    @Test
    fun theJsonCarriesTheContractsFields() {
        val index = LibraryIndex(openProjectId = ProjectId("open"), entries = listOf(entry("demo", "Demo project", ProjectOrigin.Demo)))
        val text = ProjectDocumentCodec().json.encodeToString(LibraryIndex.serializer(), index)
        for (expected in listOf("\"version\": 1", "\"openProjectId\": \"open\"", "\"key\": \"Dmin\"", "\"tempo\": 124", "\"origin\": \"demo\"", "\"lastEdited\": 1791331200000", "\"spans\": 0")) {
            assertTrue(expected in text, "$expected missing from $text")
        }
        assertEquals(index, ProjectDocumentCodec().json.decodeFromString(LibraryIndex.serializer(), text))
        val summary = index.entries.single().toSummary()
        assertTrue(summary.isDemo)
        assertEquals("Demo project", summary.name)
    }
}
