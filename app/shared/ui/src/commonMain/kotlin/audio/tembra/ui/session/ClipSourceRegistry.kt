package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ProjectLimits
import audio.tembra.engine.ClipSourceHandle
import audio.tembra.engine.ClipSourceResult
import audio.tembra.engine.TimelineAudio

/**
 * The engine's open clip sources by asset (contracts/project-session.md): opened on project open and on import, closed
 * on project close and when an asset leaves every snapshot of the history. One source per asset, plus one more for
 * every further clip of it that sounds at the same time ([ArrangementMapper.sourcesNeeded]; [ensure]): a source has
 * one read position, and the same loop on six tracks starved five of them (feature 012's stress project, 2026-11-09).
 */
class ClipSourceRegistry(private val audio: TimelineAudio) {
    private val open = LinkedHashMap<AudioAssetId, MutableList<ClipSourceHandle>>()
    private val paths = HashMap<AudioAssetId, String>()

    /** The open handles per asset, for [ArrangementMapper]. */
    val handles: Map<AudioAssetId, List<ClipSourceHandle>> get() = open

    /** The assets with an open source. */
    val assets: Set<AudioAssetId> get() = open.keys

    /** Opens [path] for [assetId] unless already open; false when the engine cannot decode it. */
    suspend fun open(assetId: AudioAssetId, path: String): Boolean {
        if (assetId in open) return true
        return when (val result = audio.openClipSource(path)) {
            is ClipSourceResult.Opened -> {
                open[assetId] = mutableListOf(result.handle)
                paths[assetId] = path
                true
            }
            ClipSourceResult.Undecodable -> false
        }
    }

    /**
     * Opens further sources of [assetId]'s file until [count] are open, at most one per track (one clip plays per
     * track, so no more can sound at once). The number opened: 0 for an asset that is not open or already has enough;
     * an open that fails ends the loop and the ones open stay.
     */
    suspend fun ensure(assetId: AudioAssetId, count: Int): Int {
        val handles = open[assetId] ?: return 0
        val path = paths[assetId] ?: return 0
        val target = minOf(count, ProjectLimits.MAX_TRACKS)
        var opened = 0
        while (handles.size < target) {
            when (val result = audio.openClipSource(path)) {
                is ClipSourceResult.Opened -> {
                    handles += result.handle
                    opened++
                }
                ClipSourceResult.Undecodable -> return opened
            }
        }
        return opened
    }

    /** True when an open asset has fewer sources than [needed] asks for (an asset that is not open cannot be helped). */
    fun lacks(needed: Map<AudioAssetId, Int>): Boolean =
        needed.any { (assetId, count) -> open[assetId]?.let { it.size < minOf(count, ProjectLimits.MAX_TRACKS) } ?: false }

    fun close(assetId: AudioAssetId) {
        open.remove(assetId)?.forEach(audio::closeClipSource)
        paths.remove(assetId)
    }

    fun closeAll() {
        for (handles in open.values) for (handle in handles) audio.closeClipSource(handle)
        open.clear()
        paths.clear()
    }
}
