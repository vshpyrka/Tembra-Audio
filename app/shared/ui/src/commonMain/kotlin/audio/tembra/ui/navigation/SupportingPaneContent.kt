package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.ui.home.LibraryPane

/** The single mapping from a [SupportingPane] to the feature package that draws it. */
@Composable
fun SupportingPaneContent(pane: SupportingPane, modifier: Modifier = Modifier) {
    when (pane) {
        SupportingPane.Library -> LibraryPane(modifier)
    }
}
