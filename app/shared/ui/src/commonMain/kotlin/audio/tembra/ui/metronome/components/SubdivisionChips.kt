package audio.tembra.ui.metronome.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.BeatUnit
import audio.tembra.core.metronome.Subdivision
import audio.tembra.ui.designsystem.ChipAccent
import audio.tembra.ui.designsystem.ChipSpec
import audio.tembra.ui.designsystem.TembraChipRow
import audio.tembra.ui.theme.TembraIcon

/**
 * The four subdivisions (spec FR-007) as a [TembraChipRow] in the `primary` accent, each with the note glyph and the
 * note value for the beat [unit] ("1/4", "1/8", "1/16", "3let" on a quarter; "1/8", "1/16", "1/32", "3let" on an
 * eighth). Each chip is tagged `<testTag>.<id>` (`metronome.subdivision.2`).
 */
@Composable
internal fun SubdivisionChips(
    selected: Subdivision,
    unit: BeatUnit,
    onSelect: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.subdivision",
) {
    val items = remember(unit) {
        Subdivision.entries.map { ChipSpec(id = it.id, label = it.label(unit), icon = TembraIcon.MusicNote) }
    }
    TembraChipRow(
        items = items,
        selectedId = selected.id,
        onSelect = { id -> Subdivision.byId(id)?.let(onSelect) },
        accent = ChipAccent.Primary,
        modifier = modifier,
        testTag = testTag,
    )
}
