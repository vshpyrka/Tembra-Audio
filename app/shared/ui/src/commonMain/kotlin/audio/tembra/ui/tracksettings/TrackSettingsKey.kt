package audio.tembra.ui.tracksettings

import androidx.navigation3.runtime.NavKey
import audio.tembra.core.project.TrackId

/** The nested track-settings entry above the Timeline on compact windows (contracts/timeline-screen.md). */
data class TrackSettingsKey(val trackId: TrackId) : NavKey
