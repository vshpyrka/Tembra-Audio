package audio.tembra.ui.session

import audio.tembra.core.project.AudioAssetId
import audio.tembra.engine.ClipSourceHandle
import audio.tembra.engine.ClipSourceResult
import audio.tembra.engine.TimelineAudio

/**
 * The engine's open clip sources by asset (contracts/project-session.md): opened on project open and on import, closed
 * on project close and when an asset leaves every snapshot of the history. One source per asset however many clips
 * use it.
 */
class ClipSourceRegistry(private val audio: TimelineAudio) {
    private val open = LinkedHashMap<AudioAssetId, ClipSourceHandle>()

    /** The open handles, for [ArrangementMapper]. */
    val handles: Map<AudioAssetId, ClipSourceHandle> get() = open

    /** The assets with an open source. */
    val assets: Set<AudioAssetId> get() = open.keys

    /** Opens [path] for [assetId] unless already open; false when the engine cannot decode it. */
    suspend fun open(assetId: AudioAssetId, path: String): Boolean {
        if (assetId in open) return true
        return when (val result = audio.openClipSource(path)) {
            is ClipSourceResult.Opened -> {
                open[assetId] = result.handle
                true
            }
            ClipSourceResult.Undecodable -> false
        }
    }

    fun close(assetId: AudioAssetId) {
        open.remove(assetId)?.let(audio::closeClipSource)
    }

    fun closeAll() {
        for (handle in open.values) audio.closeClipSource(handle)
        open.clear()
    }
}
