package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.Project
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.ProjectViewState
import audio.tembra.data.projects.WaveformOverview
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The open project as the session publishes it (contracts/project-session.md "State"): the document, the restored
 * view state, each asset's overview status and the overview itself once readable, the assets whose file is missing or
 * undecodable, the playback state, the history flags, the save indicator and the metronome toggle. [project] stays
 * null until the first open completes; [unavailable] carries the reason when the last open failed.
 */
data class SessionState(
    val project: Project? = null,
    val viewState: ProjectViewState = ProjectViewState(),
    val assetsReady: PersistentMap<AudioAssetId, OverviewStatus> = persistentMapOf(),
    val overviews: PersistentMap<AudioAssetId, WaveformOverview> = persistentMapOf(),
    val missingAssets: PersistentSet<AudioAssetId> = persistentSetOf(),
    val playback: PlaybackState = PlaybackState.Stopped(Beats.Zero),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saving: Boolean = false,
    val metronomeOn: Boolean = false,
    val unavailable: String? = null,
    /** The recording session (feature 010): the armed track, the phase, the monitor and the growing take. */
    val recording: RecordingUi = RecordingUi(),
) {
    val isPlaying: Boolean get() = playback is PlaybackState.Playing || playback is PlaybackState.Starting
}
