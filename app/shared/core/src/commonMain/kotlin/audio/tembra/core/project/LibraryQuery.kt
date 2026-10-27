package audio.tembra.core.project

/**
 * The search text, filter and sort of the Projects library (spec FR-050, FR-053), applied in memory to the library
 * index's summaries: "Recent" keeps projects edited within seven days of the injected `now`; the text matches the
 * name or the description case-insensitively; "Last edited" sorts newest first, "Name" ascending case-insensitively.
 */
data class LibraryQuery(
    val text: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
    val sort: LibrarySort = LibrarySort.LastEdited,
) {
    /** True when the text or the filter can narrow the list, so the count reads "M of N". */
    val narrows: Boolean get() = text.isNotBlank() || filter != LibraryFilter.All

    fun apply(entries: List<LibrarySummary>, nowEpochMs: Long): List<LibrarySummary> {
        val needle = text.trim()
        val recentSince = nowEpochMs - RECENT_WINDOW_MS
        val matched = entries.filter { entry ->
            val recentEnough = when (filter) {
                LibraryFilter.All -> true
                LibraryFilter.Recent -> entry.lastEditedEpochMs >= recentSince
            }
            recentEnough && (needle.isEmpty() || entry.name.contains(needle, ignoreCase = true) || entry.description.contains(needle, ignoreCase = true))
        }
        return when (sort) {
            LibrarySort.LastEdited -> matched.sortedByDescending { it.lastEditedEpochMs }
            LibrarySort.Name -> matched.sortedBy { it.name.lowercase() }
        }
    }

    /** "N projects" for the plain list, "M of N projects" when the query narrows it. */
    fun countText(shown: Int, total: Int): String {
        val noun = if (total == 1) "project" else "projects"
        return if (narrows) "$shown of $total $noun" else "$total $noun"
    }

    companion object {
        const val RECENT_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
