package audio.tembra.ui.metronome.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.ui.metronome.MetronomeCopy

/**
 * The phone-landscape style (T152, contracts/design-system-additions.md): a [MenuPill] naming the [selected] style
 * ("Standard"), announced "Style, Standard", that opens Standard, Downbeat and Syncopated with a check on the current
 * one. With null (cells edited by hand, no style matches) the pill reads "Style", is announced "Style, none" and no
 * entry is checked. Entries are tagged `<testTag>.<id>` (`metronome.style.downbeat`), the same tags as the portrait
 * style pills.
 */
@Composable
internal fun StyleMenu(
    selected: MetronomeStyle?,
    onSelect: (MetronomeStyle) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "metronome.style",
) {
    val entries = remember { MetronomeStyle.entries.map { MenuEntry(it.id, it.label) } }
    MenuPill(
        label = selected?.label ?: MetronomeCopy.style,
        description = MetronomeCopy.styleOf(selected?.label),
        entries = entries,
        selectedId = selected?.id,
        onSelect = { id -> MetronomeStyle.byId(id)?.let(onSelect) },
        testTag = testTag,
        modifier = modifier,
    )
}
