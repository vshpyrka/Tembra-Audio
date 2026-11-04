package audio.tembra.ui.session

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.data.projects.WaveformOverview

/**
 * The growing clip of a running take (feature 010, research R7): its track, name and start, the overview the
 * 10 Hz mirror replaces (never mutated in place), and the kept frames and rate at the last mirror tick for the
 * semantics. The drawn length is read per frame from the capture telemetry, not from here.
 */
@Immutable
data class LiveTakeUi(
    val trackId: TrackId,
    val clipName: String,
    val startBeats: Beats,
    val overview: WaveformOverview?,
    val framesCaptured: Long,
    val sampleRate: Int,
) {
    val lengthSeconds: Double get() = if (sampleRate > 0) framesCaptured.toDouble() / sampleRate else 0.0
}
