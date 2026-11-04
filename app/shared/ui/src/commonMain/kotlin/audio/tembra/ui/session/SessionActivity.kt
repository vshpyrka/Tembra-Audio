package audio.tembra.ui.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Work the session does that takes a noticeable time (feature 011, spec FR-015a, research R19): the navigation shell
 * shows the system's busy pointer over the window while [busy] is true.
 */
interface SessionActivity {
    /** True while an import copies files into the project. */
    val busy: StateFlow<Boolean>
}
