package audio.tembra.ui.mixer

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the mixer's own window renders (feature 012, spec FR-021a; data-model.md §4): the open project's strips and
 * master in the full form, or nothing once the project is gone ([open] false: the window closes). No selection: the
 * timeline's selection stays in its own window.
 */
@Immutable
data class MixerWindowUiState(
    val open: Boolean = false,
    val strips: ImmutableList<StripUi> = persistentListOf(),
    val master: MasterUi = MasterUi(),
    /** False while a take runs: the strips' menus offer no move or delete (feature 010, spec FR-018). */
    val canReorder: Boolean = true,
)
