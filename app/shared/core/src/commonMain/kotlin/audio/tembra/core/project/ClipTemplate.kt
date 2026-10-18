package audio.tembra.core.project

/**
 * What an accepted import yields before the clip exists (contracts/project-storage.md step 6): the asset, the whole
 * audio (offset 0, length = frames) and the name taken from the file name without its extension. The editor turns it
 * into a [Clip] once the id and the start are known.
 */
data class ClipTemplate(
    val assetId: AudioAssetId,
    val name: String,
    val offsetFrames: Long,
    val lengthFrames: Long,
) {
    fun toClip(id: ClipId, startBeats: Beats): Clip = Clip(id, assetId, startBeats, offsetFrames, lengthFrames, name)

    companion object {
        /** The whole of [asset], named after its file. */
        fun forAsset(id: AudioAssetId, asset: AudioAsset): ClipTemplate =
            ClipTemplate(assetId = id, name = nameOf(asset.fileName), offsetFrames = 0, lengthFrames = asset.frames)

        /** The file name without its last extension; a name with no extension is kept whole. */
        fun nameOf(fileName: String): String {
            val dot = fileName.lastIndexOf('.')
            return if (dot > 0) fileName.substring(0, dot) else fileName
        }
    }
}
