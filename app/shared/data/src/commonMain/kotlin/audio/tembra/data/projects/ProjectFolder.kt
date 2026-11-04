package audio.tembra.data.projects

import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.ProjectId
import okio.Path

/**
 * One project's files: `project.json`, the device-side `view.json`, `audio/<sha256>.<ext>` copies under [root] and
 * `<sha256>.peaks` overviews under [cacheRoot]. Staging files sit beside their targets so the rename is atomic; the
 * ones that start with a dot (an import in flight, a take being recorded) are invisible to the project's size and to
 * the retire sweep.
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

    /** Where the engine's recorder writes a take until adoption names it by its hash (contracts/take-storage.md; research R14). */
    fun takeStagingPath(token: String): Path = audio / "$TAKE_PREFIX$token.wav"

    /** The take's manifest beside its staging file, written by the controller before capture begins. */
    fun takeManifestPath(token: String): Path = audio / "$TAKE_PREFIX$token$TAKE_MANIFEST_SUFFIX"

    /** The token of a take manifest named like [takeManifestPath]; null for any other file. */
    fun takeTokenOf(manifest: Path): String? {
        val name = manifest.name
        if (!name.startsWith(TAKE_PREFIX) || !name.endsWith(TAKE_MANIFEST_SUFFIX)) return null
        return name.removePrefix(TAKE_PREFIX).removeSuffix(TAKE_MANIFEST_SUFFIX).takeIf { it.isNotEmpty() }
    }

    companion object {
        const val DOCUMENT_NAME = "project.json"
        const val VIEW_NAME = "view.json"
        const val AUDIO_DIR = "audio"
        const val TAKE_PREFIX = ".take-"
        const val TAKE_MANIFEST_SUFFIX = ".json"
    }
}
