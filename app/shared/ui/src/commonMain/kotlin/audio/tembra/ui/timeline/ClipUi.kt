package audio.tembra.ui.timeline

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.data.projects.WaveformOverview

/**
 * One clip as the lane draws it (contracts/timeline-screen.md): its span in beats at the current tempo, the source
 * frames the waveform columns index, the overview (null while building or failed) and whether its audio is available.
 */
@Immutable
data class ClipUi(
    val id: ClipId,
    val assetId: AudioAssetId,
    val name: String,
    val startBeats: Beats,
    val lengthBeats: Beats,
    val offsetFrames: Long,
    val lengthFrames: Long,
    val sampleRate: Int,
    val overview: WaveformOverview?,
    val overviewStatus: OverviewStatus,
    val available: Boolean,
) {
    val endBeats: Beats get() = startBeats + lengthBeats
    val lengthSeconds: Double get() = lengthFrames.toDouble() / sampleRate
}
