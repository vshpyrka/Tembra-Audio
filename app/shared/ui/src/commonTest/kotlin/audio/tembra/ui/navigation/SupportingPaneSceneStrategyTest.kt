package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneStrategyScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The scene strategy behind the Library panel (spec FR-006, FR-012, US3 scenario 3; research R2). */
class SupportingPaneSceneStrategyTest {

    private fun entry(destination: Destination, metadata: Map<String, Any> = emptyMap()) =
        NavEntry(key = DestinationKey(destination), metadata = metadata) { }

    private fun SupportingPaneSceneStrategy<DestinationKey>.calculate(entries: List<NavEntry<DestinationKey>>) =
        with(SceneStrategyScope<DestinationKey>()) { calculateScene(entries) }

    @Test
    fun disabledStrategyNeverProducesAScene() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = false)
        assertNull(strategy.calculate(listOf(entry(Destination.Home, supportingPane(SupportingPane.Library)))))
    }

    @Test
    fun entriesWithoutSupportingContentFallThrough() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        assertNull(strategy.calculate(listOf(entry(Destination.Home), entry(Destination.Tuner))))
    }

    @Test
    fun theTopEntryWithSupportingContentGetsAPaneScene() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        val home = entry(Destination.Home, supportingPane(SupportingPane.Library))
        val scene = assertIs<SupportingPaneScene<DestinationKey>>(strategy.calculate(listOf(home)))
        assertEquals(home.contentKey, scene.key)
        assertEquals(listOf(home), scene.entries)
        assertEquals(emptyList(), scene.previousEntries)
        assertEquals(SupportingPane.Library, scene.pane)
    }

    @Test
    fun previousEntriesAreEverythingBelowTheTop() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        val home = entry(Destination.Home)
        val top = entry(Destination.Tuner, supportingPane(SupportingPane.Library))
        val scene = assertIs<SupportingPaneScene<DestinationKey>>(strategy.calculate(listOf(home, top)))
        assertEquals(listOf(home), scene.previousEntries)
    }

    @Test
    fun equalInputsProduceEqualScenes() {
        val strategy = SupportingPaneSceneStrategy<DestinationKey>(enabled = true)
        val entries = listOf(entry(Destination.Home, supportingPane(SupportingPane.Library)))
        assertEquals(strategy.calculate(entries), strategy.calculate(entries))
    }
}
