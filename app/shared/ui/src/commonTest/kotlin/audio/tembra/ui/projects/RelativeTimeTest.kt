package audio.tembra.ui.projects

import audio.tembra.ui.projects.components.RelativeTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cards' relative times (spec FR-051; T117). */
class RelativeTimeTest {

    private val zone = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 19, 12, 0).toInstant(zone).toEpochMilliseconds()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long = LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    @Test
    fun withinTheHourItCountsMinutes() {
        assertEquals("Just now", RelativeTime.format(now - 20_000, now, zone))
        assertEquals("2 m ago", RelativeTime.format(now - 2 * 60_000, now, zone))
        assertEquals("59 m ago", RelativeTime.format(now - 59 * 60_000, now, zone))
    }

    @Test
    fun earlierTodayItCountsHours() {
        assertEquals("3 h ago", RelativeTime.format(at(2026, 10, 19, 9, 0), now, zone))
    }

    @Test
    fun yesterdayShowsTheTime() {
        assertEquals("Yesterday 22:40", RelativeTime.format(at(2026, 10, 18, 22, 40), now, zone))
        assertEquals("Yesterday 09:05", RelativeTime.format(at(2026, 10, 18, 9, 5), now, zone))
    }

    @Test
    fun withinTheWeekItCountsDays() {
        assertEquals("3 days ago", RelativeTime.format(at(2026, 10, 16, 12, 0), now, zone))
        assertEquals("6 days ago", RelativeTime.format(at(2026, 10, 13, 23, 0), now, zone))
    }

    @Test
    fun beyondAWeekItIsTheDate() {
        assertEquals("9 Oct 2026", RelativeTime.format(at(2026, 10, 9, 12, 0), now, zone))
        assertEquals("28 Feb 2025", RelativeTime.format(at(2025, 2, 28, 8, 0), now, zone))
    }
}
