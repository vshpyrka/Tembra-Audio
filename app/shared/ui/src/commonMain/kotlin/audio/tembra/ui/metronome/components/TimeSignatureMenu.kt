package audio.tembra.ui.metronome.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.ui.metronome.MetronomeCopy

/**
 * The compact layouts' time signature (design iteration D1, contracts/design-system-additions.md): a [MenuPill] naming
 * the [selected] signature ("4/4"), announced "Time signature, 4/4", that opens the six signatures in the spec's order
 * with a check on the current one. Entries are tagged `<testTag>.<id>` (`metronome.signature.7-8`), the same tags as
 * the chips it replaces on a phone.
 */
@Composable
internal fun TimeSignatureMenu(
    selected: TimeSignature,
    onSelect: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.signature",
) {
    val entries = remember { TimeSignature.entries.map { MenuEntry(it.id, it.label) } }
    MenuPill(
        label = selected.label,
        description = MetronomeCopy.timeSignatureOf(selected.label),
        entries = entries,
        selectedId = selected.id,
        onSelect = { id -> TimeSignature.byId(id)?.let(onSelect) },
        testTag = testTag,
        modifier = modifier,
    )
}
