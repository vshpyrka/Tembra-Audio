package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.project.CountIn
import audio.tembra.ui.metronome.components.MenuEntry
import audio.tembra.ui.metronome.components.MenuPill
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * The count-in setting in the metronome sheet (feature 010, spec FR-015; contracts/timeline-recording-ui.md
 * "CountInMenu"): a [MenuPill] row "Count-in: Off / 1 bar / 2 bars" under the time signature, read-only while a take
 * runs ([enabled], FR-018). Tagged `metronome.sheet.countIn`; the entries `metronome.sheet.countIn.<id>`.
 */
@Composable
internal fun CountInMenu(
    value: CountIn,
    enabled: Boolean,
    onSelect: (CountIn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember { CountIn.entries.map { MenuEntry(it.name, label(it)) } }
    MenuPill(
        label = "${TimelineCopy.countIn}: ${label(value)}",
        description = "${TimelineCopy.countIn}, ${label(value)}",
        entries = entries,
        selectedId = value.name,
        onSelect = { id -> CountIn.entries.firstOrNull { it.name == id }?.let(onSelect) },
        testTag = TimelineTestTags.COUNT_IN,
        modifier = modifier,
        enabled = enabled,
    )
}

private fun label(countIn: CountIn): String = when (countIn) {
    CountIn.OFF -> TimelineCopy.countInOff
    CountIn.ONE_BAR -> TimelineCopy.countInOneBar
    CountIn.TWO_BARS -> TimelineCopy.countInTwoBars
}
