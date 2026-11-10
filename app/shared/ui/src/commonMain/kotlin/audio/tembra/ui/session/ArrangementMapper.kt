package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Clip
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.Project
import audio.tembra.core.project.ProjectLimits
import audio.tembra.core.project.audibleTracks
import audio.tembra.core.project.tempoMap
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
 *
 * An engine source has one read position, so two clips of one asset that sound at once (the same loop on two tracks,
 * feature 012's stress project) each need their own: [map] hands the asset's sources out first-fit over its clips by
 * start, a source free again [SOURCE_REUSE_GAP_SECONDS] after its clip ends, and [sourcesNeeded] says how many the
 * session should hold open per asset. Short of sources, clips share one and still play (the source re-primes at each
 * start, which is what every clip of an asset did before 2026-11-09).
 */
object ArrangementMapper {
    /**
     * How long after a clip's end a source may serve another clip of the asset. The streamer primes a shared source for
     * its next clip only once the previous clip has released it, and its idle pass is 10 ms; a second covers that with
     * room for a slow disk. Clips closer than this (back to back, overlapping) get their own source.
     */
    const val SOURCE_REUSE_GAP_SECONDS = 1.0

    fun map(project: Project, sources: Map<AudioAssetId, List<ClipSourceHandle>>): ArrangementResult {
        if (project.tracks.size > ProjectLimits.MAX_TRACKS) return ArrangementResult.TooLarge
        val audible = project.audibleTracks
        val tracks = project.tracks.map { track -> TrackRender(gainLinear = track.gainDb.toLinear(), audible = track.id in audible) }
        val assigned = assignSources(project, sources)
        val clips = ArrayList<ClipRender>()
        project.tracks.forEachIndexed { index, track ->
            for (clip in track.clips.sortedBy { it.startBeats.value }) {
                val source = assigned[clip.id] ?: continue
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

    /** The sources each asset needs so that its clips sounding at once, or within [SOURCE_REUSE_GAP_SECONDS], have their own. */
    fun sourcesNeeded(project: Project): Map<AudioAssetId, Int> =
        clipsByAsset(project).mapValues { (assetId, clips) -> assign(project, assetId, clips, limit = Int.MAX_VALUE).slots }

    private fun assignSources(project: Project, sources: Map<AudioAssetId, List<ClipSourceHandle>>): Map<ClipId, ClipSourceHandle> {
        val out = HashMap<ClipId, ClipSourceHandle>()
        for ((assetId, clips) in clipsByAsset(project)) {
            val handles = sources[assetId]?.takeIf { it.isNotEmpty() } ?: continue
            for ((clipId, slot) in assign(project, assetId, clips, limit = handles.size).byClip) out[clipId] = handles[slot]
        }
        return out
    }

    private class Assignment(val slots: Int, val byClip: Map<ClipId, Int>)

    /**
     * First fit over the asset's clips by start. A clip continues a slot whose last clip ends where it begins, in time
     * and in the source (the two halves of a split: the fifo flows on); otherwise a slot is free again
     * [SOURCE_REUSE_GAP_SECONDS] after its clip ends. With [limit] slots, a clip that finds none free takes the slot
     * freed earliest and shares it.
     */
    private fun assign(project: Project, assetId: AudioAssetId, clips: List<Clip>, limit: Int): Assignment {
        val asset = project.assets[assetId] ?: return Assignment(0, emptyMap())
        val rate = asset.sampleRate
        val gap = (SOURCE_REUSE_GAP_SECONDS * rate).toLong()
        val busyUntil = ArrayList<Long>()
        val sourceEnd = ArrayList<Long>()
        val byClip = HashMap<ClipId, Int>()
        for (clip in clips.sortedBy { it.startBeats.value }) {
            val start = project.tempoMap.beatsToFrames(clip.startBeats, rate)
            val end = start + clip.lengthFrames
            var slot = busyUntil.indices.firstOrNull { kotlin.math.abs(busyUntil[it] - start) <= CONTIGUOUS_TOLERANCE_FRAMES && sourceEnd[it] == clip.offsetFrames }
                ?: busyUntil.indices.firstOrNull { busyUntil[it] + gap <= start }
                ?: -1
            when {
                slot >= 0 -> {
                    busyUntil[slot] = end
                    sourceEnd[slot] = clip.offsetFrames + clip.lengthFrames
                }
                busyUntil.size < limit -> {
                    busyUntil += end
                    sourceEnd += clip.offsetFrames + clip.lengthFrames
                    slot = busyUntil.lastIndex
                }
                else -> {
                    slot = busyUntil.indices.minBy { busyUntil[it] }
                    busyUntil[slot] = maxOf(busyUntil[slot], end)
                    sourceEnd[slot] = clip.offsetFrames + clip.lengthFrames
                }
            }
            byClip[clip.id] = slot
        }
        return Assignment(busyUntil.size, byClip)
    }

    /** Beats round to frames on both sides of a split, so its halves may differ by a frame or two in time. */
    private const val CONTIGUOUS_TOLERANCE_FRAMES = 2L

    private fun clipsByAsset(project: Project): Map<AudioAssetId, List<Clip>> {
        val out = LinkedHashMap<AudioAssetId, MutableList<Clip>>()
        for (track in project.tracks) for (clip in track.clips) out.getOrPut(clip.assetId) { ArrayList() } += clip
        return out
    }
}
