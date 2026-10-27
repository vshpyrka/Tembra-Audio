package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavKey
import audio.tembra.core.project.TrackId
import audio.tembra.ui.tracksettings.TrackSettingsKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** Back-stack derivation of data-model.md §2 (feature 002) and the track-settings entry of feature 009. */
class ShellUiStateTest {

    @Test
    fun startsOnTheStartDestination() {
        assertEquals(StartDestination, ShellUiState().selected)
        assertEquals(Destination.Timeline, StartDestination)
    }

    @Test
    fun timelineAloneIsTheBackStackOnTimeline() {
        assertEquals(listOf<NavKey>(DestinationKey(Destination.Timeline)), ShellUiState().backStack)
    }

    @Test
    fun anotherDestinationSitsAboveTimeline() {
        assertEquals(
            listOf<NavKey>(DestinationKey(Destination.Timeline), DestinationKey(Destination.Tuner)),
            ShellUiState(selected = Destination.Tuner).backStack,
        )
    }

    @Test
    fun trackSettingsSitAboveTheTimelineOnly() {
        val key = TrackSettingsKey(TrackId("t1"))
        assertEquals(
            listOf<NavKey>(DestinationKey(Destination.Timeline), key),
            ShellUiState(selected = Destination.Timeline, trackSettings = key).backStack,
        )
        assertEquals(
            listOf<NavKey>(DestinationKey(Destination.Timeline), DestinationKey(Destination.Tuner)),
            ShellUiState(selected = Destination.Tuner, trackSettings = key).backStack,
        )
    }
}
