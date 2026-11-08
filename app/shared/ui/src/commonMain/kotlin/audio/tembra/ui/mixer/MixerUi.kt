package audio.tembra.ui.mixer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything the mixer sheet renders (feature 012, data-model.md §4; Constitution II): whether it is open and at which
 * height, the strip form and the anchor heights derived from the layout and the lanes box, one strip per track, the
 * master, whether the platform offers a second window (spec FR-021a) and the selected track's strip. Immutable; the
 * meters read the engine's telemetry in draw instead.
 */
@Immutable
data class MixerUi(
    val open: Boolean = false,
    val height: MixerHeight = MixerHeight.Half,
    val form: StripForm = StripForm.Compact,
    val anchors: MixerAnchors = MixerAnchors.None,
    val strips: ImmutableList<StripUi> = persistentListOf(),
    val master: MasterUi = MasterUi(),
    val windowAvailable: Boolean = false,
    val selectedIndex: Int? = null,
    /** False while a take runs: the strips' menus offer no move or delete (feature 010, spec FR-018). */
    val canReorder: Boolean = true,
) {
    /** The sheet's height now, from its anchors. */
    val sheetHeight: Dp get() = anchors.heightOf(height)
}
