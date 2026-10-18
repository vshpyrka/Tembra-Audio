package audio.tembra.data.projects

import audio.tembra.core.project.ProjectId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `library.json` (contracts/project-storage.md): a cache of the documents' card facts plus the id of the open project
 * (spec FR-047). Rebuilt from the folders when missing, unreadable or older than a document.
 */
@Serializable
data class LibraryIndex(
    @SerialName("version") val version: Int = CURRENT_VERSION,
    @SerialName("openProjectId") val openProjectId: ProjectId? = null,
    @SerialName("entries") val entries: List<LibraryEntry>,
) {
    fun entry(id: ProjectId): LibraryEntry? = entries.firstOrNull { it.id == id }

    /** This index with [entry] replacing the one of the same id, or appended. */
    fun withEntry(entry: LibraryEntry): LibraryIndex = copy(entries = entries.filterNot { it.id == entry.id } + entry)

    /** "Project N" with N one past the highest numbered name (spec FR-052). */
    fun nextProjectName(): String {
        val highest = entries.mapNotNull { numbered.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "Project ${highest + 1}"
    }

    companion object {
        const val CURRENT_VERSION = 1
        val Empty: LibraryIndex = LibraryIndex(entries = emptyList())
        private val numbered = Regex("Project (\\d+)")
    }
}
