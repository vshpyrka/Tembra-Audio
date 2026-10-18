package audio.tembra.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.designsystem.EmptyState
import audio.tembra.ui.designsystem.TembraSupportingPane

/**
 * The Library panel with its empty state (spec FR-012): Home's supporting content on expanded windows until Home was
 * removed on 2026-10-17; no destination declares it now.
 */
@Composable
fun LibraryPane(modifier: Modifier = Modifier) {
    TembraSupportingPane(title = "Library", modifier = modifier) {
        EmptyState("Loops and sounds will appear here.")
    }
}
