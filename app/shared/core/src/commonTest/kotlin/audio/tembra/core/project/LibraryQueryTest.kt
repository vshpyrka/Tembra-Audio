package audio.tembra.core.project

import audio.tembra.core.metronome.Tempo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Filters, sorts, search and the count texts of the Projects library (spec FR-050, FR-053). */
class LibraryQueryTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun summary(id: String, name: String, description: String = "", editedDaysAgo: Long = 0, isDemo: Boolean = false): LibrarySummary =
        LibrarySummary(
            id = ProjectId(id), name = name, description = description, key = null, tempo = Tempo(120), lengthSeconds = 30.0,
            trackCount = 2, byteSize = 1_000, lastEditedEpochMs = now - editedDaysAgo * day, isDemo = isDemo,
        )

    private val demo = summary("demo", "Demo project", "Four loops to play with", editedDaysAgo = 1, isDemo = true)
    private val band = summary("band", "band rehearsal", "Tuesday take", editedDaysAgo = 10)
    private val acoustic = summary("ac", "Acoustic sketch", editedDaysAgo = 0)
    private val old = summary("old", "Zeta", "loops and pads", editedDaysAgo = 8)
    private val all = listOf(band, demo, old, acoustic)

    @Test
    fun allSortedByLastEditedDescendingByDefault() {
        assertEquals(listOf(acoustic, demo, old, band), LibraryQuery().apply(all, now))
    }

    @Test
    fun recentKeepsProjectsEditedWithinSevenDays() {
        assertEquals(listOf(acoustic, demo), LibraryQuery(filter = LibraryFilter.Recent).apply(all, now))
        val justInside = summary("edge", "Edge", editedDaysAgo = 7)
        assertEquals(listOf(justInside), LibraryQuery(filter = LibraryFilter.Recent).apply(listOf(justInside, old), now))
        val justOutside = summary("edge", "Edge", editedDaysAgo = 7).copy(lastEditedEpochMs = now - 7 * day - 1)
        assertEquals(emptyList(), LibraryQuery(filter = LibraryFilter.Recent).apply(listOf(justOutside), now))
    }

    @Test
    fun nameSortIsCaseInsensitiveAscending() {
        assertEquals(listOf(acoustic, band, demo, old), LibraryQuery(sort = LibrarySort.Name).apply(all, now))
    }

    @Test
    fun searchMatchesNameOrDescriptionCaseInsensitively() {
        assertEquals(listOf(demo, old), LibraryQuery(text = "LOOPS").apply(all, now))
        assertEquals(listOf(band), LibraryQuery(text = "rehearsal").apply(all, now))
        assertEquals(emptyList(), LibraryQuery(text = "nothing here").apply(all, now))
        assertEquals(listOf(acoustic, demo, old, band), LibraryQuery(text = "  ").apply(all, now))
    }

    @Test
    fun filtersSortAndSearchCombine() {
        assertEquals(listOf(demo), LibraryQuery(text = "loops", filter = LibraryFilter.Recent, sort = LibrarySort.Name).apply(all, now))
    }

    @Test
    fun countTexts() {
        assertEquals("4 projects", LibraryQuery().countText(shown = 4, total = 4))
        assertEquals("1 project", LibraryQuery().countText(shown = 1, total = 1))
        assertEquals("0 projects", LibraryQuery().countText(shown = 0, total = 0))
        assertEquals("2 of 4 projects", LibraryQuery(text = "loops").countText(shown = 2, total = 4))
        assertEquals("0 of 4 projects", LibraryQuery(text = "zzz").countText(shown = 0, total = 4))
        assertEquals("2 of 4 projects", LibraryQuery(filter = LibraryFilter.Recent).countText(shown = 2, total = 4))
        assertEquals("4 of 4 projects", LibraryQuery(filter = LibraryFilter.Recent).countText(shown = 4, total = 4))
    }
}
