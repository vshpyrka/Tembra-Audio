package audio.tembra.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Back-stack derivation of data-model.md §2. */
class ShellUiStateTest {

    @Test
    fun startsOnHome() {
        assertEquals(Destination.Home, ShellUiState().selected)
    }

    @Test
    fun homeAloneIsTheBackStackOnHome() {
        assertEquals(listOf(Destination.Home), ShellUiState().backStack)
    }

    @Test
    fun anotherDestinationSitsAboveHome() {
        assertEquals(listOf(Destination.Home, Destination.Tuner), ShellUiState(selected = Destination.Tuner).backStack)
    }
}
