package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Produces a [SupportingPaneScene] when [enabled] (the window is in [LayoutMode.RailWithPanel]) and the top entry
 * declares a [SupportingPane] in its metadata; otherwise returns null so the single-pane fallback renders the entry
 * alone (spec FR-006, US3 scenario 3).
 */
class SupportingPaneSceneStrategy<T : Any>(private val enabled: Boolean) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!enabled) return null
        val top = entries.lastOrNull() ?: return null
        val pane = top.metadata[SupportingPaneKey] ?: return null
        return SupportingPaneScene(key = top.contentKey, main = top, pane = pane, previousEntries = entries.dropLast(1))
    }
}
