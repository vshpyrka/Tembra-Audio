package audio.tembra.ui.navigation

import androidx.compose.runtime.Composable
import audio.tembra.ui.home.HomeScreen
import audio.tembra.ui.metronome.MetronomeScreen
import audio.tembra.ui.profile.ProfileScreen
import audio.tembra.ui.projects.ProjectsScreen
import audio.tembra.ui.timeline.TimelineScreen
import audio.tembra.ui.tuner.TunerScreen

/** The single mapping from the registry to the feature packages; a new destination fails to compile until mapped. */
@Composable
fun DestinationContent(destination: Destination) {
    when (destination) {
        Destination.Home -> HomeScreen()
        Destination.Timeline -> TimelineScreen()
        Destination.Tuner -> TunerScreen()
        Destination.Metronome -> MetronomeScreen()
        Destination.Projects -> ProjectsScreen()
        Destination.Profile -> ProfileScreen()
    }
}
