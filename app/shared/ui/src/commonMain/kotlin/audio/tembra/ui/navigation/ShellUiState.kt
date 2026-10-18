package audio.tembra.ui.navigation

import androidx.navigation3.runtime.NavKey
import audio.tembra.ui.tracksettings.TrackSettingsKey

/** The start destination: the first in display order. */
val StartDestination: Destination = Destination.entries.first()

/**
 * Navigation state (data-model.md §2 of feature 002; §5 of feature 009): which destination is selected and, on the
 * Timeline, which track's settings are open above it. The back stack is derived, never stored.
 */
data class ShellUiState(
    val selected: Destination = StartDestination,
    val trackSettings: TrackSettingsKey? = null,
) {
    /**
     * `[start]` on the start destination, else `[start, selected]`, plus the open track settings above the Timeline;
     * what the NavDisplay renders (spec FR-010).
     */
    val backStack: List<NavKey>
        get() = buildList {
            add(DestinationKey(StartDestination))
            if (selected != StartDestination) add(DestinationKey(selected))
            if (trackSettings != null && selected == Destination.Timeline) add(trackSettings)
        }
}
