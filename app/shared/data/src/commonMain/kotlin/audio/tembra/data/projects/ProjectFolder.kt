package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.ProjectId
import okio.Path

/**
 * One project's files: `project.json`, the device-side `view.json`, `audio/<sha256>.<ext>` copies under [root] and
 * `<sha256>.peaks` overviews under [cacheRoot]. Staging files sit beside their targets so the rename is atomic.
 */
data class ProjectFolder(val id: ProjectId, val root: Path, val cacheRoot: Path) {
    val document: Path get() = root / DOCUMENT_NAME
    val documentStaging: Path get() = root / "$DOCUMENT_NAME.tmp"
    val view: Path get() = root / VIEW_NAME
    val viewStaging: Path get() = root / "$VIEW_NAME.tmp"
    val audio: Path get() = root / AUDIO_DIR

    fun audioPath(assetId: AudioAssetId, format: AudioFormat): Path = audio / "${assetId.sha256}.${format.extension}"

    /** Where an import copies before it knows the hash (a UUID token) or the format (the hash itself). */
    fun stagingAudioPath(token: String): Path = audio / ".staging-$token"

    fun overviewPath(assetId: AudioAssetId): Path = cacheRoot / "${assetId.sha256}.peaks"

    companion object {
        const val DOCUMENT_NAME = "project.json"
        const val VIEW_NAME = "view.json"
        const val AUDIO_DIR = "audio"
    }
}
