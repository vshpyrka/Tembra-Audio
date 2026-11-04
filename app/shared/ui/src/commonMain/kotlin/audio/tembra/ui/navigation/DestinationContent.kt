package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import audio.tembra.core.project.TrackId
import audio.tembra.ui.metronome.MetronomeScreen
import audio.tembra.ui.profile.ProfileScreen
import audio.tembra.ui.projects.ProjectsScreen
import audio.tembra.ui.timeline.TimelineScreen
import audio.tembra.ui.tuner.TunerScreen

/** The single mapping from the registry to the feature packages; a new destination fails to compile until mapped. */
@Composable
fun DestinationContent(
    destination: Destination,
    onOpenTrackSettings: (TrackId) -> Unit = {},
    nestedTrackSettings: TrackId? = null,
    onCloseTrackSettings: () -> Unit = {},
    onSelectDestination: (Destination) -> Unit = {},
    onShowShortcuts: () -> Unit = {},
) {
    when (destination) {
        Destination.Timeline -> TimelineScreen(onOpenTrackSettings = onOpenTrackSettings, nestedTrackSettings = nestedTrackSettings, onCloseTrackSettings = onCloseTrackSettings)
        Destination.Tuner -> TunerScreen()
        Destination.Metronome -> MetronomeScreen()
        Destination.Projects -> ProjectsScreen(onOpenTimeline = { onSelectDestination(Destination.Timeline) })
        Destination.Profile -> ProfileScreen(onShowShortcuts = onShowShortcuts)
    }
}
