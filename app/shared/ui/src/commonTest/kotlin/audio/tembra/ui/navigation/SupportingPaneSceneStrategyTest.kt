package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneStrategyScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The scene strategy behind the side panel (spec FR-006; research R2). No destination declares supporting content since
 * Home and its Library panel were removed (2026-10-17), so the entries here carry the metadata themselves.
 */
class SupportingPaneSceneStrategyTest {

    private fun entry(destination: Destination, metadata: Map<String, Any> = emptyMap()) =
        NavEntry(key = DestinationKey(destination), metadata = metadata) { }

    private fun SupportingPaneSceneStrategy<DestinationKey>.calculate(entries: List<NavEntry<DestinationKey>>) =
        with(SceneStrategyScope<DestinationKey>()) { calculateScene(entries) }

    @Test
    fun disabledStrategyNeverProducesAScene() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = false)
        assertNull(strategy.calculate(listOf(entry(Destination.Timeline, supportingPane(SupportingPane.Library)))))
    }

    @Test
    fun entriesWithoutSupportingContentFallThrough() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        assertNull(strategy.calculate(listOf(entry(Destination.Timeline), entry(Destination.Tuner))))
    }

    @Test
    fun theTopEntryWithSupportingContentGetsAPaneScene() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        val start = entry(Destination.Timeline, supportingPane(SupportingPane.Library))
        val scene = assertIs<SupportingPaneScene<DestinationKey>>(strategy.calculate(listOf(start)))
        assertEquals(start.contentKey, scene.key)
        assertEquals(listOf(start), scene.entries)
        assertEquals(emptyList(), scene.previousEntries)
        assertEquals(SupportingPane.Library, scene.pane)
    }

    @Test
    fun previousEntriesAreEverythingBelowTheTop() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        val start = entry(Destination.Timeline)
        val top = entry(Destination.Tuner, supportingPane(SupportingPane.Library))
        val scene = assertIs<SupportingPaneScene<DestinationKey>>(strategy.calculate(listOf(start, top)))
        assertEquals(listOf(start), scene.previousEntries)
    }

    @Test
    fun equalInputsProduceEqualScenes() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        val entries = listOf(entry(Destination.Timeline, supportingPane(SupportingPane.Library)))
        assertEquals(strategy.calculate(entries), strategy.calculate(entries))
    }
}
