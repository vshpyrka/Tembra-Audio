package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectLimits
import audio.tembra.core.project.audibleTracks
import audio.tembra.engine.ArrangementSnapshot
import audio.tembra.engine.ClipRender
import audio.tembra.engine.ClipSourceHandle
import audio.tembra.engine.TrackRender

/**
 * The derived real-time view of a project (Constitution V; contracts/project-session.md): every track becomes a strip
 * state (`GainDb.toLinear()` and the solo/mute truth table's audibility), every clip whose asset has an open source
 * becomes a clip state with its start in beats and its offset and length at the source's rate. A clip whose source is
 * not open (a missing file, an unknown format) is left out: it draws greyed and never plays (spec FR-030). A project
 * past the engine's bounds yields [ArrangementResult.TooLarge] rather than a crash.
 */
object ArrangementMapper {
    fun map(project: Project, sources: Map<AudioAssetId, ClipSourceHandle>): ArrangementResult {
        if (project.tracks.size > ProjectLimits.MAX_TRACKS) return ArrangementResult.TooLarge
        val audible = project.audibleTracks
        val tracks = project.tracks.map { track -> TrackRender(gainLinear = track.gainDb.toLinear(), audible = track.id in audible) }
        val clips = ArrayList<ClipRender>()
        project.tracks.forEachIndexed { index, track ->
            for (clip in track.clips.sortedBy { it.startBeats.value }) {
                val source = sources[clip.assetId] ?: continue
                val asset = project.assets[clip.assetId] ?: continue
                clips += ClipRender(
                    trackIndex = index,
                    source = source,
                    startBeats = clip.startBeats.value,
                    offsetFrames = clip.offsetFrames,
                    lengthFrames = clip.lengthFrames,
                    sourceRate = asset.sampleRate,
                )
            }
        }
        if (clips.size > ProjectLimits.MAX_CLIPS) return ArrangementResult.TooLarge
        return ArrangementResult.Mapped(ArrangementSnapshot(tracks, clips))
    }

    /** One track's strip state, for the `setTrack` fast path. */
    fun track(project: Project, index: Int): TrackRender {
        val track = project.tracks[index]
        return TrackRender(gainLinear = track.gainDb.toLinear(), audible = track.id in project.audibleTracks)
    }
}
