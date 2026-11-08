package audio.tembra.ui.mixer

import androidx.compose.runtime.Immutable
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.Project
import audio.tembra.core.project.TrackId
import audio.tembra.core.project.audibleTracks
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/** One mixer strip as the sheet renders it (feature 012, data-model.md §4): the track's fields, its 1-based [number] and which level its meter reads. */
@Immutable
data class StripUi(
    val id: TrackId,
    /** The track's position in the project, the engine's track index and the strip's tag index. */
    val index: Int,
    /** The number drawn at the top: [index] + 1, never skipped (spec FR-002). */
    val number: Int,
    val name: String,
    val colorIndex: Int,
    val gainDb: GainDb,
    val pan: Pan,
    val muted: Boolean,
    val soloed: Boolean,
    /** From the solo/mute truth table: the strip draws dimmed when false. */
    val audible: Boolean,
    val meterSource: MeterSource = MeterSource.Playback,
) {
    companion object {
        /** One strip per track of [project] in order; the strip of [armedTrack] reads the input (spec FR-014). */
        fun forProject(project: Project, armedTrack: TrackId?): ImmutableList<StripUi> {
            val audible = project.audibleTracks
            return project.tracks.mapIndexed { index, track ->
                StripUi(
                    id = track.id,
                    index = index,
                    number = index + 1,
                    name = track.name,
                    colorIndex = track.colorIndex,
                    gainDb = track.gainDb,
                    pan = track.pan,
                    muted = track.muted,
                    soloed = track.soloed,
                    audible = track.id in audible,
                    meterSource = if (track.id == armedTrack) MeterSource.Input else MeterSource.Playback,
                )
            }.toPersistentList()
        }
    }
}
