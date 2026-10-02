package audio.tembra.ui.metronome.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.designsystem.ChipAccent
import audio.tembra.ui.designsystem.ChipSpec
import audio.tembra.ui.designsystem.TembraChipRow

/**
 * The six time signatures (spec FR-006) as a [TembraChipRow] in the `secondary` accent, [columns] per row (one row on
 * a phone, two rows of three in the landscape column). Each chip is tagged `<testTag>.<id>` (`metronome.signature.4-4`).
 */
@Composable
internal fun TimeSignatureChips(
    selected: TimeSignature,
    onSelect: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = TimeSignature.entries.size,
    testTag: String = "metronome.signature",
) {
    val items = remember { TimeSignature.entries.map { ChipSpec(id = it.id, label = it.label) } }
    TembraChipRow(
        items = items,
        selectedId = selected.id,
        onSelect = { id -> TimeSignature.byId(id)?.let(onSelect) },
        accent = ChipAccent.Secondary,
        modifier = modifier,
        columns = columns,
        testTag = testTag,
    )
}
