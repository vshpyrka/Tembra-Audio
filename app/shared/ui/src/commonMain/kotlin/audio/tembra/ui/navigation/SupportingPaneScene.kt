package audio.tembra.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import audio.tembra.ui.theme.TembraTheme

/**
 * A Navigation 3 scene that lays the top entry beside its supporting pane (research R2, R5). A data class because
 * NavDisplay compares scenes by equality; [key] is the entry's content key so the entry keeps its state.
 */
data class SupportingPaneScene<T : Any>(
    override val key: Any,
    val main: NavEntry<T>,
    val pane: SupportingPane,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {

    override val entries: List<NavEntry<T>>
        get() = listOf(main)

    override val content: @Composable () -> Unit = {
        val spacing = TembraTheme.spacing
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { main.Content() }
            SupportingPaneContent(
                pane = pane,
                modifier = Modifier.padding(top = spacing.lg, end = spacing.pageMargin, bottom = spacing.lg),
            )
        }
    }
}
