package audio.tembra.ui.projects.components

import audio.tembra.ui.projects.ProjectsCopy
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A last-edited time as the cards read it (spec FR-051): "Just now" within a minute, "2 m ago" within the hour,
 * "3 h ago" earlier the same day, "Yesterday 22:40", "3 days ago" within the week, then the date ("9 Oct 2026").
 * Calendar days are counted in [zone], the device's by default.
 */
object RelativeTime {
    fun format(epochMs: Long, nowMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val elapsed = nowMs - epochMs
        if (elapsed < MINUTE_MS) return ProjectsCopy.justNow
        if (elapsed < HOUR_MS) return "${elapsed / MINUTE_MS} m ago"
        val then = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)
        val days = (now.date.toEpochDays() - then.date.toEpochDays()).toInt()
        return when {
            days <= 0 -> "${elapsed / HOUR_MS} h ago"
            days == 1 -> "Yesterday ${two(then.hour)}:${two(then.minute)}"
            days < 7 -> "$days days ago"
            else -> "${then.day} ${MONTHS[then.month.number - 1]} ${then.year}"
        }
    }

    private fun two(value: Int): String = value.toString().padStart(2, '0')

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
