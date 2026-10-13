package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavKey
import audio.tembra.ui.tracksettings.TrackSettingsKey

/**
 * Navigation state (data-model.md §2 of feature 002; §5 of feature 009): which destination is selected and, on the
 * Timeline, which track's settings are open above it. The back stack is derived, never stored.
 */
data class ShellUiState(
    val selected: Destination = Destination.Home,
    val trackSettings: TrackSettingsKey? = null,
) {
    /** `[Home]` on Home, `[Home, selected]` elsewhere, plus the open track settings above the Timeline (spec FR-010). */
    val backStack: List<NavKey>
        get() = buildList {
            add(DestinationKey(Destination.Home))
            if (selected != Destination.Home) add(DestinationKey(selected))
            if (trackSettings != null && selected == Destination.Timeline) add(trackSettings)
        }
}
