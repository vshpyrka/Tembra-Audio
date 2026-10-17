package audio.tembra.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Back-stack derivation of data-model.md §2. */
class ShellUiStateTest {

    @Test
    fun startsOnTimeline() {
        assertEquals(Destination.Timeline, ShellUiState().selected)
    }

    @Test
    fun timelineAloneIsTheBackStackOnTimeline() {
        assertEquals(listOf(Destination.Timeline), ShellUiState().backStack)
    }

    @Test
    fun anotherDestinationSitsAboveTimeline() {
        assertEquals(listOf(Destination.Timeline, Destination.Tuner), ShellUiState(selected = Destination.Tuner).backStack)
    }
}
