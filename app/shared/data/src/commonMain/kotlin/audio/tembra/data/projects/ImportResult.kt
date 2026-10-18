package audio.tembra.data.projects

import audio.tembra.core.project.AudioAsset
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.ClipTemplate

/** The outcome of importing one picked file (contracts/project-storage.md, import pipeline). */
sealed interface ImportResult {
    /** The copy is in place; [overviewBuilt] is false when the engine could not write its overview. */
    data class Accepted(val assetId: AudioAssetId, val asset: AudioAsset, val clip: ClipTemplate, val overviewBuilt: Boolean) : ImportResult

    data class Refused(val fileName: String, val reason: ImportRefusal) : ImportResult
}
